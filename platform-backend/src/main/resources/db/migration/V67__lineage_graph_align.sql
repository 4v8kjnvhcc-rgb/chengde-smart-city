-- M047 图谱分析：表使用说明、边项目/跨库、节点ID对齐 tbl-{id}

-- ---------- usage_desc ----------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_data_table' AND COLUMN_NAME = 'usage_desc');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_data_table ADD COLUMN usage_desc VARCHAR(512) NULL COMMENT ''表使用说明'' AFTER table_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE ing_data_table
SET usage_desc = CONCAT('用于业务登记与汇聚分析的数据表「', table_name, '」，编码 ', table_code, '。')
WHERE usage_desc IS NULL OR usage_desc = '';

-- ---------- lineage edge 扩展 ----------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_lineage_edge' AND COLUMN_NAME = 'project_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_lineage_edge ADD COLUMN project_id BIGINT NULL COMMENT ''所属项目'' AFTER sort_order', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_lineage_edge' AND COLUMN_NAME = 'from_source_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_lineage_edge ADD COLUMN from_source_id BIGINT NULL COMMENT ''上游数据源'' AFTER project_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_lineage_edge' AND COLUMN_NAME = 'to_source_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_lineage_edge ADD COLUMN to_source_id BIGINT NULL COMMENT ''下游数据源'' AFTER from_source_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 清理旧演示边，重建对齐真实表 ----------
DELETE FROM ing_lineage_edge WHERE from_node IN ('src-mysql', 'tbl-enterprise', 'tbl-person')
   OR to_node IN ('tbl-enterprise', 'tbl-person', 'cat-001', 'cat-002');

-- 数据源 -> 企业主表
INSERT INTO ing_lineage_edge (from_node, to_node, from_label, to_label, edge_type, field_mapping, sort_order, project_id, from_source_id, to_source_id)
SELECT CONCAT('ds-', ds.id), CONCAT('tbl-', t.id), ds.source_name, t.table_name, 'EXTRACT', 'credit_code->ENT', 1,
       ds.project_id, ds.id, ds.id
FROM ing_data_table t
JOIN ing_data_source ds ON ds.id = t.source_id
WHERE t.table_code = 'TBL_ENT_MASTER'
  AND NOT EXISTS (SELECT 1 FROM ing_lineage_edge e WHERE e.to_node = CONCAT('tbl-', t.id) AND e.edge_type = 'EXTRACT');

-- 企业主表 -> 项目建设表（同库）
INSERT INTO ing_lineage_edge (from_node, to_node, from_label, to_label, edge_type, field_mapping, sort_order, project_id, from_source_id, to_source_id)
SELECT CONCAT('tbl-', a.id), CONCAT('tbl-', b.id), a.table_name, b.table_name, 'FLOW', 'ent_code->build_org', 2,
       dsa.project_id, a.source_id, b.source_id
FROM ing_data_table a
JOIN ing_data_table b ON b.table_code = 'TBL_PROJ_CONSTRUCTION'
JOIN ing_data_source dsa ON dsa.id = a.source_id
WHERE a.table_code = 'TBL_ENT_MASTER'
  AND NOT EXISTS (
    SELECT 1 FROM ing_lineage_edge e
    WHERE e.from_node = CONCAT('tbl-', a.id) AND e.to_node = CONCAT('tbl-', b.id)
  );

-- 跨库：企业主表(source23) -> 正向测试表之一(source25)
INSERT INTO ing_lineage_edge (from_node, to_node, from_label, to_label, edge_type, field_mapping, sort_order, project_id, from_source_id, to_source_id)
SELECT CONCAT('tbl-', a.id), CONCAT('tbl-', b.id), a.table_name, b.table_name, 'CROSS_DB', 'ent_name->name', 3,
       dsa.project_id, a.source_id, b.source_id
