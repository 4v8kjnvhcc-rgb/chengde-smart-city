-- 质量规则：对照旧页支持「排序」「描述」
ALTER TABLE gov_quality_rule
  ADD COLUMN sort_no INT NOT NULL DEFAULT 0 COMMENT '排序' AFTER rule_type,
  ADD COLUMN description VARCHAR(512) NULL COMMENT '描述' AFTER sort_no;
