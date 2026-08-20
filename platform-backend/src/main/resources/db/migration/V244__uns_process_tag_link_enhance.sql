-- 非结构化标识/关联：标签定义、关联规则 + 清洗/标识/关联说通演示数据
-- 可重复：清理 SEED_PROC_* 相关后再写入

CREATE TABLE IF NOT EXISTS uns_tag_def (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tag_code VARCHAR(64) NOT NULL COMMENT '标签编码',
  tag_name VARCHAR(128) NOT NULL COMMENT '标签名称',
  tag_kind VARCHAR(32) NOT NULL DEFAULT 'GENERAL' COMMENT 'GENERAL=通用|BUSINESS=业务',
  match_keywords VARCHAR(512) NULL COMMENT '匹配关键词，逗号分隔或 JSON 数组',
  description VARCHAR(512) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_uns_tag_def_code (tag_code),
  KEY idx_uns_tag_def_kind (tag_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非结构化标签定义（通用/业务）';

CREATE TABLE IF NOT EXISTS uns_link_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code VARCHAR(64) NOT NULL,
  rule_name VARCHAR(128) NOT NULL,
  link_stage VARCHAR(32) NOT NULL DEFAULT 'ALL' COMMENT 'EXTRACT|ANALYZE|BACKFILL|ALL',
  algorithm VARCHAR(32) NOT NULL DEFAULT 'SIMILARITY' COMMENT 'SIMILARITY|CATEGORY|KEYWORD',
  config_json TEXT NULL,
  description VARCHAR(512) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_uns_link_rule_code (rule_code),
  KEY idx_uns_link_rule_stage (link_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非结构化关联规则（提取/分析/回填）';

-- 标签定义（通用 + 业务）
INSERT INTO uns_tag_def (tag_code, tag_name, tag_kind, match_keywords, description, enabled, sort_order, status)
SELECT 'GEN_SENSITIVE_PUBLIC', '敏感级别:公开', 'GENERAL', '公开,公示,通知', '通用标签：敏感级别显性化', 1, 10, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_tag_def WHERE tag_code = 'GEN_SENSITIVE_PUBLIC');

INSERT INTO uns_tag_def (tag_code, tag_name, tag_kind, match_keywords, description, enabled, sort_order, status)
SELECT 'GEN_LANG_ZH', '语言:中文', 'GENERAL', '关于,通知,规划,统计', '通用标签：语言属性', 1, 20, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_tag_def WHERE tag_code = 'GEN_LANG_ZH');

INSERT INTO uns_tag_def (tag_code, tag_name, tag_kind, match_keywords, description, enabled, sort_order, status)
SELECT 'GEN_REGION_CD', '区域:承德', 'GENERAL', '承德,高新区,双桥,兴隆', '通用标签：行政区划', 1, 30, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_tag_def WHERE tag_code = 'GEN_REGION_CD');

INSERT INTO uns_tag_def (tag_code, tag_name, tag_kind, match_keywords, description, enabled, sort_order, status)
SELECT 'BIZ_PLAN_CTRL', '业务:控规附图', 'BUSINESS', '控规,起步区,公示附图', '业务标签：规划建设', 1, 40, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_tag_def WHERE tag_code = 'BIZ_PLAN_CTRL');

INSERT INTO uns_tag_def (tag_code, tag_name, tag_kind, match_keywords, description, enabled, sort_order, status)
SELECT 'BIZ_DATA_SHARE', '业务:政务数据共享', 'BUSINESS', '数据共享,目录,对接', '业务标签：数据共享专题', 1, 50, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_tag_def WHERE tag_code = 'BIZ_DATA_SHARE');

-- 关联规则：提取 / 分析 / 回填
INSERT INTO uns_link_rule (rule_code, rule_name, link_stage, algorithm, config_json, description, enabled, sort_order, status)
SELECT 'LINK_EXTRACT_SIM', '关联提取-相似指纹', 'EXTRACT', 'SIMILARITY', '{}',
 '从原始库提取相似候选文档', 1, 10, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_link_rule WHERE rule_code = 'LINK_EXTRACT_SIM');

INSERT INTO uns_link_rule (rule_code, rule_name, link_stage, algorithm, config_json, description, enabled, sort_order, status)
SELECT 'LINK_ANALYZE_KW', '关联分析-关键词', 'ANALYZE', 'KEYWORD',
 '{"keywords":["数据共享","控规","公示","目录"]}',
 '按业务关键词对候选评分', 1, 20, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_link_rule WHERE rule_code = 'LINK_ANALYZE_KW');

INSERT INTO uns_link_rule (rule_code, rule_name, link_stage, algorithm, config_json, description, enabled, sort_order, status)
SELECT 'LINK_BACKFILL_CAT', '关联回填-同分类', 'BACKFILL', 'CATEGORY', '{}',
 '将最高分候选回填到文档关联字段', 1, 30, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_link_rule WHERE rule_code = 'LINK_BACKFILL_CAT');

