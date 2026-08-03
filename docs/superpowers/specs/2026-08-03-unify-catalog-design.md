# 统一资源目录（归集指标目录 + 治理数据目录 + 部门门户）

日期：2026-08-03  
依据：`docs/repair/数据资源采集汇聚.md`

## 决策

| 项 | 选择 |
|----|------|
| 统一底座表 | `gov_catalog_resource` / `gov_catalog_category` / `gov_catalog_approval` 等 |
| 来源字段 | `catalog_origin`：`INGEST`（指标与目录体系构建）/ `GOVERNANCE`（数据目录管理系统） |
| 门户展示 | 审批通过后同步 `biz_catalog_item`（`publish_status=PUBLISHED`）；部门门户只读已发布 |
| 字典 | 目录枚举写入 `sys_dict` / `sys_dict_item` |
| UI | 双入口共用治理侧最全组件；汇聚入口强制 `catalogOrigin=INGEST`；订阅/分发仅在治理「资源目录门户」 |

## 流程

编目 → 分类（含涉密）→ 注册发布 → 审批 → 门户发布 → 部门数据共享门户申请 → 资源目录门户订阅/审批/分发

## 边界

- 部门数据共享门户：只负责目录申请（检索+申请）
- 资源目录门户：订阅、审批、分发闭环
