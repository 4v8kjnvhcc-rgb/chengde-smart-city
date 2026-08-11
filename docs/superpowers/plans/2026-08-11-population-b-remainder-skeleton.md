# 人口 B 余项骨架（主题表 / 校核台账 / 接口契约）Implementation Plan

> **For agentic workers:** Execute task-by-task. Do **not** git commit unless user asks.

**Goal:** 落地规格 B 余项骨架：人口 DWS 主题表+指标、M155/M156 校核台账 CRUD、M159/M160 服务契约与 mock invoke；Hub 可演示。不做生产双授权引擎与真源批量前置（C）。

**Architecture:** Flyway V190 建表与种子；`AnalyticsDomainService` 增校核台账与服务契约；Hub 治理区/共享区加 Tab。全程 `LEDGER` 语义。

**Tech Stack:** Flyway、MyBatis-Plus、Spring Boot、Vue3

**Spec:** `docs/superpowers/specs/2026-08-11-population-bigdata-support-design.md` 缺口 B

## Global Constraints

- 不提交 git
- 不接真 ESB / 不跑质量规则引擎
- 不实现双重授权数据权引擎（C）
- 人口域保持无 DataEase

---

### Task 1: Flyway V190

- [x] `dws_population_base` / `ads_pop_district_summary` + 种子
- [x] `THEME_POPULATION` + `rc_managed_table` + 分区 LEDGER
- [x] `ana_pop_verify_ledger` / `ana_pop_service_contract` + 种子
- [x] 指标改指向 `dws_population_base`；核心区挂载种子

### Task 2: 后端实体 / Mapper / Service / Controller

- [x] 校核台账 list/create/updateStatus
- [x] 服务契约 list + mock invoke

### Task 3: Hub UI

- [x] 治理区 tabs：挂载 / 机制 / 校核台账
- [x] 共享区接口/批量：契约列表 + 试调

### Task 4: 文档回写 + 编译自检

- [x] repair / 规格缺口表更新；后端 compile 通过
