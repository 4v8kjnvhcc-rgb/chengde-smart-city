-- 门户/治理资源申请：两级审核（平台管理员 → 目录提供单位）
ALTER TABLE biz_portal_subscription
  ADD COLUMN approval_step VARCHAR(32) NOT NULL DEFAULT 'PLATFORM'
    COMMENT 'PLATFORM=平台管理员审核; PROVIDER=目录提供单位审核' AFTER status,
  ADD COLUMN platform_reviewed_by VARCHAR(64) NULL COMMENT '平台审核人' AFTER approval_step,
  ADD COLUMN platform_reviewer_contact VARCHAR(64) NULL COMMENT '平台审核联系方式' AFTER platform_reviewed_by,
  ADD COLUMN platform_approver_note VARCHAR(512) NULL COMMENT '平台审核意见' AFTER platform_reviewer_contact,
  ADD COLUMN platform_reviewed_at DATETIME NULL COMMENT '平台审核时间' AFTER platform_approver_note;

ALTER TABLE gov_catalog_subscription
  ADD COLUMN approval_step VARCHAR(32) NOT NULL DEFAULT 'PLATFORM'
    COMMENT 'PLATFORM=平台管理员审核; PROVIDER=目录提供单位审核' AFTER status,
  ADD COLUMN platform_reviewed_by VARCHAR(64) NULL COMMENT '平台审核人' AFTER approval_step,
  ADD COLUMN platform_reviewer_contact VARCHAR(64) NULL COMMENT '平台审核联系方式' AFTER platform_reviewed_by,
  ADD COLUMN platform_approver_note VARCHAR(512) NULL COMMENT '平台审核意见' AFTER platform_reviewer_contact,
  ADD COLUMN platform_reviewed_at DATETIME NULL COMMENT '平台审核时间' AFTER platform_approver_note;

-- 历史待审单已按「提供方审」在等，保持 PROVIDER，避免再卡回平台步
UPDATE biz_portal_subscription
SET approval_step = 'PROVIDER'
WHERE UPPER(status) = 'PENDING'
  AND (platform_reviewed_at IS NULL);

UPDATE gov_catalog_subscription
SET approval_step = 'PROVIDER'
WHERE UPPER(status) = 'PENDING'
  AND (platform_reviewed_at IS NULL);
