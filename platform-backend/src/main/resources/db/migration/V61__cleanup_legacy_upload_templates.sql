-- 清理旧版手动上传模板（无 sheet/bindings，无法用于新上传流程）
DELETE FROM ing_upload_template
WHERE template_code IN ('TPL_STRUCT_01', 'TPL_DICT_01')
   OR template_name IN ('结构化示范模板', '字典导入模板', '测试模板')
   OR column_mapping_json IS NULL
   OR column_mapping_json NOT LIKE '%"bindings"%'
   OR column_mapping_json NOT LIKE '%sheetName%';
