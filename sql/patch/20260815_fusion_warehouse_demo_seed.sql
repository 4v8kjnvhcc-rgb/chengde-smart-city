-- 数据仓库建设（融合模型）演示种子数据
-- 目标库：smart_city（元模型）+ smart_city_dws（物理表样例）
-- 可重复执行：业务域/实体/关系/物理映射按编码幂等；字段按编码幂等
-- 执行：mysql -h 10.10.10.56 -P 13306 -u root -p smart_city < 本文件

SET NAMES utf8mb4;

-- ========== 1. 业务域 ==========
INSERT INTO gov_fusion_domain (domain_code, domain_name, description, status, created_by)
VALUES
  ('DOM_DEMO', '示范业务域', '融合模型示范：企业与产业主题', 'ACTIVE', 'sys_admin'),
  ('POP', '人口业务域', '承德市人口基础、户籍与流动人口主题', 'ACTIVE', 'sys_admin'),
  ('DOM_LEGAL', '法人业务域', '法人主体、证照许可与监管主题', 'ACTIVE', 'sys_admin'),
  ('DOM_TRAFFIC', '交通出行业务域', '路网流量、停车与公共交通主题', 'ACTIVE', 'sys_admin'),
  ('DOM_ENV', '生态环境业务域', '空气质量、水质与环境监测主题', 'ACTIVE', 'sys_admin'),
  ('DOM_TOURISM', '文旅产业业务域', '避暑山庄及周边景区客流、住宿接待主题', 'ACTIVE', 'sys_admin'),
  ('DOM_GOVSVC', '政务服务业务域', '一网通办办件与12345诉求主题', 'ACTIVE', 'sys_admin')
ON DUPLICATE KEY UPDATE
  domain_name = VALUES(domain_name),
  description = VALUES(description),
  status = VALUES(status);

-- ========== 2. 逻辑实体 ==========
-- 示范业务域
INSERT INTO gov_fusion_logic_entity (domain_id, entity_code, entity_name, description, status, created_by)
SELECT d.id, v.entity_code, v.entity_name, v.description, 'ACTIVE', 'sys_admin'
FROM gov_fusion_domain d
JOIN (
  SELECT 'ENT_ENTERPRISE_THEME' AS entity_code, '企业主题实体' AS entity_name, '企业主体融合宽表（信用代码脱敏）' AS description
  UNION ALL SELECT 'ENT_EMPLOYEE_THEME', '从业人员主题实体', '企业从业人员主题（与企业一对多）'
  UNION ALL SELECT 'ENT_INDUSTRY_AGG', '产业集聚主题实体', '按区县/行业汇总的产业集聚指标'
  UNION ALL SELECT 'ENT_CREDIT_PROFILE', '企业信用画像实体', '企业信用等级与风险标签'
) v
WHERE d.domain_code = 'DOM_DEMO'
ON DUPLICATE KEY UPDATE
  entity_name = VALUES(entity_name),
  description = VALUES(description),
  status = VALUES(status);

-- 兼容早期大小写不一致的员工实体编码
UPDATE gov_fusion_logic_entity e
JOIN gov_fusion_domain d ON d.id = e.domain_id AND d.domain_code = 'DOM_DEMO'
SET e.entity_code = 'ENT_EMPLOYEE_THEME',
    e.entity_name = '从业人员主题实体',
    e.description = '企业从业人员主题（与企业一对多）'
WHERE e.entity_code = 'ENT_Employee_Theme';

-- 人口业务域
INSERT INTO gov_fusion_logic_entity (domain_id, entity_code, entity_name, description, status, created_by)
SELECT d.id, v.entity_code, v.entity_name, v.description, 'ACTIVE', 'sys_admin'
FROM gov_fusion_domain d
JOIN (
  SELECT 'ENT_POP_BASE' AS entity_code, '人口基础主题实体' AS entity_name, '常住/户籍人口基础宽表' AS description
  UNION ALL SELECT 'ENT_HOUSEHOLD', '户籍家庭主题实体', '户籍户主与家庭成员关系'
  UNION ALL SELECT 'ENT_MIGRANT', '流动人口主题实体', '跨区流入/流出人口主题'
  UNION ALL SELECT 'ENT_POP_DISTRICT_SUM', '区县人口汇总实体', '按区县月度人口汇总（ADS）'
) v
WHERE d.domain_code = 'POP'
ON DUPLICATE KEY UPDATE
  entity_name = VALUES(entity_name),
  description = VALUES(description),
  status = VALUES(status);

-- 法人业务域
INSERT INTO gov_fusion_logic_entity (domain_id, entity_code, entity_name, description, status, created_by)
SELECT d.id, v.entity_code, v.entity_name, v.description, 'ACTIVE', 'sys_admin'
FROM gov_fusion_domain d
JOIN (
  SELECT 'ENT_LEGAL_SUBJECT' AS entity_code, '法人主体主题实体' AS entity_name, '市场主体登记融合主题' AS description
  UNION ALL SELECT 'ENT_PERMIT', '行政许可主题实体', '证照许可与有效期主题'
) v
WHERE d.domain_code = 'DOM_LEGAL'
ON DUPLICATE KEY UPDATE
  entity_name = VALUES(entity_name),
  description = VALUES(description),
  status = VALUES(status);

