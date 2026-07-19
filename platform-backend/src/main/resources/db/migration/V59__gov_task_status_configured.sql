-- 治理任务生命周期：READY 统一为 CONFIGURED（对外展示「已配置」）
UPDATE gov_governance_task
SET status = 'CONFIGURED'
WHERE status = 'READY';
