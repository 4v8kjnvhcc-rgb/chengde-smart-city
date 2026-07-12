-- M020~M026 供需对接全流程

ALTER TABLE biz_data_demand
  ADD COLUMN demand_type VARCHAR(32) NOT NULL DEFAULT 'STRUCTURED' COMMENT 'STRUCTURED/UNSTRUCTURED',
  ADD COLUMN template_code VARCHAR(64) NULL,
  ADD COLUMN stage VARCHAR(32) NOT NULL DEFAULT 'MANAGE' COMMENT 'MANAGE/ANALYZE/CONFIRM/SUPPLY',
  ADD COLUMN matched_catalog_id BIGINT NULL,
  ADD COLUMN match_score DECIMAL(5,2) NULL,
  ADD COLUMN analysis_note VARCHAR(512) NULL,
  ADD COLUMN assignee_org VARCHAR(128) NULL,
  ADD COLUMN supply_mode VARCHAR(32) NULL COMMENT 'EXCHANGE/API/SHARE_PAGE';

CREATE TABLE IF NOT EXISTS biz_demand_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_code VARCHAR(64) NOT NULL,
  template_name VARCHAR(128) NOT NULL,
  demand_type VARCHAR(32) NOT NULL DEFAULT 'STRUCTURED',
  field_schema VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_template_code (template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS biz_demand_supply_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  demand_id BIGINT NOT NULL,
  task_type VARCHAR(32) NOT NULL COMMENT 'COLLECT/SHARE/EXCHANGE',
  task_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  ref_flow_code VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_demand (demand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS biz_catalog_objection (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  catalog_id BIGINT NOT NULL,
  objection_type VARCHAR(32) NOT NULL COMMENT 'QUALITY/COMPLETENESS/AUTH',
  content VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  handler_note VARCHAR(256) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_catalog (catalog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS biz_supply_manifest (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  manifest_type VARCHAR(32) NOT NULL COMMENT 'CATALOG/OBJECTION/SUPPLY_DEMAND',
  ref_id BIGINT NOT NULL,
  title VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  auth_level VARCHAR(32) NULL COMMENT 'DEPT/CITY/PROVINCE',
  cascade_flag TINYINT NOT NULL DEFAULT 0,
  export_payload TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_type (manifest_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO biz_demand_template (template_code, template_name, demand_type, field_schema) VALUES
('TPL_STRUCT_01', '结构化数据需求模板', 'STRUCTURED', 'title,org,resource,period'),
('TPL_UNSTRUCT_01', '非结构化文档需求模板', 'UNSTRUCTURED', 'title,org,format,volume'),
('TPL_API_01', '接口共享需求模板', 'STRUCTURED', 'title,org,apiSpec,frequency')
ON DUPLICATE KEY UPDATE template_name = VALUES(template_name);

INSERT INTO biz_catalog_objection (catalog_id, objection_type, content, status, created_by) VALUES
(1, 'COMPLETENESS', '目录字段说明不完整，缺少更新频率', 'OPEN', 'sys_admin')
ON DUPLICATE KEY UPDATE content = VALUES(content);

INSERT INTO biz_supply_manifest (manifest_type, ref_id, title, status, auth_level, cascade_flag) VALUES
('CATALOG', 1, '企业基础信息目录清单', 'ACTIVE', 'CITY', 1),
('SUPPLY_DEMAND', 1, '供需对接台账', 'ACTIVE', 'DEPT', 0)
ON DUPLICATE KEY UPDATE title = VALUES(title);

UPDATE sys_menu SET menu_name = '供需对接平台', component = 'exchange/SupplyDemandHubView'
WHERE id = 6 OR path = '/exchange/application';
