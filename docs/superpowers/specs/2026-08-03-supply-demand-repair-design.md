# 供需对接系统修复设计（方案 1）

日期：2026-08-03  
依据：`docs/repair/供需对接.md`、`主业务流程.md`、`数据资源采集汇聚.md`、清单中心关系图

## 决策

| 项 | 选择 |
|----|------|
| 实现路径 | 在现有 `SupplyDemandService` / `SupplyDemandView` 上演进 |
| 目录真源 | 仅 `gov_catalog_*` 审批通过后同步的 `biz_catalog_item`（`gov_resource_id IS NOT NULL`） |
| 旧目录 | 删除供需侧手工/种子目录（无 `gov_resource_id`） |
| 供需侧建目录 | 禁用 `createCatalog` / `publishCatalog` / `offlineCatalog` |
| 状态机 | 填报 → 预审(PRE_AUDIT) → 审核(AUDIT) → 供给(SUPPLY) → 清单监控；异议可回流审核 |

## 清单中心

- **目录清单（资源侧）**：发布/变更/下线/接入/更新/已发布 — 来自统一目录 + 审批/归集记录  
- **供需清单（需求侧）**：需求/供给/供需审核、历史授权、历史供需、级联下行  
- **异议清单（纠错侧）**：数据异议；处理时可回流到需求审核

## 角色视图

管理员全量；需求方/供数方按部门过滤（本轮 API 支持 `roleView` 参数，前端清单中心先按管理员全量展示，后续可接组织上下文）。
