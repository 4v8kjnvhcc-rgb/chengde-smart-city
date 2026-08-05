-- V134: 部门数据共享门户演示资源（库表 / 接口 / 文件各 1 条，审批通过已发布）

-- 1) 统一编目资源
INSERT INTO gov_catalog_resource (
  resource_code, resource_name, resource_type, catalog_origin,
  physical_table_name, source_path_type, category_path, provider_org,
  resource_format, share_type, open_type, theme_name, update_cycle, description,
  secret_flag, publish_status, approval_status, subscription_status, version_no,
  ext_json, created_by, created_at, updated_at
)
SELECT
  '13080000230006', '测试KB042001test_popu_RC', 'DATA', 'INGEST',
  'cd_population', 'DIRECT', '主题资源目录/人口', '测试-高新区民政局',
  'DATABASE', 'CONDITIONAL', 'SOCIAL_OPEN', '人口', '按日',
  '人口主题库表资源，审批通过后可申请库表同步',
  0, 'PUBLISHED', 'APPROVED', 'NONE', 1,
  '{"resourceType":"TABLE","tables":[{"tableName":"cd_population","catalogCode":"13080000230006","summary":"ys_encryption","columns":[{"name":"health_status","comment":"身体状况","type":"VARCHAR","length":100,"pk":false,"nullable":false,"sensitivity":1},{"name":"client_ip","comment":"最后更新人IP","type":"VARCHAR","length":46,"pk":false,"nullable":false,"sensitivity":1},{"name":"six_special_groups","comment":"六类人群","type":"VARCHAR","length":255,"pk":false,"nullable":false,"sensitivity":1},{"name":"created_by_person_id","comment":"创建人ID","type":"INT","length":10,"pk":false,"nullable":false,"sensitivity":1},{"name":"management_type","comment":"管理类型","type":"VARCHAR","length":255,"pk":false,"nullable":false,"sensitivity":1}]}]}',
  'sys_admin', '2026-04-21 11:58:26', '2026-04-21 11:58:26'
WHERE NOT EXISTS (SELECT 1 FROM gov_catalog_resource WHERE resource_code = '13080000230006');

INSERT INTO gov_catalog_resource (
  resource_code, resource_name, resource_type, catalog_origin,
  source_path_type, category_path, provider_org,
  resource_format, share_type, open_type, theme_name, update_cycle, description,
  secret_flag, publish_status, approval_status, subscription_status, version_no,
  ext_json, created_by, created_at, updated_at
)
SELECT
  '13080000050007', '测试数据目录API', 'SERVICE', 'GOVERNANCE',
  'DIRECT', '主题资源目录/法人', '测试-高新区行政审批服务管理局',
  'API', 'CONDITIONAL', 'SOCIAL_OPEN', '法人', '按需',
  '目录查询类接口，申请通过后由服务总线封装转发并签发 token',
  0, 'PUBLISHED', 'APPROVED', 'NONE', 1,
  '{"resourceType":"API","apis":[{"apiName":"测试API","apiCode":"13080000050007_001","catalogCode":"13080000050007","version":"V1.0","targetAddressHint":"资源申请通过后前往个人中心查看","requestPath":"http://127.0.0.1/sjgxjh/glzy/sso","httpMethod":"POST","registeredAt":"2026-04-14 05:37:20","description":"","expireAt":"2026-04-16 00:00:00","requestParams":[{"name":"name","required":true,"dataType":"String","comment":"姓名"}],"responseParams":[{"name":"state","required":true,"dataType":"String","comment":"状态"},{"name":"id_card","required":true,"dataType":"String","comment":"身份证号"},{"name":"name","required":true,"dataType":"String","comment":"姓名"}],"successExample":{"state":"ok","id_card":"1308***********","name":"张三"}}]}',
  'sys_admin', '2026-04-14 05:41:44', '2026-04-14 05:41:44'
