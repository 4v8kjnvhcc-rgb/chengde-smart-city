-- M078~M122 数据融合治理平台

CREATE TABLE IF NOT EXISTS gov_standard_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  item_code VARCHAR(64) NOT NULL,
  item_name VARCHAR(128) NOT NULL,
  item_type VARCHAR(32) NOT NULL COMMENT 'ELEMENT/CODE/NAMING/FILE/MAPPING',
  standard_ref VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_item_code (item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_metadata_registry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entry_code VARCHAR(64) NOT NULL,
  entry_name VARCHAR(128) NOT NULL,
  entry_type VARCHAR(32) NOT NULL COMMENT 'CONNECTOR/SOURCE/MODEL/CATALOG/LINEAGE/VERSION',
  parent_code VARCHAR(64) NULL,
  om_ref VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_entry_code (entry_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_fusion_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  asset_code VARCHAR(64) NOT NULL,
  asset_name VARCHAR(128) NOT NULL,
  asset_type VARCHAR(32) NOT NULL COMMENT 'GOVERN_TASK/ETL/KETTLE/MODEL/SCRIPT/WORKFLOW/COMPONENT',
  ref_integration VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'READY',
  last_run_at DATETIME NULL,
  last_message VARCHAR(512) NULL,
  UNIQUE KEY uk_asset_code (asset_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_catalog_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_code VARCHAR(64) NOT NULL,
  resource_name VARCHAR(128) NOT NULL,
  resource_type VARCHAR(32) NOT NULL COMMENT 'DATA/SERVICE',
  category_path VARCHAR(256) NULL,
  secret_flag TINYINT NOT NULL DEFAULT 0,
  publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  approval_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  subscription_status VARCHAR(32) NULL,
  version_no INT NOT NULL DEFAULT 1,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_resource_code (resource_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_quality_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_code VARCHAR(64) NOT NULL,
  report_name VARCHAR(128) NOT NULL,
  dimension VARCHAR(64) NOT NULL,
  score DECIMAL(5,2) NOT NULL,
  export_payload TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_report_code (report_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, org_id, status, created_by) VALUES
('QR_ACCURACY_001', '准确性稽核', 'ACCURACY', 1, 'ENABLED', 'sys_admin'),
('QR_CONSIST_001', '一致性稽核', 'CONSISTENCY', 1, 'ENABLED', 'sys_admin'),
('QR_TIMELY_001', '及时性稽核', 'TIMELINESS', 1, 'ENABLED', 'sys_admin'),
('QR_VALID_001', '有效性稽核', 'VALIDITY', 1, 'ENABLED', 'sys_admin'),
('QR_UNIQ_001', '唯一性稽核', 'UNIQUENESS', 1, 'ENABLED', 'sys_admin'),
('QR_INTEGR_001', '完整性稽核-外键', 'INTEGRITY', 1, 'ENABLED', 'sys_admin')
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name);

INSERT INTO gov_standard_item (item_code, item_name, item_type, standard_ref) VALUES
('STD_ELEM_NAME', '姓名数据元', 'ELEMENT', 'GB/T 19488'),
('STD_ELEM_ID', '身份证号数据元', 'ELEMENT', 'GB/T 19488'),
('STD_CODE_REGION', '行政区划代码', 'CODE', 'GB/T 2260'),
('STD_NAME_TABLE', '表命名规范', 'NAMING', '政务大数据命名规范'),
('STD_FILE_BASE', '基础标准文件', 'FILE', 'D02-需求基线'),
('STD_MAP_AUTO', '标准映射稽核联动', 'MAPPING', 'M085')
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name);

INSERT INTO gov_metadata_registry (entry_code, entry_name, entry_type, om_ref) VALUES
('META_DS_DEMO', '示范库数据源', 'SOURCE', 'smart_city'),
('META_CAT_ASSET', '数据资产目录', 'CATALOG', 'om-catalog'),
('META_LINEAGE_01', '企业表血缘', 'LINEAGE', 'enterprise->catalog')
ON DUPLICATE KEY UPDATE entry_name = VALUES(entry_name);

INSERT INTO gov_fusion_asset (asset_code, asset_name, asset_type, ref_integration) VALUES
('WF_GOV_001', '治理任务调度', 'GOVERN_TASK', 'dolphinscheduler'),
('ETL_KETTLE_001', 'Kettle治理ETL', 'KETTLE', 'kettle'),
('MODEL_LOGIC_01', '人口主题逻辑模型', 'MODEL', NULL),
('SCRIPT_ETL_01', '融合脚本示例', 'SCRIPT', NULL),
('COMP_JOIN_01', '横连接算子', 'COMPONENT', NULL)
ON DUPLICATE KEY UPDATE asset_name = VALUES(asset_name);

INSERT INTO gov_catalog_resource (resource_code, resource_name, resource_type, category_path, secret_flag, publish_status, approval_status, created_by) VALUES
('RES_DATA_001', '企业基础信息资源', 'DATA', '政务/基础库', 0, 'PUBLISHED', 'APPROVED', 'sys_admin'),
('RES_SVC_001', '法人查询API服务', 'SERVICE', '政务/服务目录', 0, 'DRAFT', 'PENDING', 'sys_admin')
ON DUPLICATE KEY UPDATE resource_name = VALUES(resource_name);

UPDATE sys_menu SET menu_name = '数据融合治理平台', component = 'governance/GovernanceHubView'
WHERE id = 9 OR path = '/governance';
