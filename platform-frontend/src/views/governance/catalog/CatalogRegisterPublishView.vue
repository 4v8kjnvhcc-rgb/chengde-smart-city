<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import {
  catalogResourceStatusCode,
  catalogResourceStatusLabel,
  statusLabel,
  statusTagType,
} from '@/utils/status-label'

const props = withDefaults(defineProps<{ catalogOrigin?: 'INGEST' | 'GOVERNANCE' }>(), {
  catalogOrigin: 'GOVERNANCE',
})

/** 治理侧分类树取自归集「指标与目录体系构建」；归集侧不变 */
const categoryDataOrigin = computed(() =>
  props.catalogOrigin === 'GOVERNANCE' ? 'INGEST' : props.catalogOrigin,
)

const pageTitle = computed(() =>
  props.catalogOrigin === 'INGEST' ? '资源目录注册发布' : '目录注册发布',
)
const approvalEntryName = computed(() =>
  props.catalogOrigin === 'INGEST' ? '数据资源目录审批' : '资源目录审批',
)

interface CategoryNode {
  id: number
  categoryCode: string
  categoryName: string
  parentId?: number
  categoryPath?: string
  children?: CategoryNode[]
}

interface CatalogRes {
  id: number
  resourceCode: string
  resourceName: string
  resourceType: string
  resourceFormat?: string
  categoryId?: number
  categoryPath?: string
  publishStatus: string
  approvalStatus: string
  sourcePathType?: string
  metadataEntryCode?: string
  providerOrg?: string
}

const FORMAT_ZH: Record<string, string> = {
  DATABASE: '库表',
  API: '接口',
  FILE: '文件',
  OTHER: '其他',
}

function formatLabel(fmt?: string) {
  if (!fmt) return '—'
  return FORMAT_ZH[fmt] || statusLabel(fmt) || fmt
}

const categoryOptions = ref<{ id: number; label: string }[]>([])
/** 筛选「分类下已关联资源」列表 */
const filterCategoryId = ref<number | undefined>()
const boundAll = ref<CatalogRes[]>([])
const unboundAll = ref<CatalogRes[]>([])
const selectedBound = ref<CatalogRes[]>([])
const selectedUnbound = ref<CatalogRes[]>([])
const loading = ref(false)
const publishingId = ref<number | null>(null)
const bindDialogVisible = ref(false)
const bindCategoryId = ref<number | undefined>()
const bindSubmitting = ref(false)

interface VersionRow {
  id: number
  resourceId: number
  versionNo: number
  changeSummary?: string
  publishedBy?: string
  publishedAt?: string
}

const viewVisible = ref(false)
const viewRow = ref<CatalogRes | null>(null)
const versionDrawerVisible = ref(false)
const versionResource = ref<CatalogRes | null>(null)
const versions = ref<VersionRow[]>([])

const query = reactive({
  resourceName: '',
  categoryPath: '',
})

const filterCategoryLabel = computed(() => {
  if (filterCategoryId.value == null) return ''
  return categoryOptions.value.find((c) => c.id === filterCategoryId.value)?.label || ''
})

function matchCategoryPath(row: CatalogRes) {
  const kw = query.categoryPath.trim().toLowerCase()
  if (!kw) return true
  return (row.categoryPath || '').toLowerCase().includes(kw)
}

const boundRows = computed(() => boundAll.value.filter(matchCategoryPath))
const {
  page: boundPage,
  pageSize: boundPageSize,
  paged: pagedBoundRows,
  total: boundTotal,
  resetPage: resetBoundPage,
} = useClientPager(boundRows)

const unboundRows = computed(() => unboundAll.value)
const {
  page: unboundPage,
  pageSize: unboundPageSize,
  paged: pagedUnboundRows,
  total: unboundTotal,
  resetPage: resetUnboundPage,
} = useClientPager(unboundRows)

