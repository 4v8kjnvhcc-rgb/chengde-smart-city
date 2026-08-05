-- 资产目录管理完善：公开/未公开、子系统归属、加密、审批流程、驱动交换台账

ALTER TABLE rc_asset_catalog_entry
  ADD COLUMN managed_table_id BIGINT NULL COMMENT '关联纳管表' AFTER lib_id,
  ADD COLUMN subsystem_code VARCHAR(64) NULL COMMENT '所属子系统（未公开目录归属）' AFTER managed_table_id,
  ADD COLUMN visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE' COMMENT 'PRIVATE未公开/PUBLIC公开' AFTER subsystem_code,
  ADD COLUMN encrypt_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用加密控制' AFTER visibility,
  ADD COLUMN encrypt_algo VARCHAR(32) NULL COMMENT 'AES256/SM4/NONE' AFTER encrypt_enabled,
  ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/PUBLISHED/REJECTED' AFTER encrypt_algo,
  ADD COLUMN reject_reason VARCHAR(512) NULL AFTER publish_status,
  ADD COLUMN description VARCHAR(512) NULL AFTER reject_reason,
  ADD COLUMN exchange_task_ref VARCHAR(128) NULL COMMENT '最近一次驱动交换任务引用' AFTER drive_task,
  ADD COLUMN last_exchange_at DATETIME NULL AFTER exchange_task_ref,
  ADD COLUMN last_exchange_message VARCHAR(512) NULL AFTER last_exchange_at,
  ADD COLUMN created_by VARCHAR(64) NULL AFTER last_exchange_message,
  ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER created_by,
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
  ADD KEY idx_rc_catalog_managed (managed_table_id),
  ADD KEY idx_rc_catalog_visibility (visibility),
  ADD KEY idx_rc_catalog_publish (publish_status),
  ADD KEY idx_rc_catalog_subsystem (subsystem_code);

CREATE TABLE IF NOT EXISTS rc_catalog_exchange_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  catalog_entry_id BIGINT NOT NULL,
  job_code VARCHAR(64) NOT NULL,
  job_name VARCHAR(128) NOT NULL,
  managed_table_id BIGINT NULL,
  physical_table VARCHAR(128) NULL,
  row_count BIGINT NULL,
  run_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/LEDGER/FAILED',
  message VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rc_catalog_job_code (job_code),
  KEY idx_rc_catalog_job_entry (catalog_entry_id),
  KEY idx_rc_catalog_job_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公开目录驱动交换任务台账';

-- 既有种子目录视为已公开（可驱动交换）
UPDATE rc_asset_catalog_entry
SET visibility = 'PUBLIC',
    publish_status = 'PUBLISHED',
    subsystem_code = COALESCE(subsystem_code, 'SHARED'),
    encrypt_enabled = COALESCE(encrypt_enabled, 0),
    encrypt_algo = COALESCE(encrypt_algo, 'NONE'),
    drive_task = COALESCE(drive_task, 'exchange-collect-task')
WHERE entry_code = 'ACE_THEME_ENT';
