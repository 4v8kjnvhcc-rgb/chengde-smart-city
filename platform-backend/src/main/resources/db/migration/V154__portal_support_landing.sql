-- V154: 通用支撑 / 业务支撑 子平台点击进入落地选卡页（对齐大数据归集平台）

UPDATE portal_nav_node
SET url = '/analytics/general-support',
    menu_path = '/analytics/general-support',
    remark = '落地选卡：统一用户 / 智能BI / 任务管理 / 集成运维'
WHERE id = 31 AND node_type = 'sub_platform';

UPDATE portal_nav_node
SET url = '/analytics/business-support',
    menu_path = '/analytics/business-support',
    remark = '落地选卡：人口 / 法人 / 宏观 / 重点领域'
WHERE id = 32 AND node_type = 'sub_platform';

-- 统一用户系统入口对齐 Hub
UPDATE portal_nav_node
SET url = '/analytics/support',
    menu_path = '/analytics/support'
WHERE id = 311 AND node_type = 'system'
  AND (url IS NULL OR url = '' OR url LIKE '/system/uum%');
