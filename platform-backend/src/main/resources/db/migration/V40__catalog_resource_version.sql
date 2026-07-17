-- B6：目录资源发布版本快照

CREATE TABLE IF NOT EXISTS gov_catalog_resource_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  snapshot_json TEXT NOT NULL,
  change_summary VARCHAR(512) NULL,
  published_by VARCHAR(64) NULL,
  published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_res_version (resource_id, version_no),
  KEY idx_cat_ver_resource (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
