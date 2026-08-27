# AEAI ESB — 获取 Token 与注册服务接口文档

| 属性 | 说明 |
|------|------|
| **文档范围** | **接口 1** 获取 Token · **接口 2** 注册服务（gatewayService）· **接口 3** 创建消费者；以及 OAuth2 凭证回填 |
| **归属** | 外购沈阳数通畅联 AEAI ESB · 业务系统接口类资源注册 |
| **上位文档** | [`D03-ESB集成说明.md`](../D03-ESB集成说明.md)、[`库表接口调用流程.md`](库表接口调用流程.md) |
| **来源** | `接口调用说明.docx`（V1.0） |
| **编制日期** | 2026-08-27 |
| **版本** | V1.1 |

> **安全**：`appPwd`、Token、`param` 中的业务数据为敏感信息。本文只用占位/样例，**禁止**把现场明文或真实密文写入仓库。现场值放环境变量 / `esb.env.local`（已 `.gitignore`）。

---

## 目录

1. [调用顺序](#1-调用顺序)
2. [接口 1：获取 Token](#2-接口-1获取-token)
3. [接口 2：注册服务（gatewayService）](#3-接口-2注册服务gatewayservice)
4. [接口 3：创建消费者](#4-接口-3创建消费者)
5. [回填字段](#5-回填字段)
6. [联调检查单](#6-联调检查单)
7. [修订记录](#7-修订记录)

---

## 1. 调用顺序

接口类资源注册须按下列顺序调用；**接口 2、3 的请求头 `token` = 接口 1 响应正文（同一 Token）**。

```
┌─────────────────────────────────────────────────────────────┐
│  业务系统接口注册到 ESB                                       │
└───────────────────────────┬─────────────────────────────────┘
                            ▼
1) POST  …/SMC/services/ApiAuthenticater/security/authenticate
      Query: appCode、appPwd
      响应正文: Token（纯文本，非 JSON）
                            │
                            ▼  Header: token（同一 Token）
2) POST  …/External/services/Gateway/gatewayService
      Body: code / path / param / method
      响应: data.path / data.method / data.param
                            │
                            ▼  回填 URL = ESB根 + path
                            │  Header: 同一 token
3) POST  …/External/services/Gateway/consumer/create
      Body: { "clientName": "应用系统名称或目录标题" }
      响应: client_id / client_secret / customerId
                            │
                            ▼
              回填 oauthClientId / oauthClientSecret
```

| 步骤 | 接口 | 输入 | 产出 | 下一步用法 |
|:----:|------|------|------|------------|
| 1 | 获取 Token | `appCode`、`appPwd` | Token 字符串 | 步骤 2、3 请求头 `token` |
| 2 | 注册服务 | **Token** + 接口元数据 | `path`、`method`、`param` | 回填 URL = `{ESB根}{path}`；请求方式用 `method` |
| 3 | 创建消费者 | **同一** Token + `clientName` | `client_id`、`client_secret` | 回填 Oauth2 两字段 |

**硬约束**：

- 步骤 2、3 **必须先调接口 1**，取得有效 Token；步骤 2、3 必须使用**同一个** Token。
- 请求头字段名为 **`token`**，值为接口 1 返回的 Token **原文**（**不加** `Bearer ` 前缀）。
- 步骤 2、3 的路径前缀与接口 1 不同：

| 接口 | 路径前缀 |
|------|----------|
| 获取 Token | `/SMC/services/...` |
| 注册服务 / 创建消费者 | `/External/services/Gateway/...` |

---

## 2. 接口 1：获取 Token

### 2.1 接口概述

| 项 | 内容 |
|----|------|
| **接口用途** | 获取调用 ESB 网关接口所需的 Token（文档中称 `tokenId`） |
| **请求方法** | `POST` |
| **Body** | **无**；参数全部放在 URL Query |
| **响应类型** | `text/plain`（直接返回 Token 字符串，**非 JSON**） |

**请求地址（文档样例）**

```
http://10.216.131.100:7000/SMC/services/ApiAuthenticater/security/authenticate
```

**承德联调地址（与库表流程一致）**

```
http://10.10.10.61:7000/SMC/services/ApiAuthenticater/security/authenticate
```

其它环境只改主机与端口，路径不变。

### 2.2 请求参数（Query）

| 参数名 | 类型 | 必填 | 示例值 | 说明 |
|--------|------|:----:|--------|------|
| `appCode` | String | 是 | `ESB` | 应用系统编码（SMC 应用管理中的唯一编码） |
| `appPwd` | String | 是 | `encrypt-<密文>` 或明文（联调） | 应用系统密码；生产环境一般为 **加密后** 密码，前缀 `encrypt-` |

### 2.3 成功响应（HTTP 200）

响应体为 **纯文本 Token 字符串**，不要按 JSON 解析。

```text
203EEC0F-0137-42AF-ABCF-535289A01979
```

| 项 | 说明 |
|----|------|
| HTTP 状态 | `200 OK` |
| 正文 | Token 字符串（常见 UUID 形态） |
| 用法 | `body.trim()` 后原样放入接口 2、接口 3 请求头 `token` |

### 2.4 调用样例

**入参（Query）**

| 参数 | 值 |
|------|-----|
| `appCode` | `MDM`（文档样例）/ `ESB`（承德联调） |
| `appPwd` | `123`（文档样例；生产用加密密文） |

**出参**

```text
203EEC0F-0137-42AF-ABCF-535289A01979
```

**curl 示例**

```bash
TOKEN=$(curl -sS -X POST \
  "http://10.10.10.61:7000/SMC/services/ApiAuthenticater/security/authenticate?appCode=ESB&appPwd=encrypt-<密文>")
echo "$TOKEN"
```

---

## 3. 接口 2：注册服务（gatewayService）

### 3.1 接口概述

| 项 | 内容 |
|----|------|
| **接口用途** | 根据入参将业务系统接口注册到 ESB 中 |
| **请求方法** | `POST` |
| **前置依赖** | **必须先调接口 1**，取得有效 Token |
| **Content-Type** | `application/json` |

**请求地址（文档样例 / 承德联调）**

```
http://10.10.10.61:7000/External/services/Gateway/gatewayService
```

### 3.2 请求头

| Header | 类型 | 必填 | 示例值 | 说明 |
|--------|------|:----:|--------|------|
| `token` | String | 是 | `<Token>` | **接口 1 返回的 Token 原文**，不加 `Bearer ` |
| `Content-Type` | String | 是 | `application/json` | 请求报文类型 |

### 3.3 请求 Body

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:----:|------|
| `code` | String | 是 | 接口编码（**唯一**） |
| `path` | String | 是 | 业务接口**全路径**（含协议、主机、端口、路径） |
| `param` | String | 是 | 入参结构说明，**JSON 字符串**（需转义引号） |
| `method` | String | 是 | 业务接口 HTTP 方法：`POST` 或 `GET` |

**Body 示例**

```json
{
  "code": "getCommunityStats",
  "path": "http://10.216.131.100:8182/external/ren-fang/getCommunityStats",
  "param": "{\"code\":\"\",\"msg\":\"\",\"data\":[{\"communityId\":0,\"communityName\":\"\",\"gridManagerList\":[{\"gridManagerName\":\"\",\"contact\":\"\"}],\"totalCount\":0,\"averageAge\":0,\"maleCount\":0,\"femaleCount\":0,\"occupationDistribution\":{\"学生\":1}}],\"timestamp\":\"\",\"executionTime\":0}",
  "method": "POST"
}
```

| 字段 | 说明 |
|------|------|
| `code` | ESB 侧唯一标识，如 `getCommunityStats` |
| `path` | 被代理的**原始业务接口**完整 URL |
| `param` | 描述业务接口入参/出参结构的 JSON 字符串（文档样例为嵌套 JSON 转义后写入） |
| `method` | 调用原始业务接口时使用的方法 |

### 3.4 成功响应（HTTP 200）

```json
{
  "code": "200",
  "data": {
    "path": "/External/services/ExternalService/externalservicePost/getCommunityStats",
    "method": "POST",
    "param": "{\"code\":\"\",\"msg\":\"\",\"data\":[{\"communityId\":0,\"communityName\":\"\",\"gridManagerList\":[{\"gridManagerName\":\"\",\"contact\":\"\"}],\"totalCount\":0,\"averageAge\":0,\"maleCount\":0,\"femaleCount\":0,\"occupationDistribution\":{\"学生\":1}}],\"timestamp\":\"\",\"executionTime\":0}"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | String | 返回码；`"200"` 表示注册成功 |
| `data.path` | String | ESB 对外暴露的**相对路径**（相对 ESB 根地址） |
| `data.method` | String | 调用 `data.path` 时应使用的 HTTP 方法 |
| `data.param` | String | 注册时提交的入参结构（回显） |

失败时返回 `error` 或业务错误码（以现场响应为准）。

**完整调用地址示例**

```
http://10.216.131.100:7000/External/services/ExternalService/externalservicePost/getCommunityStats
```

> `path` 中 `externalservicePost` / `externalserviceGet` 等前缀与 `method` 对应；具体形态以当次响应为准。

### 3.5 调用示例

```bash
# TOKEN 来自接口 1
curl -sS -X POST \
  "http://10.10.10.61:7000/External/services/Gateway/gatewayService" \
  -H "token: ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "getCommunityStats",
    "path": "http://10.216.131.100:8182/external/ren-fang/getCommunityStats",
    "param": "{\"code\":\"\",\"msg\":\"\",\"data\":[{\"communityId\":0,\"communityName\":\"\"}],\"timestamp\":\"\",\"executionTime\":0}",
    "method": "POST"
  }'
```

---

## 4. 接口 3：创建消费者

### 4.1 接口概述

在 ESB 网关创建消费者调用身份，生成后续业务接口鉴权用的 `client_id`、`client_secret`。

| 项 | 内容 |
|----|------|
| **接口用途** | 创建消费者，生成 `client_id` / `client_secret` |
| **请求方法** | `POST` |
| **前置依赖** | **必须先调接口 1**；与接口 2 **共用同一 Token** |
| **Content-Type** | `application/json` |

**请求地址**

```
http://10.10.10.61:7000/External/services/Gateway/consumer/create
```

### 4.2 请求头

| Header | 类型 | 必填 | 示例值 | 说明 |
|--------|------|:----:|--------|------|
| `token` | String | 是 | `<Token>` | **接口 1 返回的 Token 原文**（与接口 2 相同） |
| `Content-Type` | String | 是 | `application/json` | 请求报文类型 |

`token` 不要加 `Bearer ` 前缀。

### 4.3 请求 Body

```json
{
  "clientName": "测试数据共享系统"
}
```

| 字段名 | 类型 | 必填 | 示例 | 说明 |
|--------|------|:----:|------|------|
| `clientName` | String | 是 | `测试数据共享系统` | 消费者名称；本平台优先取申请单「应用系统名称」，否则目录标题 |

### 4.4 成功响应（HTTP 200）

```json
{
  "code": "200",
  "data": {
    "customerId": "inFE2Id6umRCQNMb8+oFHh4QJanMCVhKRPAa7kZWNAmyVwga1m1CJA==",
    "client_secret": "TVhWZGZNZVU4K1E5c1VPR1ovT3VFTEVtWTZJVHdRZFA=",
    "client_id": "C2DC6087-3262-42FB-8C50-21B793730863"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | String | 返回码，`200` 表示创建成功 |
| `data.customerId` | String | 消费者 ID（加密串），写入 `esb_customer_id` |
| `data.client_secret` | String | 回填「用于 Oauth2 服务认证的 client secret 信息」 |
| `data.client_id` | String | 回填「用于 Oauth2 服务认证的 clientid 信息」 |

上表 JSON 为文档样例，联调以现场返回为准；`client_secret` / `customerId` 须保密，勿提交 Git。

### 4.5 调用示例

```bash
# TOKEN 须与调用 gatewayService 时相同（来自接口 1）
curl -sS -X POST \
  "http://10.10.10.61:7000/External/services/Gateway/consumer/create" \
  -H "token: ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"clientName":"测试数据共享系统"}'
```

---

## 5. 回填字段

接口类资源申请提交后，三接口串联完成，结果写入申请单（审批详情「接口信息」即可看到）：

| 回填字段（审批详情「接口信息」） | 来源 |
|----------------------------------|------|
| 接口 URL | `{ESB根}` + 接口 2 返回的 `path` |
| 接口请求方式 | 接口 2 返回的 `data.method` |
| Oauth2 clientid | 接口 3 返回的 `client_id` |
| Oauth2 client secret | 接口 3 返回的 `client_secret` |

| 库字段 / payload | 含义 |
|------------------|------|
| `biz_portal_subscription.api_url` | 完整接口 URL = `{ESB根}{path}` |
| `biz_portal_subscription.api_method` | 如 `GET` / `POST`（来自接口 2 响应） |
| `biz_portal_subscription.oauth_client_id` | 接口 3 的 `client_id` |
| `biz_portal_subscription.oauth_client_secret` | 接口 3 的 `client_secret` |
| `biz_portal_subscription.esb_customer_id` | 接口 3 的 `customerId` |

---

## 6. 联调检查单

**接口 1（获取 Token）**

- [ ] POST + Query（`appCode`、`appPwd`），无 JSON Body
- [ ] 成功响应按 **纯文本** 解析，得到 Token

**接口 2（注册服务）**

- [ ] Header `token` = 接口 1 正文（无 `Bearer`）
- [ ] POST `gatewayService`，`Content-Type: application/json`
- [ ] Body 含 `code`（唯一）、`path`（业务全路径）、`param`（JSON 字符串）、`method`
- [ ] 响应 `code` 为 `"200"`，保存 `data.path`、`data.method`

**接口 3（创建消费者）**

- [ ] Header `token` 与接口 2 **相同**
- [ ] Body 含 `clientName`（应用系统名称）
- [ ] 响应 `code` 为 `"200"`，保存并回填 `client_id`、`client_secret`

**回填**

- [ ] 审批详情「接口 URL / 请求方式 / clientid / client secret」非空
- [ ] URL = `{ESB根}{data.path}`，请求方式以接口 2 响应 `method` 为准

**安全**

- [ ] `appPwd` / Token / `client_secret` / 业务 `param` 不入库明文、不写进前端仓库

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V1.0 | 2026-08-27 | 自 `接口调用说明.docx` 整理：获取 Token + 注册服务（gatewayService）+ 已注册服务调用；明确请求头 `token` 依赖接口 1 |
| V1.1 | 2026-08-27 | 取消「调用已注册服务」；第三步改为创建消费者，回填 Oauth2 `client_id` / `client_secret` |
