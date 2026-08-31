-- 资源目录审批增加「目录提供单位」二级审核节点
-- PLATFORM=平台/超级管理员；PROVIDER=信息资源提供方（provider_org）组织审核

ALTER TABLE gov_catalog_approval
  ADD COLUMN approval_step VARCHAR(32) NOT NULL DEFAULT 'PLATFORM'
    COMMENT 'PLATFORM=平台管理员审核; PROVIDER=目录提供单位审核'
    AFTER action_type;

UPDATE gov_catalog_approval
SET approval_step = 'PLATFORM'
WHERE approval_step IS NULL OR approval_step = '';
