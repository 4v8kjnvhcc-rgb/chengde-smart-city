<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'
import { REGISTER_MODULES } from '../ingestion-nav'
import { ingestionApi, useIngestionLoading, type GuideStep } from '../useIngestionHub'

const router = useRouter()
const { loading, loadError, withLoad } = useIngestionLoading()
const guides = ref<GuideStep[]>([])
const activeStep = ref<number>(1)

const intro =
  '本指引面向数据资产登记业务人员与审核人员，按「项目 → 系统 → 数据库 → 数据表 → 数据项 → 数据字典 → 数据标签 → 数据资产目录」的层级关系，说明各环节要填什么、注意什么、如何提交与审核。点击上方步骤可切换说明，需要办理业务时可一键跳转到对应功能菜单。'

/** 结合系统能力扩写的指引正文（非方案原文照搬） */
const GUIDE_CONTENT: Record<
  number,
  { sections: { title: string; body: string }[]; tips?: string[] }
> = {
  1: {
    sections: [
      {
        title: '指引目的',
        body: '填报指引用于降低首次使用成本，把分散在多个菜单中的登记动作串成一条可理解的路径。部门管理员侧重「怎么填、按什么顺序填」；平台管理员与超级管理员侧重「填完后如何审核、驳回后对方如何改」。本页不替代正式业务录入，而是为各登记模块提供操作导航与注意事项。',
      },
      {
        title: '推荐登记顺序',
        body: '建议严格按层级推进：先建项目并挂接业务系统，再登记数据库（数据源）并从源库完成数据表、数据项登记；随后维护数据字典与资产标签；最后从已登记的有价值数据表中挑选对象，编制数据资产目录。跳过上层直接建下层，容易出现无法选择父级、无法关联、审核材料不完整等问题。',
      },
      {
        title: '角色与职责速览',
        body: '部门管理员：在各登记菜单中新建与维护草稿、提交审核、按驳回原因修改后再次提交；可查看本部门资产报告与图谱。平台管理员 / 超级管理员：在对应「管理」菜单中审核通过或驳回（驳回必须填写原因），并负责总体报告、图谱及访问控制、系统维护、菜单等平台能力。超级管理员菜单更全，但登记与审核的状态规则与平台管理员一致。',
      },
    ],
    tips: [
      '侧栏「登记」与「管理」成对出现：登记侧重填报提交，管理侧重审核与总览。',
      '不确定从哪开始时，先完成本页第 9 步「填报流程」再动手。',
    ],
  },
  2: {
    sections: [
      {
        title: '对应功能',
        body: '本维度对应「项目/系统信息登记」。项目是后续系统、库、表、项、字典关联、资产目录的根节点；系统描述该项目下的具体业务应用。填写时请保证编码唯一、名称可识别，并正确绑定所属机构，以便部门隔离与后续授权生效。',
      },
      {
        title: '建议填写内容',
        body: '项目侧：项目编码、项目名称、绑定机构（组织）、责任人或联系方式（如页面提供）、备注说明。系统侧：所属项目、业务系统名称、系统简介、运行状态等。保存成功后记录进入「草稿」状态，列表操作通常包括：查看（基本信息 + 提交审核记录）、编辑、提交、删除。',
      },
      {
        title: '与资产目录的关系',
        body: '后续编制数据资产目录时，仍需补齐目录自身的名称、描述、归属等基础信息。项目/系统信息填得越完整，目录编目时可选的归属链路越清晰，审核人员也更容易核对数据来自哪个业务系统。',
      },
    ],
    tips: [
      '草稿可反复编辑；一旦提交进入待审核，请等待审核结果，勿重复乱删。',
      '删除项目前须确保其下已无关联系统（见第 10 步规范）。',
    ],
  },
  3: {
    sections: [
      {
        title: '对应功能',
        body: '本维度对应「数据资产标签登记」，并在「数据资产目录登记」中落地应用。标签用于描述业务主题、敏感级别、共享属性等可检索维度，是后续部门/总体资产报告与图谱分析的重要过滤条件。',
      },
      {
        title: '如何分类与打标',
        body: '先在标签登记中维护标签名称、规则或适用范围，保证同一类业务使用统一标签词表，避免同义标签过多。编制或维护资产目录时，将相关标签挂到目录条目上，使检索、报告下钻、图谱着色能够按标签聚合。平台侧另有标签管理菜单，用于体系化维护与审核。',
      },
      {
        title: '使用建议',
        body: '分类不宜过细也不宜过粗：过细导致标签膨胀、填报负担重；过粗则报告与检索区分度不足。可按「业务域 + 主题 + 是否涉敏」等少量维度组合，并在部门内形成简短打标约定后再大规模推广。',
      },
    ],
    tips: [
      '标签本身也走登记审核流时，请先提交标签再在目录中引用。',
      '需要看效果时，可到数据资产报告 / 图谱分析中按标签筛选验证。',
    ],
  },
  4: {
    sections: [
      {
        title: '对应功能',
        body: '本维度对应「数据库/表/项登记」。来源信息回答三个问题：数据挂在哪个项目与系统下、物理存放在哪个数据库（数据源）、表与字段如何从源库登记进来。完整的来源链路是汇聚接入、元数据对齐与资产编目的前置条件。',
      },
      {
        title: '填写要点',
        body: '先选择所属项目与系统，再登记数据库：类型（如 MySQL/Oracle 等）、连接配置、连接测试状态等。通过后从源库「登记」数据表，系统带出数据项（字段）。请核对库名、表名、字段名与业务含义是否一致，并在备注中写清数据业务来源（如某委办局业务库）。',
      },
      {
        title: '归属链路',
        body: '正确的链路形态是：项目 → 系统 → 数据库 → 数据表 → 数据项。后续字典关联、资产目录挑选表对象，都会依赖这条链路。若链路断裂（例如表未挂到正确库下），关联选择器将无法选全，审核也难以追溯。',
      },
    ],
    tips: [
      '数据表、数据项以登记/查看为主，勿期望在登记侧做正向建模式的随意增删改。',
      '连接失败时先排查账号、网络与库权限，再继续登记表项。',
    ],
  },
  5: {
    sections: [
      {
        title: '对应功能',
        body: '本维度对应「资产目录登记」。并非每一张已登记表都要入目录，而是挑选对共享、治理、决策有价值的数据表，说明其业务用途与应用场景，形成可理解、可审核的资产条目。',
      },
      {
        title: '用途描述写什么',
        body: '建议写清：服务哪些业务场景（如人口统计、法人监管、城市部件运维）、主要使用对象（处室、领导驾驶舱、对外共享等）、更新频率与时效要求、是否计划对外共享或仅内部使用。用途写清楚，审核人员才能判断纳入目录的必要性，下游共享与汇聚也更容易对齐预期。',
      },
      {
        title: '编目注意',
        body: '目录支持增删改查，但仍遵循统一审核状态。编目时应选择已存在且链路完整的数据表，并同步完善目录名称、描述、归属、标签等信息。避免「空用途」或仅复制表名作为描述。',
      },
    ],
    tips: [
      '先完成库表项登记与必要标签，再编目录，可减少来回修改。',
      '用途描述尽量用业务语言，少用纯技术字段堆砌。',
    ],
  },
  6: {
    sections: [
      {
        title: '对应功能',
        body: '本维度对应「数据库/表/项登记」与「数据字典登记」。系统以结构化库表为主表达资产格式：数据库承载物理形态，数据表与数据项表达结构，数据字典补充码值、枚举与业务语义标准。',
      },
      {
        title: '库表项的操作边界',
        body: '数据表：以从源库登记与查看为主，不可在登记侧随意增加、删除、修改表结构。数据项：以查看为主，不可增删改。结构变更应回到源业务库或元数据相关能力处理，再同步/重新登记，避免登记库与真实库不一致。',
      },
      {
        title: '数据字典的作用',
        body: '对性别、证件类型、行政区划等标准码值，应在数据字典中维护字典编码、名称与字典项。字典可与具体数据项关联（见第 8 步），使字段取值范围有据可查，提高跨系统对账与共享质量。',
      },
    ],
    tips: [
      '先确认源库结构稳定，再大批量登记表项。',
      '字典编码建议与部门已有标准对齐，避免一人一套码。',
    ],
  },
  7: {
    sections: [
      {
        title: '对应功能',
        body: '本维度对应「访问控制管理」及机构/部门数据隔离策略。权限不仅指菜单能不能点，还包括登记数据归谁、谁能审、跨部门是否需要额外审批。填报时务必选对归属机构，否则会出现「填了但本部门看不见 / 其他部门误见」的问题。',
      },
      {
        title: '角色权限边界',
        body: '部门管理员：在本部门范围内填报、提交、查看本部门报告与图谱。平台管理员：审核各部门提交内容，查看总体报告与图谱，维护标签与字典等管理能力。超级管理员：具备更完整的菜单（含访问控制、系统维护、菜单管理等），但仍应按职责分工操作，避免越权代填代审造成责任不清。',
      },
      {
        title: '合规提示',
        body: '涉敏、涉密或有条件共享的数据，应在用途与目录说明中标明控制要求，并在访问控制中落实角色与资源权限。跨部门访问通常需要申请与审批，系统管理员一般不直接代替部门授予业务数据访问权。',
      },
    ],
    tips: [
      '机构选错比字段填错更难事后修正，保存前请核对。',
      '权限问题优先查角色菜单授权与机构绑定，再查单条数据归属。',
    ],
  },
  8: {
    sections: [
      {
        title: '对应功能',
        body: '本维度重点对应数据字典与数据项的「关联」能力，以及质量、风险等补充说明。关联打通「标准码表」与「具体字段」，是提升数据可理解性与后续治理质量的关键动作。',
      },
      {
        title: '如何关联数据项',
        body: '在数据字典登记列表中使用「关联」：按项目 → 系统 → 数据库 → 数据表 → 数据项逐级选择目标字段，确认后建立关联；也可取消关联。编辑与查看字典时，应能看到当前已关联的数据项信息，便于核对是否挂错表或挂错字段。',
      },
      {
        title: '其他可补充信息',
        body: '若业务需要，可在备注或扩展字段中补充数据质量自评（完整性、及时性、准确性）、风险评估（泄露影响、共享条件）、联系人与更新策略等。这些信息不一定替代专门的质量/治理系统，但有助于审核与共享决策。',
      },
    ],
    tips: [
      '关联前请确认库表项已登记且审核状态满足业务要求。',
      '一个字典可服务多个字段，但应避免把无关字段硬挂同一字典。',
    ],
  },
  9: {
    sections: [
      {
        title: '端到端推荐路径',
        body: '① 项目/系统信息登记 → ② 数据库登记并登记数据表/数据项 → ③ 数据字典登记并关联数据项 → ④ 数据资产标签登记 → ⑤ 数据资产目录登记（挑选有价值表并写清用途）→ ⑥ 在各登记列表中提交审核。平台/超级管理员在对应管理菜单完成审核；若驳回，部门管理员在「驳回待提交」状态下按原因修改后再提交。',
      },
      {
        title: '状态与可做操作',
        body: '草稿：可查看、编辑、提交、删除（删除仍受层级约束）。待审核：等待审核，一般不再改内容。审核通过：正式生效，作为后续引用与报告统计依据。驳回待提交：查看驳回原因与审核记录，修改后再次提交。查看操作应能同时看到基本信息与提交审核历史，便于追溯。',
      },
      {
        title: '并行与返工',
        body: '同一部门可并行维护多个项目，但单个项目内部仍建议按层级推进。若审核驳回的是上层对象（如系统），可能影响下层已填内容的有效性，应先修复上层再处理下层。',
      },
    ],
    tips: [
      '提交前用「查看」自查一遍必填项与关联是否齐全。',
      '驳回原因要读完整，避免只改表面字段反复驳回。',
    ],
  },
  10: {
    sections: [
      {
        title: '填写规范',
        body: '编码、名称类字段保持唯一、可读、可检索；机构、项目、系统等外键选择必须真实存在；描述类字段用完整句子说明业务含义。字典、标签、目录的命名应与部门已有规范或平台词表一致，减少同义异名。',
      },
      {
        title: '删除与只读约束',
        body: '删除项目前：其下不能仍关联系统。删除系统前：其下不能仍关联数据库。删除数据库前：其下不能仍关联数据表。数据表不可增删改，仅可登记与查看；数据项不可增删改，仅可查看。数据资产目录可以增删改查，但仍受审核状态约束。',
      },
      {
        title: '层级完整性',
        body: '提交审核前请自检：父级是否已存在、链路是否连续、字典关联是否指向正确字段、目录是否选对表。层级不完整的数据即使提交，也容易在审核或下游汇聚中被退回。',
      },
    ],
    tips: [
      '删除失败时先看提示：通常是仍存在下级关联。',
      '表/项结构变更走源库与元数据同步，不在登记页强行改。',
    ],
  },
  11: {
    sections: [
      {
        title: '提交',
        body: '部门管理员在登记列表确认内容无误后执行「提交」，状态由草稿变为待审核。提交后请关注审核结果通知或列表状态变化；除查看外，待审核期间一般不应再编辑，以免与审核内容不一致。',
      },
      {
        title: '审批',
        body: '平台管理员或超级管理员在对应「管理」菜单中执行审核：通过 → 状态变为审核通过，记录正式生效；驳回 → 状态变为驳回待提交，必须填写驳回原因。审核意见与操作应进入提交审核记录，供双方在「查看」中追溯。',
      },
      {
        title: '闭环要求',
        body: '未审核通过的数据不应视为可对外引用的正式资产。驳回后的修改必须针对原因逐项处理，再次提交后重新进入待审核。涉及项目、系统、库表项、字典、标签、目录等对象时，规则一致，避免有的对象跳过审核直接当正式数据使用。',
      },
    ],
    tips: [
      '审核人重点核对：归属机构、层级链路、用途说明、敏感与权限表述。',
      '可从「项目/系统信息管理」等管理菜单进入处理待审队列。',
    ],
  },
}

