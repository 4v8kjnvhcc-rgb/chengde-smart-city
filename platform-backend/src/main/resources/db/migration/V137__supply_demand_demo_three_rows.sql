-- 供需对接演示数据收敛：需求 / 异议 / 清单各保留 3 条
DELETE FROM biz_demand_supply_task;
DELETE FROM biz_data_duty;
DELETE FROM biz_data_demand;

INSERT INTO biz_data_demand (
  demand_title, requester_org, demand_type, template_code, stage, status,
  match_score, analysis_note, assignee_org, demand_content, created_by
) VALUES
(
  '人口基础信息共享需求',
  '民政局',
  'STRUCTURED',
  'TPL_STRUCT_01',
  'PRE_AUDIT',
  'SUBMITTED',
  NULL,
  NULL,
  NULL,
  '用于低保核查，需人口库姓名、身份证号、户籍地址字段',
  'sys_admin'
),
(
  '法人登记信息对接需求',
  '市场监管局',
  'STRUCTURED',
  'TPL_API_01',
  'AUDIT',
  'DISPATCHED',
  86.00,
  '匹配法人库接口资源，建议有条件共享',
  '资源管理处',
  '企业信用核查场景，需统一社会信用代码与登记状态',
  'sys_admin'
),
(
  '宏观经济指标交换需求',
  '发改委',
  'STRUCTURED',
  'TPL_STRUCT_01',
  'SUPPLY',
  'CONFIRMED',
  92.00,
  '已匹配宏观经济库表资源',
  '统计局',
  '季度 GDP、固投等指标库表同步',
  'sys_admin'
);

DELETE FROM biz_catalog_objection;

INSERT INTO biz_catalog_objection (
  title, catalog_id, demand_id, objection_type, content, provider_org, verify_org, status, created_by
)
SELECT
  '人口库字段说明不完整',
  c.id,
  d.id,
  'COMPLETENESS',
  '缺少更新频率与口径说明',
  '民政局',
  '数据管理局',
  'OPEN',
  'sys_admin'
FROM (SELECT id FROM biz_catalog_item WHERE publish_status = 'PUBLISHED' AND gov_resource_id IS NOT NULL ORDER BY id LIMIT 1) c
CROSS JOIN (SELECT id FROM biz_data_demand ORDER BY id LIMIT 1) d;

INSERT INTO biz_catalog_objection (
  title, catalog_id, demand_id, objection_type, content, provider_org, verify_org, status, created_by
)
SELECT
  '法人接口返回字段不一致',
  c.id,
  d.id,
  'QUALITY',
  '登记状态枚举与文档不符',
  '市场监管局',
  '数据管理局',
  'OPEN',
  'sys_admin'
FROM (
  SELECT id FROM biz_catalog_item
  WHERE publish_status = 'PUBLISHED' AND gov_resource_id IS NOT NULL
  ORDER BY id LIMIT 1
) c
CROSS JOIN (SELECT id FROM biz_data_demand ORDER BY id LIMIT 1 OFFSET 1) d;

INSERT INTO biz_catalog_objection (
  title, catalog_id, demand_id, objection_type, content, provider_org, verify_org, status, created_by
)
SELECT
  '宏观指标授权范围争议',
  c.id,
  d.id,
  'AUTH',
  '跨部门授权级别需复核',
  '发改委',
  '数据管理局',
  'OPEN',
  'sys_admin'
FROM (
  SELECT id FROM biz_catalog_item
  WHERE publish_status = 'PUBLISHED' AND gov_resource_id IS NOT NULL
  ORDER BY id LIMIT 1
) c
CROSS JOIN (SELECT id FROM biz_data_demand ORDER BY id LIMIT 1 OFFSET 2) d;

DELETE FROM biz_supply_manifest;
INSERT INTO biz_supply_manifest (manifest_type, ref_id, title, status, auth_level, cascade_flag)
SELECT 'SUPPLY_DEMAND', d.id, CONCAT(d.demand_title, '·对接台账'), 'ACTIVE', 'DEPT', 0
FROM biz_data_demand d
ORDER BY d.id
LIMIT 3;
