-- V105: 统一门户导航配置（portal_nav_node）+ UUM「门户配置」菜单

CREATE TABLE IF NOT EXISTS portal_nav_node (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '根平台为 0',
    name        VARCHAR(128) NOT NULL,
    node_type   VARCHAR(32)  NOT NULL COMMENT 'platform / sub_platform / system',
    sort_order  INT          NOT NULL DEFAULT 0,
    url         VARCHAR(512) NULL,
    menu_path   VARCHAR(256) NULL COMMENT '关联前端路由，用于权限过滤',
    open_mode   VARCHAR(32)  NOT NULL DEFAULT 'route' COMMENT 'route / new_tab',
    theme_key   VARCHAR(64)  NULL COMMENT '仅 platform：卡片配色键',
    remark      VARCHAR(512) NULL,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_portal_nav_parent (parent_id),
    KEY idx_portal_nav_type (node_type),
    KEY idx_portal_nav_status (status)
) COMMENT='统一门户三级导航配置';

-- ── 平台 ──────────────────────────────────────────────
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 1, 0, '数据共享交换平台', 'platform', 1, NULL, NULL, 'route', '/exchange', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 1);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 2, 0, '主数据平台', 'platform', 2, NULL, NULL, 'route', '/master-data', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 2);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 3, 0, '大数据挖掘分析平台', 'platform', 3, NULL, NULL, 'route', '/analytics', NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 3);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 4, 0, '业务功能平台', 'platform', 4, NULL, NULL, 'route', '/business', '可后续补充外链/系统', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 4);

-- ──（一）数据共享交换 · 子平台 ─────────────────────────
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 11, 1, '大数据归集平台', 'sub_platform', 1, '/exchange/ingestion', '/exchange/ingestion', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 11);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 12, 1, '服务总线', 'sub_platform', 2, NULL, NULL, 'route', NULL, '暂无系统入口', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 12);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 13, 1, '应用平台', 'sub_platform', 3, '/exchange/application', '/exchange/application', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 13);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 14, 1, '应用分析门户', 'sub_platform', 4, '/exchange/analysis-portal', '/exchange/analysis-portal', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 14);

-- 归集系统
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 111, 11, '数据资产登记管理系统', 'system', 1,
       '/exchange/ingestion?system=register', '/exchange/ingestion?system=register', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 111);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 112, 11, '数据资源采集汇聚系统', 'system', 2,
       '/exchange/ingestion?system=collect', '/exchange/ingestion?system=collect', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 112);

-- 应用平台系统
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 131, 13, '数据供需对接系统', 'system', 1,
       '/exchange/application/supply', '/exchange/application/supply', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 131);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 132, 13, '考核评估系统', 'system', 2,
       '/exchange/application/assessment', '/exchange/application/assessment', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 132);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 133, 13, '基础库统计分析', 'system', 3,
       '/exchange/application/stats-base', '/exchange/application/stats-base', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 133);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 134, 13, '重点领域统计分析应用', 'system', 4,
       '/exchange/application/stats-domain', '/exchange/application/stats-domain', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 134);

-- 应用分析门户系统
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 141, 14, '部门数据共享门户', 'system', 1,
       '/exchange/analysis-portal/dept', '/exchange/analysis-portal/dept', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 141);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 142, 14, '领导决策门户', 'system', 2,
       '/exchange/analysis-portal/leader', '/exchange/analysis-portal/leader', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 142);

-- ──（二）主数据 · 子平台 ───────────────────────────────
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 21, 2, '大数据融合治理平台', 'sub_platform', 1, '/governance', '/governance', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 21);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 22, 2, '非结构数据融合治理平台', 'sub_platform', 2, '/unstructured', '/unstructured', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 22);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 23, 2, '大数据平台资源中心', 'sub_platform', 3, '/resource-center', '/resource-center', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 23);

-- ──（三）挖掘分析 · 子平台 / 系统 ──────────────────────
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 31, 3, '通用支撑平台', 'sub_platform', 1, NULL, NULL, 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 31);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 32, 3, '业务支撑平台', 'sub_platform', 2, '/analytics', '/analytics', 'route', NULL, '人口/法人/宏观经济等可后续配置', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 32);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 311, 31, '统一用户管理系统', 'system', 1, '/system/uum', '/system/uum', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 311);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 312, 31, '智能BI平台', 'system', 2, '/analytics/bi', '/analytics/bi', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 312);

-- ── UUM 侧栏「门户配置」+ 按钮权限 ─────────────────────
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7607, m.id, '门户配置', 2, '/system/uum?tab=portal', NULL, 'hub:system:uum:portal', NULL, 8, NULL, 'hub', 1, 0
FROM sys_menu m
WHERE m.path = '/system/uum'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7607 OR permission = 'hub:system:uum:portal');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7608, 7607, '门户导航查询', 3, NULL, NULL, 'system:portal-nav:list', NULL, 1, NULL, 'self', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7608 OR permission = 'system:portal-nav:list');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7609, 7607, '门户导航新增', 3, NULL, NULL, 'system:portal-nav:add', NULL, 2, NULL, 'self', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7609 OR permission = 'system:portal-nav:add');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7610, 7607, '门户导航编辑', 3, NULL, NULL, 'system:portal-nav:edit', NULL, 3, NULL, 'self', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7610 OR permission = 'system:portal-nav:edit');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7611, 7607, '门户导航删除', 3, NULL, NULL, 'system:portal-nav:delete', NULL, 4, NULL, 'self', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7611 OR permission = 'system:portal-nav:delete');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id BETWEEN 7607 AND 7611
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 避免后续自增与种子 ID 冲突
ALTER TABLE portal_nav_node AUTO_INCREMENT = 1000;
