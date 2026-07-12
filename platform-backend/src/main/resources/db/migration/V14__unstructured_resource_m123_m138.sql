-- M123~M129 非结构化治理 + M130~M138 资源中心

ALTER TABLE uns_document
  ADD COLUMN category_code VARCHAR(64) NULL,
  ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  ADD COLUMN tag_json VARCHAR(256) NULL,
  ADD COLUMN process_status VARCHAR(32) NOT NULL DEFAULT 'RAW',
  ADD COLUMN linked_doc_id BIGINT NULL;

CREATE TABLE IF NOT EXISTS uns_doc_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_code VARCHAR(64) NOT NULL,
  category_name VARCHAR(128) NOT NULL,
  media_type VARCHAR(32) NOT NULL DEFAULT 'DOCUMENT',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_category_code (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS uns_doc_pipeline (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  pipeline_type VARCHAR(32) NOT NULL COMMENT 'CLEAN/TAG/LINK',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  result_message VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rc_base_library (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  lib_code VARCHAR(64) NOT NULL,
  lib_name VARCHAR(128) NOT NULL,
  lib_type VARCHAR(32) NOT NULL COMMENT 'BASE/SEMI/UNSTRUCT',
  record_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_lib_code (lib_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rc_partition_def (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  partition_code VARCHAR(64) NOT NULL,
  partition_name VARCHAR(128) NOT NULL,
  partition_type VARCHAR(32) NOT NULL COMMENT 'RANGE/HASH/LIST',
  theme_id BIGINT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_partition_code (partition_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rc_storage_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_code VARCHAR(64) NOT NULL,
  policy_name VARCHAR(128) NOT NULL,
  action_type VARCHAR(32) NOT NULL COMMENT 'BACKUP/ARCHIVE/DESTROY',
  retention_days INT NOT NULL DEFAULT 30,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_policy_code (policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rc_asset_catalog_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entry_code VARCHAR(64) NOT NULL,
  entry_name VARCHAR(128) NOT NULL,
  lib_id BIGINT NULL,
  drive_task VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_entry_code (entry_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rc_monitor_metric (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  metric_key VARCHAR(64) NOT NULL,
  metric_label VARCHAR(128) NOT NULL,
  metric_value VARCHAR(64) NOT NULL,
  alert_level VARCHAR(32) NOT NULL DEFAULT 'OK',
  checked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_metric_key (metric_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO uns_doc_category (category_code, category_name, media_type) VALUES
('CAT_GOV_DOC', '政务公文', 'DOCUMENT'),
('CAT_MEDIA_AV', '音视频资料', 'AV'),
('CAT_IMAGE', '图片影像', 'IMAGE')
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

INSERT INTO rc_base_library (lib_code, lib_name, lib_type, record_count) VALUES
('LIB_POP', '人口基础库', 'BASE', 582000),
('LIB_LEGAL', '法人基础库', 'BASE', 36000),
('LIB_SEMI_JSON', '半结构化JSON库', 'SEMI', 12800),
('LIB_UNS_FILE', '非结构化文件库', 'UNSTRUCT', 4500)
ON DUPLICATE KEY UPDATE lib_name = VALUES(lib_name);

INSERT INTO rc_partition_def (partition_code, partition_name, partition_type, theme_id) VALUES
('PART_ORG_HASH', '机构哈希分区', 'HASH', 1),
('PART_DATE_RANGE', '日期范围分区', 'RANGE', 1)
ON DUPLICATE KEY UPDATE partition_name = VALUES(partition_name);

INSERT INTO rc_storage_policy (policy_code, policy_name, action_type, retention_days) VALUES
('POL_BACKUP_W', '周备份策略', 'BACKUP', 7),
('POL_ARCHIVE_M', '月归档策略', 'ARCHIVE', 90),
('POL_DESTROY_Y', '年销毁策略', 'DESTROY', 1095)
ON DUPLICATE KEY UPDATE policy_name = VALUES(policy_name);

INSERT INTO rc_asset_catalog_entry (entry_code, entry_name, lib_id, drive_task) VALUES
('ACE_THEME_ENT', '企业主题资产目录', 2, 'exchange-collect-task')
ON DUPLICATE KEY UPDATE entry_name = VALUES(entry_name);

INSERT INTO rc_monitor_metric (metric_key, metric_label, metric_value, alert_level) VALUES
('avail', '可用性', '99.8%', 'OK'),
('integrity', '完整性', '100%', 'OK'),
('security', '安全合规', 'PASS', 'OK'),
('perf', '查询性能', '128ms P95', 'OK')
ON DUPLICATE KEY UPDATE metric_value = VALUES(metric_value);

UPDATE sys_menu SET menu_name = '非结构化治理平台', component = 'unstructured/UnstructuredHubView'
WHERE id = 10 OR path = '/unstructured';
UPDATE sys_menu SET menu_name = '大数据资源中心', component = 'resource/ResourceCenterHubView'
WHERE id = 11 OR path = '/resource-center';
