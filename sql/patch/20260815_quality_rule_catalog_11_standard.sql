-- 校验规则类型对齐目标截图：仅保留 11 类标准目录，排序 1～11，清理旧 QR_* / 临时规则
-- 目标库: smart_city；可重复执行（与 QualityRuleService.alignStandardCatalog 口径一致）

-- 解除引用后删除非标准规则
UPDATE gov_quality_model_rule mr
JOIN gov_quality_rule r ON r.id = mr.rule_catalog_id
SET mr.rule_catalog_id = NULL
WHERE r.rule_code NOT IN (
  'NullValueCheck','RangeCheck','StandardInspection','JavaScript','RecordCount',
  'Uniqueness','Accuracy','DataFluctuation','Consistency','LogicCheck','CustomRule'
);

DELETE FROM gov_quality_task_detail
WHERE rule_id IN (
  SELECT id FROM (
    SELECT id FROM gov_quality_rule
    WHERE rule_code NOT IN (
      'NullValueCheck','RangeCheck','StandardInspection','JavaScript','RecordCount',
      'Uniqueness','Accuracy','DataFluctuation','Consistency','LogicCheck','CustomRule'
    )
  ) t
);

DELETE FROM gov_quality_rule_config
WHERE rule_id IN (
  SELECT id FROM (
    SELECT id FROM gov_quality_rule
    WHERE rule_code NOT IN (
      'NullValueCheck','RangeCheck','StandardInspection','JavaScript','RecordCount',
      'Uniqueness','Accuracy','DataFluctuation','Consistency','LogicCheck','CustomRule'
    )
  ) t
);

DELETE FROM gov_quality_rule
WHERE rule_code NOT IN (
  'NullValueCheck','RangeCheck','StandardInspection','JavaScript','RecordCount',
  'Uniqueness','Accuracy','DataFluctuation','Consistency','LogicCheck','CustomRule'
);

-- 补齐 / 更新 11 类
INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'NullValueCheck', '空值检查', 'COMPLETENESS', 1, '用于检查字段是否为空', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'NullValueCheck');

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'RangeCheck', '值域检查', 'ACCURACY', 2, '用于检查关键指标取值范围', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'RangeCheck');

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'StandardInspection', '规范检查', 'ACCURACY', 3, '用于检查字符型字段的格式是否规范', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'StandardInspection');

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'JavaScript', 'Java脚本', 'ACCURACY', 4, '用于执行Java脚本检查数据', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'JavaScript');

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'RecordCount', '记录数', 'COMPLETENESS', 5, '核查数据总量，校验条数完整性与缺失、冗余情况', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'RecordCount');

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'Uniqueness', '唯一性', 'UNIQUENESS', 6, '校验关键字段，排查重复数据与重复录入问题', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'Uniqueness');

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'Accuracy', '准确性', 'ACCURACY', 7, '核对数据内容，确保数值、文本符合真实业务', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'Accuracy');

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'DataFluctuation', '波动', 'TIMELINESS', 8, '监控数据变化，识别异常增减、突发等不合理情况', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'DataFluctuation');

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'Consistency', '一致性', 'CONSISTENCY', 9, '比对关联数据，保障多表多源口径、格式统一', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'Consistency');

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'LogicCheck', '逻辑性', 'ACCURACY', 10, '校验业务规则，判断数据间关联关系是否合理', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'LogicCheck');

INSERT INTO gov_quality_rule (rule_code, rule_name, rule_type, sort_no, description, status, created_by, created_at, updated_at)
SELECT 'CustomRule', '自定义', 'ACCURACY', 11, '适配业务场景，按需配置专项精度校验规则', 'ENABLED', 'sys_admin', NOW(), NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_rule WHERE rule_code = 'CustomRule');

UPDATE gov_quality_rule SET rule_name='空值检查', rule_type='COMPLETENESS', sort_no=1,
  description='用于检查字段是否为空', status='ENABLED', updated_at=NOW() WHERE rule_code='NullValueCheck';
UPDATE gov_quality_rule SET rule_name='值域检查', rule_type='ACCURACY', sort_no=2,
  description='用于检查关键指标取值范围', status='ENABLED', updated_at=NOW() WHERE rule_code='RangeCheck';
UPDATE gov_quality_rule SET rule_name='规范检查', rule_type='ACCURACY', sort_no=3,
  description='用于检查字符型字段的格式是否规范', status='ENABLED', updated_at=NOW() WHERE rule_code='StandardInspection';
UPDATE gov_quality_rule SET rule_name='Java脚本', rule_type='ACCURACY', sort_no=4,
  description='用于执行Java脚本检查数据', status='ENABLED', updated_at=NOW() WHERE rule_code='JavaScript';
UPDATE gov_quality_rule SET rule_name='记录数', rule_type='COMPLETENESS', sort_no=5,
  description='核查数据总量，校验条数完整性与缺失、冗余情况', status='ENABLED', updated_at=NOW() WHERE rule_code='RecordCount';
UPDATE gov_quality_rule SET rule_name='唯一性', rule_type='UNIQUENESS', sort_no=6,
  description='校验关键字段，排查重复数据与重复录入问题', status='ENABLED', updated_at=NOW() WHERE rule_code='Uniqueness';
UPDATE gov_quality_rule SET rule_name='准确性', rule_type='ACCURACY', sort_no=7,
  description='核对数据内容，确保数值、文本符合真实业务', status='ENABLED', updated_at=NOW() WHERE rule_code='Accuracy';
UPDATE gov_quality_rule SET rule_name='波动', rule_type='TIMELINESS', sort_no=8,
  description='监控数据变化，识别异常增减、突发等不合理情况', status='ENABLED', updated_at=NOW() WHERE rule_code='DataFluctuation';
UPDATE gov_quality_rule SET rule_name='一致性', rule_type='CONSISTENCY', sort_no=9,
  description='比对关联数据，保障多表多源口径、格式统一', status='ENABLED', updated_at=NOW() WHERE rule_code='Consistency';
UPDATE gov_quality_rule SET rule_name='逻辑性', rule_type='ACCURACY', sort_no=10,
  description='校验业务规则，判断数据间关联关系是否合理', status='ENABLED', updated_at=NOW() WHERE rule_code='LogicCheck';
UPDATE gov_quality_rule SET rule_name='自定义', rule_type='ACCURACY', sort_no=11,
  description='适配业务场景，按需配置专项精度校验规则', status='ENABLED', updated_at=NOW() WHERE rule_code='CustomRule';