FROM ing_data_table a
JOIN ing_data_source dsa ON dsa.id = a.source_id
JOIN ing_data_table b ON b.source_id <> a.source_id
JOIN ing_data_source dsb ON dsb.id = b.source_id AND dsb.project_id = dsa.project_id
WHERE a.table_code = 'TBL_ENT_MASTER'
  AND b.id = (SELECT MIN(t2.id) FROM ing_data_table t2 WHERE t2.source_id <> a.source_id)
  AND NOT EXISTS (
    SELECT 1 FROM ing_lineage_edge e
    WHERE e.from_node = CONCAT('tbl-', a.id) AND e.to_node = CONCAT('tbl-', b.id) AND e.edge_type = 'CROSS_DB'
  );

-- 数据源 -> 项目建设
INSERT INTO ing_lineage_edge (from_node, to_node, from_label, to_label, edge_type, field_mapping, sort_order, project_id, from_source_id, to_source_id)
SELECT CONCAT('ds-', ds.id), CONCAT('tbl-', t.id), ds.source_name, t.table_name, 'EXTRACT', NULL, 4,
       ds.project_id, ds.id, ds.id
FROM ing_data_table t
JOIN ing_data_source ds ON ds.id = t.source_id
WHERE t.table_code = 'TBL_PROJ_CONSTRUCTION'
  AND NOT EXISTS (SELECT 1 FROM ing_lineage_edge e WHERE e.to_node = CONCAT('tbl-', t.id) AND e.from_node = CONCAT('ds-', ds.id));

-- ---------- 字段血缘对齐 ----------
DELETE FROM ing_column_lineage WHERE table_node IN ('tbl-enterprise', 'tbl-person');

INSERT INTO ing_column_lineage (table_node, column_code, column_name, upstream_table, upstream_column, downstream_table, downstream_column, sort_order)
SELECT CONCAT('tbl-', t.id), c.column_code, IFNULL(NULLIF(c.column_name, ''), c.column_code),
       CONCAT('ds-', t.source_id), LOWER(c.column_code),
       CONCAT('tbl-', p.id), 'ref_col', 1
FROM ing_data_table t
JOIN ing_data_column c ON c.table_id = t.id
JOIN ing_data_table p ON p.table_code = 'TBL_PROJ_CONSTRUCTION'
WHERE t.table_code = 'TBL_ENT_MASTER'
  AND c.sort_order = (SELECT MIN(c2.sort_order) FROM ing_data_column c2 WHERE c2.table_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM ing_column_lineage x WHERE x.table_node = CONCAT('tbl-', t.id) AND x.column_code = c.column_code);

INSERT INTO ing_column_lineage (table_node, column_code, column_name, upstream_table, upstream_column, downstream_table, downstream_column, sort_order)
SELECT CONCAT('tbl-', t.id), c.column_code, IFNULL(NULLIF(c.column_name, ''), c.column_code),
       CONCAT('tbl-', e.id), 'ENT_CODE', NULL, NULL, 2
FROM ing_data_table t
JOIN ing_data_column c ON c.table_id = t.id
JOIN ing_data_table e ON e.table_code = 'TBL_ENT_MASTER'
WHERE t.table_code = 'TBL_PROJ_CONSTRUCTION'
  AND c.sort_order = (SELECT MIN(c2.sort_order) FROM ing_data_column c2 WHERE c2.table_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM ing_column_lineage x WHERE x.table_node = CONCAT('tbl-', t.id) AND x.column_code = c.column_code);

-- 为其余表补至少一条字段血缘（便于字段页有数据）
INSERT INTO ing_column_lineage (table_node, column_code, column_name, upstream_table, upstream_column, downstream_table, downstream_column, sort_order)
SELECT CONCAT('tbl-', t.id), c.column_code, IFNULL(NULLIF(c.column_name, ''), c.column_code),
       CONCAT('ds-', t.source_id), LOWER(c.column_code), NULL, NULL, 1
FROM ing_data_table t
JOIN ing_data_column c ON c.table_id = t.id
WHERE t.table_code NOT IN ('TBL_ENT_MASTER', 'TBL_PROJ_CONSTRUCTION')
  AND c.sort_order = (SELECT MIN(c2.sort_order) FROM ing_data_column c2 WHERE c2.table_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM ing_column_lineage x WHERE x.table_node = CONCAT('tbl-', t.id));
