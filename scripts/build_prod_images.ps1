# 按远程分支构建生产前后端镜像并 docker save
# 用法: .\scripts\build_prod_images.ps1 -Branch feature_yxj
# 默认 git fetch 后按 origin/<分支> 构建（不以本机工作区为准）
# 默认输出到仓库根目录 release/
# 紧急无网可用 -LocalOnly
param(
  [Parameter(Mandatory = $true)]
  [string]$Branch,
  [string]$OutDir = "",
  [switch]$LocalOnly
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
if ([string]::IsNullOrWhiteSpace($OutDir)) {
  $OutDir = Join-Path $Root "release"
}

if (-not (Test-Path (Join-Path $Root ".git"))) {
  throw "当前目录不是 Git 仓库: $Root"
}

Write-Host "==> 仓库: $Root"
Write-Host "==> 远程分支: origin/$Branch"

if (-not $LocalOnly) {
  Write-Host "==> git fetch origin $Branch"
  git fetch origin $Branch
  if ($LASTEXITCODE -ne 0) {
    throw "git fetch origin $Branch 失败。请检查网络/权限，或确认远程已有该分支。紧急无网可加 -LocalOnly。"
  }
} else {
  Write-Host "警告: -LocalOnly，不 fetch，使用本地已缓存的 origin/$Branch"
}

$ref = "origin/$Branch"
git rev-parse --verify $ref 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
  throw "找不到远程分支 '$ref'。请先把代码 push 到 origin，或检查分支名。"
}

$sha = (git rev-parse --short $ref).Trim()
$safeBranch = ($Branch -replace '[\\/:*?"<>|]', '_')
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$tagSuffix = "${safeBranch}-${sha}"

# 用独立 worktree，不改动当前工作区
$work = Join-Path $env:TEMP "chengde-img-build_${safeBranch}_${sha}_${stamp}"
if (Test-Path $work) { Remove-Item -Recurse -Force $work }

Write-Host "==> 检出 $ref ($sha) -> $work"
git worktree add --detach $work $ref
if ($LASTEXITCODE -ne 0) { throw "git worktree add 失败" }

$backendTagLocal = "smart-city/platform-backend:local"
$webTagLocal = "smart-city/platform-web:local"
$backendTagVer = "smart-city/platform-backend:$tagSuffix"
$webTagVer = "smart-city/platform-web:$tagSuffix"

try {
  Write-Host "==> 构建后端镜像（较慢，含 Maven）..."
  docker build `
    -t $backendTagLocal `
    -t $backendTagVer `
    -f (Join-Path $work "platform-backend\Dockerfile") `
    (Join-Path $work "platform-backend")
  if ($LASTEXITCODE -ne 0) { throw "backend docker build 失败" }

  Write-Host "==> 构建前端镜像（含 npm build）..."
  docker build `
    -t $webTagLocal `
    -t $webTagVer `
    -f (Join-Path $work "platform-web\Dockerfile") `
    (Join-Path $work "platform-web")
  if ($LASTEXITCODE -ne 0) { throw "web docker build 失败" }

  if (-not (Test-Path $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir | Out-Null
  }
  $outFile = Join-Path $OutDir "chengde-app-images_${safeBranch}_${sha}_${stamp}.tar"
  Write-Host "==> 导出镜像: $outFile"
  docker save -o $outFile $backendTagLocal $webTagLocal $backendTagVer $webTagVer
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path $outFile)) { throw "docker save 失败" }

  $sizeMb = [math]::Round((Get-Item $outFile).Length / 1MB, 2)

  Write-Host "==> 生成/复用生产 env 并打入输出目录"
  & (Join-Path $PSScriptRoot "gen_prod_env.ps1") | Out-Null
  $midEnv = Join-Path $Root "compose\prod-mid.env"
  $appEnv = Join-Path $Root "compose\prod-app.env"
  $sheet = Join-Path $Root "compose\prod-secrets.local.txt"
  if (-not (Test-Path $midEnv) -or -not (Test-Path $appEnv)) {
    throw "缺少 compose/prod-mid.env 或 prod-app.env，请先运行 scripts/gen_prod_env.ps1"
  }
  Copy-Item -Force $midEnv (Join-Path $OutDir "prod-mid.env")
  Copy-Item -Force $appEnv (Join-Path $OutDir "prod-app.env")
  if (Test-Path $sheet) {
    Copy-Item -Force $sheet (Join-Path $OutDir "prod-secrets.local.txt")
  }

  Write-Host ""
  Write-Host "构建完成"
  Write-Host "  分支/提交: $ref ($sha)"
  Write-Host "  镜像标签: $backendTagLocal / $webTagLocal"
  Write-Host "           $backendTagVer / $webTagVer"
  Write-Host "  镜像文件: $outFile ($sizeMb MB)"
  Write-Host "  配置文件: $OutDir\prod-mid.env  （拷到 .51 的 compose/）"
  Write-Host "            $OutDir\prod-app.env  （拷到 .55 的 compose/）"
  Write-Host ""
  Write-Host "现场:"
  Write-Host "  .55: docker load -i $($outFile | Split-Path -Leaf)"
  Write-Host "  .51: 将 prod-mid.env 放到仓库 compose/ 后执行 prod_up_mid"
  Write-Host "  .55: 将 prod-app.env 放到仓库 compose/ 后执行 prod_up_app（可不再改密码）"
}
finally {
  Write-Host "==> 清理临时 worktree"
  Set-Location $Root
  git worktree remove --force $work 2>$null
  if (Test-Path $work) { Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue }
}
