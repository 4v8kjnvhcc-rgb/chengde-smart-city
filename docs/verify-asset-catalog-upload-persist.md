# 资产目录附件：宿主机落盘验证（生产 .57）

> 背景：附件须落在宿主机磁盘（默认 `/data/smart-city/uploads`），重建容器不丢文件。生产机**禁止** `docker compose ... --build`（无源码目录会报 `platform-backend not found`）。

---

## 一、部署后还要不要再跑命令？

| 动作 | 是否还要做 |
|------|------------|
| `mkdir` / `chmod` 宿主机上传目录 | **否**（已建过一次即可） |
| 再执行一遍 `prod_up_app.sh` / `compose up` | **否**（重建容器本身就是部署） |
| `up -d --build` | **禁止** |
| 业务验收 + 宿主机看文件 | **要**（见下文） |

---

## 二、前置条件（部署时已做则可跳过）

在应用机（`.57`）上确认：

1. 宿主机目录存在：`/data/smart-city/uploads`（权限建议 `755`）
2. `compose/prod-app.yml` 含挂载：`${UPLOAD_HOST_DIR:-/data/smart-city/uploads}:/data/uploads`
3. `compose/prod-app.env`（或等价）含：
   - `APP_UPLOAD_DIR=/data/uploads`
   - （可选）`UPLOAD_HOST_DIR=/data/smart-city/uploads`
4. 已用**无 `--build`** 方式重建过容器，例如：
   ```bash
   cd /opt/chengde/chengde-smart-city   # 按实际路径
   ./scripts/prod_up_app.sh
   # 或：
   docker compose -f compose/prod-app.yml --env-file compose/prod-app.env up -d
   ```

---

## 三、验证步骤（重新部署完成后执行）

### 1. 容器状态

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep sc_app
```

期望：`sc_app_backend`、`sc_app_web` 为 `Up` / `healthy`（web 依赖 backend healthy）。

异常时：

```bash
docker logs sc_app_backend --tail 100
```

### 2. 挂载是否生效

```bash
docker inspect sc_app_backend --format '{{range .Mounts}}{{.Source}} -> {{.Destination}}{{"\n"}}{{end}}'
```

期望至少一行：

```text
/data/smart-city/uploads -> /data/uploads
```

### 3. 页面上传

1. 浏览器打开门户（如 `http://10.10.10.57:9087/bigdata-web`）
2. 进入**资产目录登记**相关表单，选择附件并上传
3. 期望：上传成功（有成功提示或附件名出现在列表）

> 若仅更新了 compose 挂载、**未换带「下载/相对路径」新镜像**：上传落盘仍应可用；点附件下载可能仍不可用，属预期。

### 4. 宿主机落盘核对（核心）

上传成功后，在 `.57` 执行：

```bash
ls -la /data/smart-city/uploads/asset-catalog/
```

期望：出现本次上传的文件（文件名一般带时间戳/随机后缀）。

容器内对照（可选）：

```bash
docker exec sc_app_backend ls -la /data/uploads/asset-catalog/
```

两边应看到**同一批文件**（宿主机与容器为同一绑定目录）。

### 5. 持久化抽查（可选）

```bash
# 记下某个文件名后，仅重建 backend（仍不要 --build）
docker compose -f compose/prod-app.yml --env-file compose/prod-app.env up -d --force-recreate backend

# 再查宿主机，文件应仍在
ls -la /data/smart-city/uploads/asset-catalog/
```

---

## 四、常见失败对照

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| `platform-backend not found` | 现场加了 `--build` | 去掉 `--build`，只用已 load 的镜像 + `up -d` |
| `sc_app_backend` unhealthy | 连库/中间件/启动失败 | `docker logs sc_app_backend --tail 100` |
| 页面上传失败，宿主机无文件 | 挂载未生效或旧容器未重建 | 重做「挂载检查」；确认 yml/env 已同步后再 `up -d` |
| 宿主机无 `asset-catalog` 目录 | 尚未有成功上传，或写到了别的路径 | 确认 `APP_UPLOAD_DIR` 与挂载目标一致；看 backend 日志 |
| 重建容器后文件消失 | 未绑定宿主机（只写容器层） | 配置 `UPLOAD_HOST_DIR` 绑定挂载后重建 |

---

## 五、验收结论模板（可复制）

```text
验证日期：
验证人：
应用机：10.10.10.57（路径：/opt/chengde/chengde-smart-city）

[ ] docker ps：sc_app_backend / sc_app_web 正常
[ ] inspect：/data/smart-city/uploads -> /data/uploads
[ ] 页面上传成功
[ ] 宿主机可见：/data/smart-city/uploads/asset-catalog/<文件>
[ ] （可选）force-recreate 后文件仍在

结论：通过 / 不通过
备注：
```

---

## 六、与「换新镜像」的边界

| 目标 | 是否必须换镜像 |
|------|----------------|
| 附件落到宿主机、重建不丢 | **否**，更新 compose/env + 重建容器即可 |
| 上传失败明确提示、点附件下载、相对路径落库 | **是**，办公机构建 arm64 镜像 → `.57` `docker load` → 再 `prod_up_app.sh` |
