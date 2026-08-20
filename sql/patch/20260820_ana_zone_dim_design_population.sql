-- 人口大数据支撑系统：治理/核心/内部区七维设计种子 + 内部区分级分类与双重授权示例（幂等）
-- 能力同构法人 R114～R116，数据与文案均为人口域，禁止法人资产/分类码

-- ========== 治理及反馈区 ==========
INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'POSITION', 'PROBLEM_DATA', '问题数据存放', 'PROBLEM_DATA',
       '存放治理过程发现的人口问题数据，供数据质量分析以及数据问题反馈使用',
       '/quality?qSub=monitor', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='POSITION' AND item_code='PROBLEM_DATA');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'POSITION', 'SEMI', '半结构化数据存放', 'SEMI',
       '存放半结构化人口相关数据，服务后续治理与质量分析',
       '/exchange/ingestion?system=collect&module=ingest.semi', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='POSITION' AND item_code='SEMI');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'POSITION', 'UNSTRUCT_TO_STRUCT', '非结构转结构化结果', 'UNSTRUCT_TO_STRUCT',
       '存放人口非结构化数据（如电子证照）转结构化后的结果数据',
       '/unstructured?tab=process.clean', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='POSITION' AND item_code='UNSTRUCT_TO_STRUCT');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'POSITION', 'DIRTY_FULL', '周期全量脏数据', 'DIRTY_FULL',
       '提供周期全量人口脏数据，供数据质量分析以及数据问题反馈使用',
       '/quality?qSub=assess', 40, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='POSITION' AND item_code='DIRTY_FULL');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'MODEL', 'SOURCE_ALIGN', '贴源结构模型', 'SOURCE_ALIGN',
       '人口治理区数据模型采取贴源结构', NULL, 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='MODEL' AND item_code='SOURCE_ALIGN');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'MODEL', 'NORMALIZED', '规范化模型设计', 'NORMALIZED',
       '人口治理区数据模型采用模型规范化设计',
       '/governance?tab=model&mSub=clean', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='MODEL' AND item_code='NORMALIZED');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'MODEL', 'INCREMENTAL_SLICE', '流水周期增量切片', 'INCREMENTAL_SLICE',
       '流水类人口行为/变更表采用周期增量切片表存储',
       '/system/uum?tab=exec-cycle', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='MODEL' AND item_code='INCREMENTAL_SLICE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'PROCESS', 'TECH_LEGAL_CLEAN', '技术性与合法性检核清洗', 'TECH_LEGAL_CLEAN',
       '完成人口数据的技术性以及合法性检核清洗',
       '/governance?tab=etl&etlSub=task-mgmt', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='PROCESS' AND item_code='TECH_LEGAL_CLEAN');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'PROCESS', 'POINT_IN_TIME', '历史时点变更事实', 'POINT_IN_TIME',
       '以数据时点的方式记录人口信息历史变更事实（户籍、婚姻、住址等）', NULL, 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='PROCESS' AND item_code='POINT_IN_TIME');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'PROCESS', 'SEMI_UNSTRUCT_PREP', '半/非结构预处理与转结构化', 'SEMI_UNSTRUCT_PREP',
       '提供人口半结构化、非结构化数据转结构化及预处理等功能',
       '/unstructured?tab=process.clean', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='PROCESS' AND item_code='SEMI_UNSTRUCT_PREP');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'RETENTION', 'LONG_TERM', '长期存储策略', 'LONG_TERM',
       '人口治理过程数据可长期存储；具体周期随存储空间容量扩大而延长',
       '/resource-center?tab=storage', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='RETENTION' AND item_code='LONG_TERM');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'SOURCE', 'COLLECT_ZONE', '采集区数据', 'COLLECT_ZONE',
       '数据来源：人口数据采集区',
       '/analytics/population?tab=zone.collect', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='SOURCE' AND item_code='COLLECT_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'SOURCE', 'EXTERNAL_ZONE', '外部数据区数据', 'EXTERNAL_ZONE',
       '数据来源：外部数据区人口相关数据', NULL, 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='SOURCE' AND item_code='EXTERNAL_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'CONSUMER', 'CORE_ZONE', '数据核心区使用者', 'CORE_ZONE',
       '数据使用者：人口核心数据区（须经治理分流，禁止旁路权威）',
       '/analytics/population?tab=zone.core', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='CONSUMER' AND item_code='CORE_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'CONSUMER', 'SERVICE_ZONE', '服务区使用者', 'SERVICE_ZONE',
       '数据使用者：人口内部服务区/共享服务区',
       '/analytics/population?tab=zone.share', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='CONSUMER' AND item_code='SERVICE_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, config_json, deep_link, sort_no, status, created_by)
SELECT 'population', 'govern', 'FREQUENCY', 'MONTHLY_DEFAULT', '默认月度更新', 'MONTHLY',
       '一般的更新频率为每月更新一次；具体更新频度视实际情况可按一天、一周、一个季度、半年或者一年更新一次',
       '{"defaultFreq":"MONTHLY","allowed":["DAILY","WEEKLY","MONTHLY","QUARTERLY","HALF_YEAR","YEARLY"]}',
       '/system/uum?tab=exec-cycle', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='govern' AND dim_code='FREQUENCY' AND item_code='MONTHLY_DEFAULT');

