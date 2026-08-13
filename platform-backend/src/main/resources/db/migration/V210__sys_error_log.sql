-- V199: 系统运行/报错日志（前后端统一）+ 挂到「日志审计 → 系统运行日志」

CREATE TABLE IF NOT EXISTS sys_error_log (
  id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  source          VARCHAR(16)   NOT NULL COMMENT 'FRONTEND/BACKEND/JOB/GATEWAY',
  module_code     VARCHAR(64)   NULL COMMENT '功能模块码，如 hub:system:uum',
  module_name     VARCHAR(128)  NULL COMMENT '功能模块中文名',
  level           VARCHAR(16)   NOT NULL DEFAULT 'ERROR' COMMENT 'DEBUG/INFO/WARN/ERROR/FATAL',
  error_code      VARCHAR(64)   NULL COMMENT '业务或HTTP错误码',
  error_type      VARCHAR(128)  NULL COMMENT '异常类名或前端错误类型',
  message         VARCHAR(1024) NOT NULL COMMENT '简要错误信息',
  stack_trace     MEDIUMTEXT    NULL COMMENT '堆栈（脱敏后）',
  request_uri     VARCHAR(512)  NULL COMMENT '后端API路径或前端路由',
  http_method     VARCHAR(16)   NULL,
  http_status     INT           NULL,
  page_url        VARCHAR(1024) NULL COMMENT '前端完整URL',
  trace_id        VARCHAR(64)   NULL COMMENT '链路追踪ID',
  user_id         BIGINT        NULL,
  username        VARCHAR(64)   NULL,
  org_id          BIGINT        NULL,
  client_ip       VARCHAR(64)   NULL,
  user_agent      VARCHAR(512)  NULL,
  app_version     VARCHAR(32)   NULL,
  env             VARCHAR(16)   NULL COMMENT 'dev/test/prod',
  extra_json      JSON          NULL COMMENT '扩展上下文（勿存密码/令牌）',
  occurred_at     DATETIME      NOT NULL COMMENT '报错发生时间',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  KEY idx_err_time (occurred_at),
  KEY idx_err_source_module (source, module_code, occurred_at),
  KEY idx_err_level (level, occurred_at),
  KEY idx_err_trace (trace_id),
  KEY idx_err_user (user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='系统运行/报错日志（前后端统一）';

-- 通用支撑平台：日志审计 → 系统运行日志
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7733, 7505, '系统运行日志', 2, '/analytics/support?tab=audit.runtime', NULL,
       'hub:analytics:support:audit:runtime', NULL, 4, NULL, 'hub', 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE id = 7733 OR permission = 'hub:analytics:support:audit:runtime'
);

-- 统一用户 Hub：日志审计改为目录，挂操作审计 + 系统运行日志
UPDATE sys_menu
SET menu_name = '日志审计',
    menu_type = 1,
    path = NULL,
    component = NULL,
    permission = 'hub:system:uum:audit',
    status = 1
WHERE id = 7605;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7630, 7605, '操作审计', 2, '/system/uum?tab=audit.log', NULL,
       'hub:system:uum:audit:log', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE id = 7630 OR permission = 'hub:system:uum:audit:log'
);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7631, 7605, '系统运行日志', 2, '/system/uum?tab=audit.runtime', NULL,
       'hub:system:uum:audit:runtime', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE id = 7631 OR permission = 'hub:system:uum:audit:runtime'
);

-- 列表权限按钮（复用审计能力）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7632, 7631, '运行日志查询', 3, NULL, NULL, 'system:error-log:list', NULL, 1, NULL, 'self', 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE id = 7632 OR permission = 'system:error-log:list'
);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7633, 7631, '运行日志上报', 3, NULL, NULL, 'system:error-log:report', NULL, 2, NULL, 'self', 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE id = 7633 OR permission = 'system:error-log:report'
);

-- 系统管理员角色授权
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id IN (7733, 7630, 7631, 7632, 7633)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
