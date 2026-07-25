<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface ApprovalRow {
  id: number
  resourceId: number
  resourceCode?: string
  resourceName?: string
  resourceType?: string
  publishStatus?: string
  actionType: string
  status: string
  submitComment?: string
  reviewComment?: string
  submittedBy?: string
  submittedAt?: string
  reviewedBy?: string
  reviewedAt?: string
}

const ACTION_ZH: Record<string, string> = {
  PUBLISH: '发布',
  OFFLINE: '下线',
  UPDATE: '变更',
  DELETE: '删除',
}

const rows = ref<ApprovalRow[]>([])
const loading = ref(false)
const statusFilter = ref('PENDING')
const selected = ref<ApprovalRow[]>([])

async function load() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt/approvals', {
      params: { status: statusFilter.value || undefined },
    })
    rows.value = res.data || []
  } catch {
    ElMessage.error('加载审批列表失败')
  } finally {
    loading.value = false
  }
}

async function approve(row: ApprovalRow) {
  await api.post(`/governance/catalog/resources-mgmt/approvals/${row.id}/approve`, {
    comment: '同意',
  })
  ElMessage.success('已通过')
  await load()
}

async function reject(row: ApprovalRow) {
  const { value } = await ElMessageBox.prompt('请填写驳回意见', '驳回审批', {
    confirmButtonText: '驳回',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '意见不能为空',
  })
  await api.post(`/governance/catalog/resources-mgmt/approvals/${row.id}/reject`, {
    comment: value,
  })
  ElMessage.success('已驳回')
  await load()
}

async function withdraw(row: ApprovalRow) {
  await ElMessageBox.confirm('确认撤回该审批申请？', '撤回', { type: 'warning' })
  await api.post(`/governance/catalog/resources-mgmt/approvals/${row.id}/withdraw`)
  ElMessage.success('已撤回')
  await load()
}

async function batchApprove() {
  const ids = selected.value.filter((r) => r.status === 'PENDING').map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('请勾选待处理审批')
    return
  }
  await ElMessageBox.confirm(`确认批量通过 ${ids.length} 条？`, '批量通过', { type: 'warning' })
  const res = await api.post('/governance/catalog/resources-mgmt/approvals/batch-approve', {
    ids,
    comment: '批量同意',
  })
  const d = res.data || {}
  ElMessage.success(`已通过 ${d.approved || 0} 条`)
  if (d.errors?.length) ElMessage.warning(d.errors.slice(0, 3).join('；'))
  selected.value = []
  await load()
}

async function batchReject() {
  const ids = selected.value.filter((r) => r.status === 'PENDING').map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('请勾选待处理审批')
    return
  }
  const { value } = await ElMessageBox.prompt('请填写驳回意见（将应用于所选）', '批量驳回', {
    inputPattern: /\S+/,
    inputErrorMessage: '意见不能为空',
  })
  const res = await api.post('/governance/catalog/resources-mgmt/approvals/batch-reject', {
    ids,
    comment: value,
  })
  const d = res.data || {}
  ElMessage.success(`已驳回 ${d.rejected || 0} 条`)
  if (d.errors?.length) ElMessage.warning(d.errors.slice(0, 3).join('；'))
  selected.value = []
  await load()
}

onMounted(load)
</script>

<template>
  <PageCard title="资源目录审批">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="状态" class="portal-field-sm">
        <el-select v-model="statusFilter" clearable placeholder="全部" @change="load">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已撤回" value="WITHDRAWN" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="load">刷新</el-button>
        <el-button type="success" :disabled="!selected.length" @click="batchApprove">批量通过</el-button>
        <el-button type="danger" plain :disabled="!selected.length" @click="batchReject">批量驳回</el-button>
      </el-form-item>
    </el-form>
    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      size="small"
      @selection-change="(rows: ApprovalRow[]) => (selected = rows)"
    >
      <el-table-column type="selection" width="42" :selectable="(row: ApprovalRow) => row.status === 'PENDING'" />
      <el-table-column prop="resourceCode" label="资源编码" width="130" />
      <el-table-column prop="resourceName" label="资源名称" min-width="140" />
      <el-table-column label="操作类型" width="90">
        <template #default="{ row }">{{ ACTION_ZH[row.actionType] || $statusLabel(row.actionType) }}</template>
      </el-table-column>
      <el-table-column label="审批状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submitComment" label="提交说明" min-width="120" show-overflow-tooltip />
      <el-table-column prop="submittedBy" label="提交人" width="100" />
      <el-table-column prop="submittedAt" label="提交时间" width="160" />
      <el-table-column prop="reviewComment" label="审批意见" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button link type="primary" @click="approve(row)">通过</el-button>
            <el-button link type="danger" @click="reject(row)">驳回</el-button>
            <el-button link @click="withdraw(row)">撤回</el-button>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
    </el-table>
  </PageCard>
</template>
