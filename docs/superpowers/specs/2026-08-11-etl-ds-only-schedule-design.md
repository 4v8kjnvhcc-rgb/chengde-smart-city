# 三模块定时编排：仅 DolphinScheduler + Kettle Carte

## 背景与决策

汇聚 / 治理 / 融合的任务运行中间件口径调整为：

| 角色 | 组件 |
|------|------|
| 执行面 | **Kettle（Carte）** 唯一 |
| 编排/定时主路径 | **DolphinScheduler** 唯一 |
| 明确不用 | Spring `@Scheduled` 按业务 Cron **扫表触发** ETL |

已确认约束：

- 手动「执行」：**直调 Carte**（不经 DS）
- 定时「启动/停止」：DS 调度上线/下线；到期由 DS SHELL 回调平台，再调 Carte
- 方案选型：**A**（对齐汇聚 `IngestDsScheduleService`，不为三模块做大一统抽象）

## 目标心智模型

```
ExecCycleSelect → 业务表 schedule_cron
        ↓ 启动定时
DolphinScheduler（create/online schedule）
        ↓ 到期
HTTP 回调平台（token）
        ↓
Kettle Carte（抽数/KTR）
```

手动：UI「执行」→ 平台 API → Carte（跳过 DS）。

## 范围

**在范围内**

1. 文档：`docs/repair/数据治理.md`、`docs/repair/三模块中间件与数据源规划.md`、`.cursor/plans/三模块中间件规划_e8d822b8.plan.md`
2. 汇聚：去掉 `IngestTaskScheduler` 的业务 Cron 触发（及对未挂 DS 任务的 Spring 兜底）
3. 治理/融合：`gov_governance_task` 增加 DS 字段；新增 DS 调度服务与回调；删除 `GovernanceTaskScheduler`；前端定时启停对接 DS

**明确不在本次**

- 元数据采集 / 质量 / 存储策略等其它 `@Scheduled`（可后续单独改）
- 加工共享黄金路径已有的 `DsOrchestrationService.runKettleTrans`（保留，非日常任务定时主路径改造对象）
- 抽统一 `TaskDsScheduleService`（方案 B，不做）

## 汇聚（对齐与收口）

现状：已有 `IngestDsScheduleService`（启动/停止/ds-trigger）+ 并存 `IngestTaskScheduler`（对 `ds_schedule_id IS NULL` 的 Spring 触发）。

改动：

1. **删除或禁用** `IngestTaskScheduler.scanAndRunDueTasks` 中的业务触发逻辑；类若仅剩 stale 回收，则把回收挪到执行入口或删除该类。
2. 「启动」必须成功发布到 DS；DS 不可用时 **失败返回**（与现 `IngestDsScheduleService` 一致），不再静默回退 Spring。
3. 手动执行路径不变：直调 `TableIngestEngine` / Carte。

## 治理 / 融合

共用表 `gov_governance_task`（`task_domain=GOVERNANCE|FUSION`），共用列表组件 `GovernanceTaskListView`。

### 数据

Flyway（建议 `V189__gov_task_ds_schedule.sql`）：

- `ds_project_code BIGINT NULL`
- `ds_definition_code BIGINT NULL`
- `ds_schedule_id INT NULL`
- `ds_instance_id BIGINT NULL`（可选，回调写入）

实体 `GovGovernanceTask` 同步字段。

### 后端

新增 `GovernanceDsScheduleService`（镜像汇聚）：

- 项目名建议：`chengde_governance_etl`（治理与融合同项目，流程名带 domain/任务名/id）
- `startSchedule(task)`：校验 `schedule_cron` → 建 SHELL 流程 → `createAndOnlineSchedule` → 写 `ds_*`、`schedule_enabled=1`
- `stopSchedule(task)`：offline DS → `schedule_enabled=0`、清或保留 `ds_*`（与汇聚一致：保留 code，状态停用）
- `runFromDsCallback(taskId, token, dsInstanceId)`：鉴权 → `GovernanceTaskService.run(null, id)` → Carte
- 回调脚本：`POST {callback-base-url}/api/v1/governance/gov-tasks/{id}/ds-trigger`，头 `X-Ds-Callback-Token`

`GovernanceTaskController` 增加：

- `POST /{id}/schedule/start`、`POST /{id}/schedule/stop`（或与汇聚命名对齐的 start/stop）
- `POST /{id}/ds-trigger`（无登录、token 校验；permit 规则对齐汇聚）

`updateSchedule`（PUT schedule）：

- 仅保存 Cron/模式等配置；**真正上线**走 start
- 若已启用且改 Cron：先 offline 再按新 Cron online（或要求用户先停后启——实现时与汇聚行为对齐）

删除：`GovernanceTaskScheduler.java`（业务扫表触发）。

### 前端

`GovernanceTaskListView`「任务定时」：

- 启用定时 / 启动：调用 DS start（需已有 Cron；`ExecCycleSelect` 不变）
- 停止：调用 DS stop
- 列表展示：可显示「调度已上线」类状态（有 `dsScheduleId` 且 enabled）
- 手动「运行」仍走现有 `run` API → Carte

### SIMPLE 调度模式

若仍保留「简单间隔」UI：启动时在平台侧换算/生成 DS 可接受的 Cron，或 **本期仅支持 CRON + ExecCycleSelect**（推荐：启动时若为 SIMPLE 则拒绝或自动转 Cron）。实现默认：**DS 启动仅接受已解析的 Cron 字符串**；SIMPLE 在保存时换算为 Cron 再交给 DS（若现有 `computeNextSimple` 可逆性不足，则提示改用执行周期）。

## 超时 RUNNING / 状态同步

- **禁止**用 Spring 按业务 Cron 触发跑数。
- Carte 运行态同步：可保留 `KettleExecuteService` 内已有状态轮询（属执行面状态回收，不是编排面）；若与「禁用一切 @Scheduled」冲突，则改为：在 run/stop/回调/监控查询时惰性 reconcile。
- 汇聚 stale RUNNING：从 `IngestTaskScheduler` 迁出，挂到 run/start/stop/回调入口。

## 文档口径（落地文案）

三模块：

- Kettle Carte = 执行面
- DolphinScheduler = 调度/编排面（定时主路径）
- 手动执行 = 直调 Carte
- 不再写「Spring `@Scheduled` 日常主路径」

## 验收

1. 汇聚：仅 DS 上线的任务会按时跑；关掉 Spring 后无 DS 的旧任务不再被扫跑。
2. 治理/融合：启停定时后 DS 可见对应 schedule；到期回调产生 run 记录且 Carte 执行。
3. 手动执行不创建 DS 临时流程。
4. DS 宕机时「启动定时」失败有明确错误；手动执行仍只依赖 Carte。
5. repair/plan 文档与上述一致。

## 主要触点文件

- `IngestDsScheduleService.java` / `IngestTaskScheduler.java` / `IngestionPlatformController.java`
- `GovernanceDsScheduleService.java`（新）/ `GovernanceTaskService.java` / `GovernanceTaskController.java` / `GovGovernanceTask.java`
- `GovernanceTaskListView.vue`
- Flyway `V183__...`
- `docs/repair/数据治理.md`、`docs/repair/三模块中间件与数据源规划.md`、plan
