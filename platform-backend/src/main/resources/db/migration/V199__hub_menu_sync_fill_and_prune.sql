-- V199: 按各 Hub 实际侧栏补齐 sys_menu，停用页面已移除项，修复 7726 菜单 id 冲突
-- 号段 7800～7869；integration_type=hub；visible=0（仅角色「配置菜单」可见，不撑门户侧栏）

-- ========== A. 停用：Hub 已移除 / 重复 / 废弃 ==========
UPDATE sys_menu SET status = 0
WHERE id IN (7221, 7223, 7226)
   OR permission IN (
        'hub:gov:quality:standards',
        'hub:gov:quality:task-mgmt',
        'hub:gov:quality:reports'
      );

UPDATE sys_menu SET status = 0
WHERE id = 7125
   OR permission = 'hub:governance:catalog:classify';

UPDATE sys_menu SET status = 0
WHERE id = 7604
   OR permission = 'hub:system:uum:config';

UPDATE sys_menu SET status = 0
WHERE id = 7713
   OR permission = 'hub:analytics:support:apps:links';

UPDATE sys_menu SET status = 0
WHERE id = 7015
   OR permission = 'hub:ingestion:register:menu-mgmt';

UPDATE sys_menu SET status = 0
WHERE id BETWEEN 7100 AND 7111
  AND status = 1;

-- ========== B. 修复 7726：固定为「内置属性」；执行周期改 7800 ==========
UPDATE sys_menu
SET menu_name = '内置属性管理',
    parent_id = 7504,
    menu_type = 2,
    path = '/analytics/support?tab=sys.builtin',
    component = NULL,
    permission = 'hub:analytics:support:sys:builtin',
    sort_order = 5,
    integration_type = 'hub',
    status = 1,
    visible = 0
WHERE id = 7726
  AND (
    permission IN ('hub:analytics:support:sys:cron', 'hub:analytics:support:sys:builtin')
    OR path IN ('/analytics/support?tab=sys.cfg.cron', '/analytics/support?tab=sys.builtin')
    OR menu_name IN ('执行周期管理', '内置属性管理')
  );

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7726, 7504, '内置属性管理', 2, '/analytics/support?tab=sys.builtin', NULL,
       'hub:analytics:support:sys:builtin', NULL, 5, NULL, 'hub', 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu WHERE id = 7726 OR permission = 'hub:analytics:support:sys:builtin'
);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7800, cfg.id, '执行周期管理', 2, '/analytics/support?tab=sys.cfg.cron', NULL,
       'hub:analytics:support:sys:cron', NULL, 3, NULL, 'hub', 1, 0
FROM (SELECT id FROM sys_menu WHERE id = 7722 OR (parent_id = 7504 AND menu_name = '系统配置') ORDER BY id LIMIT 1) cfg
WHERE cfg.id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM sys_menu
    WHERE id = 7800
       OR permission = 'hub:analytics:support:sys:cron'
       OR path = '/analytics/support?tab=sys.cfg.cron'
  );

-- ========== C. 元数据：数据源分类 / 数据源管理 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7801, 7200, '数据源分类', 2, '/governance?tab=metadata&section=category', NULL,
       'hub:gov:metadata:category', NULL, 0, 'M087', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7801 OR permission = 'hub:gov:metadata:category');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7802, 7200, '数据源管理', 2, '/governance?tab=metadata&section=source', NULL,
       'hub:gov:metadata:source', NULL, 1, 'M088', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7802 OR permission = 'hub:gov:metadata:source');

UPDATE sys_menu SET sort_order = 2 WHERE id = 7201 AND permission = 'hub:gov:metadata:model';
UPDATE sys_menu SET sort_order = 3 WHERE id = 7202 AND permission = 'hub:gov:metadata:collect';
UPDATE sys_menu SET sort_order = 4 WHERE id = 7203 AND permission = 'hub:gov:metadata:monitor';
UPDATE sys_menu SET sort_order = 5 WHERE id = 7204 AND permission = 'hub:gov:metadata:maintain';
UPDATE sys_menu SET sort_order = 6 WHERE id = 7205 AND permission = 'hub:gov:metadata:version';
UPDATE sys_menu SET sort_order = 7 WHERE id = 7206 AND permission = 'hub:gov:metadata:catalog';
UPDATE sys_menu SET sort_order = 8 WHERE id = 7207 AND permission = 'hub:gov:metadata:analyze';

