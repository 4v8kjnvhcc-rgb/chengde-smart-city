-- V98：数据搜索门户（引擎配置 / 全局条件 / 标识融合 / 检索审计）
-- 元数据驱动；ES 可选增强，DB 文档库保证可用

CREATE TABLE IF NOT EXISTS ing_search_document (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_key         VARCHAR(128) NOT NULL COMMENT '类型:资产ID',
  doc_type        VARCHAR(32)  NOT NULL COMMENT 'TABLE|COLUMN|REGISTRY|ASSET|IDENTITY',
  asset_id        BIGINT       NULL,
  asset_code      VARCHAR(128) NULL,
  title           VARCHAR(256) NOT NULL,
  summary         VARCHAR(1024) NULL,
  content_text    TEXT         NULL COMMENT '可检索全文（含同义词展开）',
  tags_json       VARCHAR(1024) NULL,
  category_json   VARCHAR(512) NULL,
  physical_db     VARCHAR(128) NULL,
  physical_schema VARCHAR(128) NULL,
  physical_table  VARCHAR(128) NULL,
  source_id       BIGINT       NULL,
  weight_score    DECIMAL(8,2) NOT NULL DEFAULT 50,
  hot_score       INT          NOT NULL DEFAULT 0,
  identity_keys   VARCHAR(512) NULL COMMENT '可精确命中的标识摘要',
  high_attrs_json TEXT         NULL COMMENT '高权重属性快照',
  mid_attrs_json  TEXT         NULL,
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  indexed_at      DATETIME     NULL,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_search_doc_key (doc_key),
  KEY idx_search_doc_type (doc_type),
  KEY idx_search_doc_title (title),
  FULLTEXT KEY ft_search_content (title, summary, content_text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_search_sync_policy (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_code       VARCHAR(64)  NOT NULL,
  policy_name       VARCHAR(128) NOT NULL,
  scope_type        VARCHAR(32)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL|DOMAIN|SOURCE|TABLE',
  scope_ref         VARCHAR(128) NULL,
  update_strategy   VARCHAR(32)  NOT NULL DEFAULT 'SCHEDULED' COMMENT 'NEAR_REALTIME|SCHEDULED|FULL_REBUILD|MANUAL',
  cron_expr         VARCHAR(64)  NULL,
  include_tables    TINYINT      NOT NULL DEFAULT 1,
  include_columns   TINYINT      NOT NULL DEFAULT 1,
  include_registry  TINYINT      NOT NULL DEFAULT 1,
  include_data_sample TINYINT    NOT NULL DEFAULT 0,
  retry_max         INT          NOT NULL DEFAULT 3,
  status            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  last_run_at       DATETIME     NULL,
  last_success_at   DATETIME     NULL,
  last_status       VARCHAR(16)  NULL,
  last_message      VARCHAR(512) NULL,
  doc_count         INT          NOT NULL DEFAULT 0,
  create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_search_sync_code (policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_search_knowledge (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  knowledge_type  VARCHAR(32)  NOT NULL COMMENT 'SYNONYM|DICT|IDENTITY_RULE|FIELD_ALIAS|WEIGHT|UI_CONFIG|CLASS_TAG_MAP',
  knowledge_code  VARCHAR(64)  NOT NULL,
  knowledge_name  VARCHAR(128) NOT NULL,
  payload_json    TEXT         NOT NULL,
  priority        INT          NOT NULL DEFAULT 100,
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  description     VARCHAR(512) NULL,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_search_knowledge (knowledge_type, knowledge_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_search_global_field (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  field_code      VARCHAR(64)  NOT NULL,
  field_name      VARCHAR(128) NOT NULL,
  semantic        VARCHAR(256) NULL,
  data_type       VARCHAR(32)  NOT NULL DEFAULT 'STRING',
  control_type    VARCHAR(32)  NOT NULL DEFAULT 'INPUT' COMMENT 'INPUT|SELECT|DATE|NUMBER|RANGE',
  dict_code       VARCHAR(64)  NULL,
  required_flag   TINYINT      NOT NULL DEFAULT 0,
  match_name_regex VARCHAR(256) NULL,
  match_comment_keywords VARCHAR(256) NULL,
  match_tags      VARCHAR(256) NULL,
  version_no      INT          NOT NULL DEFAULT 1,
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  description     VARCHAR(512) NULL,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_search_gfield (field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_search_global_binding (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  global_field_id BIGINT       NOT NULL,
  table_id        BIGINT       NOT NULL,
  column_id       BIGINT       NULL,
  column_code     VARCHAR(128) NULL,
  match_score     DECIMAL(5,2) NULL,
  confirm_status  VARCHAR(16)  NOT NULL DEFAULT 'SUGGESTED' COMMENT 'SUGGESTED|CONFIRMED|EXCLUDED',
  match_source    VARCHAR(16)  NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO|MANUAL',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_search_gbind (global_field_id, table_id, column_code),
  KEY idx_search_gbind_table (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_search_identity (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  entity_id       VARCHAR(64)  NOT NULL COMMENT '融合实体ID',
  id_type         VARCHAR(32)  NOT NULL COMMENT 'ID_CARD|CREDIT_CODE|PHONE|CUSTOM',
  id_value        VARCHAR(128) NOT NULL,
  source_system   VARCHAR(64)  NULL,
  display_name    VARCHAR(128) NULL,
  profile_json    TEXT         NULL,
  related_docs    VARCHAR(1024) NULL COMMENT '关联 doc_key 列表',
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_search_identity (id_type, id_value),
  KEY idx_search_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_search_saved_query (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  query_code      VARCHAR(64)  NOT NULL,
  query_name      VARCHAR(128) NOT NULL,
  mode            VARCHAR(32)  NOT NULL DEFAULT 'COMBO',
  payload_json    TEXT         NOT NULL,
  owner_name      VARCHAR(64)  NULL,
  status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_search_saved (query_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_search_query_log (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  action_type     VARCHAR(32)  NOT NULL COMMENT 'SEARCH|CLICK|BROWSE|DOWNLOAD|EXACT_PROFILE|SECONDARY',
  mode            VARCHAR(32)  NULL,
  query_text      VARCHAR(512) NULL,
  payload_json    TEXT         NULL,
  hit_count       INT          NULL,
  clicked_doc_key VARCHAR(128) NULL,
  operator_id     BIGINT       NULL,
  operator_name   VARCHAR(64)  NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_search_qlog_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ing_search_audit_log (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  action_type     VARCHAR(32)  NOT NULL COMMENT 'SYNC|CONFIG|GLOBAL_BIND|BROWSE|DOWNLOAD|IDENTITY',
  target_ref      VARCHAR(128) NULL,
  detail_json     TEXT         NULL,
  operator_id     BIGINT       NULL,
  operator_name   VARCHAR(64)  NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_search_audit_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO ing_search_sync_policy (policy_code, policy_name, scope_type, update_strategy, cron_expr, status, last_status)
SELECT 'SYNC_CORE_ALL', '核心资产全量同步', 'ALL', 'FULL_REBUILD', NULL, 'ACTIVE', 'READY'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_sync_policy WHERE policy_code = 'SYNC_CORE_ALL');

INSERT INTO ing_search_sync_policy (policy_code, policy_name, scope_type, update_strategy, cron_expr, status, last_status)
SELECT 'SYNC_INCR_HOURLY', '小时增量同步', 'ALL', 'SCHEDULED', '0 0 * * * ?', 'ACTIVE', 'READY'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_sync_policy WHERE policy_code = 'SYNC_INCR_HOURLY');

INSERT INTO ing_search_sync_policy (policy_code, policy_name, scope_type, update_strategy, cron_expr, status, last_status)
SELECT 'SYNC_NEAR_RT', '近实时增量（台账）', 'ALL', 'NEAR_REALTIME', NULL, 'ACTIVE', 'READY'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_sync_policy WHERE policy_code = 'SYNC_NEAR_RT');

INSERT INTO ing_search_knowledge (knowledge_type, knowledge_code, knowledge_name, payload_json, priority, status, description)
SELECT 'SYNONYM', 'SYN_POP', '人口同义词', '{"terms":["人口","户籍","常住人口","person","rk"]}', 10, 'ACTIVE', '人口业务扩词'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_knowledge WHERE knowledge_code = 'SYN_POP');

INSERT INTO ing_search_knowledge (knowledge_type, knowledge_code, knowledge_name, payload_json, priority, status, description)
SELECT 'SYNONYM', 'SYN_ENT', '企业同义词', '{"terms":["企业","法人","市场主体","ent","corp"]}', 10, 'ACTIVE', '法人企业扩词'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_knowledge WHERE knowledge_code = 'SYN_ENT');

INSERT INTO ing_search_knowledge (knowledge_type, knowledge_code, knowledge_name, payload_json, priority, status, description)
SELECT 'WEIGHT', 'WEIGHT_DEFAULT', '默认高次权重',
       '{"high":["tableName","tagNames","identityKeys","physicalTable"],"mid":["tableCode","usageDesc","columnNames","category"],"defaultSort":"RELEVANCE"}',
       1, 'ACTIVE', '结果属性展示优先级'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_knowledge WHERE knowledge_code = 'WEIGHT_DEFAULT');

INSERT INTO ing_search_knowledge (knowledge_type, knowledge_code, knowledge_name, payload_json, priority, status, description)
SELECT 'IDENTITY_RULE', 'ID_SFZH', '身份证号识别',
       '{"idType":"ID_CARD","patterns":["^\\\\d{17}[\\\\dXx]$"],"fieldHints":["sfzh","id_card","idcard"]}',
       5, 'ACTIVE', '精确搜索标识规则'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_knowledge WHERE knowledge_code = 'ID_SFZH');

INSERT INTO ing_search_knowledge (knowledge_type, knowledge_code, knowledge_name, payload_json, priority, status, description)
SELECT 'IDENTITY_RULE', 'ID_CREDIT', '统一社会信用代码',
       '{"idType":"CREDIT_CODE","patterns":["^[0-9A-Z]{18}$"],"fieldHints":["credit_code","tyshxydm","uscc"]}',
       5, 'ACTIVE', '企业标识'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_knowledge WHERE knowledge_code = 'ID_CREDIT');

INSERT INTO ing_search_knowledge (knowledge_type, knowledge_code, knowledge_name, payload_json, priority, status, description)
SELECT 'UI_CONFIG', 'UI_PORTAL', '门户交互配置',
       '{"suggestEnabled":true,"secondarySearch":true,"emptyHint":"未找到结果，可切换「元数据找表」或检查同义词配置","defaultMode":"FUZZY","defaultSort":"RELEVANCE"}',
       1, 'ACTIVE', '人机交互'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_knowledge WHERE knowledge_code = 'UI_PORTAL');

INSERT INTO ing_search_global_field (field_code, field_name, semantic, data_type, control_type, match_name_regex, match_comment_keywords, status, description)
SELECT 'GF_REGION', '行政区划', '承德市区县等行政区划代码/名称', 'STRING', 'SELECT',
       '.*(region|area|xzqh|区划|行政区).*', '区划,行政区,地区', 'ACTIVE', '全局条件示例'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_global_field WHERE field_code = 'GF_REGION');

INSERT INTO ing_search_global_field (field_code, field_name, semantic, data_type, control_type, match_name_regex, match_comment_keywords, status, description)
SELECT 'GF_UPDATE_TIME', '更新时间', '业务数据更新/采集时间', 'DATE', 'DATE',
       '.*(update_time|gmt_modified|modify_time|更新时间|采集时间).*', '更新,修改,采集', 'ACTIVE', '时间范围全局条件'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_global_field WHERE field_code = 'GF_UPDATE_TIME');

INSERT INTO ing_search_global_field (field_code, field_name, semantic, data_type, control_type, match_name_regex, match_comment_keywords, status, description)
SELECT 'GF_ID_CARD', '身份证号', '自然人身份标识', 'STRING', 'INPUT',
       '.*(sfzh|id_card|idcard|身份证).*', '身份证,证件号', 'ACTIVE', '精确/组合查询常用'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_global_field WHERE field_code = 'GF_ID_CARD');

INSERT INTO ing_search_identity (entity_id, id_type, id_value, source_system, display_name, profile_json, related_docs, status)
SELECT 'ENT_DEMO_001', 'CREDIT_CODE', '91130800MA0XXXXXX1', 'demo', '示范企业（检索样例）',
       '{"orgName":"示范企业","region":"承德市","tags":["法人企业"]}', '', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_identity WHERE id_type = 'CREDIT_CODE' AND id_value = '91130800MA0XXXXXX1');

INSERT INTO ing_search_identity (entity_id, id_type, id_value, source_system, display_name, profile_json, related_docs, status)
SELECT 'ENT_DEMO_001', 'CUSTOM', 'ENT_CODE_DEMO', 'demo', '示范企业（检索样例）',
       '{"orgName":"示范企业","alias":"内部企业编码"}', '', 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ing_search_identity WHERE id_type = 'CUSTOM' AND id_value = 'ENT_CODE_DEMO');
