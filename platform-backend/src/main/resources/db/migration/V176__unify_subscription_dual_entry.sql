-- 申请/审批双入口同一套数据：交叉关联 + 审批字段 + 服务端「我的订阅」

ALTER TABLE biz_portal_subscription
  ADD COLUMN reviewed_by VARCHAR(64) NULL COMMENT '审批人' AFTER approver_note,
  ADD COLUMN reviewed_at DATETIME NULL COMMENT '审批时间' AFTER reviewed_by,
  ADD COLUMN gov_subscription_id BIGINT NULL COMMENT '关联 gov_catalog_subscription.id' AFTER reviewed_at;

ALTER TABLE gov_catalog_subscription
  ADD COLUMN portal_subscription_id BIGINT NULL COMMENT '关联 biz_portal_subscription.id' AFTER apply_payload;

CREATE INDEX idx_portal_sub_gov ON biz_portal_subscription (gov_subscription_id);
CREATE INDEX idx_gov_sub_portal ON gov_catalog_subscription (portal_subscription_id);
CREATE INDEX idx_portal_sub_reviewed ON biz_portal_subscription (reviewed_at);
CREATE INDEX idx_gov_sub_reviewed ON gov_catalog_subscription (reviewed_at);

-- 统一「我的订阅」（两端共用）
CREATE TABLE IF NOT EXISTS biz_resource_favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  catalog_id BIGINT NULL COMMENT 'biz_catalog_item.id',
  gov_resource_id BIGINT NULL COMMENT 'gov_catalog_resource.id',
  title VARCHAR(256) NOT NULL,
  catalog_code VARCHAR(128) NULL,
  provider_org VARCHAR(128) NULL,
  resource_type VARCHAR(64) NULL,
  resource_type_label VARCHAR(64) NULL,
  followed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_fav_user (user_id),
  KEY idx_fav_catalog (catalog_id),
  KEY idx_fav_gov (gov_resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源订阅收藏（门户/治理共用）';

-- 历史数据对账：按资源+申请人关联双表，并回写已审状态
UPDATE gov_catalog_subscription g
INNER JOIN biz_catalog_item c ON c.gov_resource_id = g.resource_id
INNER JOIN biz_portal_subscription p
  ON p.catalog_id = c.id
 AND IFNULL(p.created_by, '') = IFNULL(g.applicant_user, '')
SET g.portal_subscription_id = p.id
WHERE g.portal_subscription_id IS NULL
  AND p.gov_subscription_id IS NULL;

UPDATE biz_portal_subscription p
INNER JOIN gov_catalog_subscription g ON g.portal_subscription_id = p.id
SET p.gov_subscription_id = g.id
WHERE p.gov_subscription_id IS NULL;

UPDATE gov_catalog_subscription g
INNER JOIN biz_portal_subscription p ON (p.gov_subscription_id = g.id OR p.id = g.portal_subscription_id)
SET g.status = p.status,
    g.review_comment = COALESCE(NULLIF(g.review_comment, ''), p.approver_note),
    g.reviewed_by = COALESCE(NULLIF(g.reviewed_by, ''), p.reviewed_by, p.created_by),
    g.reviewed_at = COALESCE(g.reviewed_at, p.reviewed_at, p.updated_at)
WHERE g.status = 'PENDING'
  AND p.status IN ('APPROVED', 'REJECTED', 'CANCELLED');

UPDATE biz_portal_subscription p
INNER JOIN gov_catalog_subscription g ON (g.portal_subscription_id = p.id OR g.id = p.gov_subscription_id)
SET p.status = CASE WHEN g.status = 'DISTRIBUTED' THEN 'APPROVED' ELSE g.status END,
    p.approver_note = COALESCE(NULLIF(p.approver_note, ''), g.review_comment),
    p.reviewed_by = COALESCE(NULLIF(p.reviewed_by, ''), g.reviewed_by),
    p.reviewed_at = COALESCE(p.reviewed_at, g.reviewed_at)
WHERE p.status = 'PENDING'
  AND g.status IN ('APPROVED', 'REJECTED', 'CANCELLED', 'DISTRIBUTED');
