# OpenMetadata 菜单名映射表（Mxxx → 门户 / OM 1.12）

| 属性 | 说明 |
|------|------|
| **文档编号** | **D10** |
| **文档版本** | V1.0 |
| **编制日期** | 2026-06-23 |
| **文档性质** | MS2/MS3 OpenMetadata 门户集成**菜单名与路由契约** |
| **模块范围** | **29 条**：OM 28 模块 + M098（DS+OM） |
| **上位基线** | [`D02-需求基线说明.md`](D02-需求基线说明.md) V2.2 |
| **配套文档** | [`D05-系统功能清单.md`](D05-系统功能清单.md) V2.6、[`D06-Mxxx实现映射表.md`](D06-Mxxx实现映射表.md) V1.0、[`D07-总体技术架构设计.md`](D07-总体技术架构设计.md) V1.1、[`D04-开源框架选型评估.md`](D04-开源框架选型评估.md)、[`D00-文档索引与编号规范.md`](D00-文档索引与编号规范.md) |

---

## 一、编制说明

| 项 | 说明 |
|----|------|
| **核心要求** | 甲方在统一门户看到的菜单名与 V3.0 投标文件功能模块名 **1:1 对齐**；不得暴露 OM 原生英文 UI |
| **代理入口** | Nginx `location /om/` → OpenMetadata `http://vm1:8585`（D07 附录 E） |
| **集成模式** | `纯代理`：OM UI/API 经门户壳加载；`代理+自研扩展`：OM 能力 + 自研审批/机构/订阅等 |
| **不在本表** | M081/M084～M085/M102～M105（自研标准）、M099～M101/M106～M111（Kettle/DS/自研）；见 §六 |
| **实测路径** | OM 1.12 UI path 以 MS2 POC 部署后截图为准；本表为设计基准 |

---

## 二、门户侧菜单树（2.1 数据融合治理）

挂载路径：`2 主数据平台 > 2.1 大数据融合治理平台`（D07 §5.6）

```
2.1 大数据融合治理平台
├── 2.1.1 元数据管理
│   ├── 适配器管理 … 字典管理（M086～M097）
├── 2.1.2 数据质量中心
│   ├── 质量规则配置 … 数据质量分析报告（M078～M080、M082～M083）
├── 2.1.3 数据目录管理
│   ├── 目录分类 … 资源订阅分发（M112～M122）
└── 2.1.4 治理任务
    └── 治理任务管理（M098）
```

---

## 三、映射表列说明

| 列名 | 说明 |
|------|------|
| M | 验收模块编号 |
| 功能模块 | D05 模块名 |
| 门户父菜单 | 二级菜单块（元数据/质量/目录/治理任务） |
| 门户菜单名 | 甲方可见叶子菜单名（= 功能模块名） |
| 门户路由 | `platform-web` 路由 |
| OM 原生入口 | OpenMetadata 1.12 侧栏/页面 |
| OM 路由/API | UI path 或 REST 前缀（部署后核对） |
| 集成方式 | 纯代理 / 代理+自研扩展 |

---

## 四、逐条映射表

### 4.1 数据质量中心（M078～M080、M082～M083）

| M | 功能模块 | 门户父菜单 | 门户菜单名 | 门户路由 | OM 原生入口 | OM 路由/API | 集成方式 | MS | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M078 | 质量规则配置 | 数据质量中心 | 质量规则配置 | /governance/quality/rules | Quality > Test Definitions / Table DQ Tab | /data-quality /tables/{fqn}/profiler | 代理+自研扩展 | MS3 | 8 类稽核规则中文化；机构维度扩展 |
| M079 | 数据质量任务配置 | 数据质量中心 | 数据质量任务配置 | /governance/quality/tasks | Quality > Test Suites > Schedule | /data-quality/test-suites | 代理+自研扩展 | MS3 | 启停/告警联动自研工单 |
| M080 | 数据质量监控 | 数据质量中心 | 数据质量监控 | /governance/quality/monitor | Quality > By Test Suites | /data-quality | 代理+自研扩展 | MS3 | 工单跟踪；血统分析扩展 |
| M082 | 数据质量评估 | 数据质量中心 | 数据质量评估 | /governance/quality/assessment | Quality > Test Results / Insights | /data-quality/test-case-results | 代理+自研扩展 | MS3 | 六性指标绩效扩展 |
| M083 | 数据质量分析报告 | 数据质量中心 | 数据质量分析报告 | /governance/quality/reports | Quality + 自研报表 | /api/v1/governance/quality/reports | 代理+自研扩展 | MS3 | 多维度下钻导出；OM 结果 + 自研报表壳 |

### 4.2 元数据管理（M086～M097）

