<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import {
  FULFILL_PATH_OPTIONS,
  MANIFEST_CENTER_SECTIONS,
  SUPPLY_MAIN_SECTIONS,
  resolveApplicationNav,
  type FulfillPath,
} from './application-nav'
import DemandApplyForm, { type DemandFormModel } from './DemandApplyForm.vue'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ mode?: 'front' | 'config'; embedded?: boolean }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const moduleKey = ref('supply-flow')
const section = ref('demand')

const templates = ref<{ id: number; templateCode: string; templateName: string; demandType: string; fieldSchema?: string }[]>([])
const demands = ref<Record<string, unknown>[]>([])
const catalogs = ref<Record<string, unknown>[]>([])
const supplyTasks = ref<Record<string, unknown>[]>([])
const duties = ref<Record<string, unknown>[]>([])
const objections = ref<Record<string, unknown>[]>([])
const manifests = ref<Record<string, unknown>[]>([])
const listCenterItems = ref<Record<string, unknown>[]>([])
const listCenterSub = ref('catalog-published')
const listCenterGroup = ref('目录清单')
const catalogManifestSections = MANIFEST_CENTER_SECTIONS.filter((s) => s.group === '目录清单')
const supplyManifestSections = MANIFEST_CENTER_SECTIONS.filter((s) => s.group === '供需清单')
const objectionManifestSections = MANIFEST_CENTER_SECTIONS.filter((s) => s.group === '异议清单')
const exchangeJobs = ref<Record<string, unknown>[]>([])
const apiEndpoints = ref<Record<string, unknown>[]>([])
const sharePages = ref<Record<string, unknown>[]>([])
const modelFieldValues = reactive<Record<string, string>>({})
const analysisResult = ref<Record<string, unknown> | null>(null)
const analysisCandidates = ref<Record<string, unknown>[]>([])
const relationGraph = ref<{ nodes?: Record<string, unknown>[]; edges?: Record<string, unknown>[] } | null>(null)
const resourceHits = ref<Record<string, unknown>[]>([])
const supplyView = ref<Record<string, unknown> | null>(null)
const selectedDemandId = ref<number>()
const analyzingId = ref<number>()

const demandForm = reactive({
  demandTitle: '',
  requesterOrg: '数据管理局',
  demandType: 'STRUCTURED',
  templateCode: '',
  demandContent: '',
  targetCatalogId: undefined as number | undefined,
})
const dispatchForm = reactive({
  assigneeOrg: '资源管理处',
  analysisNote: '',
  fulfillPath: 'NEED_COLLECT' as FulfillPath,
})
const resourceSearch = reactive({
  keyword: '',
  resourceType: 'ALL',
})
const quickSet = reactive({
  evalStatus: 'MATCHED',
  shareAttr: 'CONDITIONAL',
})
const superviseNote = ref('请尽快完成需求分析与供数确认')
const returnNote = ref('材料不全，请补充后重新提交')
const analysisOrgs = ref<{ id: number; orgName: string }[]>([])
const returnDialog = reactive({ visible: false, id: 0 })
const superviseDialog = reactive({ visible: false, id: 0 })
const confirmNote = ref('供需对接确认，可满足需求，转换为数据责任并生成归集/共享任务')
const confirmFeedback = ref('')
const confirmResult = ref<Record<string, unknown> | null>(null)
const editDialog = reactive({
  visible: false,
  id: 0,
  demandTitle: '',
  requesterOrg: '',
  assigneeOrg: '',
  fulfillPath: 'NEED_COLLECT' as FulfillPath,
})
const objectionForm = reactive({ catalogId: undefined as number | undefined, objectionType: 'QUALITY', content: '' })

const demandFilter = reactive({ title: '', demandType: '', status: '' })
const demandPage = ref(1)
const demandPageSize = ref(10)
const createVisible = ref(false)
const applyMode = ref<'create' | 'edit' | 'view'>('create')
const applyFormModel = ref<Partial<DemandFormModel> | null>(null)
const editingDemandId = ref<number>(0)
const trackDrawer = reactive<{ visible: boolean; row: Record<string, unknown> | null }>({
  visible: false,
  row: null,
})

/** 需求分析（front） */
const analysisFilter = reactive({ title: '', org: '', evalStatus: '' })
const analysisPage = ref(1)
const analysisPageSize = ref(10)

/** 需求确认（front） */
const confirmFilter = reactive({ title: '', status: '' })
const confirmPage = ref(1)
const confirmPageSize = ref(10)
const confirmDrawer = reactive<{ visible: boolean; row: Record<string, unknown> | null }>({
  visible: false,
  row: null,
})

/** 供给查看（front） */
const supplyTab = ref<'share' | 'exchange' | 'api' | 'page'>('share')

/** 督办反馈抽屉 */
const feedbackDrawer = reactive<{ visible: boolean; row: Record<string, unknown> | null }>({
  visible: false,
  row: null,
})

/** 清单中心 KPI */
const manifestCounts = reactive({ catalog: 0, supply: 0, objection: 0 })
const listFilter = reactive({ code: '', title: '', status: '' })
const listPage = ref(1)
const listPageSize = ref(10)
const objectionFilter = reactive({ title: '', object: '', provider: '', status: '' })

const filteredDemands = computed(() => {
  return demands.value.filter((d) => {
    if (demandFilter.title && !String(d.demandTitle || '').includes(demandFilter.title.trim())) return false
    if (demandFilter.demandType && String(d.demandType) !== demandFilter.demandType) return false
    if (demandFilter.status && String(d.status) !== demandFilter.status) return false
    return true
  })
})
const pagedDemands = computed(() => {
  const start = (demandPage.value - 1) * demandPageSize.value
  return filteredDemands.value.slice(start, start + demandPageSize.value)
})

function resetDemandFilter() {
  demandFilter.title = ''
  demandFilter.demandType = ''
  demandFilter.status = ''
  demandPage.value = 1
}

function parseFormPayload(row: Record<string, unknown>): Partial<DemandFormModel> {
  let payload: Record<string, unknown> = {}
  const raw = row.formPayload
  if (typeof raw === 'string' && raw.trim()) {
    try {
      payload = JSON.parse(raw) as Record<string, unknown>
    } catch {
      payload = {}
    }
  } else if (raw && typeof raw === 'object') {
    payload = raw as Record<string, unknown>
  }
  return {
    id: Number(row.id),
    providerOrg: String(payload.providerOrg || row.assigneeOrg || ''),
    providerOrgId: payload.providerOrgId != null ? Number(payload.providerOrgId) : undefined,
    targetCatalogId: row.targetCatalogId != null ? Number(row.targetCatalogId) : undefined,
    catalogTitle: String(payload.catalogTitle || ''),
    dataName: String(payload.dataName || row.demandTitle || ''),
    systemNames: Array.isArray(payload.systemNames) ? (payload.systemNames as string[]) : [''],
    dataItems: Array.isArray(payload.dataItems) ? (payload.dataItems as string[]) : [],
    serviceDemandType: (payload.serviceDemandType as 'GOV' | 'NON_GOV') || 'GOV',
    matterIds: Array.isArray(payload.matterIds) ? (payload.matterIds as number[]) : [],
    matterNames: Array.isArray(payload.matterNames) ? (payload.matterNames as string[]) : [],
    matterMaterials: String(payload.matterMaterials || ''),
    usageScenario: String(payload.usageScenario || ''),
    demandBasis: String(payload.demandBasis || row.demandContent || ''),
    shareProvideMode: String(payload.shareProvideMode || row.supplyMode || ''),
    updateFrequency: String(payload.updateFrequency || '实时'),
    requesterOrg: String(row.requesterOrg || '承德高新技术产业开发区管理委员会'),
    contactName: String(payload.contactName || ''),
    contactPhone: String(payload.contactPhone || ''),
    contactEmail: String(payload.contactEmail || ''),
    demandType: (String(row.demandType || 'STRUCTURED') as 'STRUCTURED' | 'UNSTRUCTURED'),
    templateCode: String(row.templateCode || ''),
    demandContent: String(row.demandContent || ''),
  }
}

function buildDemandBody(v: DemandFormModel, draft: boolean) {
  return {
    draft,
    demandTitle: v.dataName,
    requesterOrg: v.requesterOrg,
    demandType: v.demandType || 'STRUCTURED',
    templateCode: v.templateCode || undefined,
    demandContent: v.demandBasis || v.usageScenario || v.demandContent || undefined,
    targetCatalogId: v.targetCatalogId,
    assigneeOrg: v.providerOrg || undefined,
    supplyMode: v.shareProvideMode || undefined,
    formPayload: {
      providerOrg: v.providerOrg,
      providerOrgId: v.providerOrgId,
      catalogTitle: v.catalogTitle,
      dataName: v.dataName,
      systemNames: v.systemNames,
      dataItems: v.dataItems,
      serviceDemandType: v.serviceDemandType,
      matterIds: v.matterIds,
      matterNames: v.matterNames,
      matterMaterials: v.matterMaterials,
      usageScenario: v.usageScenario,
      demandBasis: v.demandBasis,
      shareProvideMode: v.shareProvideMode,
      updateFrequency: v.updateFrequency,
      contactName: v.contactName,
      contactPhone: v.contactPhone,
      contactEmail: v.contactEmail,
    },
  }
}

function openCreateDemand() {
  applyMode.value = 'create'
  editingDemandId.value = 0
  applyFormModel.value = {
    requesterOrg: auth.user?.orgName || '承德高新技术产业开发区管理委员会',
    serviceDemandType: 'GOV',
    updateFrequency: '实时',
    systemNames: [''],
    dataItems: [],
    matterIds: [],
    matterNames: [],
    demandType: 'STRUCTURED',
  }
  createVisible.value = true
}

function openViewDemand(row: Record<string, unknown>) {
  applyMode.value = 'view'
  editingDemandId.value = Number(row.id)
  applyFormModel.value = parseFormPayload(row)
  createVisible.value = true
}

function openEditDemandForm(row: Record<string, unknown>) {
  applyMode.value = 'edit'
  editingDemandId.value = Number(row.id)
  applyFormModel.value = parseFormPayload(row)
  createVisible.value = true
}

function openTrack(row: Record<string, unknown>) {
  trackDrawer.row = row
  trackDrawer.visible = true
}

function demandOps(status: string) {
  const s = String(status || '')
  if (s === 'DRAFT') return ['view', 'edit', 'submit', 'delete'] as const
  if (s === 'SUBMITTED') return ['withdraw', 'view', 'track'] as const
  if (s === 'WITHDRAW_PENDING' || s === 'RETURNED') return ['view', 'edit', 'submit', 'delete', 'track'] as const
  if (s === 'CANCELLED') return ['view', 'track'] as const
  return ['view', 'track'] as const
}

function importHint() {
  ElMessage.info('请使用新建需求录入；批量导入请在「供需配置」维护模板后手工录入')
}

