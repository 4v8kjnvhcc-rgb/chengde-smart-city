# OSS integration smoke: component health + portal proxy APIs (no ESB)
$ErrorActionPreference = 'Continue'
$Pass = 'Test@12345'
$results = @()
$base = 'http://localhost:8080/api/v1'

function Record($name, $ok, $detail) {
    $global:results += [pscustomobject]@{ Case = $name; Pass = $ok; Detail = $detail }
    $mark = if ($ok) { 'PASS' } else { 'FAIL' }
    Write-Host "[$mark] $name - $detail"
}

function Login() {
    $body = @{ username = 'sys_admin'; password = $Pass } | ConvertTo-Json -Compress
    return (Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $body -ContentType 'application/json').data
}

try {
    $admin = Login
    $token = $admin.accessToken
    Record 'login' ($token.Length -gt 20) $admin.user.username
} catch {
    Record 'login' $false $_.Exception.Message
    exit 1
}

$h = @{ Authorization = "Bearer $token" }

try {
    $health = Invoke-RestMethod -Uri "$base/integration/health" -Headers $h
    $ok = $health.data.enabled -eq $true
    Record 'integration-enabled' $ok "enabled=$($health.data.enabled)"
} catch {
    Record 'integration-enabled' $false $_.Exception.Message
}

$components = @('openmetadata', 'dataease', 'dolphinscheduler', 'kettle', 'elasticsearch', 'seaweedfs')
foreach ($c in $components) {
    try {
        $health = Invoke-RestMethod -Uri "$base/integration/health" -Headers $h
        $up = $health.data.$c -eq $true
        Record "oss-$c" $up "$c=$($health.data.$c)"
    } catch {
        Record "oss-$c" $false $_.Exception.Message
    }
}

try {
    $tok = Invoke-RestMethod -Uri "$base/analytics/embed-token" -Method Post -Headers $h `
        -Body (@{ targetType = 'dashboard'; targetId = 'de-dash-overview' } | ConvertTo-Json -Compress) `
        -ContentType 'application/json'
    $val = Invoke-RestMethod -Uri "$base/analytics/embed-token/validate?token=$($tok.data.token)" -Headers $h
    $ok = ($tok.data.source -eq 'dataease-live') -or ($val.data.valid -eq $true)
    Record 'dataease-embed' $ok "source=$($tok.data.source)"
} catch {
    Record 'dataease-embed' $false $_.Exception.Message
}

try {
    $wfs = Invoke-RestMethod -Uri "$base/analytics/workflows" -Headers $h
    $wf = @($wfs.data)[0]
    if ($wf) {
        $run = Invoke-RestMethod -Uri "$base/analytics/workflows/$($wf.id)/run" -Method Post -Headers $h `
            -Body '{}' -ContentType 'application/json'
        Record 'ds-workflow' ($run.data.status -eq 'SUCCESS') "wf=$($wf.workflowCode)"
    } else {
        Record 'ds-workflow' $false 'no workflows'
    }
} catch {
    Record 'ds-workflow' $false $_.Exception.Message
}

try {
    $jobs = Invoke-RestMethod -Uri "$base/exchange/kettle/jobs" -Headers $h
    $job = @($jobs.data)[0]
    if ($job) {
        $run = Invoke-RestMethod -Uri "$base/exchange/kettle/jobs/$($job.id)/run" -Method Post -Headers $h
        Record 'kettle-run' ($run.data.status -eq 'SUCCESS') "job=$($job.jobCode)"
    } else {
        Record 'kettle-run' $false 'no jobs'
    }
} catch {
    Record 'kettle-run' $false $_.Exception.Message
}

try {
    $rnd = [guid]::NewGuid().ToString('N').Substring(0,6)
    $d = Invoke-RestMethod -Uri "$base/unstructured/documents" -Method Post -Headers $h `
        -Body (@{ title = "oss-doc-$rnd"; contentType = 'text/plain' } | ConvertTo-Json -Compress) `
        -ContentType 'application/json'
    $idx = Invoke-RestMethod -Uri "$base/unstructured/documents/$($d.data)/index" -Method Post -Headers $h
    Record 'unstructured-es' ($idx.data.indexStatus -eq 'INDEXED') "doc=$($d.data)"
} catch {
    Record 'unstructured-es' $false $_.Exception.Message
}

$fail = ($results | Where-Object { -not $_.Pass }).Count
Write-Host ''
Write-Host "Summary: $($results.Count - $fail)/$($results.Count) passed"
$results | Format-Table -AutoSize
if ($fail -gt 0) { exit 1 }
