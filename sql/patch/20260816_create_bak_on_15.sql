-- 生产 .15 DWS/ADS：与 smart_city_dws / smart_city_ads 同机
-- 目标库：10.10.10.15:13306
-- 可重复执行

CREATE DATABASE IF NOT EXISTS `smart_city_dws_bak` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `smart_city_ads_bak` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
