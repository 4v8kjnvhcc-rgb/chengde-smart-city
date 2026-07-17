-- 真实 JDBC 登记：数据源连接引用/源 schema/探测时间/同步状态，登记表补充源物理表与探测元数据。
-- 仅新增列，不改动历史迁移。密码改为密文存储（conn_config_json.passwordCipher）。

ALTER TABLE ing_data_source
  ADD COLUMN source_schema VARCHAR(128) NULL COMMENT '探测到的源库/schema 名' AFTER conn_config_json,
  ADD COLUMN probe_at DATETIME NULL COMMENT '最近一次真实 JDBC 探测时间' AFTER source_schema,
  ADD COLUMN probe_message VARCHAR(512) NULL COMMENT '探测结果/失败原因（脱敏）' AFTER probe_at,
  ADD COLUMN om_service_name VARCHAR(128) NULL COMMENT 'OpenMetadata database service 名' AFTER probe_message,
  ADD COLUMN sync_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'OM/DS/Kettle 同步状态 PENDING/SYNCED/FAILED' AFTER om_service_name;

ALTER TABLE ing_data_table
  ADD COLUMN source_schema VARCHAR(128) NULL COMMENT '源 schema' AFTER physical_table_name,
  ADD COLUMN source_table VARCHAR(128) NULL COMMENT '源物理表名（异构，与 ODS 目标表不同）' AFTER source_schema,
  ADD COLUMN source_row_count BIGINT NULL COMMENT '登记探测时源表行数' AFTER source_table,
  ADD COLUMN primary_key_cols VARCHAR(256) NULL COMMENT '主键列（逗号分隔）' AFTER source_row_count,
  ADD COLUMN om_fqn VARCHAR(512) NULL COMMENT 'OpenMetadata 表 FQN' AFTER primary_key_cols;
