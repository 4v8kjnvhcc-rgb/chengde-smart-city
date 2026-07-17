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
    $json = $payload | ConvertTo-Json -Compress
    return Invoke-RestMethod -Uri "$base$path" -Method Post -Headers @{ Authorization = "Bearer $token" } `
        -Body $json -ContentType 'application/json'
}

$token = Login
if (-not $token -or $token.Length -lt 20) {
    throw 'Login did not return an access token'
}

$source = (GetApi '/governance/processed-share/source' $token).data
if ($source.ingestTaskStatus -ne 'SUCCESS' -or [long]$source.sourceRows -lt 1) {
    throw "Source is not collected: $($source | ConvertTo-Json -Compress)"
}
Write-Host "[PASS] source table=$($source.physicalTableName) rows=$($source.sourceRows)"

$fusion = (PostApi '/governance/processed-share/fusion/run' $token).data
if ($fusion.status -ne 'SUCCESS' -or [long]$fusion.producedRows -lt 1) {
    throw "Fusion did not produce rows: $($fusion | ConvertTo-Json -Compress)"
}
# cleansing removes the row with NULL enterprise name -> produced rows < source rows
if ([long]$fusion.producedRows -ge [long]$source.sourceRows) {
    throw "Cleansing did not reduce rows: source=$($source.sourceRows) produced=$($fusion.producedRows)"
}
Write-Host "[PASS] fusion producedTable=$($fusion.producedTable) rows=$($fusion.producedRows)"

$metadata = (PostApi '/governance/processed-share/metadata/collect' $token).data
if ($metadata.status -ne 'SUCCESS' -or -not $metadata.entryCode -or -not $metadata.searchable) {
    throw "Metadata collection failed: $($metadata | ConvertTo-Json -Compress)"
}
$search = (GetApi "/governance/platform/metadata/catalog/search?keyword=$($metadata.entryCode)" $token).data
if (@($search).Count -lt 1) {
    throw "entryCode is not searchable: $($metadata.entryCode)"
}
Write-Host "[PASS] metadata entryCode=$($metadata.entryCode) columns=$($metadata.columnCount) lineageFrom=$($metadata.lineageFrom)"

$quality = (PostApi '/governance/processed-share/quality/run' $token).data
if (-not $quality.runId -or $null -eq $quality.score) {
    throw "Quality run has no score: $($quality | ConvertTo-Json -Compress)"
}
Write-Host "[PASS] quality runId=$($quality.runId) score=$($quality.score) issues=$($quality.issueCount)"

$catalog = (PostApi '/governance/processed-share/catalog/publish' $token).data
if ($catalog.publishStatus -ne 'PUBLISHED' -or $catalog.sourcePathType -ne 'PROCESSED') {
    throw "Catalog is not published as PROCESSED: $($catalog | ConvertTo-Json -Compress)"
}
$portal = (GetApi '/governance/catalog/resources-mgmt?publishStatus=PUBLISHED&keyword=P1_PROCESSED_ENTERPRISE' $token).data
if (@($portal).Count -lt 1 -or $portal[0].sourcePathType -ne 'PROCESSED') {
    throw 'Published PROCESSED resource is not visible in the portal query'
}
Write-Host "[PASS] catalog resourceId=$($catalog.resourceId) status=$($catalog.publishStatus) path=PROCESSED"

$authorization = (PostApi '/governance/processed-share/subscription/authorize' $token @{
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

$overview = (GetApi '/governance/processed-share/overview' $token).data
Write-Host "[PASS] golden-path completed steps=$(@($overview.steps).Count)"
