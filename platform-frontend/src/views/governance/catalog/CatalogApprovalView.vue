<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { useAuthStore } from '@/stores/auth'

const props = withDefaults(defineProps<{ catalogOrigin?: 'INGEST' | 'GOVERNANCE' }>(), {
  catalogOrigin: 'GOVERNANCE',
})

const route = useRoute()
const auth = useAuthStore()

const pageTitle = computed(() =>
  props.catalogOrigin === 'INGEST' ? '数据资源目录审批' : '资源目录审批',
)

/** 治理侧仅审批资源目录；归集侧仍含资源分类审批 */
const showCategoryScope = computed(() => props.catalogOrigin === 'INGEST')

/** 超级管理员：已通过记录可下线、删除（与后端 SYSTEM_ADMIN / PLATFORM_ADMIN 对齐，UI 以 sys_admin 为准） */
const isCatalogAdmin = computed(() => auth.isSystemAdmin)

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
  actionType: string
  status: string
  submitComment?: string
  reviewComment?: string
  submittedBy?: string
  submittedAt?: string
  reviewedBy?: string
  reviewedAt?: string
  payloadJson?: string
}

interface DetailColumn {
  columnName?: string
  columnNameZh?: string
  dataType?: string
  dataTypeZh?: string
  sensLevel?: string
  shareLevel?: string
  displayFlag?: boolean
  searchFlag?: boolean
  statFlag?: boolean
  sortFlag?: boolean
  required?: boolean
  description?: string
}

interface DetailParam {
  name?: string
  type?: string
  required?: boolean
  description?: string
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
const page = ref(1)
const pageSize = ref(10)

const reviewVisible = ref(false)
const reviewMode = ref<'single' | 'batch'>('single')
const reviewTarget = ref<ApprovalRow | null>(null)
const reviewForm = reactive({
  decision: 'APPROVE' as 'APPROVE' | 'REJECT',
  comment: '',
})

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
const FORMAT_ZH: Record<string, string> = {
  DATABASE: '库表',
  FILE: '文件',
  API: '接口',
  OTHER: '其他',
}
const SHARE_LEVEL_ZH: Record<string, string> = {
  OPEN: '无条件共享',
  CONDITIONAL: '有条件共享',
  NOT_SHARE: '不予共享',
}
const OPEN_ZH: Record<string, string> = {
  OPEN: '是',
  NOT_OPEN: '否',
  CONDITIONAL: '有条件开放',
}

const reviewTitle = computed(() => (reviewMode.value === 'batch' ? '批量审核' : '审核'))
const detailTitle = computed(() => {
  if (!detailRow.value) return '审批详情'
  return detailAllowAudit.value ? '审核 · 数据详情' : '查看 · 数据详情'
})
const pendingCount = computed(() => rows.value.filter((r) => r.status === 'PENDING').length)
const selectedPendingCount = computed(
  () => selected.value.filter((r) => r.status === 'PENDING').length,
)
const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})
const CHECK_ITEMS = ['唯一性', '完整性', '关联性', '表述性'] as const

const introDesc = computed(() =>
  activeScope.value === 'CATEGORY'
    ? '审核资源分类的新增、编辑、删除。驳回须填写审批意见。'
    : '审核编目新增/变更/删除，以及发布、下线。关联/解绑分类已即时生效，不在此审批。驳回须填写审批意见。平台管理员对已通过且仍存在的目录可下线或删除；下线后回到编目管理。',
)
const emptyText = computed(() =>
  activeScope.value === 'CATEGORY' ? '暂无资源分类审批记录' : '暂无资源目录审批记录',
)

function canAdminOffline(row: ApprovalRow) {
  return (
    isCatalogAdmin.value
    && activeScope.value === 'RESOURCE'
    && row.status === 'APPROVED'
    && row.resourceAlive !== false
    && !!row.resourceId
    && row.publishStatus === 'PUBLISHED'
    && row.approvalStatus !== 'PENDING'
  )
}

function canAdminDelete(row: ApprovalRow) {
  return (
    isCatalogAdmin.value
    && activeScope.value === 'RESOURCE'
    && row.status === 'APPROVED'
    && row.resourceAlive !== false
    && !!row.resourceId
    && row.approvalStatus !== 'PENDING'
  )
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt/approvals', {
      params: {
        status: statusFilter.value || undefined,
        catalogOrigin: props.catalogOrigin || undefined,
        scope: activeScope.value,
      },
    })
    rows.value = res.data || []
    const maxPage = Math.max(1, Math.ceil(rows.value.length / pageSize.value) || 1)
    if (page.value > maxPage) page.value = maxPage
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