-- ========== D. 融合：工作流定时改名 + 新增工作流调度 ==========
UPDATE sys_menu
SET menu_name = '工作流定时',
    path = '/governance?tab=model&mSub=schedule',
    sort_order = 3
WHERE id = 7235 OR permission = 'hub:gov:fusion:schedule';

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7803, 7232, '工作流调度', 2, '/governance?tab=model&mSub=workflow', NULL,
       'hub:gov:fusion:workflow', NULL, 4, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7803 OR permission = 'hub:gov:fusion:workflow');

UPDATE sys_menu SET sort_order = 5 WHERE id = 7236 OR permission = 'hub:gov:fusion:execute';
UPDATE sys_menu SET sort_order = 6 WHERE id = 7237 OR permission = 'hub:gov:fusion:version';

-- ========== E. 汇聚接入叶子 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7810, 7021, '结构化数据接入', 2, '/exchange/ingestion?system=collect&module=ingest.structured', NULL,
       'hub:ingestion:collect:ingest:structured', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7810 OR permission = 'hub:ingestion:collect:ingest:structured');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7811, 7021, '非结构化数据接入', 2, '/exchange/ingestion?system=collect&module=ingest.unstruct', NULL,
       'hub:ingestion:collect:ingest:unstruct', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7811 OR permission = 'hub:ingestion:collect:ingest:unstruct');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7812, 7021, '半结构化数据接入', 2, '/exchange/ingestion?system=collect&module=ingest.semi', NULL,
       'hub:ingestion:collect:ingest:semi', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7812 OR permission = 'hub:ingestion:collect:ingest:semi');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7813, 7021, 'API 接口数据接入', 2, '/exchange/ingestion?system=collect&module=ingest.api', NULL,
       'hub:ingestion:collect:ingest:api', NULL, 4, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7813 OR permission = 'hub:ingestion:collect:ingest:api');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7814, 7021, 'CDC 实时数据接入', 2, '/exchange/ingestion?system=collect&module=ingest.cdc', NULL,
       'hub:ingestion:collect:ingest:cdc', NULL, 5, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7814 OR permission = 'hub:ingestion:collect:ingest:cdc');

-- ========== F. 汇聚质量叶子 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7820, 7024, '质量规则配置', 2, '/exchange/ingestion?system=collect&module=quality.rule-config', NULL,
       'hub:ingestion:collect:quality:rule-config', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7820 OR permission = 'hub:ingestion:collect:quality:rule-config');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7821, 7024, '数据质量监控', 2, '/exchange/ingestion?system=collect&module=quality.monitor', NULL,
       'hub:ingestion:collect:quality:monitor', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7821 OR permission = 'hub:ingestion:collect:quality:monitor');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7822, 7024, '数据质量评估', 2, '/exchange/ingestion?system=collect&module=quality.assess', NULL,
       'hub:ingestion:collect:quality:assess', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7822 OR permission = 'hub:ingestion:collect:quality:assess');

