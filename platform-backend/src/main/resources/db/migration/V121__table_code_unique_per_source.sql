-- V121: 表编码唯一性改为「同一数据源内唯一」，允许不同系统/数据源登记同名表

-- 清理可能阻碍加新唯一键的脏数据（理论上旧 uk 下不应存在跨源同 code）
-- 若有历史异常数据，为重复项追加后缀后再建索引

ALTER TABLE ing_data_table DROP INDEX uk_table_code;

ALTER TABLE ing_data_table
  ADD UNIQUE KEY uk_source_table_code (source_id, table_code);
