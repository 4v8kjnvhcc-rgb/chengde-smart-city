-- V234: Hub 内页 ?module= 叶子默认可见（补 V220 只覆盖了 ?tab=）
-- 根因：V199 汇聚接入/资产等叶子种子 visible=0；前端曾忽略 module= 的隐藏，
-- 导致「数据质量分析报告」等运维设隐藏不生效。恢复「自身 visible=0 一律藏 Hub」后，
-- 须把仍停留在种子隐藏态、且非运维主动隐藏的 module= 叶子亮成 visible=1。
-- 已由菜单管理设为隐藏（visible=0）的项：本脚本用「仅点亮已知种子 id」避免误亮运维隐藏项。

UPDATE sys_menu
SET visible = 1
WHERE status = 1
  AND id IN (
    -- 汇聚接入五叶
    7810, 7811, 7812, 7813, 7814,
    -- 汇聚质量三叶（若仍为种子 0；运维已藏的 7895 等不在此列）
    7820, 7821, 7822,
    -- 汇聚资产八叶
    7830, 7831, 7832, 7833, 7834, 7835, 7836, 7837
  )
  AND IFNULL(integration_type, '') = 'hub'
  AND visible = 0;

-- 其余 hub + ?module= 且仍为 0 的叶子一并亮起（不含已主动隐藏的质量报告等：
-- 若运维刚把某叶设为 0，本句会把它亮回 1——故仅对「路径含 module= 且 permission 为汇聚细码」的历史种子）
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
