-- 门户卡片外链：可在各平台卡片下挂接外部业务系统，配合统一身份 Token 跳转
CREATE TABLE IF NOT EXISTS portal_card_link (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    platform_path VARCHAR(64)  NOT NULL COMMENT '所属卡片路径：/exchange|/master-data|/analytics|/business|/system',
    title         VARCHAR(128) NOT NULL COMMENT '显示名称',
    url           VARCHAR(1024) NOT NULL COMMENT '目标地址',
    description   VARCHAR(255) NULL,
    open_mode     VARCHAR(16)  NOT NULL DEFAULT 'new_tab' COMMENT 'new_tab|same_tab',
    sso_mode      VARCHAR(32)  NOT NULL DEFAULT 'token_query' COMMENT 'none|token_query',
    sso_param     VARCHAR(64)  NOT NULL DEFAULT 'access_token' COMMENT 'SSO 查询参数名',
    sort_order    INT          NOT NULL DEFAULT 0,
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_portal_link_platform (platform_path, status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一门户卡片外链';

-- 一级平台：业务功能平台（位于系统管理之前）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6000, 1, '业务功能平台', 1, '/business', NULL, NULL, 'Grid', 18, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6000 OR path = '/business');

-- 系统管理：门户外链管理
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6001, 19, '门户外链管理', 2, '/system/portal-links', 'system/PortalLinkManage', 'system:portal-link:list', NULL, 32, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6001 OR path = '/system/portal-links');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6002, 6001, '外链查询', 3, NULL, NULL, 'system:portal-link:query', NULL, 1, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:portal-link:query');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6003, 6001, '外链新增', 3, NULL, NULL, 'system:portal-link:add', NULL, 2, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:portal-link:add');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6004, 6001, '外链编辑', 3, NULL, NULL, 'system:portal-link:edit', NULL, 3, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:portal-link:edit');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6005, 6001, '外链删除', 3, NULL, NULL, 'system:portal-link:delete', NULL, 4, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:portal-link:delete');

-- 授权给系统管理员
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id BETWEEN 6000 AND 6005
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 示例：业务功能平台下一条例子（可在管理页改删）
INSERT INTO portal_card_link (platform_path, title, url, description, open_mode, sso_mode, sso_param, sort_order, status)
SELECT '/business', '示例业务系统', 'https://example.com', '请改为真实业务系统地址；启用 SSO 后将携带本平台 Token', 'new_tab', 'token_query', 'access_token', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_card_link WHERE platform_path = '/business' AND title = '示例业务系统');
