#!/usr/bin/env bash
# 在 10.10.10.51 上执行：启动库 + 中间件（需已安装 Docker，见 D23 §二）
#   ./scripts/prod_up_mid.sh
#   ./scripts/prod_up_mid.sh --all
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

dc() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    echo "未找到 docker compose，请先按 D23 §二安装 Docker。"
    exit 1
  fi
}

ENV_FILE="$ROOT/compose/prod-mid.env"
if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ROOT/compose/prod-mid.env.example" "$ENV_FILE"
  echo "已生成 compose/prod-mid.env，请填写密码后再执行。"
  exit 1
fi
# Windows 打包可能带 CRLF；去掉 \r，避免 docker compose 读坏密码
if grep -q $'\r' "$ENV_FILE" 2>/dev/null; then
  sed -i 's/\r$//' "$ENV_FILE"
  echo "已规范化 compose/prod-mid.env 换行（去掉 CRLF）"
fi
PROFILES=()
if [[ "${1:-}" == "--all" ]]; then
  PROFILES=(--profile storage --profile governance --profile bi --profile sched --profile etl --profile cdc)
fi
dc -f compose/prod-mid.yml --env-file "$ENV_FILE" "${PROFILES[@]}" up -d
echo "MySQL: 本机端口 ${MYSQL_PUBLISH_PORT:-13306}；Redis: 6379（供 10.10.10.55 连接）"
