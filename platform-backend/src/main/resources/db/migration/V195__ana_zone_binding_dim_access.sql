-- 采集区选型挂载：维度分组 + 接入方式
ALTER TABLE ana_zone_binding
  ADD COLUMN dim_group VARCHAR(32) NULL COMMENT 'DATATYPE|LATENCY' AFTER data_layer,
  ADD COLUMN access_mode VARCHAR(32) NULL COMMENT 'STRUCT|UNSTRUCT|API|CDC' AFTER dim_group;
