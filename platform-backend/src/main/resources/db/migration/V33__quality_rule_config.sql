-- A5：质量规则配置（与 gov_quality_rule 一对一/按规则绑定）

CREATE TABLE IF NOT EXISTS gov_quality_rule_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_id BIGINT NOT NULL,
  check_type VARCHAR(32) NOT NULL COMMENT 'NULL_CHECK/UNIQUENESS/ACCURACY/RECORD_COUNT',
  target_table VARCHAR(128) NULL,
  target_column VARCHAR(128) NULL,
  config_json TEXT NULL COMMENT '规则参数 JSON',
  threshold DECIMAL(12,4) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rule_config (rule_id),
  KEY idx_rule_config_type (check_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