async function exportDemandsCsv() {
  const rows = filteredDemands.value
  const header = ['需求标题', '需求单位', '类型', '状态', '阶段']
  const lines = rows.map((r) => [
    r.demandTitle, r.requesterOrg, r.demandType, r.status, r.stage,
  ].map((x) => `"${String(x ?? '').replace(/"/g, '""')}"`).join(','))
  const blob = new Blob([[header.join(','), ...lines].join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `供需需求清单_${rows.length}条.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${rows.length} 条`)
}

const analysisPending = computed(() =>
  demands.value.filter((d) =>
    ['SUBMITTED', 'PRE_AUDITING', 'ANALYZING', 'SUPERVISING'].includes(String(d.status)),
  ),
)
const filteredAnalysis = computed(() =>
  analysisPending.value.filter((d) => {
    if (analysisFilter.title && !String(d.demandTitle || '').includes(analysisFilter.title.trim())) return false
    if (analysisFilter.org && !String(d.requesterOrg || '').includes(analysisFilter.org.trim())) return false
    if (analysisFilter.evalStatus && String(d.evalStatus || '') !== analysisFilter.evalStatus) return false
    return true
  }),
)
const pagedAnalysis = computed(() => {
  const start = (analysisPage.value - 1) * analysisPageSize.value
  return filteredAnalysis.value.slice(start, start + analysisPageSize.value)
})
const selectedAnalysis = computed(() =>
  demands.value.find((d) => Number(d.id) === analyzingId.value) || null,
)
const relationCounts = computed(() => {
  const nodes = relationGraph.value?.nodes || []
  const typeOf = (t: string) => nodes.filter((n) => String(n.type || '').toUpperCase() === t).length
  return {
    catalog: typeOf('CATALOG') || Number((analysisResult.value as any)?.catalogCount) || 0,
    table: typeOf('TABLE') || Number((analysisResult.value as any)?.tableCount) || 0,
    api: typeOf('API') || Number((analysisResult.value as any)?.apiCount) || 0,
  }
})
const matchPercent = computed(() => {
  const raw = selectedAnalysis.value?.matchScore ?? analysisResult.value?.matchScore
  const n = Number(raw)
  return Number.isFinite(n) ? Math.max(0, Math.min(100, Math.round(n))) : 0
})

function restoreAnalysisFromRow(row: Record<string, unknown>) {
  analysisResult.value = {
    demandId: row.id,
    analysisNote: row.analysisNote || '已有匹配结果',
    matchScore: row.matchScore,
    evalStatus: row.evalStatus,
    shareAttr: row.shareAttr,
    fulfillPath: row.fulfillPath,
  }
  let payload: Record<string, unknown> = {}
  const raw = row.analysisPayload
  if (typeof raw === 'string' && raw.trim()) {
    try {
      payload = JSON.parse(raw) as Record<string, unknown>
    } catch {
      payload = {}
    }
  } else if (raw && typeof raw === 'object') {
    payload = raw as Record<string, unknown>
  }
  analysisCandidates.value = (payload.candidates as Record<string, unknown>[]) || []
  relationGraph.value = (payload.relationGraph as typeof relationGraph.value) || null
  if (row.evalStatus) quickSet.evalStatus = String(row.evalStatus)
  if (row.shareAttr) quickSet.shareAttr = String(row.shareAttr)
  if (row.fulfillPath) dispatchForm.fulfillPath = row.fulfillPath as FulfillPath
  if (row.assigneeOrg) dispatchForm.assigneeOrg = String(row.assigneeOrg)
  if (row.analysisNote) dispatchForm.analysisNote = String(row.analysisNote)
}

async function selectAnalysisRow(row: Record<string, unknown>) {
  analyzingId.value = Number(row.id)
  if (row.matchScore == null || row.matchScore === '') {
    await analyzeDemand(Number(row.id))
  } else {
    restoreAnalysisFromRow(row)
  }
}

async function loadAnalysisOrgs() {
  try {
    const res = await api.get('/system/orgs')
    analysisOrgs.value = ((res.data || []) as { id: number; orgName: string }[]).map((o) => ({
      id: o.id,
      orgName: o.orgName,
    }))
  } catch {
    analysisOrgs.value = []
  }
}

function resetConfirmFilter() {
  confirmFilter.title = ''
  confirmFilter.status = ''
  confirmPage.value = 1
}

function openConfirmDrawer(row: Record<string, unknown>) {
  confirmDrawer.row = row
  confirmDrawer.visible = true
  confirmFeedback.value = String(row.confirmFeedback || '')
}

const supplyKpi = computed(() => {
  const tasks = supplyTasks.value
  return {
    running: tasks.filter((t) => ['RUNNING', 'PENDING', 'ACTIVE', 'PROCESSING'].includes(String(t.status))).length,
    success: tasks.filter((t) => ['SUCCESS', 'COMPLETED', 'DONE'].includes(String(t.status))).length,
    fail: tasks.filter((t) => ['FAILED', 'ERROR', 'FAIL'].includes(String(t.status))).length,
  }
})
const supplyShareRows = computed(() => supplyTasks.value)
const supplyExchangeRows = computed(() =>
  exchangeJobs.value.length
    ? exchangeJobs.value
    : supplyTasks.value.filter((t) => ['EXCHANGE', 'COLLECT'].includes(String(t.taskType))),
)

const superviseRows = computed(() =>
  demands.value.filter((d) =>
    ['SUBMITTED', 'PRE_AUDITING', 'ANALYZING', 'DISPATCHED', 'RETURNED', 'SUPERVISING', 'CORRECTION'].includes(String(d.status)),
  ),
)
const superviseKpi = computed(() => ({
  overdue: superviseRows.value.filter((d) => ['RETURNED', 'SUPERVISING'].includes(String(d.status)) || !d.assigneeOrg).length,
  feedback: superviseRows.value.filter((d) => String(d.status) === 'SUPERVISING').length,
  doneWeek: demands.value.filter((d) => ['CONFIRMED', 'COMPLETED'].includes(String(d.status))).length,
}))

function openFeedback(row: Record<string, unknown>) {
  feedbackDrawer.row = row
  feedbackDrawer.visible = true
}

const filteredListItems = computed(() => {
  if (listCenterSub.value === 'objection') {
    return listCenterItems.value.filter((r) => {
      if (objectionFilter.title && !String(r.title || '').includes(objectionFilter.title.trim())) return false
      if (objectionFilter.object && !String(r.catalogId || r.objectName || '').includes(objectionFilter.object.trim())) return false
      if (objectionFilter.provider && !String(r.providerOrg || '').includes(objectionFilter.provider.trim())) return false
      if (objectionFilter.status && String(r.status) !== objectionFilter.status) return false
      return true
    })
  }
  return listCenterItems.value.filter((r) => {
    if (listFilter.code && !String(r.code || r.catalogCode || r.id || '').includes(listFilter.code.trim())) return false
    if (listFilter.title && !String(r.title || '').includes(listFilter.title.trim())) return false
    if (listFilter.status && String(r.status || r.publishStatus || '') !== listFilter.status) return false
    return true
  })
})
const pagedListItems = computed(() => {
  const start = (listPage.value - 1) * listPageSize.value
  return filteredListItems.value.slice(start, start + listPageSize.value)
})

async function refreshManifestCounts() {
  try {
    const [cat, man, obj] = await Promise.all([
      api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } }),
      api.get('/exchange/supply/manifests'),
      api.get('/exchange/supply/objections'),
    ])
    manifestCounts.catalog = (cat.data || []).length
    manifestCounts.supply = (man.data || []).length
    manifestCounts.objection = (obj.data || []).length
  } catch {
    // ignore
  }
}

function shortManifestLabel(label: string) {
  return label.replace(/清单$/g, '').replace(/^目录/, '').replace(/^数据/, '').replace(/^已发布目录/, '已发布')
}

function showObjectionContent(row: Record<string, unknown>) {
  ElMessage.info(String(row.content || '暂无核查说明'))
}

const EVAL_STATUS_OPTIONS = [
  { value: 'PENDING', label: '待评估' },
  { value: 'MATCHED', label: '已匹配' },
  { value: 'PARTIAL', label: '部分匹配' },
  { value: 'UNMATCHED', label: '未匹配' },
]
const SHARE_ATTR_OPTIONS = [
  { value: 'OPEN', label: '无条件共享' },
  { value: 'CONDITIONAL', label: '有条件共享' },
  { value: 'RESTRICTED', label: '受限共享' },
  { value: 'INTERNAL', label: '内部使用' },
]
const RESOURCE_TYPE_LABEL: Record<string, string> = {
  CATALOG: '目录',
  TABLE: '库表',
  API: '接口',
  DEMAND: '需求',
}

const mainSections = SUPPLY_MAIN_SECTIONS
const manifestCenterSections = computed(() => {
  if (listCenterGroup.value === '供需清单') return supplyManifestSections
  if (listCenterGroup.value === '异议清单') return objectionManifestSections
  return catalogManifestSections
})

const selectedTemplate = computed(() =>
  templates.value.find((t) => t.templateCode === demandForm.templateCode),
)
const templateFields = computed(() => {
  const raw = selectedTemplate.value?.fieldSchema
  if (!raw) return [] as { key: string; label: string }[]
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (Array.isArray(parsed)) {
      return parsed.map((f: Record<string, string>) => ({
        key: f.key || f.name || f.code || 'field',
        label: f.label || f.name || f.key || '字段',
      }))
    }
    if (parsed && typeof parsed === 'object' && Array.isArray((parsed as { fields?: unknown }).fields)) {
      return ((parsed as { fields: Record<string, string>[] }).fields).map((f) => ({
        key: f.key || f.name || 'field',
        label: f.label || f.name || '字段',
      }))
    }
    return Object.keys(parsed as object).map((k) => ({ key: k, label: k }))
  } catch {
    return [] as { key: string; label: string }[]
  }
})

function onTemplateChange() {
  Object.keys(modelFieldValues).forEach((k) => delete modelFieldValues[k])
  const tpl = selectedTemplate.value
  if (tpl?.demandType) demandForm.demandType = tpl.demandType
  for (const f of templateFields.value) {
    modelFieldValues[f.key] = ''
  }
}

function fulfillLabel(path: unknown) {
  const hit = FULFILL_PATH_OPTIONS.find((o) => o.value === path)
  return hit?.label || String(path || '-')
}

function evalLabel(v: unknown) {
  return EVAL_STATUS_OPTIONS.find((o) => o.value === v)?.label || String(v || '-')
}

function shareLabel(v: unknown) {
  return SHARE_ATTR_OPTIONS.find((o) => o.value === v)?.label || String(v || '-')
}

function resourceTypeLabel(v: unknown) {
  return RESOURCE_TYPE_LABEL[String(v)] || String(v || '-')
}

function syncRoute() {
  // 嵌入门户时不跟门户 section 抢 query，只用本地 Tab
  if (props.embedded) return
  const r = resolveApplicationNav(route.query as Record<string, unknown>)
  moduleKey.value = 'supply-flow'
  let sec = String(route.query.sdSection || r.section || (props.mode === 'front' ? 'demand' : 'demand'))
  if (sec === 'home') {
    // 业务页不渲染首页
    sec = 'demand'
  }
  if (sec === 'catalog') {
    sec = 'manifest-center'
    listCenterGroup.value = '目录清单'
    listCenterSub.value = 'catalog-published'
  } else if (sec === 'objection') {
    sec = 'manifest-center'
    listCenterGroup.value = '异议清单'
    listCenterSub.value = 'objection'
  } else if (sec === 'manifest') {
    sec = 'manifest-center'
    listCenterGroup.value = '供需清单'
    listCenterSub.value = 'sd-history'
  }
  if (!SUPPLY_MAIN_SECTIONS.some((s) => s.key === sec) && sec !== 'manifest-center') {
    sec = 'demand'
  }
  section.value = sec
  const lg = String(route.query.listGroup || '')
  if (lg && MANIFEST_CENTER_SECTIONS.some((s) => s.group === lg)) {
    listCenterGroup.value = lg
    const first = MANIFEST_CENTER_SECTIONS.find((s) => s.group === lg)
    if (first) listCenterSub.value = first.key
  }
}

function setSection(key: string) {
  section.value = key
  if (props.embedded) {
    // 保持门户 tab=subscribe，供需子 Tab 用 sdSection，避免弹回 catalog
    router.replace({
      query: {
        ...route.query,
        system: 'portal',
        module: 'portal-home',
        section: 'subscribe',
        sdSection: key,
      },
    })
    loadSection()
    return
  }
  router.replace({
    query: {
      ...route.query,
      section: key,
      sdSection: key,
    },
    path: '/exchange/application/supply',
  })
  loadSection()
}

function setListCenterSub(key: string) {
  listCenterSub.value = key
  const hit = MANIFEST_CENTER_SECTIONS.find((s) => s.key === key)
  if (hit) listCenterGroup.value = hit.group
  listPage.value = 1
  loadListCenter()
}

function setListCenterGroup(group: string) {
  listCenterGroup.value = group
  const first = MANIFEST_CENTER_SECTIONS.find((s) => s.group === group)
  if (first) listCenterSub.value = first.key
  loadListCenter()
}

const statusTag = (s: string) => {
  if (['CONFIRMED', 'APPROVED', 'CLOSED', 'COMPLETED'].includes(s)) return 'success'
  if (['REJECTED', 'RETURNED', 'WITHDRAWN', 'CANCELLED'].includes(s)) return 'danger'
  if (['DISPATCHED', 'PRE_AUDITING', 'ANALYZING', 'SUPERVISING', 'CORRECTION'].includes(s)) return 'warning'
  return 'info'
}
void statusTag

async function searchResourceCatalog() {
  const res = await api.get('/exchange/supply/resource-search', {
    params: {
      keyword: resourceSearch.keyword || undefined,
      resourceType: resourceSearch.resourceType === 'ALL' ? undefined : resourceSearch.resourceType,
    },
  })
  resourceHits.value = res.data.items || []
}

function goPortalApply(row: Record<string, unknown>) {
  const id = row.resourceId
  router.push({
    path: '/exchange/analysis-portal/dept',
    query: { section: 'catalog', catalogId: id != null ? String(id) : undefined },
  })
}

