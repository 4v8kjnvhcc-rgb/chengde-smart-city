-- 目标库：smart_city（10.10.10.56）
-- 与 Flyway V221 相同；生产已跑 V220 后仍缺 UUM 侧栏时可手工执行
-- 可重复执行（幂等 UPDATE）

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
