# 三模块 DS-only 定时 Implementation Plan

> **For agentic workers:** Execute task-by-task. Steps use checkbox syntax.

**Goal:** 汇聚/治理/融合定时编排仅用 DolphinScheduler；执行仅用 Kettle Carte；去掉 Spring 业务 Cron 扫表触发。

**Architecture:** 对齐 `IngestDsScheduleService`：启动=DS 上线，停止=DS 下线，到期 SHELL 回调平台再调 Carte。手动执行直调 Carte。

**Tech Stack:** Spring Boot、Flyway、DolphinScheduler REST、Vue3、Kettle Carte

**Spec:** `docs/superpowers/specs/2026-08-11-etl-ds-only-schedule-design.md`

## Global Constraints

- 手动执行不经 DS
- 禁止 Spring `@Scheduled` 按业务 Cron 触发三模块 ETL
- 不抽统一 TaskDsScheduleService
- 元数据采集等其它 Scheduled 不在本次

---

### Task 1: 文档口径

**Files:**
- Modify: `docs/repair/数据治理.md`
- Modify: `docs/repair/三模块中间件与数据源规划.md`
- Modify: `.cursor/plans/三模块中间件规划_e8d822b8.plan.md`

- [ ] 写明：Kettle=执行面；DS=定时编排主路径；手动直调 Carte；无 Spring 扫表触发

---

### Task 2: 汇聚去掉 Spring 业务触发

**Files:**
- Modify/Delete: `platform-backend/.../exchange/job/IngestTaskScheduler.java`
- Modify: `platform-backend/.../exchange/service/TableIngestEngine.java`（入口调用 stale 回收）

- [ ] 删除按 Cron 扫表触发逻辑
- [ ] stale RUNNING 在 run/start/stop/回调入口调用 `IngestTaskRecoveryRunner.resetStaleRunning`

---

### Task 3: 治理表 DS 字段 + GovernanceDsScheduleService

**Files:**
- Create: `db/migration/V189__gov_task_ds_schedule.sql`
- Modify: `GovGovernanceTask.java`
- Create: `GovernanceDsScheduleService.java`
- Modify: `GovernanceTaskController.java`、`GovernanceTaskService.java`
- Modify: `SecurityConfig.java`
- Delete: `GovernanceTaskScheduler.java`

- [ ] 字段：`ds_project_code/definition_code/schedule_id/instance_id`
- [ ] start/stop/ds-trigger；updateSchedule 启停联动 DS
- [ ] permitAll：`/api/v1/governance/gov-tasks/*/ds-trigger`

---

### Task 4: 前端定时对接

**Files:**
- Modify: `GovernanceTaskListView.vue`

- [ ] 保存定时：enabled→schedule/start；disabled→schedule/stop
- [ ] 列表展示调度上线状态（dsScheduleId）

---

### Task 5: 编译核对

- [ ] 后端编译通过；关键 API 路径齐全
