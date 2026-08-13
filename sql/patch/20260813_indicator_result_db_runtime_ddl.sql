-- 指标结果库说明（非 Flyway；业务库由任务执行时动态创建）
-- 目标主机：
--   生产：INDICATOR_DB_* → 10.10.10.12:13306（见 compose/prod-app.env）
--   本地：127.0.0.1:3306（application-dev.yml app.indicator-db）
--
-- 行为（IndicatorTaskService / IndicatorJdbcSupport）：
--   1) 指标域/组仅写控制面台账（ana_indicator_*），创建/发布时不建物理库
--   2) 指标任务「执行」或 DS 定时回调（ds-trigger）时增量：
--      - 库不存在 → CREATE DATABASE；已存在 → 跳过
--      - 表不存在 → CREATE TABLE；已存在 → 跳过建表
--      - 字段不存在 → ADD COLUMN；已存在 → 跳过
--      - INSERT 一行计算结果快照
--
-- 运维注意：
--   - 须授权应用机（如 10.10.10.57）可从 INDICATOR_DB_HOST 以配置账号建库
--   - 勿在控制面 smart_city 手工预建各指标域库
--   - 本脚本无需在生产手工执行；仅作部署清单索引

SELECT 1 AS indicator_runtime_ddl_doc;
