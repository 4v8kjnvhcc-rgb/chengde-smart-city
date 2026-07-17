-- D1：逻辑/物理融合模型

CREATE TABLE IF NOT EXISTS gov_fusion_domain (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  domain_code VARCHAR(64) NOT NULL,
  domain_name VARCHAR(128) NOT NULL,
  description VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fusion_domain_code (domain_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_fusion_logic_entity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  domain_id BIGINT NOT NULL,
  entity_code VARCHAR(64) NOT NULL,
  entity_name VARCHAR(128) NOT NULL,
  description VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fusion_entity_code (domain_id, entity_code),
  KEY idx_fusion_entity_domain (domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_fusion_field (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entity_id BIGINT NOT NULL,
  field_code VARCHAR(64) NOT NULL,
  field_name VARCHAR(128) NOT NULL,
  data_type VARCHAR(64) NOT NULL DEFAULT 'VARCHAR',
  nullable_flag TINYINT NOT NULL DEFAULT 1,
  pk_flag TINYINT NOT NULL DEFAULT 0,
  description VARCHAR(512) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fusion_field_code (entity_id, field_code),
  KEY idx_fusion_field_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_fusion_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  domain_id BIGINT NOT NULL,
  relation_code VARCHAR(64) NOT NULL,
  relation_name VARCHAR(128) NOT NULL,
  from_entity_id BIGINT NOT NULL,
  to_entity_id BIGINT NOT NULL,
  relation_type VARCHAR(32) NOT NULL DEFAULT 'ONE_TO_MANY' COMMENT 'ONE_TO_ONE/ONE_TO_MANY/MANY_TO_MANY',
  description VARCHAR(512) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fusion_relation_code (domain_id, relation_code),
  KEY idx_fusion_rel_domain (domain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gov_fusion_physical (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  entity_id BIGINT NOT NULL,
  physical_code VARCHAR(64) NOT NULL,
  table_name VARCHAR(128) NOT NULL,
  datasource_id BIGINT NULL,
  ddl_sql TEXT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fusion_physical_code (entity_id, physical_code),
  KEY idx_fusion_physical_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO gov_fusion_domain (domain_code, domain_name, description, status, created_by)
VALUES ('DOM_DEMO', '示范业务域', '融合模型示范域', 'ACTIVE', 'sys_admin')
ON DUPLICATE KEY UPDATE domain_name = VALUES(domain_name);
