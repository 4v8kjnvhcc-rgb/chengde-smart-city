-- M048 访问控制：项目资源授权、数据授权、按钮权限种子、跨部门扩展

CREATE TABLE IF NOT EXISTS sys_project_grant (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    grantee_type  VARCHAR(16)  NOT NULL COMMENT 'USER/ROLE',
    grantee_id    BIGINT       NOT NULL,
    org_id        BIGINT       NOT NULL,
    perm          VARCHAR(16)  NOT NULL DEFAULT 'VIEW' COMMENT 'VIEW/EDIT/ADMIN',
    granted_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_grantee (project_id, grantee_type, grantee_id),
    KEY idx_grantee (grantee_type, grantee_id),
    KEY idx_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_data_grant (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    scope_type    VARCHAR(16)  NOT NULL COMMENT 'SOURCE/TABLE',
    scope_id      BIGINT       NOT NULL,
    grantee_type  VARCHAR(16)  NOT NULL COMMENT 'ORG/PROJECT/USER/ROLE',
    grantee_id    BIGINT       NOT NULL,
    org_id        BIGINT       NOT NULL,
    perm          VARCHAR(16)  NOT NULL DEFAULT 'READ' COMMENT 'READ/WRITE',
    granted_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_data_grantee (scope_type, scope_id, grantee_type, grantee_id),
    KEY idx_data_grantee (grantee_type, grantee_id),
    KEY idx_data_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 跨部门申请补充审批意见（V1 表已存在）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cross_dept_access_request' AND COLUMN_NAME = 'approve_comment'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE cross_dept_access_request ADD COLUMN approve_comment VARCHAR(512) NULL AFTER approved_at',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 机构 A 演示项目（若不存在）
INSERT INTO ing_project (project_code, project_name, bound_org_id, system_name, status, created_by)
SELECT 'PRJ_ORG_A_ACCESS', '机构A访问控制演示项目', 2, '访问控制演示', 'ACTIVE', 'dept_admin_a'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ing_project WHERE project_code = 'PRJ_ORG_A_ACCESS');

-- 按钮级权限菜单（挂在机构管理 M048 下 parent_id=26，若不存在则跳过挂载用 0）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 26, '项目授权管理', 3, NULL, NULL, 'access:project-grant:manage', NULL, 91, 'M048', 'native'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'access:project-grant:manage');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 26, '数据授权管理', 3, NULL, NULL, 'access:data-grant:manage', NULL, 92, 'M048', 'native'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'access:data-grant:manage');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 26, '跨部门申请', 3, NULL, NULL, 'access:cross-dept:apply', NULL, 93, 'M048', 'native'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'access:cross-dept:apply');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 26, '跨部门审批', 3, NULL, NULL, 'access:cross-dept:approve', NULL, 94, 'M048', 'native'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'access:cross-dept:approve');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 26, '项目列表', 3, NULL, NULL, 'exchange:project:list', NULL, 95, 'M048', 'native'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'exchange:project:list');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 26, '项目创建', 3, NULL, NULL, 'exchange:project:create', NULL, 96, 'M048', 'native'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'exchange:project:create');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 26, '数据源查看', 3, NULL, NULL, 'exchange:datasource:list', NULL, 97, 'M048', 'native'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'exchange:datasource:list');

INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 26, '数据表查看', 3, NULL, NULL, 'exchange:table:list', NULL, 98, 'M048', 'native'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'exchange:table:list');

-- 系统管理员：功能按钮（含授权管理页入口权限，但业务写入仍由服务层双重授权拦截）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.permission IN (
    'access:project-grant:manage', 'access:data-grant:manage',
    'access:cross-dept:apply', 'access:cross-dept:approve',
    'exchange:project:list', 'exchange:project:create',
    'exchange:datasource:list', 'exchange:table:list'
)
AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
);

-- 机构管理员：资源/数据授权 + 跨部门审批 + 业务查看
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.id FROM sys_menu m
WHERE m.permission IN (
    'access:project-grant:manage', 'access:data-grant:manage',
    'access:cross-dept:apply', 'access:cross-dept:approve',
    'exchange:project:list', 'exchange:project:create',
    'exchange:datasource:list', 'exchange:table:list'
)
AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id
);

-- 普通用户：申请 + 列表查看
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.id FROM sys_menu m
WHERE m.permission IN (
    'access:cross-dept:apply',
    'exchange:project:list', 'exchange:datasource:list', 'exchange:table:list'
)
AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.id
);

-- 资源授权种子：机构A项目 → user_a；现有 bound_org=1 项目 → sys_admin（模拟部门已授，保证演示不中断）
INSERT INTO sys_project_grant (project_id, grantee_type, grantee_id, org_id, perm, granted_by)
SELECT p.id, 'USER', 3, 2, 'EDIT', 2
FROM ing_project p
WHERE p.project_code = 'PRJ_ORG_A_ACCESS'
AND NOT EXISTS (
    SELECT 1 FROM sys_project_grant g
    WHERE g.project_id = p.id AND g.grantee_type = 'USER' AND g.grantee_id = 3
);

INSERT INTO sys_project_grant (project_id, grantee_type, grantee_id, org_id, perm, granted_by)
SELECT p.id, 'USER', 1, COALESCE(p.bound_org_id, 1), 'ADMIN', 2
FROM ing_project p
WHERE p.bound_org_id = 1
AND NOT EXISTS (
    SELECT 1 FROM sys_project_grant g
    WHERE g.project_id = p.id AND g.grantee_type = 'USER' AND g.grantee_id = 1
);

-- 数据授权种子：机构A项目下表授给 user_a（若有表）；ORG 级授给机构A
INSERT INTO sys_data_grant (scope_type, scope_id, grantee_type, grantee_id, org_id, perm, granted_by)
SELECT 'TABLE', t.id, 'USER', 3, 2, 'READ', 2
FROM ing_data_table t
JOIN ing_data_source s ON s.id = t.source_id
JOIN ing_project p ON p.id = s.project_id AND p.project_code = 'PRJ_ORG_A_ACCESS'
LIMIT 1;

INSERT INTO sys_data_grant (scope_type, scope_id, grantee_type, grantee_id, org_id, perm, granted_by)
SELECT 'SOURCE', s.id, 'ORG', 2, 2, 'READ', 2
FROM ing_data_source s
JOIN ing_project p ON p.id = s.project_id AND p.project_code = 'PRJ_ORG_A_ACCESS'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_data_grant g
    WHERE g.scope_type = 'SOURCE' AND g.scope_id = s.id AND g.grantee_type = 'ORG' AND g.grantee_id = 2
)
LIMIT 1;
