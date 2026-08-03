-- V115: 移除旧演示机构 ORG_A / ORG_B 及其演示账号

-- 访问控制演示授权（若存在）
DELETE g FROM sys_data_grant g
WHERE g.org_id IN (SELECT id FROM sys_org WHERE org_code IN ('ORG_A', 'ORG_B'));

DELETE g FROM sys_project_grant g
WHERE g.org_id IN (SELECT id FROM sys_org WHERE org_code IN ('ORG_A', 'ORG_B'));

DELETE g FROM sys_project_grant g
WHERE g.grantee_type = 'USER'
  AND g.grantee_id IN (SELECT id FROM sys_user WHERE username IN ('user_a', 'dept_admin_a', 'user_b'));

DELETE g FROM sys_data_grant g
WHERE g.grantee_type = 'USER'
  AND g.grantee_id IN (SELECT id FROM sys_user WHERE username IN ('user_a', 'dept_admin_a', 'user_b'));

-- 演示账号
DELETE ur FROM sys_user_role ur
INNER JOIN sys_user u ON u.id = ur.user_id
WHERE u.username IN ('user_a', 'dept_admin_a', 'user_b');

DELETE FROM sys_user WHERE username IN ('user_a', 'dept_admin_a', 'user_b');

-- 演示机构（无下级）
DELETE FROM sys_org WHERE org_code IN ('ORG_A', 'ORG_B');
