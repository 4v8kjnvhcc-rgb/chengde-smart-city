# 按 profile 分波启动开源联调栈（某 profile 失败不阻断其余）
param(
    [string[]]$Profile = @('storage', 'governance', 'bi', 'sched', 'etl', 'cdc'),
    [switch]$All
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($All) {
    $Profile = @('storage', 'governance', 'bi', 'sched', 'etl', 'cdc')
}

$compose = 'compose/oss-stack.yml'
$failed = 0
foreach ($p in $Profile) {
    Write-Host ''
    Write-Host "==== profile: $p ===="
    $args = @('compose', '-f', $compose, '--profile', $p, 'up', '-d')
    & docker @args
    if ($LASTEXITCODE -ne 0) {
        $failed++
        Write-Host "WARN: profile $p failed (exit=$LASTEXITCODE)"
    }
}

Write-Host ''
if ($failed -gt 0) {
    Write-Host "Finished with $failed profile(s) failed. Run: powershell -File scripts\oss_health.ps1"
    exit 1
}
Write-Host 'All profiles started. Run: powershell -File scripts\oss_health.ps1'
