-- 元数据数据源默认可写；元模型建表/改字段不受 read_only_flag 限制，此处修正历史默认只读
ALTER TABLE gov_meta_data_source
    MODIFY read_only_flag TINYINT NOT NULL DEFAULT 0 COMMENT '预留标记，不限制元模型DDL';

UPDATE gov_meta_data_source SET read_only_flag = 0 WHERE read_only_flag = 1;
