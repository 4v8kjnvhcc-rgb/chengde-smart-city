-- 数据分级分类：1 条真实业务口径标注（可重复执行）
-- 目标库：smart_city；页面：数据资产管理 > 数据分级分类

-- 去掉明显脏数据标注
DELETE FROM ing_cls_audit_log WHERE asset_type='TABLE' AND asset_id IN (4, 10086);
DELETE FROM ing_cls_asset_mark
WHERE (asset_type='TABLE' AND asset_id=4 AND asset_name='category')
   OR asset_code='TBL_CATEGORY'
   OR (asset_type='TABLE' AND asset_id=10086);

INSERT INTO ing_cls_asset_mark (
  asset_type, asset_id, asset_code, asset_name, category_id, level_code,
  grade_basis, grade_reason, features_json, version_no, graded_by, graded_at,
  status, create_time, update_time
) VALUES (
  'TABLE',
  10086,
  'ODS_POP_BASIC_INFO',
  '人口基础信息表',
  (SELECT id FROM ing_cls_category WHERE category_code='BIZ_POP' LIMIT 1),
  'IMPORTANT',
  'REVIEWED',
  '含公民身份号码、联系电话、户籍地址等个人信息，涉及自然人身份标识；按《数据安全法》及 GB/T 43697-2024 定为重要数据，共享须脱敏并经二级审批。',
  '{"personalInfoScore":8,"businessCriticalScore":7,"leakImpactScore":8,"contentAttrs":["身份标识","联系方式"],"sourceSystem":"公安人口库对接","dataLayer":"ODS","ownerOrg":"高新公安分局"}',
  1,
  '王志强',
  '2025-10-16 14:28:00',
  'ACTIVE',
  '2025-10-16 14:28:00',
  '2025-10-16 15:06:00'
);

SET @mark_id = LAST_INSERT_ID();

INSERT INTO ing_cls_audit_log (
  mark_id, asset_type, asset_id, change_type, before_json, after_json,
  operator_id, operator_name, created_at
) VALUES (
  @mark_id,
  'TABLE',
  10086,
  'CREATE',
  NULL,
  '{"assetCode":"ODS_POP_BASIC_INFO","assetName":"人口基础信息表","levelCode":"IMPORTANT","category":"人口主题","gradeBasis":"REVIEWED"}',
  NULL,
  '王志强',
  '2025-10-16 14:28:00'
);

SELECT m.id, m.asset_code, m.asset_name, c.category_name, m.level_code, m.grade_basis, m.graded_by, m.graded_at
FROM ing_cls_asset_mark m
LEFT JOIN ing_cls_category c ON c.id = m.category_id
WHERE m.asset_code = 'ODS_POP_BASIC_INFO';
