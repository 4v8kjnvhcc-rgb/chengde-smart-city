-- 人口/法人等业务域：五区资产挂载 + 指标库 + 模型-指标关联

CREATE TABLE IF NOT EXISTS ana_zone_binding (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  domain_code VARCHAR(32) NOT NULL COMMENT 'population|legal|macro|key',
  zone_code VARCHAR(32) NOT NULL COMMENT 'collect|govern|core|internal|share',
  asset_type VARCHAR(32) NOT NULL COMMENT 'METADATA|MANAGED|CATALOG|OTHER',
  asset_ref VARCHAR(128) NOT NULL COMMENT 'entryCode / physicalTable / resourceCode',
  asset_name VARCHAR(256) NULL,
  physical_table VARCHAR(128) NULL,
  meta_entry_code VARCHAR(128) NULL,
  data_layer VARCHAR(32) NULL,
  remark VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_zone_asset (domain_code, zone_code, asset_type, asset_ref),
  KEY idx_zone (domain_code, zone_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_indicator (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  domain_code VARCHAR(32) NOT NULL,
  indicator_code VARCHAR(64) NOT NULL,
  indicator_name VARCHAR(128) NOT NULL,
  source_table VARCHAR(128) NOT NULL,
  source_column VARCHAR(128) NULL,
  agg_func VARCHAR(32) NOT NULL DEFAULT 'COUNT' COMMENT 'COUNT|SUM|AVG|MAX|MIN|EXPR',
  expr_text VARCHAR(512) NULL COMMENT 'agg=EXPR 时使用',
  unit_label VARCHAR(32) NULL,
  description VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  UNIQUE KEY uk_ind_code (indicator_code),
  KEY idx_ind_domain (domain_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_model_indicator (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_id BIGINT NOT NULL,
  indicator_id BIGINT NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_model_ind (model_id, indicator_id),
  KEY idx_mi_model (model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE ana_analysis_model
  ADD COLUMN dimension_json VARCHAR(1024) NULL COMMENT '维度JSON数组' AFTER de_dashboard_id,
  ADD COLUMN description VARCHAR(512) NULL AFTER dimension_json;

-- 人口域若干示例指标（可改可删）
INSERT INTO ana_indicator (domain_code, indicator_code, indicator_name, source_table, source_column, agg_func, unit_label, description)
SELECT 'population', 'IND_POP_COUNT', '常住人口数', 'demo_population', 'person_id', 'COUNT', '人', '示例：按人员去重计数'
WHERE NOT EXISTS (SELECT 1 FROM ana_indicator WHERE indicator_code = 'IND_POP_COUNT');

INSERT INTO ana_indicator (domain_code, indicator_code, indicator_name, source_table, source_column, agg_func, unit_label, description)
SELECT 'population', 'IND_POP_AGE60', '60岁以上人口数', 'demo_population', 'age', 'COUNT', '人', '示例：年龄>=60 需结合 EXPR/视图'
WHERE NOT EXISTS (SELECT 1 FROM ana_indicator WHERE indicator_code = 'IND_POP_AGE60');
