-- V119: 项目绑定集群账号（多对一：多项目可绑同一集群，一项目仅一个集群）

ALTER TABLE ing_project
  ADD COLUMN cluster_account_id BIGINT NULL COMMENT '绑定的集群账号ID（sys_cluster_account.id）' AFTER bound_org_id;

CREATE INDEX idx_ing_project_cluster ON ing_project (cluster_account_id);
