-- 手工补丁：gov_meta_collect_run.log_text TEXT → MEDIUMTEXT（与 Flyway V251 同效，可重复执行）
-- 目标库：smart_city（控制面）
ALTER TABLE gov_meta_collect_run
  MODIFY COLUMN log_text MEDIUMTEXT NULL COMMENT '运行日志';
