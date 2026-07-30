# 生产离线发包（本机执行，服务器连不上 Git 时用）
# 用法:
#   .\scripts\pack_prod_release.ps1 -Branch feature_yxj
#   .\scripts\pack_prod_release.ps1 -Branch main -OutDir D:\release
#
# 说明:
# - 以 Git 已提交内容为底（git archive），再打入本机 compose/prod-*.env（现场不用再填密码）
# - 未 commit 的代码改动不会进包 → 打包前请先提交
# - 若尚无 prod-*.env，会自动 gen_prod_env.ps1 生成
param(
  [Parameter(Mandatory = $true, HelpMessage = "分支名，如 feature_yxj / main")]
  [string]$Branch,
  [string]$OutDir = $(Join-Path $env:USERPROFILE "Desktop"),
  [switch]$Fetch
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not (Test-Path (Join-Path $Root ".git"))) {
  throw "当前目录不是 Git 仓库: $Root"
}

Write-Host "==> 仓库: $Root"
Write-Host "==> 分支: $Branch"

if ($Fetch) {
  Write-Host "==> git fetch origin $Branch"
  git fetch origin $Branch 2>$null
  if ($LASTEXITCODE -ne 0) {
    Write-Host "警告: fetch 失败（可能无外网），将使用本地已有分支/提交继续。"
  }
}

$ref = $null
git rev-parse --verify $Branch 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
  git rev-parse --verify "origin/$Branch" 2>$null | Out-Null
  if ($LASTEXITCODE -eq 0) {
    $ref = "origin/$Branch"
  }
} else {
  $ref = $Branch
}
if (-not $ref) {
  throw "找不到分支 '$Branch'（本地与 origin 均无）。请先 git fetch 或检查分支名。"
}

$sha = (git rev-parse --short $ref).Trim()
$safeBranch = ($Branch -replace '[\\/:*?"<>|]', '_')
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$pkgName = "chengde-smart-city_${safeBranch}_${sha}_${stamp}.tar.gz"
if (-not (Test-Path $OutDir)) {
  New-Item -ItemType Directory -Path $OutDir | Out-Null
}
$outFile = Join-Path $OutDir $pkgName

Write-Host "==> 确保生产 env 已生成"
& (Join-Path $PSScriptRoot "gen_prod_env.ps1") | Out-Null
$midEnv = Join-Path $Root "compose\prod-mid.env"
$appEnv = Join-Path $Root "compose\prod-app.env"
if (-not (Test-Path $midEnv) -or -not (Test-Path $appEnv)) {
  throw "缺少 compose/prod-mid.env 或 prod-app.env"
}

$stage = Join-Path $env:TEMP "chengde-pack_${safeBranch}_${sha}_${stamp}"
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Path $stage | Out-Null

try {
  Write-Host "==> 导出 $ref ($sha) 并打入 env"
  $tarPlain = Join-Path $stage "src.tar"
  git archive --format=tar --prefix=chengde-smart-city/ --output="$tarPlain" $ref
  if ($LASTEXITCODE -ne 0) { throw "git archive 失败" }

  Push-Location $stage
  try {
    tar -xf $tarPlain
    if ($LASTEXITCODE -ne 0) { throw "tar 解包失败（需 Git 自带 tar）" }
    $composeDir = Join-Path $stage "chengde-smart-city\compose"
    if (-not (Test-Path $composeDir)) {
      throw "归档内缺少 compose/，请确认分支已包含生产 Compose 文件"
    }
    Copy-Item -Force $midEnv (Join-Path $composeDir "prod-mid.env")
    Copy-Item -Force $appEnv (Join-Path $composeDir "prod-app.env")
    Remove-Item $tarPlain -Force

    Write-Host "==> 压缩: $outFile"
    # 优先 tar.gz；失败则 zip
    tar -czf $outFile chengde-smart-city 2>$null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $outFile)) {
      $zipFile = Join-Path $OutDir ($pkgName -replace '\.tar\.gz$', '.zip')
      if (Test-Path $zipFile) { Remove-Item $zipFile -Force }
      Compress-Archive -Path (Join-Path $stage "chengde-smart-city") -DestinationPath $zipFile -Force
      $outFile = $zipFile
      $pkgName = Split-Path $zipFile -Leaf
    }
  } finally {
    Pop-Location
  }
} finally {
  if (Test-Path $stage) { Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue }
}

# 额外在输出目录放一份裸 env，方便单独拷
Copy-Item -Force $midEnv (Join-Path $OutDir "prod-mid.env")
Copy-Item -Force $appEnv (Join-Path $OutDir "prod-app.env")
$sheet = Join-Path $Root "compose\prod-secrets.local.txt"
if (Test-Path $sheet) {
  Copy-Item -Force $sheet (Join-Path $OutDir "prod-secrets.local.txt")
}

$sizeMb = [math]::Round((Get-Item $outFile).Length / 1MB, 2)
Write-Host ""
Write-Host "打包完成: $outFile ($sizeMb MB)"
Write-Host "提交: $sha @ $ref"
Write-Host "已打入: compose/prod-mid.env + compose/prod-app.env"
Write-Host "同目录另存: prod-mid.env / prod-app.env / prod-secrets.local.txt"
Write-Host ""
Write-Host "现场:"
Write-Host "  解压后 .51 / .55 可直接 prod_up_*（无需再 vi 改密码）"
Write-Host "  口令备忘见桌面 prod-secrets.local.txt（勿上传公共盘）"
