<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'
import { useAuthStore } from '@/stores/auth'
import {
  fetchFavorites,
  removeFavorite,
  type PortalFavorite,
} from '@/views/exchange/application/portal-favorites'

interface ApprovalFlowStep {
  step: string
  status: string
  result: string
  actor?: string
  time?: string
  comment?: string
}

interface SubRow {
  id: number
  resourceId: number
  resourceCode?: string
  resourceName?: string
  providerOrg?: string
  applicantOrg?: string
  applicantUser?: string
  shareMode: string
  purpose?: string
  status: string
  reviewComment?: string
  reviewedBy?: string
  reviewerContact?: string
  reviewedAt?: string
  distributeResult?: string
  distributeAt?: string
  createdAt?: string
  resourceFormat?: string
  shareType?: string
  openType?: string
  updateCycle?: string
  description?: string
  physicalTableName?: string
  applyPayload?: string | Record<string, unknown>
  approvalFlow?: ApprovalFlowStep[]
  oauthClientId?: string
  oauthClientSecret?: string
  apiUrl?: string
  apiMethod?: string
  authorization?: {
    authorizationCode?: string
    status?: string
    credentialRef?: string
    validFrom?: string
  }
}

const SHARE_ZH: Record<string, string> = {
  DB_SYNC: '库表同步',
  FILE_SYNC: '文件同步',
  API: '接口服务',
  TABLE: '库表同步',
  FILE: '文件同步',
}

const TARGET_TYPE_OPTS = [
  { value: 'INTERNAL_SYSTEM', label: '内部系统' },
  { value: 'SUPERIOR', label: '上级单位' },
  { value: 'CITY_BIGDATA', label: '市大数据中心' },
  { value: 'NATIONAL_LOCAL_BIGDATA', label: '国家/地方大数据中心' },
  { value: 'THIRD_PARTY', label: '第三方业务应用' },
]

interface NoticeRow {
  id: number
  subscriptionId: number
  resourceId: number
  changeType: string
  title: string
  detail?: string
  notifyUser?: string
  notifyOrg?: string
  status: string
  ackedAt?: string
  createdAt?: string
  resourceCode?: string
  resourceName?: string
  providerOrg?: string
  physicalTableName?: string
  versionNo?: number
}

interface TargetRow {
  id: number
  subscriptionId: number
  resourceId: number
  targetType: string
  targetName: string
  targetOrg?: string
  targetEndpoint?: string
  shareMode?: string
  autoPush?: boolean
  status: string
  remark?: string
  resourceName?: string
  applicantOrg?: string
  subscriptionStatus?: string
  createdAt?: string
}

