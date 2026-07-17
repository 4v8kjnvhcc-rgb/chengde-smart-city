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
    $json = $payload | ConvertTo-Json -Compress
    return Invoke-RestMethod -Uri "$base$path" -Method Post -Headers @{ Authorization = "Bearer $token" } `
        -Body $json -ContentType 'application/json'
}

$token = Login
if (-not $token -or $token.Length -lt 20) {
    throw 'Login did not return an access token'
}
(GetApi '/governance/platform/quality/overview' $token) | Out-Null

$sample = (GetApi '/governance/direct-share/sample' $token).data
if ($sample.collectStatus -ne 'SUCCESS' -or [long]$sample.physicalRows -lt 1) {
    throw "Sample is not collected: $($sample | ConvertTo-Json -Compress)"
}
Write-Host "[PASS] sample dataSourceId=$($sample.dataSourceId) table=$($sample.physicalTableName) rows=$($sample.physicalRows)"

$metadata = (PostApi '/governance/direct-share/metadata/collect' $token).data
if ($metadata.status -ne 'SUCCESS' -or -not $metadata.entryCode -or -not $metadata.searchable) {
    throw "Metadata collection failed: $($metadata | ConvertTo-Json -Compress)"
}
$search = (GetApi "/governance/platform/metadata/catalog/search?keyword=$($metadata.entryCode)" $token).data
if (@($search).Count -lt 1) {
    throw "entryCode is not searchable: $($metadata.entryCode)"
}
Write-Host "[PASS] metadata entryCode=$($metadata.entryCode) columns=$($metadata.columnCount)"

$quality = (PostApi '/governance/direct-share/quality/run' $token).data
if (-not $quality.runId -or $null -eq $quality.score) {
    throw "Quality run has no score: $($quality | ConvertTo-Json -Compress)"
}
$issues = (GetApi "/governance/quality/task-mgmt/runs/$($quality.runId)/issues" $token).data
Write-Host "[PASS] quality runId=$($quality.runId) score=$($quality.score) issues=$(@($issues).Count)"

$catalog = (PostApi '/governance/direct-share/catalog/publish' $token).data
if ($catalog.publishStatus -ne 'PUBLISHED' -or $catalog.sourcePathType -ne 'DIRECT') {
    throw "Catalog is not published as DIRECT: $($catalog | ConvertTo-Json -Compress)"
}
$portal = (GetApi '/governance/catalog/resources-mgmt?publishStatus=PUBLISHED&keyword=P0_DIRECT_ENTERPRISE' $token).data
if (@($portal).Count -lt 1 -or $portal[0].sourcePathType -ne 'DIRECT') {
    throw 'Published DIRECT resource is not visible in the portal query'
}
Write-Host "[PASS] catalog resourceId=$($catalog.resourceId) status=$($catalog.publishStatus) path=DIRECT"

$authorization = (PostApi '/governance/direct-share/subscription/authorize' $token @{
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

$overview = (GetApi '/governance/direct-share/overview' $token).data
Write-Host "[PASS] golden-path completed steps=$(@($overview.steps).Count)"
