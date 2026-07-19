-- M046 数据资产报告：存储指标、分区、日趋势、脚本运行、变更日志、任务-脚本关联

-- ---------- ing_data_table 扩展 ----------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_data_table' AND COLUMN_NAME = 'storage_bytes');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_data_table ADD COLUMN storage_bytes BIGINT NULL COMMENT ''表当前存储量(字节)'' AFTER column_count', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_data_table' AND COLUMN_NAME = 'storage_bytes_today');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_data_table ADD COLUMN storage_bytes_today BIGINT NULL COMMENT ''今日新增存储量(字节)'' AFTER storage_bytes', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_data_table' AND COLUMN_NAME = 'data_changed_at');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_data_table ADD COLUMN data_changed_at DATETIME NULL COMMENT ''数据变更时间'' AFTER storage_bytes_today', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_data_table' AND COLUMN_NAME = 'ddl_sql');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_data_table ADD COLUMN ddl_sql MEDIUMTEXT NULL COMMENT ''建表DDL'' AFTER data_changed_at', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_data_table' AND COLUMN_NAME = 'advanced_settings_json');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_data_table ADD COLUMN advanced_settings_json VARCHAR(1024) NULL COMMENT ''高级设置JSON'' AFTER ddl_sql', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_data_table' AND COLUMN_NAME = 'partition_flag');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_data_table ADD COLUMN partition_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''是否分区表'' AFTER advanced_settings_json', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 字段是否分区列 ----------
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ing_data_column' AND COLUMN_NAME = 'partition_col_flag');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE ing_data_column ADD COLUMN partition_col_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''是否分区字段'' AFTER built_in_flag', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 新表 ----------
CREATE TABLE IF NOT EXISTS ing_table_partition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  table_id BIGINT NOT NULL,
  partition_name VARCHAR(128) NOT NULL,
  storage_bytes BIGINT NOT NULL DEFAULT 0,
  file_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ing_tbl_part_table (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_asset_metric_daily (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  metric_date DATE NOT NULL,
  metric_type VARCHAR(32) NOT NULL COMMENT 'TABLE_NEW/STORAGE_GB/TABLE_GROWTH/TABLE_TOTAL',
  ref_id BIGINT NULL COMMENT '表ID等，全局指标可空',
  value_num DECIMAL(18,4) NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ing_metric_daily (metric_date, metric_type, ref_id),
  KEY idx_ing_metric_type_date (metric_type, metric_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_fusion_script_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  script_id BIGINT NOT NULL,
  started_at DATETIME NOT NULL,
  ended_at DATETIME NULL,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED/RUNNING',
  message VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_fusion_script_run_script (script_id),
  KEY idx_fusion_script_run_duration (duration_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_asset_change_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  asset_type VARCHAR(32) NOT NULL COMMENT 'TABLE/SCRIPT/WORKFLOW',
  asset_id BIGINT NOT NULL,
  change_at DATETIME NOT NULL,
  change_type VARCHAR(32) NOT NULL COMMENT 'CREATE/ALTER/PUBLISH/UPDATE',
  summary VARCHAR(512) NOT NULL,
  detail_json VARCHAR(2048) NULL,
  version_label VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_asset_change (asset_type, asset_id, change_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_task_script_rel (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  script_id BIGINT NOT NULL,
  UNIQUE KEY uk_task_script (task_id, script_id),
  KEY idx_task_script_script (script_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 回填存储量 ----------
UPDATE ing_data_table
SET storage_bytes = CASE
      WHEN source_row_count IS NOT NULL AND source_row_count > 0 THEN source_row_count * 200
      ELSE GREATEST(IFNULL(column_count, 1), 1) * 1024 * 50
    END,
    storage_bytes_today = CASE
      WHEN source_row_count IS NOT NULL AND source_row_count > 0 THEN GREATEST(source_row_count * 8, 1024)
      ELSE 2048
    END,
    data_changed_at = IFNULL(last_collect_at, IFNULL(created_at, NOW())),
    ddl_sql = CONCAT(
      'CREATE TABLE `', IFNULL(physical_table_name, table_code), '` (\n',
      '  -- generated for asset report demo\n',
      '  id BIGINT PRIMARY KEY\n',
      ') COMMENT=''', table_name, ''';'
    ),
    advanced_settings_json = JSON_OBJECT(
      'location', CONCAT('/data/warehouse/', IFNULL(physical_table_name, table_code)),
      'storageFormat', 'ORC',
      'storageType', 'HDFS',
      'fieldDelimiter', ',',
      'escapeChar', '\\'
    )
WHERE storage_bytes IS NULL;

-- 演示：将首张表标为分区表并写分区
UPDATE ing_data_table SET partition_flag = 1
WHERE id = (SELECT id FROM (SELECT MIN(id) AS id FROM ing_data_table) t) AND partition_flag = 0;

INSERT INTO ing_table_partition (table_id, partition_name, storage_bytes, file_count)
SELECT t.id, CONCAT('p', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL n.n DAY), '%Y%m%d')),
       GREATEST(IFNULL(t.storage_bytes, 10000) / 7, 1024), 3 + n.n
FROM ing_data_table t
CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n
WHERE t.partition_flag = 1
  AND NOT EXISTS (SELECT 1 FROM ing_table_partition p WHERE p.table_id = t.id);

UPDATE ing_data_column c
INNER JOIN (
  SELECT table_id, MIN(sort_order) AS min_sort
  FROM ing_data_column
  GROUP BY table_id
) m ON c.table_id = m.table_id AND c.sort_order = m.min_sort
INNER JOIN ing_data_table t ON t.id = c.table_id AND t.partition_flag = 1
SET c.partition_col_flag = 1
WHERE c.partition_col_flag = 0;

-- ---------- 全局日趋势（近 30 天） ----------
INSERT INTO ing_asset_metric_daily (metric_date, metric_type, ref_id, value_num)
SELECT d.dt, 'TABLE_NEW', NULL,
       GREATEST(1, (SELECT COUNT(*) FROM ing_data_table) - (29 - d.seq) DIV 3)
FROM (
  SELECT DATE_SUB(CURDATE(), INTERVAL seq DAY) AS dt, seq
  FROM (
    SELECT 0 seq UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7
    UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
    UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20
    UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26
    UNION SELECT 27 UNION SELECT 28 UNION SELECT 29
  ) s
) d
WHERE NOT EXISTS (
  SELECT 1 FROM ing_asset_metric_daily m
  WHERE m.metric_date = d.dt AND m.metric_type = 'TABLE_NEW' AND m.ref_id IS NULL
);

INSERT INTO ing_asset_metric_daily (metric_date, metric_type, ref_id, value_num)
SELECT d.dt, 'STORAGE_GB', NULL,
       ROUND(IFNULL((SELECT SUM(storage_bytes) FROM ing_data_table), 0) / 1073741824.0 * (0.4 + d.seq * 0.02), 4)
FROM (
  SELECT DATE_SUB(CURDATE(), INTERVAL seq DAY) AS dt, seq
  FROM (
    SELECT 0 seq UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7
    UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
    UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20
    UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26
    UNION SELECT 27 UNION SELECT 28 UNION SELECT 29
  ) s
) d
WHERE NOT EXISTS (
  SELECT 1 FROM ing_asset_metric_daily m
  WHERE m.metric_date = d.dt AND m.metric_type = 'STORAGE_GB' AND m.ref_id IS NULL
);

-- 表产出近 7 天
INSERT INTO ing_asset_metric_daily (metric_date, metric_type, ref_id, value_num)
SELECT d.dt, 'TABLE_GROWTH', t.id,
       ROUND(IFNULL(t.storage_bytes_today, 1024) * (0.6 + d.seq * 0.05) / 1024.0, 4)
FROM ing_data_table t
CROSS JOIN (
  SELECT DATE_SUB(CURDATE(), INTERVAL seq DAY) AS dt, seq
  FROM (SELECT 0 seq UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) s
) d
WHERE NOT EXISTS (
  SELECT 1 FROM ing_asset_metric_daily m
  WHERE m.metric_date = d.dt AND m.metric_type = 'TABLE_GROWTH' AND m.ref_id = t.id
);

INSERT INTO ing_asset_metric_daily (metric_date, metric_type, ref_id, value_num)
SELECT d.dt, 'TABLE_TOTAL', t.id,
       ROUND(IFNULL(t.storage_bytes, 0) * (0.7 + d.seq * 0.04) / 1024.0 / 1024.0, 4)
FROM ing_data_table t
CROSS JOIN (
  SELECT DATE_SUB(CURDATE(), INTERVAL seq DAY) AS dt, seq
  FROM (SELECT 0 seq UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) s
) d
WHERE NOT EXISTS (
  SELECT 1 FROM ing_asset_metric_daily m
  WHERE m.metric_date = d.dt AND m.metric_type = 'TABLE_TOTAL' AND m.ref_id = t.id
);

-- ---------- 脚本运行种子 ----------
INSERT INTO gov_fusion_script_run (script_id, started_at, ended_at, duration_ms, status, message)
SELECT s.id,
       DATE_SUB(NOW(), INTERVAL (n.n + 1) DAY) + INTERVAL (8 + n.n) HOUR,
       DATE_SUB(NOW(), INTERVAL (n.n + 1) DAY) + INTERVAL (8 + n.n) HOUR + INTERVAL (30 + n.n * 25) SECOND,
       (30 + n.n * 25) * 1000 + s.id * 100,
       IF(n.n = 3, 'FAILED', 'SUCCESS'),
       IF(n.n = 3, '演示失败运行', '演示成功运行')
FROM gov_fusion_script s
CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n
WHERE NOT EXISTS (SELECT 1 FROM gov_fusion_script_run r WHERE r.script_id = s.id);

-- 无脚本时插入演示脚本
INSERT INTO gov_fusion_script (script_code, script_name, script_type, script_content, publish_status, version_no, status, created_by)
SELECT 'SCR_ASSET_DEMO_01', '资产报告演示脚本', 'SELECT', 'SELECT 1 AS demo;', 'PUBLISHED', 1, 'ACTIVE', 'sys_admin'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM gov_fusion_script LIMIT 1);

INSERT INTO gov_fusion_script_version (script_id, version_no, script_content, change_summary, published_by, published_at)
SELECT s.id, 1, s.script_content, '初始发布', 'sys_admin', NOW()
FROM gov_fusion_script s
WHERE s.script_code = 'SCR_ASSET_DEMO_01'
  AND NOT EXISTS (SELECT 1 FROM gov_fusion_script_version v WHERE v.script_id = s.id);

INSERT INTO gov_fusion_script_run (script_id, started_at, ended_at, duration_ms, status, message)
SELECT s.id,
       DATE_SUB(NOW(), INTERVAL (n.n + 1) HOUR),
       DATE_SUB(NOW(), INTERVAL (n.n + 1) HOUR) + INTERVAL (40 + n.n * 10) SECOND,
       (40 + n.n * 10) * 1000,
       'SUCCESS',
       '演示运行'
FROM gov_fusion_script s
CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) n
WHERE s.script_code = 'SCR_ASSET_DEMO_01'
  AND NOT EXISTS (SELECT 1 FROM gov_fusion_script_run r WHERE r.script_id = s.id AND r.message = '演示运行');

-- ---------- 任务-脚本关联 ----------
INSERT INTO gov_task_script_rel (task_id, script_id)
SELECT t.id, s.id
FROM gov_governance_task t
CROSS JOIN gov_fusion_script s
WHERE t.id = (SELECT MIN(id) FROM gov_governance_task)
  AND s.id = (SELECT MIN(id) FROM gov_fusion_script)
  AND NOT EXISTS (SELECT 1 FROM gov_task_script_rel r WHERE r.task_id = t.id AND r.script_id = s.id);

-- 无工作流时插演示任务
INSERT INTO gov_governance_task (task_code, task_name, description, status, schedule_enabled, schedule_cron, created_by)
SELECT 'WF_ASSET_DEMO_01', '资产报告演示工作流', 'M046 演示', 'READY', 1, '0 0 2 * * ?', 'sys_admin'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM gov_governance_task LIMIT 1);

INSERT INTO gov_governance_task_run (task_id, status, started_at, ended_at, total_nodes, success_nodes, failed_nodes, row_count, message, triggered_by)
SELECT t.id, 'SUCCESS',
       DATE_SUB(NOW(), INTERVAL (n.n + 1) DAY),
       DATE_SUB(NOW(), INTERVAL (n.n + 1) DAY) + INTERVAL (5 + n.n) MINUTE,
       3, 3, 0, 100 + n.n * 10, '演示实例', 'sys_admin'
FROM gov_governance_task t
CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n
WHERE NOT EXISTS (SELECT 1 FROM gov_governance_task_run r WHERE r.task_id = t.id AND r.message = '演示实例');

INSERT INTO gov_task_script_rel (task_id, script_id)
SELECT t.id, s.id
FROM gov_governance_task t
CROSS JOIN gov_fusion_script s
WHERE t.task_code = 'WF_ASSET_DEMO_01'
  AND s.id = (SELECT MIN(id) FROM gov_fusion_script)
  AND NOT EXISTS (SELECT 1 FROM gov_task_script_rel r WHERE r.task_id = t.id AND r.script_id = s.id);

UPDATE gov_governance_task SET schedule_enabled = 1
WHERE id = (SELECT id FROM (SELECT MIN(id) AS id FROM gov_governance_task) x)
  AND (schedule_enabled IS NULL OR schedule_enabled = 0);

-- ---------- 变更日志种子 ----------
INSERT INTO ing_asset_change_log (asset_type, asset_id, change_at, change_type, summary, detail_json, version_label)
SELECT 'TABLE', t.id, DATE_SUB(NOW(), INTERVAL n.n DAY), IF(n.n = 6, 'CREATE', 'ALTER'),
       CONCAT('表结构变更 #', n.n),
       JSON_OBJECT('columnsAdded', n.n),
       CONCAT('v1.', n.n)
FROM ing_data_table t
CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n
WHERE NOT EXISTS (
  SELECT 1 FROM ing_asset_change_log c WHERE c.asset_type = 'TABLE' AND c.asset_id = t.id
);

INSERT INTO ing_asset_change_log (asset_type, asset_id, change_at, change_type, summary, detail_json, version_label)
SELECT 'SCRIPT', s.id, DATE_SUB(NOW(), INTERVAL n.n DAY), IF(n.n = 0, 'PUBLISH', 'UPDATE'),
       CONCAT('脚本变更 #', n.n),
       JSON_OBJECT('version', IFNULL(s.version_no, 1)),
       CONCAT('v', IFNULL(s.version_no, 1), '.', n.n)
FROM gov_fusion_script s
CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n
WHERE NOT EXISTS (
  SELECT 1 FROM ing_asset_change_log c WHERE c.asset_type = 'SCRIPT' AND c.asset_id = s.id
);

INSERT INTO ing_asset_change_log (asset_type, asset_id, change_at, change_type, summary, detail_json, version_label)
SELECT 'WORKFLOW', t.id, DATE_SUB(NOW(), INTERVAL n.n DAY), IF(n.n = 0, 'PUBLISH', 'UPDATE'),
       CONCAT('工作流变更 #', n.n),
       JSON_OBJECT('status', t.status),
       CONCAT('wf-', n.n)
FROM gov_governance_task t
CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n
WHERE NOT EXISTS (
  SELECT 1 FROM ing_asset_change_log c WHERE c.asset_type = 'WORKFLOW' AND c.asset_id = t.id
);

-- 稽核表示例：为质量明细补一条指向已有表
INSERT INTO gov_quality_task_detail (task_id, rule_id, target_table, target_column, check_type, sort_order, status)
SELECT qt.id, IFNULL(qt.rule_id, 1), t.table_name, NULL, 'RECORD_COUNT', 1, 'ENABLED'
FROM gov_quality_task qt
CROSS JOIN ing_data_table t
WHERE qt.id = (SELECT MIN(id) FROM gov_quality_task)
  AND t.id = (SELECT MIN(id) FROM ing_data_table)
  AND NOT EXISTS (
    SELECT 1 FROM gov_quality_task_detail d WHERE d.task_id = qt.id AND d.target_table = t.table_name
  );
