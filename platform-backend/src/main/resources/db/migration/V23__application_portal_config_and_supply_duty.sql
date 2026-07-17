-- 应用平台：同构配置菜单 + 供需双路径/数据责任 + 考核菜单收敛

-- 1) 业务字段：履约路径
ALTER TABLE biz_data_demand
  ADD COLUMN fulfill_path VARCHAR(32) NULL COMMENT 'AUTHORIZE_EXISTING|NEED_COLLECT' AFTER supply_mode;

-- 2) 数据责任台账
CREATE TABLE IF NOT EXISTS biz_data_duty (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  demand_id BIGINT NOT NULL,
  duty_org VARCHAR(128) NOT NULL,
  duty_type VARCHAR(32) NOT NULL COMMENT 'AUTHORIZE|COLLECT|SHARE',
  catalog_id BIGINT NULL,
  fulfill_path VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_duty_demand (demand_id),
  INDEX idx_duty_org (duty_org)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) 门户业务菜单：应用平台名称恢复；考核独立菜单隐藏为已并入应用平台
UPDATE sys_menu SET menu_name = '应用平台', component = NULL
WHERE id = 6 OR path = '/exchange/application';

UPDATE sys_menu SET menu_name = '考核评估(已并入应用平台)', path = '/exchange/assessment', sort_order = 99
WHERE id = 35;

-- 4) 系统管理同构配置树
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6100, 19, '数据共享交换平台', 1, '/system/exchange', NULL, NULL, 'Connection', 40, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6100);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6101, 6100, '应用平台', 1, '/system/exchange/application', NULL, NULL, NULL, 41, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6101);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6102, 6101, '供需配置', 2, '/system/exchange/application/supply-config', 'system/SupplyConfigView', 'system:exchange:supply-config', NULL, 42, 'M020', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6102);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6103, 6101, '考核评估配置', 2, '/system/exchange/application/assessment-config', 'system/AssessmentConfigView', 'system:exchange:assessment-config', NULL, 43, 'M027', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6103);

-- 管理员角色授权新菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (6100, 6101, 6102, 6103)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
