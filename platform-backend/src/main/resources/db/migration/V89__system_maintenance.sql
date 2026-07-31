-- V89 系统维护管理：外观 / 邮箱 / 安全扩展键 / 改密锁定字段

CREATE TABLE IF NOT EXISTS sys_appearance_config (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  theme_id        VARCHAR(64)  NOT NULL DEFAULT 'builtin-blue',
  custom_themes_json TEXT      NULL COMMENT '自定义主题 JSON 数组',
  logo_mode       VARCHAR(16)  NOT NULL DEFAULT 'CUSTOM' COMMENT 'CUSTOM|BLANK',
  logo_path       VARCHAR(512) NULL,
  login_captcha_enabled TINYINT NOT NULL DEFAULT 0,
  login_title     VARCHAR(128) NOT NULL DEFAULT '承德高新区智慧城市基础平台',
  login_title_font_size INT NOT NULL DEFAULT 28,
  login_title_color VARCHAR(32) NOT NULL DEFAULT '#ffffff',
  login_bg_mode   VARCHAR(16)  NOT NULL DEFAULT 'DEFAULT' COMMENT 'DEFAULT|CUSTOM',
  login_media_path VARCHAR(512) NULL,
  login_media_type VARCHAR(16) NULL COMMENT 'IMAGE|VIDEO',
  browser_title   VARCHAR(128) NULL,
  favicon_path    VARCHAR(512) NULL,
  watermark_enabled TINYINT NOT NULL DEFAULT 0,
  watermark_text  VARCHAR(256) NULL,
  watermark_show_username TINYINT NOT NULL DEFAULT 0,
  updated_by      BIGINT NULL,
  updated_at      DATETIME NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_appearance_config (id, theme_id, logo_mode, login_title)
SELECT 1, 'builtin-blue', 'CUSTOM', '承德高新区智慧城市基础平台'
WHERE NOT EXISTS (SELECT 1 FROM sys_appearance_config WHERE id = 1);

CREATE TABLE IF NOT EXISTS sys_mail_config (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  enabled         TINYINT NOT NULL DEFAULT 0,
  smtp_host       VARCHAR(256) NULL,
  smtp_port       INT NOT NULL DEFAULT 465,
  smtp_ssl        TINYINT NOT NULL DEFAULT 1,
  username        VARCHAR(256) NULL,
  password_enc    VARCHAR(512) NULL,
  from_name       VARCHAR(128) NULL,
  from_address    VARCHAR(256) NULL,
  updated_by      BIGINT NULL,
  updated_at      DATETIME NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_mail_config (id, enabled, smtp_port, smtp_ssl)
SELECT 1, 0, 465, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_mail_config WHERE id = 1);

INSERT INTO sys_security_config (config_key, config_value) VALUES
('pwd_change_max_failures', '5'),
('pwd_change_lock_minutes', '60'),
('pwd_expire_warn_days', '5'),
('pwd_expire_lock_days', '10')
ON DUPLICATE KEY UPDATE config_key = config_key;

ALTER TABLE sys_user
  ADD COLUMN pwd_change_fail_count INT NOT NULL DEFAULT 0 COMMENT '改密失败次数',
  ADD COLUMN pwd_change_locked_until DATETIME NULL COMMENT '改密功能锁定截止';

