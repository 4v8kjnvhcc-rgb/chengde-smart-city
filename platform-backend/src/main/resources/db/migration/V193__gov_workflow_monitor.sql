-- 工作流实时监控：流水线优先级 + 告警通道/台账

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gov_cross_pipeline'
    AND COLUMN_NAME = 'priority'
);
SET @sql := IF(@col_exists = 0,
  'ALTER TABLE gov_cross_pipeline ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT ''MEDIUM'' COMMENT ''HIGHEST/HIGH/MEDIUM/LOW'' AFTER schedule_status',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS gov_workflow_alert_channel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  mail_enabled TINYINT NOT NULL DEFAULT 0,
  mail_receivers VARCHAR(1024) NULL,
  sms_enabled TINYINT NOT NULL DEFAULT 0,
  sms_phones VARCHAR(512) NULL,
  sms_gateway_url VARCHAR(512) NULL,
  sms_sign_name VARCHAR(64) NULL,
  sms_template_code VARCHAR(64) NULL,
  owner_name VARCHAR(64) NULL,
  updated_by VARCHAR(64) NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO gov_workflow_alert_channel (id, mail_enabled, sms_enabled, owner_name)
SELECT 1, 0, 0, '数据融合运维组'
WHERE NOT EXISTS (SELECT 1 FROM gov_workflow_alert_channel WHERE id = 1);

CREATE TABLE IF NOT EXISTS gov_workflow_alert_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pipeline_id BIGINT NULL,
  instance_id BIGINT NULL,
  channel VARCHAR(32) NOT NULL,
  receivers VARCHAR(1024) NULL,
  subject VARCHAR(256) NULL,
  content TEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'LEDGER',
  message VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_wal_pipeline (pipeline_id),
  KEY idx_wal_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