async function loadDetailResource(row: ApprovalRow) {
  detailResource.value = null
  if (!row.resourceId) return
  detailLoading.value = true
  try {
    const res = await api.get(`/governance/catalog/resources-mgmt/${row.resourceId}`)
    detailResource.value = (res.data || null) as Record<string, unknown> | null
  } catch {
    detailResource.value = null
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
  detailRow.value = row
  detailAllowAudit.value = row.status === 'PENDING'
  detailVisible.value = true
  await loadDetailResource(row)
}

/** 从详情进入单条审核决策 */
function openReviewFromDetail() {
  if (!detailRow.value || detailRow.value.status !== 'PENDING') return
  reviewMode.value = 'single'
  reviewTarget.value = detailRow.value
  reviewForm.decision = 'APPROVE'
  reviewForm.comment = ''
  reviewVisible.value = true
}

function openBatchReview() {
  if (!selectedPendingCount.value) {
    ElMessage.warning('请勾选待处理审批')
    return
  }
  reviewMode.value = 'batch'
  reviewTarget.value = null
  reviewForm.decision = 'APPROVE'
  reviewForm.comment = ''
  reviewVisible.value = true
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

function parseExt(): Record<string, unknown> | null {
  const raw = detailResource.value?.extJson
  if (raw == null || raw === '') return null
  if (typeof raw === 'object') return raw as Record<string, unknown>
  try {
    return JSON.parse(String(raw)) as Record<string, unknown>
  } catch {
    return null
  }
}

const detailExt = computed(() => parseExt())
const detailFormat = computed(() => {
  const f = detailField('resourceFormat')
  return f === '—' ? '' : f
})
const detailBindSourceName = computed(() => {
  const ext = detailExt.value
  if (!ext) return '—'
  return String(ext.bindSourceName || '—')
})
const detailBindTableName = computed(() => {
  const ext = detailExt.value
  if (!ext) return detailField('physicalTableName')
  return String(ext.bindTableName || detailField('physicalTableName'))
})
const detailColumnList = computed((): DetailColumn[] => {
  const ext = detailExt.value
  const list = ext?.columnList
  return Array.isArray(list) ? (list as DetailColumn[]) : []
})
const detailApi = computed(() => {
  const api = detailExt.value?.api
  return api && typeof api === 'object' ? (api as Record<string, unknown>) : null
})
const detailApiRequestParams = computed((): DetailParam[] => {
  const list = detailApi.value?.requestParams
  return Array.isArray(list) ? (list as DetailParam[]) : []
})
const detailApiResponseParams = computed((): DetailParam[] => {
  const list = detailApi.value?.responseParams
  return Array.isArray(list) ? (list as DetailParam[]) : []
})
const detailFile = computed(() => {
  const file = detailExt.value?.file
  return file && typeof file === 'object' ? (file as Record<string, unknown>) : null
})
const detailFileColumns = computed((): DetailColumn[] => {
  const list = detailFile.value?.columnList
  return Array.isArray(list) ? (list as DetailColumn[]) : []
})
const hasStep2Content = computed(() => {
  if (detailFormat.value === 'DATABASE') return !!detailExt.value || detailField('physicalTableName') !== '—'
  if (detailFormat.value === 'API') return !!detailApi.value
  if (detailFormat.value === 'FILE') return !!detailFile.value
  return false
})

function yesNo(v: unknown): string {
  if (v === true || v === 1 || v === '1' || v === 'true') return '是'
  if (v === false || v === 0 || v === '0' || v === 'false') return '否'
  return '—'
}

function shareLevelLabel(v?: string): string {
  if (!v) return '—'
  return SHARE_LEVEL_ZH[v] || statusLabel(v)
}

async function submitReview() {
  if (reviewForm.decision === 'REJECT' && !reviewForm.comment.trim()) {
    ElMessage.warning('驳回须填写审批意见')
    return
  }
  const comment =
    reviewForm.comment.trim() ||
    (reviewForm.decision === 'APPROVE' ? '四性校验通过，同意' : '')

  submitting.value = true
  try {
    if (reviewMode.value === 'single' && reviewTarget.value) {
      const id = reviewTarget.value.id
      if (reviewForm.decision === 'APPROVE') {
        await api.post(`/governance/catalog/resources-mgmt/approvals/${id}/approve`, { comment })
        ElMessage.success('审核通过')
      } else {
        await api.post(`/governance/catalog/resources-mgmt/approvals/${id}/reject`, { comment })
        ElMessage.success('已驳回')
      }
    } else {
      const ids = selected.value.filter((r) => r.status === 'PENDING').map((r) => r.id)
      if (!ids.length) {
        ElMessage.warning('请勾选待处理审批')
        return
      }
      if (reviewForm.decision === 'APPROVE') {
        const res = await api.post('/governance/catalog/resources-mgmt/approvals/batch-approve', {
          ids,
          comment,
        })
        const d = res.data || {}
        ElMessage.success(`已通过 ${d.approved || 0} 条`)
        if (d.errors?.length) ElMessage.warning(d.errors.slice(0, 3).join('；'))
      } else {
        const res = await api.post('/governance/catalog/resources-mgmt/approvals/batch-reject', {
          ids,
          comment,
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

async function withdraw(row: ApprovalRow) {
  await ElMessageBox.confirm('确认撤回该审批申请？', '撤回', { type: 'warning' })
  await api.post(`/governance/catalog/resources-mgmt/approvals/${row.id}/withdraw`)
  ElMessage.success('已撤回')
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
    <PageCard :title="pageTitle">
      <section class="intro">
        <div class="intro__main">
          <p class="intro__title">目录四性校验审核</p>
          <p class="intro__desc">{{ introDesc }}</p>
          <div class="check-chips">
            <span v-for="item in CHECK_ITEMS" :key="item" class="chip">{{ item }}</span>
          </div>
        </div>
        <div class="stat-cards">
          <div class="stat">
            <div class="stat__num">{{ pendingCount }}</div>
            <div class="stat__label">待处理</div>
          </div>
          <div class="stat">
            <div class="stat__num">{{ selectedPendingCount }}</div>
            <div class="stat__label">已勾选</div>
          </div>
          <div class="stat">
            <div class="stat__num">{{ pageRows.length }}</div>
            <div class="stat__label">本页合计</div>
          </div>
        </div>
      </section>

      <el-tabs
        v-if="showCategoryScope"
        v-model="activeScope"
        class="approval-tabs"
        @tab-change="onScopeChange"
      >
        <el-tab-pane label="资源目录" name="RESOURCE" />
        <el-tab-pane label="资源分类" name="CATEGORY" />
      </el-tabs>

      <el-form inline class="portal-inline-form portal-inline-form--block toolbar">
        <el-form-item label="状态" class="portal-field-sm">
          <el-select v-model="statusFilter" clearable placeholder="全部">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
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
      >
        <el-table-column type="selection" width="46" :selectable="(row: ApprovalRow) => row.status === 'PENDING'" />
        <el-table-column label="审核对象" min-width="180">
          <template #default="{ row }">
            <div class="obj">
              <div class="obj__name">{{ displayName(row) }}</div>
              <div class="obj__code">{{ displayCode(row) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="(ACTION_TAG[row.actionType] as any) || 'info'">
              {{ actionLabel(row.actionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitComment" label="提交说明" min-width="130" show-overflow-tooltip />
        <el-table-column prop="submittedBy" label="提交人" width="100" />
        <el-table-column prop="submittedAt" label="提交时间" width="166" />
        <el-table-column prop="reviewComment" label="审批意见" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button link type="primary" @click="openReview(row)">审核</el-button>
              <el-button link type="info" @click="withdraw(row)">撤回</el-button>
            </template>
            <template v-else-if="canAdminOffline(row) || canAdminDelete(row)">
              <el-button v-if="canAdminOffline(row)" link type="warning" @click="adminOffline(row)">下线</el-button>
              <el-button v-if="canAdminDelete(row)" link type="danger" @click="adminDelete(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <PortalPagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="rows.length"
      />
    </PageCard>

    <!-- 数据详情：查看只读；审核入口在详情内再点「审核」；含手动新建「下一步」关联资源内容 -->
    <el-drawer
      v-model="detailVisible"
      :title="detailTitle"
      size="720px"
      destroy-on-close
      append-to-body
    >
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detailRow">
          <el-descriptions :column="1" border size="small" title="审批信息">
            <el-descriptions-item label="审核对象">{{ displayName(detailRow) }}</el-descriptions-item>
            <el-descriptions-item label="编码">{{ displayCode(detailRow) }}</el-descriptions-item>
            <el-descriptions-item label="操作类型">{{ actionLabel(detailRow.actionType) }}</el-descriptions-item>
            <el-descriptions-item label="审批状态">
              <el-tag size="small" :type="statusTagType(detailRow.status)">{{ statusLabel(detailRow.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="提交说明">{{ detailRow.submitComment || '—' }}</el-descriptions-item>
            <el-descriptions-item label="提交人">{{ detailRow.submittedBy || '—' }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ detailRow.submittedAt || '—' }}</el-descriptions-item>
            <el-descriptions-item v-if="detailRow.reviewComment" label="审批意见">
              {{ detailRow.reviewComment }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detailRow.reviewedBy" label="审批人">
              {{ detailRow.reviewedBy }}
              <span v-if="detailRow.reviewedAt"> · {{ detailRow.reviewedAt }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-descriptions
            v-if="detailRow.resourceId"
            :column="1"
            border
            size="small"
            title="基本信息（新建第一步）"
            class="detail-resource"
          >
            <el-descriptions-item label="资源名称">{{ detailField('resourceName') }}</el-descriptions-item>
            <el-descriptions-item label="资源代码">{{ detailField('resourceCode') }}</el-descriptions-item>
            <el-descriptions-item label="资源类型">{{ statusLabel(detailField('resourceType')) }}</el-descriptions-item>
            <el-descriptions-item label="提供方">{{ detailField('providerOrg') }}</el-descriptions-item>
            <el-descriptions-item label="资源格式">{{ detailFormatLabel() }}</el-descriptions-item>
            <el-descriptions-item label="共享方式">{{ detailShareLabel() }}</el-descriptions-item>
            <el-descriptions-item label="共享条件">{{ detailField('shareCondition') }}</el-descriptions-item>
            <el-descriptions-item label="不予共享原因">{{ detailField('notShareReason') }}</el-descriptions-item>
            <el-descriptions-item label="是否开放">{{ detailOpenLabel() }}</el-descriptions-item>
            <el-descriptions-item label="开放条件">{{ detailField('openCondition') }}</el-descriptions-item>
            <el-descriptions-item label="联系人">{{ detailField('contactName') }}</el-descriptions-item>
            <el-descriptions-item label="联系电话">{{ detailField('contactPhone') }}</el-descriptions-item>
            <el-descriptions-item label="更新周期">{{ statusLabel(detailField('updateCycle')) }}</el-descriptions-item>
            <el-descriptions-item label="发布状态">
              <el-tag size="small" :type="statusTagType(String(detailResource?.publishStatus || detailRow.publishStatus || ''))">
                {{ statusLabel(detailField('publishStatus') !== '—' ? detailField('publishStatus') : detailRow.publishStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="分类路径">{{ detailField('categoryPath') }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ detailField('description') }}</el-descriptions-item>
          </el-descriptions>

          <!-- 下一步：关联资源 -->
          <template v-if="detailRow.resourceId && hasStep2Content">
            <div class="detail-section-title">关联资源（新建下一步）</div>

            <template v-if="detailFormat === 'DATABASE'">
              <el-descriptions :column="1" border size="small" class="detail-resource">
                <el-descriptions-item label="数据源">{{ detailBindSourceName }}</el-descriptions-item>
                <el-descriptions-item label="数据表">{{ detailBindTableName }}</el-descriptions-item>
                <el-descriptions-item label="元数据条目">{{ detailField('metadataEntryCode') }}</el-descriptions-item>
                <el-descriptions-item label="来源路径">
                  {{ detailField('sourcePathType') === 'PROCESSED' ? '加工共享' : detailField('sourcePathType') === 'DIRECT' ? '直通共享' : detailField('sourcePathType') }}
                </el-descriptions-item>
              </el-descriptions>
              <el-table
                v-if="detailColumnList.length"
                :data="detailColumnList"
                size="small"
                stripe
                border
                class="detail-table"
                max-height="360"
              >
                <el-table-column prop="columnName" label="名称" width="120" show-overflow-tooltip />
                <el-table-column prop="columnNameZh" label="中文名称" min-width="110" show-overflow-tooltip />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">{{ row.dataTypeZh || row.dataType || '—' }}</template>
                </el-table-column>
                <el-table-column prop="sensLevel" label="敏感级别" width="90" />
                <el-table-column label="共享类型" width="110">
                  <template #default="{ row }">{{ shareLevelLabel(row.shareLevel) }}</template>
                </el-table-column>
                <el-table-column label="展示" width="60" align="center">
                  <template #default="{ row }">{{ yesNo(row.displayFlag) }}</template>
                </el-table-column>
                <el-table-column label="搜索" width="60" align="center">
                  <template #default="{ row }">{{ yesNo(row.searchFlag) }}</template>
                </el-table-column>
                <el-table-column label="统计" width="60" align="center">
                  <template #default="{ row }">{{ yesNo(row.statFlag) }}</template>
                </el-table-column>
                <el-table-column label="排序" width="60" align="center">
                  <template #default="{ row }">{{ yesNo(row.sortFlag) }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无字段清单" :image-size="56" />
            </template>

            <template v-else-if="detailFormat === 'API' && detailApi">
              <el-descriptions :column="1" border size="small" class="detail-resource">
                <el-descriptions-item label="接口名称">{{ detailApi.apiName || '—' }}</el-descriptions-item>
                <el-descriptions-item label="接口版本">{{ detailApi.apiVersion || '—' }}</el-descriptions-item>
                <el-descriptions-item label="目标地址">{{ detailApi.apiUrl || '—' }}</el-descriptions-item>
                <el-descriptions-item label="请求路径">{{ detailApi.apiPath || '—' }}</el-descriptions-item>
                <el-descriptions-item label="请求方式">{{ detailApi.apiMethod || '—' }}</el-descriptions-item>
                <el-descriptions-item label="超时(ms)">{{ detailApi.apiTimeout ?? '—' }}</el-descriptions-item>
                <el-descriptions-item label="注册时间">{{ detailApi.registerAt || '—' }}</el-descriptions-item>
                <el-descriptions-item label="失效时间">{{ detailApi.expireAt || '—' }}</el-descriptions-item>
                <el-descriptions-item label="接口描述">{{ detailApi.apiDescription || '—' }}</el-descriptions-item>
                <el-descriptions-item label="返回示例">
                  <pre class="detail-pre">{{ detailApi.apiResultJson || '—' }}</pre>
                </el-descriptions-item>
              </el-descriptions>
              <div class="detail-sub-title">请求参数</div>
              <el-table
                v-if="detailApiRequestParams.length"
                :data="detailApiRequestParams"
                size="small"
                stripe
                border
                class="detail-table"
              >
                <el-table-column prop="name" label="参数名" min-width="120" />
                <el-table-column label="必填" width="70" align="center">
                  <template #default="{ row }">{{ yesNo(row.required) }}</template>
                </el-table-column>
                <el-table-column prop="type" label="类型" width="100" />
                <el-table-column prop="description" label="简介" min-width="140" show-overflow-tooltip />
              </el-table>
              <el-empty v-else description="无请求参数" :image-size="48" />
              <div class="detail-sub-title">响应参数</div>
              <el-table
                v-if="detailApiResponseParams.length"
                :data="detailApiResponseParams"
                size="small"
                stripe
                border
                class="detail-table"
              >
                <el-table-column prop="name" label="参数名" min-width="120" />
                <el-table-column label="必须" width="70" align="center">
                  <template #default="{ row }">{{ yesNo(row.required) }}</template>
                </el-table-column>
                <el-table-column prop="type" label="类型" width="100" />
                <el-table-column prop="description" label="简介" min-width="140" show-overflow-tooltip />
              </el-table>
              <el-empty v-else description="无响应参数" :image-size="48" />
            </template>

            <template v-else-if="detailFormat === 'FILE' && detailFile">
              <el-descriptions :column="1" border size="small" class="detail-resource">
                <el-descriptions-item label="文件名称">{{ detailFile.fileName || '—' }}</el-descriptions-item>
                <el-descriptions-item label="工作表">{{ detailFile.sheetName || '—' }}</el-descriptions-item>
                <el-descriptions-item label="文件大小">
                  {{ detailFile.fileSize ? `${Math.round(Number(detailFile.fileSize) / 1024)} KB` : '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="文件说明">{{ detailFile.fileRemark || '—' }}</el-descriptions-item>
              </el-descriptions>
              <div class="detail-sub-title">文件列信息</div>
              <el-table
                v-if="detailFileColumns.length"
                :data="detailFileColumns"
                size="small"
                stripe
                border
                class="detail-table"
              >
                <el-table-column prop="columnName" label="列名" min-width="110" />
                <el-table-column prop="columnNameZh" label="中文名称" min-width="110" />
                <el-table-column label="必填" width="70" align="center">
                  <template #default="{ row }">{{ yesNo(row.required) }}</template>
                </el-table-column>
                <el-table-column prop="dataType" label="类型" width="100" />
                <el-table-column prop="description" label="简介" min-width="140" show-overflow-tooltip />
              </el-table>
              <el-empty v-else description="无列信息" :image-size="48" />
            </template>
          </template>

          <el-alert
            v-else-if="detailRow.categoryId && !detailRow.resourceId"
            type="info"
            :closable="false"
            show-icon
            title="本单为资源分类审批，无资源目录实体详情。"
            class="detail-resource"
          />
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
        <span class="review-subject__badge">批量</span>
        <div>
          <div class="review-subject__name">已选 {{ selectedPendingCount }} 条待处理审批</div>
          <div class="review-subject__meta">通过或驳回将统一应用到所选记录</div>
        </div>
      </div>
      <div v-else-if="reviewTarget" class="review-subject">
        <span class="review-subject__badge">单条</span>
        <div>
          <div class="review-subject__name">{{ displayName(reviewTarget) }}</div>
          <div class="review-subject__meta">
            {{ displayCode(reviewTarget) }} · {{ actionLabel(reviewTarget.actionType) }}
          </div>
        </div>
      </div>

      <div class="decision">
        <button
          type="button"
          class="decision__card"
          :class="{ 'is-active': reviewForm.decision === 'APPROVE', 'is-ok': true }"
          @click="reviewForm.decision = 'APPROVE'"
        >
          <span class="decision__title">通过</span>
          <span class="decision__hint">四性校验合格，生效变更</span>
        </button>
        <button
          type="button"
          class="decision__card"
          :class="{ 'is-active': reviewForm.decision === 'REJECT', 'is-no': true }"
          @click="reviewForm.decision = 'REJECT'"
        >
          <span class="decision__title">驳回</span>
          <span class="decision__hint">须填写审批意见</span>
        </button>
      </div>

      <div class="comment-block">
        <div class="comment-block__label">
          审批意见
          <em v-if="reviewForm.decision === 'REJECT'">（必填）</em>
          <em v-else>（选填）</em>
        </div>
        <el-input
          v-model="reviewForm.comment"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          :placeholder="reviewForm.decision === 'REJECT' ? '请说明驳回原因' : '默认：四性校验通过，同意'"
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

.intro {
  display: flex;
  gap: 20px;
  align-items: stretch;
  justify-content: space-between;
  padding: 16px 18px;
  margin-bottom: 14px;
  border-radius: var(--portal-radius);
  border: 1px solid #d6e4ff;
  background: linear-gradient(135deg, #f5f9ff 0%, #eef4ff 55%, #f8fbff 100%);
}

.intro__main {
  min-width: 0;
  flex: 1;
}

.intro__title {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--portal-text);
}

.intro__desc {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--portal-text-secondary);
}

.check-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--portal-primary-dark);
  background: rgba(22, 119, 255, 0.08);
  border: 1px solid rgba(22, 119, 255, 0.18);
}

.stat-cards {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.stat {
  width: 78px;
  padding: 10px 8px;
  text-align: center;
  border-radius: 8px;
  background: #fff;
  border: 1px solid var(--portal-border);
  box-shadow: var(--portal-shadow);
}

.stat__num {
  font-size: 22px;
  font-weight: 650;
  line-height: 1.2;
  color: var(--portal-primary);
  font-variant-numeric: tabular-nums;
}

.stat__label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--portal-text-secondary);
}

.toolbar {
  margin-bottom: 4px;
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

.detail-resource {
  margin-top: 16px;
}

.detail-section-title {
  margin: 18px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--portal-text);
}

.detail-sub-title {
  margin: 12px 0 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--portal-text-secondary);
}

.detail-table {
  width: 100%;
  margin-bottom: 4px;
}

.detail-pre {
  margin: 0;
  max-height: 160px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--portal-text-secondary);
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
