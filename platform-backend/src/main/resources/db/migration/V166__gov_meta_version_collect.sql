-- 定时采集变更自动写入版本历史：关联采集任务与运行记录
ALTER TABLE gov_meta_version
    ADD COLUMN version_source VARCHAR(32) NULL COMMENT 'PUBLISH/SCHEDULED_COLLECT/MAINTAIN/ROLLBACK/MANUAL' AFTER change_summary,
    ADD COLUMN collect_task_id BIGINT NULL COMMENT '来源采集任务' AFTER version_source,
    ADD COLUMN collect_run_id BIGINT NULL COMMENT '来源采集运行' AFTER collect_task_id;

CREATE INDEX idx_gov_meta_version_collect_run ON gov_meta_version (collect_run_id);
CREATE INDEX idx_gov_meta_version_source ON gov_meta_version (version_source);
