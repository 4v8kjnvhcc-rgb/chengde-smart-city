-- 双引擎类型字段
ALTER TABLE gov_governance_task
    ADD COLUMN engine_type VARCHAR(16) DEFAULT 'KETTLE' COMMENT '执行引擎 IN_MEMORY/KETTLE' AFTER variables_json;
