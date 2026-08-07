# D25 · DolphinScheduler 原生安装手册（非 Docker）

| 项 | 内容 |
|----|------|
| 文档编号 | D25 |
| 版本 | V1.1 |
| 日期 | 2026-08-07 |
| 适用机 | 生产中间件机（示例：`10.10.10.51`，常见 `aarch64`） |
| 对照 | Docker 方案见 [D23-生产环境部署手册](D23-生产环境部署手册.md) |
| 版本对齐 | DolphinScheduler **3.2.2**（standalone） |

---

## 1. 说明与选型

### 1.1 DataEase 不在本手册范围

**现场结论：DataEase 不支持 arm64，仅支持 x86（amd64）。**

因此：

- **本手册不再提供 DataEase 原生安装步骤。**
- 中间件机若为 **aarch64**，无法在本机原生（或按 x86 包）安装 DataEase。
- BI 若必须交付：改到 **x86 服务器** 按 DataEase 官方方式安装，并把门户 `DE_URL` / `DE_EMBED_BASE` 指到该机；或接受门户 BI **诚实降级**（未部署时不假成功）。
- Compose 中的 `bi` profile / `dataease` 服务在 **arm64 中间件机上不要按 x86 预期强行部署**。

### 1.2 何时用本手册（仅 DolphinScheduler）

| 场景 | 建议 |
|------|------|
| 已有可用的 DS Docker 镜像且架构匹配 | **优先** `docker load` + Compose `sched`（D23） |
| 镜像无法拉取 / 无法 load / 必须脱离 Docker | 按本手册 **原生安装 DS** |
| 暂不验收调度 | 可不装 DS；相关能力降级 |

**原则**：门户只认 **HTTP 地址 + 账号**，不强制 Docker。

### 1.3 与门户的契约（勿改错）

| 组件 | 环境变量（`compose/prod-app.env`） | 对外地址（须一致） |
|------|-----------------------------------|--------------------|
| DolphinScheduler | `DS_URL` | `http://10.10.10.51:12345/dolphinscheduler` |
| DS 账号 | `DS_USER` / `DS_PASSWORD` | 以现场 `prod-app.env` 为准（示例常为 `admin` / `dolphinscheduler123`） |

`DE_URL` / `DE_EMBED_BASE` 仅在 **另有 x86 机部署 DataEase** 时配置；arm64 中间件机本手册不涉及。

**业务 Java / Vue 代码一般无需修改。** 仅当端口或密码变更时，改 `prod-app.env` 并在 `.55` 重启 `backend`。

### 1.4 Compose 中的对照（便于对齐参数）

`compose/prod-mid.yml`：

- `dolphinscheduler`：profile `sched`，端口 `12345`（及 `25333`），库 `dolphinscheduler`，连 `oss-mysql`
- 宿主机访问 **oss-mysql** 映射：**`3307 → 3306`**（容器内仍为 `oss-mysql:3306`）

---

## 2. 前置条件（.51）

1. 确认架构：`uname -m`（`aarch64` / `x86_64`）。  
2. JDK：DolphinScheduler 3.2 按官方包（常见 **JDK 8 / 11**），宿主机需已安装。  
3. MySQL：复用已启动的 **oss-mysql**（推荐），或本机另装 MySQL 8。  
4. 防火墙：

```bash
firewall-cmd --permanent --add-port=12345/tcp
# 按需
firewall-cmd --permanent --add-port=25333/tcp
firewall-cmd --reload
```

5. **与 Docker 方案互斥**：同一端口不要同时跑容器与原生进程：

```bash
cd /opt/chengde/chengde-smart-city
docker compose --env-file compose/prod-mid.env -f compose/prod-mid.yml --profile sched stop dolphinscheduler ds-schema-init 2>/dev/null || true
docker rm -f sc_mid_dolphinscheduler sc_mid_ds_schema_init 2>/dev/null || true
ss -lntp | grep -E '12345' || true
```

---

## 3. 数据库准备

