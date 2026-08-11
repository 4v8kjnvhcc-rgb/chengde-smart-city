# 采集汇聚修复 7–14 Implementation Plan

> **For Claude:** 按任务顺序改代码并自测关键路径。

**Goal:** 落地 repair 7–14（日期时间选择、列表瘦身、文案、字段类型/长度、命名去重、机构权限、SQL 尾部分号）。

**Files:** StructuredTableWizard.vue, ManualUploadView.vue, KettleCollectService.java, PinyinInitials（按需）

## Tasks
1. Wizard：日志详情 datetime picker；删列表四列；删副标题文案；字段映射类型下拉+长度
2. KettleCollectService：validateSelectSql 去尾部分号；DDL 尊重 length
3. ManualUpload：命名去重；机构锁定确认
4. 手动冒烟：日志筛选、列表、新建任务字段映射、条件 SQL 尾部分号、新建模板命名与机构