-- 交通出行
INSERT INTO gov_fusion_logic_entity (domain_id, entity_code, entity_name, description, status, created_by)
SELECT d.id, v.entity_code, v.entity_name, v.description, 'ACTIVE', 'sys_admin'
FROM gov_fusion_domain d
JOIN (
  SELECT 'ENT_ROAD_FLOW' AS entity_code, '路网流量主题实体' AS entity_name, '主要道路断面流量日汇总' AS description
  UNION ALL SELECT 'ENT_PARKING', '停车资源主题实体', '公共停车场泊位与利用率'
) v
WHERE d.domain_code = 'DOM_TRAFFIC'
ON DUPLICATE KEY UPDATE
  entity_name = VALUES(entity_name),
  description = VALUES(description),
  status = VALUES(status);

-- 生态环境
INSERT INTO gov_fusion_logic_entity (domain_id, entity_code, entity_name, description, status, created_by)
SELECT d.id, v.entity_code, v.entity_name, v.description, 'ACTIVE', 'sys_admin'
FROM gov_fusion_domain d
JOIN (
  SELECT 'ENT_AIR_QUALITY' AS entity_code, '空气质量主题实体' AS entity_name, '国控/市控站点空气质量日值' AS description
  UNION ALL SELECT 'ENT_WATER_MONITOR', '水质监测主题实体', '主要水体断面水质监测'
) v
WHERE d.domain_code = 'DOM_ENV'
ON DUPLICATE KEY UPDATE
  entity_name = VALUES(entity_name),
  description = VALUES(description),
  status = VALUES(status);

-- 文旅产业
INSERT INTO gov_fusion_logic_entity (domain_id, entity_code, entity_name, description, status, created_by)
SELECT d.id, v.entity_code, v.entity_name, v.description, 'ACTIVE', 'sys_admin'
FROM gov_fusion_domain d
JOIN (
  SELECT 'ENT_SCENIC_VISITOR' AS entity_code, '景区客流主题实体' AS entity_name, '避暑山庄等重点景区日客流' AS description
  UNION ALL SELECT 'ENT_HOTEL_OCCUPANCY', '住宿接待主题实体', '星级及以上酒店入住率'
) v
WHERE d.domain_code = 'DOM_TOURISM'
ON DUPLICATE KEY UPDATE
  entity_name = VALUES(entity_name),
  description = VALUES(description),
  status = VALUES(status);

-- 政务服务
INSERT INTO gov_fusion_logic_entity (domain_id, entity_code, entity_name, description, status, created_by)
SELECT d.id, v.entity_code, v.entity_name, v.description, 'ACTIVE', 'sys_admin'
FROM gov_fusion_domain d
JOIN (
  SELECT 'ENT_ONLINE_SERVICE' AS entity_code, '一网通办主题实体' AS entity_name, '线上办件量与办结时效' AS description
  UNION ALL SELECT 'ENT_HOTLINE', '12345诉求主题实体', '热线工单受理与办结主题'
) v
WHERE d.domain_code = 'DOM_GOVSVC'
ON DUPLICATE KEY UPDATE
  entity_name = VALUES(entity_name),
  description = VALUES(description),
  status = VALUES(status);

-- ========== 3. 实体关系 ==========
INSERT INTO gov_fusion_relation (domain_id, relation_code, relation_name, from_entity_id, to_entity_id, relation_type, description)
SELECT d.id, 'REL_ENT_EMP', '企业-从业人员', e1.id, e2.id, 'ONE_TO_MANY', '一家企业对应多名从业人员'
FROM gov_fusion_domain d
JOIN gov_fusion_logic_entity e1 ON e1.domain_id = d.id AND e1.entity_code = 'ENT_ENTERPRISE_THEME'
JOIN gov_fusion_logic_entity e2 ON e2.domain_id = d.id AND e2.entity_code = 'ENT_EMPLOYEE_THEME'
WHERE d.domain_code = 'DOM_DEMO'
ON DUPLICATE KEY UPDATE
  relation_name = VALUES(relation_name),
  from_entity_id = VALUES(from_entity_id),
  to_entity_id = VALUES(to_entity_id),
  relation_type = VALUES(relation_type),
  description = VALUES(description);

INSERT INTO gov_fusion_relation (domain_id, relation_code, relation_name, from_entity_id, to_entity_id, relation_type, description)
SELECT d.id, 'REL_ENT_CREDIT', '企业-信用画像', e1.id, e2.id, 'ONE_TO_ONE', '企业主体与信用画像一对一'
FROM gov_fusion_domain d
JOIN gov_fusion_logic_entity e1 ON e1.domain_id = d.id AND e1.entity_code = 'ENT_ENTERPRISE_THEME'
JOIN gov_fusion_logic_entity e2 ON e2.domain_id = d.id AND e2.entity_code = 'ENT_CREDIT_PROFILE'
WHERE d.domain_code = 'DOM_DEMO'
ON DUPLICATE KEY UPDATE
  relation_name = VALUES(relation_name),
  from_entity_id = VALUES(from_entity_id),
  to_entity_id = VALUES(to_entity_id),
  relation_type = VALUES(relation_type);

