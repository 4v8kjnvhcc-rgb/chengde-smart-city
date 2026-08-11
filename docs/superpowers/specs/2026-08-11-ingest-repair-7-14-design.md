# 数据资源采集汇聚修复 7–14 设计

## 范围
结构化数据接入 / 手动上传相关 UI 与校验修复（repair 文档 298–306）。

## 决策
- #14：去掉 SQL 尾部分号后再校验，仍禁止真正多语句。
- #10+#13：字段映射类型下拉 + 长度列；建表 DDL 使用所选类型与长度；映射尽量带出源类型。
- #11：模板名/目标表去重（文件名=工作表名时不拼接两遍；拼音建议不重复）。
- #12：超管可选机构；机构用户锁定本机构。

## 主要文件
- `StructuredTableWizard.vue`
- `ManualUploadView.vue`
- `KettleCollectService.java`
- `PinyinInitials.java` / `ExcelManualUploadService.java`（若命名重复）