### 3.1 连接参数（宿主机视角）

```bash
# 与 compose/prod-mid.env 中 OSS_MYSQL_ROOT_PASSWORD 一致
export OSS_MYSQL_HOST=127.0.0.1
export OSS_MYSQL_PORT=3307
export OSS_MYSQL_ROOT_PASSWORD='填写现场密码'
```

验证：

```bash
mysql -h "$OSS_MYSQL_HOST" -P "$OSS_MYSQL_PORT" -uroot -p"$OSS_MYSQL_ROOT_PASSWORD" -e "SELECT 1"
```

### 3.2 创建并初始化 DolphinScheduler 库

项目内脚本（随代码包部署）：

`/opt/chengde/chengde-smart-city/compose/libs/ds/dolphinscheduler_mysql.sql`

```bash
mysql -h "$OSS_MYSQL_HOST" -P "$OSS_MYSQL_PORT" -uroot -p"$OSS_MYSQL_ROOT_PASSWORD" \
  -e "CREATE DATABASE IF NOT EXISTS dolphinscheduler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 若尚无 t_ds_user 等表，再导入
mysql -h "$OSS_MYSQL_HOST" -P "$OSS_MYSQL_PORT" -uroot -p"$OSS_MYSQL_ROOT_PASSWORD" \
  dolphinscheduler < /opt/chengde/chengde-smart-city/compose/libs/ds/dolphinscheduler_mysql.sql
```

MySQL 驱动（后续拷贝进应用 `libs`）：

`/opt/chengde/chengde-smart-city/compose/libs/ds/mysql-connector-j-8.0.33.jar`

---

## 4. 原生安装 DolphinScheduler 3.2.2（standalone）

### 4.1 获取安装包

下载地址（Java 通用包，不按 CPU 分包）：

- 华为云：https://mirrors.huaweicloud.com/apache/dolphinscheduler/3.2.2/apache-dolphinscheduler-3.2.2-bin.tar.gz  
- Apache 归档：https://archive.apache.org/dist/dolphinscheduler/3.2.2/apache-dolphinscheduler-3.2.2-bin.tar.gz  

内网拷到 `.51`：

```powershell
scp D:\apache-dolphinscheduler-3.2.2-bin.tar.gz root@10.10.10.51:/opt/chengde/
```

解压：

```bash
mkdir -p /opt/chengde/dolphinscheduler
tar -xzf /opt/chengde/apache-dolphinscheduler-3.2.2-bin.tar.gz -C /opt/chengde/dolphinscheduler --strip-components=1
# 若 tar 顶层目录名不同，先 tar -tzf 查看后调整
ls /opt/chengde/dolphinscheduler
```

> **aarch64 说明**：主体为 Java，一般可在 aarch64 JDK 上运行。若遇原生依赖问题，在有匹配架构的 Docker 镜像时再考虑容器方案。

### 4.2 放入 MySQL 驱动

```bash
find /opt/chengde/dolphinscheduler -type d -name 'libs' | head -20

cp /opt/chengde/chengde-smart-city/compose/libs/ds/mysql-connector-j-8.0.33.jar \
   /opt/chengde/dolphinscheduler/standalone-server/libs/
# 若实际路径为 libs/standalone-server，请改到对应目录
```

### 4.3 配置数据源与端口

编辑 standalone 配置（常见路径：`standalone-server/conf/application.yaml` 或 `application-mysql.yaml`）：

```yaml
spring:
  profiles:
    active: mysql
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3307/dolphinscheduler?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: root
    password: 与OSS_MYSQL_ROOT_PASSWORD一致
```

API / UI 端口保持 **12345**（与 `DS_URL` 一致）。可选端口 **25333**。

与 Compose 环境变量对应：

| Compose（容器内） | 原生（宿主机） |
|-------------------|----------------|
| `jdbc:mysql://oss-mysql:3306/...` | `jdbc:mysql://127.0.0.1:3307/...` |
| `SPRING_DATASOURCE_USERNAME=root` | 同上 |
| `MASTER/WORKER_SERVER_LOAD_PROTECTION_ENABLED=false` | 可在对应配置中关闭（可选） |

