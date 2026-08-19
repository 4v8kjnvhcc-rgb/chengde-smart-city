#!/usr/bin/env bash
# =============================================================================
# 在 10.10.10.57（应用机）上执行：B0 备份（镜像 + 代码目录，默认不做库 dump）
#   + 可选换代码包 + load 镜像 + prod_up_app
# 一般由跳板机 jump_oneclick_deploy.sh 上传后调用；也可在 .57 上手动：
#   IMAGE_TAR=/opt/chengde/inbox/chengde-app-images_xxx.tar \
#   CODE_TGZ=/opt/chengde/inbox/chengde-smart-city_xxx.tar.gz \
#   bash /opt/chengde/inbox/prod_deploy_on_57.sh
# =============================================================================
set -eu
# pipefail 在部分旧版 bash（如麒麟 V10）不支持，单独启用
(set -o pipefail 2>/dev/null) && set -o pipefail || true

REMOTE_ROOT="${REMOTE_ROOT:-/opt/chengde}"
REMOTE_APP="${REMOTE_APP:-$REMOTE_ROOT/chengde-smart-city}"
REMOTE_INBOX="${REMOTE_INBOX:-$REMOTE_ROOT/inbox}"
IMAGE_TAR="${IMAGE_TAR:-}"
CODE_TGZ="${CODE_TGZ:-}"
# 发版策略：必备镜像/代码备份；库 dump 默认跳过（避免现场拉 mysql:8.0 卡住）。
# 确需 dump 时再显式：SKIP_DB_BACKUP=0
SKIP_DB_BACKUP="${SKIP_DB_BACKUP:-1}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:9087/actuator/health}"
PORTAL_HINT="${PORTAL_HINT:-http://10.10.10.57:9087/bigdata-web}"

log() { echo "[$(date '+%H:%M:%S')] $*"; }
die() { echo "ERROR: $*" >&2; exit 1; }

[[ -n "$IMAGE_TAR" && -f "$IMAGE_TAR" ]] || die "IMAGE_TAR 不存在: ${IMAGE_TAR:-"(空)"}"
command -v docker >/dev/null 2>&1 || die "未找到 docker"

STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="$REMOTE_ROOT/backup/$STAMP"
mkdir -p "$BACKUP_DIR" "$REMOTE_INBOX"

log "======== 发版开始 STAMP=$STAMP ========"
log "镜像: $IMAGE_TAR"
log "代码包: ${CODE_TGZ:-"(无)"}"
log "备份目录: $BACKUP_DIR"

# ---------- B0① 备份当前应用镜像 ----------
log "[B0] 备份当前 smart-city/platform-*:local 镜像标签为 :prev ..."
if docker image inspect smart-city/platform-backend:local >/dev/null 2>&1 \
  && docker image inspect smart-city/platform-frontend:local >/dev/null 2>&1; then
  docker tag smart-city/platform-backend:local smart-city/platform-backend:prev
  docker tag smart-city/platform-frontend:local smart-city/platform-frontend:prev
  docker save -o "$BACKUP_DIR/app-images-prev.tar" \
    smart-city/platform-backend:prev \
    smart-city/platform-frontend:prev
  docker images 'smart-city/platform-*' --format 'table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}' \
    | tee "$BACKUP_DIR/images-before.txt"
  log "[B0] 镜像备份完成: $BACKUP_DIR/app-images-prev.tar"
else
  log "[B0] 未找到现有 :local 镜像，跳过镜像备份（首次或已清理）"
fi

# ---------- B0③ 备份代码目录 ----------
if [[ -d "$REMOTE_APP" ]]; then
  log "[B0] 备份代码目录 $REMOTE_APP ..."
  tar -czf "$BACKUP_DIR/chengde-smart-city-prev.tar.gz" -C "$REMOTE_ROOT" "$(basename "$REMOTE_APP")"
  cp -a "$REMOTE_APP"/compose/prod-*.env "$BACKUP_DIR/" 2>/dev/null || true
  log "[B0] 代码目录备份完成"
else
  log "[B0] 无现有 $REMOTE_APP，跳过代码目录备份"
fi

