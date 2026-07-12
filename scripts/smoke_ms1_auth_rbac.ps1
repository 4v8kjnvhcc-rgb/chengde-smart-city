# MS1 login + RBAC smoke test (API)
$ErrorActionPreference = 'Continue'
$Pass = 'Test@12345'
$results = @()

function Record($name, $ok, $detail) {
    $global:results += [pscustomobject]@{ Case = $name; Pass = $ok; Detail = $detail }
    $mark = if ($ok) { 'PASS' } else { 'FAIL' }
    Write-Host "[$mark] $name - $detail"
}

function Login($user) {
    $body = @{ username = $user; password = $Pass } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/auth/login' -Method Post -Body $body -ContentType 'application/json; charset=utf-8'
    return $r.data
}

function GetApi($path, $token) {
    $h = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "http://localhost:8080/api/v1$path" -Headers $h
}

function GetStatus($path, $token) {
    $h = @{}
    if ($token) { $h['Authorization'] = "Bearer $token" }
    try {
        Invoke-WebRequest -Uri "http://localhost:8080/api/v1$path" -Headers $h -UseBasicParsing | Out-Null
        return 200
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

try {
    $h = Invoke-RestMethod 'http://localhost:8080/actuator/health'
    Record 'health' ($h.data.status -eq 'UP') "status=$($h.data.status)"
} catch {
    Record 'health' $false $_.Exception.Message
    exit 1
}

try {
    $admin = Login 'sys_admin'
    Record 'login sys_admin' ($admin.accessToken.Length -gt 20) "user=$($admin.user.username)"
} catch {
    Record 'login sys_admin' $false $_.Exception.Message
    exit 1
}

try {
    Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/auth/login' -Method Post -Body '{"username":"sys_admin","password":"wrong"}' -ContentType 'application/json'
    Record 'login wrong password' $false 'should reject'
} catch {
    Record 'login wrong password' $true 'rejected'
}

try {
    $perms = GetApi '/system/permissions/me' $admin.accessToken
    Record 'admin user:add' ($perms.data -contains 'system:user:add') "count=$($perms.data.Count)"
    $menus = GetApi '/system/menus/me' $admin.accessToken
    Record 'admin menu tree' ($menus.data.Count -ge 1) "roots=$($menus.data.Count)"
} catch {
    Record 'admin rbac' $false $_.Exception.Message
}

try {
    $dept = Login 'dept_admin_a'
    $dp = GetApi '/system/permissions/me' $dept.accessToken
    $noAdd = $dp.data -notcontains 'system:user:add'
    $hasQuery = $dp.data -contains 'system:user:query'
    Record 'dept_admin no user:add' $noAdd "has query=$hasQuery"
    $code = GetStatus '/system/users?page=1&size=5' $dept.accessToken
    Record 'dept_admin users 403' ($code -eq 403) "http=$code (needs user:list)"
    $orgs = GetApi '/system/orgs' $dept.accessToken
    Record 'dept_admin orgs ok' ($orgs.data.Count -ge 1) "orgs=$($orgs.data.Count)"
} catch {
    Record 'dept_admin rbac' $false $_.Exception.Message
}

try {
    $ua = Login 'user_a'
    $up = GetApi '/system/permissions/me' $ua.accessToken
    Record 'user_a only dashboard' (($up.data.Count -eq 1) -and ($up.data[0] -eq 'dashboard:view')) "perms=$($up.data -join ',')"
    $code = GetStatus '/system/users?page=1' $ua.accessToken
    Record 'user_a users 403' ($code -eq 403) "http=$code"
} catch {
    Record 'user_a rbac' $false $_.Exception.Message
}

$unauth = GetStatus '/system/users?page=1' ''
Record 'unauth 401' ($unauth -eq 401) "http=$unauth"

# 单会话绑定：其他客户端（如门户静默刷新）可能踢掉旧 accessToken，关键操作前重新登录
try {
    $admin = Login 'sys_admin'
} catch {
    Record 'relogin sys_admin' $false $_.Exception.Message
    exit 1
}

try {
    $audit = GetApi '/system/audit-logs?page=1&size=10' $admin.accessToken
    $loginCnt = ($audit.data.records | Where-Object { $_.action -eq 'LOGIN' }).Count
    Record 'audit LOGIN' ($loginCnt -gt 0) "login rows=$loginCnt"
} catch {
    Record 'audit LOGIN' $false $_.Exception.Message
}

try {
    $uname = "smoke_u_$([guid]::NewGuid().ToString('N').Substring(0,8))"
    $createBody = @{
        username = $uname
        password = $Pass
        displayName = 'smoke-user'
        orgId = 1
        roleIds = @(3)
    } | ConvertTo-Json -Compress
    $h = @{ Authorization = "Bearer $($admin.accessToken)" }
    $created = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/system/users' -Method Post -Headers $h -Body $createBody -ContentType 'application/json'
    $uid = $created.data
    $upd = @{ displayName = 'smoke-user-edited'; status = 1; orgId = 1; roleIds = @(3) } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/system/users/$uid" -Method Put -Headers $h -Body $upd -ContentType 'application/json' | Out-Null
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/system/users/$uid" -Method Delete -Headers $h | Out-Null
    try {
        Login $uname | Out-Null
        Record 'disable user blocks login' $false 'disabled user still logged in'
    } catch {
        Record 'disable user blocks login' $true "user=$uname disabled"
    }
} catch {
    Record 'user crud disable' $false $_.Exception.Message
}

try {
    $code = "ORG_SMOKE_$([guid]::NewGuid().ToString('N').Substring(0,6))"
    $orgBody = @{ orgCode = $code; orgName = 'smoke-org'; parentId = 1; orgType = 1 } | ConvertTo-Json -Compress
    $h = @{ Authorization = "Bearer $($admin.accessToken)" }
    $oid = (Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/system/orgs' -Method Post -Headers $h -Body $orgBody -ContentType 'application/json').data
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/system/orgs/$oid" -Method Delete -Headers $h | Out-Null
    Record 'org create delete' $true "orgId=$oid"
} catch {
    Record 'org create delete' $false $_.Exception.Message
}

try {
    $body = @{ refreshToken = $admin.refreshToken } | ConvertTo-Json -Compress
    $ref = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/auth/refresh' -Method Post -Body $body -ContentType 'application/json'
    Record 'refresh token' ($ref.data.accessToken.Length -gt 20) 'ok'
} catch {
    Record 'refresh token' $false $_.Exception.Message
}

Write-Host ''
$passed = ($results | Where-Object { $_.Pass }).Count
$total = $results.Count
Write-Host "Total: $passed / $total PASS"
$results | Format-Table -AutoSize
if ($passed -lt $total) { exit 1 }
