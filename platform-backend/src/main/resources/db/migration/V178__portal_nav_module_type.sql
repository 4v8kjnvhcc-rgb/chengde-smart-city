-- 门户节点类型扩展：module 为 system 的下一级
ALTER TABLE portal_nav_node
  MODIFY COLUMN node_type VARCHAR(32) NOT NULL COMMENT 'platform / sub_platform / system / module';
