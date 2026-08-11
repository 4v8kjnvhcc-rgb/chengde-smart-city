-- 人口 C 演示骨架：批量交换台账（LEDGER，非生产前置全链路）

CREATE TABLE IF NOT EXISTS ana_pop_batch_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  domain_code VARCHAR(32) NOT NULL DEFAULT 'population',
  batch_code VARCHAR(64) NOT NULL,
  service_code VARCHAR(64) NULL,
  channel VARCHAR(256) NULL COMMENT '前置库↔交换↔结果库说明',
  table_name VARCHAR(128) NULL,
  row_limit INT NULL,
  batch_status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN|ACCEPTED|DONE|FAILED',
  message VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'LEDGER',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  UNIQUE KEY uk_pop_batch_code (batch_code),
  KEY idx_pop_batch_status (batch_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人口批量交换设计台账';

INSERT INTO ana_pop_batch_ledger
  (domain_code, batch_code, service_code, channel, table_name, row_limit, batch_status, message, status, created_by)
VALUES
('population','BATCH-POP-202607-01','POP_BATCH_EXCHANGE','前置库 ↔ 交换系统 ↔ 共享结果库','dws_population_base',1000,'DONE',
 '演示批次：已登记完成（未真实传输）','LEDGER','sys_admin'),
('population','BATCH-POP-202608-01','POP_BATCH_EXCHANGE','前置库 ↔ 交换系统 ↔ 共享结果库','ads_pop_district_summary',500,'ACCEPTED',
 '演示批次：已受理待回传','LEDGER','sys_admin'),
('population','BATCH-POP-202608-02','POP_BATCH_EXCHANGE','前置库 ↔ 交换系统 ↔ 共享结果库','dws_population_base',2000,'OPEN',
 '演示批次：待共建单位前置库就绪','LEDGER','sys_admin')
ON DUPLICATE KEY UPDATE message = VALUES(message);
