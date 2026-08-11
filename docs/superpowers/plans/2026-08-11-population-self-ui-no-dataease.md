# 人口域自研展示（收敛 DataEase）Implementation Plan

> **For agentic workers:** Execute task-by-task. Do **not** git commit unless user asks.

**Goal:** 人口域（`domain=population`）模型预览改为自研样例/结果表，取消 DataEase / BI 嵌入依赖。

**Architecture:** Hub 对人口域隐藏看板标识与签发嵌入；打开模型设计时拉取 `/analytics/models/{id}/samples` 展示表格。后端对人口模型拒绝 embed-token。法人域暂保留原 DataEase 路径。

**Tech Stack:** Vue3、Element Plus、Spring Boot

**Spec:** `docs/superpowers/specs/2026-08-11-population-bigdata-support-design.md` 分期 B

## Global Constraints

- 不提交 git
- 仅人口域去 DataEase；法人/宏观/智能 BI 不在本次
- 不伪造外部 BI 成功

---

### Task 1: 实施计划文档

- [x] 本文件

### Task 2: 后端拒绝人口 embed + 暴露样例

**Files:**
- `AnalyticsDomainService.java` — `issueModelEmbedById` / `issueModuleEmbed` 对 `population` 抛业务错；`listModelSamples`
- `AnalyticsDomainController.java` — `GET /models/{id}/samples`

- [x] 人口模型签发嵌入返回明确错误文案
- [x] 样例接口可用

### Task 3: 前端 Hub 自研结果页

**Files:**
- `AnalyticsDomainHubView.vue`

- [x] `isPopulation`：共享区文案、隐藏 DE 表单项/iframe/签发
- [x] 打开设计时加载样例表；保存时人口域不提交 deDashboardId

### Task 4: 规格/repair 标注 B 已开工

- [x] repair 摘要补一行
- [x] 规格缺口表更新

### Task 5: 自检

- [x] 后端编译 / lints
