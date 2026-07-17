-- D：多表多源黄金路径
-- 1) 第二张异构汇聚样例表（项目信息，字段与企业表不同）
-- 2) 治理任务增加 fusion_spec_json 持久化声明式加工配置
-- 3) 汇聚任务/登记表补充索引，便于按 table_id 判定已汇聚

ALTER TABLE gov_governance_task
  ADD COLUMN fusion_spec_json MEDIUMTEXT NULL COMMENT '声明式加工配置 JSON' AFTER graph_json;

ALTER TABLE ing_ingest_task
  ADD KEY idx_ingest_task_source_status (source_id, status);

ALTER TABLE ing_data_table
  ADD KEY idx_data_table_collect (collect_status, status);

CREATE TABLE IF NOT EXISTS ods_project_base (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_code VARCHAR(64) NOT NULL,
  project_name VARCHAR(256) NULL,
  budget_amount DECIMAL(18,2) NULL,
  start_date DATE NULL,
  owner_org VARCHAR(128) NULL,
  district_code VARCHAR(12) NULL,
  UNIQUE KEY uk_ods_project_code (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='D多表直通/加工共享异构样例-项目信息';

INSERT INTO ods_project_base
  (project_code, project_name, budget_amount, start_date, owner_org, district_code)
VALUES
  ('PRJ-2026-001', '智慧城市底座一期', 1200.00, '2026-01-15', '市大数据局', '130802'),
  ('PRJ-2026-002', '政务数据归集试点', 800.00, '2026-03-01', '市发改委', '130803'),
  ('PRJ-2026-003', NULL, 350.00, '2026-04-10', '市工信局', '130804')
ON DUPLICATE KEY UPDATE
  project_name = VALUES(project_name),
  budget_amount = VALUES(budget_amount),
  start_date = VALUES(start_date),
  owner_org = VALUES(owner_org),
  district_code = VALUES(district_code);

-- 登记项目表（挂到同一示范数据源）
INSERT INTO ing_data_table
  (source_id, table_code, table_name, physical_table_name, modeling_mode, column_count, status, collect_status, last_collect_at, created_at)
SELECT s.id, 'TBL_PROJECT', '项目基础信息', 'ods_project_base', 'LOGICAL', 6, 'ACTIVE', 'SUCCESS', NOW(), NOW()
FROM ing_data_source s
WHERE s.source_code = 'DS_MYSQL_01'
  AND NOT EXISTS (
    SELECT 1 FROM ing_data_table t
    WHERE t.source_id = s.id AND t.table_code = 'TBL_PROJECT'
  );

-- 汇聚任务（关联 table_id）
INSERT INTO ing_ingest_task
  (task_code, task_name, channel_id, source_id, table_id, target_table, collected_rows, schedule_cron, status, last_run_at, last_run_message, created_at)
SELECT
  'TASK_TBL_PROJECT',
  '项目基础信息汇聚',
  c.id,
  s.id,
  t.id,
  'ods_project_base',
  3,
  '0 3 * * *',
  'SUCCESS',
  NOW(),
  'D多表样例已汇聚 rows=3',
  NOW()
FROM ing_ingest_channel c
JOIN ing_data_source s ON s.source_code = 'DS_MYSQL_01'
JOIN ing_data_table t ON t.source_id = s.id AND t.table_code = 'TBL_PROJECT'
WHERE c.channel_code = 'CH_TABLE'
  AND NOT EXISTS (SELECT 1 FROM ing_ingest_task x WHERE x.task_code = 'TASK_TBL_PROJECT');

UPDATE ing_ingest_task task
JOIN ing_data_source source ON source.source_code = 'DS_MYSQL_01'
JOIN ing_data_table data_table ON data_table.source_id = source.id AND data_table.table_code = 'TBL_PROJECT'
SET task.source_id = source.id,
    task.table_id = data_table.id,
    task.target_table = 'ods_project_base',
    task.collected_rows = 3,
    task.status = 'SUCCESS',
    task.last_run_at = NOW(),
    task.last_run_message = 'D多表样例已汇聚 rows=3'
WHERE task.task_code = 'TASK_TBL_PROJECT';
