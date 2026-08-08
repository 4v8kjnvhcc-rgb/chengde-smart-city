-- M090/M091 元数据采集：DolphinScheduler 定时调度与监控字段

ALTER TABLE gov_meta_collect_task
    ADD COLUMN schedule_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/SCHEDULED' AFTER table_list,
    ADD COLUMN publish_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED' AFTER schedule_type,
    ADD COLUMN category_id BIGINT NULL COMMENT '元数据分类 gov_meta_source_category.id' AFTER publish_status,
    ADD COLUMN meta_data_source_id BIGINT NULL COMMENT '元数据数据源 gov_meta_data_source.id' AFTER category_id,
    ADD COLUMN ds_project_code BIGINT NULL AFTER meta_data_source_id,
    ADD COLUMN ds_definition_code BIGINT NULL AFTER ds_project_code,
    ADD COLUMN ds_schedule_id INT NULL AFTER ds_definition_code;

ALTER TABLE gov_meta_collect_run
    ADD COLUMN trigger_type VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/SCHEDULED' AFTER task_id,
    ADD COLUMN ds_instance_id BIGINT NULL AFTER trigger_type,
    ADD COLUMN ds_state VARCHAR(32) NULL AFTER ds_instance_id;

CREATE INDEX idx_gov_meta_collect_task_schedule ON gov_meta_collect_task (schedule_type, publish_status);
CREATE INDEX idx_gov_meta_collect_run_ds ON gov_meta_collect_run (ds_instance_id);
