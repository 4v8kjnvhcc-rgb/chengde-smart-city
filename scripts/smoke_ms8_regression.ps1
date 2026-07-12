# MS8 regression: run all milestone + L1 PoC smoke scripts sequentially
# OSS integration (smoke_oss_integration.ps1) is optional — requires Docker + INTEGRATION_ENABLED=true
param(
    [switch]$IncludeOss
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
if (-not $root) { $root = (Get-Location).Path }
Set-Location $root

$groups = @(
    @{ Label = 'MS1 Auth/RBAC'; Script = 'scripts\smoke_ms1_auth_rbac.ps1'; Cases = 15 },
    @{ Label = 'D05 Catalog M001-M215'; Script = 'scripts\smoke_d05_catalog.ps1'; Cases = 5 },
    @{ Label = 'MS2 E2E Demos'; Script = 'scripts\smoke_e2e_demos.ps1'; Cases = 7 },
    @{ Label = 'M027-M030 Assessment'; Script = 'scripts\smoke_assessment.ps1'; Cases = 3 },
    @{ Label = 'M020-M026 Supply/Demand'; Script = 'scripts\smoke_supply.ps1'; Cases = 6 },
    @{ Label = 'M031-M036 Portal'; Script = 'scripts\smoke_portal.ps1'; Cases = 4 },
    @{ Label = 'M037-M077 Ingestion'; Script = 'scripts\smoke_ingestion.ps1'; Cases = 6 },
    @{ Label = 'M078-M122 Governance'; Script = 'scripts\smoke_governance.ps1'; Cases = 6 },
    @{ Label = 'M123-M138 Unstructured+RC'; Script = 'scripts\smoke_ms4_ms5.ps1'; Cases = 4 },
    @{ Label = 'M139-M151 Analytics Support+BI'; Script = 'scripts\smoke_analytics.ps1'; Cases = 8 },
    @{ Label = 'M152-M209 Domain Models'; Script = 'scripts\smoke_analytics_domains.ps1'; Cases = 7 },
    @{ Label = 'MS6-MS7 Models/Embed/DS'; Script = 'scripts\smoke_ms6_ms7.ps1'; Cases = 5 },
    @{ Label = 'MS3-MS5 Legacy API'; Script = 'scripts\smoke_ms3_ms5.ps1'; Cases = 4 }
)

if ($IncludeOss) {
    $groups += @{ Label = 'OSS Integration'; Script = 'scripts\smoke_oss_integration.ps1'; Cases = 12 }
}

$results = @()
$failed = 0
$totalCases = 0
foreach ($g in $groups) { $totalCases += [int]$g.Cases }

foreach ($g in $groups) {
    $s = $g.Script
    Write-Host ''
    Write-Host "======== $($g.Label) :: $s ========"
    if (-not (Test-Path $s)) {
        $failed++
        $results += [pscustomobject]@{ Group = $g.Label; Script = $s; Pass = $false; Detail = 'script missing' }
        Write-Host "FAILED: $s (missing)"
        continue
    }
    & powershell -ExecutionPolicy Bypass -File $s
    $exit = $LASTEXITCODE
    if ($null -eq $exit) { $exit = 0 }
    if ($exit -ne 0) {
        $failed++
        $results += [pscustomobject]@{ Group = $g.Label; Script = $s; Pass = $false; Detail = "exit=$exit" }
        Write-Host "FAILED: $s (exit=$exit)"
    } else {
        $results += [pscustomobject]@{ Group = $g.Label; Script = $s; Pass = $true; Detail = "$($g.Cases) cases" }
        Write-Host "OK: $s"
    }
}

Write-Host ''
Write-Host '======== MS8 REGRESSION SUMMARY ========'
$results | Format-Table -AutoSize
$passCount = ($results | Where-Object { $_.Pass }).Count
Write-Host "Scripts: $passCount/$($results.Count) passed | Estimated cases: $totalCases"
if ($failed -gt 0) {
    Write-Host "MS8 regression FAILED: $failed script(s)"
    exit 1
}
Write-Host 'MS8 regression PASSED: all smoke scripts OK'
exit 0
