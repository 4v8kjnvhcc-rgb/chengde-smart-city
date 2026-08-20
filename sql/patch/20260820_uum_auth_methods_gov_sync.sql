-- 手工补丁（与 Flyway V242 对应）
-- 目标库：smart_city
-- 可重复执行：INSERT ON DUPLICATE / CREATE IF NOT EXISTS / WHERE NOT EXISTS

INSERT INTO sys_security_config (config_key, config_value, description) VALUES
('auth_method_password', 'true', '启用用户名密码认证'),
('auth_method_sms', 'false', '启用短信验证码认证'),
('auth_method_totp', 'false', '启用动态令牌(TOTP)认证'),
('auth_method_fingerprint', 'false', '启用指纹识别认证（需终端硬件）'),
('auth_sms_code_ttl_seconds', '300', '短信验证码有效秒数'),
('auth_sms_demo_code', '000000', '短信验证码演示码（无网关时可用）')
ON DUPLICATE KEY UPDATE description = VALUES(description);

CREATE TABLE IF NOT EXISTS sys_gov_sync_target (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  target_code     VARCHAR(64)  NOT NULL COMMENT '对接系统编码',
  target_name     VARCHAR(128) NOT NULL COMMENT '对接系统名称',
  endpoint        VARCHAR(512) NULL COMMENT '同步推送地址',
  sync_direction  VARCHAR(16)  NOT NULL DEFAULT 'PUSH' COMMENT 'PUSH|PULL',
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  remark          VARCHAR(512) NULL,
  created_by      BIGINT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NULL,
  UNIQUE KEY uk_gov_sync_target_code (target_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_gov_sync_job (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  target_id          BIGINT       NOT NULL,
  target_name        VARCHAR(128) NULL,
  sync_org           TINYINT      NOT NULL DEFAULT 0,
  sync_user          TINYINT      NOT NULL DEFAULT 0,
  sync_role          TINYINT      NOT NULL DEFAULT 0,
  sync_password      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否同步账号密码摘要（不落明文）',
  sync_sms           TINYINT      NOT NULL DEFAULT 0 COMMENT '是否同步短信认证相关字段',
  org_ids_json       TEXT         NULL,
  user_ids_json      TEXT         NULL,
  role_ids_json      TEXT         NULL,
  payload_summary    VARCHAR(1024) NULL,
  synced_org_count   INT          NOT NULL DEFAULT 0,
  synced_user_count  INT          NOT NULL DEFAULT 0,
  synced_role_count  INT          NOT NULL DEFAULT 0,
  status             VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'SUCCESS|PARTIAL|FAILED|LEDGER|RUNNING',
  message            VARCHAR(1024) NULL,
  started_at         DATETIME     NULL,
  finished_at        DATETIME     NULL,
  created_by         BIGINT       NULL,
  created_by_name    VARCHAR(64)  NULL,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_gov_sync_job_target (target_id),
  KEY idx_gov_sync_job_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_gov_sync_target (target_code, target_name, endpoint, sync_direction, status, remark)
SELECT 'GOV_UUM_DEFAULT', '政务统一用户（默认对接）', NULL, 'PUSH', 'ACTIVE', '未配置 endpoint 时执行记入台账，不伪造成功'
WHERE NOT EXISTS (SELECT 1 FROM sys_gov_sync_target WHERE target_code = 'GOV_UUM_DEFAULT');
