<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { metaSectionItems, resolveMetaSection } from './metadata/meta-nav'
import GovernanceEtlPanel from './etl/GovernanceEtlPanel.vue'

const MetadataSubsystemView = defineAsyncComponent(() => import('./metadata/MetadataSubsystemView.vue'))
const StandardsView = defineAsyncComponent(() => import('./quality/StandardsView.vue'))
const QualityRuleConfigView = defineAsyncComponent(() => import('./quality/QualityRuleConfigView.vue'))
const QualityTaskView = defineAsyncComponent(() => import('./quality/QualityTaskView.vue'))
const QualityMonitorView = defineAsyncComponent(() => import('./quality/QualityMonitorView.vue'))
const QualityAssessView = defineAsyncComponent(() => import('./quality/QualityAssessView.vue'))
const QualityReportView = defineAsyncComponent(() => import('./quality/QualityReportView.vue'))
const CatalogResourceView = defineAsyncComponent(() => import('./catalog/CatalogResourceView.vue'))
const CatalogRegisterPublishView = defineAsyncComponent(() => import('./catalog/CatalogRegisterPublishView.vue'))
const CatalogApprovalView = defineAsyncComponent(() => import('./catalog/CatalogApprovalView.vue'))
const CatalogSubscriptionView = defineAsyncComponent(() => import('./catalog/CatalogSubscriptionView.vue'))
const CatalogPortalView = defineAsyncComponent(() => import('./catalog/CatalogPortalView.vue'))
const FusionModelView = defineAsyncComponent(() => import('./fusion/FusionModelView.vue'))
const FusionCapabilityHost = defineAsyncComponent(() => import('./fusion/FusionCapabilityHost.vue'))
const GovernanceComponentsView = defineAsyncComponent(() => import('./etl/GovernanceComponentsView.vue'))

/** V3.0：数据融合处理下挂五子能力，侧栏三级可见 */
const FUSION_CAPS = ['script', 'clean', 'schedule', 'execute', 'version'] as const

const navItems: HubNavItem[] = [
  {
    key: 'metadata',
    label: '元数据管理',
    children: metaSectionItems.map((i) => ({ key: `metadata.${i.key}`, label: i.label })),
  },
  {
    key: 'etl',
    label: '数据治理',
    children: [
      { key: 'etl.task-mgmt', label: '任务管理' },
      { key: 'etl.task-run', label: '任务运行' },
      { key: 'etl.task-schedule', label: '任务定时' },
      { key: 'etl.etl-monitor', label: 'ETL监控' },
      { key: 'etl.components', label: '数据治理组件' },
    ],
  },
  {
    key: 'quality',
    label: '数据质量管理系统',
    children: [
      { key: 'quality.standards', label: '数据标准体系' },
      { key: 'quality.rule-config', label: '质量规则配置' },
      { key: 'quality.task-mgmt', label: '数据质量任务' },
      { key: 'quality.monitor', label: '数据质量监控' },
      { key: 'quality.assess', label: '数据质量评估' },
      { key: 'quality.reports', label: '数据质量分析报告' },
    ],
  },
  {
    key: 'model',
    label: '数据融合系统',
    children: [
      { key: 'model.warehouse', label: '数据仓库建设' },
      {
        key: 'model.processing',
        label: '数据融合处理',
        children: [
          { key: 'model.script', label: '脚本开发' },
          { key: 'model.clean', label: '数据清洗', subLabel: '多表融合加工→DWS/ADS' },
          { key: 'model.schedule', label: '工作流调度' },
          { key: 'model.execute', label: '任务执行', subLabel: '加工/直通共享' },
          { key: 'model.version', label: '版本管理' },
        ],
      },
      { key: 'model.components', label: '数据融合组件' },
    ],
  },
  {
    key: 'catalog',
    label: '数据目录管理系统',
    children: [
      { key: 'catalog.resources', label: '资源目录编制' },
      { key: 'catalog.publish', label: '目录注册发布' },
      { key: 'catalog.approvals', label: '资源目录审批' },
      { key: 'catalog.subscriptions', label: '资源申请订阅' },
      { key: 'catalog.portal', label: '资源目录门户' },
    ],
  },
]

