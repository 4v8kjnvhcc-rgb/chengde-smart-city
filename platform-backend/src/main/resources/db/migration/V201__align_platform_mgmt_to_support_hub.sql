-- V201: 角色「配置菜单」与实际「通用支撑」Hub 对齐
-- 停用过时「平台管理 /system」旧树（身份与权限→统一用户管理等），避免与 /analytics/support 双轨并存。

-- ========== A. 停用过时平台管理 / UUM / 集成运维一级壳 ==========
UPDATE sys_menu
SET status = 0, visible = 0
WHERE status = 1
  AND (
    id = 19
    OR id IN (6400, 6401, 6402, 6403, 6410, 6500)
    OR id BETWEEN 7600 AND 7611
    OR id IN (20, 25, 26, 27, 28, 29, 6001, 6300)
    OR path IN (
      '/system', '/system/uum', '/system/users', '/system/roles', '/system/orgs', '/system/menus',
      '/system/portal-links', '/system/tags', '/system/audit', '/system/maintenance',
      '/system/security', '/system/access', '/integration'
    )
    OR path LIKE '/system/uum%'
    OR permission LIKE 'hub:system:uum:%'
  );

-- ========== B. 确保通用支撑入口与 Hub 侧栏节点启用 ==========
UPDATE sys_menu
SET status = 1,
    visible = 0,
    menu_name = '通用支撑平台',
    path = '/analytics/support',
    integration_type = COALESCE(NULLIF(integration_type, ''), 'hub')
WHERE id = 13;

UPDATE sys_menu
SET status = 1, visible = 0, integration_type = 'hub'
WHERE id IN (7500, 7501, 7502, 7503, 7504, 7505, 7506, 7507, 7508)
   OR id BETWEEN 7700 AND 7741
   OR id IN (7726, 7727, 7800)
   OR permission LIKE 'hub:analytics:support:%';

-- 侧栏顺序与 AnalyticsSupportHubView 一致
UPDATE sys_menu SET sort_order = 1, menu_name = '用户中心', parent_id = 13 WHERE id = 7500;
UPDATE sys_menu SET sort_order = 2, menu_name = '应用中心', parent_id = 13 WHERE id = 7501;
UPDATE sys_menu SET sort_order = 3, menu_name = '认证中心', parent_id = 13 WHERE id = 7502;
UPDATE sys_menu SET sort_order = 4, menu_name = '服务中心', parent_id = 13 WHERE id = 7503;
UPDATE sys_menu SET sort_order = 5, menu_name = '任务管理', parent_id = 13 WHERE id = 7507;
UPDATE sys_menu SET sort_order = 6, menu_name = '集成运维', parent_id = 13 WHERE id = 7508;
UPDATE sys_menu SET sort_order = 7, menu_name = '系统管理', parent_id = 13 WHERE id = 7504;
UPDATE sys_menu SET sort_order = 8, menu_name = '日志审计', parent_id = 13 WHERE id = 7505;
UPDATE sys_menu SET sort_order = 9, menu_name = '其他', parent_id = 13 WHERE id = 7506;

-- ========== C. 旧授权迁移到通用支撑（有旧平台管理权 → 授对应 support 菜单） ==========
-- 曾授「平台管理/UUM」任一节点的角色，补授通用支撑根与 Hub 一级目录
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, t.menu_id
FROM sys_role_menu rm
INNER JOIN sys_menu oldm ON oldm.id = rm.menu_id
CROSS JOIN (
  SELECT 13 AS menu_id UNION ALL
  SELECT 7500 UNION ALL SELECT 7501 UNION ALL SELECT 7502 UNION ALL SELECT 7503 UNION ALL
  SELECT 7504 UNION ALL SELECT 7505 UNION ALL SELECT 7506 UNION ALL SELECT 7507 UNION ALL SELECT 7508
) t
WHERE (
    oldm.id = 19
    OR oldm.id BETWEEN 6400 AND 6410
    OR oldm.id BETWEEN 7600 AND 7611
    OR IFNULL(oldm.path, '') LIKE '/system%'
    OR IFNULL(oldm.permission, '') LIKE 'hub:system:uum:%'
    OR IFNULL(oldm.permission, '') LIKE 'system:%'
  )
  AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = t.menu_id AND m.status = 1)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = t.menu_id
  );

-- 细项映射：旧 UUM Tab → support 叶子
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, map.new_id
FROM sys_role_menu rm
INNER JOIN sys_menu oldm ON oldm.id = rm.menu_id
INNER JOIN (
  SELECT 'hub:system:uum:users' AS old_perm, 7700 AS new_id UNION ALL
  SELECT 'hub:system:uum:users', 7701 UNION ALL
  SELECT 'hub:system:uum:users', 7702 UNION ALL
  SELECT 'hub:system:uum:users', 7703 UNION ALL
  SELECT 'hub:system:uum:apps', 7710 UNION ALL
  SELECT 'hub:system:uum:apps', 7711 UNION ALL
  SELECT 'hub:system:uum:apps', 7712 UNION ALL
  SELECT 'hub:system:uum:auth', 7502 UNION ALL
  SELECT 'hub:system:uum:services', 7503 UNION ALL
  SELECT 'hub:system:uum:audit', 7730 UNION ALL
  SELECT 'hub:system:uum:integration', 7711 UNION ALL
  SELECT 'hub:system:uum:portal', 7712 UNION ALL
  SELECT 'system:user:list', 7701 UNION ALL
  SELECT 'system:role:list', 7702 UNION ALL
  SELECT 'system:org:list', 7700 UNION ALL
  SELECT 'system:menu:list', 7720 UNION ALL
  SELECT 'system:audit:list', 7730 UNION ALL
  SELECT 'system:security:config', 7732 UNION ALL
  SELECT 'system:tag:list', 7725 UNION ALL
  SELECT 'system:tag:query', 7725 UNION ALL
  SELECT 'system:tag:edit', 7725
) map ON map.old_perm = oldm.permission
WHERE EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = map.new_id AND m.status = 1)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = map.new_id
  );

-- ========== D. 清理已停用菜单上的角色勾选 ==========
DELETE rm FROM sys_role_menu rm
INNER JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.status = 0
  AND (
    m.id = 19
    OR m.id IN (6400, 6401, 6402, 6403, 6410, 6500)
    OR m.id BETWEEN 7600 AND 7611
    OR m.id IN (20, 25, 26, 27, 28, 29, 6001, 6300)
    OR IFNULL(m.path, '') LIKE '/system%'
    OR IFNULL(m.path, '') = '/integration'
    OR IFNULL(m.permission, '') LIKE 'hub:system:uum:%'
  );

-- ========== E. 系统管理员补齐通用支撑全量 ==========
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.status = 1
  AND (
    m.id = 13
    OR m.parent_id = 13
    OR m.id BETWEEN 7500 AND 7508
    OR m.id BETWEEN 7700 AND 7741
    OR m.id IN (7726, 7727, 7800)
    OR m.permission LIKE 'hub:analytics:support:%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );
