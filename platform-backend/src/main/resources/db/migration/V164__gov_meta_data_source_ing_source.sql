-- 元数据数据源：关联归集登记数据源，支持「来源」分类采集
ALTER TABLE gov_meta_data_source
    ADD COLUMN ing_source_id BIGINT NULL COMMENT '关联 ing_data_source.id' AFTER connector_id;

CREATE UNIQUE INDEX uk_gov_meta_data_source_ing ON gov_meta_data_source (ing_source_id);
