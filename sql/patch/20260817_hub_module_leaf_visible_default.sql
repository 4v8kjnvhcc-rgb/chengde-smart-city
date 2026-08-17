-- 手工补丁（对应 Flyway V234）：汇聚 Hub ?module= 种子叶子默认可见
-- 目标库：smart_city（控制面）
-- 可重复执行：仅更新 visible=0 的指定种子/细码项；不碰「数据质量分析报告」等运维已藏项（7895 不在列表）

UPDATE sys_menu
SET visible = 1
WHERE status = 1
  AND id IN (
    7810, 7811, 7812, 7813, 7814,
    7820, 7821, 7822,
    7830, 7831, 7832, 7833, 7834, 7835, 7836, 7837
  )
  AND IFNULL(integration_type, '') = 'hub'
  AND visible = 0;

UPDATE sys_menu
SET visible = 1
WHERE status = 1
  AND IFNULL(integration_type, '') = 'hub'
  AND visible = 0
  AND (
    IFNULL(path, '') LIKE '%?module=%'
    OR IFNULL(path, '') LIKE '%&module=%'
  )
  AND (
    IFNULL(permission, '') LIKE 'hub:ingestion:collect:ingest:%'
    OR IFNULL(permission, '') LIKE 'hub:ingestion:collect:asset:%'
    OR IFNULL(permission, '') IN (
      'hub:ingestion:collect:quality:rule-config',
      'hub:ingestion:collect:quality:monitor',
      'hub:ingestion:collect:quality:assess'
    )
  );
