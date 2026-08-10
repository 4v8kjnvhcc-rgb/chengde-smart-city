# 生产离线发包（本机执行，服务器连不上 Git 时用）
# 用法:
#   .\scripts\pack_prod_release.ps1 -Branch feature_yxj
#   .\scripts\pack_prod_release.ps1 -Branch main -OutDir D:\elsewhere
#
# 说明:
# - 默认 git fetch 后按 origin/<分支> 打包（不以本机工作区 / 本地分支为准）
# - 默认输出到仓库根目录 release/
# - 只打生产路径：compose/ + scripts/prod_up_*（不含 docs/catalog/源码）
# - 本机 compose/prod-*.env 仍追加进包（密码不在远程时用本机已生成文件）
# - 紧急无网可用 -LocalOnly（改用本地 origin/<分支> 缓存，不推荐）
param(
  [Parameter(Mandatory = $true, HelpMessage = "远程分支名，如 feature_yxj / main")]
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
$pkgName = "chengde-smart-city_${safeBranch}_${sha}_${stamp}.tar.gz"
if (-not (Test-Path $OutDir)) {
  New-Item -ItemType Directory -Path $OutDir | Out-Null
}
$outFile = Join-Path $OutDir $pkgName

# 生产现场实际用到的路径（不含 docs / catalog / 源码 / IDE 配置）
$prodPaths = @(
  "compose",
  "scripts/prod_up_mid.sh",
  "scripts/prod_up_app.sh",
  "scripts/prod_up_mid.ps1",
  "scripts/prod_up_app.ps1"
)

Write-Host "==> 确保生产 env 已生成（本机文件，追加进包）"
& (Join-Path $PSScriptRoot "gen_prod_env.ps1") | Out-Null
$midEnv = Join-Path $Root "compose\prod-mid.env"
$appEnv = Join-Path $Root "compose\prod-app.env"
if (-not (Test-Path $midEnv) -or -not (Test-Path $appEnv)) {
  throw "缺少 compose/prod-mid.env 或 compose/prod-app.env"
}

$stage = Join-Path $env:TEMP "chengde-pack_${safeBranch}_${sha}_${stamp}"
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Path $stage | Out-Null

try {
  Write-Host "==> 导出 $ref ($sha) 生产路径: $($prodPaths -join ', ')"
  $tarPlain = Join-Path $stage "src.tar"
  $archiveArgs = @("archive", "--format=tar", "--prefix=chengde-smart-city/", "--output=$tarPlain", $ref) + $prodPaths
  & git @archiveArgs
  if ($LASTEXITCODE -ne 0) { throw "git archive 失败（请确认分支已包含 compose/ 与 prod_up_*）" }

  # 解包 → 把 .sh 转成 Unix 换行（LF），避免 Linux 上 env: bash\r 报错
  Write-Host "==> 规范化 shell 脚本换行（LF）"
  Push-Location $stage
  try {
    tar -xf src.tar
    if ($LASTEXITCODE -ne 0) { throw "tar 解包失败" }
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    Get-ChildItem -Path (Join-Path $stage "chengde-smart-city\scripts") -Filter "*.sh" -File -ErrorAction SilentlyContinue | ForEach-Object {
      $text = [System.IO.File]::ReadAllText($_.FullName) -replace "`r`n", "`n" -replace "`r", "`n"
      [System.IO.File]::WriteAllText($_.FullName, $text, $utf8NoBom)
    }
    Remove-Item $tarPlain -Force
    tar -cf src.tar chengde-smart-city
    if ($LASTEXITCODE -ne 0) { throw "tar 重打包失败" }
  } finally {
    Pop-Location
  }

  # 打入本机 env（强制 LF，避免现场 grep/mysqldump 读到 \r）
  Write-Host "==> 打入本机 compose/prod-*.env（规范化为 LF）"
  $composeDir = Join-Path $stage "chengde-smart-city\compose"
  New-Item -ItemType Directory -Force -Path $composeDir | Out-Null
  $utf8NoBom = New-Object System.Text.UTF8Encoding $false
  foreach ($pair in @(
    @{ Src = $midEnv; Name = "prod-mid.env" },
    @{ Src = $appEnv; Name = "prod-app.env" }
  )) {
    $dest = Join-Path $composeDir $pair.Name
    $text = [System.IO.File]::ReadAllText($pair.Src) -replace "`r`n", "`n" -replace "`r", "`n"
    if (-not $text.EndsWith("`n")) { $text = $text + "`n" }
    [System.IO.File]::WriteAllText($dest, $text, $utf8NoBom)
  }

  Push-Location $stage
  try {
    tar -rf src.tar chengde-smart-city/compose/prod-mid.env chengde-smart-city/compose/prod-app.env
    if ($LASTEXITCODE -ne 0) { throw "tar 追加 env 失败" }
  } finally {
    Pop-Location
  }

  Write-Host "==> 压缩: $outFile"
  $inStream = [System.IO.File]::OpenRead($tarPlain)
  try {
    $outStream = [System.IO.File]::Create($outFile)
    try {
      $gzStream = New-Object System.IO.Compression.GZipStream($outStream, [System.IO.Compression.CompressionMode]::Compress)
      try {
        $inStream.CopyTo($gzStream)
      } finally {
        $gzStream.Dispose()
      }
    } finally {
      $outStream.Dispose()
    }
  } finally {
    $inStream.Dispose()
  }
  if (-not (Test-Path $outFile)) { throw "gzip 压缩失败: $outFile" }
} finally {
  if (Test-Path $stage) { Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue }
}

$utf8NoBomOut = New-Object System.Text.UTF8Encoding $false
foreach ($pair in @(
  @{ Src = $midEnv; Name = "prod-mid.env" },
  @{ Src = $appEnv; Name = "prod-app.env" }
)) {
  $text = [System.IO.File]::ReadAllText($pair.Src) -replace "`r`n", "`n" -replace "`r", "`n"
  if (-not $text.EndsWith("`n")) { $text = $text + "`n" }
  [System.IO.File]::WriteAllText((Join-Path $OutDir $pair.Name), $text, $utf8NoBomOut)
}
$sheet = Join-Path $Root "compose\prod-secrets.local.txt"
if (Test-Path $sheet) {
  $sheetText = [System.IO.File]::ReadAllText($sheet) -replace "`r`n", "`n" -replace "`r", "`n"
  if (-not $sheetText.EndsWith("`n")) { $sheetText = $sheetText + "`n" }
  [System.IO.File]::WriteAllText((Join-Path $OutDir "prod-secrets.local.txt"), $sheetText, $utf8NoBomOut)
}

$sizeMb = [math]::Round((Get-Item $outFile).Length / 1MB, 2)
Write-Host ""
Write-Host "打包完成: $outFile ($sizeMb MB)"
Write-Host "来源: $ref ($sha)"
Write-Host "范围: compose/ + scripts/prod_up_* （不含 docs/源码/catalog）"
Write-Host "已打入: compose/prod-mid.env + compose/prod-app.env（本机）"
Write-Host "同目录另存: prod-mid.env / prod-app.env / prod-secrets.local.txt"
Write-Host ""
Write-Host "现场:"
Write-Host "  解压后 .51 / .55 可直接 prod_up_*；应用镜像另用 docker load"
Write-Host "  口令备忘见 release/prod-secrets.local.txt（勿上传公共盘）"
