-- V97：数据标签管理增强（维度、同义词、识别规则、打标确认、审计）
-- 复用 ing_asset_tag / ing_asset_tag_binding，不推翻国标标准类目

ALTER TABLE ing_asset_tag
  ADD COLUMN dim_type VARCHAR(32) NULL COMMENT 'BUSINESS|THEME|OBJECT|SCENE|FREQUENCY|QUALITY|OTHER' AFTER tag_source,
  ADD COLUMN value_type VARCHAR(32) NOT NULL DEFAULT 'ENUM' COMMENT 'ENUM|TREE|TEXT|BOOL|MULTI' AFTER dim_type,
  ADD COLUMN synonyms VARCHAR(512) NULL COMMENT '同义词，逗号/分号分隔' AFTER tag_desc,
  ADD COLUMN color VARCHAR(32) NULL AFTER synonyms,
  ADD COLUMN multi_select TINYINT NOT NULL DEFAULT 1 AFTER color,
  ADD COLUMN required_flag TINYINT NOT NULL DEFAULT 0 AFTER multi_select,
  ADD COLUMN sort_no INT NOT NULL DEFAULT 0 AFTER required_flag;

ALTER TABLE ing_asset_tag_binding
  ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL|RULE|IMPORT' AFTER asset_id,
  ADD COLUMN confidence DECIMAL(5,2) NULL COMMENT '0-100' AFTER source,
  ADD COLUMN confirm_status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED' COMMENT 'SUGGESTED|CONFIRMED|REJECTED' AFTER confidence,
  ADD COLUMN tagged_by VARCHAR(64) NULL AFTER confirm_status,
  ADD COLUMN note VARCHAR(256) NULL AFTER tagged_by;

CREATE TABLE IF NOT EXISTS ing_tag_dim (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  dim_code      VARCHAR(32)  NOT NULL,
  dim_name      VARCHAR(64)  NOT NULL,
  description   VARCHAR(256) NULL,
  sort_no       INT          NOT NULL DEFAULT 0,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tag_dim_code (dim_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_tag_rule (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code       VARCHAR(64)  NOT NULL,
  rule_name       VARCHAR(128) NOT NULL,
  tag_id          BIGINT       NOT NULL,
  condition_json  TEXT         NOT NULL COMMENT 'nameRegex/commentKeywords/dataTypes/minConfidence 等',
  action_type     VARCHAR(16)  NOT NULL DEFAULT 'SUGGEST' COMMENT 'AUTO|SUGGEST',
  priority        INT          NOT NULL DEFAULT 100,
  conflict_policy VARCHAR(32)  NOT NULL DEFAULT 'KEEP_MANUAL' COMMENT 'PRIORITY|KEEP_MANUAL|OVERRIDE',
  version_no      INT          NOT NULL DEFAULT 1,
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  description     VARCHAR(512) NULL,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tag_rule_code (rule_code),
  KEY idx_tag_rule_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_tag_audit_log (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  action_type   VARCHAR(32)  NOT NULL COMMENT 'CREATE_TAG|UPDATE_TAG|BIND|UNBIND|CONFIRM|REJECT|RULE_RUN|MERGE',
  tag_id        BIGINT       NULL,
  asset_type    VARCHAR(16)  NULL,
  asset_id      BIGINT       NULL,
  before_json   TEXT         NULL,
  after_json    TEXT         NULL,
  source        VARCHAR(16)  NULL,
  operator_id   BIGINT       NULL,
  operator_name VARCHAR(64)  NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tag_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ing_tag_dim (dim_code, dim_name, description, sort_no, status)
SELECT 'BUSINESS', '业务域', '按业务领域划分', 1, 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ing_tag_dim WHERE dim_code = 'BUSINESS');
INSERT INTO ing_tag_dim (dim_code, dim_name, description, sort_no, status)
SELECT 'THEME', '主题', '业务主题', 2, 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ing_tag_dim WHERE dim_code = 'THEME');
INSERT INTO ing_tag_dim (dim_code, dim_name, description, sort_no, status)
SELECT 'OBJECT', '数据对象', '自然人/法人等对象', 3, 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ing_tag_dim WHERE dim_code = 'OBJECT');
INSERT INTO ing_tag_dim (dim_code, dim_name, description, sort_no, status)
SELECT 'SCENE', '应用场景', '共享/开放/内部等场景', 4, 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ing_tag_dim WHERE dim_code = 'SCENE');
INSERT INTO ing_tag_dim (dim_code, dim_name, description, sort_no, status)
SELECT 'FREQUENCY', '更新频率', '实时/日/月等', 5, 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ing_tag_dim WHERE dim_code = 'FREQUENCY');
INSERT INTO ing_tag_dim (dim_code, dim_name, description, sort_no, status)
SELECT 'QUALITY', '质量等级', '业务质量相关标签', 6, 'ACTIVE' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ing_tag_dim WHERE dim_code = 'QUALITY');

