-- A2：数据元发布版本快照

CREATE TABLE IF NOT EXISTS gov_standard_item_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  standard_item_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  snapshot_json TEXT NOT NULL,
  change_summary VARCHAR(512) NULL,
  published_by VARCHAR(64) NULL,
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_item_version (standard_item_id, version_no),
  KEY idx_std_ver_item (standard_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