const DEFAULT_NAV = 'metadata.model'
const activeNav = ref(DEFAULT_NAV)

const etlView = ref('list')
const etlTaskId = ref<number | null>(null)

const route = useRoute()
const router = useRouter()

const tabMap: Record<string, string> = {
  quality: 'quality', m078: 'quality', m085: 'quality', m102: 'quality', m105: 'quality',
  metadata: 'metadata', m086: 'metadata', m097: 'metadata',
  etl: 'etl', m098: 'etl', m099: 'etl', m101: 'etl',
  model: 'model', m106: 'model', m111: 'model',
  catalog: 'catalog', m112: 'catalog', m122: 'catalog',
}

const tab = computed(() => {
  if (activeNav.value === 'etl' || activeNav.value.startsWith('etl.')) return 'etl'
  const mod = activeNav.value.split('.')[0]
  return tabMap[mod] || mod || 'metadata'
})

const qualitySub = computed(() => (activeNav.value.startsWith('quality.') ? activeNav.value.slice('quality.'.length) : 'standards'))
const metaSection = computed(() => {
  if (!activeNav.value.startsWith('metadata.')) return 'model'
  return resolveMetaSection(activeNav.value.slice('metadata.'.length))
})
/** 兼容旧 mSub；processing 无叶子时落到脚本开发 */
function normalizeModelSub(raw: string): string {
  const s = String(raw || 'warehouse')
  if (s === 'logic' || s === 'model') return 'warehouse'
  if (s === 'processing') return 'script'
  if (s === 'direct-share' || s === 'processed-share') return 'execute'
  if (s === 'warehouse' || s === 'components') return s
  if ((FUSION_CAPS as readonly string[]).includes(s)) return s
  return 'warehouse'
}

const modelSub = computed(() => {
  if (!activeNav.value.startsWith('model.')) return 'warehouse'
  const leaf = activeNav.value.slice('model.'.length)
  // model.processing 仅为展开节点，不是叶子
  if (leaf === 'processing') return 'script'
  return normalizeModelSub(leaf)
})

const isFusionCap = computed(() => (FUSION_CAPS as readonly string[]).includes(modelSub.value))
const catalogSub = computed(() => (activeNav.value.startsWith('catalog.') ? activeNav.value.slice('catalog.'.length) : 'resources'))
const etlSub = computed(() => (activeNav.value.startsWith('etl.') ? activeNav.value.slice('etl.'.length) : 'task-mgmt'))

const ETL_LIST_SUBS = ['task-mgmt', 'task-run', 'task-schedule', 'components']

function defaultNavForTab(t: string): string {
  if (t === 'quality') return 'quality.standards'
  if (t === 'metadata') return 'metadata.model'
  if (t === 'etl') return 'etl.task-mgmt'
  if (t === 'model') return 'model.warehouse'
  if (t === 'catalog') return 'catalog.resources'
  return DEFAULT_NAV
}

/** 避免 route → activeNav → syncQuery → route 死循环把页面卡死 */
let applyingRoute = false

