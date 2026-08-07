<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { useAuthStore } from '@/stores/auth'
import { statusTagType } from '@/utils/status-label'
import {
  ingestionApi,
  useIngestionLoading,
  type AssetCatalogReg,
  type Project,
} from '../useIngestionHub'
import {
  canEditRegister,
  canSubmitRegister,
  canWithdrawRegister,
  registerStatusZh,
  submitRegister,
  withdrawRegister,
} from './register-workflow'
import AssetCatalogFormDialog from './AssetCatalogFormDialog.vue'

defineProps<{ module: string }>()

const auth = useAuthStore()
const { loading, loadError, withLoad } = useIngestionLoading()
const rows = ref<AssetCatalogReg[]>([])
const query = reactive({
  assetName: '',
  orgName: '',
  projectName: '',
  status: '',
})

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit' | 'view'>('create')
const editingId = ref<number | null>(null)
const actingId = ref<number | null>(null)
const orgOptions = ref<Array<{ id: number; orgName: string; label: string }>>([])
const projectOptions = ref<Project[]>([])

function normStatus(status?: string | null) {
  return (status || 'DRAFT').trim().toUpperCase()
}

function isApproved(status?: string | null) {
  const s = normStatus(status)
  return s === 'APPROVED' || s === 'ARCHIVED'
}

/** 草稿/驳回待提交：均可删；其它状态：平台/超级管理员可删 */
function canDelete(row: AssetCatalogReg) {
  if (auth.isPlatformOrSystemAdmin) return true
  const s = normStatus(row.status)
  return s === 'DRAFT' || s === 'REJECTED'
}

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

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row: AssetCatalogReg) {
  if (!canEditRegister(row.status)) {
    ElMessage.warning('仅草稿或驳回待提交状态可编辑')
    return
  }
  dialogMode.value = 'edit'
  editingId.value = row.id
  dialogVisible.value = true
}

function openView(row: AssetCatalogReg) {
  dialogMode.value = 'view'
  editingId.value = row.id
  dialogVisible.value = true
}

async function doSubmit(row: AssetCatalogReg) {
  if (!canSubmitRegister(row.status)) {
    ElMessage.warning('仅草稿或驳回待提交可提交审核')
    return
  }
  await ElMessageBox.confirm(`确认提交资产「${row.assetName}」审核？`, '提交确认', { type: 'info' })
  actingId.value = row.id
  try {
    await submitRegister('CATALOG_REG', row.id)
    ElMessage.success('已提交审核')
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  } finally {
    actingId.value = null
  }
}

async function doWithdraw(row: AssetCatalogReg) {
  if (!canWithdrawRegister(row.status)) {
    ElMessage.warning('仅待审核状态可撤销')
    return
  }
  await ElMessageBox.confirm(`确认撤销「${row.assetName}」的审核提交？撤销后状态将回到草稿。`, '撤销确认', {
    type: 'warning',
  })
  actingId.value = row.id
  try {
    await withdrawRegister('CATALOG_REG', row.id)
    ElMessage.success('已撤销，状态为草稿')
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '撤销失败')
  } finally {
    actingId.value = null
  }
}

async function doDelete(row: AssetCatalogReg) {
  if (!canDelete(row)) {
    ElMessage.warning('当前状态不可删除；待审核/审核通过需平台管理员或超级管理员删除')
    return
  }
  const tip = !canEditRegister(row.status)
    ? `确认以平台/超级管理员身份删除「${row.assetName}」？`
    : `确认删除资产「${row.assetName}」？`
  await ElMessageBox.confirm(tip, '删除确认', { type: 'warning' })
  actingId.value = row.id
  try {
    await ingestionApi.assetCatalogDelete(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  } finally {
    actingId.value = null
  }
}

function formatTime(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

async function loadFilterOptions() {
  try {
    orgOptions.value = (await ingestionApi.assetCatalogOrgOptions()).data || []
  } catch {
    orgOptions.value = []
  }
  try {
    projectOptions.value = (await ingestionApi.projects()).data || []
  } catch {
    projectOptions.value = []
  }
}

onMounted(() => {
  void loadFilterOptions()
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
        <el-select v-model="query.orgName" clearable filterable placeholder="全部机构" style="width:100%">
          <el-option
            v-for="o in orgOptions"
            :key="o.id"
            :label="o.orgName || o.label"
            :value="o.orgName || o.label"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="来源项目" class="portal-field-md">
        <el-select v-model="query.projectName" clearable filterable placeholder="全部项目" style="width:100%">
          <el-option
            v-for="p in projectOptions"
            :key="p.id"
            :label="p.projectName"
            :value="p.projectName"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" class="portal-field-sm">
        <el-select v-model="query.status" clearable placeholder="全部">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待审核" value="PENDING_REVIEW" />
          <el-option label="审核通过" value="APPROVED" />
          <el-option label="驳回待提交" value="REJECTED" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="reload">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新增</el-button>
    </div>

    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError" style="margin-bottom: 12px" />

    <el-table v-loading="loading" :data="rows" stripe size="small">
      <el-table-column prop="assetName" label="资产名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="orgName" label="所属机构" min-width="140" show-overflow-tooltip />
      <el-table-column prop="projectName" label="来源项目" min-width="140" show-overflow-tooltip />
      <el-table-column prop="systemName" label="来源系统" min-width="140" show-overflow-tooltip />
      <el-table-column prop="tableName" label="来源表" min-width="140" show-overflow-tooltip />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ registerStatusZh(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openView(row)">查看</el-button>
          <el-button
            v-if="canEditRegister(row.status)"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="canSubmitRegister(row.status)"
            link
            type="primary"
            :loading="actingId === row.id"
            @click="doSubmit(row)"
          >
            提交
          </el-button>
          <el-button
            v-if="canWithdrawRegister(row.status)"
            link
            type="warning"
            :loading="actingId === row.id"
            @click="doWithdraw(row)"
          >
            撤销
          </el-button>
          <el-button
            v-if="canDelete(row)"
            link
            type="danger"
            :loading="actingId === row.id"
            @click="doDelete(row)"
          >
            删除
          </el-button>
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
