-- 报告/图谱菜单去掉「（总体）」「（部门）」后缀；统一显示名

UPDATE sys_menu
SET menu_name = '数据资产报告'
WHERE permission IN ('hub:ingestion:register:m046', 'hub:ingestion:register:m046:dept')
   OR id IN (7008, 7018);

UPDATE sys_menu
SET menu_name = '数据资产图谱分析'
WHERE permission IN ('hub:ingestion:register:m047', 'hub:ingestion:register:m047:dept')
   OR id IN (7009, 7019);

-- 部门专用节点仅作权限载体，侧栏不重复展示
UPDATE sys_menu SET visible = 0
WHERE permission IN ('hub:ingestion:register:m046:dept', 'hub:ingestion:register:m047:dept');
