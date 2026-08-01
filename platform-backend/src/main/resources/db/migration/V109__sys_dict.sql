-- V109: 平台统一数据字典 sys_dict / sys_dict_item
-- 迁入 ana_platform_config 后删除旧表；业务字典 ing_dict* 不动
-- 注意：库表统一 utf8mb4_unicode_ci，避免与部分表 utf8mb4_0900_ai_ci 混比失败

CREATE TABLE IF NOT EXISTS sys_dict (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    dict_code   VARCHAR(64)  NOT NULL COMMENT '字典编码',
    dict_name   VARCHAR(128) NOT NULL COMMENT '字典名称',
    remark      VARCHAR(512) NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_dict_code (dict_code),
    KEY idx_sys_dict_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台统一数据字典-类型';

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    dict_id     BIGINT       NOT NULL COMMENT 'sys_dict.id',
    item_key    VARCHAR(128) NOT NULL COMMENT '项编码',
    item_value  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '项值',
    item_label  VARCHAR(256) NULL COMMENT '显示名/说明',
    sort_order  INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark      VARCHAR(512) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_dict_item (dict_id, item_key),
    KEY idx_sys_dict_item_dict (dict_id),
    KEY idx_sys_dict_item_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台统一数据字典-项';

-- 兼容此前失败迁移留下的表（可能已是错误排序规则）
ALTER TABLE sys_dict CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE sys_dict_item CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 按原 config_group 建类型
INSERT INTO sys_dict (dict_code, dict_name, remark, sort_order, status)
SELECT 'AUTH', '认证参数', '原 ana_platform_config.config_group=AUTH', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'AUTH');

INSERT INTO sys_dict (dict_code, dict_name, remark, sort_order, status)
SELECT 'SYSTEM', '系统参数', '原 ana_platform_config.config_group=SYSTEM', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'SYSTEM');

INSERT INTO sys_dict (dict_code, dict_name, remark, sort_order, status)
SELECT 'BI', 'BI参数', '原 ana_platform_config.config_group=BI', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'BI');

-- 迁入平台参数（显式 COLLATE，兼容 ana_platform_config 的 0900_ai_ci）
INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id,
       c.config_key,
       IFNULL(c.config_value, ''),
       c.description,
       CAST(c.id AS SIGNED),
       CASE WHEN UPPER(IFNULL(c.status, 'ACTIVE')) IN ('ACTIVE', '1', 'ENABLED') THEN 1 ELSE 0 END
FROM ana_platform_config c
INNER JOIN sys_dict d
        ON d.dict_code = CONVERT(c.config_group USING utf8mb4) COLLATE utf8mb4_unicode_ci
WHERE NOT EXISTS (
      SELECT 1 FROM sys_dict_item i
      WHERE i.dict_id = d.id
        AND i.item_key = CONVERT(c.config_key USING utf8mb4) COLLATE utf8mb4_unicode_ci
  );

-- 菜单权限（挂在支撑平台「字典管理」下；ID 避开 V106 的 7730–7732 审计菜单）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7760, p.id, '字典查询', 3, NULL, NULL, 'system:dict:list', NULL, 1, NULL, 'native', 1, 0
FROM (SELECT id FROM sys_menu WHERE id = 7721 OR permission = 'hub:analytics:support:sys:dict' ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7760 OR permission = 'system:dict:list');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7761, p.id, '字典新增', 3, NULL, NULL, 'system:dict:add', NULL, 2, NULL, 'native', 1, 0
FROM (SELECT id FROM sys_menu WHERE id = 7721 OR permission = 'hub:analytics:support:sys:dict' ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7761 OR permission = 'system:dict:add');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7762, p.id, '字典编辑', 3, NULL, NULL, 'system:dict:edit', NULL, 3, NULL, 'native', 1, 0
FROM (SELECT id FROM sys_menu WHERE id = 7721 OR permission = 'hub:analytics:support:sys:dict' ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7762 OR permission = 'system:dict:edit');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7763, p.id, '字典删除', 3, NULL, NULL, 'system:dict:delete', NULL, 4, NULL, 'native', 1, 0
FROM (SELECT id FROM sys_menu WHERE id = 7721 OR permission = 'hub:analytics:support:sys:dict' ORDER BY id LIMIT 1) p
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7763 OR permission = 'system:dict:delete');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'SYSTEM_ADMIN'
  AND m.permission IN ('system:dict:list', 'system:dict:add', 'system:dict:edit', 'system:dict:delete')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

DROP TABLE IF EXISTS ana_platform_config;
