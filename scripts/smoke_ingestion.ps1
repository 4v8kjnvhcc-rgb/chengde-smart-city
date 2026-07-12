# M037~M077 ingestion smoke
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
    $baseStats = GetApi '/exchange/ingestion/stats/base' $token
    $guides = GetApi '/exchange/ingestion/guides' $token
    if (@($baseStats.data).Count -lt 3) { $ok = $false; Write-Host '[FAIL] base-stats' }
    else { Write-Host "[PASS] base-stats=$($baseStats.data.Count)" }
    if (@($guides.data).Count -ne 11) { $ok = $false; Write-Host '[FAIL] guide-steps' }
    else { Write-Host '[PASS] guide-steps 11/11' }
} catch { $ok = $false; Write-Host "[FAIL] stats-guides - $($_.Exception.Message)" }

try {
    $channels = GetApi '/exchange/ingestion/channels' $token
    $chId = $channels.data[0].id
    PostApi "/exchange/ingestion/channels/$chId/run" $token $null | Out-Null
    $pipeId = (PostApi '/exchange/ingestion/pipeline-jobs/run' $token @{ jobType = 'PROBE'; jobName = 'smoke-probe' }).data
    $jobs = GetApi '/exchange/ingestion/pipeline-jobs' $token
    if (-not $pipeId) { $ok = $false; Write-Host '[FAIL] pipeline-run' }
    else { Write-Host "[PASS] channel+pipeline jobs=$($jobs.data.Count)" }
    $recon = GetApi '/exchange/ingestion/reconcile/analysis' $token
    if (-not $recon.data.matched) { $ok = $false; Write-Host '[FAIL] reconcile-api' }
    else { Write-Host '[PASS] reconcile-4api (analysis)' }
} catch { $ok = $false; Write-Host "[FAIL] channel-pipeline - $($_.Exception.Message)" }

try {
    $regId = (PostApi '/exchange/ingestion/registries' $token @{
        title = 'Smoke Registry'; categoryPath = 'Gov/Base'; secretLevel = 'INTERNAL'
    }).data
    PostApi "/exchange/ingestion/registries/$regId/approve" $token @{ action = 'APPROVE' } | Out-Null
    $health = GetApi '/exchange/ingestion/health' $token
    $global = GetApi '/exchange/ingestion/global-view' $token
    if (@($health.data).Count -lt 4) { $ok = $false; Write-Host '[FAIL] health' }
    else { Write-Host "[PASS] registry+health metrics=$($health.data.Count) assets=$($global.data.totalAssets)" }
} catch { $ok = $false; Write-Host "[FAIL] registry-govern - $($_.Exception.Message)" }

if (-not $ok) { exit 1 }
Write-Host 'Ingestion smoke: all critical checks passed'
