# Asset 360 aggregation smoke test (ASCII for Windows PowerShell 5)
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

$token = Login
if (-not $token -or $token.Length -lt 20) { throw 'Login did not return an access token' }

# processed-share produced table entry has the full chain
$entry = 'TBL_FUSION_DWS_ENTERPRISE_THEME'
$a = (GetApi "/governance/asset/360?entryCode=$entry" $token).data

if ($a.entry.entryCode -ne $entry) { throw "entry mismatch: $($a | ConvertTo-Json -Depth 6 -Compress)" }
Write-Host "[PASS] entry=$($a.entry.entryCode) name=$($a.entry.entryName) columns=$(@($a.columns).Count)"

if (@($a.lineage.upstream).Count -lt 1) { throw 'expected upstream lineage from source table' }
Write-Host "[PASS] lineage upstream=$(@($a.lineage.upstream).Count) downstream=$(@($a.lineage.downstream).Count)"

if ($a.quality.status -eq 'NONE') { throw 'expected quality bound' }
Write-Host "[PASS] quality status=$($a.quality.status) score=$($a.quality.score) issues=$($a.quality.issueCount)"

if ($a.catalog.status -eq 'NONE' -or $a.catalog.sourcePathType -ne 'PROCESSED') { throw "catalog not linked as PROCESSED: $($a.catalog | ConvertTo-Json -Compress)" }
Write-Host "[PASS] catalog resource=$($a.catalog.resourceName) path=$($a.catalog.sourcePathType) publish=$($a.catalog.publishStatus)"

if (@($a.subscriptions).Count -lt 1) { throw 'expected at least one subscription' }
$hasAuth = @($a.subscriptions | Where-Object { $_.authorization -and $_.authorization.status -eq 'ACTIVE' }).Count
if ($hasAuth -lt 1) { throw 'expected an ACTIVE authorization in subscriptions' }
Write-Host "[PASS] subscriptions=$(@($a.subscriptions).Count) withActiveAuth=$hasAuth"

Write-Host '[PASS] asset-360 aggregation complete'
