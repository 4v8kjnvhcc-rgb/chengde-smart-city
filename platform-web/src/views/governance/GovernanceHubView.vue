<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { metaSectionItems, resolveMetaSection } from './metadata/meta-nav'
import GovernanceEtlPanel from './etl/GovernanceEtlPanel.vue'

const MetadataSubsystemView = defineAsyncComponent(() => import('./metadata/MetadataSubsystemView.vue'))
const StandardsView = defineAsyncComponent(() => import('./quality/StandardsView.vue'))
const QualityRuleConfigView = defineAsyncComponent(() => import('./quality/QualityRuleConfigView.vue'))
const QualityTaskView = defineAsyncComponent(() => import('./quality/QualityTaskView.vue'))
const QualityMonitorView = defineAsyncComponent(() => import('./quality/QualityMonitorView.vue'))
const QualityReportView = defineAsyncComponent(() => import('./quality/QualityReportView.vue'))
const CatalogResourceView = defineAsyncComponent(() => import('./catalog/CatalogResourceView.vue'))
const CatalogApprovalView = defineAsyncComponent(() => import('./catalog/CatalogApprovalView.vue'))
const CatalogSubscriptionView = defineAsyncComponent(() => import('./catalog/CatalogSubscriptionView.vue'))
const CatalogPortalView = defineAsyncComponent(() => import('./catalog/CatalogPortalView.vue'))
const FusionModelView = defineAsyncComponent(() => import('./fusion/FusionModelView.vue'))
const FusionScriptView = defineAsyncComponent(() => import('./fusion/FusionScriptView.vue'))

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
    label: '数据质量管理',
    children: [
      { key: 'quality.standards', label: '数据标准' },
      { key: 'quality.rule-config', label: '质量规则配置' },
      { key: 'quality.task-mgmt', label: '质量任务' },
      { key: 'quality.monitor', label: '质量监控' },
      { key: 'quality.reports', label: '质量报告' },
      { key: 'quality.tasks', label: '规则与任务' },
    ],
  },
  {
    key: 'model',
    label: '数据融合',
    children: [
      { key: 'model.logic', label: '逻辑/物理模型' },
      { key: 'model.script', label: '融合脚本' },
    ],
  },
  {
    key: 'catalog',
    label: '数据目录管理',
    children: [
      { key: 'catalog.resources', label: '资源编目' },
      { key: 'catalog.approvals', label: '目录审批' },
      { key: 'catalog.subscriptions', label: '订阅' },
      { key: 'catalog.portal', label: '门户' },
    ],
  },
]

const DEFAULT_NAV = 'metadata.model'
const activeNav = ref(DEFAULT_NAV)

const etlView = ref('list')
const etlTaskId = ref<number | null>(null)

interface Rule { id: number; ruleCode: string; ruleName: string; ruleType: string; status: string }
interface QTask { id: number; taskName: string; status: string; lastScore?: number; lastMessage?: string }
interface Standard { id: number; itemCode: string; itemName: string; itemType: string; standardRef?: string }

const route = useRoute()
const router = useRouter()

const tabMap: Record<string, string> = {
  quality: 'quality', m078: 'quality', m085: 'quality', m102: 'quality', m105: 'quality',
  metadata: 'metadata', m086: 'metadata', m097: 'metadata',
  etl: 'etl', m098: 'etl', m099: 'etl', m101: 'etl',
  model: 'model', m106: 'model', m111: 'model',
  catalog: 'catalog', m112: 'catalog', m122: 'catalog',
}

const integration = ref<Record<string, unknown>>({})
const rules = ref<Rule[]>([])
const qTasks = ref<QTask[]>([])
const standards = ref<Standard[]>([])

const ruleForm = reactive({ ruleName: '', ruleType: 'COMPLETENESS' })
const taskForm = reactive({ taskName: '', ruleId: undefined as number | undefined })

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
const modelSub = computed(() => (activeNav.value === 'model.script' ? 'script' : 'logic'))
const catalogSub = computed(() => (activeNav.value.startsWith('catalog.') ? activeNav.value.slice('catalog.'.length) : 'resources'))
const etlSub = computed(() => (activeNav.value.startsWith('etl.') ? activeNav.value.slice('etl.'.length) : 'task-mgmt'))

const ETL_LIST_SUBS = ['task-mgmt', 'task-run', 'task-schedule', 'components']

function defaultNavForTab(t: string): string {
  if (t === 'quality') return 'quality.standards'
  if (t === 'metadata') return 'metadata.model'
  if (t === 'etl') return 'etl.task-mgmt'
  if (t === 'model') return 'model.logic'
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
    const sub = String(route.query.qSub || 'standards')
    activeNav.value = `quality.${sub}`
  } else if (mapped === 'model') {
    const sub = String(route.query.mSub || 'logic')
    activeNav.value = sub === 'script' ? 'model.script' : 'model.logic'
  } else if (mapped === 'catalog') {
    const sub = String(route.query.cSub || 'resources')
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
    if (['tab', 'section', 'qSub', 'mSub', 'cSub', 'etlSub', 'etlView', 'taskId'].includes(k)) continue
    q[k] = Array.isArray(v) ? String(v[0]) : String(v)
  }
  q.tab = tab.value
  if (tab.value === 'metadata') q.section = metaSection.value
  if (tab.value === 'quality') q.qSub = qualitySub.value
  if (tab.value === 'model') q.mSub = modelSub.value
  if (tab.value === 'catalog') q.cSub = catalogSub.value
  if (tab.value === 'etl') {
    q.etlSub = etlSub.value || 'task-mgmt'
    if ((etlView.value === 'design' || etlView.value === 'monitor') && etlTaskId.value != null) {
      q.etlView = etlView.value
      q.taskId = String(etlTaskId.value)
    }
  } else {
    etlView.value = 'list'
    etlTaskId.value = null
  }
  router.replace({ query: q })
}

