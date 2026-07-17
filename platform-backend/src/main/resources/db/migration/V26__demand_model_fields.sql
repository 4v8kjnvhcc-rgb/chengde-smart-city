-- 需求模型化：非结构化正文 + 结构化模型字段
ALTER TABLE biz_data_demand
  ADD COLUMN demand_content TEXT NULL COMMENT '非结构化需求正文' AFTER demand_type,
  ADD COLUMN model_fields TEXT NULL COMMENT '结构化模型字段JSON' AFTER demand_content;
