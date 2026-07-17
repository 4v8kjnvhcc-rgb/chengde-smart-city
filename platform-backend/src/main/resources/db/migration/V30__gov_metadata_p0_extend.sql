-- 元数据 P0～P2：标签/关键字/分级、适配器 JDBC、订阅、变更提醒台账

ALTER TABLE gov_metadata_registry
    ADD COLUMN tags VARCHAR(512) NULL COMMENT '逗号或JSON标签' AFTER description,
    ADD COLUMN keywords VARCHAR(512) NULL COMMENT '检索关键字' AFTER tags,
    ADD COLUMN security_level VARCHAR(32) NULL COMMENT '分级分类' AFTER keywords;

ALTER TABLE gov_om_connector
    ADD COLUMN jdbc_url VARCHAR(512) NULL AFTER source_type,
    ADD COLUMN jdbc_user VARCHAR(128) NULL AFTER jdbc_url,
    ADD COLUMN jdbc_password VARCHAR(256) NULL AFTER jdbc_user,
    ADD COLUMN jdbc_database VARCHAR(128) NULL AFTER jdbc_password;

CREATE TABLE IF NOT EXISTS gov_meta_subscription (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    username        VARCHAR(64)  NOT NULL,
    target_type     VARCHAR(32)  NOT NULL COMMENT 'MODEL/ENTRY',
    target_id       BIGINT       NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gov_meta_sub (user_id, target_type, target_id),
    KEY idx_gov_meta_sub_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据定版订阅';

CREATE TABLE IF NOT EXISTS gov_meta_change_notice (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    entry_id        BIGINT       NOT NULL,
    entry_code      VARCHAR(64)  NULL,
    title           VARCHAR(256) NOT NULL,
    detail          VARCHAR(512) NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'UNREAD',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_gov_meta_notice_entry (entry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据变更提醒';
