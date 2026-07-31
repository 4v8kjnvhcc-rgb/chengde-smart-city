-- V96：数据脱敏策略（规则库 + 策略编排 + 场景绑定 + 密钥引用 + 审计）
-- 对齐 GB/T 43697 / GB/T 37964 与分级分类联动；不含涉密/军事数据

CREATE TABLE IF NOT EXISTS ing_mask_rule (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code          VARCHAR(64)  NOT NULL,
  rule_name          VARCHAR(128) NOT NULL,
  algo_type          VARCHAR(32)  NOT NULL COMMENT 'MASK|REPLACE|TRUNCATE|GENERALIZE|HASH|CLEAR|CONSTANT|ENCRYPT|FPE',
  param_json         TEXT         NULL,
  reversible         TINYINT      NOT NULL DEFAULT 0,
  match_field_pattern VARCHAR(256) NULL COMMENT '字段名匹配，如 *phone*|id_card',
  match_data_type    VARCHAR(64)  NULL,
  match_sensitive_tag VARCHAR(64) NULL,
  fail_policy        VARCHAR(32)  NOT NULL DEFAULT 'DENY' COMMENT 'DENY|PASSTHROUGH|CLEAR',
  version_no         INT          NOT NULL DEFAULT 1,
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  description        VARCHAR(512) NULL,
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mask_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_mask_policy (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_code        VARCHAR(64)  NOT NULL,
  policy_name        VARCHAR(128) NOT NULL,
  scene_code         VARCHAR(64)  NOT NULL COMMENT 'QUERY|LIST|DETAIL|EXPORT|API_SHARE|OPEN_DOWNLOAD|TEST_SYNC|LOG',
  match_level_code   VARCHAR(32)  NULL,
  match_category_id  BIGINT       NULL,
  match_role         VARCHAR(64)  NULL,
  match_purpose      VARCHAR(64)  NULL,
  match_env          VARCHAR(32)  NOT NULL DEFAULT 'PROD' COMMENT 'PROD|TEST',
  priority           INT          NOT NULL DEFAULT 100 COMMENT '越小越优先；冲突再从严',
  strict_mode        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=冲突从严',
  rule_ids_json      TEXT         NULL COMMENT '有序规则ID列表',
  field_overrides_json TEXT       NULL COMMENT '字段级覆盖 {field:ruleId}',
  version_no         INT          NOT NULL DEFAULT 1,
  status             VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|ACTIVE|INACTIVE',
  published_at       DATETIME     NULL,
  description        VARCHAR(512) NULL,
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mask_policy_code_ver (policy_code, version_no),
  KEY idx_mask_policy_scene (scene_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_mask_binding (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  binding_code       VARCHAR(64)  NOT NULL,
  policy_id          BIGINT       NOT NULL,
  target_type        VARCHAR(32)  NOT NULL COMMENT 'ASSET|TABLE|REGISTRY|API|TASK',
  target_id          BIGINT       NULL,
  target_code        VARCHAR(128) NULL,
  target_name        VARCHAR(256) NULL,
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mask_binding_code (binding_code),
  KEY idx_mask_binding_policy (policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_mask_crypto_ref (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  ref_code           VARCHAR(64)  NOT NULL,
  ref_name           VARCHAR(128) NOT NULL,
  algo               VARCHAR(32)  NOT NULL DEFAULT 'AES_GCM',
  key_alias          VARCHAR(128) NOT NULL COMMENT 'KMS/本地密钥别名，禁止硬编码明文密钥',
  rotate_days        INT          NOT NULL DEFAULT 90,
  owner_org          VARCHAR(128) NULL,
  status             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  remark             VARCHAR(512) NULL,
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mask_crypto_ref (ref_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_mask_audit_log (
  id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
  action_type        VARCHAR(32)  NOT NULL COMMENT 'PREVIEW|APPLY|PUBLISH|RESTORE|CONFIG',
  policy_id          BIGINT       NULL,
  policy_version     INT          NULL,
  rule_id            BIGINT       NULL,
  target_type        VARCHAR(32)  NULL,
  target_id          BIGINT       NULL,
  scene_code         VARCHAR(64)  NULL,
  restored           TINYINT      NOT NULL DEFAULT 0,
  sample_before      VARCHAR(512) NULL,
  sample_after       VARCHAR(512) NULL,
  result             VARCHAR(32)  NULL,
  operator_id        BIGINT       NULL,
  operator_name      VARCHAR(64)  NULL,
  detail_json        TEXT         NULL,
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_mask_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子规则
INSERT INTO ing_mask_rule (rule_code, rule_name, algo_type, param_json, reversible, match_field_pattern, match_sensitive_tag, fail_policy, description, status)
SELECT 'MR_PHONE_MASK', '手机号中间掩码', 'MASK',
       '{"keepPrefix":3,"keepSuffix":4,"maskChar":"*"}', 0, '*phone*|*mobile*', 'PII_PHONE', 'CLEAR',
       '保留前3后4，中间掩码', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_rule WHERE rule_code = 'MR_PHONE_MASK');

INSERT INTO ing_mask_rule (rule_code, rule_name, algo_type, param_json, reversible, match_field_pattern, match_sensitive_tag, fail_policy, description, status)
SELECT 'MR_ID_MASK', '身份证掩码', 'MASK',
       '{"keepPrefix":4,"keepSuffix":4,"maskChar":"*"}', 0, '*id_card*|*idcard*|*sfzh*', 'PII_ID', 'CLEAR',
       '保留前4后4', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_rule WHERE rule_code = 'MR_ID_MASK');

INSERT INTO ing_mask_rule (rule_code, rule_name, algo_type, param_json, reversible, match_field_pattern, match_sensitive_tag, fail_policy, description, status)
SELECT 'MR_NAME_REPLACE', '姓名替身', 'REPLACE',
       '{"replacement":"**"}', 0, '*name*|*xm*', 'PII_NAME', 'CLEAR',
       '姓名替换为固定符', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_rule WHERE rule_code = 'MR_NAME_REPLACE');

INSERT INTO ing_mask_rule (rule_code, rule_name, algo_type, param_json, reversible, match_field_pattern, fail_policy, description, status)
SELECT 'MR_ADDR_GEN', '地址泛化到区县', 'GENERALIZE',
       '{"level":"district"}', 0, '*addr*|*address*', 'CLEAR',
       '地址泛化（截断到区县级示意）', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_rule WHERE rule_code = 'MR_ADDR_GEN');

INSERT INTO ing_mask_rule (rule_code, rule_name, algo_type, param_json, reversible, match_field_pattern, fail_policy, description, status)
SELECT 'MR_HASH_SALT', '加盐哈希', 'HASH',
       '{"algo":"SHA-256","saltRef":"DEFAULT_SALT"}', 0, '*token*|*secret*', 'DENY',
       '不可逆哈希，盐引用别名', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_rule WHERE rule_code = 'MR_HASH_SALT');

INSERT INTO ing_mask_rule (rule_code, rule_name, algo_type, param_json, reversible, match_field_pattern, fail_policy, description, status)
SELECT 'MR_CLEAR', '置空清空', 'CLEAR', '{}', 0, NULL, 'CLEAR', '清空字段值', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_rule WHERE rule_code = 'MR_CLEAR');

INSERT INTO ing_mask_rule (rule_code, rule_name, algo_type, param_json, reversible, match_field_pattern, fail_policy, description, status)
SELECT 'MR_AES_REF', '可逆加密(密钥引用)', 'ENCRYPT',
       '{"cryptoRefCode":"CRYPTO_DEFAULT","mode":"AES_GCM"}', 1, '*bank*|*account*', 'DENY',
       '可逆加密，仅授权还原', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_rule WHERE rule_code = 'MR_AES_REF');

-- 种子策略（关联分级：重要/核心更严）
INSERT INTO ing_mask_policy (policy_code, policy_name, scene_code, match_level_code, match_env, priority, strict_mode,
                            rule_ids_json, version_no, status, published_at, description)
SELECT 'MP_QUERY_GENERAL', '一般数据-查询展示', 'QUERY', 'GENERAL', 'PROD', 50, 1,
       (SELECT CONCAT('[', GROUP_CONCAT(id ORDER BY id), ']') FROM ing_mask_rule WHERE rule_code IN ('MR_PHONE_MASK','MR_ID_MASK')),
       1, 'ACTIVE', NOW(), '一般数据在线查询默认掩码'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_policy WHERE policy_code = 'MP_QUERY_GENERAL' AND version_no = 1);

