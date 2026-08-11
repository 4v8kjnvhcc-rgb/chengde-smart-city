-- 质量告警通道配置 + 告警推送台账

CREATE TABLE IF NOT EXISTS gov_quality_alert_channel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  mail_enabled TINYINT NOT NULL DEFAULT 0,
  mail_receivers VARCHAR(1024) NULL COMMENT '分号/逗号分隔邮箱',
  sms_enabled TINYINT NOT NULL DEFAULT 0,
  sms_phones VARCHAR(512) NULL COMMENT '分号/逗号分隔手机号',
  sms_gateway_url VARCHAR(512) NULL,
  sms_sign_name VARCHAR(64) NULL,
  sms_template_code VARCHAR(64) NULL,
  owner_name VARCHAR(64) NULL COMMENT '默认责任人',
  updated_by VARCHAR(64) NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO gov_quality_alert_channel (id, mail_enabled, sms_enabled, owner_name)
SELECT 1, 0, 0, '数据治理组'
WHERE NOT EXISTS (SELECT 1 FROM gov_quality_alert_channel WHERE id = 1);

CREATE TABLE IF NOT EXISTS gov_quality_alert_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scheme_id BIGINT NULL,
  task_id BIGINT NULL,
  run_id BIGINT NULL,
  channel VARCHAR(32) NOT NULL COMMENT 'EMAIL/SMS',
  receivers VARCHAR(1024) NULL,
  subject VARCHAR(256) NULL,
  content TEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'LEDGER' COMMENT 'SUCCESS/FAILED/LEDGER',
  message VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_qal_run (run_id),
  KEY idx_qal_scheme (scheme_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
