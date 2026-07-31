-- V95：归集「数据资产管理」— 分级分类（对齐 GB/T 43697-2024 框架，级别/分类可配置）+ Hub 菜单

-- 1) 敏感级别字典（默认：一般 / 重要 / 核心；可扩展 1～4 级）
CREATE TABLE IF NOT EXISTS ing_cls_level (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  level_code         VARCHAR(32)  NOT NULL,
  level_name         VARCHAR(64)  NOT NULL,
  sort_no            INT          NOT NULL DEFAULT 0,
  sensitivity_score  INT          NOT NULL DEFAULT 1 COMMENT '敏感分，越大越严',
  control_strength   VARCHAR(32)  NOT NULL DEFAULT 'LOW' COMMENT 'LOW|MEDIUM|HIGH',
  description        VARCHAR(512) NULL,
  share_allowed      TINYINT      NOT NULL DEFAULT 1,
  open_allowed       TINYINT      NOT NULL DEFAULT 0,
  mask_required      TINYINT      NOT NULL DEFAULT 0,
  approval_level     VARCHAR(32)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE|L1|L2|L3',
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cls_level_code (level_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) 多维分类树
CREATE TABLE IF NOT EXISTS ing_cls_category (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_code      VARCHAR(64)  NOT NULL,
  category_name      VARCHAR(128) NOT NULL,
  parent_id          BIGINT       NULL,
  dim_type           VARCHAR(32)  NOT NULL COMMENT 'BUSINESS|OBJECT|SOURCE|CONTENT',
  path               VARCHAR(512) NULL,
  sort_no            INT          NOT NULL DEFAULT 0,
  share_scope_hint   VARCHAR(256) NULL COMMENT '共享域提示',
  description        VARCHAR(512) NULL,
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cls_category_code (category_code),
  KEY idx_cls_cat_parent (parent_id),
  KEY idx_cls_cat_dim (dim_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) 资产分类分级标注
CREATE TABLE IF NOT EXISTS ing_cls_asset_mark (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  asset_type         VARCHAR(32)  NOT NULL COMMENT 'TABLE|ASSET|REGISTRY',
  asset_id           BIGINT       NOT NULL,
  asset_code         VARCHAR(128) NULL,
  asset_name         VARCHAR(256) NULL,
  category_id        BIGINT       NULL,
  level_code         VARCHAR(32)  NOT NULL,
  grade_basis        VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL|RULE_SUGGEST|REVIEWED',
  grade_reason       VARCHAR(512) NULL,
  features_json      TEXT         NULL COMMENT '多维特征 JSON',
  version_no         INT          NOT NULL DEFAULT 1,
  graded_by          VARCHAR(64)  NULL,
  graded_at          DATETIME     NULL,
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cls_mark_asset (asset_type, asset_id),
  KEY idx_cls_mark_level (level_code),
  KEY idx_cls_mark_cat (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4) 使用范围策略（级别/类别；冲突取更严）
CREATE TABLE IF NOT EXISTS ing_cls_scope_rule (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code          VARCHAR(64)  NOT NULL,
  rule_name          VARCHAR(128) NOT NULL,
  level_code         VARCHAR(32)  NULL,
  category_id        BIGINT       NULL,
  action_type        VARCHAR(32)  NOT NULL COMMENT 'VIEW|SHARE|OPEN|EXPORT|TRANSFER',
  allow_flag         TINYINT      NOT NULL DEFAULT 1,
  mask_required      TINYINT      NOT NULL DEFAULT 0,
  approval_required  TINYINT      NOT NULL DEFAULT 0,
  subject_scope      VARCHAR(64)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL|ORG_INTERNAL|WHITELIST',
  severity_weight    INT          NOT NULL DEFAULT 1 COMMENT '越大越严，冲突取最大',
  remark             VARCHAR(512) NULL,
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cls_scope_code (rule_code),
  KEY idx_cls_scope_action (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5) 变更审计
CREATE TABLE IF NOT EXISTS ing_cls_audit_log (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  mark_id            BIGINT       NULL,
  asset_type         VARCHAR(32)  NOT NULL,
  asset_id           BIGINT       NOT NULL,
  change_type        VARCHAR(32)  NOT NULL COMMENT 'CREATE|UPDATE|CONFIRM|BATCH',
  before_json        TEXT         NULL,
  after_json         TEXT         NULL,
  operator_id        BIGINT       NULL,
  operator_name      VARCHAR(64)  NULL,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_cls_audit_asset (asset_type, asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6) 策略命中日志
CREATE TABLE IF NOT EXISTS ing_cls_hit_log (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  asset_type         VARCHAR(32)  NOT NULL,
  asset_id           BIGINT       NOT NULL,
  action_type        VARCHAR(32)  NOT NULL,
  level_code         VARCHAR(32)  NULL,
  category_id        BIGINT       NULL,
  rule_id            BIGINT       NULL,
  result             VARCHAR(16)  NOT NULL COMMENT 'ALLOW|DENY',
  reason             VARCHAR(512) NULL,
  operator_name      VARCHAR(64)  NULL,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_cls_hit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子：级别（GB/T 43697 一般/重要/核心）
INSERT INTO ing_cls_level (level_code, level_name, sort_no, sensitivity_score, control_strength, description,
                           share_allowed, open_allowed, mask_required, approval_level, status)
SELECT 'GENERAL', '一般数据', 1, 1, 'LOW', '泄露影响可控，可按规定共享', 1, 1, 0, 'NONE', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_level WHERE level_code = 'GENERAL');

INSERT INTO ing_cls_level (level_code, level_name, sort_no, sensitivity_score, control_strength, description,
                           share_allowed, open_allowed, mask_required, approval_level, status)
SELECT 'IMPORTANT', '重要数据', 2, 5, 'MEDIUM', '泄露可能影响公共利益或安全，共享须审批并脱敏', 1, 0, 1, 'L2', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_level WHERE level_code = 'IMPORTANT');

INSERT INTO ing_cls_level (level_code, level_name, sort_no, sensitivity_score, control_strength, description,
                           share_allowed, open_allowed, mask_required, approval_level, status)
SELECT 'CORE', '核心数据', 3, 9, 'HIGH', '高敏感，默认禁止开放，跨域/出境严控', 0, 0, 1, 'L3', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_level WHERE level_code = 'CORE');

-- 种子：分类（业务主题 / 数据对象 / 来源 / 内容属性）
INSERT INTO ing_cls_category (category_code, category_name, parent_id, dim_type, path, sort_no, share_scope_hint, description, status)
SELECT 'BIZ_POP', '人口主题', NULL, 'BUSINESS', '/BIZ_POP', 1, '政务内部共享', '人口相关业务主题', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_category WHERE category_code = 'BIZ_POP');

INSERT INTO ing_cls_category (category_code, category_name, parent_id, dim_type, path, sort_no, share_scope_hint, description, status)
SELECT 'BIZ_ENT', '法人主题', NULL, 'BUSINESS', '/BIZ_ENT', 2, '政务内部共享', '法人与市场主体', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_category WHERE category_code = 'BIZ_ENT');

INSERT INTO ing_cls_category (category_code, category_name, parent_id, dim_type, path, sort_no, share_scope_hint, description, status)
SELECT 'OBJ_PERSON', '自然人', NULL, 'OBJECT', '/OBJ_PERSON', 1, '涉个人信息从严', '数据对象：自然人', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_category WHERE category_code = 'OBJ_PERSON');

