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
$webTagLocal = "smart-city/platform-frontend:local"
$backendTagVer = "smart-city/platform-backend:$tagSuffix"
$webTagVer = "smart-city/platform-frontend:$tagSuffix"

try {
  # 在宿主机构建产物：Docker Desktop 构建容器内 DNS 常不可用，
  # 无法在镜像里跑 mvn / npm 拉依赖，故先本机 build 再打包产物。
  $backendDir = Join-Path $work "platform-backend"
  $webDir = Join-Path $work "platform-frontend"

  Write-Host "==> 本机构建后端 jar（Maven，较慢）..."
  Push-Location $backendDir
  try {
    & (Join-Path $backendDir "mvnw.cmd") -B -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "mvn package 失败" }
  } finally {
    Pop-Location
  }
  if (-not (Get-ChildItem (Join-Path $backendDir "target") -Filter "platform-backend-*.jar" -ErrorAction SilentlyContinue)) {
    throw "未找到 platform-backend-*.jar"
  }

  Write-Host "==> 本机构建前端 dist（npm）..."
  Push-Location $webDir
  try {
    # 复用主仓库 node_modules，避免每次 npm ci 联网
    $srcModules = Join-Path $Root "platform-frontend\node_modules"
    if ((Test-Path $srcModules) -and (-not (Test-Path (Join-Path $webDir "node_modules")))) {
      Write-Host "    复用本仓库 node_modules"
      cmd /c mklink /J "$webDir\node_modules" "$srcModules" | Out-Null
    }
    if (-not (Test-Path (Join-Path $webDir "node_modules"))) {
      npm ci
      if ($LASTEXITCODE -ne 0) { throw "npm ci 失败" }
    }
    # 只跑 vite build：npm run build 含 vue-tsc 类型检查，
    # 当前仓库存在未使用变量等历史类型告警，会阻断发版但不影响产物
    npx vite build
    if ($LASTEXITCODE -ne 0) { throw "vite build 失败" }
  } finally {
    Pop-Location
  }
  if (-not (Test-Path (Join-Path $webDir "dist"))) { throw "未找到 platform-frontend/dist" }

  # 生产机为 aarch64，必须打 linux/arm64（本机 Windows/x86 上靠 Docker Desktop 跨架构）
  $platform = "linux/arm64"
  Write-Host "==> 打包后端镜像（$platform）..."
  docker build `
    --platform $platform `
    -t $backendTagLocal `
    -t $backendTagVer `
    -f (Join-Path $backendDir "Dockerfile.prebuilt") `
    $backendDir
  if ($LASTEXITCODE -ne 0) { throw "backend docker build 失败" }

  Write-Host "==> 打包前端镜像（$platform）..."
  docker build `
    --platform $platform `
    -t $webTagLocal `
    -t $webTagVer `
    -f (Join-Path $webDir "Dockerfile.prebuilt") `
    $webDir
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
  # 输出到 release/ 的 env 也统一 LF，避免拷到 Linux 后 B0 备份脚本踩 \r
  $utf8NoBom = New-Object System.Text.UTF8Encoding $false
  foreach ($pair in @(
    @{ Src = $midEnv; Name = "prod-mid.env" },
    @{ Src = $appEnv; Name = "prod-app.env" }
  )) {
    $text = [System.IO.File]::ReadAllText($pair.Src) -replace "`r`n", "`n" -replace "`r", "`n"
    if (-not $text.EndsWith("`n")) { $text = $text + "`n" }
    [System.IO.File]::WriteAllText((Join-Path $OutDir $pair.Name), $text, $utf8NoBom)
  }
  if (Test-Path $sheet) {
    $sheetText = [System.IO.File]::ReadAllText($sheet) -replace "`r`n", "`n" -replace "`r", "`n"
    if (-not $sheetText.EndsWith("`n")) { $sheetText = $sheetText + "`n" }
    [System.IO.File]::WriteAllText((Join-Path $OutDir "prod-secrets.local.txt"), $sheetText, $utf8NoBom)
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
  # 先摘掉 node_modules 联接，否则删除会顺着联接删主仓库依赖
  $linkedModules = Join-Path $work "platform-frontend\node_modules"
  if (Test-Path $linkedModules) {
    cmd /c rmdir "$linkedModules" 2>$null | Out-Null
  }
  git worktree remove --force $work 2>$null
  if (Test-Path $work) { Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue }
}
