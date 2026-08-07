-- M039 填报指引：按 docs/repair/数据资产.md 同步步骤名称、说明与跳转模块
ALTER TABLE ing_guide_step
  MODIFY COLUMN step_desc TEXT NOT NULL,
  ADD COLUMN jump_module VARCHAR(64) NULL COMMENT '登记侧 module key，如 m040' AFTER required_flag;

UPDATE ing_guide_step SET
  step_name = '填报指引概要',
  step_desc = '提供系统的填报指引的概要和目的，说明填报指引的重要性和使用方法；明确适用角色（部门管理员负责登记填报与提交，平台管理员/超级管理员负责审核），以及推荐登记顺序：先项目/系统，再数据库/表/项，再数据字典与标签，最后选取有价值的数据表登记数据资产目录。',
  required_flag = 1,
  jump_module = NULL
WHERE step_no = 1;

UPDATE ing_guide_step SET
  step_name = '填报基本信息',
  step_desc = '要求用户填写数据资产的基本信息；对应项目/系统信息登记，引导填写项目编码与名称、绑定机构、业务系统名称等内容，保存后状态为草稿，操作列支持查看（含基本信息及提交审核记录）、编辑、提交、删除；数据资产目录登记时同步补齐目录名称、描述、归属等基础信息。',
  required_flag = 1,
  jump_module = 'm040'
WHERE step_no = 2;

UPDATE ing_guide_step SET
  step_name = '数据资产分类',
  step_desc = '引导用户设定数据资产的分类和标签，方便后续检索和管理；对应数据资产标签登记及数据资产目录登记中的分类与打标，标签在数据资产目录处应用，并支撑部门/总体报告与图谱分析。',
  required_flag = 1,
  jump_module = 'm043'
WHERE step_no = 3;

UPDATE ing_guide_step SET
  step_name = '数据资产来源',
  step_desc = '要求用户填写数据资产的来源信息；对应数据库/表/项登记，引导填写所属项目与系统、数据库（数据源）类型与连接信息，以及由源库登记得到的数据表、数据项来源关系，体现业务系统到库表项的归属链路。',
  required_flag = 1,
  jump_module = 'm041'
WHERE step_no = 4;

UPDATE ing_guide_step SET
  step_name = '数据资产用途',
  step_desc = '要求用户描述数据资产的主要用途和场景；对应数据资产目录登记，引导说明所覆盖数据表的业务用途与应用场景，以及为何将该表纳入数据资产目录，方便他人了解和利用。',
  required_flag = 1,
  jump_module = 'asset-catalog-reg'
WHERE step_no = 5;

UPDATE ing_guide_step SET
  step_name = '数据资产格式',
  step_desc = '要求用户说明数据资产的格式类型；对应数据库/表/项及数据字典登记，说明以库表结构为主的登记方式（数据库、数据表、数据项），并引导登记数据字典标准码值；数据表仅可登记与查看（不可增删改），数据项仅可查看（不可增删改）。',
  required_flag = 1,
  jump_module = 'm041'
WHERE step_no = 6;

UPDATE ing_guide_step SET
  step_name = '数据资产权限',
  step_desc = '要求用户设定数据资产的访问权限范围和控制要求；对应访问控制及机构/部门隔离，明确登记对象归属机构与可操作角色（部门管理员填报本部门数据，平台/超级管理员审核与管理），确保数据安全和合规。',
  required_flag = 1,
  jump_module = 'm048'
WHERE step_no = 7;

UPDATE ing_guide_step SET
  step_name = '其他信息',
  step_desc = '提供额外的填报选项；对应数据字典与数据项关联及其他补充说明，引导使用数据字典「关联」功能，按项目→系统→数据库→数据表→数据项建立或取消关联，编辑/查看时可看到关联信息；可按需补充质量评估、风险评估等扩展说明。',
  required_flag = 1,
  jump_module = 'm042'
WHERE step_no = 8;

UPDATE ing_guide_step SET
  step_name = '填报流程',
  step_desc = '提供用户填报数据资产信息的流程步骤和操作指引；按系统业务顺序引导：①登记项目/系统 →②登记数据库并登记数据表/数据项 →③登记数据字典并关联数据项 →④登记数据资产标签 →⑤登记数据资产目录 →⑥提交审核；并说明各状态下操作（草稿可编辑/提交/删除，待审核等待审核，驳回待提交可按驳回原因修改后再次提交）。',
  required_flag = 1,
  jump_module = 'm040'
WHERE step_no = 9;

UPDATE ing_guide_step SET
  step_name = '填报规范',
  step_desc = '规范填报内容的格式和要求；明确各登记对象编码/名称等必填项，以及删除约束——删除项目前其下不能有系统，删除系统前其下不能有数据库，删除数据库前其下不能有数据表；数据资产目录支持增删改查，确保填报信息一致、准确且层级完整。',
  required_flag = 1,
  jump_module = NULL
WHERE step_no = 10;

UPDATE ing_guide_step SET
  step_name = '提交和审批',
  step_desc = '设定数据资产填报信息的提交和审批流程；部门管理员提交后状态由草稿变为待审核；平台管理员/超级管理员审核，通过后状态为审核通过并正式生效，驳回后状态为驳回待提交且须填写驳回原因；查看可追溯基本信息及提交审核记录，确保填报信息经审核后方可正式记录到系统中。',
  required_flag = 1,
  jump_module = 'project-system-mgmt'
WHERE step_no = 11;
