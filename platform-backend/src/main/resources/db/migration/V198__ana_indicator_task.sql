-- V198: 分析域「指标任务」+ 运行日志（对接 DolphinScheduler）
CREATE TABLE IF NOT EXISTS ana_indicator_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_domain_code VARCHAR(32) NOT NULL COMMENT '所属业务域',
  group_id BIGINT NOT NULL COMMENT '来源指标组',
  task_name VARCHAR(128) NOT NULL,
  exec_cycle VARCHAR(32) NOT NULL DEFAULT 'MONTHLY' COMMENT 'MONTHLY|DAILY|WEEKLY',
  schedule_cron VARCHAR(64) NOT NULL DEFAULT '0 0 2 1 * ?' COMMENT 'Quartz 6 段',
  schedule_status VARCHAR(32) NOT NULL DEFAULT 'STOPPED' COMMENT 'STOPPED|STARTED',
  exec_status VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE|RUNNING|SUCCESS|FAILED',
  calc_result VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE|ALL_SUCCESS|PARTIAL|FAILED',
  publish_status VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED|OFFLINE',
  published_at DATETIME NULL,
  ds_project_code BIGINT NULL,
  ds_definition_code BIGINT NULL,
  ds_schedule_id INT NULL,
  ds_instance_id BIGINT NULL,
  last_run_at DATETIME NULL,
  last_run_message VARCHAR(1024) NULL,
  last_log MEDIUMTEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  UNIQUE KEY uk_ana_ind_task_group (group_id),
  KEY idx_ana_ind_task_owner (owner_domain_code, status),
  KEY idx_ana_ind_task_name (task_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ana_indicator_task_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  trigger_type VARCHAR(32) NOT NULL COMMENT 'MANUAL|SCHEDULE|DS_CALLBACK',
  exec_status VARCHAR(32) NOT NULL,
  calc_result VARCHAR(32) NOT NULL,
  ds_instance_id BIGINT NULL,
  message VARCHAR(1024) NULL,
  log_text MEDIUMTEXT NULL,
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  KEY idx_ana_ind_task_run_task (task_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
