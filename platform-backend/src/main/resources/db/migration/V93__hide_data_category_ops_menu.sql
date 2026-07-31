-- V93：数据分类入口从「平台运维」侧栏移除，改由归集「规范设计 · 汇聚数据分类」承载
UPDATE sys_menu SET status = 0 WHERE id = 6411 AND path = '/data-category';
