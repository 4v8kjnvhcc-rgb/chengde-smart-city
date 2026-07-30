#!/usr/bin/env bash
# 在 10.10.10.55 上执行：启动门户（需已安装 Docker，且 .51 库已就绪，见 D23）
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

ENV_FILE="$ROOT/compose/prod-app.env"
if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ROOT/compose/prod-app.env.example" "$ENV_FILE"
  echo "已生成 compose/prod-app.env，请填写与 .51 一致的密码后再执行。"
  exit 1
fi
dc -f compose/prod-app.yml --env-file "$ENV_FILE" up -d --build
echo "门户: http://10.10.10.55/"
echo "健康: http://10.10.10.55/actuator/health"
