-- 按图一重建统一身份组织机构：根「承德市高新区」+ 19 个直属单位，删除其余机构

-- 1) 根节点改名（保留 id=1）
UPDATE sys_org
SET org_code = '100000',
    org_name = '承德市高新区',
    parent_id = 0,
    org_type = 1,
    sort_order = 0,
    status = 1
WHERE id = 1;

-- 2) 复用可对齐的旧机构：挂到根下并按图一改名/改编码
UPDATE sys_org
SET parent_id = 1, org_name = '党群工作部（人力资源部）', org_type = 2, sort_order = 13, status = 1
WHERE org_code = '100020';
INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, '100020', '党群工作部（人力资源部）', 2, 13, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = '100020');

UPDATE sys_org
SET parent_id = 1, org_name = '经济发展部', org_type = 2, sort_order = 12, status = 1
WHERE org_code = '100100';
INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, '100100', '经济发展部', 2, 12, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = '100100');

UPDATE sys_org
SET parent_id = 1, org_name = '科技创新部（综合统计部）', org_type = 2, sort_order = 7, status = 1
WHERE org_code = '100110';
INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, '100110', '科技创新部（综合统计部）', 2, 7, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = '100110');

UPDATE sys_org
SET parent_id = 1, org_name = '财政金融部', org_type = 2, sort_order = 11, status = 1
WHERE org_code = '100120';
INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, '100120', '财政金融部', 2, 11, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = '100120');

UPDATE sys_org
SET parent_id = 1, org_name = '社会事务部', org_type = 2, sort_order = 10, status = 1
WHERE org_code = '100130';
INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, '100130', '社会事务部', 2, 10, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = '100130');

-- 应急管理部 → 应急事务部
UPDATE sys_org
SET parent_id = 1, org_code = 'GX_YJSW', org_name = '应急事务部', org_type = 2, sort_order = 6, status = 1
WHERE org_code = '100140';
INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_YJSW', '应急事务部', 2, 6, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_YJSW');

-- 行政审批服务局 → 行政审批局
UPDATE sys_org
SET parent_id = 1, org_code = 'GX_XZSP', org_name = '行政审批局', org_type = 2, sort_order = 15, status = 1
WHERE org_code = '100150';
INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_XZSP', '行政审批局', 2, 15, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_XZSP');

-- 城市管理综合行政执法局 → 城市管理部（综合协调联络办公室）
UPDATE sys_org
SET parent_id = 1, org_code = 'GX_CSGL', org_name = '城市管理部（综合协调联络办公室）', org_type = 2, sort_order = 4, status = 1
WHERE org_code = '100160';
INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_CSGL', '城市管理部（综合协调联络办公室）', 2, 4, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_CSGL');

-- 管委会保留为叶子
UPDATE sys_org
SET parent_id = 1, org_name = '承德高新技术产业开发区管理委员会', org_type = 1, sort_order = 18, status = 1
WHERE org_code = '100002';
INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, '100002', '承德高新技术产业开发区管理委员会', 1, 18, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = '100002');

-- 3) 图一其余单位
INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_SJDJ', '数据对接', 2, 1, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_SJDJ');

INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_TEST_MZ', '测试-高新区民政局', 2, 2, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_TEST_MZ');

INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_TEST_RS', '测试-高新区人社局', 2, 3, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_TEST_RS');

INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_ZSFW', '征收服务部', 2, 5, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_ZSFW');

INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_ZSFWB', '招商服务部', 2, 8, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_ZSFWB');

INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_JCJS', '基础建设部', 2, 9, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_JCJS');

INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_ZHB', '综合部（内审部）', 2, 14, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_ZHB');

INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_TEST_YJ', '测试-高新区应急管理局', 2, 16, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_TEST_YJ');

INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_TEST_SC', '测试-高新区市场监督管理分局', 2, 17, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_TEST_SC');

INSERT INTO sys_org (parent_id, org_code, org_name, org_type, sort_order, status)
SELECT 1, 'GX_TEST_SP', '测试-高新区行政审批服务管理局', 2, 19, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_org WHERE org_code = 'GX_TEST_SP');

