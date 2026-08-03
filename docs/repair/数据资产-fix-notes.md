# 数据资产登记管理系统 — 按 sjzc.md 修复说明（本地，未提交 Git）

对照 `docs/repair/sjzc.md` 已落地要点：

## 1. 角色与菜单
- Flyway `V111__register_workflow_sjzc.sql`：为 `DEPT_ADMIN` / `PLATFORM_ADMIN` / `SYSTEM_ADMIN` 分配登记侧权限（含 submit/audit、部门报告节点）。
- 侧栏仍按 `hub:ingestion:register:*` 过滤；部门可用 `m046:dept` / `m047:dept` 打开报告/图谱。

## 2. 审核流
- 状态：`DRAFT` 草稿 → `PENDING_REVIEW` 待审核 → `APPROVED` 审核通过 / `REJECTED` 驳回待提交。
- 表字段：`register_status` / `reject_reason`（项目、系统、数据源、字典、标签）；资产目录 `status` 对齐。
- API：`/register/workflow/submit|approve|reject` + `/logs`。
- 前端样板：项目列表、数据字典（查看/提交/审核/驳回 + 审核记录）。

## 3. 字典关联数据项
- 表 `ing_dict_column_link`；API 关联/取消关联；字典页「关联」级联选项目→系统→库→表→项。

## 4. 删除与只读
- 删项目：有系统则拒绝。
- 删系统：有库则拒绝（原已有）。
- 删数据库：有表则拒绝（不再级联）。
- 数据表：禁止正向手工建表；保留源库「登记」。
- 数据项：API/页面临时禁增改，仅查看。

## 5. 菜单双入口
- 仍共用 `sys_menu`；登记 `register-scope` 仅 7000 子树；页内已加说明。

## 联调
1. 重启后端跑 Flyway V111。
2. 用 `sys_admin` / 部门管理员 / 平台管理员分别验证侧栏与操作列。
3. 系统/库/标签等实体可复用同一 workflow API（objectType=`SYSTEM`/`DATA_SOURCE`/`TAG`/`CATALOG_REG`），页面可按项目页模式继续铺开。
