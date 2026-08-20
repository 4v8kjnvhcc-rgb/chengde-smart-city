-- 目标库：smart_city
-- 手工可重复执行；与 Flyway V243 同源
-- 非结构化元数据管理：演示数据（文档/图片/音频/视频 + 内容理解/标签/相似连接）
-- 可重复执行：先清 SEED_META_* 再写入

DELETE p FROM uns_doc_pipeline p
INNER JOIN uns_document d ON p.doc_id = d.id
WHERE d.doc_code LIKE 'SEED_META_%';

DELETE FROM uns_document WHERE doc_code LIKE 'SEED_META_%';

INSERT INTO uns_doc_category (category_code, category_name, parent_id, media_type, description, sort_order, status)
SELECT 'CAT_GOV_DOC', '政务公文', NULL, 'DOCUMENT', '公文、通知、政策文件', 10, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_doc_category WHERE category_code = 'CAT_GOV_DOC');

INSERT INTO uns_doc_category (category_code, category_name, parent_id, media_type, description, sort_order, status)
SELECT 'CAT_IMAGE', '图片影像', NULL, 'IMAGE', '现场影像、扫描件、图纸', 20, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_doc_category WHERE category_code = 'CAT_IMAGE');

INSERT INTO uns_doc_category (category_code, category_name, parent_id, media_type, description, sort_order, status)
SELECT 'CAT_MEDIA_AV', '音视频资料', NULL, 'AV', '会议录音、宣传视频等', 30, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_doc_category WHERE category_code = 'CAT_MEDIA_AV');

-- 1) 政务通知 PDF：已内容理解 + 标签（后续连接控规附图）
INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, media_width, media_height, media_duration_sec,
  feature_json, content_json, fingerprint, meta_status,
  source_type, source_system, source_url,
  index_status, category_code, publish_status, tag_json, process_status,
  linked_doc_id, created_by, created_at, updated_at
) VALUES (
  'SEED_META_DOC_SHARE',
  '承德高新区管委会关于推进政务数据共享工作的通知',
  '承高新管发〔2025〕48号.pdf',
  'application/pdf',
  'uns/seed/meta/SEED_META_DOC_SHARE.pdf',
  286720,
  '明确各部门数据共享责任、目录更新周期及对接大数据平台时限要求。',
  '张明远',
  'PDF',
  NULL, NULL, NULL,
  '{"mediaKind":"DOCUMENT","title":"承德高新区管委会关于推进政务数据共享工作的通知","format":"PDF","fileSize":286720,"sampleKeywords":["政务数据","共享","目录"]}',
  '{"keywords":["政务数据","共享","目录","对接"],"topics":["数据共享","政务公开"],"sentiment":"POSITIVE","summary":"《承德高新区管委会关于推进政务数据共享工作的通知》关键词：政务数据、共享、目录；主题：数据共享、政务公开；情感倾向：正向"}',
  '政务数据|共享|目录|对接|数据共享|政务公开|CAT:CAT_GOV_DOC|高新|管委|共享',
  'UNDERSTOOD',
  'UPLOAD', 'OA 办公系统', NULL,
  'INDEXED', 'CAT_GOV_DOC', 'PUBLISHED',
  '["政务数据","共享","通知","目录"]',
  'TAGGED',
  NULL,
  'sys_admin', '2025-08-12 09:36:00', '2025-08-15 14:22:00'
);

-- 2) 控规公示附图 PNG：已提取特征 + 标签（与通知建立相似连接）
INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, media_width, media_height, media_duration_sec,
  feature_json, content_json, fingerprint, meta_status,
  source_type, source_system, source_url,
  index_status, category_code, publish_status, tag_json, process_status,
  linked_doc_id, created_by, created_at, updated_at
) VALUES (
  'SEED_META_IMG_PLAN',
  '高新区起步区控制性详细规划公示附图',
  '起步区控规公示附图-202509.png',
  'image/png',
  'uns/seed/meta/SEED_META_IMG_PLAN.png',
  1843200,
  '起步区用地性质、道路红线及公共服务设施布局公示附图，用于规划意见征集。',
  '李婉清',
  'PNG',
  1920, 1080, NULL,
  '{"mediaKind":"IMAGE","title":"高新区起步区控制性详细规划公示附图","format":"PNG","width":1920,"height":1080,"fileSize":1843200,"resolutionSource":"REGISTERED"}',
  NULL,
  '控规|公示|附图|起步区|CAT:CAT_IMAGE|规划',
  'EXTRACTED',
  'UPLOAD', '规划建设局业务系统', NULL,
  'PENDING', 'CAT_IMAGE', 'DRAFT',
  '["控规","公示","附图","起步区"]',
  'RAW',
  NULL,
  'sys_admin', '2025-09-03 16:48:00', '2025-09-03 16:48:00'
);

