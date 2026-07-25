# 采集测试/本机故障诊断包，供发给 Cursor / 开发排障。
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\collect_diag.ps1 -Message "问题简述" [-WindowMinutes 30] [-OutDir .]
#   $env:BACKEND_LOG = "E:\logs\backend.log"

param(
    [Parameter(Mandatory = $true)][string]$Message,
    [int]$WindowMinutes = 30,
    [string]$OutDir = "."
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$Stamp = Get-Date -Format "yyyyMMdd-HHmm"
$Dir = Join-Path (Resolve-Path $OutDir) "diag-$Stamp"
New-Item -ItemType Directory -Path $Dir -Force | Out-Null

$meta = @"
时间：$(Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz")
环境：请填写（测试/本机） URL=
账号角色：
菜单/页面：
操作步骤：
期望：
实际：$Message
是否必现：
备注：请补全本文件后连同 zip 发给开发
"@
Set-Content -Path (Join-Path $Dir "META.txt") -Value $meta -Encoding UTF8

$commit = "unknown"
$branch = "unknown"
$short = "unknown"
Push-Location $RepoRoot
try {
    $commit = (git rev-parse HEAD 2>$null)
    $short = (git rev-parse --short HEAD 2>$null)
    $branch = (git rev-parse --abbrev-ref HEAD 2>$null)
    $porcelain = (git status --porcelain | Select-Object -First 40) -join "`n"
} catch {
    $porcelain = ""
} finally {
    Pop-Location
}

$version = @"
repo=$RepoRoot
commit=$commit
commit_short=$short
branch=$branch
collected_at=$(Get-Date -Format o)
window_minutes=$WindowMinutes
status_porcelain<<EOF
$porcelain
EOF
"@
Set-Content -Path (Join-Path $Dir "version.txt") -Value $version -Encoding UTF8

$candidates = @()
if ($env:BACKEND_LOG) { $candidates += $env:BACKEND_LOG }
$candidates += @(
    (Join-Path $RepoRoot "backend-run.log"),
    (Join-Path $RepoRoot "backend-run.err.log"),
    (Join-Path $RepoRoot "platform-backend\backend-run.log"),
    "C:\var\log\chengde\backend.log"
)

$lines = [Math]::Min(20000, [Math]::Max(500, $WindowMinutes * 200))
$copied = $false
foreach ($f in $candidates) {
    if (-not $f) { continue }
    if (Test-Path -LiteralPath $f) {
        Get-Content -LiteralPath $f -Tail $lines -ErrorAction SilentlyContinue |
            Set-Content -Path (Join-Path $Dir "backend.log") -Encoding UTF8
        Add-Content -Path (Join-Path $Dir "version.txt") -Value "backend_log_source=$f"
        $copied = $true
        break
    }
}
if (-not $copied) {
    Set-Content -Path (Join-Path $Dir "backend.log") -Encoding UTF8 -Value @"
未找到后端日志。请设置 `$env:BACKEND_LOG = '路径'` 后重跑，或手工复制到 backend.log。
若使用 ``mvnw spring-boot:run``，可把终端输出重定向到 backend-run.log。
"@
    Add-Content -Path (Join-Path $Dir "version.txt") -Value "backend_log_source=MISSING"
}

$envSrc = Join-Path $RepoRoot "local.env"
if (Test-Path $envSrc) {
    Get-Content $envSrc | ForEach-Object {
        if ($_ -match '(?i)(PASSWORD|SECRET|TOKEN|KEY)=') {
            $_ -replace '=.*$', '=***REDACTED***'
        } else {
            $_
        }
    } | Set-Content (Join-Path $Dir "env.redacted.txt") -Encoding UTF8
} else {
    Set-Content (Join-Path $Dir "env.redacted.txt") -Encoding UTF8 -Value "# local.env not found"
}

Set-Content (Join-Path $Dir "browser-network.txt") -Encoding UTF8 -Value @"
# 请从浏览器 F12 → Network 粘贴失败请求：
# Method / URL / Status / Response body（Authorization 打码）
"@

Set-Content (Join-Path $Dir "browser-console.txt") -Encoding UTF8 -Value @"
# 请从浏览器 F12 → Console 粘贴红色报错原文
"@

Set-Content (Join-Path $Dir "flyway-tail.txt") -Encoding UTF8 -Value @"
# 可选：在 MySQL 执行后粘贴结果
# SELECT version, description, success, installed_on
# FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 20;
"@

$zip = "$Dir.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path $Dir -DestinationPath $zip -Force

Write-Host "已生成: $zip"
Write-Host ""
Write-Host "下一步："
Write-Host "  1) 编辑 $Dir\META.txt 补全步骤"
Write-Host "  2) 填写 browser-network.txt / browser-console.txt"
Write-Host "  3) 将 zip 或目录发给 Cursor 对话"
