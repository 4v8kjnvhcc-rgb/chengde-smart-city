-- 平台分层数仓库（ODS/DWD/DWS/ADS），与 smart_city 控制库隔离。
-- 供 Kettle 汇聚、加工融合、元数据采集等写入物理表。

CREATE DATABASE IF NOT EXISTS smart_city_ods CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_city_dwd CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_city_dws CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_city_ads CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 可选：授予 root 全权限（本地开发 compose 默认 root 已具备）
-- GRANT ALL PRIVILEGES ON smart_city_ods.* TO 'root'@'%';
-- GRANT ALL PRIVILEGES ON smart_city_dwd.* TO 'root'@'%';
-- GRANT ALL PRIVILEGES ON smart_city_dws.* TO 'root'@'%';
-- GRANT ALL PRIVILEGES ON smart_city_ads.* TO 'root'@'%';
-- FLUSH PRIVILEGES;
