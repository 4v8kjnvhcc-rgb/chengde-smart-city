-- 手工补丁（对应 Flyway V237）：门户订阅保存 ESB OAuth 凭证
-- 目标库：smart_city
-- 执行顺序：可单独执行；与 Flyway V237 等价
-- 可重复执行：否（列已存在会报 Duplicate column）

ALTER TABLE biz_portal_subscription
  ADD COLUMN oauth_client_id VARCHAR(128) NULL COMMENT 'ESB 消费者 client_id' AFTER updated_at,
  ADD COLUMN oauth_client_secret VARCHAR(256) NULL COMMENT 'ESB 消费者 client_secret' AFTER oauth_client_id,
  ADD COLUMN esb_customer_id VARCHAR(512) NULL COMMENT 'ESB customerId 加密串' AFTER oauth_client_secret,
  ADD COLUMN api_url VARCHAR(512) NULL COMMENT '接口 URL 快照' AFTER esb_customer_id,
  ADD COLUMN api_method VARCHAR(16) NULL COMMENT '接口请求方式' AFTER api_url;
