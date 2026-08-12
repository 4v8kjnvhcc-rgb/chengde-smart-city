-- 手工补丁：分析域「指标组」（与 Flyway V197 对齐）
-- 目标库：smart_city（控制面）
-- 可重复执行

CREATE TABLE IF NOT EXISTS ana_indicator_group (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_domain_code VARCHAR(32) NOT NULL COMMENT '所属业务域 population|legal|macro|key',
  indicator_domain_id BIGINT NOT NULL COMMENT '所属指标域 ana_indicator_domain.id',
  group_name VARCHAR(128) NOT NULL COMMENT '组名称',
  target_table VARCHAR(128) NOT NULL COMMENT '目标/结果表名 ind_*',
  group_category VARCHAR(32) NOT NULL DEFAULT 'UNIT' COMMENT 'UNIT=单元指标组',
  model_method VARCHAR(32) NOT NULL DEFAULT 'SQL' COMMENT 'SQL=SQL建模',
  description VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|PUBLISHED|INACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  UNIQUE KEY uk_ana_ind_group_table (target_table),
  KEY idx_ana_ind_group_owner (owner_domain_code, status),
  KEY idx_ana_ind_group_domain (indicator_domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @col_group := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ana_indicator' AND COLUMN_NAME = 'group_id'
);
SET @sql_group := IF(@col_group = 0,
  'ALTER TABLE ana_indicator ADD COLUMN group_id BIGINT NULL COMMENT ''所属指标组'' AFTER domain_code',
  'SELECT 1');
PREPARE stmt_group FROM @sql_group;
EXECUTE stmt_group;
DEALLOCATE PREPARE stmt_group;

SET @col_flag := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ana_indicator' AND COLUMN_NAME = 'indicator_flag'
);
SET @sql_flag := IF(@col_flag = 0,
  'ALTER TABLE ana_indicator ADD COLUMN indicator_flag VARCHAR(128) NULL COMMENT ''指标标识'' AFTER field_name',
  'SELECT 1');
PREPARE stmt_flag FROM @sql_flag;
EXECUTE stmt_flag;
DEALLOCATE PREPARE stmt_flag;

SET @idx_g := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ana_indicator' AND INDEX_NAME = 'idx_ana_ind_group'
);
SET @sql_idx := IF(@idx_g = 0,
  'ALTER TABLE ana_indicator ADD KEY idx_ana_ind_group (group_id)',
  'SELECT 1');
PREPARE stmt_idx FROM @sql_idx;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;