async function loadSection() {
  loading.value = true
  try {
    if (section.value === 'demand') {
      const [tp, dm] = await Promise.all([
        api.get('/exchange/supply/templates'),
        api.get('/exchange/supply/demands'),
      ])
      templates.value = tp.data
      demands.value = dm.data
    } else if (section.value === 'analysis' || section.value === 'confirm' || section.value === 'supervise') {
      demands.value = (await api.get('/exchange/supply/demands')).data
      if (section.value === 'analysis') {
        await loadAnalysisOrgs()
        if (!analyzingId.value && analysisPending.value.length) {
          await selectAnalysisRow(analysisPending.value[0])
        }
      }
    } else if (section.value === 'home') {
      // 首页由 SupplyAppView 渲染
    } else if (section.value === 'supply') {
      const [dm, tasks] = await Promise.all([
        api.get('/exchange/supply/demands'),
        api.get('/exchange/supply/supply-tasks'),
      ])
      demands.value = dm.data
      supplyTasks.value = tasks.data || []
      exchangeJobs.value = supplyTasks.value.filter((t) => ['EXCHANGE', 'COLLECT'].includes(String(t.taskType)))
      if (!selectedDemandId.value && supplyViewDemands.value.length) {
        selectedDemandId.value = Number(supplyViewDemands.value[0].id)
      }
      if (selectedDemandId.value) {
        await loadSupplyView(selectedDemandId.value)
      } else {
        apiEndpoints.value = []
        sharePages.value = []
        duties.value = []
      }
    } else if (section.value === 'manifest-center') {
      await Promise.all([loadListCenter(), refreshManifestCounts()])
      listPage.value = 1
    } else if (section.value === 'catalog') {
      catalogs.value = (await api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } })).data
    } else if (section.value === 'objection') {
      const [cat, obj] = await Promise.all([
        api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } }),
        api.get('/exchange/supply/objections'),
      ])
      catalogs.value = cat.data
      objections.value = obj.data
    } else if (section.value === 'manifest') {
      manifests.value = (await api.get('/exchange/supply/manifests')).data
    }
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function loadListCenter() {
  const res = await api.get('/exchange/supply/list-center', { params: { listType: listCenterSub.value } })
  listCenterItems.value = res.data.items || []
  if (listCenterSub.value === 'objection') {
    objections.value = listCenterItems.value
    catalogs.value = (await api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } })).data
  }
}

async function submitDemand() {
  if (!demandForm.demandTitle) return ElMessage.warning('请填写需求标题')
  if (demandForm.demandType === 'UNSTRUCTURED' && !demandForm.demandContent) {
    return ElMessage.warning('请填写非结构化需求内容')
  }
  await api.post('/exchange/supply/demands', {
    ...demandForm,
    modelFields: demandForm.demandType === 'STRUCTURED' ? { ...modelFieldValues } : undefined,
  })
  ElMessage.success('需求已提交')
  demandForm.demandTitle = ''
  demandForm.demandContent = ''
  Object.keys(modelFieldValues).forEach((k) => { modelFieldValues[k] = '' })
  createVisible.value = false
  demands.value = (await api.get('/exchange/supply/demands')).data
  demandPage.value = 1
}

async function saveApplyDraft(v: DemandFormModel) {
  const body = buildDemandBody(v, true)
  if (applyMode.value === 'edit' && editingDemandId.value) {
    await api.post(`/exchange/supply/demands/${editingDemandId.value}/update`, { ...body, draft: true })
  } else {
    await api.post('/exchange/supply/demands', body)
  }
  ElMessage.success('已暂存草稿')
  createVisible.value = false
  demands.value = (await api.get('/exchange/supply/demands')).data
  demandPage.value = 1
}

async function saveApplySubmit(v: DemandFormModel) {
  const body = buildDemandBody(v, false)
  if (applyMode.value === 'edit' && editingDemandId.value) {
    await api.post(`/exchange/supply/demands/${editingDemandId.value}/update`, { ...body, submit: true })
  } else {
    await api.post('/exchange/supply/demands', body)
  }
  ElMessage.success('已提交，待数据主管部门审核')
  createVisible.value = false
  demands.value = (await api.get('/exchange/supply/demands')).data
  demandPage.value = 1
}

async function submitExistingDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/submit`)
  ElMessage.success('已提交，待数据主管部门审核')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function deleteDemandRow(id: number) {
  await api.post(`/exchange/supply/demands/${id}/delete`)
  ElMessage.success('已删除')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function withdrawDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/withdraw`)
  ElMessage.success('已撤销，状态为撤销待提交')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function analyzeDemand(id: number) {
  analyzingId.value = id
  analysisResult.value = (await api.post(`/exchange/supply/demands/${id}/analyze`)).data
  analysisCandidates.value = (analysisResult.value?.candidates as Record<string, unknown>[]) || []
  relationGraph.value = (analysisResult.value?.relationGraph as typeof relationGraph.value) || null
  if (analysisResult.value?.fulfillPath) {
    dispatchForm.fulfillPath = analysisResult.value.fulfillPath as FulfillPath
  }
  if (analysisResult.value?.evalStatus) {
    quickSet.evalStatus = String(analysisResult.value.evalStatus)
  }
  if (analysisResult.value?.shareAttr) {
    quickSet.shareAttr = String(analysisResult.value.shareAttr)
  }
  demands.value = (await api.get('/exchange/supply/demands')).data
  ElMessage.success('智能辅助分析完成')
}

async function dispatchDemand(id: number) {
  if (!dispatchForm.assigneeOrg?.trim()) {
    return ElMessage.warning('请选择分发部门')
  }
  await api.post(`/exchange/supply/demands/${id}/dispatch`, {
    ...dispatchForm,
    analysisNote: dispatchForm.analysisNote || analysisResult.value?.analysisNote || '已完成需求分析并分发',
  })
  ElMessage.success('已分发到对应部门')
  analyzingId.value = undefined
  demands.value = (await api.get('/exchange/supply/demands')).data
}

function openReturnDialog(id: number) {
  returnDialog.id = id
  returnNote.value = returnNote.value || '材料不全，请补充后重新提交'
  returnDialog.visible = true
}

async function confirmReturnDemandAnalysis() {
  if (!returnNote.value.trim()) return ElMessage.warning('请填写退回原因')
  await api.post(`/exchange/supply/demands/${returnDialog.id}/return`, {
    analysisNote: returnNote.value.trim(),
  })
  returnDialog.visible = false
  ElMessage.success('已退回数据提交部门')
  analyzingId.value = undefined
  demands.value = (await api.get('/exchange/supply/demands')).data
}

function openSuperviseDialog(id: number) {
  superviseDialog.id = id
  superviseDialog.visible = true
}

