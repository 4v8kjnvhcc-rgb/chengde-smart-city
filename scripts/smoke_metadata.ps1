# M086~M097 metadata subsystem smoke
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

function PutApi($path, $token, $payload) {
    $h = @{ Authorization = "Bearer $token" }
    $json = if ($payload) { $payload | ConvertTo-Json -Compress } else { '{}' }
    return Invoke-RestMethod -Uri "$base$path" -Method Put -Headers $h -Body $json -ContentType 'application/json; charset=utf-8'
}

function GetApi($path, $token) {
    $h = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$base$path" -Headers $h
}

$token = Login
$ok = $true

try {
    $models = GetApi '/governance/platform/metadata/models' $token
    if (-not $models.data) { $ok = $false; Write-Host '[FAIL] models' }
    else { Write-Host "[PASS] models=$($models.data.Count)" }
} catch { $ok = $false; Write-Host "[FAIL] models - $($_.Exception.Message)" }

try {
    $modelId = (PostApi '/governance/platform/metadata/models' $token @{
        modelNameZh = 'Smoke Meta Model'; modelType = 'TABLE'; contentJson = '[]'
    }).data
    PostApi "/governance/platform/metadata/models/$modelId/publish" $token $null | Out-Null
    Write-Host "[PASS] model-publish id=$modelId"
} catch { $ok = $false; Write-Host "[FAIL] model-publish - $($_.Exception.Message)" }

try {
    $meta = GetApi '/governance/platform/metadata/overview' $token
    $connId = $meta.data.connectors[0].id
    Write-Host "[PASS] connectors id=$connId"
    $taskId = (PostApi '/governance/platform/metadata/collect/tasks' $token @{
        taskName = 'Smoke Collect'; connectorId = [int64]$connId; modelId = [int64]$modelId; cronExpr = '0 0 3 * * ?'; scopeType = 'TABLE'; tableList = 'sys_user,sys_org'
    }).data
    Write-Host "[PASS] task-create id=$taskId"
    $run = PostApi "/governance/platform/metadata/collect/tasks/$taskId/run" $token @{}
    if ($run.data.status -ne 'SUCCESS') { $ok = $false; Write-Host "[FAIL] collect-run status=$($run.data.status)" }
    else { Write-Host "[PASS] collect-run runId=$($run.data.runId) jdbc=$($run.data.jdbcUsed)" }
    $results = GetApi "/governance/platform/metadata/collect/runs/$($run.data.runId)/results" $token
    Write-Host "[PASS] run-results count=$($results.data.Count)"
    PutApi "/governance/platform/metadata/collect/tasks/$taskId" $token @{ taskName = 'Smoke Collect Updated' } | Out-Null
    Write-Host '[PASS] task-update'
} catch { $ok = $false; Write-Host "[FAIL] tasks/run - $($_.Exception.Message)"; if ($_.ErrorDetails) { Write-Host $_.ErrorDetails.Message } }

try {
    $search = GetApi '/governance/platform/metadata/catalog/search?keyword=META' $token
    Write-Host "[PASS] catalog-search count=$($search.data.Count)"
    $catalog = GetApi '/governance/platform/metadata/catalog' $token
    if (-not $catalog.data.sourceCatalog) { $ok = $false; Write-Host '[FAIL] catalog' }
    else { Write-Host '[PASS] catalog-views' }
} catch { $ok = $false; Write-Host "[FAIL] catalog - $($_.Exception.Message)" }

try {
    $graph = GetApi '/governance/platform/metadata/analyze?relationType=ASSOC' $token
    Write-Host "[PASS] analyze nodes=$($graph.data.nodes.Count)"
    $suggest = GetApi '/governance/platform/metadata/maintain/suggest-standards' $token
    Write-Host "[PASS] suggest-standards count=$($suggest.data.Count)"
    $notices = GetApi '/governance/platform/metadata/notices' $token
    Write-Host "[PASS] notices count=$($notices.data.Count)"
} catch { $ok = $false; Write-Host "[FAIL] analyze/maintain - $($_.Exception.Message)" }

if (-not $ok) { exit 1 }
Write-Host 'Metadata smoke: all critical checks passed'