| M | 功能模块 | 门户父菜单 | 门户菜单名 | 门户路由 | OM 原生入口 | OM 路由/API | 集成方式 | MS | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M086 | 适配器管理 | 元数据管理 | 适配器管理 | /governance/metadata/connectors | Settings > Connectors / Ingestion | /settings/integrations | 纯代理 | MS3 | 内置与扩展连接器 |
| M087 | 数据源分类管理 | 元数据管理 | 数据源分类管理 | /governance/metadata/domains | Settings > Domains / Classifications | /settings/domains | 纯代理 | MS3 | 类型与业务域映射 OM Domains |
| M088 | 数据源管理 | 元数据管理 | 数据源管理 | /governance/metadata/services | Settings > Services > Databases | /settings/services/database | 纯代理 | MS3 | 连接与生命周期 |
| M089 | 元模型管理 | 元数据管理 | 元模型管理 | /governance/metadata/types | Settings > Custom Properties / Types | /settings/customProperties | 纯代理 | MS3 | 元模型发布导入导出 |
| M090 | 元数据采集 | 元数据管理 | 元数据采集 | /governance/metadata/ingestion | Settings > Services > Ingestion | /settings/services/ingestion | 纯代理 | MS3 | 整库/选表；定时/增量采集 |
| M091 | 元数据采集监控 | 元数据管理 | 元数据采集监控 | /governance/metadata/ingestion/logs | Settings > Ingestion > Logs | /settings/services/ingestion/logs | 纯代理 | MS3 | 状态、日志、停止 |
| M092 | 元数据维护 | 元数据管理 | 元数据维护 | /governance/metadata/explore | Explore > Tables > Edit Metadata | /explore/tables | 纯代理 | MS3 | 自动/手工维护；沉淀标准 |
| M093 | 元数据版本管理 | 元数据管理 | 元数据版本管理 | /governance/metadata/versions | Explore > Entity Version History | /api/v1/metadata/version | 纯代理 | MS3 | 发布/对比/订阅；版本以 OM + 自研补充 |
| M094 | 元数据复制 | 元数据管理 | 元数据复制 | /governance/metadata/export-import | Settings > Export / Import Metadata | /api/v1/metadata/export | 纯代理 | MS3 | 跨环境迁移 |
| M095 | 元数据目录 | 元数据管理 | 元数据目录 | /governance/metadata/catalog | Explore > Search / Data Assets | /explore | 纯代理 | MS3 | 数据源目录、资产目录 |
| M096 | 元数据分析 | 元数据管理 | 元数据分析 | /governance/metadata/lineage | Explore > Lineage Tab | /explore/tables/{fqn}/lineage | 纯代理 | MS3 | 关联、血缘、影响分析 |
| M097 | 字典管理 | 元数据管理 | 字典管理 | /governance/metadata/glossary | Govern > Glossary | /glossary | 纯代理 | MS3 | 平台字典映射 OM Glossary |

### 4.3 数据目录管理（M112～M122）

| M | 功能模块 | 门户父菜单 | 门户菜单名 | 门户路由 | OM 原生入口 | OM 路由/API | 集成方式 | MS | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M112 | 目录分类 | 数据目录管理 | 目录分类 | /governance/catalog/categories | Govern > Domains / Tags | /settings/domains | 代理+自研扩展 | MS3 | 涉密分类；机构扩展 |
| M113 | 数据资源编目 | 数据目录管理 | 数据资源编目 | /governance/catalog/data-assets | Explore + 自研编目 | /api/v1/governance/catalog/assets | 代理+自研扩展 | MS3 | 批量/手动编目 |
| M114 | 服务资源编目 | 数据目录管理 | 服务资源编目 | /governance/catalog/service-assets | Explore > APIs / Services | /explore/apis | 代理+自研扩展 | MS3 | 关联 API 服务编目 |
| M115 | 目录注册发布 | 数据目录管理 | 目录注册发布 | /governance/catalog/publish | Govern + 自研发布 | /api/v1/governance/catalog/publish | 代理+自研扩展 | MS3 | 数据/服务目录发布 |
| M116 | 目录审批 | 数据目录管理 | 目录审批 | /governance/catalog/approval | 自研审批流 | /api/v1/governance/catalog/approval | 代理+自研扩展 | MS3 | 目录审批闭环；OM 无原生等价 |
| M117 | 目录查询与维护 | 数据目录管理 | 目录查询与维护 | /governance/catalog/maintenance | Explore + 自研维护 | /api/v1/governance/catalog/maintenance | 代理+自研扩展 | MS3 | 查询与维护 |
| M118 | 目录版本管理 | 数据目录管理 | 目录版本管理 | /governance/catalog/versions | Entity Version + 自研 | /api/v1/governance/catalog/versions | 代理+自研扩展 | MS3 | 变更历史对比 |
| M119 | 资源目录门户 | 数据目录管理 | 资源目录门户 | /governance/catalog/portal | 自研目录门户 | /governance/catalog/portal | 代理+自研扩展 | MS3 | 变更通知 |
| M120 | 资源申请订阅 | 数据目录管理 | 资源申请订阅 | /governance/catalog/subscribe | 自研订阅申请 | /api/v1/governance/catalog/subscribe | 代理+自研扩展 | MS3 | 库表/文件/API 申请 |
| M121 | 资源订阅审批 | 数据目录管理 | 资源订阅审批 | /governance/catalog/subscribe-approval | 自研订阅审批 | /api/v1/governance/catalog/subscribe/approval | 代理+自研扩展 | MS3 | 提供方审批 |
| M122 | 资源订阅分发 | 数据目录管理 | 资源订阅分发 | /governance/catalog/distribute | 自研订阅分发 | /api/v1/governance/catalog/distribute | 代理+自研扩展 | MS3 | 调用与测试 |

