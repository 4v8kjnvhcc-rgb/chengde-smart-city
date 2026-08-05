-- 分区设计管理最终版：策略扩展字段 + 维护/迁移/备份恢复台账

ALTER TABLE rc_partition_def
  ADD COLUMN remark VARCHAR(512) NULL AFTER pretest_at,
  ADD COLUMN updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER remark;

CREATE TABLE IF NOT EXISTS rc_partition_op (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  partition_def_id BIGINT NULL COMMENT '关联分区策略',
  managed_table_id BIGINT NULL COMMENT '关联纳管表',
  physical_table VARCHAR(128) NOT NULL,
  op_type VARCHAR(32) NOT NULL COMMENT 'MIGRATE/COMPRESS/REBUILD_INDEX/CLEANUP/ANALYZE/BACKUP/RESTORE_PLAN',
  op_status VARCHAR(32) NOT NULL DEFAULT 'LEDGER' COMMENT 'LEDGER/SUCCESS/FAILED/BLOCKED',
  preview_sql TEXT NULL COMMENT '推荐SQL或候选DDL（多数不自动执行）',
  message VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_rc_part_op_def (partition_def_id),
  KEY idx_rc_part_op_table (physical_table),
  KEY idx_rc_part_op_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分区维护/迁移/恢复计划台账';
