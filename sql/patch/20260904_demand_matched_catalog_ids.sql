-- 手工补丁：需求已挂载目录多选（与 Flyway V256 对齐，可重复执行）
-- 目标库：smart_city（控制面）

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'biz_data_demand'
    AND COLUMN_NAME = 'matched_catalog_ids'
);

SET @sql := IF(@col_exists = 0,
  'ALTER TABLE biz_data_demand ADD COLUMN matched_catalog_ids TEXT NULL COMMENT ''已挂载门户目录ID列表JSON'' AFTER matched_catalog_id',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE biz_data_demand
SET matched_catalog_ids = CONCAT('[', matched_catalog_id, ']')
WHERE matched_catalog_id IS NOT NULL
  AND (matched_catalog_ids IS NULL OR matched_catalog_ids = '');
