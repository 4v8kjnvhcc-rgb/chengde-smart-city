# MS3~MS5 smoke: governance / unstructured / resource-center
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
    $rnd = [guid]::NewGuid().ToString('N').Substring(0,6)
    $c = ApiPost '/governance/connectors' $token @{ connectorName = "conn-$rnd"; sourceType = 'MySQL' }
    $sync = ApiPost "/governance/connectors/$($c.data)/sync" $token @{ ping = 1 }
    $r = ApiPost '/governance/quality/rules' $token @{ ruleName = "rule-$rnd"; ruleType = 'COMPLETENESS' }
    $t = ApiPost '/governance/quality/tasks' $token @{ taskName = "qtask-$rnd"; ruleId = $r.data }
    $h = @{ Authorization = "Bearer $token" }
    $run = Invoke-RestMethod -Uri "$base/governance/quality/tasks/$($t.data)/run" -Method Post -Headers $h
    $ok = ($sync.data.status -eq 'SUCCESS') -and ($run.data.status -eq 'SUCCESS')
    Record 'MS3 governance' $ok "conn=$($c.data) score=$($run.data.score)"
} catch {
    Record 'MS3 governance' $false $_.Exception.Message
}

try {
    $rnd = [guid]::NewGuid().ToString('N').Substring(0,6)
    $d = ApiPost '/unstructured/documents' $token @{ title = "doc-$rnd"; contentType = 'application/pdf' }
    $idx = ApiPost "/unstructured/documents/$($d.data)/index" $token @{ ping = 1 }
    $list = ApiGet '/unstructured/documents' $token
    $found = $false
    foreach ($row in @($list.data)) {
        if (($row.id -as [int]) -eq ($d.data -as [int]) -and $row.indexStatus -eq 'INDEXED') { $found = $true }
    }
    Record 'MS4 unstructured' ($found -and $idx.data.indexStatus -eq 'INDEXED') "doc=$($d.data)"
} catch {
    Record 'MS4 unstructured' $false $_.Exception.Message
}

try {
    $rnd = [guid]::NewGuid().ToString('N').Substring(0,6)
    $th = ApiPost '/resource-center/themes' $token @{ themeName = "theme-$rnd"; partitionKey = 'org_id' }
    $bk = ApiPost '/resource-center/backups' $token @{ jobName = "backup-$rnd"; themeId = $th.data }
    $h = @{ Authorization = "Bearer $token" }
    $run = Invoke-RestMethod -Uri "$base/resource-center/backups/$($bk.data)/run" -Method Post -Headers $h
    Record 'MS5 resource-center' ($run.data.status -eq 'SUCCESS') "theme=$($th.data) backup=$($bk.data)"
} catch {
    Record 'MS5 resource-center' $false $_.Exception.Message
}

$fail = ($results | Where-Object { -not $_.Pass }).Count
Write-Host ''
Write-Host "Summary: $($results.Count - $fail)/$($results.Count) passed"
$results | Format-Table -AutoSize
if ($fail -gt 0) { exit 1 }
