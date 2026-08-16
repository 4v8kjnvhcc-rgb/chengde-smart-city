-- 人口信息库菜单：允许门户展示（visible=1）。启动脚本不再覆盖该字段。
-- 目标库：smart_city；可重复执行。
UPDATE sys_menu
SET visible = 1
WHERE id = 6010
   OR path = '/business/gaoxin-pop-lib';
