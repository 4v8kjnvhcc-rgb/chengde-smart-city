-- V196: 分析域「指标域」台账（共享服务区·指标库）
CREATE TABLE IF NOT EXISTS ana_indicator_domain (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_domain_code VARCHAR(32) NOT NULL COMMENT '所属业务域 population|legal|macro|key',
  domain_name VARCHAR(128) NOT NULL COMMENT '指标域名称',
  domain_db_name VARCHAR(128) NOT NULL COMMENT '指标域库名，ind_ 开头',
  remark VARCHAR(512) NULL COMMENT '备注',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  UNIQUE KEY uk_ana_ind_domain_db (domain_db_name),
  KEY idx_ana_ind_domain_owner (owner_domain_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ana_indicator_domain (owner_domain_code, domain_name, domain_db_name, remark, status, created_by)
SELECT 'population', '人口大数据支撑系统', 'ind_population_big_data_support_system', '新建', 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_indicator_domain WHERE domain_db_name = 'ind_population_big_data_support_system'
);

INSERT INTO ana_indicator_domain (owner_domain_code, domain_name, domain_db_name, remark, status, created_by)
SELECT 'population', '基础库统计分析', 'ind_basic_database', NULL, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_indicator_domain WHERE domain_db_name = 'ind_basic_database'
);