-- 3) 规划意见征集说明 DOCX：已内容理解（与控规附图主题相近，便于相似检索）
INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, media_width, media_height, media_duration_sec,
  feature_json, content_json, fingerprint, meta_status,
  source_type, source_system, source_url,
  index_status, category_code, publish_status, tag_json, process_status,
  linked_doc_id, created_by, created_at, updated_at
) VALUES (
  'SEED_META_DOC_PLAN',
  '起步区控制性详细规划意见征集说明',
  '起步区控规意见征集说明.docx',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'uns/seed/meta/SEED_META_DOC_PLAN.docx',
  152400,
  '面向公众征集起步区控规意见的说明材料，含反馈渠道与截止时间。',
  '王思远',
  'DOCX',
  NULL, NULL, NULL,
  '{"mediaKind":"DOCUMENT","title":"起步区控制性详细规划意见征集说明","format":"DOCX","fileSize":152400,"sampleKeywords":["控规","意见征集","起步区"]}',
  '{"keywords":["控规","公示","意见征集","起步区"],"topics":["规划公示","公众参与"],"sentiment":"NEUTRAL","summary":"《起步区控制性详细规划意见征集说明》关键词：控规、公示、意见征集；主题：规划公示、公众参与；情感倾向：中性"}',
  '控规|公示|意见征集|起步区|规划公示|公众参与|CAT:CAT_GOV_DOC|规划',
  'UNDERSTOOD',
  'UPLOAD', '规划建设局业务系统', NULL,
  'INDEXED', 'CAT_GOV_DOC', 'PUBLISHED',
  '["控规","公示","意见征集","起步区"]',
  'TAGGED',
  NULL,
  'sys_admin', '2025-09-05 10:20:00', '2025-09-06 11:05:00'
);

-- 4) 政务服务热线录音：已内容理解 + 多媒体台账
INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, media_width, media_height, media_duration_sec,
  feature_json, content_json, fingerprint, meta_status,
  source_type, source_system, source_url,
  index_status, category_code, publish_status, tag_json, process_status,
  linked_doc_id, created_by, created_at, updated_at
) VALUES (
  'SEED_META_AUD_HOTLINE',
  '12345政务服务热线典型来电录音（数据共享咨询）',
  'hotline-share-202510.mp3',
  'audio/mpeg',
  'uns/seed/meta/SEED_META_AUD_HOTLINE.mp3',
  4204800,
  '市民咨询部门数据共享目录查询与申请流程的录音样例。',
  '热线坐席组',
  'MP3',
  NULL, NULL, 186,
  '{"mediaKind":"AUDIO","title":"12345政务服务热线典型来电录音（数据共享咨询）","format":"MP3","durationSec":186,"fileSize":4204800}',
  '{"keywords":["热线","数据共享","目录","咨询"],"topics":["政务服务","数据共享"],"sentiment":"NEUTRAL","summary":"热线录音：市民咨询数据共享目录与申请流程。","mediaKind":"AUDIO","mediaInsights":{"durationSec":186,"format":"MP3","scene":"热线咨询","topicHint":"数据共享"}}',
  '热线|数据共享|目录|咨询|政务服务|CAT:CAT_MEDIA_AV|共享',
  'UNDERSTOOD',
  'UPLOAD', '12345 热线系统', NULL,
  'INDEXED', 'CAT_MEDIA_AV', 'PUBLISHED',
  '["热线","数据共享","咨询"]',
  'TAGGED',
  NULL,
  'sys_admin', '2025-10-08 14:12:00', '2025-10-08 15:40:00'
);

-- 5) 园区宣传短视频：待提取（RAW，演示批量特征提取）
INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, media_width, media_height, media_duration_sec,
  feature_json, content_json, fingerprint, meta_status,
  source_type, source_system, source_url,
  index_status, category_code, publish_status, tag_json, process_status,
  linked_doc_id, created_by, created_at, updated_at
) VALUES (
  'SEED_META_VID_PARK',
  '承德高新区智慧园区宣传短视频',
  '智慧园区宣传-202511.mp4',
  'video/mp4',
  'uns/seed/meta/SEED_META_VID_PARK.mp4',
  28512000,
  '园区数字化治理与数据共享成果宣传样片，待提取基本特征与内容理解。',
  '宣传中心',
  'MP4',
  1280, 720, 90,
  NULL,
  NULL,
  NULL,
  'RAW',
  'UPLOAD', '融媒体中心', NULL,
  'PENDING', 'CAT_MEDIA_AV', 'DRAFT',
  NULL,
  'RAW',
  NULL,
  'sys_admin', '2025-11-12 09:00:00', '2025-11-12 09:00:00'
);

-- 相似连接：控规附图 → 意见征集说明；热线录音 → 政务共享通知
UPDATE uns_document d
JOIN uns_document t ON t.doc_code = 'SEED_META_DOC_PLAN'
SET d.linked_doc_id = t.id,
    d.process_status = 'LINKED',
    d.updated_at = NOW()
WHERE d.doc_code = 'SEED_META_IMG_PLAN';

UPDATE uns_document d
JOIN uns_document t ON t.doc_code = 'SEED_META_DOC_SHARE'
SET d.linked_doc_id = t.id,
    d.process_status = 'LINKED',
    d.updated_at = NOW()
WHERE d.doc_code = 'SEED_META_AUD_HOTLINE';
