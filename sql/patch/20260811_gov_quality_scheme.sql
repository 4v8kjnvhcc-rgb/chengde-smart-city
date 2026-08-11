-- 手工部署补丁：质量方案管理（与 Flyway V182 同结构）
-- 目标库：smart_city（控制面）
-- 可重复执行：CREATE TABLE IF NOT EXISTS

CREATE TABLE IF NOT EXISTS gov_quality_scheme (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scheme_name VARCHAR(128) NOT NULL COMMENT '方案名称',
  model_id BIGINT NOT NULL COMMENT '质量模型 ID',
  model_name VARCHAR(128) NULL COMMENT '模型名称快照',
  executor_address VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '执行器/Worker 组',
  cron_expr VARCHAR(128) NULL COMMENT '执行周期 Cron',
  cycle_name VARCHAR(64) NULL COMMENT '执行周期显示名',
  description VARCHAR(512) NULL,
  schedule_status VARCHAR(32) NOT NULL DEFAULT 'STOPPED' COMMENT 'STOPPED/RUNNING',
  generate_status VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/SUCCESS/FAILED',
  exec_status VARCHAR(32) NOT NULL DEFAULT 'NOT_RUN' COMMENT 'NOT_RUN/RUNNING/SUCCESS/FAILED',
  last_exec_at DATETIME NULL,
  last_message VARCHAR(512) NULL,
  quality_task_id BIGINT NULL COMMENT '同步生成的 gov_quality_task.id',
  ds_project_code BIGINT NULL,
  ds_definition_code BIGINT NULL,
  ds_schedule_id INT NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_qs_name (scheme_name),
  KEY idx_qs_model (model_id),
  KEY idx_qs_sched (schedule_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_quality_scheme_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scheme_id BIGINT NOT NULL,
  model_rule_id BIGINT NOT NULL COMMENT 'gov_quality_model_rule.id',
  rule_type_name VARCHAR(128) NULL,
  rule_name VARCHAR(256) NULL,
  table_name VARCHAR(128) NULL,
  field_names VARCHAR(1024) NULL,
  check_type VARCHAR(32) NULL,
  sort_no INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_qsr_scheme (scheme_id),
  KEY idx_qsr_rule (model_rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
