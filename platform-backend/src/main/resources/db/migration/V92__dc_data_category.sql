-- V92 数据分类目录（数字化管道差异化配置）

CREATE TABLE IF NOT EXISTS dc_data_category (
  uuid           VARCHAR(64)  NOT NULL PRIMARY KEY,
  category_code  VARCHAR(64)  NOT NULL,
  category_name  VARCHAR(128) NOT NULL,
  category_type  VARCHAR(32)  NOT NULL COMMENT 'STATIC|FILE|DYNAMIC|VIDEO',
  config_level   VARCHAR(32)  NOT NULL DEFAULT 'BASIC' COMMENT '当前编辑聚焦 BASIC|OPTIONAL|ADVANCED',
  config_json    MEDIUMTEXT   NOT NULL COMMENT '三级配置 JSON',
  description    VARCHAR(512) NULL,
  sort_no        INT          NOT NULL DEFAULT 0,
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dc_category_code (category_code)
);

INSERT INTO dc_data_category (uuid, category_code, category_name, category_type, config_level, config_json, description, sort_no, status, create_time)
SELECT 'dc-static-001', 'STATIC', '静态基础数据', 'STATIC', 'BASIC',
       '{"BASIC":{"dataFormat":"table","refreshCycle":"quarterly","metadataRequired":true},"OPTIONAL":{"indexEnabled":true,"compressionType":"gzip","displayFormat":"standard"},"ADVANCED":{"archivePolicy":"yearly","replicaCount":2,"retentionYears":5}}',
       '离线数据和文本文件，无高时效要求', 1, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dc_data_category WHERE category_code = 'STATIC');

INSERT INTO dc_data_category (uuid, category_code, category_name, category_type, config_level, config_json, description, sort_no, status, create_time)
SELECT 'dc-file-001', 'FILE', '文件和影像数据', 'FILE', 'ADVANCED',
       '{"BASIC":{"mapScale":"1:10000","coordinateSystem":"CGCS2000","imageResolution":"2m","updateCycle":"yearly","pipelineLayers":["segment","valve","station"],"enableBasePipeline":true},"OPTIONAL":{"terrainShading":false,"labelDisplay":["road"],"symbolStyle":"standard","enhancedResolution":"1m","transparency":80,"nightMode":false},"ADVANCED":{"geoBackgroundTypes":["satellite"],"dataPrecision":"1:2000","coverageScope":"city","customRegion":"","demPrecision":"12.5m","satelliteResolution":"1m","pipelineBufferKm":5}}',
       '基础地图影像文件', 2, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dc_data_category WHERE category_code = 'FILE');

INSERT INTO dc_data_category (uuid, category_code, category_name, category_type, config_level, config_json, description, sort_no, status, create_time)
SELECT 'dc-dynamic-001', 'DYNAMIC', '动态数据', 'DYNAMIC', 'ADVANCED',
       '{"BASIC":{"latencyRequirement":"hour","syncMode":"incremental","qualityCheck":true},"OPTIONAL":{"realtimeAlert":true,"cacheEnabled":false,"displayRefreshSec":60},"ADVANCED":{"streamPartition":"time","qosLevel":"standard","failoverEnabled":true}}',
       '需及时处理的时效数据', 3, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dc_data_category WHERE category_code = 'DYNAMIC');

INSERT INTO dc_data_category (uuid, category_code, category_name, category_type, config_level, config_json, description, sort_no, status, create_time)
SELECT 'dc-video-001', 'VIDEO', '视频数据', 'VIDEO', 'OPTIONAL',
       '{"BASIC":{"videoFormat":"mp4","frameRate":25,"storagePath":"/video/{region}/{date}"},"OPTIONAL":{"thumbnailEnabled":true,"transcodingProfile":"720p","watermarkEnabled":false},"ADVANCED":{"aiAnalysis":[],"retentionDays":90,"clipBufferSec":30}}',
       '监控视频等连续图像序列', 4, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dc_data_category WHERE category_code = 'VIDEO');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 6411, 6402, '数据分类', 2, '/data-category', 'resource/DataCategoryView',
       'resource:data-category:manage', NULL, 35, NULL, 'self', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6411 OR path = '/data-category');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 6411
FROM (SELECT 1 AS role_id UNION ALL SELECT 2) r
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 6411)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = 6411);
