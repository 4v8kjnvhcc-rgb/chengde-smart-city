-- 字典项增加业务使用说明
ALTER TABLE ing_dict_item
  ADD COLUMN biz_usage VARCHAR(512) NULL COMMENT '业务使用说明' AFTER item_value;
