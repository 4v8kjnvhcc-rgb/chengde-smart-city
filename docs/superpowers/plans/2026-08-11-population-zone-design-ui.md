# 人口五区口径落地 Hub UI Implementation Plan

> **For agentic workers:** Execute task-by-task. Do **not** git commit unless user asks.

**Goal:** 把规格中的人口五区七维度、服务方式与更新/校核设计口径落到 `/analytics/population` Hub，使采集区等页面与规格一致、可演示。

**Architecture:** 前端按 `zoneCode` 切换人口域七维度文案与区告警；共享区「接口/批量」展开 M159/M160 设计卡；治理区补充 M155/M156 台账口径块。后端将人口模型样例维度按 M 码生成可读标签。不新建子系统、不做真源 DDL/校核引擎。

**Tech Stack:** Vue3、Element Plus、Spring Boot

**Spec:** `docs/superpowers/specs/2026-08-11-population-bigdata-support-design.md` 五区设计口径 / 服务方式 / 人员三大机制

## Global Constraints

- 不提交 git
- 仅改人口域展示与样例；法人/宏观保持原通用文案
- 不实现生产级校核引擎、主题库全量 DDL、真实批量前置
- 不伪造 DataEase / 外部调度成功

---

### Task 1: 实施计划文档

- [x] 本文件

### Task 2: Hub 人口五区七维度 + 区告警

**Files:**
- Modify: `platform-frontend/src/views/analytics/AnalyticsDomainHubView.vue`

- [x] 人口域按 zoneCode 使用规格七维度；法人等仍用通用 SEVEN_DIMS
- [x] 各区 alert 文案对齐：采集 ODS、治理 DWD、核心 DWS、内部双重授权、共享自研模型

### Task 3: 治理区 M155/M156 + 共享区 M159/M160 设计卡

**Files:**
- Modify: `AnalyticsDomainHubView.vue`

- [x] 治理区挂载表上方增加「更新维护 / 信息校核」设计口径卡片
- [x] 共享区「接口/批量」页改为规格服务方式说明 + 深链按钮

### Task 4: 人口模型样例维度按 M 码可读化

**Files:**
- Modify: `platform-backend/.../AnalysisDemoService.java`

- [x] `ensureSamples` 读取模型 mCode，按 M161～M174 设置 dim1/dim2 语义标签

### Task 5: 文档回写

**Files:**
- Modify: `docs/repair/人口大数据支撑系统.md`
- Modify: `docs/superpowers/specs/2026-08-11-population-bigdata-support-design.md` 缺口表

- [x] 标注五区口径已落地 Hub；B 余项仍为真源/校核引擎/生产接口

### Task 6: 自检

- [x] 前端无语法问题；后端 `AnalysisDemoService` 可编译路径自检
