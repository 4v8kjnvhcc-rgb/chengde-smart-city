<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import {
  FULFILL_PATH_OPTIONS,
  MANIFEST_CENTER_SECTIONS,
  SUPPLY_MAIN_SECTIONS,
  resolveApplicationNav,
  type FulfillPath,
} from './application-nav'
import DemandApplyForm from './DemandApplyForm.vue'
import {
  normalizeAttachments,
  normalizeDataItems,
  type DemandFormModel,
} from './demand-apply-model'
import { useSupplyRole } from './supply-role'
import { useAuthStore } from '@/stores/auth'
import { statusLabel } from '@/utils/status-label'
import { formatDateTime, formatMaybeDateTime, sortByTimeDesc } from '@/utils/datetime'

const props = defineProps<{ mode?: 'front' | 'config'; embedded?: boolean }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { isPlatformAdmin, isSuperAdmin } = useSupplyRole()

/** 部门管理员按组织隔离；平台/超管看全量 */
const myOrgName = computed(() => String(auth.user?.orgName || '').trim())
const orgScoped = computed(() => !isPlatformAdmin.value && !!myOrgName.value)

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
const listCenterStats = ref<Record<string, unknown> | null>(null)
const listCenterSub = ref('catalog-published')
const listCenterGroup = ref('目录清单')
const listDetail = reactive<{
  visible: boolean
  row: Record<string, unknown> | null
  stages: { status: string; result: string; createdAt?: string }[]
  loading: boolean
}>({
  visible: false,
  row: null,
  stages: [],
  loading: false,
})

/** 清单详情字段中文标签 */
const LIST_DETAIL_LABEL_ZH: Record<string, string> = {
  id: '编号',
  code: '编码',
  title: '标题',
  catalogId: '目录编号',
  catalogCode: '目录代码',
  catalogName: '目录名称',
  demandId: '需求编号',
  demandScene: '需求场景',
  demandCatalog: '需求目录',
  demandService: '需求服务',
  demandTitle: '需求名称',
  objectionType: '异议类型',
  content: '异议内容',
  objectName: '异议对象',
  serviceName: '服务名称',
  providerOrg: '提供单位',
  verifyOrg: '异议核查单位',
  requesterOrg: '需求单位',
  resourceLevel: '资源级别',
  matchedCatalogId: '匹配目录编号',
  status: '状态',
  publishStatus: '发布状态',
  stage: '阶段',
  fulfillPath: '履约路径',
  handlerNote: '处理说明',
  description: '说明',
  shareAttr: '共享属性',
  catalogOrigin: '目录来源码',
  catalogOriginLabel: '目录来源',
  versionNo: '版本',
  govResourceId: '资源编号',
  themeName: '主题',
  baseCatalogName: '基础库',
  createdAt: '创建时间',
  updatedAt: '更新时间',
  publishedAt: '发布时间',
  createdBy: '创建人',
  reviewedAt: '审批时间',
  reviewedBy: '审批人',
  approvalId: '审批编号',
  actionType: '审批动作',
  actions: '可用操作',
}

const LIST_DETAIL_HIDDEN_KEYS = new Set(['actions'])

const OBJECTION_TYPE_ZH: Record<string, string> = {
  QUALITY: '质量',
  COMPLETENESS: '完整性',
  AUTH: '授权',
  OTHER: '其他',
}

function listDetailFieldLabel(key: string) {
  return LIST_DETAIL_LABEL_ZH[key] || key
}

function listDetailFieldEntries(row: Record<string, unknown> | null) {
  if (!row) return [] as { key: string; label: string; value: unknown }[]
  // 异议清单：提供单位展示为提出单位
  const providerLabel = isObjectionListSub() ? '异议提出单位' : LIST_DETAIL_LABEL_ZH.providerOrg
  return Object.entries(row)
    .filter(([k]) => !LIST_DETAIL_HIDDEN_KEYS.has(k))
    .map(([key, value]) => ({
      key,
      label: key === 'providerOrg' ? providerLabel : listDetailFieldLabel(key),
      value,
    }))
}

function formatListDetailValue(key: string, val: unknown) {
  if (val == null || val === '') return '—'
  if (Array.isArray(val)) return val.map((v) => formatListDetailValue(key, v)).join('、') || '—'
  if (key === 'status' || key === 'publishStatus' || key === 'stage') {
    if (isObjectionListSub()) return objectionStatusLabel(val)
    return statusLabel(val)
  }
  if (key === 'objectionType') {
    const k = String(val).toUpperCase()
    return OBJECTION_TYPE_ZH[k] || String(val)
  }
  if (key === 'demandScene' || key === 'resourceLevel' || key === 'fulfillPath' || key === 'actionType' || key === 'shareAttr') {
    return statusLabel(val) || String(val)
  }
  if (key === 'catalogOrigin') {
    const k = String(val).toUpperCase()
    if (k === 'INGEST') return '指标与目录'
    if (k === 'GOVERNANCE') return '数据目录'
  }
  return formatMaybeDateTime(key, val)
}

function resolveListCenterAuditKind() {
  if (isObjectionListSub()) return 'objection'
  if (listCenterGroup.value === '供需清单') return 'demand'
  return 'catalog'
}

function resolveListCenterAuditId(row: Record<string, unknown>, kind: string) {
  if (kind === 'catalog') {
    const gov = Number(row.govResourceId || 0)
    if (gov) return gov
    const approval = Number(row.approvalId || 0)
    if (approval) return approval
  }
  if (kind === 'demand') {
    const demandId = Number(row.demandId || row.id || 0)
    if (demandId) return demandId
  }
  return Number(row.id || 0)
}
const catalogManifestSections = MANIFEST_CENTER_SECTIONS.filter((s) => s.group === '目录清单')
const supplyManifestSections = MANIFEST_CENTER_SECTIONS.filter((s) => s.group === '供需清单')
const objectionManifestSections = MANIFEST_CENTER_SECTIONS.filter((s) => s.group === '异议清单')

function isObjectionListSub(key = listCenterSub.value) {
  return String(key).startsWith('objection')
}

function asRows(data: unknown): Record<string, unknown>[] {
  return Array.isArray(data) ? (data as Record<string, unknown>[]) : []
}

function assignDemands(data: unknown) {
  demands.value = sortByTimeDesc(asRows(data))
}