INSERT INTO gov_fusion_relation (domain_id, relation_code, relation_name, from_entity_id, to_entity_id, relation_type, description)
SELECT d.id, 'REL_HH_POP', '户籍家庭-人口', e1.id, e2.id, 'ONE_TO_MANY', '一户对应多名家庭成员'
FROM gov_fusion_domain d
JOIN gov_fusion_logic_entity e1 ON e1.domain_id = d.id AND e1.entity_code = 'ENT_HOUSEHOLD'
JOIN gov_fusion_logic_entity e2 ON e2.domain_id = d.id AND e2.entity_code = 'ENT_POP_BASE'
WHERE d.domain_code = 'POP'
ON DUPLICATE KEY UPDATE
  relation_name = VALUES(relation_name),
  from_entity_id = VALUES(from_entity_id),
  to_entity_id = VALUES(to_entity_id),
  relation_type = VALUES(relation_type);

INSERT INTO gov_fusion_relation (domain_id, relation_code, relation_name, from_entity_id, to_entity_id, relation_type, description)
SELECT d.id, 'REL_LEGAL_PERMIT', '法人-行政许可', e1.id, e2.id, 'ONE_TO_MANY', '一个法人可持有多项许可'
FROM gov_fusion_domain d
JOIN gov_fusion_logic_entity e1 ON e1.domain_id = d.id AND e1.entity_code = 'ENT_LEGAL_SUBJECT'
JOIN gov_fusion_logic_entity e2 ON e2.domain_id = d.id AND e2.entity_code = 'ENT_PERMIT'
WHERE d.domain_code = 'DOM_LEGAL'
ON DUPLICATE KEY UPDATE
  relation_name = VALUES(relation_name),
  from_entity_id = VALUES(from_entity_id),
  to_entity_id = VALUES(to_entity_id),
  relation_type = VALUES(relation_type);

INSERT INTO gov_fusion_relation (domain_id, relation_code, relation_name, from_entity_id, to_entity_id, relation_type, description)
SELECT d.id, 'REL_SCENIC_HOTEL', '景区客流-住宿接待', e1.id, e2.id, 'MANY_TO_MANY', '景区客流与住宿接待关联分析'
FROM gov_fusion_domain d
JOIN gov_fusion_logic_entity e1 ON e1.domain_id = d.id AND e1.entity_code = 'ENT_SCENIC_VISITOR'
JOIN gov_fusion_logic_entity e2 ON e2.domain_id = d.id AND e2.entity_code = 'ENT_HOTEL_OCCUPANCY'
WHERE d.domain_code = 'DOM_TOURISM'
ON DUPLICATE KEY UPDATE
  relation_name = VALUES(relation_name),
  from_entity_id = VALUES(from_entity_id),
  to_entity_id = VALUES(to_entity_id),
  relation_type = VALUES(relation_type);

-- 清理早期随意命名的关系编码（若存在）
DELETE FROM gov_fusion_relation WHERE relation_code = 'Enterprise_Employee_Relationship';

-- ========== 4. 字段（企业主题：替换不真实字段） ==========
DELETE f FROM gov_fusion_field f
JOIN gov_fusion_logic_entity e ON e.id = f.entity_id
WHERE e.entity_code = 'ENT_ENTERPRISE_THEME'
  AND f.field_code IN ('ITEM01','ITEM02','class_id','class_name','grade','head_teacher','stu_count','create_time','name','age');

INSERT INTO gov_fusion_field (entity_id, field_code, field_name, data_type, nullable_flag, pk_flag, description, sort_order)
SELECT e.id, v.field_code, v.field_name, v.data_type, v.nullable_flag, v.pk_flag, v.description, v.sort_order
FROM gov_fusion_logic_entity e
JOIN (
  SELECT 'id' AS field_code, '主键' AS field_name, 'BIGINT' AS data_type, 0 AS nullable_flag, 1 AS pk_flag, '自增主键' AS description, 1 AS sort_order
  UNION ALL SELECT 'unified_credit_code_masked', '统一社会信用代码(脱敏)', 'VARCHAR', 0, 0, '脱敏后的信用代码', 2
  UNION ALL SELECT 'enterprise_name', '企业名称', 'VARCHAR', 0, 0, '工商登记名称', 3
  UNION ALL SELECT 'capital_level', '注册资本等级', 'VARCHAR', 1, 0, 'HIGH/MEDIUM/LOW', 4
  UNION ALL SELECT 'industry_code', '行业代码', 'VARCHAR', 1, 0, '国民经济行业分类', 5
  UNION ALL SELECT 'district_code', '区县代码', 'VARCHAR', 1, 0, '承德市区县行政区划', 6
  UNION ALL SELECT 'fused_at', '融合时间', 'DATETIME', 1, 0, '主题库落库时间', 7
) v
WHERE e.entity_code = 'ENT_ENTERPRISE_THEME'
ON DUPLICATE KEY UPDATE
  field_name = VALUES(field_name),
  data_type = VALUES(data_type),
  nullable_flag = VALUES(nullable_flag),
  pk_flag = VALUES(pk_flag),
  description = VALUES(description),
  sort_order = VALUES(sort_order);

