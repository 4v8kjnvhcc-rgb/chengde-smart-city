-- A6：质量任务明细 + 任务扩展字段（列已存在则跳过）

CREATE TABLE IF NOT EXISTS gov_quality_task_detail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  rule_id BIGINT NOT NULL,
  target_table VARCHAR(128) NULL,
  target_column VARCHAR(128) NULL,
  check_type VARCHAR(32) NULL COMMENT 'NULL_CHECK/UNIQUENESS/ACCURACY/RECORD_COUNT',
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_qtask_detail_task (task_id),
  KEY idx_qtask_detail_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- description
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_quality_task' AND COLUMN_NAME = 'description'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE gov_quality_task ADD COLUMN description VARCHAR(512) NULL AFTER task_name',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- schedule_type
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_quality_task' AND COLUMN_NAME = 'schedule_type'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE gov_quality_task ADD COLUMN schedule_type VARCHAR(32) NOT NULL DEFAULT ''MANUAL'' COMMENT ''MANUAL/CRON'' AFTER last_message',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- cron_expr
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_quality_task' AND COLUMN_NAME = 'cron_expr'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE gov_quality_task ADD COLUMN cron_expr VARCHAR(64) NULL AFTER schedule_type',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- datasource_id
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_quality_task' AND COLUMN_NAME = 'datasource_id'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE gov_quality_task ADD COLUMN datasource_id BIGINT NULL AFTER cron_expr',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
