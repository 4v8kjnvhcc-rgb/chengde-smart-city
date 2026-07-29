<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { ingestionApi, useIngestionLoading, type AssetCatalogReg } from '../useIngestionHub'
import AssetCatalogFormDialog from './AssetCatalogFormDialog.vue'

defineProps<{ module: string }>()

const { loading, loadError, withLoad } = useIngestionLoading()
const rows = ref<AssetCatalogReg[]>([])
const selected = ref<AssetCatalogReg[]>([])
const query = reactive({
  assetName: '',
  orgName: '',
  projectName: '',
  status: '',
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit' | 'view'>('create')
const editingId = ref<number | null>(null)

async function reload() {
  await withLoad(async () => {
    rows.value = (await ingestionApi.assetCatalogList({
      assetName: query.assetName || undefined,
      orgName: query.orgName || undefined,
      projectName: query.projectName || undefined,
      status: query.status || undefined,
    })).data || []
  })
}

function resetQuery() {
  query.assetName = ''
  query.orgName = ''
  query.projectName = ''
  query.status = ''
  void reload()
}

function onSelectionChange(list: AssetCatalogReg[]) {
  selected.value = list
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  dialogVisible.value = true
}

function openEdit() {
  if (selected.value.length !== 1) {
    ElMessage.warning('请选择一条记录进行编辑')
    return
  }
  const row = selected.value[0]
  if (!['DRAFT', 'REJECTED'].includes(row.status)) {
    ElMessage.warning('仅草稿或已驳回状态可编辑')
    return
  }
  dialogMode.value = 'edit'
  editingId.value = row.id
  dialogVisible.value = true
}

async function doDelete() {
  if (selected.value.length !== 1) {
    ElMessage.warning('请选择一条记录删除')
    return
  }
  const row = selected.value[0]
  if (row.status !== 'DRAFT') {
    ElMessage.warning('仅草稿状态可删除')
    return
  }
  await ElMessageBox.confirm(`确认删除资产「${row.assetName}」？`, '删除确认', { type: 'warning' })
  await ingestionApi.assetCatalogDelete(row.id)
  ElMessage.success('已删除')
  selected.value = []
  await reload()
}

async function doReport() {
  if (selected.value.length !== 1) {
    ElMessage.warning('请选择一条记录上报')
    return
  }
  const row = selected.value[0]
  if (!['DRAFT', 'REJECTED'].includes(row.status)) {
    ElMessage.warning('仅草稿或已驳回状态可上报')
    return
  }
  await ElMessageBox.confirm(`确认上报资产「${row.assetName}」？上报后将进入待归档。`, '上报确认', { type: 'info' })
  await ingestionApi.assetCatalogReport(row.id)
  ElMessage.success('已上报')
  await reload()
}

function formatTime(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  void reload()
})
</script>

<template>
  <PageCard title="资产目录登记">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="资产名称" class="portal-field-md">
        <el-input v-model="query.assetName" clearable placeholder="资产名称" @keyup.enter="reload" />
      </el-form-item>
      <el-form-item label="所属机构" class="portal-field-md">
        <el-input v-model="query.orgName" clearable placeholder="所属机构" @keyup.enter="reload" />
      </el-form-item>
      <el-form-item label="来源项目" class="portal-field-md">
        <el-input v-model="query.projectName" clearable placeholder="来源项目" @keyup.enter="reload" />
      </el-form-item>
      <el-form-item label="上报状态" class="portal-field-sm">
        <el-select v-model="query.status" clearable placeholder="全部">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待归档" value="PENDING_ARCHIVE" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新增</el-button>
      <el-button :disabled="selected.length !== 1" @click="openEdit">编辑</el-button>
      <el-button :disabled="selected.length !== 1" @click="doDelete">删除</el-button>
      <el-button type="success" :disabled="selected.length !== 1" @click="doReport">上报</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError" style="margin-bottom: 12px" />

    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      size="small"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column prop="assetName" label="资产名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="orgName" label="所属机构" min-width="140" show-overflow-tooltip />
      <el-table-column prop="projectName" label="来源项目" min-width="140" show-overflow-tooltip />
      <el-table-column prop="systemName" label="来源系统" min-width="140" show-overflow-tooltip />
      <el-table-column prop="tableName" label="来源表" min-width="140" show-overflow-tooltip />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <AssetCatalogFormDialog
      v-model="dialogVisible"
      :mode="dialogMode"
      :record-id="editingId"
      @saved="reload"
    />
  </PageCard>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
</style>