### 4.4 启动与停止

以包内脚本为准，常见：

```bash
cd /opt/chengde/dolphinscheduler
./bin/dolphinscheduler-daemon.sh start standalone-server
./bin/dolphinscheduler-daemon.sh stop standalone-server
# 或 standalone-server/bin/start.sh | stop.sh
```

### 4.5 systemd 示例

> `ExecStart` / `ExecStop` 请按包内真实脚本路径修改后再 enable。

```ini
# /etc/systemd/system/dolphinscheduler.service
[Unit]
Description=Apache DolphinScheduler Standalone 3.2.2
After=network.target

[Service]
Type=forking
Environment=JAVA_HOME=/usr/lib/jvm/java-11   # 按现场 JDK 路径修改
WorkingDirectory=/opt/chengde/dolphinscheduler
ExecStart=/opt/chengde/dolphinscheduler/bin/dolphinscheduler-daemon.sh start standalone-server
ExecStop=/opt/chengde/dolphinscheduler/bin/dolphinscheduler-daemon.sh stop standalone-server
Restart=on-failure
RestartSec=5
User=root

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now dolphinscheduler
systemctl status dolphinscheduler --no-pager
```

### 4.6 验收

```bash
curl -sf -o /dev/null -w 'DS %{http_code}\n' http://127.0.0.1:12345/dolphinscheduler
# 浏览器：http://10.10.10.51:12345/dolphinscheduler
```

确认 `compose/prod-app.env`：

```text
DS_URL=http://10.10.10.51:12345/dolphinscheduler
DS_USER=admin
DS_PASSWORD=与可登录账号一致
```

若刚改过 env，在 **.55** 重启 backend：

```bash
cd /opt/chengde/chengde-smart-city
docker compose --env-file compose/prod-app.env -f compose/prod-app.yml up -d backend
```

---

## 5. 门户联调清单

1. `.51` 本机：`12345` 的 `curl` 非失败。  
2. `.55` → `.51`：同样 `curl` 通（检查防火墙 / 安全组）。  
3. 门户集成健康：DolphinScheduler 可用（或页面诚实降级说明符合预期）。  
4. 调度：触发依赖 DS 的能力（若有），确认 API 登录与项目/实例正常。  
5. DataEase / BI：arm64 中间件机本手册不部署；见 §1.1。

---

## 6. 运维注意

1. **升级**：原生需自行换包与迁移配置；Docker 方案仍以 D23 镜像标签为准。  
2. **备份**：定期备份 `dolphinscheduler` 库及本地 conf。  
3. **禁止混跑**：同一端口不要同时起 Compose `dolphinscheduler` 与原生进程。  
4. **文档口径**：能用匹配架构的 Docker 时仍优先 D23；本手册为 DS 必须脱离 Docker 时的替代路径。  
5. **DataEase**：仅 x86；勿在本 arm64 机按本手册安装。

---

## 7. DS 离线 Docker 回退（可选）

仅当已有 **与机器架构匹配** 的 DS 镜像离线包时：

```bash
docker load -i /opt/chengde/ds-arm64.tar
# 若标签与 prod-mid.yml 不一致，先 docker tag 为：
#   apache/dolphinscheduler-standalone-server:3.2.2

cd /opt/chengde/chengde-smart-city
docker compose --env-file compose/prod-mid.env -f compose/prod-mid.yml --profile sched up -d
```

验收：

```bash
curl -sf -o /dev/null -w 'DS %{http_code}\n' http://127.0.0.1:12345/dolphinscheduler
```

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V1.0 | 2026-08-07 | 初稿：含 DataEase + DS 原生步骤 |
| V1.1 | 2026-08-07 | DataEase 明确仅支持 x86/不支持 arm64；删除全部 DataEase 安装步骤；手册范围改为仅 DS |
