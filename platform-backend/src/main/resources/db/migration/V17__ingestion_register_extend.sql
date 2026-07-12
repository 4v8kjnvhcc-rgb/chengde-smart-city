-- V17: 归集登记域扩展（M041/M044/M046/M047/M043/M045）

CREATE TABLE IF NOT EXISTS ing_data_table (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id BIGINT NOT NULL,
  table_code VARCHAR(64) NOT NULL,
  table_name VARCHAR(128) NOT NULL,
  modeling_mode VARCHAR(32) NOT NULL DEFAULT 'FORWARD' COMMENT 'FORWARD/REVERSE',
  column_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_table_code (table_code),
  KEY idx_source (source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_data_column (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  table_id BIGINT NOT NULL,
  column_code VARCHAR(64) NOT NULL,
  column_name VARCHAR(128) NOT NULL,
  data_type VARCHAR(64) NOT NULL DEFAULT 'VARCHAR',
  nullable_flag TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_col_table (table_id, column_code),
  KEY idx_table (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_lineage_edge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  from_node VARCHAR(64) NOT NULL,
  to_node VARCHAR(64) NOT NULL,
  from_label VARCHAR(128) NOT NULL,
  to_label VARCHAR(128) NOT NULL,
  edge_type VARCHAR(32) NOT NULL DEFAULT 'FLOW',
  field_mapping VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_asset_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tag_code VARCHAR(64) NOT NULL,
  tag_name VARCHAR(128) NOT NULL,
  rule_expr VARCHAR(512) NULL,
  hit_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_tag_code (tag_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ing_data_table (source_id, table_code, table_name, modeling_mode, column_count)
SELECT ds.id, 'TBL_ENTERPRISE', '企业基础信息表', 'FORWARD', 4 FROM ing_data_source ds WHERE ds.source_code = 'DS_MYSQL_01' LIMIT 1;

INSERT INTO ing_data_table (source_id, table_code, table_name, modeling_mode, column_count)
SELECT ds.id, 'TBL_PERSON', '人口基础信息表', 'FORWARD', 3 FROM ing_data_source ds WHERE ds.source_code = 'DS_MYSQL_01' LIMIT 1;

INSERT INTO ing_data_column (table_id, column_code, column_name, data_type, nullable_flag, sort_order)
SELECT t.id, 'ENT_CODE', '统一社会信用代码', 'VARCHAR(32)', 0, 1 FROM ing_data_table t WHERE t.table_code = 'TBL_ENTERPRISE';

INSERT INTO ing_data_column (table_id, column_code, column_name, data_type, nullable_flag, sort_order)
SELECT t.id, 'ENT_NAME', '企业名称', 'VARCHAR(256)', 0, 2 FROM ing_data_table t WHERE t.table_code = 'TBL_ENTERPRISE';

INSERT INTO ing_data_column (table_id, column_code, column_name, data_type, nullable_flag, sort_order)
SELECT t.id, 'REG_CAPITAL', '注册资本', 'DECIMAL(18,2)', 1, 3 FROM ing_data_table t WHERE t.table_code = 'TBL_ENTERPRISE';

INSERT INTO ing_data_column (table_id, column_code, column_name, data_type, nullable_flag, sort_order)
SELECT t.id, 'INDUSTRY', '行业分类', 'VARCHAR(64)', 1, 4 FROM ing_data_table t WHERE t.table_code = 'TBL_ENTERPRISE';

INSERT INTO ing_data_column (table_id, column_code, column_name, data_type, nullable_flag, sort_order)
SELECT t.id, 'ID_NO', '证件号码', 'VARCHAR(32)', 0, 1 FROM ing_data_table t WHERE t.table_code = 'TBL_PERSON';

INSERT INTO ing_data_column (table_id, column_code, column_name, data_type, nullable_flag, sort_order)
SELECT t.id, 'PERSON_NAME', '姓名', 'VARCHAR(64)', 0, 2 FROM ing_data_table t WHERE t.table_code = 'TBL_PERSON';

INSERT INTO ing_data_column (table_id, column_code, column_name, data_type, nullable_flag, sort_order)
SELECT t.id, 'BIRTH_DATE', '出生日期', 'DATE', 1, 3 FROM ing_data_table t WHERE t.table_code = 'TBL_PERSON';

INSERT INTO ing_lineage_edge (from_node, to_node, from_label, to_label, edge_type, field_mapping, sort_order) VALUES
('src-mysql', 'tbl-enterprise', '示范业务库', '企业基础表', 'EXTRACT', NULL, 1),
('tbl-enterprise', 'cat-001', '企业基础表', '企业基础信息目录', 'CATALOG', 'ENT_CODE->asset_key', 2),
('src-mysql', 'tbl-person', '示范业务库', '人口基础表', 'EXTRACT', NULL, 3),
('tbl-person', 'cat-002', '人口基础表', '人口基础目录', 'CATALOG', 'ID_NO->asset_key', 4);

INSERT INTO ing_asset_tag (tag_code, tag_name, rule_expr, hit_count, status) VALUES
('TAG_ENTERPRISE', '企业主体', 'table_name LIKE %enterprise%', 128, 'ACTIVE'),
('TAG_PII', '个人信息', 'column_name IN (ID_NO,PERSON_NAME)', 64, 'ACTIVE'),
('TAG_GOV', '政务数据', 'category_path LIKE 政务%', 256, 'ACTIVE');