async function reloadDemands() {
  assignDemands((await api.get('/exchange/supply/demands')).data)
}
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
  assigneeOrg: '',
  analysisNote: '',
  fulfillPath: 'NEED_COLLECT' as FulfillPath,
})
const resourceSearch = reactive({
  keyword: '',
  resourceType: 'CATALOG',
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
const objectionForm = reactive({
  id: undefined as number | undefined,
  demandId: undefined as number | undefined,
  catalogId: undefined as number | undefined,
  objectionType: 'QUALITY',
  content: '',
  title: '',
})
const objectionCreateVisible = ref(false)
const objectionEditMode = ref(false)

/** 异议六态展示（不覆盖全局 APPROVED/SUBMITTED 等字典） */
const OBJECTION_STATUS_ZH: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待审核',
  APPROVED: '已审核待处理',
  REJECTED: '驳回待提交',
  PROCESSED: '已处理',
  CLOSED: '已办结',
  OPEN: '待审核',
  PROCESSING: '已审核待处理',
}
function objectionStatusLabel(s: unknown) {
  const k = String(s || '').toUpperCase()
  return OBJECTION_STATUS_ZH[k] || statusLabel(s)
}

/** 可供异议挂载的已通过需求 */
const objectionEligibleDemands = computed(() =>
  demands.value.filter((d) => ['CONFIRMED', 'COMPLETED'].includes(String(d.status))),
)

function isObjectionRaiser(row: Record<string, unknown>) {
  return !!myOrgName.value && myOrgName.value === String(row.providerOrg || '')
}
function isObjectionReceiver(row: Record<string, unknown>) {
  return !!myOrgName.value && myOrgName.value === String(row.verifyOrg || '')
}

/** 按角色+状态裁剪操作列 */
function canObjectionAction(row: Record<string, unknown>, action: string) {
  if (isSuperAdmin.value) {
    return action === 'view' || action === 'delete'
  }
  const st = String(row.status || '').toUpperCase()
  const raiser = isObjectionRaiser(row)
  const receiver = isObjectionReceiver(row)
  // 平台管理员（非超管）可代操作；部门侧严格按提出/接收方
  const platformOp = isPlatformAdmin.value && !orgScoped.value
  switch (action) {
    case 'view':
      return true
    case 'edit':
    case 'submit':
    case 'delete':
      return (raiser || platformOp) && (st === 'DRAFT' || st === 'REJECTED')
    case 'withdraw':
      return (raiser || platformOp) && st === 'SUBMITTED'
    case 'approve':
    case 'reject':
      return (receiver || platformOp) && st === 'SUBMITTED'
    case 'process':
      return (receiver || platformOp) && st === 'APPROVED'
    case 'close':
      return (raiser || platformOp) && st === 'PROCESSED'
    default:
      return false
  }
}

function openObjectionCreate() {
  if (isSuperAdmin.value) {
    ElMessage.warning('超级管理员不可提交异议')
    return
  }
  objectionEditMode.value = false
  objectionForm.id = undefined
  objectionForm.demandId = undefined
  objectionForm.catalogId = undefined
  objectionForm.objectionType = 'QUALITY'
  objectionForm.content = ''
  objectionForm.title = ''
  objectionCreateVisible.value = true
  void reloadDemands()
}

function openObjectionEdit(row: Record<string, unknown>) {
  objectionEditMode.value = true
  objectionForm.id = Number(row.id)
  objectionForm.demandId = row.demandId != null ? Number(row.demandId) : undefined
  objectionForm.catalogId = row.catalogId != null ? Number(row.catalogId) : undefined
  objectionForm.objectionType = String(row.objectionType || 'QUALITY')
  objectionForm.content = String(row.content || '')
  objectionForm.title = String(row.title || '')
  objectionCreateVisible.value = true
}

function resetObjectionForm() {
  objectionEditMode.value = false
  objectionForm.id = undefined
  objectionForm.demandId = undefined
  objectionForm.catalogId = undefined
  objectionForm.objectionType = 'QUALITY'
  objectionForm.content = ''
  objectionForm.title = ''
}

const demandFilter = reactive({ title: '', demandType: '', status: '' })
const demandPage = ref(1)
const demandPageSize = ref(10)
const createVisible = ref(false)
const applyMode = ref<'create' | 'edit' | 'view'>('create')
const applyFormModel = ref<Partial<DemandFormModel> | null>(null)
const editingDemandId = ref<number>(0)
const mountDialog = reactive({
  visible: false,
  id: 0,
  providerOrg: '',
  catalogId: undefined as number | undefined,
  loading: false,
  catalogs: [] as Record<string, unknown>[],
})
const trackDrawer = reactive<{
  visible: boolean
  row: Record<string, unknown> | null
  stages: { status: string; result: string; createdAt?: string }[]
  loading: boolean
}>({
  visible: false,
  row: null,
  stages: [],
  loading: false,
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
const supplyScopeTab = ref<'my-demand' | 'my-supply'>('my-demand')
const supplyTab = ref<'share' | 'exchange' | 'api' | 'page'>('share')

/** 督办反馈抽屉 */
const feedbackDrawer = reactive<{ visible: boolean; row: Record<string, unknown> | null }>({
  visible: false,
  row: null,
})

/** 清单中心筛选 */
const listFilter = reactive({ code: '', title: '', status: '' })
const listPage = ref(1)
const listPageSize = ref(10)
const objectionFilter = reactive({ title: '', object: '', provider: '', verify: '', status: '' })

const filteredDemands = computed(() => {
  return sortByTimeDesc(demands.value.filter((d) => {
    // 数据需求管理：部门管理员仅看本部门提出的需求
    if (orgScoped.value && String(d.requesterOrg || '') !== myOrgName.value) return false
    if (demandFilter.title && !String(d.demandTitle || '').includes(demandFilter.title.trim())) return false
    if (demandFilter.demandType && String(d.demandType) !== demandFilter.demandType) return false
    if (demandFilter.status && String(d.status) !== demandFilter.status) return false
    return true
  }))
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
    dataItems: normalizeDataItems(payload.dataItems),
    serviceDemandType: (payload.serviceDemandType as 'GOV' | 'NON_GOV') || 'GOV',
    matterIds: Array.isArray(payload.matterIds) ? (payload.matterIds as number[]) : [],
    matterNames: Array.isArray(payload.matterNames) ? (payload.matterNames as string[]) : [],
    matterCodes: Array.isArray(payload.matterCodes) ? (payload.matterCodes as string[]) : [],
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
    demandContent: String(payload.demandContent || row.demandContent || ''),
    attachments: normalizeAttachments(payload.attachments),
  }
}

function buildDemandBody(v: DemandFormModel, draft: boolean) {
  return {
    draft,
    demandTitle: v.dataName,
    requesterOrg: v.requesterOrg,
    demandType: v.demandType || 'STRUCTURED',
    templateCode: v.templateCode || undefined,
    demandContent: v.demandType === 'UNSTRUCTURED'
      ? (v.demandContent || v.demandBasis || v.usageScenario || undefined)
      : (v.demandBasis || v.usageScenario || v.demandContent || undefined),
    targetCatalogId: v.targetCatalogId,
    assigneeOrg: v.providerOrg || undefined,
    supplyMode: v.shareProvideMode || undefined,
    formPayload: {
      providerOrg: v.providerOrg,
      providerOrgId: v.providerOrgId,
      catalogTitle: v.catalogTitle,
      dataName: v.dataName,
      systemNames: v.systemNames,
      dataItems: normalizeDataItems(v.dataItems),
      serviceDemandType: v.serviceDemandType,
      matterIds: v.matterIds,
      matterNames: v.matterNames,
      matterCodes: v.matterCodes,
      matterMaterials: v.matterMaterials,
      usageScenario: v.usageScenario,
      demandBasis: v.demandBasis,
      demandContent: v.demandContent,
      shareProvideMode: v.shareProvideMode,
      updateFrequency: v.updateFrequency,
      contactName: v.contactName,
      contactPhone: v.contactPhone,
      contactEmail: v.contactEmail,
      attachments: normalizeAttachments(v.attachments),
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
    matterCodes: [],
    attachments: [],
    demandContent: '',
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

async function openTrack(row: Record<string, unknown>) {
  trackDrawer.row = row
  trackDrawer.stages = []
  trackDrawer.visible = true
  trackDrawer.loading = true
  try {
    const id = Number(row.id)
    if (!id) return
    const res = await api.get(`/exchange/supply/demands/${id}/track`)
    const data = res.data || {}
    trackDrawer.row = {
      ...row,
      demandTitle: data.demandTitle ?? row.demandTitle,
      requesterOrg: data.requesterOrg ?? row.requesterOrg,
      status: data.status ?? row.status,
      stage: data.stage ?? row.stage,
    }
    trackDrawer.stages = sortByTimeDesc(Array.isArray(data.stages) ? data.stages : [])
  } catch {
    // 接口失败时用本地字段兜底一条
    trackDrawer.stages = [{
      status: statusLabel(row.status),
      result: String(row.analysisNote || row.confirmNote || row.superviseNote || '—'),
      createdAt: formatDateTime(row.updatedAt || row.createdAt, ''),
    }]
  } finally {
    trackDrawer.loading = false
  }
}

function demandOps(status: string) {
  const s = String(status || '')
  if (s === 'DRAFT') return ['view', 'edit', 'submit', 'delete'] as const
  if (s === 'SUBMITTED') return ['withdraw', 'view', 'track'] as const
  if (s === 'WITHDRAW_PENDING' || s === 'RETURNED') return ['view', 'edit', 'submit', 'delete', 'track'] as const
  if (s === 'CATALOG_MOUNTED') return ['view', 'complete', 'track'] as const
  if (s === 'CANCELLED') return ['view', 'track'] as const
  return ['view', 'track'] as const
}

function importHint() {
  ElMessage.info('请使用新建需求录入；批量导入请在「供需配置」维护模板后手工录入')
}

async function exportDemandsCsv() {
  const rows = filteredDemands.value
  const header = ['需求名称', '需求单位', '类型', '状态', '阶段']
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
    ['SUBMITTED', 'PRE_AUDITING', 'ANALYZING', 'SUPERVISING', 'PROVIDER_RETURNED'].includes(String(d.status)),
  ),
)

function isMountSupervising(row: Record<string, unknown>) {
  return String(row.status) === 'SUPERVISING' && !!row.catalogMountDeadline
}

function canProviderConfirm(row: Record<string, unknown>) {
  const s = String(row.status || '')
  if (s === 'DISPATCHED' || s === 'CORRECTION') return true
  // 预审期督办可确认；挂载超时督办不可再次「同意确认」
  if (s === 'SUPERVISING') return !row.catalogMountDeadline
  return false
}
const filteredAnalysis = computed(() =>
  sortByTimeDesc(analysisPending.value.filter((d) => {
    if (analysisFilter.title && !String(d.demandTitle || '').includes(analysisFilter.title.trim())) return false
    if (analysisFilter.org && !String(d.requesterOrg || '').includes(analysisFilter.org.trim())) return false
    if (analysisFilter.evalStatus && String(d.evalStatus || '') !== analysisFilter.evalStatus) return false
    return true
  })),
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
  const fromCandidates = analysisCandidates.value.filter(
    (c) => String(c.resourceType || '').toUpperCase() === 'CATALOG',
  ).length
  return {
    catalog: typeOf('CATALOG') || fromCandidates || Number((analysisResult.value as any)?.catalogCount) || 0,
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
  // 履约路径默认「未在中台·需归集补数」，由管理员人工选择
  dispatchForm.fulfillPath = 'NEED_COLLECT'
  if (row.assigneeOrg) dispatchForm.assigneeOrg = String(row.assigneeOrg)
  if (row.analysisNote) dispatchForm.analysisNote = String(row.analysisNote)
}

function prefillsAnalysisOrg(row: Record<string, unknown>) {
  // 优先用当前需求的分发/提供单位，保证资源目录默认对应该部门门户目录
  if (row.assigneeOrg) {
    dispatchForm.assigneeOrg = String(row.assigneeOrg)
    return
  }
  const form = parseFormPayload(row)
  if (form.providerOrg) {
    dispatchForm.assigneeOrg = form.providerOrg
  }
}

async function selectAnalysisRow(row: Record<string, unknown>) {
  analyzingId.value = Number(row.id)
  prefillsAnalysisOrg(row)
  if (row.matchScore == null || row.matchScore === '') {
    if (!dispatchForm.assigneeOrg?.trim()) {
      restoreAnalysisFromRow(row)
      resourceHits.value = []
      ElMessage.info('请先选择分发部门，再对该组织已发布门户目录进行智能匹配')
      return
    }
    await analyzeDemand(Number(row.id))
  } else {
    restoreAnalysisFromRow(row)
  }
  // 默认展示分发部门已在部门数据共享门户发布的目录
  await searchResourceCatalog()
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
const supplyShareRows = computed(() => sortByTimeDesc(supplyTasks.value))
const supplyExchangeRows = computed(() =>
  sortByTimeDesc(
    exchangeJobs.value.length
      ? exchangeJobs.value
      : supplyTasks.value.filter((t) => ['EXCHANGE', 'COLLECT'].includes(String(t.taskType))),
  ),
)

const superviseRows = computed(() =>
  sortByTimeDesc(demands.value.filter((d) =>
    ['SUBMITTED', 'PRE_AUDITING', 'ANALYZING', 'DISPATCHED', 'RETURNED', 'SUPERVISING', 'CORRECTION', 'PROVIDER_RETURNED', 'CONFIRMED'].includes(String(d.status)),
  )),
)
const superviseKpi = computed(() => {
  const now = Date.now()
  const overdue = superviseRows.value.filter((d) => {
    if (String(d.status) === 'SUPERVISING') {
      if (d.catalogMountDeadline && !d.catalogMountedAt) {
        return new Date(String(d.catalogMountDeadline)).getTime() < now
      }
      if (d.responseDeadline) {
        return new Date(String(d.responseDeadline)).getTime() < now
      }
      return true
    }
    if (String(d.status) === 'CONFIRMED' && d.catalogMountDeadline && !d.catalogMountedAt) {
      return new Date(String(d.catalogMountDeadline)).getTime() < now
    }
    if (['DISPATCHED', 'RETURNED', 'PROVIDER_RETURNED', 'CORRECTION'].includes(String(d.status)) && d.responseDeadline) {
      return new Date(String(d.responseDeadline)).getTime() < now
    }
    if (String(d.status) === 'PROVIDER_RETURNED') return true
    return false
  }).length
  return {
    overdue,
    feedback: superviseRows.value.filter((d) => String(d.status) === 'SUPERVISING').length,
    doneWeek: demands.value.filter((d) => String(d.status) === 'COMPLETED').length,
  }
})

function openFeedback(row: Record<string, unknown>) {
  feedbackDrawer.row = row
  feedbackDrawer.visible = true
}

function matchListCenterOrg(r: Record<string, unknown>) {
  // 清单中心：部门管理员仅看本部门相关记录
  if (!orgScoped.value) return true
  const org = myOrgName.value
  const provider = String(r.providerOrg || '')
  const requester = String(r.requesterOrg || '')
  const verify = String(r.verifyOrg || '')
  return provider === org || requester === org || verify === org
}

const filteredListItems = computed(() => {
  if (listCenterSub.value === 'objection-stats') {
    return listCenterItems.value
  }
  let rows: Record<string, unknown>[]
  if (isObjectionListSub()) {
    rows = listCenterItems.value.filter((r) => {
      if (!matchListCenterOrg(r)) return false
      if (objectionFilter.title && !String(r.title || '').includes(objectionFilter.title.trim())) return false
      if (objectionFilter.object && !String(r.objectName || r.catalogId || '').includes(objectionFilter.object.trim())) return false
      if (objectionFilter.provider && !String(r.providerOrg || '').includes(objectionFilter.provider.trim())) return false
      if (objectionFilter.verify && !String(r.verifyOrg || '').includes(objectionFilter.verify.trim())) return false
      if (objectionFilter.status && String(r.status) !== objectionFilter.status) return false
      return true
    })
  } else {
    rows = listCenterItems.value.filter((r) => {
      if (!matchListCenterOrg(r)) return false
      if (listFilter.code && !String(r.code || r.catalogCode || r.id || '').includes(listFilter.code.trim())) return false
      if (listFilter.title && !String(r.title || r.catalogName || r.demandCatalog || '').includes(listFilter.title.trim())) return false
      if (listFilter.status && String(r.status || r.publishStatus || '') !== listFilter.status) return false
      return true
    })
  }
  return sortByTimeDesc(rows)
})
const pagedListItems = computed(() => {
  const start = (listPage.value - 1) * listPageSize.value
  return filteredListItems.value.slice(start, start + listPageSize.value)
})

function shortManifestLabel(label: string) {
  return label
    .replace(/清单$/g, '')
    .replace(/^目录/, '')
    .replace(/^数据/, '')
    .replace(/^已发布目录/, '已发布')
}

async function openListDetail(row: Record<string, unknown>) {
  listDetail.row = row
  listDetail.stages = []
  listDetail.visible = true
  listDetail.loading = true
  try {
    const kind = resolveListCenterAuditKind()
    const id = resolveListCenterAuditId(row, kind)
    if (!id) return
    const res = await api.get('/exchange/supply/list-center/audit-flow', {
      params: { listType: listCenterSub.value, kind, id },
    })
    const data = (res.data || {}) as { stages?: { status: string; result: string; createdAt?: string }[] }
    listDetail.stages = Array.isArray(data.stages) ? data.stages : []
  } catch {
    listDetail.stages = [{
      status: isObjectionListSub() ? objectionStatusLabel(row.status) : statusLabel(row.status || row.publishStatus),
      result: String(row.handlerNote || row.description || row.reviewedBy || '—'),
      createdAt: String(row.reviewedAt || row.updatedAt || row.createdAt || ''),
    }]
  } finally {
    listDetail.loading = false
  }
}

function exportListCenterCsv() {
  const rows = filteredListItems.value
  if (!rows.length) {
    ElMessage.warning('当前无可导出数据')
    return
  }
  const keys = Object.keys(rows[0]).filter((k) => k !== 'actions')
  const header = keys.join(',')
  const lines = rows.map((r) =>
    keys.map((k) => `"${String(r[k] ?? '').replace(/"/g, '""')}"`).join(','),
  )
  const blob = new Blob([[header, ...lines].join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `清单中心_${listCenterSub.value}_${rows.length}条.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${rows.length} 条`)
}

function showObjectionContent(row: Record<string, unknown>) {
  openListDetail(row)
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
  FILE: '文件',
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

/** 清单中心「共享属性」：支持 TABLE/API/FILE 及逗号分隔组合，显示中文字典值 */
function catalogShareAttrLabel(v: unknown) {
  if (v == null || v === '' || v === '—') return '—'
  const raw = String(v).trim()
  if (raw.includes(',') || raw.includes('，')) {
    return raw
      .split(/[,，]/)
      .map((s) => s.trim())
      .filter(Boolean)
      .map((s) => catalogShareAttrLabel(s))
      .join('、')
  }
  const key = raw.toUpperCase()
  const fromShare = SHARE_ATTR_OPTIONS.find((o) => o.value === key)?.label
  if (fromShare) return fromShare
  if (RESOURCE_TYPE_LABEL[key]) return RESOURCE_TYPE_LABEL[key]
  return statusLabel(key)
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
    listCenterSub.value = 'objection-apply'
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
      resourceType: 'CATALOG',
      providerOrg: dispatchForm.assigneeOrg?.trim() || undefined,
    },
  })
  resourceHits.value = res.data.items || []
}

function resetResourceSearch() {
  resourceSearch.keyword = ''
  void searchResourceCatalog()
}

function resetListFilter() {
  listFilter.code = ''
  listFilter.title = ''
  listFilter.status = ''
  listPage.value = 1
}

function resetObjectionFilter() {
  objectionFilter.title = ''
  objectionFilter.object = ''
  objectionFilter.provider = ''
  objectionFilter.verify = ''
  objectionFilter.status = ''
  listPage.value = 1
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
      assignDemands(dm.data)
    } else if (section.value === 'analysis' || section.value === 'confirm' || section.value === 'supervise') {
      await reloadDemands()
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
      assignDemands(dm.data)
      supplyTasks.value = sortByTimeDesc(asRows(tasks.data))
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
      await Promise.all([loadListCenter(), reloadDemands()])
      listPage.value = 1
    } else if (section.value === 'catalog') {
      catalogs.value = sortByTimeDesc(asRows(
        (await api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } })).data,
      ))
    } else if (section.value === 'objection') {
      const [cat, obj] = await Promise.all([
        api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } }),
        api.get('/exchange/supply/objections'),
      ])
      catalogs.value = sortByTimeDesc(asRows(cat.data))
      objections.value = sortByTimeDesc(asRows(obj.data))
    } else if (section.value === 'manifest') {
      manifests.value = sortByTimeDesc(asRows((await api.get('/exchange/supply/manifests')).data))
    }
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function loadListCenter() {
  const res = await api.get('/exchange/supply/list-center', { params: { listType: listCenterSub.value } })
  listCenterItems.value = sortByTimeDesc(asRows(res.data.items))
  listCenterStats.value = (res.data.stats as Record<string, unknown>) || null
  if (isObjectionListSub()) {
    objections.value = listCenterItems.value
    catalogs.value = sortByTimeDesc(asRows(
      (await api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } })).data,
    ))
  }
}

