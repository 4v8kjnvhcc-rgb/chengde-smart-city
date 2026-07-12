# MS6~MS7 smoke: analytics models / DataEase embed / DolphinScheduler
$ErrorActionPreference = 'Continue'
$Pass = 'Test@12345'
$results = @()
$base = 'http://localhost:8080/api/v1'

function Record($name, $ok, $detail) {
    $global:results += [pscustomobject]@{ Case = $name; Pass = $ok; Detail = $detail }
    $mark = if ($ok) { 'PASS' } else { 'FAIL' }
    Write-Host "[$mark] $name - $detail"
}

function Login($user) {
    $body = @{ username = $user; password = $Pass } | ConvertTo-Json -Compress
    return (Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $body -ContentType 'application/json').data
}

function ApiGet($path, $token) {
    $h = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$base$path" -Method Get -Headers $h
}

function ApiPost($path, $token, $obj) {
    $h = @{ Authorization = "Bearer $token" }
    $body = $obj | ConvertTo-Json -Compress
    return Invoke-RestMethod -Uri "$base$path" -Method Post -Headers $h -Body $body -ContentType 'application/json'
}

try {
    $admin = Login 'sys_admin'
    $token = $admin.accessToken
    Record 'login' ($token.Length -gt 20) $admin.user.username
} catch {
    Record 'login' $false $_.Exception.Message
    exit 1
}

try {
    $sum = ApiGet '/analytics/summary' $token
    $ok = ($sum.data.totalModels -as [int]) -ge 40
    Record 'MS6 models-40' $ok "totalModels=$($sum.data.totalModels)"
} catch {
    Record 'MS6 models-40' $false $_.Exception.Message
}

try {
    $models = ApiGet '/analytics/models?domain=population' $token
    $first = @($models.data)[0]
    $samples = ApiGet "/analytics/models/$($first.id)/samples" $token
    $cnt = @($samples.data).Count
    Record 'MS6 sample-100' ($cnt -ge 100) "model=$($first.modelCode) rows=$cnt"
} catch {
    Record 'MS6 sample-100' $false $_.Exception.Message
}

try {
    $tok = ApiPost '/analytics/embed-token' $token @{ targetType = 'dashboard'; targetId = 'de-dash-overview' }
    $val = ApiGet "/analytics/embed-token/validate?token=$($tok.data.token)" $token
    $ok = ($tok.data.token -like 'DE_*') -and ($val.data.valid -eq $true)
    Record 'MS6 DataEase-SSO' $ok "token=$($tok.data.token.Substring(0,10))..."
} catch {
    Record 'MS6 DataEase-SSO' $false $_.Exception.Message
}

try {
    $wfs = ApiGet '/analytics/workflows' $token
    $wf = @($wfs.data)[0]
    $run = ApiPost "/analytics/workflows/$($wf.id)/run" $token @{ ping = 1 }
    Record 'MS7 DS-workflow' ($run.data.status -eq 'SUCCESS') "wf=$($wf.workflowCode)"
} catch {
    Record 'MS7 DS-workflow' $false $_.Exception.Message
}

$fail = ($results | Where-Object { -not $_.Pass }).Count
Write-Host ''
Write-Host "Summary: $($results.Count - $fail)/$($results.Count) passed"
$results | Format-Table -AutoSize
if ($fail -gt 0) { exit 1 }
