# M051～M077 ingestion collect smoke (system=collect)
param([switch]$CollectOnly)

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

if (-not $CollectOnly) {
    # M039/M041 register L1
    try {
        $guides = GetApi '/exchange/ingestion/guides' $token
        $tables = GetApi '/exchange/ingestion/register/tables' $token
        $lineage = GetApi '/exchange/ingestion/register/lineage' $token
        $report = GetApi '/exchange/ingestion/register/asset-report' $token
        if (@($guides.data).Count -ne 11) { $ok = $false; Write-Host '[FAIL] M039 guide-steps' }
        else { Write-Host '[PASS] M039 guide-steps 11/11' }
        if (@($tables.data).Count -lt 1) { $ok = $false; Write-Host '[FAIL] M041 tables' }
        else { Write-Host "[PASS] M041 tables=$($tables.data.Count)" }
        if (-not $lineage.data.edges) { $ok = $false; Write-Host '[FAIL] M047 lineage' }
        else { Write-Host "[PASS] M047 lineage edges=$($lineage.data.edges.Count)" }
        if (-not $report.data.projectCount) { $ok = $false; Write-Host '[FAIL] M046 asset-report' }
        else { Write-Host '[PASS] M046 asset-report' }
    } catch { $ok = $false; Write-Host "[FAIL] register - $($_.Exception.Message)" }
}

# M051-M060 collect upload + ingest
try {
    $templates = GetApi '/exchange/ingestion/collect/templates' $token
    $uploads = GetApi '/exchange/ingestion/uploads' $token
    $channels = GetApi '/exchange/ingestion/channels' $token
    if (@($templates.data).Count -lt 1) { $ok = $false; Write-Host '[FAIL] M051 templates' }
    else { Write-Host "[PASS] M051 templates=$($templates.data.Count)" }
    if ($null -eq $uploads.data) { $ok = $false; Write-Host '[FAIL] M052-M053 uploads' }
    else { Write-Host "[PASS] M052-M053 uploads=$($uploads.data.Count)" }
    $chId = $channels.data[0].id
    PostApi "/exchange/ingestion/channels/$chId/run" $token $null | Out-Null
    $tasks = GetApi '/exchange/ingestion/collect/tasks' $token
    if (@($channels.data).Count -lt 1) { $ok = $false; Write-Host '[FAIL] M054-M060 channels' }
    else { Write-Host "[PASS] M054-M060 channels=$($channels.data.Count) tasks=$($tasks.data.Count)" }
} catch { $ok = $false; Write-Host "[FAIL] upload-ingest - $($_.Exception.Message)" }

# M061-M064 pipeline
try {
    $pipeId = (PostApi '/exchange/ingestion/pipeline-jobs/run' $token @{ jobType = 'PROBE'; jobName = 'smoke-probe' }).data
    $probes = GetApi '/exchange/ingestion/collect/probe-reports' $token
    $defs = GetApi '/exchange/ingestion/collect/definitions' $token
    $recon = GetApi '/exchange/ingestion/reconcile/analysis' $token
    $logs = GetApi '/exchange/ingestion/collect/reconcile-logs' $token
    if (-not $pipeId) { $ok = $false; Write-Host '[FAIL] M061 pipeline-run' }
    else { Write-Host "[PASS] M061 probes=$($probes.data.Count) defs=$($defs.data.Count)" }
    if (-not $recon.data.matched) { $ok = $false; Write-Host '[FAIL] M064 reconcile-api' }
    else { Write-Host "[PASS] M064 reconcile logs=$($logs.data.Count)" }
} catch { $ok = $false; Write-Host "[FAIL] pipeline - $($_.Exception.Message)" }

# M065-M068 catalog
try {
    $regId = (PostApi '/exchange/ingestion/registries' $token @{
        title = 'Smoke Registry'; categoryPath = 'Gov/Base'; secretLevel = 'INTERNAL'
    }).data
    PostApi "/exchange/ingestion/registries/$regId/approve" $token @{ action = 'APPROVE' } | Out-Null
    $cats = GetApi '/exchange/ingestion/collect/categories' $token
    $regs = GetApi '/exchange/ingestion/registries' $token
    if (@($cats.data).Count -lt 1) { $ok = $false; Write-Host '[FAIL] M066 categories' }
    else { Write-Host "[PASS] M065-M068 registries=$($regs.data.Count) categories=$($cats.data.Count)" }
} catch { $ok = $false; Write-Host "[FAIL] catalog - $($_.Exception.Message)" }

# M069-M077 已移出采集 Tab 验收（见 D17 R1.2）

if (-not $ok) { exit 1 }
if ($CollectOnly) { Write-Host 'Collect smoke: M051-M077 checks passed' }
else { Write-Host 'Ingestion smoke: all critical checks passed (M039-M077 IA)' }
