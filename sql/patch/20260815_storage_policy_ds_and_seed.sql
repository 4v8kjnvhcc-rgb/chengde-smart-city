-- 备份策略：DolphinScheduler 编排字段 + 3 条可演示策略/产物
-- 目标库: smart_city；策略按 policy_code 幂等，产物按 file_name 幂等

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rc_storage_policy' AND COLUMN_NAME = 'ds_project_code');
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE rc_storage_policy
     ADD COLUMN ds_project_code BIGINT NULL COMMENT ''DS 项目编码'' AFTER last_run_message,
     ADD COLUMN ds_definition_code BIGINT NULL COMMENT ''DS 流程定义编码'' AFTER ds_project_code,
     ADD COLUMN ds_schedule_id INT NULL COMMENT ''DS 调度 ID'' AFTER ds_definition_code,
     ADD COLUMN ds_publish_status VARCHAR(32) NULL COMMENT ''DRAFT/PUBLISHED/OFFLINE'' AFTER ds_schedule_id',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO rc_managed_table (theme_id, physical_table, meta_entry_code, catalog_resource_code, status, created_by)
SELECT t.id, 'dws_enterprise_theme', 'TBL_FUS_DWS_ENTERPRISE_THEME', NULL, 'ACTIVE', 'sys_admin'
FROM rc_theme_library t
WHERE t.theme_code = 'THEME_ENTERPRISE'
  AND NOT EXISTS (SELECT 1 FROM rc_managed_table WHERE physical_table = 'dws_enterprise_theme');

-- 绑定首个 ACTIVE 纳管表
SET @mt_id := (SELECT id FROM rc_managed_table WHERE status = 'ACTIVE' ORDER BY id LIMIT 1);
SET @mt_theme := (SELECT theme_id FROM rc_managed_table WHERE id = @mt_id);
SET @mt_table := (SELECT physical_table FROM rc_managed_table WHERE id = @mt_id);

INSERT INTO rc_storage_policy (
  policy_code, policy_name, action_type, retention_days, theme_id, managed_table_id,
  storage_strategy, table_rule, compress_enabled, compress_type,
  schedule_enabled, schedule_cron, status, ds_publish_status
)
SELECT 'POL_BACKUP_DAILY', '日备份-企业主题表', 'BACKUP', 7, @mt_theme, @mt_id,
       'LOCAL', '全表逻辑备份', 0, 'NONE',
       1, '0 0 2 * * ?', 'ACTIVE', 'DRAFT'
FROM DUAL
WHERE @mt_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM rc_storage_policy WHERE policy_code = 'POL_BACKUP_DAILY');

INSERT INTO rc_storage_policy (
  policy_code, policy_name, action_type, retention_days, theme_id, managed_table_id,
  storage_strategy, table_rule, compress_enabled, compress_type,
  schedule_enabled, schedule_cron, status, ds_publish_status
)
SELECT 'POL_BACKUP_WEEKLY', '周备份-企业主题表', 'BACKUP', 30, @mt_theme, @mt_id,
       'NAS', '周全量', 0, 'NONE',
       1, '0 0 3 ? * MON', 'ACTIVE', 'DRAFT'
FROM DUAL
WHERE @mt_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM rc_storage_policy WHERE policy_code = 'POL_BACKUP_WEEKLY');

INSERT INTO rc_storage_policy (
  policy_code, policy_name, action_type, retention_days, theme_id, managed_table_id,
  storage_strategy, table_rule, compress_enabled, compress_type,
  schedule_enabled, schedule_cron, status, ds_publish_status
)
SELECT 'POL_BACKUP_MANUAL', '手工备份-企业主题表', 'BACKUP', 14, @mt_theme, @mt_id,
       'LOCAL', '验收手工执行', 0, 'NONE',
       0, NULL, 'ACTIVE', 'DRAFT'
FROM DUAL
WHERE @mt_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM rc_storage_policy WHERE policy_code = 'POL_BACKUP_MANUAL');

INSERT INTO rc_backup_artifact (
  artifact_type, job_id, managed_table_id, physical_table, file_path, storage_location,
  file_name, row_count, byte_size, status, message, created_by, created_at
)
SELECT 'BACKUP', 0, @mt_id, @mt_table,
       CONCAT('data/nas-demo/backups/', @mt_table, '_seed_daily.cdbak'),
       CONCAT('LOCAL://backups/', @mt_table, '_seed_daily.cdbak'),
       CONCAT(@mt_table, '_seed_daily.cdbak'),
       128, 24576, 'SUCCESS', '验收种子产物（日备份）', 'sys_admin', DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM DUAL
WHERE @mt_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM rc_backup_artifact WHERE file_name = CONCAT(@mt_table, '_seed_daily.cdbak'));

INSERT INTO rc_backup_artifact (
  artifact_type, job_id, managed_table_id, physical_table, file_path, storage_location,
  file_name, row_count, byte_size, status, message, created_by, created_at
)
SELECT 'BACKUP', 0, @mt_id, @mt_table,
       CONCAT('data/nas-demo/backups/', @mt_table, '_seed_weekly.cdbak'),
       CONCAT('NAS://backups/', @mt_table, '_seed_weekly.cdbak'),
       CONCAT(@mt_table, '_seed_weekly.cdbak'),
       256, 49152, 'SUCCESS', '验收种子产物（周备份）', 'sys_admin', DATE_SUB(NOW(), INTERVAL 3 DAY)
FROM DUAL
WHERE @mt_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM rc_backup_artifact WHERE file_name = CONCAT(@mt_table, '_seed_weekly.cdbak'));

INSERT INTO rc_backup_artifact (
  artifact_type, job_id, managed_table_id, physical_table, file_path, storage_location,
  file_name, row_count, byte_size, status, message, created_by, created_at
)
SELECT 'BACKUP', 0, @mt_id, @mt_table,
       CONCAT('data/nas-demo/backups/', @mt_table, '_seed_manual.cdbak'),
       CONCAT('LOCAL://backups/', @mt_table, '_seed_manual.cdbak'),
       CONCAT(@mt_table, '_seed_manual.cdbak'),
       64, 12288, 'SUCCESS', '验收种子产物（手工备份）', 'sys_admin', NOW()
FROM DUAL
WHERE @mt_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM rc_backup_artifact WHERE file_name = CONCAT(@mt_table, '_seed_manual.cdbak'));