# ---------- B0② 库 dump（默认跳过）----------
if [[ "$SKIP_DB_BACKUP" != "1" && -f "$REMOTE_APP/compose/prod-app.env" ]]; then
  log "[B0] SKIP_DB_BACKUP=0，尝试备份业务库（失败不中断发版）..."
  ENV="$REMOTE_APP/compose/prod-app.env"
  if grep -q $'\r' "$ENV" 2>/dev/null; then
    sed -i 's/\r$//' "$ENV"
  fi
  # shellcheck disable=SC1090
  set +e
  MYSQL_HOST=$(grep '^MYSQL_HOST=' "$ENV" | cut -d= -f2- | tr -d '\r')
  MYSQL_PORT=$(grep '^MYSQL_PORT=' "$ENV" | cut -d= -f2- | tr -d '\r')
  MYSQL_USER=$(grep '^MYSQL_USER=' "$ENV" | cut -d= -f2- | tr -d '\r')
  MYSQL_PASSWORD=$(grep '^MYSQL_PASSWORD=' "$ENV" | cut -d= -f2- | tr -d '\r')
  if [[ -n "$MYSQL_HOST" && -n "$MYSQL_USER" ]]; then
    # 须本机已有 mysql:8.0；现场勿依赖临时 pull（内网易卡住）
    docker run --rm mysql:8.0 mysqldump -h"$MYSQL_HOST" -P"${MYSQL_PORT:-13306}" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
      --single-transaction --routines --triggers smart_city \
      > "$BACKUP_DIR/smart_city.sql" 2>"$BACKUP_DIR/mysqldump.err"
    if [[ $? -eq 0 && -s "$BACKUP_DIR/smart_city.sql" ]]; then
      log "[B0] smart_city 库备份成功"
    else
      log "[B0] 库备份失败（见 $BACKUP_DIR/mysqldump.err），继续发版"
    fi
  fi
  set -e
else
  log "[B0] 按策略跳过库 dump（仅备份镜像+代码；SKIP_DB_BACKUP=$SKIP_DB_BACKUP）"
fi

# ---------- B2 换代码包（可选）----------
if [[ -n "$CODE_TGZ" && -f "$CODE_TGZ" ]]; then
  log "[B2] 解压代码包到 $REMOTE_ROOT ..."
  # 保留现场 env：解压前再拷一份到 inbox 旁
  if [[ -f "$REMOTE_APP/compose/prod-app.env" ]]; then
    cp -a "$REMOTE_APP/compose/prod-app.env" "$BACKUP_DIR/prod-app.env.before-extract"
  fi
  if [[ -f "$REMOTE_APP/compose/prod-mid.env" ]]; then
    cp -a "$REMOTE_APP/compose/prod-mid.env" "$BACKUP_DIR/prod-mid.env.before-extract"
  fi
  tar -xzf "$CODE_TGZ" -C "$REMOTE_ROOT"
  # 若解压出来的 env 与备份并存：优先恢复「解压前现场 env」，避免误用开发机密码覆盖生产
  if [[ -f "$BACKUP_DIR/prod-app.env.before-extract" ]]; then
    mkdir -p "$REMOTE_APP/compose"
    cp -a "$BACKUP_DIR/prod-app.env.before-extract" "$REMOTE_APP/compose/prod-app.env"
    log "[B2] 已恢复现场 compose/prod-app.env（避免被代码包覆盖）"
  fi
  if [[ -f "$BACKUP_DIR/prod-mid.env.before-extract" ]]; then
    cp -a "$BACKUP_DIR/prod-mid.env.before-extract" "$REMOTE_APP/compose/prod-mid.env"
    log "[B2] 已恢复现场 compose/prod-mid.env"
  fi
else
  log "[B2] 无代码包，保持现有 $REMOTE_APP"
fi

[[ -d "$REMOTE_APP" ]] || die "缺少应用目录 $REMOTE_APP（请先完成 D23 初次部署或放入代码包）"
[[ -x "$REMOTE_APP/scripts/prod_up_app.sh" || -f "$REMOTE_APP/scripts/prod_up_app.sh" ]] \
  || die "缺少 $REMOTE_APP/scripts/prod_up_app.sh"

# ---------- B1 load 镜像 ----------
log "[B1] docker load ..."
docker load -i "$IMAGE_TAR"
docker images 'smart-city/platform-*' --format 'table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}' \
  | tee "$BACKUP_DIR/images-after-load.txt"

# ---------- 启动 ----------
log "[B1] 执行 prod_up_app.sh ..."
chmod +x "$REMOTE_APP/scripts/prod_up_app.sh" || true
(
  cd "$REMOTE_APP"
  # 去 CRLF
  if [[ -f compose/prod-app.env ]] && grep -q $'\r' compose/prod-app.env 2>/dev/null; then
    sed -i 's/\r$//' compose/prod-app.env
  fi
  bash ./scripts/prod_up_app.sh
)

log "等待后端健康检查..."
ok=0
for i in $(seq 1 36); do
  if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    ok=1
    break
  fi
  sleep 5
done

log "-------- docker ps（应用相关）--------"
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | head -n 30 || true

if [[ "$ok" -eq 1 ]]; then
  log "健康检查通过: $HEALTH_URL"
else
  log "WARN: 健康检查未在约 3 分钟内通过，请手工: curl -s $HEALTH_URL"
fi

log "======== 发版结束 ========"
log "备份: $BACKUP_DIR"
log "门户: $PORTAL_HINT"
log "回退镜像可参考 D23 B5：docker load -i $BACKUP_DIR/app-images-prev.tar 后 prod_up_app.sh"