INSERT INTO ing_mask_policy (policy_code, policy_name, scene_code, match_level_code, match_env, priority, strict_mode,
                            rule_ids_json, version_no, status, published_at, description)
SELECT 'MP_EXPORT_IMPORTANT', '重要数据-导出强制脱敏', 'EXPORT', 'IMPORTANT', 'PROD', 20, 1,
       (SELECT CONCAT('[', GROUP_CONCAT(id ORDER BY id), ']') FROM ing_mask_rule WHERE rule_code IN ('MR_PHONE_MASK','MR_ID_MASK','MR_NAME_REPLACE','MR_ADDR_GEN')),
       1, 'ACTIVE', NOW(), '重要数据导出加强脱敏'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_policy WHERE policy_code = 'MP_EXPORT_IMPORTANT' AND version_no = 1);

INSERT INTO ing_mask_policy (policy_code, policy_name, scene_code, match_level_code, match_env, priority, strict_mode,
                            rule_ids_json, version_no, status, published_at, description)
SELECT 'MP_SHARE_CORE', '核心数据-共享加密', 'API_SHARE', 'CORE', 'PROD', 10, 1,
       (SELECT CONCAT('[', GROUP_CONCAT(id ORDER BY id), ']') FROM ing_mask_rule WHERE rule_code IN ('MR_CLEAR','MR_AES_REF','MR_HASH_SALT')),
       1, 'ACTIVE', NOW(), '核心数据共享默认清空/加密/哈希'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_policy WHERE policy_code = 'MP_SHARE_CORE' AND version_no = 1);

INSERT INTO ing_mask_crypto_ref (ref_code, ref_name, algo, key_alias, rotate_days, owner_org, status, remark)
SELECT 'CRYPTO_DEFAULT', '默认业务加密密钥引用', 'AES_GCM', 'kms://chengde/mask/default', 90, '示范单位', 'ACTIVE',
       '仅存别名，对接 KMS；禁止硬编码密钥材料'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_mask_crypto_ref WHERE ref_code = 'CRYPTO_DEFAULT');