INSERT INTO gov_fusion_field (entity_id, field_code, field_name, data_type, nullable_flag, pk_flag, description, sort_order)
SELECT e.id, v.field_code, v.field_name, v.data_type, v.nullable_flag, v.pk_flag, v.description, v.sort_order
FROM gov_fusion_logic_entity e
JOIN (
  SELECT 'id' AS field_code, '主键' AS field_name, 'BIGINT' AS data_type, 0 AS nullable_flag, 1 AS pk_flag, NULL AS description, 1 AS sort_order
  UNION ALL SELECT 'employee_id', '人员编号', 'VARCHAR', 0, 0, '从业人员唯一编号', 2
  UNION ALL SELECT 'credit_code_masked', '所属企业信用代码(脱敏)', 'VARCHAR', 0, 0, '关联企业主题', 3
  UNION ALL SELECT 'name_masked', '姓名(脱敏)', 'VARCHAR', 1, 0, NULL, 4
  UNION ALL SELECT 'job_title', '岗位', 'VARCHAR', 1, 0, NULL, 5
  UNION ALL SELECT 'district_code', '区县代码', 'VARCHAR', 1, 0, NULL, 6
  UNION ALL SELECT 'fused_at', '融合时间', 'DATETIME', 1, 0, NULL, 7
) v
WHERE e.entity_code = 'ENT_EMPLOYEE_THEME'
ON DUPLICATE KEY UPDATE
  field_name = VALUES(field_name),
  data_type = VALUES(data_type),
  sort_order = VALUES(sort_order);

INSERT INTO gov_fusion_field (entity_id, field_code, field_name, data_type, nullable_flag, pk_flag, description, sort_order)
SELECT e.id, v.field_code, v.field_name, v.data_type, v.nullable_flag, v.pk_flag, v.description, v.sort_order
FROM gov_fusion_logic_entity e
JOIN (
  SELECT 'id' AS field_code, '主键' AS field_name, 'BIGINT' AS data_type, 0 AS nullable_flag, 1 AS pk_flag, NULL AS description, 1 AS sort_order
  UNION ALL SELECT 'person_id', '人员编号', 'VARCHAR', 0, 0, NULL, 2
  UNION ALL SELECT 'name_masked', '姓名(脱敏)', 'VARCHAR', 1, 0, NULL, 3
  UNION ALL SELECT 'id_card_masked', '证件号(脱敏)', 'VARCHAR', 1, 0, NULL, 4
  UNION ALL SELECT 'gender', '性别', 'VARCHAR', 1, 0, NULL, 5
  UNION ALL SELECT 'age', '年龄', 'BIGINT', 1, 0, NULL, 6
  UNION ALL SELECT 'district_code', '区县代码', 'VARCHAR', 1, 0, NULL, 7
  UNION ALL SELECT 'district_name', '区县名称', 'VARCHAR', 1, 0, NULL, 8
  UNION ALL SELECT 'hukou_type', '户籍类型', 'VARCHAR', 1, 0, NULL, 9
  UNION ALL SELECT 'fused_at', '融合时间', 'DATETIME', 1, 0, NULL, 10
) v
WHERE e.entity_code = 'ENT_POP_BASE'
ON DUPLICATE KEY UPDATE
  field_name = VALUES(field_name),
  data_type = VALUES(data_type),
  sort_order = VALUES(sort_order);

INSERT INTO gov_fusion_field (entity_id, field_code, field_name, data_type, nullable_flag, pk_flag, description, sort_order)
SELECT e.id, v.field_code, v.field_name, v.data_type, v.nullable_flag, v.pk_flag, v.description, v.sort_order
FROM gov_fusion_logic_entity e
JOIN (
  SELECT 'id' AS field_code, '主键' AS field_name, 'BIGINT' AS data_type, 0 AS nullable_flag, 1 AS pk_flag, NULL AS description, 1 AS sort_order
  UNION ALL SELECT 'stat_date', '统计日期', 'VARCHAR', 0, 0, 'yyyy-MM-dd', 2
  UNION ALL SELECT 'scenic_code', '景区编码', 'VARCHAR', 0, 0, NULL, 3
  UNION ALL SELECT 'scenic_name', '景区名称', 'VARCHAR', 0, 0, NULL, 4
  UNION ALL SELECT 'visitor_count', '接待人次', 'BIGINT', 0, 0, NULL, 5
  UNION ALL SELECT 'ticket_income', '门票收入(元)', 'DECIMAL', 1, 0, NULL, 6
  UNION ALL SELECT 'district_code', '区县代码', 'VARCHAR', 1, 0, NULL, 7
) v
WHERE e.entity_code = 'ENT_SCENIC_VISITOR'
ON DUPLICATE KEY UPDATE
  field_name = VALUES(field_name),
  data_type = VALUES(data_type),
  sort_order = VALUES(sort_order);

