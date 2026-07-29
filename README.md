# 承德智慧城市 — MS1 工程初始化说明

## 目录

| 目录 | 说明 |
|------|------|
| `platform-backend/` | Spring Boot 3.2 后端 |
| `platform-web/` | Vue3 统一门户 |
| `scripts/setup_smart_city.sql` | 本机 MySQL 建库 |
| `local.env.example` | 本机数据库/Redis/开源组件 URL 模板 |
| `compose/oss-stack.yml` | 开源组件 Docker Compose（profile 分波） |

## 本机快速启动（推荐）

1. 启动 **MySQL 8.4** 与 **Redis**（见 [`platform-backend/README.md`](platform-backend/README.md)）
2. 执行 `scripts/setup_smart_city.sql`，配置 `local.env`
3. 后端：`cd platform-backend  .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"`
4. 前端：`cd platform-web && npm run dev`
5. 登录 `http://localhost:4000`，`sys_admin` / `Test@12345`

## 开源组件联调（D04/D07/D13）

验收范围包含 **7+2 开源组件真实部署 + 门户集成**，不仅是自研 POC。

1. 复制 `local.env.example` → `local.env`，设置 `INTEGRATION_ENABLED=true`
2. 启动开源栈：`powershell -File scripts\oss_up.ps1 -All`
3. 探活：`powershell -File scripts\oss_health.ps1`
4. 样例配置：`powershell -File scripts\seed_oss_config.ps1`
5. 集成冒烟：`powershell -File scripts\smoke_oss_integration.ps1`

部署手册：[`D13-框架部署手册.md`](D13-框架部署手册.md)。**AEAI ESB 不在本机联调范围**，现场环境再验收。

### 冒烟与端到端演示（D02 §6.2）

```powershell
# MS1 认证/RBAC/用户机构
powershell -ExecutionPolicy Bypass -File scripts\smoke_ms1_auth_rbac.ps1

# 五条端到端演示 API（归集/目录共享/供需/ESB+Kettle）
powershell -ExecutionPolicy Bypass -File scripts\smoke_e2e_demos.ps1

# MS3~MS5：治理 / 非结构化 / 资源中心
powershell -ExecutionPolicy Bypass -File scripts\smoke_ms3_ms5.ps1

# MS6~MS7：40 分析模型 / DataEase iframe SSO / 调度
powershell -ExecutionPolicy Bypass -File scripts\smoke_ms6_ms7.ps1

# M139~M151：通用支撑 + 智能 BI
powershell -ExecutionPolicy Bypass -File scripts\smoke_analytics.ps1

# M152~M209：人口/法人/宏观/重点领域域模块
powershell -ExecutionPolicy Bypass -File scripts\smoke_analytics_domains.ps1

# MS8：全量冒烟回归（80 条 / 13 脚本）+ 备份演练 POC
powershell -ExecutionPolicy Bypass -File scripts\smoke_ms8_regression.ps1
powershell -ExecutionPolicy Bypass -File scripts\backup_drill_poc.ps1

# 含开源栈联调（加 -IncludeOss，需 Docker + INTEGRATION_ENABLED=true）
powershell -ExecutionPolicy Bypass -File scripts\smoke_ms8_regression.ps1 -IncludeOss
```

门户内手工路径：登录 → Hub → 系统管理；数据共享交换平台 → 归集 / 应用平台 / 共享门户 / ESB；**主数据平台 → 数据融合治理 / 非结构化治理 / 资源中心**；**挖掘分析平台 → 智能 BI / 人口 / 法人 / 宏观 / 重点领域**；系统管理 → ETL 治理 / 调度管理。

终验交付包：[`D15-终验交付包.md`](D15-终验交付包.md)。  
甲方基线/外部依赖签字：[`D16-基线与外部依赖确认单.md`](D16-基线与外部依赖确认单.md)。


## 可选：Docker 仅作 MySQL/Redis

若不想本机装 Redis，可用 `docker compose -p smartcity up -d`（见 `docker-compose.yml`），后端仍在本机运行。

## 文档

- D11：[`D11-统一门户技术方案.md`](D11-统一门户技术方案.md)
- D12：[`D12-数据库设计.md`](D12-数据库设计.md)
- D17：[`D17-系统优化流程及方式.md`](D17-系统优化流程及方式.md)（分块优化活文档；**R8** 元数据定时采集 `summary` 溢出修复见该文档 §四）

### 采集汇聚域 CI（R1）

```powershell
python scripts/normalize_v3_catalog.py --scope collect
python scripts/validate_d05_consistency.py --scope collect
powershell -File scripts/smoke_ingestion.ps1 -CollectOnly
```

