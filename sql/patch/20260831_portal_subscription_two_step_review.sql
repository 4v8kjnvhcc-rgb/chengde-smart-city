-- 手工补丁：与 Flyway V254 对齐（门户资源申请两级审核）
-- 目标库：smart_city
-- 可重复执行

-- biz_portal_subscription.approval_step
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_portal_subscription' AND COLUMN_NAME = 'approval_step');
SET @sql := IF(@c = 0,
  'ALTER TABLE biz_portal_subscription ADD COLUMN approval_step VARCHAR(32) NOT NULL DEFAULT ''PLATFORM'' COMMENT ''PLATFORM=平台管理员审核; PROVIDER=目录提供单位审核'' AFTER status',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_portal_subscription' AND COLUMN_NAME = 'platform_reviewed_by');
SET @sql := IF(@c = 0,
  'ALTER TABLE biz_portal_subscription ADD COLUMN platform_reviewed_by VARCHAR(64) NULL COMMENT ''平台审核人'' AFTER approval_step, ADD COLUMN platform_reviewer_contact VARCHAR(64) NULL COMMENT ''平台审核联系方式'' AFTER platform_reviewed_by, ADD COLUMN platform_approver_note VARCHAR(512) NULL COMMENT ''平台审核意见'' AFTER platform_reviewer_contact, ADD COLUMN platform_reviewed_at DATETIME NULL COMMENT ''平台审核时间'' AFTER platform_approver_note',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- gov_catalog_subscription
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_catalog_subscription' AND COLUMN_NAME = 'approval_step');
SET @sql := IF(@c = 0,
  'ALTER TABLE gov_catalog_subscription ADD COLUMN approval_step VARCHAR(32) NOT NULL DEFAULT ''PLATFORM'' COMMENT ''PLATFORM=平台管理员审核; PROVIDER=目录提供单位审核'' AFTER status',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gov_catalog_subscription' AND COLUMN_NAME = 'platform_reviewed_by');
SET @sql := IF(@c = 0,
  'ALTER TABLE gov_catalog_subscription ADD COLUMN platform_reviewed_by VARCHAR(64) NULL COMMENT ''平台审核人'' AFTER approval_step, ADD COLUMN platform_reviewer_contact VARCHAR(64) NULL COMMENT ''平台审核联系方式'' AFTER platform_reviewed_by, ADD COLUMN platform_approver_note VARCHAR(512) NULL COMMENT ''平台审核意见'' AFTER platform_reviewer_contact, ADD COLUMN platform_reviewed_at DATETIME NULL COMMENT ''平台审核时间'' AFTER platform_approver_note',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE biz_portal_subscription
SET approval_step = 'PROVIDER'
WHERE UPPER(IFNULL(status,'')) = 'PENDING' AND platform_reviewed_at IS NULL;

UPDATE gov_catalog_subscription
SET approval_step = 'PROVIDER'
WHERE UPPER(IFNULL(status,'')) = 'PENDING' AND platform_reviewed_at IS NULL;

-- 说明：本脚本可重复执行列变更部分；上方 UPDATE 仅影响仍无平台审核痕迹的待审单。
