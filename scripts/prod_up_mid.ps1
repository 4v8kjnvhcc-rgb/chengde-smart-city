# 在 10.10.10.51 上执行：启动库 + 中间件（需已安装 Docker，见 D23 §二）
#   .\scripts\prod_up_mid.ps1
#   .\scripts\prod_up_mid.ps1 -All
param([switch]$All)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Invoke-Compose {
  param([Parameter(ValueFromRemainingArguments = $true)]$Args)
  $null = docker compose version 2>$null
  if ($LASTEXITCODE -eq 0) {
    & docker compose @Args
  } elseif (Get-Command docker-compose -ErrorAction SilentlyContinue) {
    & docker-compose @Args
  } else {
    throw "未找到 docker compose，请先按 D23 §二安装 Docker。"
  }
}

$EnvFile = Join-Path $Root "compose\prod-mid.env"
if (-not (Test-Path $EnvFile)) {
  Copy-Item (Join-Path $Root "compose\prod-mid.env.example") $EnvFile
  Write-Host "已生成 compose/prod-mid.env，请填写密码后再执行。"
  exit 1
}
$profiles = @()
if ($All) {
  $profiles = @("--profile","storage","--profile","governance","--profile","bi","--profile","sched","--profile","etl","--profile","cdc")
}
Invoke-Compose -f compose/prod-mid.yml --env-file $EnvFile @profiles up -d
Write-Host "MySQL/Redis/中间件已在本机启动，供 10.10.10.55 连接。"
