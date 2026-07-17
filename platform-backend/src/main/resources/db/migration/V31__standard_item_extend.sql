-- A1：数据标准体系表扩展（gov_standard_item 在 V13 无 created_at/updated_at，需一并 ADD）

ALTER TABLE gov_standard_item
  ADD COLUMN data_type VARCHAR(64) NULL COMMENT '数据类型' AFTER standard_ref,
  ADD COLUMN data_format VARCHAR(128) NULL COMMENT '数据格式' AFTER data_type,
  ADD COLUMN value_domain VARCHAR(512) NULL COMMENT '值域' AFTER data_format,
  ADD COLUMN business_definition VARCHAR(1024) NULL COMMENT '业务定义' AFTER value_domain,
  ADD COLUMN business_rule VARCHAR(1024) NULL COMMENT '业务规则' AFTER business_definition,
  ADD COLUMN reference_standard VARCHAR(256) NULL COMMENT '引用标准' AFTER business_rule,
  ADD COLUMN category VARCHAR(64) NULL COMMENT '分类' AFTER reference_standard,
  ADD COLUMN sensitivity VARCHAR(32) NULL COMMENT '敏感级别' AFTER category,
  ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE' AFTER sensitivity,
  ADD COLUMN version_no INT NOT NULL DEFAULT 1 COMMENT '当前版本号' AFTER publish_status,
  ADD COLUMN published_by VARCHAR(64) NULL AFTER version_no,
  ADD COLUMN published_at DATETIME NULL AFTER published_by,
  ADD COLUMN created_by VARCHAR(64) NULL AFTER published_at,
  ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER created_by,
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

-- 存量：将引用标准回填到 reference_standard，发布状态按 status 粗映射
UPDATE gov_standard_item
SET reference_standard = COALESCE(reference_standard, standard_ref),
    publish_status = CASE WHEN status = 'ACTIVE' THEN 'PUBLISHED' ELSE 'DRAFT' END
WHERE reference_standard IS NULL OR publish_status = 'DRAFT';

CREATE TABLE IF NOT EXISTS gov_standard_codebook (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  standard_item_id BIGINT NOT NULL,
  code_value VARCHAR(64) NOT NULL,
  code_name VARCHAR(128) NOT NULL,
  code_desc VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_item_code_value (standard_item_id, code_value),
  KEY idx_codebook_item (standard_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_naming_standard (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  naming_type VARCHAR(32) NOT NULL COMMENT 'TABLE/COLUMN/API/OTHER',
  naming_name VARCHAR(128) NOT NULL,
  standard_content TEXT NOT NULL,
  description VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