async function loadCategoryOptions() {
  try {
    const [treeRes, listRes] = await Promise.all([
      api.get('/governance/catalog/categories/tree', { params: { catalogOrigin: categoryDataOrigin.value } }),
      api.get('/governance/catalog/categories', { params: { catalogOrigin: categoryDataOrigin.value } }),
    ])
    const treeData = (treeRes.data || []) as CategoryNode[]
    const rows = (listRes.data || []) as Array<{
      id: number
      categoryName?: string
      categoryPath?: string
      categoryCode?: string
    }>
    if (rows.length) {
      categoryOptions.value = rows.map((r) => ({
        id: r.id,
        label: r.categoryPath || r.categoryName || r.categoryCode || String(r.id),
      }))
    } else {
      const out: { id: number; label: string }[] = []
      const walk = (nodes: CategoryNode[], prefix = '') => {
        for (const n of nodes) {
          const label = prefix ? `${prefix} / ${n.categoryName}` : n.categoryName
          out.push({ id: n.id, label })
          if (n.children?.length) walk(n.children, label)
        }
      }
      walk(treeData)
      categoryOptions.value = out
    }
  } catch {
    categoryOptions.value = []
  }
}

async function loadBound() {
  const keyword = query.resourceName.trim() || undefined
  if (filterCategoryId.value != null) {
    const res = await api.get('/governance/catalog/resources-mgmt', {
      params: {
        categoryId: filterCategoryId.value,
        catalogOrigin: props.catalogOrigin,
        keyword,
        excludeApprovalDraft: true,
      },
    })
    boundAll.value = res.data || []
    return
  }
  const res = await api.get('/governance/catalog/resources-mgmt', {
    params: {
      catalogOrigin: props.catalogOrigin,
      keyword,
      excludeApprovalDraft: true,
    },
  })
  const all = (res.data || []) as CatalogRes[]
  boundAll.value = all.filter((r) => r.categoryId != null && Number(r.categoryId) > 0)
}

async function loadUnbound() {
  const keyword = query.resourceName.trim() || undefined
  const res = await api.get('/governance/catalog/resources-mgmt', {
    params: {
      unboundOnly: true,
      catalogOrigin: props.catalogOrigin,
      keyword,
      excludeApprovalDraft: true,
    },
  })
  unboundAll.value = res.data || []
}

async function refreshLists() {
  loading.value = true
  try {
    await Promise.all([loadBound(), loadUnbound()])
    resetBoundPage()
    resetUnboundPage()
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.resourceName = ''
  query.categoryPath = ''
  filterCategoryId.value = undefined
  void refreshLists()
}

function onFilterCategoryChange() {
  selectedBound.value = []
  resetBoundPage()
  void loadBound()
}

function openBindDialog() {
  if (!selectedUnbound.value.length) {
    ElMessage.warning('请勾选未挂载资源')
    return
  }
  bindCategoryId.value = filterCategoryId.value
  bindDialogVisible.value = true
}

async function confirmBind() {
  if (!bindCategoryId.value) {
    ElMessage.warning('请选择数据资源分类')
    return
  }
  if (!selectedUnbound.value.length) {
    ElMessage.warning('请勾选未挂载资源')
    return
  }
  bindSubmitting.value = true
  try {
    const res = await api.post<{ submitted?: number; bound?: number; errors?: string[] }>(
      '/governance/catalog/resources-mgmt/bind-category',
      {
        categoryId: bindCategoryId.value,
        resourceIds: selectedUnbound.value.map((r) => r.id),
      },
    )
    const d = res.data || {}
    ElMessage.success(`已关联 ${d.bound ?? d.submitted ?? 0} 条到所选分类，可在「分类下已关联资源」中发布`)
    if (d.errors?.length) {
      ElMessage.warning(d.errors.slice(0, 3).join('；'))
    }
    bindDialogVisible.value = false
    selectedUnbound.value = []
    filterCategoryId.value = bindCategoryId.value
    await refreshLists()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '关联失败')
  } finally {
    bindSubmitting.value = false
  }
}

async function unbindSelected() {
  if (!selectedBound.value.length) {
    ElMessage.warning('请勾选已挂载资源')
    return
  }
  const published = selectedBound.value.filter((r) => r.publishStatus === 'PUBLISHED')
  if (published.length && published.length === selectedBound.value.length) {
    ElMessage.warning('已发布资源不可解绑，请先下线后再解除关联')
    return
  }
  await ElMessageBox.confirm(
    `确认解除关联 ${selectedBound.value.length} 条？解除后将移至「未挂载资源（可关联）」。`,
    '批量解除关联',
    { type: 'warning' },
  )
  try {
    const res = await api.post<{ submitted?: number; unbound?: number; errors?: string[] }>(
      '/governance/catalog/resources-mgmt/unbind-category',
      {
        resourceIds: selectedBound.value.map((r) => r.id),
      },
    )
    const d = res.data || {}
    ElMessage.success(`已解除关联 ${d.unbound ?? d.submitted ?? 0} 条，已移至未挂载`)
    if (d.errors?.length) {
      ElMessage.warning(d.errors.slice(0, 3).join('；'))
    }
    selectedBound.value = []
    await refreshLists()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '解除关联失败')
  }
}

