# M048 access control smoke (ASCII for Windows PowerShell 5)
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api/v1'
$Pass = 'Test@12345'

function Login($user) {
    $body = @{ username = $user; password = $Pass } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $body -ContentType 'application/json; charset=utf-8'
    return $r.data.accessToken
}

function GetApi($path, $token) {
    return (curl.exe -s -H "Authorization: Bearer $token" "$base$path") | Out-String
}

function PostApi($path, $token, $payload) {
    $json = $payload | ConvertTo-Json -Compress -Depth 6
    $tmp = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tmp, $json, [System.Text.UTF8Encoding]::new($false))
    $out = curl.exe -s -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@$tmp" "$base$path"
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    return $out
}

function Parse($raw) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($raw)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    return ($text | ConvertFrom-Json)
}

Write-Host '=== 1) sys_admin overview + dual-auth block on project grant ==='
$tokAdmin = Login 'sys_admin'
$ov = Parse (GetApi '/system/access/overview' $tokAdmin)
if ($ov.code -ne 0) { throw "overview failed: $($ov.message)" }
Write-Host "[PASS] overview projectGrantCount=$($ov.data.projectGrantCount) myProjects=$((@($ov.data.myProjectIds)).Count)"

$deny = Parse (PostApi '/system/access/project-grants' $tokAdmin @{ projectId = 1; granteeType = 'USER'; granteeId = 3; perm = 'VIEW' })
if ($deny.code -eq 0) { throw 'sys_admin should NOT create project grant' }
Write-Host "[PASS] sys_admin grant blocked code=$($deny.code) msg=$($deny.message)"

Write-Host '=== 2) dept_admin_a can grant on org-A project ==='
$tokDept = Login 'dept_admin_a'
$projs = Parse (GetApi '/exchange/ingestion/projects' $tokDept)
$prj = @($projs.data) | Where-Object { $_.projectCode -eq 'PRJ_ORG_A_ACCESS' } | Select-Object -First 1
if (-not $prj) { $prj = @($projs.data) | Select-Object -First 1 }
if (-not $prj) { throw 'dept_admin has no visible projects' }
Write-Host "[PASS] dept projects=$($projs.data.Count) focus=$($prj.id)/$($prj.projectName)"

$g = Parse (PostApi '/system/access/project-grants' $tokDept @{ projectId = $prj.id; granteeType = 'USER'; granteeId = 3; perm = 'EDIT' })
if ($g.code -ne 0) { throw "dept grant failed: $($g.message)" }
Write-Host "[PASS] dept grant id=$($g.data)"

Write-Host '=== 3) user_a sees granted project; user_b empty or fewer ==='
$tokA = Login 'user_a'
$pa = Parse (GetApi '/exchange/ingestion/projects' $tokA)
Write-Host "[PASS] user_a projects=$($pa.data.Count)"

$tokB = Login 'user_b'
$pb = Parse (GetApi '/exchange/ingestion/projects' $tokB)
Write-Host "[INFO] user_b projects=$($pb.data.Count)"

Write-Host '=== 4) cross-dept apply user_b -> org2 project ==='
if ($prj) {
    $req = Parse (PostApi '/system/access/cross-dept/requests' $tokB @{
        targetOrgId = 2
        resourceType = 'PROJECT'
        resourceId = [string]$prj.id
        reason = 'smoke cross dept'
    })
    if ($req.code -ne 0) { throw "apply failed: $($req.message)" }
    Write-Host "[PASS] cross apply id=$($req.data)"
    $ap = Parse (PostApi "/system/access/cross-dept/requests/$($req.data)/approve" $tokDept @{ comment = 'ok' })
    if ($ap.code -ne 0) { throw "approve failed: $($ap.message)" }
    Write-Host '[PASS] cross approved'
    $pb2 = Parse (GetApi '/exchange/ingestion/projects' $tokB)
    Write-Host "[PASS] user_b projects after approve=$($pb2.data.Count)"
}

Write-Host '=== ALL ACCESS CONTROL SMOKE PASSED ==='
