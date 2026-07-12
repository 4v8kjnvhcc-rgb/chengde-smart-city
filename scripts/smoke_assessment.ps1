# M027~M030 assessment smoke
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
    $ds = PostApi '/exchange/assessment/data-sources/sync' $token $null
    if (@($ds.data).Count -lt 3) { $ok = $false; Write-Host '[FAIL] data-sources sync' }
    else { Write-Host '[PASS] data-sources sync' }
} catch { $ok = $false; Write-Host "[FAIL] data-sources - $($_.Exception.Message)" }

try {
    $periods = GetApi '/exchange/assessment/periods' $token
    $periodId = $periods.data[0].id
    $run = PostApi '/exchange/assessment/executions/run' $token @{
        periodId = $periodId; targetType = 'DEPT'; targetName = '演示部门'
    }
    $results = GetApi "/exchange/assessment/executions/$($run.data)/results" $token
    if (@($results.data).Count -lt 3) { $ok = $false; Write-Host '[FAIL] eval-run' }
    else { Write-Host "[PASS] eval-run score-items=$($results.data.Count)" }
    PostApi "/exchange/assessment/executions/$($run.data)/publish" $token $null | Out-Null
    Write-Host '[PASS] eval-publish'
} catch { $ok = $false; Write-Host "[FAIL] eval-run - $($_.Exception.Message)" }

if (-not $ok) { exit 1 }
