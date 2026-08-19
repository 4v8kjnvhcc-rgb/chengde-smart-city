-- 扩展 uns_document 的 tag_json 和 fingerprint 字段长度，避免特征提取/内容理解写入时截断报错
ALTER TABLE uns_document
  MODIFY COLUMN tag_json TEXT NULL COMMENT '标签 JSON 数组',
  MODIFY COLUMN fingerprint TEXT NULL COMMENT '相似性指纹（标签+关键词+标题）';
