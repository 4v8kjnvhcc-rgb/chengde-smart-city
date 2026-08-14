-- 手工补丁：四类业务支撑系统指标域/组/任务归属对齐（与 Flyway V219 一致）
-- 目标库：smart_city（控制面）；可重复执行

INSERT INTO ana_indicator_domain (owner_domain_code, domain_name, domain_db_name, remark, status, created_by)
SELECT 'legal', '法人大数据支撑系统', 'ind_legal_entity_big_data_support_system', '新建', 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_indicator_domain WHERE domain_db_name = 'ind_legal_entity_big_data_support_system'
);

INSERT INTO ana_indicator_domain (owner_domain_code, domain_name, domain_db_name, remark, status, created_by)
SELECT 'macro', '宏观经济及工业运行大数据支撑系统', 'ind_macro_economy_big_data_support_system', '新建', 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_indicator_domain
  WHERE domain_db_name IN ('ind_macro_economy_big_data_support_system', 'ind_cde')
     OR domain_name LIKE '%宏观经济%'
);

INSERT INTO ana_indicator_domain (owner_domain_code, domain_name, domain_db_name, remark, status, created_by)
SELECT 'key', '重点领域示范应用支撑系统', 'ind_key_domain_demo_support_system', '新建', 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_indicator_domain
  WHERE domain_db_name IN ('ind_key_domain_demo_support_system', 'ind_efg')
     OR domain_name LIKE '%重点领域%'
);

UPDATE ana_indicator_domain SET owner_domain_code = 'population', updated_at = NOW()
WHERE status = 'ACTIVE' AND (domain_db_name = 'ind_population_big_data_support_system' OR domain_name = '人口大数据支撑系统');

UPDATE ana_indicator_domain SET owner_domain_code = 'legal', updated_at = NOW()
WHERE status = 'ACTIVE' AND (domain_db_name = 'ind_legal_entity_big_data_support_system' OR domain_name = '法人大数据支撑系统');

UPDATE ana_indicator_domain SET owner_domain_code = 'macro', updated_at = NOW()
WHERE status = 'ACTIVE' AND (domain_db_name IN ('ind_cde', 'ind_macro_economy_big_data_support_system') OR domain_name LIKE '%宏观经济%');

UPDATE ana_indicator_domain SET owner_domain_code = 'key', updated_at = NOW()
WHERE status = 'ACTIVE' AND (domain_db_name IN ('ind_efg', 'ind_key_domain_demo_support_system') OR domain_name LIKE '%重点领域%');

UPDATE ana_indicator_group g
INNER JOIN ana_indicator_domain d ON d.id = g.indicator_domain_id
SET g.owner_domain_code = d.owner_domain_code, g.updated_at = NOW()
WHERE g.status <> 'INACTIVE';

UPDATE ana_indicator_task t
INNER JOIN ana_indicator_group g ON g.id = t.group_id
SET t.owner_domain_code = g.owner_domain_code, t.updated_at = NOW()
WHERE t.status = 'ACTIVE';

INSERT INTO ana_indicator_group (
  owner_domain_code, indicator_domain_id, group_name, target_table,
  group_category, model_method, description, status, created_by, created_at, updated_at
)
SELECT d.owner_domain_code, d.id, '人口示范指标组', 'ind_population_seed_group',
       'UNIT', 'SQL', '系统种子：人口大数据支撑系统', 'DRAFT', 'sys_admin', NOW(), NOW()
FROM ana_indicator_domain d
WHERE d.status = 'ACTIVE' AND d.owner_domain_code = 'population'
  AND d.domain_db_name = 'ind_population_big_data_support_system'
  AND NOT EXISTS (SELECT 1 FROM ana_indicator_group g WHERE g.indicator_domain_id = d.id AND g.status <> 'INACTIVE')
  AND NOT EXISTS (SELECT 1 FROM ana_indicator_group g WHERE g.target_table = 'ind_population_seed_group');

INSERT INTO ana_indicator_group (
  owner_domain_code, indicator_domain_id, group_name, target_table,
  group_category, model_method, description, status, created_by, created_at, updated_at
)
SELECT d.owner_domain_code, d.id, '法人示范指标组', 'ind_legal_entity_seed_group',
       'UNIT', 'SQL', '系统种子：法人大数据支撑系统', 'DRAFT', 'sys_admin', NOW(), NOW()
FROM ana_indicator_domain d
WHERE d.status = 'ACTIVE' AND d.owner_domain_code = 'legal'
  AND d.domain_db_name = 'ind_legal_entity_big_data_support_system'
  AND NOT EXISTS (SELECT 1 FROM ana_indicator_group g WHERE g.indicator_domain_id = d.id AND g.status <> 'INACTIVE')
  AND NOT EXISTS (SELECT 1 FROM ana_indicator_group g WHERE g.target_table = 'ind_legal_entity_seed_group');

INSERT INTO ana_indicator_group (
  owner_domain_code, indicator_domain_id, group_name, target_table,
  group_category, model_method, description, status, created_by, created_at, updated_at
)
SELECT d.owner_domain_code, d.id, '宏观示范指标组', 'ind_macro_economy_seed_group',
       'UNIT', 'SQL', '系统种子：宏观经济及工业运行大数据支撑系统', 'DRAFT', 'sys_admin', NOW(), NOW()
FROM ana_indicator_domain d
WHERE d.status = 'ACTIVE' AND d.owner_domain_code = 'macro'
  AND NOT EXISTS (SELECT 1 FROM ana_indicator_group g WHERE g.indicator_domain_id = d.id AND g.status <> 'INACTIVE')
  AND NOT EXISTS (SELECT 1 FROM ana_indicator_group g WHERE g.target_table = 'ind_macro_economy_seed_group')
LIMIT 1;

INSERT INTO ana_indicator_group (
  owner_domain_code, indicator_domain_id, group_name, target_table,
  group_category, model_method, description, status, created_by, created_at, updated_at
)
SELECT d.owner_domain_code, d.id, '重点领域示范指标组', 'ind_key_domain_seed_group',
       'UNIT', 'SQL', '系统种子：重点领域示范应用支撑系统', 'DRAFT', 'sys_admin', NOW(), NOW()
FROM ana_indicator_domain d
WHERE d.status = 'ACTIVE' AND d.owner_domain_code = 'key'
  AND NOT EXISTS (SELECT 1 FROM ana_indicator_group g WHERE g.indicator_domain_id = d.id AND g.status <> 'INACTIVE')
  AND NOT EXISTS (SELECT 1 FROM ana_indicator_group g WHERE g.target_table = 'ind_key_domain_seed_group')
LIMIT 1;
