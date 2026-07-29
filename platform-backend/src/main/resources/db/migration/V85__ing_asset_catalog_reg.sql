-- V85: 资产目录登记台账 + 归集 Hub 侧栏入口（登记 / 管理）

CREATE TABLE IF NOT EXISTS ing_asset_catalog_reg (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_name        VARCHAR(200)  NOT NULL COMMENT '资产名称',
    asset_desc        VARCHAR(1000) NULL COMMENT '资产描述',
    owner_name        VARCHAR(100)  NULL COMMENT '所有者',
    contact_info      VARCHAR(100)  NULL COMMENT '联系方式',
    data_tags         VARCHAR(500)  NULL COMMENT '数据标签（逗号分隔）',
    org_id            BIGINT        NULL COMMENT '所属机构ID',
    org_name          VARCHAR(200)  NULL COMMENT '所属机构名称',
    project_id        BIGINT        NULL COMMENT '来源项目ID',
    project_name      VARCHAR(200)  NULL COMMENT '来源项目名称',
    source_id         BIGINT        NULL COMMENT '数据源ID',
    system_name       VARCHAR(200)  NULL COMMENT '来源系统/数据源名称',
    table_id          BIGINT        NULL COMMENT '来源表ID',
    table_name        VARCHAR(200)  NULL COMMENT '来源表名称',
    access_mode       VARCHAR(50)   NULL COMMENT '数据接入方式',
    format_type       VARCHAR(50)   NULL COMMENT '格式类型 DATABASE/FILE/API 等',
    transfer_mode     VARCHAR(50)   NULL COMMENT '数据传输方式',
    format_locked     TINYINT       NOT NULL DEFAULT 0 COMMENT '格式类型是否由数据源带出锁定',
    biz_purpose       VARCHAR(1000) NULL COMMENT '主要用途',
    biz_scenario      VARCHAR(1000) NULL COMMENT '业务场景',
    access_scope      VARCHAR(500)  NULL COMMENT '访问权限范围',
    control_req       VARCHAR(1000) NULL COMMENT '控制要求',
    quality_file_path VARCHAR(500)  NULL COMMENT '数据质量评估附件路径',
    quality_file_name VARCHAR(200)  NULL COMMENT '数据质量评估附件名',
    risk_file_path    VARCHAR(500)  NULL COMMENT '数据风险评估附件路径',
    risk_file_name    VARCHAR(200)  NULL COMMENT '数据风险评估附件名',
    other_info        VARCHAR(1000) NULL COMMENT '其他信息',
    status            VARCHAR(30)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_ARCHIVE/REJECTED/ARCHIVED',
    reject_reason     VARCHAR(500)  NULL COMMENT '驳回原因',
    reported_at       DATETIME      NULL,
    archived_at       DATETIME      NULL,
    created_by        VARCHAR(64)   NULL,
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_acr_status (status),
    KEY idx_acr_asset_name (asset_name),
    KEY idx_acr_org (org_name),
    KEY idx_acr_project (project_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产目录登记';

-- Hub 侧栏：挂在「数据资产登记管理」下
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7013, 7000, '资产目录登记', 2, '/exchange/ingestion?system=register&module=asset-catalog-reg', NULL,
       'hub:ingestion:register:asset-catalog-reg', NULL, 13, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7013);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7014, 7000, '资产目录管理', 2, '/exchange/ingestion?system=register&module=asset-catalog-mgmt', NULL,
       'hub:ingestion:register:asset-catalog-mgmt', NULL, 14, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7014);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id IN (7013, 7014)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
