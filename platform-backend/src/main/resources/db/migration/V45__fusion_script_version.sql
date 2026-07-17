-- D4：融合脚本版本

CREATE TABLE IF NOT EXISTS gov_fusion_script_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  script_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  script_content MEDIUMTEXT NOT NULL,
  change_summary VARCHAR(512) NULL,
  published_by VARCHAR(64) NULL,
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fusion_script_ver (script_id, version_no),
  KEY idx_fusion_script_ver_script (script_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
