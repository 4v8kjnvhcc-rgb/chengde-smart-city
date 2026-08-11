-- 跨模块流水线（手工补丁，与 Flyway V190 对齐；可重复执行）
-- 目标库：smart_city

CREATE TABLE IF NOT EXISTS gov_cross_pipeline (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pipeline_name VARCHAR(128) NOT NULL COMMENT '流水线名称',
  description VARCHAR(512) NULL,
  schedule_cron VARCHAR(128) NULL COMMENT '可选 Cron',
  schedule_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '1=定时已上线',
  publish_status VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/SUCCESS/FAILED',
  schedule_status VARCHAR(32) NOT NULL DEFAULT 'STOPPED' COMMENT 'STOPPED/RUNNING',
  ds_project_code BIGINT NULL,
  ds_definition_code BIGINT NULL,
  ds_schedule_id INT NULL,
  last_run_at DATETIME NULL,
  last_message VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cp_name (pipeline_name),
  KEY idx_cp_sched (schedule_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_cross_pipeline_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pipeline_id BIGINT NOT NULL,
  sort_no INT NOT NULL DEFAULT 0,
  step_type VARCHAR(32) NOT NULL COMMENT 'INGEST/GOVERNANCE/QUALITY/FUSION',
  ref_id BIGINT NOT NULL COMMENT '对应作业/任务/方案 ID',
  ref_name VARCHAR(256) NULL COMMENT '对象名称快照',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_cps_pipeline (pipeline_id),
  KEY idx_cps_sort (pipeline_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
