-- C：资源中心实用闭环
-- 主题/专题库扩展、物理表纳管、分区预检字段、策略绑定、备份产物

ALTER TABLE rc_theme_library
  ADD COLUMN library_kind VARCHAR(32) NOT NULL DEFAULT 'THEME' COMMENT 'THEME/TOPIC' AFTER theme_name,
  ADD COLUMN zone_code VARCHAR(64) NULL COMMENT '库区编码' AFTER library_kind,
  ADD COLUMN owner_org VARCHAR(128) NULL AFTER zone_code,
  ADD COLUMN description VARCHAR(512) NULL AFTER owner_org;

UPDATE rc_theme_library
SET library_kind = 'THEME',
    zone_code = 'ZONE_THEME',
    owner_org = '示范单位',
    description = '企业主题库（加工共享产出纳管）'
WHERE theme_code = 'THEME_ENTERPRISE';

INSERT INTO rc_theme_library (theme_code, theme_name, library_kind, zone_code, partition_key, owner_org, description, status, created_by)
VALUES ('TOPIC_PROJECT', '项目专题库', 'TOPIC', 'ZONE_TOPIC', 'district_code', '示范单位', '项目加工产出专题库', 'ACTIVE', 'sys_admin')
ON DUPLICATE KEY UPDATE theme_name = VALUES(theme_name);

CREATE TABLE IF NOT EXISTS rc_managed_table (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  theme_id BIGINT NOT NULL,
  physical_table VARCHAR(128) NOT NULL,
  meta_entry_code VARCHAR(64) NULL,
  fusion_physical_id BIGINT NULL,
  catalog_resource_code VARCHAR(64) NULL,
  record_count BIGINT NULL,
  data_bytes BIGINT NULL,
  index_bytes BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rc_managed_table (physical_table),
  KEY idx_rc_managed_theme (theme_id),
  KEY idx_rc_managed_meta (meta_entry_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源中心纳管物理表';

ALTER TABLE rc_partition_def
  ADD COLUMN table_name VARCHAR(128) NULL AFTER theme_id,
  ADD COLUMN partition_column VARCHAR(64) NULL AFTER table_name,
  ADD COLUMN expression_text VARCHAR(512) NULL AFTER partition_column,
  ADD COLUMN preview_ddl TEXT NULL AFTER expression_text,
  ADD COLUMN pretest_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/READY/BLOCKED' AFTER preview_ddl,
  ADD COLUMN pretest_message VARCHAR(512) NULL AFTER pretest_status,
  ADD COLUMN pretest_at DATETIME NULL AFTER pretest_message;

ALTER TABLE rc_storage_policy
  ADD COLUMN theme_id BIGINT NULL AFTER retention_days,
  ADD COLUMN managed_table_id BIGINT NULL AFTER theme_id,
  ADD KEY idx_rc_policy_managed (managed_table_id);

CREATE TABLE IF NOT EXISTS rc_backup_artifact (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  managed_table_id BIGINT NULL,
  physical_table VARCHAR(128) NOT NULL,
  file_path VARCHAR(512) NOT NULL,
  file_name VARCHAR(256) NOT NULL,
  row_count BIGINT NULL,
  byte_size BIGINT NULL,
  sha256 VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
  message VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rc_backup_job (job_id),
  KEY idx_rc_backup_table (physical_table),
  KEY idx_rc_backup_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源中心逻辑备份产物';

-- 种子：纳管企业主题落地表
INSERT INTO rc_managed_table (theme_id, physical_table, meta_entry_code, catalog_resource_code, status, created_by)
SELECT t.id, 'dws_enterprise_theme', 'TBL_FUS_DWS_ENTERPRISE_THEME', NULL, 'ACTIVE', 'sys_admin'
FROM rc_theme_library t
WHERE t.theme_code = 'THEME_ENTERPRISE'
ON DUPLICATE KEY UPDATE meta_entry_code = VALUES(meta_entry_code);

-- 分区策略示例挂到纳管表
UPDATE rc_partition_def p
JOIN rc_theme_library t ON t.theme_code = 'THEME_ENTERPRISE'
SET p.theme_id = t.id,
    p.table_name = 'dws_enterprise_theme',
    p.partition_column = 'district_code',
    p.expression_text = 'RANGE COLUMNS(district_code)',
    p.pretest_status = 'DRAFT'
WHERE p.partition_code IN ('PART_ORG_HASH', 'PART_DATE_RANGE');
