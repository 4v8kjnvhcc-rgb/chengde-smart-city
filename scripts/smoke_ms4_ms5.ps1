# M123~M138 unstructured + resource smoke
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
    $ov = GetApi '/unstructured/platform/overview' $token
    if (@(GetApi '/unstructured/platform/categories' $token).data.Count -lt 3) { $ok = $false; Write-Host '[FAIL] uns-categories' }
    else { Write-Host '[PASS] uns-categories' }
    $docId = (PostApi '/unstructured/platform/documents' $token @{ title = 'smoke-doc'; contentType = 'application/pdf' }).data
    PostApi "/unstructured/platform/documents/$docId/publish" $token $null | Out-Null
    PostApi "/unstructured/platform/documents/$docId/index" $token $null | Out-Null
    PostApi "/unstructured/platform/documents/$docId/pipeline/CLEAN" $token $null | Out-Null
    Write-Host "[PASS] uns-doc-flow seaweed=$($ov.data.seaweedHealthy)"
} catch { $ok = $false; Write-Host "[FAIL] unstructured - $($_.Exception.Message)" }

try {
    $libs = GetApi '/resource-center/platform/libraries/overview' $token
    if (@($libs.data.baseLibraries).Count -lt 2) { $ok = $false; Write-Host '[FAIL] rc-libraries' }
    else { Write-Host '[PASS] rc-libraries' }
    $part = GetApi '/resource-center/platform/partition/overview' $token
    PostApi "/resource-center/platform/policies/$($part.data.policies[0].id)/execute" $token $null | Out-Null
    $stats = GetApi '/resource-center/platform/statistics' $token
    $mon = GetApi '/resource-center/platform/monitor' $token
    if (@($mon.data).Count -lt 4) { $ok = $false; Write-Host '[FAIL] rc-monitor' }
    else { Write-Host "[PASS] rc-stats+monitor records=$($stats.data.totalRecords)" }
} catch { $ok = $false; Write-Host "[FAIL] resource-center - $($_.Exception.Message)" }

if (-not $ok) { exit 1 }
Write-Host 'MS4/MS5 smoke: all critical checks passed'
