-- 治理/融合任务：DolphinScheduler 调度字段（对齐 ing_ingest_task）
SET @col_ds_proj := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_governance_task' AND COLUMN_NAME = 'ds_project_code'
);
SET @sql_ds_proj := IF(@col_ds_proj = 0,
  'ALTER TABLE gov_governance_task ADD COLUMN ds_project_code BIGINT NULL COMMENT ''DS 项目 code'' AFTER next_run_at',
  'SELECT 1');
PREPARE stmt FROM @sql_ds_proj; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_ds_def := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_governance_task' AND COLUMN_NAME = 'ds_definition_code'
);
SET @sql_ds_def := IF(@col_ds_def = 0,
  'ALTER TABLE gov_governance_task ADD COLUMN ds_definition_code BIGINT NULL COMMENT ''DS 流程定义 code'' AFTER ds_project_code',
  'SELECT 1');
PREPARE stmt FROM @sql_ds_def; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_ds_sch := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_governance_task' AND COLUMN_NAME = 'ds_schedule_id'
);
SET @sql_ds_sch := IF(@col_ds_sch = 0,
  'ALTER TABLE gov_governance_task ADD COLUMN ds_schedule_id INT NULL COMMENT ''DS 调度 ID'' AFTER ds_definition_code',
  'SELECT 1');
PREPARE stmt FROM @sql_ds_sch; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_ds_inst := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_governance_task' AND COLUMN_NAME = 'ds_instance_id'
);
SET @sql_ds_inst := IF(@col_ds_inst = 0,
  'ALTER TABLE gov_governance_task ADD COLUMN ds_instance_id BIGINT NULL COMMENT ''最近 DS 实例 ID'' AFTER ds_schedule_id',
  'SELECT 1');
PREPARE stmt FROM @sql_ds_inst; EXECUTE stmt; DEALLOCATE PREPARE stmt;
