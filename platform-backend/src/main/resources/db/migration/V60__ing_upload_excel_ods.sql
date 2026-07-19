-- 手动上传：sheet / 目标表 / 本地缓存路径；预览 JSON 放宽
ALTER TABLE ing_upload_record
  ADD COLUMN sheet_name VARCHAR(128) NULL COMMENT '选中的 Excel sheet' AFTER file_name,
  ADD COLUMN target_table VARCHAR(128) NULL COMMENT '写入 ODS 表名' AFTER sheet_name,
  ADD COLUMN storage_path VARCHAR(512) NULL COMMENT '临时文件路径' AFTER target_table,
  MODIFY COLUMN preview_json TEXT NULL;
