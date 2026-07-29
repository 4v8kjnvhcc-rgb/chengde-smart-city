-- V86: 资产目录登记/管理侧栏顺序靠前，与标签登记/管理相邻

UPDATE sys_menu SET sort_order = 6 WHERE id = 7013 AND permission = 'hub:ingestion:register:asset-catalog-reg';
UPDATE sys_menu SET sort_order = 8 WHERE id = 7014 AND permission = 'hub:ingestion:register:asset-catalog-mgmt';

-- 原 m044～m050 顺延，避免与 6/8 冲突
UPDATE sys_menu SET sort_order = 7 WHERE id = 7006 AND permission = 'hub:ingestion:register:m044';
UPDATE sys_menu SET sort_order = 9 WHERE id = 7007 AND permission = 'hub:ingestion:register:m045';
UPDATE sys_menu SET sort_order = 10 WHERE id = 7008 AND permission = 'hub:ingestion:register:m046';
UPDATE sys_menu SET sort_order = 11 WHERE id = 7009 AND permission = 'hub:ingestion:register:m047';
UPDATE sys_menu SET sort_order = 12 WHERE id = 7010 AND permission = 'hub:ingestion:register:m048';
UPDATE sys_menu SET sort_order = 13 WHERE id = 7011 AND permission = 'hub:ingestion:register:m049';
UPDATE sys_menu SET sort_order = 14 WHERE id = 7012 AND permission = 'hub:ingestion:register:m050';
