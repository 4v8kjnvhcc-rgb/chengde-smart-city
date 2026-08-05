-- 数据库存储管理完善：备份/归档/销毁策略字段、调度、运行日志、产物类型与存储位置

ALTER TABLE rc_storage_policy
  ADD COLUMN storage_strategy VARCHAR(32) NULL COMMENT '存储策略 LOCAL/NAS/OBJECT' AFTER managed_table_id,
  ADD COLUMN backup_library_id BIGINT NULL COMMENT '备份库（rc_base_library.id）' AFTER storage_strategy,
  ADD COLUMN table_rule VARCHAR(512) NULL COMMENT '备份表规则：表名模式或行筛选说明' AFTER backup_library_id,
  ADD COLUMN compress_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '归档是否压缩' AFTER table_rule,
  ADD COLUMN compress_type VARCHAR(32) NULL COMMENT 'GZIP/NONE' AFTER compress_enabled,
  ADD COLUMN destroy_rule VARCHAR(512) NULL COMMENT '销毁规则说明（禁止自动物理删除）' AFTER compress_type,
  ADD COLUMN schedule_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用周期调度' AFTER destroy_rule,
  ADD COLUMN schedule_cron VARCHAR(64) NULL COMMENT 'Cron 表达式（Spring 6 段）' AFTER schedule_enabled,
  ADD COLUMN next_run_at DATETIME NULL COMMENT '下次计划执行时间' AFTER schedule_cron,
  ADD COLUMN last_run_at DATETIME NULL AFTER next_run_at,
  ADD COLUMN last_run_status VARCHAR(32) NULL AFTER last_run_at,
  ADD COLUMN last_run_message VARCHAR(512) NULL AFTER last_run_status;

ALTER TABLE rc_backup_artifact
  ADD COLUMN artifact_type VARCHAR(32) NOT NULL DEFAULT 'BACKUP' COMMENT 'BACKUP/ARCHIVE' AFTER id,
  ADD COLUMN storage_location VARCHAR(512) NULL COMMENT '存储位置摘要' AFTER file_path;

CREATE TABLE IF NOT EXISTS rc_policy_run_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_id BIGINT NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  run_status VARCHAR(32) NOT NULL COMMENT 'SUCCESS/LEDGER/FAILED/REJECTED',
  row_count BIGINT NULL,
  artifact_id BIGINT NULL,
  storage_location VARCHAR(512) NULL,
  message VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rc_policy_run_policy (policy_id),
  KEY idx_rc_policy_run_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存储策略执行运行日志';
