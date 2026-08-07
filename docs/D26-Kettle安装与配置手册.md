# Kettle 安装与配置手册


| 属性       | 说明                                                                                   |
| -------- | ------------------------------------------------------------------------------------ |
| **文档编号** | **D26**                                                                              |
| **文档版本** | V1.0                                                                                 |
| **编制日期** | 2026-08-07                                                                           |
| **文档来源** | 长河飞书：[Kettle安装与配置（新）](https://igalaxycn.feishu.cn/docx/N7Gpd1zIAoShjox5fAYceoRNnMf)  |
| **适用范围** | Kettle（PDI）原生安装、资源库、etl-gen / etl-executor 配置                                        |
| **对照**   | 本项目 Docker Carte 方案见 [D23](D23-生产环境部署手册.md)；门户 `KETTLE_URL` 见 `compose/prod-app.env` |


---



## 0. 与本项目门户的关系


| 路径                     | 说明                                                                                                           |
| ---------------------- | ------------------------------------------------------------------------------------------------------------ |
| **Docker Carte（推荐生产）** | `compose/prod-mid.yml` profile `etl`，默认宿主机端口 **18081**；门户配置 `KETTLE_URL` / `KETTLE_USER` / `KETTLE_PASSWORD` |
| **本手册（原生 + 资源库）**      | 对应长河既有 **PDI + 数据库资源库 + etl-gen.war / etl-executor.war** 部署方式；不强制 Docker                                     |


门户只认 **HTTP 地址 + 账号**。若现场按本手册装原生 Kettle/Carte，只要把 `KETTLE_URL` 指到可达地址即可；**业务 Java/Vue 一般无需改代码**。

飞书原文中的驱动 jar、`repositories.xml`、`kettle.properties` 等附件需从原文档下载，本文仅保留文件名引用。

---



## 1. 前言

本文档说明 Kettle 工具的安装与配置。在传统图形界面（Spoon）配置之外，增加了 **etl-gen** 程序自动：

- 创建资源库
- 生成资源库配置文件 `repositories.xml`
- 创建自定义任务 ETL 路径

**建议优先使用 etl-gen 自动生成**：新部署项目配置好 `etl-gen.war` 并启动后即可生成相关配置，无需另行运行 Kettle 图形程序；在没有桌面版跳板机或网络不可达时也可操作。

---



## 2. 安装 Kettle

1. 将 `pdi-ce-xxxxx.zip` 上传到服务器目标目录（如 `/data`）。
2. 解压，并记住解压后的路径（即为 Kettle 安装目录）：

```bash
unzip pdi-ce-9.6.0.0.zip
cd data-integration
pwd
# 示例：/usr/local/data-integration
# 该路径后续写入 etl-executor、push-executor 配置中的 kettle.home
```

1. 添加系统环境变量 `PENTAHO_JAVA_HOME`，值为 JDK 根目录。
2. 将数据库连接驱动 jar 拷贝到 `data-integration/lib` 目录。



### 2.1 常用数据库驱动（附件名）


| 数据库                     | 驱动文件（飞书附件）                                           |
| ----------------------- | ---------------------------------------------------- |
| MySQL                   | `mysql-connector-java-8.0.26.jar`                    |
| Oracle                  | `ojdbc8-19.3.0.0.jar`                                |
| DM8                     | `DmJdbcDriver18.jar`                                 |
| GBase                   | `gbase-connector-java-8.3.81.53-build55.2.1-bin.jar` |
| PostgreSQL / KingbaseV8 | `postgresql-42.6.0.jar`                              |


另需准备 `kettle.properties`（用于解决 Kettle 将空字符串当作 null 的问题），见 §6。

---



## 3. 配置资源库



### 3.1 etl-gen 程序生成（推荐）

新版本 `etl-gen.war` 启动时会检查资源库是否已创建；若未创建，会自动初始化 Kettle 资源库。

1. 在 `etl-gen/WEB-INF/classes/application-pro.yml` 中配置资源库（`setting:db-meta:repository:`），**须填写正确**：

```yaml
setting:db-meta:repository:name: repo207      # 资源库名称（重要）
setting:db-meta:repository:host: 172.16.16.207 # 数据库服务器 IP
setting:db-meta:repository:port: 3306         # 端口
setting:db-meta:repository:db: kettle         # 库名（需事先建好空库，名称可自定义）
setting:db-meta:repository:username: root
setting:db-meta:repository:password: 123456
```

1. 启动 etl-gen 程序完成创建。
2. 用数据库客户端连接该库，确认资源库表已创建成功。



### 3.2 手动使用 Kettle 程序生成

1. 在可连通 Kettle 数据库的图形化操作系统上安装 Kettle。
2. 启动客户端：Windows 用 `Spoon.bat`，Linux 用 `spoon.sh`。
3. 点击右上角 **Connect** → **Other Repositories**。
  - Windows 若看不到 Connect，见 §8.2。
4. 选择 **Database Repositories** → **Get Started**。
5. 填写资源库显示名（示例：`kettle`，可自定义；后续执行器须配置为同名）。
6. 在 **database connection** 中 **Create New Connection**：
  - 连接名称（自定义）
  - 类型：MySQL；方式：JDBC
  - 主机、库名（建议空库 `kettle`）、端口、用户名、密码
  - **测试**成功后确定
7. 返回已创建连接列表后点 **Back**。
8. 勾选 **Launch connection on startup**，点右下角 **Finish**。
9. 等待创建完成，再点 **Finish**。
10. 再次确认库表已创建；测试连接资源库（默认用户名/密码多为 `admin` / `admin`）：
  - 点 Connect 右侧下拉 → 选择已配置资源库
    - Login 窗口填用户名密码 → Connect
    - 成功后 Connect 显示为 `用户名|资源库名称`

---



## 4. 生成资源库配置文件 `repositories.xml`



### 4.1 etl-gen 下载获取（推荐）

新版本 `etl-gen.war` 可按 `application-pro.yml` 中 `setting:db-meta:repository:` 生成 `repositories.xml`。启动 etl-gen 后访问：

```text
http://{etl-gen部署地址}:{etl-gen部署端口}/etl-gen/xml
```

下载获取。

### 4.2 手动配置

自行创建 `repositories.xml`（样例见飞书附件 `repositories.xml`）。有注释处按现场修改。

**加密数据库密码**（在 Kettle 安装目录执行）：

```bash
./encr.sh 数据库密码
```



### 4.3 直接拷贝 Spoon 生成的配置

按 §3.2 生成资源库后，在**当前用户**根目录的 `.kettle` 下取 `repositories.xml`：


| 系统         | 典型路径                     |
| ---------- | ------------------------ |
| Linux root | `/root/.kettle`          |
| Linux 普通用户 | `/home/{用户名}/.kettle`    |
| Windows    | `C:\Users\{用户名}\.kettle` |


---



## 5. 上传资源库配置到服务器

```bash
cd /root          # 或对应用户家目录
mkdir -p .kettle
# 将 repositories.xml 上传到该 .kettle 目录
```

---



## 6. 上传 `kettle.properties`（空串/null 问题）

在服务器用户 `.kettle` 目录创建或拷贝 `kettle.properties`（内容见飞书附件），用于避免 Kettle 把空字符串当成 null。

---



## 7. 在资源库中增加自定义任务目录



### 7.1 etl-gen 创建（推荐）

新版本 `etl-gen.war` 在检查完资源库后，会创建自定义任务 ETL 文件存放路径。确认配置正确后运行即可。

### 7.2 手动使用 Kettle 创建

1. 连接资源库
2. 打开探索资源库
3. 右键空白处 → **新建目录**
4. 依次新建：`CollCustom`、`FuseCustom`、`GovCustom`

---



## 8. 修改 etl-executor 的配置

编辑 `etl-executor.war` 内 `WEB-INF/classes/application-pro.yml`（原文写作 elt-executor，以实际包名为准）：

1. `kettle.home`：改为 Kettle 安装目录（错误则无法执行 ETL）。
2. `kettle.repository.name`：改为 §3 配置的资源库名称（错误则无法执行 ETL）。
3. `kettle.repository.user` **/** `kettle.repository.pass`：默认多为 `admin`，一般可不改。

---



## 9. 常见问题



### 9.1 运行报 `/bin/bash^M: bad interpreter: No such file or directory`

**原因**：Windows 编辑的脚本带 CR+LF，Linux 只需 LF。

**处理**（在 Kettle 安装目录）：

```bash
sed -i "s/\r//" *.sh
```



### 9.2 图形客户端右上角找不到资源库 Connect

**原因 1**：默认编译为提升效率关闭了部分图形功能。  

**处理**：打开安装目录 `classes` 下 `kettle-lifecycle-listeners.xml`、`kettle-registry-extensions.xml`，去掉相关 `<!--` `-->` 注释。

**原因 2**：用户目录 `.kettle/repositories.xml` 含中文导致读取失败。  

**处理**：删除后重新连接资源库，或将文件中中文改为英文。

---

