-- 数据字典：补全标准属性；类型改为国标/地标/行标/团标/自定义
ALTER TABLE ing_dict
  ADD COLUMN standard_no VARCHAR(128) NULL COMMENT '标准号' AFTER dict_type,
  ADD COLUMN publisher VARCHAR(256) NULL COMMENT '发布单位' AFTER standard_no,
  ADD COLUMN version_no VARCHAR(64) NULL COMMENT '版本' AFTER publisher,
  ADD COLUMN remark VARCHAR(512) NULL COMMENT '说明' AFTER version_no;

UPDATE ing_dict SET dict_type = 'GB' WHERE dict_type IN ('STANDARD', '标准');
UPDATE ing_dict SET dict_type = 'CUSTOM' WHERE dict_type IN ('BUSINESS', '业务');
