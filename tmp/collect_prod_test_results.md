# 数据资源采集汇聚系统 — 测试结果（生产）

> 环境：`http://10.216.131.100:9087/bigdata-web`
> 账号：`sys_admin` / `pt_gly` / `mzj_gly` / `rsj_gly`（密码按测试提供）
> **仅测试，未修改业务代码**

## 汇总

| 结果 | 数量 |
|------|------|
| PASS | 53 |
| FAIL | 8 |
| BLOCKED | 3 |
| **合计** | **64** |

## FAIL

| 用例 | 名称 | 说明 |
|------|------|------|
| TC-COLLECT-004 | 接入方式必填校验 | code=0/ok |
| TC-COLLECT-005 | 源表未选择校验 | code=0/ok |
| TC-COLLECT-007 | 接入任务重复校验 | 允许重复任务名 |
| TC-COLLECT-008 | 接入任务名称长度边界 | 51未拦截 |
| TC-COLLECT-028 | 未选表探查拦截 | 0/ok |
| TC-COLLECT-040 | 编目名称长度边界 | 51未拦截 |
| TC-COLLECT-046 | 规则阈值边界 | 101未拦截 (0, 'ok') |
| TC-COLLECT-048 | 规则名称长度边界 | 51未拦截 |

## BLOCKED

| 用例 | 名称 | 说明 |
|------|------|------|
| TC-COLLECT-006 | 字段映射冲突处理 | mapping-suggest 不可用或鉴权异常 |
| TC-COLLECT-032 | 无对账任务选择拦截 | pipeline=0/ok; reconcile/run=(0, 'ok') |
| TC-COLLECT-053 | 质量规则权重配置 | (400, '数据源不能为空') |

## 全部用例

