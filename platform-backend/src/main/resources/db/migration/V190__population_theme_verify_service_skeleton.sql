-- 人口 B 余项骨架：主题表真源样例 + 校核台账 + 服务契约（LEDGER，非生产引擎）

-- ---------- 主题 / ADS ----------
CREATE TABLE IF NOT EXISTS dws_population_base (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  person_id VARCHAR(64) NOT NULL COMMENT '人员业务主键（脱敏演示）',
  name_masked VARCHAR(64) NULL,
  id_card_masked VARCHAR(32) NULL,
  gender VARCHAR(8) NULL,
  age INT NULL,
  age_band VARCHAR(32) NULL,
  education VARCHAR(32) NULL,
  hukou_type VARCHAR(32) NULL COMMENT '户籍/常住等',
  district_code VARCHAR(12) NULL,
  district_name VARCHAR(64) NULL,
  focus_tag VARCHAR(64) NULL COMMENT '重点人群标签，可空',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  fused_at DATETIME NULL,
  UNIQUE KEY uk_dws_pop_person (person_id),
  KEY idx_dws_pop_district (district_code),
  KEY idx_dws_pop_age_band (age_band)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人口主题库基础宽表（演示样例）';

CREATE TABLE IF NOT EXISTS ads_pop_district_summary (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  district_code VARCHAR(12) NOT NULL,
  district_name VARCHAR(64) NULL,
  stat_month CHAR(7) NOT NULL COMMENT 'YYYY-MM',
  hukou_count INT NOT NULL DEFAULT 0,
  resident_count INT NOT NULL DEFAULT 0,
  age60_count INT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ads_pop_dist_month (district_code, stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人口区县月汇总（ADS 演示）';

INSERT INTO dws_population_base
  (person_id, name_masked, id_card_masked, gender, age, age_band, education, hukou_type, district_code, district_name, focus_tag, status, fused_at)
VALUES
('P0001','张*','1308**********001','男',28,'25-34','本科','户籍','130802','双桥区',NULL,'ACTIVE',NOW()),
('P0002','李*','1308**********002','女',35,'35-44','大专','常住','130802','双桥区',NULL,'ACTIVE',NOW()),
('P0003','王*','1308**********003','男',62,'60+','初中','户籍','130803','双滦区',NULL,'ACTIVE',NOW()),
('P0004','赵*','1308**********004','女',7,'0-14','小学','户籍','130803','双滦区',NULL,'ACTIVE',NOW()),
('P0005','刘*','1308**********005','男',45,'45-59','高中','常住','130804','鹰手营子矿区','重点','ACTIVE',NOW()),
('P0006','陈*','1308**********006','女',22,'15-24','本科','户籍','130821','承德县',NULL,'ACTIVE',NOW()),
('P0007','杨*','1308**********007','男',55,'45-59','初中','户籍','130821','承德县',NULL,'ACTIVE',NOW()),
('P0008','黄*','1308**********008','女',31,'25-34','硕士','常住','130822','兴隆县',NULL,'ACTIVE',NOW()),
('P0009','周*','1308**********009','男',68,'60+','小学','户籍','130822','兴隆县',NULL,'ACTIVE',NOW()),
('P0010','吴*','1308**********010','女',19,'15-24','高中','户籍','130823','平泉市',NULL,'ACTIVE',NOW()),
('P0011','徐*','1308**********011','男',40,'35-44','本科','常住','130823','平泉市',NULL,'ACTIVE',NOW()),
('P0012','孙*','1308**********012','女',52,'45-59','大专','户籍','130824','滦平县','残疾','ACTIVE',NOW()),
('P0013','马*','1308**********013','男',9,'0-14','小学','户籍','130824','滦平县',NULL,'ACTIVE',NOW()),
('P0014','朱*','1308**********014','女',27,'25-34','本科','常住','130825','隆化县',NULL,'ACTIVE',NOW()),
('P0015','胡*','1308**********015','男',73,'60+','文盲','户籍','130825','隆化县',NULL,'ACTIVE',NOW()),
('P0016','郭*','1308**********016','女',33,'25-34','大专','户籍','130826','丰宁满族自治县',NULL,'ACTIVE',NOW()),
('P0017','何*','1308**********017','男',48,'45-59','高中','常住','130826','丰宁满族自治县','贫困','ACTIVE',NOW()),
('P0018','高*','1308**********018','女',16,'15-24','初中','户籍','130827','宽城满族自治县',NULL,'ACTIVE',NOW()),
('P0019','林*','1308**********019','男',36,'35-44','本科','户籍','130827','宽城满族自治县',NULL,'ACTIVE',NOW()),
('P0020','罗*','1308**********020','女',61,'60+','小学','常住','130828','围场满族蒙古族自治县',NULL,'ACTIVE',NOW()),
('P0021','郑*','1308**********021','男',24,'15-24','本科','户籍','130802','双桥区',NULL,'ACTIVE',NOW()),
('P0022','梁*','1308**********022','女',29,'25-34','硕士','常住','130803','双滦区',NULL,'ACTIVE',NOW()),
('P0023','谢*','1308**********023','男',41,'35-44','大专','户籍','130821','承德县','党员','ACTIVE',NOW()),
('P0024','宋*','1308**********024','女',58,'45-59','初中','常住','130828','围场满族蒙古族自治县',NULL,'ACTIVE',NOW())
ON DUPLICATE KEY UPDATE district_name = VALUES(district_name);

INSERT INTO ads_pop_district_summary (district_code, district_name, stat_month, hukou_count, resident_count, age60_count)
VALUES
('130802','双桥区','2026-07',18200,19500,3200),
('130803','双滦区','2026-07',12100,12800,2100),
('130804','鹰手营子矿区','2026-07',4800,5100,900),
('130821','承德县','2026-07',15600,16200,2800),
('130822','兴隆县','2026-07',14200,14900,2600),
('130823','平泉市','2026-07',16800,17500,3000),
('130824','滦平县','2026-07',13900,14500,2500),
('130825','隆化县','2026-07',15100,15800,2700),
('130826','丰宁满族自治县','2026-07',14800,15400,2650),
('130827','宽城满族自治县','2026-07',11200,11800,2000),
('130828','围场满族蒙古族自治县','2026-07',20100,21000,3800)
ON DUPLICATE KEY UPDATE resident_count = VALUES(resident_count);

-- ---------- 资源中心纳管 / 分区 LEDGER ----------
INSERT INTO rc_theme_library (theme_code, theme_name, library_kind, zone_code, partition_key, owner_org, description, status, created_by)
VALUES ('THEME_POPULATION', '人口主题库', 'THEME', 'ZONE_THEME', 'district_code', '公安/人口主管部门',
        '人口大数据支撑核心区权威主题（演示）', 'ACTIVE', 'sys_admin')
ON DUPLICATE KEY UPDATE theme_name = VALUES(theme_name), description = VALUES(description), status = 'ACTIVE';

INSERT INTO rc_managed_table (theme_id, physical_table, meta_entry_code, catalog_resource_code, lib_id, asset_type, record_count, status, created_by)
SELECT t.id, 'dws_population_base', 'TBL_DWS_POPULATION_BASE', NULL, b.id, 'BASE', 24, 'ACTIVE', 'sys_admin'
FROM rc_theme_library t
LEFT JOIN rc_base_library b ON b.lib_code = 'LIB_POP'
WHERE t.theme_code = 'THEME_POPULATION'
ON DUPLICATE KEY UPDATE meta_entry_code = VALUES(meta_entry_code), record_count = VALUES(record_count), status = 'ACTIVE';

INSERT INTO rc_managed_table (theme_id, physical_table, meta_entry_code, catalog_resource_code, lib_id, asset_type, record_count, status, created_by)
SELECT t.id, 'ads_pop_district_summary', 'TBL_ADS_POP_DISTRICT_SUMMARY', NULL, b.id, 'BASE', 11, 'ACTIVE', 'sys_admin'
FROM rc_theme_library t
LEFT JOIN rc_base_library b ON b.lib_code = 'LIB_POP'
WHERE t.theme_code = 'THEME_POPULATION'
ON DUPLICATE KEY UPDATE meta_entry_code = VALUES(meta_entry_code), record_count = VALUES(record_count), status = 'ACTIVE';

INSERT INTO rc_partition_def (partition_code, partition_name, partition_type, theme_id, table_name, partition_column, expression_text, pretest_status, pretest_message)
SELECT 'PART_POP_DISTRICT', '人口主题按区县分区', 'RANGE', t.id, 'dws_population_base', 'district_code',
       'PARTITION BY LIST COLUMNS(district_code)', 'READY', 'LEDGER：设计预检通过，不自动改表'
FROM rc_theme_library t
WHERE t.theme_code = 'THEME_POPULATION'
  AND NOT EXISTS (SELECT 1 FROM rc_partition_def WHERE partition_code = 'PART_POP_DISTRICT');

INSERT INTO rc_partition_op (partition_def_id, managed_table_id, physical_table, op_type, op_status, preview_sql, message, created_by)
SELECT p.id, m.id, 'dws_population_base', 'ANALYZE', 'LEDGER',
       '-- LEDGER only: ANALYZE TABLE dws_population_base;',
       '人口主题分区维护计划（不自动执行）', 'sys_admin'
FROM rc_partition_def p
JOIN rc_managed_table m ON m.physical_table = 'dws_population_base'
WHERE p.partition_code = 'PART_POP_DISTRICT'
  AND NOT EXISTS (
    SELECT 1 FROM rc_partition_op o WHERE o.physical_table = 'dws_population_base' AND o.op_type = 'ANALYZE' AND o.message LIKE '人口主题%'
  );

-- ---------- 核心区挂载种子 ----------
INSERT INTO ana_zone_binding (domain_code, zone_code, asset_type, asset_ref, asset_name, physical_table, meta_entry_code, data_layer, remark, status, created_by)
SELECT 'population', 'core', 'MANAGED', 'dws_population_base', '人口主题基础宽表', 'dws_population_base', 'TBL_DWS_POPULATION_BASE', 'DWS', 'V190 种子', 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_zone_binding WHERE domain_code='population' AND zone_code='core' AND asset_ref='dws_population_base'
);

INSERT INTO ana_zone_binding (domain_code, zone_code, asset_type, asset_ref, asset_name, physical_table, meta_entry_code, data_layer, remark, status, created_by)
SELECT 'population', 'share', 'MANAGED', 'ads_pop_district_summary', '人口区县月汇总', 'ads_pop_district_summary', 'TBL_ADS_POP_DISTRICT_SUMMARY', 'ADS', 'V190 种子', 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_zone_binding WHERE domain_code='population' AND zone_code='share' AND asset_ref='ads_pop_district_summary'
);

-- ---------- 指标改挂主题表 ----------
UPDATE ana_indicator
SET source_table = 'dws_population_base',
    source_column = 'person_id',
    description = '常住/户籍主题表人员去重计数（dws_population_base）'
WHERE indicator_code = 'IND_POP_COUNT';

UPDATE ana_indicator
SET source_table = 'dws_population_base',
    source_column = 'age',
    agg_func = 'EXPR',
    expr_text = 'COUNT(CASE WHEN age >= 60 THEN 1 END)',
    description = '主题表年龄>=60 计数'
WHERE indicator_code = 'IND_POP_AGE60';

INSERT INTO ana_indicator (domain_code, indicator_code, indicator_name, source_table, source_column, agg_func, expr_text, unit_label, description, status)
SELECT 'population', 'IND_POP_HUKOU', '户籍人口数', 'dws_population_base', 'person_id', 'EXPR',
       'COUNT(CASE WHEN hukou_type=''户籍'' THEN 1 END)', '人', '主题表户籍人口计数', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM ana_indicator WHERE indicator_code = 'IND_POP_HUKOU');

