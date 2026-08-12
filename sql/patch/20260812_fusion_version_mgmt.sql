-- 手工部署补丁（与 Flyway V194 同源）：融合版本管理
-- 目标库：smart_city（控制面）
-- 可重复执行：列存在则跳过；表 IF NOT EXISTS

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_fusion_script' AND COLUMN_NAME = 'locked_by');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_fusion_script ADD COLUMN locked_by VARCHAR(64) NULL COMMENT ''锁定人'' AFTER last_message',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_fusion_script' AND COLUMN_NAME = 'locked_at');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_fusion_script ADD COLUMN locked_at DATETIME NULL COMMENT ''锁定时间'' AFTER locked_by',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_fusion_script' AND COLUMN_NAME = 'env_scope');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_fusion_script ADD COLUMN env_scope VARCHAR(16) NOT NULL DEFAULT ''DEV'' COMMENT ''DEV/PROD'' AFTER locked_at',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_fusion_script' AND COLUMN_NAME = 'ds_project_code');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_fusion_script ADD COLUMN ds_project_code BIGINT NULL AFTER env_scope',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_fusion_script' AND COLUMN_NAME = 'ds_definition_code');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_fusion_script ADD COLUMN ds_definition_code BIGINT NULL AFTER ds_project_code',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_fusion_script' AND COLUMN_NAME = 'prod_deployed_version');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_fusion_script ADD COLUMN prod_deployed_version INT NULL COMMENT ''已部署到生产调度的版本号'' AFTER ds_definition_code',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_fusion_script' AND COLUMN_NAME = 'prod_deployed_at');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_fusion_script ADD COLUMN prod_deployed_at DATETIME NULL AFTER prod_deployed_version',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_governance_task' AND COLUMN_NAME = 'version_no');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_governance_task ADD COLUMN version_no INT NOT NULL DEFAULT 1 COMMENT ''当前版本号'' AFTER last_message',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_governance_task' AND COLUMN_NAME = 'env_scope');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_governance_task ADD COLUMN env_scope VARCHAR(16) NOT NULL DEFAULT ''DEV'' COMMENT ''DEV/PROD'' AFTER version_no',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_cross_pipeline' AND COLUMN_NAME = 'locked_by');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_cross_pipeline ADD COLUMN locked_by VARCHAR(64) NULL AFTER last_message',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_cross_pipeline' AND COLUMN_NAME = 'locked_at');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_cross_pipeline ADD COLUMN locked_at DATETIME NULL AFTER locked_by',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_cross_pipeline' AND COLUMN_NAME = 'version_no');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_cross_pipeline ADD COLUMN version_no INT NOT NULL DEFAULT 1 AFTER locked_at',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_cross_pipeline' AND COLUMN_NAME = 'env_scope');
SET @sql := IF(@col = 0,
  'ALTER TABLE gov_cross_pipeline ADD COLUMN env_scope VARCHAR(16) NOT NULL DEFAULT ''DEV'' COMMENT ''DEV/PROD'' AFTER version_no',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS gov_fusion_workflow_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  object_type VARCHAR(32) NOT NULL COMMENT 'TASK=融合任务 PIPELINE=跨模块流水线',
  object_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  snapshot_json MEDIUMTEXT NOT NULL COMMENT '画布/步骤等快照',
  change_summary VARCHAR(512) NULL,
  env_scope VARCHAR(16) NOT NULL DEFAULT 'DEV',
  published_by VARCHAR(64) NULL,
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fusion_wf_ver (object_type, object_id, version_no),
  KEY idx_fusion_wf_ver_obj (object_type, object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