async function submitDemand() {
  if (!demandForm.demandTitle) return ElMessage.warning('请填写需求名称')
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
  await reloadDemands()
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
  await reloadDemands()
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
  await reloadDemands()
  demandPage.value = 1
}

async function submitExistingDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/submit`)
  ElMessage.success('已提交，待数据主管部门审核')
  await reloadDemands()
}

async function deleteDemandRow(id: number) {
  await api.post(`/exchange/supply/demands/${id}/delete`)
  ElMessage.success('已删除')
  await reloadDemands()
}

async function withdrawDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/withdraw`)
  ElMessage.success('已撤销，状态为撤销待提交')
  await reloadDemands()
}

async function analyzeDemand(id: number) {
  const org = dispatchForm.assigneeOrg?.trim()
  if (!org) {
    return ElMessage.warning('请先选择分发部门（组织机构），再分析该组织已发布到门户的目录')
  }
  analyzingId.value = id
  analysisResult.value = (
    await api.post(`/exchange/supply/demands/${id}/analyze`, {
      providerOrg: org,
      assigneeOrg: org,
    })
  ).data
  analysisCandidates.value = (analysisResult.value?.candidates as Record<string, unknown>[]) || []
  relationGraph.value = (analysisResult.value?.relationGraph as typeof relationGraph.value) || null
  dispatchForm.fulfillPath = 'NEED_COLLECT'
  if (analysisResult.value?.evalStatus) {
    quickSet.evalStatus = String(analysisResult.value.evalStatus)
  }
  if (analysisResult.value?.shareAttr) {
    quickSet.shareAttr = String(analysisResult.value.shareAttr)
  }
  await reloadDemands()
  await searchResourceCatalog()
  ElMessage.success('门户目录智能匹配完成')
}