INSERT INTO ing_cls_category (category_code, category_name, parent_id, dim_type, path, sort_no, share_scope_hint, description, status)
SELECT 'SRC_DEPT', '部门业务系统', NULL, 'SOURCE', '/SRC_DEPT', 1, '按提供方授权', '来源：部门业务系统', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_category WHERE category_code = 'SRC_DEPT');

INSERT INTO ing_cls_category (category_code, category_name, parent_id, dim_type, path, sort_no, share_scope_hint, description, status)
SELECT 'CNT_ID', '身份标识类', NULL, 'CONTENT', '/CNT_ID', 1, '须脱敏后共享', '内容属性：身份标识', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_category WHERE category_code = 'CNT_ID');

-- 种子：使用范围策略
INSERT INTO ing_cls_scope_rule (rule_code, rule_name, level_code, category_id, action_type, allow_flag, mask_required, approval_required, subject_scope, severity_weight, remark, status)
SELECT 'SR_GEN_SHARE', '一般数据允许共享', 'GENERAL', NULL, 'SHARE', 1, 0, 0, 'ALL', 1, '级别默认', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_scope_rule WHERE rule_code = 'SR_GEN_SHARE');

INSERT INTO ing_cls_scope_rule (rule_code, rule_name, level_code, category_id, action_type, allow_flag, mask_required, approval_required, subject_scope, severity_weight, remark, status)
SELECT 'SR_IMP_SHARE', '重要数据共享须脱敏审批', 'IMPORTANT', NULL, 'SHARE', 1, 1, 1, 'ORG_INTERNAL', 5, '级别默认', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_scope_rule WHERE rule_code = 'SR_IMP_SHARE');

INSERT INTO ing_cls_scope_rule (rule_code, rule_name, level_code, category_id, action_type, allow_flag, mask_required, approval_required, subject_scope, severity_weight, remark, status)
SELECT 'SR_CORE_SHARE', '核心数据禁止共享', 'CORE', NULL, 'SHARE', 0, 1, 1, 'WHITELIST', 9, '级别默认', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_scope_rule WHERE rule_code = 'SR_CORE_SHARE');

INSERT INTO ing_cls_scope_rule (rule_code, rule_name, level_code, category_id, action_type, allow_flag, mask_required, approval_required, subject_scope, severity_weight, remark, status)
SELECT 'SR_CORE_OPEN', '核心数据禁止开放', 'CORE', NULL, 'OPEN', 0, 1, 1, 'WHITELIST', 9, '级别默认', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_scope_rule WHERE rule_code = 'SR_CORE_OPEN');

INSERT INTO ing_cls_scope_rule (rule_code, rule_name, level_code, category_id, action_type, allow_flag, mask_required, approval_required, subject_scope, severity_weight, remark, status)
SELECT 'SR_ID_SHARE', '身份标识类共享须脱敏', NULL,
       (SELECT id FROM ing_cls_category WHERE category_code = 'CNT_ID' LIMIT 1),
       'SHARE', 1, 1, 1, 'ORG_INTERNAL', 6, '类别策略', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_cls_scope_rule WHERE rule_code = 'SR_ID_SHARE')
  AND EXISTS (SELECT 1 FROM ing_cls_category WHERE category_code = 'CNT_ID');

-- Hub 菜单：数据资产管理
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7025, 7020, '数据资产管理', 2,
       '/exchange/ingestion?system=collect&module=asset.classify',
       NULL, 'hub:ingestion:collect:asset', NULL, 5, 'M069', 'hub', 1, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7025 OR permission = 'hub:ingestion:collect:asset');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 7025
FROM DUAL
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 7025)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 7025);
