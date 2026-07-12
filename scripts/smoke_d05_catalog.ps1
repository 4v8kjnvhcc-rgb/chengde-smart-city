# D05 catalog smoke: 215 modules API + menu seeds
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
    $r = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $body -ContentType 'application/json; charset=utf-8'
    return $r.data
}

function GetApi($path, $token) {
    $h = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$base$path" -Headers $h
}

function FlattenMenus($nodes) {
    $flat = New-Object System.Collections.Generic.List[object]
    function Walk($list) {
        foreach ($n in $list) {
            [void]$flat.Add($n)
            if ($n.children -and $n.children.Count -gt 0) {
                Walk $n.children
            }
        }
    }
    Walk $nodes
    return $flat
}

try {
    $login = Login
    $token = $login.accessToken
    Record 'login' ($token -and $token.Length -gt 20) $login.user.username
} catch {
    Record 'login' $false $_.Exception.Message
    exit 1
}

try {
    $summary = GetApi '/catalog/summary' $token
    Record 'catalog-count' ($summary.data.moduleCount -eq 215) "count=$($summary.data.moduleCount)"
} catch {
    Record 'catalog-count' $false $_.Exception.Message
}

try {
    $m = GetApi '/catalog/modules/M161' $token
    Record 'catalog-detail' ($m.data.mCode -eq 'M161') $m.data.moduleName
} catch {
    Record 'catalog-detail' $false $_.Exception.Message
}

try {
    $list = GetApi '/catalog/modules?platform=analytics&status=poc' $token
    $cnt = @($list.data).Count
    Record 'catalog-filter' ($cnt -gt 30) "poc-analytics=$cnt"
} catch {
    Record 'catalog-filter' $false $_.Exception.Message
}

try {
    $menus = GetApi '/system/menus/me' $token
    $flat = FlattenMenus $menus.data
    $d05 = @($flat | Where-Object { $_.path -like '/modules/*' }).Count
    Record 'menu-modules' ($d05 -ge 215) "module-menus=$d05"
} catch {
    Record 'menu-modules' $false $_.Exception.Message
}

$fail = @($results | Where-Object { $_.Pass -eq $false }).Count
Write-Host ''
Write-Host "Summary: $($results.Count - $fail)/$($results.Count) passed"
if ($fail -gt 0) { exit 1 }
