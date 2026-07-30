# M078~M122 governance smoke
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
    $q = GetApi '/governance/platform/quality/overview' $token
    if (@($q.data.rules).Count -lt 7) { $ok = $false; Write-Host '[FAIL] quality-rules' }
    else { Write-Host "[PASS] quality-rules=$($q.data.rules.Count)" }
    if (@($q.data.standards).Count -lt 4) { $ok = $false; Write-Host '[FAIL] standards' }
    else { Write-Host "[PASS] standards=$($q.data.standards.Count)" }
} catch { $ok = $false; Write-Host "[FAIL] quality - $($_.Exception.Message)" }

try {
    $meta = GetApi '/governance/platform/metadata/overview' $token
    if (@($meta.data.connectors).Count -lt 1) { $ok = $false; Write-Host '[FAIL] metadata' }
    else { Write-Host '[PASS] metadata-connectors' }
    $connId = $meta.data.connectors[0].id
    PostApi "/governance/connectors/$connId/sync" $token $null | Out-Null
    Write-Host '[PASS] om-sync'
} catch { $ok = $false; Write-Host "[FAIL] metadata - $($_.Exception.Message)" }

try {
    $resId = (PostApi '/governance/platform/catalog/resources' $token @{
        resourceName = 'Smoke Gov Resource'; resourceType = 'DATA'
    }).data
    PostApi "/governance/platform/catalog/resources/$resId/approve" $token @{ action = 'APPROVE' } | Out-Null
    PostApi "/governance/platform/catalog/resources/$resId/subscribe" $token $null | Out-Null
    $dist = PostApi "/governance/platform/catalog/resources/$resId/distribute" $token $null
    if ($dist.data.status -ne 'DISTRIBUTED') { $ok = $false; Write-Host '[FAIL] catalog-flow' }
    else { Write-Host '[PASS] catalog-subscribe-distribute' }
    $fusion = GetApi '/governance/platform/fusion/assets' $token
    $faId = $fusion.data[0].id
    PostApi "/governance/platform/fusion/assets/$faId/run" $token $null | Out-Null
    Write-Host '[PASS] fusion-run'
} catch { $ok = $false; Write-Host "[FAIL] catalog-fusion - $($_.Exception.Message)" }

if (-not $ok) { exit 1 }
Write-Host 'Governance smoke: all critical checks passed'
