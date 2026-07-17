-- 独立业务源库（模拟部门真实库），与 smart_city / openmetadata_db 完全隔离。
-- 特意保留脏数据：空值、重复主键值、非法码值，用于验证 JDBC 探测与质量闭环。
-- 表结构字段名与平台 ODS 目标表不同，强制走字段映射，体现真实异构。

CREATE DATABASE IF NOT EXISTS biz_source CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE biz_source;

-- 只读探测账号（供平台登记探库使用，仅授予 biz_source 的 SELECT）
CREATE USER IF NOT EXISTS 'probe'@'%' IDENTIFIED BY 'probe_pass';
GRANT SELECT ON biz_source.* TO 'probe'@'%';
FLUSH PRIVILEGES;

-- ============ 企业主体表（异构字段名）============
DROP TABLE IF EXISTS ent_master;
CREATE TABLE ent_master (
  ent_id       BIGINT PRIMARY KEY AUTO_INCREMENT,
  credit_code  VARCHAR(32)  NOT NULL COMMENT '统一社会信用代码（源库允许重复，制造脏数据）',
  ent_name     VARCHAR(256) NULL     COMMENT '企业名称',
  reg_capital  DECIMAL(18,2) NULL    COMMENT '注册资本(万元)',
  industry     VARCHAR(64)  NULL     COMMENT '行业代码',
  area_code    VARCHAR(12)  NULL     COMMENT '行政区划代码',
  updated_time DATETIME     NULL     COMMENT '源系统更新时间',
  KEY idx_ent_credit (credit_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业主体源表';

INSERT INTO ent_master (credit_code, ent_name, reg_capital, industry, area_code, updated_time) VALUES
  ('91130800P000000001', '承德示范科技有限公司', 1000.00, 'I65', '130802', NOW()),
  ('91130800P000000002', '承德示范制造有限公司', 2500.00, 'C39', '130803', NOW()),
  ('91130800P000000003', NULL,                    500.00,  'L72', '130804', NOW()),          -- 空值：企业名称缺失
  ('91130800P000000002', '承德示范制造有限公司(重复登记)', 2600.00, 'C39', '130803', NOW()), -- 重复：credit_code 重复
  ('INVALID_CODE_XX',    '非法码值测试企业',      -10.00, 'ZZ',  'BADAREA', NOW());           -- 非法：信用代码/区划/负资本

-- ============ 项目建设表（异构字段名，结构与企业表不同）============
DROP TABLE IF EXISTS proj_construction;
CREATE TABLE proj_construction (
  proj_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
  proj_no     VARCHAR(64)  NOT NULL COMMENT '项目编号',
  proj_name   VARCHAR(256) NULL     COMMENT '项目名称',
  budget      DECIMAL(18,2) NULL    COMMENT '预算(万元)',
  begin_date  DATE         NULL     COMMENT '开工日期',
  build_org   VARCHAR(128) NULL     COMMENT '建设单位',
  area_code   VARCHAR(12)  NULL     COMMENT '行政区划代码',
  status_code VARCHAR(16)  NULL     COMMENT '状态码 (ONGOING/DONE/PAUSED，含非法值)',
  KEY idx_proj_no (proj_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目建设源表';

INSERT INTO proj_construction (proj_no, proj_name, budget, begin_date, build_org, area_code, status_code) VALUES
  ('PRJ-2026-001', '智慧城市底座一期',   1200.00, '2026-01-15', '市大数据局', '130802', 'ONGOING'),
  ('PRJ-2026-002', '政务数据归集试点',    800.00, '2026-03-01', '市发改委',   '130803', 'DONE'),
  ('PRJ-2026-003', NULL,                  350.00, '2026-04-10', '市工信局',   '130804', 'PAUSED'),   -- 空值：项目名称缺失
  ('PRJ-2026-002', '政务数据归集试点(重复)', 810.00, '2026-03-02', '市发改委', '130803', 'DONE'),    -- 重复：proj_no 重复
  ('PRJ-2026-005', '非法状态码项目',      120.00, '2026-05-20', '市住建局',   '130811', 'XXX');       -- 非法：status_code 非枚举值