async function publishOne(row: CatalogRes) {
  if (!row.categoryId) {
    ElMessage.warning('请先关联到分类后再发布')
    return
  }
  if (row.publishStatus === 'PUBLISHED') {
    ElMessage.warning('已发布')
    return
  }
  if (row.approvalStatus === 'PENDING') {
    ElMessage.warning('审批中，请勿重复提交')
    return
  }
  await ElMessageBox.confirm(
    `确认提交「${row.resourceName}」发布审批？通过后生效，可在「${approvalEntryName.value}」查看。`,
    '提交发布',
    { type: 'info' },
  )
  publishingId.value = row.id
  try {
    await api.post(`/governance/catalog/resources-mgmt/${row.id}/submit`, {
      actionType: 'PUBLISH',
      comment: '目录注册发布：提交发布审批',
    })
    ElMessage.success(`已提交发布审批，请到「${approvalEntryName.value}」处理`)
    await refreshLists()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '提交发布失败')
  } finally {
    publishingId.value = null
  }
}

interface ApprovalFlowRow {
  id: number
  actionType?: string
  status?: string
  submitComment?: string
  reviewComment?: string
  submittedBy?: string
  submittedAt?: string
  reviewedBy?: string
  reviewerContact?: string
  reviewedAt?: string
}

interface FlowStepRow {
  step: string
  actionType?: string
  status: string
  actor?: string
  contact?: string
  time?: string
  comment?: string
}

const approvalFlowRows = ref<ApprovalFlowRow[]>([])
const approvalFlowLoading = ref(false)
const ACTION_FLOW_ZH: Record<string, string> = {
  PUBLISH: '发布',
  OFFLINE: '下线',
  UPDATE: '变更',
  DELETE: '删除',
  CREATE: '编目新增',
}

function buildRegisterFlowSteps(rows: ApprovalFlowRow[]): FlowStepRow[] {
  const out: FlowStepRow[] = []
  for (const row of rows) {
    out.push({
      step: '提交',
      actionType: row.actionType,
      status: 'DONE',
      actor: row.submittedBy || '—',
      contact: '—',
      time: row.submittedAt,
      comment: row.submitComment || '已提交',
    })
    const st = String(row.status || '').toUpperCase()
    if (st === 'PENDING') {
      out.push({
        step: '审批',
        actionType: row.actionType,
        status: 'PENDING',
        actor: '—',
        contact: '—',
        time: '',
        comment: '待审批',
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
        step: '审批',
        actionType: row.actionType,
        status: st || String(row.status || ''),
        actor: row.reviewedBy || '—',
        contact: row.reviewerContact || '—',
        time: row.reviewedAt,
        comment: row.reviewComment || (st === 'APPROVED' ? '已通过' : st === 'REJECTED' ? '已驳回' : '—'),
      })
    }
  }
  return out
}

const registerFlowSteps = computed(() => buildRegisterFlowSteps(approvalFlowRows.value))

async function openView(row: CatalogRes) {
  viewRow.value = row
  viewVisible.value = true
  approvalFlowLoading.value = true
  approvalFlowRows.value = []
  try {
    const res = await api.get(`/governance/catalog/resources-mgmt/${row.id}/approvals`)
    approvalFlowRows.value = (res.data || []) as ApprovalFlowRow[]
  } catch {
    approvalFlowRows.value = []
  } finally {
    approvalFlowLoading.value = false
  }
}

async function openVersions(row: CatalogRes) {
  versionResource.value = row
  versionDrawerVisible.value = true
  try {
    versions.value = (await api.get(`/governance/catalog/resources-mgmt/${row.id}/versions`)).data || []
  } catch {
    ElMessage.error('加载版本历史失败')
  }
}

