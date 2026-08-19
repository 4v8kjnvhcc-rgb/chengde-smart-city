# AEAI ESB — 获取 Token 与创建消费者接口文档

| 属性 | 说明 |
|------|------|
| **文档范围** | 接口 1：获取 Token；接口 2：创建消费者 |
| **归属** | 外购沈阳数通畅联 AEAI ESB · SMC / 网关 |
| **对应模块** | M001～M019（服务总线）、本平台 M214 |
| **上位文档** | [`D03-ESB集成说明.md`](../D03-ESB集成说明.md)、[`ESB接口说明文档.docx`](ESB接口说明文档.docx) |
| **编制日期** | 2026-08-18 |
| **版本** | V1.0 |

> **安全**：`appPwd`、`client_secret`、`customerId` 为敏感信息。本文只用占位/样例，**禁止**把现场明文或真实密文写入仓库。现场值放 `esb.env.local`（已 `.gitignore`）。

---

## 0. 调用顺序

两个接口必须按顺序调用：**先拿 Token，再创建消费者**。接口 2 请求头 `token` = 接口 1 响应正文（纯文本 Token 字符串）。

```
1) POST  …/ApiAuthenticater/security/authenticate
      Query: appCode、appPwd
      响应正文: Token 字符串（非 JSON）
           │
           ▼  放入请求头 token
2) POST  …/External/services/Gateway/consumer/create
      Header: token、Content-Type: application/json
      Body:   { "clientName": "…" }
      响应 JSON: client_id / client_secret / customerId
           │
           ▼
   后续业务网关请求头使用 client_id、client_secret
   「API 增加消费者」接口使用 customerId
```

| 步骤 | 接口 | 产出 | 谁用 |
|:----:|------|------|------|
| 1 | 获取 Token | 纯文本 `token` | 接口 2 的请求头 `token` |
| 2 | 创建消费者 | `client_id`、`client_secret`、`customerId` | 业务网关请求头；后续「API 增加消费者」 |

---

## 1. 获取 Token

### 1.1 接口概述

调用 SMC 认证服务，校验应用编码与加密密码，换取访问 ESB 网关所需的 Token。

| 项 | 内容 |
|----|------|
| **接口作用** | 获取访问 ESB 网关的有效 Token |
| **请求方法** | `POST` |
| **Body** | **无**；参数全部放在 URL Query |
| **响应类型** | `text/plain`（直接返回 token 字符串，**非 JSON**） |

**请求地址（现场 / 承德联调）**

```
http://10.10.10.61:7000/SMC/services/ApiAuthenticater/security/authenticate
```

其它环境只改主机与端口，路径不变。

### 1.2 请求参数（Query）

| 参数名 | 类型 | 必填 | 示例值 | 说明 |
|--------|------|:----:|--------|------|
| `appCode` | String | 是 | `ESB` | 应用编码（SMC 应用管理中的唯一编码） |
| `appPwd` | String | 是 | `encrypt-<密文>` | **加密后**的应用密码，一般以 `encrypt-` 为前缀 |

**完整 URL 示例**

```
POST http://10.10.10.61:7000/SMC/services/ApiAuthenticater/security/authenticate?appCode=ESB&appPwd=encrypt-<密文>
```

### 1.3 成功响应（HTTP 200）

响应体为 **纯文本 token 字符串**，不要按 JSON 解析。

```text
6F83AEE2-51D6-41BE-88EF-6C2ADB7BB192
```

| 项 | 说明 |
|----|------|
| HTTP 状态 | `200 OK` |
| 正文 | Token 字符串（常见 UUID） |
| 用法 | `body.trim()` 后原样放入接口 2 请求头 `token` |

### 1.4 调用示例

```bash
TOKEN=$(curl -sS -X POST \
  "http://10.10.10.61:7000/SMC/services/ApiAuthenticater/security/authenticate?appCode=ESB&appPwd=encrypt-<密文>")
echo "$TOKEN"
```

---

## 2. 创建消费者

### 2.1 接口概述

在 ESB 网关创建消费者调用身份，生成后续业务接口鉴权用的 `client_id`、`client_secret`。

| 项 | 内容 |
|----|------|
| **接口作用** | 创建消费者，生成 `client_id` / `client_secret` |
| **请求方法** | `POST` |
| **前置依赖** | **必须先调接口 1**，取得有效 `token` |
| **Content-Type** | `application/json` |

**请求地址**

```
http://10.10.10.61:7000/External/services/Gateway/consumer/create
```

注意路径前缀是 `/External/services/Gateway`，与接口 1 的 `/SMC/services` 不同。

### 2.2 请求头

| Header | 类型 | 必填 | 示例值 | 说明 |
|--------|------|:----:|--------|------|
| `token` | String | 是 | `6F83AEE2-51D6-41BE-88EF-6C2ADB7BB192` | **接口 1 返回的 Token 原文** |
| `Content-Type` | String | 是 | `application/json` | 请求报文类型 |

`token` 不要加 `Bearer ` 前缀，与接口 1 正文一致即可。

### 2.3 请求 Body

```json
{
  "clientName": "测试低保系统"
}
```

| 字段名 | 类型 | 必填 | 示例 | 说明 |
|--------|------|:----:|------|------|
| `clientName` | String | 是 | `测试低保系统` | 消费者名称，自定义唯一标识 |

### 2.4 成功响应（HTTP 200）

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
| `data.customerId` | String | 消费者 ID（加密串），后续 **「API 增加消费者」** 接口使用 |
| `data.client_secret` | String | 消费者密钥，**调用业务网关接口时放请求头** |
| `data.client_id` | String | 消费者 ID，**调用业务网关接口时放请求头** |

上表 JSON 为文档样例，联调以现场返回为准；`client_secret` / `customerId` 须保密，勿提交 Git。

### 2.5 调用示例

```bash
# TOKEN 来自接口 1
curl -sS -X POST \
  "http://10.10.10.61:7000/External/services/Gateway/consumer/create" \
  -H "token: ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"clientName":"测试低保系统"}'
```

---

## 3. 两接口联调检查单

- [ ] 接口 1：POST + Query（`appCode`、`appPwd`），无 JSON Body
- [ ] 接口 1：成功响应按 **纯文本** 解析，得到 Token
- [ ] 接口 2：请求头 `token` = 接口 1 正文（无 `Bearer`）
- [ ] 接口 2：`Content-Type: application/json`，Body 含唯一 `clientName`
- [ ] 接口 2：`code` 为 `"200"`，保存 `client_id`、`client_secret`、`customerId`
- [ ] 后续业务网关：请求头带 `client_id`、`client_secret`（字段名以现场业务接口文档为准）
- [ ] `appPwd` / `client_secret` 不入库、不写进前端

---

## 4. 环境变量（建议，勿入库）

```
ESB_SMC_BASE=http://10.10.10.61:7000
ESB_APP_CODE=ESB
ESB_APP_PWD=encrypt-<密文>
```

Token、`client_id`、`client_secret` 由调用过程产生，建议缓存到本平台 Redis（对照 D07 ESB Token、D08 `TC-M214-004`），不要写死在代码里。

---

## 5. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V1.0 | 2026-08-18 | 将获取 Token、创建消费者两接口合订；接口 2 的 `token` 头取自接口 1 响应 |
