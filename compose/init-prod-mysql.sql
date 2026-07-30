-- 生产 Compose：在 MYSQL_DATABASE=smart_city 之外补分层库与授权
CREATE DATABASE IF NOT EXISTS smart_city_ods CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_city_dwd CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_city_dws CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS smart_city_ads CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON smart_city.* TO 'smart_city'@'%';
GRANT ALL PRIVILEGES ON smart_city_ods.* TO 'smart_city'@'%';
GRANT ALL PRIVILEGES ON smart_city_dwd.* TO 'smart_city'@'%';
GRANT ALL PRIVILEGES ON smart_city_dws.* TO 'smart_city'@'%';
GRANT ALL PRIVILEGES ON smart_city_ads.* TO 'smart_city'@'%';
FLUSH PRIVILEGES;
