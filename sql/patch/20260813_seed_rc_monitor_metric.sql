-- 资源监控管理：1 条真实口径指标（可重复执行）
-- 目标库：smart_city；页面：大数据平台资源中心 > 资源监控管理

DELETE FROM rc_monitor_metric WHERE metric_key = 'ods_pop_basic_latency';

INSERT INTO rc_monitor_metric (
  metric_key, metric_category, metric_label, metric_value, metric_unit,
  resource_type, alert_level, checked_at
) VALUES (
  'ods_pop_basic_latency',
  'PERFORMANCE',
  '人口基础信息表查询 P95',
  '86',
  'ms',
  'PERF',
  'OK',
  '2025-11-18 09:42:16'
);

SELECT id, metric_key, metric_category, metric_label, metric_value, metric_unit, resource_type, alert_level, checked_at
FROM rc_monitor_metric
WHERE metric_key = 'ods_pop_basic_latency';
