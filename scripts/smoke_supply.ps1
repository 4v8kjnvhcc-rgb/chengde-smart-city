# M020~M026 supply-demand smoke
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
    $tpl = GetApi '/exchange/supply/templates' $token
    if (@($tpl.data).Count -lt 3) { $ok = $false; Write-Host '[FAIL] templates' }
    else { Write-Host "[PASS] templates=$($tpl.data.Count)" }
} catch { $ok = $false; Write-Host "[FAIL] templates - $($_.Exception.Message)" }

try {
    $demandId = (PostApi '/exchange/supply/demands' $token @{
        demandTitle = 'Legal entity data demand'; requesterOrg = 'Demo Bureau'; demandType = 'STRUCTURED'; templateCode = 'TPL_STRUCT_01'
    }).data
    PostApi "/exchange/supply/demands/$demandId/analyze" $token $null | Out-Null
    $confirm = PostApi "/exchange/supply/demands/$demandId/confirm" $token @{
        confirmNote = 'smoke confirm'; supplyMode = 'EXCHANGE'; authLevel = 'CITY'; cascadeFlag = 1
    }
    $view = GetApi "/exchange/supply/supply-view/$demandId" $token
    if (@($confirm.data.tasks).Count -lt 3) { $ok = $false; Write-Host '[FAIL] confirm-tasks' }
    else { Write-Host "[PASS] demand-flow tasks=$($confirm.data.tasks.Count)" }
    if (-not $view.data.tasks) { $ok = $false; Write-Host '[FAIL] supply-view' }
    else { Write-Host '[PASS] supply-view' }
} catch { $ok = $false; Write-Host "[FAIL] demand-flow - $($_.Exception.Message)" }

try {
    $objId = (PostApi '/exchange/supply/objections' $token @{
        catalogId = 1; objectionType = 'QUALITY'; content = 'smoke objection'
    }).data
    PostApi "/exchange/supply/objections/$objId/process" $token @{ action = 'CLOSE'; handlerNote = 'ok' } | Out-Null
    $manifests = GetApi '/exchange/supply/manifests' $token
    if (@($manifests.data).Count -lt 1) { $ok = $false; Write-Host '[FAIL] manifests' }
    else { Write-Host "[PASS] manifests=$($manifests.data.Count)" }
    $export = GetApi '/exchange/supply/catalog-manifest/export' $token
    if ($export.data.rowCount -lt 1) { $ok = $false; Write-Host '[FAIL] catalog-export' }
    else { Write-Host "[PASS] catalog-export rows=$($export.data.rowCount)" }
} catch { $ok = $false; Write-Host "[FAIL] objection-manifest - $($_.Exception.Message)" }

if (-not $ok) { exit 1 }
Write-Host 'Supply-demand smoke: all critical checks passed'
