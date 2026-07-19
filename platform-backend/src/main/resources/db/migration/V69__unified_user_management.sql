-- 统一用户管理系统（3.1.1 / M139～M145）：应用授权、服务调用与审批台账；系统管理侧菜单

CREATE TABLE IF NOT EXISTS sys_app_grant (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    app_id        BIGINT       NOT NULL COMMENT 'ana_platform_app.id',
    grantee_type  VARCHAR(16)  NOT NULL COMMENT 'USER/ROLE',
    grantee_id    BIGINT       NOT NULL,
    perm          VARCHAR(32)  NOT NULL DEFAULT 'ACCESS' COMMENT 'ACCESS/ADMIN',
    granted_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_grantee (app_id, grantee_type, grantee_id),
    KEY idx_app_grant_grantee (grantee_type, grantee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用中心-用户/角色应用授权';

CREATE TABLE IF NOT EXISTS sys_service_call_stat (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    service_id    BIGINT       NOT NULL COMMENT 'ana_platform_service.id',
    call_date     DATE         NOT NULL,
    call_count    INT          NOT NULL DEFAULT 0,
    success_count INT          NOT NULL DEFAULT 0,
    fail_count    INT          NOT NULL DEFAULT 0,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_svc_day (service_id, call_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务中心-调用统计按日';

CREATE TABLE IF NOT EXISTS sys_service_approval (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    service_id      BIGINT       NOT NULL,
    applicant_id    BIGINT       NOT NULL,
    reason          VARCHAR(512) NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    approver_id     BIGINT       NULL,
    approve_comment VARCHAR(512) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at     DATETIME     NULL,
    KEY idx_svc_appr_status (status),
    KEY idx_svc_appr_svc (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务中心-敏感调用审批';

-- 演示：为已有应用/服务补授权与统计种子（若存在）
INSERT INTO sys_app_grant (app_id, grantee_type, grantee_id, perm, granted_by)
SELECT a.id, 'ROLE', 1, 'ADMIN', 1
FROM ana_platform_app a
WHERE NOT EXISTS (
    SELECT 1 FROM sys_app_grant g WHERE g.app_id = a.id AND g.grantee_type = 'ROLE' AND g.grantee_id = 1
)
LIMIT 5;

INSERT INTO sys_service_call_stat (service_id, call_date, call_count, success_count, fail_count)
SELECT s.id, CURDATE(), 120, 115, 5
FROM ana_platform_service s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_service_call_stat st WHERE st.service_id = s.id AND st.call_date = CURDATE()
)
LIMIT 5;

INSERT INTO sys_service_call_stat (service_id, call_date, call_count, success_count, fail_count)
SELECT s.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 98, 96, 2
FROM ana_platform_service s
WHERE NOT EXISTS (
    SELECT 1 FROM sys_service_call_stat st WHERE st.service_id = s.id AND st.call_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY)
)
LIMIT 5;

-- 系统管理侧：统一用户管理入口（Hub，七中心在页内切换）
INSERT INTO sys_menu (parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 19, '统一用户管理', 2, '/system/uum', 'system/UnifiedUserHubView', 'system:uum:view', 'User', 18, 'M139', 'self'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path = '/system/uum');

-- 赋权给系统管理员与机构管理员
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.path = '/system/uum'
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.id FROM sys_menu m
WHERE m.path = '/system/uum'
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id);

-- 认证中心扩展配置键（若不存在）
INSERT INTO ana_platform_config (config_key, config_value, config_group, description)
SELECT 'auth.methods', 'PASSWORD,TOTP', 'AUTH', '支持的认证方式：PASSWORD/TOTP/SMS（短信二期）/BIOMETRIC（指纹扩展位）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ana_platform_config WHERE config_key = 'auth.methods');

INSERT INTO ana_platform_config (config_key, config_value, config_group, description)
SELECT 'auth.sso.enabled', 'false', 'AUTH', '统一认证/SSO 开关（一期 JWT；打开后走扩展位）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ana_platform_config WHERE config_key = 'auth.sso.enabled');

INSERT INTO ana_platform_config (config_key, config_value, config_group, description)
SELECT 'auth.user.sync.enabled', 'false', 'AUTH', '与政务其它系统用户同步开关（扩展位）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ana_platform_config WHERE config_key = 'auth.user.sync.enabled');

INSERT INTO ana_platform_config (config_key, config_value, config_group, description)
SELECT 'system.params.notice', '统一用户管理系统运行中', 'SYSTEM', '系统参数-运行公告'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ana_platform_config WHERE config_key = 'system.params.notice');
