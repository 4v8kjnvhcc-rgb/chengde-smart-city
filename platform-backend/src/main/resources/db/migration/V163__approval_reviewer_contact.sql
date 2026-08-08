-- 审批表单：审批人联系方式（审批人沿用 reviewed_by / reviewedBy）

ALTER TABLE gov_catalog_approval
  ADD COLUMN reviewer_contact VARCHAR(64) NULL COMMENT '审批人联系方式' AFTER reviewed_by;

ALTER TABLE biz_portal_subscription
  ADD COLUMN reviewer_contact VARCHAR(64) NULL COMMENT '审批人联系方式' AFTER reviewed_by;

ALTER TABLE gov_catalog_subscription
  ADD COLUMN reviewer_contact VARCHAR(64) NULL COMMENT '审批人联系方式' AFTER reviewed_by;
