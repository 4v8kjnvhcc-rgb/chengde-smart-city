-- 汇聚接入任务：记录最近一次执行耗时（毫秒）
-- 对应 Flyway: V186__ingest_task_duration_ms.sql
-- 目标库: smart_city
-- 说明：优先重启后端让 Flyway 执行；仅在无法启动时手工执行本脚本
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ing_ingest_task'
    AND COLUMN_NAME = 'duration_ms'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE ing_ingest_task ADD COLUMN duration_ms BIGINT NULL COMMENT ''最近一次执行耗时毫秒'' AFTER lines_rejected',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
