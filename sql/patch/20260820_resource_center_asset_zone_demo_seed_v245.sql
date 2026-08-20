-- 目标库：smart_city
-- 可重复执行：CREATE IF NOT EXISTS + NOT EXISTS 守卫
-- 对应 Flyway：V245__resource_center_asset_zone_demo_seed.sql
-- 数据资产区演示种子：物理表 + 元数据 + 纳管到人口/法人/企业/地理/半结构关联

-- 业务故事：市级基础库盘点 → 表作为资产单元挂资源类型 → 进入对应数据中心模块；
--           非结构化证照扫描件抽取为半结构关联表，可在文件目录/索引库互查。

CREATE TABLE IF NOT EXISTS rc_demo_person_basic (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  person_id VARCHAR(32) NOT NULL,
  person_name VARCHAR(64) NOT NULL,
  id_card_mask VARCHAR(32) NULL,
  district_code VARCHAR(16) NULL,
  updated_at DATETIME NULL,
  UNIQUE KEY uk_rc_demo_person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演示·人口基础信息';

CREATE TABLE IF NOT EXISTS rc_demo_legal_entity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  credit_code VARCHAR(32) NOT NULL,
  ent_name VARCHAR(128) NOT NULL,
  ent_status VARCHAR(32) NULL,
  district_code VARCHAR(16) NULL,
  updated_at DATETIME NULL,
  UNIQUE KEY uk_rc_demo_legal (credit_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演示·法人主体';

CREATE TABLE IF NOT EXISTS rc_demo_enterprise_econ (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  credit_code VARCHAR(32) NOT NULL,
  year_no INT NOT NULL,
  revenue_wan DECIMAL(18,2) NULL,
  employee_cnt INT NULL,
  updated_at DATETIME NULL,
  UNIQUE KEY uk_rc_demo_ent_econ (credit_code, year_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演示·企业经济指标';

CREATE TABLE IF NOT EXISTS rc_demo_geo_poi (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  poi_code VARCHAR(32) NOT NULL,
  poi_name VARCHAR(128) NOT NULL,
  lng DECIMAL(12,8) NULL,
  lat DECIMAL(12,8) NULL,
  district_code VARCHAR(16) NULL,
  UNIQUE KEY uk_rc_demo_geo (poi_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演示·地理兴趣点';

CREATE TABLE IF NOT EXISTS rc_demo_uns_file_meta (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_code VARCHAR(64) NOT NULL,
  file_title VARCHAR(256) NOT NULL,
  storage_key VARCHAR(512) NULL,
  related_person_id VARCHAR(32) NULL,
  related_credit_code VARCHAR(32) NULL,
  extract_status VARCHAR(32) NULL,
  UNIQUE KEY uk_rc_demo_uns_meta (doc_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演示·非结构化文件元数据抽取（半结构关联）';

INSERT INTO rc_demo_person_basic (person_id, person_name, id_card_mask, district_code, updated_at)
SELECT 'P20260001', '张三', '1308**********1234', '130800', NOW()
WHERE NOT EXISTS (SELECT 1 FROM rc_demo_person_basic WHERE person_id = 'P20260001');

INSERT INTO rc_demo_person_basic (person_id, person_name, id_card_mask, district_code, updated_at)
SELECT 'P20260002', '李四', '1308**********5678', '130802', NOW()
WHERE NOT EXISTS (SELECT 1 FROM rc_demo_person_basic WHERE person_id = 'P20260002');

INSERT INTO rc_demo_legal_entity (credit_code, ent_name, ent_status, district_code, updated_at)
SELECT '91130800MA0XXXXX1X', '承德示例科技有限公司', '在营', '130800', NOW()
WHERE NOT EXISTS (SELECT 1 FROM rc_demo_legal_entity WHERE credit_code = '91130800MA0XXXXX1X');

INSERT INTO rc_demo_enterprise_econ (credit_code, year_no, revenue_wan, employee_cnt, updated_at)
SELECT '91130800MA0XXXXX1X', 2025, 3280.50, 126, NOW()
WHERE NOT EXISTS (SELECT 1 FROM rc_demo_enterprise_econ WHERE credit_code = '91130800MA0XXXXX1X' AND year_no = 2025);

INSERT INTO rc_demo_geo_poi (poi_code, poi_name, lng, lat, district_code)
SELECT 'POI_CD_001', '避暑山庄门口', 117.93810000, 40.98620000, '130802'
WHERE NOT EXISTS (SELECT 1 FROM rc_demo_geo_poi WHERE poi_code = 'POI_CD_001');

INSERT INTO rc_demo_uns_file_meta (doc_code, file_title, storage_key, related_person_id, related_credit_code, extract_status)
SELECT 'DOC_RC_LICENSE_001', '营业执照扫描件', 'uns/catalog/license/DOC_RC_LICENSE_001.pdf', NULL, '91130800MA0XXXXX1X', 'EXTRACTED'
WHERE NOT EXISTS (SELECT 1 FROM rc_demo_uns_file_meta WHERE doc_code = 'DOC_RC_LICENSE_001');

INSERT INTO gov_metadata_registry (
  entry_code, entry_name, entry_type, physical_table_name, database_name, data_layer,
  business_domain, owner_name, status, description, updated_at
)
SELECT 'TBL_RC_DEMO_PERSON', '人口基础信息（演示）', 'TABLE', 'rc_demo_person_basic', DATABASE(), 'DWS',
       '人口', '公安/人口主管部门', 'ACTIVE', '资源中心人口库演示资产', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gov_metadata_registry WHERE entry_code = 'TBL_RC_DEMO_PERSON');

INSERT INTO gov_metadata_registry (
  entry_code, entry_name, entry_type, physical_table_name, database_name, data_layer,
  business_domain, owner_name, status, description, updated_at
)
SELECT 'TBL_RC_DEMO_LEGAL', '法人主体（演示）', 'TABLE', 'rc_demo_legal_entity', DATABASE(), 'DWS',
       '法人', '市场监管部门', 'ACTIVE', '资源中心法人库演示资产', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gov_metadata_registry WHERE entry_code = 'TBL_RC_DEMO_LEGAL');

INSERT INTO gov_metadata_registry (
  entry_code, entry_name, entry_type, physical_table_name, database_name, data_layer,
  business_domain, owner_name, status, description, updated_at
)
SELECT 'TBL_RC_DEMO_ENT_ECON', '企业经济指标（演示）', 'TABLE', 'rc_demo_enterprise_econ', DATABASE(), 'DWS',
       '企业经济', '工信/统计部门', 'ACTIVE', '资源中心企业经济库演示资产', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gov_metadata_registry WHERE entry_code = 'TBL_RC_DEMO_ENT_ECON');

INSERT INTO gov_metadata_registry (
  entry_code, entry_name, entry_type, physical_table_name, database_name, data_layer,
  business_domain, owner_name, status, description, updated_at
)
SELECT 'TBL_RC_DEMO_GEO', '地理兴趣点（演示）', 'TABLE', 'rc_demo_geo_poi', DATABASE(), 'DWS',
       '地理', '自然资源部门', 'ACTIVE', '资源中心地理信息库演示资产', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gov_metadata_registry WHERE entry_code = 'TBL_RC_DEMO_GEO');

INSERT INTO gov_metadata_registry (
  entry_code, entry_name, entry_type, physical_table_name, database_name, data_layer,
  business_domain, owner_name, status, description, updated_at
)
SELECT 'TBL_RC_DEMO_UNS_META', '非结构化文件元数据抽取（演示）', 'TABLE', 'rc_demo_uns_file_meta', DATABASE(), 'DWS',
       '非结构化关联', '大数据中心', 'ACTIVE', '证照扫描件抽取后的半结构关联表，对接文件目录/索引库', NOW()
WHERE NOT EXISTS (SELECT 1 FROM gov_metadata_registry WHERE entry_code = 'TBL_RC_DEMO_UNS_META');

INSERT INTO rc_managed_table (theme_id, lib_id, asset_type, physical_table, meta_entry_code, record_count, status, created_by)
SELECT t.id, l.id, 'BASE', 'rc_demo_person_basic', 'TBL_RC_DEMO_PERSON', 2, 'ACTIVE', 'sys_admin'
FROM rc_theme_library t
JOIN rc_base_library l ON l.lib_code = 'LIB_POP'
WHERE t.theme_code = 'MOD_POPULATION'
  AND NOT EXISTS (SELECT 1 FROM rc_managed_table WHERE physical_table = 'rc_demo_person_basic');

INSERT INTO rc_managed_table (theme_id, lib_id, asset_type, physical_table, meta_entry_code, record_count, status, created_by)
SELECT t.id, l.id, 'BASE', 'rc_demo_legal_entity', 'TBL_RC_DEMO_LEGAL', 1, 'ACTIVE', 'sys_admin'
FROM rc_theme_library t
JOIN rc_base_library l ON l.lib_code = 'LIB_LEGAL'
WHERE t.theme_code = 'MOD_LEGAL'
  AND NOT EXISTS (SELECT 1 FROM rc_managed_table WHERE physical_table = 'rc_demo_legal_entity');

INSERT INTO rc_managed_table (theme_id, lib_id, asset_type, physical_table, meta_entry_code, record_count, status, created_by)
SELECT t.id, l.id, 'BASE', 'rc_demo_enterprise_econ', 'TBL_RC_DEMO_ENT_ECON', 1, 'ACTIVE', 'sys_admin'
FROM rc_theme_library t
JOIN rc_base_library l ON l.lib_code = 'LIB_ENTERPRISE'
WHERE t.theme_code = 'MOD_ENTERPRISE'
  AND NOT EXISTS (SELECT 1 FROM rc_managed_table WHERE physical_table = 'rc_demo_enterprise_econ');

INSERT INTO rc_managed_table (theme_id, lib_id, asset_type, physical_table, meta_entry_code, record_count, status, created_by)
SELECT t.id, l.id, 'BASE', 'rc_demo_geo_poi', 'TBL_RC_DEMO_GEO', 1, 'ACTIVE', 'sys_admin'
FROM rc_theme_library t
JOIN rc_base_library l ON l.lib_code = 'LIB_GEO'
WHERE t.theme_code = 'MOD_GEO'
  AND NOT EXISTS (SELECT 1 FROM rc_managed_table WHERE physical_table = 'rc_demo_geo_poi');

INSERT INTO rc_managed_table (theme_id, lib_id, asset_type, physical_table, meta_entry_code, record_count, status, created_by)
SELECT t.id, l.id, 'SEMI', 'rc_demo_uns_file_meta', 'TBL_RC_DEMO_UNS_META', 1, 'ACTIVE', 'sys_admin'
FROM rc_theme_library t
JOIN rc_base_library l ON l.lib_code = 'LIB_SEMI_RELATED'
WHERE t.theme_code = 'MOD_LICENSE'
  AND NOT EXISTS (SELECT 1 FROM rc_managed_table WHERE physical_table = 'rc_demo_uns_file_meta');
