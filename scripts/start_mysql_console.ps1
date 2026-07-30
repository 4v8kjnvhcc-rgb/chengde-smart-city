# 不依赖 Windows 服务启动 MySQL（无需管理员；数据须已初始化）
# 用法：在一个终端运行本脚本并保持窗口打开；另开终端启动后端。

$MysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.4'
$IniFile = Join-Path $MysqlHome 'my.ini'
$MysqldExe = Join-Path $MysqlHome 'bin\mysqld.exe'
$DataDir = Join-Path $env:LOCALAPPDATA 'smart-city-mysql-data'

if (-not (Test-Path (Join-Path $DataDir 'auto.cnf'))) {
    throw 'Data not initialized. Run setup_mysql_windows.ps1 -ForceReinit as Administrator first.'
}

Write-Host "datadir: $DataDir"
Write-Host "config : $IniFile"
Write-Host 'Starting mysqld (Ctrl+C to stop)...'
Push-Location (Split-Path $MysqldExe -Parent)
& $MysqldExe --console
Pop-Location