async function dispatchDemand(id: number) {
  if (!dispatchForm.assigneeOrg?.trim()) {
    return ElMessage.warning('请选择分发部门')
  }
  try {
    await api.post(`/exchange/supply/demands/${id}/dispatch`, {
      ...dispatchForm,
      analysisNote: dispatchForm.analysisNote || analysisResult.value?.analysisNote || '已完成需求分析并分发',
    })
    ElMessage.success('已分发到对应部门')
    analyzingId.value = undefined
    await reloadDemands()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '分发失败')
  }
}

async function returnToPortalApply(id: number) {
  await api.post(`/exchange/supply/demands/${id}/return-portal`, {
    analysisNote:
      returnNote.value.trim()
      || analysisResult.value?.analysisNote
      || '管理员判定门户目录已可满足，请到部门数据共享门户直接申请',
  })
  ElMessage.success('已退回需求部门，请其前往门户申请')
  analyzingId.value = undefined
  await reloadDemands()
}

async function adminAgreeProviderReturn(id: number) {
  await ElMessageBox.confirm('同意提供方退回后，需求将退回需求部门修改/重新提交。确认？', '同意退回', {
    type: 'warning',
  })
  await api.post(`/exchange/supply/demands/${id}/admin-agree-return`, {
    analysisNote: returnNote.value.trim() || undefined,
  })
  ElMessage.success('已同意退回需求部门')
  analyzingId.value = undefined
  await reloadDemands()
}

async function adminRefuseProviderReturn(id: number) {
  await ElMessageBox.confirm('拒绝退回后，需求将打回提供部门重新确认。确认？', '拒绝退回', {
    type: 'warning',
  })
  await api.post(`/exchange/supply/demands/${id}/admin-refuse-return`, {
    analysisNote: returnNote.value.trim() || '管理员拒绝退回，请提供部门重新确认',
  })
  ElMessage.success('已打回提供部门重新确认')
  analyzingId.value = undefined
  await reloadDemands()
}

async function openMountDialog(id: number, row?: Record<string, unknown>) {
  const hit = row || demands.value.find((d) => Number(d.id) === id)
  const providerOrg = String(hit?.assigneeOrg || parseFormPayload(hit || {}).providerOrg || '').trim()
  mountDialog.id = id
  mountDialog.providerOrg = providerOrg
  mountDialog.catalogId = hit?.matchedCatalogId != null ? Number(hit.matchedCatalogId) : undefined
  mountDialog.visible = true
  mountDialog.loading = true
  mountDialog.catalogs = []
  try {
    if (!providerOrg) {
      ElMessage.warning('缺少供数单位，无法加载门户目录')
      return
    }
    const res = await api.get('/exchange/portal/catalog', {
      params: { providerOrg },
    })
    mountDialog.catalogs = asRows(res.data)
  } catch {
    mountDialog.catalogs = []
    ElMessage.error('加载门户目录失败')
  } finally {
    mountDialog.loading = false
  }
}

async function confirmMarkCatalogMounted() {
  if (!mountDialog.catalogId) {
    return ElMessage.warning('请选择已挂载的目录名称')
  }
  const cat = mountDialog.catalogs.find((c) => Number(c.id) === Number(mountDialog.catalogId))
  const title = String(cat?.title || '')
  await api.post(`/exchange/supply/demands/${mountDialog.id}/mark-mounted`, {
    matchedCatalogId: mountDialog.catalogId,
    catalogTitle: title || undefined,
    confirmNote: title
      ? `目录已挂载至部门数据共享门户：${title}`
      : '目录已挂载至部门数据共享门户',
  })
  mountDialog.visible = false
  ElMessage.success('状态已更新为「已挂载」，待数据需求部门办结')
  await reloadDemands()
}

/** @deprecated 保留兼容：改为弹窗选目录 */
async function markCatalogMounted(id: number, row?: Record<string, unknown>) {
  await openMountDialog(id, row)
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
  await reloadDemands()
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
  await reloadDemands()
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
  ElMessage.success(`已设置：评估=${evalLabel(res.data.evalStatus)}`)
  await reloadDemands()
}

async function applyCandidate(row: Record<string, unknown>) {
  const targetId = analyzingId.value
  if (!targetId) return ElMessage.warning('请先对需求执行智能匹配')
  const body: Record<string, unknown> = {
    evalStatus: row.suggestedEvalStatus || 'MATCHED',
    shareAttr: row.suggestedShareAttr || quickSet.shareAttr || 'CONDITIONAL',
    resourceType: row.resourceType,
    resourceId: row.resourceId,
    matchScore: row.score,
    fulfillPath: dispatchForm.fulfillPath || 'NEED_COLLECT',
  }
  if (row.resourceType === 'CATALOG') {
    body.matchedCatalogId = row.resourceId
  }
  const res = await api.post(`/exchange/supply/demands/${targetId}/analysis-settings`, body)
  quickSet.evalStatus = String(res.data.evalStatus)
  if (res.data.shareAttr) quickSet.shareAttr = String(res.data.shareAttr)
  ElMessage.success(`已一键绑定「${row.title}」`)
  await reloadDemands()
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
  ElMessage.success(`已同意提供：数据责任 #${res.data.dutyId}，生成 ${taskCount} 项共享任务；请在 10 个工作日内挂载目录至门户`)
  selectedDemandId.value = id
  confirmDrawer.visible = false
  await reloadDemands()
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
  ElMessage.success('已提交平台管理员裁决（提供方不同意提供）')
  await reloadDemands()
}

