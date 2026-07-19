-- 将全局「其他」迁为按部门：PRJ_OTHER_{orgId} / DS_MANUAL_UPLOAD_{orgId}
-- 运行时按登录部门懒创建，互不冲突；系统管理员可见全部

-- 1) 旧全局项目 → 绑定 org=1 的部门「其他」
UPDATE ing_project
SET project_code = 'PRJ_OTHER_1',
    project_name = '其他',
    system_name = COALESCE(NULLIF(TRIM(system_name), ''), '其他'),
    bound_org_id = 1
WHERE project_code = 'PRJ_OTHER';

-- 2) 旧全局手动上传数据源 → 部门 1
UPDATE ing_data_source
SET source_code = 'DS_MANUAL_UPLOAD_1',
    source_name = COALESCE(NULLIF(TRIM(source_name), ''), '手动上传'),
    system_name = COALESCE(NULLIF(TRIM(system_name), ''), '其他')
WHERE source_code = 'DS_MANUAL_UPLOAD';

-- 确保 DS_MANUAL_UPLOAD_1 挂在 PRJ_OTHER_1 下
UPDATE ing_data_source ds
INNER JOIN ing_project p ON p.project_code = 'PRJ_OTHER_1'
SET ds.project_id = p.id
WHERE ds.source_code = 'DS_MANUAL_UPLOAD_1';
