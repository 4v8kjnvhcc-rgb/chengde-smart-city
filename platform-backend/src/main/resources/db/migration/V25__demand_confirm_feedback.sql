-- M022 需求确认：确认反馈字段
ALTER TABLE biz_data_demand
  ADD COLUMN confirm_feedback VARCHAR(512) NULL COMMENT '供数部门督查反馈' AFTER confirm_note;