interface DistLogRow {
  id: number
  subscriptionId: number
  targetId?: number
  resourceId: number
  triggerType: string
  changeType?: string
  targetType?: string
  targetName?: string
  shareMode?: string
  status: string
  resultSummary?: string
  createdAt?: string
  finishedAt?: string
  resourceName?: string
  resourceCode?: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activeTab = ref('mine')
const mineRows = ref<SubRow[]>([])
const pendingRows = ref<SubRow[]>([])
const reviewedRows = ref<SubRow[]>([])
const favoriteRows = ref<PortalFavorite[]>([])
const noticeRows = ref<NoticeRow[]>([])
const targetRows = ref<TargetRow[]>([])
const distLogRows = ref<DistLogRow[]>([])
const loading = ref(false)
const statusFilter = ref('')
const noticeStatusFilter = ref('')
const distScope = ref<'targets' | 'logs'>('targets')
const minePage = ref(1)
const minePageSize = ref(10)
const pendingPage = ref(1)
const pendingPageSize = ref(10)
const reviewedPage = ref(1)
const reviewedPageSize = ref(10)
const favPage = ref(1)
const favPageSize = ref(10)
const noticePage = ref(1)
const noticePageSize = ref(10)
const targetPage = ref(1)
const targetPageSize = ref(10)
const logPage = ref(1)
const logPageSize = ref(10)
const reviewForm = reactive({
  reviewerName: '',
  reviewerContact: '',
  note: '',
})
const targetForm = reactive({
  visible: false,
  id: null as number | null,
  subscriptionId: null as number | null,
  targetType: 'INTERNAL_SYSTEM',
  targetName: '',
  targetOrg: '',
  targetEndpoint: '',
  shareMode: 'DB_SYNC',
  autoPush: true,
  remark: '',
})
const approvedSubs = ref<SubRow[]>([])

const subDetail = reactive<{
  visible: boolean
  mode: 'mine' | 'pending' | 'reviewed'
  row: SubRow | null
}>({ visible: false, mode: 'mine', row: null })

const pagedMine = computed(() => {
  const start = (minePage.value - 1) * minePageSize.value
  return mineRows.value.slice(start, start + minePageSize.value)
})
const pagedPending = computed(() => {
  const start = (pendingPage.value - 1) * pendingPageSize.value
  return pendingRows.value.slice(start, start + pendingPageSize.value)
})
const pagedReviewed = computed(() => {
  const start = (reviewedPage.value - 1) * reviewedPageSize.value
  return reviewedRows.value.slice(start, start + reviewedPageSize.value)
})
const pagedFav = computed(() => {
  const start = (favPage.value - 1) * favPageSize.value
  return favoriteRows.value.slice(start, start + favPageSize.value)
})
const pagedNotices = computed(() => {
  const start = (noticePage.value - 1) * noticePageSize.value
  return noticeRows.value.slice(start, start + noticePageSize.value)
})
const pagedTargets = computed(() => {
  const start = (targetPage.value - 1) * targetPageSize.value
  return targetRows.value.slice(start, start + targetPageSize.value)
})
const pagedLogs = computed(() => {
  const start = (logPage.value - 1) * logPageSize.value
  return distLogRows.value.slice(start, start + logPageSize.value)
})

function shareLabel(mode?: string) {
  if (!mode) return '—'
  return SHARE_ZH[mode] || statusLabel(mode)
}

function targetTypeLabel(t?: string) {
  return TARGET_TYPE_OPTS.find((x) => x.value === t)?.label || statusLabel(t) || '—'
}

function showApiCredential(row: SubRow | null) {
  const m = String(row?.shareMode || row?.resourceType || '').toUpperCase()
  return m === 'API' || m === 'TABLE' || m === 'DATABASE' || m === 'DB_SYNC' || m === 'DB'
}

function parsePayload(row: SubRow | null): Record<string, unknown> {
  if (!row?.applyPayload) return {}
  const p = row.applyPayload
  if (typeof p === 'object' && p) return p as Record<string, unknown>
  try {
    return JSON.parse(String(p)) as Record<string, unknown>
  } catch {
    return {}
  }
}

const detailPayload = computed(() => parsePayload(subDetail.row))
const detailInputParams = computed(() =>
  Array.isArray(detailPayload.value.inputParams)
    ? (detailPayload.value.inputParams as Record<string, unknown>[])
    : [],
)
const detailOutputParams = computed(() =>
  Array.isArray(detailPayload.value.outputParams)
    ? (detailPayload.value.outputParams as Record<string, unknown>[])
    : [],
)
const detailHasParams = computed(() =>
  Array.isArray(detailPayload.value.inputParams) || Array.isArray(detailPayload.value.outputParams),
)

function payloadVal(obj: Record<string, unknown>, key: string) {
  const v = obj[key]
  if (v == null || String(v).trim() === '') return ''
  return String(v)
}

function fmtTime(v?: string) {
  return formatDateTime(v)
}

async function loadMine() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/subscriptions', {
      params: { status: statusFilter.value || undefined },
    })
    mineRows.value = res.data || []
    minePage.value = 1
  } catch {
    ElMessage.error('加载我的申请失败')
  } finally {
    loading.value = false
  }
}

function onQuery() {
  void loadMine()
}

function onReset() {
  statusFilter.value = ''
  void loadMine()
}

async function loadPending() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/subscriptions/pending')
    pendingRows.value = res.data || []
    pendingPage.value = 1
  } catch {
    ElMessage.error('加载待审批失败')
  } finally {
    loading.value = false
  }
}

async function loadReviewed() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/subscriptions/reviewed')
    reviewedRows.value = res.data || []
    reviewedPage.value = 1
  } catch {
    ElMessage.error('加载已审批失败')
  } finally {
    loading.value = false
  }
}

async function loadFavorites() {
  favoriteRows.value = await fetchFavorites('GOV')
  favPage.value = 1
}

async function loadNotices() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/subscriptions/notices', {
      params: { status: noticeStatusFilter.value || undefined },
    })
    noticeRows.value = res.data || []
    noticePage.value = 1
  } catch {
    ElMessage.error('加载变更通知失败')
  } finally {
    loading.value = false
  }
}

async function loadDistribute() {
  loading.value = true
  try {
    if (distScope.value === 'targets') {
      const [tRes, sRes] = await Promise.all([
        api.get('/governance/catalog/subscriptions/distribute-targets'),
        api.get('/governance/catalog/subscriptions', { params: { status: undefined } }),
      ])
      targetRows.value = tRes.data || []
      const all = (sRes.data || []) as SubRow[]
      approvedSubs.value = all.filter((r) =>
        ['APPROVED', 'DISTRIBUTED'].includes(String(r.status || '').toUpperCase()),
      )
      targetPage.value = 1
    } else {
      const res = await api.get('/governance/catalog/subscriptions/distribute-logs')
      distLogRows.value = res.data || []
      logPage.value = 1
    }
  } catch {
    ElMessage.error('加载分发数据失败')
  } finally {
    loading.value = false
  }
}

async function load() {
  if (activeTab.value === 'mine') await loadMine()
  else if (activeTab.value === 'pending') await loadPending()
  else if (activeTab.value === 'reviewed') await loadReviewed()
  else if (activeTab.value === 'notices') await loadNotices()
  else if (activeTab.value === 'distribute') await loadDistribute()
  else await loadFavorites()
}

