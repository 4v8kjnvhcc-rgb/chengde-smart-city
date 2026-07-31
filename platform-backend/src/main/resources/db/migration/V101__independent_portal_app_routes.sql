-- R36: 六入口独立路径（禁止同页切换）
-- 父级仅落地分发；子入口各走独立路由

UPDATE sys_menu
SET path = '/exchange/analysis-portal',
    menu_type = 2,
    menu_name = '应用分析门户',
    status = 1,
    sort_order = 6
WHERE id = 7;

UPDATE sys_menu
SET path = '/exchange/application',
    menu_type = 2,
    menu_name = '应用平台',
    status = 1,
    sort_order = 7
WHERE id = 6;

UPDATE sys_menu
SET path = '/exchange/analysis-portal/dept',
    menu_name = '部门数据共享门户',
    parent_id = 7,
    status = 1,
    sort_order = 1
WHERE id = 7120;

UPDATE sys_menu
SET path = '/exchange/analysis-portal/leader',
    menu_name = '领导决策门户',
    parent_id = 7,
    status = 1,
    sort_order = 2
WHERE id = 7121;

UPDATE sys_menu
SET path = '/exchange/application/supply',
    menu_name = '数据供需对接系统',
    parent_id = 6,
    status = 1,
    sort_order = 1
WHERE id = 7130;

UPDATE sys_menu
SET path = '/exchange/application/assessment',
    menu_name = '考核评估系统',
    parent_id = 6,
    status = 1,
    sort_order = 2
WHERE id = 7131;

UPDATE sys_menu
SET path = '/exchange/application/stats-base',
    menu_name = '基础库统计分析应用',
    parent_id = 6,
    status = 1,
    sort_order = 3
WHERE id = 7132;

UPDATE sys_menu
SET path = '/exchange/application/stats-domain',
    menu_name = '重点领域统计分析应用',
    parent_id = 6,
    status = 1,
    sort_order = 4
WHERE id = 7133;
