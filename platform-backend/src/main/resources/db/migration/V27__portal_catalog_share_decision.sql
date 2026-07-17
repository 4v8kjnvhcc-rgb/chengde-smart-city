-- R2.3 应用分析门户：目录扩展（主题/单位/双目录）+ 决策态势看板 URL

ALTER TABLE biz_catalog_item
  ADD COLUMN catalog_kind VARCHAR(16) NOT NULL DEFAULT 'DATA' COMMENT 'DATA/SERVICE' AFTER description,
  ADD COLUMN theme_code VARCHAR(64) NULL AFTER catalog_kind,
  ADD COLUMN theme_name VARCHAR(128) NULL AFTER theme_code,
  ADD COLUMN provider_org VARCHAR(128) NULL AFTER theme_name,
  ADD COLUMN share_modes VARCHAR(64) NULL COMMENT 'TABLE,FILE,API' AFTER provider_org,
  ADD COLUMN resource_count INT NOT NULL DEFAULT 0 COMMENT '挂接资源数量' AFTER share_modes,
  ADD COLUMN hot_score INT NOT NULL DEFAULT 0 COMMENT '热度分' AFTER resource_count,
  ADD COLUMN published_at DATETIME NULL COMMENT '发布时间' AFTER hot_score;

ALTER TABLE biz_portal_situation
  ADD COLUMN board_url VARCHAR(512) NULL COMMENT 'DataEase/外部看板 URL' AFTER summary_metric;

-- 回填已有演示目录
UPDATE biz_catalog_item SET
  catalog_kind = 'DATA',
  theme_code = 'POPULATION',
  theme_name = '人口主题',
  provider_org = '公安局',
  share_modes = 'TABLE,FILE,API',
  resource_count = 12,
  hot_score = 28,
  published_at = DATE_SUB(NOW(), INTERVAL 5 DAY)
WHERE catalog_code = 'CAT_POP_001';

UPDATE biz_catalog_item SET
  catalog_kind = 'DATA',
  theme_code = 'LEGAL',
  theme_name = '法人主题',
  provider_org = '市场监管局',
  share_modes = 'TABLE,API',
  resource_count = 8,
  hot_score = 22,
  published_at = DATE_SUB(NOW(), INTERVAL 3 DAY)
WHERE catalog_code = 'CAT_LEG_001';

UPDATE biz_catalog_item SET
  catalog_kind = 'DATA',
  theme_code = 'ECONOMY',
  theme_name = '经济主题',
  provider_org = '统计局',
  share_modes = 'TABLE,FILE',
  resource_count = 15,
  hot_score = 35,
  published_at = DATE_SUB(NOW(), INTERVAL 1 DAY)
WHERE catalog_code = 'CAT_ECO_001';

UPDATE biz_catalog_item SET
  catalog_kind = 'SERVICE',
  theme_code = 'GOVERNANCE',
  theme_name = '社会治理',
  provider_org = '政务服务中心',
  share_modes = 'API',
  resource_count = 6,
  hot_score = 18,
  published_at = DATE_SUB(NOW(), INTERVAL 2 DAY)
WHERE catalog_code = 'CAT_GOV_001';

-- 补充双目录演示数据
INSERT INTO biz_catalog_item (
  catalog_code, title, description, catalog_kind, theme_code, theme_name, provider_org,
  share_modes, resource_count, hot_score, publish_status, published_at, created_by
) VALUES
('CAT_POP_API', '人口信息查询服务', '人口主题 API 服务目录', 'SERVICE', 'POPULATION', '人口主题', '公安局',
 'API', 4, 40, 'PUBLISHED', DATE_SUB(NOW(), INTERVAL 1 DAY), 'sys_admin'),
('CAT_LEG_FILE', '法人证照影像目录', '法人主题文件共享', 'DATA', 'LEGAL', '法人主题', '市场监管局',
 'FILE,API', 9, 16, 'PUBLISHED', DATE_SUB(NOW(), INTERVAL 4 DAY), 'sys_admin'),
('CAT_ECO_SVC', '经济指标开放接口', '宏观经济服务目录', 'SERVICE', 'ECONOMY', '经济主题', '统计局',
 'API', 7, 31, 'PUBLISHED', NOW(), 'sys_admin'),
('CAT_CITY_TAB', '城市部件基础库表', '城市管理库表同步', 'DATA', 'CITY', '城市管理', '住建局',
 'TABLE,FILE', 11, 12, 'PUBLISHED', DATE_SUB(NOW(), INTERVAL 6 DAY), 'sys_admin')
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  catalog_kind = VALUES(catalog_kind),
  theme_code = VALUES(theme_code),
  theme_name = VALUES(theme_name),
  provider_org = VALUES(provider_org),
  share_modes = VALUES(share_modes),
  resource_count = VALUES(resource_count),
  hot_score = VALUES(hot_score),
  publish_status = VALUES(publish_status),
  published_at = VALUES(published_at);

UPDATE biz_catalog_item
SET published_at = COALESCE(published_at, created_at)
WHERE publish_status = 'PUBLISHED' AND published_at IS NULL;
