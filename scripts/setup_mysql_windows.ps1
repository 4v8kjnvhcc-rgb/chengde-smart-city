# Install / repair MySQL 8.4 Windows service (run PowerShell as Administrator)
param(
    [switch]$ForceReinit
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$MysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.4'
$MysqlBin = Join-Path $MysqlHome 'bin'
$ServiceName = 'MySQL84'
$DataDir = Join-Path $env:LOCALAPPDATA 'smart-city-mysql-data'
$IniFile = Join-Path $MysqlHome 'my.ini'
$MysqldExe = Join-Path $MysqlBin 'mysqld.exe'
$MysqlExe = Join-Path $MysqlBin 'mysql.exe'

function Test-ServiceExists($name) {
    return [bool](sc.exe query $name 2>$null | Select-String 'SERVICE_NAME')
}

if (-not (Test-Path $MysqldExe)) {
    throw "mysqld not found: $MysqldExe"
}

$IniContent = @"
[mysqld]
basedir=$($MysqlHome -replace '\\', '/')
datadir=$($DataDir -replace '\\', '/')
port=3306
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci

[client]
port=3306
default-character-set=utf8mb4
"@

$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($IniFile, $IniContent, $utf8NoBom)
Write-Host "Config: $IniFile"

if ($ForceReinit -and (Test-Path $DataDir)) {
    Write-Host "Removing data directory: $DataDir"
    Remove-Item -LiteralPath $DataDir -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

$initialized = Test-Path (Join-Path $DataDir 'auto.cnf')

if (-not $initialized) {
    Write-Host '[1/4] Initializing data directory (root empty password)...'
    Push-Location $MysqlBin
    & $MysqldExe --initialize-insecure --console
    Pop-Location
    if (-not (Test-Path (Join-Path $DataDir 'auto.cnf'))) {
        throw 'Initialize failed. Check error log in data directory.'
    }
} else {
    Write-Host '[1/4] Data directory OK, skip initialize.'
}

if (Test-ServiceExists $ServiceName) {
    Write-Host '[2/4] Removing old service registration...'
    try { Stop-Service $ServiceName -Force -ErrorAction SilentlyContinue } catch {}
    Push-Location $MysqlBin
    & $MysqldExe --remove $ServiceName
    Pop-Location
    Start-Sleep -Seconds 2
} else {
    Write-Host '[2/4] No existing service.'
}

Write-Host "[3/4] Installing service $ServiceName ..."
Push-Location $MysqlBin
$installOut = & $MysqldExe --install $ServiceName 2>&1 | Out-String
Pop-Location
Write-Host $installOut.Trim()

if (-not (Test-ServiceExists $ServiceName)) {
    Write-Host 'mysqld --install did not register service, trying sc.exe create...'
    $binPath = "`"$MysqldExe`" --defaults-file=`"$IniFile`""
    sc.exe create $ServiceName binPath= $binPath start= auto DisplayName= $ServiceName | Out-String | Write-Host
}

if (-not (Test-ServiceExists $ServiceName)) {
    throw 'Service install failed. Ensure PowerShell is Administrator (not only Cursor terminal).'
}

Write-Host '[4/4] Starting MySQL...'
Start-Service -Name $ServiceName
Start-Sleep -Seconds 3
$status = (Get-Service $ServiceName).Status
if ($status -ne 'Running') {
    $err = Get-ChildItem "$DataDir\*.err" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($err) { Write-Host '--- error log tail ---'; Get-Content $err.FullName -Tail 20 }
    throw "MySQL service state: $status"
}

$sqlFile = Join-Path $Root 'scripts\setup_smart_city.sql'
Write-Host 'MySQL84 is running on port 3306.'
Write-Host "Next: Get-Content '$sqlFile' | & '$MysqlExe' -u root"
