-- 手工补丁：四类业务支撑系统各补一条示范指标组（与 Flyway V218 对齐）
-- 目标库：smart_city（控制面）
-- 可重复执行
-- 执行顺序：建议先有指标域（含法人 V217 / 本脚本内幂等补种）

-- 确保法人指标域存在
INSERT INTO ana_indicator_domain (owner_domain_code, domain_name, domain_db_name, remark, status, created_by)
SELECT 'population', '法人大数据支撑系统', 'ind_legal_entity_big_data_support_system', '新建', 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_indicator_domain WHERE domain_db_name = 'ind_legal_entity_big_data_support_system'
);

INSERT INTO ana_indicator_group (
  owner_domain_code, indicator_domain_id, group_name, target_table,
  group_category, model_method, description, status, created_by, created_at, updated_at
)
SELECT d.owner_domain_code, d.id, '人口示范指标组', 'ind_population_seed_group',
       'UNIT', 'SQL', '系统种子：人口大数据支撑系统', 'DRAFT', 'sys_admin', NOW(), NOW()
FROM ana_indicator_domain d
WHERE d.status = 'ACTIVE'
  AND d.domain_db_name = 'ind_population_big_data_support_system'
  AND NOT EXISTS (
    SELECT 1 FROM ana_indicator_group g
    WHERE g.indicator_domain_id = d.id AND g.status <> 'INACTIVE'
  )
  AND NOT EXISTS (
    SELECT 1 FROM ana_indicator_group g WHERE g.target_table = 'ind_population_seed_group'
  );

INSERT INTO ana_indicator_group (
  owner_domain_code, indicator_domain_id, group_name, target_table,
  group_category, model_method, description, status, created_by, created_at, updated_at
)
SELECT d.owner_domain_code, d.id, '法人示范指标组', 'ind_legal_entity_seed_group',
       'UNIT', 'SQL', '系统种子：法人大数据支撑系统', 'DRAFT', 'sys_admin', NOW(), NOW()
FROM ana_indicator_domain d
WHERE d.status = 'ACTIVE'
  AND d.domain_db_name = 'ind_legal_entity_big_data_support_system'
  AND NOT EXISTS (
    SELECT 1 FROM ana_indicator_group g
    WHERE g.indicator_domain_id = d.id AND g.status <> 'INACTIVE'
  )
  AND NOT EXISTS (
    SELECT 1 FROM ana_indicator_group g WHERE g.target_table = 'ind_legal_entity_seed_group'
  );

INSERT INTO ana_indicator_group (
  owner_domain_code, indicator_domain_id, group_name, target_table,
  group_category, model_method, description, status, created_by, created_at, updated_at
)
SELECT d.owner_domain_code, d.id, '宏观示范指标组', 'ind_macro_economy_seed_group',
       'UNIT', 'SQL', '系统种子：宏观经济及工业运行大数据支撑系统', 'DRAFT', 'sys_admin', NOW(), NOW()
FROM ana_indicator_domain d
WHERE d.status = 'ACTIVE'
  AND (
    d.domain_db_name = 'ind_cde'
    OR d.domain_name LIKE '%宏观经济%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM ana_indicator_group g
    WHERE g.indicator_domain_id = d.id AND g.status <> 'INACTIVE'
  )
  AND NOT EXISTS (
    SELECT 1 FROM ana_indicator_group g WHERE g.target_table = 'ind_macro_economy_seed_group'
  )
LIMIT 1;

INSERT INTO ana_indicator_group (
  owner_domain_code, indicator_domain_id, group_name, target_table,
  group_category, model_method, description, status, created_by, created_at, updated_at
)
SELECT d.owner_domain_code, d.id, '重点领域示范指标组', 'ind_key_domain_seed_group',
       'UNIT', 'SQL', '系统种子：重点领域示范应用支撑系统', 'DRAFT', 'sys_admin', NOW(), NOW()
FROM ana_indicator_domain d
WHERE d.status = 'ACTIVE'
  AND (
    d.domain_db_name = 'ind_efg'
    OR d.domain_name LIKE '%重点领域%'
  )
  AND NOT EXISTS (
    SELECT 1 FROM ana_indicator_group g
    WHERE g.indicator_domain_id = d.id AND g.status <> 'INACTIVE'
  )
  AND NOT EXISTS (
    SELECT 1 FROM ana_indicator_group g WHERE g.target_table = 'ind_key_domain_seed_group'
  )
LIMIT 1;
