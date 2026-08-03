-- V111: 数据资产登记 sjzc 修复 — 登记审核状态、审核记录、字典关联数据项、角色菜单

-- ── 登记状态（草稿/待审核/审核通过/驳回待提交）────────────────
ALTER TABLE ing_project
  ADD COLUMN register_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/APPROVED/REJECTED' AFTER status,
  ADD COLUMN reject_reason VARCHAR(512) NULL AFTER register_status;

ALTER TABLE ing_biz_system
  ADD COLUMN register_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/APPROVED/REJECTED' AFTER status,
  ADD COLUMN reject_reason VARCHAR(512) NULL AFTER register_status;

ALTER TABLE ing_data_source
  ADD COLUMN register_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/APPROVED/REJECTED' AFTER conn_status,
  ADD COLUMN reject_reason VARCHAR(512) NULL AFTER register_status;

ALTER TABLE ing_dict
  ADD COLUMN register_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/APPROVED/REJECTED' AFTER status,
  ADD COLUMN reject_reason VARCHAR(512) NULL AFTER register_status;

ALTER TABLE ing_asset_tag
  ADD COLUMN register_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_REVIEW/APPROVED/REJECTED' AFTER status,
  ADD COLUMN reject_reason VARCHAR(512) NULL AFTER register_status;

-- 存量数据视为已审核通过（避免阻断现网）
UPDATE ing_project SET register_status = 'APPROVED' WHERE register_status = 'DRAFT' OR register_status IS NULL OR register_status = '';
UPDATE ing_biz_system SET register_status = 'APPROVED' WHERE register_status = 'DRAFT' OR register_status IS NULL OR register_status = '';
UPDATE ing_data_source SET register_status = 'APPROVED' WHERE register_status = 'DRAFT' OR register_status IS NULL OR register_status = '';
UPDATE ing_dict SET register_status = 'APPROVED' WHERE register_status = 'DRAFT' OR register_status IS NULL OR register_status = '';
UPDATE ing_asset_tag SET register_status = 'APPROVED' WHERE register_status = 'DRAFT' OR register_status IS NULL OR register_status = '';

-- 资产目录登记状态对齐 sjzc（待归档→待审核，已归档→审核通过）
UPDATE ing_asset_catalog_reg SET status = 'PENDING_REVIEW' WHERE status IN ('PENDING_ARCHIVE', 'PENDING');
UPDATE ing_asset_catalog_reg SET status = 'APPROVED' WHERE status IN ('ARCHIVED', 'APPROVED');
UPDATE ing_asset_catalog_reg SET status = 'REJECTED' WHERE status IN ('REJECTED', 'RETURNED');
UPDATE ing_asset_catalog_reg SET status = 'DRAFT' WHERE status NOT IN ('DRAFT','PENDING_REVIEW','APPROVED','REJECTED');

-- ── 审核操作记录 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ing_register_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  object_type VARCHAR(32) NOT NULL COMMENT 'PROJECT/SYSTEM/DATA_SOURCE/DICT/TAG/CATALOG_REG',
  object_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/CREATE/UPDATE/DELETE',
  from_status VARCHAR(32) NULL,
  to_status VARCHAR(32) NULL,
  comment_text VARCHAR(512) NULL,
  operator_id BIGINT NULL,
  operator_name VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_reg_audit_obj (object_type, object_id),
  KEY idx_reg_audit_created (created_at)
) COMMENT='数据资产登记审核/提交流水';

-- ── 字典关联数据项 ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ing_dict_column_link (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  dict_id BIGINT NOT NULL,
  column_id BIGINT NOT NULL,
  project_id BIGINT NULL,
  system_id BIGINT NULL,
  source_id BIGINT NULL,
  table_id BIGINT NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dict_column (dict_id, column_id),
  KEY idx_dict_link_dict (dict_id),
  KEY idx_dict_link_column (column_id)
) COMMENT='数据字典与数据项关联';

-- ── 权限：登记提交 / 审核 ───────────────────────────────────
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7016, 7000, '登记提交', 3, NULL, NULL, 'hub:ingestion:register:submit', NULL, 90, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'hub:ingestion:register:submit');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7017, 7000, '登记审核', 3, NULL, NULL, 'hub:ingestion:register:audit', NULL, 91, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'hub:ingestion:register:audit');

-- 菜单标题：平台侧「管理」语义（7002/7003 保持登记名；平台管理员看管理模块）
UPDATE sys_menu SET menu_name = '数据资产报告（总体）' WHERE id = 7008 AND menu_name = '数据资产报告';
UPDATE sys_menu SET menu_name = '数据资产图谱分析（总体）' WHERE id = 7009 AND menu_name = '数据资产图谱分析';

-- 部门报告/图谱：复用同一 path，另增菜单节点供部门角色（可选独立标题）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7018, 7000, '数据资产报告（部门）', 2, '/exchange/ingestion?system=register&module=m046', NULL, 'hub:ingestion:register:m046:dept', NULL, 8, 'M046', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'hub:ingestion:register:m046:dept');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7019, 7000, '数据资产图谱分析（部门）', 2, '/exchange/ingestion?system=register&module=m047', NULL, 'hub:ingestion:register:m047:dept', NULL, 9, 'M047', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'hub:ingestion:register:m047:dept');

-- ── 角色菜单：DEPT_ADMIN ─────────────────────────────────────
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'DEPT_ADMIN'
  AND m.permission IN (
    'hub:ingestion:register:m039',
    'hub:ingestion:register:m040',
    'hub:ingestion:register:m041',
    'hub:ingestion:register:m042',
    'hub:ingestion:register:m043',
    'hub:ingestion:register:asset-catalog-reg',
    'hub:ingestion:register:m046:dept',
    'hub:ingestion:register:m047:dept',
    'hub:ingestion:register:submit',
    'exchange:project:create'
  )
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- ── 角色菜单：PLATFORM_ADMIN ─────────────────────────────────
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'PLATFORM_ADMIN'
  AND (
    m.permission IN (
      'hub:ingestion:register:m039',
      'hub:ingestion:register:m040',
      'hub:ingestion:register:m041',
      'hub:ingestion:register:m045',
      'hub:ingestion:register:m050',
      'hub:ingestion:register:asset-catalog-mgmt',
      'hub:ingestion:register:m046',
      'hub:ingestion:register:m047',
      'hub:ingestion:register:audit',
      'hub:ingestion:register:submit'
    )
    OR m.id IN (7002, 7003)
  )
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

-- 平台管理员菜单显示名：项目/系统信息管理、数据库/表/项管理（侧栏用角色过滤后的 menu_name；
-- 同一菜单共享，名称统一改为带「管理」在平台侧由前端按角色覆盖，库表保留登记原名）

-- SYSTEM_ADMIN：提交+审核 + 登记全量（若尚未授权）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'SYSTEM_ADMIN'
  AND m.permission IN (
    'hub:ingestion:register:submit',
    'hub:ingestion:register:audit',
    'hub:ingestion:register:m046:dept',
    'hub:ingestion:register:m047:dept'
  )
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);
