-- 供需状态收敛：ANALYZING→PRE_AUDITING；REJECTED→RETURNED（可回流编辑）
UPDATE biz_data_demand SET status = 'PRE_AUDITING', stage = 'PRE_AUDIT'
WHERE status = 'ANALYZING';

UPDATE biz_data_demand SET status = 'RETURNED', stage = 'MANAGE'
WHERE status = 'REJECTED';
