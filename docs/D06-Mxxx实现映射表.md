# Mxxx 实现映射表

| 属性 | 说明 |
|------|------|
| **文档编号** | **D06** |
| **文档版本** | V1.0（终稿） |
| **编制日期** | 2026-06-23 |
| **配套清单** | [`D05-系统功能清单.md`](D05-系统功能清单.md) V2.6 |
| **框架选型** | [`D04-开源框架选型评估.md`](D04-开源框架选型评估.md) V1.0 |
| **ESB** | [`D03-ESB集成说明.md`](D03-ESB集成说明.md)；SMC `http://10.10.10.61:7000` |
| **凭证** | 复制 `esb.env.example` → `esb.env.local`（**勿提交 Git**） |
| **Excel** | [`Mxxx实现映射表_V1.0.xlsx`](mapping/Mxxx实现映射表_V1.0.xlsx) |
| **模块总数** | 215 |

---

## 一、列说明

| 列名 | 说明 |
|------|------|
| **M** | 验收/合同引用编号 |
| **V3板块** | V3.0 块级板块（基线附录 B）；非 Word 叶子章节 |
| **实现方式** | 与功能清单 V2.6 一致 |
| **框架/组件** | 外购/开源/自研产品 |
| **部署** | VM1：平台+ESB+OM+DS+Kettle；VM2：ES+DataEase+Canal 等 |
| **门户集成** | 甲方须从统一门户进入 |
| **代码包** | 规划路径（`platform-backend` / `platform-web`） |
| **二次开发要点** | 适配或自研重点 |

---

## 二、实现方式统计

### 2.1 按清单实现方式（明细）

| 实现方式 | 数量 |
|----------|------|
| 纯自研 | 95 |
| 集成+自研·DataEase(iframe) | 50 |
| 外购·AEAI ESB | 19 |
| 集成+自研·OpenMetadata | 16 |
| 开源集成·OpenMetadata | 12 |
| 开源集成·DataEase(iframe) | 6 |
| 开源集成·Elasticsearch | 5 |
| 开源集成·SeaweedFS | 3 |
| 开源集成·MongoDB | 2 |
| 开源集成·Kettle | 2 |
| 开源集成·DolphinScheduler | 2 |
| 开源集成·Canal | 1 |
| 集成+自研·DS+OpenMetadata | 1 |
| 集成+自研·ESB适配(M214) | 1 |

### 2.2 按开源评估四类汇总

| 类型 | 数量 | 说明 |
|------|------|------|
| 外购 | 19 | M001～M019 |
| 开源集成 | 33 | 以框架能力为主（清单明细加总；评估文档写 35 为口径差异） |
| 集成+自研 | 68 | 框架底座 + 定制 |
| 纯自研 | 95 | 政务业务与等保 |

---

## 三、M001～M215 完整映射表

