#!/usr/bin/env bash
# 生产离线发包
# 用法: ./scripts/pack_prod_release.sh feature_yxj [输出目录]
# 仅包含已提交内容；打包前请先 commit
set -euo pipefail
BRANCH="${1:-}"
OUT_DIR="${2:-$HOME/Desktop}"
if [[ -z "$BRANCH" ]]; then
  echo "用法: $0 <分支名> [输出目录]"
  exit 1
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -d .git ]]; then
  echo "不是 Git 仓库: $ROOT"
  exit 1
fi

REF=""
if git rev-parse --verify "$BRANCH" >/dev/null 2>&1; then
  REF="$BRANCH"
elif git rev-parse --verify "origin/$BRANCH" >/dev/null 2>&1; then
  REF="origin/$BRANCH"
else
  echo "找不到分支: $BRANCH"
  exit 1
fi

SHA="$(git rev-parse --short "$REF")"
SAFE_BRANCH="$(echo "$BRANCH" | tr '/\\' '__')"
STAMP="$(date +%Y%m%d-%H%M)"
PKG="chengde-smart-city_${SAFE_BRANCH}_${SHA}_${STAMP}.tar.gz"
mkdir -p "$OUT_DIR"
OUT_FILE="$OUT_DIR/$PKG"

echo "==> 打包 $REF ($SHA) -> $OUT_FILE"
git archive --format=tar.gz --prefix=chengde-smart-city/ --output="$OUT_FILE" "$REF"

echo "完成: $OUT_FILE"
echo "拷到服务器后: tar -xzf $PKG -C /opt/chengde && ls /opt/chengde/chengde-smart-city/compose"
