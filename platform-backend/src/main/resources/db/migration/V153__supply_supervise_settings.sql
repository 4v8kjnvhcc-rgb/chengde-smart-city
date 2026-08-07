-- 供需督查督办时限配置（自然日）+ 需求确认/反馈截止字段
CREATE TABLE IF NOT EXISTS biz_supply_setting (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    setting_key   VARCHAR(64)  NOT NULL,
    setting_value VARCHAR(128) NOT NULL,
    description   VARCHAR(255) NULL,
    updated_at    DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_biz_supply_setting_key (setting_key)
) COMMENT='供需对接系统配置（督查督办时限等）';

INSERT INTO biz_supply_setting (setting_key, setting_value, description)
SELECT 'response_deadline_days', '10', '确认/反馈时限（自然日）：分发或督办后，数据提供/需求部门须在 N 天内确认或反馈'
WHERE NOT EXISTS (SELECT 1 FROM biz_supply_setting WHERE setting_key = 'response_deadline_days');

INSERT INTO biz_supply_setting (setting_key, setting_value, description)
SELECT 'mount_deadline_days', '10', '挂载门户时限（自然日）：数源部门同意提供后，须在 N 天内将目录挂载到门户'
WHERE NOT EXISTS (SELECT 1 FROM biz_supply_setting WHERE setting_key = 'mount_deadline_days');

ALTER TABLE biz_data_demand
    ADD COLUMN response_deadline DATETIME NULL COMMENT '确认/反馈截止（自然日，分发或督办起算）' AFTER supervise_by;
