-- V129: 资源目录审批覆盖分类增删改、关联/解绑、编目新增等（对齐采集汇聚规格）

ALTER TABLE gov_catalog_approval
  MODIFY COLUMN resource_id BIGINT NULL COMMENT '编目资源ID，分类类审批可为空',
  MODIFY COLUMN action_type VARCHAR(32) NOT NULL
    COMMENT 'PUBLISH/OFFLINE/UPDATE/DELETE/CREATE/BIND/UNBIND/CAT_CREATE/CAT_UPDATE/CAT_DELETE';

ALTER TABLE gov_catalog_approval
  ADD COLUMN category_id BIGINT NULL COMMENT '分类ID' AFTER resource_id,
  ADD COLUMN catalog_origin VARCHAR(32) NULL COMMENT 'INGEST|GOVERNANCE' AFTER category_id,
  ADD COLUMN payload_json TEXT NULL COMMENT '待审批变更快照 JSON' AFTER reviewed_at;

ALTER TABLE gov_catalog_approval
  ADD KEY idx_approval_category (category_id),
  ADD KEY idx_approval_origin (catalog_origin);
