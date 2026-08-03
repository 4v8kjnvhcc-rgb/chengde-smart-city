-- V108: 门户节点 SSO 模式（门户票据单点登录）

ALTER TABLE portal_nav_node
    ADD COLUMN sso_mode VARCHAR(32) NOT NULL DEFAULT 'none'
        COMMENT 'none / portal_ticket' AFTER open_mode;

-- 考核评估系统：新窗口 + 门户票据 + 本地默认外链（生产请在门户配置中改地址）
UPDATE portal_nav_node
SET url = 'http://127.0.0.1:18081/assessment/index#/dashboard',
    menu_path = '/exchange/application/assessment',
    open_mode = 'new_tab',
    sso_mode = 'portal_ticket',
    remark = '统一门户票据 SSO；生产请改地址为考核域名'
WHERE id = 132;
