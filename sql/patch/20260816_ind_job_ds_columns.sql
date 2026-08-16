-- 手工补丁：ind_job 补 DS 调度列（与 Flyway V230 同内容，可重复执行）
-- 目标库：smart_city
SET @db := DATABASE();

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='ds_project_code');
SET @sql := IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN ds_project_code BIGINT NULL COMMENT ''DS 项目编码''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='ds_definition_code');
SET @sql := IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN ds_definition_code BIGINT NULL COMMENT ''DS 流程定义编码''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='ds_schedule_id');
SET @sql := IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN ds_schedule_id INT NULL COMMENT ''DS 调度 id''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='ds_instance_id');
SET @sql := IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN ds_instance_id BIGINT NULL COMMENT ''DS 实例 id''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='schedule_cron');
SET @sql := IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN schedule_cron VARCHAR(64) NULL COMMENT ''Quartz cron''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='ind_job' AND COLUMN_NAME='last_log');
SET @sql := IF(@c=0, 'ALTER TABLE ind_job ADD COLUMN last_log MEDIUMTEXT NULL COMMENT ''最近执行日志''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
