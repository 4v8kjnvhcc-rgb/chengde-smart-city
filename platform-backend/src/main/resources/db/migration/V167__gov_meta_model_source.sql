-- 元模型关联 M088 数据源与源表/源字段
ALTER TABLE gov_meta_model
    ADD COLUMN meta_data_source_id BIGINT NULL COMMENT '关联元数据数据源' AFTER component_type,
    ADD COLUMN source_table_name VARCHAR(128) NULL COMMENT '源表名' AFTER meta_data_source_id,
    ADD COLUMN source_column_name VARCHAR(128) NULL COMMENT '字段模型关联列' AFTER source_table_name;

CREATE INDEX idx_gov_meta_model_source ON gov_meta_model (meta_data_source_id, source_table_name);
