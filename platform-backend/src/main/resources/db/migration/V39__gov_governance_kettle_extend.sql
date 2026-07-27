-- 数据治理 ETL：变量、调度扩展、Kettle运行字段
ALTER TABLE gov_governance_task
    ADD COLUMN variables_json TEXT NULL COMMENT '任务变量JSON' AFTER next_run_at,
    ADD COLUMN schedule_mode VARCHAR(16) DEFAULT 'CRON' COMMENT '调度模式 CRON/SIMPLE' AFTER schedule_cron,
    ADD COLUMN start_time DATETIME NULL COMMENT '简单调度起始时间' AFTER schedule_mode,
    ADD COLUMN time_unit VARCHAR(16) NULL COMMENT '时间单位 HOUR/DAY/WEEK/MONTH' AFTER start_time,
    ADD COLUMN interval_value INT DEFAULT 1 COMMENT '间隔值' AFTER time_unit;

ALTER TABLE gov_governance_task_run
    ADD COLUMN trans_name VARCHAR(128) NULL COMMENT 'Kettle转换名' AFTER task_id,
    ADD COLUMN line_count INT DEFAULT 0 COMMENT 'Kettle处理行数' AFTER row_count;