function resolveFromRoute() {
  applyingRoute = true
  const qTab = String(route.query.tab || 'metadata').toLowerCase()
  const mapped = tabMap[qTab] || 'metadata'
  if (mapped === 'etl') {
    const sub = String(route.query.etlSub || 'task-mgmt')
    activeNav.value = `etl.${sub}`
  } else if (mapped === 'metadata') {
    const sec = resolveMetaSection(route.query.section)
    activeNav.value = `metadata.${sec}`
  } else if (mapped === 'quality') {
    // 旧 mock 入口 quality.tasks → 正式任务配置
    let sub = String(route.query.qSub || 'standards')
    if (sub === 'tasks') sub = 'task-mgmt'
    activeNav.value = `quality.${sub}`
  } else if (mapped === 'model') {
    let raw = String(route.query.mSub || 'warehouse')
    // 旧 procTab 兼容：processing + procTab=script|clean|...
    if (raw === 'processing') {
      const pt = String(route.query.procTab || 'script')
      raw = normalizeModelSub(pt === 'processing' ? 'script' : pt)
    }
    if (raw === 'direct-share' || raw === 'processed-share') {
      activeNav.value = 'model.execute'
      nextTick(() => {
        const q = { ...route.query, tab: 'model', mSub: 'execute', execTab: raw } as Record<string, string>
        delete (q as Record<string, unknown>).procTab
        router.replace({ query: q })
      })
    } else {
      const leaf = normalizeModelSub(raw)
      activeNav.value = `model.${leaf}`
    }
  } else if (mapped === 'catalog') {
    // 治理侧不维护「数据资源分类」（分类在归集·指标与目录体系构建）
    let sub = String(route.query.cSub || 'resources')
    if (sub === 'classify') sub = 'resources'
    activeNav.value = `catalog.${sub}`
  } else {
    activeNav.value = defaultNavForTab(mapped)
  }
  resolveEtlView()
  nextTick(() => { applyingRoute = false })
}

function resolveEtlView() {
  const sub = String(route.query.etlSub || etlSub.value || 'task-mgmt')
  const v = String(route.query.etlView || 'list').toLowerCase()
  const tid = Number(route.query.taskId || 0)
  // 列表页默认展示任务列表；仅 session 内点击「开发/监控」后才进入画布（见 openEtlDesign/openEtlMonitor）
  if (ETL_LIST_SUBS.includes(sub) && etlView.value !== 'design' && etlView.value !== 'monitor') {
    etlView.value = 'list'
    etlTaskId.value = null
    return
  }
  if (v === 'design' && tid > 0) {
    etlView.value = 'design'
    etlTaskId.value = tid
  } else if (v === 'monitor') {
    etlView.value = 'monitor'
    etlTaskId.value = tid > 0 ? tid : null
  } else {
    etlView.value = 'list'
    etlTaskId.value = null
  }
}

function syncQuery() {
  const q: Record<string, string> = {}
  for (const [k, v] of Object.entries(route.query)) {
    if (v == null) continue
    // 跳过由 Hub 托管的键，下面按当前状态重写
    if (['tab', 'section', 'qSub', 'mSub', 'cSub', 'etlSub', 'etlView', 'taskId', 'procTab'].includes(k)) continue
    q[k] = Array.isArray(v) ? String(v[0]) : String(v)
  }
  q.tab = tab.value
  if (tab.value === 'metadata') q.section = metaSection.value
  if (tab.value === 'quality') q.qSub = qualitySub.value
  if (tab.value === 'model') q.mSub = modelSub.value
  if (tab.value === 'catalog') q.cSub = catalogSub.value
  if (tab.value === 'catalog' && catalogSub.value === 'subscriptions' && route.query.subTab) {
    q.subTab = String(Array.isArray(route.query.subTab) ? route.query.subTab[0] : route.query.subTab)
  }
  const fusionNeedsEtl = tab.value === 'model' && ['clean', 'schedule', 'execute'].includes(modelSub.value)
  if (tab.value === 'etl' || fusionNeedsEtl) {
    if (tab.value === 'etl') q.etlSub = etlSub.value || 'task-mgmt'
    if ((etlView.value === 'design' || etlView.value === 'monitor') && etlTaskId.value != null) {
      q.etlView = etlView.value
      q.taskId = String(etlTaskId.value)
    }
  } else if (!fusionNeedsEtl) {
    etlView.value = 'list'
    etlTaskId.value = null
  }
  router.replace({ query: q })
}

/** 侧栏 key 形如 etl.task-schedule，取子段再判断 */
function etlSubOf(navKey: string): string | null {
  if (!navKey.startsWith('etl.')) return null
  return navKey.slice('etl.'.length)
}

/** 侧栏切换时退出画布/单任务监控，避免停在旧界面 */
function resetEtlToListIfNeeded(navKey: string) {
  const sub = etlSubOf(navKey)
  if (sub == null) return
  if (ETL_LIST_SUBS.includes(sub) || sub === 'etl-monitor') {
    etlView.value = 'list'
    etlTaskId.value = null
  }
}

