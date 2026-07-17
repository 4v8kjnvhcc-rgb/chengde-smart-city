-- B4/B5：资源目录订阅申请、审批与分发

CREATE TABLE IF NOT EXISTS gov_catalog_subscription (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_id BIGINT NOT NULL COMMENT '关联 gov_catalog_resource.id',
  applicant_org VARCHAR(128) NULL COMMENT '申请机构',
  applicant_user VARCHAR(64) NULL COMMENT '申请人账号',
  share_mode VARCHAR(32) NOT NULL COMMENT 'DB_SYNC/FILE_SYNC/API',
  purpose VARCHAR(512) NULL COMMENT '申请用途',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED/DISTRIBUTED',
  review_comment VARCHAR(512) NULL,
  reviewed_by VARCHAR(64) NULL,
  reviewed_at DATETIME NULL,
  distribute_result VARCHAR(1024) NULL COMMENT '分发结果摘要',
  distribute_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cat_sub_resource (resource_id),
  KEY idx_cat_sub_status (status),
  KEY idx_cat_sub_applicant (applicant_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源目录订阅申请';
