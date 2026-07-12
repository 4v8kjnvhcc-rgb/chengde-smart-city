# M031~M036 portal smoke
$ErrorActionPreference = 'Continue'
$Pass = 'Test@12345'
$base = 'http://localhost:8080/api/v1'

function Login() {
    $body = @{ username = 'sys_admin'; password = $Pass } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $body -ContentType 'application/json; charset=utf-8'
    return $r.data.accessToken
}

function PostApi($path, $token, $payload) {
    $h = @{ Authorization = "Bearer $token" }
    $json = if ($payload) { $payload | ConvertTo-Json -Compress } else { '{}' }
    return Invoke-RestMethod -Uri "$base$path" -Method Post -Headers $h -Body $json -ContentType 'application/json; charset=utf-8'
}

function GetApi($path, $token) {
    $h = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$base$path" -Headers $h
}

$token = Login
$ok = $true

try {
    $homeData = GetApi '/exchange/portal/home' $token
    if ($homeData.data.publishedCount -lt 1) { $ok = $false; Write-Host '[FAIL] portal-home count' }
    else { Write-Host "[PASS] portal-home published=$($homeData.data.publishedCount)" }
} catch { $ok = $false; Write-Host "[FAIL] portal-home - $($_.Exception.Message)" }

try {
    $sit = GetApi '/exchange/portal/situations' $token
    if (@($sit.data).Count -ne 8) { $ok = $false; Write-Host '[FAIL] situations count' }
    else { Write-Host '[PASS] situations 8/8' }
} catch { $ok = $false; Write-Host "[FAIL] situations - $($_.Exception.Message)" }

try {
    PostApi '/exchange/portal/search/sync' $token $null | Out-Null
    $catalog = GetApi '/exchange/portal/catalog' $token
    $catId = $catalog.data[0].id
    $subId = (PostApi '/exchange/portal/subscriptions' $token @{
        catalogId = [long]$catId; applicantOrg = '演示局'; resourceType = 'TABLE'; purpose = '门户冒烟'
    }).data
    PostApi "/exchange/portal/subscriptions/$subId/review" $token @{ action = 'APPROVE'; approverNote = 'ok' } | Out-Null
    $subs = GetApi '/exchange/portal/subscriptions?status=APPROVED' $token
    if (@($subs.data).Count -lt 1) { $ok = $false; Write-Host '[FAIL] subscription flow' }
    else { Write-Host '[PASS] subscription-approve' }
    $search = GetApi "/exchange/portal/search?q=$( [uri]::EscapeDataString('法人') )" $token
    if (@($search.data).Count -lt 1) { Write-Host '[WARN] search-empty (ES may be offline, DB fallback expected)' }
    else { Write-Host "[PASS] search hits=$($search.data.Count)" }
} catch { $ok = $false; Write-Host "[FAIL] subscription/search - $($_.Exception.Message)" }

if (-not $ok) { exit 1 }
Write-Host 'Portal smoke: all critical checks passed'
