-- 目标库：smart_city
-- 可重复执行：按列是否存在判断
-- 对应 Flyway：V242__uns_external_platform_sync.sql

SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'uns_external_platform' AND COLUMN_NAME = 'last_sync_at'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE uns_external_platform ADD COLUMN last_sync_at DATETIME NULL COMMENT ''最近同步时间'' AFTER status',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'uns_external_platform' AND COLUMN_NAME = 'last_sync_count'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE uns_external_platform ADD COLUMN last_sync_count INT NOT NULL DEFAULT 0 COMMENT ''最近同步刷新条数'' AFTER last_sync_at',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'uns_external_platform' AND COLUMN_NAME = 'last_sync_message'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE uns_external_platform ADD COLUMN last_sync_message VARCHAR(512) NULL COMMENT ''最近同步说明'' AFTER last_sync_count',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
