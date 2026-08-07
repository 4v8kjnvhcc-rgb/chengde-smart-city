# 督查督办设置设计（方案一，已落地）

日期：2026-08-07

## 配置

| key | 含义 | 默认 | 单位 |
|-----|------|------|------|
| response_deadline_days | 分发/督办后，提供方与需求方确认/反馈时限 | 10 | 自然日 |
| mount_deadline_days | 同意提供后，目录挂载门户时限 | 10 | 自然日 |

## 落点

- 表 `biz_supply_setting`；需求字段 `response_deadline`
- API：`GET/PUT /exchange/supply/supervise-settings`
- 分发/督办写 `response_deadline`；确认写 `catalog_mount_deadline`（改为自然日）
- 供需配置 Tab「督查督办设置」