const activeGuide = computed(() => guides.value.find((g) => g.stepNo === activeStep.value) || guides.value[0])

const activeContent = computed(() => {
  const no = activeGuide.value?.stepNo
  return no ? GUIDE_CONTENT[no] : undefined
})

const moduleLabel = computed(() => {
  const map = Object.fromEntries(REGISTER_MODULES.map((m) => [m.key, m.label]))
  return (key?: string) => (key ? map[key] || key : '')
})

function goStep(g: GuideStep) {
  if (!g.jumpModule) return
  router.push({ path: '/exchange/ingestion', query: { system: 'register', module: g.jumpModule } })
}

onMounted(() =>
  withLoad(async () => {
    guides.value = (await ingestionApi.guides()).data || []
    if (guides.value.length) activeStep.value = guides.value[0].stepNo
  }),
)
</script>

<template>
  <div v-loading="loading" class="guide-page">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="填报指引（11 步）">
      <p class="guide-intro">{{ intro }}</p>

      <div class="guide-nav">
        <button
          v-for="g in guides"
          :key="g.stepNo"
          type="button"
          class="guide-nav__btn"
          :class="{ 'guide-nav__btn--active': activeStep === g.stepNo }"
          @click="activeStep = g.stepNo"
        >
          <span class="guide-nav__no">{{ g.stepNo }}</span>
          <span class="guide-nav__name">{{ g.stepName }}</span>
        </button>
      </div>

      <div v-if="activeGuide" class="guide-detail">
        <div class="guide-detail__head">
          <h3 class="guide-detail__title">
            <span class="guide-no">{{ activeGuide.stepNo }}</span>
            {{ activeGuide.stepName }}
          </h3>
          <el-button
            v-if="activeGuide.jumpModule"
            type="primary"
            @click="goStep(activeGuide)"
          >
            前往{{ moduleLabel(activeGuide.jumpModule) }}
          </el-button>
        </div>

        <template v-if="activeContent">
          <section v-for="(sec, idx) in activeContent.sections" :key="idx" class="guide-section">
            <h4 class="guide-section__title">{{ sec.title }}</h4>
            <p class="guide-section__body">{{ sec.body }}</p>
          </section>
          <div v-if="activeContent.tips?.length" class="guide-tips">
            <div class="guide-tips__label">操作提示</div>
            <ul>
              <li v-for="(tip, i) in activeContent.tips" :key="i">{{ tip }}</li>
            </ul>
          </div>
        </template>
        <p v-else class="guide-section__body">{{ activeGuide.stepDesc }}</p>
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.guide-page {
  width: 100%;
  max-width: none;
  margin: 0;
  padding: 0 0 28px;
  box-sizing: border-box;
}
.guide-intro {
  margin: 0 0 18px;
  color: #606266;
  line-height: 1.7;
  font-size: 14px;
}
.guide-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}
.guide-nav__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  color: #606266;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, color 0.15s;
}
.guide-nav__btn:hover {
  border-color: #409eff;
  color: #409eff;
}
.guide-nav__btn--active {
  border-color: #409eff;
  background: #ecf5ff;
  color: #409eff;
}
.guide-nav__no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #c0c4cc;
  color: #fff;
  font-size: 11px;
  flex-shrink: 0;
}
.guide-nav__btn--active .guide-nav__no {
  background: #409eff;
}
.guide-nav__name {
  line-height: 1.2;
}
.guide-detail {
  padding: 18px 22px 20px;
  background: #f8fafc;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  min-height: 280px;
}
.guide-detail__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
  flex-wrap: wrap;
}
.guide-detail__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}
.guide-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  padding: 0 6px;
  border-radius: 12px;
  background: #409eff;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
}
.guide-section {
  margin-bottom: 14px;
}
.guide-section__title {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.guide-section__body {
  margin: 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.75;
}
.guide-tips {
  margin-top: 6px;
  padding: 12px 14px;
  background: #fff;
  border: 1px dashed #c6e2ff;
  border-radius: 6px;
}
.guide-tips__label {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 6px;
}
.guide-tips ul {
  margin: 0;
  padding-left: 18px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
}
.guide-tips li + li {
  margin-top: 4px;
}
</style>
