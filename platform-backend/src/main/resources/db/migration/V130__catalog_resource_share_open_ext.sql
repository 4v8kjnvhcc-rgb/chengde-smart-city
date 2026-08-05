-- V130: 资源编目扩展共享/开放/联系人及关联资源扩展 JSON（对齐手动新增两步向导）

ALTER TABLE gov_catalog_resource
  ADD COLUMN share_condition VARCHAR(256) NULL COMMENT '有条件共享时的共享条件' AFTER share_type,
  ADD COLUMN not_share_reason VARCHAR(512) NULL COMMENT '不予共享理由' AFTER share_condition,
  ADD COLUMN open_type VARCHAR(32) NULL DEFAULT 'SOCIAL_OPEN' COMMENT 'SOCIAL_OPEN|NOT_OPEN' AFTER not_share_reason,
  ADD COLUMN open_condition VARCHAR(256) NULL COMMENT '对社会开放时的开放条件' AFTER open_type,
  ADD COLUMN not_open_reason VARCHAR(512) NULL COMMENT '不开放理由' AFTER open_condition,
  ADD COLUMN contact_name VARCHAR(64) NULL COMMENT '联系人' AFTER not_open_reason,
  ADD COLUMN contact_phone VARCHAR(64) NULL COMMENT '联系电话' AFTER contact_name,
  ADD COLUMN contact_email VARCHAR(128) NULL COMMENT '联系邮箱' AFTER contact_phone,
  ADD COLUMN theme_name VARCHAR(128) NULL COMMENT '所属主题' AFTER contact_email,
  ADD COLUMN base_catalog_name VARCHAR(128) NULL COMMENT '基础信息资源目录' AFTER theme_name,
  ADD COLUMN tags VARCHAR(512) NULL COMMENT '标签，逗号分隔' AFTER base_catalog_name,
  ADD COLUMN ext_json TEXT NULL COMMENT '接口/文件/字段清单等关联扩展 JSON' AFTER tags;
