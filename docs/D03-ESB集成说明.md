# AEAI ESB 集成说明

| 属性 | 说明 |
|------|------|
| **文档编号** | **D03** |
| **文档版本** | V1.0 |
| **编制日期** | 2026-06-23 |
| **上位基线** | [`D02-需求基线说明.md`](D02-需求基线说明.md) V2.2 |
| **功能清单** | [`D05-系统功能清单.md`](D05-系统功能清单.md) V2.6 |
| **厂商接口原文** | [`ESB接口说明文档.docx`](vendor/ESB接口说明文档.docx) |
| **产品** | 沈阳数通畅联 **AEAI ESB**（设计器 + Runtime + **SMC** 服务管理中心） |

---

## 一、集成定位

**M001～M019（服务总线）全部由外购 AEAI ESB 实现**，本平台不自研 API 网关、编排引擎或交换 ETL 引擎。

| 角色 | 职责 |
|------|------|
| **AEAI ESB** | 交付 M001～M019 全部业务能力：API 注册/发布、集成流程编排、交换任务、监控告警、脱敏加密等 |
| **ESB 设计器** | 可视化建模、MessageFlow/WebService/ServiceModel 开发与部署 |
| **SMC 控制台** | 运行监控、启停、日志、API 配置查询（默认端口 **7000**） |
| **本平台 M214** | M001～M019 **统一集成层**：门户菜单/深链、REST/SOAP 代理、Token 透传、联调 Mock |
| **本平台 M215** | **仅 M099** 治理 ETL 的 Kettle 集成；**不覆盖 M011～M013** |

详细模块映射见第三节；验收口径见 [`D05-系统功能清单.md`](D05-系统功能清单.md) 第八节。

---

## 二、SMC 服务分组

**联调环境（承德）**

| 项 | 地址 |
|----|------|
| SMC 控制台 | `http://10.10.10.61:7000/SMC/index?Login` |
| API 服务根 | `http://10.10.10.61:7000/SMC/services` |
| 账号配置 | 现场 SMC 账号；本地可写 `esb.env.local`（**勿提交 Git**，已在 `.gitignore`） |

| 分组 | 服务名 | 协议 | 主要用途 |
|------|--------|------|----------|
| 设计器登录 | SoakerLogin | SOAP | 设计器测试连接、用户校验 |
| 设计器管理 | SoakerManage | SOAP | 工程/Web 服务/消息流程/服务模型 CRUD、部署、启停 |
| 流程配置 | EaiFlowConfig | REST | 场景查询、集成流程 `/rest/service-flow` |
| 运行监控 | Ws8MfRuntimeMonitor | REST | RS/WS/MF 运行时统计 |
| 运行日志 | EaiManage | REST | `/rest/service-flow-log` 集成流程日志 |
| API 注册 | ApiResourceLocator | REST | `/registry/api-config*` |
| API 认证 | ApiAuthenticater | REST | `/security/authenticate` Token |
| 安全算法 | SafetyAlgorithmManage | REST | 加密规则与配置 |
| 权限同步 | AuthManage | REST | `syncAuthority`、`syncGroupUser` |
| 文件交换 | FileTransManage | REST | `/rest/trans-send` 触发文件传输 |
| 流处理 | FlinkServiceManage | REST | Flink 作业 SQL/日志（备用） |

> 文档示例 IP `192.168.202.173` 为厂商样例环境，**非承德生产地址**。

---

## 三、M001～M019 能力映射

| 编号 | 功能模块 | ESB 实现路径 | 设计/运维入口 |
|------|----------|-------------|--------------|
| M001 | API 设计与建模 | SoakerManage WebService/ServiceModel + ApiResourceLocator | ESB 设计器 |
| M002 | 第三方服务配置 | `findPropertiesResourceConfigList`、ApiResourceLocator | SMC / 设计器 |
| M003 | 第三方服务注册发布 | `deployWebServiceProfile`、`start/stopWebServiceProfile` | SMC |
| M004 | 服务监控告警 | Ws8MfRuntimeMonitor `WSRuntimeStat` / `RSRuntimeStat` | SMC |
| M005 | 服务流量控制 | ESB 内置限流（**接口文档未列 REST**，见第四节） | SMC 控制台 |
| M006 | 参数处理 | API 配置 + SafetyAlgorithmManage | SMC |
| M007 | 黑白名单 | AuthManage `syncAuthority` | SMC |
| M008 | ESB 工作流对接 | `findFlowNameList`、`findRawApplicationList`；EaiFlowConfig | 设计器 + SMC |
| M009 | 工作流任务管理 | `start/stopMessageFlowProfile`、`start/stopApplication` | SMC |
| M010 | 工作流监控 | `MFRuntimeStat` + EaiManage 日志 | SMC |
| M011 | ETL/集成作业管理 | MessageFlow Profile CRUD/deploy（**非 Kettle**） | ESB 设计器 |
| M012 | 调度执行 | deploy + start MessageFlow | SMC |
| M013 | 运行监控 | `MFRuntimeStat` + `/rest/service-flow-log` | SMC |
| M014 | 数据脱敏与匿名化 | SafetyAlgorithmManage | SMC |
| M015 | API 监控分析与 QoS | Ws8MfRuntimeMonitor 三类统计 | SMC |
| M016 | 共享交换分析 | RS/WS/MF 统计汇总 | SMC |
| M017 | 交换健康监控 | MFRuntimeStat 趋势/成功率 | SMC |
| M018 | 交换任务管理 | FileTransManage + MessageFlow | 设计器 + SMC |
| M019 | 交换任务监控 | EaiManage 日志 + MF 监控 | SMC |

---

## 四、接口缺口与待确认

| 项 | 说明 | 影响模块 |
|----|------|----------|
| ESB 生产地址 | 承德政务云 SMC/Runtime 实际 URL | M001～M019、M214 |
| M005 限流 REST | 接口文档未列出独立限流 API | M005（SMC 控制台验收） |
| Mock 联调 | 开发期 M214 可 Mock | 终验须真实 ESB |

---

## 五、M214 / M215 边界

- **M214**：M001～M019 统一 ESB 集成（REST/SOAP 代理、门户深链、Mock/真实切换）。  
- **M215**：**仅 M099** Kettle 治理 ETL；与 M011～M013 解绑。

---

## 六、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V1.0 | 2026-06-23 | 初版：M001～M019 全 ESB 映射 |
