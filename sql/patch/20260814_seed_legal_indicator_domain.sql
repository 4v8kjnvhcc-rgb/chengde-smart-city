-- 手工补丁：补种「法人大数据支撑系统」指标域（与 Flyway V217 对齐）
-- 目标库：smart_city（控制面）
-- 可重复执行：INSERT WHERE NOT EXISTS
-- 说明：物理库 ind_legal_entity_big_data_support_system 在指标任务「执行」时
--       由 app.indicator-db / INDICATOR_DB_* 连接主机幂等 CREATE DATABASE

INSERT INTO ana_indicator_domain (owner_domain_code, domain_name, domain_db_name, remark, status, created_by)
SELECT 'population', '法人大数据支撑系统', 'ind_legal_entity_big_data_support_system', '新建', 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_indicator_domain WHERE domain_db_name = 'ind_legal_entity_big_data_support_system'
);
