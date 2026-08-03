-- V117: 集群账号管理（用户中心）

CREATE TABLE IF NOT EXISTS sys_cluster_account (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  cluster_code VARCHAR(64) NOT NULL COMMENT '集群编码',
  cluster_name VARCHAR(128) NOT NULL COMMENT '集群名称',
  account_name VARCHAR(128) NOT NULL COMMENT '账号',
  account_password VARCHAR(256) NULL COMMENT '密码（明文台账，仅内网运维）',
  endpoint VARCHAR(512) NULL COMMENT '访问地址',
  remark VARCHAR(512) NULL COMMENT '备注',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cluster_code (cluster_code),
  KEY idx_cluster_name (cluster_name),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集群账号';

-- 用户中心 · 集群管理
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7703, 7500, '集群管理', 2, '/analytics/support?tab=users.cluster', NULL,
       'hub:analytics:support:users:cluster', NULL, 4, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7703 OR permission = 'hub:analytics:support:users:cluster');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.permission = 'hub:analytics:support:users:cluster'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
