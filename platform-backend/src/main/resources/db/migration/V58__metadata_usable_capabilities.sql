-- 元数据分层与可用性能力：registry 扩展字段 + 分层物理库

CREATE DATABASE IF NOT EXISTS smart_city_ods CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_city_dwd CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_city_dws CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_city_ads CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE gov_metadata_registry
  ADD COLUMN database_name VARCHAR(64) NULL COMMENT '物理库名' AFTER physical_table_name,
  ADD COLUMN schema_name VARCHAR(64) NULL COMMENT '物理 schema/库名' AFTER database_name,
  ADD COLUMN data_layer VARCHAR(16) NULL COMMENT 'ODS/DWD/DWS/ADS/CONTROL' AFTER schema_name,
  ADD COLUMN business_domain VARCHAR(64) NULL COMMENT '业务域' AFTER data_layer,
  ADD COLUMN owner_name VARCHAR(64) NULL COMMENT '责任人' AFTER business_domain,
  ADD COLUMN conform_status VARCHAR(16) NULL COMMENT 'PASS/PARTIAL/FAIL/SKIP' AFTER owner_name,
  ADD COLUMN conform_report TEXT NULL COMMENT '一致性校验报告 JSON' AFTER conform_status;

-- 注：smart_city 中已有 ods_enterprise_base / ods_project_base 等 demo 表暂不物理迁移，
-- 新汇聚写入 smart_city_ods；后续可按需手工迁移或重建。
