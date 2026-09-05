-- 供需对接：需求已挂载目录支持多选（JSON 数组）
ALTER TABLE biz_data_demand
  ADD COLUMN matched_catalog_ids TEXT NULL COMMENT '已挂载门户目录ID列表JSON' AFTER matched_catalog_id;

-- 回填：已有单选目录写入数组
UPDATE biz_data_demand
SET matched_catalog_ids = CONCAT('[', matched_catalog_id, ']')
WHERE matched_catalog_id IS NOT NULL
  AND (matched_catalog_ids IS NULL OR matched_catalog_ids = '');
