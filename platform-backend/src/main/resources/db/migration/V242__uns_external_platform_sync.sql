-- 外部文件业务平台：动态同步台账字段
-- 对应手工补丁：sql/patch/20260820_uns_external_platform_sync.sql

ALTER TABLE uns_external_platform
  ADD COLUMN last_sync_at DATETIME NULL COMMENT '最近同步时间' AFTER status,
  ADD COLUMN last_sync_count INT NOT NULL DEFAULT 0 COMMENT '最近同步刷新条数' AFTER last_sync_at,
  ADD COLUMN last_sync_message VARCHAR(512) NULL COMMENT '最近同步说明' AFTER last_sync_count;
