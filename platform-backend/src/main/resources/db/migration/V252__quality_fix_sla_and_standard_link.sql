-- 生产 bug：整改时间要求 + 标准数据之间映射
CREATE TABLE IF NOT EXISTS gov_quality_fix_sla (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  rule_code       VARCHAR(64)  NULL COMMENT '关联质量规则编码，可空表示通用',
  rule_name       VARCHAR(128) NULL,
  severity        VARCHAR(32)  NOT NULL DEFAULT 'IMPORTANT' COMMENT 'GENERAL|IMPORTANT|CRITICAL',
  fix_days        INT          NOT NULL DEFAULT 7 COMMENT '整改时限（天）',
  overdue_action  VARCHAR(32)  NOT NULL DEFAULT 'ALERT' COMMENT 'ALERT|ESCALATE',
  notify_roles    VARCHAR(256) NULL COMMENT '通知角色，逗号分隔',
  remark          VARCHAR(512) NULL,
  sort_no         INT          NOT NULL DEFAULT 0,
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_by      VARCHAR(64)  NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NULL,
  KEY idx_gov_fix_sla (status, severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量问题整改时间要求';

CREATE TABLE IF NOT EXISTS gov_standard_item_link (
  id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  source_item_id    BIGINT       NOT NULL COMMENT '源标准项',
  target_item_id    BIGINT       NOT NULL COMMENT '目标标准项',
  link_type         VARCHAR(32)  NOT NULL DEFAULT 'EQUIVALENT' COMMENT 'EQUIVALENT|ALIAS|REFINES',
  remark            VARCHAR(512) NULL,
  status            VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_by        VARCHAR(64)  NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NULL,
  UNIQUE KEY uk_gov_std_link (source_item_id, target_item_id, link_type),
  KEY idx_gov_std_link_tgt (target_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准数据项之间映射关系';

INSERT INTO gov_quality_fix_sla (rule_code, rule_name, severity, fix_days, overdue_action, notify_roles, remark, sort_no, status, created_by)
SELECT NULL, '通用-重要问题', 'IMPORTANT', 7, 'ALERT', 'DEPT_ADMIN', '默认：重要问题 7 日内整改', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM gov_quality_fix_sla WHERE rule_name='通用-重要问题' AND severity='IMPORTANT');

INSERT INTO gov_quality_fix_sla (rule_code, rule_name, severity, fix_days, overdue_action, notify_roles, remark, sort_no, status, created_by)
SELECT NULL, '通用-严重问题', 'CRITICAL', 3, 'ESCALATE', 'DEPT_ADMIN,SYSTEM_ADMIN', '默认：严重问题 3 日内整改并升级', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM gov_quality_fix_sla WHERE rule_name='通用-严重问题' AND severity='CRITICAL');
