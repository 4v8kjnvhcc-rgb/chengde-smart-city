# C resource center smoke: manage -> pretest -> backup -> verify -> monitor
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api/v1'
$Pass = 'Test@12345'

function Login() {
    $body = @{ username = 'sys_admin'; password = $Pass } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $body -ContentType 'application/json; charset=utf-8'
    return $r.data.accessToken
}
function GetApi($path, $token) {
    return Invoke-RestMethod -Uri "$base$path" -Headers @{ Authorization = "Bearer $token" }
}
function PostApi($path, $token, $payload = @{}) {
    $json = $payload | ConvertTo-Json -Depth 8 -Compress
    $r = Invoke-RestMethod -Uri "$base$path" -Method Post -Headers @{ Authorization = "Bearer $token" } `
        -Body $json -ContentType 'application/json'
    if ($null -ne $r.code -and [int]$r.code -ne 0) {
        throw $r.message
    }
    return $r
}

$token = Login
if (-not $token) { throw 'login failed' }

$themes = (GetApi '/resource-center/platform/themes' $token).data
$enterpriseTheme = @($themes | Where-Object { $_.themeCode -eq 'THEME_ENTERPRISE' })[0]
if (-not $enterpriseTheme) { throw 'THEME_ENTERPRISE missing' }

$managed = (GetApi '/resource-center/platform/managed-tables' $token).data
$entManaged = @($managed | Where-Object { $_.physicalTable -eq 'dws_enterprise_theme' })[0]
if (-not $entManaged) {
    $mid = (PostApi '/resource-center/platform/managed-tables' $token @{
        themeId = $enterpriseTheme.id
        physicalTable = 'dws_enterprise_theme'
        metaEntryCode = 'TBL_FUS_DWS_ENTERPRISE_THEME'
    }).data
    $entManaged = @{ id = $mid; physicalTable = 'dws_enterprise_theme' }
}
Write-Host "[PASS] managed enterprise table id=$($entManaged.id)"

$parts = (GetApi '/resource-center/platform/partition/overview' $token).data.partitions
$part = @($parts | Where-Object { $_.tableName -eq 'dws_enterprise_theme' })[0]
if (-not $part) {
    $pid = (PostApi '/resource-center/platform/partitions' $token @{
        partitionName = '企业主题范围分区'
        partitionType = 'RANGE'
        themeId = $enterpriseTheme.id
        tableName = 'dws_enterprise_theme'
        partitionColumn = 'district_code'
        expressionText = 'RANGE COLUMNS(district_code)'
    }).data
    $part = @{ id = $pid }
}
$pretest = (PostApi "/resource-center/platform/partitions/$($part.id)/pretest" $token).data
if ($pretest.executed -ne $false) { throw 'partition pretest must not execute DDL' }
if ($pretest.pretestStatus -notin @('READY', 'BLOCKED')) { throw "unexpected pretest status $($pretest.pretestStatus)" }
Write-Host "[PASS] partition pretest status=$($pretest.pretestStatus)"

$backup = (PostApi "/resource-center/platform/managed-tables/$($entManaged.id)/backup" $token @{ retentionDays = 30 }).data
if (-not $backup.sha256 -or [long]$backup.rowCount -lt 1) {
    throw "backup failed: $($backup | ConvertTo-Json -Compress)"
}
$verify = (GetApi "/resource-center/platform/backups/artifacts/$($backup.artifactId)/verify" $token).data
if ($verify.match -ne $true) { throw 'backup sha256 verify failed' }
Write-Host "[PASS] backup rows=$($backup.rowCount) sha256 ok"

$refresh = (PostApi '/resource-center/platform/monitor/refresh' $token).data
if ([int]$refresh.managedTables -lt 1) { throw 'monitor refresh missing managed tables' }
Write-Host "[PASS] monitor managedTables=$($refresh.managedTables) totalRows=$($refresh.totalRows)"
Write-Host '[PASS] resource center smoke complete'
