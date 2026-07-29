-- 项目 → 业务系统 → 数据源 三层：独立系统表 + 数据源挂 system_id

CREATE TABLE IF NOT EXISTS ing_biz_system (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  system_code VARCHAR(64) NOT NULL,
  system_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_system_code (system_code),
  UNIQUE KEY uk_project_system_name (project_id, system_name),
  KEY idx_biz_system_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 先可空，回填后再收紧
ALTER TABLE ing_data_source
  ADD COLUMN system_id BIGINT NULL COMMENT '归属业务系统' AFTER project_id;

-- 按既有 (project_id, system_name) 去重建系统
INSERT INTO ing_biz_system (project_id, system_code, system_name, status, created_by)
SELECT
  ds.project_id,
  CONCAT('SYS_', ds.project_id, '_', MIN(ds.id)),
  COALESCE(NULLIF(TRIM(ds.system_name), ''), '其他'),
  'ACTIVE',
  'migration'
FROM ing_data_source ds
GROUP BY ds.project_id, COALESCE(NULLIF(TRIM(ds.system_name), ''), '其他');

-- 无数据源但有项目的，补一条系统（沿用项目上的默认系统名）
INSERT INTO ing_biz_system (project_id, system_code, system_name, status, created_by)
SELECT
  p.id,
  CONCAT('SYS_', p.id, '_P'),
  COALESCE(NULLIF(TRIM(p.system_name), ''), '其他'),
  'ACTIVE',
  'migration'
FROM ing_project p
WHERE NOT EXISTS (
  SELECT 1 FROM ing_biz_system s WHERE s.project_id = p.id
);

-- 回填 system_id
UPDATE ing_data_source ds
INNER JOIN ing_biz_system s
  ON s.project_id = ds.project_id
 AND s.system_name = COALESCE(NULLIF(TRIM(ds.system_name), ''), '其他')
SET ds.system_id = s.id
WHERE ds.system_id IS NULL;

-- 兜底：仍无 system_id 的挂到该项目首个系统
UPDATE ing_data_source ds
INNER JOIN (
  SELECT project_id, MIN(id) AS sid
  FROM ing_biz_system
  GROUP BY project_id
) x ON x.project_id = ds.project_id
SET ds.system_id = x.sid
WHERE ds.system_id IS NULL;

ALTER TABLE ing_data_source
  MODIFY COLUMN system_id BIGINT NOT NULL COMMENT '归属业务系统';

ALTER TABLE ing_data_source
  ADD KEY idx_data_source_system (system_id);

-- 项目默认系统名与首个业务系统对齐（仅空值时）
UPDATE ing_project p
INNER JOIN (
  SELECT project_id, MIN(id) AS sid
  FROM ing_biz_system
  GROUP BY project_id
) x ON x.project_id = p.id
INNER JOIN ing_biz_system s ON s.id = x.sid
SET p.system_name = s.system_name
WHERE p.system_name IS NULL OR TRIM(p.system_name) = '';

-- 各部门「其他」项目下的「其他」系统编码统一为 SYS_OTHER_{orgId}
UPDATE ing_biz_system s
INNER JOIN ing_project p ON p.id = s.project_id
SET s.system_code = CONCAT('SYS_OTHER_', SUBSTRING(p.project_code, 11))
WHERE p.project_code LIKE 'PRJ_OTHER_%'
  AND s.system_name = '其他'
  AND SUBSTRING(p.project_code, 11) REGEXP '^[0-9]+$';