function syncTabFromRoute() {
  const t = String(route.query.subTab || '')
  if (
    t === 'favorites' ||
    t === 'pending' ||
    t === 'mine' ||
    t === 'reviewed' ||
    t === 'notices' ||
    t === 'distribute'
  ) {
    activeTab.value = t
  }
}

function openDetail(row: SubRow, mode: 'mine' | 'pending' | 'reviewed') {
  subDetail.row = row
  subDetail.mode = mode
  subDetail.visible = true
  reviewForm.note = ''
  if (mode === 'pending') {
    reviewForm.reviewerName = auth.user?.displayName || auth.user?.username || ''
    reviewForm.reviewerContact = ''
  }
}

function flowStatusTag(s?: string) {
  if (s === 'APPROVED' || s === 'DONE') return 'success'
  if (s === 'REJECTED' || s === 'CANCELLED') return 'danger'
  return 'warning'
}

function reviewApiError(e: unknown): string {
  const msg = e instanceof Error ? e.message : ''
  if (/timeout of \d+ms exceeded/i.test(msg) || /timeout/i.test(msg) && /exceeded|aborted/i.test(msg)) {
    return '审核请求超时，未在限定时间内收到 ESB 网关响应，请稍后重试'
  }
  return msg || '审批失败'
}

async function approve(row: SubRow) {
  if (!reviewForm.reviewerName.trim()) {
    ElMessage.warning('请填写审批人')
    return
  }
  if (!reviewForm.reviewerContact.trim()) {
    ElMessage.warning('请填写联系方式')
    return
  }
  try {
    const res = await api.post(`/governance/catalog/subscriptions/${row.id}/approve`, {
      comment: reviewForm.note.trim() || '同意',
      reviewerName: reviewForm.reviewerName.trim(),
      reviewerContact: reviewForm.reviewerContact.trim(),
    }, { timeout: 45_000 })
    const nextStep = String((res.data as any)?.approvalStep || '').toUpperCase()
    if (nextStep === 'PROVIDER' || String((res.data as any)?.status || '').toUpperCase() === 'PENDING') {
      ElMessage.success(`平台审核已通过，已转交「${row.providerOrg || '目录提供单位'}」审核`)
    } else {
      ElMessage.success(res.data?.oauthClientId ? '部门审核已通过，已发放接口调用凭证' : '部门审核已通过')
    }
    subDetail.visible = false
    await Promise.all([loadPending(), loadReviewed()])
  } catch (e: unknown) {
    ElMessage.error(reviewApiError(e))
  }
}

async function reject(row: SubRow) {
  if (!reviewForm.reviewerName.trim()) {
    ElMessage.warning('请填写审批人')
    return
  }
  if (!reviewForm.reviewerContact.trim()) {
    ElMessage.warning('请填写联系方式')
    return
  }
  let comment = reviewForm.note
  if (!comment?.trim()) {
    try {
      const { value } = await ElMessageBox.prompt('请填写驳回意见', '驳回申请', {
        confirmButtonText: '驳回',
        cancelButtonText: '取消',
        inputPattern: /\S+/,
        inputErrorMessage: '意见不能为空',
      })
      comment = value
    } catch {
      return
    }
  }
  try {
    await api.post(`/governance/catalog/subscriptions/${row.id}/reject`, {
      comment,
      reviewerName: reviewForm.reviewerName.trim(),
      reviewerContact: reviewForm.reviewerContact.trim(),
    })
    ElMessage.success('已驳回')
    subDetail.visible = false
    await Promise.all([loadPending(), loadReviewed()])
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '驳回失败')
  }
}

async function cancel(row: SubRow) {
  await ElMessageBox.confirm('确认取消该资源申请？', '取消申请', { type: 'warning' })
  await api.post(`/governance/catalog/subscriptions/${row.id}/cancel`)
  ElMessage.success('已取消')
  subDetail.visible = false
  await loadMine()
}

async function distribute(row: SubRow) {
  const res = await api.post(`/governance/catalog/subscriptions/${row.id}/distribute`)
  ElMessage.success(res.data?.distributeResult || '分发完成')
  await loadMine()
  if (subDetail.row?.id === row.id) {
    const refreshed = mineRows.value.find((r) => r.id === row.id)
    if (refreshed) subDetail.row = refreshed
  }
}

async function showResult(row: SubRow) {
  const res = await api.get(`/governance/catalog/subscriptions/${row.id}/distribute-result`)
  const d = res.data || {}
  await ElMessageBox.alert(
    `${d.distributeResult || '暂无分发结果'}\n时间：${d.distributeAt || '—'}`,
    '分发结果',
    { confirmButtonText: '知道了' },
  )
}

