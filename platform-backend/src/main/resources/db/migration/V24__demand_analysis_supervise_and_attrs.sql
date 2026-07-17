-- M021 需求分析：督办、评估状态、共享属性、分析快照

ALTER TABLE biz_data_demand
  ADD COLUMN eval_status VARCHAR(32) NULL COMMENT 'PENDING|MATCHED|PARTIAL|UNMATCHED' AFTER fulfill_path,
  ADD COLUMN share_attr VARCHAR(32) NULL COMMENT 'OPEN|CONDITIONAL|RESTRICTED|INTERNAL' AFTER eval_status,
  ADD COLUMN supervise_note VARCHAR(512) NULL AFTER share_attr,
  ADD COLUMN supervise_at DATETIME NULL AFTER supervise_note,
  ADD COLUMN supervise_by VARCHAR(64) NULL AFTER supervise_at,
  ADD COLUMN analysis_payload TEXT NULL COMMENT 'JSON candidates+graph' AFTER supervise_by;
