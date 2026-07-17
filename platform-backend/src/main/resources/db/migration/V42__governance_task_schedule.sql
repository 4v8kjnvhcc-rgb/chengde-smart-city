-- C5：治理 ETL 任务定时调度

ALTER TABLE gov_governance_task
  ADD COLUMN schedule_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用定时' AFTER last_message,
  ADD COLUMN schedule_cron VARCHAR(64) NULL COMMENT 'Cron 表达式（6 段）' AFTER schedule_enabled,
  ADD COLUMN next_run_at DATETIME NULL COMMENT '下次计划运行时间' AFTER schedule_cron,
  ADD KEY idx_gov_task_next_run (schedule_enabled, next_run_at);