INSERT INTO gov_fusion_field (entity_id, field_code, field_name, data_type, nullable_flag, pk_flag, description, sort_order)
SELECT e.id, v.field_code, v.field_name, v.data_type, v.nullable_flag, v.pk_flag, v.description, v.sort_order
FROM gov_fusion_logic_entity e
JOIN (
  SELECT 'id' AS field_code, '主键' AS field_name, 'BIGINT' AS data_type, 0 AS nullable_flag, 1 AS pk_flag, NULL AS description, 1 AS sort_order
  UNION ALL SELECT 'stat_date', '监测日期', 'VARCHAR', 0, 0, NULL, 2
  UNION ALL SELECT 'station_code', '站点编码', 'VARCHAR', 0, 0, NULL, 3
  UNION ALL SELECT 'station_name', '站点名称', 'VARCHAR', 1, 0, NULL, 4
  UNION ALL SELECT 'aqi', 'AQI', 'BIGINT', 1, 0, NULL, 5
  UNION ALL SELECT 'pm25', 'PM2.5', 'DECIMAL', 1, 0, 'μg/m³', 6
  UNION ALL SELECT 'pm10', 'PM10', 'DECIMAL', 1, 0, 'μg/m³', 7
  UNION ALL SELECT 'quality_level', '空气质量等级', 'VARCHAR', 1, 0, '优/良/轻度污染等', 8
  UNION ALL SELECT 'district_code', '区县代码', 'VARCHAR', 1, 0, NULL, 9
) v
WHERE e.entity_code = 'ENT_AIR_QUALITY'
ON DUPLICATE KEY UPDATE
  field_name = VALUES(field_name),
  data_type = VALUES(data_type),
  sort_order = VALUES(sort_order);

INSERT INTO gov_fusion_field (entity_id, field_code, field_name, data_type, nullable_flag, pk_flag, description, sort_order)
SELECT e.id, v.field_code, v.field_name, v.data_type, v.nullable_flag, v.pk_flag, v.description, v.sort_order
FROM gov_fusion_logic_entity e
JOIN (
  SELECT 'id' AS field_code, '主键' AS field_name, 'BIGINT' AS data_type, 0 AS nullable_flag, 1 AS pk_flag, NULL AS description, 1 AS sort_order
  UNION ALL SELECT 'stat_date', '统计日期', 'VARCHAR', 0, 0, NULL, 2
  UNION ALL SELECT 'road_code', '道路编码', 'VARCHAR', 0, 0, NULL, 3
  UNION ALL SELECT 'road_name', '道路名称', 'VARCHAR', 1, 0, NULL, 4
  UNION ALL SELECT 'avg_speed', '平均车速(km/h)', 'DECIMAL', 1, 0, NULL, 5
  UNION ALL SELECT 'flow_count', '断面流量', 'BIGINT', 1, 0, NULL, 6
  UNION ALL SELECT 'congestion_index', '拥堵指数', 'DECIMAL', 1, 0, NULL, 7
  UNION ALL SELECT 'district_code', '区县代码', 'VARCHAR', 1, 0, NULL, 8
) v
WHERE e.entity_code = 'ENT_ROAD_FLOW'
ON DUPLICATE KEY UPDATE
  field_name = VALUES(field_name),
  data_type = VALUES(data_type),
  sort_order = VALUES(sort_order);

-- ========== 5. 物理映射（绑定 DWS=-3 / ADS=-4） ==========
INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_ENTERPRISE_THEME', 'dws_enterprise_theme', -3, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_ENTERPRISE_THEME'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_EMPLOYEE_THEME', 'dws_employee_theme', -3, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_EMPLOYEE_THEME'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_INDUSTRY_AGG', 'dws_industry_agg', -3, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_INDUSTRY_AGG'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_CREDIT_PROFILE', 'dws_enterprise_credit', -3, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_CREDIT_PROFILE'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_POP_BASE', 'dws_population_base', -3, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_POP_BASE'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_POP_DISTRICT_SUM', 'ads_pop_district_summary', -4, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_POP_DISTRICT_SUM'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_LEGAL_SUBJECT', 'dws_legal_subject', -3, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_LEGAL_SUBJECT'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_ROAD_FLOW', 'dws_road_flow_daily', -3, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_ROAD_FLOW'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_AIR_QUALITY', 'dws_air_quality_daily', -3, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_AIR_QUALITY'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_SCENIC_VISITOR', 'dws_scenic_visitor_daily', -3, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_SCENIC_VISITOR'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

INSERT INTO gov_fusion_physical (entity_id, physical_code, table_name, datasource_id, ddl_sql, status)
SELECT e.id, 'PHY_ONLINE_SERVICE', 'dws_online_service_daily', -3, NULL, 'ACTIVE'
FROM gov_fusion_logic_entity e WHERE e.entity_code = 'ENT_ONLINE_SERVICE'
ON DUPLICATE KEY UPDATE table_name = VALUES(table_name), datasource_id = VALUES(datasource_id), status = VALUES(status);

-- 移除挂在企业主题上的过程层草稿映射（演示不真实）
DELETE p FROM gov_fusion_physical p
JOIN gov_fusion_logic_entity e ON e.id = p.entity_id
WHERE e.entity_code = 'ENT_ENTERPRISE_THEME' AND p.physical_code = 'PHY_dwd_class02';

-- ========== 6. DWS/ADS 物理表 + 样例行 ==========
CREATE DATABASE IF NOT EXISTS smart_city_dws CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_city_ads CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE smart_city_dws;

