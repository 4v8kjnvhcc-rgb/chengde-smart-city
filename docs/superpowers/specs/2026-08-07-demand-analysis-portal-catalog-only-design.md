# 数据需求分析：仅匹配门户目录设计

日期：2026-08-07  
状态：已批准并实现  
范围：供需对接系统 · 数据需求分析（M021）

## 1. 背景与目标

当前 `analyzeDemand` / `searchResourceCandidates` 除查询部门数据共享门户的 `biz_catalog_item` 外，还会扫描数据资产管理系统库表（`IngDataTable`）与服务总线接口（`BizEsbFlow`）。这与业务口径不符。

**目标：** 需求分析只根据所选组织机构，判断该组织已发布到部门数据共享门户的目录是否满足需求；不查找数据资产管理系统、不查找服务总线。

## 2. 业务规则

1. **数据源唯一：** 只读 `biz_catalog_item`（`publishStatus = PUBLISHED`）。
2. **组织必选：** 分析前必须选定组织机构（页面「分发部门」）；以其作为 `providerOrg` 过滤目录。
3. **两步匹配：**
   - 若该组织下无已发布门户目录 → `evalStatus = UNMATCHED`，说明「该组织暂无已发布门户目录」。
   - 若有目录 → 用需求信息与目录字段打分，返回可匹配目录列表及匹配度。
4. **满足需求时：** 可选用目录并跳转部门数据共享门户申请；不满足则继续分发/退回/督办等供需流程。

## 3. 组织机构来源

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1 | 分析页所选「分发部门」 | `assigneeOrg` / 请求参数 `providerOrg` |
| 2 | 需求 `formPayload.providerOrg` | 可预填选择框 |
| 3 | 已写入的 `demand.assigneeOrg` | 再次分析时复用 |

未选组织时：接口返回 400，提示先选择组织机构；前端禁用「智能匹配」或弹提示。

`providerOrg` 与目录字段按**机构名称字符串**匹配（与现有 `biz_catalog_item.providerOrg` 存法一致）；比较时 trim，大小写不敏感。

## 4. 匹配字段与打分

### 4.1 需求侧信号（拼成匹配语料）

从 `BizDataDemand` 与 `formPayload` 抽取：

- `demandTitle` / `formPayload.dataName`
- `demandContent` / `formPayload.demandBasis` / `formPayload.usageScenario`
- `formPayload.dataItems`（数组拼成文本）
- `formPayload.shareProvideMode` / `supplyMode`（共享方式）
- `modelFields`（结构化字段名，若有）

### 4.2 目录侧信号

- `title`、`description`
- `shareModes`（与需求共享方式比对加分）
- `catalogCode`、`themeName`（弱匹配）

### 4.3 打分规则（保持简单、可解释）

在现有 token 命中逻辑上扩展为加权：

| 维度 | 权重（示意） | 规则 |
|------|--------------|------|
| 标题/数据名称 vs `title` | 高 | 子串/分词命中 |
| 描述/场景/依据/数据项 vs `description`+`title` | 中 | token 命中累计 |
| 共享方式 vs `shareModes` | 低～中 | 任一模式命中则加分 |
| 主题名弱匹配 | 低 | 可选 |

得分归一到 0～100；`>= 30` 视为可建议匹配（`MATCHED`），`> 0` 且 `< 30` 为 `PARTIAL`，`0` 为 `UNMATCHED`。  
写回：`matchScore`、`matchedCatalogId`（最高分目录）、`evalStatus`、`fulfillPath`（高匹配建议门户授权路径，弱/无匹配建议归集补数）、`analysisNote`、`analysisPayload.candidates`。

候选仅含 `resourceType = CATALOG`；去掉 TABLE / API。

## 5. API 变更

### 5.1 `POST /exchange/supply/demands/{id}/analyze`

- Body 增加可选（建议必传）：`providerOrg`（或 `assigneeOrg`）。
- 若 body 有组织：写入/覆盖 `demand.assigneeOrg` 后再匹配。
- 仅在该组织 `providerOrg` 的已发布目录中匹配。
- 响应 `candidates` 仅目录；`relationGraph` 仅 DEMAND ↔ CATALOG。

### 5.2 `GET /exchange/supply/resource-search`

- 增加可选参数 `providerOrg`。
- 仅查 `biz_catalog_item`；忽略 `resourceType=TABLE|API`（或仅接受 `CATALOG`/`ALL`，结果仍只有目录）。
- 传入 `providerOrg` 时按提供方过滤。

## 6. 前端变更（SupplyDemandView 分析区）

1. 「资源目录快速查询」文案与筛选项：去掉库表/接口类型；关键词仅目录；支持按已选组织过滤查询。
2. 关联关系展示：去掉「库表 N 个 / 接口 N 个」，只保留目录数量与目录边。
3. 智能匹配候选标题改为「门户目录匹配候选」；仅目录行，「选用」「跳转门户申请」。
4. 「重新智能匹配」前校验已选分发部门；调用 analyze 时带上 `providerOrg`/`assigneeOrg`。
5. 切换组织后可提示重新匹配。

## 7. 文档同步

- 更新 `docs/repair/供需对接.md`：需求分析仅检索门户已发布目录，按所选组织匹配；删除「库表、接口」表述。
- 可选：在 `docs/repair/供需对接-fix-notes.md` 追加本条说明。

## 8. 非目标

- 不改造数据资产登记、ESB、门户订阅申请后端逻辑。
- 不引入外部 NLP / 向量检索。
- 不改变需求确认、分发、督办、供给查看主流程状态机（仅改分析数据源与打分输入）。

## 9. 验收标准

1. 未选组织时无法完成智能匹配，有明确提示。
2. 选中某组织后，候选全部来自该组织 `providerOrg` 的已发布 `biz_catalog_item`。
3. 网络抓包/SQL 层不再因分析查询 `IngDataTable` / `BizEsbFlow`。
4. 有命中时展示目录名称与匹配度；可选用并跳转门户。
5. 该组织无目录或零分时，`UNMATCHED` 且说明清晰。

## 10. 实现落点（预估）

| 层 | 文件 |
|----|------|
| 后端 | `SupplyDemandService.java`（`analyzeDemand`、`searchResourceCandidates`、`searchResources`、`buildRelationGraph`） |
| 后端 | `SupplyDemandController.java`（analyze body、resource-search 参数） |
| 前端 | `SupplyDemandView.vue`（分析区 UI 与调用参数） |
| 文档 | `docs/repair/供需对接.md` 等 |
