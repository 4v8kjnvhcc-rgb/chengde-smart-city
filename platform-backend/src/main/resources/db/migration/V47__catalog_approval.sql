-- B3：资源目录审批流

CREATE TABLE IF NOT EXISTS gov_catalog_approval (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_id BIGINT NOT NULL,
  action_type VARCHAR(32) NOT NULL COMMENT 'PUBLISH/OFFLINE/UPDATE/DELETE',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/WITHDRAWN',
  submit_comment VARCHAR(512) NULL,
  review_comment VARCHAR(512) NULL,
  submitted_by VARCHAR(64) NULL,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_by VARCHAR(64) NULL,
  reviewed_at DATETIME NULL,
  KEY idx_approval_resource (resource_id),
  KEY idx_approval_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