-- ========== 核心数据区 ==========
INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'POSITION', 'AUTHORITY_BASE', '人口基础/主题权威区', 'AUTHORITY_BASE',
       '统一标准的人口基础/主题权威区；「一数一源」；按业务分类存放',
       '/resource-center?tab=asset&assetTab=libraries', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='POSITION' AND item_code='AUTHORITY_BASE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'POSITION', 'ONE_SOURCE', '一数一源', 'ONE_SOURCE',
       '核心区保证人口权威数据「一数一源」，避免多权威冲突',
       NULL, 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='POSITION' AND item_code='ONE_SOURCE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'POSITION', 'BIZ_CLASSIFY', '按业务分类存放', 'BIZ_CLASSIFY',
       '按业务分类存放人口基础库与主题库成果（户籍、常住、教育、婚姻等）',
       '/resource-center?tab=asset&assetTab=classify', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='POSITION' AND item_code='BIZ_CLASSIFY');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'MODEL', 'WIDE_TABLE', '宽表模型', 'WIDE_TABLE',
       '数据模型采用宽表设计，便于人口主题分析与权威供数',
       '/governance?tab=model&mSub=clean', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='MODEL' AND item_code='WIDE_TABLE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'MODEL', 'MULTIDIM', '多维模型', 'MULTIDIM',
       '数据模型支持多维组织，允许合理冗余',
       NULL, 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='MODEL' AND item_code='MULTIDIM');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'MODEL', 'SPECIALTY_EXT', '专业库逻辑扩展', 'SPECIALTY_EXT',
       '可在人口基础/主题上扩展专业库（逻辑分层，落 DWS/ADS）',
       '/resource-center?tab=asset&assetTab=modules', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='MODEL' AND item_code='SPECIALTY_EXT');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'PROCESS', 'ENTITY_MERGE', '多源合并同一人员实体', 'ENTITY_MERGE',
       '多源合并到同一人员实体，形成人口权威主数据',
       '/governance?tab=model&mSub=clean', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='PROCESS' AND item_code='ENTITY_MERGE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'PROCESS', 'CROSS_CALC', '跨业务计算', 'CROSS_CALC',
       '支持跨业务计算与人口主题指标加工',
       '/governance?tab=etl&etlSub=task-mgmt', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='PROCESS' AND item_code='CROSS_CALC');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'PROCESS', 'SERVICE_FEATURE', '提取服务区特征', 'SERVICE_FEATURE',
       '从人口核心权威数据提取服务区所需特征与专题结果',
       '/analytics/population?tab=zone.share', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='PROCESS' AND item_code='SERVICE_FEATURE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'RETENTION', 'LONG_TERM_PARTITION', '长期存储与分区', 'LONG_TERM_PARTITION',
       '长期可存；支持垂直分片/水平分区；历史与审计近线',
       '/resource-center?tab=partition', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='RETENTION' AND item_code='LONG_TERM_PARTITION');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'SOURCE', 'FROM_GOVERN', '治理及反馈区', 'FROM_GOVERN',
       '数据来源：人口治理及反馈区（经融合落入 DWS）',
       '/analytics/population?tab=zone.govern', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='SOURCE' AND item_code='FROM_GOVERN');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'CONSUMER', 'INTERNAL_ZONE', '内部服务区使用者', 'INTERNAL_ZONE',
       '数据使用者：人口内部服务区',
       '/analytics/population?tab=zone.internal', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='CONSUMER' AND item_code='INTERNAL_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'CONSUMER', 'SHARE_ZONE', '共享服务区使用者', 'SHARE_ZONE',
       '数据使用者：人口共享服务区（目录/接口/指标模型）',
       '/analytics/population?tab=zone.share', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='CONSUMER' AND item_code='SHARE_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, config_json, deep_link, sort_no, status, created_by)
SELECT 'population', 'core', 'FREQUENCY', 'MONTHLY_DEFAULT', '默认月度更新', 'MONTHLY',
       '默认每月更新一次；可按业务配置日/周/季/半年/年',
       '{"defaultFreq":"MONTHLY","allowed":["DAILY","WEEKLY","MONTHLY","QUARTERLY","HALF_YEAR","YEARLY"]}',
       '/system/uum?tab=exec-cycle', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='core' AND dim_code='FREQUENCY' AND item_code='MONTHLY_DEFAULT');

