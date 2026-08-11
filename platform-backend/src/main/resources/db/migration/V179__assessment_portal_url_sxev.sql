-- 考核评估外链改为 /sxev；生产默认 10.10.10.11，本地开发请在门户配置改为 127.0.0.1
-- 清空 menu_path，避免首页按菜单权限裁剪后「配置有、飞出无」

UPDATE portal_nav_node
SET url = 'http://10.10.10.11:18081/sxev/index',
    menu_path = NULL,
    open_mode = 'new_tab',
    sso_mode = 'portal_ticket',
    remark = '考核评估；本地联调改为 http://127.0.0.1:18081/sxev/index'
WHERE id = 132
   OR name = '考核评估系统';
