-- V94：归集「数据资源采集汇聚」增加「汇聚数据质量管控」Hub 侧栏（复用质量管理系统三页，治理侧入口保留）

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7024, 7020, '汇聚数据质量管控', 2,
       '/exchange/ingestion?system=collect&module=quality.rule-config',
       NULL, 'hub:ingestion:collect:quality', NULL, 4, NULL, 'hub', 1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7024 OR permission = 'hub:ingestion:collect:quality');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 7024
FROM DUAL
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 7024)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 7024);
