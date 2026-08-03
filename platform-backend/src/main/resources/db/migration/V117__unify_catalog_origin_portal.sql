-- V117: 统一资源目录（归集指标目录 + 治理数据目录 + 部门门户）
-- 底座：gov_catalog_*；门户展示：biz_catalog_item（审批通过后同步）
-- catalog_origin：INGEST=指标与目录体系构建；GOVERNANCE=数据目录管理系统

-- 1) 资源 / 分类 / 门户增加来源字段
ALTER TABLE gov_catalog_resource
  ADD COLUMN catalog_origin VARCHAR(32) NOT NULL DEFAULT 'GOVERNANCE'
    COMMENT 'INGEST|GOVERNANCE 目录来源模块' AFTER resource_type,
  ADD COLUMN portal_catalog_id BIGINT NULL
    COMMENT '同步的 biz_catalog_item.id' AFTER subscription_status;

ALTER TABLE gov_catalog_category
  ADD COLUMN catalog_origin VARCHAR(32) NOT NULL DEFAULT 'GOVERNANCE'
    COMMENT 'INGEST|GOVERNANCE' AFTER category_name,
  ADD COLUMN secret_flag TINYINT NOT NULL DEFAULT 0
    COMMENT '1涉密 0非涉密' AFTER status,
  ADD COLUMN description VARCHAR(512) NULL COMMENT '分类描述' AFTER secret_flag;

ALTER TABLE biz_catalog_item
  ADD COLUMN catalog_origin VARCHAR(32) NOT NULL DEFAULT 'GOVERNANCE'
    COMMENT 'INGEST|GOVERNANCE' AFTER catalog_kind,
  ADD COLUMN gov_resource_id BIGINT NULL
    COMMENT '来源 gov_catalog_resource.id' AFTER catalog_origin;

-- 2) 将归集侧已有编目迁入统一资源表（按 registry_code 去重）
INSERT INTO gov_catalog_resource (
  resource_code, resource_name, resource_type, catalog_origin,
  category_path, category_id, provider_org, resource_format, share_type, update_cycle,
  description, secret_flag, publish_status, approval_status, subscription_status,
  version_no, portal_catalog_id, physical_table_name, source_path_type,
  created_by, created_at, updated_at
)
SELECT
  COALESCE(NULLIF(r.resource_code, ''), r.registry_code),
  COALESCE(NULLIF(r.title, ''), r.registry_code),
  'DATA',
  'INGEST',
  r.category_path,
  r.category_id,
  r.provider_org,
  COALESCE(r.resource_format, 'DATABASE'),
  COALESCE(r.share_type, 'OPEN'),
  r.update_cycle,
  COALESCE(r.description, r.asset_summary),
  CASE
    WHEN UPPER(IFNULL(r.secret_level, '')) IN ('SECRET', 'CONFIDENTIAL', 'TOP_SECRET') THEN 1
    ELSE 0
  END,
  COALESCE(r.publish_status, 'DRAFT'),
  COALESCE(r.approval_status, 'DRAFT'),
  'NONE',
  1,
  r.portal_catalog_id,
  NULL,
  'DIRECT',
  r.created_by,
  COALESCE(r.created_at, NOW()),
  NOW()
FROM ing_resource_registry r
WHERE NOT EXISTS (
  SELECT 1 FROM gov_catalog_resource g
  WHERE g.resource_code = COALESCE(NULLIF(r.resource_code, ''), r.registry_code)
);

-- 回写门户 gov_resource_id / catalog_origin
UPDATE biz_catalog_item b
INNER JOIN gov_catalog_resource g ON g.portal_catalog_id = b.id
SET b.gov_resource_id = g.id,
    b.catalog_origin = g.catalog_origin
WHERE b.gov_resource_id IS NULL OR b.gov_resource_id = 0;

-- 3) 目录相关系统字典
INSERT INTO sys_dict (dict_code, dict_name, remark, sort_order, status)
SELECT 'CATALOG_ORIGIN', '目录来源模块', '指标与目录 / 数据目录管理系统', 20, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'CATALOG_ORIGIN');

INSERT INTO sys_dict (dict_code, dict_name, remark, sort_order, status)
SELECT 'CATALOG_SHARE_TYPE', '目录共享属性', '无条件/有条件/不予共享', 21, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'CATALOG_SHARE_TYPE');

INSERT INTO sys_dict (dict_code, dict_name, remark, sort_order, status)
SELECT 'CATALOG_RESOURCE_FORMAT', '目录资源格式', '库表/文件/接口等', 22, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'CATALOG_RESOURCE_FORMAT');

INSERT INTO sys_dict (dict_code, dict_name, remark, sort_order, status)
SELECT 'CATALOG_PUBLISH_STATUS', '目录发布状态', '草稿/待审/已发布/下线', 23, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'CATALOG_PUBLISH_STATUS');

