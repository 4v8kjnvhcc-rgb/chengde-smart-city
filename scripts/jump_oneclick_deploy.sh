#!/usr/bin/env bash
# =============================================================================
# 跳板机一键部署到 10.10.10.57（应用机）
# -----------------------------------------------------------------------------
# 用法（在跳板机）：
#   1. 建目录，例如：mkdir -p ~/chengde-release && cd ~/chengde-release
#   2. 放入本脚本 + 产物（至少镜像；代码包可选）：
#        jump_oneclick_deploy.sh
#        prod_deploy_on_57.sh          # 与本脚本同目录，会一并上传
#        chengde-app-images_*.tar      # build_prod_images 产出
#        chengde-smart-city_*.tar.gz   # pack_prod_release 产出（可选）
#   3. chmod +x jump_oneclick_deploy.sh prod_deploy_on_57.sh
#   4. 编辑下方默认变量，或导出环境变量后执行：
#        ./jump_oneclick_deploy.sh
#
# 依赖：跳板机能 ssh/scp 到 .57；.57 已装 Docker 且已有 /opt/chengde/chengde-smart-city
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# ---------- 可改配置（也可用环境变量覆盖）----------
DEPLOY_HOST="${DEPLOY_HOST:-root@10.10.10.57}"
REMOTE_ROOT="${REMOTE_ROOT:-/opt/chengde}"
REMOTE_INBOX="${REMOTE_INBOX:-$REMOTE_ROOT/inbox}"
REMOTE_APP="${REMOTE_APP:-$REMOTE_ROOT/chengde-smart-city}"
# 1=跳过库 mysqldump（纯换镜像、无 Flyway 时更快）；0=按 D23 尝试备份库（需 .57 能访问库机）
SKIP_DB_BACKUP="${SKIP_DB_BACKUP:-1}"
# ssh 额外参数，例如：SSH_OPTS="-p 22 -i ~/.ssh/id_rsa"
SSH_OPTS="${SSH_OPTS:-}"

log() { echo "[$(date '+%H:%M:%S')] $*"; }
die() { echo "ERROR: $*" >&2; exit 1; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "跳板机缺少命令: $1"
}

pick_latest() {
  # $1=glob；按修改时间取最新一个
  local pattern="$1"
  local f
  # shellcheck disable=SC2086
  f="$(ls -t $pattern 2>/dev/null | head -n1 || true)"
  [[ -n "$f" && -f "$f" ]] || return 1
  echo "$f"
}

need_cmd ssh
need_cmd scp
need_cmd ls

REMOTE_DEPLOY_SH="$SCRIPT_DIR/prod_deploy_on_57.sh"
[[ -f "$REMOTE_DEPLOY_SH" ]] || die "同目录缺少 prod_deploy_on_57.sh"

IMG="$(pick_latest 'chengde-app-images_*.tar' || true)"
PKG="$(pick_latest 'chengde-smart-city_*.tar.gz' || true)"

[[ -n "$IMG" ]] || die "当前目录未找到 chengde-app-images_*.tar（请先从办公机拷入）"

log "跳板目录: $SCRIPT_DIR"
log "目标主机: $DEPLOY_HOST"
log "镜像文件: $IMG ($(du -h "$IMG" | awk '{print $1}'))"
if [[ -n "$PKG" ]]; then
  log "代码包:   $PKG ($(du -h "$PKG" | awk '{print $1}'))"
else
  log "代码包:   （无）仅换镜像 / 使用 .57 现有 compose"
fi
log "SKIP_DB_BACKUP=$SKIP_DB_BACKUP"

log "检查 SSH..."
# shellcheck disable=SC2086
ssh $SSH_OPTS "$DEPLOY_HOST" "echo ok && uname -m && docker version --format '{{.Server.Version}}'" \
  || die "无法 SSH 到 $DEPLOY_HOST，请先配置免密或手动登录一次"

log "准备远端目录 $REMOTE_INBOX ..."
# shellcheck disable=SC2086
ssh $SSH_OPTS "$DEPLOY_HOST" "mkdir -p '$REMOTE_INBOX' '$REMOTE_ROOT/backup' && test -d '$REMOTE_APP' || test -d '$REMOTE_ROOT'"

log "上传部署脚本与产物（大文件可能较久，请勿中断）..."
# shellcheck disable=SC2086
scp $SSH_OPTS "$REMOTE_DEPLOY_SH" "$IMG" "$DEPLOY_HOST:$REMOTE_INBOX/"
if [[ -n "$PKG" ]]; then
  # shellcheck disable=SC2086
  scp $SSH_OPTS "$PKG" "$DEPLOY_HOST:$REMOTE_INBOX/"
fi

IMG_BASE="$(basename "$IMG")"
PKG_BASE=""
[[ -n "$PKG" ]] && PKG_BASE="$(basename "$PKG")"

log "远端执行部署..."
# shellcheck disable=SC2086
ssh $SSH_OPTS "$DEPLOY_HOST" \
  "chmod +x '$REMOTE_INBOX/prod_deploy_on_57.sh' && \
   SKIP_DB_BACKUP='$SKIP_DB_BACKUP' \
   REMOTE_ROOT='$REMOTE_ROOT' \
   REMOTE_APP='$REMOTE_APP' \
   REMOTE_INBOX='$REMOTE_INBOX' \
   IMAGE_TAR='$REMOTE_INBOX/$IMG_BASE' \
   CODE_TGZ='$REMOTE_INBOX/$PKG_BASE' \
   bash '$REMOTE_INBOX/prod_deploy_on_57.sh'"

log "全部完成。浏览器验收: http://10.10.10.57:9087/bigdata-web"
