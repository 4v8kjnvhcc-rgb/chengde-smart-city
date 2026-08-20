-- 手工部署补丁（与 Flyway V241 同内容）
-- 目标库：smart_city（控制面）
-- 可重复执行：CREATE IF NOT EXISTS + INSERT WHERE NOT EXISTS

CREATE TABLE IF NOT EXISTS ana_zone_internal_classify (
  id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  domain_code     VARCHAR(32)   NOT NULL COMMENT 'population|legal|macro|key',
  zone_code       VARCHAR(32)   NOT NULL DEFAULT 'internal' COMMENT '固定 internal',
  asset_code      VARCHAR(128)  NOT NULL COMMENT '资产编码/物理表引用',
  asset_name      VARCHAR(256)  NOT NULL,
  category_code   VARCHAR(64)   NOT NULL COMMENT '分类编码',
  category_name   VARCHAR(128)  NOT NULL COMMENT '分类名称',
  level_code      VARCHAR(32)   NOT NULL COMMENT 'GENERAL|IMPORTANT|SENSITIVE|CORE',
  level_name      VARCHAR(64)   NULL,
  classify_basis  VARCHAR(512)  NULL COMMENT '分级依据',
  control_hint    VARCHAR(512)  NULL COMMENT '管控要求',
  binding_id      BIGINT        NULL COMMENT '可选关联 ana_zone_binding.id',
  sort_no         INT           NOT NULL DEFAULT 0,
  status          VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
  created_by      VARCHAR(64)   NULL,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NULL,
  UNIQUE KEY uk_ana_zone_cls (domain_code, zone_code, asset_code),
  KEY idx_ana_zone_cls (domain_code, zone_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部服务区分级分类登记';

CREATE TABLE IF NOT EXISTS ana_zone_internal_grant (
  id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  domain_code       VARCHAR(32)   NOT NULL,
  zone_code         VARCHAR(32)   NOT NULL DEFAULT 'internal',
  grant_type        VARCHAR(32)   NOT NULL COMMENT 'ROLE_GRANT|DATA_ACCESS',
  grantee_type      VARCHAR(32)   NOT NULL COMMENT 'USER|ROLE|ORG',
  grantee_code      VARCHAR(128)  NOT NULL,
  grantee_name      VARCHAR(256)  NULL,
  asset_code        VARCHAR(128)  NULL COMMENT 'DATA_ACCESS 必填',
  asset_name        VARCHAR(256)  NULL,
  level_code        VARCHAR(32)   NULL COMMENT '可访问最高敏感级',
  auth_mode         VARCHAR(32)   NOT NULL DEFAULT 'DUAL' COMMENT 'DUAL|SINGLE',
  permission_scope  VARCHAR(128)  NULL COMMENT 'READ|EXPORT|MASKED_READ',
  reason            VARCHAR(512)  NULL,
  status            VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|ACTIVE|REVOKED|REJECTED',
  applicant         VARCHAR(64)   NULL,
  approved_by       VARCHAR(64)   NULL,
  approved_at       DATETIME      NULL,
  created_by        VARCHAR(64)   NULL,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NULL,
  KEY idx_ana_zone_grant (domain_code, zone_code, status),
  KEY idx_ana_zone_grant_asset (domain_code, zone_code, asset_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部服务区双重授权/数据权限台账';

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'internal', 'POSITION', 'HIGH_SENSITIVE_ZONE', '高敏独立服务边界', 'HIGH_SENSITIVE_BOUNDARY',
       '针对法人基础数据等权限较高、敏感等级较高的应用场景，设计独立内部服务区域',
       NULL, 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='internal' AND dim_code='POSITION' AND item_code='HIGH_SENSITIVE_ZONE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'internal', 'MODEL', 'CONSUME_CORE', '消费核心区权威数据', 'CONSUME_CORE',
       '消费核心区权威结构化数据；不另建第二权威源',
       NULL, 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='internal' AND dim_code='MODEL' AND item_code='CONSUME_CORE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'internal', 'PROCESS', 'CLASSIFY_DUAL_AUTH', '分级分类与双重授权', 'CLASSIFY_DUAL_AUTH',
       '制定数据分级分类管理规范；数据访问须双重授权，系统管理员不可直接授跨部门数据访问权',
       '/exchange/ingestion?system=register&module=m048', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='internal' AND dim_code='PROCESS' AND item_code='CLASSIFY_DUAL_AUTH');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'internal', 'SOURCE', 'FROM_CORE', '来源核心区', 'FROM_CORE',
       '数据来源于法人核心数据区经挂载的权威资源',
       NULL, 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='internal' AND dim_code='SOURCE' AND item_code='FROM_CORE');

INSERT INTO ana_zone_dim_design (domain_code, zone_code, dim_code, item_code, item_name, item_type, content, deep_link, sort_no, status, created_by)
SELECT 'legal', 'internal', 'CONSUMER', 'DEPT_HIGH_SENS', '部门内高敏应用', 'DEPT_HIGH_SENS',
       '部门内高敏业务应用与管理员；须完成双重授权后方可访问',
       NULL, 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_dim_design WHERE domain_code='legal' AND zone_code='internal' AND dim_code='CONSUMER' AND item_code='DEPT_HIGH_SENS');

INSERT INTO ana_zone_internal_classify
  (domain_code, zone_code, asset_code, asset_name, category_code, category_name, level_code, level_name, classify_basis, control_hint, sort_no, status, created_by)
SELECT 'legal', 'internal', 'dws_legal_entity_base', '法人基础信息宽表', 'LEGAL_BASE', '法人基础数据',
       'CORE', '核心数据', '含统一社会信用代码、法定代表人证件等标识类字段',
       '仅内部服务区挂载；访问须双重授权；禁止系统管理员直接跨部门授数权', 10, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_internal_classify WHERE domain_code='legal' AND zone_code='internal' AND asset_code='dws_legal_entity_base');

