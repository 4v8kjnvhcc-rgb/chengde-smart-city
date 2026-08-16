-- 生产 .16 ODS/DWD：与 smart_city_ods / smart_city_dwd 同机
-- 目标库：10.10.10.16:13306
-- 可重复执行

CREATE DATABASE IF NOT EXISTS `smart_city_ods_bak` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `smart_city_dwd_bak` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
