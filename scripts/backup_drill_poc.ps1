# MS8 backup drill POC: mysqldump + SHA256 marker (local demo, not production RTO<=4h)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$outDir = Join-Path $root 'data\nas-demo\mysql-drill'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dumpFile = Join-Path $outDir "smart_city-$stamp.sql"
$hashFile = Join-Path $outDir "smart_city-$stamp.sha256"

$db = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { 'smart_city' }
$user = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { 'root' }
$mysqlHost = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { 'localhost' }
$port = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { '3306' }
$pass = $env:MYSQL_PASSWORD

$mysqldump = Get-Command mysqldump -ErrorAction SilentlyContinue
if (-not $mysqldump) {
    $candidates = @(
        'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe',
        'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe'
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { $mysqldump = $c; break }
    }
}
if (-not $mysqldump) {
    Write-Host 'FAIL: mysqldump not found. Install MySQL client or add to PATH.'
    exit 1
}

$dumpArgs = @("-h$mysqlHost", "-P$port", "-u$user", '--single-transaction', '--routines', $db)
if ($pass) { $dumpArgs = @("-p$pass") + $dumpArgs }

Write-Host "Dumping $db -> $dumpFile"
& $mysqldump @dumpArgs | Set-Content -Path $dumpFile -Encoding utf8
if (-not (Test-Path $dumpFile) -or (Get-Item $dumpFile).Length -lt 100) {
    Write-Host 'FAIL: dump empty'
    exit 1
}

$hash = (Get-FileHash -Algorithm SHA256 -Path $dumpFile).Hash
Set-Content -Path $hashFile -Value "$hash  $(Split-Path $dumpFile -Leaf)" -Encoding ascii
Write-Host "PASS: backup drill dump ok"
Write-Host "  file=$dumpFile"
Write-Host "  size=$((Get-Item $dumpFile).Length) bytes"
Write-Host "  sha256=$hash"
Write-Host "Note: full RTO<=4h restore drill requires staging env +甲方确认窗口"
exit 0
