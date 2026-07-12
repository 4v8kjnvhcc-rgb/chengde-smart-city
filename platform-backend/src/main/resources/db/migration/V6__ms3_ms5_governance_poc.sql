-- MS3~MS5 POC：治理 / 非结构化 / 资源中心

CREATE TABLE IF NOT EXISTS gov_om_connector (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  connector_code VARCHAR(64) NOT NULL,
  connector_name VARCHAR(128) NOT NULL,
  source_type VARCHAR(64) NOT NULL DEFAULT 'MySQL',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  last_sync_at DATETIME NULL,
  last_message VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_connector_code (connector_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_quality_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code VARCHAR(64) NOT NULL,
  rule_name VARCHAR(128) NOT NULL,
  rule_type VARCHAR(64) NOT NULL DEFAULT 'COMPLETENESS',
  org_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_quality_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_name VARCHAR(128) NOT NULL,
  rule_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'READY',
  last_run_at DATETIME NULL,
  last_score DECIMAL(5,2) NULL,
  last_message VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS uns_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_code VARCHAR(64) NOT NULL,
  title VARCHAR(256) NOT NULL,
  content_type VARCHAR(64) NOT NULL DEFAULT 'application/pdf',
  storage_key VARCHAR(256) NULL,
  index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_doc_code (doc_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rc_theme_library (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  theme_code VARCHAR(64) NOT NULL,
  theme_name VARCHAR(128) NOT NULL,
  partition_key VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_theme_code (theme_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rc_backup_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_name VARCHAR(128) NOT NULL,
  theme_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'READY',
  last_run_at DATETIME NULL,
  last_message VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO gov_om_connector (connector_code, connector_name, source_type, status, last_message, created_by)
VALUES ('OM_CONN_MYSQL_DEMO', '示范库元数据采集适配器', 'MySQL', 'ACTIVE', 'ready for ingestion', 'sys_admin')
ON DUPLICATE KEY UPDATE connector_name = VALUES(connector_name);

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, org_id, status, created_by)
VALUES ('QR_COMPLETE_001', '完整性稽核-主键非空', 'COMPLETENESS', 1, 'ENABLED', 'sys_admin')
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name);

INSERT INTO rc_theme_library (theme_code, theme_name, partition_key, status, created_by)
VALUES ('THEME_ENTERPRISE', '企业主题库', 'org_id', 'ACTIVE', 'sys_admin')
ON DUPLICATE KEY UPDATE theme_name = VALUES(theme_name);

UPDATE sys_menu SET integration_type = 'self', component = 'governance/GovernanceView' WHERE id = 9;
UPDATE sys_menu SET integration_type = 'self', component = 'unstructured/UnstructuredView' WHERE id = 10;
UPDATE sys_menu SET integration_type = 'self', component = 'resource/ResourceCenterView' WHERE id = 11;
