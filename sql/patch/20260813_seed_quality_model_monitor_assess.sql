-- 汇聚质量：1 条质量模型 + 对应监控/评估数据（可重复执行）
-- 目标库：smart_city；无「演示」字样

-- 清理旧种子（按业务名称）
DELETE sr FROM gov_quality_scheme_rule sr
  INNER JOIN gov_quality_scheme s ON s.id = sr.scheme_id
WHERE s.scheme_name = '人口基础信息日稽核方案';

DELETE FROM gov_quality_scheme WHERE scheme_name = '人口基础信息日稽核方案';

DELETE i FROM gov_quality_issue i
  INNER JOIN gov_quality_task t ON t.id = i.task_id
WHERE t.task_name = '人口基础信息日稽核';

DELETE r FROM gov_quality_task_run r
  INNER JOIN gov_quality_task t ON t.id = r.task_id
WHERE t.task_name = '人口基础信息日稽核';

DELETE d FROM gov_quality_task_detail d
  INNER JOIN gov_quality_task t ON t.id = d.task_id
WHERE t.task_name = '人口基础信息日稽核';

DELETE FROM gov_quality_task WHERE task_name = '人口基础信息日稽核';

DELETE mr FROM gov_quality_model_rule mr
  INNER JOIN gov_quality_model m ON m.id = mr.model_id
WHERE m.model_name = '人口基础信息汇聚质量模型';

DELETE mt FROM gov_quality_model_table mt
  INNER JOIN gov_quality_model m ON m.id = mt.model_id
WHERE m.model_name = '人口基础信息汇聚质量模型';

DELETE FROM gov_quality_model WHERE model_name = '人口基础信息汇聚质量模型';

-- 1) 质量模型（质量规则配置 · 质量模型管理）
INSERT INTO gov_quality_model (
  model_name, datasource_id, datasource_name, description, status, created_by, created_at, updated_at
) VALUES (
  '人口基础信息汇聚质量模型',
  -1,
  '平台 ODS（贴源/汇聚）',
  '对汇聚层人口基础表做非空与唯一性校验，支撑直通共享前质量门禁。',
  'ENABLED',
  'sys_admin',
  '2025-11-08 10:20:00',
  '2025-11-18 09:15:00'
);
SET @model_id = LAST_INSERT_ID();

INSERT INTO gov_quality_model_table (model_id, table_name, table_comment, sort_no, created_at)
VALUES (@model_id, 'ods_pop_basic_info', '人口基础信息表', 1, '2025-11-08 10:21:00');
SET @model_table_id = LAST_INSERT_ID();

INSERT INTO gov_quality_model_rule (
  model_id, model_table_id, rule_catalog_id, rule_type_code, rule_type_name, rule_name,
  table_name, field_names, remark, check_type, config_json, threshold, status, created_by, created_at, updated_at
) VALUES
(
  @model_id, @model_table_id, 1, 'NULL_CHECK', '空值检查', '身份证号非空检查',
  'ods_pop_basic_info', 'id_card_no', '证件号码不得为空', 'NULL_CHECK',
  '{"nullable":false}', 0.0000, 'ENABLED', 'sys_admin', '2025-11-08 10:25:00', '2025-11-08 10:25:00'
),
(
  @model_id, @model_table_id, 6, 'UNIQUENESS', '唯一性检查', '身份证号唯一性检查',
  'ods_pop_basic_info', 'id_card_no', '同一证件号不得重复登记', 'UNIQUENESS',
  '{"distinct":true}', 0.0000, 'ENABLED', 'sys_admin', '2025-11-08 10:26:00', '2025-11-08 10:26:00'
);
SET @model_rule_id = (SELECT id FROM gov_quality_model_rule WHERE model_id=@model_id AND rule_name='身份证号非空检查' LIMIT 1);
SET @model_rule_id2 = (SELECT id FROM gov_quality_model_rule WHERE model_id=@model_id AND rule_name='身份证号唯一性检查' LIMIT 1);

-- 2) 质量任务（评估页列表 + 监控运行归属）
INSERT INTO gov_quality_task (
  task_name, description, rule_id, status, last_run_at, last_score, last_message,
  schedule_type, cron_expr, datasource_id, metadata_entry_code, created_by, created_at, updated_at
) VALUES (
  '人口基础信息日稽核',
  '每日对 ODS 人口基础信息表执行非空与唯一性稽核。',
  1,
  'READY',
  DATE_FORMAT(NOW(), '%Y-%m-%d 06:30:00'),
  96.50,
  '稽核完成：2 项检查，发现空值 12 条、重复证件号 3 组',
  'CRON',
  '0 30 6 * * ?',
  -1,
  NULL,
  'sys_admin',
  '2025-11-08 11:00:00',
  NOW()
);
SET @task_id = LAST_INSERT_ID();

