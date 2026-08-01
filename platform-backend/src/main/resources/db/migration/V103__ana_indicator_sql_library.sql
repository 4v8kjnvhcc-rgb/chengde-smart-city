-- V103: 分析域指标库改造为「指标语句 + 结果字段映射」
CREATE TABLE IF NOT EXISTS ana_indicator_query (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  domain_code VARCHAR(32) NOT NULL,
  query_no VARCHAR(128) NOT NULL COMMENT '查询编号',
  datasource_key VARCHAR(64) NOT NULL,
  datasource_name VARCHAR(128) NULL,
  timeout_sec INT NOT NULL DEFAULT 60,
  sql_text MEDIUMTEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  UNIQUE KEY uk_ana_ind_query_no (query_no),
  KEY idx_ana_ind_query_domain (domain_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE ana_indicator
  ADD COLUMN query_id BIGINT NULL COMMENT '关联指标语句' AFTER domain_code,
  ADD COLUMN result_field VARCHAR(128) NULL COMMENT '查询结果字段' AFTER query_id,
  ADD COLUMN field_type VARCHAR(32) NULL COMMENT '字段类型中文/枚举' AFTER result_field,
  ADD COLUMN field_length INT NULL AFTER field_type,
  ADD COLUMN field_precision INT NULL AFTER field_length,
  ADD COLUMN field_name VARCHAR(128) NULL COMMENT '映射字段名' AFTER field_precision;

ALTER TABLE ana_indicator
  MODIFY COLUMN source_table VARCHAR(128) NULL;

-- 回填旧数据展示字段
UPDATE ana_indicator
SET result_field = COALESCE(NULLIF(source_column, ''), indicator_code),
    field_name = indicator_code,
    field_type = CASE
      WHEN UPPER(IFNULL(agg_func, '')) IN ('COUNT', 'SUM', 'AVG', 'MAX', 'MIN') THEN '数值'
      ELSE '字符串'
    END
WHERE result_field IS NULL;