async function testApi(row: SubRow) {
  const res = await api.get(`/governance/catalog/subscriptions/${row.id}/test-api`)
  const d = res.data || {}
  await ElMessageBox.alert(
    `${d.message || 'OK'}\n资源：${d.resourceName || ''}\n样例行数：${(d.sampleRows || []).length}`,
    'API 调试',
    { confirmButtonText: '关闭' },
  )
}

async function cancelFavorite(row: PortalFavorite) {
  await removeFavorite(row.govResourceId || row.catalogId, 'GOV')
  await loadFavorites()
  ElMessage.success('已取消订阅')
}

function openFavorite(row: PortalFavorite) {
  router.push({
    query: {
      ...route.query,
      tab: 'catalog',
      cSub: 'portal',
      resourceId: String(row.govResourceId || row.catalogId),
    },
  })
}

async function markNoticeRead(row: NoticeRow) {
  await api.post(`/governance/catalog/subscriptions/notices/${row.id}/read`)
  await loadNotices()
}

async function ackNotice(row: NoticeRow) {
  await ElMessageBox.confirm(
    '确认已了解最新情况并完成/安排本地数据更新？',
    '确认已知晓',
    { type: 'info' },
  )
  await api.post(`/governance/catalog/subscriptions/notices/${row.id}/ack`)
  ElMessage.success('已确认')
  await loadNotices()
}

function openNoticeResource(row: NoticeRow) {
  router.push({
    query: {
      ...route.query,
      tab: 'catalog',
      cSub: 'portal',
      resourceId: String(row.resourceId),
    },
  })
}

function openTargetDialog(row?: TargetRow) {
  if (row) {
    targetForm.visible = true
    targetForm.id = row.id
    targetForm.subscriptionId = row.subscriptionId
    targetForm.targetType = row.targetType || 'INTERNAL_SYSTEM'
    targetForm.targetName = row.targetName || ''
    targetForm.targetOrg = row.targetOrg || ''
    targetForm.targetEndpoint = row.targetEndpoint || ''
    targetForm.shareMode = row.shareMode || 'DB_SYNC'
    targetForm.autoPush = row.autoPush !== false
    targetForm.remark = row.remark || ''
    return
  }
  if (!approvedSubs.value.length) {
    ElMessage.warning('暂无已通过的订阅，请先在门户申请并审批通过')
    return
  }
  targetForm.visible = true
  targetForm.id = null
  targetForm.subscriptionId = approvedSubs.value[0]?.id ?? null
  targetForm.targetType = 'INTERNAL_SYSTEM'
  targetForm.targetName = ''
  targetForm.targetOrg = ''
  targetForm.targetEndpoint = ''
  targetForm.shareMode = 'DB_SYNC'
  targetForm.autoPush = true
  targetForm.remark = ''
}

async function saveTarget() {
  if (!targetForm.subscriptionId) {
    ElMessage.warning('请选择订阅申请')
    return
  }
  if (!targetForm.targetName.trim()) {
    ElMessage.warning('请填写目标名称')
    return
  }
  const body = {
    id: targetForm.id,
    subscriptionId: targetForm.subscriptionId,
    targetType: targetForm.targetType,
    targetName: targetForm.targetName.trim(),
    targetOrg: targetForm.targetOrg.trim() || undefined,
    targetEndpoint: targetForm.targetEndpoint.trim() || undefined,
    shareMode: targetForm.shareMode,
    autoPush: targetForm.autoPush,
    remark: targetForm.remark.trim() || undefined,
    status: 'ACTIVE',
  }
  if (targetForm.id) {
    await api.put(`/governance/catalog/subscriptions/distribute-targets/${targetForm.id}`, body)
  } else {
    await api.post('/governance/catalog/subscriptions/distribute-targets', body)
  }
  ElMessage.success('已保存分发目标')
  targetForm.visible = false
  distScope.value = 'targets'
  await loadDistribute()
}

async function removeTarget(row: TargetRow) {
  await ElMessageBox.confirm(`确认删除分发目标「${row.targetName}」？`, '删除', { type: 'warning' })
  await api.delete(`/governance/catalog/subscriptions/distribute-targets/${row.id}`)
  ElMessage.success('已删除')
  await loadDistribute()
}

async function pushTarget(row: TargetRow) {
  const res = await api.post(`/governance/catalog/subscriptions/${row.subscriptionId}/distribute-now`, {
    targetId: row.id,
  })
  ElMessage.success(`已触发分发（${res.data?.count ?? 0}）`)
  distScope.value = 'logs'
  await loadDistribute()
}

watch(activeTab, (t) => {
  const q = { ...route.query, subTab: t }
  router.replace({ query: q })
  void load()
})

watch(distScope, () => {
  if (activeTab.value === 'distribute') void loadDistribute()
})

watch(
  () => route.query.subTab,
  () => {
    syncTabFromRoute()
  },
)

onMounted(() => {
  syncTabFromRoute()
  void load()
})
onActivated(() => {
  syncTabFromRoute()
  void load()
})
</script>