async function confirmSuperviseDemand() {
  if (!superviseNote.value.trim()) return ElMessage.warning('请填写督办说明')
  await api.post(`/exchange/supply/demands/${superviseDialog.id}/supervise`, {
    superviseNote: superviseNote.value.trim(),
  })
  superviseDialog.visible = false
  ElMessage.success('已发起督查督办')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function returnDemand(id: number) {
  openReturnDialog(id)
}

async function superviseDemand(id: number) {
  openSuperviseDialog(id)
}

async function applyQuickSettings(id?: number) {
  const targetId = id || analyzingId.value
  if (!targetId) return ElMessage.warning('请先选择需求并完成智能匹配')
  const res = await api.post(`/exchange/supply/demands/${targetId}/analysis-settings`, {
    evalStatus: quickSet.evalStatus,
    shareAttr: quickSet.shareAttr,
    fulfillPath: dispatchForm.fulfillPath,
  })
  ElMessage.success(`已设置：评估=${evalLabel(res.data.evalStatus)}，共享=${shareLabel(res.data.shareAttr)}`)
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function applyCandidate(row: Record<string, unknown>) {
  const targetId = analyzingId.value
  if (!targetId) return ElMessage.warning('请先对需求执行智能匹配')
  const body: Record<string, unknown> = {
    evalStatus: row.suggestedEvalStatus || 'MATCHED',
    shareAttr: row.suggestedShareAttr || 'CONDITIONAL',
    resourceType: row.resourceType,
    resourceId: row.resourceId,
    matchScore: row.score,
  }
  if (row.resourceType === 'CATALOG') {
    body.matchedCatalogId = row.resourceId
    body.fulfillPath = Number(row.score) >= 30 ? 'AUTHORIZE_EXISTING' : 'NEED_COLLECT'
  }
  const res = await api.post(`/exchange/supply/demands/${targetId}/analysis-settings`, body)
  quickSet.evalStatus = String(res.data.evalStatus)
  quickSet.shareAttr = String(res.data.shareAttr)
  if (res.data.fulfillPath) dispatchForm.fulfillPath = res.data.fulfillPath as FulfillPath
  ElMessage.success(`已一键绑定「${row.title}」`)
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function bindResourceToDemand(row: Record<string, unknown>) {
  if (!analyzingId.value) {
    ElMessage.info('请先在需求列表点击「智能匹配」，再绑定资源')
    resourceSearch.keyword = String(row.title || '')
    return
  }
  await applyCandidate(row)
}

async function confirmDemand(id: number, row: Record<string, unknown>) {
  const res = await api.post(`/exchange/supply/demands/${id}/confirm`, {
    confirmNote: confirmNote.value || '同意提供，生成共享任务',
    confirmFeedback: confirmFeedback.value || undefined,
    fulfillPath: row.fulfillPath || dispatchForm.fulfillPath,
    supplyMode: row.fulfillPath === 'NEED_COLLECT' ? 'COLLECT' : 'EXCHANGE',
    authLevel: 'DEPT',
    cascadeFlag: 0,
  })
  confirmResult.value = res.data
  const taskCount = (res.data.tasks as unknown[])?.length || 0
  ElMessage.success(`已同意提供：数据责任 #${res.data.dutyId}，生成 ${taskCount} 项共享任务`)
  selectedDemandId.value = id
  confirmDrawer.visible = false
  demands.value = (await api.get('/exchange/supply/demands')).data
  await loadSupplyView(id)
}

const confirmReturnDialog = reactive({ visible: false, id: 0 })
const confirmReturnReason = ref('不同意提供，请补充说明后重新分析分发')

function openConfirmReturn(id: number) {
  confirmReturnDialog.id = id
  confirmReturnDialog.visible = true
}

async function confirmReturnDemand(id: number) {
  openConfirmReturn(id)
}

async function submitConfirmReturn() {
  if (!confirmReturnReason.value.trim()) return ElMessage.warning('请填写不同意/退回原因')
  await api.post(`/exchange/supply/demands/${confirmReturnDialog.id}/confirm-return`, {
    confirmNote: confirmReturnReason.value.trim(),
    confirmFeedback: confirmFeedback.value || undefined,
  })
  confirmReturnDialog.visible = false
  confirmDrawer.visible = false
  ElMessage.success('已退回（不同意提供）')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function rejectDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/reject`, {
    confirmNote: confirmNote.value || '不符合共享范围',
  })
  ElMessage.success('已驳回')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function submitConfirmFeedback(id: number) {
  if (!confirmFeedback.value) return ElMessage.warning('请填写督查反馈')
  await api.post(`/exchange/supply/demands/${id}/confirm-feedback`, {
    confirmFeedback: confirmFeedback.value,
  })
  ElMessage.success('督查反馈已提交')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function completeDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/complete`, {
    confirmNote: confirmNote.value,
    confirmFeedback: confirmFeedback.value || undefined,
  })
  confirmDrawer.visible = false
  ElMessage.success('需求已办结，可在「数据供给查看」中查看共享方式')
  demands.value = (await api.get('/exchange/supply/demands')).data
  selectedDemandId.value = id
  await loadSupplyView(id)
  setSection('supply')
}

async function cancelDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/cancel`, {
    confirmNote: confirmNote.value || '需求已撤销',
  })
  confirmDrawer.visible = false
  ElMessage.success('需求已撤销')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

function openEdit(row: Record<string, unknown>) {
  editDialog.visible = true
  editDialog.id = Number(row.id)
  editDialog.demandTitle = String(row.demandTitle || '')
  editDialog.requesterOrg = String(row.requesterOrg || '')
  editDialog.assigneeOrg = String(row.assigneeOrg || '')
  editDialog.fulfillPath = (row.fulfillPath as FulfillPath) || 'NEED_COLLECT'
}

async function saveEdit() {
  await api.post(`/exchange/supply/demands/${editDialog.id}/update`, {
    demandTitle: editDialog.demandTitle,
    requesterOrg: editDialog.requesterOrg,
    assigneeOrg: editDialog.assigneeOrg,
    fulfillPath: editDialog.fulfillPath,
    confirmNote: confirmNote.value,
  })
  editDialog.visible = false
  ElMessage.success('需求已修改')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

/** 已分发至供数部门、待确认（含督办/补正） */
const confirmPending = computed(() =>
  demands.value.filter((d) => ['DISPATCHED', 'SUPERVISING', 'CORRECTION'].includes(String(d.status))),
)
/** 已确认 / 办结 / 退回 / 撤销等台账 */
const confirmManaged = computed(() =>
  demands.value.filter((d) => ['CONFIRMED', 'COMPLETED', 'CANCELLED', 'REJECTED', 'RETURNED'].includes(String(d.status))),
)
/** 数据供给查看：仅已办结 */
const supplyViewDemands = computed(() =>
  demands.value.filter((d) => String(d.status) === 'COMPLETED'),
)
const confirmRows = computed(() => {
  const ids = new Set<number>()
  const out: Record<string, unknown>[] = []
  for (const d of [...confirmPending.value, ...confirmManaged.value]) {
    const id = Number(d.id)
    if (ids.has(id)) continue
    ids.add(id)
    out.push(d)
  }
  return out
})
const filteredConfirm = computed(() =>
  confirmRows.value.filter((d) => {
    if (confirmFilter.title && !String(d.demandTitle || '').includes(confirmFilter.title.trim())) return false
    if (confirmFilter.status && String(d.status) !== confirmFilter.status) return false
    return true
  }),
)
const pagedConfirm = computed(() => {
  const start = (confirmPage.value - 1) * confirmPageSize.value
  return filteredConfirm.value.slice(start, start + confirmPageSize.value)
})

async function loadSupplyView(id: number) {
  selectedDemandId.value = id
  supplyView.value = (await api.get(`/exchange/supply/supply-view/${id}`)).data
  supplyTasks.value = (supplyView.value?.tasks as Record<string, unknown>[])
    || (await api.get('/exchange/supply/supply-tasks', { params: { demandId: id } })).data
  duties.value = (supplyView.value?.duties as Record<string, unknown>[])
    || (await api.get('/exchange/supply/duties', { params: { demandId: id } })).data
  exchangeJobs.value = (supplyView.value?.exchangeJobs as Record<string, unknown>[]) || []
  apiEndpoints.value = (supplyView.value?.apiEndpoints as Record<string, unknown>[]) || []
  sharePages.value = (supplyView.value?.sharePages as Record<string, unknown>[]) || []
}

async function onSupplyDemandPick(v: number | undefined) {
  if (v) await loadSupplyView(v)
}

async function submitObjection() {
  if (!objectionForm.catalogId || !objectionForm.content) return ElMessage.warning('请填写异议')
  await api.post('/exchange/supply/objections', objectionForm)
  objectionForm.content = ''
  ElMessage.success('异议已登记')
  await loadListCenter()
}

async function reopenObjectionAudit(id: number) {
  await api.post(`/exchange/supply/objections/${id}/process`, {
    action: 'REOPEN_AUDIT',
    handlerNote: '发现数据问题，回流需求审核',
  })
  ElMessage.success('已回流需求审核')
  await loadListCenter()
  demands.value = (await api.get('/exchange/supply/demands')).data
}

watch(() => [route.query.module, route.query.section, route.query.tab, route.query.sdSection, route.query.listGroup], () => {
  if (props.embedded) {
    const sd = String(route.query.sdSection || '')
    if (sd && SUPPLY_MAIN_SECTIONS.some((s) => s.key === sd)) {
      section.value = sd
      loadSection()
    }
    return
  }
  syncRoute()
  loadSection()
})
onMounted(() => {
  if (props.embedded) {
    const sd = String(route.query.sdSection || 'demand')
    section.value = SUPPLY_MAIN_SECTIONS.some((s) => s.key === sd) ? sd : 'demand'
    loadSection()
    return
  }
  syncRoute()
  loadSection()
})
</script>

<template>
  <div v-loading="loading">
    <el-radio-group
      v-if="mode !== 'front'"
      :model-value="section"
      style="margin-bottom:12px"
      @change="setSection"
    >
      <el-radio-button v-for="s in mainSections.filter(x => x.key !== 'home')" :key="s.key" :value="s.key">{{ s.label }}</el-radio-button>
    </el-radio-group>

    <PageCard v-if="section === 'demand'" :title="mode === 'front' ? '' : '数据需求管理'" class="sd-panel">
      <template v-if="mode === 'front'">
        <div class="sd-filter-card">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="需求标题" class="portal-field-lg">
              <el-input v-model="demandFilter.title" placeholder="请输入需求标题" clearable />
            </el-form-item>
            <el-form-item label="需求类型" class="portal-field-md">
              <el-select v-model="demandFilter.demandType" clearable placeholder="请选择需求类型">
                <el-option label="结构化" value="STRUCTURED" />
                <el-option label="非结构化" value="UNSTRUCTURED" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态" class="portal-field-md">
              <el-select v-model="demandFilter.status" clearable placeholder="请选择状态">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="待数据主管部门审核" value="SUBMITTED" />
                <el-option label="撤销待提交" value="WITHDRAW_PENDING" />
                <el-option label="预审中" value="PRE_AUDITING" />
                <el-option label="待确认" value="DISPATCHED" />
                <el-option label="督办中" value="SUPERVISING" />
                <el-option label="已确认" value="CONFIRMED" />
                <el-option label="已办结" value="COMPLETED" />
                <el-option label="已退回" value="RETURNED" />
                <el-option label="已撤销" value="CANCELLED" />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="demandPage = 1">查询</el-button>
              <el-button @click="resetDemandFilter">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="sd-filter-actions">
            <el-button type="primary" @click="openCreateDemand">新建需求</el-button>
            <el-button @click="importHint">导入</el-button>
            <el-button @click="exportDemandsCsv">导出</el-button>
          </div>
        </div>

        <div class="sd-table-card">
          <el-table :data="pagedDemands" stripe size="small">
            <el-table-column prop="demandTitle" label="需求标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="requesterOrg" label="需求单位" width="140" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ $statusLabel(row.demandType) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">
                {{ row.updatedAt ? String(row.updatedAt).replace('T', ' ').slice(0, 19) : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <template v-for="op in demandOps(String(row.status))" :key="op">
                  <el-button v-if="op === 'view'" link type="primary" @click="openViewDemand(row)">查看</el-button>
                  <el-button v-else-if="op === 'edit'" link type="primary" @click="openEditDemandForm(row)">修改</el-button>
                  <el-button v-else-if="op === 'submit'" link type="primary" @click="submitExistingDemand(Number(row.id))">提交</el-button>
                  <el-button v-else-if="op === 'delete'" link type="danger" @click="deleteDemandRow(Number(row.id))">删除</el-button>
                  <el-button v-else-if="op === 'withdraw'" link type="danger" @click="withdrawDemand(Number(row.id))">撤销</el-button>
                  <el-button v-else-if="op === 'track'" link type="primary" @click="openTrack(row)">需求跟踪</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <PortalPagination
            v-if="filteredDemands.length"
            v-model:page="demandPage"
            v-model:page-size="demandPageSize"
            :total="filteredDemands.length"
          />
        </div>

        <el-dialog
          v-model="createVisible"
          :title="applyMode === 'view' ? '查看需求' : applyMode === 'edit' ? '修改需求' : '新增需求'"
          width="1080px"
          top="4vh"
          destroy-on-close
        >
          <DemandApplyForm
            :model-value="applyFormModel"
            :readonly="applyMode === 'view'"
            @draft="saveApplyDraft"
            @submit="saveApplySubmit"
            @cancel="createVisible = false"
          />
        </el-dialog>

        <el-drawer v-model="trackDrawer.visible" title="需求跟踪" size="420px">
          <template v-if="trackDrawer.row">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="需求标题">{{ trackDrawer.row.demandTitle }}</el-descriptions-item>
              <el-descriptions-item label="申请单位">{{ trackDrawer.row.requesterOrg || '—' }}</el-descriptions-item>
              <el-descriptions-item label="阶段">{{ $statusLabel(trackDrawer.row.stage) }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ $statusLabel(trackDrawer.row.status) }}</el-descriptions-item>
              <el-descriptions-item label="匹配度">{{ trackDrawer.row.matchScore ?? '—' }}</el-descriptions-item>
              <el-descriptions-item label="分发单位">{{ trackDrawer.row.assigneeOrg || '—' }}</el-descriptions-item>
              <el-descriptions-item label="分析说明">{{ trackDrawer.row.analysisNote || '—' }}</el-descriptions-item>
              <el-descriptions-item label="督办说明">{{ trackDrawer.row.superviseNote || '—' }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </el-drawer>
      </template>

      <template v-else>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="支持模型化管理：选择需求模板后按模板字段填报；可切换结构化 / 非结构化输入。模板维护在「系统管理 → 供需配置」。"
      />
      <el-form label-width="96px" style="max-width:860px">
        <el-form-item label="需求模板">
          <el-select v-model="demandForm.templateCode" clearable placeholder="可选模板" style="width:280px" @change="onTemplateChange">
            <el-option v-for="t in templates" :key="t.templateCode" :label="`${t.templateName}（${t.demandType}）`" :value="t.templateCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求类型">
          <el-radio-group v-model="demandForm.demandType">
            <el-radio-button value="STRUCTURED">结构化</el-radio-button>
            <el-radio-button value="UNSTRUCTURED">非结构化</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="需求标题">
          <el-input v-model="demandForm.demandTitle" placeholder="模型化需求名称" />
        </el-form-item>
        <el-form-item label="申请方">
          <el-input v-model="demandForm.requesterOrg" />
        </el-form-item>
        <template v-if="demandForm.demandType === 'STRUCTURED'">
          <el-form-item v-for="f in templateFields" :key="f.key" :label="f.label">
            <el-input v-model="modelFieldValues[f.key]" :placeholder="`请输入${f.label}`" />
          </el-form-item>
          <el-form-item v-if="!templateFields.length" label="说明">
            <el-text type="info">未选择模板或模板无字段定义时，仅提交标题等基础信息；可在供需配置中维护模板 schema。</el-text>
          </el-form-item>
        </template>
        <el-form-item v-else label="需求正文">
          <el-input v-model="demandForm.demandContent" type="textarea" :rows="5" placeholder="粘贴或录入非结构化需求说明、附件摘要等" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitDemand">提交需求</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="demands" stripe size="small" style="margin-top:12px">
        <el-table-column prop="demandTitle" label="需求" min-width="160" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ $statusLabel(row.demandType) }}</template>
        </el-table-column>
        <el-table-column prop="templateCode" label="模板" width="120" />
        <el-table-column prop="stage" label="阶段" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" link type="danger" @click="withdrawDemand(Number(row.id))">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
      </template>
    </PageCard>

    <PageCard v-else-if="section === 'analysis'" :title="mode === 'front' ? '' : '数据需求分析'" class="sd-panel">
      <template v-if="mode === 'front'">
        <div class="sd-analysis">
          <div class="sd-analysis__left sd-table-card">
            <div class="sd-card-title">待分析需求（已提交待审核）</div>
            <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
              <el-form-item class="portal-field-md">
                <el-input v-model="analysisFilter.title" placeholder="请输入需求标题" clearable />
              </el-form-item>
              <el-form-item class="portal-field-sm">
                <el-input v-model="analysisFilter.org" placeholder="需求部门" clearable />
              </el-form-item>
              <el-form-item class="portal-field-sm">
                <el-select v-model="analysisFilter.evalStatus" clearable placeholder="评估状态">
                  <el-option v-for="o in EVAL_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button @click="loadSection">刷新</el-button>
              </el-form-item>
            </el-form>
            <button
              v-for="row in pagedAnalysis"
              :key="String(row.id)"
              type="button"
              class="sd-demand-card"
              :class="{ 'is-on': Number(row.id) === analyzingId }"
              @click="selectAnalysisRow(row)"
            >
              <div class="sd-demand-card__title">{{ row.demandTitle }}</div>
              <div class="sd-demand-card__meta">
                <span>{{ row.requesterOrg || '—' }}</span>
                <span>{{ row.updatedAt ? String(row.updatedAt).replace('T', ' ').slice(0, 16) : '—' }}</span>
              </div>
              <div class="sd-demand-card__score">匹配度 {{ row.matchScore != null ? row.matchScore : '—' }}%</div>
            </button>
            <el-empty v-if="!pagedAnalysis.length" description="暂无待匹配需求" :image-size="56" />
            <PortalPagination
              v-if="filteredAnalysis.length"
              v-model:page="analysisPage"
              v-model:page-size="analysisPageSize"
              :total="filteredAnalysis.length"
            />
          </div>

          <div class="sd-analysis__right sd-table-card">
            <div class="sd-card-title">需求详情与智能分析</div>
            <template v-if="selectedAnalysis">
              <div class="sd-detail-head">
                <h3>{{ selectedAnalysis.demandTitle }}</h3>
                <el-tag type="primary" size="small">{{ $statusLabel(selectedAnalysis.status) }}</el-tag>
              </div>
              <el-descriptions :column="2" size="small" class="sd-detail-desc">
                <el-descriptions-item label="需求部门">{{ selectedAnalysis.requesterOrg || '—' }}</el-descriptions-item>
                <el-descriptions-item label="提出时间">
                  {{ selectedAnalysis.createdAt ? String(selectedAnalysis.createdAt).replace('T', ' ').slice(0, 16) : '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="评估状态">
                  <el-tag size="small" type="success">{{ evalLabel(selectedAnalysis.evalStatus) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="共享属性">
                  <el-tag size="small">{{ shareLabel(selectedAnalysis.shareAttr) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="分发单位">{{ selectedAnalysis.assigneeOrg || dispatchForm.assigneeOrg || '—' }}</el-descriptions-item>
                <el-descriptions-item label="履约路径">{{ fulfillLabel(selectedAnalysis.fulfillPath || dispatchForm.fulfillPath) }}</el-descriptions-item>
                <el-descriptions-item label="分析说明" :span="2">{{ selectedAnalysis.analysisNote || analysisResult?.analysisNote || '—' }}</el-descriptions-item>
              </el-descriptions>

              <div class="sd-match-row">
                <div class="sd-match-gauge">
                  <el-progress type="circle" :percentage="matchPercent" :width="110" />
                  <div class="sd-match-gauge__lab">匹配度</div>
                </div>
                <div class="sd-relation-tri">
                  <div class="sd-relation-tri__title">关联关系图谱</div>
                  <div class="sd-relation-tri__nodes">
                    <div class="sd-rel-node is-catalog">目录 {{ relationCounts.catalog }} 个</div>
                    <div class="sd-rel-node is-table">库表 {{ relationCounts.table }} 个</div>
                    <div class="sd-rel-node is-api">接口 {{ relationCounts.api }} 个</div>
                  </div>
                  <ul v-if="relationGraph?.edges?.length" class="sd-rel-edges">
                    <li v-for="(e, idx) in relationGraph.edges" :key="idx">
                      {{ e.from }} → {{ e.to }}
                      <em v-if="e.label">（{{ e.label }}）</em>
                    </li>
                  </ul>
                </div>
              </div>

              <PageCard title="资源目录快速查询（同部门数据共享门户已发布目录）" style="margin:12px 0">
                <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
                  <el-form-item label="关键词" class="portal-field-lg">
                    <el-input
                      v-model="resourceSearch.keyword"
                      placeholder="目录/库表/接口名称"
                      clearable
                      @keyup.enter="searchResourceCatalog"
                    />
                  </el-form-item>
                  <el-form-item label="类型" class="portal-field-sm">
                    <el-select v-model="resourceSearch.resourceType">
                      <el-option label="全部" value="ALL" />
                      <el-option label="目录" value="CATALOG" />
                      <el-option label="库表" value="TABLE" />
                      <el-option label="接口" value="API" />
                    </el-select>
                  </el-form-item>
                  <el-form-item class="portal-form-actions">
                    <el-button type="primary" @click="searchResourceCatalog">查询</el-button>
                  </el-form-item>
                </el-form>
                <el-table :data="resourceHits" stripe size="small" max-height="200">
                  <el-table-column label="类型" width="80">
                    <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
                  </el-table-column>
                  <el-table-column prop="title" label="名称" min-width="140" show-overflow-tooltip />
                  <el-table-column prop="score" label="匹配度" width="80" />
                  <el-table-column label="操作" width="200">
                    <template #default="{ row }">
                      <el-button link type="primary" @click="bindResourceToDemand(row)">选用</el-button>
                      <el-button
                        v-if="row.resourceType === 'CATALOG'"
                        link
                        type="success"
                        @click="goPortalApply(row)"
                      >跳转门户申请</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </PageCard>

              <el-form inline class="portal-inline-form portal-inline-form--sm" size="small" style="margin-top:12px">
                <el-form-item label="分发部门" class="portal-field-lg" required>
                  <el-select
                    v-model="dispatchForm.assigneeOrg"
                    filterable
                    allow-create
                    default-first-option
                    placeholder="选择或输入数源部门"
                    style="width:240px"
                  >
                    <el-option
                      v-for="o in analysisOrgs"
                      :key="o.id"
                      :label="o.orgName"
                      :value="o.orgName"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="履约路径" class="portal-field-lg">
                  <el-select v-model="dispatchForm.fulfillPath">
                    <el-option v-for="o in FULFILL_PATH_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="评估" class="portal-field-sm">
                  <el-select v-model="quickSet.evalStatus">
                    <el-option v-for="o in EVAL_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="共享" class="portal-field-sm">
                  <el-select v-model="quickSet.shareAttr">
                    <el-option v-for="o in SHARE_ATTR_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                  </el-select>
                </el-form-item>
              </el-form>

              <div class="sd-detail-actions">
                <el-button type="primary" @click="dispatchDemand(Number(selectedAnalysis.id))">分发到部门</el-button>
                <el-button @click="returnDemand(Number(selectedAnalysis.id))">退回提交部门</el-button>
                <el-button type="warning" @click="superviseDemand(Number(selectedAnalysis.id))">督查督办</el-button>
                <el-button type="success" @click="applyQuickSettings(Number(selectedAnalysis.id))">一键设置信息项</el-button>
                <el-button link type="primary" @click="analyzeDemand(Number(selectedAnalysis.id))">重新智能匹配</el-button>
              </div>

              <div v-if="analysisCandidates.length" class="analysis-panel">
                <h4>智能匹配候选（目录 / 库表 / 接口）</h4>
                <el-table :data="analysisCandidates" stripe size="small">
                  <el-table-column label="类型" width="80">
                    <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
                  </el-table-column>
                  <el-table-column prop="title" label="资源" min-width="140" />
                  <el-table-column prop="score" label="匹配度%" width="90" />
                  <el-table-column prop="subtitle" label="说明" min-width="120" show-overflow-tooltip />
                  <el-table-column label="操作" width="100">
                    <template #default="{ row }">
                      <el-button link type="primary" @click="applyCandidate(row)">一键采用</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>
            <el-empty v-else description="请从左侧选择待匹配需求" :image-size="72" />
          </div>
        </div>

        <el-dialog v-model="returnDialog.visible" title="退回数据提交部门" width="480px" destroy-on-close>
          <el-form label-width="88px">
            <el-form-item label="退回原因" required>
              <el-input v-model="returnNote" type="textarea" :rows="3" placeholder="请说明退回原因" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="returnDialog.visible = false">取消</el-button>
            <el-button type="primary" @click="confirmReturnDemandAnalysis">确认退回</el-button>
          </template>
        </el-dialog>

        <el-dialog v-model="superviseDialog.visible" title="督查督办" width="480px" destroy-on-close>
          <el-form label-width="88px">
            <el-form-item label="督办说明" required>
              <el-input v-model="superviseNote" type="textarea" :rows="3" placeholder="请填写督办说明" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="superviseDialog.visible = false">取消</el-button>
            <el-button type="warning" @click="confirmSuperviseDemand">发起督办</el-button>
          </template>
        </el-dialog>
      </template>

      <template v-else>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="预审：分析/分发/退回/督办。资源目录快查仅含已发布到部门共享门户的统一目录；已满足可跳转门户申请，否则分发数源进入审核。支持智能匹配与一键设置评估状态/共享属性。"
      />

      <PageCard title="资源目录快速查询" style="margin-bottom:12px">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="关键词" class="portal-field-lg">
            <el-input v-model="resourceSearch.keyword" placeholder="目录/库表/接口名称" clearable @keyup.enter="searchResourceCatalog" />
          </el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="resourceSearch.resourceType">
              <el-option label="全部" value="ALL" />
              <el-option label="目录" value="CATALOG" />
              <el-option label="库表" value="TABLE" />
              <el-option label="接口" value="API" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="searchResourceCatalog">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="resourceHits" stripe size="small" max-height="220">
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
          </el-table-column>
          <el-table-column prop="resourceCode" label="编码" width="120" />
          <el-table-column prop="title" label="名称" min-width="140" />
          <el-table-column prop="score" label="匹配度" width="80" />
          <el-table-column prop="subtitle" label="说明" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" @click="bindResourceToDemand(row)">选用</el-button>
              <el-button
                v-if="row.resourceType === 'CATALOG'"
                link
                type="success"
                @click="goPortalApply(row)"
              >跳转门户申请</el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>

      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="履约路径" class="portal-field-xl">
          <el-select v-model="dispatchForm.fulfillPath">
            <el-option v-for="o in FULFILL_PATH_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="分发单位" class="portal-field-md"><el-input v-model="dispatchForm.assigneeOrg" /></el-form-item>
        <el-form-item label="督办说明" class="portal-field-xl"><el-input v-model="superviseNote" /></el-form-item>
      </el-form>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="评估状态" class="portal-field-md">
          <el-select v-model="quickSet.evalStatus">
            <el-option v-for="o in EVAL_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="共享属性" class="portal-field-md">
          <el-select v-model="quickSet.shareAttr">
            <el-option v-for="o in SHARE_ATTR_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="success" @click="applyQuickSettings()">一键设置当前需求</el-button>
        </el-form-item>
      </el-form>

      <el-table
        :data="demands.filter(d => ['SUBMITTED','ANALYZING','DISPATCHED','RETURNED','SUPERVISING'].includes(String(d.status)))"
        stripe
        size="small"
      >
        <el-table-column prop="demandTitle" label="需求" min-width="140" />
        <el-table-column prop="matchScore" label="匹配度" width="80" />
        <el-table-column label="评估" width="100">
          <template #default="{ row }">{{ evalLabel(row.evalStatus) }}</template>
        </el-table-column>
        <el-table-column label="共享" width="110">
          <template #default="{ row }">{{ shareLabel(row.shareAttr) }}</template>
        </el-table-column>
        <el-table-column label="履约路径" width="150">
          <template #default="{ row }">{{ fulfillLabel(row.fulfillPath) }}</template>
        </el-table-column>
        <el-table-column prop="assigneeOrg" label="分发单位" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="analyzeDemand(Number(row.id))">智能匹配</el-button>
            <el-button link @click="dispatchDemand(Number(row.id))">分发</el-button>
            <el-button link type="warning" @click="returnDemand(Number(row.id))">退回</el-button>
            <el-button link type="danger" @click="superviseDemand(Number(row.id))">督办</el-button>
            <el-button link type="success" @click="applyQuickSettings(Number(row.id))">一键设置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="analysisResult" class="analysis-panel">
        <el-alert
          type="success"
          :closable="false"
          style="margin-bottom:12px"
          :title="`需求 #${analysisResult.demandId}：${analysisResult.analysisNote}`"
        />
        <h4>智能匹配候选（目录 / 库表 / 接口）</h4>
        <el-table :data="analysisCandidates" stripe size="small" style="margin-bottom:12px">
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
          </el-table-column>
          <el-table-column prop="title" label="资源" min-width="140" />
          <el-table-column prop="score" label="匹配度%" width="90" />
          <el-table-column label="建议评估" width="100">
            <template #default="{ row }">{{ evalLabel(row.suggestedEvalStatus) }}</template>
          </el-table-column>
          <el-table-column label="建议共享" width="110">
            <template #default="{ row }">{{ shareLabel(row.suggestedShareAttr) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click="applyCandidate(row)">一键采用</el-button>
            </template>
          </el-table-column>
        </el-table>

        <h4>关联关系图</h4>
        <div v-if="relationGraph?.nodes?.length" class="relation-graph">
          <div class="relation-nodes">
            <div
              v-for="n in relationGraph.nodes"
              :key="String(n.id)"
              class="relation-node"
              :class="`is-${String(n.type).toLowerCase()}`"
            >
              <span class="relation-node__type">{{ resourceTypeLabel(n.type) }}</span>
              <span class="relation-node__label">{{ n.label }}</span>
            </div>
          </div>
          <ul class="relation-edges">
            <li v-for="(e, idx) in relationGraph.edges || []" :key="idx">
              {{ e.from }} → {{ e.to }}
              <el-tag size="small" type="info">{{ e.label }}</el-tag>
            </li>
          </ul>
        </div>
        <el-empty v-else description="暂无关联关系" :image-size="64" />
      </div>
      </template>
    </PageCard>

    <PageCard v-else-if="section === 'supervise'" :title="mode === 'front' ? '' : '业务督办'" class="sd-panel">
      <template v-if="mode === 'front'">
        <div class="sd-kpi-row">
          <div class="sd-kpi tone-amber">
            <div class="sd-kpi__lab">超时未办</div>
            <div class="sd-kpi__num">{{ superviseKpi.overdue }}</div>
          </div>
          <div class="sd-kpi tone-blue">
            <div class="sd-kpi__lab">待反馈</div>
            <div class="sd-kpi__num">{{ superviseKpi.feedback }}</div>
          </div>
          <div class="sd-kpi tone-green">
            <div class="sd-kpi__lab">本周办结</div>
            <div class="sd-kpi__num">{{ superviseKpi.doneWeek }}</div>
          </div>
        </div>
        <div class="sd-filter-card">
          <el-form inline class="portal-inline-form">
            <el-form-item label="督办说明" class="portal-field-xl">
              <el-input v-model="superviseNote" placeholder="请填写督办说明" clearable />
            </el-form-item>
          </el-form>
        </div>
        <div class="sd-table-card">
          <el-table :data="superviseRows" stripe size="small">
            <el-table-column label="督办单号" width="120">
              <template #default="{ row }">DB{{ String(row.id).padStart(6, '0') }}</template>
            </el-table-column>
            <el-table-column label="关联需求" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.demandTitle }}</template>
            </el-table-column>
            <el-table-column prop="assigneeOrg" label="责任单位" width="120" />
            <el-table-column label="督办类型" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'RETURNED' ? 'primary' : 'warning'" size="small">
                  {{ row.status === 'RETURNED' ? '退回督办' : '超时督办' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="superviseNote" label="督办说明" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="superviseDemand(Number(row.id))">催办</el-button>
                <el-button link type="primary" @click="openFeedback(row)">查看反馈</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-drawer v-model="feedbackDrawer.visible" title="督办反馈" size="400px">
          <template v-if="feedbackDrawer.row">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="需求">{{ feedbackDrawer.row.demandTitle }}</el-descriptions-item>
              <el-descriptions-item label="责任单位">{{ feedbackDrawer.row.assigneeOrg || '—' }}</el-descriptions-item>
              <el-descriptions-item label="督办说明">{{ feedbackDrawer.row.superviseNote || '—' }}</el-descriptions-item>
              <el-descriptions-item label="确认反馈">{{ feedbackDrawer.row.confirmFeedback || '暂无反馈' }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ $statusLabel(feedbackDrawer.row.status) }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </el-drawer>
      </template>
      <template v-else>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="对超时未办、退回待补的需求发起督办，跟踪反馈闭环。"
      />
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="督办说明" class="portal-field-xl">
          <el-input v-model="superviseNote" placeholder="请填写督办说明" clearable />
        </el-form-item>
      </el-form>
      <el-table
        :data="demands.filter(d => ['SUBMITTED','PRE_AUDITING','ANALYZING','DISPATCHED','RETURNED','SUPERVISING'].includes(String(d.status)))"
        stripe
        size="small"
      >
        <el-table-column prop="demandTitle" label="需求" min-width="160" />
        <el-table-column prop="requesterOrg" label="申请单位" width="120" />
        <el-table-column prop="assigneeOrg" label="责任单位" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="superviseNote" label="督办说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="superviseDemand(Number(row.id))">催办</el-button>
            <el-button link type="primary" @click="setSection('analysis')">去预审</el-button>
          </template>
        </el-table-column>
      </el-table>
      </template>
    </PageCard>

    <PageCard v-else-if="section === 'confirm'" :title="mode === 'front' ? '' : '数据需求确认'" class="sd-panel">
      <template v-if="mode === 'front'">
        <div class="sd-filter-card">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="需求标题" class="portal-field-lg">
              <el-input v-model="confirmFilter.title" placeholder="请输入需求标题" clearable />
            </el-form-item>
            <el-form-item label="状态" class="portal-field-md">
              <el-select v-model="confirmFilter.status" clearable placeholder="全部状态">
                <el-option label="待确认" value="DISPATCHED" />
                <el-option label="督办中" value="SUPERVISING" />
                <el-option label="待补正" value="CORRECTION" />
                <el-option label="已确认" value="CONFIRMED" />
                <el-option label="已退回" value="RETURNED" />
                <el-option label="已办结" value="COMPLETED" />
                <el-option label="已撤销" value="CANCELLED" />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="confirmPage = 1">查询</el-button>
              <el-button @click="resetConfirmFilter">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
        <div class="sd-table-card">
          <el-table :data="pagedConfirm" stripe size="small" @row-click="openConfirmDrawer">
            <el-table-column prop="demandTitle" label="需求标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="requesterOrg" label="需求单位" width="120" />
            <el-table-column prop="assigneeOrg" label="供数单位" width="120" />
            <el-table-column label="匹配目录" width="100">
              <template #default="{ row }">{{ row.matchedCatalogId || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="320" fixed="right">
              <template #default="{ row }">
                <template v-if="['DISPATCHED','SUPERVISING','CORRECTION'].includes(String(row.status))">
                  <el-button link type="success" @click.stop="confirmDemand(Number(row.id), row)">同意并生成共享任务</el-button>
                  <el-button link type="warning" @click.stop="confirmReturnDemand(Number(row.id))">退回</el-button>
                  <el-button link @click.stop="openConfirmDrawer(row)">督查反馈</el-button>
                </template>
                <template v-else-if="row.status === 'CONFIRMED'">
                  <el-button link type="success" @click.stop="completeDemand(Number(row.id))">办结</el-button>
                  <el-button link @click.stop="openEdit(row)">修改</el-button>
                  <el-button link type="info" @click.stop="cancelDemand(Number(row.id))">撤销</el-button>
                </template>
                <template v-else-if="row.status === 'COMPLETED'">
                  <el-button link type="primary" @click.stop="loadSupplyView(Number(row.id)); setSection('supply')">供给查看</el-button>
                  <el-button link @click.stop="openConfirmDrawer(row)">详情</el-button>
                </template>
                <el-button v-else link type="primary" @click.stop="openConfirmDrawer(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <PortalPagination
            v-if="filteredConfirm.length"
            v-model:page="confirmPage"
            v-model:page-size="confirmPageSize"
            :total="filteredConfirm.length"
          />
        </div>

        <el-drawer v-model="confirmDrawer.visible" title="需求确认" size="480px">
          <template v-if="confirmDrawer.row">
            <h4 class="confirm-h">基本信息</h4>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="标题">{{ confirmDrawer.row.demandTitle }}</el-descriptions-item>
              <el-descriptions-item label="申请单位">{{ confirmDrawer.row.requesterOrg || '—' }}</el-descriptions-item>
              <el-descriptions-item label="供数单位">{{ confirmDrawer.row.assigneeOrg || '—' }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ $statusLabel(confirmDrawer.row.status) }}</el-descriptions-item>
              <el-descriptions-item v-if="confirmDrawer.row.superviseNote" label="督办说明">{{ confirmDrawer.row.superviseNote }}</el-descriptions-item>
            </el-descriptions>
            <h4 class="confirm-h">匹配信息</h4>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="匹配目录">{{ confirmDrawer.row.matchedCatalogId || '—' }}</el-descriptions-item>
              <el-descriptions-item label="履约路径">{{ fulfillLabel(confirmDrawer.row.fulfillPath) }}</el-descriptions-item>
              <el-descriptions-item label="共享属性">{{ shareLabel(confirmDrawer.row.shareAttr) }}</el-descriptions-item>
            </el-descriptions>
            <h4 class="confirm-h">确认说明</h4>
            <el-input v-model="confirmNote" type="textarea" :rows="2" placeholder="同意提供时可填写说明" />
            <h4 class="confirm-h">督查反馈</h4>
            <el-input v-model="confirmFeedback" type="textarea" :rows="2" placeholder="对平台管理员督查督办的反馈意见" />
            <div class="sd-drawer-actions">
              <el-button
                v-if="['DISPATCHED','SUPERVISING','CORRECTION'].includes(String(confirmDrawer.row.status))"
                type="success"
                @click="confirmDemand(Number(confirmDrawer.row.id), confirmDrawer.row)"
              >同意并生成共享任务</el-button>
              <el-button
                v-if="['DISPATCHED','SUPERVISING','CORRECTION'].includes(String(confirmDrawer.row.status))"
                type="warning"
                @click="confirmReturnDemand(Number(confirmDrawer.row.id))"
              >退回</el-button>
              <el-button type="warning" plain @click="submitConfirmFeedback(Number(confirmDrawer.row.id))">督查反馈</el-button>
              <el-button
                v-if="confirmDrawer.row.status === 'CONFIRMED'"
                type="success"
                @click="completeDemand(Number(confirmDrawer.row.id))"
              >办结</el-button>
              <el-button
                v-if="confirmDrawer.row.status === 'CONFIRMED' || ['DISPATCHED','SUPERVISING','CORRECTION'].includes(String(confirmDrawer.row.status))"
                @click="openEdit(confirmDrawer.row)"
              >修改</el-button>
              <el-button
                v-if="confirmDrawer.row.status === 'CONFIRMED'"
                type="info"
                @click="cancelDemand(Number(confirmDrawer.row.id))"
              >撤销</el-button>
              <el-button
                v-if="['CONFIRMED','COMPLETED'].includes(String(confirmDrawer.row.status))"
                type="primary"
                :disabled="confirmDrawer.row.status !== 'COMPLETED'"
                @click="loadSupplyView(Number(confirmDrawer.row.id)); setSection('supply'); confirmDrawer.visible = false"
              >{{ confirmDrawer.row.status === 'COMPLETED' ? '供给查看' : '请先办结' }}</el-button>
            </div>
            <div v-if="confirmResult" class="confirm-result">
              <el-alert type="success" :closable="false" show-icon
                :title="`已同意提供并生成共享任务：数据责任 #${confirmResult.dutyId}，共 ${(confirmResult.tasks as unknown[])?.length || 0} 项`" />
            </div>
          </template>
        </el-drawer>
      </template>
      <template v-else>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="数据提供部门确认：同意并生成共享任务；不同意须填写原因后退回；对督查督办填写督查反馈；已确认需求可办结、撤销、修改。"
      />
      <el-form label-width="88px" style="max-width:720px;margin-bottom:12px">
        <el-form-item label="确认说明">
          <el-input v-model="confirmNote" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="督查反馈">
          <el-input v-model="confirmFeedback" type="textarea" :rows="2" placeholder="供数部门对督办的反馈意见" />
        </el-form-item>
      </el-form>

      <h4 class="confirm-h">待确认需求</h4>
      <el-table :data="confirmPending" stripe size="small">
        <el-table-column prop="demandTitle" label="需求" min-width="140" />
        <el-table-column prop="assigneeOrg" label="供数单位" width="110" />
        <el-table-column prop="matchedCatalogId" label="匹配目录" width="90" />
        <el-table-column label="履约路径" width="150">
          <template #default="{ row }">{{ fulfillLabel(row.fulfillPath) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="400" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="confirmDemand(Number(row.id), row)">同意并生成共享任务</el-button>
            <el-button link type="warning" @click="confirmReturnDemand(Number(row.id))">退回</el-button>
            <el-button link @click="submitConfirmFeedback(Number(row.id))">督查反馈</el-button>
            <el-button link @click="openEdit(row)">修改</el-button>
            <el-button link type="info" @click="cancelDemand(Number(row.id))">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>

      <h4 class="confirm-h">已确认 / 办结台账</h4>
      <el-table :data="confirmManaged" stripe size="small">
        <el-table-column prop="demandTitle" label="需求" min-width="140" />
        <el-table-column prop="confirmNote" label="确认说明" min-width="160" show-overflow-tooltip />
        <el-table-column prop="confirmFeedback" label="督查反馈" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'CONFIRMED'" link type="success" @click="completeDemand(Number(row.id))">办结</el-button>
            <el-button v-if="!['COMPLETED','CANCELLED','WITHDRAWN'].includes(String(row.status))" link @click="openEdit(row)">修改</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" link type="info" @click="cancelDemand(Number(row.id))">撤销</el-button>
            <el-button v-if="row.status === 'COMPLETED'" link type="primary" @click="loadSupplyView(Number(row.id)); setSection('supply')">供给查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="confirmResult" class="confirm-result">
        <el-alert type="success" :closable="false" show-icon style="margin-bottom:12px"
          :title="`确认成功：数据责任 #${confirmResult.dutyId}；${(confirmResult.integrations as any)?.message || ''}`" />
        <el-descriptions :column="1" border size="small" title="系统对接结果">
          <el-descriptions-item label="目录系统">
            匹配目录 ID={{ (confirmResult.integrations as any)?.catalog?.matchedCatalogId || '-' }}，
            责任 ID={{ (confirmResult.integrations as any)?.catalog?.dutyId }}
          </el-descriptions-item>
          <el-descriptions-item label="数据归集系统">
            {{ ((confirmResult.integrations as any)?.collect?.tasks || []).length }} 项归集任务
          </el-descriptions-item>
          <el-descriptions-item label="共享交换系统">
            {{ ((confirmResult.integrations as any)?.exchange?.tasks || []).length }} 项共享/交换任务
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="(confirmResult.tasks as Record<string, unknown>[]) || []" stripe size="small" style="margin-top:12px">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ $statusLabel(row.taskType) }}</template>
          </el-table-column>
          <el-table-column prop="taskName" label="任务" min-width="200" />
          <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
          <el-table-column prop="refFlowCode" label="关联引用" width="140" />
        </el-table>
      </div>
      </template>
    </PageCard>

    <PageCard v-else-if="section === 'supply'" :title="mode === 'front' ? '' : '数据供给查看'" class="sd-panel">
      <template v-if="mode === 'front'">
        <div class="sd-filter-card">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="办结需求" class="portal-field-xl">
              <el-select
                v-model="selectedDemandId"
                clearable
                filterable
                placeholder="请选择已办结的需求"
                style="min-width:280px"
                @change="onSupplyDemandPick"
              >
                <el-option
                  v-for="d in supplyViewDemands"
                  :key="String(d.id)"
                  :label="`${d.demandTitle}（${$statusLabel(d.status)}）`"
                  :value="Number(d.id)"
                />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" :disabled="!selectedDemandId" @click="selectedDemandId && loadSupplyView(selectedDemandId)">刷新供给</el-button>
            </el-form-item>
          </el-form>
        </div>
        <div class="sd-table-card">
          <el-empty
            v-if="!supplyViewDemands.length"
            description="暂无已办结需求。请先在「数据需求确认」中同意提供并办结。"
          />
          <template v-else-if="!selectedDemandId">
            <el-empty description="请选择已办结的数据需求，查看共享方式、交换作业、接口与通用共享页" />
          </template>
          <template v-else>
          <el-tabs v-model="supplyTab">
            <el-tab-pane label="共享方式" name="share" />
            <el-tab-pane label="交换作业" name="exchange" />
            <el-tab-pane label="接口服务" name="api" />
            <el-tab-pane label="通用共享页" name="page" />
          </el-tabs>
          <div class="sd-kpi-row sd-kpi-row--sm">
            <div class="sd-kpi tone-blue"><div class="sd-kpi__lab">运行中</div><div class="sd-kpi__num">{{ supplyKpi.running }}</div></div>
            <div class="sd-kpi tone-green"><div class="sd-kpi__lab">成功</div><div class="sd-kpi__num">{{ supplyKpi.success }}</div></div>
            <div class="sd-kpi tone-red"><div class="sd-kpi__lab">失败</div><div class="sd-kpi__num">{{ supplyKpi.fail }}</div></div>
          </div>

          <el-table v-if="supplyTab === 'share'" :data="supplyShareRows" stripe size="small" empty-text="暂无共享任务（请先同意并生成共享任务）">
            <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip />
            <el-table-column label="共享方式" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="row.taskType === 'API' ? 'warning' : (row.taskType === 'COLLECT' ? 'success' : 'primary')">
                  {{ $statusLabel(row.taskType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="关联需求" width="100">
              <template #default="{ row }">{{ row.demandId || selectedDemandId || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">
                {{ row.updatedAt ? String(row.updatedAt).replace('T', ' ').slice(0, 19) : (row.createdAt ? String(row.createdAt).replace('T', ' ').slice(0, 19) : '—') }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTrack(demands.find(d => Number(d.id) === Number(row.demandId || selectedDemandId)) || row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-table v-else-if="supplyTab === 'exchange'" :data="supplyExchangeRows" stripe size="small" empty-text="暂无交换/归集作业">
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ $statusLabel(row.taskType) }}</template>
            </el-table-column>
            <el-table-column prop="taskName" label="作业" min-width="160" />
            <el-table-column prop="flowCode" label="流编码" width="120">
              <template #default="{ row }">{{ row.flowCode || row.refFlowCode || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
            </el-table-column>
          </el-table>

          <el-table v-else-if="supplyTab === 'api'" :data="apiEndpoints" stripe size="small" empty-text="暂无接口服务">
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="method" label="方式" width="80" />
            <el-table-column prop="endpoint" label="地址" min-width="160" show-overflow-tooltip />
          </el-table>

          <el-table v-else :data="sharePages" stripe size="small" empty-text="暂无通用共享页">
            <el-table-column prop="title" label="页面" min-width="140" />
            <el-table-column prop="url" label="链接" min-width="160" show-overflow-tooltip />
            <el-table-column label="打开" width="80">
              <template #default="{ row }">
                <el-button link type="primary" @click="router.push(String(row.url))">进入</el-button>
              </template>
            </el-table-column>
          </el-table>
          </template>
        </div>
      </template>
      <template v-else>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="针对已办结的数据需求，查看共享方式、交换作业、接口与通用共享页面。"
      />
      <el-select v-model="selectedDemandId" filterable placeholder="选择已办结需求" style="width:360px;margin-bottom:12px" @change="loadSupplyView">
        <el-option
          v-for="d in supplyViewDemands"
          :key="String(d.id)"
          :label="`${d.demandTitle}（${$statusLabel(d.status)}）`"
          :value="Number(d.id)"
        />
      </el-select>
      <h4 v-if="duties.length" class="confirm-h">数据责任</h4>
      <el-table v-if="duties.length" :data="duties" stripe size="small" style="margin-bottom:12px">
        <el-table-column prop="dutyOrg" label="责任单位" min-width="120" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ $statusLabel(row.dutyType) }}</template>
        </el-table-column>
        <el-table-column label="履约路径" width="160">
          <template #default="{ row }">{{ fulfillLabel(row.fulfillPath) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>

      <el-row :gutter="12">
        <el-col :span="24" :md="8">
          <PageCard title="交换作业">
            <el-table :data="exchangeJobs" stripe size="small" empty-text="暂无交换/归集作业">
              <el-table-column label="类型" width="90">
                <template #default="{ row }">{{ $statusLabel(row.taskType) }}</template>
              </el-table-column>
              <el-table-column prop="taskName" label="作业" min-width="120" />
              <el-table-column prop="flowCode" label="流编码" width="100" />
              <el-table-column label="状态" width="80">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
            </el-table>
          </PageCard>
        </el-col>
        <el-col :span="24" :md="8">
          <PageCard title="接口">
            <el-table :data="apiEndpoints" stripe size="small" empty-text="暂无接口">
              <el-table-column prop="name" label="名称" min-width="100" />
              <el-table-column prop="method" label="方式" width="80" />
              <el-table-column prop="endpoint" label="地址" min-width="140" show-overflow-tooltip />
            </el-table>
          </PageCard>
        </el-col>
        <el-col :span="24" :md="8">
          <PageCard title="通用共享页面">
            <el-table :data="sharePages" stripe size="small" empty-text="暂无共享页">
              <el-table-column prop="title" label="页面" min-width="120" />
              <el-table-column prop="url" label="链接" min-width="140" show-overflow-tooltip />
              <el-table-column label="打开" width="80">
                <template #default="{ row }">
                  <el-button link type="primary" @click="router.push(String(row.url))">进入</el-button>
                </template>
              </el-table-column>
            </el-table>
          </PageCard>
        </el-col>
      </el-row>
      <el-empty v-if="!selectedDemandId" description="请选择已办结需求查看供给共享方式" />
      </template>
    </PageCard>

    <PageCard v-else-if="section === 'manifest-center'" :title="mode === 'front' ? '' : '数据清单中心'" class="sd-panel">
      <template v-if="mode === 'front'">
        <div class="sd-manifest-cards">
          <div class="sd-mcard tone-blue">
            <div class="sd-mcard__top"><span>目录清单</span><b>{{ manifestCounts.catalog }}</b></div>
            <div class="sd-mcard__grid">
              <button
                v-for="s in catalogManifestSections"
                :key="s.key"
                type="button"
                class="sd-mcard__btn"
                :class="{ 'is-on': listCenterSub === s.key }"
                @click="setListCenterSub(s.key)"
              >{{ shortManifestLabel(s.label) }}</button>
            </div>
          </div>
          <div class="sd-mcard tone-green">
            <div class="sd-mcard__top"><span>供需清单</span><b>{{ manifestCounts.supply }}</b></div>
            <div class="sd-mcard__grid">
              <button
                v-for="s in supplyManifestSections"
                :key="s.key"
                type="button"
                class="sd-mcard__btn"
                :class="{ 'is-on': listCenterSub === s.key }"
                @click="setListCenterSub(s.key)"
              >{{ shortManifestLabel(s.label) }}</button>
            </div>
          </div>
          <div class="sd-mcard tone-amber">
            <div class="sd-mcard__top"><span>异议清单</span><b>{{ manifestCounts.objection }}</b></div>
            <div class="sd-mcard__grid">
              <button
                type="button"
                class="sd-mcard__btn"
                :class="{ 'is-on': listCenterSub === 'objection' }"
                @click="setListCenterSub('objection')"
              >异议申请</button>
              <button type="button" class="sd-mcard__btn" @click="setListCenterSub('objection')">异议审核</button>
              <button type="button" class="sd-mcard__btn" @click="setListCenterSub('objection')">异议处理</button>
              <button type="button" class="sd-mcard__btn" @click="setListCenterSub('objection')">异议办结</button>
              <button type="button" class="sd-mcard__btn" @click="setListCenterSub('objection')">历史异议</button>
              <button type="button" class="sd-mcard__btn" @click="setListCenterSub('objection')">统计分析</button>
            </div>
          </div>
        </div>

        <div class="sd-table-card">
          <div class="sd-card-title">{{ listCenterGroup }} · {{ manifestCenterSections.find(s => s.key === listCenterSub)?.label || '' }}</div>

          <template v-if="listCenterSub === 'objection'">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="异议标题" class="portal-field-md">
                <el-input v-model="objectionFilter.title" clearable />
              </el-form-item>
              <el-form-item label="异议对象" class="portal-field-md">
                <el-input v-model="objectionFilter.object" clearable />
              </el-form-item>
              <el-form-item label="提供单位" class="portal-field-md">
                <el-input v-model="objectionFilter.provider" clearable />
              </el-form-item>
              <el-form-item label="状态" class="portal-field-sm">
                <el-select v-model="objectionFilter.status" clearable placeholder="全部">
                  <el-option label="待核查" value="OPEN" />
                  <el-option label="核查中" value="PROCESSING" />
                  <el-option label="已办结" value="CLOSED" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button @click="Object.assign(objectionFilter, { title: '', object: '', provider: '', status: '' })">重置</el-button>
                <el-button type="primary" @click="listPage = 1">查询</el-button>
              </el-form-item>
            </el-form>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="登记目录" class="portal-field-default">
                <el-select v-model="objectionForm.catalogId" filterable placeholder="统一编目已发布目录">
                  <el-option v-for="c in catalogs" :key="String(c.id)" :label="String(c.title)" :value="Number(c.id)" />
                </el-select>
              </el-form-item>
              <el-form-item label="类型" class="portal-field-sm">
                <el-select v-model="objectionForm.objectionType">
                  <el-option label="质量" value="QUALITY" />
                  <el-option label="完整性" value="COMPLETENESS" />
                  <el-option label="授权" value="AUTH" />
                </el-select>
              </el-form-item>
              <el-form-item label="内容" class="portal-field-lg"><el-input v-model="objectionForm.content" /></el-form-item>
              <el-form-item class="portal-form-actions"><el-button type="primary" @click="submitObjection">登记异议</el-button></el-form-item>
            </el-form>
            <el-table :data="pagedListItems" stripe size="small">
              <el-table-column prop="title" label="异议标题" min-width="140" show-overflow-tooltip />
              <el-table-column label="异议对象" width="110">
                <template #default="{ row }">{{ row.catalogId || row.objectName || '—' }}</template>
              </el-table-column>
              <el-table-column label="类型" width="90">
                <template #default="{ row }">{{ $statusLabel(row.objectionType) }}</template>
              </el-table-column>
              <el-table-column prop="providerOrg" label="提供单位" width="120" />
              <el-table-column prop="verifyOrg" label="核查单位" width="120" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="160" />
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="showObjectionContent(row)">核查</el-button>
                  <el-button
                    v-if="row.status !== 'CLOSED'"
                    link
                    type="warning"
                    @click="reopenObjectionAudit(Number(row.id))"
                  >回流审核</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template v-else>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="编码" class="portal-field-md">
                <el-input v-model="listFilter.code" clearable />
              </el-form-item>
              <el-form-item label="名称" class="portal-field-lg">
                <el-input v-model="listFilter.title" clearable />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="listPage = 1">查询</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="pagedListItems" stripe size="small">
              <el-table-column prop="code" label="编码" width="140">
                <template #default="{ row }">{{ row.code || row.catalogCode || row.id }}</template>
              </el-table-column>
              <el-table-column prop="title" label="名称" min-width="160" />
              <el-table-column v-if="listCenterGroup === '目录清单'" prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
              <el-table-column v-if="listCenterGroup === '目录清单'" prop="catalogOrigin" label="来源" width="110">
                <template #default="{ row }">{{ row.catalogOrigin === 'INGEST' ? '指标与目录' : (row.catalogOrigin === 'GOVERNANCE' ? '数据目录管理' : (row.catalogOrigin || '-')) }}</template>
              </el-table-column>
              <el-table-column v-if="listCenterGroup === '目录清单'" prop="shareAttr" label="共享属性" width="110" show-overflow-tooltip />
              <el-table-column v-if="listCenterGroup === '供需清单'" prop="requesterOrg" label="需求单位" width="120" show-overflow-tooltip />
              <el-table-column v-if="listCenterGroup === '供需清单'" prop="providerOrg" label="提供单位" width="120" show-overflow-tooltip />
              <el-table-column label="状态" width="110">
                <template #default="{ row }"><el-tag :type="$statusTagType(row.status || row.publishStatus)" size="small">{{ $statusLabel(row.status || row.publishStatus) }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="160" />
              <el-table-column prop="description" label="说明" min-width="140" show-overflow-tooltip />
            </el-table>
          </template>

          <PortalPagination
            v-if="filteredListItems.length"
            v-model:page="listPage"
            v-model:page-size="listPageSize"
            :total="filteredListItems.length"
          />
        </div>
      </template>
      <template v-else>
      <el-radio-group :model-value="listCenterGroup" style="margin-bottom:12px" @change="setListCenterGroup">
        <el-radio-button value="目录清单">目录清单</el-radio-button>
        <el-radio-button value="供需清单">供需清单</el-radio-button>
        <el-radio-button value="异议清单">异议清单</el-radio-button>
      </el-radio-group>
      <el-radio-group :model-value="listCenterSub" style="margin-bottom:12px" @change="setListCenterSub">
        <el-radio-button v-for="s in manifestCenterSections" :key="s.key" :value="s.key">{{ s.label }}</el-radio-button>
      </el-radio-group>

      <template v-if="listCenterSub === 'objection'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="目录" class="portal-field-default">
            <el-select v-model="objectionForm.catalogId" filterable placeholder="统一编目已发布目录">
              <el-option v-for="c in catalogs" :key="String(c.id)" :label="String(c.title)" :value="Number(c.id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="objectionForm.objectionType">
              <el-option label="质量" value="QUALITY" />
              <el-option label="完整性" value="COMPLETENESS" />
              <el-option label="授权" value="AUTH" />
            </el-select>
          </el-form-item>
          <el-form-item label="内容" class="portal-field-lg"><el-input v-model="objectionForm.content" /></el-form-item>
          <el-form-item class="portal-form-actions"><el-button type="primary" @click="submitObjection">登记异议</el-button></el-form-item>
        </el-form>
        <el-table :data="listCenterItems" stripe size="small">
          <el-table-column prop="title" label="异议标题" min-width="140" />
          <el-table-column prop="catalogId" label="目录ID" width="90" />
          <el-table-column prop="demandId" label="需求ID" width="90" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ $statusLabel(row.objectionType) }}</template>
          </el-table-column>
          <el-table-column prop="content" label="内容" min-width="160" />
          <el-table-column prop="providerOrg" label="提出单位" width="120" />
          <el-table-column prop="verifyOrg" label="核查单位" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status !== 'CLOSED'"
                link
                type="warning"
                @click="reopenObjectionAudit(Number(row.id))"
              >回流审核</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-table v-else :data="listCenterItems" stripe size="small">
        <el-table-column prop="code" label="编码" width="140">
          <template #default="{ row }">{{ row.code || row.catalogCode || row.id }}</template>
        </el-table-column>
        <el-table-column prop="title" label="名称" min-width="160" />
        <el-table-column v-if="listCenterGroup === '目录清单'" prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
        <el-table-column v-if="listCenterGroup === '目录清单'" prop="catalogOrigin" label="来源" width="110">
          <template #default="{ row }">{{ row.catalogOrigin === 'INGEST' ? '指标与目录' : (row.catalogOrigin === 'GOVERNANCE' ? '数据目录管理' : (row.catalogOrigin || '-')) }}</template>
        </el-table-column>
        <el-table-column v-if="listCenterGroup === '目录清单'" prop="shareAttr" label="共享属性" width="110" show-overflow-tooltip />
        <el-table-column v-if="listCenterGroup === '供需清单'" prop="requesterOrg" label="需求单位" width="120" show-overflow-tooltip />
        <el-table-column v-if="listCenterGroup === '供需清单'" prop="providerOrg" label="提供单位" width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status || row.publishStatus)" size="small">{{ $statusLabel(row.status || row.publishStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
      </el-table>
      </template>
    </PageCard>

    <el-dialog v-model="confirmReturnDialog.visible" title="不同意提供 / 退回" width="480px">
      <el-form label-width="88px">
        <el-form-item label="退回原因" required>
          <el-input
            v-model="confirmReturnReason"
            type="textarea"
            :rows="4"
            placeholder="请填写不同意提供的原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmReturnDialog.visible = false">取消</el-button>
        <el-button type="warning" @click="submitConfirmReturn">确认退回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialog.visible" title="修改需求" width="520px">
      <el-form label-width="88px">
        <el-form-item label="标题"><el-input v-model="editDialog.demandTitle" /></el-form-item>
        <el-form-item label="申请方"><el-input v-model="editDialog.requesterOrg" /></el-form-item>
        <el-form-item label="供数单位"><el-input v-model="editDialog.assigneeOrg" /></el-form-item>
        <el-form-item label="履约路径">
          <el-select v-model="editDialog.fulfillPath" style="width:100%">
            <el-option v-for="o in FULFILL_PATH_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sd-filter-card,