-- ========== G. 汇聚资产叶子 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7830, 7025, '数据分级分类', 2, '/exchange/ingestion?system=collect&module=asset.classify', NULL,
       'hub:ingestion:collect:asset:classify', NULL, 1, 'M069', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7830 OR permission = 'hub:ingestion:collect:asset:classify');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7831, 7025, '数据脱敏策略', 2, '/exchange/ingestion?system=collect&module=asset.mask', NULL,
       'hub:ingestion:collect:asset:mask', NULL, 2, 'M070', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7831 OR permission = 'hub:ingestion:collect:asset:mask');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7832, 7025, '数据标签管理', 2, '/exchange/ingestion?system=collect&module=asset.tag', NULL,
       'hub:ingestion:collect:asset:tag', NULL, 3, 'M071', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7832 OR permission = 'hub:ingestion:collect:asset:tag');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7833, 7025, '数据搜索', 2, '/exchange/ingestion?system=collect&module=asset.search', NULL,
       'hub:ingestion:collect:asset:search', NULL, 4, 'M072', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7833 OR permission = 'hub:ingestion:collect:asset:search');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7834, 7025, '数据备份', 2, '/exchange/ingestion?system=collect&module=asset.backup', NULL,
       'hub:ingestion:collect:asset:backup', NULL, 5, 'M073', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7834 OR permission = 'hub:ingestion:collect:asset:backup');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7835, 7025, '数据归档', 2, '/exchange/ingestion?system=collect&module=asset.archive', NULL,
       'hub:ingestion:collect:asset:archive', NULL, 6, 'M074', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7835 OR permission = 'hub:ingestion:collect:asset:archive');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7836, 7025, '数据销毁', 2, '/exchange/ingestion?system=collect&module=asset.destroy', NULL,
       'hub:ingestion:collect:asset:destroy', NULL, 7, 'M075', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7836 OR permission = 'hub:ingestion:collect:asset:destroy');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7837, 7025, '全局数据资产视图', 2, '/exchange/ingestion?system=collect&module=asset.global', NULL,
       'hub:ingestion:collect:asset:global', NULL, 8, 'M076', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7837 OR permission = 'hub:ingestion:collect:asset:global');

-- ========== H. 部门数据共享门户 Tab ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7840, 7120, '首页', 2, '/exchange/analysis-portal/dept?section=home', NULL,
       'hub:analysis:dept:home', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7840 OR permission = 'hub:analysis:dept:home');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7841, 7120, '政务共享资源', 2, '/exchange/analysis-portal/dept?section=catalog', NULL,
       'hub:analysis:dept:catalog', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7841 OR permission = 'hub:analysis:dept:catalog');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7842, 7120, '基础资源目录', 2, '/exchange/analysis-portal/dept?section=subscribe', NULL,
       'hub:analysis:dept:subscribe', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7842 OR permission = 'hub:analysis:dept:subscribe');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7843, 7120, '个人空间', 2, '/exchange/analysis-portal/dept?section=myspace', NULL,
       'hub:analysis:dept:myspace', NULL, 4, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7843 OR permission = 'hub:analysis:dept:myspace');

-- ========== I. 供需对接侧栏 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7850, 7130, '首页', 2, '/exchange/application?app=supply&section=home', NULL,
       'hub:application:supply:home', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7850 OR permission = 'hub:application:supply:home');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7851, 7130, '数据需求管理', 2, '/exchange/application?app=supply&section=demand', NULL,
       'hub:application:supply:demand', NULL, 2, 'M020', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7851 OR permission = 'hub:application:supply:demand');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7852, 7130, '数据需求分析', 2, '/exchange/application?app=supply&section=analysis', NULL,
       'hub:application:supply:analysis', NULL, 3, 'M021', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7852 OR permission = 'hub:application:supply:analysis');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7853, 7130, '数据需求确认', 2, '/exchange/application?app=supply&section=confirm', NULL,
       'hub:application:supply:confirm', NULL, 4, 'M022', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7853 OR permission = 'hub:application:supply:confirm');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7854, 7130, '数据供给查看', 2, '/exchange/application?app=supply&section=supply', NULL,
       'hub:application:supply:supply', NULL, 5, 'M023', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7854 OR permission = 'hub:application:supply:supply');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7855, 7130, '业务督办', 2, '/exchange/application?app=supply&section=supervise', NULL,
       'hub:application:supply:supervise', NULL, 6, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7855 OR permission = 'hub:application:supply:supervise');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7856, 7130, '清单中心', 2, '/exchange/application?app=supply&section=manifest-center', NULL,
       'hub:application:supply:manifest', NULL, 7, 'M024', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7856 OR permission = 'hub:application:supply:manifest');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7857, 7130, '供需配置', 2, '/exchange/application?app=supply&section=supply-config', NULL,
       'hub:application:supply:config', NULL, 8, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7857 OR permission = 'hub:application:supply:config');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7858, 7130, '事项管理', 2, '/exchange/application?app=supply&section=matter-manage', NULL,
       'hub:application:supply:matter', NULL, 9, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7858 OR permission = 'hub:application:supply:matter');

