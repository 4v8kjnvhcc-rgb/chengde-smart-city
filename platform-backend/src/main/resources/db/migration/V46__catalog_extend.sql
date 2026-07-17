-- B1：数据资源目录扩展（分类树 + 编目元数据）

ALTER TABLE gov_catalog_resource
  ADD COLUMN category_id BIGINT NULL COMMENT '分类ID' AFTER category_path,
  ADD COLUMN provider_org VARCHAR(128) NULL COMMENT '信息资源提供方' AFTER category_id,
  ADD COLUMN resource_format VARCHAR(64) NULL COMMENT '信息资源格式 DATABASE/FILE/API/OTHER' AFTER provider_org,
  ADD COLUMN share_type VARCHAR(32) NULL COMMENT '共享类型 OPEN/CONDITIONAL/NOT_SHARE' AFTER resource_format,
  ADD COLUMN update_cycle VARCHAR(32) NULL COMMENT '更新周期 REALTIME/DAILY/WEEKLY/MONTHLY/YEARLY' AFTER share_type,
  ADD COLUMN description VARCHAR(1024) NULL COMMENT '资源描述' AFTER update_cycle,
  ADD COLUMN updated_by VARCHAR(64) NULL AFTER created_by,
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER updated_by;

CREATE TABLE IF NOT EXISTS gov_catalog_category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_code VARCHAR(64) NOT NULL,
  category_name VARCHAR(128) NOT NULL,
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '0=根',
  category_path VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_category_code (category_code),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO gov_catalog_category (category_code, category_name, parent_id, category_path, sort_order, status, created_by) VALUES
('CAT_GOV', '政务', 0, '政务', 1, 'ACTIVE', 'sys_admin'),
('CAT_BASE', '基础库', 0, '政务/基础库', 2, 'ACTIVE', 'sys_admin'),
('CAT_SVC', '服务目录', 0, '政务/服务目录', 3, 'ACTIVE', 'sys_admin')
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

UPDATE gov_catalog_category c
JOIN gov_catalog_category p ON p.category_code = 'CAT_GOV'
SET c.parent_id = p.id,
    c.category_path = CONCAT(p.category_name, '/', c.category_name)
WHERE c.category_code IN ('CAT_BASE', 'CAT_SVC');

UPDATE gov_catalog_resource r
LEFT JOIN gov_catalog_category c ON c.category_path = r.category_path
SET r.category_id = c.id,
    r.provider_org = COALESCE(r.provider_org, '示范单位'),
    r.resource_format = COALESCE(r.resource_format, CASE WHEN r.resource_type = 'SERVICE' THEN 'API' ELSE 'DATABASE' END),
    r.share_type = COALESCE(r.share_type, 'OPEN'),
    r.update_cycle = COALESCE(r.update_cycle, 'DAILY')
WHERE r.category_id IS NULL;
