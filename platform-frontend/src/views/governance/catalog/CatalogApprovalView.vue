<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'
import { useAuthStore } from '@/stores/auth'

const props = withDefaults(defineProps<{ catalogOrigin?: 'INGEST' | 'GOVERNANCE'; embedded?: boolean }>(), {
  catalogOrigin: 'GOVERNANCE',
  embedded: false,
})

const route = useRoute()
const auth = useAuthStore()

const pageTitle = computed(() =>
  props.catalogOrigin === 'INGEST' ? '数据资源目录审批' : '资源目录审批',
)

/** 治理侧仅审批资源目录；归集侧仍含资源分类审批 */
const showCategoryScope = computed(() => props.catalogOrigin === 'INGEST')

/** 超级/平台管理员：资源目录审批（一审即生效） */
const isCatalogAdmin = computed(() => auth.isSystemAdmin || auth.isPlatformAdmin)

function approvalStepLabel(step?: string) {
  // 现行：仅平台一审；历史 PROVIDER 节点仍按原名展示
  if (String(step || '').toUpperCase() === 'PROVIDER') return '目录提供单位审核（历史）'
  return '平台管理员审核'
}

/** 是否可审：仅平台/超管；以后端 canApprove 为准 */
function canApproveRow(row: ApprovalRow) {
  if (row.status !== 'PENDING') return false
  if (row.canApprove === true) return true
  if (row.canApprove === false) return false
  return isCatalogAdmin.value
}

interface ApprovalRow {
  id: number
  resourceId?: number
  categoryId?: number
  resourceCode?: string
  resourceName?: string
  categoryName?: string
  categoryCode?: string
  resourceType?: string
  publishStatus?: string
  approvalStatus?: string
  resourceAlive?: boolean
  resourceFormat?: string
  providerOrg?: string
  actionType: string
  /** PLATFORM=平台审核；PROVIDER=目录提供单位审核 */
  approvalStep?: string
  status: string
  submitComment?: string
  reviewComment?: string
  submittedBy?: string
  submittedAt?: string
  reviewedBy?: string
  reviewerContact?: string
  reviewedAt?: string
  payloadJson?: string
  canApprove?: boolean
}

const ACTION_ZH: Record<string, string> = {
  PUBLISH: '发布',
  OFFLINE: '下线',
  UPDATE: '变更',
  DELETE: '删除',
  CREATE: '编目新增',
  BIND: '关联资源',
  UNBIND: '解除关联',
  CAT_CREATE: '分类新增',
  CAT_UPDATE: '分类编辑',
  CAT_DELETE: '分类删除',
}

const ACTION_TAG: Record<string, string> = {
  PUBLISH: 'success',
  OFFLINE: 'info',
  DELETE: 'danger',
  CREATE: '',
  BIND: 'warning',
  UNBIND: 'warning',
  CAT_CREATE: '',
  CAT_UPDATE: 'warning',
  CAT_DELETE: 'danger',
  UPDATE: 'warning',
}

const rows = ref<ApprovalRow[]>([])
const loading = ref(false)
const submitting = ref(false)
const statusFilter = ref('')
const selected = ref<ApprovalRow[]>([])
/** RESOURCE=资源目录；CATEGORY=资源分类 */
const activeScope = ref<'RESOURCE' | 'CATEGORY'>('RESOURCE')
/** 数据资源=库表/文件；服务资源=接口 */
const resourceKind = ref<'DATA' | 'SERVICE'>('DATA')
const DATA_FORMATS = new Set(['DATABASE', 'FILE'])
const SERVICE_FORMATS = new Set(['API'])
const page = ref(1)
const pageSize = ref(10)
const sortProp = ref('')
const sortOrder = ref<'ascending' | 'descending' | null>(null)

const reviewVisible = ref(false)
const reviewMode = ref<'single' | 'batch'>('single')
const reviewTarget = ref<ApprovalRow | null>(null)
const DEFAULT_APPROVE_COMMENT = '同意'
const reviewForm = reactive({
  decision: 'APPROVE' as 'APPROVE' | 'REJECT',
  reviewerName: '',
  reviewerContact: '',
  comment: DEFAULT_APPROVE_COMMENT,
})

function resetReviewForm() {
  reviewForm.decision = 'APPROVE'
  reviewForm.reviewerName = auth.user?.displayName || auth.user?.username || ''
  reviewForm.reviewerContact = ''
  reviewForm.comment = DEFAULT_APPROVE_COMMENT
}

/** 审批数据详情（查看 / 审核入口） */
const detailVisible = ref(false)
const detailRow = ref<ApprovalRow | null>(null)
const detailAllowAudit = ref(false)
const detailLoading = ref(false)
const detailResource = ref<Record<string, unknown> | null>(null)

const SHARE_ZH: Record<string, string> = {
  OPEN: '无条件共享',
  CONDITIONAL: '有条件共享',
  NOT_SHARE: '不予共享',
}
const SHARE_LEVEL_ZH: Record<string, string> = {
  OPEN: '无条件共享',
  CONDITIONAL: '有条件共享',
  NOT_SHARE: '不予共享',
}
const FORMAT_ZH: Record<string, string> = {
  DATABASE: '库表',
  FILE: '文件',
  API: '接口',
  OTHER: '其他',
}
const OPEN_ZH: Record<string, string> = {
  SOCIAL_OPEN: '开放',
  OPEN: '开放',
  NOT_OPEN: '不开放',
  CONDITIONAL: '有条件开放',
}
const CYCLE_ZH: Record<string, string> = {
  REALTIME: '实时',
  DAILY: '每日',
  WEEKLY: '每周',
  MONTHLY: '每月',
  YEARLY: '每年',
}

