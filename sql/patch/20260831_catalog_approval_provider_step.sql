-- 手工补丁：与 Flyway V253 对齐（资源目录审批二级：目录提供单位审核）
-- 目标库：smart_city
-- 可重复执行：列已存在则跳过

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gov_catalog_approval'
    AND COLUMN_NAME = 'approval_step'
);

SET @sql := IF(@col_exists = 0,
  'ALTER TABLE gov_catalog_approval ADD COLUMN approval_step VARCHAR(32) NOT NULL DEFAULT ''PLATFORM'' COMMENT ''PLATFORM=平台管理员审核; PROVIDER=目录提供单位审核'' AFTER action_type',
  'SELECT 1');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE gov_catalog_approval
SET approval_step = 'PLATFORM'
WHERE approval_step IS NULL OR approval_step = '';
