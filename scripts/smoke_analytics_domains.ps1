# M152~M209 domain analytics smoke
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

$domains = @(
    @{ domain = 'population'; expect = 23; mCode = 'M152'; analysis = 'M161' },
    @{ domain = 'legal'; expect = 18; mCode = 'M175'; analysis = 'M184' },
    @{ domain = 'macro'; expect = 11; mCode = 'M193'; analysis = 'M193' },
    @{ domain = 'key'; expect = 6; mCode = 'M204'; analysis = 'M204' }
)

foreach ($d in $domains) {
    try {
        $ov = GetApi "/analytics/domain/$($d.domain)/overview" $token
        if ($ov.data.totalModules -ne $d.expect) {
            $ok = $false
            Write-Host "[FAIL] $($d.domain)-count expected=$($d.expect) got=$($ov.data.totalModules)"
        } else {
            Write-Host "[PASS] $($d.domain)-modules=$($d.expect)"
        }
    } catch {
        $ok = $false
        Write-Host "[FAIL] $($d.domain)-overview - $($_.Exception.Message)"
    }
}

try {
    $run = PostApi '/analytics/domain/modules/M152/run' $token $null
    if ($run.data.status -ne 'SUCCESS') { $ok = $false; Write-Host '[FAIL] data-ops-run' }
    else { Write-Host '[PASS] data-ops-run M152' }
} catch {
    $ok = $false
    Write-Host "[FAIL] data-ops-run - $($_.Exception.Message)"
}

try {
    $det = GetApi '/analytics/domain/modules/M161' $token
    if ($det.data.module.moduleType -ne 'ANALYSIS') { $ok = $false; Write-Host '[FAIL] analysis-detail' }
    elseif ($det.data.sampleCount -lt 100) { $ok = $false; Write-Host "[FAIL] sample-100 got=$($det.data.sampleCount)" }
    else { Write-Host "[PASS] analysis-M161 samples=$($det.data.sampleCount)" }
} catch {
    $ok = $false
    Write-Host "[FAIL] analysis-detail - $($_.Exception.Message)"
}

try {
    $tok = PostApi '/analytics/domain/modules/M161/embed-token' $token $null
    $issued = $tok.data.token
    $val = GetApi "/analytics/embed-token/validate?token=$issued" $token
    if ($val.data.valid -ne $true -or $val.data.token -ne $issued) {
        $ok = $false
        Write-Host '[FAIL] domain-embed'
    } else {
        Write-Host '[PASS] domain-embed M161'
    }
} catch {
    $ok = $false
    Write-Host "[FAIL] domain-embed - $($_.Exception.Message)"
}

if (-not $ok) { exit 1 }
Write-Host 'Analytics domain M152-M209 smoke: all critical checks passed'
