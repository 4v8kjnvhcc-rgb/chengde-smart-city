-- M031~M036 应用分析门户

CREATE TABLE IF NOT EXISTS biz_portal_subscription (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  catalog_id BIGINT NOT NULL,
  applicant_org VARCHAR(128) NOT NULL,
  resource_type VARCHAR(32) NOT NULL COMMENT 'TABLE/FILE/API',
  purpose VARCHAR(256) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  approver_note VARCHAR(256) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_catalog (catalog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS biz_portal_situation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  situation_code VARCHAR(32) NOT NULL,
  situation_name VARCHAR(64) NOT NULL,
  domain_route VARCHAR(128) NOT NULL,
  model_m_code VARCHAR(16) NULL,
  summary_metric VARCHAR(128) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_situation_code (situation_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO biz_portal_situation (situation_code, situation_name, domain_route, model_m_code, summary_metric, sort_order) VALUES
('POPULATION', '人口态势', '/analytics/population', 'M161', '常住人口 58.2万', 1),
('LEGAL', '法人态势', '/analytics/legal-entity', 'M175', '法人主体 3.6万', 2),
('LEGAL_INDUSTRY', '法人行业态势', '/analytics/legal-entity', 'M192', '行业覆盖 24类', 3),
('INDUSTRY', '产业发展分布', '/analytics/macro', 'M201', '产业增加值 +6.2%', 4),
('ECONOMY', '经济运行态势', '/analytics/macro', 'M193', 'GDP 增速 5.8%', 5),
('SOCIAL', '社会治理态势', '/analytics/key-domains', 'M209', '救助覆盖 1.2万', 6),
('CITY', '城市管理态势', '/analytics/key-domains', 'M207', '市政事件 326件', 7),
('SAFETY', '公共安全态势', '/analytics/key-domains', 'M205', '应急事件 42起', 8)
ON DUPLICATE KEY UPDATE situation_name = VALUES(situation_name);

UPDATE sys_menu SET path = '/exchange/portal', component = 'exchange/PortalHubView', menu_name = '应用分析门户'
WHERE id = 7 OR path = '/exchange/analysis-portal';

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 36, 3, '门户首页', 2, '/exchange/portal', 'exchange/PortalHubView', NULL, NULL, 7, 'M032', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 36);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, 36 FROM (SELECT 1 AS id UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) r
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = 36);

INSERT INTO biz_catalog_item (catalog_code, title, description, publish_status, created_by) VALUES
('CAT_POP_001', '常住人口统计目录', '人口态势分析基础数据', 'PUBLISHED', 'sys_admin'),
('CAT_LEG_001', '法人主体登记目录', '法人态势与行业分布', 'PUBLISHED', 'sys_admin'),
('CAT_ECO_001', '宏观经济指标目录', 'GDP与产业增加值', 'PUBLISHED', 'sys_admin'),
('CAT_GOV_001', '政务服务事项目录', '社会治理与城市管理', 'PUBLISHED', 'sys_admin')
ON DUPLICATE KEY UPDATE title = VALUES(title);