### 4.4 治理任务（M098）

| M | 功能模块 | 门户父菜单 | 门户菜单名 | 门户路由 | OM 原生入口 | OM/DS 路由 | 集成方式 | MS | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M098 | 治理任务管理 | 治理任务管理 | 治理任务管理 | /governance/tasks | OM Governance + DolphinScheduler | /om/api/v1/governance/tasks + /ds/ | 代理+自研扩展 | MS2 | 调度执行→DS(/ds/)；任务元数据→OM API |

---

## 五、功能域汇总

| 门户分组 | 模块数 | M 范围 |
|----------|--------|--------|
| 数据质量中心 | 5 | M078～M080、M082～M083 |
| 元数据管理 | 12 | M086～M097 |
| 数据目录管理 | 11 | M112～M122 |
| 治理任务 | 1 | M098 |
| **合计** | **29** | — |

---

## 六、非 OM 邻域模块索引（2.1 内不在本表）

| M | 功能模块 | 实现方式 | 门户归属 |
|---|----------|----------|----------|
| M081 | 数据标准监控 | 纯自研 | 2.1.2 数据质量中心（自研扩展） |
| M084 | 数据标准体系管理 | 纯自研 | 2.1.2 标准体系 |
| M085 | 标准映射与稽核联动 | 纯自研 | 2.1.2 标准体系 |
| M102 | 数据元标准管理 | 纯自研 | 2.1.2 标准体系 |
| M103 | 数据编码规范管理 | 纯自研 | 2.1.2 标准体系 |
| M104 | 命名规范管理 | 纯自研 | 2.1.2 标准体系 |
| M105 | 标准文件管理 | 纯自研 | 2.1.2 标准体系 |
| M099 | 可视化 ETL 治理开发 | Kettle | 2.1.4 治理 ETL |
| M100 | ETL 治理监控 | DolphinScheduler | 2.1.4 调度监控 |
| M101 | 数据治理组件库 | 纯自研 | 2.1.4 自研组件库 |
| M106 | 逻辑模型管理 | 纯自研 | 2.1.5 数据融合 |
| M107 | 物理模型管理 | 纯自研 | 2.1.5 数据融合 |
| M108 | 模型报告 | 纯自研 | 2.1.5 数据融合 |
| M109 | 数据开发（脚本） | 纯自研 | 2.1.5 数据融合 |
| M110 | 工作流调度 | DolphinScheduler | 2.1.5 调度 |
| M111 | 数据融合组件库 | 纯自研 | 2.1.5 数据融合 |

---

## 七、配置与验收

### 7.1 application.yml（摘录）

```yaml
integration:
  openmetadata:
    base-url: http://vm1:8585
    menu-mapping-file: classpath:om-menu-mapping.json  # 可由本表导出
  dolphinscheduler:
    base-url: http://vm1:12345  # M098 调度侧
```

### 7.2 验收对齐

| 项 | 说明 |
|----|------|
| D08 | **TC-P1-OM-001**（MS3）：数据源接入 + Schema 发现 |
| D04 MS2 | 门户登录 → 按本表菜单进入 → 对照 D05 验收要点 |
| 脚本校验 | `python scripts/gen_om_menu_mapping.py --check` 行数 = 29 |

---

## 八、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V1.0 | 2026-06-23 | 初版：29 条 OM 菜单映射；脚本可再生 |

---

*文档结束 — D10 与 D06 OpenMetadata 索引（28+1）配套；全表 Excel 见 `D10-OM菜单名映射表_V1.0.xlsx`。*
