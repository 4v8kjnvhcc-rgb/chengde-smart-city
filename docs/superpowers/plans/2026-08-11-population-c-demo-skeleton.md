# 人口 C 演示骨架（双重授权 / 存储分区 / 批量台账 / 采集源清单）Implementation Plan

> **For agentic workers:** Execute task-by-task. Do **not** git commit unless user asks.

**Goal:** 补齐人口 Hub 剩余可演示项：内部区 M158、核心区 M157、采集区源清单、共享区 M160 批量台账。不接部门真源、不执行物理 ALTER、不新建授权引擎。

**Architecture:** Flyway V191 批量台账；Hub 各区仿 govern 加 Tab；双重授权深链复用 `/system/access` + M048；分区复用资源中心 pretest 只读摘要。

**Tech Stack:** Flyway、Spring Boot、Vue3

**Spec:** 人口规格分期 C（演示骨架）

## Global Constraints

- 不提交 git
- 不执行真实分区 DDL
- 不扩展生产级数据权引擎到 DWS（仅台账 + 深链 M048）
- 全程 LEDGER 诚实表述

---

### Task 1: V191 批量台账 + 采集源种子（可选静态）

- [x] `ana_pop_batch_ledger` + 种子

### Task 2: 后端批量台账 API + 分区摘要只读

- [x] batch-ledger CRUD/status；BATCH invoke 写台账；storage-summary

### Task 3: Hub 四区 Tab UI

- [x] collect 源清单 / core 存储分区 / internal 双重授权 / share 批量台账

### Task 4: 文档回写 + 编译

- [x] repair / 规格；后端 compile