.sd-table-card {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 10px;
  padding: 14px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(15, 40, 80, .04);
}
.sd-filter-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed #eef1f6;
}
.sd-panel :deep(.page-card__title),
.sd-panel :deep(.pc-title) {
  display: none;
}
.sd-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
}
.sd-analysis {
  display: grid;
  grid-template-columns: minmax(280px, 360px) 1fr;
  gap: 12px;
  align-items: start;
}
.sd-demand-card {
  appearance: none;
  width: 100%;
  text-align: left;
  border: 1px solid #e8edf5;
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
}
.sd-demand-card:hover { border-color: #91caff; }
.sd-demand-card.is-on {
  border-color: #1677ff;
  background: #f0f7ff;
  box-shadow: 0 0 0 1px #1677ff inset;
}
.sd-demand-card__title { font-size: 13px; font-weight: 600; color: #303133; }
.sd-demand-card__meta {
  display: flex; justify-content: space-between; gap: 8px;
  margin-top: 4px; font-size: 12px; color: #909399;
}
.sd-demand-card__score { margin-top: 6px; font-size: 12px; color: #1677ff; font-weight: 600; }
.sd-detail-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.sd-detail-head h3 { margin: 0; font-size: 16px; }
.sd-match-row { display: flex; gap: 20px; align-items: center; margin-top: 14px; flex-wrap: wrap; }
.sd-match-gauge { text-align: center; }
.sd-match-gauge__lab { margin-top: 6px; font-size: 12px; color: #606266; }
.sd-relation-tri { flex: 1; min-width: 200px; }
.sd-relation-tri__title { font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.sd-relation-tri__nodes { display: flex; gap: 8px; flex-wrap: wrap; }
.sd-rel-node {
  padding: 10px 14px; border-radius: 8px; font-size: 12px; font-weight: 600;
  border: 1px solid #e8edf5; background: #fafbfc;
}
.sd-rel-node.is-catalog { border-color: #91caff; background: #e8f3ff; color: #1677ff; }
.sd-rel-node.is-table { border-color: #b7eb8f; background: #f6ffed; color: #389e0d; }
.sd-rel-node.is-api { border-color: #ffd591; background: #fff7e6; color: #d46b08; }
.sd-rel-edges {
  margin: 10px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: #606266;
  max-height: 120px;
  overflow: auto;
}
.sd-rel-edges em { font-style: normal; color: #909399; }
.sd-rel-node.is-api { border-color: #ffd591; background: #fff7e6; color: #d46b08; }
.sd-detail-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.sd-drawer-actions { display: flex; flex-direction: column; gap: 8px; margin-top: 16px; }
.sd-drawer-actions .el-button { margin: 0; }
.sd-kpi-row {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 12px;
}
.sd-kpi-row--sm { margin-top: 4px; }
.sd-kpi {
  background: #fff; border: 1px solid #e8edf5; border-radius: 10px;
  padding: 14px 16px; box-shadow: 0 1px 4px rgba(15, 40, 80, .04);
}
.sd-kpi__lab { font-size: 13px; color: #606266; }
.sd-kpi__num { font-size: 28px; font-weight: 700; margin-top: 4px; line-height: 1.1; }
.sd-kpi.tone-blue .sd-kpi__num { color: #1677ff; }
.sd-kpi.tone-green .sd-kpi__num { color: #2e7d32; }
.sd-kpi.tone-amber .sd-kpi__num { color: #ef6c00; }
.sd-kpi.tone-red .sd-kpi__num { color: #cf1322; }
.sd-manifest-cards {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 12px;
}
.sd-mcard {
  background: #fff; border: 1px solid #e8edf5; border-radius: 10px;
  padding: 14px; box-shadow: 0 1px 4px rgba(15, 40, 80, .04);
}
.sd-mcard__top {
  display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 10px;
}
.sd-mcard__top span { font-size: 14px; font-weight: 600; }
.sd-mcard__top b { font-size: 26px; }
.sd-mcard.tone-blue .sd-mcard__top { color: #1677ff; }
.sd-mcard.tone-green .sd-mcard__top { color: #2e7d32; }
.sd-mcard.tone-amber .sd-mcard__top { color: #ef6c00; }
.sd-mcard__grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 6px;
}
.sd-mcard__btn {
  appearance: none; border: 0; border-radius: 4px; padding: 6px 8px;
  font-size: 12px; cursor: pointer; text-align: center;
}
.sd-mcard.tone-blue .sd-mcard__btn { background: #e8f3ff; color: #1677ff; }
.sd-mcard.tone-green .sd-mcard__btn { background: #e8f8ef; color: #2e7d32; }
.sd-mcard.tone-amber .sd-mcard__btn { background: #fff4e5; color: #ef6c00; }
.sd-mcard__btn.is-on { outline: 1px solid currentColor; font-weight: 600; }
@media (max-width: 1100px) {
  .sd-analysis { grid-template-columns: 1fr; }
  .sd-manifest-cards, .sd-kpi-row { grid-template-columns: 1fr; }
}
.analysis-panel {
  margin-top: 16px;
  padding-top: 8px;
  border-top: 1px solid var(--portal-border);
}
.confirm-h {
  margin: 16px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--portal-text);
}
.confirm-result {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--portal-border);
}
.analysis-panel h4 {
  margin: 8px 0 10px;
  font-size: 14px;
  color: var(--portal-text);
}
.relation-graph {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.relation-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.relation-node {
  min-width: 140px;
  max-width: 220px;
  padding: 10px 12px;
  border-radius: var(--portal-radius);
  border: 1px solid var(--portal-border);
  background: var(--portal-card-bg);
  box-shadow: var(--portal-shadow);
}
.relation-node__type {
  display: block;
  font-size: 11px;
  color: var(--portal-text-secondary);
  margin-bottom: 4px;
}
.relation-node__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--portal-text);
  word-break: break-all;
}
.relation-node.is-demand {
  border-color: color-mix(in srgb, var(--portal-primary) 50%, var(--portal-border));
  background: color-mix(in srgb, var(--portal-primary) 8%, white);
}
.relation-node.is-catalog {
  border-color: #9ad4c8;
}
.relation-node.is-table {
  border-color: #fac775;
}
.relation-node.is-api {
  border-color: #cecbf6;
}
.relation-edges {
  margin: 0;
  padding-left: 18px;
  color: var(--portal-text-secondary);
  font-size: 12px;
  line-height: 1.8;
}
</style>
