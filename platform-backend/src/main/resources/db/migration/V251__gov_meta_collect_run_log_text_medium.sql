-- 元数据采集日志可能含大量表/字段 diff，TEXT(64KB) 易触发 MysqlDataTruncation
ALTER TABLE gov_meta_collect_run
  MODIFY COLUMN log_text MEDIUMTEXT NULL COMMENT '运行日志';
