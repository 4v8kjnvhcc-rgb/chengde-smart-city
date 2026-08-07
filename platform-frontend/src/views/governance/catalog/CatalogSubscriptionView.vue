<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface SubRow {
  id: number
  resourceId: number
  resourceCode?: string
  resourceName?: string
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

const activeTab = ref('mine')
const mineRows = ref<SubRow[]>([])
const pendingRows = ref<SubRow[]>([])
const loading = ref(false)
const statusFilter = ref('')
const minePage = ref(1)
const minePageSize = ref(10)
const pendingPage = ref(1)
const pendingPageSize = ref(10)
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

function shareLabel(mode?: string) {
  if (!mode) return '—'
  return SHARE_ZH[mode] || statusLabel(mode)
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

async function load() {
  if (activeTab.value === 'mine') await loadMine()
  else await loadPending()
}

function openDetail(row: SubRow, mode: 'mine' | 'pending') {
  subDetail.row = row
  subDetail.mode = mode
  subDetail.visible = true
  reviewNote.value = ''
}

function payloadEntries(row: SubRow | null): { label: string; value: string }[] {
  if (!row) return []
  const base: { label: string; value: string }[] = [
    { label: '资源名称', value: row.resourceName || String(row.resourceId || '—') },
    { label: '资源编码', value: row.resourceCode || '—' },
    { label: '共享方式', value: shareLabel(row.shareMode) },
    { label: '申请单位', value: row.applicantOrg || '—' },
    { label: '申请人', value: row.applicantUser || '—' },
    { label: '用途/场景', value: row.purpose || '—' },
    { label: '状态', value: statusLabel(row.status) },
    { label: '申请时间', value: row.createdAt ? String(row.createdAt).replace('T', ' ').slice(0, 19) : '—' },
  ]
  if (row.reviewComment) base.push({ label: '审批意见', value: row.reviewComment })
  if (row.authorization?.authorizationCode) {
    base.push({ label: '授权码', value: row.authorization.authorizationCode })
  }
  if (row.distributeResult) base.push({ label: '分发结果', value: row.distributeResult })

  const p = row.applyPayload
  const obj = typeof p === 'string'
    ? (() => { try { return JSON.parse(p) as Record<string, unknown> } catch { return null } })()
    : p
  if (obj && typeof obj === 'object') {
    const map: Record<string, string> = {
      contactName: '联系人',
      contactPhone: '联系电话',
      contactEmail: '联系邮箱',
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
    for (const [k, label] of Object.entries(map)) {
      if (obj[k] != null && String(obj[k]).trim() !== '') {
        base.push({ label, value: String(obj[k]) })
      }
    }
  }
  return base
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
    const { value } = await ElMessageBox.prompt('请填写驳回意见', '驳回订阅', {
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
  await ElMessageBox.confirm('确认取消该订阅申请？', '取消申请', { type: 'warning' })
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

watch(activeTab, () => {
  void load()
})
onMounted(load)
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
        <p class="hint">仅可查看详情；通过后可在详情中分发。点击行查看申请内容。</p>
        <el-table
          v-loading="loading"
          :data="pagedMine"
          stripe
          size="small"
          class="clickable-table"
          @row-click="(row: SubRow) => openDetail(row, 'mine')"
        >
          <el-table-column prop="resourceCode" label="资源编码" width="130" />
          <el-table-column label="资源名称" min-width="160">
            <template #default="{ row }">
              <button type="button" class="link-title" @click.stop="openDetail(row, 'mine')">
                {{ row.resourceName || row.resourceId }}
              </button>
            </template>
          </el-table-column>
          <el-table-column label="共享方式" width="100">
            <template #default="{ row }">{{ shareLabel(row.shareMode) }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="申请时间" width="160" />
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
          <el-table-column label="资源" min-width="160">
            <template #default="{ row }">
              <button type="button" class="link-title" @click.stop="openDetail(row, 'pending')">
                {{ row.resourceName || row.resourceCode || row.resourceId }}
              </button>
            </template>
          </el-table-column>
          <el-table-column prop="applicantOrg" label="申请单位" width="140" />
          <el-table-column label="共享方式" width="100">
            <template #default="{ row }">{{ shareLabel(row.shareMode) }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="120" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="申请时间" width="160" />
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
    </el-tabs>

    <el-drawer
      v-model="subDetail.visible"
      :title="subDetail.mode === 'mine' ? '申请详情' : '审批详情'"
      size="520px"
    >
      <template v-if="subDetail.row">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item v-for="(it, i) in payloadEntries(subDetail.row)" :key="i" :label="it.label">
            {{ it.value }}
          </el-descriptions-item>
        </el-descriptions>

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
.sub-detail-ops {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #eef1f6;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
