-- V19: 登记域 L2 扩展（连接配置、字典项、数据项语义、字段血缘）

ALTER TABLE ing_data_source ADD COLUMN conn_config_json VARCHAR(1024) NULL COMMENT 'host/port/db/user 等，密码仅存占位';

ALTER TABLE ing_data_column
  ADD COLUMN semantic_desc VARCHAR(256) NULL,
  ADD COLUMN length_val INT NULL,
  ADD COLUMN component_type VARCHAR(32) NULL DEFAULT 'INPUT',
  ADD COLUMN required_tip VARCHAR(128) NULL,
  ADD COLUMN built_in_flag TINYINT NOT NULL DEFAULT 0;

ALTER TABLE ing_asset_tag ADD COLUMN tag_desc VARCHAR(512) NULL;

CREATE TABLE IF NOT EXISTS ing_dict_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dict_id BIGINT NOT NULL,
  item_key VARCHAR(64) NOT NULL,
  item_value VARCHAR(256) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_dict_item (dict_id, item_key),
  KEY idx_dict (dict_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_column_lineage (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  table_node VARCHAR(64) NOT NULL,
  column_code VARCHAR(64) NOT NULL,
  column_name VARCHAR(128) NOT NULL,
  upstream_table VARCHAR(64) NULL,
  upstream_column VARCHAR(64) NULL,
  downstream_table VARCHAR(64) NULL,
  downstream_column VARCHAR(64) NULL,
  sort_order INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ing_dict_item (dict_id, item_key, item_value, sort_order)
SELECT d.id, 'M', '男', 1 FROM ing_dict d WHERE d.dict_code = 'DICT_GENDER' LIMIT 1;
INSERT INTO ing_dict_item (dict_id, item_key, item_value, sort_order)
SELECT d.id, 'F', '女', 2 FROM ing_dict d WHERE d.dict_code = 'DICT_GENDER' LIMIT 1;

INSERT INTO ing_column_lineage (table_node, column_code, column_name, upstream_table, upstream_column, downstream_table, downstream_column, sort_order) VALUES
('tbl-enterprise', 'ENT_CODE', '统一社会信用代码', 'src-mysql', 'credit_code', 'cat-001', 'asset_key', 1),
('tbl-enterprise', 'ENT_NAME', '企业名称', 'src-mysql', 'ent_name', NULL, NULL, 2),
('tbl-person', 'ID_NO', '证件号码', 'src-mysql', 'id_no', 'cat-002', 'asset_key', 3);