const reviewTitle = computed(() => (reviewMode.value === 'batch' ? '批量审核' : '审核'))
const detailTitle = computed(() => {
  if (!detailRow.value) return '目录详情'
  return detailAllowAudit.value ? '审核 · 目录详情' : '查看 · 目录详情'
})
const selectedPendingCount = computed(
  () => selected.value.filter((r) => canApproveRow(r)).length,
)
function sortValue(row: ApprovalRow, prop: string): string {
  if (prop === 'resourceName') return displayName(row)
  if (prop === 'actionType') return actionLabel(row.actionType)
  if (prop === 'status') return statusLabel(row.status)
  const v = (row as unknown as Record<string, unknown>)[prop]
  return v == null ? '' : String(v)
}

const sortedRows = computed(() => {
  const list = [...rows.value]
  const prop = sortProp.value
  const order = sortOrder.value
  if (!prop || !order) return list
  const dir = order === 'ascending' ? 1 : -1
  list.sort((a, b) => {
    const av = sortValue(a, prop)
    const bv = sortValue(b, prop)
    if (av === bv) return (b.id - a.id) * dir
    return av.localeCompare(bv, 'zh-CN', { numeric: true, sensitivity: 'base' }) * dir
  })
  return list
})

function resolveApprovalFormat(row: ApprovalRow): string {
  const direct = String(row.resourceFormat || '').toUpperCase()
  if (direct) return direct
  try {
    const p = JSON.parse(row.payloadJson || '{}') as Record<string, unknown>
    return String(p.resourceFormat || '').toUpperCase()
  } catch {
    return ''
  }
}

function matchResourceKind(row: ApprovalRow) {
  // 分类审批不属于库表/接口资源，两个 Tab 均展示（归集侧资源分类）
  if (activeScope.value === 'CATEGORY') return true
  const fmt = resolveApprovalFormat(row) || 'DATABASE'
  if (resourceKind.value === 'SERVICE') return SERVICE_FORMATS.has(fmt)
  return DATA_FORMATS.has(fmt)
}

const kindFilteredRows = computed(() => sortedRows.value.filter(matchResourceKind))

const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return kindFilteredRows.value.slice(start, start + pageSize.value)
})

function onSortChange(payload: { prop?: string; order?: 'ascending' | 'descending' | null }) {
  sortProp.value = payload.prop || ''
  sortOrder.value = payload.order || null
  page.value = 1
}
const emptyText = computed(() => {
  if (activeScope.value === 'CATEGORY') return '暂无资源分类审批记录'
  return resourceKind.value === 'SERVICE' ? '暂无服务资源目录审批记录' : '暂无数据资源目录审批记录'
})
const kindFilteredTotal = computed(() => kindFilteredRows.value.length)

/** 每个数据目录（或分类）仅保留最新一条审批记录 */
function collapseLatestPerDirectory(list: ApprovalRow[]): ApprovalRow[] {
  const map = new Map<string, ApprovalRow>()
  for (const row of list) {
    const key =
      activeScope.value === 'CATEGORY'
        ? `c:${row.categoryId ?? `a:${row.id}`}`
        : `r:${row.resourceId ?? `a:${row.id}`}`
    if (!map.has(key)) map.set(key, row)
  }
  return Array.from(map.values())
}

function canAdminOffline(row: ApprovalRow) {
  // 每目录一行：以资源发布态为准；待审中不展示下线
  return (
    isCatalogAdmin.value
    && activeScope.value === 'RESOURCE'
    && row.status !== 'PENDING'
    && row.resourceAlive !== false
    && !!row.resourceId
    && row.publishStatus === 'PUBLISHED'
    && row.approvalStatus !== 'PENDING'
  )
}

/** 待审核：审批页操作列「删除」；已审核不再展示删除（仅下线） */
function canAdminDelete(row: ApprovalRow) {
  return (
    isCatalogAdmin.value
    && activeScope.value === 'RESOURCE'
    && row.status === 'PENDING'
    && row.resourceAlive !== false
    && !!row.resourceId
  )
}

interface VersionRow {
  id: number
  resourceId: number
  versionNo: number
  changeSummary?: string
  publishedBy?: string
  publishedAt?: string
}

const versionDrawerVisible = ref(false)
const versionTitle = ref('')
const versions = ref<VersionRow[]>([])

async function openVersions(row: ApprovalRow) {
  if (!row.resourceId) {
    ElMessage.warning('无关联资源，无法查看版本')
    return
  }
  versionTitle.value = displayName(row)
  versionDrawerVisible.value = true
  try {
    versions.value = (await api.get(`/governance/catalog/resources-mgmt/${row.resourceId}/versions`)).data || []
  } catch {
    ElMessage.error('加载版本历史失败')
  }
}