WHERE NOT EXISTS (SELECT 1 FROM gov_catalog_resource WHERE resource_code = '13080000050007');

INSERT INTO gov_catalog_resource (
  resource_code, resource_name, resource_type, catalog_origin,
  source_path_type, category_path, provider_org,
  resource_format, share_type, open_type, theme_name, update_cycle, description,
  secret_flag, publish_status, approval_status, subscription_status, version_no,
  ext_json, created_by, created_at, updated_at
)
SELECT
  '13080000990001', '社会救助证明材料包', 'DATA', 'INGEST',
  'DIRECT', '部门资源目录/社会事务', '社会事务部',
  'FILE', 'CONDITIONAL', 'PARTIAL_OPEN', '社会事务', '按月',
  '社会救助证明批量导出清单及附件说明，申请通过后可在个人中心查看 FTP 地址',
  0, 'PUBLISHED', 'APPROVED', 'NONE', 1,
  '{"resourceType":"FILE","files":[{"fileName":"救助证明清单.xlsx","fileCode":"13080000990001_F01","catalogCode":"13080000990001","format":"XLSX","size":"2.4 MB","updateCycle":"按月","storage":"FTP","addressHint":"资源申请通过后前往个人中心查看 FTP 地址","registeredAt":"2026-04-18 10:05:33","description":"社会救助证明批量导出清单及字段说明"},{"fileName":"附件说明.pdf","fileCode":"13080000990001_F02","catalogCode":"13080000990001","format":"PDF","size":"356 KB","updateCycle":"按月","storage":"FTP","addressHint":"资源申请通过后前往个人中心查看 FTP 地址","registeredAt":"2026-04-18 10:06:01","description":"字段与使用说明"}]}',
  'sys_admin', '2026-04-20 14:22:08', '2026-04-20 14:22:08'
WHERE NOT EXISTS (SELECT 1 FROM gov_catalog_resource WHERE resource_code = '13080000990001');

-- 2) 门户展示目录（审批通过同步）
INSERT INTO biz_catalog_item (
  catalog_code, title, description, catalog_kind, catalog_origin, gov_resource_id,
  theme_code, theme_name, provider_org, share_modes, resource_count, hot_score,
  published_at, publish_status, created_by, created_at, updated_at
)
SELECT
  g.resource_code, g.resource_name, g.description,
  CASE WHEN g.resource_type = 'SERVICE' OR g.resource_format = 'API' THEN 'SERVICE' ELSE 'DATA' END,
  g.catalog_origin, g.id,
  CASE g.resource_code
    WHEN '13080000230006' THEN 'THEME_POPU'
    WHEN '13080000050007' THEN 'THEME_LEGAL'
    ELSE 'THEME_SOCIAL'
  END,
  g.theme_name, g.provider_org,
  CASE g.resource_format
    WHEN 'API' THEN 'API'
    WHEN 'FILE' THEN 'FILE'
    ELSE 'TABLE'
  END,
  CASE g.resource_code
    WHEN '13080000230006' THEN 1
    WHEN '13080000050007' THEN 1
    ELSE 2
  END,
  CASE g.resource_code
    WHEN '13080000230006' THEN 45
    WHEN '13080000050007' THEN 67
    ELSE 28
  END,
  g.updated_at, 'PUBLISHED', 'sys_admin', g.created_at, g.updated_at
FROM gov_catalog_resource g
WHERE g.resource_code IN ('13080000230006', '13080000050007', '13080000990001')
  AND NOT EXISTS (
    SELECT 1 FROM biz_catalog_item b WHERE b.catalog_code = g.resource_code
  );

UPDATE gov_catalog_resource g
INNER JOIN biz_catalog_item b ON b.catalog_code = g.resource_code AND b.gov_resource_id = g.id
SET g.portal_catalog_id = b.id
WHERE g.resource_code IN ('13080000230006', '13080000050007', '13080000990001')
  AND (g.portal_catalog_id IS NULL OR g.portal_catalog_id = 0);
