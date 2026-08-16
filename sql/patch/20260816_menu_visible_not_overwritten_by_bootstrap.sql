-- 生产/现场：部署后菜单「是否隐藏」被启动脚本改回的修补说明
-- 目标库：smart_city
-- 可重复执行
--
-- 根因：HubSidebarMenuBootstrap 每次启动曾强制 SET visible=0（含 6010、旧 /system/* 等）。
-- 修复后启动脚本不再改 visible；本脚本只恢复常见需展示项，其余请在「菜单管理」按需改「是否隐藏=否」。

-- 1) 业务功能：承德市高新区人口信息库（门户「业务功能平台」依赖此项可见）
UPDATE sys_menu
SET visible = 1, status = 1
WHERE id = 6010
   OR path = '/business/gaoxin-pop-lib';

UPDATE sys_menu
SET status = 1
WHERE id = 6000
   OR path = '/business';

-- 2) 平台管理一级入口
UPDATE sys_menu
SET status = 1
WHERE id = 19
   OR (path = '/system' AND permission = 'hub:system:platform');

-- 说明：Hub 内页（integration_type=hub）的 visible 以菜单管理为准；
-- 若侧栏整组消失，在菜单管理把对应目录/叶子「是否隐藏」改为否即可，重启不会再被改回。
