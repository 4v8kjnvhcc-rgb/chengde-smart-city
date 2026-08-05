-- 资源监控管理：四维指标（可用性/完整性/安全性/性能）

ALTER TABLE rc_monitor_metric
  ADD COLUMN metric_category VARCHAR(32) NOT NULL DEFAULT 'AVAILABILITY'
    COMMENT 'AVAILABILITY/INTEGRITY/SECURITY/PERFORMANCE' AFTER metric_key,
  ADD COLUMN metric_unit VARCHAR(32) NULL COMMENT '单位' AFTER metric_value,
  ADD COLUMN resource_type VARCHAR(64) NULL
    COMMENT 'DB/STORAGE/CHANNEL/BACKUP/CATALOG/AUDIT/PERF' AFTER metric_unit,
  MODIFY COLUMN metric_value VARCHAR(256) NOT NULL;

UPDATE rc_monitor_metric SET metric_category = 'AVAILABILITY', resource_type = 'DB'
WHERE metric_key IN ('avail', 'db_ping', 'managed_tables');

UPDATE rc_monitor_metric SET metric_category = 'INTEGRITY', resource_type = 'BACKUP'
WHERE metric_key IN ('integrity', 'total_rows');

UPDATE rc_monitor_metric SET metric_category = 'SECURITY', resource_type = 'AUDIT'
WHERE metric_key IN ('security');

UPDATE rc_monitor_metric SET metric_category = 'PERFORMANCE', resource_type = 'PERF'
WHERE metric_key IN ('perf', 'total_bytes');
