-- 质量模型 + 模型表 + 模型上挂载的质量规则（对照旧页：质量模型管理 / 质量规则配置）

CREATE TABLE IF NOT EXISTS gov_quality_model (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
  datasource_id BIGINT NOT NULL COMMENT '来源库 ID（平台分层虚拟 ID 或登记源）',
  datasource_name VARCHAR(256) NULL COMMENT '来源库展示名',
  description VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_qm_ds (datasource_id),
  KEY idx_qm_name (model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_quality_model_table (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_id BIGINT NOT NULL,
  table_name VARCHAR(128) NOT NULL,
  table_comment VARCHAR(256) NULL,
  sort_no INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qm_table (model_id, table_name),
  KEY idx_qmt_model (model_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_quality_model_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_id BIGINT NOT NULL,
  model_table_id BIGINT NOT NULL,
  rule_catalog_id BIGINT NULL COMMENT '关联校验规则类型 gov_quality_rule.id',
  rule_type_code VARCHAR(64) NULL COMMENT '规则类型编码',
  rule_type_name VARCHAR(128) NOT NULL COMMENT '规则类型名称（如空值检查）',
  rule_name VARCHAR(256) NOT NULL COMMENT '规则实例名称',
  table_name VARCHAR(128) NOT NULL,
  field_names VARCHAR(1024) NULL COMMENT '字段名，逗号分隔',
  remark VARCHAR(512) NULL,
  check_type VARCHAR(32) NULL COMMENT 'NULL_CHECK/UNIQUENESS/ACCURACY/RECORD_COUNT',
  config_json TEXT NULL,
  threshold DECIMAL(12,4) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_qmr_model (model_id),
  KEY idx_qmr_table (model_table_id),
  KEY idx_qmr_name (rule_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
