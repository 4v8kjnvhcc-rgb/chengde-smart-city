-- 确保政务事项内置数据（国家/河北/承德）存在
INSERT INTO biz_gov_matter (matter_code, matter_name, matter_type, region_scope, status, sort_order, created_by) VALUES
('NATION-001', '结婚登记', '户籍婚姻', 'NATIONAL', 'ACTIVE', 1, 'sys_admin'),
('NATION-002', '出生医学证明签发', '户籍婚姻', 'NATIONAL', 'ACTIVE', 2, 'sys_admin'),
('NATION-003', '居民身份证申领', '证件证照', 'NATIONAL', 'ACTIVE', 3, 'sys_admin'),
('NATION-004', '户口迁移', '户籍婚姻', 'NATIONAL', 'ACTIVE', 4, 'sys_admin'),
('HEBEI-001', '居住证办理', '证件证照', 'PROVINCE', 'ACTIVE', 10, 'sys_admin'),
('HEBEI-002', '社保卡申领', '社会保障', 'PROVINCE', 'ACTIVE', 11, 'sys_admin'),
('HEBEI-003', '不动产登记查询', '不动产', 'PROVINCE', 'ACTIVE', 12, 'sys_admin'),
('HEBEI-004', '公积金缴存证明', '社会保障', 'PROVINCE', 'ACTIVE', 13, 'sys_admin'),
('CD-001', '高新技术企业认定', '企业服务', 'CITY', 'ACTIVE', 20, 'sys_admin'),
('CD-002', '建设项目规划许可', '工程建设', 'CITY', 'ACTIVE', 21, 'sys_admin'),
('CD-003', '公共场所卫生许可', '卫生健康', 'CITY', 'ACTIVE', 22, 'sys_admin'),
('CD-004', '社会救助证明开具', '社会救助', 'CITY', 'ACTIVE', 23, 'sys_admin'),
('CD-005', '排污许可证核发', '生态环境', 'CITY', 'ACTIVE', 24, 'sys_admin')
ON DUPLICATE KEY UPDATE
  matter_name = VALUES(matter_name),
  matter_type = VALUES(matter_type),
  region_scope = VALUES(region_scope),
  status = 'ACTIVE',
  sort_order = VALUES(sort_order);
