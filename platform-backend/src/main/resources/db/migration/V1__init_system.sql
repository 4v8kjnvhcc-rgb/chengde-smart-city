-- MS1 平台基础表（smart_city）
-- 对齐 D12 / D07 §9.1 / D08 MS1

CREATE TABLE IF NOT EXISTS sys_org (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_id       BIGINT       NOT NULL DEFAULT 0,
    org_code        VARCHAR(64)  NOT NULL,
    org_name        VARCHAR(128) NOT NULL,
    org_type        TINYINT      NOT NULL DEFAULT 1 COMMENT '1机构 2部门',
    sort_order      INT          NOT NULL DEFAULT 0,
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_org_code (org_code),
    KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username            VARCHAR(64)  NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    display_name        VARCHAR(128) NULL,
    org_id              BIGINT       NOT NULL,
    email               VARCHAR(128) NULL,
    phone               VARCHAR(32)  NULL,
    totp_secret         VARCHAR(128) NULL,
    totp_enabled        TINYINT      NOT NULL DEFAULT 0,
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    failed_login_count  INT          NOT NULL DEFAULT 0,
    locked_until        DATETIME     NULL,
    password_changed_at DATETIME     NULL,
    last_login_at       DATETIME     NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username),
    KEY idx_org_id (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(128) NOT NULL,
    role_type   TINYINT      NOT NULL DEFAULT 2 COMMENT '1系统 2业务',
    description VARCHAR(255) NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_menu (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_id        BIGINT       NOT NULL DEFAULT 0,
    menu_name        VARCHAR(128) NOT NULL,
    menu_type        TINYINT      NOT NULL COMMENT '1目录 2菜单 3按钮',
    path             VARCHAR(255) NULL,
    component        VARCHAR(255) NULL,
    permission       VARCHAR(128) NULL,
    icon             VARCHAR(64)  NULL,
    sort_order       INT          NOT NULL DEFAULT 0,
    visible          TINYINT      NOT NULL DEFAULT 1,
    status           TINYINT      NOT NULL DEFAULT 1,
    m_code           VARCHAR(16)  NULL COMMENT 'Mxxx 验收编号',
    integration_type VARCHAR(32)  NULL COMMENT 'self/om/de/iframe/esb/placeholder',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_parent (parent_id),
    KEY idx_permission (permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NULL,
    username      VARCHAR(64)  NULL,
    org_id        BIGINT       NULL,
    action        VARCHAR(64)  NOT NULL,
    resource_type VARCHAR(64)  NULL,
    resource_id   VARCHAR(64)  NULL,
    detail        TEXT         NULL,
    ip_address    VARCHAR(64)  NULL,
    user_agent    VARCHAR(512) NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_created (created_at),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_security_config (
    config_key   VARCHAR(64)  NOT NULL PRIMARY KEY,
    config_value VARCHAR(512) NOT NULL,
    description  VARCHAR(255) NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cross_dept_access_request (
    id                 BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_user_id  BIGINT   NOT NULL,
    applicant_org_id   BIGINT   NOT NULL,
    target_org_id      BIGINT   NOT NULL,
    resource_type      VARCHAR(64) NULL,
    resource_id        VARCHAR(64) NULL,
    reason             TEXT     NULL,
    status             TINYINT  NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2拒绝',
    approver_user_id   BIGINT   NULL,
    approved_at        DATETIME NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_applicant (applicant_user_id),
    KEY idx_target_org (target_org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
