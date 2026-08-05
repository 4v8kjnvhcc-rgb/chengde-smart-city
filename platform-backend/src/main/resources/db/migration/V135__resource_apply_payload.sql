-- 门户/治理资源申请表单扩展：详细申请信息 JSON
ALTER TABLE biz_portal_subscription
  ADD COLUMN apply_payload TEXT NULL COMMENT '资源申请表单JSON' AFTER purpose;

ALTER TABLE biz_portal_subscription
  MODIFY COLUMN purpose VARCHAR(512) NULL;

ALTER TABLE gov_catalog_subscription
  ADD COLUMN apply_payload TEXT NULL COMMENT '资源申请表单JSON' AFTER purpose;

ALTER TABLE gov_catalog_subscription
  MODIFY COLUMN purpose VARCHAR(512) NULL;
