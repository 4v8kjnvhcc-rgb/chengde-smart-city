-- M139~M145 通用支撑 + M146~M151 智能 BI widget 注册

CREATE TABLE IF NOT EXISTS ana_platform_app (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_code VARCHAR(64) NOT NULL,
  app_name VARCHAR(128) NOT NULL,
  app_type VARCHAR(32) NOT NULL DEFAULT 'WEB',
  endpoint_url VARCHAR(256) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  m_code VARCHAR(16) NOT NULL DEFAULT 'M140',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_code (app_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_platform_service (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  service_code VARCHAR(64) NOT NULL,
  service_name VARCHAR(128) NOT NULL,
  service_path VARCHAR(256) NOT NULL,
  protocol VARCHAR(16) NOT NULL DEFAULT 'REST',
  status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
  m_code VARCHAR(16) NOT NULL DEFAULT 'M142',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_service_code (service_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_platform_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(64) NOT NULL,
  config_value VARCHAR(512) NOT NULL,
  config_group VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
  description VARCHAR(256) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_platform_integration (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  integration_code VARCHAR(64) NOT NULL,
  integration_name VARCHAR(128) NOT NULL,
  target_system VARCHAR(64) NOT NULL,
  endpoint VARCHAR(256) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  last_message VARCHAR(512) NULL,
  m_code VARCHAR(16) NOT NULL DEFAULT 'M145',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_integration_code (integration_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_bi_widget (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  widget_code VARCHAR(64) NOT NULL,
  widget_name VARCHAR(128) NOT NULL,
  widget_type VARCHAR(32) NOT NULL,
  m_code VARCHAR(16) NOT NULL,
  de_dashboard_id VARCHAR(64) NOT NULL,
  description VARCHAR(256) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_widget_code (widget_code),
  KEY idx_m_code (m_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ana_platform_app (app_code, app_name, app_type, endpoint_url, m_code) VALUES
('APP_PORTAL', '数据门户', 'WEB', '/exchange/portal', 'M140'),
('APP_GOV', '治理工作台', 'WEB', '/governance', 'M140'),
('APP_BI', '智能BI工作台', 'WEB', '/analytics/bi', 'M140')
ON DUPLICATE KEY UPDATE app_name = VALUES(app_name);

INSERT INTO ana_platform_service (service_code, service_name, service_path, protocol, m_code) VALUES
('SVC_USER', '用户查询服务', '/api/v1/system/users', 'REST', 'M142'),
('SVC_CATALOG', '目录检索服务', '/api/v1/catalog/modules', 'REST', 'M142'),
('SVC_EMBED', 'BI嵌入令牌服务', '/api/v1/analytics/embed-token', 'REST', 'M142'),
('SVC_AUDIT', '审计日志服务', '/api/v1/system/audit', 'REST', 'M142')
ON DUPLICATE KEY UPDATE service_name = VALUES(service_name);

INSERT INTO ana_platform_config (config_key, config_value, config_group, description) VALUES
('sso.enabled', 'true', 'AUTH', 'M141 统一认证开关'),
('sso.provider', 'portal-jwt', 'AUTH', '门户 JWT SSO'),
('session.timeout.minutes', '30', 'SYSTEM', 'M143 会话超时'),
('audit.retention.days', '180', 'SYSTEM', 'M144 审计保留天数'),
('embed.token.ttl.minutes', '30', 'BI', 'M146 DataEase 嵌入令牌 TTL')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

INSERT INTO ana_platform_integration (integration_code, integration_name, target_system, endpoint, m_code) VALUES
('INT_DE', 'DataEase BI', 'DataEase', 'http://localhost:8100', 'M145'),
('INT_DS', 'DolphinScheduler', 'DolphinScheduler', 'http://localhost:12345/dolphinscheduler', 'M145'),
('INT_OM', 'OpenMetadata', 'OpenMetadata', 'http://localhost:8585', 'M145')
ON DUPLICATE KEY UPDATE integration_name = VALUES(integration_name);

INSERT INTO ana_bi_widget (widget_code, widget_name, widget_type, m_code, de_dashboard_id, description, sort_order) VALUES
('W_DISPLAY', '显示引擎', 'DISPLAY', 'M146', 'de-dash-overview', '大屏显示引擎', 1),
('W_COMPONENT', '组件引擎', 'COMPONENT', 'M147', 'de-widget-lib', '可视化组件库', 2),
('W_MAP', '地图管理', 'MAP', 'M148', 'de-map-gis', 'GIS 地图图层', 3),
('W_DATASOURCE', '数据源管理', 'DATASOURCE', 'M149', 'de-ds-manager', 'BI 数据源连接', 4),
('W_DESIGN', '可视化设计', 'DESIGN', 'M150', 'de-dash-alert', '专题大屏设计', 5),
('W_SELF', '自助分析', 'SELF_SERVICE', 'M151', 'de-self-analysis', '查询报表自助分析', 6)
ON DUPLICATE KEY UPDATE widget_name = VALUES(widget_name);

UPDATE sys_menu SET menu_name = '通用支撑平台', component = 'analytics/AnalyticsSupportHubView'
WHERE id = 13 OR path = '/analytics/support';

UPDATE sys_menu SET menu_name = '智能BI平台', component = 'analytics/AnalyticsBiHubView'
WHERE id = 14 OR path = '/analytics/bi';