INSERT INTO ana_indicator_query (domain_code, query_no, datasource_key, datasource_name, timeout_sec, sql_text, status, created_by)
SELECT 'population', 'Q_POP_DISTRICT_RESIDENT', 'population_demo', '人口主题库', 60,
       'SELECT district_name AS dim, SUM(resident_count) AS metric FROM ads_pop_district_summary WHERE stat_month = ''2026-07'' GROUP BY district_name',
       'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_indicator_query WHERE query_no = 'Q_POP_DISTRICT_RESIDENT');

INSERT INTO ana_indicator (domain_code, indicator_code, indicator_name, query_id, result_field, field_type, field_name, source_table, agg_func, unit_label, description, status)
SELECT 'population', 'IND_POP_DISTRICT_RESIDENT', '区县常住人口（月汇总）', q.id, 'metric', '数值', 'resident_metric',
       'ads_pop_district_summary', 'SUM', '人', 'ADS 区县月汇总常住人口', 'ACTIVE'
FROM ana_indicator_query q
WHERE q.query_no = 'Q_POP_DISTRICT_RESIDENT'
  AND NOT EXISTS (SELECT 1 FROM ana_indicator WHERE indicator_code = 'IND_POP_DISTRICT_RESIDENT');

-- ---------- 校核台账 M155/M156 ----------
CREATE TABLE IF NOT EXISTS ana_pop_verify_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  domain_code VARCHAR(32) NOT NULL DEFAULT 'population',
  m_code VARCHAR(16) NOT NULL COMMENT 'M155|M156',
  scene_code VARCHAR(64) NOT NULL COMMENT '场景编码',
  scene_name VARCHAR(128) NOT NULL,
  check_type VARCHAR(32) NOT NULL DEFAULT 'MULTI_SOURCE' COMMENT 'BASELINE|MULTI_SOURCE|UPDATE',
  source_dept VARCHAR(128) NULL,
  issue_summary VARCHAR(512) NULL,
  feedback_status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN|FEEDBACK|CLOSED',
  related_person_id VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'LEDGER' COMMENT 'LEDGER 设计台账，非引擎结果',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  KEY idx_pop_verify_m (m_code, feedback_status),
  KEY idx_pop_verify_scene (scene_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人口更新/校核设计台账';

INSERT INTO ana_pop_verify_ledger
  (domain_code, m_code, scene_code, scene_name, check_type, source_dept, issue_summary, feedback_status, related_person_id, status, created_by)
VALUES
('population','M156','CANCELLED_PERSON','注销人员校核','MULTI_SOURCE','公安局','源库仍有有效户籍记录，疑似未同步注销','OPEN','P0005','LEDGER','sys_admin'),
('population','M156','BIRTH_UNREGISTERED','出生未申报户口','BASELINE','卫健委','出生登记有记录但户籍库无对应人员','FEEDBACK','P0004','LEDGER','sys_admin'),
('population','M156','MARITAL_STATUS','婚姻状况校核','MULTI_SOURCE','民政局','婚姻状态与户籍库不一致','OPEN','P0008','LEDGER','sys_admin'),
('population','M155','HUKOU_BASE_UPDATE','户籍基准更新','UPDATE','公安局','按月增量维护户籍基准库','CLOSED','P0001','LEDGER','sys_admin'),
('population','M155','EDU_DOMAIN_UPDATE','教育域信息维护','UPDATE','教育局','学历字段批量更新待入链','OPEN','P0006','LEDGER','sys_admin');

-- ---------- 服务契约 M159/M160 ----------
CREATE TABLE IF NOT EXISTS ana_pop_service_contract (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  domain_code VARCHAR(32) NOT NULL DEFAULT 'population',
  service_code VARCHAR(64) NOT NULL,
  service_name VARCHAR(128) NOT NULL,
  m_code VARCHAR(16) NOT NULL COMMENT 'M159|M160',
  mode VARCHAR(16) NOT NULL COMMENT 'API|BATCH',
  path_or_channel VARCHAR(256) NULL,
  request_sample TEXT NULL,
  response_sample TEXT NULL,
  description VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'LEDGER',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pop_svc_code (service_code),
  KEY idx_pop_svc_m (m_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人口共享服务契约台账';

INSERT INTO ana_pop_service_contract
  (domain_code, service_code, service_name, m_code, mode, path_or_channel, request_sample, response_sample, description, status, created_by)
VALUES
('population','POP_API_BASELINE','人员基准比对接口','M159','API','/api/v1/analytics/domain/population/services/POP_API_BASELINE/invoke',
 '{"idCardMasked":"1308**********001","fields":["hukou_type","age"]}',
 '{"matched":true,"personId":"P0001","hukouType":"户籍","age":28,"mode":"LEDGER"}',
 '小流量校核/比对演示；不接真 ESB','LEDGER','sys_admin'),
('population','POP_API_OVERLAY','基准叠加查询接口','M159','API','/api/v1/analytics/domain/population/services/POP_API_OVERLAY/invoke',
 '{"personId":"P0001","overlay":["education","focus_tag"]}',
 '{"personId":"P0001","education":"本科","focusTag":null,"mode":"LEDGER"}',
 '基准叠加查询演示','LEDGER','sys_admin'),
('population','POP_BATCH_EXCHANGE','人口批量交换前置','M160','BATCH','前置库 ↔ 交换系统 ↔ 批量结果库',
 '{"batchId":"BATCH-POP-202607","table":"dws_population_base","rowLimit":1000}',
 '{"batchId":"BATCH-POP-202607","accepted":true,"status":"LEDGER","message":"已登记批量交换计划，未执行真实传输"}',
 '大批量应用方式设计台账；全链路属后续工程','LEDGER','sys_admin')
ON DUPLICATE KEY UPDATE service_name = VALUES(service_name), description = VALUES(description);