<template>
  <PageCard title="资源申请订阅">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="我的申请" name="mine">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="状态" class="portal-field-sm">
            <el-select v-model="statusFilter" clearable placeholder="全部">
              <el-option label="待处理" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
              <el-option label="已取消" value="CANCELLED" />
              <el-option label="已分发" value="DISTRIBUTED" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="onQuery">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-form-item>
        </el-form>
        <el-table
          v-loading="loading"
          :data="pagedMine"
          stripe
          size="small"
          class="clickable-table"
          @row-click="(row: SubRow) => openDetail(row, 'mine')"
        >
          <el-table-column prop="resourceCode" label="资源编码" width="120" show-overflow-tooltip />
          <el-table-column label="资源名称" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <button type="button" class="link-title" @click.stop="openDetail(row, 'mine')">
                {{ row.resourceName || row.resourceId }}
              </button>
            </template>
          </el-table-column>
          <el-table-column label="提供方" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.providerOrg || '—' }}</template>
          </el-table-column>
          <el-table-column label="共享方式" width="100">
            <template #default="{ row }">{{ shareLabel(row.shareMode) }}</template>
          </el-table-column>
          <el-table-column label="办事场景" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ payloadVal(parsePayload(row), 'scene') || row.purpose || '—' }}</template>
          </el-table-column>
          <el-table-column label="联系人" width="90" show-overflow-tooltip>
            <template #default="{ row }">{{ payloadVal(parsePayload(row), 'contactName') || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row, 'mine')">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-if="mineRows.length"
          v-model:page="minePage"
          v-model:page-size="minePageSize"
          :total="mineRows.length"
        />
      </el-tab-pane>

      <el-tab-pane label="待我审批" name="pending" lazy>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadPending">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-empty v-if="!pendingRows.length && !loading" description="暂无待审批申请" :image-size="72" />
        <el-table
          v-else
          v-loading="loading"
          :data="pagedPending"
          stripe
          size="small"
          class="clickable-table"
          @row-click="(row: SubRow) => openDetail(row, 'pending')"
        >
          <el-table-column label="资源" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <button type="button" class="link-title" @click.stop="openDetail(row, 'pending')">
                {{ row.resourceName || row.resourceCode || row.resourceId }}
              </button>
            </template>
          </el-table-column>
          <el-table-column prop="applicantOrg" label="申请单位" width="140" show-overflow-tooltip />
          <el-table-column label="申请人" width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.applicantUser || '—' }}</template>
          </el-table-column>
          <el-table-column label="共享方式" width="100">
            <template #default="{ row }">{{ shareLabel(row.shareMode) }}</template>
          </el-table-column>
          <el-table-column label="办事场景" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ payloadVal(parsePayload(row), 'scene') || row.purpose || '—' }}</template>
          </el-table-column>
          <el-table-column label="联系人" width="90" show-overflow-tooltip>
            <template #default="{ row }">{{ payloadVal(parsePayload(row), 'contactName') || '—' }}</template>
          </el-table-column>
          <el-table-column label="联系电话" width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ payloadVal(parsePayload(row), 'contactPhone') || '—' }}</template>
          </el-table-column>
          <el-table-column label="申请时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row, 'pending')">审核</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-if="pendingRows.length"
          v-model:page="pendingPage"
          v-model:page-size="pendingPageSize"
          :total="pendingRows.length"
        />
      </el-tab-pane>

      <el-tab-pane label="已审批" name="reviewed" lazy>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadReviewed">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-empty v-if="!reviewedRows.length && !loading" description="暂无审批历史" :image-size="72" />
        <el-table
          v-else
          v-loading="loading"
          :data="pagedReviewed"
          stripe
          size="small"
          class="clickable-table"
          @row-click="(row: SubRow) => openDetail(row, 'reviewed')"
        >
          <el-table-column label="资源" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <button type="button" class="link-title" @click.stop="openDetail(row, 'reviewed')">
                {{ row.resourceName || row.resourceCode || row.resourceId }}
              </button>
            </template>
          </el-table-column>
          <el-table-column prop="applicantOrg" label="申请单位" width="140" show-overflow-tooltip />
          <el-table-column label="共享方式" width="100">
            <template #default="{ row }">{{ shareLabel(row.shareMode) }}</template>
          </el-table-column>
          <el-table-column label="审批结果" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审批人" width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.reviewedBy || '—' }}</template>
          </el-table-column>
          <el-table-column label="审批时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.reviewedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row, 'reviewed')">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-if="reviewedRows.length"
          v-model:page="reviewedPage"
          v-model:page-size="reviewedPageSize"
          :total="reviewedRows.length"
        />
      </el-tab-pane>

      <el-tab-pane label="我的订阅" name="favorites" lazy>
        <el-empty v-if="!favoriteRows.length" description="暂无订阅，请到资源目录门户点击「订阅」" :image-size="72" />
        <el-table v-else :data="pagedFav" stripe size="small">
          <el-table-column label="资源名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <button type="button" class="link-title" @click="openFavorite(row)">{{ row.title }}</button>
            </template>
          </el-table-column>
          <el-table-column prop="providerOrg" label="提供方" width="140" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ row.resourceTypeLabel || statusLabel(row.resourceType) || '—' }}</template>
          </el-table-column>
          <el-table-column label="订阅时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.followedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openFavorite(row)">查看</el-button>
              <el-button link type="danger" @click="cancelFavorite(row)">取消订阅</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-if="favoriteRows.length"
          v-model:page="favPage"
          v-model:page-size="favPageSize"
          :total="favoriteRows.length"
        />
      </el-tab-pane>

      <el-tab-pane label="变更通知" name="notices" lazy>
        <p class="hint">
          共用基础数据与专业数据发布到门户并被订阅后，资源发生变更、新增或再发布时，订阅单位可在此及时了解最新情况并确认更新。
        </p>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="状态" class="portal-field-sm">
            <el-select v-model="noticeStatusFilter" clearable placeholder="全部">
              <el-option label="未读" value="UNREAD" />
              <el-option label="已读" value="READ" />
              <el-option label="已确认" value="ACKED" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadNotices">查询</el-button>
            <el-button
              @click="
                () => {
                  noticeStatusFilter = ''
                  void loadNotices()
                }
              "
            >
              重置
            </el-button>
          </el-form-item>
        </el-form>
        <el-empty v-if="!noticeRows.length && !loading" description="暂无变更通知" :image-size="72" />
        <el-table v-else v-loading="loading" :data="pagedNotices" stripe size="small" border>
          <el-table-column label="标题" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <button type="button" class="link-title" @click="openNoticeResource(row)">
                {{ row.title }}
              </button>
            </template>
          </el-table-column>
          <el-table-column label="变更类型" width="110">
            <template #default="{ row }">{{ statusLabel(row.changeType) }}</template>
          </el-table-column>
          <el-table-column prop="resourceName" label="资源" min-width="140" show-overflow-tooltip />
          <el-table-column prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
          <el-table-column label="说明" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.detail || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="通知时间" width="170">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'UNREAD'"
                link
                type="primary"
                @click="markNoticeRead(row)"
              >
                标已读
              </el-button>
              <el-button
                v-if="row.status !== 'ACKED'"
                link
                type="success"
                @click="ackNotice(row)"
              >
                确认已更新
              </el-button>
              <el-button link type="primary" @click="openNoticeResource(row)">查看资源</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-if="noticeRows.length"
          v-model:page="noticePage"
          v-model:page-size="noticePageSize"
          :total="noticeRows.length"
        />
      </el-tab-pane>

      <el-tab-pane label="数据分发" name="distribute" lazy>
        <p class="hint">
          按订阅申请内容，将数据变更/新增推送至内部系统、上级、市大数据中心、国家/地方大数据中心及第三方业务应用。配置推送地址后将真实外呼；未配置则记分发台账。
        </p>
        <el-radio-group v-model="distScope" size="small" style="margin-bottom: 12px">
          <el-radio-button value="targets">分发目标</el-radio-button>
          <el-radio-button value="logs">分发台账</el-radio-button>
        </el-radio-group>

        <template v-if="distScope === 'targets'">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="openTargetDialog()">新增目标</el-button>
              <el-button @click="loadDistribute">刷新</el-button>
            </el-form-item>
          </el-form>
          <el-empty v-if="!targetRows.length && !loading" description="暂无分发目标，审批通过后会按申请自动生成，也可手工新增" :image-size="72" />
          <el-table v-else v-loading="loading" :data="pagedTargets" stripe size="small" border>
            <el-table-column prop="resourceName" label="资源" min-width="140" show-overflow-tooltip />
            <el-table-column label="目标类型" width="150">
              <template #default="{ row }">{{ targetTypeLabel(row.targetType) }}</template>
            </el-table-column>
            <el-table-column prop="targetName" label="目标名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="targetOrg" label="目标单位" width="120" show-overflow-tooltip />
            <el-table-column label="共享方式" width="100">
              <template #default="{ row }">{{ shareLabel(row.shareMode) }}</template>
            </el-table-column>
            <el-table-column label="自动推送" width="90">
              <template #default="{ row }">{{ row.autoPush ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="推送地址" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.targetEndpoint || '（仅台账）' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTargetDialog(row)">编辑</el-button>
                <el-button link type="success" @click="pushTarget(row)">立即分发</el-button>
                <el-button link type="danger" @click="removeTarget(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <PortalPagination
            v-if="targetRows.length"
            v-model:page="targetPage"
            v-model:page-size="targetPageSize"
            :total="targetRows.length"
          />
        </template>

        <template v-else>
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="loadDistribute">刷新</el-button>
            </el-form-item>
          </el-form>
          <el-empty v-if="!distLogRows.length && !loading" description="暂无分发台账" :image-size="72" />
          <el-table v-else v-loading="loading" :data="pagedLogs" stripe size="small" border>
            <el-table-column prop="resourceName" label="资源" min-width="140" show-overflow-tooltip />
            <el-table-column label="触发" width="90">
              <template #default="{ row }">{{ statusLabel(row.triggerType) }}</template>
            </el-table-column>
            <el-table-column label="变更类型" width="100">
              <template #default="{ row }">{{ row.changeType ? statusLabel(row.changeType) : '—' }}</template>
            </el-table-column>
            <el-table-column label="目标类型" width="140">
              <template #default="{ row }">{{ targetTypeLabel(row.targetType) }}</template>
            </el-table-column>
            <el-table-column prop="targetName" label="目标" min-width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结果" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.resultSummary || '—' }}</template>
            </el-table-column>
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <PortalPagination
            v-if="distLogRows.length"
            v-model:page="logPage"
            v-model:page-size="logPageSize"
            :total="distLogRows.length"
          />
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-drawer
      v-model="subDetail.visible"
      :title="subDetail.mode === 'mine' ? '申请详情' : '审批详情'"
      size="640px"
    >
      <template v-if="subDetail.row">
        <section class="detail-block">
          <h4>资源信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="资源名称">{{ subDetail.row.resourceName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="资源编码">{{ subDetail.row.resourceCode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="提供方">{{ subDetail.row.providerOrg || '—' }}</el-descriptions-item>
            <el-descriptions-item label="共享方式">{{ shareLabel(subDetail.row.shareMode) }}</el-descriptions-item>
            <el-descriptions-item label="物理表">{{ subDetail.row.physicalTableName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="更新周期">{{ subDetail.row.updateCycle || '—' }}</el-descriptions-item>
            <el-descriptions-item label="资源描述">{{ subDetail.row.description || '—' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-block">
          <h4>申请方信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="申请单位">{{ subDetail.row.applicantOrg || '—' }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ subDetail.row.applicantUser || '—' }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ payloadVal(parsePayload(subDetail.row), 'contactName') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ payloadVal(parsePayload(subDetail.row), 'contactPhone') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="联系邮箱">{{ payloadVal(parsePayload(subDetail.row), 'contactEmail') || '—' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-block">
          <h4>申请内容</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="使用办事场景">{{ payloadVal(parsePayload(subDetail.row), 'scene') || subDetail.row.purpose || '—' }}</el-descriptions-item>
            <el-descriptions-item label="应用系统名称">{{ payloadVal(parsePayload(subDetail.row), 'systemName') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="使用时间范围">{{ payloadVal(parsePayload(subDetail.row), 'timeRange') || '—' }}</el-descriptions-item>
            <el-descriptions-item v-if="payloadVal(parsePayload(subDetail.row), 'callFreq')" label="接口调用频次">
              {{ payloadVal(parsePayload(subDetail.row), 'callFreq') }} 次/天
            </el-descriptions-item>
            <el-descriptions-item v-if="payloadVal(parsePayload(subDetail.row), 'peakFreq')" label="接口峰值频率">
              {{ payloadVal(parsePayload(subDetail.row), 'peakFreq') }} 次/天
            </el-descriptions-item>
            <el-descriptions-item v-if="payloadVal(parsePayload(subDetail.row), 'useDays')" label="接口使用期限">
              {{ payloadVal(parsePayload(subDetail.row), 'useDays') }} 天
            </el-descriptions-item>
            <el-descriptions-item label="使用范围说明">{{ payloadVal(parsePayload(subDetail.row), 'useScope') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="数据描述">{{ payloadVal(parsePayload(subDetail.row), 'dataDesc') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="申请依据">{{ payloadVal(parsePayload(subDetail.row), 'applyBasis') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="其他技术需求">{{ payloadVal(parsePayload(subDetail.row), 'techReq') || '—' }}</el-descriptions-item>
          </el-descriptions>
          <template v-if="detailHasParams">
            <div class="detail-section-title">入参</div>
            <el-table
              :data="detailInputParams"
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
              :data="detailOutputParams"
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
          </template>
        </section>

        <section v-if="showApiCredential(subDetail.row)" class="detail-block">
          <h4>接口信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="应用系统名称">{{ payloadVal(parsePayload(subDetail.row), 'systemName') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="接口URL">{{ subDetail.row.apiUrl || payloadVal(parsePayload(subDetail.row), 'apiUrl') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="接口请求方式">{{ subDetail.row.apiMethod || payloadVal(parsePayload(subDetail.row), 'apiMethod') || 'POST' }}</el-descriptions-item>
            <el-descriptions-item label="用于Oauth2服务认证的client secret信息">
              {{ subDetail.row.oauthClientSecret || payloadVal(parsePayload(subDetail.row), 'oauthClientSecret') || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="用于Oauth2服务认证的clientid信息">
              {{ subDetail.row.oauthClientId || payloadVal(parsePayload(subDetail.row), 'oauthClientId') || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="使用时间范围">{{ payloadVal(parsePayload(subDetail.row), 'timeRange') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="使用期限">
              {{ payloadVal(parsePayload(subDetail.row), 'useDays') ? `${payloadVal(parsePayload(subDetail.row), 'useDays')}天` : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="其他技术请求说明">{{ payloadVal(parsePayload(subDetail.row), 'techReq') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="办事场景">{{ payloadVal(parsePayload(subDetail.row), 'scene') || payloadVal(parsePayload(subDetail.row), 'useScope') || subDetail.row.purpose || '—' }}</el-descriptions-item>
            <el-descriptions-item label="数据范围">{{ payloadVal(parsePayload(subDetail.row), 'dataDesc') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="接口调用频次">{{ payloadVal(parsePayload(subDetail.row), 'callFreq') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="接口峰值频率">{{ payloadVal(parsePayload(subDetail.row), 'peakFreq') || '—' }}</el-descriptions-item>
            <el-descriptions-item label="申请依据">{{ payloadVal(parsePayload(subDetail.row), 'applyBasis') || '—' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-block">
          <h4>办理信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="状态">{{ statusLabel(subDetail.row.status) }}</el-descriptions-item>
            <el-descriptions-item label="申请时间">{{ fmtTime(subDetail.row.createdAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.reviewedBy" label="审批人">{{ subDetail.row.reviewedBy }}</el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.reviewerContact" label="联系方式">{{ subDetail.row.reviewerContact }}</el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.reviewedAt" label="审批时间">{{ fmtTime(subDetail.row.reviewedAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.reviewComment" label="审批意见">{{ subDetail.row.reviewComment }}</el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.authorization?.authorizationCode" label="授权码">
              {{ subDetail.row.authorization.authorizationCode }}
            </el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.distributeResult" label="分发结果">{{ subDetail.row.distributeResult }}</el-descriptions-item>
          </el-descriptions>
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
              <template #default="{ row }">{{ fmtTime(row.time) }}</template>
            </el-table-column>
            <el-table-column prop="comment" label="结果/意见" min-width="120" show-overflow-tooltip />
          </el-table>
        </section>

        <div v-if="subDetail.mode === 'pending' && subDetail.row.status === 'PENDING' && (subDetail.row as any).canApprove !== false" class="sub-detail-ops">
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
          <el-button type="success" @click="approve(subDetail.row)">通过</el-button>
          <el-button type="danger" @click="reject(subDetail.row)">驳回</el-button>
        </div>

        <div v-else-if="subDetail.mode === 'mine'" class="sub-detail-ops">
          <template v-if="subDetail.row.status === 'PENDING'">
            <el-button type="danger" plain @click="cancel(subDetail.row)">取消申请</el-button>
          </template>
          <template v-else-if="subDetail.row.status === 'APPROVED' || subDetail.row.status === 'DISTRIBUTED'">
            <el-button type="primary" @click="distribute(subDetail.row)">分发</el-button>
            <el-button @click="showResult(subDetail.row)">分发结果</el-button>
            <el-button v-if="subDetail.row.shareMode === 'API'" @click="testApi(subDetail.row)">测试接口</el-button>
          </template>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="targetForm.visible" :title="targetForm.id ? '编辑分发目标' : '新增分发目标'" width="560px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="订阅申请" required>
          <el-select v-model="targetForm.subscriptionId" filterable placeholder="选择已通过的订阅" style="width: 100%">
            <el-option
              v-for="s in approvedSubs"
              :key="s.id"
              :label="`${s.resourceName || s.resourceId}（#${s.id}）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型" required>
          <el-select v-model="targetForm.targetType" style="width: 100%">
            <el-option v-for="o in TARGET_TYPE_OPTS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标名称" required>
          <el-input v-model="targetForm.targetName" maxlength="128" placeholder="系统/中心名称" />
        </el-form-item>
        <el-form-item label="目标单位">
          <el-input v-model="targetForm.targetOrg" maxlength="128" />
        </el-form-item>
        <el-form-item label="推送地址">
          <el-input v-model="targetForm.targetEndpoint" maxlength="512" placeholder="可选；填写后变更时 POST JSON 推送" />
        </el-form-item>
        <el-form-item label="共享方式">
          <el-select v-model="targetForm.shareMode" style="width: 100%">
            <el-option label="库表同步" value="DB_SYNC" />
            <el-option label="文件同步" value="FILE_SYNC" />
            <el-option label="接口服务" value="API" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更自动推送">
          <el-switch v-model="targetForm.autoPush" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="targetForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="targetForm.visible = false">取消</el-button>
        <el-button type="primary" @click="saveTarget">保存</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: #909399;
}
.link-title {
  appearance: none;
  border: 0;
  background: transparent;
  color: #1677ff;
  cursor: pointer;
  padding: 0;
  font: inherit;
  text-align: left;
}
.link-title:hover { text-decoration: underline; }
.clickable-table :deep(.el-table__row) { cursor: pointer; }
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
.sub-detail-ops {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #eef1f6;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
