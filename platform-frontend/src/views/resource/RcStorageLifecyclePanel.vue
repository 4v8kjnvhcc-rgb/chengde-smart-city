<script setup lang="ts">
/**
 * 资源中心存储生命周期：备份 / 归档 / 销毁（同一套 rc policies API）。
 * 归集「数据资产管理」与资源中心存储管理可复用；销毁禁止自动物理删除。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import { statusLabel, statusTagType } from '@/utils/status-label'

const props = defineProps<{
  mode: 'backup' | 'archive' | 'destroy'
}>()

interface ManagedTable {
  id: number
  physicalTable: string
}
interface Policy {
  id: number
  policyName: string
  actionType: string
  retentionDays?: number
  managedTableId?: number
  status?: string
}
interface Artifact {
  id: number
  physicalTable?: string
  fileName?: string
  rowCount?: number
  status?: string
}

const loading = ref(false)
const managedTables = ref<ManagedTable[]>([])
const policies = ref<Policy[]>([])
const artifacts = ref<Artifact[]>([])
const lastRun = ref<Record<string, unknown> | null>(null)

const form = reactive({
  policyName: '',
  retentionDays: 30,
  managedTableId: undefined as number | undefined,
})

const actionType = computed(() =>
  props.mode === 'backup' ? 'BACKUP' : props.mode === 'archive' ? 'ARCHIVE' : 'DESTROY',
)

const filtered = computed(() => policies.value.filter((p) => p.actionType === actionType.value))

async function reload() {
  loading.value = true
  try {
    const [tables, pols, overview] = await Promise.all([
      api.get('/resource-center/platform/managed-tables'),
      api.get('/resource-center/platform/policies', { params: { actionType: actionType.value } }),
      api.get('/resource-center/platform/partition/overview'),
    ])
    managedTables.value = tables.data || []
    policies.value = pols.data || []
    artifacts.value = (overview.data?.artifacts as Artifact[]) || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function createPolicy() {
  if (!form.policyName.trim()) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if ((actionType.value === 'BACKUP' || actionType.value === 'ARCHIVE') && !form.managedTableId) {
    ElMessage.warning('请选择关联纳管表')
    return
  }
  await api.post('/resource-center/platform/policies', {
    policyName: form.policyName,
    actionType: actionType.value,
    retentionDays: form.retentionDays,
    managedTableId: form.managedTableId,
  })
  ElMessage.success('策略已创建')
  form.policyName = ''
  await reload()
}

async function runPolicy(id: number) {
  try {
    const res = await api.post(`/resource-center/platform/policies/${id}/execute`)
    lastRun.value = res.data
    const st = String(res.data?.status || '')
    if (st === 'LEDGER') {
      ElMessage.warning(String(res.data?.message || '已记台账，未改物理数据'))
    } else {
      ElMessage.success(String(res.data?.message || '策略已执行'))
    }
    await reload()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '策略执行失败（销毁场景可能被策略拒绝）')
  }
}

async function runBackup(tableId: number) {
  const res = await api.post(`/resource-center/platform/managed-tables/${tableId}/backup`, {
    retentionDays: form.retentionDays || 30,
  })
  ElMessage.success(`备份完成：${res.data?.rowCount ?? '-'} 行`)
  await reload()
}

async function verifyArtifact(id: number) {
  const res = await api.get(`/resource-center/platform/backups/artifacts/${id}/verify`)
  ElMessage.success(res.data?.match ? '校验通过' : '校验失败')
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert
      v-if="mode === 'archive'"
      type="info"
      :closable="false"
      title="归档执行仅记台账，不移动物理数据；状态为「台账」而非假成功。"
      style="margin-bottom:12px"
    />
    <el-alert
      v-if="mode === 'destroy'"
      type="warning"
      :closable="false"
      title="销毁策略可配置，但禁止自动物理删除；执行将被拒绝。"
      style="margin-bottom:12px"
    />
    <el-alert
      v-if="lastRun"
      :type="lastRun.status === 'LEDGER' ? 'warning' : 'success'"
      :closable="true"
      style="margin-bottom:12px"
      :title="`最近执行：${statusLabel(String(lastRun.actionType || ''))} · ${statusLabel(String(lastRun.status || ''))}`"
      @close="lastRun = null"
    >
      {{ lastRun.message || `产物行数 ${lastRun.rowCount ?? '-'}` }}
    </el-alert>

    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="策略名" class="portal-field-md">
        <el-input
          v-model="form.policyName"
          :placeholder="mode === 'backup' ? '备份策略名称' : mode === 'archive' ? '归档策略名称' : '销毁策略名称'"
        />
      </el-form-item>
      <el-form-item label="纳管表" class="portal-field-lg">
        <el-select v-model="form.managedTableId" filterable clearable :placeholder="mode === 'backup' ? '选择备份目标表' : '选择纳管表'">
          <el-option v-for="t in managedTables" :key="t.id" :label="t.physicalTable" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="mode !== 'destroy'" label="保留天" class="portal-field-xs">
        <el-input-number v-model="form.retentionDays" :min="1" :max="3650" controls-position="right" />
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button
          :type="mode === 'destroy' ? 'danger' : 'primary'"
          @click="createPolicy"
        >
          {{ mode === 'backup' ? '创建备份策略' : mode === 'archive' ? '创建归档策略' : '创建销毁策略' }}
        </el-button>
      </el-form-item>
    </el-form>

    <el-divider v-if="mode === 'backup'" content-position="left">备份策略</el-divider>
    <el-table :data="filtered" stripe size="small">
      <el-table-column prop="policyName" label="策略" />
      <el-table-column v-if="mode !== 'destroy'" prop="retentionDays" label="保留天" width="80" />
      <el-table-column v-if="mode === 'destroy'" label="动作" width="100">
        <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
      </el-table-column>
      <el-table-column label="操作" :width="mode === 'backup' ? 160 : 100">
        <template #default="{ row }">
          <el-button
            v-if="mode === 'backup'"
            link
            type="primary"
            @click="runPolicy(row.id)"
          >执行备份</el-button>
          <el-button
            v-if="mode === 'backup' && row.managedTableId"
            link
            type="primary"
            @click="runBackup(row.managedTableId!)"
          >立即备份</el-button>
          <el-button
            v-if="mode === 'archive'"
            link
            type="primary"
            @click="runPolicy(row.id)"
          >执行归档</el-button>
          <el-button
            v-if="mode === 'destroy'"
            link
            type="danger"
            @click="runPolicy(row.id)"
          >尝试执行</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template v-if="mode === 'backup'">
      <el-divider content-position="left">备份产物</el-divider>
      <el-table :data="artifacts" stripe size="small">
        <el-table-column prop="physicalTable" label="表" width="180" />
        <el-table-column prop="fileName" label="文件" min-width="160" show-overflow-tooltip />
        <el-table-column prop="rowCount" label="行数" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" @click="verifyArtifact(row.id)">校验</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>
