-- 对应手工补丁：sql/patch/20260820_quality_problem_analysis_v240.sql
-- M083 数据质量分析报告：问题快速定位 / 编码映射影响 / 知识沉淀

CREATE TABLE IF NOT EXISTS gov_quality_analysis_case (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  case_code VARCHAR(64) NOT NULL,
  case_name VARCHAR(128) NOT NULL,
  report_id BIGINT NULL,
  target_table VARCHAR(128) NULL,
  target_column VARCHAR(128) NULL,
  issue_type VARCHAR(64) NULL,
  severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
  locate_summary VARCHAR(512) NULL COMMENT '快速定位结论',
  root_cause VARCHAR(1024) NULL COMMENT '疑似根因',
  impact_scope VARCHAR(512) NULL COMMENT '影响范围',
  suggested_action VARCHAR(1024) NULL COMMENT '建议整改',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/IN_ANALYSIS/RESOLVED/ARCHIVED',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qa_case_code (case_code),
  KEY idx_qa_case_table (target_table, target_column),
  KEY idx_qa_case_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_quality_code_impact (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  impact_code VARCHAR(64) NOT NULL,
  case_id BIGINT NULL,
  standard_item_id BIGINT NULL,
  standard_code VARCHAR(64) NULL,
  standard_name VARCHAR(128) NULL,
  source_system VARCHAR(128) NULL,
  source_table VARCHAR(128) NULL,
  source_column VARCHAR(128) NULL,
  mapping_status VARCHAR(32) NULL COMMENT 'MAPPED/PARTIAL/UNMAPPED',
  impact_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
  impact_desc VARCHAR(1024) NULL,
  downstream_refs VARCHAR(1024) NULL COMMENT '下游表/任务/目录引用',
  issue_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qa_impact_code (impact_code),
  KEY idx_qa_impact_src (source_table, source_column),
  KEY idx_qa_impact_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_quality_knowledge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_code VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL,
  issue_type VARCHAR(64) NULL,
  category VARCHAR(64) NULL COMMENT 'LOCATE/CODE_MAP/PROCESS/OTHER',
  symptom VARCHAR(512) NULL,
  root_cause VARCHAR(1024) NULL,
  solution VARCHAR(2048) NULL,
  related_standard VARCHAR(256) NULL,
  hit_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_qa_knowledge_code (knowledge_code),
  KEY idx_qa_knowledge_type (issue_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 测试报告 4 条（可重复执行）
INSERT INTO gov_quality_report (report_code, report_name, dimension, score, export_payload, created_at)
SELECT 'RPT_QA_LOCATE_01', '人口基础信息-空值问题定位报告', '完整性+准确性', 92.40,
       'seed=problem-analysis', '2026-08-18 09:20:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_report WHERE report_code = 'RPT_QA_LOCATE_01');

INSERT INTO gov_quality_report (report_code, report_name, dimension, score, export_payload, created_at)
SELECT 'RPT_QA_CODEMAP_01', '行政区划编码映射影响分析报告', '规范性+一致性', 88.75,
       'seed=problem-analysis', '2026-08-18 14:35:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_report WHERE report_code = 'RPT_QA_CODEMAP_01');

INSERT INTO gov_quality_report (report_code, report_name, dimension, score, export_payload, created_at)
SELECT 'RPT_QA_KNOW_01', '法人统一社会信用代码质量问题分析报告', '唯一性+完整性+规范性', 95.10,
       'seed=problem-analysis', '2026-08-19 10:05:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_report WHERE report_code = 'RPT_QA_KNOW_01');

INSERT INTO gov_quality_report (report_code, report_name, dimension, score, export_payload, created_at)
SELECT 'RPT_QA_MIX_01', '汇聚层多表质量问题综合分析报告', '完整性+规范性+准确性+唯一性+一致性+及时性', 90.60,
       'seed=problem-analysis', '2026-08-19 16:48:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_report WHERE report_code = 'RPT_QA_MIX_01');

INSERT INTO gov_quality_report (report_code, report_name, dimension, score, export_payload, created_at)
SELECT 'RPT_QA_TIMELY_01', '人口迁入及时性异常分析报告', '及时性+一致性', 86.20,
       'seed=problem-analysis', '2026-08-20 08:15:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_report WHERE report_code = 'RPT_QA_TIMELY_01');

-- 标准映射种子（支撑编码映射影响分析）
INSERT INTO gov_standard_mapping (standard_item_id, source_system, source_table, source_column, mapping_status, match_score, remark, created_by, created_at)
SELECT si.id, '人口库', 'ods_pop_basic_info', 'region_code', 'PARTIAL', 72.00,
       '区划码存在历史 6 位与 12 位混用', 'sys_admin', '2026-08-18 11:00:00'
FROM gov_standard_item si
WHERE si.item_code = 'STD_CODE_REGION'
  AND NOT EXISTS (
    SELECT 1 FROM gov_standard_mapping m
    WHERE m.source_table = 'ods_pop_basic_info' AND m.source_column = 'region_code' AND m.standard_item_id = si.id
  )
LIMIT 1;

INSERT INTO gov_standard_mapping (standard_item_id, source_system, source_table, source_column, mapping_status, match_score, remark, created_by, created_at)
SELECT si.id, '法人库', 'ods_corp_basic', 'uscc', 'MAPPED', 96.00,
       '统一社会信用代码已对标', 'sys_admin', '2026-08-18 11:05:00'
FROM gov_standard_item si
WHERE si.item_code = 'STD_ELEM_ID'
  AND NOT EXISTS (
    SELECT 1 FROM gov_standard_mapping m
    WHERE m.source_table = 'ods_corp_basic' AND m.source_column = 'uscc' AND m.standard_item_id = si.id
  )
LIMIT 1;

-- 快速定位案例
INSERT INTO gov_quality_analysis_case (
  case_code, case_name, report_id, target_table, target_column, issue_type, severity,
  locate_summary, root_cause, impact_scope, suggested_action, status, created_by, created_at
)
SELECT 'QAC_POP_NULL_01', '身份证号空值快速定位', r.id, 'ods_pop_basic_info', 'id_card_no', 'NULL_VALUE', 'HIGH',
       '定位到 ODS 人口基础表 id_card_no 空值集中在「临时登记」来源批次',
       '源业务系统临登接口未强制校验证件号；汇聚通道直写未做非空拦截',
       '直通编目门禁、人口主题挂载、共享服务区人口查询',
       '1) 源端补录；2) 接入侧加非空规则；3) 存量空值回流整改',
       'IN_ANALYSIS', 'sys_admin', '2026-08-18 09:30:00'
FROM gov_quality_report r
WHERE r.report_code = 'RPT_QA_LOCATE_01'
  AND NOT EXISTS (SELECT 1 FROM gov_quality_analysis_case WHERE case_code = 'QAC_POP_NULL_01');

INSERT INTO gov_quality_analysis_case (
  case_code, case_name, report_id, target_table, target_column, issue_type, severity,
  locate_summary, root_cause, impact_scope, suggested_action, status, created_by, created_at
)
SELECT 'QAC_REGION_MAP_01', '行政区划编码不一致定位', r.id, 'ods_pop_basic_info', 'region_code', 'INVALID', 'HIGH',
       '问题值多为旧版 6 位区划码，与标准 12 位码表不匹配',
       '编码映射未覆盖历史码转换；对标状态 PARTIAL',
       '区划维度统计、跨系统人口比对、目录门户区划筛选',
       '补齐码表映射与历史码转换规则，重跑一致性稽核',
       'OPEN', 'sys_admin', '2026-08-18 14:50:00'
FROM gov_quality_report r
WHERE r.report_code = 'RPT_QA_CODEMAP_01'
  AND NOT EXISTS (SELECT 1 FROM gov_quality_analysis_case WHERE case_code = 'QAC_REGION_MAP_01');

INSERT INTO gov_quality_analysis_case (
  case_code, case_name, report_id, target_table, target_column, issue_type, severity,
  locate_summary, root_cause, impact_scope, suggested_action, status, created_by, created_at
)
SELECT 'QAC_USCC_DUP_01', '统一社会信用代码重复定位', r.id, 'ods_corp_basic', 'uscc', 'DUPLICATE', 'MEDIUM',
       '重复组集中在分支机构与总机构混登记录',
       '源系统未区分主体/分支；唯一性规则仅按 uscc 未加主体类型',
       '法人主题库、共享服务区法人查询',
       '按主体类型复合唯一；重复组人工确认后合并',
       'RESOLVED', 'sys_admin', '2026-08-19 10:20:00'
FROM gov_quality_report r
WHERE r.report_code = 'RPT_QA_KNOW_01'
  AND NOT EXISTS (SELECT 1 FROM gov_quality_analysis_case WHERE case_code = 'QAC_USCC_DUP_01');

INSERT INTO gov_quality_analysis_case (
  case_code, case_name, report_id, target_table, target_column, issue_type, severity,
  locate_summary, root_cause, impact_scope, suggested_action, status, created_by, created_at
)
SELECT 'QAC_TIMELY_01', '迁入登记滞后定位', r.id, 'ods_pop_move_in', 'register_time', 'OUT_OF_RANGE', 'MEDIUM',
       '滞后样本集中在跨县迁入业务，登记时间晚于业务发生日超过 7 天',
       '线下材料补录延迟；及时性阈值未按迁入类型区分',
       '人口迁入专题统计、领导驾驶舱及时性指标',
       '区分迁入类型阈值；对超期样本生成整改工单',
       'OPEN', 'sys_admin', '2026-08-20 08:30:00'
FROM gov_quality_report r
WHERE r.report_code = 'RPT_QA_TIMELY_01'
  AND NOT EXISTS (SELECT 1 FROM gov_quality_analysis_case WHERE case_code = 'QAC_TIMELY_01');

-- 编码映射影响
INSERT INTO gov_quality_code_impact (
  impact_code, case_id, standard_item_id, standard_code, standard_name,
  source_system, source_table, source_column, mapping_status, impact_level, impact_desc,
  downstream_refs, issue_count, status, created_by, created_at
)
SELECT 'QCI_REGION_01', c.id, si.id, si.item_code, si.item_name,
       '人口库', 'ods_pop_basic_info', 'region_code', 'PARTIAL', 'HIGH',
       '区划码对标不完整导致跨系统一致性失败，影响人口主题区划聚合与目录筛选。',
       'dwd_pop_basic;dws_pop_theme;目录门户区划筛选;人口共享服务',
       128, 'OPEN', 'sys_admin', '2026-08-18 15:00:00'
FROM gov_quality_analysis_case c
JOIN gov_standard_item si ON si.item_code = 'STD_CODE_REGION'
WHERE c.case_code = 'QAC_REGION_MAP_01'
  AND NOT EXISTS (SELECT 1 FROM gov_quality_code_impact WHERE impact_code = 'QCI_REGION_01');

INSERT INTO gov_quality_code_impact (
  impact_code, case_id, standard_item_id, standard_code, standard_name,
  source_system, source_table, source_column, mapping_status, impact_level, impact_desc,
  downstream_refs, issue_count, status, created_by, created_at
)
SELECT 'QCI_USCC_01', c.id, si.id, si.item_code, si.item_name,
       '法人库', 'ods_corp_basic', 'uscc', 'MAPPED', 'MEDIUM',
       '信用代码虽已对标，重复值仍会污染法人唯一性与共享查询命中率。',
       'dwd_corp_basic;dws_corp_theme;法人共享查询',
       17, 'OPEN', 'sys_admin', '2026-08-19 10:40:00'
FROM gov_quality_analysis_case c
JOIN gov_standard_item si ON si.item_code = 'STD_ELEM_ID'
WHERE c.case_code = 'QAC_USCC_DUP_01'
  AND NOT EXISTS (SELECT 1 FROM gov_quality_code_impact WHERE impact_code = 'QCI_USCC_01');

INSERT INTO gov_quality_code_impact (
  impact_code, case_id, standard_item_id, standard_code, standard_name,
  source_system, source_table, source_column, mapping_status, impact_level, impact_desc,
  downstream_refs, issue_count, status, created_by, created_at
)
SELECT 'QCI_IDCARD_01', c.id, si.id, si.item_code, si.item_name,
       '人口库', 'ods_pop_basic_info', 'id_card_no', 'UNMAPPED', 'HIGH',
       '身份证号空值且对标缺失，阻断直通编目门禁与人口精确检索。',
       '直通共享门禁;人口精确搜索;五区挂载校验',
       56, 'OPEN', 'sys_admin', '2026-08-18 09:45:00'
FROM gov_quality_analysis_case c
JOIN gov_standard_item si ON si.item_code = 'STD_ELEM_ID'
WHERE c.case_code = 'QAC_POP_NULL_01'
  AND NOT EXISTS (SELECT 1 FROM gov_quality_code_impact WHERE impact_code = 'QCI_IDCARD_01');

-- 知识沉淀
INSERT INTO gov_quality_knowledge (
  knowledge_code, title, issue_type, category, symptom, root_cause, solution, related_standard, hit_count, status, created_by, created_at
)
SELECT 'QK_NULL_IDCARD', '身份证号空值排查手册', 'NULL_VALUE', 'LOCATE',
       '稽核报告出现 id_card_no 空值，且集中在临登来源',
       '源接口未强制非空；汇聚未做空值拦截',
       '源端补强校验 → 接入规则加 NULL_CHECK → 空值批次回流整改 → 复跑评估报告',
       'GB/T 19488 姓名/证件数据元', 3, 'ACTIVE', 'sys_admin', '2026-08-18 10:00:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_knowledge WHERE knowledge_code = 'QK_NULL_IDCARD');

INSERT INTO gov_quality_knowledge (
  knowledge_code, title, issue_type, category, symptom, root_cause, solution, related_standard, hit_count, status, created_by, created_at
)
SELECT 'QK_REGION_MAP', '行政区划编码映射影响处置', 'INVALID', 'CODE_MAP',
       '区划码长度不一或与标准码表无法匹配',
       '历史 6 位码未转换；标准映射状态 PARTIAL',
       '维护码表映射与历史码转换 → 更新 gov_standard_mapping → 重跑一致性稽核 → 更新影响分析结论',
       'GB/T 2260 行政区划代码', 5, 'ACTIVE', 'sys_admin', '2026-08-18 15:10:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_knowledge WHERE knowledge_code = 'QK_REGION_MAP');

INSERT INTO gov_quality_knowledge (
  knowledge_code, title, issue_type, category, symptom, root_cause, solution, related_standard, hit_count, status, created_by, created_at
)
SELECT 'QK_USCC_DUP', '统一社会信用代码重复处置', 'DUPLICATE', 'PROCESS',
       '法人表 uscc 出现重复组',
       '主体/分支混登；唯一性规则过粗',
       '按主体类型复合唯一 → 重复组合并台账 → 复跑唯一性稽核 → 写入知识命中次数',
       '统一社会信用代码编码规则', 2, 'ACTIVE', 'sys_admin', '2026-08-19 11:00:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_knowledge WHERE knowledge_code = 'QK_USCC_DUP');

INSERT INTO gov_quality_knowledge (
  knowledge_code, title, issue_type, category, symptom, root_cause, solution, related_standard, hit_count, status, created_by, created_at
)
SELECT 'QK_TIMELY_MOVE', '迁入登记及时性异常处置', 'OUT_OF_RANGE', 'LOCATE',
       'register_time 相对业务日滞后超过阈值',
       '线下补录延迟；阈值未按业务类型区分',
       '按迁入类型配置阈值 → 超期工单 → 复评及时性维度',
       '人口迁入业务时效规范', 1, 'ACTIVE', 'sys_admin', '2026-08-20 08:40:00'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gov_quality_knowledge WHERE knowledge_code = 'QK_TIMELY_MOVE');
