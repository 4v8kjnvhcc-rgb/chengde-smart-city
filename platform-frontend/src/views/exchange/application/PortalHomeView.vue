<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { DEPT_PORTAL_BRAND } from './application-nav'
import ShareCatalogPanel from './ShareCatalogPanel.vue'
import type { CatalogRow as ShareCatalogRow } from './ShareCatalogPanel.vue'
import SubscribeApplyPanel from './SubscribeApplyPanel.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { statusLabel } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'
import { phoneRule } from '@/utils/validators'
import {
  fetchFavorites,
  removeFavorite,
  type PortalFavorite,
} from './portal-favorites'

interface CatalogRow {
  id: number | string
  catalogCode: string
  title: string
  description: string
  catalogKind?: string
  themeCode?: string
  themeName?: string
  providerOrg?: string
  shareModes?: string
  resourceCount?: number
  hotScore?: number
  visitCount?: number
  publishedAt?: string
  previewItems?: { label: string; value: string }[]
}

interface HomeData {
  openResourceTotal: number
  apiServiceTotal: number
  shareOrgTotal: number
  exchangeVolumeTotal: number
  publishedCount?: number
  hotKeywords: string[]
  themes: { code: string; name: string; count?: number; apiCount?: number; dataCount?: number }[]
  baseLibraries?: { code: string; name: string; count?: number; apiCount?: number; dataCount?: number }[]
  providers: { name: string; count: number; apiCount?: number; dataCount?: number }[]
  latestResources: CatalogRow[]
  latestApplications?: { id: number; catalogTitle?: string; applicantOrg?: string; status?: string; createdAt?: string }[]
  hotResources: CatalogRow[]
}

interface ApprovalFlowStep {
  step: string
  status: string
  result: string
  actor?: string
  time?: string
  comment?: string
}

interface Subscription {
  id: number
  catalogId: number
  catalogTitle?: string
  catalogCode?: string
  applicantOrg: string
  resourceType: string
  purpose: string
  status: string
  approvalStep?: string
  platformReviewedBy?: string
  platformReviewerContact?: string
  platformApproverNote?: string
  platformReviewedAt?: string
  approverNote?: string
  reviewComment?: string
  reviewedBy?: string
  reviewerContact?: string
  reviewedAt?: string
  applyPayload?: Record<string, unknown> | string | null
  createdBy?: string
  createdAt?: string
  providerOrg?: string
  canApprove?: boolean
  taskId?: number
  taskType?: string
  taskStatus?: string
  approvalFlow?: ApprovalFlowStep[]
  oauthClientId?: string
  oauthClientSecret?: string
  apiUrl?: string
  apiMethod?: string
}

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const portalTab = ref('home')
const home = ref<HomeData | null>(null)
const searchQ = ref('')
const filterTheme = ref('')
const filterProvider = ref('')
const filterShareMode = ref('')
const catalogKind = ref<'DATA' | 'SERVICE' | ''>('')
const catalogView = ref<'table' | 'card'>('table')
const catalogRows = ref<CatalogRow[]>([])
const subscriptions = ref<Subscription[]>([])
const pendingSubsList = ref<Subscription[]>([])
const reviewedSubsList = ref<Subscription[]>([])
const myFavorites = ref<PortalFavorite[]>([])
const catalogOptions = ref<CatalogRow[]>([])
const preview = reactive<{ visible: boolean; row: CatalogRow | null }>({ visible: false, row: null })
const shareCatalogRef = ref<{
  loadCatalog: () => Promise<void>
  openDetail?: (row: ShareCatalogRow) => void | Promise<void>
  applyKeyword?: (kw?: string) => Promise<void>
} | null>(null)
/** 首页点资源后，切到政务共享资源再打开详情 */
const pendingCatalogId = ref<number | string | null>(null)

const subForm = reactive({
  catalogId: undefined as number | undefined,
  applicantOrg: '数据管理局',
  resourceType: 'TABLE',
  purpose: '',
})
const reviewForm = reactive({
  reviewerName: '',
  reviewerContact: '',
  note: '',
})
const subDetail = reactive<{
  visible: boolean
  row: Subscription | null
  mode: 'mine' | 'pending' | 'reviewed'
}>({
  visible: false,
  row: null,
  mode: 'mine',
})
const progressDlg = reactive<{ visible: boolean; row: Subscription | null }>({
  visible: false,
  row: null,
})

const shareLabel = (m?: string) => {
  if (!m) return '-'
  return m
    .split(',')
    .map((x) => ({ TABLE: '库表同步', FILE: '文件同步', API: 'API服务' }[x.trim()] || x.trim()))
    .join(' / ')
}
const kindLabel = (k?: string) => (k === 'SERVICE' ? '服务目录' : '数据目录')
const statusTag = (s: string) => {
  if (s === 'APPROVED' || s === 'READY') return 'success'
  if (s === 'REJECTED') return 'danger'
  return 'warning'
}
void statusTag

function syncTab() {
  const s = String(route.query.section || 'home')
  portalTab.value = ['home', 'catalog', 'subscribe', 'myspace'].includes(s) ? s : 'home'
}

function setTab(key: string) {
  portalTab.value = key
  const q: Record<string, string> = { section: key }
  if (searchQ.value) q.q = searchQ.value
  if (filterTheme.value) q.themeCode = filterTheme.value
  router.replace({ path: '/exchange/analysis-portal/dept', query: q })
  void loadTab().then(() => flushPendingCatalogDetail())
}

async function loadHome() {
  const res = await api.get('/exchange/portal/home', { params: { keyword: searchQ.value || undefined } })
  home.value = res.data
  catalogOptions.value = [...(res.data.hotResources || []), ...(res.data.latestResources || [])]
}

async function loadCatalog() {
  const res = await api.get('/exchange/portal/catalog', {
    params: {
      keyword: searchQ.value || undefined,
      themeCode: filterTheme.value || undefined,
      providerOrg: filterProvider.value || undefined,
      catalogKind: catalogKind.value || undefined,
      shareMode: filterShareMode.value || undefined,
    },
  })
  catalogRows.value = res.data
}

async function loadSubscriptions() {
  const [mineRes, pendingRes, reviewedRes, favs] = await Promise.all([
    api.get('/exchange/portal/subscriptions', { params: { scope: 'mine' } }),
    api.get('/exchange/portal/subscriptions', { params: { scope: 'pending' } }),
    api.get('/exchange/portal/subscriptions', { params: { scope: 'reviewed' } }),
    fetchFavorites('PORTAL'),
  ])
  subscriptions.value = mineRes.data || []
  pendingSubsList.value = pendingRes.data || []
  reviewedSubsList.value = reviewedRes.data || []
  myFavorites.value = favs
  try {
    await loadMyApps()
  } catch {
    myApps.value = []
  }
}

const shareBrowseMode = ref<'theme' | 'dept'>('theme')

/** 最新资源：按发布时间倒序，默认 5 条 */
const latestResources = computed(() => (home.value?.latestResources || []).slice(0, 5))
/** 最热资源：按访问量倒序，默认 5 条 */
const hotResources = computed(() => (home.value?.hotResources || []).slice(0, 5))

const themeApiTotal = computed(() =>
  (home.value?.themes || []).reduce((s, t) => s + (t.apiCount || 0), 0),
)
const themeDataTotal = computed(() =>
  (home.value?.themes || []).reduce((s, t) => s + (t.dataCount ?? t.count ?? 0), 0),
)
const deptApiTotal = computed(() =>
  (home.value?.providers || []).reduce((s, p) => s + (p.apiCount || 0), 0),
)
const deptDataTotal = computed(() =>
  (home.value?.providers || []).reduce((s, p) => s + (p.dataCount ?? p.count ?? 0), 0),
)

