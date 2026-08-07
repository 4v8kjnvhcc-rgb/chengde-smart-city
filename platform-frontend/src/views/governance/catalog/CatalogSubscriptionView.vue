<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import {
  listFavorites,
  removeFavorite,
  type PortalFavorite,
} from '@/views/exchange/application/portal-favorites'

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

const route = useRoute()
const router = useRouter()
const activeTab = ref('mine')
const mineRows = ref<SubRow[]>([])
const pendingRows = ref<SubRow[]>([])
const favoriteRows = ref<PortalFavorite[]>([])
const loading = ref(false)
const statusFilter = ref('')
const minePage = ref(1)
const minePageSize = ref(10)
const pendingPage = ref(1)
const pendingPageSize = ref(10)
const favPage = ref(1)
const favPageSize = ref(10)
const reviewNote = ref('')

const subDetail = reactive<{
  visible: boolean
  mode: 'mine' | 'pending'
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
const pagedFav = computed(() => {
  const start = (favPage.value - 1) * favPageSize.value
  return favoriteRows.value.slice(start, start + favPageSize.value)
})

function shareLabel(mode?: string) {
  if (!mode) return '—'
  return SHARE_ZH[mode] || statusLabel(mode)
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

function payloadVal(obj: Record<string, unknown>, key: string) {
  const v = obj[key]
  if (v == null || String(v).trim() === '') return ''
  return String(v)
}

function fmtTime(v?: string) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : '—'
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

function loadFavorites() {
  favoriteRows.value = listFavorites('GOV')
  favPage.value = 1
}

async function load() {
  if (activeTab.value === 'mine') await loadMine()
  else if (activeTab.value === 'pending') await loadPending()
  else loadFavorites()
}

function syncTabFromRoute() {
  const t = String(route.query.subTab || '')
  if (t === 'favorites' || t === 'pending' || t === 'mine') {
    activeTab.value = t === 'favorites' ? 'favorites' : t
  }
}

function openDetail(row: SubRow, mode: 'mine' | 'pending') {
  subDetail.row = row
  subDetail.mode = mode
  subDetail.visible = true
  reviewNote.value = ''
}

async function approve(row: SubRow) {
  await api.post(`/governance/catalog/subscriptions/${row.id}/approve`, {
    comment: reviewNote.value || '同意',
  })
  ElMessage.success('已通过')
  subDetail.visible = false
  await loadPending()
}

async function reject(row: SubRow) {
  let comment = reviewNote.value
  if (!comment?.trim()) {
    const { value } = await ElMessageBox.prompt('请填写驳回意见', '驳回申请', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '意见不能为空',
    })
    comment = value
  }
  await api.post(`/governance/catalog/subscriptions/${row.id}/reject`, { comment })
  ElMessage.success('已驳回')
  subDetail.visible = false
  await loadPending()
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

function cancelFavorite(row: PortalFavorite) {
  removeFavorite(row.catalogId, 'GOV')
  loadFavorites()
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

watch(activeTab, (t) => {
  const q = { ...route.query, subTab: t }
  router.replace({ query: q })
  void load()
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
  if (activeTab.value === 'favorites') loadFavorites()
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
        <p class="hint">本部门提交的资源申请。点击行查看完整申请单（联系人、办事场景、依据等）。</p>
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
        <p class="hint">本部门作为资源提供方的待审申请。点击行查看完整申请内容后再通过/驳回。</p>
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

      <el-tab-pane label="我的订阅" name="favorites" lazy>
        <p class="hint">在「资源目录门户」点击订阅后的收藏资源，可快速回看或取消订阅。</p>
        <el-empty v-if="!favoriteRows.length" description="暂无订阅，请到资源目录门户点击「订阅」" :image-size="72" />
        <el-table v-else :data="pagedFav" stripe size="small">
          <el-table-column prop="catalogCode" label="资源编码" width="130" show-overflow-tooltip />
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
        </section>

        <section class="detail-block">
          <h4>办理信息</h4>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="状态">{{ statusLabel(subDetail.row.status) }}</el-descriptions-item>
            <el-descriptions-item label="申请时间">{{ fmtTime(subDetail.row.createdAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.reviewedBy" label="审批人">{{ subDetail.row.reviewedBy }}</el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.reviewedAt" label="审批时间">{{ fmtTime(subDetail.row.reviewedAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.reviewComment" label="审批意见">{{ subDetail.row.reviewComment }}</el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.authorization?.authorizationCode" label="授权码">
              {{ subDetail.row.authorization.authorizationCode }}
            </el-descriptions-item>
            <el-descriptions-item v-if="subDetail.row.distributeResult" label="分发结果">{{ subDetail.row.distributeResult }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <div v-if="subDetail.mode === 'pending' && subDetail.row.status === 'PENDING'" class="sub-detail-ops">
          <el-input
            v-model="reviewNote"
            placeholder="审批意见（驳回时建议填写）"
            clearable
            style="margin-bottom: 12px"
          />
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
.sub-detail-ops {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #eef1f6;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
