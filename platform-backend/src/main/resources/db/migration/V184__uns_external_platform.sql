-- 非结构化：外部文件业务平台对接台账（文件资源管理 · 外部平台 Tab）

CREATE TABLE IF NOT EXISTS uns_external_platform (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform_name VARCHAR(128) NOT NULL COMMENT '平台名称',
  connect_type VARCHAR(32) NOT NULL COMMENT '对接方式 API/FTP/S3/HTTP/DB_SYNC',
  api_config TEXT NOT NULL COMMENT '接口配置（JSON 或连接参数文本）',
  sync_frequency VARCHAR(32) NOT NULL DEFAULT 'DAILY' COMMENT '同步频率 HOURLY/DAILY/WEEKLY/MONTHLY/MANUAL',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_uns_ext_plat_name (platform_name),
  KEY idx_uns_ext_plat_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