async function rejectDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/reject`, {
    confirmNote: confirmNote.value || '不符合共享范围',
  })
  ElMessage.success('已驳回')
  await reloadDemands()
}

async function submitConfirmFeedback(id: number) {
  if (!confirmFeedback.value) return ElMessage.warning('请填写督查反馈')
  await api.post(`/exchange/supply/demands/${id}/confirm-feedback`, {
    confirmFeedback: confirmFeedback.value,
  })
  ElMessage.success('督查反馈已提交')
  await reloadDemands()
}

async function completeDemand(id: number) {
  await ElMessageBox.confirm(
    '确认办结该需求？办结后流程结束，可在「数据供给查看」中查看共享方式。',
    '办结',
    { type: 'info' },
  )
  await api.post(`/exchange/supply/demands/${id}/complete`, {
    confirmNote: confirmNote.value || '数据需求部门确认办结',
    confirmFeedback: confirmFeedback.value || undefined,
  })
  confirmDrawer.visible = false
  createVisible.value = false
  ElMessage.success('需求已办结，流程结束')
  await reloadDemands()
  selectedDemandId.value = id
}

async function cancelDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/cancel`, {
    confirmNote: confirmNote.value || '需求已撤销',
  })
  confirmDrawer.visible = false
  ElMessage.success('需求已撤销')
  await reloadDemands()
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
  await reloadDemands()
}

function matchConfirmOrg(d: Record<string, unknown>) {
  // 数据需求确认：部门管理员仅看需本部门确认的需求
  if (!orgScoped.value) return true
  return String(d.assigneeOrg || '') === myOrgName.value
}

/** 已分发至供数部门、待确认（含督办/补正；挂载超时督办归入已确认台账处理） */
const confirmPending = computed(() =>
  sortByTimeDesc(demands.value.filter((d) => {
    if (!matchConfirmOrg(d)) return false
    const s = String(d.status)
    if (s === 'DISPATCHED' || s === 'CORRECTION') return true
    if (s === 'SUPERVISING' && !d.catalogMountDeadline) return true
    return false
  })),
)
/** 已确认 / 挂载督办 / 办结 / 退回 / 撤销等台账（提供方退回待裁决在「数据分析」由管理员处理） */
const confirmManaged = computed(() =>
  sortByTimeDesc(demands.value.filter((d) => {
    if (!matchConfirmOrg(d)) return false
    return ['CONFIRMED', 'CATALOG_MOUNTED', 'COMPLETED', 'CANCELLED', 'REJECTED', 'RETURNED'].includes(String(d.status))
      || (String(d.status) === 'SUPERVISING' && !!d.catalogMountDeadline)
  })),
)
/** 数据供给查看：已办结；部门侧按「我的需求 / 我的供给」隔离 */
const supplyViewDemands = computed(() =>
  sortByTimeDesc(demands.value.filter((d) => {
    if (String(d.status) !== 'COMPLETED') return false
    if (!myOrgName.value) return true
    if (isPlatformAdmin.value && !orgScoped.value) return true
    if (supplyScopeTab.value === 'my-demand') {
      return String(d.requesterOrg || '') === myOrgName.value
    }
    return String(d.assigneeOrg || '') === myOrgName.value
  })),
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
  sortByTimeDesc(confirmRows.value.filter((d) => {
    if (confirmFilter.title && !String(d.demandTitle || '').includes(confirmFilter.title.trim())) return false
    if (confirmFilter.status && String(d.status) !== confirmFilter.status) return false
    return true
  })),
)
const pagedConfirm = computed(() => {
  const start = (confirmPage.value - 1) * confirmPageSize.value
  return filteredConfirm.value.slice(start, start + confirmPageSize.value)
})

async function loadSupplyView(id: number) {
  selectedDemandId.value = id
  supplyView.value = (await api.get(`/exchange/supply/supply-view/${id}`)).data
  supplyTasks.value = sortByTimeDesc(asRows(
    (supplyView.value?.tasks as Record<string, unknown>[])
    || (await api.get('/exchange/supply/supply-tasks', { params: { demandId: id } })).data,
  ))
  duties.value = sortByTimeDesc(asRows(
    (supplyView.value?.duties as Record<string, unknown>[])
    || (await api.get('/exchange/supply/duties', { params: { demandId: id } })).data,
  ))
  exchangeJobs.value = sortByTimeDesc(asRows(supplyView.value?.exchangeJobs as Record<string, unknown>[]))
  apiEndpoints.value = asRows(supplyView.value?.apiEndpoints as Record<string, unknown>[])
  sharePages.value = asRows(supplyView.value?.sharePages as Record<string, unknown>[])
}

async function onSupplyDemandPick(v: number | undefined) {
  if (v) await loadSupplyView(v)
}

async function onSupplyScopeTabChange() {
  selectedDemandId.value = undefined
  supplyView.value = null
  const first = supplyViewDemands.value[0]
  if (first) {
    await loadSupplyView(Number(first.id))
  }
}

async function submitObjection(draft = false) {
  if (objectionEditMode.value && objectionForm.id) {
    if (!objectionForm.content) {
      return ElMessage.warning('请填写异议内容')
    }
    await api.put(`/exchange/supply/objections/${objectionForm.id}`, {
      objectionType: objectionForm.objectionType,
      content: objectionForm.content,
      title: objectionForm.title || undefined,
    })
    if (!draft) {
      await api.post(`/exchange/supply/objections/${objectionForm.id}/process`, { action: 'SUBMIT' })
    }
    objectionCreateVisible.value = false
    ElMessage.success(draft ? '异议已保存' : '异议已提交')
    listCenterSub.value = 'objection-apply'
    listCenterGroup.value = '异议清单'
    await loadListCenter()
    return
  }
  if (!objectionForm.demandId || !objectionForm.content) {
    return ElMessage.warning('请选择已通过的需求并填写异议内容')
  }
  const demand = demands.value.find((d) => Number(d.id) === Number(objectionForm.demandId))
  const catalogId = objectionForm.catalogId
    || Number(demand?.matchedCatalogId || demand?.targetCatalogId || 0)
    || undefined
  await api.post('/exchange/supply/objections', {
    demandId: objectionForm.demandId,
    catalogId,
    objectionType: objectionForm.objectionType,
    content: objectionForm.content,
    title: objectionForm.title || undefined,
    draft,
  })
  objectionCreateVisible.value = false
  ElMessage.success(draft ? '异议已暂存草稿' : '异议已提交')
  listCenterSub.value = 'objection-apply'
  listCenterGroup.value = '异议清单'
  await loadListCenter()
}

async function objectionAction(id: number, action: string, needReason = false) {
  let handlerNote = ''
  if (needReason) {
    const { value } = await ElMessageBox.prompt(
      action === 'REJECT' ? '请填写驳回理由' : '请填写处理说明（可选）',
      '异议操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: action === 'REJECT' ? /\S+/ : undefined,
        inputErrorMessage: '驳回理由不能为空',
      },
    )
    handlerNote = String(value || '')
  }
  await api.post(`/exchange/supply/objections/${id}/process`, { action, handlerNote })
  const tip: Record<string, string> = {
    SUBMIT: '已提交，待审核',
    WITHDRAW: '已撤销为草稿',
    APPROVE: '已审核通过，待处理',
    REJECT: '已驳回，待重新提交',
    PROCESS: '已处理',
    CLOSE: '已办结',
  }
  ElMessage.success(tip[action] || '操作成功')
  await loadListCenter()
}

async function auditObjection(id: number) {
  try {
    await ElMessageBox.confirm('请选择审核结果', '异议审核', {
      distinguishCancelAndClose: true,
      confirmButtonText: '通过',
      cancelButtonText: '驳回',
      type: 'warning',
    })
    await objectionAction(id, 'APPROVE')
  } catch (err) {
    if (err === 'cancel') {
      try {
        await objectionAction(id, 'REJECT', true)
      } catch {
        /* 取消填写驳回理由 */
      }
    }
  }
}

async function deleteObjection(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该异议？删除后不可恢复。', '删除异议', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  await api.delete(`/exchange/supply/objections/${id}`)
  ElMessage.success('已删除')
  await loadListCenter()
}

async function closeObjectionFromList(id: number) {
  await objectionAction(id, 'CLOSE')
}

async function startObjectionProcess(id: number) {
  await objectionAction(id, 'PROCESS')
}