/** 待审核撤回 → 待发布 */
async function withdrawPending(row: CatalogRes) {
  await ElMessageBox.confirm(`确认撤回「${row.resourceName}」的待审核申请？撤回后回到待发布。`, '撤回', {
    type: 'warning',
  })
  const res = await api.get('/governance/catalog/resources-mgmt/approvals', {
    params: { resourceId: row.id, status: 'PENDING', catalogOrigin: props.catalogOrigin },
  })
  const pending = (res.data || []) as Array<{ id: number }>
  if (!pending.length) {
    ElMessage.warning('未找到待审核审批单')
    await refreshLists()
    return
  }
  await api.post(`/governance/catalog/resources-mgmt/approvals/${pending[0].id}/withdraw`)
  ElMessage.success('已撤回，状态回到待发布')
  await refreshLists()
}

async function bootstrap() {
  try {
    await loadCategoryOptions()
    await refreshLists()
  } catch {
    ElMessage.error('加载目录注册数据失败')
  }
}

onMounted(() => {
  void bootstrap()
})

onActivated(() => {
  void bootstrap()
})
</script>

<template>
  <PageCard :title="pageTitle">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="名称" class="portal-field-lg">
        <el-input
          v-model="query.resourceName"
          clearable
          placeholder="资源名称/编码"
          @keyup.enter="refreshLists"
        />
      </el-form-item>
      <el-form-item label="分类路径" class="portal-field-xl">
        <el-input
          v-model="query.categoryPath"
          clearable
          placeholder="筛选已关联资源的分类路径"
          @keyup.enter="refreshLists"
        />
      </el-form-item>
      <el-form-item label="分类筛选" class="portal-field-xl">
        <el-select
          v-model="filterCategoryId"
          clearable
          filterable
          placeholder="已关联列表按分类筛选"
          @change="onFilterCategoryChange"
        >
          <el-option v-for="c in categoryOptions" :key="c.id" :label="c.label" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="refreshLists">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <div v-loading="loading" class="reg-main">
      <h4>
        分类下已关联资源
        <span v-if="filterCategoryLabel" class="sub">（{{ filterCategoryLabel }}）</span>
        <span v-else class="sub">（全部已挂载）</span>
      </h4>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item class="portal-form-actions">
          <el-button type="danger" plain :disabled="!selectedBound.length" @click="unbindSelected">
            批量解除关联
          </el-button>
        </el-form-item>
      </el-form>
      <el-table
        :data="pagedBoundRows"
        stripe
        size="small"
        @selection-change="(rows: CatalogRes[]) => (selectedBound = rows)"
      >
        <el-table-column type="selection" width="42" />
        <el-table-column prop="resourceCode" label="编码" width="130" />
        <el-table-column prop="resourceName" label="名称" min-width="140" />
        <el-table-column prop="categoryPath" label="分类路径" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.categoryPath || '—' }}</template>
        </el-table-column>
        <el-table-column label="数据资源格式" width="110">
          <template #default="{ row }">{{ formatLabel(row.resourceFormat) }}</template>
        </el-table-column>
        <el-table-column prop="providerOrg" label="提供方" width="110" show-overflow-tooltip />
        <el-table-column prop="metadataEntryCode" label="元数据" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.metadataEntryCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="statusTagType(catalogResourceStatusCode(row.approvalStatus, row.publishStatus))"
            >
              {{ catalogResourceStatusLabel(row.approvalStatus, row.publishStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button link @click="openVersions(row)">版本</el-button>
            <el-button
              v-if="row.publishStatus !== 'PUBLISHED' && row.approvalStatus !== 'PENDING'"
              link
              type="primary"
              :loading="publishingId === row.id"
              @click="publishOne(row)"
            >
              发布
            </el-button>
            <el-button
              v-else-if="row.approvalStatus === 'PENDING'"
              link
              type="info"
              @click="withdrawPending(row)"
            >
              撤回
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-model:page="boundPage"
        v-model:page-size="boundPageSize"
        :total="boundTotal"
      />
      <el-empty v-if="!boundRows.length" description="暂无已关联资源（编目时选择分类或在此关联后可见）" />

      <h4 style="margin-top: 24px">未挂载资源（可关联）</h4>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :disabled="!selectedUnbound.length" @click="openBindDialog">
            关联分类
          </el-button>
        </el-form-item>
      </el-form>
      <el-table
        :data="pagedUnboundRows"
        stripe
        size="small"
        @selection-change="(rows: CatalogRes[]) => (selectedUnbound = rows)"
      >
        <el-table-column type="selection" width="42" />
        <el-table-column prop="resourceCode" label="编码" width="130" />
        <el-table-column prop="resourceName" label="名称" min-width="140" />
        <el-table-column label="数据资源格式" width="110">
          <template #default="{ row }">{{ formatLabel(row.resourceFormat) }}</template>
        </el-table-column>
        <el-table-column prop="providerOrg" label="提供方" width="110" show-overflow-tooltip />
        <el-table-column prop="metadataEntryCode" label="元数据" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.metadataEntryCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            {{ row.sourcePathType === 'PROCESSED' ? '加工' : row.sourcePathType === 'DIRECT' ? '直通' : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="statusTagType(catalogResourceStatusCode(row.approvalStatus, row.publishStatus))"
            >
              {{ catalogResourceStatusLabel(row.approvalStatus, row.publishStatus) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-model:page="unboundPage"
        v-model:page-size="unboundPageSize"
        :total="unboundTotal"
      />
      <el-empty v-if="!unboundRows.length" description="暂无未挂载资源" />
    </div>

    <el-dialog v-model="bindDialogVisible" title="关联分类" width="480px" destroy-on-close append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="`已选 ${selectedUnbound.length} 条未挂载资源，请选择要挂载的数据资源分类。`"
        style="margin-bottom: 12px"
      />
      <el-form label-width="110px">
        <el-form-item label="数据资源分类" required>
          <el-select v-model="bindCategoryId" filterable placeholder="请选择分类" style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindSubmitting" @click="confirmBind">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="viewVisible" :title="`查看 · ${viewRow?.resourceName || ''}`" size="640px" destroy-on-close>
      <el-descriptions v-if="viewRow" :column="1" border size="small">
        <el-descriptions-item label="编码">{{ viewRow.resourceCode }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ viewRow.resourceName }}</el-descriptions-item>
        <el-descriptions-item label="数据资源格式">{{ formatLabel(viewRow.resourceFormat) }}</el-descriptions-item>
        <el-descriptions-item label="分类路径">{{ viewRow.categoryPath || '—' }}</el-descriptions-item>
        <el-descriptions-item label="提供方">{{ viewRow.providerOrg || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ catalogResourceStatusLabel(viewRow.approvalStatus, viewRow.publishStatus) }}
        </el-descriptions-item>
        <el-descriptions-item label="元数据">{{ viewRow.metadataEntryCode || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div v-loading="approvalFlowLoading" style="margin-top: 16px">
        <div style="font-weight: 600; margin-bottom: 8px">审批流程</div>
        <el-table :data="registerFlowSteps" stripe size="small" empty-text="暂无审批记录">
          <el-table-column prop="step" label="环节" width="80" />
          <el-table-column label="操作类型" width="90">
            <template #default="{ row }">{{ ACTION_FLOW_ZH[row.actionType || ''] || statusLabel(row.actionType) || '—' }}</template>
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
            <template #default="{ row }">{{ row.time ? String(row.time).replace('T', ' ').slice(0, 19) : '—' }}</template>
          </el-table-column>
          <el-table-column prop="comment" label="结果/意见" min-width="120" show-overflow-tooltip />
        </el-table>
      </div>
    </el-drawer>

    <el-drawer v-model="versionDrawerVisible" :title="`版本历史 · ${versionResource?.resourceName || ''}`" size="520px">
      <el-table :data="versions" stripe size="small">
        <el-table-column prop="versionNo" label="版本" width="70" />
        <el-table-column prop="changeSummary" label="摘要" min-width="120" show-overflow-tooltip />
        <el-table-column prop="publishedBy" label="发布人" width="90" />
        <el-table-column prop="publishedAt" label="时间" width="150" />
      </el-table>
    </el-drawer>
  </PageCard>
</template>

<style scoped>
.reg-main h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
.reg-main h4 .sub {
  font-weight: 400;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 6px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
