-- V231: 指标域/组/任务改挂生产 ind_* 六表后，删除新系统平行台账
-- 保留 ana_indicator、ana_model_indicator（分析模型种子指标，如 IND_POP_COUNT）
-- 不删除 ind_area/ind_group/ind_field/ind_sql/ind_job/ind_job_log

DROP TABLE IF EXISTS ana_indicator_task_run;
DROP TABLE IF EXISTS ana_indicator_task;
DROP TABLE IF EXISTS ana_indicator_query;
DROP TABLE IF EXISTS ana_indicator_group;
DROP TABLE IF EXISTS ana_indicator_domain;
