# 本机开发（无 Docker）

## 依赖

| 组件 | 路径 / 说明 |
|------|-------------|
| Java | 17+ |
| Node.js | 18+ |
| MySQL | `C:\Program Files\MySQL\MySQL Server 8.4` |
| Redis | `C:\Program Files\Redis`（已通过 winget 安装） |

## 1. 启动 MySQL

**若 `net start MySQL84` 提示「服务名无效」**：说明只装了客户端/未注册服务。请以**管理员**打开 PowerShell：

```powershell
cd e:\myProject\承德
powershell -ExecutionPolicy Bypass -File scripts\setup_mysql_windows.ps1 -ForceReinit
```

**若服务安装仍失败**：不必反复 `-ForceReinit`（会删库）。数据已初始化时直接：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup_mysql_windows.ps1
```

或不用服务，控制台启动 MySQL：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start_mysql_console.ps1
```

（保持该窗口运行，另开终端启动后端。）

**注意**：须用 **Windows 开始菜单 → PowerShell → 以管理员身份运行**，Cursor 内置终端可能没有管理员权限（会出现 remove 成功但 install 失败）。

若服务已存在且名称不同，在「服务」里搜索 **MySQL** 查看实际名称，例如：

```powershell
Get-Service *mysql*
net start <实际服务名>
```

## 2. 创建库

```powershell
cd e:\myProject\承德
& "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -u root -p < scripts\setup_smart_city.sql
```

## 3. 配置账号密码

```powershell
copy local.env.example local.env
# 编辑 local.env，填写 MYSQL_PASSWORD（root 密码）
```

`application-dev.yml` 会读取项目根目录 `local.env`（键值对格式）。

门户 → 考核评估 **票据 SSO**：默认密钥已写入 `application-*.yml` 的 `app.portal-sso.secret`（与考核 `assessment.portal-sso.secret` 相同）。门户配置中「考核评估系统」设地址为考核落地页、SSO 模式「门户票据」、打开方式「新窗口」。生产改地址即可，密钥可用 `PORTAL_SSO_SECRET` 覆盖。

## 4. 启动 Redis

Redis 安装后默认服务名 **Redis**，或：

```powershell
net start Redis
# 或
& "C:\Program Files\Redis\redis-cli.exe" ping   # 应返回 PONG
```

## 5. 启动后端

```powershell
cd .\chengde-smart-city\platform-backend\
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Flyway 自动执行 V1～V3（建表 + 菜单/测试账号）。

## 6. 启动前端

```powershell
cd .\chengde-smart-city\platform-frontend\
npm install
npm run dev
```

门户：`http://localhost:4000`  
账号：`sys_admin` / `Test@12345`

## 菜单 seed 再生

```powershell
python scripts\gen_portal_menu_seed.py
```
