-- 手工补丁（与 Flyway V231 同内容）：指标改挂 ind_* 后删除平行台账
-- 目标库：smart_city
-- 可重复执行
-- 保留：ana_indicator、ana_model_indicator
-- 禁止删除：ind_area、ind_group、ind_field、ind_sql、ind_job、ind_job_log、ana_zone_binding、ana_analysis_model

DROP TABLE IF EXISTS ana_indicator_task_run;
DROP TABLE IF EXISTS ana_indicator_task;
DROP TABLE IF EXISTS ana_indicator_query;
DROP TABLE IF EXISTS ana_indicator_group;
DROP TABLE IF EXISTS ana_indicator_domain;
