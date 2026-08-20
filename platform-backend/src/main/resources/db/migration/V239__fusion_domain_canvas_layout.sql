-- 数据仓库建设：可视化建模画布布局持久化
ALTER TABLE gov_fusion_domain
  ADD COLUMN canvas_layout MEDIUMTEXT NULL COMMENT '可视化建模画布布局 JSON' AFTER description;
