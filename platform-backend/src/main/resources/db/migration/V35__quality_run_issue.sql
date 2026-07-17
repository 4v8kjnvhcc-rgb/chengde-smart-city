-- A7：质量任务运行记录 + 问题数据

CREATE TABLE IF NOT EXISTS gov_quality_task_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED/STOPPED',
  started_at DATETIME NOT NULL,
  ended_at DATETIME NULL,
  score DECIMAL(5,2) NULL,
  total_checks INT NOT NULL DEFAULT 0,
  issue_count INT NOT NULL DEFAULT 0,
  message VARCHAR(512) NULL,
  triggered_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_qrun_task (task_id),
  KEY idx_qrun_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_quality_issue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  rule_id BIGINT NULL,
  detail_id BIGINT NULL,
  check_type VARCHAR(32) NULL,
  target_table VARCHAR(128) NULL,
  target_column VARCHAR(128) NULL,
  issue_type VARCHAR(64) NULL COMMENT 'NULL/DUPLICATE/INVALID/COUNT',
  issue_value VARCHAR(512) NULL,
  issue_count INT NOT NULL DEFAULT 1,
  sample_data TEXT NULL,
  severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_qissue_run (run_id),
  KEY idx_qissue_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
