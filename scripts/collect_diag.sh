#!/usr/bin/env bash
# 采集测试/本机故障诊断包，供发给 Cursor / 开发排障。
# 用法：
#   ./scripts/collect_diag.sh -m "问题简述" [-w 30] [-o ./diag-out]
#   BACKEND_LOG=/var/log/chengde/backend.log ./scripts/collect_diag.sh -m "..."

set -euo pipefail

MESSAGE=""
WINDOW_MIN=30
OUT_ROOT="."
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

while getopts "m:w:o:h" opt; do
  case "$opt" in
    m) MESSAGE="$OPTARG" ;;
    w) WINDOW_MIN="$OPTARG" ;;
    o) OUT_ROOT="$OPTARG" ;;
    h)
      echo "Usage: $0 -m \"问题简述\" [-w 分钟] [-o 输出目录]"
      exit 0
      ;;
    *) exit 1 ;;
  esac
done

if [[ -z "$MESSAGE" ]]; then
  echo "必须提供 -m \"问题简述\"" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d-%H%M)"
DIR="${OUT_ROOT%/}/diag-${STAMP}"
mkdir -p "$DIR"

{
  echo "时间：$(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "环境：请填写（测试/本机） URL="
  echo "账号角色："
  echo "菜单/页面："
  echo "操作步骤："
  echo "期望："
  echo "实际：${MESSAGE}"
  echo "是否必现："
  echo "备注：请补全本文件后连同 zip 发给开发"
} > "$DIR/META.txt"

{
  echo "repo=$REPO_ROOT"
  if git -C "$REPO_ROOT" rev-parse HEAD >/dev/null 2>&1; then
    echo "commit=$(git -C "$REPO_ROOT" rev-parse HEAD)"
    echo "commit_short=$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
    echo "branch=$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)"
    echo "status_porcelain<<EOF"
    git -C "$REPO_ROOT" status --porcelain | head -n 40
    echo "EOF"
  else
    echo "commit=unknown (not a git checkout)"
  fi
  echo "collected_at=$(date -Iseconds)"
  echo "window_minutes=${WINDOW_MIN}"
} > "$DIR/version.txt"

# 后端日志：优先环境变量，其次常见路径
BACKEND_CANDIDATES=(
  "${BACKEND_LOG:-}"
  "/var/log/chengde/backend.log"
  "${REPO_ROOT}/backend-run.log"
  "${REPO_ROOT}/platform-backend/backend-run.log"
)
COPIED_LOG=0
for f in "${BACKEND_CANDIDATES[@]}"; do
  [[ -z "$f" ]] && continue
  if [[ -f "$f" ]]; then
    # 粗略按行尾截取（约按窗口估算：每分钟 ~200 行上限）
    LINES=$(( WINDOW_MIN * 200 ))
    if [[ "$LINES" -lt 500 ]]; then LINES=500; fi
    if [[ "$LINES" -gt 20000 ]]; then LINES=20000; fi
    tail -n "$LINES" "$f" > "$DIR/backend.log" || true
    echo "backend_log_source=$f" >> "$DIR/version.txt"
    COPIED_LOG=1
    break
  fi
done
if [[ "$COPIED_LOG" -eq 0 ]]; then
  echo "未找到后端日志文件。请设置 BACKEND_LOG=/path/to/backend.log 后重跑，或手工复制到 backend.log" > "$DIR/backend.log"
  echo "backend_log_source=MISSING" >> "$DIR/version.txt"
fi

if [[ -f /var/log/nginx/chengde.error.log ]]; then
  tail -n 300 /var/log/nginx/chengde.error.log > "$DIR/nginx-error.log" || true
elif [[ -f /var/log/nginx/error.log ]]; then
  tail -n 300 /var/log/nginx/error.log > "$DIR/nginx-error.log" || true
fi

# 打码 env：去掉常见密码字段值
ENV_SRC="${REPO_ROOT}/local.env"
if [[ -f "$ENV_SRC" ]]; then
  sed -E \
    -e 's/(PASSWORD|SECRET|TOKEN|KEY)=.*/\1=***REDACTED***/I' \
    "$ENV_SRC" > "$DIR/env.redacted.txt"
else
  echo "# local.env not found at $ENV_SRC" > "$DIR/env.redacted.txt"
fi

cat > "$DIR/browser-network.txt" <<'EOF'
# 请从浏览器 F12 → Network 粘贴失败请求：
# Method / URL / Status / Response body（Authorization 打码）
EOF

cat > "$DIR/browser-console.txt" <<'EOF'
# 请从浏览器 F12 → Console 粘贴红色报错原文
EOF

cat > "$DIR/flyway-tail.txt" <<'EOF'
# 可选：在 MySQL 执行后粘贴结果
# SELECT version, description, success, installed_on
# FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 20;
EOF

ZIP="${DIR}.zip"
if command -v zip >/dev/null 2>&1; then
  (cd "$(dirname "$DIR")" && zip -qr "$(basename "$ZIP")" "$(basename "$DIR")")
  echo "已生成: $ZIP"
else
  echo "未安装 zip，已生成目录: $DIR（请手动打包）"
fi

echo ""
echo "下一步："
echo "  1) 编辑 $DIR/META.txt 补全步骤"
echo "  2) 填写 browser-network.txt / browser-console.txt"
echo "  3) 将 zip 或目录发给 Cursor 对话"
