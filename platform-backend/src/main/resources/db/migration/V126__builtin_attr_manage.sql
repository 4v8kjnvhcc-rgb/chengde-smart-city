-- V126: 内置属性管理（全局：属性维度是否可编辑）+ 系统管理菜单

CREATE TABLE IF NOT EXISTS sys_builtin_attr_config (
  id              BIGINT       NOT NULL PRIMARY KEY COMMENT '固定为 1',
  column_code     TINYINT      NOT NULL DEFAULT 1 COMMENT '属性代码 1可编0锁定',
  column_name     TINYINT      NOT NULL DEFAULT 1 COMMENT '属性名称',
  data_type       TINYINT      NOT NULL DEFAULT 1 COMMENT '数据类型',
  length_val      TINYINT      NOT NULL DEFAULT 1 COMMENT '长度',
  component_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '组件类型',
  nullable_flag   TINYINT      NOT NULL DEFAULT 1 COMMENT '必填',
  updated_at      DATETIME     NULL,
  updated_by      VARCHAR(64)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据项属性编辑控制（内置属性管理）';

INSERT INTO sys_builtin_attr_config (
  id, column_code, column_name, data_type, length_val, component_type, nullable_flag, updated_at, updated_by
) VALUES (1, 1, 1, 1, 1, 1, 1, NOW(), 'system')
ON DUPLICATE KEY UPDATE id = id;

-- 通用支撑 · 系统管理 · 内置属性管理
INSERT INTO sys_menu (
  id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible
)
SELECT 7726, 7504, '内置属性管理', 2, '/analytics/support?tab=sys.builtin', NULL,
       'hub:analytics:support:sys:builtin', NULL, 5, NULL, 'hub', 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE id = 7726 OR permission = 'hub:analytics:support:sys:builtin'
);

-- 授权系统管理员（role_id=1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id = 7726
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 7726);
