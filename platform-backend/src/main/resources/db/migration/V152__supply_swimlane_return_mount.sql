-- 供需对接泳道：提供方退回待裁决、确认后 10 工作日挂载门户
ALTER TABLE biz_data_demand
    ADD COLUMN catalog_mount_deadline DATETIME NULL COMMENT '同意提供后挂载目录截止（10工作日）' AFTER supervise_by,
    ADD COLUMN catalog_mounted_at DATETIME NULL COMMENT '目录已挂载至门户时间' AFTER catalog_mount_deadline,
    ADD COLUMN return_kind VARCHAR(32) NULL COMMENT '退回类型：ADMIN_MATERIAL/ADMIN_PORTAL/PROVIDER_PENDING/ADMIN_AGREE_RETURN' AFTER catalog_mounted_at;

-- 历史：供数部门已直接打回需求部门的记录保持 RETURNED；新流程使用 PROVIDER_RETURNED
