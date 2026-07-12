-- MS6~MS7 POC: DataEase models + BI embed + DolphinScheduler workflows

CREATE TABLE IF NOT EXISTS ana_analysis_model (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_code VARCHAR(64) NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  domain_code VARCHAR(32) NOT NULL,
  m_code VARCHAR(16) NULL,
  de_dashboard_id VARCHAR(64) NULL,
  sample_row_count INT NOT NULL DEFAULT 100,
  status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_model_code (model_code),
  KEY idx_domain (domain_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_model_sample (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_id BIGINT NOT NULL,
  row_no INT NOT NULL,
  dim1 VARCHAR(64) NULL,
  dim2 VARCHAR(64) NULL,
  metric1 DECIMAL(18,2) NULL,
  metric2 DECIMAL(18,2) NULL,
  KEY idx_model_row (model_id, row_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_bi_dashboard (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dash_code VARCHAR(64) NOT NULL,
  dash_name VARCHAR(128) NOT NULL,
  de_dashboard_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dash_code (dash_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_ds_workflow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  workflow_code VARCHAR(64) NOT NULL,
  workflow_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE',
  last_run_at DATETIME NULL,
  last_message VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_wf_code (workflow_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_embed_token (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  token VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ana_bi_dashboard (dash_code, dash_name, de_dashboard_id) VALUES
('BI_OVERVIEW', '智能BI总览大屏', 'de-dash-overview'),
('BI_ALERT', '运行监测大屏', 'de-dash-alert')
ON DUPLICATE KEY UPDATE dash_name = VALUES(dash_name);

INSERT INTO ana_ds_workflow (workflow_code, workflow_name, status) VALUES
('DS_WF_DAILY_ETL', '日批治理联动工作流', 'ONLINE'),
('DS_WF_QUALITY', '质量任务调度工作流', 'ONLINE')
ON DUPLICATE KEY UPDATE workflow_name = VALUES(workflow_name);

-- 40 analysis models across domains (5 L2 reps + domain coverage)
INSERT INTO ana_analysis_model (model_code, model_name, domain_code, m_code, de_dashboard_id, sample_row_count) VALUES
('POP_001','人口总量趋势','population','M152','de-pop-001',100),
('POP_002','年龄结构分布','population','M153','de-pop-002',100),
('POP_003','户籍流动分析','population','M154','de-pop-003',100),
('POP_004','就业结构画像','population','M155','de-pop-004',100),
('POP_005','教育程度分布','population','M156','de-pop-005',100),
('POP_006','人口预测模型','population','M157','de-pop-006',100),
('POP_007','区域人口密度','population','M158','de-pop-007',100),
('POP_008','流动人口画像','population','M159','de-pop-008',100),
('LEG_001','企业存量趋势','legal','M175','de-leg-001',100),
('LEG_002','行业结构分布','legal','M176','de-leg-002',100),
('LEG_003','新设企业监测','legal','M177','de-leg-003',100),
('LEG_004','注销企业分析','legal','M178','de-leg-004',100),
('LEG_005','注册资本分层','legal','M179','de-leg-005',100),
('LEG_006','园区企业画像','legal','M180','de-leg-006',100),
('LEG_007','信用风险监测','legal','M181','de-leg-007',100),
('LEG_008','招商引资看板','legal','M182','de-leg-008',100),
('MAC_001','GDP增速监测','macro','M193','de-mac-001',100),
('MAC_002','固定资产投资','macro','M194','de-mac-002',100),
('MAC_003','财政收支分析','macro','M195','de-mac-003',100),
('MAC_004','消费价格指数','macro','M196','de-mac-004',100),
('MAC_005','工业增加值','macro','M197','de-mac-005',100),
('MAC_006','进出口总额','macro','M198','de-mac-006',100),
('MAC_007','社消零总额','macro','M199','de-mac-007',100),
('MAC_008','用电量景气','macro','M200','de-mac-008',100),
('KEY_001','生态环境监测','key','M204','de-key-001',100),
('KEY_002','交通运输运行','key','M205','de-key-002',100),
('KEY_003','文旅客流分析','key','M206','de-key-003',100),
('KEY_004','教育资源配置','key','M207','de-key-004',100),
('KEY_005','医疗卫生资源','key','M208','de-key-005',100),
('KEY_006','应急事件统计','key','M209','de-key-006',100),
('BI_001','综合驾驶舱','bi','M146','de-bi-001',100),
('BI_002','专题对比分析','bi','M147','de-bi-002',100),
('BI_003','指标预警大屏','bi','M148','de-bi-003',100),
('BI_004','自助取数分析','bi','M149','de-bi-004',100),
('BI_005','报表订阅中心','bi','M150','de-bi-005',100),
('BI_006','多维下钻分析','bi','M151','de-bi-006',100),
('SUP_001','指标体系管理','support','M139','de-sup-001',100),
('SUP_002','标签体系管理','support','M140','de-sup-002',100),
('SUP_003','分析任务编排','support','M141','de-sup-003',100),
('SUP_004','结果发布管理','support','M142','de-sup-004',100)
ON DUPLICATE KEY UPDATE model_name = VALUES(model_name);

UPDATE sys_menu SET integration_type = 'self', component = 'analytics/AnalyticsSupportView' WHERE id = 13;
UPDATE sys_menu SET integration_type = 'self', component = 'analytics/AnalyticsDomainView' WHERE id IN (14,15,16,17,18);
UPDATE sys_menu SET integration_type = 'self', component = 'integration/SchedulerView' WHERE id = 30;
