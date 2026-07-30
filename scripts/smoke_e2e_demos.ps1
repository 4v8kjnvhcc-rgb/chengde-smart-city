# End-to-end demo smoke (D02 five scenarios, capability-equivalent POC)
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
    $r = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $body -ContentType 'application/json'
    return $r.data
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
    Record 'E2E1 login' ($admin.accessToken.Length -gt 20) "user=$($admin.user.username)"
    $menus = ApiGet '/system/menus/me' $admin.accessToken
    Record 'E2E1 hub menus' ($menus.data.Count -ge 1) "roots=$($menus.data.Count)"
    $users = ApiGet '/system/users?page=1&size=5' $admin.accessToken
    Record 'E2E1 system users' ($users.data.total -ge 1) "total=$($users.data.total)"
} catch {
    Record 'E2E1 login-hub-system' $false $_.Exception.Message
    exit 1
}

$token = $admin.accessToken

try {
    $rnd = [guid]::NewGuid().ToString('N').Substring(0,6)
    $asset = ApiPost '/exchange/assets' $token @{ assetName = "E2E-asset-$rnd"; sourceSystem = 'demo-src' }
    $task = ApiPost '/exchange/collect-tasks' $token @{ taskName = "E2E-task-$rnd"; assetId = $asset.data }
    $h = @{ Authorization = "Bearer $token" }
    Invoke-RestMethod -Uri "$base/exchange/collect-tasks/$($task.data)/run" -Method Post -Headers $h | Out-Null
    $tasks = ApiGet '/exchange/collect-tasks' $token
    $found = $false
    foreach ($row in @($tasks.data)) {
        if (($row.id -as [int]) -eq ($task.data -as [int]) -and $row.status -eq 'SUCCESS') { $found = $true }
    }
    Record 'E2E2 asset-collect' $found "asset=$($asset.data) task=$($task.data)"
} catch {
    Record 'E2E2 asset-collect' $false $_.Exception.Message
}

try {
    $rnd = [guid]::NewGuid().ToString('N').Substring(0,6)
    $cat = ApiPost '/exchange/catalog' $token @{ title = "E2E-catalog-$rnd"; description = 'share-demo' }
    $h = @{ Authorization = "Bearer $token" }
    Invoke-RestMethod -Uri "$base/exchange/catalog/$($cat.data)/publish" -Method Post -Headers $h | Out-Null
    $portal = ApiGet '/exchange/shared-portal' $token
    $found = $false
    foreach ($row in @($portal.data)) {
        if (($row.id -as [int]) -eq ($cat.data -as [int])) { $found = $true }
    }
    Record 'E2E3 catalog-share' $found "catalog=$($cat.data) portal=$(@($portal.data).Count)"
} catch {
    Record 'E2E3 catalog-share' $false $_.Exception.Message
}

try {
    $rnd = [guid]::NewGuid().ToString('N').Substring(0,6)
    $portal = ApiGet '/exchange/shared-portal' $token
    $target = (@($portal.data)[0].id -as [int])
    $dem = ApiPost '/exchange/demands' $token @{ demandTitle = "E2E-demand-$rnd"; requesterOrg = 'ORG-A'; targetCatalogId = $target }
    $h = @{ Authorization = "Bearer $token" }
    $confirmBody = @{ confirmNote = 'confirmed' } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "$base/exchange/demands/$($dem.data)/confirm" -Method Post -Headers $h -Body $confirmBody -ContentType 'application/json' | Out-Null
    $list = ApiGet '/exchange/demands' $token
    $found = $false
    foreach ($row in @($list.data)) {
        if (($row.id -as [int]) -eq ($dem.data -as [int]) -and $row.status -eq 'CONFIRMED') { $found = $true }
    }
    Record 'E2E4 demand-confirm' $found "demand=$($dem.data)"
} catch {
    Record 'E2E4 demand-confirm' $false $_.Exception.Message
}

try {
    $flows = ApiGet '/exchange/esb/flows' $token
    $flowId = (@($flows.data)[0].id -as [int])
    $inv = ApiPost "/exchange/esb/flows/$flowId/invoke" $token @{ ping = 1 }
    $jobs = ApiGet '/exchange/kettle/jobs' $token
    $jobId = (@($jobs.data)[0].id -as [int])
    $run = ApiPost "/exchange/kettle/jobs/$jobId/run" $token @{ ping = 1 }
    $ok = ($inv.data.status -eq 'SUCCESS') -and ($run.data.status -eq 'SUCCESS')
    Record 'E2E5 esb-kettle' $ok "esb=$($inv.data.traceId) kettle=$($run.data.jobCode)"
} catch {
    Record 'E2E5 esb-kettle' $false $_.Exception.Message
}

$fail = ($results | Where-Object { -not $_.Pass }).Count
Write-Host ''
Write-Host "Summary: $($results.Count - $fail)/$($results.Count) passed"
$results | Format-Table -AutoSize
if ($fail -gt 0) { exit 1 }
