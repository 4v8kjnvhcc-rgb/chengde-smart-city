# P1 processed-share golden-path smoke test (ASCII for Windows PowerShell 5)
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

$ts = Get-Date -Format 'yyyyMMdd_HHmmss'

$prjId = (PostApi '/exchange/ingestion/projects' $token @{
    projectName = "smoke_processed_$ts"
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

$eligible = (GetApi '/governance/processed-share/eligible-tables' $token).data
$picked = ($eligible | Where-Object { $_.physicalTableName -eq 'ods_enterprise_base' } | Select-Object -First 1)
if (-not $picked) {
    throw "No eligible processed source table found after collect. eligibleCount=$(@($eligible).Count)"
}
$tableId = $picked.tableId
Write-Host "[PASS] eligible tableId=$tableId physicalRows=$($picked.physicalRows)"

$source = (GetApi "/governance/processed-share/source?tableId=$tableId" $token).data
$rowCount = if ($null -ne $source.physicalRows) { [long]$source.physicalRows } else { [long]$source.collectedRows }
if ($source.ingestTaskStatus -ne 'SUCCESS' -or $rowCount -lt 1) {
    throw "Source is not collected: $($source | ConvertTo-Json -Compress)"
}
Write-Host "[PASS] source table=$($source.physicalTableName) rows=$rowCount"

# Real source columns after JDBC collect: ent_name/credit_code/... (not legacy enterprise_name)
$fusion = (PostApi '/governance/processed-share/fusion/run' $token @{
    tableId = $tableId
    fusionSpec = @{
        sourceTable = 'ods_enterprise_base'
        targetTable = 'dws_enterprise_theme'
        writeMode = 'TRUNCATE_INSERT'
        themeCode = 'TOPIC_ENTERPRISE'
        filterSql = 'ent_name IS NOT NULL'
        select = @(
            @{ expr = 'credit_code'; as = 'credit_code' }
            @{ expr = 'ent_name'; as = 'ent_name' }
            @{ expr = 'reg_capital'; as = 'reg_capital' }
            @{ expr = 'industry'; as = 'industry' }
            @{ expr = 'area_code'; as = 'area_code' }
        )
    }
}).data
if ($fusion.status -ne 'SUCCESS' -or [long]$fusion.producedRows -lt 1) {
    throw "Fusion did not produce rows: $($fusion | ConvertTo-Json -Compress)"
}
# cleansing removes the row with NULL ent_name -> produced rows < source rows
if ([long]$fusion.producedRows -ge $rowCount) {
    throw "Cleansing did not reduce rows: source=$rowCount produced=$($fusion.producedRows)"
}
Write-Host "[PASS] fusion producedTable=$($fusion.producedTable) rows=$($fusion.producedRows)"

$metadata = (PostApi '/governance/processed-share/metadata/collect' $token @{
    tableId = $tableId
    producedTable = $fusion.producedTable
}).data
if ($metadata.status -ne 'SUCCESS' -or -not $metadata.entryCode -or -not $metadata.searchable) {
    throw "Metadata collection failed: $($metadata | ConvertTo-Json -Compress)"
}
$search = (GetApi "/governance/platform/metadata/catalog/search?keyword=$($metadata.entryCode)" $token).data
if (@($search).Count -lt 1) {
    throw "entryCode is not searchable: $($metadata.entryCode)"
}
Write-Host "[PASS] metadata entryCode=$($metadata.entryCode) columns=$($metadata.columnCount) lineageFrom=$($metadata.lineageFrom)"

$quality = (PostApi '/governance/processed-share/quality/run' $token @{
    tableId = $tableId
    producedTable = $fusion.producedTable
}).data
if (-not $quality.runId -or $null -eq $quality.score) {
    throw "Quality run has no score: $($quality | ConvertTo-Json -Compress)"
}
Write-Host "[PASS] quality runId=$($quality.runId) score=$($quality.score) issues=$($quality.issueCount)"

$catalog = (PostApi '/governance/processed-share/catalog/publish' $token @{
    tableId = $tableId
    producedTable = $fusion.producedTable
}).data
if ($catalog.publishStatus -ne 'PUBLISHED' -or $catalog.sourcePathType -ne 'PROCESSED') {
    throw "Catalog is not published as PROCESSED: $($catalog | ConvertTo-Json -Compress)"
}
$portal = (GetApi '/governance/catalog/resources-mgmt?publishStatus=PUBLISHED&keyword=P1_PROCESSED_ENTERPRISE' $token).data
if (@($portal).Count -lt 1 -or $portal[0].sourcePathType -ne 'PROCESSED') {
    throw 'Published PROCESSED resource is not visible in the portal query'
}
Write-Host "[PASS] catalog resourceId=$($catalog.resourceId) status=$($catalog.publishStatus) path=PROCESSED"

$authorization = (PostApi '/governance/processed-share/subscription/authorize' $token @{
    tableId = $tableId
    producedTable = $fusion.producedTable
    shareMode = 'DB_SYNC'
    purpose = 'P1 processed-share golden-path acceptance'
}).data
if ($authorization.authorizationStatus -ne 'ACTIVE') {
    throw "Local authorization was not created: $($authorization | ConvertTo-Json -Depth 5 -Compress)"
}
$ledger = (GetApi "/governance/catalog/subscriptions/$($authorization.subscriptionId)/authorization" $token).data
if ($ledger.status -ne 'ACTIVE') {
    throw "Authorization ledger is not queryable: $($ledger | ConvertTo-Json -Compress)"
}
Write-Host "[PASS] authorization code=$($ledger.authorizationCode) status=$($ledger.status)"

$overview = (GetApi "/governance/processed-share/overview?tableId=$tableId" $token).data
Write-Host "[PASS] golden-path completed steps=$(@($overview.steps).Count)"