| M | 功能模块 | 逻辑域 | L | V3板块 | 实现方式 | 框架/组件 | 部署 | 门户集成 | 代码包 | 二次开发要点 |
|---|----------|--------|---|--------|----------|-----------|------|----------|--------|--------------|
| M001 | API 设计与建模 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M002 | 第三方服务配置 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M003 | 第三方服务注册 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M004 | 服务监控 | L1.2 | L2 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M005 | 服务流量控制 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M006 | 参数处理 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M007 | 黑白名单控制 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M008 | ESB 工作流对接 | L1.2 | L2 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M009 | ESB 工作流任务管理 | L1.2 | L2 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M010 | ESB 工作流监控 | L1.2 | L2 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M011 | ETL 作业管理 | L1.2 | L2 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M012 | ETL 调度执行 | L1.2 | L2 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M013 | ETL 运行监控 | L1.2 | L2 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M014 | 数据脱敏与匿名化 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M015 | API 监控分析与服务质量 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M016 | 共享交换分析 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M017 | 交换健康监控 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M018 | 交换任务管理 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M019 | 交换任务监控 | L1.2 | L1 | 1.2 服务总线 | 外购·AEAI ESB | AEAI ESB | VM1/ESB | M214代理/深链 | integration/esb | 见 ESB集成说明；凭证 esb.env.local |
| M020 | 数据需求管理 | L1.3 | L1 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M021 | 数据需求分析 | L1.3 | L1 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M022 | 数据需求确认 | L1.3 | L1 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M023 | 数据供给查看 | L1.3 | L1 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M024 | 目录清单 | L1.3 | L1 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M025 | 异议清单 | L1.3 | L1 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M026 | 供需清单 | L1.3 | L1 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M027 | 评价数据来源 | L1.3 | L2 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M028 | 评价周期管理 | L1.3 | L1 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M029 | 评价指标管理 | L1.3 | L1 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M030 | 评价执行与结果 | L1.3 | L1 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M031 | 部门数据共享门户 | L1.4 | L1 | 1.4 应用分析门户 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M032 | 门户首页 | L1.4 | L1 | 1.4 应用分析门户 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M033 | 全文资源检索 | L1.4 | L1 | 1.4 应用分析门户 | 开源集成·Elasticsearch | Elasticsearch 8.x | VM2 | 检索页 | search/es | 与 OpenMetadata 共用 ES |
| M034 | 资源目录检索 | L1.4 | L1 | 1.4 应用分析门户 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M035 | 资源订阅申请 | L1.4 | L1 | 1.4 应用分析门户 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M036 | 领导决策门户 | L1.4 | L2 | 1.4 应用分析门户 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 8 子态势大屏+跨域汇总；非独立 M161～M209 |
| M037 | 基础库统计分析 | L1.3 | L2 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M038 | 重点领域统计分析 | L1.3 | L2 | 1.3 应用平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/application | 政务/等保 |
| M039 | 填报指引 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M040 | 项目/系统信息登记 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M041 | 数据库/表/项登记 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M042 | 数据字典登记 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M043 | 数据资产标签登记 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M044 | 数据项管理 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M045 | 数据资产标签管理 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M046 | 数据资产报告 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M047 | 数据资产图谱分析 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M048 | 访问控制管理 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 双重授权：系统管理员不直接授数据访问权；跨部门须审批 |
| M049 | 系统维护管理 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M050 | 数据字典管理 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M051 | 上传模板管理 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M052 | 数据上传管理 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M053 | 数据上传记录 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M054 | 结构化数据接入 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M055 | 远程文件接入（FTP） | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M056 | 本地文件接入 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M057 | 非结构化数据接入 | L1.1 | L2 | 1.1 大数据归集平台 | 开源集成·SeaweedFS | SeaweedFS 3.x | VM2 | 文件页 | storage/seaweedfs | S3 协议 |
| M058 | 半结构化数据接入 | L1.1 | L3 | 1.1 大数据归集平台 | 开源集成·MongoDB | MongoDB 7.x | VM2 | 半结构化页 | ingestion/mongo | 半结构化占位 |
| M059 | API 接口数据接入 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M060 | CDC 实时数据接入 | L1.1 | L1 | 1.1 大数据归集平台 | 开源集成·Canal | Canal 1.1.x | VM2 | CDC配置页 | ingestion/cdc | MySQL binlog |
| M061 | 数据探查 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M062 | 数据定义 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M063 | 数据读取 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M064 | 数据对账 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M065 | 数据资源编目管理 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M066 | 数据资源分类 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M067 | 资源目录注册发布 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M068 | 数据资源目录审批 | L1.1 | L1 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M069 | 数据分级分类 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M070 | 数据脱敏策略 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M071 | 数据标签管理 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M072 | 数据搜索引擎 | L1.1 | L2 | 1.1 大数据归集平台 | 开源集成·Elasticsearch | Elasticsearch 8.x | VM2 | 检索页 | search/es | 与 OpenMetadata 共用 ES |
| M073 | 数据备份 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M074 | 数据归档 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M075 | 数据销毁 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M076 | 全局数据资产视图 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M077 | 健康监控 | L1.1 | L2 | 1.1 大数据归集平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/ingestion | 政务/等保 |
| M078 | 质量规则配置 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M079 | 数据质量任务配置 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M080 | 数据质量监控 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M081 | 数据标准监控 | L2.1 | L2 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M082 | 数据质量评估 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M083 | 数据质量分析报告 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M084 | 数据标准体系管理 | L2.1 | L2 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M085 | 标准映射与稽核联动 | L2.1 | L2 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M086 | 适配器管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M087 | 数据源分类管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M088 | 数据源管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M089 | 元模型管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M090 | 元数据采集 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M091 | 元数据采集监控 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M092 | 元数据维护 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M093 | 元数据版本管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M094 | 元数据复制 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M095 | 元数据目录 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M096 | 元数据分析 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M097 | 字典管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 门户代理 OM；菜单名映射 V3.0 |
| M098 | 治理任务管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·DS+OpenMetadata | DolphinScheduler + OpenMetadata | VM1 | 门户代理+扩展 | integration/openmetadata + integration/ds | DS 调度联动 OM 治理任务 |
| M099 | 可视化 ETL 治理开发 | L2.1 | L2 | 2.1 大数据融合治理平台 | 开源集成·Kettle | Kettle 9.4.x | VM1 | 自研调度页 | integration/kettle | Kettle 治理 ETL；MS2 前确认路径；不覆盖 M011～M013 |
| M100 | ETL 治理监控 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·DolphinScheduler | DolphinScheduler 3.x | VM1 | 门户代理 | integration/ds | 调度联动 |
| M101 | 数据治理组件库 | L2.1 | L1 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M102 | 数据元标准管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M103 | 数据编码规范管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M104 | 命名规范管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M105 | 标准文件管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M106 | 逻辑模型管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M107 | 物理模型管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M108 | 模型报告 | L2.1 | L1 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M109 | 数据开发（脚本） | L2.1 | L1 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M110 | 工作流调度 | L2.1 | L1 | 2.1 大数据融合治理平台 | 开源集成·DolphinScheduler | DolphinScheduler 3.x | VM1 | 门户代理 | integration/ds | 调度联动 |
| M111 | 数据融合组件库 | L2.1 | L1 | 2.1 大数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/governance | 政务/等保 |
| M112 | 目录分类 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M113 | 数据资源编目 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M114 | 服务资源编目 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M115 | 目录注册发布 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M116 | 目录审批 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M117 | 目录查询与维护 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M118 | 目录版本管理 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M119 | 资源目录门户 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M120 | 资源申请订阅 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M121 | 资源订阅审批 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M122 | 资源订阅分发 | L2.1 | L1 | 2.1 大数据融合治理平台 | 集成+自研·OpenMetadata | OpenMetadata 1.12.x | VM1 | 门户代理+扩展 | integration/openmetadata | 审批/机构/质量定制 |
| M123 | 数据分类管理 | L2.2 | L2 | 2.2 非结构数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/unstructured | 政务/等保 |
| M124 | 文件资源管理 | L2.2 | L2 | 2.2 非结构数据融合治理平台 | 开源集成·SeaweedFS | SeaweedFS 3.x | VM2 | 文件页 | storage/seaweedfs | S3 协议 |
| M125 | 文件资源检索 | L2.2 | L2 | 2.2 非结构数据融合治理平台 | 开源集成·Elasticsearch | Elasticsearch 8.x | VM2 | 检索页 | search/es | 与 OpenMetadata 共用 ES |
| M126 | 非结构化元数据管理 | L2.2 | L2 | 2.2 非结构数据融合治理平台 | 开源集成·Elasticsearch | Elasticsearch 8.x | VM2 | 检索页 | search/es | 与 OpenMetadata 共用 ES |
| M127 | 非结构化数据清洗转换 | L2.2 | L2 | 2.2 非结构数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/unstructured | 政务/等保 |
| M128 | 非结构化数据标识处理 | L2.2 | L2 | 2.2 非结构数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/unstructured | 政务/等保 |
| M129 | 非结构化数据关联处理 | L2.2 | L2 | 2.2 非结构数据融合治理平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/unstructured | 政务/等保 |
| M130 | 基础库管理 | L2.3 | L1 | 2.3 大数据平台资源中心 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/resource-center | 政务/等保 |
| M131 | 半结构化库管理 | L2.3 | L1 | 2.3 大数据平台资源中心 | 开源集成·MongoDB | MongoDB 7.x | VM2 | 半结构化页 | ingestion/mongo | 半结构化占位 |
| M132 | 非结构化库管理 | L2.3 | L1 | 2.3 大数据平台资源中心 | 开源集成·SeaweedFS | SeaweedFS 3.x | VM2 | 文件页 | storage/seaweedfs | S3 协议 |
| M133 | 分区设计管理 | L2.3 | L2 | 2.3 大数据平台资源中心 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/resource-center | 政务/等保 |
| M134 | 数据库存储管理 | L2.3 | L2 | 2.3 大数据平台资源中心 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/resource-center | 政务/等保 |
| M135 | 资产目录管理 | L2.3 | L2 | 2.3 大数据平台资源中心 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/resource-center | 政务/等保 |
| M136 | 数据库检索查询 | L2.3 | L2 | 2.3 大数据平台资源中心 | 开源集成·Elasticsearch | Elasticsearch 8.x | VM2 | 检索页 | search/es | 与 OpenMetadata 共用 ES |
| M137 | 数据库统计分析 | L2.3 | L2 | 2.3 大数据平台资源中心 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/resource-center | 政务/等保 |
| M138 | 资源监控管理 | L2.3 | L2 | 2.3 大数据平台资源中心 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/resource-center | 政务/等保 |
| M139 | 用户中心 | L3.1 | L1 | 3.1 通用支撑+智能BI | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M140 | 应用中心 | L3.1 | L1 | 3.1 通用支撑+智能BI | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M141 | 认证中心 | L3.1 | L2 | 3.1 通用支撑+智能BI | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M142 | 服务中心 | L3.1 | L2 | 3.1 通用支撑+智能BI | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M143 | 系统管理 | L3.1 | L1 | 3.1 通用支撑+智能BI | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M144 | 日志审计 | L3.1 | L1 | 3.1 通用支撑+智能BI | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M145 | 系统对接 | L3.1 | L1 | 3.1 通用支撑+智能BI | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M146 | 显示引擎 | L3.1 | L3 | 3.1 通用支撑+智能BI | 开源集成·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 不改源码 iframe；GPL 法务待确认 |
| M147 | 组件引擎 | L3.1 | L3 | 3.1 通用支撑+智能BI | 开源集成·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 不改源码 iframe；GPL 法务待确认 |
| M148 | 地图管理 | L3.1 | L3 | 3.1 通用支撑+智能BI | 开源集成·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 不改源码 iframe；GPL 法务待确认 |
| M149 | 数据源管理 | L3.1 | L2 | 3.1 通用支撑+智能BI | 开源集成·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 不改源码 iframe；GPL 法务待确认 |
| M150 | 可视化设计 | L3.1 | L3 | 3.1 通用支撑+智能BI | 开源集成·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 不改源码 iframe；GPL 法务待确认 |
| M151 | 自助分析 | L3.1 | L3 | 3.1 通用支撑+智能BI | 开源集成·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 不改源码 iframe；GPL 法务待确认 |
| M152 | 人口数据采集管理 | L3.2 | L1 | 3.2 业务支撑平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M153 | 人口数据分区管理 | L3.2 | L1 | 3.2 业务支撑平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M154 | 人口源目录管理 | L3.2 | L1 | 3.2 业务支撑平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M155 | 人口信息更新维护 | L3.2 | L1 | 3.2 业务支撑平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M156 | 人口信息校核 | L3.2 | L1 | 3.2 业务支撑平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M157 | 人口信息存储管理 | L3.2 | L1 | 3.2 业务支撑平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M158 | 人口信息双重授权管理 | L3.2 | L1 | 3.2 业务支撑平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 双重授权：与 M048/M211 一致；三角色场景可验证 |
| M159 | 人口数据服务-接口方式 | L3.2 | L1 | 3.2 业务支撑平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M160 | 人口数据服务-批量应用方式 | L3.2 | L1 | 3.2 业务支撑平台 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/analysis | 政务/等保 |
| M161 | 户籍人口统计分析模型 | L3.2 | L2 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M162 | 城镇人口统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M163 | 人口年龄结构统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M164 | 人口学历结构统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M165 | 出生人口数据统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M166 | 人口离异统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M167 | 贫困人口统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M168 | 重点人口统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M169 | 残疾人口统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M170 | 人口党员统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M171 | 常住人口同比统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M172 | 死亡人口同比统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M173 | 人口数据空间分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M174 | 义务教育阶段人口空间分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M175 | 法人数据采集管理 | L3.2 | L1 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M176 | 法人数据分区管理 | L3.2 | L1 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M177 | 法人源目录管理 | L3.2 | L1 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M178 | 法人信息更新维护 | L3.2 | L1 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M179 | 法人信息校核 | L3.2 | L1 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M180 | 法人信息存储管理 | L3.2 | L1 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M181 | 法人信息双重授权管理 | L3.2 | L1 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 法人域双重授权：同 M158 机制 |
| M182 | 法人数据服务-接口方式 | L3.2 | L1 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M183 | 法人数据服务-批量应用方式 | L3.2 | L1 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M184 | 法人年龄结构信息分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M185 | 法人学历结构信息分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M186 | 企业所得税统计分析模型 | L3.2 | L2 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M187 | 企业纳税总额统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M188 | 企业社保统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M189 | 企业规模统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M190 | 企业性质统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M191 | 法人产业结构分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M192 | 法人行业结构分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M193 | 地方生产总值分析模型 | L3.2 | L2 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M194 | 一般公共预算收入分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M195 | 工业国税开票销售分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M196 | 行业营业收入分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M197 | 行业税收分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M198 | 外贸进出口分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M199 | 工业用电量分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M200 | 规上工业分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M201 | 产业增加值分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M202 | 实际利用外资同比环比分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M203 | 投资项目同比环比分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M204 | 应急资源空间分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M205 | 应急突发事件统计分析模型 | L3.2 | L2 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M206 | 应急突发事件空间分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M207 | 安全生产事故统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M208 | 安全生产事故空间分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M209 | 低保特困残疾学生统计分析模型 | L3.2 | L3 | 3.2 业务支撑平台 | 集成+自研·DataEase(iframe) | DataEase | VM2 | iframe+SSO | integration/dataease | 指标/SQL 样例自研 |
| M210 | 统一门户登录 | 跨平台 | L1 | 跨平台 跨平台公共能力 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/system | 政务/等保 |
| M211 | 统一系统管理 | 跨平台 | L1 | 跨平台 跨平台公共能力 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/system | 统一系统管理；双重授权/等保 |
| M212 | 平台事件总线 | 跨平台 | L1 | 跨平台 跨平台公共能力 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/hub | 政务/等保 |
| M213 | 联通基础管理 | 跨平台 | L1 | 跨平台 跨平台公共能力 | 纯自研 | 自研 Spring Boot + Vue3 | VM1 | 原生页面 | backend/hub | 政务/等保 |
| M214 | ESB 集成层 | 跨平台 | L1 | 跨平台 跨平台公共能力 | 集成+自研·ESB适配(M214) | ESB + 自研 | VM1 | 门户代理 | backend/integration/esb | Mock/真实 ESB 切换；终验须真实环境；代理 M001～M019 |
| M215 | Kettle ETL 集成 | 跨平台 | L1 | 跨平台 跨平台公共能力 | 开源集成·Kettle | Kettle 9.4.x | VM1 | 自研调度页 | integration/kettle | 仅 M099 治理 ETL；不覆盖 M011～M013 |

---

## 四、框架 → M 编号索引

| 框架/组件 | 模块数 | M 编号 |
|-----------|--------|--------|
| AEAI ESB（外购） | 19 | M001～M019 |
| ESB 适配层（M214） | 1 | M214 |
| OpenMetadata | 28 | M078～M080、M082～M083、M086～M097、M112～M122 |
| DataEase | 56 | M036、M146～M151、M161～M209 |
| DolphinScheduler | 2 | M100、M110 |
| DS+OM（M098） | 1 | M098 |
| Kettle | 2 | M099、M215 |
| Canal | 1 | M060 |
| MongoDB | 2 | M058、M131 |
| SeaweedFS | 3 | M057、M124、M132 |
| Elasticsearch | 5 | M033、M072、M125～M126、M136 |
| 自研平台 | 95 | 其余模块 |

---

## 五、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V1.0（终稿） | 2026-06-23 | 215 模块完整映射；增补 V3板块列；脚本可再生 |