async function load() {
  loading.value = true
  try {
    // 不按状态服务端过滤：先按目录折叠为最新一条，再按状态筛选，保证「每目录一行」
    const res = await api.get('/governance/catalog/resources-mgmt/approvals', {
      params: {
        catalogOrigin: props.catalogOrigin || undefined,
        scope: activeScope.value,
      },
    })
    const all = collapseLatestPerDirectory((res.data || []) as ApprovalRow[])
    const st = statusFilter.value
    rows.value = st ? all.filter((r) => r.status === st) : all
    page.value = 1
  } catch {
    ElMessage.error('加载审批列表失败')
  } finally {
    loading.value = false
  }
}

function onScopeChange() {
  selected.value = []
  page.value = 1
  void load()
}

function onResourceKindChange() {
  selected.value = []
  page.value = 1
}

function onQuery() {
  page.value = 1
  void load()
}

function onReset() {
  statusFilter.value = ''
  page.value = 1
  void load()
}

function displayName(row: ApprovalRow) {
  return row.resourceName || row.categoryName || '—'
}

function displayCode(row: ApprovalRow) {
  return row.resourceCode || row.categoryCode || '—'
}

function actionLabel(type: string) {
  return ACTION_ZH[type] || statusLabel(type)
}

const resourceFlowRows = ref<ApprovalRow[]>([])

interface FlowStepRow {
  step: string
  actionType?: string
  status: string
  actor?: string
  contact?: string
  time?: string
  comment?: string
}

/** 审批流程按环节展开：提交 → 平台管理员审核（历史单可能仍含提供方节点） */
function buildFlowSteps(rows: ApprovalRow[]): FlowStepRow[] {
  const sorted = [...rows].sort((a, b) => (a.id || 0) - (b.id || 0))
  const out: FlowStepRow[] = []
  let submitWritten = false
  for (const row of sorted) {
    if (!submitWritten) {
      out.push({
        step: '提交',
        actionType: row.actionType,
        status: 'DONE',
        actor: row.submittedBy || '—',
        contact: '—',
        time: row.submittedAt,
        comment: row.submitComment || '已提交',
      })
      submitWritten = true
    }
    const st = String(row.status || '').toUpperCase()
    const stepName = approvalStepLabel(row.approvalStep)
    if (st === 'PENDING') {
      out.push({
        step: '平台管理员审核',
        actionType: row.actionType,
        status: 'PENDING',
        actor: '—',
        contact: '—',
        time: '',
        comment: '待平台管理员审核',
      })
    } else if (st === 'WITHDRAWN') {
      out.push({
        step: '撤回',
        actionType: row.actionType,
        status: 'WITHDRAWN',
        actor: row.reviewedBy || row.submittedBy || '—',
        contact: row.reviewerContact || '—',
        time: row.reviewedAt || row.submittedAt,
        comment: row.reviewComment || '已撤回',
      })
    } else {
      out.push({
        step: stepName,
        actionType: row.actionType,
        status: st || row.status,
        actor: row.reviewedBy || '—',
        contact: row.reviewerContact || '—',
        time: row.reviewedAt,
        comment: row.reviewComment || (st === 'APPROVED' ? '已通过' : st === 'REJECTED' ? '已驳回' : '—'),
      })
    }
  }
  return out
}

const detailFlowSteps = computed(() => {
  if (detailRow.value?.resourceId) {
    return buildFlowSteps(resourceFlowRows.value)
  }
  return detailRow.value ? buildFlowSteps([detailRow.value]) : []
})

async function loadDetailResource(row: ApprovalRow) {
  detailResource.value = null
  resourceFlowRows.value = []
  if (!row.resourceId) return
  detailLoading.value = true
  try {
    const [res, flowRes] = await Promise.all([
      api.get(`/governance/catalog/resources-mgmt/${row.resourceId}`),
      api.get(`/governance/catalog/resources-mgmt/${row.resourceId}/approvals`),
    ])
    detailResource.value = (res.data || null) as Record<string, unknown> | null
    resourceFlowRows.value = (flowRes.data || []) as ApprovalRow[]
  } catch {
    detailResource.value = null
    resourceFlowRows.value = []
  } finally {
    detailLoading.value = false
  }
}

/** 查看：只读详情 */
async function openDetail(row: ApprovalRow) {
  detailRow.value = row
  detailAllowAudit.value = false
  detailVisible.value = true
  await loadDetailResource(row)
}

/**
 * 审核：先打开数据详情，详情内再点「审核」进入通过/驳回。
 * 批量审核仍直接打开决策弹窗。
 */
async function openReview(row: ApprovalRow) {
  if (!canApproveRow(row)) {
    ElMessage.warning('当前节点无权审核，或已处理')
    return
  }
  detailRow.value = row
  detailAllowAudit.value = true
  detailVisible.value = true
  await loadDetailResource(row)
}

/** 从详情进入单条审核决策 */
function openReviewFromDetail() {
  if (!detailRow.value || !canApproveRow(detailRow.value)) return
  reviewMode.value = 'single'
  reviewTarget.value = detailRow.value
  resetReviewForm()
  reviewVisible.value = true
}

function openBatchReview() {
  if (!selectedPendingCount.value) {
    ElMessage.warning('请勾选待处理审批')
    return
  }
  reviewMode.value = 'batch'
  reviewTarget.value = null
  resetReviewForm()
  reviewVisible.value = true
}

