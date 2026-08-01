# 归集侧资源目录四模块设计（方案 1）

## 决策摘要

| 项 | 选择 |
|----|------|
| 底座 | 扩展 `ing_resource_registry` / `ing_category_node` |
| 入口 | 父级「指标与目录体系构建」下 4 子菜单 |
| 权限 | SYSTEM_ADMIN / DEPT_ADMIN / PLATFORM_ADMIN + 配置菜单 |
| 组织隔离 | 部门管理员仅本 `org_id`；超管/平台管理员看全部 |
| 门户 | 审批通过自动同步 `biz_catalog_item` |

## 模块与权限

| 模块 | permission | 默认角色 |
|------|------------|----------|
| 数据资源编目管理 | `hub:ingestion:collect:catalog:resources` | DEPT + SUPER |
| 数据资源分类 | `hub:ingestion:collect:catalog:classify` | PLATFORM + SUPER |
| 资源目录注册发布 | `hub:ingestion:collect:catalog:publish` | DEPT + SUPER |
| 数据资源目录审批 | `hub:ingestion:collect:catalog:approvals` | PLATFORM + SUPER |

## 状态流

1. 部门编目 → `DRAFT`
2. 分类关联资源 → 提交发布 → 审批单 `PUBLISH/PENDING`
3. 平台审批通过 → 资源 `PUBLISHED` + upsert 门户；拒绝须填意见
4. 下线同样走审批；通过后门户下线

## 表扩展

- `ing_resource_registry`：元数据字段、`org_id`、`category_id`、挂载源/表、共享方式等
- `ing_category_node`：`description`、`secret_flag`
- `ing_category_resource`：分类↔资源关联
- `ing_catalog_approval`：审批单
