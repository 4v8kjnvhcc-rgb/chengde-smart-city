# OpenViking 接入配置说明（Cursor 完整记忆集成）

| 属性 | 说明 |
|------|------|
| **文档编号** | **D19** |
| **文档版本** | V1.0 |
| **编制日期** | 2026-07-23 |
| **适用范围** | 本机 Windows + Cursor；火山引擎 OpenViking 云服务 |
| **官方文档** | [Cursor 记忆集成](https://github.com/volcengine/OpenViking/blob/main/docs/zh/agent-integrations/12-cursor.md) |
| **关联** | 凭据与 Hooks 为**用户级**（`%USERPROFILE%`），不随本仓库 Git 提交 |

---

## 一、能力分层（先分清）

| 层级 | 作用 | 本机状态 |
|------|------|----------|
| **仅 MCP** | Agent 主动调用 `search` / `remember` / `read` 等 | 已被完整集成替代 |
| **完整记忆集成**（本机目标） | Hook 自动召回/捕获 + MCP 代理 + Rule/Skill | ✅ 已安装 |

完整集成后的工作流：

```text
sessionStart          → 加载用户画像 / 项目记忆索引
beforeSubmitPrompt    → 按当前问题召回，注入 additional_context
stop / preCompact /
sessionEnd            → 捕获对话并触发记忆抽取
MCP（openviking）     → 主动搜索、读取、管理（补充路径）
```

---

## 二、本机关键路径一览

| 用途 | 路径 |
|------|------|
| 云端凭据 | `%USERPROFILE%\.openviking\ovcli.conf` |
| 本地服务样例配置（旧） | `%USERPROFILE%\.openviking\ov.conf`（完整集成以 `ovcli.conf` 为准） |
| Cursor Hooks | `%USERPROFILE%\.cursor\hooks.json` |
| Cursor MCP | `%USERPROFILE%\.cursor\mcp.json` |
| Always-on Rule | `%USERPROFILE%\.cursor\rules\openviking-memory.mdc` |
| Skill | `%USERPROFILE%\.cursor\skills\openviking-memory\` |
| Hook / MCP 运行时 | `%USERPROFILE%\.openviking\agent-integrations\cursor\` |
| 共享运行时库 | `%USERPROFILE%\.openviking\agent-integrations\memory-plugin-shared\lib\` |
| 调试日志（可选） | `%USERPROFILE%\.openviking\logs\cursor-hooks.log` |

> 在本机即：`C:\Users\ming\.openviking\`、`C:\Users\ming\.cursor\`。

---

## 三、云端连接（ovcli.conf）

文件：`%USERPROFILE%\.openviking\ovcli.conf`

```json
{
  "url": "https://api.vikingdb.cn-beijing.volces.com/openviking",
  "api_key": "<火山控制台 API Key，勿提交 Git>"
}
```

| 项 | 说明 |
|----|------|
| `url` | 服务根地址（**不要**带 `/mcp`；代理会拼 `/mcp`） |
| `api_key` | 控制台 Bearer Token；轮换后须同步改此文件并重启 Cursor |
| 控制台 | 火山引擎 VikingDB → OpenViking 实例 → 接入 Cursor |

**禁止**把含真实 `api_key` 的配置提交到本仓库。

---

## 四、Cursor MCP（stdio 代理）

文件：`%USERPROFILE%\.cursor\mcp.json`（完整集成后形态）

```json
{
  "mcpServers": {
    "openviking": {
      "command": "C:/Program Files/nodejs/node",
      "args": [
        "C:\\Users\\ming\\.openviking\\agent-integrations\\cursor\\servers\\mcp-proxy.mjs"
      ],
      "env": {
        "OPENVIKING_INTEGRATION_ID": "openviking-memory",
        "OPENVIKING_INTEGRATION_VERSION": "0.1.2",
        "OPENVIKING_HOOK_SOURCE": "cursor"
      }
    }
  }
}
```

说明：

- 安装器会把早期手写的 `ov-mcp-server`（直连 HTTP URL）迁移为上述 **`openviking` + mcp-proxy**。
- 代理读取 `ovcli.conf`，再访问云端；Node 须 ≥ 18（本机为 Node 24）。

对照检查：**Settings → Tools & MCP** → `openviking` 为 Connected。

---

## 五、Cursor Hooks（自动记忆）

文件：`%USERPROFILE%\.cursor\hooks.json`

| Hook 事件 | 脚本 | 作用 |
|-----------|------|------|
| `sessionStart` | `session-start.mjs` | 加载画像与项目索引 |
| `beforeSubmitPrompt` | `auto-recall.mjs` | 提问前召回并注入上下文 |
| `beforeReadFile` / `beforeShellExecution` | `uri-guard.mjs` | 阻止把 `viking://` 当本地路径 |
| `stop` | `auto-capture.mjs` | 回复结束后捕获对话 |
| `preCompact` | `pre-compact.mjs` | 压缩前提交未处理消息 |
| `sessionEnd` | `session-end.mjs` | 会话结束冲刷 / 抽取 |

命令形态（摘要）：用本机 `node` 执行  
`%USERPROFILE%\.openviking\agent-integrations\cursor\scripts\*.mjs`，并带环境变量  
`OPENVIKING_INTEGRATION_ID=openviking-memory`、`OPENVIKING_HOOK_SOURCE=cursor`。

对照检查：**Settings → Hooks**，确认上述脚本被执行；`beforeSubmitPrompt` 输出中宜出现 `additional_context`。

---

## 六、Rule / Skill

| 类型 | 路径 | 作用 |
|------|------|------|
| Rule（alwaysApply） | `.cursor\rules\openviking-memory.mdc` | 优先使用 Hook 已注入上下文；不足时再调 MCP |
| Skill | `.cursor\skills\openviking-memory\` | 记忆使用约定 |

---

## 七、多项目记忆如何区分

Cursor 用当前窗口的 **`workspace_roots`（项目路径）** 生成 **workspace peer**：

- 规则：路径中非 `[A-Za-z0-9]` 的字符变为 `-`（不做大小写折叠、不规范化）。
- 示例：`E:\myProject\承德` 与 `E:\myProject\OpenViking` → **不同 peer**。
- 全局偏好仍在用户空间 `viking://user/default/...`，可跨项目共享。
- 项目相关会话/记忆挂在 `viking://user/default/peers/<peer>/` 下。

默认召回为 **broad**（当前项目优先，其它项目可能降权出现）。若要 **仅「全局 + 当前项目」**：

```text
OPENVIKING_RECALL_PEER_SCOPE=actor
```

写入用户环境变量后重启 Cursor。关闭按路径分 peer：`OPENVIKING_WORKSPACE_PEER=0`（一般不建议）。

---

## 八、其它软件接入与冲突规避

同一份 `ovcli.conf`（同一 API Key）→ 同一云端用户空间。

| 场景 | 建议 |
|------|------|
| 同一人多用 Cursor / Claude / Trae 等 | 共用 Key；项目靠 workspace peer；全局偏好共享 |
| 官方支持的客户端 | 安装器 `--harness <claude\|codex\|cursor\|trae\|…>`，仍读 `ovcli.conf` |
| 仅 MCP、无 Hook | 能读写，但未必按 workspace peer 自动落库；重要项目事实优先在带 Hook 的客户端产生 |
| 不同人 / 必须硬隔离 | 不同 API Key，或 `ovcli.conf` 中不同 `user` / `actor_peer_id`，或 `OPENVIKING_PEER_ID` |

常用环境变量：

| 变量 | 含义 |
|------|------|
| `OPENVIKING_MEMORY_ENABLED` | 总开关（默认 true） |
| `OPENVIKING_AUTO_RECALL` | 自动召回 |
| `OPENVIKING_AUTO_CAPTURE` | 自动捕获 |
| `OPENVIKING_WORKSPACE_PEER` | 按工作区生成 peer（默认 true） |
| `OPENVIKING_RECALL_PEER_SCOPE` | `actor` = 严格隔离；默认 broad |
| `OPENVIKING_PEER_ID` | 显式 peer，覆盖路径推导 |
| `OPENVIKING_DEBUG=1` | 写 `logs\cursor-hooks.log` |

---

## 九、Windows 安装备注（本机实践）

官方安装器声明支持 **macOS / Linux**；本机通过 **Git Bash + 伪装 `uname=Linux` + 本机 Node** 完成安装（Hooks 内为 Windows 的 `node.exe` 路径，Cursor 可直接调用）。

要点：

1. 凭据选 **火山引擎 OpenViking 云服务**，写入 `ovcli.conf`。
2. 安装源可用 TOS 归档（`ovrelease.tos-cn-beijing.volces.com`）。
3. 安装脚本末尾可能因 Windows 路径反斜杠对 `grep scripts/...` 误报「不完整」；以文件是否存在 + smoke test 为准。
4. 另已安装 **Ubuntu-24.04 WSL**，后续可在 WSL 中升级（注意：`HOME` 需指向 `/mnt/c/Users/ming` 才能写到 Windows Cursor 配置）。

升级 / 卸载（在可用 Linux/Git Bash 伪装环境下）：

```bash
# TOS 升级（示例）
bash install.sh --harness cursor --dist tos --yes \
  --url "https://api.vikingdb.cn-beijing.volces.com/openviking" \
  --api-key "<KEY>"

# 卸载
bash install.sh --harness cursor --dist tos --uninstall --yes
```

官方一键命令见 [12-cursor.md](https://github.com/volcengine/OpenViking/blob/main/docs/zh/agent-integrations/12-cursor.md)（原生 Windows PowerShell **不能**直接跑 `bash <(curl …)`）。

---

## 十、验证清单

1. **完全退出并重启** Cursor。  
2. Settings → **Hooks**：存在 session-start / auto-recall / auto-capture / uri-guard。  
3. Settings → **Tools & MCP**：`openviking` 已连接。  
4. 新建 Agent 会话：说一个临时偏好 → 本轮结束后新开会话询问 → 应能召回。  
5. 可选：`OPENVIKING_DEBUG=1` 后查看 `%USERPROFILE%\.openviking\logs\cursor-hooks.log`。

快速文件存在性检查（PowerShell）：

```powershell
@(
  "$env:USERPROFILE\.openviking\ovcli.conf",
  "$env:USERPROFILE\.cursor\hooks.json",
  "$env:USERPROFILE\.cursor\mcp.json",
  "$env:USERPROFILE\.cursor\rules\openviking-memory.mdc",
  "$env:USERPROFILE\.openviking\agent-integrations\cursor\servers\mcp-proxy.mjs"
) | ForEach-Object { if (Test-Path $_) { "OK  $_" } else { "MISS $_" } }
```

---

## 十一、故障排查摘要

| 现象 | 处理 |
|------|------|
| Hook 不触发 | 完全退出 Cursor；新建 Agent 会话 |
| 有召回但不进回答 | 升级到支持 `beforeSubmitPrompt.additional_context` 的稳定版 Cursor |
| MCP 未连接 | 检查 `ovcli.conf` 的 url/api_key；确认 Node 路径；重启 Cursor |
| 多项目串记忆 | 确认 `OPENVIKING_WORKSPACE_PEER` 未关；必要时设 `OPENVIKING_RECALL_PEER_SCOPE=actor` |
| 与其它软件串号 | 分 Key / 分 `user` / 分 `OPENVIKING_PEER_ID` |

---

## 十二、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V1.0 | 2026-07-23 | 初版：完整记忆集成路径、凭据/MCP/Hooks、多项目与多客户端隔离、Windows 安装备注 |
