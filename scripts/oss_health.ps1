# 开源组件健康探活（不含 AEAI ESB）
$ErrorActionPreference = 'Continue'
$checks = @(
    @{ Name = 'Elasticsearch'; Url = 'http://localhost:9200/_cluster/health' },
    @{ Name = 'SeaweedFS'; Url = 'http://localhost:9333/dir/status' },
    @{ Name = 'MongoDB'; Url = $null; Port = 27017 },
    @{ Name = 'OpenMetadata'; Url = 'http://localhost:8585/api/v1/system/version' },
    @{ Name = 'DataEase'; Url = 'http://localhost:8100' },
    @{ Name = 'DolphinScheduler'; Url = 'http://localhost:12345/dolphinscheduler/actuator/health' },
    @{ Name = 'Kettle'; Url = 'http://localhost:18081'; Accept401 = $true },
    @{ Name = 'Canal'; Url = $null; Port = 19090 }
)

$results = @()
foreach ($c in $checks) {
    $ok = $false
    $detail = ''
    try {
        if ($c.Url) {
            try {
                $r = Invoke-WebRequest -Uri $c.Url -UseBasicParsing -TimeoutSec 8
                $ok = $r.StatusCode -ge 200 -and $r.StatusCode -lt 500
                $detail = "http=$($r.StatusCode)"
            } catch {
                if ($c.Accept401 -and $_.Exception.Response.StatusCode.value__ -eq 401) {
                    $ok = $true
                    $detail = 'http=401 (auth required)'
                } else {
                    $detail = $_.Exception.Message
                }
            }
        } elseif ($c.Port) {
            $conn = Get-NetTCPConnection -LocalPort $c.Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
            $ok = $null -ne $conn
            $detail = if ($ok) { "port=$($c.Port)" } else { 'not listening' }
        }
    } catch {
        $detail = $_.Exception.Message
    }
    $mark = if ($ok) { 'PASS' } else { 'FAIL' }
    Write-Host "[$mark] $($c.Name) - $detail"
    $results += [pscustomobject]@{ Component = $c.Name; Pass = $ok; Detail = $detail }
}

$fail = @($results | Where-Object { $_.Pass -eq $false }).Count
Write-Host ''
Write-Host "Summary: $($results.Count - $fail)/$($results.Count) up"
$results | Format-Table -AutoSize
if ($fail -gt 0) { exit 1 }
