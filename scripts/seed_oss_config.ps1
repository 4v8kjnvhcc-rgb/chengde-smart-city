# 开源组件配置级样例导入（不改源码，在各组件 UI/API 内配置）
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host '=== Seed OSS config (manual + portal API) ==='
Write-Host ''
Write-Host '1. OpenMetadata (http://localhost:8585)'
Write-Host '   - Login: admin@open-metadata.org / admin'
Write-Host '   - Add Database Service: smart_city @ host.docker.internal:3306'
Write-Host '   - Run metadata ingestion for smart_city'
Write-Host ''
Write-Host '2. DataEase (http://localhost:8100)'
Write-Host '   - Login: admin / DataEase@123456'
Write-Host '   - Create dashboards for domains: bi, population, legal, macro, key'
Write-Host '   - Map dashboard IDs to ana_analysis_model.de_dashboard_id (Flyway V7 seeds)'
Write-Host ''
Write-Host '3. DolphinScheduler (http://localhost:12345/dolphinscheduler)'
Write-Host '   - Login: admin / dolphinscheduler123'
Write-Host '   - Import workflows: DS_WF_DAILY_ETL, DS_WF_QUALITY'
Write-Host ''
Write-Host '4. Kettle Carte (http://localhost:8081)'
Write-Host '   - Deploy sample job KTR_M215_DEMO to Carte repository'
Write-Host ''
Write-Host '5. Canal + MongoDB'
Write-Host '   - Ensure MySQL binlog enabled (see D13 §五)'
Write-Host '   - Canal instance points to host.docker.internal:3306'
Write-Host '   - CDC events land in MongoDB smartcity_cdc'
Write-Host ''

if (Test-Path local.env) {
    Get-Content local.env | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
        }
    }
}

$base = 'http://localhost:8080/api/v1'
try {
    $login = @{ username = 'sys_admin'; password = 'Test@12345' } | ConvertTo-Json -Compress
    $tok = (Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $login -ContentType 'application/json').data.accessToken
    $h = @{ Authorization = "Bearer $tok" }
    $health = Invoke-RestMethod -Uri "$base/integration/health" -Headers $h
    Write-Host 'Portal integration health:'
    $health.data | Format-List
} catch {
    Write-Host "Portal not running or login failed: $($_.Exception.Message)"
}

Write-Host ''
Write-Host 'Seed script done. Complete component UI steps above for full acceptance demo.'
