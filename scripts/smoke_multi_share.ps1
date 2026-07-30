# D multi-table direct + processed share smoke (ASCII for Windows PowerShell 5)
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api/v1'
$Pass = 'Test@12345'

function Login() {
    $body = @{ username = 'sys_admin'; password = $Pass } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $body -ContentType 'application/json; charset=utf-8'
    return $r.data.accessToken
}
function GetApi($path, $token) {
    return Invoke-RestMethod -Uri "$base$path" -Headers @{ Authorization = "Bearer $token" }
}
function PostApi($path, $token, $payload = @{}) {
    $json = $payload | ConvertTo-Json -Depth 8 -Compress
    $r = Invoke-RestMethod -Uri "$base$path" -Method Post -Headers @{ Authorization = "Bearer $token" } `
        -Body $json -ContentType 'application/json'
    if ($null -ne $r.code -and [int]$r.code -ne 0) {
        throw $r.message
    }
    return $r
}

$token = Login
if (-not $token) { throw 'login failed' }

$tables = (GetApi '/governance/direct-share/eligible-tables' $token).data
if (@($tables).Count -lt 2) { throw "expected >=2 eligible tables, got $(@($tables).Count)" }
$enterprise = @($tables | Where-Object { $_.physicalTableName -eq 'ods_enterprise_base' })[0]
$project = @($tables | Where-Object { $_.physicalTableName -eq 'ods_project_base' })[0]
if (-not $enterprise -or -not $project) { throw 'missing enterprise/project eligible tables' }
Write-Host "[PASS] eligible enterprise=$($enterprise.tableId) project=$($project.tableId)"

# Direct path on project table
$tid = $project.tableId
$meta = (PostApi '/governance/direct-share/metadata/collect' $token @{ tableId = $tid }).data
if ($meta.status -ne 'SUCCESS' -or -not $meta.entryCode) { throw "direct metadata failed: $($meta | ConvertTo-Json -Compress)" }
$q = (PostApi '/governance/direct-share/quality/run' $token @{
    tableId = $tid
    rules = @(@{ column = 'project_name'; checkType = 'NULL_CHECK' })
}).data
if ($null -eq $q.score) { throw "direct quality failed" }
$cat = (PostApi '/governance/direct-share/catalog/publish' $token @{ tableId = $tid }).data
if ($cat.publishStatus -ne 'PUBLISHED' -or $cat.sourcePathType -ne 'DIRECT') { throw "direct catalog failed" }
$auth = (PostApi '/governance/direct-share/subscription/authorize' $token @{ tableId = $tid; shareMode = 'DB_SYNC' }).data
if ($auth.authorizationStatus -ne 'ACTIVE') { throw "direct auth failed" }
Write-Host "[PASS] direct project entry=$($meta.entryCode) score=$($q.score) auth=$($auth.authorizationCode)"

# Reject illegal fusion expression
try {
    PostApi '/governance/processed-share/fusion/preview' $token @{
        tableId = $project.tableId
        fusionSpec = @{
            sourceTable = 'ods_project_base'
            targetTable = 'dws_project_theme'
            select = @(@{ expr = 'DROP TABLE x'; 'as' = 'evil' })
        }
    } | Out-Null
    throw 'illegal expression should be rejected'
} catch {
    if ("$($_.Exception.Message)" -match 'illegal expression should be rejected') { throw }
    Write-Host '[PASS] illegal fusion expression rejected'
}

# Processed path on project table
$fusion = (PostApi '/governance/processed-share/fusion/run' $token @{
    tableId = $project.tableId
    fusionSpec = @{
        sourceTable = 'ods_project_base'
        targetTable = 'dws_project_theme'
        writeMode = 'TRUNCATE_INSERT'
        filterSql = 'project_name IS NOT NULL'
        select = @(
            @{ expr = 'project_code'; 'as' = 'project_code' },
            @{ expr = 'project_name'; 'as' = 'project_name' },
            @{ expr = 'CASE_LEVEL(budget_amount,1000,500)'; 'as' = 'budget_level' },
            @{ expr = 'owner_org'; 'as' = 'owner_org' }
        )
    }
}).data
if ($fusion.status -ne 'SUCCESS' -or [long]$fusion.producedRows -lt 1) { throw "fusion failed: $($fusion | ConvertTo-Json -Compress)" }
if ([long]$fusion.producedRows -ge [long]$project.physicalRows) { throw 'cleansing should drop null-name row' }

$pmeta = (PostApi '/governance/processed-share/metadata/collect' $token @{
    tableId = $project.tableId
    producedTable = 'dws_project_theme'
}).data
if ($pmeta.status -ne 'SUCCESS' -or -not $pmeta.lineageFrom) { throw 'processed metadata failed' }
$pq = (PostApi '/governance/processed-share/quality/run' $token @{
    tableId = $project.tableId
    producedTable = 'dws_project_theme'
}).data
if ($null -eq $pq.score) { throw 'processed quality failed' }
$pcat = (PostApi '/governance/processed-share/catalog/publish' $token @{
    tableId = $project.tableId
    producedTable = 'dws_project_theme'
}).data
if ($pcat.sourcePathType -ne 'PROCESSED' -or $pcat.publishStatus -ne 'PUBLISHED') { throw 'processed catalog failed' }
$pauth = (PostApi '/governance/processed-share/subscription/authorize' $token @{
    tableId = $project.tableId
    producedTable = 'dws_project_theme'
    shareMode = 'DB_SYNC'
}).data
if ($pauth.authorizationStatus -ne 'ACTIVE') { throw 'processed auth failed' }

$a360 = (GetApi "/governance/asset/360?entryCode=$($pmeta.entryCode)" $token).data
if (@($a360.lineage.upstream).Count -lt 1) { throw 'asset360 missing lineage' }
Write-Host "[PASS] processed project produced=$($fusion.producedTable) rows=$($fusion.producedRows) entry=$($pmeta.entryCode) score=$($pq.score)"
Write-Host '[PASS] multi-table share golden path complete'
