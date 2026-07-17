-- P0 直通共享黄金路径：
-- 登记/汇聚表 -> 元数据 -> 质量 -> 目录 -> 订阅授权

ALTER TABLE ing_data_table
  ADD COLUMN physical_table_name VARCHAR(128) NULL COMMENT '实际落地物理表名' AFTER table_name,
  ADD COLUMN collect_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED' AFTER status,
  ADD COLUMN last_collect_at DATETIME NULL AFTER collect_status;

ALTER TABLE ing_ingest_task
  ADD COLUMN source_id BIGINT NULL COMMENT '关联 ing_data_source.id' AFTER channel_id,
  ADD COLUMN table_id BIGINT NULL COMMENT '关联 ing_data_table.id' AFTER source_id,
  ADD COLUMN target_table VARCHAR(128) NULL COMMENT '汇聚落地物理表' AFTER table_id,
  ADD COLUMN collected_rows BIGINT NULL AFTER target_table,
  ADD KEY idx_ingest_task_table (table_id);

ALTER TABLE gov_metadata_registry
  ADD COLUMN data_source_id BIGINT NULL COMMENT '关联 ing_data_source.id' AFTER run_id,
  ADD COLUMN source_table_id BIGINT NULL COMMENT '关联 ing_data_table.id' AFTER data_source_id,
  ADD COLUMN physical_table_name VARCHAR(128) NULL AFTER source_table_id,
  ADD KEY idx_meta_source_table (data_source_id, source_table_id);

ALTER TABLE gov_meta_collect_task
  ADD COLUMN ing_data_source_id BIGINT NULL COMMENT '登记数据源' AFTER connector_id,
  ADD COLUMN ing_table_id BIGINT NULL COMMENT '登记表' AFTER ing_data_source_id,
  ADD KEY idx_meta_collect_ing_table (ing_table_id);

ALTER TABLE gov_quality_rule_config
  ADD COLUMN metadata_entry_code VARCHAR(64) NULL COMMENT '绑定元数据条目' AFTER rule_id,
  ADD KEY idx_quality_config_entry (metadata_entry_code);

ALTER TABLE gov_quality_task
  ADD COLUMN metadata_entry_code VARCHAR(64) NULL COMMENT '绑定元数据条目' AFTER datasource_id,
  ADD KEY idx_quality_task_entry (metadata_entry_code);

ALTER TABLE gov_catalog_resource
  ADD COLUMN metadata_entry_code VARCHAR(64) NULL COMMENT '绑定元数据条目' AFTER resource_type,
  ADD COLUMN data_source_id BIGINT NULL AFTER metadata_entry_code,
  ADD COLUMN physical_table_name VARCHAR(128) NULL AFTER data_source_id,
  ADD COLUMN source_path_type VARCHAR(32) NOT NULL DEFAULT 'DIRECT' COMMENT 'DIRECT/PROCESSED' AFTER physical_table_name,
  ADD COLUMN quality_score DECIMAL(5,2) NULL AFTER source_path_type,
  ADD KEY idx_catalog_meta_entry (metadata_entry_code);

CREATE TABLE IF NOT EXISTS gov_catalog_authorization (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  authorization_code VARCHAR(64) NOT NULL,
  subscription_id BIGINT NOT NULL,
  resource_id BIGINT NOT NULL,
  grantee_org VARCHAR(128) NULL,
  grantee_user VARCHAR(64) NULL,
  share_mode VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED/EXPIRED',
  credential_ref VARCHAR(256) NULL COMMENT '本地授权凭据引用，后续可替换为ESB授权标识',
  valid_from DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  valid_until DATETIME NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_catalog_auth_code (authorization_code),
  UNIQUE KEY uk_catalog_auth_subscription (subscription_id),
  KEY idx_catalog_auth_resource (resource_id),
  KEY idx_catalog_auth_grantee (grantee_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目录订阅授权台账';

-- 真实可查询的汇聚落地样例表；特意保留一条企业名称空值，用于验证质量问题闭环。
CREATE TABLE IF NOT EXISTS ods_enterprise_base (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  unified_credit_code VARCHAR(32) NOT NULL,
  enterprise_name VARCHAR(256) NULL,
  registered_capital DECIMAL(18,2) NULL,
  industry_code VARCHAR(64) NULL,
  district_code VARCHAR(12) NULL,
  source_updated_at DATETIME NULL,
  UNIQUE KEY uk_ods_enterprise_credit (unified_credit_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P0直通共享企业基础信息汇聚样例';

INSERT INTO ods_enterprise_base
  (unified_credit_code, enterprise_name, registered_capital, industry_code, district_code, source_updated_at)
VALUES
  ('91130800P000000001', '承德示范科技有限公司', 1000.00, 'I65', '130802', NOW()),
  ('91130800P000000002', '承德示范制造有限公司', 2500.00, 'C39', '130803', NOW()),
  ('91130800P000000003', NULL, 500.00, 'L72', '130804', NOW())
ON DUPLICATE KEY UPDATE
  enterprise_name = VALUES(enterprise_name),
  registered_capital = VALUES(registered_capital),
  industry_code = VALUES(industry_code),
  district_code = VALUES(district_code),
  source_updated_at = VALUES(source_updated_at);

UPDATE ing_data_table t
JOIN ing_data_source s ON s.id = t.source_id AND s.source_code = 'DS_MYSQL_01'
SET t.physical_table_name = 'ods_enterprise_base',
    t.collect_status = 'SUCCESS',
    t.last_collect_at = NOW(),
    t.column_count = 6
WHERE t.table_code = 'TBL_ENTERPRISE';

UPDATE ing_ingest_task task
JOIN ing_ingest_channel channel ON channel.id = task.channel_id AND channel.channel_code = 'CH_TABLE'
JOIN ing_data_source source ON source.source_code = 'DS_MYSQL_01'
JOIN ing_data_table data_table ON data_table.source_id = source.id AND data_table.table_code = 'TBL_ENTERPRISE'
SET task.source_id = source.id,
    task.table_id = data_table.id,
    task.target_table = 'ods_enterprise_base',
    task.collected_rows = 3,
    task.status = 'SUCCESS',
    task.last_run_at = NOW(),
    task.last_run_message = 'P0直通共享样例已汇聚 rows=3'
WHERE task.task_code = 'TASK_TBL_T1';
