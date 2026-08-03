-- V114: 门户节点 SSO 模式（门户票据单点登录；兼容本机已用 V108 加过列的库）

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'portal_nav_node'
      AND COLUMN_NAME = 'sso_mode'
);
SET @sql := IF(
    @col_exists = 0,
    'ALTER TABLE portal_nav_node ADD COLUMN sso_mode VARCHAR(32) NOT NULL DEFAULT ''none'' COMMENT ''none / portal_ticket'' AFTER open_mode',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 考核评估系统：新窗口 + 门户票据 + 本地默认外链（生产请在门户配置中改地址）
UPDATE portal_nav_node
SET url = 'http://127.0.0.1:18081/assessment/index#/dashboard',
    menu_path = '/exchange/application/assessment',
    open_mode = 'new_tab',
    sso_mode = 'portal_ticket',
    remark = '统一门户票据 SSO；生产请改地址为考核域名'
WHERE id = 132;
