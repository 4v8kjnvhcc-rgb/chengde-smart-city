-- 库表接入：生命周期 / 版本快照 / 运行史 / DS 调度 ID

-- lifecycle + version + published meta + ds_schedule_id
SET @col_lifecycle := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_ingest_task' AND COLUMN_NAME = 'lifecycle_status'
);
SET @sql_lifecycle := IF(@col_lifecycle = 0,
  'ALTER TABLE ing_ingest_task ADD COLUMN lifecycle_status VARCHAR(32) NOT NULL DEFAULT ''DRAFT'' COMMENT ''生命周期：DRAFT/ONLINE/STARTED/STOPPED/OFFLINE'' AFTER enabled',
  'SELECT 1');
PREPARE stmt FROM @sql_lifecycle; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_ver := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_ingest_task' AND COLUMN_NAME = 'version_no'
);
SET @sql_ver := IF(@col_ver = 0,
  'ALTER TABLE ing_ingest_task ADD COLUMN version_no INT NOT NULL DEFAULT 0 COMMENT ''当前上线版本号'' AFTER lifecycle_status',
  'SELECT 1');
PREPARE stmt FROM @sql_ver; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_pub_by := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_ingest_task' AND COLUMN_NAME = 'published_by'
);
SET @sql_pub_by := IF(@col_pub_by = 0,
  'ALTER TABLE ing_ingest_task ADD COLUMN published_by VARCHAR(64) NULL COMMENT ''最近上线人'' AFTER version_no',
  'SELECT 1');
PREPARE stmt FROM @sql_pub_by; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_pub_at := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_ingest_task' AND COLUMN_NAME = 'published_at'
);
SET @sql_pub_at := IF(@col_pub_at = 0,
  'ALTER TABLE ing_ingest_task ADD COLUMN published_at DATETIME NULL COMMENT ''最近上线时间'' AFTER published_by',
  'SELECT 1');
PREPARE stmt FROM @sql_pub_at; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_ds_sch := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_ingest_task' AND COLUMN_NAME = 'ds_schedule_id'
);
SET @sql_ds_sch := IF(@col_ds_sch = 0,
  'ALTER TABLE ing_ingest_task ADD COLUMN ds_schedule_id INT NULL COMMENT ''DS 调度 ID'' AFTER ds_definition_code',
  'SELECT 1');
PREPARE stmt FROM @sql_ds_sch; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS ing_ingest_task_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  snapshot_json MEDIUMTEXT NOT NULL,
  change_summary VARCHAR(512) NULL,
  published_by VARCHAR(64) NULL,
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ingest_task_version (task_id, version_no),
  KEY idx_ingest_task_ver_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_ingest_task_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  trigger_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/SCHEDULE',
  run_status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
  schedule_result VARCHAR(32) NULL COMMENT '调度结果 SUCCESS/FAILED',
  collected_rows BIGINT NULL,
  insert_rows BIGINT NULL,
  update_rows BIGINT NULL DEFAULT 0,
  table_count INT NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  duration_ms BIGINT NULL,
  schedule_time DATETIME NULL,
  ds_instance_id BIGINT NULL,
  message VARCHAR(1024) NULL,
  error_detail VARCHAR(2048) NULL,
  log_text MEDIUMTEXT NULL,
  detail_json MEDIUMTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ingest_run_task_time (task_id, started_at),
  KEY idx_ingest_run_status (run_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 存量迁移：enabled=1 → STARTED；曾跑过 → ONLINE；否则保持 DRAFT
UPDATE ing_ingest_task
SET lifecycle_status = CASE
  WHEN enabled = 1 AND schedule_cron IS NOT NULL AND schedule_cron <> '' THEN 'STARTED'
  WHEN UPPER(IFNULL(status, '')) IN ('SUCCESS', 'PARTIAL', 'FAILED', 'RUNNING') THEN 'ONLINE'
  ELSE 'DRAFT'
END;