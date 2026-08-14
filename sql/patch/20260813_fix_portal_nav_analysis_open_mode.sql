-- 手工补丁（可重复执行）：应用分析门户子入口改为站内 route
-- 目标库：smart_city（生产 10.10.10.56:13306 / 本地同源）
-- 对应 Flyway：V216__fix_portal_nav_analysis_open_mode.sql
--
-- 根因：141/143 的 url 已对齐为 /exchange/analysis-portal/...（无 /bigdata-web），
-- 但 open_mode 仍为 new_tab；首页 window.open 相对路径不带 publicPath，新标签打不开。
-- 修复：站内入口改用 open_mode=route，走 Vue Router（自动带 /bigdata-web）。
--
-- 执行后：刷新首页或重新登录即可（清门户导航前端缓存）。

UPDATE portal_nav_node
SET open_mode = 'route'
WHERE id IN (141, 143)
  AND status = 1;

-- 验收
SELECT id, name, menu_path, url, open_mode
FROM portal_nav_node
WHERE id IN (141, 143)
ORDER BY id;
