-- V151: 系统执行周期管理（统一 Cron 配置，供各业务定时任务选用）

CREATE TABLE IF NOT EXISTS sys_exec_cycle (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cycle_code VARCHAR(64) NOT NULL COMMENT '周期编码',
  cycle_name VARCHAR(128) NOT NULL COMMENT '周期名称',
  cron_expr VARCHAR(128) NOT NULL COMMENT 'Cron表达式（秒 分 时 日 月 周 [年]）',
  description VARCHAR(512) NULL COMMENT '说明',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|INACTIVE',
  sort_order INT NOT NULL DEFAULT 0,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_exec_cycle_code (cycle_code),
  KEY idx_sys_exec_cycle_status (status)
) COMMENT='系统执行周期（统一定时配置）';

INSERT INTO sys_exec_cycle (cycle_code, cycle_name, cron_expr, description, status, sort_order, created_by) VALUES
('EVERY_HOUR', '每小时整点', '0 0 * * * ?', '每小时 0 分执行', 'ACTIVE', 10, 'system'),
('DAILY_2AM', '每天凌晨2点', '0 0 2 * * ?', '每天 02:00 执行', 'ACTIVE', 20, 'system'),
('DAILY_0AM', '每天零点', '0 0 0 * * ?', '每天 00:00 执行', 'ACTIVE', 30, 'system'),
('WEEKLY_MON', '每周一凌晨2点', '0 0 2 ? * MON', '每周一 02:00 执行', 'ACTIVE', 40, 'system'),
('EVERY_5MIN', '每5分钟', '0 */5 * * * ?', '每5分钟执行一次', 'ACTIVE', 50, 'system')
ON DUPLICATE KEY UPDATE cycle_name = VALUES(cycle_name), cron_expr = VALUES(cron_expr);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7726, cfg.id, '执行周期管理', 2, '/analytics/support?tab=sys.cfg.cron', NULL, 'hub:analytics:support:sys:cron', NULL, 3, NULL, 'hub', 1, 0
FROM (SELECT id FROM sys_menu WHERE parent_id = 7504 AND menu_name = '系统配置' ORDER BY id LIMIT 1) cfg
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7726 OR path = '/analytics/support?tab=sys.cfg.cron');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 7726
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 7726);
