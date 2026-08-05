-- 数据资产区完善：库描述/排序、纳管表关联库与资产类型、业务库与数据中心模块种子

ALTER TABLE rc_base_library
  ADD COLUMN description VARCHAR(512) NULL AFTER status,
  ADD COLUMN owner_org VARCHAR(128) NULL AFTER description,
  ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER owner_org;

ALTER TABLE rc_managed_table
  ADD COLUMN lib_id BIGINT NULL COMMENT '关联基础/半结构/非结构库' AFTER theme_id,
  ADD COLUMN asset_type VARCHAR(32) NULL COMMENT 'BASE/SEMI/UNSTRUCT' AFTER lib_id,
  ADD KEY idx_rc_managed_lib (lib_id),
  ADD KEY idx_rc_managed_asset_type (asset_type);

-- （1）基础库
INSERT INTO rc_base_library (lib_code, lib_name, lib_type, record_count, status, description, owner_org, sort_order) VALUES
('LIB_POP', '人口基础库', 'BASE', 0, 'ACTIVE', '人口库数据中心成果区', '公安/人口主管部门', 10),
('LIB_LEGAL', '法人基础库', 'BASE', 0, 'ACTIVE', '法人库数据中心成果区', '市场监管部门', 20),
('LIB_LICENSE', '电子证照库', 'BASE', 0, 'ACTIVE', '电子证照库数据中心', '行政审批/证照主管部门', 30),
('LIB_MACRO', '宏观经济库', 'BASE', 0, 'ACTIVE', '宏观经济库数据中心', '发改/统计部门', 40),
('LIB_ENTERPRISE', '企业经济库', 'BASE', 0, 'ACTIVE', '企业经济库数据中心', '工信/统计部门', 50),
('LIB_GEO', '地理信息库', 'BASE', 0, 'ACTIVE', '地理信息库数据中心', '自然资源部门', 60),
('LIB_CITYPART', '城市部件库', 'BASE', 0, 'ACTIVE', '城市部件库数据中心', '城管/住建部门', 70),
('LIB_TECH', '科技资源库', 'BASE', 0, 'ACTIVE', '科技资源库数据中心', '科技主管部门', 80),
('LIB_OTHER', '其他业务基础库', 'BASE', 0, 'ACTIVE', '其他业务基础库数据中心', '各业务主管部门', 90),
('LIB_APPROVAL', '行政审批库', 'BASE', 0, 'ACTIVE', '行政审批业务基础库', '行政审批局', 100)
ON DUPLICATE KEY UPDATE
  lib_name = VALUES(lib_name),
  description = VALUES(description),
  owner_org = VALUES(owner_org),
  sort_order = VALUES(sort_order),
  status = 'ACTIVE';

-- （1）半结构化 / 非结构化相关库
INSERT INTO rc_base_library (lib_code, lib_name, lib_type, record_count, status, description, owner_org, sort_order) VALUES
('LIB_SEMI_JSON', '半结构化JSON库', 'SEMI', 0, 'ACTIVE', '半结构化 JSON 文档与配置类资产', '大数据中心', 110),
('LIB_SEMI_XML', '半结构化XML库', 'SEMI', 0, 'ACTIVE', '半结构化 XML/交换报文类资产', '大数据中心', 120),
('LIB_SEMI_RELATED', '非结构化相关结构化数据', 'SEMI', 0, 'ACTIVE', '非结构化数据抽取/关联后的结构化中间成果', '大数据中心', 130),
('LIB_UNS_CATALOG', '文件目录库', 'UNSTRUCT', 0, 'ACTIVE', '非结构化文件目录与存储键台账', '大数据中心', 210),
('LIB_UNS_INDEX', '文件索引库', 'UNSTRUCT', 0, 'ACTIVE', '非结构化全文/元数据索引库', '大数据中心', 220),
('LIB_UNS_FILE', '非结构化文件库', 'UNSTRUCT', 0, 'ACTIVE', '非结构化原始文件资源库（目录侧）', '大数据中心', 200),
('LIB_UNS_DISTRIBUTED', '分布式数据存储系统', 'UNSTRUCT', 0, 'ACTIVE', '分布式对象存储与海量文件资源', '大数据中心', 230)
ON DUPLICATE KEY UPDATE
  lib_name = VALUES(lib_name),
  description = VALUES(description),
  owner_org = VALUES(owner_org),
  sort_order = VALUES(sort_order),
  status = 'ACTIVE';

-- （3）数据资产中心模块（主题库区）
INSERT INTO rc_theme_library (theme_code, theme_name, library_kind, zone_code, partition_key, owner_org, description, status, created_by)
VALUES
('MOD_POPULATION', '人口库数据中心', 'THEME', 'MODULE_POPULATION', NULL, '公安/人口主管部门', '人口基础库模块化管理', 'ACTIVE', 'sys_admin'),
('MOD_LEGAL', '法人库数据中心', 'THEME', 'MODULE_LEGAL', NULL, '市场监管部门', '法人基础库模块化管理', 'ACTIVE', 'sys_admin'),
('MOD_LICENSE', '电子证照库数据中心', 'THEME', 'MODULE_LICENSE', NULL, '行政审批/证照主管部门', '电子证照模块化管理', 'ACTIVE', 'sys_admin'),
('MOD_MACRO', '宏观经济库数据中心', 'THEME', 'MODULE_MACRO', NULL, '发改/统计部门', '宏观经济模块化管理', 'ACTIVE', 'sys_admin'),
('MOD_ENTERPRISE', '企业经济库数据中心', 'THEME', 'MODULE_ENTERPRISE', NULL, '工信/统计部门', '企业经济模块化管理', 'ACTIVE', 'sys_admin'),
('MOD_GEO', '地理信息库数据中心', 'THEME', 'MODULE_GEO', NULL, '自然资源部门', '地理信息模块化管理', 'ACTIVE', 'sys_admin'),
('MOD_CITYPART', '城市部件库数据中心', 'THEME', 'MODULE_CITYPART', NULL, '城管/住建部门', '城市部件模块化管理', 'ACTIVE', 'sys_admin'),
('MOD_TECH', '科技资源库数据中心', 'THEME', 'MODULE_TECH', NULL, '科技主管部门', '科技资源模块化管理', 'ACTIVE', 'sys_admin'),
('MOD_OTHER', '其他业务基础库数据中心', 'THEME', 'MODULE_OTHER', NULL, '各业务主管部门', '其他业务基础库模块化管理', 'ACTIVE', 'sys_admin'),
('MOD_APPROVAL', '行政审批库数据中心', 'THEME', 'MODULE_APPROVAL', NULL, '行政审批局', '行政审批库模块化管理', 'ACTIVE', 'sys_admin')
ON DUPLICATE KEY UPDATE
  theme_name = VALUES(theme_name),
  library_kind = VALUES(library_kind),
  zone_code = VALUES(zone_code),
  owner_org = VALUES(owner_org),
  description = VALUES(description),
  status = 'ACTIVE';

-- 回填既有纳管表资产类型（默认按主题归基础库）
UPDATE rc_managed_table
SET asset_type = 'BASE'
WHERE asset_type IS NULL AND status = 'ACTIVE';
