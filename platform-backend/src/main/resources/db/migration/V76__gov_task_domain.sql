-- 治理任务 / 融合任务分域：GOVERNANCE=ODS→DWD；FUSION=DWD→DWS/ADS
ALTER TABLE gov_governance_task
    ADD COLUMN task_domain VARCHAR(32) NOT NULL DEFAULT 'GOVERNANCE'
        COMMENT 'GOVERNANCE|FUSION' AFTER engine_type;

CREATE INDEX idx_gov_task_domain ON gov_governance_task (task_domain);
