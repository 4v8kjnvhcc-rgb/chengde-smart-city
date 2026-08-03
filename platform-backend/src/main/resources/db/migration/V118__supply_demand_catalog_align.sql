-- V118: 供需对接 — 目录与统一编目对齐；清理旧供需目录；状态机预审/审核迁移

-- 1) 异议表扩展（纠错回流）
ALTER TABLE biz_catalog_objection
  ADD COLUMN title VARCHAR(200) NULL COMMENT '异议标题' AFTER id,
  ADD COLUMN demand_id BIGINT NULL COMMENT '关联需求，回流审核' AFTER catalog_id,
  ADD COLUMN provider_org VARCHAR(128) NULL COMMENT '异议提供单位' AFTER content,
  ADD COLUMN verify_org VARCHAR(128) NULL COMMENT '异议核查单位' AFTER provider_org;

-- 2) 清空指向旧目录的引用
UPDATE biz_data_demand d
LEFT JOIN biz_catalog_item c ON c.id = d.matched_catalog_id
SET d.matched_catalog_id = NULL
WHERE d.matched_catalog_id IS NOT NULL AND (c.id IS NULL OR c.gov_resource_id IS NULL);

UPDATE biz_data_demand d
LEFT JOIN biz_catalog_item c ON c.id = d.target_catalog_id
SET d.target_catalog_id = NULL
WHERE d.target_catalog_id IS NOT NULL AND (c.id IS NULL OR c.gov_resource_id IS NULL);

UPDATE biz_data_duty d
LEFT JOIN biz_catalog_item c ON c.id = d.catalog_id
SET d.catalog_id = NULL
WHERE d.catalog_id IS NOT NULL AND (c.id IS NULL OR c.gov_resource_id IS NULL);

UPDATE biz_catalog_objection o
LEFT JOIN biz_catalog_item c ON c.id = o.catalog_id
SET o.status = 'CLOSED',
    o.handler_note = CONCAT(IFNULL(o.handler_note, ''), ' [关联目录已清理]')
WHERE o.catalog_id IS NOT NULL AND (c.id IS NULL OR c.gov_resource_id IS NULL);

-- 3) 删除旧目录相关清单与目录行（仅保留统一编目同步项）
DELETE m FROM biz_supply_manifest m
LEFT JOIN biz_catalog_item c ON m.manifest_type = 'CATALOG' AND m.ref_id = c.id
WHERE m.manifest_type = 'CATALOG' AND (c.id IS NULL OR c.gov_resource_id IS NULL);

DELETE FROM biz_catalog_item WHERE gov_resource_id IS NULL;

-- 4) 需求阶段/状态：ANALYZE→PRE_AUDIT，CONFIRM→AUDIT，ANALYZING→PRE_AUDITING
UPDATE biz_data_demand SET stage = 'PRE_AUDIT' WHERE stage = 'ANALYZE';
UPDATE biz_data_demand SET stage = 'AUDIT' WHERE stage = 'CONFIRM';
UPDATE biz_data_demand SET status = 'PRE_AUDITING' WHERE status = 'ANALYZING';
