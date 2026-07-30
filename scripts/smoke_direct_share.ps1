# P0 direct-share golden-path smoke test (ASCII for Windows PowerShell 5)
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
        -Body $json -ContentType 'application/json; charset=utf-8'
    if ($null -ne $r.code -and [int]$r.code -ne 0) {
        throw "API $path failed code=$($r.code) message=$($r.message)"
    }
    return $r
}

function PostApiNoBody($path, $token) {
    $r = Invoke-RestMethod -Uri "$base$path" -Method Post -Headers @{ Authorization = "Bearer $token" } `
        -ContentType 'application/json; charset=utf-8'
    if ($null -ne $r.code -and [int]$r.code -ne 0) {
        throw "API $path failed code=$($r.code) message=$($r.message)"
    }
    return $r
}

$token = Login
if (-not $token -or $token.Length -lt 20) {
    throw 'Login did not return an access token'
}
(GetApi '/governance/platform/quality/overview' $token) | Out-Null

$ts = Get-Date -Format 'yyyyMMdd_HHmmss'
$prjId = (PostApi '/exchange/ingestion/projects' $token @{
    projectName = "smoke_direct_$ts"
    systemName = 'SMOKE'
}).data

$dsId = (PostApi '/exchange/ingestion/data-sources' $token @{
    projectId = $prjId
    sourceName = 'source-mysql'
    sourceCode = "DS_SMOKE_SOURCE_$ts"
    sourceType = 'MYSQL'
    host = 'localhost'
    port = 3308
    database = 'biz_source'
    username = 'probe'
    password = 'probe_pass'
}).data

$test = (PostApi "/exchange/ingestion/data-sources/$dsId/test" $token).data
if ($test.connStatus -ne 'OK') {
    throw "Source connection test failed: $($test | ConvertTo-Json -Compress)"
}
Write-Host "[PASS] datasource test ok sourceId=$dsId tables=$($test.tableCount)"

$tableCode = "TBL_SMOKE_ENT_$ts"
$register = (PostApi "/exchange/ingestion/data-sources/$dsId/register-tables" $token @{
    tables = @(
        @{
            sourceTable = 'ent_master'
            tableCode = $tableCode
            tableName = 'enterprise master'
        }
    )
}).data

$enterpriseTableId = ($register.registered | Where-Object { $_.sourceTable -eq 'ent_master' } | Select-Object -First 1).tableId
if (-not $enterpriseTableId) {
    throw "Register did not return ent_master: $($register | ConvertTo-Json -Compress)"
}
Write-Host "[PASS] registered tableId=$enterpriseTableId"

$collect = (PostApiNoBody "/exchange/ingestion/register/tables/$enterpriseTableId/collect" $token).data
if ($collect.status -ne 'SUCCESS') {
    throw "Collect failed: $($collect | ConvertTo-Json -Depth 6 -Compress)"
}
Write-Host "[PASS] collected enterprise tableId=$enterpriseTableId ods=$($collect.odsTable) rows=$($collect.collectedRows)"

$eligible = (GetApi '/governance/direct-share/eligible-tables' $token).data
$picked = ($eligible | Where-Object { $_.physicalTableName -eq 'ods_enterprise_base' } | Select-Object -First 1)
if (-not $picked) {
    throw "No eligible enterprise table found after collect. eligibleCount=$(@($eligible).Count)"
}
$tableId = $picked.tableId
Write-Host "[PASS] eligible tableId=$tableId physicalRows=$($picked.physicalRows)"

$metadata = (PostApi '/governance/direct-share/metadata/collect' $token @{
    tableId = $tableId
}).data
if ($metadata.status -ne 'SUCCESS' -or -not $metadata.entryCode -or -not $metadata.searchable) {
    throw "Metadata collection failed: $($metadata | ConvertTo-Json -Compress)"
}
$search = (GetApi "/governance/platform/metadata/catalog/search?keyword=$($metadata.entryCode)" $token).data
if (@($search).Count -lt 1) {
    throw "entryCode is not searchable: $($metadata.entryCode)"
}
Write-Host "[PASS] metadata entryCode=$($metadata.entryCode) columns=$($metadata.columnCount) om=$($metadata.omSyncStatus)"

$quality = (PostApi '/governance/direct-share/quality/run' $token @{
    tableId = $tableId
}).data
if (-not $quality.runId -or $null -eq $quality.score) {
    throw "Quality run has no score: $($quality | ConvertTo-Json -Compress)"
}
$issues = (GetApi "/governance/quality/task-mgmt/runs/$($quality.runId)/issues" $token).data
Write-Host "[PASS] quality runId=$($quality.runId) score=$($quality.score) issues=$(@($issues).Count)"

$catalog = (PostApi '/governance/direct-share/catalog/publish' $token @{
    tableId = $tableId
}).data
if ($catalog.publishStatus -ne 'PUBLISHED' -or $catalog.sourcePathType -ne 'DIRECT') {
    throw "Catalog is not published as DIRECT: $($catalog | ConvertTo-Json -Compress)"
}
$portal = (GetApi '/governance/catalog/resources-mgmt?publishStatus=PUBLISHED&keyword=P0_DIRECT_ENTERPRISE' $token).data
if (@($portal).Count -lt 1 -or $portal[0].sourcePathType -ne 'DIRECT') {
    throw 'Published DIRECT resource is not visible in the portal query'
}
Write-Host "[PASS] catalog resourceId=$($catalog.resourceId) status=$($catalog.publishStatus) path=DIRECT"

$authorization = (PostApi '/governance/direct-share/subscription/authorize' $token @{
    tableId = $tableId
    shareMode = 'DB_SYNC'
    purpose = 'P0 direct-share golden-path acceptance'
}).data
if ($authorization.authorizationStatus -ne 'ACTIVE') {
    throw "Local authorization was not created: $($authorization | ConvertTo-Json -Depth 5 -Compress)"
}
$ledger = (GetApi "/governance/catalog/subscriptions/$($authorization.subscriptionId)/authorization" $token).data
if ($ledger.status -ne 'ACTIVE') {
    throw "Authorization ledger is not queryable: $($ledger | ConvertTo-Json -Compress)"
}
Write-Host "[PASS] authorization code=$($ledger.authorizationCode) status=$($ledger.status)"

$overview = (GetApi "/governance/direct-share/overview?tableId=$tableId" $token).data
Write-Host "[PASS] golden-path completed steps=$(@($overview.steps).Count)"
