-- V160: 集群台账不再强制账号/密码（表单已移除）
ALTER TABLE sys_cluster_account
    MODIFY COLUMN account_name VARCHAR(128) NULL COMMENT '账号（可选，历史兼容）',
    MODIFY COLUMN account_password VARCHAR(256) NULL COMMENT '密码（可选，历史兼容）';
