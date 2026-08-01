-- V110: 归集侧资源目录四模块（编目/分类/注册发布/审批）+ 门户同步字段

-- ── 1. 编目表扩展 ──────────────────────────────────────
ALTER TABLE ing_resource_registry
  ADD COLUMN org_id BIGINT NULL COMMENT '所属组织' AFTER created_by,
  ADD COLUMN category_id BIGINT NULL COMMENT '当前关联分类' AFTER category_path,
  ADD COLUMN resource_code VARCHAR(64) NULL COMMENT '信息资源代码' AFTER registry_code,
  ADD COLUMN provider_org VARCHAR(128) NULL COMMENT '信息资源提供方' AFTER title,
  ADD COLUMN resource_format VARCHAR(32) NULL DEFAULT 'DATABASE' COMMENT 'DATABASE/FILE/API/OTHER' AFTER provider_org,
  ADD COLUMN share_type VARCHAR(32) NULL DEFAULT 'CONDITIONAL' COMMENT 'OPEN/CONDITIONAL/NOT_SHARE' AFTER resource_format,
  ADD COLUMN update_cycle VARCHAR(32) NULL DEFAULT 'MONTHLY' AFTER share_type,
  ADD COLUMN description VARCHAR(512) NULL AFTER update_cycle,
  ADD COLUMN ref_source_id BIGINT NULL AFTER description,
  ADD COLUMN ref_table_id BIGINT NULL AFTER ref_source_id,
  ADD COLUMN asset_summary VARCHAR(512) NULL AFTER ref_table_id,
  ADD COLUMN portal_catalog_id BIGINT NULL COMMENT '同步的 biz_catalog_item.id' AFTER asset_summary,
  ADD COLUMN updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

-- ── 2. 分类表扩展 ──────────────────────────────────
ALTER TABLE ing_category_node
  ADD COLUMN description VARCHAR(512) NULL AFTER secret_level,
  ADD COLUMN secret_flag TINYINT NOT NULL DEFAULT 0 COMMENT '1涉密 0非涉密' AFTER description;

UPDATE ing_category_node
SET secret_flag = CASE WHEN secret_level IN ('SECRET', 'CONFIDENTIAL', 'TOP_SECRET') THEN 1 ELSE 0 END
WHERE secret_flag = 0 AND secret_level IS NOT NULL;

-- ── 3. 分类-资源关联 ───────────────────────────────
CREATE TABLE IF NOT EXISTS ing_category_resource (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id BIGINT NOT NULL,
  registry_id BIGINT NOT NULL,
  bound_by VARCHAR(64) NULL,
  bound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cat_reg (category_id, registry_id),
  UNIQUE KEY uk_registry_once (registry_id),
  KEY idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源分类与编目关联';

-- ── 4. 审批单 ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS ing_catalog_approval (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  registry_id BIGINT NULL,
  category_id BIGINT NULL,
  action_type VARCHAR(32) NOT NULL COMMENT 'PUBLISH/OFFLINE/DELETE/BIND/UNBIND/CAT_CREATE/CAT_UPDATE/CAT_DELETE',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/WITHDRAWN',
  submit_comment VARCHAR(512) NULL,
  review_comment VARCHAR(512) NULL,
  submitted_by VARCHAR(64) NULL,
  submitted_org_id BIGINT NULL,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_by VARCHAR(64) NULL,
  reviewed_at DATETIME NULL,
  payload_json TEXT NULL,
  KEY idx_status (status),
  KEY idx_registry (registry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='归集资源目录审批';

-- ── 5. 菜单：父级下挂四子模块 ─────────────────────
UPDATE sys_menu
SET menu_type = 1,
    path = NULL,
    component = NULL,
    menu_name = '指标与目录体系构建'
WHERE id = 7023;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7750, 7023, '数据资源编目管理', 2,
       '/exchange/ingestion?system=collect&module=catalog.resources', NULL,
       'hub:ingestion:collect:catalog:resources', NULL, 1, 'M065', 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7750 OR permission = 'hub:ingestion:collect:catalog:resources');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7751, 7023, '数据资源分类', 2,
       '/exchange/ingestion?system=collect&module=catalog.classify', NULL,
       'hub:ingestion:collect:catalog:classify', NULL, 2, 'M066', 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7751 OR permission = 'hub:ingestion:collect:catalog:classify');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7752, 7023, '资源目录注册发布', 2,
       '/exchange/ingestion?system=collect&module=catalog.publish', NULL,
       'hub:ingestion:collect:catalog:publish', NULL, 3, 'M067', 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7752 OR permission = 'hub:ingestion:collect:catalog:publish');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7753, 7023, '数据资源目录审批', 2,
       '/exchange/ingestion?system=collect&module=catalog.approvals', NULL,
       'hub:ingestion:collect:catalog:approvals', NULL, 4, 'M068', 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7753 OR permission = 'hub:ingestion:collect:catalog:approvals');

-- 超级管理员
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (7023, 7750, 7751, 7752, 7753)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 部门管理员：编目 + 注册发布（含父级）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.id FROM sys_menu m
WHERE m.id IN (7023, 7750, 7752)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id);

-- 平台管理员角色（若不存在则创建）
INSERT INTO sys_role (role_code, role_name, role_type, description, status)
SELECT 'PLATFORM_ADMIN', '平台管理员', 2, '平台级资源分类与目录审批', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'PLATFORM_ADMIN');

-- 平台管理员：分类 + 审批（含父级）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'PLATFORM_ADMIN'
  AND m.id IN (7023, 7751, 7753)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );
