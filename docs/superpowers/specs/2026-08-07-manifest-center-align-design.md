# 清单中心对齐设计（方案一）

## 范围
按 `docs/repair/供需对接.md` 97–121 改造清单中心。异议改造**隔离**：只动异议表/API/清单 UI，不改变需求确认、分析、分发、挂载等状态机。

## 目录清单
- 发布/接入/已发布：同源门户已发布 `biz_catalog_item`
- 变更/更新：审批记录 + `gov_catalog_resource.version_no`，展示来源 INGEST/GOVERNANCE
- 下线：`OFFLINE` 统一编目目录

## 供需清单
- 需求审核：供数部门按 assigneeOrg；平台全量
- 供给审核：需求部门按 requesterOrg；平台全量
- 供需审核：并集（含 SUBMITTED）
- 历史授权：`biz_data_duty`；级联下行：`cascade_flag=1`

## 异议六态（独立）
DRAFT → SUBMITTED → APPROVED|REJECTED → PROCESSED → CLOSED  
旧：OPEN→SUBMITTED，PROCESSING→APPROVED，CLOSED 保持。  
不调用 `reopenDemandAuditFromObjection`（避免改需求状态）。
