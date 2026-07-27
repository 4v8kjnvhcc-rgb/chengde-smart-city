-- V80: 角色/组织「配置菜单」与各 Hub 实际侧栏对齐
-- 1) 隐藏 D05 catalog、已并入旧入口
-- 2) 写入各平台 Hub 真实侧栏节点（integration_type=hub；visible=0 避免外层门户侧栏膨胀，角色授权仍可见）
-- 3) 赋权系统管理员

-- ========== A. 隐藏噪音 ==========
UPDATE sys_menu
SET status = 0
WHERE status = 1
  AND (
    integration_type = 'catalog'
    OR menu_name LIKE '%D05%'
    OR menu_name LIKE '%已并入%'
    OR IFNULL(path, '') LIKE '/modules/%'
    OR IFNULL(path, '') LIKE '/catalog/%'
    OR path = '/catalog'
  );

-- ========== B. 大数据归集平台 (parent=4) ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7000, 4, '数据资产登记管理', 1, NULL, NULL, NULL, NULL, 10, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7000);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7001, 7000, '填报指引', 2, '/exchange/ingestion?system=register&module=m039', NULL, 'hub:ingestion:register:m039', NULL, 1, 'M039', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7001);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7002, 7000, '项目/系统信息登记', 2, '/exchange/ingestion?system=register&module=m040', NULL, 'hub:ingestion:register:m040', NULL, 2, 'M040', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7002);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7003, 7000, '数据库/表/项登记', 2, '/exchange/ingestion?system=register&module=m041', NULL, 'hub:ingestion:register:m041', NULL, 3, 'M041', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7003);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7004, 7000, '数据字典登记', 2, '/exchange/ingestion?system=register&module=m042', NULL, 'hub:ingestion:register:m042', NULL, 4, 'M042', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7004);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7005, 7000, '数据资产标签登记', 2, '/exchange/ingestion?system=register&module=m043', NULL, 'hub:ingestion:register:m043', NULL, 5, 'M043', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7005);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7006, 7000, '数据项管理', 2, '/exchange/ingestion?system=register&module=m044', NULL, 'hub:ingestion:register:m044', NULL, 6, 'M044', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7006);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7007, 7000, '数据资产标签管理', 2, '/exchange/ingestion?system=register&module=m045', NULL, 'hub:ingestion:register:m045', NULL, 7, 'M045', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7007);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7008, 7000, '数据资产报告', 2, '/exchange/ingestion?system=register&module=m046', NULL, 'hub:ingestion:register:m046', NULL, 8, 'M046', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7008);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7009, 7000, '数据资产图谱分析', 2, '/exchange/ingestion?system=register&module=m047', NULL, 'hub:ingestion:register:m047', NULL, 9, 'M047', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7009);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7010, 7000, '访问控制管理', 2, '/exchange/ingestion?system=register&module=m048', NULL, 'hub:ingestion:register:m048', NULL, 10, 'M048', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7010);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7011, 7000, '系统维护管理', 2, '/exchange/ingestion?system=register&module=m049', NULL, 'hub:ingestion:register:m049', NULL, 11, 'M049', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7011);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7012, 7000, '数据字典管理', 2, '/exchange/ingestion?system=register&module=m050', NULL, 'hub:ingestion:register:m050', NULL, 12, 'M050', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7012);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7020, 4, '数据资源采集汇聚', 1, NULL, NULL, NULL, NULL, 20, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7020);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7021, 7020, '数据汇聚接入', 2, '/exchange/ingestion?system=collect&module=ingest', NULL, 'hub:ingestion:collect:ingest', NULL, 1, 'M054', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7021);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7022, 7020, '规范设计', 2, '/exchange/ingestion?system=collect&module=pipeline', NULL, 'hub:ingestion:collect:pipeline', NULL, 2, 'M061', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7022);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7023, 7020, '指标与目录体系构建', 2, '/exchange/ingestion?system=collect&module=catalog', NULL, 'hub:ingestion:collect:catalog', NULL, 3, 'M065', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7023);

-- 删除登记项目按钮挂到「项目/系统信息登记」下
UPDATE sys_menu SET parent_id = 7002, sort_order = 99 WHERE id = 4100 AND permission = 'exchange:project:delete';

