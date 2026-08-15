-- V221: 统一用户 Hub 内页侧栏可见（补 V220 未覆盖的页壳与分组）
-- 根因：id=13 path=/analytics/support 无 ?tab=，V220 未置 visible=1；
-- filterHubNavByMenuVisible 沿 parent 判定，导致 UUM 七中心侧栏被整树隐藏，
-- 仅通用支撑下 visible=1 的 7882/7883（任务/运维）仍显示。

UPDATE sys_menu
SET visible = 1
WHERE status = 1
  AND IFNULL(integration_type, '') = 'hub'
  AND id IN (13, 14, 7500, 7501, 7502, 7503, 7504, 7505, 7506);

UPDATE sys_menu
SET visible = 1
WHERE status = 1
  AND IFNULL(integration_type, '') = 'hub'
  AND parent_id IN (7500, 7501, 7502, 7503, 7504, 7505, 7506, 7722);

UPDATE sys_menu
SET visible = 1
WHERE status = 1
  AND IFNULL(integration_type, '') = 'hub'
  AND parent_id = 7722;
