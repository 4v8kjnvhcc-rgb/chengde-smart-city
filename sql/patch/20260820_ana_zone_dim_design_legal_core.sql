-- 法人 · 核心数据区 · 七维设计种子（幂等）
INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'POSITION', 'AUTHORITY_BASE', '法人基础/主题权威区', 'AUTHORITY_BASE',
       '统一标准的法人基础/主题权威区；「一数一源」；按业务分类存放',
       '/resource-center?tab=asset&assetTab=libraries', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='POSITION' AND item_code='AUTHORITY_BASE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'POSITION', 'ONE_SOURCE', '一数一源', 'ONE_SOURCE',
       '核心区保证法人权威数据「一数一源」，避免多权威冲突',
       NULL, 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='POSITION' AND item_code='ONE_SOURCE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'POSITION', 'BIZ_CLASSIFY', '按业务分类存放', 'BIZ_CLASSIFY',
       '按业务分类存放法人基础库与主题库成果',
       '/resource-center?tab=asset&assetTab=classify', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='POSITION' AND item_code='BIZ_CLASSIFY');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'MODEL', 'WIDE_TABLE', '宽表模型', 'WIDE_TABLE',
       '数据模型采用宽表设计，便于主题分析与权威供数',
       '/governance?tab=model&mSub=clean', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='MODEL' AND item_code='WIDE_TABLE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'MODEL', 'MULTIDIM', '多维模型', 'MULTIDIM',
       '数据模型支持多维组织，允许合理冗余',
       NULL, 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='MODEL' AND item_code='MULTIDIM');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'MODEL', 'SPECIALTY_EXT', '专业库逻辑扩展', 'SPECIALTY_EXT',
       '可在基础/主题上扩展专业库（逻辑分层，落 DWS/ADS）',
       '/resource-center?tab=asset&assetTab=modules', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='MODEL' AND item_code='SPECIALTY_EXT');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'PROCESS', 'ENTITY_MERGE', '多源合并同一法人实体', 'ENTITY_MERGE',
       '多源合并到同一法人实体，形成权威主数据',
       '/governance?tab=model&mSub=clean', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='PROCESS' AND item_code='ENTITY_MERGE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'PROCESS', 'CROSS_CALC', '跨业务计算', 'CROSS_CALC',
       '支持跨业务计算与主题指标加工',
       '/governance?tab=etl&etlSub=task-mgmt', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='PROCESS' AND item_code='CROSS_CALC');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'PROCESS', 'SERVICE_FEATURE', '提取服务区特征', 'SERVICE_FEATURE',
       '从核心权威数据提取服务区所需特征与专题结果',
       '/analytics/legal-entity?tab=zone.share', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='PROCESS' AND item_code='SERVICE_FEATURE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'RETENTION', 'LONG_TERM_PARTITION', '长期存储与分区', 'LONG_TERM_PARTITION',
       '长期可存；支持垂直分片/水平分区；历史与审计近线',
       '/resource-center?tab=partition', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='RETENTION' AND item_code='LONG_TERM_PARTITION');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'SOURCE', 'FROM_GOVERN', '治理及反馈区', 'FROM_GOVERN',
       '数据来源：治理及反馈区（经融合落入 DWS）',
       '/analytics/legal-entity?tab=zone.govern', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='SOURCE' AND item_code='FROM_GOVERN');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'CONSUMER', 'INTERNAL_ZONE', '内部服务区使用者', 'INTERNAL_ZONE',
       '数据使用者：法人内部服务区',
       '/analytics/legal-entity?tab=zone.internal', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='CONSUMER' AND item_code='INTERNAL_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'CONSUMER', 'SHARE_ZONE', '共享服务区使用者', 'SHARE_ZONE',
       '数据使用者：法人共享服务区（目录/接口/指标模型）',
       '/analytics/legal-entity?tab=zone.share', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='CONSUMER' AND item_code='SHARE_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, config_json, deep_link, sort_no, status, created_by)
SELECT 'legal', 'core', 'FREQUENCY', 'MONTHLY_DEFAULT', '默认月度更新', 'MONTHLY',
       '默认每月更新一次；可按业务配置日/周/季/半年/年',
       '{"defaultFreq":"MONTHLY","allowed":["DAILY","WEEKLY","MONTHLY","QUARTERLY","HALF_YEAR","YEARLY"]}',
       '/system/uum?tab=exec-cycle', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='core' AND dim_code='FREQUENCY' AND item_code='MONTHLY_DEFAULT');
