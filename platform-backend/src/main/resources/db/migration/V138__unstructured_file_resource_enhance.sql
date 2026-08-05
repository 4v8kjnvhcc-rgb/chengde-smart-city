-- 非结构化文件资源管理、分类与多维检索增强

ALTER TABLE uns_doc_category
  ADD COLUMN parent_id BIGINT NULL COMMENT '父分类ID' AFTER category_name,
  ADD COLUMN description VARCHAR(512) NULL COMMENT '分类说明' AFTER media_type,
  ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT '排序号' AFTER description,
  ADD KEY idx_uns_category_parent (parent_id);

ALTER TABLE uns_document
  ADD COLUMN original_file_name VARCHAR(256) NULL COMMENT '原始文件名' AFTER title,
  ADD COLUMN file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小（字节）' AFTER storage_key,
  ADD COLUMN description VARCHAR(1000) NULL COMMENT '文件描述' AFTER file_size,
  ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'UPLOAD' COMMENT 'UPLOAD|EXTERNAL' AFTER description,
  ADD COLUMN source_system VARCHAR(128) NULL COMMENT '来源业务平台' AFTER source_type,
  ADD COLUMN source_url VARCHAR(1000) NULL COMMENT '外部资源地址' AFTER source_system,
  ADD KEY idx_uns_doc_content_type (content_type),
  ADD KEY idx_uns_doc_file_size (file_size),
  ADD KEY idx_uns_doc_created_at (created_at),
  ADD KEY idx_uns_doc_updated_at (updated_at);

UPDATE uns_document
SET original_file_name = title
WHERE original_file_name IS NULL OR original_file_name = '';
