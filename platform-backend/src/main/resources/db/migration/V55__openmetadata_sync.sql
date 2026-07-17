-- OpenMetadata 真实同步与对账台账：登记条目保存 OM entityId/FQN/同步状态；血缘关系保存 OM 两端 FQN 与同步状态。

ALTER TABLE gov_metadata_registry
  ADD COLUMN om_entity_id VARCHAR(64) NULL COMMENT 'OpenMetadata 实体 UUID' AFTER om_ref,
  ADD COLUMN om_sync_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SYNCED/FAILED' AFTER om_entity_id,
  ADD COLUMN om_synced_at DATETIME NULL COMMENT '最近 OM 同步时间' AFTER om_sync_status;

ALTER TABLE gov_meta_relation
  ADD COLUMN om_from_fqn VARCHAR(512) NULL COMMENT '血缘上游 OM FQN' AFTER label,
  ADD COLUMN om_to_fqn VARCHAR(512) NULL COMMENT '血缘下游 OM FQN' AFTER om_from_fqn,
  ADD COLUMN om_sync_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SYNCED/FAILED' AFTER om_to_fqn;