-- ========== J. 人口 / 法人五区叶子 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7860, 7520, '人口数据采集区设计', 2, '/analytics/population?tab=zone.collect', NULL,
       'hub:analytics:population:zone.collect', NULL, 1, 'M152', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7860 OR permission = 'hub:analytics:population:zone.collect');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7861, 7520, '人口数据治理及反馈区设计', 2, '/analytics/population?tab=zone.govern', NULL,
       'hub:analytics:population:zone.govern', NULL, 2, 'M153', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7861 OR permission = 'hub:analytics:population:zone.govern');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7862, 7520, '人口核心数据区设计', 2, '/analytics/population?tab=zone.core', NULL,
       'hub:analytics:population:zone.core', NULL, 3, 'M157', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7862 OR permission = 'hub:analytics:population:zone.core');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7863, 7520, '人口数据内部服务区设计', 2, '/analytics/population?tab=zone.internal', NULL,
       'hub:analytics:population:zone.internal', NULL, 4, 'M158', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7863 OR permission = 'hub:analytics:population:zone.internal');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7864, 7520, '人口数据共享服务区设计', 2, '/analytics/population?tab=zone.share', NULL,
       'hub:analytics:population:zone.share', NULL, 5, 'M154', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7864 OR permission = 'hub:analytics:population:zone.share');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7865, 7521, '法人数据采集区设计', 2, '/analytics/legal-entity?tab=zone.collect', NULL,
       'hub:analytics:legal:zone.collect', NULL, 1, 'M175', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7865 OR permission = 'hub:analytics:legal:zone.collect');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7866, 7521, '法人数据治理及反馈区设计', 2, '/analytics/legal-entity?tab=zone.govern', NULL,
       'hub:analytics:legal:zone.govern', NULL, 2, 'M176', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7866 OR permission = 'hub:analytics:legal:zone.govern');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7867, 7521, '法人核心数据区设计', 2, '/analytics/legal-entity?tab=zone.core', NULL,
       'hub:analytics:legal:zone.core', NULL, 3, 'M180', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7867 OR permission = 'hub:analytics:legal:zone.core');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7868, 7521, '法人数据内部服务区设计', 2, '/analytics/legal-entity?tab=zone.internal', NULL,
       'hub:analytics:legal:zone.internal', NULL, 4, 'M181', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7868 OR permission = 'hub:analytics:legal:zone.internal');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7869, 7521, '法人数据共享服务区设计', 2, '/analytics/legal-entity?tab=zone.share', NULL,
       'hub:analytics:legal:zone.share', NULL, 5, 'M177', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7869 OR permission = 'hub:analytics:legal:zone.share');

-- ========== K. Hub 节点不进外层门户侧栏 ==========
UPDATE sys_menu SET visible = 0
WHERE id BETWEEN 7800 AND 7869
  AND integration_type = 'hub';

-- ========== L. 赋权系统管理员（role_id=1） ==========
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.status = 1
  AND (
    m.id BETWEEN 7800 AND 7869
    OR m.id = 7726
    OR m.permission IN (
      'hub:analytics:support:sys:builtin',
      'hub:analytics:support:sys:cron',
      'hub:gov:metadata:category',
      'hub:gov:metadata:source',
      'hub:gov:fusion:workflow',
      'hub:gov:fusion:schedule'
    )
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );

-- 清理已停用菜单上的角色勾选，避免配置树半勾脏状态
DELETE rm FROM sys_role_menu rm
INNER JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.status = 0
  AND (
    m.id IN (7221, 7223, 7226, 7125, 7604, 7713, 7015)
    OR m.id BETWEEN 7100 AND 7111
    OR m.permission IN (
      'hub:gov:quality:standards',
      'hub:gov:quality:task-mgmt',
      'hub:gov:quality:reports',
      'hub:governance:catalog:classify',
      'hub:system:uum:config',
      'hub:analytics:support:apps:links',
      'hub:ingestion:register:menu-mgmt'
    )
  );
