-- 部门数据共享门户「个人空间 · 我的应用」
CREATE TABLE IF NOT EXISTS biz_portal_app (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '所属用户',
  username VARCHAR(64) NOT NULL,
  org_id BIGINT NULL,
  app_name VARCHAR(128) NOT NULL COMMENT '应用系统名称',
  contact_name VARCHAR(64) NOT NULL COMMENT '联系人',
  contact_phone VARCHAR(32) NOT NULL COMMENT '联系电话',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_portal_app_user_name (user_id, app_name),
  KEY idx_portal_app_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门户个人空间-我的应用';