/** 侧栏点到任务管理等列表页时，退出画布/监控，确保列表能显示 */
function resetEtlToListIfNeeded(navKey: string) {
  if (ETL_LIST_SUBS.includes(navKey)) {
    etlView.value = 'list'
    etlTaskId.value = null
  }
}

function onHubNavSelect(key: string) {
  if (!ETL_LIST_SUBS.includes(key)) return
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

async function loadIntegration() {
  try {
    integration.value = (await api.get('/integration/health')).data || {}
  } catch { integration.value = {} }
}

async function loadQuality() {
  if (qualitySub.value !== 'tasks') return
  const res = await api.get('/governance/platform/quality/overview')
  rules.value = res.data.rules
  qTasks.value = res.data.tasks
  standards.value = res.data.standards || []
}

async function loadEtl() {
  // ETL页面数据加载
}

async function loadTabData() {
  await loadIntegration()
  try {
    if (tab.value === 'quality') await loadQuality()
    if (tab.value === 'etl') await loadEtl()
  } catch { ElMessage.error('加载失败') }
}

async function createRule() {
  await api.post('/governance/quality/rules', ruleForm)
  ElMessage.success('规则已创建')
  ruleForm.ruleName = ''
  await loadQuality()
}

async function createTask() {
  await api.post('/governance/quality/tasks', taskForm)
  ElMessage.success('任务已创建')
  taskForm.taskName = ''
  await loadQuality()
}

async function runTask(id: number) {
  const res = await api.post(`/governance/quality/tasks/${id}/run`)
  ElMessage.success(`评分 ${res.data.score}`)
  await loadQuality()
}

async function genReport() {
  await api.post('/governance/platform/quality/reports', { reportName: '六性质量报告', dimension: '完整性+准确性' })
  ElMessage.success('报告已生成')
  await loadQuality()
}

const tabTitle = computed(() => ({
  metadata: '元数据管理',
  etl: '数据治理',
  quality: '数据质量管理',
  model: '数据融合',
  catalog: '数据目录管理',
}[tab.value] || '元数据管理'))

watch(activeNav, (nav) => {
  if (applyingRoute) return
  resetEtlToListIfNeeded(nav)
  syncQuery()
  loadTabData()
})
watch(qualitySub, () => {
  if (tab.value === 'quality') loadQuality()
})
watch(
  () => [route.query.tab, route.query.section, route.query.qSub, route.query.mSub, route.query.cSub, route.query.etlSub],
  () => { resolveFromRoute() },
)
watch(() => [route.query.etlView, route.query.taskId], () => {
  if (applyingRoute) return
  resolveEtlView()
  if (tab.value === 'etl') void loadEtl()
})
onMounted(() => { resolveFromRoute(); loadTabData() })
</script>

<template>
  <div>
    <PageHeader
      :title="`数据融合治理平台 · ${tabTitle}`"
      description="元数据管理、数据治理、数据质量管理、数据融合、数据目录管理"
    />
    <el-alert v-if="integration.openmetadata !== undefined" type="info" :closable="false" show-icon
      :title="`OpenMetadata=${integration.openmetadata ? 'UP' : 'DOWN'} · 集成=${integration.enabled ? 'ON' : 'OFF'}`"
      style="margin-bottom:12px" />
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
        <QualityReportView v-else-if="tab === 'quality' && qualitySub === 'reports'" />
        <PageCard v-else-if="tab === 'quality' && qualitySub === 'tasks'" title="质量规则与任务">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="规则" class="portal-field-md"><el-input v-model="ruleForm.ruleName" /></el-form-item>
            <el-form-item label="类型" class="portal-field-sm">
              <el-select v-model="ruleForm.ruleType">
                <el-option label="完整性" value="COMPLETENESS" />
                <el-option label="准确性" value="ACCURACY" />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="createRule">新增规则</el-button>
              <el-button @click="genReport">生成报告</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="rules" stripe size="small">
            <el-table-column prop="ruleCode" label="编码" width="140" />
            <el-table-column prop="ruleName" label="名称" />
            <el-table-column prop="ruleType" label="类型" width="120" />
          </el-table>
          <el-divider />
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="任务" class="portal-field-md"><el-input v-model="taskForm.taskName" /></el-form-item>
            <el-form-item label="关联规则" class="portal-field-lg">
              <el-select v-model="taskForm.ruleId" clearable>
                <el-option v-for="r in rules" :key="r.id" :label="r.ruleName" :value="r.id" />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="createTask">创建任务</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="qTasks" stripe size="small">
            <el-table-column prop="taskName" label="任务" />
            <el-table-column prop="lastScore" label="评分" width="80" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }"><el-button link @click="runTask(row.id)">执行</el-button></template>
            </el-table-column>
          </el-table>
        </PageCard>

        <FusionModelView v-else-if="tab === 'model' && modelSub === 'logic'" />
        <FusionScriptView v-else-if="tab === 'model' && modelSub === 'script'" />

        <CatalogResourceView v-else-if="tab === 'catalog' && catalogSub === 'resources'" />
        <CatalogApprovalView v-else-if="tab === 'catalog' && catalogSub === 'approvals'" />
        <CatalogSubscriptionView v-else-if="tab === 'catalog' && catalogSub === 'subscriptions'" />
        <CatalogPortalView v-else-if="tab === 'catalog' && catalogSub === 'portal'" />

        <el-empty v-else :description="`未识别的导航：${activeNav}`" />
      </div>
    </HubSideLayout>
  </div>
</template>