-- ========== 内部服务区 · 七维规范 ==========
INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'internal', 'POSITION', 'HIGH_SENSITIVE_ZONE', '高敏独立服务边界', 'HIGH_SENSITIVE_BOUNDARY',
       '针对人口基础数据等权限较高、敏感等级较高的应用场景，设计独立内部服务区域',
       NULL, 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='internal' AND dim_code='POSITION' AND item_code='HIGH_SENSITIVE_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'internal', 'MODEL', 'CONSUME_CORE', '消费核心区权威数据', 'CONSUME_CORE',
       '消费人口核心区权威结构化数据；不另建第二权威源',
       NULL, 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='internal' AND dim_code='MODEL' AND item_code='CONSUME_CORE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'internal', 'PROCESS', 'CLASSIFY_DUAL_AUTH', '分级分类与双重授权', 'CLASSIFY_DUAL_AUTH',
       '制定人口数据分级分类管理规范；数据访问须双重授权，系统管理员不可直接授跨部门数据访问权',
       '/exchange/ingestion?system=register&module=m048', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='internal' AND dim_code='PROCESS' AND item_code='CLASSIFY_DUAL_AUTH');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'internal', 'SOURCE', 'FROM_CORE', '来源核心区', 'FROM_CORE',
       '数据来源于人口核心数据区经挂载的权威资源',
       NULL, 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='internal' AND dim_code='SOURCE' AND item_code='FROM_CORE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'population', 'internal', 'CONSUMER', 'DEPT_HIGH_SENS', '部门内高敏应用', 'DEPT_HIGH_SENS',
       '部门内高敏人口业务应用与管理员；须完成双重授权后方可访问',
       NULL, 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='population' AND zone_code='internal' AND dim_code='CONSUMER' AND item_code='DEPT_HIGH_SENS');

-- ========== 内部区 · 分级分类示例（人口资产） ==========
INSERT INTO ana_zone_internal_classify
  (domain_code, zone_code, asset_code, asset_name, category_code, category_name, level_code, level_name, classify_basis, control_hint, sort_no, status, created_by)
SELECT 'population', 'internal', 'dws_population_base', '人口基础信息宽表', 'POP_BASE', '人口基础数据',
       'CORE', '核心数据', '含身份证号、姓名、户籍住址等自然人标识类字段',
       '仅内部服务区挂载；访问须双重授权；禁止系统管理员直接跨部门授数权', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_internal_classify WHERE domain_code='population' AND zone_code='internal' AND asset_code='dws_population_base');

INSERT INTO ana_zone_internal_classify
  (domain_code, zone_code, asset_code, asset_name, category_code, category_name, level_code, level_name, classify_basis, control_hint, sort_no, status, created_by)
SELECT 'population', 'internal', 'dws_population_identity', '人口身份与证件主题', 'IDENTITY', '身份证件信息',
       'SENSITIVE', '敏感数据', '含证件类型、证件号、证照影像元数据等可识别字段',
       '脱敏只读为默认授权范围；导出须单独审批', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_internal_classify WHERE domain_code='population' AND zone_code='internal' AND asset_code='dws_population_identity');

INSERT INTO ana_zone_internal_classify
  (domain_code, zone_code, asset_code, asset_name, category_code, category_name, level_code, level_name, classify_basis, control_hint, sort_no, status, created_by)
SELECT 'population', 'internal', 'dws_population_biz_stat', '人口业务统计主题', 'POP_BIZ', '人口业务主题',
       'IMPORTANT', '重要数据', '聚合统计类（年龄结构、户籍/常住等），不含直接证件号',
       '部门管理员可审定数据访问；建议 READ', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_internal_classify WHERE domain_code='population' AND zone_code='internal' AND asset_code='dws_population_biz_stat');

-- ========== 内部区 · 双重授权示例 ==========
INSERT INTO ana_zone_internal_grant
  (domain_code, zone_code, grant_type, grantee_type, grantee_code, grantee_name, asset_code, asset_name,
   level_code, auth_mode, permission_scope, reason, status, applicant, created_by)
SELECT 'population', 'internal', 'ROLE_GRANT', 'ROLE', 'DEPT_ADMIN', '部门管理员',
       NULL, NULL, NULL, 'SINGLE', NULL,
       '系统管理员可授予部门管理员角色，由其承接本部门人口数据访问审批',
       'ACTIVE', 'sys_admin', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_zone_internal_grant
  WHERE domain_code='population' AND zone_code='internal' AND grant_type='ROLE_GRANT'
    AND grantee_type='ROLE' AND grantee_code='DEPT_ADMIN' AND (asset_code IS NULL OR asset_code='')
);

INSERT INTO ana_zone_internal_grant
  (domain_code, zone_code, grant_type, grantee_type, grantee_code, grantee_name, asset_code, asset_name,
   level_code, auth_mode, permission_scope, reason, status, applicant, created_by)
SELECT 'population', 'internal', 'DATA_ACCESS', 'ORG', 'ORG_DEMO_POP', '示例人口业务处室',
       'dws_population_base', '人口基础信息宽表',
       'CORE', 'DUAL', 'MASKED_READ',
       '跨部门申请访问人口基础宽表；系统管理员创建后须部门管理员审批',
       'PENDING', 'pop_biz_user', 'pop_biz_user'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_zone_internal_grant
  WHERE domain_code='population' AND zone_code='internal' AND grant_type='DATA_ACCESS'
    AND grantee_code='ORG_DEMO_POP' AND asset_code='dws_population_base'
);
