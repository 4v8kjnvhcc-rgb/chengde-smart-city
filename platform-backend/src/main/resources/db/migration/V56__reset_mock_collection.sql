-- 真实联调：移除平台库中由旧 P0 demo 迁移预置的“已汇聚 SUCCESS”台账。
-- 目的：
-- 1) 让直通/加工的 eligible tables 必须来自“真实 JDBC 登记 + Carte Kettle 汇聚”；
-- 2) 避免在未触发真实采集前，直接走元数据/质量/编目导致“假闭环”。

-- 清空预置 ODS 数据（表结构不删，避免影响探测/页面展示）。
TRUNCATE TABLE ods_enterprise_base;
TRUNCATE TABLE ods_project_base;

-- 取消样例登记表的物理映射与汇聚成功状态。
UPDATE ing_data_table
SET physical_table_name = NULL,
    collect_status = 'PENDING',
    last_collect_at = NULL,
    column_count = 0
WHERE table_code IN ('TBL_ENTERPRISE', 'TBL_PROJECT');

-- 取消样例汇聚任务成功标记，避免 eligible tables 误选。
UPDATE ing_ingest_task
SET status = 'IDLE',
    last_run_at = NULL,
    last_run_message = NULL,
    collected_rows = NULL,
    target_table = NULL
WHERE task_code IN ('TASK_TBL_T1', 'TASK_TBL_PROJECT');
