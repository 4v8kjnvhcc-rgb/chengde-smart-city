-- P1 加工共享黄金路径：
-- 源表 -> 治理ETL/融合 -> 主题库落表 -> 二次元数据 -> 二次质量 -> 融合资源编目 -> 订阅授权

-- 加工产出的主题库落地表（清洗去空 + 脱敏 + 派生资本等级）
CREATE TABLE IF NOT EXISTS dws_enterprise_theme (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  unified_credit_code_masked VARCHAR(64) NOT NULL,
  enterprise_name VARCHAR(256) NOT NULL,
  capital_level VARCHAR(16) NULL COMMENT 'HIGH/MEDIUM/LOW',
  industry_code VARCHAR(64) NULL,
  district_code VARCHAR(12) NULL,
  fused_at DATETIME NULL,
  UNIQUE KEY uk_dws_enterprise_credit (unified_credit_code_masked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P1加工共享企业主题库落地表';

-- 主题库登记（资源中心）
INSERT INTO rc_theme_library (theme_code, theme_name, partition_key, status, created_by)
VALUES ('THEME_ENTERPRISE', '企业主题库', 'district_code', 'ACTIVE', 'sys_admin')
ON DUPLICATE KEY UPDATE theme_name = VALUES(theme_name);

-- 融合逻辑实体 + 物理映射（挂在示范业务域 DOM_DEMO 下）
INSERT INTO gov_fusion_logic_entity (domain_id, entity_code, entity_name, description, status, created_by)
SELECT d.id, 'ENT_ENTERPRISE_THEME', '企业主题实体', 'P1加工共享融合产出实体', 'ACTIVE', 'sys_admin'
FROM gov_fusion_domain d
WHERE d.domain_code = 'DOM_DEMO'
ON DUPLICATE KEY UPDATE entity_name = VALUES(entity_name);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_ENTERPRISE_THEME', 'dws_enterprise_theme', NULL, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e
WHERE e.entity_code = 'ENT_ENTERPRISE_THEME'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name);