INSERT INTO uns_link_rule (rule_code, rule_name, link_stage, algorithm, config_json, description, enabled, sort_order, status)
SELECT 'LINK_ALL_SIM', '全流程-相似关联', 'ALL', 'SIMILARITY', '{}',
 '一次任务完成提取/分析/回填', 1, 40, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_link_rule WHERE rule_code = 'LINK_ALL_SIM');

-- 清理旧演示文档相关
DELETE i FROM uns_clean_issue i
INNER JOIN uns_document d ON i.doc_id = d.id
WHERE d.doc_code LIKE 'SEED_PROC_%';

DELETE p FROM uns_doc_pipeline p
INNER JOIN uns_document d ON p.doc_id = d.id
WHERE d.doc_code LIKE 'SEED_PROC_%';

DELETE FROM uns_document WHERE doc_code LIKE 'SEED_PROC_%';

INSERT INTO uns_doc_category (category_code, category_name, parent_id, media_type, description, sort_order, status)
SELECT 'CAT_GOV_DOC', '政务公文', NULL, 'DOCUMENT', '公文、通知、政策文件', 10, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_doc_category WHERE category_code = 'CAT_GOV_DOC');

INSERT INTO uns_doc_category (category_code, category_name, parent_id, media_type, description, sort_order, status)
SELECT 'CAT_IMAGE', '图片影像', NULL, 'IMAGE', '现场影像、扫描件、图纸', 20, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_doc_category WHERE category_code = 'CAT_IMAGE');

-- 1) 垃圾标题草稿：用于清洗命中 FILTER → 问题数据
INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, fingerprint, meta_status, source_type, source_system,
  index_status, category_code, publish_status, tag_json, process_status,
  created_by, created_at, updated_at
) VALUES (
  'SEED_PROC_CLEAN_JUNK',
  '临时测试 untitled 草稿',
  'tmp-untitled.docx',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'uns/seed/proc/SEED_PROC_CLEAN_JUNK.docx',
  12288,
  '演示用不规范标题，清洗规则应标记为问题数据。联系电话 138-0000-1234',
  '演示用户',
  'DOCX',
  '临时|测试|untitled|草稿',
  'RAW',
  'UPLOAD', '演示导入',
  'PENDING', 'CAT_GOV_DOC', 'DRAFT', '[]', 'RAW',
  'sys_admin', '2026-08-18 10:00:00', '2026-08-18 10:00:00'
);

-- 2) 人口统计扫描件：标识通用+业务标签
INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, media_width, media_height, fingerprint, meta_status, source_type, source_system,
  index_status, category_code, publish_status, tag_json, process_status, content_json,
  created_by, created_at, updated_at
) VALUES (
  'SEED_PROC_TAG_POP',
  '承德市双桥区人口统计年报扫描件',
  '双桥区人口统计年报-2025.png',
  'image/png',
  'uns/seed/proc/SEED_PROC_TAG_POP.png',
  920000,
  '双桥区常住人口、户籍人口及社保参保人数统计年报扫描件，承德本地公开材料。',
  '统计科',
  'PNG', 1600, 1200,
  '承德|双桥|人口|统计|年报|CAT:CAT_IMAGE',
  'EXTRACTED',
  'UPLOAD', '统计局业务系统',
  'INDEXED', 'CAT_IMAGE', 'PUBLISHED',
  '["区域:承德","业务:人口社会"]',
  'TAGGED',
  '{"keywords":["人口","统计","双桥"],"topics":["人口社会"],"generalTags":["敏感级别:公开","语言:中文","区域:承德"],"businessTags":["业务:人口社会"]}',
  'sys_admin', '2026-08-18 11:00:00', '2026-08-19 09:30:00'
);

-- 3) 法人登记证：业务标签法人经济，待关联
INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, fingerprint, meta_status, source_type, source_system,
  index_status, category_code, publish_status, tag_json, process_status, content_json,
  created_by, created_at, updated_at
) VALUES (
  'SEED_PROC_LINK_CORP',
  '承德高新区某科技有限公司营业执照扫描件',
  '营业执照-高新科技.pdf',
  'application/pdf',
  'uns/seed/proc/SEED_PROC_LINK_CORP.pdf',
  245760,
  '法人登记证扫描件，用于与政务数据共享目录中的法人主题关联演示。',
  '市场监管局',
  'PDF',
  '承德|高新|法人|企业|营业执照|数据共享',
  'UNDERSTOOD',
  'UPLOAD', '市场监管业务系统',
  'INDEXED', 'CAT_GOV_DOC', 'PUBLISHED',
  '["区域:承德","业务:法人经济"]',
  'RAW',
  '{"keywords":["法人","企业","高新"],"topics":["法人经济"],"summary":"高新科技公司营业执照"}',
  'sys_admin', '2026-08-18 14:00:00', '2026-08-18 14:00:00'
);

