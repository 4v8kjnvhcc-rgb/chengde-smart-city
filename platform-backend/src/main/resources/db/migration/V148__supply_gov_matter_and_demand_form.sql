-- 政务事项管理 + 需求表单扩展（草稿/撤销待提交）
CREATE TABLE IF NOT EXISTS biz_gov_matter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  matter_code VARCHAR(64) NOT NULL COMMENT '事项编码',
  matter_name VARCHAR(256) NOT NULL COMMENT '事项名称',
  matter_type VARCHAR(64) NOT NULL DEFAULT 'OTHER' COMMENT '事项类型',
  region_scope VARCHAR(32) NOT NULL DEFAULT 'CITY' COMMENT 'NATIONAL/PROVINCE/CITY',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  sort_order INT NOT NULL DEFAULT 0,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_matter_code (matter_code),
  KEY idx_matter_name (matter_name),
  KEY idx_matter_type (matter_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO biz_gov_matter (matter_code, matter_name, matter_type, region_scope, sort_order, created_by) VALUES
('NATION-001', '结婚登记', '户籍婚姻', 'NATIONAL', 1, 'sys_admin'),
('NATION-002', '出生医学证明签发', '户籍婚姻', 'NATIONAL', 2, 'sys_admin'),
('NATION-003', '居民身份证申领', '证件证照', 'NATIONAL', 3, 'sys_admin'),
('HEBEI-001', '居住证办理', '证件证照', 'PROVINCE', 10, 'sys_admin'),
('HEBEI-002', '社保卡申领', '社会保障', 'PROVINCE', 11, 'sys_admin'),
('HEBEI-003', '不动产登记查询', '不动产', 'PROVINCE', 12, 'sys_admin'),
('CD-001', '高新技术企业认定', '企业服务', 'CITY', 20, 'sys_admin'),
('CD-002', '建设项目规划许可', '工程建设', 'CITY', 21, 'sys_admin'),
('CD-003', '公共场所卫生许可', '卫生健康', 'CITY', 22, 'sys_admin'),
('CD-004', '社会救助证明开具', '社会救助', 'CITY', 23, 'sys_admin')
ON DUPLICATE KEY UPDATE matter_name = VALUES(matter_name), matter_type = VALUES(matter_type);

ALTER TABLE biz_data_demand
  ADD COLUMN form_payload TEXT NULL COMMENT '需求填报表单JSON' AFTER analysis_payload;

ALTER TABLE biz_demand_template
  MODIFY COLUMN field_schema TEXT NULL COMMENT '结构化字段模型JSON';