async function loadTab() {
  loading.value = true
  try {
    if (portalTab.value === 'home') {
      await loadHome()
    } else if (portalTab.value === 'catalog') {
      await Promise.all([ensureHomeFacets(), loadCatalog()])
    } else if (portalTab.value === 'subscribe') {
      await ensureHomeFacets()
    } else if (portalTab.value === 'myspace') {
      await loadSubscriptions()
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function ensureHomeFacets() {
  if (!home.value) await loadHome()
}

function openTheme(code: string) {
  filterTheme.value = code
  filterProvider.value = ''
  setTab('catalog')
}

function openProvider(name: string) {
  filterProvider.value = name
  filterTheme.value = ''
  setTab('catalog')
}

async function doBannerSearch() {
  setTab('catalog')
  await nextTick()
  if (shareCatalogRef.value?.applyKeyword) {
    await shareCatalogRef.value.applyKeyword(searchQ.value)
  }
}

function clickHotKeyword(kw: string) {
  searchQ.value = kw
  void doBannerSearch()
}

function openPreview(row: CatalogRow) {
  preview.row = row
  preview.visible = true
}

/** 跳转政务共享资源详情页 */
function openShareDetail(row: CatalogRow) {
  preview.visible = false
  pendingCatalogId.value = row.id
  setTab('catalog')
}

async function flushPendingCatalogDetail() {
  const id = pendingCatalogId.value
  if (id == null || portalTab.value !== 'catalog') return
  for (let i = 0; i < 30; i++) {
    await nextTick()
    const open = shareCatalogRef.value?.openDetail
    if (open) {
      pendingCatalogId.value = null
      await open({ id } as ShareCatalogRow)
      return
    }
    await new Promise((r) => setTimeout(r, 50))
  }
  pendingCatalogId.value = null
}

function applyFromCatalog(row: CatalogRow) {
  openShareDetail(row)
}

async function submitSubscription() {
  if (!subForm.catalogId) return ElMessage.warning('请选择目录')
  await api.post('/exchange/portal/subscriptions', subForm)
  ElMessage.success('订阅申请已提交')
  subForm.purpose = ''
  await loadSubscriptions()
}

function reviewApiError(e: unknown): string {
  const msg = e instanceof Error ? e.message : ''
  if (/timeout of \d+ms exceeded/i.test(msg) || /timeout/i.test(msg) && /exceeded|aborted/i.test(msg)) {
    return '审核请求超时，未在限定时间内收到 ESB 网关响应，请稍后重试'
  }
  return msg || '审批失败'
}

async function reviewSub(id: number, action: 'APPROVE' | 'REJECT') {
  if (!reviewForm.reviewerName.trim()) {
    ElMessage.warning('请填写审批人')
    return
  }
  if (!reviewForm.reviewerContact.trim()) {
    ElMessage.warning('请填写联系方式')
    return
  }
  if (action === 'REJECT' && !reviewForm.note.trim()) {
    ElMessage.warning('驳回须填写驳回意见')
    return
  }
  try {
    const pendingRow = pendingSubsList.value.find((r) => r.id === id)
      || (subDetail.row?.id === id ? subDetail.row : null)
    const wasPlatform = String(pendingRow?.approvalStep || 'PLATFORM').toUpperCase() !== 'PROVIDER'
    const providerName = pendingRow?.providerOrg || '目录提供单位'
    const res = await api.post(`/exchange/portal/subscriptions/${id}/review`, {
      action,
      approverNote: reviewForm.note,
      reviewerName: reviewForm.reviewerName.trim(),
      reviewerContact: reviewForm.reviewerContact.trim(),
    }, { timeout: 45_000 })
    if (action === 'APPROVE') {
      const nextStep = String(res.data?.approvalStep || '').toUpperCase()
      if (wasPlatform && (nextStep === 'PROVIDER' || res.data?.status === 'PENDING')) {
        ElMessage.success(`平台审核已通过，已转交「${providerName}」进行部门审核`)
      } else {
        ElMessage.success(`部门审核已通过${res.data?.taskId ? `，任务 #${res.data.taskId}` : ''}${res.data?.oauthClientId ? '，已发放接口调用凭证' : ''}`)
      }
    } else {
      ElMessage.success('已驳回')
    }
    reviewForm.note = ''
    subDetail.visible = false
    await loadSubscriptions()
  } catch (e: unknown) {
    ElMessage.error(reviewApiError(e))
  }
}

function openSubDetail(row: Subscription, mode: 'mine' | 'pending' | 'reviewed') {
  subDetail.row = row
  subDetail.mode = mode
  subDetail.visible = true
  if (mode === 'pending') {
    reviewForm.reviewerName = auth.user?.displayName || auth.user?.username || ''
    reviewForm.reviewerContact = ''
    reviewForm.note = ''
  }
}

function fmtFlowTime(v?: string) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : '—'
}

function flowStatusTag(s?: string) {
  if (s === 'APPROVED' || s === 'DONE') return 'success'
  if (s === 'REJECTED' || s === 'CANCELLED') return 'danger'
  if (s === 'SKIPPED') return 'info'
  return 'warning'
}

function approvalProgressText(row: Subscription): string {
  const st = String(row.status || '').toUpperCase()
  const hasCred = isCredentialSub(row) && !!(row.oauthClientId || payloadStr(payloadObj(row), 'oauthClientId'))
  if (st === 'PENDING') {
    const step = String((row as any).approvalStep || 'PLATFORM').toUpperCase()
    return step === 'PROVIDER' ? '提交申请 → 平台已通过 → 待提供方审批' : '提交申请 → 待平台审核'
  }
  if (st === 'REJECTED') return '提交申请 → 已驳回'
  if (st === 'CANCELLED') return '已取消'
  if (st === 'APPROVED') {
    if (isCredentialSub(row) && hasCred) return '提交申请 → 已通过 → 凭证已发放'
    if (isCredentialSub(row)) return '提交申请 → 已通过 → 待发放凭证'
    return '提交申请 → 已通过'
  }
  const flow = row.approvalFlow || []
  const current = flow.find((s) => s.status === 'PENDING' || s.status === 'WAITING') || flow[flow.length - 1]
  return current?.result || current?.step || statusLabel(row.status)
}

function openApplyProgress(row: Subscription) {
  progressDlg.row = row
  progressDlg.visible = true
}

function applyProgressSteps(row: Subscription | null) {
  if (!row) return []
  const st = String(row.status || '').toUpperCase()
  const step = String((row as any).approvalStep || 'PLATFORM').toUpperCase()
  const applyTime = formatDateTime(row.createdAt, '')
  const platformTime = formatDateTime((row as any).platformReviewedAt, '')
  const reviewTime = formatDateTime(row.reviewedAt, '')
  const rejected = st === 'REJECTED'
  const cancelled = st === 'CANCELLED'
  const approved = st === 'APPROVED'
  const hasCred = isCredentialSub(row) && !!(row.oauthClientId || payloadStr(payloadObj(row), 'oauthClientId'))
  const synced = approved && (!isCredentialSub(row) || hasCred)
  const providerName = (row.providerOrg || '').trim() || '目录提供单位'
  const applicantName = (row.applicantOrg || row.createdBy || '').trim() || '申请人'
  const platformName = String((row as any).platformReviewedBy || '').trim() || '平台管理员'
  const deptName = String(row.reviewedBy || '').trim() || providerName
  const flow = row.approvalFlow || []
  const flowActor = (titleHint: string, fallback: string) => {
    const hit = flow.find((f) => String(f.step || '').includes(titleHint) && f.actor)
    return (hit?.actor || fallback).trim()
  }

  // 平台节点
  let platformState: 'done' | 'current' | 'wait' | 'reject' = 'wait'
  let platformHint = '未开始'
  let platformAt = ''
  let platformActor = flowActor('平台', '平台管理员')
  if (cancelled && !(row as any).platformReviewedAt) {
    platformState = 'wait'
    platformHint = '—'
  } else if ((row as any).platformReviewedAt) {
    platformState = 'done'
    platformHint = '已通过'
    platformAt = platformTime
    platformActor = platformName
  } else if (st === 'PENDING' && step === 'PLATFORM') {
    platformState = 'current'
    platformHint = '审批中'
    platformActor = '平台管理员'
  } else if (rejected && step === 'PLATFORM') {
    platformState = 'reject'
    platformHint = '已驳回'
    platformAt = reviewTime
    platformActor = String(row.reviewedBy || '').trim() || '平台管理员'
  } else if (approved || step === 'PROVIDER' || rejected) {
    platformState = 'done'
    platformHint = '已通过'
    platformAt = platformTime || applyTime
    platformActor = platformName
  }

  // 部门节点
  let deptState: 'done' | 'current' | 'wait' | 'reject' = 'wait'
  let deptHint = '未开始'
  let deptAt = ''
  let deptActor = flowActor('部门', providerName)
  if (cancelled) {
    deptHint = '—'
    deptActor = providerName
  } else if (st === 'PENDING' && step === 'PLATFORM') {
    deptState = 'wait'
    deptHint = '未开始'
    deptActor = providerName
  } else if (st === 'PENDING' && step === 'PROVIDER') {
    deptState = 'current'
    deptHint = '审批中'
    deptActor = providerName
  } else if (approved) {
    deptState = 'done'
    deptHint = '已通过'
    deptAt = reviewTime
    deptActor = deptName
  } else if (rejected && step === 'PROVIDER') {
    deptState = 'reject'
    deptHint = '已驳回'
    deptAt = reviewTime
    deptActor = deptName
  } else if (rejected) {
    deptHint = '—'
    deptActor = providerName
  }

  const syncState = synced ? 'done' : approved && !rejected ? 'current' : 'wait'
  return [
    {
      title: '用户申请',
      time: applyTime,
      state: cancelled ? 'wait' : 'done',
      hint: cancelled ? '已取消' : '已提交',
      actor: flowActor('提交', applicantName) || applicantName,
    },
    { title: '平台审核', time: platformAt, state: platformState, hint: platformHint, actor: platformActor },
    { title: '部门审核', time: deptAt, state: deptState, hint: deptHint, actor: deptActor },
    {
      title: '权限同步',
      time: synced ? reviewTime : '',
      state: syncState,
      hint: synced ? '已完成' : syncState === 'current' ? '同步中' : '未开始',
      actor: synced ? (String(row.reviewedBy || '').trim() || '系统') : '系统',
    },
  ]
}

function payloadObj(row: Subscription | null): Record<string, unknown> | null {
  if (!row) return null
  const p = row.applyPayload
  if (p && typeof p === 'object') return p as Record<string, unknown>
  if (typeof p === 'string') {
    try { return JSON.parse(p) as Record<string, unknown> } catch { return null }
  }
  return null
}

function payloadStr(obj: Record<string, unknown> | null, key: string): string {
  if (!obj || obj[key] == null) return ''
  const v = String(obj[key]).trim()
  return v
}

function dash(v?: string | number | null) {
  if (v == null) return '—'
  const s = String(v).trim()
  return s === '' ? '—' : s
}

function isApiSub(row: Subscription | null) {
  return String(row?.resourceType || '').toUpperCase() === 'API'
}

function isTableSub(row: Subscription | null) {
  const t = String(row?.resourceType || '').toUpperCase()
  return t === 'TABLE' || t === 'DATABASE' || t === 'DB' || t === 'DB_SYNC'
}

/** 接口与库表审核后展示同一套 OAuth / 接口信息 */
function isCredentialSub(row: Subscription | null) {
  return isApiSub(row) || isTableSub(row)
}

function payloadParamRows(row: Subscription | null, key: 'inputParams' | 'outputParams'): Record<string, unknown>[] {
  const obj = payloadObj(row)
  const v = obj?.[key]
  return Array.isArray(v) ? (v as Record<string, unknown>[]) : []
}

function hasTableParams(row: Subscription | null) {
  const obj = payloadObj(row)
  return Array.isArray(obj?.inputParams) || Array.isArray(obj?.outputParams)
}

function payloadEntries(row: Subscription | null): { label: string; value: string; section?: string }[] {
  if (!row) return []
  const obj = payloadObj(row)
  if (isCredentialSub(row)) {
    const days = payloadStr(obj, 'useDays')
    return [
      { label: '目录名称', value: dash(row.catalogTitle), section: 'base' },
      { label: '资源名称', value: dash(row.catalogCode), section: 'base' },
      { label: '资源提供方名称', value: dash(row.providerOrg), section: 'base' },
      { label: '联系人', value: dash(payloadStr(obj, 'contactName')), section: 'base' },
      { label: '联系电话', value: dash(payloadStr(obj, 'contactPhone')), section: 'base' },
      { label: '应用系统名称', value: dash(payloadStr(obj, 'systemName')), section: 'api' },
      { label: '接口URL', value: dash(row.apiUrl || payloadStr(obj, 'apiUrl')), section: 'api' },
      { label: '接口请求方式', value: dash(row.apiMethod || payloadStr(obj, 'apiMethod') || 'POST'), section: 'api' },
      { label: '用于Oauth2服务认证的client secret信息', value: dash(row.oauthClientSecret || payloadStr(obj, 'oauthClientSecret')), section: 'api' },
      { label: '用于Oauth2服务认证的clientid信息', value: dash(row.oauthClientId || payloadStr(obj, 'oauthClientId')), section: 'api' },
      { label: '使用时间范围', value: dash(payloadStr(obj, 'timeRange')), section: 'api' },
      { label: '使用期限', value: days ? `${days}天` : '—', section: 'api' },
      { label: '其他技术请求说明', value: dash(payloadStr(obj, 'techReq')), section: 'api' },
      { label: '办事场景', value: dash(payloadStr(obj, 'scene') || payloadStr(obj, 'useScope') || row.purpose), section: 'api' },
      { label: '数据范围', value: dash(payloadStr(obj, 'dataDesc')), section: 'api' },
      { label: '接口调用频次', value: dash(payloadStr(obj, 'callFreq')), section: 'api' },
      { label: '接口峰值频率', value: dash(payloadStr(obj, 'peakFreq')), section: 'api' },
      { label: '申请依据', value: dash(payloadStr(obj, 'applyBasis')), section: 'api' },
    ]
  }
  const base: { label: string; value: string; section?: string }[] = [
    { label: '资源名称', value: String(row.catalogTitle || row.catalogId || '—'), section: 'base' },
    { label: '资源编码', value: String(row.catalogCode || '—'), section: 'base' },
    { label: '共享方式', value: shareLabel(row.resourceType), section: 'base' },
    { label: '申请单位', value: row.applicantOrg || '—', section: 'base' },
    { label: '提供方', value: row.providerOrg || '—', section: 'base' },
    { label: '用途/场景', value: row.purpose || '—', section: 'base' },
    { label: '状态', value: statusLabel(row.status), section: 'base' },
    { label: '申请人', value: row.createdBy || '—', section: 'base' },
    { label: '申请时间', value: formatDateTime(row.createdAt) || '—', section: 'base' },
  ]
  if (row.reviewedBy) base.push({ label: '审批人', value: row.reviewedBy, section: 'base' })
  if (row.reviewerContact) base.push({ label: '联系方式', value: row.reviewerContact, section: 'base' })
  if (row.approverNote) base.push({ label: '审批意见', value: row.approverNote, section: 'base' })
  if (row.taskId) base.push({ label: '交换任务', value: `#${row.taskId} ${row.taskStatus || ''}`, section: 'base' })

  if (obj) {
    const contactMap: Record<string, string> = {
      contactName: '联系人',
      contactPhone: '联系电话',
      contactEmail: '联系邮箱',
    }
    const applyMap: Record<string, string> = {
      scene: '使用办事场景',
      systemName: '应用系统名称',
      timeRange: '使用时间范围',
      callFreq: '接口调用频次',
      peakFreq: '接口峰值频率',
      useDays: '接口使用期限(天)',
      useScope: '使用范围说明',
      dataDesc: '数据描述',
      applyBasis: '申请依据',
      techReq: '其他技术需求',
    }
    for (const [k, label] of Object.entries(contactMap)) {
      if (obj[k] != null && String(obj[k]).trim() !== '') {
        base.push({ label, value: String(obj[k]), section: 'contact' })
      }
    }
    for (const [k, label] of Object.entries(applyMap)) {
      if (obj[k] != null && String(obj[k]).trim() !== '') {
        base.push({ label, value: String(obj[k]), section: 'apply' })
      }
    }
  }
  return base
}

interface PortalMyApp {
  id: number
  appName: string
  contactName: string
  contactPhone: string
  createdAt?: string
  updatedAt?: string
}

const pendingSubs = computed(() => pendingSubsList.value)
const mySubs = computed(() => subscriptions.value)
const reviewedSubs = computed(() => reviewedSubsList.value)
type MyspaceInnerTab = 'mine' | 'apps' | 'pending' | 'reviewed' | 'favorites'
const myspaceInnerTab = ref<MyspaceInnerTab>('mine')
const myspacePage = ref(1)
const myspacePageSize = ref(10)
const pendingPage = ref(1)
const pendingPageSize = ref(10)
const reviewedPage = ref(1)
const reviewedPageSize = ref(10)
const favoritesPage = ref(1)
const favoritesPageSize = ref(10)
const myApps = ref<PortalMyApp[]>([])
const appsPage = ref(1)
const appsPageSize = ref(10)
const appDialogVisible = ref(false)
const appSaving = ref(false)
const appEditingId = ref<number | null>(null)
const appFormRef = ref<FormInstance>()
const appForm = reactive({
  appName: '',
  contactName: '',
  contactPhone: '',
})
const appFormRules: FormRules = {
  appName: [{ required: true, message: '请填写应用系统名称', trigger: 'blur' }],
  contactName: [{ required: true, message: '请填写联系人', trigger: 'blur' }],
  contactPhone: [phoneRule({ required: true, allowLandline: true })],
}

async function loadMyApps() {
  const res = await api.get('/exchange/portal/my-apps')
  myApps.value = Array.isArray(res.data) ? res.data : []
}

async function setMyspaceInnerTab(tab: MyspaceInnerTab) {
  myspaceInnerTab.value = tab
  if (tab === 'favorites') {
    myFavorites.value = await fetchFavorites('PORTAL')
  } else if (tab === 'reviewed') {
    const res = await api.get('/exchange/portal/subscriptions', { params: { scope: 'reviewed' } })
    reviewedSubsList.value = res.data || []
  } else if (tab === 'apps') {
    await loadMyApps()
  }
}
const pagedMySubs = computed(() => {
  const start = (myspacePage.value - 1) * myspacePageSize.value
  return mySubs.value.slice(start, start + myspacePageSize.value)
})
const pagedPendingSubs = computed(() => {
  const start = (pendingPage.value - 1) * pendingPageSize.value
  return pendingSubs.value.slice(start, start + pendingPageSize.value)
})
const pagedReviewedSubs = computed(() => {
  const start = (reviewedPage.value - 1) * reviewedPageSize.value
  return reviewedSubs.value.slice(start, start + reviewedPageSize.value)
})
const pagedFavorites = computed(() => {
  const start = (favoritesPage.value - 1) * favoritesPageSize.value
  return myFavorites.value.slice(start, start + favoritesPageSize.value)
})
const pagedMyApps = computed(() => {
  const start = (appsPage.value - 1) * appsPageSize.value
  return myApps.value.slice(start, start + appsPageSize.value)
})

function openAppCreate() {
  appEditingId.value = null
  appForm.appName = ''
  appForm.contactName = String(auth.user?.displayName || '').trim()
  appForm.contactPhone = ''
  appDialogVisible.value = true
}

function openAppEdit(row: PortalMyApp) {
  appEditingId.value = row.id
  appForm.appName = row.appName
  appForm.contactName = row.contactName
  appForm.contactPhone = row.contactPhone
  appDialogVisible.value = true
}

async function saveMyApp() {
  if (!appFormRef.value) return
  try {
    await appFormRef.value.validate()
  } catch {
    return
  }
  appSaving.value = true
  try {
    const body = {
      appName: appForm.appName.trim(),
      contactName: appForm.contactName.trim(),
      contactPhone: appForm.contactPhone.trim(),
    }
    if (appEditingId.value) {
      await api.put(`/exchange/portal/my-apps/${appEditingId.value}`, body)
      ElMessage.success('应用已更新')
    } else {
      await api.post('/exchange/portal/my-apps', body)
      ElMessage.success('应用已新增')
    }
    appDialogVisible.value = false
    await loadMyApps()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    appSaving.value = false
  }
}

async function deleteMyApp(row: PortalMyApp) {
  try {
    await ElMessageBox.confirm(`确认删除应用「${row.appName}」？删除后不可恢复。`, '删除应用', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await api.delete(`/exchange/portal/my-apps/${row.id}`)
    ElMessage.success('已删除')
    await loadMyApps()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function favoriteShareLabel(f: PortalFavorite) {
  if (f.resourceTypeLabel) return f.resourceTypeLabel
  return shareLabel(f.resourceType)
}

async function cancelFavorite(row: PortalFavorite) {
  await removeFavorite(row.catalogId, 'PORTAL')
  myFavorites.value = await fetchFavorites('PORTAL')
  ElMessage.success('已取消订阅')
}

async function openFavoriteDetail(row: PortalFavorite) {
  pendingCatalogId.value = row.catalogId
  setTab('catalog')
}

// themeTree reserved for future grouping

watch(() => route.query.section, () => {
  syncTab()
  void loadTab().then(() => flushPendingCatalogDetail())
})

watch(
  () => route.query.catalogId,
  (cid) => {
    if (cid == null || cid === '') return
    pendingCatalogId.value = String(cid)
    if (portalTab.value !== 'catalog') {
      setTab('catalog')
    } else {
      void flushPendingCatalogDetail()
    }
  },
)

onMounted(() => {
  syncTab()
  if (route.query.q != null && String(route.query.q) !== '') {
    searchQ.value = String(route.query.q)
  }
  const cid = route.query.catalogId
  if (cid != null && cid !== '') {
    pendingCatalogId.value = String(cid)
    if (String(route.query.section || '') !== 'catalog') {
      portalTab.value = 'catalog'
      router.replace({
        path: '/exchange/analysis-portal/dept',
        query: { ...route.query, section: 'catalog', catalogId: String(cid) },
      })
    }
  }
  void loadTab().then(() => flushPendingCatalogDetail())
})
</script>

<template>
  <div v-loading="loading" class="portal-home">
    <!-- 首页落地（对齐参考图） -->
    <template v-if="portalTab === 'home'">
      <section class="hero">
        <div class="hero__inner">
          <div class="hero__copy">
            <h1 class="hero__title">构建完善的数据资产管理体系，数据汇聚全覆盖</h1>
            <div class="hero__search">
              <input
                v-model="searchQ"
                class="hero__input"
                placeholder="请输入关键词..."
                @keyup.enter="doBannerSearch"
              >
              <button type="button" class="hero__btn" @click="doBannerSearch">搜索</button>
            </div>
            <div v-if="home?.hotKeywords?.length" class="hero__keywords">
              <button
                v-for="kw in home.hotKeywords.slice(0, 8)"
                :key="kw"
                type="button"
                class="hero__kw"
                @click="clickHotKeyword(kw)"
              >{{ kw }}</button>
            </div>
          </div>
          <div class="hero__art" aria-hidden="true">
            <div class="hero__cube" />
            <div class="hero__ring" />
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel__head">
          <h2>政务共享资源</h2>
          <div class="seg">
            <button type="button" :class="{ 'is-on': shareBrowseMode === 'theme' }" @click="shareBrowseMode = 'theme'">主题</button>
            <button type="button" :class="{ 'is-on': shareBrowseMode === 'dept' }" @click="shareBrowseMode = 'dept'">部门</button>
          </div>
        </div>

        <!-- 主题：审批通过且选择了「主题资源目录」的资源 -->
        <div v-if="shareBrowseMode === 'theme'" class="theme-grid">
          <button type="button" class="theme-hero" @click="setTab('catalog')">
            <div class="theme-hero__title">主题信息资源</div>
            <div class="theme-hero__metrics">
              <div><span>接口</span><b class="c-cyan">{{ themeApiTotal || home?.apiServiceTotal || 0 }}</b></div>
              <div><span>库表</span><b class="c-amber">{{ themeDataTotal || home?.openResourceTotal || 0 }}</b></div>
            </div>
          </button>
          <button
            v-for="t in home?.themes || []"
            :key="t.code"
            type="button"
            class="theme-card"
            @click="openTheme(t.code)"
          >
            <div class="theme-card__top">
              <span class="theme-card__icon" aria-hidden="true" />
              <span class="theme-card__name">{{ t.name }}</span>
              <span class="theme-card__chev">›</span>
            </div>
            <div class="theme-card__nums">
              <span>接口 <b class="c-green">{{ t.apiCount ?? 0 }}</b></span>
              <span>库表 <b class="c-amber">{{ t.dataCount ?? t.count ?? 0 }}</b></span>
            </div>
          </button>
          <div v-if="!(home?.themes?.length)" class="theme-empty">暂无已发布的主题资源目录</div>
        </div>
        <!-- 部门：审批通过且选择了组织机构（提供方）的资源 -->
        <div v-else class="theme-grid">
          <button type="button" class="theme-hero" @click="setTab('catalog')">
            <div class="theme-hero__title">部门信息资源</div>
            <div class="theme-hero__metrics">
              <div><span>接口</span><b class="c-cyan">{{ deptApiTotal || home?.apiServiceTotal || 0 }}</b></div>
              <div><span>库表</span><b class="c-amber">{{ deptDataTotal || home?.openResourceTotal || 0 }}</b></div>
            </div>
          </button>
          <button
            v-for="p in home?.providers || []"
            :key="p.name"
            type="button"
            class="theme-card"
            @click="openProvider(p.name)"
          >
            <div class="theme-card__top">
              <span class="theme-card__icon" aria-hidden="true" />
              <span class="theme-card__name">{{ p.name }}</span>
              <span class="theme-card__chev">›</span>
            </div>
            <div class="theme-card__nums">
              <span>接口 <b class="c-green">{{ p.apiCount ?? 0 }}</b></span>
              <span>库表 <b class="c-amber">{{ p.dataCount ?? p.count ?? 0 }}</b></span>
            </div>
          </button>
          <div v-if="!(home?.providers?.length)" class="theme-empty">暂无已发布的部门资源目录</div>
        </div>
      </section>

      <section class="panel">
        <div class="panel__head">
          <h2>资源动态</h2>
        </div>
        <div class="dual">
          <div class="dual__col">
            <div class="dual__caption dual__caption--blue">
              <div>
                <h3>最新资源</h3>
              </div>
              <button type="button" class="more more--light" @click="catalogKind=''; setTab('catalog')">更多</button>
            </div>
            <ul class="res-list">
              <li v-for="r in latestResources" :key="'latest-' + String(r.id)">
                <button type="button" @click="openShareDetail(r)">
                  <span class="res-list__main">
                    <span class="res-list__title">{{ r.title }}</span>
                    <span class="res-list__meta">来源: {{ r.providerOrg || '—' }}</span>
                  </span>
                  <span class="res-list__views">访问量: {{ r.visitCount ?? r.hotScore ?? 0 }}次</span>
                </button>
              </li>
              <li v-if="!latestResources.length" class="empty">暂无最新资源</li>
            </ul>
          </div>
          <div class="dual__col">
            <div class="dual__caption dual__caption--blue">
              <div>
                <h3>最热资源</h3>
              </div>
              <button type="button" class="more more--light" @click="catalogKind=''; setTab('catalog')">更多</button>
            </div>
            <ul class="res-list">
              <li v-for="r in hotResources" :key="'hot-' + String(r.id)">
                <button type="button" @click="openShareDetail(r)">
                  <span class="res-list__main">
                    <span class="res-list__title">{{ r.title }}</span>
                    <span class="res-list__meta">来源: {{ r.providerOrg || '—' }}</span>
                  </span>
                  <span class="res-list__views">访问量: {{ r.visitCount ?? r.hotScore ?? 0 }}次</span>
                </button>
              </li>
              <li v-if="!hotResources.length" class="empty">暂无最热资源</li>
            </ul>
          </div>
        </div>
      </section>

      <footer class="portal-foot">
        <div>承德市大数据归集平台 · {{ DEPT_PORTAL_BRAND }}</div>
        <div>建议使用 Chrome / Edge 浏览器访问 · V1.0</div>
      </footer>
    </template>

    <!-- 共享资源 / 其他业务区 -->
    <div v-else class="portal-body">
      <div v-if="portalTab === 'catalog'" class="catalog-shell">
        <ShareCatalogPanel
          ref="shareCatalogRef"
          :themes="home?.themes || []"
          :providers="home?.providers || []"
          :initial-keyword="searchQ"
          @submitted="loadSubscriptions"
        />
      </div>

      <div v-else-if="portalTab === 'subscribe'" class="catalog-shell">
        <SubscribeApplyPanel
          :themes="home?.themes || []"
          :base-libraries="home?.baseLibraries || []"
          :applicant-org="subForm.applicantOrg"
          @submitted="loadSubscriptions"
        />
      </div>

      <div v-else-if="portalTab === 'myspace'" class="myspace">
        <header class="myspace__hero">
          <div>
            <h1>个人空间</h1>
          </div>
          <div class="myspace__stats">
            <button
              type="button"
              class="myspace-stat"
              :class="{ 'is-active': myspaceInnerTab === 'mine' }"
              @click="setMyspaceInnerTab('mine')"
            >
              <b>{{ mySubs.length }}</b>
              <span>我的申请</span>
            </button>
            <button
              type="button"
              class="myspace-stat"
              :class="{ 'is-active': myspaceInnerTab === 'apps' }"
              @click="setMyspaceInnerTab('apps')"
            >
              <b>{{ myApps.length }}</b>
              <span>我的应用</span>
            </button>
            <button
              type="button"
              class="myspace-stat"
              :class="{ 'is-active': myspaceInnerTab === 'pending' }"
              @click="setMyspaceInnerTab('pending')"
            >
              <b>{{ pendingSubs.length }}</b>
              <span>待我审批</span>
            </button>
            <button
              type="button"
              class="myspace-stat"
              :class="{ 'is-active': myspaceInnerTab === 'reviewed' }"
              @click="setMyspaceInnerTab('reviewed')"
            >
              <b>{{ reviewedSubs.length }}</b>
              <span>已审批</span>
            </button>
            <button
              type="button"
              class="myspace-stat"
              :class="{ 'is-active': myspaceInnerTab === 'favorites' }"
              @click="setMyspaceInnerTab('favorites')"
            >
              <b>{{ myFavorites.length }}</b>
              <span>我的订阅</span>
            </button>
          </div>
        </header>

        <div class="myspace-card myspace-card--wide">
          <el-tabs v-model="myspaceInnerTab" class="myspace-tabs">
            <el-tab-pane name="mine">
              <template #label>
                <span>我的申请</span>
                <span v-if="mySubs.length" class="myspace-tab-count">{{ mySubs.length }}</span>
              </template>
              <el-table
                :data="pagedMySubs"
                stripe
                class="clickable-table"
                @row-click="(row: Subscription) => openSubDetail(row, 'mine')"
              >
                <el-table-column label="资源" min-width="180">
                  <template #default="{ row }">
                    <button type="button" class="link-title" @click.stop="openSubDetail(row, 'mine')">
                      {{ row.catalogTitle || row.catalogId }}
                    </button>
                  </template>
                </el-table-column>
                <el-table-column label="共享方式" width="110">
                  <template #default="{ row }">{{ shareLabel(row.resourceType) }}</template>
                </el-table-column>
                <el-table-column label="申请单位" width="140" prop="applicantOrg" />
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="审批进度" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">{{ approvalProgressText(row) }}</template>
                </el-table-column>
                <el-table-column label="申请时间" width="170">
                  <template #default="{ row }">
                    {{ row.createdAt ? String(row.createdAt).replace('T', ' ').slice(0, 19) : '—' }}
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" type="primary" plain @click.stop="openApplyProgress(row)">审批进度</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <PortalPagination
                v-if="mySubs.length"
                v-model:page="myspacePage"
                v-model:page-size="myspacePageSize"
                :total="mySubs.length"
              />
            </el-tab-pane>

            <el-tab-pane name="apps">
              <template #label>
                <span>我的应用</span>
                <span v-if="myApps.length" class="myspace-tab-count">{{ myApps.length }}</span>
              </template>
              <div class="myspace-apps-toolbar">
                <el-button type="primary" @click="openAppCreate">新增</el-button>
              </div>
              <el-empty v-if="!myApps.length" description="暂无应用，点击「新增」登记应用系统" :image-size="72" />
              <template v-else>
                <el-table :data="pagedMyApps" stripe border class="portal-table">
                  <el-table-column
                    type="index"
                    label="序号"
                    width="70"
                    :index="(i: number) => (appsPage - 1) * appsPageSize + i + 1"
                  />
                  <el-table-column prop="appName" label="应用系统名称" min-width="200" show-overflow-tooltip />
                  <el-table-column prop="contactName" label="联系人" width="120" show-overflow-tooltip />
                  <el-table-column prop="contactPhone" label="联系电话" width="140" />
                  <el-table-column label="创建时间" width="170">
                    <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="140" fixed="right">
                    <template #default="{ row }">
                      <el-button link type="primary" @click="openAppEdit(row)">编辑</el-button>
                      <el-button link type="danger" @click="deleteMyApp(row)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <PortalPagination
                  v-model:page="appsPage"
                  v-model:page-size="appsPageSize"
                  :total="myApps.length"
                />
              </template>
            </el-tab-pane>

            <el-tab-pane name="pending">
              <template #label>
                <span>待我审批</span>
                <span v-if="pendingSubs.length" class="myspace-badge myspace-badge--tab">{{ pendingSubs.length }}</span>
              </template>
              <el-empty v-if="!pendingSubs.length" description="暂无待审批申请" :image-size="72" />
              <template v-else>
                <el-table
                  :data="pagedPendingSubs"
                  stripe
                  class="clickable-table"
                  @row-click="(row: Subscription) => openSubDetail(row, 'pending')"
                >
                  <el-table-column label="资源" min-width="180">
                    <template #default="{ row }">
                      <button type="button" class="link-title" @click.stop="openSubDetail(row, 'pending')">
                        {{ row.catalogTitle || row.catalogId }}
                      </button>
                    </template>
                  </el-table-column>
                  <el-table-column prop="applicantOrg" label="申请单位" width="160" />
                  <el-table-column label="共享方式" width="110">
                    <template #default="{ row }">{{ shareLabel(row.resourceType) }}</template>
                  </el-table-column>
                  <el-table-column prop="purpose" label="用途" min-width="140" show-overflow-tooltip />
                  <el-table-column label="审批进度" min-width="200" show-overflow-tooltip>
                    <template #default="{ row }">{{ approvalProgressText(row) }}</template>
                  </el-table-column>
                  <el-table-column label="申请时间" width="170">
                    <template #default="{ row }">
                      {{ row.createdAt ? String(row.createdAt).replace('T', ' ').slice(0, 19) : '—' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="260" fixed="right">
                    <template #default="{ row }">
                      <el-button size="small" type="primary" plain @click.stop="openApplyProgress(row)">审批进度</el-button>
                      <el-button link type="primary" @click.stop="openSubDetail(row, 'reviewed')">详情</el-button>
                      <el-button
                        v-if="row.canApprove !== false"
                        link
                        type="primary"
                        @click.stop="openSubDetail(row, 'pending')"
                      >审核</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <PortalPagination
                  v-model:page="pendingPage"
                  v-model:page-size="pendingPageSize"
                  :total="pendingSubs.length"
                />
              </template>
            </el-tab-pane>

            <el-tab-pane name="reviewed">
              <template #label>
                <span>已审批</span>
                <span v-if="reviewedSubs.length" class="myspace-tab-count">{{ reviewedSubs.length }}</span>
              </template>
              <el-empty v-if="!reviewedSubs.length" description="暂无审批历史" :image-size="72" />
              <template v-else>
                <el-table
                  :data="pagedReviewedSubs"
                  stripe
                  class="clickable-table"
                  @row-click="(row: Subscription) => openSubDetail(row, 'reviewed')"
                >
                  <el-table-column label="资源" min-width="180">
                    <template #default="{ row }">
                      <button type="button" class="link-title" @click.stop="openSubDetail(row, 'reviewed')">
                        {{ row.catalogTitle || row.catalogId }}
                      </button>
                    </template>
                  </el-table-column>
                  <el-table-column prop="applicantOrg" label="申请单位" width="150" show-overflow-tooltip />
                  <el-table-column label="共享方式" width="110">
                    <template #default="{ row }">{{ shareLabel(row.resourceType) }}</template>
                  </el-table-column>
                  <el-table-column label="审批结果" width="100">
                    <template #default="{ row }">
                      <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="审批进度" min-width="200" show-overflow-tooltip>
                    <template #default="{ row }">{{ approvalProgressText(row) }}</template>
                  </el-table-column>
                  <el-table-column label="审批人" width="110" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.reviewedBy || '—' }}</template>
                  </el-table-column>
                  <el-table-column label="审批时间" width="170">
                    <template #default="{ row }">{{ fmtFlowTime(row.reviewedAt) }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="200" fixed="right">
                    <template #default="{ row }">
                      <el-button size="small" type="primary" plain @click.stop="openApplyProgress(row)">审批进度</el-button>
                      <el-button link type="primary" @click.stop="openSubDetail(row, 'reviewed')">详情</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <PortalPagination
                  v-model:page="reviewedPage"
                  v-model:page-size="reviewedPageSize"
                  :total="reviewedSubs.length"
                />
              </template>
            </el-tab-pane>

            <el-tab-pane name="favorites">
              <template #label>
                <span>我的订阅</span>
                <span v-if="myFavorites.length" class="myspace-tab-count">{{ myFavorites.length }}</span>
              </template>
              <el-empty v-if="!myFavorites.length" description="暂无订阅，可在资源详情页点击「订阅」收藏" :image-size="72" />
              <template v-else>
                <el-table :data="pagedFavorites" stripe class="clickable-table">
                  <el-table-column label="资源" min-width="180">
                    <template #default="{ row }">
                      <button type="button" class="link-title" @click.stop="openFavoriteDetail(row)">
                        {{ row.title || row.catalogId }}
                      </button>
                    </template>
                  </el-table-column>
                  <el-table-column label="提供方" width="140" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.providerOrg || '—' }}</template>
                  </el-table-column>
                  <el-table-column label="类型" width="100">
                    <template #default="{ row }">{{ favoriteShareLabel(row) }}</template>
                  </el-table-column>
                  <el-table-column label="订阅时间" width="170">
                    <template #default="{ row }">
                      {{ row.followedAt ? String(row.followedAt).replace('T', ' ').slice(0, 19) : '—' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="160" fixed="right">
                    <template #default="{ row }">
                      <el-button link type="primary" @click.stop="openFavoriteDetail(row)">查看</el-button>
                      <el-button link type="danger" @click.stop="cancelFavorite(row)">取消订阅</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <PortalPagination
                  v-model:page="favoritesPage"
                  v-model:page-size="favoritesPageSize"
                  :total="myFavorites.length"
                />
              </template>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </div>

    <el-drawer
      v-model="subDetail.visible"
      :title="subDetail.mode === 'mine' ? '申请详情' : '审批详情'"
      size="640px"
    >
      <template v-if="subDetail.row">
        <section class="detail-block">
          <h4>{{ isCredentialSub(subDetail.row) ? '基本信息' : '资源与办理' }}</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item v-for="(it, i) in payloadEntries(subDetail.row).filter(e => !e.section || e.section === 'base')" :key="'b'+i" :label="it.label">
              {{ it.value }}
            </el-descriptions-item>
          </el-descriptions>
        </section>
        <section v-if="payloadEntries(subDetail.row).some(e => e.section === 'api')" class="detail-block">
          <h4>接口信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item v-for="(it, i) in payloadEntries(subDetail.row).filter(e => e.section === 'api')" :key="'i'+i" :label="it.label">
              {{ it.value }}
            </el-descriptions-item>
          </el-descriptions>
        </section>
        <section v-if="payloadEntries(subDetail.row).some(e => e.section === 'contact')" class="detail-block">
          <h4>申请方信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item v-for="(it, i) in payloadEntries(subDetail.row).filter(e => e.section === 'contact')" :key="'c'+i" :label="it.label">
              {{ it.value }}
            </el-descriptions-item>
          </el-descriptions>
        </section>
        <section v-if="payloadEntries(subDetail.row).some(e => e.section === 'apply')" class="detail-block">
          <h4>申请内容</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item v-for="(it, i) in payloadEntries(subDetail.row).filter(e => e.section === 'apply')" :key="'a'+i" :label="it.label">
              {{ it.value }}
            </el-descriptions-item>
          </el-descriptions>
        </section>
        <section v-if="hasTableParams(subDetail.row)" class="detail-block">
          <h4>库表入参 / 出参</h4>
          <div class="detail-section-title">入参</div>
          <el-table
            :data="payloadParamRows(subDetail.row, 'inputParams')"
            size="small"
            stripe
            border
            empty-text="编目未勾选搜索项"
            class="detail-table"
          >
            <el-table-column prop="name" label="字段名称" min-width="120" />
            <el-table-column prop="comment" label="中文名称" min-width="120" />
            <el-table-column prop="type" label="字段类型" width="110" />
            <el-table-column prop="length" label="字段长度" width="90" />
          </el-table>
          <div class="detail-section-title">出参</div>
          <el-table
            :data="payloadParamRows(subDetail.row, 'outputParams')"
            size="small"
            stripe
            border
            empty-text="无"
            class="detail-table"
          >
            <el-table-column prop="name" label="字段名称" min-width="120" />
            <el-table-column prop="comment" label="中文名称" min-width="120" />
            <el-table-column prop="type" label="字段类型" width="110" />
            <el-table-column prop="length" label="字段长度" width="90" />
          </el-table>
        </section>
        <section class="detail-block">
          <h4>审批流程</h4>
          <el-table
            :data="subDetail.row.approvalFlow || []"
            size="small"
            stripe
            border
            empty-text="暂无审批记录"
          >
            <el-table-column prop="step" label="环节" width="110" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="flowStatusTag(row.status)">{{ row.result || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处理人" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.actor || '—' }}</template>
            </el-table-column>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ fmtFlowTime(row.time) }}</template>
            </el-table-column>
            <el-table-column prop="comment" label="结果/意见" min-width="120" show-overflow-tooltip />
          </el-table>
        </section>
        <div v-if="subDetail.mode === 'pending' && subDetail.row.status === 'PENDING' && subDetail.row.canApprove !== false" class="sub-detail-ops">
          <el-form label-width="88px" class="review-meta-form" @submit.prevent>
            <el-form-item label="审批人" required>
              <el-input v-model="reviewForm.reviewerName" maxlength="64" placeholder="请填写审批人" clearable />
            </el-form-item>
            <el-form-item label="联系方式" required>
              <el-input v-model="reviewForm.reviewerContact" maxlength="64" placeholder="请填写手机号或固话" clearable />
            </el-form-item>
            <el-form-item label="审批意见">
              <el-input
                v-model="reviewForm.note"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="通过可填同意；驳回时必须填写驳回意见"
              />
            </el-form-item>
          </el-form>
          <el-button type="success" @click="reviewSub(subDetail.row.id, 'APPROVE')">通过</el-button>
          <el-button type="danger" @click="reviewSub(subDetail.row.id, 'REJECT')">驳回</el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="preview.visible" title="资源预览" size="420px">
      <template v-if="preview.row">
        <h3>{{ preview.row.title }}</h3>
        <p class="muted">{{ preview.row.description }}</p>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item v-for="(it, i) in preview.row.previewItems || []" :key="i" :label="it.label">{{ it.value }}</el-descriptions-item>
        </el-descriptions>
        <el-button type="primary" style="margin-top:12px" @click="applyFromCatalog(preview.row!)">申请该资源</el-button>
      </template>
    </el-drawer>

    <el-dialog
      v-model="appDialogVisible"
      :title="appEditingId ? '编辑应用' : '新增应用'"
      width="480px"
      destroy-on-close
      align-center
    >
      <el-form
        ref="appFormRef"
        :model="appForm"
        :rules="appFormRules"
        label-width="120px"
      >
        <el-form-item label="应用系统名称" prop="appName">
          <el-input v-model="appForm.appName" maxlength="128" placeholder="请输入应用系统名称" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="appForm.contactName" maxlength="64" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="appForm.contactPhone" maxlength="32" placeholder="手机号或座机" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="appSaving" @click="saveMyApp">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="progressDlg.visible"
      width="440px"
      align-center
      append-to-body
      class="apply-progress-dlg"
    >
      <template #header>
        <div class="apply-progress-dlg__title">申请进度</div>
      </template>
      <ol class="apply-progress">
        <li
          v-for="s in applyProgressSteps(progressDlg.row)"
          :key="s.title"
          class="apply-progress__item"
          :class="`is-${s.state}`"
        >
          <span class="apply-progress__dot" />
          <div class="apply-progress__body">
            <div class="apply-progress__head">
              <span class="apply-progress__name">{{ s.title }}</span>
              <span class="apply-progress__tag">{{ s.hint }}</span>
            </div>
            <div v-if="s.actor" class="apply-progress__actor">{{ s.actor }}</div>
            <div v-if="s.time" class="apply-progress__time">{{ s.time }}</div>
          </div>
        </li>
      </ol>
      <template #footer>
        <el-button type="primary" @click="progressDlg.visible = false">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.portal-home { background: #f3f6fb; min-height: 100%; }
.portal-body { max-width: 1280px; margin: 0 auto; padding: 16px 20px 28px; }
.catalog-shell { max-width: 1280px; margin: 0 auto; padding: 12px 16px 28px; background: transparent; }

.myspace {
  max-width: 1280px;
  margin: 0 auto;
  padding: 12px 16px 32px;
}
.myspace__hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
  padding: 22px 24px;
  margin-bottom: 16px;
  border-radius: 10px;
  background: linear-gradient(105deg, #eef5ff 0%, #f7fbff 55%, #e8f3ff 100%);
  border: 1px solid #e8edf5;
}
.myspace__hero h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
}
.myspace__stats {
  display: flex;
  gap: 28px;
  flex-wrap: wrap;
}
.myspace-apps-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.myspace-stat {
  text-align: center;
  min-width: 72px;
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.15s ease;
}
.myspace-stat:hover,
.myspace-stat.is-active {
  background: rgba(22, 119, 255, 0.08);
}
.myspace-stat b {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: #1677ff;
  line-height: 1.1;
}
.myspace-stat span {
  font-size: 12px;
  color: #909399;
}
.myspace-card {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 10px;
  padding: 8px 18px 18px;
  box-shadow: 0 1px 4px rgba(15, 40, 80, 0.04);
  min-height: auto;
}
.myspace-card--wide {
  min-height: auto;
}
.myspace-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.myspace-tabs :deep(.el-tabs__item) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.myspace-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.myspace-tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #eef5ff;
  color: #1677ff;
  font-size: 11px;
  font-weight: 600;
}
.myspace-badge,
.myspace-badge--tab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: #1677ff;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.myspace-badge--tab {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  font-size: 11px;
}
.link-title {
  border: 0;
  background: transparent;
  color: #1677ff;
  cursor: pointer;
  padding: 0;
  font-size: 13px;
  text-align: left;
}
.link-title:hover { text-decoration: underline; }
.clickable-table :deep(tbody tr) { cursor: pointer; }
.sub-detail-ops { margin-top: 16px; }
.detail-block { margin-bottom: 16px; }
.detail-block h4 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
}
.detail-section-title {
  margin: 12px 0 8px;
  font-size: 13px;
  font-weight: 600;
}
.detail-table {
  width: 100%;
  margin-bottom: 8px;
}
.muted { color: #c0c4cc; }

@media (max-width: 960px) {
  .myspace__stats { gap: 16px; }
}

.hero {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 78% 45%, rgba(120, 200, 255, 0.28), transparent 42%),
    linear-gradient(115deg, #0a4ea8 0%, #1677ff 42%, #0d47a1 100%);
  color: #fff;
  padding: 56px 20px 48px;
}
.hero__inner {
  max-width: 1280px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(220px, 0.85fr);
  gap: 24px;
  align-items: center;
}
.hero__brand {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.08em;
  opacity: 0.92;
  margin-bottom: 10px;
}
.hero__title {
  margin: 0 0 22px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.02em;
  max-width: 640px;
  line-height: 1.4;
}
.hero__search {
  display: flex;
  max-width: 560px;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 40, 100, 0.25);
}
.hero__input {
  flex: 1;
  border: 0;
  outline: none;
  height: 46px;
  padding: 0 16px;
  font-size: 15px;
  color: #1f2937;
}
.hero__btn {
  border: 0;
  background: #0b3d91;
  color: #fff;
  font-weight: 600;
  padding: 0 26px;
  cursor: pointer;
  font-size: 15px;
}
.hero__btn:hover { background: #083070; }
.hero__keywords { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.hero__kw {
  border: 1px solid rgba(255,255,255,0.35);
  background: rgba(255,255,255,0.12);
  color: #fff;
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
}
.hero__art {
  position: relative;
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero__cube {
  width: 120px;
  height: 120px;
  border-radius: 18px;
  background:
    linear-gradient(145deg, rgba(255,255,255,0.55), rgba(255,255,255,0.08)),
    linear-gradient(135deg, #4fc3f7, #1565c0);
  box-shadow: 0 18px 40px rgba(0, 30, 80, 0.35);
  transform: rotate(18deg) skewY(-6deg);
}
.hero__ring {
  position: absolute;
  width: 210px;
  height: 210px;
  border-radius: 50%;
  border: 2px solid rgba(255,255,255,0.28);
  box-shadow: inset 0 0 40px rgba(127, 210, 255, 0.25);
}

.panel {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 20px 8px;
}
.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.panel__head h2 {
  margin: 0;
  font-size: 18px;
  color: #1f2937;
  position: relative;
  padding-left: 12px;
}
.panel__head h2::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 4px;
  border-radius: 2px;
  background: #1677ff;
}
.panel__en {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.08em;
  color: #9ca3af;
  vertical-align: middle;
}
.seg {
  display: inline-flex;
  background: #e8eef8;
  border-radius: 6px;
  padding: 3px;
  gap: 2px;
}
.seg button {
  border: 0;
  background: transparent;
  padding: 6px 14px;
  border-radius: 4px;
  cursor: pointer;
  color: #4b5563;
  font-size: 13px;
}
.seg button.is-on {
  background: #fff;
  color: #1677ff;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}
.more {
  border: 0;
  background: transparent;
  color: #1677ff;
  cursor: pointer;
  font-size: 13px;
}

.theme-grid {
  display: grid;
  grid-template-columns: 1.15fr repeat(3, 1fr);
  gap: 12px;
}
.theme-grid--dept {
  grid-template-columns: repeat(4, 1fr);
}
.theme-empty {
  grid-column: 1 / -1;
  padding: 28px 16px;
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  background: #fff;
  border: 1px dashed #dbe3f0;
  border-radius: 10px;
}
.theme-hero {
  grid-row: span 2;
  min-height: 220px;
  border: 0;
  border-radius: 10px;
  padding: 24px;
  text-align: left;
  cursor: pointer;
  color: #fff;
  background:
    linear-gradient(160deg, rgba(8, 46, 110, 0.55), rgba(8, 46, 110, 0.9)),
    linear-gradient(135deg, #0d47a1, #42a5f5);
}
.theme-hero__title { font-size: 22px; font-weight: 700; margin-bottom: 28px; }
.theme-hero__metrics {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.theme-hero__metrics span { display: block; font-size: 13px; opacity: 0.9; margin-bottom: 4px; }
.theme-hero__metrics b { font-size: 32px; line-height: 1; font-weight: 700; }
.c-cyan { color: #26e0ff; }
.c-amber { color: #ffb74d; }
.c-green { color: #26a69a; }
.theme-card {
  border: 1px solid #e5eaf2;
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  min-height: 104px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: box-shadow 150ms ease, transform 150ms ease;
}
.theme-card:hover {
  box-shadow: 0 6px 18px rgba(22, 119, 255, 0.12);
  transform: translateY(-1px);
}
.theme-card__top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.theme-card__icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  flex-shrink: 0;
  background:
    radial-gradient(circle at 50% 50%, #fff 0 2px, transparent 3px),
    linear-gradient(135deg, #42a5f5, #1565c0);
  box-shadow: 0 0 0 1px rgba(21, 101, 192, 0.15);
}
.theme-card__name {
  flex: 1;
  font-weight: 600;
  color: #1f2937;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.theme-card__chev { color: #9ca3af; font-size: 18px; line-height: 1; }
.theme-card__nums {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  font-size: 12px;
  color: #6b7280;
  padding-left: 38px;
}
.theme-card__nums .c-amber { color: #fb8c00; }
.theme-card__nums .c-green { color: #26a69a; }

.dual {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding-bottom: 12px;
}
.dual__col {
  background: #fff;
  border: 1px solid #e5eaf2;
  border-radius: 10px;
  overflow: hidden;
}
.dual__caption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #eef5ff;
  border-bottom: 1px solid #dde7f5;
}
.dual__caption--blue {
  background: linear-gradient(90deg, #0d47a1, #1976d2);
  border-bottom: 0;
  color: #fff;
}
.dual__caption--blue h3 { color: #fff; }
.dual__en {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  opacity: 0.75;
  letter-spacing: 0.04em;
}
.more--light { color: rgba(255,255,255,0.92); }
.dual__caption h3 {
  margin: 0;
  font-size: 15px;
  color: #1f2937;
}
.feed-list { list-style: none; margin: 0; padding: 6px 12px 10px; }
.feed-list li + li { border-top: 1px solid #f0f3f8; }
.feed-list button,
.feed-list__static {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 11px 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
}
.feed-list__static { cursor: default; }
.feed-list__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1677ff;
  flex-shrink: 0;
}
.feed-list__dot--amber { background: #fb8c00; }
.feed-list__title {
  flex: 1;
  min-width: 0;
  color: #1f2937;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.feed-list__date,
.feed-list__static em {
  flex-shrink: 0;
  color: #9ca3af;
  font-size: 12px;
  font-style: normal;
}
.feed-list__static span {
  flex: 1;
  color: #1f2937;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.feed-list { list-style: none; margin: 0; padding: 6px 12px 10px; }
.feed-list li + li { border-top: 1px solid #f0f3f8; }
.feed-list button,
.feed-list__static {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 11px 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
}
.feed-list__static { cursor: default; }
.feed-list__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1677ff;
  flex-shrink: 0;
}
.feed-list__dot--amber { background: #fb8c00; }
.feed-list__title {
  flex: 1;
  min-width: 0;
  color: #1f2937;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.feed-list__date,
.feed-list__static em {
  flex-shrink: 0;
  color: #9ca3af;
  font-size: 12px;
  font-style: normal;
}
.feed-list__static span {
  flex: 1;
  color: #1f2937;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.res-list { list-style: none; margin: 0; padding: 4px 12px 8px; }
.res-list li + li { border-top: 1px solid #f0f3f8; }
.res-list button {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 12px 4px;
  cursor: pointer;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.res-list__main { min-width: 0; }
.res-list__title { display: block; color: #1f2937; font-size: 14px; font-weight: 500; }
.res-list__meta { display: block; margin-top: 4px; color: #9ca3af; font-size: 12px; }
.res-list__views { flex-shrink: 0; color: #9ca3af; font-size: 12px; padding-top: 2px; }
.empty { padding: 20px; color: #9ca3af; font-size: 13px; }

.portal-foot {
  margin: 24px 0 0;
  padding: 22px 20px 28px;
  text-align: center;
  background: #0b3a7a;
  color: rgba(255,255,255,0.82);
  font-size: 12px;
  line-height: 1.8;
}

.filter-panel h4 { margin: 12px 0 8px; font-size: 13px; }
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.res-card { margin-bottom: 12px; min-height: 140px; }
.card-code { font-size: 12px; color: #909399; }
.card-title { font-weight: 600; margin: 6px 0; }
.card-desc { font-size: 13px; color: #606266; min-height: 36px; }
.supply-hint { border: 1px solid var(--el-color-warning); }
.supply-embed { margin-top: 8px; }
.muted { color: #909399; font-size: 13px; }
.subscribe-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.subscribe-head__title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.supply-dialog-body {
  max-height: calc(92vh - 140px);
  overflow: auto;
  padding-right: 4px;
}

@media (max-width: 960px) {
  .hero__inner { grid-template-columns: 1fr; }
  .hero__art { display: none; }
  .theme-grid,
  .theme-grid--dept { grid-template-columns: 1fr 1fr; }
  .theme-hero { grid-row: auto; }
  .dual,
  .info-grid { grid-template-columns: 1fr; }
  .hero__title { font-size: 22px; }
}
</style>

<style>
.apply-progress-dlg.el-dialog {
  border-radius: 8px;
}
.apply-progress-dlg .el-dialog__header {
  margin-right: 0;
  padding: 16px 20px 12px;
  border-bottom: 1px solid #e5e6eb;
}
.apply-progress-dlg .el-dialog__body {
  padding: 22px 28px 8px;
}
.apply-progress-dlg .el-dialog__footer {
  padding: 8px 20px 16px;
}
.apply-progress-dlg__title {
  padding-left: 10px;
  border-left: 3px solid #1677ff;
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
  line-height: 1.25;
}
.apply-progress {
  list-style: none;
  margin: 0;
  padding: 4px 4px 4px 8px;
}
.apply-progress__item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding-bottom: 28px;
}
.apply-progress__item:last-child {
  padding-bottom: 4px;
}
.apply-progress__item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 9px;
  top: 22px;
  bottom: 2px;
  width: 2px;
  background: #e5e6eb;
}
.apply-progress__item.is-done:not(:last-child)::before {
  background: #00b42a;
}
.apply-progress__dot {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  box-sizing: border-box;
  border-radius: 50%;
  border: 2px solid #c9cdd4;
  background: #fff;
}
.apply-progress__item.is-done .apply-progress__dot {
  border-color: #00b42a;
  background: #00b42a;
}
.apply-progress__item.is-done .apply-progress__dot::after {
  content: '';
  position: absolute;
  left: 5px;
  top: 2px;
  width: 5px;
  height: 9px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}
.apply-progress__item.is-current .apply-progress__dot {
  border-color: #1677ff;
  background: #1677ff;
  box-shadow: 0 0 0 4px rgba(22, 119, 255, 0.18);
}
.apply-progress__item.is-reject .apply-progress__dot {
  border-color: #f53f3f;
  background: #f53f3f;
}
.apply-progress__item.is-reject .apply-progress__dot::after,
.apply-progress__item.is-reject .apply-progress__dot::before {
  content: '';
  position: absolute;
  left: 8px;
  top: 3px;
  width: 2px;
  height: 10px;
  background: #fff;
  border-radius: 1px;
}
.apply-progress__item.is-reject .apply-progress__dot::before {
  transform: rotate(45deg);
}
.apply-progress__item.is-reject .apply-progress__dot::after {
  transform: rotate(-45deg);
}
.apply-progress__body {
  min-width: 0;
  flex: 1;
  padding-top: 0;
}
.apply-progress__head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
}
.apply-progress__name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  line-height: 20px;
}
.apply-progress__item.is-wait .apply-progress__name {
  font-weight: 400;
  color: #86909c;
}
.apply-progress__item.is-current .apply-progress__name {
  color: #1677ff;
}
.apply-progress__item.is-reject .apply-progress__name {
  color: #f53f3f;
}
.apply-progress__tag {
  font-size: 12px;
  line-height: 20px;
  padding: 0 6px;
  border-radius: 4px;
  background: #f2f3f5;
  color: #86909c;
}
.apply-progress__item.is-done .apply-progress__tag {
  background: #e8ffea;
  color: #00b42a;
}
.apply-progress__item.is-current .apply-progress__tag {
  background: #e8f3ff;
  color: #1677ff;
}
.apply-progress__item.is-reject .apply-progress__tag {
  background: #ffece8;
  color: #f53f3f;
}
.apply-progress__actor {
  margin-top: 4px;
  font-size: 13px;
  color: #4e5969;
  line-height: 18px;
}
.apply-progress__item.is-wait .apply-progress__actor {
  color: #86909c;
}
.apply-progress__time {
  margin-top: 4px;
  font-size: 12px;
  color: #86909c;
  line-height: 18px;
}
</style>
