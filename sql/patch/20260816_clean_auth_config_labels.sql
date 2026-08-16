-- 手工补丁：认证中心说明文案去一期/扩展位/M 码等内部标注
-- 目标库：smart_city（控制面）
-- 可重复执行；与 Flyway V232__clean_auth_config_labels.sql 同内容
-- 若环境已跑过 V232，无需再手工执行

UPDATE sys_dict_item i
INNER JOIN sys_dict d ON d.id = i.dict_id AND d.dict_code = 'AUTH'
SET i.item_label = '统一认证开关'
WHERE i.item_key = 'sso.enabled';

UPDATE sys_dict_item i
INNER JOIN sys_dict d ON d.id = i.dict_id AND d.dict_code = 'AUTH'
SET i.item_label = '支持的认证方式：PASSWORD/TOTP/SMS/BIOMETRIC'
WHERE i.item_key = 'auth.methods';

UPDATE sys_dict_item i
INNER JOIN sys_dict d ON d.id = i.dict_id AND d.dict_code = 'AUTH'
SET i.item_label = '统一认证/SSO 开关'
WHERE i.item_key = 'auth.sso.enabled';

UPDATE sys_dict_item i
INNER JOIN sys_dict d ON d.id = i.dict_id AND d.dict_code = 'AUTH'
SET i.item_label = TRIM(REPLACE(IFNULL(i.item_label, ''), '（扩展位）', ''))
WHERE i.item_key IN ('auth.user.sync.enabled', 'auth.usersync.enabled');
