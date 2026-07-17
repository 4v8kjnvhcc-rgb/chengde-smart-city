-- 采集 diff 摘要可能超过 VARCHAR(512)；改为 TEXT，避免 MysqlDataTruncation 导致整次 run 落库失败
ALTER TABLE gov_meta_collect_run
  MODIFY COLUMN summary TEXT NULL COMMENT '运行摘要（压缩 JSON 或短文本）';
