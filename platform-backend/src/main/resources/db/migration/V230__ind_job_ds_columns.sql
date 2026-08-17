-- V230: 指标任务表补 DolphinScheduler 列（业务列保持旧结构）
-- 本地若尚未导入生产 ind_* 六表，则跳过（不阻塞应用启动）
SET @db := DATABASE();

SET @t := (
  SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'ind_job'
);

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='ds_project_code');
SET @sql := IF(@t=0, 'SELECT 1', IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN ds_project_code BIGINT NULL COMMENT ''DS 项目编码''', 'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='ds_definition_code');
SET @sql := IF(@t=0, 'SELECT 1', IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN ds_definition_code BIGINT NULL COMMENT ''DS 流程定义编码''', 'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='ds_schedule_id');
SET @sql := IF(@t=0, 'SELECT 1', IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN ds_schedule_id INT NULL COMMENT ''DS 调度 id''', 'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='ds_instance_id');
SET @sql := IF(@t=0, 'SELECT 1', IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN ds_instance_id BIGINT NULL COMMENT ''DS 实例 id''', 'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='schedule_cron');
SET @sql := IF(@t=0, 'SELECT 1', IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN schedule_cron VARCHAR(64) NULL COMMENT ''Quartz cron''', 'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='last_log');
SET @sql := IF(@t=0, 'SELECT 1', IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN last_log MEDIUMTEXT NULL COMMENT ''最近执行日志''', 'SELECT 1'));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
