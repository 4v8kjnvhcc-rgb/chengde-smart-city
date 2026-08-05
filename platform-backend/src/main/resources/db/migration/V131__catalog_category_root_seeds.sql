-- 为归集(INGEST)与治理(GOVERNANCE)分别种子化三类顶级资源目录，便于其下再建下级分类
-- category_code 全局唯一，故按来源加前缀

INSERT INTO gov_catalog_category (
  category_code, category_name, catalog_origin, parent_id, category_path, sort_order, status, secret_flag, created_by
)
SELECT v.category_code, v.category_name, v.catalog_origin, 0, v.category_name, v.sort_order, 'ACTIVE', 0, 'sys_admin'
FROM (
  SELECT 'CAT_INGEST_BASE' AS category_code, '基础资源目录' AS category_name, 'INGEST' AS catalog_origin, 1 AS sort_order
  UNION ALL SELECT 'CAT_INGEST_DEPT', '部门资源目录', 'INGEST', 2
  UNION ALL SELECT 'CAT_INGEST_THEME', '主题资源目录', 'INGEST', 3
  UNION ALL SELECT 'CAT_GOV_BASE', '基础资源目录', 'GOVERNANCE', 1
  UNION ALL SELECT 'CAT_GOV_DEPT', '部门资源目录', 'GOVERNANCE', 2
  UNION ALL SELECT 'CAT_GOV_THEME', '主题资源目录', 'GOVERNANCE', 3
) v
WHERE NOT EXISTS (
  SELECT 1 FROM gov_catalog_category c WHERE c.category_code = v.category_code
);
