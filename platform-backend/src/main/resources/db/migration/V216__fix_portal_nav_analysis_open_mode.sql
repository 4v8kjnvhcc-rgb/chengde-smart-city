-- V216: 部门数据共享 / 领导决策门户改为站内 route 打开
-- 避免 new_tab + 无 /bigdata-web 前缀导致 window.open 落到错误绝对路径

UPDATE portal_nav_node
SET open_mode = 'route'
WHERE id IN (141, 143)
  AND status = 1;
