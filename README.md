# 承德智慧城市 — MS1 工程初始化说明

## 目录

| 目录 | 说明 |
|------|------|
| `platform-backend/` | Spring Boot 3.2 后端 |
| `platform-web/` | Vue3 统一门户 |
| `scripts/setup_smart_city.sql` | 本机 MySQL 建库 |
| `local.env.example` | 本机数据库/Redis 账号模板 |

## 本机快速启动（推荐）

1. 启动 **MySQL 8.4** 与 **Redis**（见 [`platform-backend/README.md`](platform-backend/README.md)）
2. 执行 `scripts/setup_smart_city.sql`，配置 `local.env`
3. 后端：`platform-backend\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"`
4. 前端：`cd platform-web && npm run dev`
5. 登录 `http://localhost:3000`，`sys_admin` / `Test@12345`

## 可选：Docker 仅作 MySQL/Redis

若不想本机装 Redis，可用 `docker compose -p smartcity up -d`（见 `docker-compose.yml`），后端仍在本机运行。

## 文档

- D11：[`D11-统一门户技术方案.md`](D11-统一门户技术方案.md)
- D12：[`D12-数据库设计.md`](D12-数据库设计.md)
