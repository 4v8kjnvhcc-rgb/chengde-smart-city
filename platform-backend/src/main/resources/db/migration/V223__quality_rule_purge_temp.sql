-- 质量规则目录对齐：清理临时压测规则名（空值率规则_* / 阈值101_* / 临时规则*）
-- 标准 11 类由应用 POST /governance/quality/rule-mgmt/align-standard 补齐并重排；
-- 本脚本仅做可重复的 DML 清理，避免启动时误删业务自定义规则。

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