-- ========== C. 应用平台 (parent=6) ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7100, 6, '数据共享门户', 1, NULL, NULL, NULL, NULL, 10, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7100);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7101, 7100, '首页', 2, '/exchange/application?system=portal&section=home', NULL, 'hub:application:portal:home', NULL, 1, 'M032', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7101);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7102, 7100, '共享资源', 2, '/exchange/application?system=portal&section=catalog', NULL, 'hub:application:portal:catalog', NULL, 2, 'M034', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7102);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7103, 7100, '资源订阅申请', 2, '/exchange/application?system=portal&section=subscribe', NULL, 'hub:application:portal:subscribe', NULL, 3, 'M035', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7103);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7104, 7100, '考核评估', 2, '/exchange/application?system=portal&section=assessment', NULL, 'portal:assessment:view', NULL, 4, 'M030', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7104);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7105, 7100, '我的空间', 2, '/exchange/application?system=portal&section=myspace', NULL, 'hub:application:portal:myspace', NULL, 5, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7105);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7110, 6, '统计分析', 2, '/exchange/application?system=stats', NULL, 'analytics:stats:view', NULL, 20, 'M037', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7110);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7111, 6, '决策驾驶舱', 2, '/exchange/application?system=cockpit', NULL, 'analytics:cockpit:view', NULL, 30, 'M036', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7111);

-- ========== D. 数据融合治理平台 (parent=9) ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7200, 9, '元数据管理', 1, NULL, NULL, NULL, NULL, 10, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7200);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7201, 7200, '元模型管理', 2, '/governance?tab=metadata&section=model', NULL, 'hub:gov:metadata:model', NULL, 1, 'M089', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7201);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7202, 7200, '元数据采集', 2, '/governance?tab=metadata&section=collect', NULL, 'hub:gov:metadata:collect', NULL, 2, 'M090', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7202);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7203, 7200, '元数据采集监控', 2, '/governance?tab=metadata&section=monitor', NULL, 'hub:gov:metadata:monitor', NULL, 3, 'M091', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7203);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7204, 7200, '元数据维护', 2, '/governance?tab=metadata&section=maintain', NULL, 'hub:gov:metadata:maintain', NULL, 4, 'M092', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7204);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7205, 7200, '元数据版本管理', 2, '/governance?tab=metadata&section=version', NULL, 'hub:gov:metadata:version', NULL, 5, 'M093', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7205);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7206, 7200, '元数据目录', 2, '/governance?tab=metadata&section=catalog', NULL, 'hub:gov:metadata:catalog', NULL, 6, 'M095', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7206);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7207, 7200, '元数据分析', 2, '/governance?tab=metadata&section=analyze', NULL, 'hub:gov:metadata:analyze', NULL, 7, 'M096', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7207);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7210, 9, '数据治理', 1, NULL, NULL, NULL, NULL, 20, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7210);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7211, 7210, '任务管理', 2, '/governance?tab=etl&etlSub=task-mgmt', NULL, 'hub:gov:etl:task-mgmt', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7211);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7212, 7210, '任务运行', 2, '/governance?tab=etl&etlSub=task-run', NULL, 'hub:gov:etl:task-run', NULL, 2, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7212);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7213, 7210, '任务定时', 2, '/governance?tab=etl&etlSub=task-schedule', NULL, 'hub:gov:etl:task-schedule', NULL, 3, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7213);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7214, 7210, 'ETL监控', 2, '/governance?tab=etl&etlSub=etl-monitor', NULL, 'hub:gov:etl:etl-monitor', NULL, 4, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7214);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7215, 7210, '数据治理组件', 2, '/governance?tab=etl&etlSub=components', NULL, 'hub:gov:etl:components', NULL, 5, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7215);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7220, 9, '数据质量管理系统', 1, NULL, NULL, NULL, NULL, 30, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7220);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7221, 7220, '数据标准体系', 2, '/governance?tab=quality&qSub=standards', NULL, 'hub:gov:quality:standards', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7221);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7222, 7220, '质量规则配置', 2, '/governance?tab=quality&qSub=rule-config', NULL, 'hub:gov:quality:rule-config', NULL, 2, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7222);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7223, 7220, '数据质量任务', 2, '/governance?tab=quality&qSub=task-mgmt', NULL, 'hub:gov:quality:task-mgmt', NULL, 3, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7223);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7224, 7220, '数据质量监控', 2, '/governance?tab=quality&qSub=monitor', NULL, 'hub:gov:quality:monitor', NULL, 4, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7224);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7225, 7220, '数据质量评估', 2, '/governance?tab=quality&qSub=assess', NULL, 'hub:gov:quality:assess', NULL, 5, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7225);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7226, 7220, '数据质量分析报告', 2, '/governance?tab=quality&qSub=reports', NULL, 'hub:gov:quality:reports', NULL, 6, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7226);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7230, 9, '数据融合系统', 1, NULL, NULL, NULL, NULL, 40, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7230);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7231, 7230, '数据仓库建设', 2, '/governance?tab=model&mSub=warehouse', NULL, 'hub:gov:fusion:warehouse', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7231);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7232, 7230, '数据融合处理', 1, NULL, NULL, NULL, NULL, 2, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7232);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7233, 7232, '脚本开发', 2, '/governance?tab=model&mSub=script', NULL, 'hub:gov:fusion:script', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7233);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7234, 7232, '数据清洗', 2, '/governance?tab=model&mSub=clean', NULL, 'hub:gov:fusion:clean', NULL, 2, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7234);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7235, 7232, '工作流调度', 2, '/governance?tab=model&mSub=schedule', NULL, 'hub:gov:fusion:schedule', NULL, 3, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7235);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7236, 7232, '任务执行', 2, '/governance?tab=model&mSub=execute', NULL, 'hub:gov:fusion:execute', NULL, 4, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7236);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7237, 7232, '版本管理', 2, '/governance?tab=model&mSub=version', NULL, 'hub:gov:fusion:version', NULL, 5, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7237);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7238, 7230, '数据融合组件', 2, '/governance?tab=model&mSub=components', NULL, 'hub:gov:fusion:components', NULL, 3, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7238);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7240, 9, '数据目录管理系统', 1, NULL, NULL, NULL, NULL, 50, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7240);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7241, 7240, '资源目录编制', 2, '/governance?tab=catalog&cSub=resources', NULL, 'hub:gov:catalog:resources', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7241);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7242, 7240, '目录注册发布', 2, '/governance?tab=catalog&cSub=publish', NULL, 'hub:gov:catalog:publish', NULL, 2, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7242);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7243, 7240, '资源目录审批', 2, '/governance?tab=catalog&cSub=approvals', NULL, 'hub:gov:catalog:approvals', NULL, 3, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7243);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7244, 7240, '资源申请订阅', 2, '/governance?tab=catalog&cSub=subscriptions', NULL, 'hub:gov:catalog:subscriptions', NULL, 4, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7244);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7245, 7240, '资源目录门户', 2, '/governance?tab=catalog&cSub=portal', NULL, 'hub:gov:catalog:portal', NULL, 5, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7245);

