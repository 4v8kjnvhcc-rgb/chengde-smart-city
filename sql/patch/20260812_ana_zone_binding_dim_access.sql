-- 手工补丁：采集区选型挂载维度字段（与 Flyway V195 对应）
-- 目标库：smart_city（控制面）
-- 可重复执行：已存在列时跳过

SET @db := DATABASE();

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'ana_zone_binding' AND COLUMN_NAME = 'dim_group'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE ana_zone_binding ADD COLUMN dim_group VARCHAR(32) NULL COMMENT ''DATATYPE|LATENCY'' AFTER data_layer',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'ana_zone_binding' AND COLUMN_NAME = 'access_mode'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE ana_zone_binding ADD COLUMN access_mode VARCHAR(32) NULL COMMENT ''STRUCT|UNSTRUCT|API|CDC'' AFTER dim_group',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