UPDATE sys_org SET parent_id = 1, org_name = '数据对接', org_type = 2, sort_order = 1, status = 1 WHERE org_code = 'GX_SJDJ';
UPDATE sys_org SET parent_id = 1, org_name = '测试-高新区民政局', org_type = 2, sort_order = 2, status = 1 WHERE org_code = 'GX_TEST_MZ';
UPDATE sys_org SET parent_id = 1, org_name = '测试-高新区人社局', org_type = 2, sort_order = 3, status = 1 WHERE org_code = 'GX_TEST_RS';
UPDATE sys_org SET parent_id = 1, org_name = '征收服务部', org_type = 2, sort_order = 5, status = 1 WHERE org_code = 'GX_ZSFW';
UPDATE sys_org SET parent_id = 1, org_name = '招商服务部', org_type = 2, sort_order = 8, status = 1 WHERE org_code = 'GX_ZSFWB';
UPDATE sys_org SET parent_id = 1, org_name = '基础建设部', org_type = 2, sort_order = 9, status = 1 WHERE org_code = 'GX_JCJS';
UPDATE sys_org SET parent_id = 1, org_name = '综合部（内审部）', org_type = 2, sort_order = 14, status = 1 WHERE org_code = 'GX_ZHB';
UPDATE sys_org SET parent_id = 1, org_name = '测试-高新区应急管理局', org_type = 2, sort_order = 16, status = 1 WHERE org_code = 'GX_TEST_YJ';
UPDATE sys_org SET parent_id = 1, org_name = '测试-高新区市场监督管理分局', org_type = 2, sort_order = 17, status = 1 WHERE org_code = 'GX_TEST_SC';
UPDATE sys_org SET parent_id = 1, org_name = '测试-高新区行政审批服务管理局', org_type = 2, sort_order = 19, status = 1 WHERE org_code = 'GX_TEST_SP';

-- 4) 临时白名单
CREATE TEMPORARY TABLE tmp_keep_org AS
SELECT id FROM sys_org
WHERE id = 1
   OR org_code IN (
     'GX_SJDJ', 'GX_TEST_MZ', 'GX_TEST_RS', 'GX_CSGL', 'GX_ZSFW', 'GX_YJSW',
     '100110', 'GX_ZSFWB', 'GX_JCJS', '100130', '100120', '100100',
     '100020', 'GX_ZHB', 'GX_XZSP', 'GX_TEST_YJ', 'GX_TEST_SC', '100002', 'GX_TEST_SP'
   );

-- 5) 用户与业务引用迁到根
UPDATE sys_user SET org_id = 1
WHERE org_id IS NOT NULL AND org_id NOT IN (SELECT id FROM tmp_keep_org);

UPDATE ing_project SET bound_org_id = 1
WHERE bound_org_id IS NOT NULL AND bound_org_id NOT IN (SELECT id FROM tmp_keep_org);

UPDATE sys_project_grant SET org_id = 1
WHERE org_id NOT IN (SELECT id FROM tmp_keep_org);

UPDATE sys_data_grant SET org_id = 1
WHERE org_id NOT IN (SELECT id FROM tmp_keep_org);

UPDATE ing_asset_catalog_reg SET org_id = 1
WHERE org_id IS NOT NULL AND org_id NOT IN (SELECT id FROM tmp_keep_org);

UPDATE ing_resource_registry SET org_id = 1
WHERE org_id IS NOT NULL AND org_id NOT IN (SELECT id FROM tmp_keep_org);

UPDATE audit_log SET org_id = 1
WHERE org_id IS NOT NULL AND org_id NOT IN (SELECT id FROM tmp_keep_org);

UPDATE cross_dept_access_request SET applicant_org_id = 1
WHERE applicant_org_id NOT IN (SELECT id FROM tmp_keep_org);

UPDATE cross_dept_access_request SET target_org_id = 1
WHERE target_org_id NOT IN (SELECT id FROM tmp_keep_org);

-- 6) 删除多余机构
DELETE FROM sys_org
WHERE id NOT IN (SELECT id FROM tmp_keep_org);

DROP TEMPORARY TABLE tmp_keep_org;

ALTER TABLE sys_org AUTO_INCREMENT = 400;
