<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type CatalogApproval } from '../../useIngestionHub'

const ACTION_ZH: Record<string, string> = {
  PUBLISH: '发布',
  OFFLINE: '下线',
  DELETE: '删除',
  BIND: '关联',
  UNBIND: '解除关联',
}

const { loading, loadError, withLoad } = useIngestionLoading()
const rows = ref<CatalogApproval[]>([])
const statusFilter = ref('PENDING')
const selected = ref<CatalogApproval[]>([])

async function load() {
  await withLoad(async () => {
    const res = await ingestionApi.catalogApprovals({
      status: statusFilter.value || undefined,
    })
    rows.value = res.data || []
  })
}

async function approve(row: CatalogApproval) {
  await ingestionApi.approveCatalog(row.id, { comment: '四性校验通过，同意' })
  ElMessage.success('已通过（发布将同步门户）')
  await load()
}

async function reject(row: CatalogApproval) {
  const { value } = await ElMessageBox.prompt('请填写拒绝意见', '审批拒绝', {
    inputPattern: /\S+/,
    inputErrorMessage: '意见不能为空',
  })
  await ingestionApi.rejectCatalog(row.id, { comment: value })
  ElMessage.success('已拒绝')
  await load()
}

async function batchApprove() {
  const ids = selected.value.filter((r) => r.status === 'PENDING').map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('请勾选待处理审批')
    return
  }
  await ElMessageBox.confirm(`确认批量通过 ${ids.length} 条？`, '批量通过', { type: 'warning' })
  const res = await ingestionApi.batchApproveCatalog({ ids, comment: '批量四性校验通过' })
  ElMessage.success(`已通过 ${res.data?.approved || 0} 条`)
  selected.value = []
  await load()
}

async function batchReject() {
  const ids = selected.value.filter((r) => r.status === 'PENDING').map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('请勾选待处理审批')
    return
  }
  const { value } = await ElMessageBox.prompt('请填写拒绝意见（应用于所选）', '批量拒绝', {
    inputPattern: /\S+/,
    inputErrorMessage: '意见不能为空',
  })
  const res = await ingestionApi.batchRejectCatalog({ ids, comment: value })
  ElMessage.success(`已拒绝 ${res.data?.rejected || 0} 条`)
  selected.value = []
  await load()
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据资源目录审批">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="对数据唯一性、完整性、关联性、表述性进行校验；通过后非涉密目录自动展示到部门数据共享门户。"
      />
      <el-form inline>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" clearable placeholder="全部" style="width:120px" @change="load">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">刷新</el-button>
          <el-button type="success" :disabled="!selected.length" @click="batchApprove">批量通过</el-button>
          <el-button type="danger" plain :disabled="!selected.length" @click="batchReject">批量拒绝</el-button>
        </el-form-item>
      </el-form>
      <el-table
        :data="rows"
        border
        stripe
        @selection-change="(v: CatalogApproval[]) => (selected = v)"
      >
        <el-table-column type="selection" width="42" :selectable="(row: CatalogApproval) => row.status === 'PENDING'" />
        <el-table-column prop="resourceCode" label="资源编码" width="130" />
        <el-table-column prop="resourceName" label="资源名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作类型" width="90">
          <template #default="{ row }">{{ ACTION_ZH[row.actionType] || row.actionType }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="submittedBy" label="提交人" width="110" />
        <el-table-column prop="submittedAt" label="提交时间" width="170" />
        <el-table-column prop="reviewComment" label="审批意见" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button link type="success" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="reject(row)">拒绝</el-button>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
