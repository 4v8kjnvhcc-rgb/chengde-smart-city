-- 目标库：smart_city
-- 可重复执行：建表 IF NOT EXISTS
-- 对应 Flyway：V241__catalog_subscribe_notify_distribute.sql
-- 资源订阅变更通知 + 多目标数据分发

CREATE TABLE IF NOT EXISTS gov_catalog_sub_notice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  subscription_id BIGINT NOT NULL COMMENT 'gov_catalog_subscription.id',
  resource_id BIGINT NOT NULL COMMENT 'gov_catalog_resource.id',
  change_type VARCHAR(32) NOT NULL COMMENT 'DATA_UPDATE/DATA_INSERT/META_UPDATE/SCHEMA_CHANGE/REPUBLISH',
  title VARCHAR(256) NOT NULL,
  detail VARCHAR(1024) NULL,
  notify_user VARCHAR(64) NULL,
  notify_org VARCHAR(128) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'UNREAD' COMMENT 'UNREAD/READ/ACKED',
  acked_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_cat_sub_notice_sub (subscription_id),
  KEY idx_cat_sub_notice_res (resource_id),
  KEY idx_cat_sub_notice_user (notify_user, status),
  KEY idx_cat_sub_notice_org (notify_org, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目录订阅变更通知';

CREATE TABLE IF NOT EXISTS gov_catalog_distribute_target (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  subscription_id BIGINT NOT NULL COMMENT 'gov_catalog_subscription.id',
  resource_id BIGINT NOT NULL,
  target_type VARCHAR(32) NOT NULL COMMENT 'INTERNAL_SYSTEM/SUPERIOR/CITY_BIGDATA/NATIONAL_LOCAL_BIGDATA/THIRD_PARTY',
  target_name VARCHAR(256) NOT NULL,
  target_org VARCHAR(128) NULL,
  target_endpoint VARCHAR(512) NULL COMMENT '推送地址；空则仅记台账',
  share_mode VARCHAR(32) NULL COMMENT 'DB_SYNC/FILE_SYNC/API，空则继承订阅',
  auto_push TINYINT NOT NULL DEFAULT 1 COMMENT '1=资源变更时自动分发',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  remark VARCHAR(512) NULL,
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cat_dist_tgt_sub (subscription_id),
  KEY idx_cat_dist_tgt_res (resource_id),
  KEY idx_cat_dist_tgt_status (status, auto_push)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅数据分发目标';

CREATE TABLE IF NOT EXISTS gov_catalog_distribute_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  subscription_id BIGINT NOT NULL,
  target_id BIGINT NULL,
  resource_id BIGINT NOT NULL,
  trigger_type VARCHAR(32) NOT NULL COMMENT 'MANUAL/CHANGE/APPROVE',
  change_type VARCHAR(32) NULL,
  target_type VARCHAR(32) NULL,
  target_name VARCHAR(256) NULL,
  share_mode VARCHAR(32) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED/LEDGER',
  result_summary VARCHAR(1024) NULL,
  payload_digest VARCHAR(256) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME NULL,
  KEY idx_cat_dist_log_sub (subscription_id),
  KEY idx_cat_dist_log_res (resource_id),
  KEY idx_cat_dist_log_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅数据分发执行台账';
