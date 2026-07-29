-- 系统管理侧栏不再展示「访问控制」（能力已迁至归集平台 · 访问控制管理）
UPDATE sys_menu
SET status = 0, visible = 0
WHERE id = 6403
   OR path = '/system/access';
