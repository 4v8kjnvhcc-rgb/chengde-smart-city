-- 五区七维设计登记（D02 §5.7：配置表可查看可编辑）
CREATE TABLE IF NOT EXISTS ana_zone_dim_design (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  domain_code   VARCHAR(32)  NOT NULL COMMENT 'population|legal|macro|key',
  zone_code     VARCHAR(32)  NOT NULL COMMENT 'collect|govern|core|internal|share',
  dim_code      VARCHAR(32)  NOT NULL COMMENT 'POSITION|MODEL|PROCESS|RETENTION|SOURCE|CONSUMER|FREQUENCY',
  item_code     VARCHAR(64)  NOT NULL,
  item_name     VARCHAR(128) NOT NULL,
  item_type     VARCHAR(64)  NULL COMMENT '维度子类型',
  content       VARCHAR(1000) NULL,
  config_json   VARCHAR(2000) NULL,
  deep_link     VARCHAR(512) NULL,
  sort_no       INT          NOT NULL DEFAULT 0,
  status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_by    VARCHAR(64)  NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NULL,
  UNIQUE KEY uk_ana_zone_dim (domain_code, zone_code, dim_code, item_code),
  KEY idx_ana_zone_dim_zone (domain_code, zone_code, dim_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务支撑五区七维设计项';

-- 法人 · 治理及反馈区 · 七维种子（幂等）
INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'POSITION', 'PROBLEM_DATA', '问题数据存放', 'PROBLEM_DATA',
       '存放治理过程发现的问题数据，供数据质量分析以及数据问题反馈使用',
       '/quality?qSub=monitor', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='POSITION' AND item_code='PROBLEM_DATA');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'POSITION', 'SEMI', '半结构化数据存放', 'SEMI',
       '存放半结构化数据，服务后续治理与质量分析',
       '/exchange/ingestion?system=collect&module=ingest.semi', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='POSITION' AND item_code='SEMI');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'POSITION', 'UNSTRUCT_TO_STRUCT', '非结构转结构化结果', 'UNSTRUCT_TO_STRUCT',
       '存放非结构化数据转结构化后的结果数据',
       '/unstructured?tab=process.clean', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='POSITION' AND item_code='UNSTRUCT_TO_STRUCT');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'POSITION', 'DIRTY_FULL', '周期全量脏数据', 'DIRTY_FULL',
       '提供周期全量脏数据，供数据质量分析以及数据问题反馈使用',
       '/quality?qSub=assess', 40, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='POSITION' AND item_code='DIRTY_FULL');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'MODEL', 'SOURCE_ALIGN', '贴源结构模型', 'SOURCE_ALIGN',
       '数据模型采取贴源结构', NULL, 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='MODEL' AND item_code='SOURCE_ALIGN');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'MODEL', 'NORMALIZED', '规范化模型设计', 'NORMALIZED',
       '数据模型采用模型规范化设计',
       '/governance?tab=model&mSub=clean', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='MODEL' AND item_code='NORMALIZED');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'MODEL', 'INCREMENTAL_SLICE', '流水周期增量切片', 'INCREMENTAL_SLICE',
       '流水类的表采用周期增量切片表存储的方式',
       '/system/uum?tab=exec-cycle', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='MODEL' AND item_code='INCREMENTAL_SLICE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'PROCESS', 'TECH_LEGAL_CLEAN', '技术性与合法性检核清洗', 'TECH_LEGAL_CLEAN',
       '完成数据的技术性以及合法性检核清洗',
       '/governance?tab=etl&etlSub=task-mgmt', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='PROCESS' AND item_code='TECH_LEGAL_CLEAN');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'PROCESS', 'POINT_IN_TIME', '历史时点变更事实', 'POINT_IN_TIME',
       '以数据时点的方式记录某个历史时点的信息变更事实', NULL, 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='PROCESS' AND item_code='POINT_IN_TIME');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'PROCESS', 'SEMI_UNSTRUCT_PREP', '半/非结构预处理与转结构化', 'SEMI_UNSTRUCT_PREP',
       '提供半结构化、非结构化数据转结构化及预处理等功能',
       '/unstructured?tab=process.clean', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='PROCESS' AND item_code='SEMI_UNSTRUCT_PREP');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'RETENTION', 'LONG_TERM', '长期存储策略', 'LONG_TERM',
       '数据的存储周期可支持长期存储；具体存储情况根据存储空间而定，周期随容量变大而延长',
       '/resource-center?tab=storage', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='RETENTION' AND item_code='LONG_TERM');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'SOURCE', 'COLLECT_ZONE', '采集区数据', 'COLLECT_ZONE',
       '数据来源：采集区数据',
       '/analytics/legal-entity?tab=zone.collect', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='SOURCE' AND item_code='COLLECT_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'SOURCE', 'EXTERNAL_ZONE', '外部数据区数据', 'EXTERNAL_ZONE',
       '数据来源：外部数据区数据', NULL, 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='SOURCE' AND item_code='EXTERNAL_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'CONSUMER', 'CORE_ZONE', '数据核心区使用者', 'CORE_ZONE',
       '数据使用者：数据核心区使用者（须经治理分流，禁止旁路权威）',
       '/analytics/legal-entity?tab=zone.core', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='CONSUMER' AND item_code='CORE_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'CONSUMER', 'SERVICE_ZONE', '服务区使用者', 'SERVICE_ZONE',
       '数据使用者：服务区使用者（内部服务区/共享服务区）',
       '/analytics/legal-entity?tab=zone.share', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='CONSUMER' AND item_code='SERVICE_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, config_json, deep_link, sort_no, status, created_by)
SELECT 'legal', 'govern', 'FREQUENCY', 'MONTHLY_DEFAULT', '默认月度更新', 'MONTHLY',
       '一般的更新频率为每月更新一次；具体更新频度视实际情况可按一天、一周、一个季度、半年或者一年更新一次',
       '{"defaultFreq":"MONTHLY","allowed":["DAILY","WEEKLY","MONTHLY","QUARTERLY","HALF_YEAR","YEARLY"]}',
       '/system/uum?tab=exec-cycle', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='govern' AND dim_code='FREQUENCY' AND item_code='MONTHLY_DEFAULT');
