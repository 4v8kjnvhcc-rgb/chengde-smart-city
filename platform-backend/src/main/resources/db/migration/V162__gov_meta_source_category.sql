-- M087 数据源分类：元数据管理侧分层/来源分类树
CREATE TABLE IF NOT EXISTS gov_meta_source_category (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_code   VARCHAR(64)  NOT NULL COMMENT '分类编码',
    category_name   VARCHAR(128) NOT NULL COMMENT '分类名称',
    parent_id       BIGINT       NOT NULL DEFAULT 0 COMMENT '0=顶层分类',
    layer_code      VARCHAR(32)  NULL COMMENT 'ODS/DWD/DWS/ADS/SOURCE/OTHER/DICT',
    description     VARCHAR(512) NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    system_flag     TINYINT      NOT NULL DEFAULT 0 COMMENT '1=系统内置不可删',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gov_meta_src_cat_code (category_code),
    KEY idx_gov_meta_src_cat_parent (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据-数据源分类';

INSERT INTO gov_meta_source_category (category_code, category_name, parent_id, layer_code, sort_order, system_flag, status)
SELECT 'CAT_BASIC', '基础库', 0, 'ADS', 1, 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM gov_meta_source_category WHERE category_code = 'CAT_BASIC');

INSERT INTO gov_meta_source_category (category_code, category_name, parent_id, layer_code, sort_order, system_flag, status)
SELECT 'CAT_GOV', '治理库', 0, 'DWD', 2, 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM gov_meta_source_category WHERE category_code = 'CAT_GOV');

INSERT INTO gov_meta_source_category (category_code, category_name, parent_id, layer_code, sort_order, system_flag, status)
SELECT 'CAT_THEME', '主题库', 0, 'DWS', 3, 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM gov_meta_source_category WHERE category_code = 'CAT_THEME');

INSERT INTO gov_meta_source_category (category_code, category_name, parent_id, layer_code, sort_order, system_flag, status)
SELECT 'CAT_ODS', '原始库', 0, 'ODS', 4, 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM gov_meta_source_category WHERE category_code = 'CAT_ODS');

INSERT INTO gov_meta_source_category (category_code, category_name, parent_id, layer_code, sort_order, system_flag, status)
SELECT 'CAT_SOURCE', '来源', 0, 'SOURCE', 5, 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM gov_meta_source_category WHERE category_code = 'CAT_SOURCE');

INSERT INTO gov_meta_source_category (category_code, category_name, parent_id, layer_code, sort_order, system_flag, status)
SELECT 'CAT_OTHER', '其他', 0, 'OTHER', 6, 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM gov_meta_source_category WHERE category_code = 'CAT_OTHER');

INSERT INTO gov_meta_source_category (category_code, category_name, parent_id, layer_code, sort_order, system_flag, status)
SELECT 'CAT_DICT', '字典', 0, 'DICT', 7, 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM gov_meta_source_category WHERE category_code = 'CAT_DICT');
