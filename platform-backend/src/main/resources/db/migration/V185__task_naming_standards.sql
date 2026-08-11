-- 内置任务命名规范（数据标准体系 · 命名规范模块）
INSERT INTO gov_naming_standard (naming_type, naming_name, standard_content, description, status, created_by)
SELECT 'TASK', '归集任务', 't_gj_{targetTable}', '归集任务：t_gj_+目标表表名', 'ACTIVE', 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM gov_naming_standard WHERE naming_type = 'TASK' AND naming_name = '归集任务'
);

INSERT INTO gov_naming_standard (naming_type, naming_name, standard_content, description, status, created_by)
SELECT 'TASK', '治理任务', 't_zl_{targetTable}', '治理任务：t_zl_+目标表表名', 'ACTIVE', 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM gov_naming_standard WHERE naming_type = 'TASK' AND naming_name = '治理任务'
);

INSERT INTO gov_naming_standard (naming_type, naming_name, standard_content, description, status, created_by)
SELECT 'TASK', '融合任务', 't_rh_{targetTable}', '融合任务：t_rh_+目标表表名', 'ACTIVE', 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM gov_naming_standard WHERE naming_type = 'TASK' AND naming_name = '融合任务'
);

INSERT INTO gov_naming_standard (naming_type, naming_name, standard_content, description, status, created_by)
SELECT 'TASK', '元数据任务', 't_meta_{dataSourceName}{seq}', '元数据任务：t_meta_+数据源名称+三位顺序号', 'ACTIVE', 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM gov_naming_standard WHERE naming_type = 'TASK' AND naming_name = '元数据任务'
);

INSERT INTO gov_naming_standard (naming_type, naming_name, standard_content, description, status, created_by)
SELECT 'TASK', '质量任务', 't_q_{dataSourceName}{seq}', '质量任务：t_q_+数据源名称+三位顺序号', 'ACTIVE', 'system'
WHERE NOT EXISTS (
  SELECT 1 FROM gov_naming_standard WHERE naming_type = 'TASK' AND naming_name = '质量任务'
);
