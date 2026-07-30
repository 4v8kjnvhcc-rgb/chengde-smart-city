# 在 10.10.10.55 上执行：启动门户（需已安装 Docker，且 .51 库已就绪，见 D23）
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

$EnvFile = Join-Path $Root "compose\prod-app.env"
if (-not (Test-Path $EnvFile)) {
  Copy-Item (Join-Path $Root "compose\prod-app.env.example") $EnvFile
  Write-Host "已生成 compose/prod-app.env，请填写与 .51 一致的密码后再执行。"
  exit 1
}
Invoke-Compose -f compose/prod-app.yml --env-file $EnvFile up -d --build
Write-Host "门户: http://10.10.10.55/"
Write-Host "健康: http://10.10.10.55/actuator/health"
