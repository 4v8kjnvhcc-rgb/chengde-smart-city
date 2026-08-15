-- 补强表血缘：人口表抽取边 + 字段血缘样例（若存在 TBL_PERSON）
-- 目标库: smart_city；可重复执行

INSERT INTO ing_lineage_edge (from_node, to_node, from_label, to_label, edge_type, field_mapping, sort_order, project_id, from_source_id, to_source_id)
SELECT CONCAT('ds-', ds.id), CONCAT('tbl-', t.id), ds.source_name, t.table_name, 'EXTRACT', 'id_no->PERSON', 10,
       ds.project_id, ds.id, ds.id
FROM ing_data_table t
JOIN ing_data_source ds ON ds.id = t.source_id
WHERE t.table_code = 'TBL_PERSON'
  AND NOT EXISTS (
    SELECT 1 FROM ing_lineage_edge e
    WHERE e.from_node = CONCAT('ds-', ds.id) AND e.to_node = CONCAT('tbl-', t.id) AND e.edge_type = 'EXTRACT'
  );

-- 人口表 -> 企业主表（示意跨主题关联，同项目）
INSERT INTO ing_lineage_edge (from_node, to_node, from_label, to_label, edge_type, field_mapping, sort_order, project_id, from_source_id, to_source_id)
SELECT CONCAT('tbl-', p.id), CONCAT('tbl-', e.id), p.table_name, e.table_name, 'FLOW', 'region->district', 11,
       dsp.project_id, p.source_id, e.source_id
FROM ing_data_table p
JOIN ing_data_source dsp ON dsp.id = p.source_id
JOIN ing_data_table e ON e.table_code = 'TBL_ENT_MASTER'
JOIN ing_data_source dse ON dse.id = e.source_id AND dse.project_id = dsp.project_id
WHERE p.table_code = 'TBL_PERSON'
  AND NOT EXISTS (
    SELECT 1 FROM ing_lineage_edge x
    WHERE x.from_node = CONCAT('tbl-', p.id) AND x.to_node = CONCAT('tbl-', e.id)
  );

INSERT INTO ing_column_lineage (table_node, column_code, column_name, upstream_table, upstream_column, downstream_table, downstream_column, sort_order)
SELECT CONCAT('tbl-', t.id), c.column_code, IFNULL(NULLIF(c.column_name, ''), c.column_code),
       CONCAT('ds-', t.source_id), LOWER(c.column_code),
       NULL, NULL, 20
FROM ing_data_table t
JOIN ing_data_column c ON c.table_id = t.id
WHERE t.table_code = 'TBL_PERSON'
  AND c.column_code IN ('ID_NO', 'PERSON_NAME', 'ID_CARD', 'NAME')
  AND NOT EXISTS (
    SELECT 1 FROM ing_column_lineage cl
    WHERE cl.table_node = CONCAT('tbl-', t.id) AND cl.column_code = c.column_code
  );
