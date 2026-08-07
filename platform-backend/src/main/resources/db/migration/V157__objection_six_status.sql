-- 异议清单六态迁移（不改其它业务表状态机）
-- DRAFT / SUBMITTED / APPROVED / REJECTED / PROCESSED / CLOSED
UPDATE biz_catalog_objection SET status = 'SUBMITTED' WHERE UPPER(status) IN ('OPEN');
UPDATE biz_catalog_objection SET status = 'APPROVED' WHERE UPPER(status) IN ('PROCESSING');
-- CLOSED 保持为已办结
