-- 治理侧(GOVERNANCE)补齐与归集侧同结构的一级分类；清理重复/垃圾根节点
-- 归集与治理共用同一套编目 UI，靠 catalog_origin 隔离数据，两侧均需有可用的下一级选项

-- 1) 删除无下级、非种子码的重复「基础资源目录」及垃圾根
DELETE FROM gov_catalog_category
WHERE catalog_origin = 'GOVERNANCE'
  AND parent_id = 0
  AND category_code NOT IN ('CAT_GOV_BASE', 'CAT_GOV_DEPT', 'CAT_GOV_THEME')
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT id, parent_id FROM gov_catalog_category) t
    WHERE t.parent_id = gov_catalog_category.id
  );

-- 2) 在 GOVERNANCE 三类根下种子一级子分类（幂等：按 category_code）
INSERT INTO gov_catalog_category (
  category_code, category_name, catalog_origin, parent_id, category_path, sort_order, status, secret_flag, created_by
)
SELECT
  v.category_code,
  v.category_name,
  'GOVERNANCE',
  p.id,
  CONCAT(p.category_name, '/', v.category_name),
  v.sort_order,
  'ACTIVE',
  0,
  'sys_admin'
FROM (
  SELECT 'CAT_GOV_BASE_RENKOU' AS category_code, '人口库' AS category_name, 'CAT_GOV_BASE' AS parent_code, 1 AS sort_order
  UNION ALL SELECT 'CAT_GOV_BASE_CREDIT', '公共信用库', 'CAT_GOV_BASE', 2
  UNION ALL SELECT 'CAT_GOV_BASE_MACRO', '宏观经济库', 'CAT_GOV_BASE', 3
  UNION ALL SELECT 'CAT_GOV_BASE_GEO', '空间地理库', 'CAT_GOV_BASE', 4
  UNION ALL SELECT 'CAT_GOV_BASE_LICENSE', '电子证照库', 'CAT_GOV_BASE', 5
  UNION ALL SELECT 'CAT_GOV_BASE_LEGAL', '法人库', 'CAT_GOV_BASE', 6
  UNION ALL SELECT 'CAT_GOV_THEME_SAFE', '安全生产监督', 'CAT_GOV_THEME', 1
  UNION ALL SELECT 'CAT_GOV_THEME_DRUG', '药品安全保障', 'CAT_GOV_THEME', 2
  UNION ALL SELECT 'CAT_GOV_THEME_SOCIAL', '全民社会保障', 'CAT_GOV_THEME', 3
  UNION ALL SELECT 'CAT_GOV_THEME_HOUSING', '全民住房保障', 'CAT_GOV_THEME', 4
  UNION ALL SELECT 'CAT_GOV_THEME_HEALTH', '全民健康保障', 'CAT_GOV_THEME', 5
) v
INNER JOIN gov_catalog_category p ON p.category_code = v.parent_code AND p.catalog_origin = 'GOVERNANCE'
WHERE NOT EXISTS (
  SELECT 1 FROM gov_catalog_category c WHERE c.category_code = v.category_code
);
