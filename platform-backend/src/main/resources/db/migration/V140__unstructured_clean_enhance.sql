-- 非结构化数据清洗转换：规则、问题库、流水线明细

CREATE TABLE IF NOT EXISTS uns_clean_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code VARCHAR(64) NOT NULL COMMENT '规则编码',
  rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
  rule_type VARCHAR(32) NOT NULL COMMENT 'FILTER|DEDUP|VALIDATE|TRANSFORM',
  target_field VARCHAR(64) NOT NULL DEFAULT 'title' COMMENT '目标字段',
  error_level VARCHAR(32) NOT NULL DEFAULT 'WARN' COMMENT 'INFO|WARN|ERROR|CRITICAL',
  enabled TINYINT NOT NULL DEFAULT 1,
  auto_apply TINYINT NOT NULL DEFAULT 1 COMMENT '命中后是否自动改写字段',
  sort_order INT NOT NULL DEFAULT 0,
  config_json TEXT NULL COMMENT '规则参数 JSON',
  description VARCHAR(512) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_uns_clean_rule_code (rule_code),
  KEY idx_uns_clean_rule_type (rule_type),
  KEY idx_uns_clean_rule_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非结构化清洗规则';

CREATE TABLE IF NOT EXISTS uns_clean_issue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_id BIGINT NOT NULL,
  rule_id BIGINT NULL,
  rule_code VARCHAR(64) NULL,
  pipeline_id BIGINT NULL,
  target_field VARCHAR(64) NOT NULL DEFAULT 'title',
  error_level VARCHAR(32) NOT NULL DEFAULT 'WARN',
  issue_status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN|CLEANED_IN|ABANDONED|OTHER',
  before_value VARCHAR(1024) NULL,
  after_value VARCHAR(1024) NULL,
  message VARCHAR(512) NULL,
  handle_note VARCHAR(512) NULL,
  handled_by VARCHAR(64) NULL,
  handled_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_uns_clean_issue_doc (doc_id),
  KEY idx_uns_clean_issue_status (issue_status),
  KEY idx_uns_clean_issue_level (error_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非结构化清洗问题数据';

ALTER TABLE uns_doc_pipeline
  ADD COLUMN detail_json TEXT NULL COMMENT '清洗明细/一致性检查 JSON' AFTER result_message;

INSERT IGNORE INTO uns_clean_rule
  (rule_code, rule_name, rule_type, target_field, error_level, enabled, auto_apply, sort_order, config_json, description, status)
VALUES
('FILTER_GARBAGE_TITLE', '过滤垃圾标题', 'FILTER', 'title', 'ERROR', 1, 0, 10,
 '{"patterns":["测试","test","临时","tmp","untitled","未命名","asdf"]}',
 '基于样本关键词识别冗余/垃圾标题，标记问题数据', 'ACTIVE'),
('TRANSFORM_TRIM_TITLE', '标题去空白', 'TRANSFORM', 'title', 'INFO', 1, 1, 20,
 '{"ops":["trim","collapse_space"]}',
 '过滤首尾空白并合并连续空格', 'ACTIVE'),
('TRANSFORM_SPECIAL_CHAR', '特定字符清理', 'TRANSFORM', 'title', 'WARN', 1, 1, 30,
 '{"removeChars":"\\t\\r\\n<>{}"}',
 '移除标题中的控制符与危险字符', 'ACTIVE'),
('TRANSFORM_FULLWIDTH', '全角转半角-标题', 'TRANSFORM', 'title', 'INFO', 1, 1, 40,
 '{"fullToHalf":true}',
 '格式规范：全角数字/字母转半角', 'ACTIVE'),
('VALIDATE_EMPTY_TITLE', '空值校验-标题', 'VALIDATE', 'title', 'CRITICAL', 1, 0, 50,
 '{"notBlank":true}',
 '完整性校验：标题不可为空', 'ACTIVE'),
('VALIDATE_TITLE_LENGTH', '长度校验-标题', 'VALIDATE', 'title', 'ERROR', 1, 0, 60,
 '{"min":2,"max":200}',
 '规范性校验：标题长度 2～200', 'ACTIVE'),
('VALIDATE_PHONE_DESC', '手机号校验-描述', 'VALIDATE', 'description', 'WARN', 1, 0, 70,
 '{"requireMobileIfPresent":true}',
 '描述中若出现手机号片段则校验 11 位规范', 'ACTIVE'),
('TRANSFORM_PHONE_DESC', '手机号标准化-描述', 'TRANSFORM', 'description', 'INFO', 1, 1, 80,
 '{"normalizePhone":true}',
 '将描述中的手机号去空格/横线后标准化', 'ACTIVE'),
('DEDUP_SAME_TITLE', '去重-同名标题', 'DEDUP', 'title', 'ERROR', 1, 0, 90,
 '{"scope":"category","strategy":"mark"}',
 '同分类下同名标题判重，标记问题数据待确认', 'ACTIVE'),
('TRANSFORM_CASE_ASCII', 'ASCII 大小写规范', 'TRANSFORM', 'title', 'INFO', 1, 1, 100,
 '{"upperAsciiExt":true}',
 '扩展名等 ASCII 片段规范（台账规则引擎，非外部清洗软件）', 'ACTIVE');