function onReviewDecision(decision: 'APPROVE' | 'REJECT') {
  const prev = reviewForm.decision
  reviewForm.decision = decision
  if (decision === 'REJECT' && reviewForm.comment.trim() === DEFAULT_APPROVE_COMMENT) {
    reviewForm.comment = ''
  } else if (decision === 'APPROVE' && prev === 'REJECT' && !reviewForm.comment.trim()) {
    reviewForm.comment = DEFAULT_APPROVE_COMMENT
  }
}

function detailField(key: string): string {
  const r = detailResource.value
  if (!r) return '—'
  const v = r[key]
  if (v == null || v === '') return '—'
  return String(v)
}

function detailShareLabel(): string {
  const raw = detailField('shareType')
  if (raw === '—') return raw
  return SHARE_ZH[raw] || statusLabel(raw)
}

function detailFormatLabel(): string {
  const raw = detailField('resourceFormat')
  if (raw === '—') return raw
  return FORMAT_ZH[raw] || statusLabel(raw)
}

function detailOpenLabel(): string {
  const raw = detailField('openType')
  if (raw === '—') return raw
  return OPEN_ZH[raw] || statusLabel(raw)
}

function detailCycleLabel(): string {
  const raw = detailField('updateCycle')
  if (raw === '—') return raw
  return CYCLE_ZH[raw] || statusLabel(raw)
}

function detailCategoryLabel(): string {
  const name = detailField('categoryName')
  if (name !== '—') return name
  const path = detailField('categoryPath')
  if (path === '—') return '—'
  const parts = path.split('/').filter(Boolean)
  return parts.length ? parts[parts.length - 1] : path
}

const detailTagList = computed((): string[] => {
  const r = detailResource.value
  if (!r) return []
  const tags = r.tagList
  if (Array.isArray(tags)) return tags.map(String).filter(Boolean)
  const raw = r.tags
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean)
    } catch {
      return raw.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
    }
  }
  return []
})

