-- V217: 补种「法人大数据支撑系统」指标域（与人口/宏观/重点并列）
-- 治理平台「数据指标」当前按 owner=population 列表；与已有宏观/重点台账一致挂在 population 下，
-- 便于指标域管理一页四类业务支撑系统齐套。物理结果库仍按 domain_db_name 在 INDICATOR_DB_* 主机建库。

INSERT INTO ana_indicator_domain (owner_domain_code, domain_name, domain_db_name, remark, status, created_by)
SELECT 'population', '法人大数据支撑系统', 'ind_legal_entity_big_data_support_system', '新建', 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_indicator_domain WHERE domain_db_name = 'ind_legal_entity_big_data_support_system'
);
