-- M027~M030 考核评估系统

CREATE TABLE IF NOT EXISTS biz_eval_data_source (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_code VARCHAR(64) NOT NULL,
  source_name VARCHAR(128) NOT NULL,
  source_type VARCHAR(32) NOT NULL COMMENT 'AUDIT/BIZ_METRIC/EXCHANGE',
  ref_table VARCHAR(64) NULL,
  last_sync_at DATETIME NULL,
  record_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_code (source_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS biz_eval_period (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  period_code VARCHAR(64) NOT NULL,
  period_name VARCHAR(128) NOT NULL,
  cycle_type VARCHAR(32) NOT NULL COMMENT 'WEEK/MONTH/QUARTER/HALF_YEAR/YEAR/CUSTOM',
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_period_code (period_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS biz_eval_indicator (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  indicator_code VARCHAR(64) NOT NULL,
  indicator_name VARCHAR(128) NOT NULL,
  indicator_type VARCHAR(8) NOT NULL COMMENT 'A=自动 B=人工',
  weight DECIMAL(5,2) NOT NULL DEFAULT 1.00,
  data_source_id BIGINT NULL,
  formula_desc VARCHAR(256) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_indicator_code (indicator_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS biz_eval_execution (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  period_id BIGINT NOT NULL,
  target_type VARCHAR(32) NOT NULL COMMENT 'REGION/DEPT/POSITION/SYSTEM',
  target_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
  total_score DECIMAL(8,2) NULL,
  published TINYINT NOT NULL DEFAULT 0,
  executed_by VARCHAR(64) NULL,
  executed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_period (period_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS biz_eval_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  execution_id BIGINT NOT NULL,
  indicator_id BIGINT NOT NULL,
  score DECIMAL(8,2) NOT NULL,
  raw_value VARCHAR(128) NULL,
  remark VARCHAR(256) NULL,
  KEY idx_exec (execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO biz_eval_data_source (source_code, source_name, source_type, ref_table, last_sync_at, record_count) VALUES
('DS_AUDIT', '平台操作审计日志', 'AUDIT', 'audit_log', NOW(), 0),
('DS_EXCHANGE', '共享交换统计', 'EXCHANGE', 'biz_catalog_item', NOW(), 0),
('DS_COLLECT', '归集任务成功率', 'BIZ_METRIC', 'biz_collect_task', NOW(), 0)
ON DUPLICATE KEY UPDATE source_name = VALUES(source_name);

INSERT INTO biz_eval_period (period_code, period_name, cycle_type, start_date, end_date, status, created_by) VALUES
('2026-Q2', '2026年第二季度考核', 'QUARTER', '2026-04-01', '2026-06-30', 'ACTIVE', 'sys_admin')
ON DUPLICATE KEY UPDATE period_name = VALUES(period_name);

INSERT INTO biz_eval_indicator (indicator_code, indicator_name, indicator_type, weight, data_source_id, formula_desc) VALUES
('IND_SHARE_RATE', '数据共享率', 'A', 30.00, 2, '已发布目录数/总目录数'),
('IND_COLLECT_OK', '归集任务成功率', 'A', 25.00, 3, 'SUCCESS任务/总任务'),
('IND_AUDIT_COVER', '审计覆盖度', 'A', 20.00, 1, '审计事件数/活跃用户数'),
('IND_SATISFACTION', '用户满意度', 'B', 15.00, NULL, '人工打分1-100'),
('IND_RESP_TIME', '需求响应时效', 'B', 10.00, NULL, '人工打分1-100')
ON DUPLICATE KEY UPDATE indicator_name = VALUES(indicator_name);

-- 菜单：考核评估（M027 入口）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 35, 3, '考核评估', 2, '/exchange/assessment', 'exchange/AssessmentView', NULL, NULL, 8, 'M027', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 35);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, 35 FROM (SELECT 1 AS id UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) r
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 35);
