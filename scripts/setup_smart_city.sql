-- 本机 MySQL 初始化 smart_city（MS1）
-- 用法（按你的 root 密码调整）：
--   mysql -u root -p < scripts/setup_smart_city.sql

CREATE DATABASE IF NOT EXISTS smart_city
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 若只用 root 开发，可跳过创建业务账号，并在 application-dev.yml 里用 root
CREATE USER IF NOT EXISTS 'smart_city'@'localhost' IDENTIFIED BY 'smart_city';
GRANT ALL PRIVILEGES ON smart_city.* TO 'smart_city'@'localhost';
FLUSH PRIVILEGES;
