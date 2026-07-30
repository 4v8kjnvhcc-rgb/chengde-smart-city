# 首次把 compose/prod-*.env、prod-secrets.local.txt 提交进仓库之后执行本脚本。
# 作用：对这些文件设置 skip-worktree，本地再改密码不会出现在 git status，也不会被误提交。
# 若以后必须把仓库里的 env 更新一版：先 unskip，再改、再 commit，再 skip。
#
#   .\scripts\skip_prod_env_after_commit.ps1
#   .\scripts\skip_prod_env_after_commit.ps1 -Unskip

param([switch]$Unskip)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$files = @(
  "compose/prod-mid.env",
  "compose/prod-app.env",
  "compose/prod-secrets.local.txt"
)

foreach ($f in $files) {
  if (-not (Test-Path $f)) {
    Write-Host "skip (missing): $f"
    continue
  }
  git ls-files --error-unmatch $f 2>$null | Out-Null
  if ($LASTEXITCODE -ne 0) {
    Write-Host "not in git yet: $f  （请先 git add 并完成首次 commit）"
    continue
  }
  if ($Unskip) {
    git update-index --no-skip-worktree -- $f
    Write-Host "unskip: $f"
  } else {
    git update-index --skip-worktree -- $f
    Write-Host "skip-worktree: $f"
  }
}

Write-Host ""
if ($Unskip) {
  Write-Host "已取消跳过。改完并 commit 后请再执行本脚本（不加 -Unskip）。"
} else {
  Write-Host "完成。之后本地改这些 env 不会进提交；队友新 clone 后也请跑一次本脚本。"
}