INSERT INTO gov_quality_task_detail (
  task_id, rule_id, target_table, target_column, check_type, sort_order, status, created_at, updated_at
) VALUES
(@task_id, 1, 'ods_pop_basic_info', 'id_card_no', 'NULL_CHECK', 1, 'ENABLED', '2025-11-08 11:01:00', '2025-11-08 11:01:00'),
(@task_id, 6, 'ods_pop_basic_info', 'id_card_no', 'UNIQUENESS', 2, 'ENABLED', '2025-11-08 11:01:00', '2025-11-08 11:01:00');

-- 3) 运行记录 + 问题（质量监控）
INSERT INTO gov_quality_task_run (
  task_id, status, started_at, ended_at, score, total_checks, issue_count, message, triggered_by, created_at
) VALUES (
  @task_id,
  'SUCCESS',
  DATE_FORMAT(NOW(), '%Y-%m-%d 06:30:12'),
  DATE_FORMAT(NOW(), '%Y-%m-%d 06:31:48'),
  96.50,
  2,
  2,
  '稽核完成：发现空值 12 条、重复证件号 3 组',
  'SCHEDULE',
  NOW()
);
SET @run_id = LAST_INSERT_ID();

-- 再补一条昨日成功记录，便于趋势
INSERT INTO gov_quality_task_run (
  task_id, status, started_at, ended_at, score, total_checks, issue_count, message, triggered_by, created_at
) VALUES (
  @task_id,
  'SUCCESS',
  DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y-%m-%d 06:30:08'),
  DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y-%m-%d 06:31:22'),
  97.20,
  2,
  1,
  '稽核完成：发现空值 8 条',
  'SCHEDULE',
  DATE_SUB(NOW(), INTERVAL 1 DAY)
);

INSERT INTO gov_quality_issue (
  run_id, task_id, rule_id, detail_id, check_type, target_table, target_column,
  issue_type, issue_value, issue_count, sample_data, severity, status, created_at
) VALUES
(
  @run_id, @task_id, 1, NULL, 'NULL_CHECK', 'ods_pop_basic_info', 'id_card_no',
  'NULL', '身份证号为空', 12,
  '[{"name":"王*","id_card_no":null},{"name":"李*","id_card_no":""}]',
  'HIGH', 'OPEN', NOW()
),
(
  @run_id, @task_id, 6, NULL, 'UNIQUENESS', 'ods_pop_basic_info', 'id_card_no',
  'DUPLICATE', '130800********1234', 3,
  '[{"id_card_no":"130800********1234","cnt":3}]',
  'MEDIUM', 'OPEN', NOW()
);

-- 4) 质量方案（监控页「已发布方案」需 generate_status=SUCCESS）
INSERT INTO gov_quality_scheme (
  scheme_name, model_id, model_name, executor_address, cron_expr, cycle_name, description,
  schedule_status, generate_status, exec_status, last_exec_at, last_message,
  quality_task_id, created_by, created_at, updated_at
) VALUES (
  '人口基础信息日稽核方案',
  @model_id,
  '人口基础信息汇聚质量模型',
  'default',
  '0 30 6 * * ?',
  '每日',
  '绑定人口基础信息汇聚质量模型，每日 06:30 执行。',
  'RUNNING',
  'SUCCESS',
  'SUCCESS',
  DATE_FORMAT(NOW(), '%Y-%m-%d 06:31:48'),
  '调度执行成功，得分 96.50',
  @task_id,
  'sys_admin',
  '2025-11-08 11:10:00',
  NOW()
);
SET @scheme_id = LAST_INSERT_ID();

INSERT INTO gov_quality_scheme_rule (
  scheme_id, model_rule_id, rule_type_name, rule_name, table_name, field_names, check_type, sort_no, created_at
) VALUES
(@scheme_id, @model_rule_id, '空值检查', '身份证号非空检查', 'ods_pop_basic_info', 'id_card_no', 'NULL_CHECK', 1, '2025-11-08 11:11:00'),
(@scheme_id, @model_rule_id2, '唯一性检查', '身份证号唯一性检查', 'ods_pop_basic_info', 'id_card_no', 'UNIQUENESS', 2, '2025-11-08 11:11:00');

-- 验收
SELECT id, model_name, datasource_name, status FROM gov_quality_model WHERE id=@model_id;
SELECT id, task_name, status, last_score, schedule_type FROM gov_quality_task WHERE id=@task_id;
SELECT id, scheme_name, generate_status, schedule_status, exec_status, quality_task_id
FROM gov_quality_scheme WHERE id=@scheme_id;
SELECT id, status, score, issue_count, started_at FROM gov_quality_task_run WHERE task_id=@task_id ORDER BY id;
SELECT id, check_type, target_column, issue_type, issue_count, severity FROM gov_quality_issue WHERE run_id=@run_id;
