-- A10：标准对标映射

CREATE TABLE IF NOT EXISTS gov_standard_mapping (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  standard_item_id BIGINT NOT NULL COMMENT '关联标准项',
  source_system VARCHAR(128) NULL COMMENT '来源系统',
  source_table VARCHAR(128) NULL COMMENT '来源表',
  source_column VARCHAR(128) NULL COMMENT '来源字段',
  mapping_status VARCHAR(32) NOT NULL DEFAULT 'MAPPED' COMMENT 'MAPPED/PARTIAL/UNMAPPED',
  match_score DECIMAL(5,2) NULL COMMENT '匹配分',
  remark VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_std_mapping_item (standard_item_id),
  KEY idx_std_mapping_src (source_system, source_table, source_column)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
