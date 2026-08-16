-- 本地：在同一 MySQL 实例创建五个备份库（与源库同机）
-- 可重复执行

CREATE DATABASE IF NOT EXISTS `smart_city_bak` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `smart_city_ods_bak` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `smart_city_dwd_bak` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `smart_city_dws_bak` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `smart_city_ads_bak` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