-- 4) 会议录音纪要：音视频，关联分析用
INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, media_duration_sec, fingerprint, meta_status, source_type, source_system,
  index_status, category_code, publish_status, tag_json, process_status,
  created_by, created_at, updated_at
) VALUES (
  'SEED_PROC_LINK_MEET',
  '高新区数据共享专题会议录音纪要',
  '数据共享专题会-202608.mp3',
  'audio/mpeg',
  'uns/seed/proc/SEED_PROC_LINK_MEET.mp3',
  3500000,
  '会议讨论数据共享目录对接、控规附图上架与问题数据清洗口径。',
  '大数据中心',
  'MP3', 1860,
  '数据共享|目录|对接|控规|清洗|承德|高新',
  'EXTRACTED',
  'UPLOAD', '会议系统',
  'PENDING', 'CAT_GOV_DOC', 'DRAFT',
  '[]', 'RAW',
  'sys_admin', '2026-08-19 16:00:00', '2026-08-19 16:00:00'
);

-- 5) 规范公文：已清洗通过，用于一致性对照
INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, fingerprint, meta_status, source_type, source_system,
  index_status, category_code, publish_status, tag_json, process_status,
  created_by, created_at, updated_at
) VALUES (
  'SEED_PROC_CLEAN_OK',
  '承德高新区管委会办公室关于规范非结构化文件归档的通知',
  '归档规范通知.pdf',
  'application/pdf',
  'uns/seed/proc/SEED_PROC_CLEAN_OK.pdf',
  156000,
  '明确归档命名、媒介类型与清洗后入库确认流程，承德高新区公开文件。',
  '办公室',
  'PDF',
  '承德|高新|归档|非结构化|清洗|通知',
  'UNDERSTOOD',
  'UPLOAD', 'OA 办公系统',
  'INDEXED', 'CAT_GOV_DOC', 'PUBLISHED',
  '["敏感级别:公开","区域:承德"]',
  'CLEANED',
  'sys_admin', '2026-08-17 09:00:00', '2026-08-19 11:00:00'
);

-- 问题数据：垃圾标题命中过滤规则（待确认）
INSERT INTO uns_clean_issue (
  doc_id, rule_id, rule_code, pipeline_id, target_field, error_level, issue_status,
  before_value, after_value, message, created_at
)
SELECT d.id,
       (SELECT id FROM uns_clean_rule WHERE rule_code = 'FILTER_GARBAGE_TITLE' LIMIT 1),
       'FILTER_GARBAGE_TITLE',
       NULL,
       'title',
       'ERROR',
       'OPEN',
       d.title,
       d.title,
       '命中垃圾/冗余过滤规则「测试」：过滤垃圾标题',
       '2026-08-19 10:15:00'
FROM uns_document d
WHERE d.doc_code = 'SEED_PROC_CLEAN_JUNK'
  AND NOT EXISTS (
    SELECT 1 FROM uns_clean_issue i WHERE i.doc_id = d.id AND i.rule_code = 'FILTER_GARBAGE_TITLE' AND i.issue_status = 'OPEN'
  );

-- 任务台账样例（清洗/标识/关联）
INSERT INTO uns_doc_pipeline (doc_id, pipeline_type, status, result_message, detail_json, created_at)
SELECT d.id, 'CLEAN', 'SUCCESS',
       '台账清洗完成：命中规则 1 条，问题 1 条，阻塞级 1；一致性检查未通过（演示）',
       '{"engineMode":"LEDGER","consistency":{"passed":false},"issueCount":1}',
       '2026-08-19 10:15:00'
FROM uns_document d WHERE d.doc_code = 'SEED_PROC_CLEAN_JUNK';

INSERT INTO uns_doc_pipeline (doc_id, pipeline_type, status, result_message, detail_json, created_at)
SELECT d.id, 'TAG', 'SUCCESS',
       '标识完成：通用标签 3、业务标签 1（知识库规则引擎台账）',
       '{"generalTags":["敏感级别:公开","语言:中文","区域:承德"],"businessTags":["业务:人口社会"]}',
       '2026-08-19 09:31:00'
FROM uns_document d WHERE d.doc_code = 'SEED_PROC_TAG_POP';

INSERT INTO uns_doc_pipeline (doc_id, pipeline_type, status, result_message, detail_json, created_at)
SELECT d.id, 'CLEAN', 'SUCCESS',
       '台账清洗完成：命中规则 2 条，问题 0 条，阻塞级 0；一致性检查通过',
       '{"engineMode":"LEDGER","consistency":{"passed":true},"issueCount":0}',
       '2026-08-19 11:00:00'
FROM uns_document d WHERE d.doc_code = 'SEED_PROC_CLEAN_OK';
