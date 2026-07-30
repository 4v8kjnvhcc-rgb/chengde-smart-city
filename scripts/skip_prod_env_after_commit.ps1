# After first commit of compose/prod-*.env, run this so local password edits stay out of git status.
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
    Write-Host "not in git yet: $f (git add + commit first)"
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
  Write-Host "Done. After editing and committing, run this script again without -Unskip."
} else {
  Write-Host "Done. Local env edits will not appear in git status. Teammates should run this after clone."
}