-- ========== E. 非结构数据融合治理 (parent=10) ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7300, 10, '文件资源管理', 2, '/unstructured?tab=files', NULL, 'hub:unstruct:files', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7300);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7301, 10, '数据分类管理', 2, '/unstructured?tab=classify', NULL, 'hub:unstruct:classify', NULL, 2, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7301);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7302, 10, '文件资源检索', 2, '/unstructured?tab=search', NULL, 'hub:unstruct:search', NULL, 3, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7302);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7303, 10, '非结构化元数据管理', 2, '/unstructured?tab=metadata', NULL, 'hub:unstruct:metadata', NULL, 4, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7303);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7304, 10, '非结构化数据处理', 1, NULL, NULL, NULL, NULL, 5, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7304);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7305, 7304, '非结构化数据清洗转换处理', 2, '/unstructured?tab=process.clean', NULL, 'hub:unstruct:process:clean', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7305);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7306, 7304, '非结构化数据标识处理', 2, '/unstructured?tab=process.tag', NULL, 'hub:unstruct:process:tag', NULL, 2, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7306);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7307, 7304, '非结构化数据关联处理', 2, '/unstructured?tab=process.link', NULL, 'hub:unstruct:process:link', NULL, 3, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7307);

-- ========== F. 资源中心 (parent=11) ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7400, 11, '数据资产区', 2, '/resource-center?tab=asset', NULL, 'hub:resource:asset', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7400);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7401, 11, '分区设计管理', 2, '/resource-center?tab=partition', NULL, 'hub:resource:partition', NULL, 2, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7401);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7402, 11, '数据库存储管理', 2, '/resource-center?tab=storage', NULL, 'hub:resource:storage', NULL, 3, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7402);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7403, 11, '资产目录管理', 2, '/resource-center?tab=catalog', NULL, 'hub:resource:catalog', NULL, 4, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7403);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7404, 11, '数据库检索查询', 2, '/resource-center?tab=search', NULL, 'hub:resource:search', NULL, 5, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7404);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7405, 11, '数据库统计分析', 2, '/resource-center?tab=stats', NULL, 'hub:resource:stats', NULL, 6, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7405);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7406, 11, '资源监控管理', 2, '/resource-center?tab=monitor', NULL, 'hub:resource:monitor', NULL, 7, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7406);

