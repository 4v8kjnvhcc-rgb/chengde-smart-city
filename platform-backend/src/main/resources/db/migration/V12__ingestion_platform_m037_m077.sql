-- M037~M077 大数据归集平台

CREATE TABLE IF NOT EXISTS ing_stats_metric (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  metric_code VARCHAR(64) NOT NULL,
  metric_name VARCHAR(128) NOT NULL,
  domain_type VARCHAR(32) NOT NULL COMMENT 'BASE/DOMAIN',
  metric_value VARCHAR(64) NOT NULL,
  trend_pct DECIMAL(6,2) NULL,
  drill_route VARCHAR(128) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_metric_code (metric_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_guide_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  step_no INT NOT NULL,
  step_name VARCHAR(64) NOT NULL,
  step_desc VARCHAR(256) NOT NULL,
  required_flag TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_step_no (step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_code VARCHAR(64) NOT NULL,
  project_name VARCHAR(128) NOT NULL,
  bound_org_id BIGINT NULL,
  system_name VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_project_code (project_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_data_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  source_code VARCHAR(64) NOT NULL,
  source_name VARCHAR(128) NOT NULL,
  source_type VARCHAR(32) NOT NULL COMMENT 'MYSQL/ORACLE/FILE/API',
  conn_status VARCHAR(32) NOT NULL DEFAULT 'UNTESTED',
  table_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_code (source_code),
  KEY idx_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_dict (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dict_code VARCHAR(64) NOT NULL,
  dict_name VARCHAR(128) NOT NULL,
  dict_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
  item_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_dict_code (dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_upload_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_code VARCHAR(64) NOT NULL,
  file_name VARCHAR(256) NOT NULL,
  row_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  preview_json VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_ingest_channel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  channel_code VARCHAR(64) NOT NULL,
  channel_name VARCHAR(128) NOT NULL,
  channel_type VARCHAR(32) NOT NULL COMMENT 'TABLE/FTP/LOCAL/UNSTRUCT/API/CDC/SEMI',
  config_json VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
  last_run_at DATETIME NULL,
  last_message VARCHAR(512) NULL,
  UNIQUE KEY uk_channel_code (channel_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_pipeline_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(64) NOT NULL,
  job_name VARCHAR(128) NOT NULL,
  job_type VARCHAR(32) NOT NULL COMMENT 'PROBE/DEFINE/READ/RECONCILE',
  ref_channel_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  result_json VARCHAR(1024) NULL,
  bill_amount DECIMAL(12,2) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_job_code (job_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_resource_registry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  registry_code VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL,
  category_path VARCHAR(256) NULL,
  secret_level VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
  publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  approval_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_registry_code (registry_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_govern_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_code VARCHAR(64) NOT NULL,
  policy_name VARCHAR(128) NOT NULL,
  policy_type VARCHAR(32) NOT NULL COMMENT 'CLASSIFY/MASK/TAG/BACKUP/ARCHIVE/DESTROY',
  rule_expr VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  lifecycle_stage VARCHAR(32) NULL,
  UNIQUE KEY uk_policy_code (policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_health_metric (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  metric_key VARCHAR(64) NOT NULL,
  metric_label VARCHAR(128) NOT NULL,
  metric_value VARCHAR(64) NOT NULL,
  alert_level VARCHAR(32) NOT NULL DEFAULT 'OK',
  checked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_metric_key (metric_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ing_stats_metric (metric_code, metric_name, domain_type, metric_value, trend_pct, drill_route, sort_order) VALUES
('POP_BASE', '常住人口', 'BASE', '58.2万', 1.2, '/analytics/population', 1),
('HUKOU_BASE', '户籍人口', 'BASE', '61.5万', 0.8, '/analytics/population', 2),
('GDP_BASE', '地区生产总值', 'BASE', '186.3亿', 5.8, '/analytics/macro', 3),
('INSUR_DOMAIN', '参保人数', 'DOMAIN', '42.1万', 2.1, '/analytics/key-domains', 1),
('SUBSIDY_DOMAIN', '津贴发放', 'DOMAIN', '3.2万', -0.5, '/analytics/key-domains', 2),
('PERMIT_DOMAIN', '行政许可', 'DOMAIN', '1.8万件', 4.3, '/analytics/legal-entity', 3)
ON DUPLICATE KEY UPDATE metric_value = VALUES(metric_value);

INSERT INTO ing_guide_step (step_no, step_name, step_desc, required_flag) VALUES
(1, '概要说明', '填写数据资产概要', 1),
(2, '基本信息', '名称、编码、归属机构', 1),
(3, '分类信息', '业务分类与主题域', 1),
(4, '来源信息', '数据来源系统与方式', 1),
(5, '用途说明', '共享用途与场景', 1),
(6, '格式规范', '结构化/非结构化格式', 1),
(7, '权限范围', '访问权限与授权级别', 1),
(8, '质量评估', '质量规则与风险评估', 1),
(9, '流程说明', '审批与发布流程', 1),
(10, '规范引用', '引用标准与规范文件', 0),
(11, '提交审批', '提交登记审批', 1)
ON DUPLICATE KEY UPDATE step_name = VALUES(step_name);

INSERT INTO ing_project (project_code, project_name, bound_org_id, system_name, created_by) VALUES
('PRJ_DEMO_01', '智慧城市归集示范工程', 1, '政务数据共享平台', 'sys_admin')
ON DUPLICATE KEY UPDATE project_name = VALUES(project_name);

INSERT INTO ing_data_source (project_id, source_code, source_name, source_type, conn_status, table_count)
SELECT p.id, 'DS_MYSQL_01', '示范业务库', 'MYSQL', 'OK', 128 FROM ing_project p WHERE p.project_code = 'PRJ_DEMO_01'
ON DUPLICATE KEY UPDATE conn_status = VALUES(conn_status);

INSERT INTO ing_dict (dict_code, dict_name, dict_type, item_count) VALUES
('DICT_GENDER', '性别代码', 'STANDARD', 3),
('DICT_REGION', '行政区划', 'STANDARD', 12)
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name);

INSERT INTO ing_ingest_channel (channel_code, channel_name, channel_type, config_json, status) VALUES
('CH_TABLE', '结构化库表接入', 'TABLE', '{"mode":"T+1","mapping":"auto"}', 'IDLE'),
('CH_FTP', '远程FTP文件', 'FTP', '{"host":"ftp.demo.local"}', 'IDLE'),
('CH_API', 'REST API采集', 'API', '{"url":"https://api.demo.gov/data"}', 'IDLE'),
('CH_CDC', 'MySQL CDC同步', 'CDC', '{"canal":"localhost:19090"}', 'IDLE'),
('CH_UNSTRUCT', '非结构化文件', 'UNSTRUCT', '{"storage":"seaweed"}', 'IDLE'),
('CH_SEMI', '半结构化Kafka', 'SEMI', '{"broker":"kafka:9092","placeholder":true}', 'IDLE')
ON DUPLICATE KEY UPDATE channel_name = VALUES(channel_name);

INSERT INTO ing_govern_policy (policy_code, policy_name, policy_type, rule_expr, lifecycle_stage) VALUES
('POL_CLASS_L2', '二级数据分类', 'CLASSIFY', 'sensitivity>=2', NULL),
('POL_MASK_ID', '身份证脱敏', 'MASK', 'mask(id_card,4,4)', NULL),
('POL_BACKUP_D', '日备份策略', 'BACKUP', 'cron=0 2 * * *', 'BACKUP'),
('POL_ARCHIVE_M', '月度归档', 'ARCHIVE', 'retention>90d', 'ARCHIVE'),
('POL_DESTROY_Y', '年度销毁', 'DESTROY', 'retention>3y', 'DESTROY')
ON DUPLICATE KEY UPDATE policy_name = VALUES(policy_name);

INSERT INTO ing_health_metric (metric_key, metric_label, metric_value, alert_level) VALUES
('table_delta', '表增量监控', '326 tables +12', 'OK'),
('workflow_ok', '工作流成功率', '98.6%', 'OK'),
('script_fail', '脚本失败数', '2', 'WARN'),
('change_hist', '变更历史', '48 events/24h', 'OK')
ON DUPLICATE KEY UPDATE metric_value = VALUES(metric_value);

UPDATE sys_menu SET menu_name = '大数据归集平台', component = 'exchange/IngestionHubView'
WHERE id = 4 OR path = '/exchange/ingestion';
