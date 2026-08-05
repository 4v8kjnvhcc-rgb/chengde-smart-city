-- 非结构化元数据管理：基本特征、内容理解、标签与相似性支撑字段

ALTER TABLE uns_document
  ADD COLUMN author VARCHAR(128) NULL COMMENT '作者/责任人' AFTER description,
  ADD COLUMN media_format VARCHAR(32) NULL COMMENT '文件格式扩展名' AFTER author,
  ADD COLUMN media_width INT NULL COMMENT '图片/视频宽度' AFTER media_format,
  ADD COLUMN media_height INT NULL COMMENT '图片/视频高度' AFTER media_width,
  ADD COLUMN media_duration_sec INT NULL COMMENT '音视频时长（秒）' AFTER media_height,
  ADD COLUMN feature_json TEXT NULL COMMENT '基本特征提取结果 JSON' AFTER media_duration_sec,
  ADD COLUMN content_json TEXT NULL COMMENT '内容理解结果 JSON（关键词/主题/情感）' AFTER feature_json,
  ADD COLUMN fingerprint VARCHAR(512) NULL COMMENT '相似性指纹（标签+关键词+标题）' AFTER content_json,
  ADD COLUMN meta_status VARCHAR(32) NOT NULL DEFAULT 'RAW' COMMENT 'RAW|EXTRACTED|UNDERSTOOD' AFTER fingerprint,
  ADD KEY idx_uns_doc_meta_status (meta_status),
  ADD KEY idx_uns_doc_media_format (media_format);

UPDATE uns_document
SET media_format = UPPER(SUBSTRING_INDEX(COALESCE(original_file_name, title, ''), '.', -1))
WHERE (media_format IS NULL OR media_format = '')
  AND COALESCE(original_file_name, title, '') LIKE '%.%';

UPDATE uns_document
SET meta_status = CASE
  WHEN tag_json IS NOT NULL AND tag_json <> '' AND tag_json <> '[]' THEN 'EXTRACTED'
  ELSE 'RAW'
END
WHERE meta_status IS NULL OR meta_status = '' OR meta_status = 'RAW';