-- ========== G. 挖掘分析 · 通用支撑 (parent=13) ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7500, 13, '用户中心', 2, '/analytics/support?tab=users', NULL, 'hub:analytics:support:users', NULL, 1, 'M139', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7500);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7501, 13, '应用中心', 2, '/analytics/support?tab=apps', NULL, 'hub:analytics:support:apps', NULL, 2, 'M140', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7501);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7502, 13, '认证中心', 2, '/analytics/support?tab=auth', NULL, 'hub:analytics:support:auth', NULL, 3, 'M141', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7502);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7503, 13, '服务中心', 2, '/analytics/support?tab=services', NULL, 'hub:analytics:support:services', NULL, 4, 'M142', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7503);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7504, 13, '系统管理', 2, '/analytics/support?tab=config', NULL, 'hub:analytics:support:config', NULL, 5, 'M143', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7504);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7505, 13, '日志审计', 2, '/analytics/support?tab=audit', NULL, 'hub:analytics:support:audit', NULL, 6, 'M144', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7505);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7506, 13, '系统对接', 2, '/analytics/support?tab=integration', NULL, 'hub:analytics:support:integration', NULL, 7, 'M145', 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7506);

-- ========== H. 智能 BI (parent=14) ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7510, 14, '显示引擎', 2, '/analytics/bi?tab=display', NULL, 'hub:analytics:bi:display', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7510);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7511, 14, '组件引擎', 2, '/analytics/bi?tab=component', NULL, 'hub:analytics:bi:component', NULL, 2, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7511);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7512, 14, '地图管理', 2, '/analytics/bi?tab=map', NULL, 'hub:analytics:bi:map', NULL, 3, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7512);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7513, 14, '数据源管理', 2, '/analytics/bi?tab=datasource', NULL, 'hub:analytics:bi:datasource', NULL, 4, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7513);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7514, 14, '可视化设计', 2, '/analytics/bi?tab=design', NULL, 'hub:analytics:bi:design', NULL, 5, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7514);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7515, 14, '自助分析', 2, '/analytics/bi?tab=self', NULL, 'hub:analytics:bi:self', NULL, 6, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7515);

-- ========== I. 业务域 Hub（人口/法人/宏观/重点） ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7520, 15, '数据区设计', 2, '/analytics/population', NULL, 'hub:analytics:population:zones', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7520);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7521, 16, '数据区设计', 2, '/analytics/legal-entity', NULL, 'hub:analytics:legal:zones', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7521);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7522, 17, '指标与分析模型', 2, '/analytics/macro', NULL, 'hub:analytics:macro:designer', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7522);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7523, 18, '指标与分析模型', 2, '/analytics/key-domains', NULL, 'hub:analytics:key:designer', NULL, 1, NULL, 'hub', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7523);

-- ========== J. 统一用户管理 Hub（挂到 UUM 入口下） ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7600, m.id, '用户中心', 2, '/system/uum?tab=users', NULL, 'hub:system:uum:users', NULL, 1, 'M139', 'hub', 1
FROM sys_menu m
WHERE m.path = '/system/uum'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7600)
LIMIT 1;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7601, m.id, '应用中心', 2, '/system/uum?tab=apps', NULL, 'hub:system:uum:apps', NULL, 2, NULL, 'hub', 1
FROM sys_menu m
WHERE m.path = '/system/uum'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7601)
LIMIT 1;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7602, m.id, '认证中心', 2, '/system/uum?tab=auth', NULL, 'hub:system:uum:auth', NULL, 3, NULL, 'hub', 1
FROM sys_menu m
WHERE m.path = '/system/uum'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7602)
LIMIT 1;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7603, m.id, '服务中心', 2, '/system/uum?tab=services', NULL, 'hub:system:uum:services', NULL, 4, NULL, 'hub', 1
FROM sys_menu m
WHERE m.path = '/system/uum'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7603)
LIMIT 1;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7604, m.id, '系统管理', 2, '/system/uum?tab=config', NULL, 'hub:system:uum:config', NULL, 5, NULL, 'hub', 1
FROM sys_menu m
WHERE m.path = '/system/uum'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7604)
LIMIT 1;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7605, m.id, '日志审计', 2, '/system/uum?tab=audit', NULL, 'hub:system:uum:audit', NULL, 6, NULL, 'hub', 1
FROM sys_menu m
WHERE m.path = '/system/uum'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7605)
LIMIT 1;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7606, m.id, '系统对接', 2, '/system/uum?tab=integration', NULL, 'hub:system:uum:integration', NULL, 7, NULL, 'hub', 1
FROM sys_menu m
WHERE m.path = '/system/uum'
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7606)
LIMIT 1;

-- ========== K. Hub 节点不进外层门户侧栏（角色配置仍可见） ==========
UPDATE sys_menu SET visible = 0 WHERE integration_type = 'hub' AND id BETWEEN 7000 AND 7606;

-- ========== L. 赋权系统管理员 ==========
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id BETWEEN 7000 AND 7606
  AND m.status = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
