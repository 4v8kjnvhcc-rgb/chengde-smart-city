-- Kettle/DolphinScheduler 真实执行：汇聚任务保存 Carte 转换名、DS 项目/流程/实例标识、真实行数与错误详情。

ALTER TABLE ing_ingest_task
  ADD COLUMN kettle_trans_name VARCHAR(160) NULL COMMENT 'Carte 转换名' AFTER collected_rows,
  ADD COLUMN ds_project_code BIGINT NULL COMMENT 'DolphinScheduler 项目 code' AFTER kettle_trans_name,
  ADD COLUMN ds_definition_code BIGINT NULL COMMENT 'DS 流程定义 code' AFTER ds_project_code,
  ADD COLUMN ds_instance_id BIGINT NULL COMMENT 'DS 流程实例 id' AFTER ds_definition_code,
  ADD COLUMN lines_input BIGINT NULL COMMENT 'Carte 读取行数' AFTER ds_instance_id,
  ADD COLUMN lines_output BIGINT NULL COMMENT 'Carte 写入行数' AFTER lines_input,
  ADD COLUMN lines_rejected BIGINT NULL COMMENT 'Carte 拒绝行数' AFTER lines_output,
  ADD COLUMN error_detail VARCHAR(1024) NULL COMMENT '失败真实原因' AFTER lines_rejected;

-- 融合任务运行台账补充 Carte 转换名与真实行数（gov_governance_task_run 已有 status/message）。
ALTER TABLE gov_governance_task_run
  ADD COLUMN kettle_trans_name VARCHAR(160) NULL COMMENT 'Carte 转换名' AFTER status,
  ADD COLUMN produced_rows BIGINT NULL COMMENT '产出行数（真实）' AFTER kettle_trans_name,
  ADD COLUMN ds_instance_id BIGINT NULL COMMENT 'DS 流程实例 id' AFTER produced_rows;
