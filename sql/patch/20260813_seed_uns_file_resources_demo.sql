-- 非结构化文件资源：两条仿真登记数据（可重复执行）
-- 目标库：smart_city；先清 SEED_UNS_* 旧演示，再写入 2 条

DELETE FROM uns_document WHERE doc_code LIKE 'SEED_UNS_%';

INSERT INTO uns_doc_category (category_code, category_name, parent_id, media_type, description, sort_order, status)
SELECT 'CAT_GOV_DOC', '政务公文', NULL, 'DOCUMENT', '公文、通知、政策文件', 10, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_doc_category WHERE category_code = 'CAT_GOV_DOC');

INSERT INTO uns_doc_category (category_code, category_name, parent_id, media_type, description, sort_order, status)
SELECT 'CAT_IMAGE', '图片影像', NULL, 'IMAGE', '现场影像、扫描件、图纸', 20, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM uns_doc_category WHERE category_code = 'CAT_IMAGE');

INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, meta_status, source_type, source_system, source_url,
  index_status, category_code, publish_status, tag_json, process_status, created_by, created_at, updated_at
)
SELECT
  'DOC_GZGK_20250812',
  '承德高新区管委会关于推进政务数据共享工作的通知',
  '承高新管发〔2025〕48号.pdf',
  'application/pdf',
  'uns/2025/08/DOC_GZGK_20250812.pdf',
  286720,
  '明确各部门数据共享责任、目录更新周期及对接大数据平台时限要求。',
  '张明远',
  'PDF',
  'EXTRACTED',
  'UPLOAD',
  'OA 办公系统',
  NULL,
  'INDEXED',
  'CAT_GOV_DOC',
  'PUBLISHED',
  '["政务数据","共享","通知"]',
  'TAGGED',
  'sys_admin',
  '2025-08-12 09:36:00',
  '2025-08-15 14:22:00'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uns_document d WHERE d.doc_code = 'DOC_GZGK_20250812');

INSERT INTO uns_document (
  doc_code, title, original_file_name, content_type, storage_key, file_size, description,
  author, media_format, meta_status, source_type, source_system, source_url,
  index_status, category_code, publish_status, tag_json, process_status, created_by, created_at, updated_at
)
SELECT
  'DOC_GHGS_20250903',
  '高新区起步区控制性详细规划公示附图',
  '起步区控规公示附图-202509.png',
  'image/png',
  'uns/2025/09/DOC_GHGS_20250903.png',
  1843200,
  '起步区用地性质、道路红线及公共服务设施布局公示附图，用于规划意见征集。',
  '李婉清',
  'PNG',
  'RAW',
  'UPLOAD',
  '规划建设局业务系统',
  NULL,
  'PENDING',
  'CAT_IMAGE',
  'DRAFT',
  '["控规","公示","附图"]',
  'RAW',
  'sys_admin',
  '2025-09-03 16:48:00',
  '2025-09-03 16:48:00'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM uns_document d WHERE d.doc_code = 'DOC_GHGS_20250903');

SELECT doc_code, title, category_code, publish_status, index_status, source_system, original_file_name
FROM uns_document
WHERE doc_code IN ('DOC_GZGK_20250812', 'DOC_GHGS_20250903')
ORDER BY doc_code;