INSERT INTO ing_asset_tag (tag_code, tag_name, rule_expr, tag_desc, hit_count, status, tag_source, dim_type, value_type, synonyms, multi_select, required_flag, sort_no)
SELECT 'BIZ_POP', '人口业务', 'table_name LIKE %pop% OR table_name LIKE %person% OR table_name LIKE %rk%',
       '人口相关业务范畴', 0, 'ACTIVE', 'CUSTOM', 'BUSINESS', 'ENUM', '人口,户籍,常住人口', 1, 0, 10
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_asset_tag WHERE tag_code = 'BIZ_POP');

INSERT INTO ing_asset_tag (tag_code, tag_name, rule_expr, tag_desc, hit_count, status, tag_source, dim_type, value_type, synonyms, multi_select, required_flag, sort_no)
SELECT 'BIZ_ENT', '法人企业', 'table_name LIKE %ent% OR table_name LIKE %corp% OR table_name LIKE %qy%',
       '法人与市场主体', 0, 'ACTIVE', 'CUSTOM', 'BUSINESS', 'ENUM', '企业,法人,市场主体', 1, 0, 20
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_asset_tag WHERE tag_code = 'BIZ_ENT');

INSERT INTO ing_asset_tag (tag_code, tag_name, rule_expr, tag_desc, hit_count, status, tag_source, dim_type, value_type, synonyms, multi_select, required_flag, sort_no)
SELECT 'OBJ_PERSON', '自然人对象', 'column_name LIKE %name% OR column_name LIKE %sfzh% OR column_name LIKE %id_card%',
       '涉及自然人标识字段', 0, 'ACTIVE', 'CUSTOM', 'OBJECT', 'ENUM', '个人,自然人', 1, 0, 30
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_asset_tag WHERE tag_code = 'OBJ_PERSON');

INSERT INTO ing_tag_rule (rule_code, rule_name, tag_id, condition_json, action_type, priority, conflict_policy, status, description)
SELECT 'TR_POP_TABLE', '人口表名识别', t.id,
       '{"nameRegex":".*(pop|person|rk|huji|户籍|人口).*","assetTypes":["TABLE"],"minConfidence":70}',
       'SUGGEST', 20, 'KEEP_MANUAL', 'ACTIVE', '表名含人口相关关键词建议打「人口业务」'
FROM ing_asset_tag t
WHERE t.tag_code = 'BIZ_POP'
  AND NOT EXISTS (SELECT 1 FROM ing_tag_rule WHERE rule_code = 'TR_POP_TABLE')
LIMIT 1;

INSERT INTO ing_tag_rule (rule_code, rule_name, tag_id, condition_json, action_type, priority, conflict_policy, status, description)
SELECT 'TR_ENT_TABLE', '企业表名识别', t.id,
       '{"nameRegex":".*(ent|corp|company|qy|企业|法人).*","assetTypes":["TABLE"],"minConfidence":70}',
       'SUGGEST', 20, 'KEEP_MANUAL', 'ACTIVE', '表名含企业相关关键词建议打「法人企业」'
FROM ing_asset_tag t
WHERE t.tag_code = 'BIZ_ENT'
  AND NOT EXISTS (SELECT 1 FROM ing_tag_rule WHERE rule_code = 'TR_ENT_TABLE')
LIMIT 1;

INSERT INTO ing_tag_rule (rule_code, rule_name, tag_id, condition_json, action_type, priority, conflict_policy, status, description)
SELECT 'TR_PERSON_COL', '自然人字段识别', t.id,
       '{"nameRegex":".*(name|sfzh|id_card|idcard|phone|mobile).*","assetTypes":["COLUMN","TABLE"],"minConfidence":65}',
       'SUGGEST', 30, 'KEEP_MANUAL', 'ACTIVE', '字段/表名含身份相关关键词'
FROM ing_asset_tag t
WHERE t.tag_code = 'OBJ_PERSON'
  AND NOT EXISTS (SELECT 1 FROM ing_tag_rule WHERE rule_code = 'TR_PERSON_COL')
LIMIT 1;

UPDATE ing_asset_tag SET dim_type = 'BUSINESS' WHERE tag_source = 'STANDARD' AND (dim_type IS NULL OR dim_type = '');
