#!/usr/bin/env bash
# 生产离线发包：git fetch 后按 origin/<分支> 打包（只含 compose/ + scripts/prod_up_*）
# 用法: ./scripts/pack_prod_release.sh feature_yxj [输出目录]
# 默认输出到仓库 release/
set -euo pipefail
BRANCH="${1:-}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${2:-$ROOT/release}"
if [[ -z "$BRANCH" ]]; then
  echo "用法: $0 <远程分支名> [输出目录]"
  exit 1
fi
cd "$ROOT"

if [[ ! -d .git ]]; then
  echo "不是 Git 仓库: $ROOT"
  exit 1
fi

echo "==> git fetch origin $BRANCH"
git fetch origin "$BRANCH"

REF="origin/$BRANCH"
if ! git rev-parse --verify "$REF" >/dev/null 2>&1; then
  echo "找不到远程分支: $REF（请先 push）"
  exit 1
fi

SHA="$(git rev-parse --short "$REF")"
SAFE_BRANCH="$(echo "$BRANCH" | tr '/\\' '__')"
STAMP="$(date +%Y%m%d-%H%M)"
PKG="chengde-smart-city_${SAFE_BRANCH}_${SHA}_${STAMP}.tar.gz"
mkdir -p "$OUT_DIR"
OUT_FILE="$OUT_DIR/$PKG"

PROD_PATHS=(
  compose
  scripts/prod_up_mid.sh
  scripts/prod_up_app.sh
  scripts/prod_up_mid.ps1
  scripts/prod_up_app.ps1
)

echo "==> 打包 $REF ($SHA) -> $OUT_FILE"
git archive --format=tar.gz --prefix=chengde-smart-city/ --output="$OUT_FILE" "$REF" "${PROD_PATHS[@]}"

echo "完成: $OUT_FILE"
echo "来源: $REF（非本地工作区）"
echo "范围: compose/ + scripts/prod_up_*"
echo "拷到服务器后: tar -xzf $PKG -C /opt/chengde && ls /opt/chengde/chengde-smart-city/compose"
