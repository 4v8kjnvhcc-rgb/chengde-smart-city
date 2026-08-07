-- V155: 系统通用配置 — 部门数据共享门户跳转链接（供需需求填报「选择目录」提示用）

INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status, remark)
SELECT d.id,
       'DEPT_PORTAL_URL',
       '/exchange/analysis-portal/dept',
       '部门数据共享门户地址',
       10,
       1,
       '供需对接「选择目录」提示链接；支持相对路径或完整 URL'
FROM sys_dict d
WHERE d.dict_code = 'SYSTEM'
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict_item i
      WHERE i.dict_id = d.id AND i.item_key = 'DEPT_PORTAL_URL'
  );

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7727, cfg.id, '通用配置', 2, '/analytics/support?tab=sys.cfg.general', NULL, 'hub:analytics:support:sys:general', NULL, 0, NULL, 'hub', 1, 0
FROM (SELECT id FROM sys_menu WHERE parent_id = 7504 AND menu_name = '系统配置' ORDER BY id LIMIT 1) cfg
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7727 OR path = '/analytics/support?tab=sys.cfg.general');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 7727
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 7727);
