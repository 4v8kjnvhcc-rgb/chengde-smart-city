-- 本地库：V210 曾以「业务平台修复」入库，后脚本改名为 V212，本地 V210 变为 sys_error_log
-- 导致 description/checksum 与文件不一致，启动 Validate failed。
-- 目标库：smart_city
-- 可重复执行
--
-- 用法：
--   mysql -h127.0.0.1 -P3306 -uroot -p smart_city < sql/patch/20260813_flyway_repair_v210_checksum.sql

UPDATE flyway_schema_history
SET description = 'sys error log',
    script = 'V210__sys_error_log.sql',
    checksum = -989717380
WHERE version = '210';
