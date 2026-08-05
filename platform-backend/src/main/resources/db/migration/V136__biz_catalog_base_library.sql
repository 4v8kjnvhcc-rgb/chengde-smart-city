-- 门户目录项：基础资源库（信息资源分类 / 六库）维度，与主题、提供方并列
ALTER TABLE biz_catalog_item
  ADD COLUMN base_catalog_code VARCHAR(64) NULL COMMENT '基础资源目录分类编码' AFTER theme_name,
  ADD COLUMN base_catalog_name VARCHAR(128) NULL COMMENT '基础资源目录分类名称' AFTER base_catalog_code;
