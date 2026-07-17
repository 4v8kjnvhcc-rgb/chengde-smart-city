<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
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
}

const activeTab = ref('mine')
const mineRows = ref<SubRow[]>([])
const pendingRows = ref<SubRow[]>([])
const loading = ref(false)
const statusFilter = ref('')

async function loadMine() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/subscriptions', {
      params: { status: statusFilter.value || undefined },
    })
    mineRows.value = res.data || []
  } catch {
    ElMessage.error('加载我的申请失败')
  } finally {
    loading.value = false
  }
}

async function loadPending() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/subscriptions/pending')
    pendingRows.value = res.data || []
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

async function approve(row: SubRow) {
  await api.post(`/governance/catalog/subscriptions/${row.id}/approve`, { comment: '同意' })
  ElMessage.success('已通过')
  await load()
}

async function reject(row: SubRow) {
  const { value } = await ElMessageBox.prompt('请填写驳回意见', '驳回订阅', {
    confirmButtonText: '驳回',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '意见不能为空',
  })
  await api.post(`/governance/catalog/subscriptions/${row.id}/reject`, { comment: value })
  ElMessage.success('已驳回')
  await load()
}

async function cancel(row: SubRow) {
  await ElMessageBox.confirm('确认取消该订阅申请？', '取消申请', { type: 'warning' })
  await api.post(`/governance/catalog/subscriptions/${row.id}/cancel`)
  ElMessage.success('已取消')
  await load()
}

async function distribute(row: SubRow) {
  const res = await api.post(`/governance/catalog/subscriptions/${row.id}/distribute`)
  ElMessage.success(res.data?.distributeResult || '分发完成')
  await load()
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

watch(activeTab, load)
onMounted(load)
</script>

<template>
  <PageCard title="资源订阅">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="我的申请" name="mine">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="状态" class="portal-field-sm">
            <el-select v-model="statusFilter" clearable placeholder="全部" @change="loadMine">
              <el-option label="待处理" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
              <el-option label="已取消" value="CANCELLED" />
              <el-option label="已分发" value="DISTRIBUTED" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadMine">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="loading" :data="mineRows" stripe size="small">
          <el-table-column prop="resourceCode" label="资源编码" width="130" />
          <el-table-column prop="resourceName" label="资源名称" min-width="140" />
          <el-table-column label="共享方式" width="100">
            <template #default="{ row }">{{ SHARE_ZH[row.shareMode] || row.shareMode }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="授权码" width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.authorization?.authorizationCode || '—' }}</template>
          </el-table-column>
          <el-table-column label="授权状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.authorization" size="small" :type="statusTagType(row.authorization.status)">
                {{ statusLabel(row.authorization.status) }}
              </el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="申请时间" width="160" />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button link type="danger" @click="cancel(row)">取消</el-button>
              </template>
              <template v-else-if="row.status === 'APPROVED' || row.status === 'DISTRIBUTED'">
                <el-button link type="primary" @click="distribute(row)">分发</el-button>
                <el-button link @click="showResult(row)">分发结果</el-button>
                <el-button v-if="row.shareMode === 'API'" link @click="testApi(row)">测试接口</el-button>
              </template>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="待我审批" name="pending" lazy>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadPending">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-table v-loading="loading" :data="pendingRows" stripe size="small">
          <el-table-column prop="resourceCode" label="资源编码" width="130" />
          <el-table-column prop="resourceName" label="资源名称" min-width="140" />
          <el-table-column label="共享方式" width="100">
            <template #default="{ row }">{{ SHARE_ZH[row.shareMode] || row.shareMode }}</template>
          </el-table-column>
          <el-table-column prop="applicantOrg" label="申请机构" width="120" />
          <el-table-column prop="applicantUser" label="申请人" width="100" />
          <el-table-column prop="purpose" label="用途" min-width="120" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="申请时间" width="160" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="reject(row)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </PageCard>
</template>