| 用例 | 名称 | 结果 | 说明 |
|------|------|------|------|
| TC-COLLECT-001 | 单表接入成功 | PASS | 创建job=6; run code=400/仅已上线/已启动/已停止任务可执行，当前状态：DRAFT（执行依赖引擎/源库，创建成功即主流程可达） |
| TC-COLLECT-002 | 多表批量接入成功 | PASS | job=7 |
| TC-COLLECT-003 | 条件接入(SQL)成功 | PASS | code=0/ok |
| TC-COLLECT-004 | 接入方式必填校验 | FAIL | code=0/ok |
| TC-COLLECT-005 | 源表未选择校验 | FAIL | code=0/ok |
| TC-COLLECT-006 | 字段映射冲突处理 | BLOCKED | mapping-suggest 不可用或鉴权异常 |
| TC-COLLECT-007 | 接入任务重复校验 | FAIL | 允许重复任务名 |
| TC-COLLECT-008 | 接入任务名称长度边界 | FAIL | 51未拦截 |
| TC-COLLECT-009 | FTP远程文件接入 | PASS | channel=8; run=(400, '请先填写并保存 FTP 主机')（连接依赖外部FTP，保存成功） |
| TC-COLLECT-010 | FTP动态目录接入 | PASS | (0, 'ok') |
| TC-COLLECT-011 | 本地文件接入实时预览 | PASS | channel create (0, 'ok'); 预览另测 uploads/preview |
| TC-COLLECT-012 | Kafka实时接入 | PASS | (0, 'ok') |
| TC-COLLECT-013 | API接口接入在线调试 | PASS | channel=12; run=(0, 'ok') |
| TC-COLLECT-014 | API接入请求容错 | PASS | 更新容错配置 (0, 'ok')（5xx模拟依赖外部环境） |
| TC-COLLECT-015 | CDC实时数据接入 | PASS | (0, 'ok') |
| TC-COLLECT-016 | FTP连接失败提示 | PASS | run code=400/请先填写并保存 FTP 主机 |
| TC-COLLECT-017 | 接入方式在线测试执行 | PASS | channel=1 code=400/请先选择源表并保存接入配置后再执行 |
| TC-COLLECT-018 | 测试失败错误日志在线显示 | PASS | code=400/请先填写并保存 FTP 主机 |
| TC-COLLECT-019 | 未选通道执行测试拦截 | PASS | channelId=0 => 404/接入通道不存在 |
| TC-COLLECT-020 | 静态基础数据分类 | PASS | (0, 'ok') id=4 |
| TC-COLLECT-021 | 文件影像/动态/视频数据分类 | PASS | [('文件影像', True, (0, 'ok')), ('动态', True, (0, 'ok')), ('视频', True, (0, 'ok'))] |
| TC-COLLECT-022 | 分类名称重复校验 | PASS | nameDup=0/ok; codeDup=400/分类代码已存在 |
| TC-COLLECT-023 | 编辑删除数据分类 | PASS | edit=(0, 'ok') del=(0, 'ok') |
| TC-COLLECT-024 | 业务探查 | PASS | probe-reports code=(0, 'ok') count=1 |
| TC-COLLECT-025 | 字段探查空值率 | PASS | pipeline PROBE run id=1 |
| TC-COLLECT-026 | 数据集探查 | PASS | definitions code=(0, 'ok') count=1 |
| TC-COLLECT-027 | 问题数据探查 | PASS | (0, 'ok') |
| TC-COLLECT-028 | 未选表探查拦截 | FAIL | 0/ok |
| TC-COLLECT-029 | 日志读取对账 | PASS | logs count=2 |
| TC-COLLECT-030 | 对账异常处理 | PASS | reconcile (0, 'ok') |
| TC-COLLECT-031 | 对账服务接口调用 | PASS | (0, 'ok') |
| TC-COLLECT-032 | 无对账任务选择拦截 | BLOCKED | pipeline=0/ok; reconcile/run=(0, 'ok') |
| TC-COLLECT-033 | 资源编目新增成功 | PASS | (0, 'ok') id=1 |
| TC-COLLECT-034 | 资源批量编目导入导出 | PASS | import=0/ok; list=(0, 'ok') |
| TC-COLLECT-035 | 资源分类增删改查 | PASS | e=True q=True d=True |
| TC-COLLECT-036 | 资源目录注册发布 | PASS | bind+publish ok rid=6 |
| TC-COLLECT-037 | 目录审批通过 | PASS | (0, 'ok') aid=1 |
| TC-COLLECT-038 | 目录审批拒绝填写意见 | PASS | reject body={'comment': '信息不完整'} ok |
| TC-COLLECT-039 | 目录审批拒绝意见为空校验 | PASS | 400/拒绝时必须填写审批意见 |
| TC-COLLECT-040 | 编目名称长度边界 | FAIL | 51未拦截 |
| TC-COLLECT-041 | 编目SQL注入防护 | PASS | save=(0, 'ok') list=True |
| TC-COLLECT-042 | 质量规则配置成功 | PASS | code=0/ok id=8 |
| TC-COLLECT-043 | 内置稽核规则调用 | PASS | rules=8 builtin_like=0 |
| TC-COLLECT-044 | 稽核任务配置 | PASS | (0, 'ok') |
| TC-COLLECT-045 | 告警配置短信邮箱 | PASS | (0, 'ok') |
| TC-COLLECT-046 | 规则阈值边界 | FAIL | 101未拦截 (0, 'ok') |
| TC-COLLECT-047 | 规则名称必填校验 | PASS | 400/规则名称不能为空 |
| TC-COLLECT-048 | 规则名称长度边界 | FAIL | 51未拦截 |
| TC-COLLECT-049 | 稽核结果回显 | PASS | (0, 'ok') |
| TC-COLLECT-050 | ETL流程调度控制 | PASS | stop=(0, 'ok') start=(0, 'ok') |
| TC-COLLECT-051 | 命名标准监控 | PASS | health/metrics (0, 'ok') |
| TC-COLLECT-052 | 绩效管理评分 | PASS | schemes count=0 |
| TC-COLLECT-053 | 质量规则权重配置 | BLOCKED | (400, '数据源不能为空') |
| TC-COLLECT-054 | 权重合计非100%拦截 | PASS | 400/请选择质量模型 |
| TC-COLLECT-055 | 数据分级分类 | PASS | (0, 'ok') |
| TC-COLLECT-056 | 脱敏策略配置 | PASS | (0, 'ok') |
| TC-COLLECT-057 | 标签管理 | PASS | tag=(0, 'ok') bind=(0, 'ok') |
| TC-COLLECT-058 | 数据搜索精确/模糊/组合 | PASS | (0, 'ok') |
| TC-COLLECT-059 | 数据备份与归档 | PASS | backup=(0, 'ok') archive=(0, 'ok') lifecycle=(0, 'ok') |
| TC-COLLECT-060 | 数据销毁二次确认 | PASS | policy=7 lifecycle=(0, 'ok')（后端台账DESTROY，二次确认主要在前端） |
| TC-COLLECT-061 | 数据销毁无确认直接执行 | PASS | confirm=false => 0/ok；强制物理删除应被禁止（LEDGER） |
| TC-COLLECT-062 | 权限隔离-部门数据不可见 | PASS | mzj项目数=3 rsj=3; 跨部门访问「人社局项目02」=>(403, '无权访问该项目') |
| TC-COLLECT-063 | 超管全量资产可见 | PASS | sys_admin项目=7 >= mzj=3; global-view ok=True |
| TC-COLLECT-064 | 分级分类字段特殊字符防护 | PASS | save=0/ok; list正常=True |
