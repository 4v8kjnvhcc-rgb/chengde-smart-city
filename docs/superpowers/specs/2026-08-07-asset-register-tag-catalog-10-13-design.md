# 数据资产登记 10–13 改造设计

日期：2026-08-07  
状态：已确认，立即实施

## 范围

对应 `docs/repair/数据资产.md` 第 10–13 条。

## 决策摘要

- 标签登记页：Tab1「标签库」+ Tab2「标签管理」（原挂标）
- 标签管理筛选：项目 → 系统 → 数据源；去掉列表顶栏「完成登记」
- 资产目录列表：所属机构、来源项目改为下拉
- 资产目录表单：机构默认当前组织（部门管理员锁定；平台/超管可选）；联系方式默认当前账号且可改，换机构不自动换号；数据标签仅选自标签库
- UUM 系统管理「标签库」入口本次保留（同源数据）

## 主要改动文件

- `TagRegisterView.vue`、可选抽取/嵌入 `TagLibraryManage.vue`
- `AssetCatalogRegView.vue` / `AssetCatalogMgmtView.vue`
- `AssetCatalogFormDialog.vue` + `AssetCatalogRegService.defaults`
- 必要时扩展登录 `UserInfo.phone`
