-- 手动上传模板/记录：归属机构，用于部门隔离
ALTER TABLE ing_upload_template
  ADD COLUMN org_id BIGINT NULL COMMENT '归属机构 ID' AFTER status;

ALTER TABLE ing_upload_record
  ADD COLUMN org_id BIGINT NULL COMMENT '归属机构 ID' AFTER created_by;

CREATE INDEX idx_ing_upload_tpl_org ON ing_upload_template (org_id);
CREATE INDEX idx_ing_upload_rec_org ON ing_upload_record (org_id);
CREATE INDEX idx_ing_upload_rec_tpl ON ing_upload_record (template_code);
