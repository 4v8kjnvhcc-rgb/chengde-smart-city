-- 目标库: smart_city
-- 说明: 清理质量规则临时压测数据（与 Flyway V223 同内容）
-- 执行后建议在门户「校验规则类型」点击「对齐标准目录」补齐 11 类并重排

DELETE FROM gov_quality_rule_config
WHERE rule_id IN (
  SELECT id FROM (
    SELECT id FROM gov_quality_rule
    WHERE rule_name LIKE '空值率规则_%'
       OR rule_name LIKE '阈值101_%'
       OR rule_name LIKE '临时规则%'
  ) t
);

UPDATE gov_quality_model_rule
SET rule_catalog_id = NULL
WHERE rule_catalog_id IN (
  SELECT id FROM (
    SELECT id FROM gov_quality_rule
    WHERE rule_name LIKE '空值率规则_%'
       OR rule_name LIKE '阈值101_%'
       OR rule_name LIKE '临时规则%'
  ) t
);

DELETE FROM gov_quality_task_detail
WHERE rule_id IN (
  SELECT id FROM (
    SELECT id FROM gov_quality_rule
    WHERE rule_name LIKE '空值率规则_%'
       OR rule_name LIKE '阈值101_%'
       OR rule_name LIKE '临时规则%'
  ) t
);

DELETE FROM gov_quality_rule
WHERE rule_name LIKE '空值率规则_%'
   OR rule_name LIKE '阈值101_%'
   OR rule_name LIKE '临时规则%';
