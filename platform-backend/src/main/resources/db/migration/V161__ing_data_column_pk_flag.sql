-- 数据项是否主键（正向建模登记可维护）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ing_data_column'
    AND COLUMN_NAME = 'pk_flag'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE ing_data_column ADD COLUMN pk_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''是否主键'' AFTER nullable_flag',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 按表上 primary_key_cols 回填已有逆向登记字段
UPDATE ing_data_column c
JOIN ing_data_table t ON t.id = c.table_id
SET c.pk_flag = 1
WHERE t.primary_key_cols IS NOT NULL
  AND t.primary_key_cols <> ''
  AND FIND_IN_SET(c.column_code, REPLACE(t.primary_key_cols, ' ', ''));