function onHubNavSelect(key: string) {
  // 融合处理子能力切换时也退出画布
  if (key.startsWith('model.') && ['model.clean', 'model.schedule', 'model.execute', 'model.script', 'model.version'].includes(key)) {
    etlView.value = 'list'
    etlTaskId.value = null
  }
  if (etlSubOf(key) == null) {
    if (key.startsWith('model.')) syncQuery()
    return
  }
  resetEtlToListIfNeeded(key)
  syncQuery()
}

function openEtlDesign(id: number) {
  etlView.value = 'design'
  etlTaskId.value = id
  syncQuery()
}

function openEtlMonitor(id: number) {
  etlView.value = 'monitor'
  etlTaskId.value = id
  syncQuery()
}

watch(activeNav, (nav) => {
  if (applyingRoute) return
  resetEtlToListIfNeeded(nav)
  syncQuery()
})
watch(
  () => [route.query.tab, route.query.section, route.query.qSub, route.query.mSub, route.query.cSub, route.query.etlSub],
  () => { resolveFromRoute() },
)
watch(() => [route.query.etlView, route.query.taskId], () => {
  if (applyingRoute) return
  resolveEtlView()
})
onMounted(() => { resolveFromRoute() })
</script>

<template>
  <div class="gov-hub-root">
    <HubSideLayout v-model="activeNav" :items="navItems" @select="onHubNavSelect">
      <div class="gov-hub-panel">
        <MetadataSubsystemView v-if="tab === 'metadata'" :section="metaSection" />

        <GovernanceEtlPanel
          v-else-if="tab === 'etl'"
          :sub="etlSub"
          :view="etlView"
          :task-id="etlTaskId"
          @design="openEtlDesign"
          @monitor="openEtlMonitor"
        />

        <StandardsView v-else-if="tab === 'quality' && qualitySub === 'standards'" />
        <QualityRuleConfigView v-else-if="tab === 'quality' && qualitySub === 'rule-config'" />
        <QualityTaskView v-else-if="tab === 'quality' && qualitySub === 'task-mgmt'" />
        <QualityMonitorView v-else-if="tab === 'quality' && qualitySub === 'monitor'" />
        <QualityAssessView v-else-if="tab === 'quality' && qualitySub === 'assess'" />
        <QualityReportView v-else-if="tab === 'quality' && qualitySub === 'reports'" />

        <FusionModelView v-else-if="tab === 'model' && modelSub === 'warehouse'" />
        <FusionCapabilityHost
          v-else-if="tab === 'model' && isFusionCap"
          :capability="modelSub"
          :etl-view="etlView"
          :etl-task-id="etlTaskId"
          @design="openEtlDesign"
          @monitor="openEtlMonitor"
        />
        <GovernanceComponentsView
          v-else-if="tab === 'model' && modelSub === 'components'"
          page-title="数据融合组件"
          context-hint="融合组件支撑多表成主题/专题库（优先输出 DWS/ADS）。与「数据治理组件」同库同状态；单表过程清洗属数据治理（DWD）。执行统一 Kettle Carte。"
        />

        <CatalogResourceView v-else-if="tab === 'catalog' && catalogSub === 'resources'" catalog-origin="GOVERNANCE" />
        <CatalogRegisterPublishView v-else-if="tab === 'catalog' && catalogSub === 'publish'" catalog-origin="GOVERNANCE" />
        <CatalogApprovalView v-else-if="tab === 'catalog' && catalogSub === 'approvals'" catalog-origin="GOVERNANCE" />
        <CatalogSubscriptionView v-else-if="tab === 'catalog' && catalogSub === 'subscriptions'" />
        <CatalogPortalView v-else-if="tab === 'catalog' && catalogSub === 'portal'" />

        <el-empty v-else :description="`未识别的导航：${activeNav}`" />
      </div>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.gov-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
.gov-hub-panel {
  height: 100%;
  min-height: 0;
}
</style>