CREATE TABLE IF NOT EXISTS dws_enterprise_theme (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  unified_credit_code_masked VARCHAR(64) NOT NULL,
  enterprise_name VARCHAR(256) NOT NULL,
  capital_level VARCHAR(16) NULL COMMENT 'HIGH/MEDIUM/LOW',
  industry_code VARCHAR(64) NULL,
  district_code VARCHAR(12) NULL,
  fused_at DATETIME NULL,
  UNIQUE KEY uk_dws_enterprise_credit (unified_credit_code_masked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业主题库';

CREATE TABLE IF NOT EXISTS dws_employee_theme (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(64) NOT NULL,
  credit_code_masked VARCHAR(64) NOT NULL,
  name_masked VARCHAR(64) NULL,
  job_title VARCHAR(64) NULL,
  district_code VARCHAR(12) NULL,
  fused_at DATETIME NULL,
  UNIQUE KEY uk_emp_id (employee_id),
  KEY idx_emp_credit (credit_code_masked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='从业人员主题';

CREATE TABLE IF NOT EXISTS dws_industry_agg (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  district_code VARCHAR(12) NOT NULL,
  district_name VARCHAR(64) NULL,
  industry_code VARCHAR(64) NOT NULL,
  industry_name VARCHAR(128) NULL,
  enterprise_count INT NOT NULL DEFAULT 0,
  employee_count INT NOT NULL DEFAULT 0,
  stat_month CHAR(7) NOT NULL,
  UNIQUE KEY uk_ind_agg (district_code, industry_code, stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产业集聚汇总';

CREATE TABLE IF NOT EXISTS dws_enterprise_credit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  credit_code_masked VARCHAR(64) NOT NULL,
  enterprise_name VARCHAR(256) NOT NULL,
  credit_level VARCHAR(16) NOT NULL COMMENT 'A/B/C/D',
  risk_tag VARCHAR(64) NULL,
  score DECIMAL(6,2) NULL,
  eval_date DATE NULL,
  UNIQUE KEY uk_credit (credit_code_masked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业信用画像';

CREATE TABLE IF NOT EXISTS dws_population_base (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  person_id VARCHAR(64) NOT NULL,
  name_masked VARCHAR(64) NULL,
  id_card_masked VARCHAR(32) NULL,
  gender VARCHAR(8) NULL,
  age INT NULL,
  age_band VARCHAR(32) NULL,
  education VARCHAR(32) NULL,
  hukou_type VARCHAR(32) NULL,
  district_code VARCHAR(12) NULL,
  district_name VARCHAR(64) NULL,
  focus_tag VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  fused_at DATETIME NULL,
  UNIQUE KEY uk_person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人口基础主题';

CREATE TABLE IF NOT EXISTS dws_legal_subject (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  credit_code_masked VARCHAR(64) NOT NULL,
  subject_name VARCHAR(256) NOT NULL,
  subject_type VARCHAR(32) NULL COMMENT '企业/个体/事业等',
  register_status VARCHAR(32) NULL,
  district_code VARCHAR(12) NULL,
  register_date DATE NULL,
  UNIQUE KEY uk_legal (credit_code_masked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法人主体主题';

CREATE TABLE IF NOT EXISTS dws_road_flow_daily (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date CHAR(10) NOT NULL,
  road_code VARCHAR(32) NOT NULL,
  road_name VARCHAR(128) NULL,
  avg_speed DECIMAL(8,2) NULL,
  flow_count BIGINT NULL,
  congestion_index DECIMAL(6,2) NULL,
  district_code VARCHAR(12) NULL,
  UNIQUE KEY uk_road_day (stat_date, road_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路网流量日汇总';

CREATE TABLE IF NOT EXISTS dws_air_quality_daily (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date CHAR(10) NOT NULL,
  station_code VARCHAR(32) NOT NULL,
  station_name VARCHAR(128) NULL,
  aqi INT NULL,
  pm25 DECIMAL(8,2) NULL,
  pm10 DECIMAL(8,2) NULL,
  quality_level VARCHAR(32) NULL,
  district_code VARCHAR(12) NULL,
  UNIQUE KEY uk_aqi_day (stat_date, station_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空气质量日值';

CREATE TABLE IF NOT EXISTS dws_scenic_visitor_daily (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date CHAR(10) NOT NULL,
  scenic_code VARCHAR(32) NOT NULL,
  scenic_name VARCHAR(128) NOT NULL,
  visitor_count BIGINT NOT NULL DEFAULT 0,
  ticket_income DECIMAL(14,2) NULL,
  district_code VARCHAR(12) NULL,
  UNIQUE KEY uk_scenic_day (stat_date, scenic_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='景区客流日汇总';

CREATE TABLE IF NOT EXISTS dws_online_service_daily (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stat_date CHAR(10) NOT NULL,
  dept_name VARCHAR(128) NOT NULL,
  apply_count INT NOT NULL DEFAULT 0,
  finish_count INT NOT NULL DEFAULT 0,
  avg_hours DECIMAL(8,2) NULL,
  district_code VARCHAR(12) NULL,
  UNIQUE KEY uk_svc_day (stat_date, dept_name, district_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一网通办日汇总';

INSERT INTO dws_enterprise_theme (unified_credit_code_masked, enterprise_name, capital_level, industry_code, district_code, fused_at)
VALUES
  ('91130802******1X', '承德避暑山庄旅游集团有限公司', 'HIGH', 'N79', '130802', NOW()),
  ('91130803******2Y', '承德钒钛新材料有限公司', 'HIGH', 'C32', '130803', NOW()),
  ('91130821******3Z', '承德县绿色农业合作社', 'MEDIUM', 'A01', '130821', NOW()),
  ('91130881******4A', '平泉食用菌产业集团', 'MEDIUM', 'A03', '130881', NOW()),
  ('91130826******5B', '丰宁清洁能源开发有限公司', 'HIGH', 'D44', '130826', NOW()),
  ('91130828******6C', '围场生态林业有限公司', 'LOW', 'A02', '130828', NOW())
ON DUPLICATE KEY UPDATE enterprise_name = VALUES(enterprise_name), capital_level = VALUES(capital_level);

INSERT INTO dws_employee_theme (employee_id, credit_code_masked, name_masked, job_title, district_code, fused_at)
VALUES
  ('EMP-CD-0001', '91130802******1X', '张*', '运营经理', '130802', NOW()),
  ('EMP-CD-0002', '91130802******1X', '李*', '导游', '130802', NOW()),
  ('EMP-CD-0003', '91130803******2Y', '王*', '工艺工程师', '130803', NOW()),
  ('EMP-CD-0004', '91130826******5B', '赵*', '运维专员', '130826', NOW())
ON DUPLICATE KEY UPDATE name_masked = VALUES(name_masked), job_title = VALUES(job_title);

INSERT INTO dws_industry_agg (district_code, district_name, industry_code, industry_name, enterprise_count, employee_count, stat_month)
VALUES
  ('130802', '双桥区', 'N79', '旅行社及相关服务', 126, 2180, '2026-07'),
  ('130803', '双滦区', 'C32', '黑色金属冶炼和压延', 48, 5600, '2026-07'),
  ('130881', '平泉市', 'A03', '农业', 210, 4300, '2026-07'),
  ('130826', '丰宁满族自治县', 'D44', '电力热力生产供应', 32, 1900, '2026-07')
ON DUPLICATE KEY UPDATE enterprise_count = VALUES(enterprise_count), employee_count = VALUES(employee_count);

INSERT INTO dws_enterprise_credit (credit_code_masked, enterprise_name, credit_level, risk_tag, score, eval_date)
VALUES
  ('91130802******1X', '承德避暑山庄旅游集团有限公司', 'A', '无异常', 92.50, '2026-07-01'),
  ('91130803******2Y', '承德钒钛新材料有限公司', 'B', '环保关注', 81.20, '2026-07-01'),
  ('91130821******3Z', '承德县绿色农业合作社', 'A', '无异常', 88.00, '2026-07-01'),
  ('91130826******5B', '丰宁清洁能源开发有限公司', 'A', '无异常', 90.10, '2026-07-01')
ON DUPLICATE KEY UPDATE credit_level = VALUES(credit_level), score = VALUES(score);

INSERT INTO dws_population_base (person_id, name_masked, id_card_masked, gender, age, age_band, education, hukou_type, district_code, district_name, focus_tag, status, fused_at)
VALUES
  ('P1308020001', '刘*', '130802********1234', '男', 36, '30-39', '本科', '城镇', '130802', '双桥区', NULL, 'ACTIVE', NOW()),
  ('P1308020002', '陈*', '130802********5678', '女', 62, '60+', '高中', '城镇', '130802', '双桥区', '老龄', 'ACTIVE', NOW()),
  ('P1308030001', '杨*', '130803********9012', '男', 28, '20-29', '大专', '农村', '130803', '双滦区', NULL, 'ACTIVE', NOW()),
  ('P1308260001', '周*', '130826********3456', '女', 45, '40-49', '初中', '农村', '130826', '丰宁满族自治县', NULL, 'ACTIVE', NOW()),
  ('P1308280001', '吴*', '130828********7890', '男', 17, '0-17', '高中', '农村', '130828', '围场满族蒙古族自治县', '未成年', 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE district_name = VALUES(district_name), age = VALUES(age);

INSERT INTO dws_legal_subject (credit_code_masked, subject_name, subject_type, register_status, district_code, register_date)
VALUES
  ('91130802******1X', '承德避暑山庄旅游集团有限公司', '企业', '存续', '130802', '2008-05-18'),
  ('91130803******2Y', '承德钒钛新材料有限公司', '企业', '存续', '130803', '2012-09-01'),
  ('92130821******9K', '承德县张某种植户', '个体', '存续', '130821', '2019-03-12')
ON DUPLICATE KEY UPDATE subject_name = VALUES(subject_name), register_status = VALUES(register_status);

INSERT INTO dws_road_flow_daily (stat_date, road_code, road_name, avg_speed, flow_count, congestion_index, district_code)
VALUES
  ('2026-08-14', 'RD-WQ-01', '武烈路', 28.50, 18620, 1.85, '130802'),
  ('2026-08-14', 'RD-XH-02', '新华路', 32.10, 15240, 1.62, '130802'),
  ('2026-08-14', 'RD-SL-03', '双滦大道', 41.20, 9800, 1.20, '130803'),
  ('2026-08-13', 'RD-WQ-01', '武烈路', 26.80, 20110, 2.05, '130802')
ON DUPLICATE KEY UPDATE avg_speed = VALUES(avg_speed), flow_count = VALUES(flow_count), congestion_index = VALUES(congestion_index);

INSERT INTO dws_air_quality_daily (stat_date, station_code, station_name, aqi, pm25, pm10, quality_level, district_code)
VALUES
  ('2026-08-14', 'AQ-SQ-01', '双桥区文化广场站', 42, 18.0, 35.0, '优', '130802'),
  ('2026-08-14', 'AQ-SL-01', '双滦区开发区站', 58, 28.0, 52.0, '良', '130803'),
  ('2026-08-14', 'AQ-FN-01', '丰宁县城站', 36, 12.0, 28.0, '优', '130826'),
  ('2026-08-13', 'AQ-SQ-01', '双桥区文化广场站', 51, 22.0, 40.0, '良', '130802')
ON DUPLICATE KEY UPDATE aqi = VALUES(aqi), pm25 = VALUES(pm25), quality_level = VALUES(quality_level);

INSERT INTO dws_scenic_visitor_daily (stat_date, scenic_code, scenic_name, visitor_count, ticket_income, district_code)
VALUES
  ('2026-08-14', 'SC-BSZ', '避暑山庄及周围寺庙', 28650, 2158000.00, '130802'),
  ('2026-08-14', 'SC-JSS', '金山岭长城', 9200, 690000.00, '130824'),
  ('2026-08-14', 'SC-SBLH', '塞罕坝国家森林公园', 15480, 1083600.00, '130828'),
  ('2026-08-13', 'SC-BSZ', '避暑山庄及周围寺庙', 31200, 2340000.00, '130802')
ON DUPLICATE KEY UPDATE visitor_count = VALUES(visitor_count), ticket_income = VALUES(ticket_income);

INSERT INTO dws_online_service_daily (stat_date, dept_name, apply_count, finish_count, avg_hours, district_code)
VALUES
  ('2026-08-14', '市行政审批局', 1260, 1188, 4.20, '130802'),
  ('2026-08-14', '市人社局', 860, 802, 6.50, '130802'),
  ('2026-08-14', '双滦区政务服务中心', 420, 405, 3.80, '130803'),
  ('2026-08-14', '丰宁县政务服务中心', 310, 298, 5.10, '130826')
ON DUPLICATE KEY UPDATE apply_count = VALUES(apply_count), finish_count = VALUES(finish_count);

USE smart_city_ads;

CREATE TABLE IF NOT EXISTS ads_pop_district_summary (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  district_code VARCHAR(12) NOT NULL,
  district_name VARCHAR(64) NULL,
  stat_month CHAR(7) NOT NULL,
  hukou_count INT NOT NULL DEFAULT 0,
  resident_count INT NOT NULL DEFAULT 0,
  age60_count INT NOT NULL DEFAULT 0,
  KEY idx_ads_pop (district_code, stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='区县人口汇总';

INSERT INTO ads_pop_district_summary (district_code, district_name, stat_month, hukou_count, resident_count, age60_count)
SELECT v.district_code, v.district_name, v.stat_month, v.hukou_count, v.resident_count, v.age60_count
FROM (
  SELECT '130802' AS district_code, '双桥区' AS district_name, '2026-07' AS stat_month, 286000 AS hukou_count, 312000 AS resident_count, 52000 AS age60_count
  UNION ALL SELECT '130803', '双滦区', '2026-07', 148000, 165000, 28000
  UNION ALL SELECT '130821', '承德县', '2026-07', 360000, 342000, 71000
  UNION ALL SELECT '130826', '丰宁满族自治县', '2026-07', 380000, 355000, 76000
  UNION ALL SELECT '130828', '围场满族蒙古族自治县', '2026-07', 420000, 390000, 82000
) v
WHERE NOT EXISTS (
  SELECT 1 FROM ads_pop_district_summary a
  WHERE a.district_code = v.district_code AND a.stat_month = v.stat_month
);

-- 同步控制面库中已有同名表样例（兼容旧映射未切分层时）
USE smart_city;

INSERT INTO dws_enterprise_theme (unified_credit_code_masked, enterprise_name, capital_level, industry_code, district_code, fused_at)
VALUES
  ('91130802******1X', '承德避暑山庄旅游集团有限公司', 'HIGH', 'N79', '130802', NOW()),
  ('91130803******2Y', '承德钒钛新材料有限公司', 'HIGH', 'C32', '130803', NOW()),
  ('91130821******3Z', '承德县绿色农业合作社', 'MEDIUM', 'A01', '130821', NOW()),
  ('91130881******4A', '平泉食用菌产业集团', 'MEDIUM', 'A03', '130881', NOW()),
  ('91130826******5B', '丰宁清洁能源开发有限公司', 'HIGH', 'D44', '130826', NOW()),
  ('91130828******6C', '围场生态林业有限公司', 'LOW', 'A02', '130828', NOW())
ON DUPLICATE KEY UPDATE enterprise_name = VALUES(enterprise_name);