INSERT INTO sys_dict (dict_code, dict_name, remark, sort_order, status)
SELECT 'CATALOG_APPROVAL_STATUS', '目录审批状态', '资源/审批单状态', 24, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'CATALOG_APPROVAL_STATUS');

INSERT INTO sys_dict (dict_code, dict_name, remark, sort_order, status)
SELECT 'CATALOG_UPDATE_CYCLE', '目录更新周期', NULL, 25, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'CATALOG_UPDATE_CYCLE');

INSERT INTO sys_dict (dict_code, dict_name, remark, sort_order, status)
SELECT 'CATALOG_RESOURCE_TYPE', '目录资源类型', '数据/服务', 26, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code = 'CATALOG_RESOURCE_TYPE');

INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'INGEST', 'INGEST', '指标与目录体系构建', 1, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_ORIGIN'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='INGEST');
INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'GOVERNANCE', 'GOVERNANCE', '数据目录管理系统', 2, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_ORIGIN'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='GOVERNANCE');

INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'OPEN', 'OPEN', '无条件共享', 1, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_SHARE_TYPE'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='OPEN');
INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'CONDITIONAL', 'CONDITIONAL', '有条件共享', 2, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_SHARE_TYPE'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='CONDITIONAL');
INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'NOT_SHARE', 'NOT_SHARE', '不予共享', 3, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_SHARE_TYPE'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='NOT_SHARE');

INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'DATABASE', 'DATABASE', '数据库', 1, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_RESOURCE_FORMAT'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='DATABASE');
INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'FILE', 'FILE', '文件', 2, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_RESOURCE_FORMAT'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='FILE');
INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'API', 'API', '接口', 3, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_RESOURCE_FORMAT'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='API');
INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'OTHER', 'OTHER', '其他', 4, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_RESOURCE_FORMAT'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='OTHER');

INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, k.item_key, k.item_value, k.item_label, k.sort_order, 1
FROM sys_dict d
CROSS JOIN (
  SELECT 'DRAFT' item_key, 'DRAFT' item_value, '草稿' item_label, 1 sort_order UNION ALL
  SELECT 'PENDING_PUBLISH', 'PENDING_PUBLISH', '待发布审批', 2 UNION ALL
  SELECT 'PUBLISHED', 'PUBLISHED', '已发布', 3 UNION ALL
  SELECT 'OFFLINE', 'OFFLINE', '已下线', 4
) k
WHERE d.dict_code = 'CATALOG_PUBLISH_STATUS'
  AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id = d.id AND i.item_key = k.item_key);

INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, k.item_key, k.item_value, k.item_label, k.sort_order, 1
FROM sys_dict d
CROSS JOIN (
  SELECT 'DRAFT' item_key, 'DRAFT' item_value, '草稿' item_label, 1 sort_order UNION ALL
  SELECT 'PENDING', 'PENDING', '待审批', 2 UNION ALL
  SELECT 'APPROVED', 'APPROVED', '已通过', 3 UNION ALL
  SELECT 'REJECTED', 'REJECTED', '已拒绝', 4 UNION ALL
  SELECT 'WITHDRAWN', 'WITHDRAWN', '已撤回', 5
) k
WHERE d.dict_code = 'CATALOG_APPROVAL_STATUS'
  AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id = d.id AND i.item_key = k.item_key);

INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, k.item_key, k.item_value, k.item_label, k.sort_order, 1
FROM sys_dict d
CROSS JOIN (
  SELECT 'REALTIME' item_key, 'REALTIME' item_value, '实时' item_label, 1 sort_order UNION ALL
  SELECT 'DAILY', 'DAILY', '每日', 2 UNION ALL
  SELECT 'WEEKLY', 'WEEKLY', '每周', 3 UNION ALL
  SELECT 'MONTHLY', 'MONTHLY', '每月', 4 UNION ALL
  SELECT 'YEARLY', 'YEARLY', '每年', 5
) k
WHERE d.dict_code = 'CATALOG_UPDATE_CYCLE'
  AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id = d.id AND i.item_key = k.item_key);

INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'DATA', 'DATA', '数据资源', 1, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_RESOURCE_TYPE'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='DATA');
INSERT INTO sys_dict_item (dict_id, item_key, item_value, item_label, sort_order, status)
SELECT d.id, 'SERVICE', 'SERVICE', '服务资源', 2, 1 FROM sys_dict d WHERE d.dict_code='CATALOG_RESOURCE_TYPE'
AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_id=d.id AND i.item_key='SERVICE');

-- 4) 菜单由前端 Hub 挂载；权限码预留（可选授权）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7125, COALESCE(p.id, 0), '数据资源分类', 2, '/governance?tab=catalog&cSub=classify', NULL,
       'hub:governance:catalog:classify', NULL, 15, 'M112C', 'hub', 1, 0
FROM (SELECT 1) dummy
LEFT JOIN (SELECT id FROM sys_menu WHERE path LIKE '/governance%' ORDER BY id LIMIT 1) p ON 1=1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7125 OR permission = 'hub:governance:catalog:classify');
