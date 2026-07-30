# M139~M151 analytics support + BI embed smoke
$ErrorActionPreference = 'Continue'
$Pass = 'Test@12345'
$base = 'http://localhost:8080/api/v1'

function Login() {
    $body = @{ username = 'sys_admin'; password = $Pass } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $body -ContentType 'application/json; charset=utf-8'
    return $r.data.accessToken
}

function GetApi($path, $token) {
    $h = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$base$path" -Headers $h
}

function PostApi($path, $token, $payload) {
    $h = @{ Authorization = "Bearer $token" }
    $json = if ($payload) { $payload | ConvertTo-Json -Compress } else { '{}' }
    return Invoke-RestMethod -Uri "$base$path" -Method Post -Headers $h -Body $json -ContentType 'application/json; charset=utf-8'
}

$token = Login
$ok = $true

try {
    $sup = GetApi '/analytics/platform/support/overview' $token
    if (@($sup.data.apps).Count -lt 3) { $ok = $false; Write-Host '[FAIL] support-apps' }
    else { Write-Host "[PASS] support-apps=$($sup.data.apps.Count)" }
    if (@($sup.data.services).Count -lt 4) { $ok = $false; Write-Host '[FAIL] support-services' }
    else { Write-Host "[PASS] support-services=$($sup.data.services.Count)" }
    if (@($sup.data.integrations).Count -lt 3) { $ok = $false; Write-Host '[FAIL] integrations' }
    else { Write-Host '[PASS] integrations' }
} catch { $ok = $false; Write-Host "[FAIL] support - $($_.Exception.Message)" }

try {
    $appId = (PostApi '/analytics/platform/apps' $token @{ appName = 'Smoke App'; endpointUrl = '/smoke' }).data
    if ($appId -gt 0) { Write-Host "[PASS] create-app id=$appId" }
    else { $ok = $false; Write-Host '[FAIL] create-app' }
} catch { $ok = $false; Write-Host "[FAIL] create-app - $($_.Exception.Message)" }

try {
    $bi = GetApi '/analytics/platform/bi/overview' $token
    if (@($bi.data.widgets).Count -ne 6) { $ok = $false; Write-Host '[FAIL] bi-widgets' }
    else { Write-Host '[PASS] bi-widgets=6' }
    $w = GetApi '/analytics/platform/bi/widgets/M146' $token
    if ($w.data.mCode -ne 'M146') { $ok = $false; Write-Host '[FAIL] widget-M146' }
    else { Write-Host '[PASS] widget-M146' }
} catch { $ok = $false; Write-Host "[FAIL] bi - $($_.Exception.Message)" }

try {
    $tok = PostApi '/analytics/platform/bi/widgets/M146/embed-token' $token $null
    $issued = $tok.data.token
    $val = GetApi "/analytics/embed-token/validate?token=$issued" $token
    if ($val.data.valid -ne $true) { $ok = $false; Write-Host '[FAIL] embed-validate-valid' }
    elseif ($val.data.token -ne $issued) { $ok = $false; Write-Host '[FAIL] embed-token-mismatch' }
    else { Write-Host '[PASS] embed-SSO-token-reuse' }
} catch { $ok = $false; Write-Host "[FAIL] embed - $($_.Exception.Message)" }

try {
    $intId = (GetApi '/analytics/platform/support/overview' $token).data.integrations[0].id
    $test = PostApi "/analytics/platform/integrations/$intId/test" $token $null
    if ($test.data.status) { Write-Host "[PASS] integration-test=$($test.data.status)" }
    else { $ok = $false; Write-Host '[FAIL] integration-test' }
} catch { $ok = $false; Write-Host "[FAIL] integration-test - $($_.Exception.Message)" }

if (-not $ok) { exit 1 }
Write-Host 'Analytics M139-M151 smoke: all critical checks passed'
