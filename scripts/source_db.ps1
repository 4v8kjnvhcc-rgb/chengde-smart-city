# 独立业务源库管理：启动 / 重置 / 数据基线校验
# 用法:
#   powershell -File scripts\source_db.ps1 up      # 启动 source-mysql
#   powershell -File scripts\source_db.ps1 reset   # 销毁并重建（重新导入基线数据）
#   powershell -File scripts\source_db.ps1 verify  # 校验行数与脏数据基线
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'reset', 'verify')]
    [string]$Action = 'verify'
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$compose = 'compose/oss-stack.yml'
$svc = 'source-mysql'
$container = 'sc_source_mysql'
$rootPw = 'source_root'

function Wait-Healthy {
    for ($i = 0; $i -lt 30; $i++) {
        $st = & docker inspect --format '{{.State.Health.Status}}' $container 2>$null
        if ($st -eq 'healthy') { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Invoke-SourceSql([string]$sql) {
    & docker exec -i $container mysql -uroot -p"$rootPw" -N -B biz_source -e $sql
}

switch ($Action) {
    'up' {
        Write-Host '==== 启动 source-mysql ===='
        & docker compose -f $compose --profile source up -d $svc
        if (Wait-Healthy) { Write-Host 'source-mysql healthy' } else { Write-Host 'WARN: source-mysql 未就绪'; exit 1 }
    }
    'reset' {
        Write-Host '==== 重置 source-mysql（销毁数据卷）===='
        & docker compose -f $compose --profile source rm -sf $svc
        & docker volume rm smart-city-oss_sc_source_mysql_data 2>$null
        & docker compose -f $compose --profile source up -d $svc
        if (Wait-Healthy) { Write-Host 'source-mysql 重建完成' } else { Write-Host 'WARN: source-mysql 未就绪'; exit 1 }
    }
    'verify' {
        Write-Host '==== 校验源库数据基线 ===='
        $entTotal = (Invoke-SourceSql 'SELECT COUNT(*) FROM ent_master;').Trim()
        $entNull = (Invoke-SourceSql 'SELECT COUNT(*) FROM ent_master WHERE ent_name IS NULL;').Trim()
        $entDup = (Invoke-SourceSql 'SELECT COUNT(*) FROM (SELECT credit_code FROM ent_master GROUP BY credit_code HAVING COUNT(*)>1) d;').Trim()
        $projTotal = (Invoke-SourceSql 'SELECT COUNT(*) FROM proj_construction;').Trim()
        $projNull = (Invoke-SourceSql 'SELECT COUNT(*) FROM proj_construction WHERE proj_name IS NULL;').Trim()
        $projBad = (Invoke-SourceSql "SELECT COUNT(*) FROM proj_construction WHERE status_code NOT IN ('ONGOING','DONE','PAUSED');").Trim()

        Write-Host "ent_master        总行数=$entTotal (期望5) 空名称=$entNull (期望1) 重复信用码=$entDup (期望1)"
        Write-Host "proj_construction 总行数=$projTotal (期望5) 空名称=$projNull (期望1) 非法状态码=$projBad (期望1)"

        $ok = ($entTotal -eq '5' -and $entNull -eq '1' -and $entDup -eq '1' -and `
               $projTotal -eq '5' -and $projNull -eq '1' -and $projBad -eq '1')
        if ($ok) { Write-Host 'PASS: 源库基线符合预期' } else { Write-Host 'FAIL: 源库基线与预期不符'; exit 1 }
    }
}
