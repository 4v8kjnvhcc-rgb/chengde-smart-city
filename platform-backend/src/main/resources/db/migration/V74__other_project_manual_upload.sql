-- 数据源增加业务系统名称；默认初始化「其他」项目 +「手动上传」数据源
-- 一项目可挂多个数据源，每个数据源对应一个系统名称

ALTER TABLE ing_data_source
  ADD COLUMN system_name VARCHAR(128) NULL COMMENT '业务系统名称（同一项目下可有多个）' AFTER source_name;

-- 回填：沿用项目上的系统名
UPDATE ing_data_source ds
INNER JOIN ing_project p ON p.id = ds.project_id
SET ds.system_name = COALESCE(NULLIF(TRIM(p.system_name), ''), ds.source_name)
WHERE ds.system_name IS NULL OR TRIM(ds.system_name) = '';

-- 默认项目：其他 / 系统：其他
INSERT INTO ing_project (project_code, project_name, bound_org_id, system_name, status, created_by)
SELECT 'PRJ_OTHER', '其他', 1, '其他', 'ACTIVE', 'sys_admin'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ing_project WHERE project_code = 'PRJ_OTHER');

-- 若已有 DS_MANUAL_UPLOAD，迁到「其他」项目并规范名称；否则新建
UPDATE ing_data_source ds
INNER JOIN ing_project p ON p.project_code = 'PRJ_OTHER'
SET ds.project_id = p.id,
    ds.source_name = '手动上传',
    ds.source_type = 'FILE',
    ds.system_name = COALESCE(NULLIF(TRIM(ds.system_name), ''), '其他'),
    ds.conn_status = 'OK',
    ds.source_schema = COALESCE(ds.source_schema, 'smart_city_ods'),
    ds.conn_config_json = COALESCE(ds.conn_config_json, '{"channel":"MANUAL_UPLOAD","odsDb":"smart_city_ods"}')
WHERE ds.source_code = 'DS_MANUAL_UPLOAD';

INSERT INTO ing_data_source (
  project_id, source_code, source_name, system_name, source_type,
  conn_status, table_count, conn_config_json, source_schema, sync_status
)
SELECT p.id, 'DS_MANUAL_UPLOAD', '手动上传', '其他', 'FILE',
       'OK', 0, '{"channel":"MANUAL_UPLOAD","odsDb":"smart_city_ods"}', 'smart_city_ods', 'PENDING'
FROM ing_project p
WHERE p.project_code = 'PRJ_OTHER'
  AND NOT EXISTS (SELECT 1 FROM ing_data_source WHERE source_code = 'DS_MANUAL_UPLOAD');
