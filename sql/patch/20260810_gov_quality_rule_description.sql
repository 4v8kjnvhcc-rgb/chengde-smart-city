-- 手工补丁（与 Flyway V180 对应）：gov_quality_rule 增加排序、描述
-- 目标库：smart_city（控制面）
-- 可重复执行：已存在列则跳过

SET @db := DATABASE();

SET @exists_sort := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'gov_quality_rule' AND COLUMN_NAME = 'sort_no'
);
SET @sql_sort := IF(@exists_sort = 0,
  'ALTER TABLE gov_quality_rule ADD COLUMN sort_no INT NOT NULL DEFAULT 0 COMMENT ''排序'' AFTER rule_type',
  'SELECT 1');
PREPARE stmt_sort FROM @sql_sort;
EXECUTE stmt_sort;
DEALLOCATE PREPARE stmt_sort;

SET @exists_desc := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'gov_quality_rule' AND COLUMN_NAME = 'description'
);
SET @sql_desc := IF(@exists_desc = 0,
  'ALTER TABLE gov_quality_rule ADD COLUMN description VARCHAR(512) NULL COMMENT ''描述'' AFTER sort_no',
  'SELECT 1');
PREPARE stmt_desc FROM @sql_desc;
EXECUTE stmt_desc;
DEALLOCATE PREPARE stmt_desc;
