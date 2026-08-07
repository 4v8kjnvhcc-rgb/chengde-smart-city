# 需求分析仅匹配门户目录 Implementation Plan

> **For agentic workers:** Execute task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 数据需求分析只按所选组织匹配该组织已发布到门户的 `biz_catalog_item`，不再查资产库表与服务总线。

**Architecture:** 改 `SupplyDemandService.analyzeDemand` / `searchResourceCandidates`：必选 `providerOrg`，过滤已发布目录并用需求 formPayload 加权打分；前端分析区去掉 TABLE/API，匹配前校验分发部门。

**Tech Stack:** Java Spring Boot、MyBatis-Plus、Vue 3

## Global Constraints

- 只读 `biz_catalog_item`（`publishStatus=PUBLISHED`）
- 组织按 `providerOrg` 名称字符串匹配（trim、忽略大小写）
- 不查 `IngDataTable` / `BizEsbFlow`（分析路径）
- 不改需求确认/分发状态机主流程

---

### Task 1: 后端分析与资源检索

**Files:**
- Modify: `platform-backend/.../SupplyDemandService.java`
- Modify: `platform-backend/.../SupplyDemandController.java`

- [ ] **Step 1:** `analyzeDemand` 接收 body 中的 `providerOrg`/`assigneeOrg`，解析组织，无组织则 400
- [ ] **Step 2:** 重写 `searchResourceCandidates`：仅门户目录 + 组织过滤 + 需求字段打分
- [ ] **Step 3:** `searchResources` 增加 `providerOrg`，去掉库表/接口扫描
- [ ] **Step 4:** Controller：analyze 接 body；resource-search 接 `providerOrg`

### Task 2: 前端分析区

**Files:**
- Modify: `platform-frontend/.../SupplyDemandView.vue`

- [ ] **Step 1:** analyze / 快查带上组织；未选则提示
- [ ] **Step 2:** 去掉库表/接口 UI；候选区仅目录；关系图只显示目录

### Task 3: 文档

**Files:**
- Modify: `docs/repair/供需对接.md`
- Modify: `docs/repair/供需对接-fix-notes.md`（可选追加）

- [ ] **Step 1:** 更新需求分析口径
