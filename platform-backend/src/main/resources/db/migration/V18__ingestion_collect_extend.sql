-- V18: 归集采集域扩展（M051-M077）

CREATE TABLE IF NOT EXISTS ing_upload_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_code VARCHAR(64) NOT NULL,
  template_name VARCHAR(128) NOT NULL,
  column_mapping_json VARCHAR(1024) NOT NULL,
  validate_rules_json VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tpl_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_ingest_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_code VARCHAR(64) NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  channel_id BIGINT NOT NULL,
  schedule_cron VARCHAR(64) NOT NULL DEFAULT '0 2 * * *',
  status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
  last_run_at DATETIME NULL,
  last_run_message VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_task_code (task_code),
  KEY idx_channel (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_probe_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_code VARCHAR(64) NOT NULL,
  source_name VARCHAR(128) NOT NULL,
  null_rate DECIMAL(6,4) NOT NULL DEFAULT 0,
  domain_check VARCHAR(64) NOT NULL DEFAULT 'OK',
  entity_type VARCHAR(64) NULL,
  metrics_json VARCHAR(1024) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DONE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_report_code (report_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_data_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  def_code VARCHAR(64) NOT NULL,
  def_name VARCHAR(128) NOT NULL,
  business_desc VARCHAR(512) NULL,
  tech_desc VARCHAR(512) NULL,
  metadata_json VARCHAR(1024) NULL COMMENT '8项定义',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_def_code (def_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_reconcile_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_no VARCHAR(64) NOT NULL,
  matched_pct DECIMAL(6,2) NOT NULL,
  diff_rows INT NOT NULL DEFAULT 0,
  alert_level VARCHAR(32) NOT NULL DEFAULT 'OK',
  status VARCHAR(32) NOT NULL DEFAULT 'CLOSED',
  detail_json VARCHAR(1024) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_batch (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_category_node (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  node_code VARCHAR(64) NOT NULL,
  node_name VARCHAR(128) NOT NULL,
  parent_id BIGINT NOT NULL DEFAULT 0,
  secret_level VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
  sort_order INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_node_code (node_code),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_backup_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(64) NOT NULL,
  policy_id BIGINT NULL,
  schedule_cron VARCHAR(64) NOT NULL DEFAULT '0 3 * * 0',
  backup_path VARCHAR(256) NOT NULL,
  last_restore_point VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_backup_code (job_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_archive_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(64) NOT NULL,
  policy_id BIGINT NULL,
  archive_path VARCHAR(256) NOT NULL,
  retention_days INT NOT NULL DEFAULT 365,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_archive_code (job_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ing_upload_template (template_code, template_name, column_mapping_json, validate_rules_json) VALUES
('TPL_STRUCT_01', '结构化示范模板', '[{"col":"name","target":"entity_name"},{"col":"code","target":"entity_code"}]', '{"required":["name","code"]}'),
('TPL_DICT_01', '字典导入模板', '[{"col":"dict_key","target":"item_key"},{"col":"dict_val","target":"item_value"}]', NULL);

INSERT INTO ing_ingest_task (task_code, task_name, channel_id, schedule_cron, status, last_run_message)
SELECT 'TASK_TBL_T1', '结构化T+1同步', c.id, '0 2 * * *', 'IDLE', 'ready' FROM ing_ingest_channel c WHERE c.channel_code = 'CH_TABLE' LIMIT 1;

INSERT INTO ing_ingest_task (task_code, task_name, channel_id, schedule_cron, status, last_run_message)
SELECT 'TASK_API_HOURLY', 'API小时采集', c.id, '0 * * * *', 'SUCCESS', 'last ok rows=320' FROM ing_ingest_channel c WHERE c.channel_code = 'CH_API' LIMIT 1;

INSERT INTO ing_ingest_task (task_code, task_name, channel_id, schedule_cron, status, last_run_message)
SELECT 'TASK_CDC_RT', 'CDC实时订阅', c.id, '*/5 * * * *', 'IDLE', 'canal placeholder' FROM ing_ingest_channel c WHERE c.channel_code = 'CH_CDC' LIMIT 1;

INSERT INTO ing_probe_report (report_code, source_name, null_rate, domain_check, entity_type, metrics_json) VALUES
('PROBE_DEMO_01', '示范业务库', 0.0180, 'OK', 'enterprise', '{"fields":5,"distinct":1200,"pattern":"ok"}');

INSERT INTO ing_data_definition (def_code, def_name, business_desc, tech_desc, metadata_json) VALUES
('DEF_ENT_01', '企业基础信息定义', '市场主体登记信息', 'MySQL tbl_enterprise', '{"items":8,"owner":"数据局","update":"T+1"}');

INSERT INTO ing_reconcile_log (batch_no, matched_pct, diff_rows, alert_level, status) VALUES
('RCN_20250712_01', 98.60, 12, 'WARN', 'OPEN'),
('RCN_20250711_01', 99.20, 3, 'OK', 'CLOSED');

INSERT INTO ing_category_node (node_code, node_name, parent_id, secret_level, sort_order) VALUES
('CAT_GOV', '政务数据', 0, 'INTERNAL', 1),
('CAT_BASE', '基础库', 1, 'INTERNAL', 2),
('CAT_TOPIC', '主题库', 1, 'INTERNAL', 3);

INSERT INTO ing_backup_job (job_code, schedule_cron, backup_path, last_restore_point, status) VALUES
('BK_ING_WEEKLY', '0 3 * * 0', '/backup/ingestion/weekly', '2025-07-06', 'ACTIVE');

INSERT INTO ing_archive_job (job_code, archive_path, retention_days, status) VALUES
('ARC_ING_MONTHLY', '/archive/ingestion/monthly', 365, 'ACTIVE');
