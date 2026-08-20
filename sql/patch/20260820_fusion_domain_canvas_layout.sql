-- 数据仓库建设：可视化建模画布布局持久化（手工补丁，可重复执行）
-- 目标库：smart_city（控制面）
-- 对应 Flyway：V239__fusion_domain_canvas_layout.sql

SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gov_fusion_domain'
    AND COLUMN_NAME = 'canvas_layout'
);

SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE gov_fusion_domain ADD COLUMN canvas_layout MEDIUMTEXT NULL COMMENT ''可视化建模画布布局 JSON'' AFTER description',
  'SELECT ''canvas_layout already exists'' AS info'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
