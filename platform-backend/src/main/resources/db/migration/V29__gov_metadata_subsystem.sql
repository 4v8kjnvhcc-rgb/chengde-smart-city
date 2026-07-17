-- M089～M096 元数据子系统加深：元模型 / 采集任务与运行 / 版本 / 关联边；扩展 registry

ALTER TABLE gov_metadata_registry
    ADD COLUMN model_id BIGINT NULL COMMENT '关联元模型' AFTER om_ref,
    ADD COLUMN run_id BIGINT NULL COMMENT '最近采集运行' AFTER model_id,
    ADD COLUMN description VARCHAR(512) NULL AFTER run_id,
    ADD COLUMN change_flag VARCHAR(32) NULL COMMENT 'NEW/CHANGED/UNCHANGED' AFTER description,
    ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER status;

CREATE TABLE IF NOT EXISTS gov_meta_model (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_code      VARCHAR(64)  NOT NULL,
    model_name_zh   VARCHAR(128) NOT NULL,
    model_name_en   VARCHAR(128) NULL,
    model_type      VARCHAR(64)  NOT NULL DEFAULT 'TABLE',
    data_length     INT          NULL,
    required_flag   TINYINT      NOT NULL DEFAULT 0,
    component_type  VARCHAR(64)  NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE',
    content_json    TEXT         NULL COMMENT '字段定义JSON',
    published_at    DATETIME     NULL,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gov_meta_model_code (model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元模型 M089';

CREATE TABLE IF NOT EXISTS gov_meta_collect_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_code       VARCHAR(64)  NOT NULL,
    task_name       VARCHAR(128) NOT NULL,
    connector_id    BIGINT       NOT NULL,
    model_id        BIGINT       NULL COMMENT '须已发布模型',
    cron_expr       VARCHAR(64)  NULL,
    scope_type      VARCHAR(32)  NOT NULL DEFAULT 'FULL' COMMENT 'FULL/TABLE',
    table_list      VARCHAR(1024) NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'READY' COMMENT 'READY/RUNNING/STOPPED',
    last_run_at     DATETIME     NULL,
    last_message    VARCHAR(512) NULL,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gov_meta_collect_task_code (task_code),
    KEY idx_gov_meta_collect_connector (connector_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据采集任务 M090';

CREATE TABLE IF NOT EXISTS gov_meta_collect_run (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         BIGINT       NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED/STOPPED',
    started_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at        DATETIME     NULL,
    table_count     INT          NULL,
    summary         VARCHAR(512) NULL,
    log_text        TEXT         NULL,
    KEY idx_gov_meta_collect_run_task (task_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据采集运行日志 M091';

CREATE TABLE IF NOT EXISTS gov_meta_version (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_type     VARCHAR(32)  NOT NULL COMMENT 'MODEL/ENTRY',
    target_id       BIGINT       NOT NULL,
    version_no      INT          NOT NULL DEFAULT 1,
    snapshot_json   TEXT         NULL,
    change_summary  VARCHAR(512) NULL,
    created_by      VARCHAR(64)  NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_gov_meta_version_target (target_type, target_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据版本快照 M093';

CREATE TABLE IF NOT EXISTS gov_meta_relation (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    from_code       VARCHAR(64)  NOT NULL,
    to_code         VARCHAR(64)  NOT NULL,
    relation_type   VARCHAR(32)  NOT NULL COMMENT 'FK/LINEAGE/IMPACT/ASSOC',
    label           VARCHAR(128) NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_gov_meta_rel_from (from_code),
    KEY idx_gov_meta_rel_to (to_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据关联/血缘/影响边 M096';

INSERT INTO gov_meta_model (model_code, model_name_zh, model_name_en, model_type, data_length, required_flag, component_type, status, content_json, published_at, created_by)
VALUES
('MM_PERSON_BASE', '人员基础信息模型', 'PersonBase', 'TABLE', NULL, 1, 'FORM', 'PUBLISHED',
 '[{"code":"id_card","name":"证件号","type":"VARCHAR","length":18,"required":true},{"code":"name","name":"姓名","type":"VARCHAR","length":64,"required":true}]',
 NOW(), 'system'),
('MM_ORG_DRAFT', '机构草稿模型', 'OrgDraft', 'TABLE', NULL, 0, 'FORM', 'DRAFT',
 '[{"code":"org_code","name":"机构编码","type":"VARCHAR","length":32,"required":true}]',
 NULL, 'system');

INSERT INTO gov_meta_collect_task (task_code, task_name, connector_id, model_id, cron_expr, scope_type, status, last_message, created_by)
SELECT 'MCT_MYSQL_DEMO', '演示库元数据采集', c.id, m.id, '0 0 2 * * ?', 'FULL', 'READY', 'seed ready', 'system'
FROM gov_om_connector c
JOIN gov_meta_model m ON m.model_code = 'MM_PERSON_BASE'
WHERE c.connector_code = 'OM_CONN_MYSQL_DEMO'
LIMIT 1;

INSERT INTO gov_meta_relation (from_code, to_code, relation_type, label, status) VALUES
('META_DS_DEMO', 'META_CAT_ASSET', 'LINEAGE', '采集产出', 'ACTIVE'),
('META_CAT_ASSET', 'META_LINEAGE_01', 'LINEAGE', '血缘下游', 'ACTIVE'),
('META_CAT_ASSET', 'META_LINEAGE_01', 'IMPACT', '影响分析', 'ACTIVE'),
('META_DS_DEMO', 'META_CAT_ASSET', 'ASSOC', '主外键关联', 'ACTIVE');
