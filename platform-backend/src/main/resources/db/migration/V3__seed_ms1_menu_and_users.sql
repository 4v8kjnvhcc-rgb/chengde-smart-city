-- MS1 菜单树 + 机构/角色/用户（D08 测试账号）
-- 默认密码: Test@12345（BCrypt）

DELETE FROM sys_role_menu;
DELETE FROM sys_user_role;
DELETE FROM sys_menu;
DELETE FROM sys_user;
DELETE FROM sys_role;
DELETE FROM sys_org;
DELETE FROM sys_security_config;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (1, 0, '统一门户', 1, '/', NULL, NULL, 'HomeFilled', 1, NULL, 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (2, 1, '工作台', 2, '/dashboard', 'dashboard/Index', 'dashboard:view', 'Odometer', 2, NULL, 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (3, 1, '数据共享交换平台', 1, '/exchange', NULL, NULL, 'Connection', 3, NULL, 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (4, 3, '大数据归集', 2, '/exchange/ingestion', NULL, NULL, NULL, 4, 'M039', 'placeholder');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (5, 3, '服务总线', 2, '/exchange/esb', NULL, NULL, NULL, 5, 'M214', 'esb');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (6, 3, '应用平台', 2, '/exchange/application', NULL, NULL, NULL, 6, 'M020', 'placeholder');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (7, 3, '应用分析门户', 2, '/exchange/analysis-portal', NULL, NULL, NULL, 7, 'M031', 'iframe');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (8, 1, '主数据平台', 1, '/master-data', NULL, NULL, 'Coin', 8, NULL, 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (9, 8, '数据融合治理', 2, '/governance', NULL, NULL, NULL, 9, 'M078', 'om');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (10, 8, '非结构化治理', 2, '/unstructured', NULL, NULL, NULL, 10, 'M123', 'placeholder');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (11, 8, '资源中心', 2, '/resource-center', NULL, NULL, NULL, 11, 'M130', 'placeholder');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (12, 1, '大数据挖掘分析平台', 1, '/analytics', NULL, NULL, 'DataAnalysis', 12, NULL, 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (13, 12, '通用支撑', 2, '/analytics/support', NULL, NULL, NULL, 13, 'M139', 'placeholder');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (14, 12, '智能BI', 2, '/analytics/bi', NULL, NULL, NULL, 14, 'M146', 'iframe');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (15, 12, '人口大数据', 2, '/analytics/population', NULL, NULL, NULL, 15, 'M152', 'iframe');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (16, 12, '法人大数据', 2, '/analytics/legal-entity', NULL, NULL, NULL, 16, 'M175', 'iframe');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (17, 12, '宏观经济', 2, '/analytics/macro', NULL, NULL, NULL, 17, 'M193', 'iframe');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (18, 12, '重点领域', 2, '/analytics/key-domains', NULL, NULL, NULL, 18, 'M204', 'iframe');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (19, 1, '系统管理', 1, '/system', NULL, NULL, 'Setting', 19, NULL, 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (20, 19, '用户管理', 2, '/system/users', 'system/UserManage', 'system:user:list', NULL, 20, 'M211', 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (21, 20, '用户查询', 3, NULL, NULL, 'system:user:query', NULL, 21, 'M211', 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (22, 20, '用户新增', 3, NULL, NULL, 'system:user:add', NULL, 22, 'M211', 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (23, 20, '用户编辑', 3, NULL, NULL, 'system:user:edit', NULL, 23, 'M211', 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (24, 20, '用户删除', 3, NULL, NULL, 'system:user:delete', NULL, 24, 'M211', 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (25, 19, '角色管理', 2, '/system/roles', 'system/RoleManage', 'system:role:list', NULL, 25, 'M211', 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (26, 19, '机构管理', 2, '/system/orgs', 'system/OrgManage', 'system:org:list', NULL, 26, 'M048', 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (27, 19, '菜单管理', 2, '/system/menus', 'system/MenuManage', 'system:menu:list', NULL, 27, 'M050', 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (28, 19, '审计日志', 2, '/system/audit', 'system/AuditLog', 'system:audit:list', NULL, 28, 'M144', 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (29, 19, '等保开关', 2, '/system/security', 'system/SecurityConfig', 'system:security:config', NULL, 29, 'M049', 'self');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (30, 19, '调度管理', 2, '/integration/ds', NULL, NULL, NULL, 30, 'M098', 'placeholder');
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type) VALUES (31, 19, 'ETL治理', 2, '/integration/kettle', NULL, NULL, NULL, 31, 'M215', 'placeholder');

INSERT INTO sys_org (id, parent_id, org_code, org_name, org_type) VALUES
(1, 0, 'ORG_ROOT', '承德高新区', 1),
(2, 1, 'ORG_A', '机构A', 1),
(3, 1, 'ORG_B', '机构B', 1);

INSERT INTO sys_role (id, role_code, role_name, role_type, description) VALUES
(1, 'SYSTEM_ADMIN', '系统管理员', 1, '全平台管理'),
(2, 'DEPT_ADMIN', '机构管理员', 2, '本机构管理'),
(3, 'USER', '普通用户', 2, '业务用户'),
(4, 'USER_QUERY_ONLY', '仅查询用户', 2, 'RBAC演示：仅用户查询');

INSERT INTO sys_user (id, username, password_hash, display_name, org_id, status) VALUES
(1, 'sys_admin', '$2a$10$CWY2FHCAutORDZAK6auKauFUi8R.VSu4PMwnwhjuzcj5XaCqSHyZm', '系统管理员', 1, 1),
(2, 'dept_admin_a', '$2a$10$CWY2FHCAutORDZAK6auKauFUi8R.VSu4PMwnwhjuzcj5XaCqSHyZm', '机构A管理员', 2, 1),
(3, 'user_a', '$2a$10$CWY2FHCAutORDZAK6auKauFUi8R.VSu4PMwnwhjuzcj5XaCqSHyZm', '机构A用户', 2, 1),
(4, 'user_b', '$2a$10$CWY2FHCAutORDZAK6auKauFUi8R.VSu4PMwnwhjuzcj5XaCqSHyZm', '机构B用户', 3, 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1,1), (2,2), (3,3), (4,3);

INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;

INSERT INTO sys_role_menu (role_id, menu_id) SELECT 2, id FROM sys_menu WHERE permission IS NULL OR permission NOT LIKE 'system:user:%' OR permission = 'system:user:query';

INSERT INTO sys_role_menu (role_id, menu_id) SELECT 3, id FROM sys_menu WHERE path = '/dashboard' OR menu_name = '工作台';

INSERT INTO sys_role_menu (role_id, menu_id) SELECT 4, id FROM sys_menu WHERE permission IS NULL OR permission = 'system:user:query' OR path = '/dashboard';

INSERT INTO sys_security_config (config_key, config_value, description) VALUES
('two_factor_enabled', 'false', '双因素登录（MS1 默认关闭，验收时可开）'),
('password_min_length', '8', '密码最小长度'),
('password_require_complex', 'true', '密码复杂度'),
('login_max_failures', '5', '登录失败锁定次数'),
('login_lock_minutes', '60', '锁定时长分钟'),
('session_idle_minutes', '30', '会话空闲超时'),
('access_token_minutes', '30', 'Access Token 有效期'),
('refresh_token_hours', '8', 'Refresh Token 有效期'),
('audit_enabled', 'true', '审计开关');
