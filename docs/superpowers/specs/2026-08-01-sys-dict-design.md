# 平台统一数据字典 `sys_dict` 设计

日期：2026-08-01  
状态：已确认（方案一），实现中；**本轮改动不提交 Git**。

## 目标

- 新建平台统一字典：`sys_dict` + `sys_dict_item`
- 将现有 `ana_platform_config`（平台参数）迁入后 **DROP** 旧表
- 归集/数据资产登记业务字典 **`ing_dict` / `ing_dict_item` 保持不变**
- 通用支撑「字典管理」升级为完整字典 CRUD

## 表结构

### sys_dict

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AI | |
| dict_code | VARCHAR(64) UNIQUE | 如 AUTH / SYSTEM / BI |
| dict_name | VARCHAR(128) | 显示名 |
| remark | VARCHAR(512) | |
| sort_order | INT | 默认 0 |
| status | TINYINT | 1 启用 / 0 停用 |
| created_at / updated_at | DATETIME | |

### sys_dict_item

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK AI | |
| dict_id | BIGINT | FK 逻辑关联 |
| item_key | VARCHAR(128) | 原 config_key |
| item_value | VARCHAR(512) | 原 config_value |
| item_label | VARCHAR(256) | 原 description |
| sort_order | INT | |
| status | TINYINT | 1/0 |
| remark | VARCHAR(512) | |
| created_at / updated_at | DATETIME | |
| UNIQUE(dict_id, item_key) | | |

## 迁移

1. 建表  
2. 按 `config_group` 建字典类型，按行插入字典项（ACTIVE→1）  
3. `DROP TABLE ana_platform_config`  
4. 不写权限菜单时可沿用 `isAuthenticated` + `SYSTEM_ADMIN` / `system:uum:view`；可选补 `system:dict:*`

## API

前缀：`/api/v1/system/dicts`

- GET/POST `/` — 列表 / 新建类型  
- PUT/DELETE `/{id}` — 更新 / 删除类型（有项时禁止删）  
- GET/POST `/{id}/items` — 项列表 / 新建项  
- PUT/DELETE `/items/{itemId}` — 更新 / 删除项  
- GET `/code/{dictCode}/items` — 按编码取项  

认证中心兼容：

- GET `/api/v1/system/uum/auth-configs` → AUTH 字典项（字段映射为 configKey/configValue/description）  
- PUT `/api/v1/system/uum/auth-configs/{id}` → 更新 item_value  

删除：`PUT /api/v1/analytics/platform/configs/{id}`；overview 不再返回 configs。

## 前端

- `SysDictManagePanel.vue` 嵌入 `?tab=sys.dict`  
- 左类型右项完整 CRUD；保留跳转归集 M050 说明  
- 认证页保存走 uum auth-configs PUT  

## 非目标

- 不改 `ing_dict*`  
- 不做双写 / analytics 兼容转发  
- 本轮不 git commit / push  