function parseExtJson(raw: unknown): Record<string, any> | null {
  if (raw == null || raw === '') return null
  if (typeof raw === 'object') return raw as Record<string, any>
  if (typeof raw !== 'string') return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

function joinApiUrl(base: string, path: string): string {
  const b = (base || '').trim()
  const p = (path || '').trim()
  if (!p) return b
  if (/^https?:\/\//i.test(p)) return p
  if (!b) return p
  if (b.endsWith(p) || b.endsWith(`/${p.replace(/^\/+/, '')}`)) return b
  return `${b.replace(/\/+$/, '')}/${p.replace(/^\/+/, '')}`
}

const detailExt = computed(() => parseExtJson(detailResource.value?.extJson))
const detailResourceFormat = computed(() => String(detailResource.value?.resourceFormat || '').toUpperCase())

const detailTableBind = computed(() => {
  const r = detailResource.value
  const ext = detailExt.value
  const path = String(r?.sourcePathType || '')
  return {
    sourceName: String(ext?.bindSourceName || '—'),
    tableName: String(ext?.bindTableName || r?.physicalTableName || '—'),
    entryCode: String(r?.metadataEntryCode || '—'),
    sourcePath: path === 'PROCESSED' ? '加工共享' : path ? '直通共享' : '—',
  }
})

interface DetailColumnRow {
  columnName: string
  columnNameZh: string
  dataType: string
  sensLevel: string
  shareLevel: string
  displayFlag: boolean
  searchFlag: boolean
  statFlag: boolean
  sortFlag: boolean
}

const detailColumns = computed((): DetailColumnRow[] => {
  const list = detailExt.value?.columnList
  if (!Array.isArray(list)) return []
  return list.map((c: Record<string, unknown>) => ({
    columnName: String(c.columnName || ''),
    columnNameZh: String(c.columnNameZh || c.remark || ''),
    dataType: String(c.dataTypeZh || c.dataType || ''),
    sensLevel: String(c.sensLevel || ''),
    shareLevel: String(c.shareLevel || c.fieldType || ''),
    displayFlag: c.displayFlag !== false,
    searchFlag: !!c.searchFlag,
    statFlag: !!c.statFlag,
    sortFlag: !!c.sortFlag,
  }))
})

const detailApi = computed(() => {
  let apiBlk = detailExt.value?.api as unknown
  if (typeof apiBlk === 'string') {
    try {
      apiBlk = JSON.parse(apiBlk)
    } catch {
      apiBlk = null
    }
  }
  if (!apiBlk || typeof apiBlk !== 'object') return null
  const api = apiBlk as Record<string, any>
  const apiUrl = String(api.apiUrl || '')
  const apiPath = String(api.apiPath || '')
  const mapParams = (raw: unknown) =>
    Array.isArray(raw)
      ? raw.map((p: Record<string, unknown>) => ({
          name: String(p.name || ''),
          type: String(p.type || ''),
          required: !!p.required,
          description: String(p.description || ''),
        }))
      : []
  return {
    apiName: String(api.apiName || ''),
    apiUrl,
    apiPath,
    fullUrl: joinApiUrl(apiUrl, apiPath),
    apiMethod: String(api.apiMethod || ''),
    apiVersion: String(api.apiVersion || ''),
    apiTimeout: api.apiTimeout == null || api.apiTimeout === '' ? '—' : String(api.apiTimeout),
    apiDescription: String(api.apiDescription || ''),
    requestParams: mapParams(api.requestParams),
    responseParams: mapParams(api.responseParams),
  }
})

const detailFile = computed(() => {
  const file = detailExt.value?.file
  if (!file || typeof file !== 'object') return null
  const f = file as Record<string, any>
  const cols = Array.isArray(f.columnList)
    ? f.columnList.map((c: Record<string, unknown>) => ({
        columnName: String(c.columnName || c.name || ''),
        columnNameZh: String(c.columnNameZh || ''),
        dataType: String(c.dataType || c.type || ''),
        description: String(c.description || ''),
      }))
    : []
  return {
    fileName: String(f.fileName || ''),
    fileRemark: String(f.fileRemark || ''),
    columns: cols,
  }
})

function shareLevelLabel(code: string): string {
  if (!code) return '—'
  return SHARE_LEVEL_ZH[code] || statusLabel(code) || code
}

async function submitReview() {
  if (!reviewForm.reviewerName.trim()) {
    ElMessage.warning('请填写审批人')
    return
  }
  if (!reviewForm.reviewerContact.trim()) {
    ElMessage.warning('请填写联系方式')
    return
  }
  if (reviewForm.decision === 'REJECT' && !reviewForm.comment.trim()) {
    ElMessage.warning('驳回须填写驳回意见')
    return
  }
  const comment =
    reviewForm.comment.trim() ||
    (reviewForm.decision === 'APPROVE' ? DEFAULT_APPROVE_COMMENT : '')
  const payload = {
    comment,
    reviewerName: reviewForm.reviewerName.trim(),
    reviewerContact: reviewForm.reviewerContact.trim(),
  }

  submitting.value = true
  try {
    if (reviewMode.value === 'single' && reviewTarget.value) {
      const id = reviewTarget.value.id
      if (reviewForm.decision === 'APPROVE') {
        await api.post(`/governance/catalog/resources-mgmt/approvals/${id}/approve`, payload)
        ElMessage.success('审核已通过')
      } else {
        await api.post(`/governance/catalog/resources-mgmt/approvals/${id}/reject`, payload)
        ElMessage.success('已驳回')
      }
    } else {
      const ids = selected.value.filter((r) => canApproveRow(r)).map((r) => r.id)
      if (!ids.length) {
        ElMessage.warning('请勾选有权审核的待处理审批')
        return
      }
      if (reviewForm.decision === 'APPROVE') {
        const res = await api.post('/governance/catalog/resources-mgmt/approvals/batch-approve', {
          ids,
          ...payload,
        })
        const d = res.data || {}
        ElMessage.success(`已处理 ${d.approved || 0} 条`)
        if (d.errors?.length) ElMessage.warning(d.errors.slice(0, 3).join('；'))
      } else {
        const res = await api.post('/governance/catalog/resources-mgmt/approvals/batch-reject', {
          ids,
          ...payload,
        })
        const d = res.data || {}
        ElMessage.success(`已驳回 ${d.rejected || 0} 条`)
        if (d.errors?.length) ElMessage.warning(d.errors.slice(0, 3).join('；'))
      }
      selected.value = []
    }
    reviewVisible.value = false
    detailVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '审核失败')
  } finally {
    submitting.value = false
  }
}

async function bootstrap() {
  selected.value = []
  page.value = 1
  await load()
}

async function adminOffline(row: ApprovalRow) {
  if (!row.resourceId) return
  await ElMessageBox.confirm(
    `确认下线「${displayName(row)}」？下线后将从门户移除，目录回到「数据资源编目管理」可再编辑/发布。`,
    '下线目录',
    { type: 'warning' },
  )
  await api.post(`/governance/catalog/resources-mgmt/${row.resourceId}/admin-offline`, {
    comment: '管理员从审批页下线',
  })
  ElMessage.success('已下线，可在编目管理中查看')
  await load()
}

async function adminDelete(row: ApprovalRow) {
  if (!row.resourceId) return
  await ElMessageBox.confirm(
    `确认删除「${displayName(row)}」？删除后不可恢复。`,
    '删除目录',
    { type: 'warning', confirmButtonText: '确认删除' },
  )
  await api.post(`/governance/catalog/resources-mgmt/${row.resourceId}/admin-delete`, {
    comment: '管理员从审批页删除',
  })
  ElMessage.success('已删除')
  await load()
}

watch(
  () => [route.query.cSub, route.query.module, props.catalogOrigin] as const,
  ([cSub, module]) => {
    const onApprovals =
      cSub === 'approvals' ||
      module === 'catalog.approvals' ||
      String(module || '').endsWith('approvals')
    // 治理 Hub 用 cSub；归集 Hub 用 module；进入本页时 bootstrap 已由 onMounted/onActivated 覆盖
    if (onApprovals) void bootstrap()
  },
)

onMounted(() => {
  if (!showCategoryScope.value) activeScope.value = 'RESOURCE'
  void bootstrap()
})

onActivated(() => {
  if (!showCategoryScope.value) activeScope.value = 'RESOURCE'
  void bootstrap()
})
</script>

<template>
  <div class="approval-page">
    <component :is="embedded ? 'div' : PageCard" :title="embedded ? undefined : pageTitle">
      <el-tabs v-model="resourceKind" class="approval-tabs" @tab-change="onResourceKindChange">
        <el-tab-pane label="数据资源" name="DATA" />
        <el-tab-pane label="服务资源" name="SERVICE" />
      </el-tabs>

      <el-tabs
        v-if="showCategoryScope"
        v-model="activeScope"
        class="approval-tabs approval-tabs--scope"
        @tab-change="onScopeChange"
      >
        <el-tab-pane label="资源目录" name="RESOURCE" />
        <el-tab-pane label="资源分类" name="CATEGORY" />
      </el-tabs>

      <el-form inline class="portal-inline-form portal-inline-form--block toolbar">
        <el-form-item label="状态" class="portal-field-sm">
          <el-select v-model="statusFilter" clearable placeholder="全部">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已审核" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已撤回" value="WITHDRAWN" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="onQuery">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <el-button type="primary" plain :disabled="!selectedPendingCount" @click="openBatchReview">
            批量审核
            <template v-if="selectedPendingCount">（{{ selectedPendingCount }}）</template>
          </el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="loading"
        class="approval-table"
        :data="pageRows"
        stripe
        border
        size="small"
        :empty-text="emptyText"
        @selection-change="(list: ApprovalRow[]) => (selected = list)"
        @sort-change="onSortChange"
      >
        <el-table-column type="selection" width="46" :selectable="(row: ApprovalRow) => canApproveRow(row)" />
        <el-table-column label="目录名称" min-width="180" prop="resourceName" sortable="custom">
          <template #default="{ row }">
            <div class="obj">
              <div class="obj__name">{{ displayName(row) }}</div>
              <div class="obj__code">{{ displayCode(row) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" prop="actionType" width="110" align="center" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="(ACTION_TAG[row.actionType] as any) || 'info'">
              {{ actionLabel(row.actionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.status === 'PENDING'">平台管理员审核</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
        <el-table-column label="审批状态" prop="status" width="110" align="center" sortable="custom">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submittedBy" label="提交人" width="120" show-overflow-tooltip sortable="custom" />
        <el-table-column label="提交时间" width="166" sortable="custom" prop="submittedAt">
          <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column prop="reviewComment" label="审批意见" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button
              v-if="activeScope === 'RESOURCE' && row.resourceId"
              link
              @click="openVersions(row)"
            >版本</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button v-if="canApproveRow(row)" link type="primary" @click="openReview(row)">审核</el-button>
              <el-button v-if="canAdminDelete(row)" link type="danger" @click="adminDelete(row)">删除</el-button>
            </template>
            <template v-else-if="canAdminOffline(row)">
              <el-button link type="warning" @click="adminOffline(row)">下线</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <PortalPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="kindFilteredTotal"
      />
    </component>

    <!-- 查看：目录基本信息 + 审批流程；审核入口在详情内再点「审核」 -->
    <el-drawer
      v-model="detailVisible"
      :title="detailTitle"
      size="960px"
      destroy-on-close
      append-to-body
    >
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detailRow">
          <el-descriptions
            v-if="detailRow.resourceId"
            :column="1"
            border
            size="small"
            title="基本信息"
          >
            <el-descriptions-item label="目录名称">{{ detailField('resourceName') }}</el-descriptions-item>
            <el-descriptions-item label="目录编码">{{ detailField('resourceCode') }}</el-descriptions-item>
            <el-descriptions-item label="所属部门">{{ detailField('providerOrg') }}</el-descriptions-item>
            <el-descriptions-item label="所属主题">{{ detailField('themeName') }}</el-descriptions-item>
            <el-descriptions-item label="信息资源格式">{{ detailFormatLabel() }}</el-descriptions-item>
            <el-descriptions-item label="共享类型">{{ detailShareLabel() }}</el-descriptions-item>
            <el-descriptions-item label="共享条件">{{ detailField('shareCondition') }}</el-descriptions-item>
            <el-descriptions-item v-if="detailField('notShareReason') !== '—'" label="不共享理由">
              {{ detailField('notShareReason') }}
            </el-descriptions-item>
            <el-descriptions-item label="是否向社会开放">{{ detailOpenLabel() }}</el-descriptions-item>
            <el-descriptions-item label="开放条件">{{ detailField('openCondition') }}</el-descriptions-item>
            <el-descriptions-item v-if="detailField('notOpenReason') !== '—'" label="不开放理由">
              {{ detailField('notOpenReason') }}
            </el-descriptions-item>
            <el-descriptions-item label="更新周期">{{ detailCycleLabel() }}</el-descriptions-item>
            <el-descriptions-item label="基础信息资源目录">{{ detailCategoryLabel() }}</el-descriptions-item>
            <el-descriptions-item label="联系人名称">{{ detailField('contactName') }}</el-descriptions-item>
            <el-descriptions-item label="联系人电话">{{ detailField('contactPhone') }}</el-descriptions-item>
            <el-descriptions-item label="联系人邮箱">{{ detailField('contactEmail') }}</el-descriptions-item>
            <el-descriptions-item label="标签">
              <template v-if="detailTagList.length">
                <el-tag v-for="tag in detailTagList" :key="tag" size="small" class="detail-tag">{{ tag }}</el-tag>
              </template>
              <span v-else>—</span>
            </el-descriptions-item>
            <el-descriptions-item label="目录描述">{{ detailField('description') }}</el-descriptions-item>
          </el-descriptions>

          <template v-if="detailResourceFormat === 'DATABASE'">
            <div class="detail-section-title">库表信息</div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="数据源">{{ detailTableBind.sourceName }}</el-descriptions-item>
              <el-descriptions-item label="数据表">{{ detailTableBind.tableName }}</el-descriptions-item>
              <el-descriptions-item label="元数据条目">{{ detailTableBind.entryCode }}</el-descriptions-item>
              <el-descriptions-item label="来源路径">{{ detailTableBind.sourcePath }}</el-descriptions-item>
            </el-descriptions>
            <div class="detail-section-title">字段列表</div>
            <el-table
              :data="detailColumns"
              size="small"
              stripe
              border
              class="detail-table"
              empty-text="暂无字段信息"
              max-height="360"
            >
              <el-table-column prop="columnName" label="名称" width="140" show-overflow-tooltip />
              <el-table-column prop="columnNameZh" label="中文名称" min-width="120" show-overflow-tooltip />
              <el-table-column prop="dataType" label="类型" width="100" show-overflow-tooltip />
              <el-table-column label="敏感级别" width="90">
                <template #default="{ row }">{{ row.sensLevel || '—' }}</template>
              </el-table-column>
              <el-table-column label="共享类型" width="120">
                <template #default="{ row }">{{ shareLevelLabel(row.shareLevel) }}</template>
              </el-table-column>
              <el-table-column label="展示项" width="80" align="center">
                <template #default="{ row }">{{ row.displayFlag ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="搜索项" width="80" align="center">
                <template #default="{ row }">{{ row.searchFlag ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="统计项" width="80" align="center">
                <template #default="{ row }">{{ row.statFlag ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="排序项" width="80" align="center">
                <template #default="{ row }">{{ row.sortFlag ? '是' : '否' }}</template>
              </el-table-column>
            </el-table>
          </template>

          <template v-else-if="detailResourceFormat === 'API' && detailApi">
            <div class="detail-section-title">接口信息</div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="接口名称">{{ detailApi.apiName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="目标地址">{{ detailApi.apiUrl || '—' }}</el-descriptions-item>
              <el-descriptions-item label="请求路径">{{ detailApi.apiPath || '—' }}</el-descriptions-item>
              <el-descriptions-item label="完整接口地址">{{ detailApi.fullUrl || '—' }}</el-descriptions-item>
              <el-descriptions-item label="请求方式">{{ detailApi.apiMethod || '—' }}</el-descriptions-item>
              <el-descriptions-item label="接口版本">{{ detailApi.apiVersion || '—' }}</el-descriptions-item>
              <el-descriptions-item label="超时(ms)">{{ detailApi.apiTimeout }}</el-descriptions-item>
              <el-descriptions-item label="接口描述">{{ detailApi.apiDescription || '—' }}</el-descriptions-item>
            </el-descriptions>
            <div class="detail-section-title">请求参数</div>
            <el-table
              :data="detailApi.requestParams"
              size="small"
              stripe
              border
              class="detail-table"
              empty-text="暂无请求参数"
            >
              <el-table-column prop="name" label="参数名" min-width="120" show-overflow-tooltip />
              <el-table-column label="必填" width="70" align="center">
                <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column prop="description" label="简介" min-width="140" show-overflow-tooltip />
            </el-table>
            <div class="detail-section-title">响应参数</div>
            <el-table
              :data="detailApi.responseParams"
              size="small"
              stripe
              border
              class="detail-table"
              empty-text="暂无响应参数"
            >
              <el-table-column prop="name" label="参数名" min-width="120" show-overflow-tooltip />
              <el-table-column label="必填" width="70" align="center">
                <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="type" label="类型" width="100" />
              <el-table-column prop="description" label="简介" min-width="140" show-overflow-tooltip />
            </el-table>
          </template>

          <template v-else-if="detailResourceFormat === 'FILE' && detailFile">
            <div class="detail-section-title">文件信息</div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="文件名称">{{ detailFile.fileName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="说明">{{ detailFile.fileRemark || '—' }}</el-descriptions-item>
            </el-descriptions>
            <el-table
              :data="detailFile.columns"
              size="small"
              stripe
              border
              class="detail-table"
              empty-text="暂无列信息"
              max-height="360"
            >
              <el-table-column prop="columnName" label="列名" width="140" show-overflow-tooltip />
              <el-table-column prop="columnNameZh" label="中文名称" min-width="120" show-overflow-tooltip />
              <el-table-column prop="dataType" label="类型" width="100" />
              <el-table-column prop="description" label="简介" min-width="140" show-overflow-tooltip />
            </el-table>
          </template>

          <el-alert
            v-if="detailRow.categoryId && !detailRow.resourceId"
            type="info"
            :closable="false"
            show-icon
            title="本单为资源分类审批，无资源目录实体详情。"
          />

          <div class="detail-section-title">审批流程</div>
          <el-table
            :data="detailFlowSteps"
            size="small"
            stripe
            border
            empty-text="暂无审批记录"
            class="detail-table"
          >
            <el-table-column prop="step" label="环节" width="80" />
            <el-table-column label="操作类型" width="90">
              <template #default="{ row }">{{ actionLabel(row.actionType || '') }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status === 'DONE' ? 'APPROVED' : row.status)">
                  {{ row.status === 'DONE' ? '已完成' : statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="actor" label="处理人" width="110" show-overflow-tooltip />
            <el-table-column prop="contact" label="联系方式" width="120" show-overflow-tooltip />
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.time) }}</template>
            </el-table-column>
            <el-table-column prop="comment" label="结果/意见" min-width="120" show-overflow-tooltip />
          </el-table>
        </template>
      </div>
      <template #footer>
        <div class="detail-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button
            v-if="detailAllowAudit && detailRow?.status === 'PENDING'"
            type="primary"
            @click="openReviewFromDetail"
          >
            审核
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="reviewVisible"
      :title="reviewTitle"
      width="520px"
      class="review-dialog"
      align-center
      destroy-on-close
      append-to-body
    >
      <div v-if="reviewMode === 'batch'" class="review-subject">
        <div>
          <div class="review-subject__name">已选 {{ selectedPendingCount }} 条待处理审批</div>
        </div>
      </div>
      <div v-else-if="reviewTarget" class="review-subject">
        <div>
          <div class="review-subject__name">{{ displayName(reviewTarget) }}</div>
        </div>
      </div>

      <el-form label-width="88px" class="review-meta-form" @submit.prevent>
        <el-form-item label="审批人" required>
          <el-input v-model="reviewForm.reviewerName" maxlength="64" placeholder="请填写审批人姓名" clearable />
        </el-form-item>
        <el-form-item label="联系方式" required>
          <el-input v-model="reviewForm.reviewerContact" maxlength="64" placeholder="请填写手机号或固话" clearable />
        </el-form-item>
      </el-form>

      <div class="decision">
        <button
          type="button"
          class="decision__card"
          :class="{ 'is-active': reviewForm.decision === 'APPROVE', 'is-ok': true }"
          @click="onReviewDecision('APPROVE')"
        >
          <span class="decision__title">通过</span>
        </button>
        <button
          type="button"
          class="decision__card"
          :class="{ 'is-active': reviewForm.decision === 'REJECT', 'is-no': true }"
          @click="onReviewDecision('REJECT')"
        >
          <span class="decision__title">驳回</span>
        </button>
      </div>

      <div class="comment-block">
        <div class="comment-block__label">
          {{ reviewForm.decision === 'REJECT' ? '驳回意见' : '审批意见' }}
          <em v-if="reviewForm.decision === 'REJECT'">（必填）</em>
        </div>
        <el-input
          v-model="reviewForm.comment"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          :placeholder="reviewForm.decision === 'REJECT' ? '请说明驳回原因' : '默认：同意'"
        />
      </div>

      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button
          :type="reviewForm.decision === 'REJECT' ? 'danger' : 'primary'"
          :loading="submitting"
          @click="submitReview"
        >
          {{ reviewForm.decision === 'REJECT' ? '确认驳回' : '确认通过' }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="versionDrawerVisible" :title="`版本历史 · ${versionTitle}`" size="520px">
      <el-table :data="versions" stripe size="small">
        <el-table-column prop="versionNo" label="版本" width="70" />
        <el-table-column prop="changeSummary" label="摘要" min-width="120" show-overflow-tooltip />
        <el-table-column prop="publishedBy" label="发布人" width="90" />
        <el-table-column prop="publishedAt" label="时间" width="150" />
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.approval-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.approval-tabs {
  margin-bottom: 4px;
}
.approval-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.pager-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.toolbar {
  margin-bottom: 4px;
}

.detail-tag {
  margin-right: 6px;
  margin-bottom: 2px;
}

.approval-table {
  width: 100%;
}

.obj__name {
  font-weight: 500;
  color: var(--portal-text);
  line-height: 1.35;
}

.obj__code {
  margin-top: 2px;
  font-size: 12px;
  color: var(--portal-text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.muted {
  color: #c0c4cc;
}

.detail-body {
  min-height: 120px;
}

.detail-section-title {
  margin: 18px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--portal-text);
}

.detail-table {
  width: 100%;
  margin-bottom: 4px;
}

.detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.review-subject {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px 14px;
  margin-bottom: 16px;
  border-radius: 8px;
  background: #f7f8fa;
  border: 1px solid var(--portal-border);
}

.review-subject__badge {
  flex-shrink: 0;
  height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 22px;
  color: #fff;
  background: var(--portal-primary);
}

.review-subject__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--portal-text);
}

.review-subject__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--portal-text-secondary);
}

.review-meta-form {
  margin-bottom: 8px;
}
.review-meta-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.decision {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.decision__card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-start;
  padding: 14px 14px;
  border-radius: 8px;
  border: 1px solid var(--portal-border);
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}

.decision__card:hover {
  border-color: #91caff;
}

.decision__card.is-active.is-ok {
  border-color: #1677ff;
  background: #f0f7ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.12);
}

.decision__card.is-active.is-no {
  border-color: #ff4d4f;
  background: #fff2f0;
  box-shadow: 0 0 0 2px rgba(255, 77, 79, 0.12);
}

.decision__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--portal-text);
}

.decision__hint {
  font-size: 12px;
  color: var(--portal-text-secondary);
}

.comment-block__label {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--portal-text);
}

.comment-block__label em {
  font-style: normal;
  color: var(--portal-text-secondary);
}

@media (max-width: 900px) {
  .intro {
    flex-direction: column;
  }

  .stat-cards {
    width: 100%;
  }

  .stat {
    flex: 1;
  }
}
</style>