INSERT INTO ana_zone_internal_classify
  (domain_code, zone_code, asset_code, asset_name, category_code, category_name, level_code, level_name, classify_basis, control_hint, sort_no, status, created_by)
SELECT 'legal', 'internal', 'dws_legal_contact', '法人联系信息主题', 'CONTACT', '联系信息',
       'SENSITIVE', '敏感数据', '含联系人、电话、地址等可识别字段',
       '脱敏只读为默认授权范围；导出须单独审批', 20, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_internal_classify WHERE domain_code='legal' AND zone_code='internal' AND asset_code='dws_legal_contact');

INSERT INTO ana_zone_internal_classify
  (domain_code, zone_code, asset_code, asset_name, category_code, category_name, level_code, level_name, classify_basis, control_hint, sort_no, status, created_by)
SELECT 'legal', 'internal', 'dws_legal_biz_stat', '法人业务统计主题', 'LEGAL_BIZ', '法人业务主题',
       'IMPORTANT', '重要数据', '聚合统计类，不含直接标识证件号',
       '部门管理员可审定数据访问；建议 READ', 30, 'ACTIVE', 'sys_admin'
WHERE NOT EXISTS (SELECT 1 FROM ana_zone_internal_classify WHERE domain_code='legal' AND zone_code='internal' AND asset_code='dws_legal_biz_stat');

INSERT INTO ana_zone_internal_grant
  (domain_code, zone_code, grant_type, grantee_type, grantee_code, grantee_name, asset_code, asset_name,
   level_code, auth_mode, permission_scope, reason, status, applicant, created_by)
SELECT 'legal', 'internal', 'ROLE_GRANT', 'ROLE', 'DEPT_ADMIN', '部门管理员',
       NULL, NULL, NULL, 'SINGLE', NULL,
       '系统管理员可授予部门管理员角色，由其承接本部门数据访问审批',
       'ACTIVE', 'sys_admin', 'sys_admin'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_zone_internal_grant
  WHERE domain_code='legal' AND zone_code='internal' AND grant_type='ROLE_GRANT'
    AND grantee_type='ROLE' AND grantee_code='DEPT_ADMIN' AND (asset_code IS NULL OR asset_code='')
);

INSERT INTO ana_zone_internal_grant
  (domain_code, zone_code, grant_type, grantee_type, grantee_code, grantee_name, asset_code, asset_name,
   level_code, auth_mode, permission_scope, reason, status, applicant, created_by)
SELECT 'legal', 'internal', 'DATA_ACCESS', 'ORG', 'ORG_DEMO_LEGAL', '示例法人业务处室',
       'dws_legal_entity_base', '法人基础信息宽表',
       'CORE', 'DUAL', 'MASKED_READ',
       '跨部门申请访问法人基础宽表；系统管理员创建后须部门管理员审批',
       'PENDING', 'legal_biz_user', 'legal_biz_user'
WHERE NOT EXISTS (
  SELECT 1 FROM ana_zone_internal_grant
  WHERE domain_code='legal' AND zone_code='internal' AND grant_type='DATA_ACCESS'
    AND grantee_code='ORG_DEMO_LEGAL' AND asset_code='dws_legal_entity_base'
);
