# 供需对接系统修复说明（本地）

对照 `docs/repair/供需对接.md` 与业务泳道图已落地：

## 1. 目录一致性
- 清单中心「目录清单」仅展示 `biz_catalog_item.gov_resource_id IS NOT NULL`（统一编目审批同步）。
- 供需侧 `createCatalog` / `publishCatalog` / `offlineCatalog` 已禁用；配置页改为只读统一目录。

## 2. 状态机（泳道对齐）
- 阶段：`MANAGE` → `PRE_AUDIT` → `AUDIT` → `SUPPLY`。
- 智能匹配分仅供参考；**是否分发 / 退回门户 / 退回补材料由平台管理员人工决定**（系统不按分数强制）。
- 管理员「退回门户申请」→ `RETURNED`（`returnKind=ADMIN_PORTAL`）。
- 管理员「分发到部门」→ `DISPATCHED`。
- 提供方不同意：`PROVIDER_RETURNED`（待管理员裁决）。
  - 管理员同意退回 → `RETURNED` / `MANAGE`
  - 管理员拒绝退回 → `DISPATCHED`（再确认）
- 提供方同意：`CONFIRMED` + `catalogMountDeadline`（按配置自然日，默认 10）；挂载后 `catalogMountedAt`，再办结 → `COMPLETED` / `SUPPLY`。
- 挂载超时：定时任务自动 `SUPERVISING`。
- 分发/督办/退回写入 `response_deadline`（确认/反馈时限，默认 10 自然日）。

## 3. API 增量
- `POST /demands/{id}/return-portal`
- `POST /demands/{id}/admin-agree-return`
- `POST /demands/{id}/admin-refuse-return`
- `POST /demands/{id}/mark-mounted`
- `GET/PUT /supervise-settings`（督查督办时限）
- Flyway `V152`：挂载截止/已挂载/退回类型字段
- Flyway `V153`：`biz_supply_setting` + `response_deadline`

## 3.1 清单中心（文档 1.1.1.3.1.5）
- 目录清单列：目录代码/名称/提供方/共享属性/状态/创建时间/操作；更新清单优先展示发布后有变更的目录。
- 供需清单列：需求场景、需求目录、需求服务、需求/提供单位、资源级别、状态；含 `PROVIDER_RETURNED`。
- 异议分态：`objection-apply|audit|process|closed|history|stats`；字段含对象、服务名称、提供/核查单位；支持转处理/办结/回流。
- `GET /list-center?listType=...` 返回 `columns` / `stats`（统计页）。

## 4. 预审匹配
- 分析必选组织机构；只在该组织 `providerOrg` 的已发布 `biz_catalog_item` 中打分。

## 联调
1. 重启后端跑 Flyway V152 / V153。
2. 供需配置 →「督查督办设置」查看/修改确认反馈时限与挂载时限（默认 10 自然日）。
3. 平台管理员人工判定：门户可满足则「退回门户申请」，需供数则「分发」；智能匹配分仅供参考。
4. 低/高匹配均可分发或退回，无系统强制。
5. 提供方同意 → 标记已挂载 → 办结 → 供给查看。
6. 可手工把 `catalog_mount_deadline` / `response_deadline` 改到过去验证超时督办。
