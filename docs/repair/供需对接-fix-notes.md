# 供需对接系统修复说明（本地，未提交 Git）

对照 `docs/repair/供需对接.md`、`主业务流程.md`、`数据资源采集汇聚.md` 与清单中心关系图已落地：

## 1. 目录一致性
- 清单中心「目录清单」仅展示 `biz_catalog_item.gov_resource_id IS NOT NULL`（统一编目审批同步）。
- Flyway `V118`：删除无 `gov_resource_id` 的旧供需目录及关联 CATALOG 清单；清空失效引用。
- 供需侧 `createCatalog` / `publishCatalog` / `offlineCatalog` 已禁用；配置页改为只读统一目录。

## 2. 状态机
- 阶段：`MANAGE` → `PRE_AUDIT`（预审）→ `AUDIT`（审核）→ `SUPPLY`。
- 状态：`PRE_AUDITING`（兼容旧 `ANALYZING`）、`DISPATCHED`、`CORRECTION`（异议回流）等。
- 异议处理支持 `REOPEN_AUDIT`，将关联需求置为 `CORRECTION` 并进入审核。

## 3. 清单中心结构
- 三类：目录清单（6 子清单）/ 供需清单（6 子清单）/ 异议清单。
- API：`GET /exchange/supply/list-center?listType=catalog-published|sd-demand-audit|objection|...`

## 4. 预审匹配
- 资源目录匹配仅查已发布且带 `gov_resource_id` 的门户目录（与部门共享门户一致）。

## 联调
1. 重启后端跑 Flyway V118。
2. 在指标与目录 / 数据目录管理编目审批发布后，门户与清单中心「已发布目录」应一致。
3. 供需：填报 → 预审（分析/分发/退回）→ 审核（确认生成责任）→ 供给查看 → 清单中心；异议可「回流审核」。