watch(
  () => dispatchForm.assigneeOrg,
  (org, prev) => {
    if (section.value !== 'analysis') return
    if (String(org || '').trim() === String(prev || '').trim()) return
    if (!analyzingId.value) return
    void searchResourceCatalog()
  },
)

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
            <el-form-item label="需求名称" class="portal-field-lg">
              <el-input v-model="demandFilter.title" placeholder="请输入需求名称" clearable />
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
                <el-option label="已挂载" value="CATALOG_MOUNTED" />
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
            <el-table-column prop="demandTitle" label="需求名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="requesterOrg" label="需求单位" width="140" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ $statusLabel(row.demandType) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <template v-for="op in demandOps(String(row.status))" :key="op">
                  <el-button v-if="op === 'view'" link type="primary" @click="openViewDemand(row)">查看</el-button>
                  <el-button v-else-if="op === 'edit'" link type="primary" @click="openEditDemandForm(row)">修改</el-button>
                  <el-button v-else-if="op === 'submit'" link type="primary" @click="submitExistingDemand(Number(row.id))">提交</el-button>
                  <el-button v-else-if="op === 'delete'" link type="danger" @click="deleteDemandRow(Number(row.id))">删除</el-button>
                  <el-button v-else-if="op === 'withdraw'" link type="danger" @click="withdrawDemand(Number(row.id))">撤销</el-button>
                  <el-button v-else-if="op === 'complete'" link type="success" @click="completeDemand(Number(row.id))">办结</el-button>
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

        <el-drawer v-model="trackDrawer.visible" title="需求跟踪" size="520px">
          <div v-loading="trackDrawer.loading">
            <template v-if="trackDrawer.row">
              <el-descriptions :column="1" border size="small" class="track-summary">
                <el-descriptions-item label="需求名称">{{ trackDrawer.row.demandTitle || '—' }}</el-descriptions-item>
                <el-descriptions-item label="申请单位">{{ trackDrawer.row.requesterOrg || '—' }}</el-descriptions-item>
                <el-descriptions-item label="目前状态">{{ $statusLabel(trackDrawer.row.status) }}</el-descriptions-item>
              </el-descriptions>
              <div class="track-stage-title">阶段流水</div>
              <el-table :data="trackDrawer.stages" stripe size="small" empty-text="暂无阶段记录">
                <el-table-column prop="status" label="状态" width="140" show-overflow-tooltip />
                <el-table-column prop="result" label="结果" min-width="160" show-overflow-tooltip />
                <el-table-column label="创建时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                </el-table-column>
              </el-table>
            </template>
          </div>
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
        <el-form-item label="需求名称">
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
                <el-input v-model="analysisFilter.title" placeholder="请输入需求名称" clearable />
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
                <span>{{ formatDateTime(row.updatedAt || row.createdAt) }}</span>
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
                  {{ formatDateTime(selectedAnalysis.createdAt) }}
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
                  <div class="sd-relation-tri__title">关联关系（门户目录）</div>
                  <div class="sd-relation-tri__nodes">
                    <div class="sd-rel-node is-catalog">匹配目录 {{ relationCounts.catalog }} 个</div>
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
                      placeholder="目录名称/描述"
                      clearable
                      @keyup.enter="searchResourceCatalog"
                    />
                  </el-form-item>
                  <el-form-item class="portal-form-actions">
                    <el-button type="primary" @click="searchResourceCatalog">查询</el-button>
                    <el-button @click="resetResourceSearch">重置</el-button>
                  </el-form-item>
                </el-form>
                <el-table :data="resourceHits" stripe size="small" max-height="200">
                  <el-table-column prop="title" label="目录名称" min-width="140" show-overflow-tooltip />
                  <el-table-column prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
                  <el-table-column prop="score" label="匹配度" width="80" />
                  <el-table-column label="操作" width="200">
                    <template #default="{ row }">
                      <el-button link type="primary" @click="bindResourceToDemand(row)">选用</el-button>
                      <el-button link type="success" @click="goPortalApply(row)">跳转门户申请</el-button>
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
              </el-form>

              <div class="sd-detail-actions">
                <template v-if="String(selectedAnalysis.status) === 'PROVIDER_RETURNED'">
                  <el-alert
                    type="warning"
                    :closable="false"
                    show-icon
                    style="margin-bottom:10px"
                    :title="`提供方不同意：${selectedAnalysis.confirmNote || '未填写原因'}。请裁决：同意退回需求部门，或拒绝并打回提供部门再确认。`"
                  />
                  <el-button type="primary" @click="adminAgreeProviderReturn(Number(selectedAnalysis.id))">同意退回需求部门</el-button>
                  <el-button type="danger" @click="adminRefuseProviderReturn(Number(selectedAnalysis.id))">拒绝退回（打回再确认）</el-button>
                  <el-button type="warning" @click="superviseDemand(Number(selectedAnalysis.id))">督查督办</el-button>
                </template>
                <template v-else>
                  <el-button type="primary" @click="dispatchDemand(Number(selectedAnalysis.id))">分发到部门</el-button>
                  <el-button type="success" @click="returnToPortalApply(Number(selectedAnalysis.id))">退回门户申请</el-button>
                  <el-button @click="returnDemand(Number(selectedAnalysis.id))">退回提交部门</el-button>
                  <el-button type="warning" @click="superviseDemand(Number(selectedAnalysis.id))">督查督办</el-button>
                  <el-button type="success" plain @click="applyQuickSettings(Number(selectedAnalysis.id))">一键设置信息项</el-button>
                  <el-button link type="primary" @click="analyzeDemand(Number(selectedAnalysis.id))">重新智能匹配</el-button>
                </template>
              </div>

              <div v-if="analysisCandidates.length" class="analysis-panel">
                <h4>门户目录匹配候选（仅所选组织已发布目录）</h4>
                <el-table :data="analysisCandidates" stripe size="small">
                  <el-table-column prop="title" label="目录" min-width="140" />
                  <el-table-column prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
                  <el-table-column prop="score" label="匹配度%" width="90" />
                  <el-table-column prop="subtitle" label="说明" min-width="120" show-overflow-tooltip />
                  <el-table-column label="操作" width="180">
                    <template #default="{ row }">
                      <el-button link type="primary" @click="applyCandidate(row)">一键采用</el-button>
                      <el-button link type="success" @click="goPortalApply(row)">门户申请</el-button>
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
        title="预审：智能匹配仅供参考。由平台管理员人工判定：门户已可满足则「退回门户申请」；需数源部门供数则「分发到部门」；材料不全则「退回提交部门」。提供方退回后由管理员裁决。"
      />

      <PageCard title="资源目录快速查询" style="margin-bottom:12px">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="关键词" class="portal-field-lg">
            <el-input v-model="resourceSearch.keyword" placeholder="目录名称/描述" clearable @keyup.enter="searchResourceCatalog" />
          </el-form-item>
          <el-form-item label="提供方" class="portal-field-md">
            <el-input v-model="dispatchForm.assigneeOrg" placeholder="组织机构（与分发部门一致）" clearable />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="searchResourceCatalog">查询</el-button>
            <el-button @click="resetResourceSearch">重置</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="resourceHits" stripe size="small" max-height="220">
          <el-table-column prop="resourceCode" label="编码" width="120" />
          <el-table-column prop="title" label="目录名称" min-width="140" />
          <el-table-column prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
          <el-table-column prop="score" label="匹配度" width="80" />
          <el-table-column prop="subtitle" label="说明" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button link type="primary" @click="bindResourceToDemand(row)">选用</el-button>
              <el-button link type="success" @click="goPortalApply(row)">跳转门户申请</el-button>
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
        <h4>门户目录匹配候选</h4>
        <el-table :data="analysisCandidates" stripe size="small" style="margin-bottom:12px">
          <el-table-column prop="title" label="目录" min-width="140" />
          <el-table-column prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
          <el-table-column prop="score" label="匹配度%" width="90" />
          <el-table-column label="建议评估" width="100">
            <template #default="{ row }">{{ evalLabel(row.suggestedEvalStatus) }}</template>
          </el-table-column>
          <el-table-column label="建议共享" width="110">
            <template #default="{ row }">{{ shareLabel(row.suggestedShareAttr) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="applyCandidate(row)">一键采用</el-button>
              <el-button link type="success" @click="goPortalApply(row)">门户申请</el-button>
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
                <el-tag :type="row.status === 'PROVIDER_RETURNED' ? 'danger' : (row.status === 'RETURNED' ? 'primary' : 'warning')" size="small">
                  {{
                    row.status === 'PROVIDER_RETURNED'
                      ? '提供方退回'
                      : row.status === 'RETURNED'
                        ? '退回督办'
                        : (row.catalogMountDeadline && !row.catalogMountedAt ? '挂载超时/督办' : '超时督办')
                  }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
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
        :data="superviseRows"
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
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
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
            <el-form-item label="需求名称" class="portal-field-lg">
              <el-input v-model="confirmFilter.title" placeholder="请输入需求名称" clearable />
            </el-form-item>
            <el-form-item label="状态" class="portal-field-md">
              <el-select v-model="confirmFilter.status" clearable placeholder="全部状态">
                <el-option label="待确认" value="DISPATCHED" />
                <el-option label="督办中" value="SUPERVISING" />
                <el-option label="待补正" value="CORRECTION" />
                <el-option label="已确认" value="CONFIRMED" />
                <el-option label="已挂载" value="CATALOG_MOUNTED" />
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
            <el-table-column prop="demandTitle" label="需求名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="requesterOrg" label="需求单位" width="120" />
            <el-table-column prop="assigneeOrg" label="供数单位" width="120" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="360" fixed="right">
              <template #default="{ row }">
                <template v-if="canProviderConfirm(row)">
                  <el-button link type="success" @click.stop="confirmDemand(Number(row.id), row)">同意并生成共享任务</el-button>
                  <el-button link type="warning" @click.stop="confirmReturnDemand(Number(row.id))">不同意退回</el-button>
                  <el-button link @click.stop="openConfirmDrawer(row)">督查反馈</el-button>
                </template>
                <template v-else-if="row.status === 'CONFIRMED' || isMountSupervising(row)">
                  <el-button
                    v-if="!row.catalogMountedAt && String(row.status) !== 'CATALOG_MOUNTED'"
                    link
                    type="primary"
                    @click.stop="markCatalogMounted(Number(row.id), row)"
                  >已挂载</el-button>
                  <el-button link @click.stop="openEdit(row)">修改</el-button>
                  <el-button link type="info" @click.stop="cancelDemand(Number(row.id))">撤销</el-button>
                </template>
                <template v-else-if="row.status === 'CATALOG_MOUNTED'">
                  <el-button link type="success" @click.stop="openConfirmDrawer(row)">已挂载（待需求部门办结）</el-button>
                  <el-button link @click.stop="openConfirmDrawer(row)">详情</el-button>
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
              <el-descriptions-item v-if="confirmDrawer.row.catalogMountDeadline" label="挂载截止">{{ formatDateTime(confirmDrawer.row.catalogMountDeadline) }}</el-descriptions-item>
              <el-descriptions-item v-if="confirmDrawer.row.catalogMountedAt" label="已挂载时间">{{ formatDateTime(confirmDrawer.row.catalogMountedAt) }}</el-descriptions-item>
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
                v-if="canProviderConfirm(confirmDrawer.row)"
                type="success"
                @click="confirmDemand(Number(confirmDrawer.row.id), confirmDrawer.row)"
              >同意并生成共享任务</el-button>
              <el-button
                v-if="canProviderConfirm(confirmDrawer.row)"
                type="warning"
                @click="confirmReturnDemand(Number(confirmDrawer.row.id))"
              >不同意退回</el-button>
              <el-button type="warning" plain @click="submitConfirmFeedback(Number(confirmDrawer.row.id))">督查反馈</el-button>
              <el-button
                v-if="(confirmDrawer.row.status === 'CONFIRMED' || isMountSupervising(confirmDrawer.row)) && !confirmDrawer.row.catalogMountedAt"
                type="primary"
                @click="markCatalogMounted(Number(confirmDrawer.row.id), confirmDrawer.row)"
              >已挂载</el-button>
              <el-button
                v-if="confirmDrawer.row.status === 'CONFIRMED' || canProviderConfirm(confirmDrawer.row)"
                @click="openEdit(confirmDrawer.row)"
              >修改</el-button>
              <el-button
                v-if="confirmDrawer.row.status === 'CONFIRMED'"
                type="info"
                @click="cancelDemand(Number(confirmDrawer.row.id))"
              >撤销</el-button>
              <el-button
                v-if="['CATALOG_MOUNTED','COMPLETED'].includes(String(confirmDrawer.row.status))"
                type="primary"
                :disabled="confirmDrawer.row.status !== 'COMPLETED'"
                @click="loadSupplyView(Number(confirmDrawer.row.id)); setSection('supply'); confirmDrawer.visible = false"
              >{{ confirmDrawer.row.status === 'COMPLETED' ? '供给查看' : '待需求部门办结' }}</el-button>
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
        title="数据提供部门确认：同意后须在时限内将目录挂载至门户（超时自动督办）；不同意须填原因退回管理员裁决；点击「已挂载」选择门户目录后，由数据需求部门办结。"
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
        <el-table-column label="履约路径" width="150">
          <template #default="{ row }">{{ fulfillLabel(row.fulfillPath) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="400" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="confirmDemand(Number(row.id), row)">同意并生成共享任务</el-button>
            <el-button link type="warning" @click="confirmReturnDemand(Number(row.id))">不同意退回</el-button>
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
        <el-table-column label="挂载截止" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ formatDateTime(row.catalogMountDeadline) }}</template>
        </el-table-column>
        <el-table-column label="挂载" width="90">
          <template #default="{ row }">{{ row.catalogMountedAt ? '已挂载' : '未挂载' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="(row.status === 'CONFIRMED' || isMountSupervising(row)) && !row.catalogMountedAt"
              link
              type="primary"
              @click="markCatalogMounted(Number(row.id), row)"
            >已挂载</el-button>
            <el-button v-if="!['COMPLETED','CANCELLED','WITHDRAWN','CATALOG_MOUNTED'].includes(String(row.status))" link @click="openEdit(row)">修改</el-button>
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
        <el-tabs
          v-model="supplyScopeTab"
          class="supply-scope-tabs"
          @tab-change="onSupplyScopeTabChange"
        >
          <el-tab-pane label="我的需求" name="my-demand" />
          <el-tab-pane label="我的供给" name="my-supply" />
        </el-tabs>
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
            :description="supplyScopeTab === 'my-demand'
              ? '暂无本部门作为需求方已办结的需求。'
              : '暂无本部门作为提供方已办结的供给。'"
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
              <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
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
        title="针对已办结的数据需求，查看共享方式、交换作业、接口与通用共享页面。需求方看「我的需求」，提供方看「我的供给」。"
      />
      <el-tabs v-model="supplyScopeTab" class="supply-scope-tabs" @tab-change="onSupplyScopeTabChange">
        <el-tab-pane label="我的需求" name="my-demand" />
        <el-tab-pane label="我的供给" name="my-supply" />
      </el-tabs>
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
      <div v-if="mode === 'front'" class="sd-manifest-cards">
        <div class="sd-mcard tone-blue">
          <div class="sd-mcard__top"><span>目录清单</span></div>
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
          <div class="sd-mcard__top"><span>供需清单</span></div>
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
          <div class="sd-mcard__top"><span>异议清单</span></div>
          <div class="sd-mcard__grid">
            <button
              v-for="s in objectionManifestSections"
              :key="s.key"
              type="button"
              class="sd-mcard__btn"
              :class="{ 'is-on': listCenterSub === s.key }"
              @click="setListCenterSub(s.key)"
            >{{ shortManifestLabel(s.label) }}</button>
          </div>
        </div>
      </div>
      <template v-else>
        <el-radio-group :model-value="listCenterGroup" style="margin-bottom:12px" @change="setListCenterGroup">
          <el-radio-button value="目录清单">目录清单</el-radio-button>
          <el-radio-button value="供需清单">供需清单</el-radio-button>
          <el-radio-button value="异议清单">异议清单</el-radio-button>
        </el-radio-group>
        <el-radio-group :model-value="listCenterSub" style="margin-bottom:12px" @change="setListCenterSub">
          <el-radio-button v-for="s in manifestCenterSections" :key="s.key" :value="s.key">{{ s.label }}</el-radio-button>
        </el-radio-group>
      </template>

      <div class="sd-table-card">
        <div class="sd-card-title" style="display:flex;justify-content:space-between;align-items:center;gap:12px">
          <span>{{ listCenterGroup }} · {{ manifestCenterSections.find(s => s.key === listCenterSub)?.label || '' }}</span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-button
              v-if="listCenterSub === 'objection-apply' && !isSuperAdmin"
              type="primary"
              size="small"
              @click="openObjectionCreate"
            >新增</el-button>
            <el-button size="small" @click="exportListCenterCsv">导出 CSV</el-button>
          </div>
        </div>

        <!-- 异议统计 -->
        <template v-if="listCenterSub === 'objection-stats'">
          <div class="sd-kpi-row" style="display:flex;gap:12px;margin-bottom:14px;flex-wrap:wrap">
            <div class="sd-kpi"><div class="sd-kpi__lab">异议总数</div><div class="sd-kpi__num">{{ listCenterStats?.total ?? 0 }}</div></div>
            <div class="sd-kpi"><div class="sd-kpi__lab">待审核</div><div class="sd-kpi__num">{{ listCenterStats?.submitted ?? listCenterStats?.open ?? 0 }}</div></div>
            <div class="sd-kpi"><div class="sd-kpi__lab">已审核待处理</div><div class="sd-kpi__num">{{ listCenterStats?.approved ?? 0 }}</div></div>
            <div class="sd-kpi"><div class="sd-kpi__lab">已处理</div><div class="sd-kpi__num">{{ listCenterStats?.processed ?? 0 }}</div></div>
            <div class="sd-kpi"><div class="sd-kpi__lab">已办结</div><div class="sd-kpi__num">{{ listCenterStats?.closed ?? 0 }}</div></div>
          </div>
          <el-row :gutter="12">
            <el-col :span="12">
              <h4 class="confirm-h">按状态</h4>
              <el-table :data="(listCenterStats?.byStatus as Record<string, unknown>[]) || []" stripe size="small">
                <el-table-column prop="title" label="状态" />
                <el-table-column prop="count" label="数量" width="100" />
              </el-table>
            </el-col>
            <el-col :span="12">
              <h4 class="confirm-h">按类型</h4>
              <el-table :data="(listCenterStats?.byType as Record<string, unknown>[]) || []" stripe size="small">
                <el-table-column label="类型">
                  <template #default="{ row }">{{ $statusLabel(row.code || row.title) }}</template>
                </el-table-column>
                <el-table-column prop="count" label="数量" width="100" />
              </el-table>
            </el-col>
          </el-row>
        </template>

        <!-- 异议清单分态 -->
        <template v-else-if="isObjectionListSub()">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="异议标题" class="portal-field-md">
              <el-input v-model="objectionFilter.title" clearable />
            </el-form-item>
            <el-form-item label="异议对象" class="portal-field-md">
              <el-input v-model="objectionFilter.object" clearable />
            </el-form-item>
            <el-form-item label="异议提出单位" class="portal-field-md">
              <el-input v-model="objectionFilter.provider" clearable />
            </el-form-item>
            <el-form-item label="异议核查单位" class="portal-field-md">
              <el-input v-model="objectionFilter.verify" clearable />
            </el-form-item>
            <el-form-item label="状态" class="portal-field-sm">
              <el-select v-model="objectionFilter.status" clearable placeholder="全部">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="待审核" value="SUBMITTED" />
                <el-option label="已审核待处理" value="APPROVED" />
                <el-option label="驳回待提交" value="REJECTED" />
                <el-option label="已处理" value="PROCESSED" />
                <el-option label="已办结" value="CLOSED" />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="listPage = 1">查询</el-button>
              <el-button @click="resetObjectionFilter">重置</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="pagedListItems" stripe size="small">
            <el-table-column prop="title" label="异议标题" min-width="140" show-overflow-tooltip />
            <el-table-column prop="objectName" label="异议对象" min-width="120" show-overflow-tooltip />
            <el-table-column prop="serviceName" label="服务名称" width="120" show-overflow-tooltip />
            <el-table-column prop="providerOrg" label="异议提出单位" width="120" show-overflow-tooltip />
            <el-table-column prop="verifyOrg" label="异议核查单位" width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status)" size="small">{{ objectionStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt || row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openListDetail(row)">查看</el-button>
                <el-button
                  v-if="canObjectionAction(row, 'edit')"
                  link
                  @click="openObjectionEdit(row)"
                >编辑</el-button>
                <el-button
                  v-if="canObjectionAction(row, 'submit')"
                  link
                  @click="objectionAction(Number(row.id), 'SUBMIT')"
                >提交</el-button>
                <el-button
                  v-if="canObjectionAction(row, 'withdraw')"
                  link
                  @click="objectionAction(Number(row.id), 'WITHDRAW')"
                >撤销</el-button>
                <el-button
                  v-if="canObjectionAction(row, 'approve')"
                  link
                  type="success"
                  @click="auditObjection(Number(row.id))"
                >审核</el-button>
                <el-button
                  v-if="canObjectionAction(row, 'process')"
                  link
                  @click="startObjectionProcess(Number(row.id))"
                >处理</el-button>
                <el-button
                  v-if="canObjectionAction(row, 'close')"
                  link
                  type="success"
                  @click="closeObjectionFromList(Number(row.id))"
                >办结</el-button>
                <el-button
                  v-if="canObjectionAction(row, 'delete')"
                  link
                  type="danger"
                  @click="deleteObjection(Number(row.id))"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <!-- 目录 / 供需清单 -->
        <template v-else>
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item v-if="listCenterGroup !== '目录清单'" label="编码" class="portal-field-md">
              <el-input v-model="listFilter.code" clearable />
            </el-form-item>
            <el-form-item :label="listCenterGroup === '目录清单' ? '目录名称' : '名称'" class="portal-field-lg">
              <el-input v-model="listFilter.title" clearable />
            </el-form-item>
            <el-form-item label="状态" class="portal-field-sm">
              <el-input v-model="listFilter.status" clearable placeholder="状态码" />
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="listPage = 1">查询</el-button>
              <el-button @click="resetListFilter">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-if="listCenterGroup === '目录清单'" :data="pagedListItems" stripe size="small">
            <el-table-column label="目录名称" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.catalogName || row.title }}</template>
            </el-table-column>
            <el-table-column prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
            <el-table-column label="共享属性" width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ catalogShareAttrLabel(row.shareAttr) }}</template>
            </el-table-column>
            <el-table-column label="目录来源" width="110">
              <template #default="{ row }">{{ row.catalogOriginLabel || '—' }}</template>
            </el-table-column>
            <el-table-column label="版本" width="80">
              <template #default="{ row }">{{ row.versionNo ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status || row.publishStatus)" size="small">
                  {{ $statusLabel(row.status || row.publishStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt || row.updatedAt || row.publishedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openListDetail(row)">查看</el-button>
                <el-button link @click="exportListCenterCsv">导出</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-table v-else :data="pagedListItems" stripe size="small">
            <el-table-column prop="demandScene" label="需求场景" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ $statusLabel(row.demandScene) || row.demandScene || '—' }}</template>
            </el-table-column>
            <el-table-column prop="demandCatalog" label="需求目录" min-width="140" show-overflow-tooltip />
            <el-table-column prop="demandService" label="需求服务" width="120" show-overflow-tooltip />
            <el-table-column prop="requesterOrg" label="数据需求单位" width="120" show-overflow-tooltip />
            <el-table-column prop="providerOrg" label="数据提供单位" width="120" show-overflow-tooltip />
            <el-table-column prop="resourceLevel" label="资源级别" width="100">
              <template #default="{ row }">{{ $statusLabel(row.resourceLevel) || row.resourceLevel || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt || row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openListDetail(row)">查看</el-button>
                <el-button
                  v-if="row.id && String(row.code || '').startsWith('DEMAND')"
                  link
                  @click="openTrack(row)"
                >跟踪</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <PortalPagination
          v-if="listCenterSub !== 'objection-stats' && filteredListItems.length"
          v-model:page="listPage"
          v-model:page-size="listPageSize"
          :total="filteredListItems.length"
        />
      </div>

      <el-drawer v-model="listDetail.visible" title="清单详情" size="520px">
        <div v-loading="listDetail.loading">
          <template v-if="listDetail.row">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item
                v-for="item in listDetailFieldEntries(listDetail.row)"
                :key="item.key"
                :label="item.label"
              >{{ formatListDetailValue(item.key, item.value) }}</el-descriptions-item>
            </el-descriptions>
            <div class="track-stage-title" style="margin-top:16px">审核流程</div>
            <el-table :data="listDetail.stages" stripe size="small" empty-text="暂无审核记录">
              <el-table-column prop="status" label="状态" width="140" show-overflow-tooltip />
              <el-table-column prop="result" label="结果" min-width="160" show-overflow-tooltip />
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
          </template>
        </div>
      </el-drawer>

      <el-dialog
        v-model="objectionCreateVisible"
        :title="objectionEditMode ? '编辑异议' : '新增异议'"
        width="560px"
        destroy-on-close
        @closed="resetObjectionForm"
      >
        <el-form label-width="110px">
          <el-form-item label="已通过需求" required>
            <el-select
              v-model="objectionForm.demandId"
              filterable
              clearable
              placeholder="选择已确认/已办结需求"
              style="width:100%"
              :disabled="objectionEditMode"
            >
              <el-option
                v-for="d in objectionEligibleDemands"
                :key="String(d.id)"
                :label="String(d.demandTitle || d.id)"
                :value="Number(d.id)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="objectionForm.title" clearable placeholder="可选，默认自动生成" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="objectionForm.objectionType" style="width:100%">
              <el-option label="质量" value="QUALITY" />
              <el-option label="完整性" value="COMPLETENESS" />
              <el-option label="授权" value="AUTH" />
            </el-select>
          </el-form-item>
          <el-form-item label="内容" required>
            <el-input
              v-model="objectionForm.content"
              type="textarea"
              :rows="4"
              placeholder="请描述异议内容"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="objectionCreateVisible = false">取消</el-button>
          <el-button @click="submitObjection(true)">{{ objectionEditMode ? '保存' : '暂存草稿' }}</el-button>
          <el-button type="primary" @click="submitObjection(false)">提交异议</el-button>
        </template>
      </el-dialog>
    </PageCard>

    <el-dialog v-model="mountDialog.visible" title="已挂载" width="560px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:14px"
        title="确认该需求对应目录已挂载数据共享门户？挂载后需求部门可从门户申请，并可办结进入供给审核。"
      />
      <el-form label-width="120px">
        <el-form-item label="供数单位">
          <el-input :model-value="mountDialog.providerOrg || '—'" disabled />
        </el-form-item>
        <el-form-item label="已挂载目录" required>
          <el-select
            v-model="mountDialog.catalogId"
            filterable
            clearable
            placeholder="请选择部门数据共享门户中该提供部门的目录"
            style="width:100%"
            :loading="mountDialog.loading"
          >
            <el-option
              v-for="c in mountDialog.catalogs"
              :key="Number(c.id)"
              :label="String(c.title || c.id)"
              :value="Number(c.id)"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mountDialog.visible = false">取消</el-button>
        <el-button type="primary" :disabled="!mountDialog.catalogId" @click="confirmMarkCatalogMounted">确定</el-button>
      </template>
    </el-dialog>

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
.track-summary {
  margin-bottom: 16px;
}
.track-stage-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 4px 0 10px;
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
.supply-scope-tabs {
  margin-bottom: 8px;
}
</style>
