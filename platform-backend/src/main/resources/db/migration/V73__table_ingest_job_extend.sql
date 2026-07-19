-- 库表接入任务扩展：单表/多表/条件 SQL + 调度开关 + 增量水位
ALTER TABLE ing_ingest_task
    ADD COLUMN access_mode VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT 'SINGLE|MULTI|SQL' AFTER channel_id,
    ADD COLUMN config_json TEXT NULL COMMENT '接入配置 JSON' AFTER target_table,
    ADD COLUMN write_mode VARCHAR(16) NOT NULL DEFAULT 'FULL' COMMENT 'FULL|INCREMENTAL' AFTER config_json,
    ADD COLUMN watermark_value VARCHAR(128) NULL COMMENT '增量水位' AFTER write_mode,
    ADD COLUMN enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否参与 Cron 调度' AFTER watermark_value;

CREATE INDEX idx_ingest_task_sched ON ing_ingest_task (enabled, status);
