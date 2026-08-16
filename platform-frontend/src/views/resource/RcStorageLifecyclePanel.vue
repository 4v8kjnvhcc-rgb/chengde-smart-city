<script setup lang="ts">
/**
 * 资源中心存储生命周期：备份 / 归档 / 销毁（同一套 rc policies API）。
 * 备份模式：弹框新增/编辑 + 查看/执行/启停/删除；调度优先 DS，不可用时回退应用内定时。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'
import { useExecCycleLabel } from '@/utils/exec-cycle-label'

const props = defineProps<{
  mode: 'backup' | 'archive' | 'destroy'
}>()
const { label: cycleLabel } = useExecCycleLabel()

interface ManagedTable {
  id: number
  physicalTable: string
}
interface Library {
  id: number
  libName: string
  libType: string
}
interface Policy {
  id: number
  policyName: string
  policyCode?: string
  actionType: string
  retentionDays?: number
  managedTableId?: number
  storageStrategy?: string
  backupLibraryId?: number
  tableRule?: string
  compressEnabled?: number
  compressType?: string
  destroyRule?: string
  scheduleEnabled?: number
  scheduleCron?: string
  status?: string
  lastRunStatus?: string
  lastRunAt?: string
  lastRunMessage?: string
  dsPublishStatus?: string
  nextRunAt?: string
}
interface Artifact {
  id: number
  artifactType?: string
  physicalTable?: string
  fileName?: string
  filePath?: string
  storageLocation?: string
  rowCount?: number
  status?: string
  message?: string
  createdAt?: string
}

const loading = ref(false)
const saving = ref(false)
const managedTables = ref<ManagedTable[]>([])
const libraries = ref<Library[]>([])
const policies = ref<Policy[]>([])
const artifacts = ref<Artifact[]>([])
const lastRun = ref<Record<string, unknown> | null>(null)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit' | 'view'>('create')
const editingId = ref<number | null>(null)

const form = reactive({
  policyName: '',
  retentionDays: 30,
  managedTableId: undefined as number | undefined,
  storageStrategy: 'LOCAL',
  backupLibraryId: undefined as number | undefined,
  tableRule: '',
  backupScope: 'FULL' as 'FULL' | 'BY_TIME' | 'BY_PARTITION' | 'BY_BOTH',
  timeColumn: '',
  timeBeforeDays: 30,
  partitionName: '',
  compressEnabled: true,
  compressType: 'GZIP',
  destroyRule: '',
  scheduleEnabled: false,
  scheduleCron: '0 0 2 * * ?',
})

function encodeTableRule(): string | undefined {
  if (props.mode !== 'backup') return form.tableRule || undefined
  if (form.backupScope === 'FULL') return form.tableRule || undefined
  return JSON.stringify({
    v: 1,
    backupScope: form.backupScope,
    timeColumn: form.timeColumn || '',
    timeBeforeDays: form.timeBeforeDays,
    partitionName: form.partitionName || '',
    note: form.tableRule || '',
  })
}

function decodeTableRule(raw?: string) {
  form.backupScope = 'FULL'
  form.timeColumn = ''
  form.timeBeforeDays = form.retentionDays || 30
  form.partitionName = ''
  form.tableRule = ''
  if (!raw || !raw.trim()) return
  const t = raw.trim()
  if (t.startsWith('{')) {
    try {
      const o = JSON.parse(t) as Record<string, unknown>
      const scope = String(o.backupScope || 'FULL').toUpperCase()
      if (scope === 'BY_TIME' || scope === 'BY_PARTITION' || scope === 'BY_BOTH' || scope === 'FULL') {
        form.backupScope = scope as typeof form.backupScope
      }
      form.timeColumn = String(o.timeColumn || '')
      form.timeBeforeDays = Number(o.timeBeforeDays ?? form.retentionDays ?? 30) || 30
      form.partitionName = String(o.partitionName || '')
      form.tableRule = String(o.note || '')
      return
    } catch {
      /* fall through */
    }
  }
  form.tableRule = raw
}

const actionType = computed(() =>
  props.mode === 'backup' ? 'BACKUP' : props.mode === 'archive' ? 'ARCHIVE' : 'DESTROY',
)

const policyQuery = ref('')
const policyKeyword = ref('')
const artifactQuery = ref('')
const artifactKeyword = ref('')

const filtered = computed(() => {
  const list = policies.value.filter((p) => p.actionType === actionType.value)
  const kw = policyKeyword.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter((p) => {
    const table = tableName(p.managedTableId).toLowerCase()
    return (
      (p.policyName || '').toLowerCase().includes(kw)
      || (p.policyCode || '').toLowerCase().includes(kw)
      || table.includes(kw)
      || (p.tableRule || '').toLowerCase().includes(kw)
    )
  })
})

const filteredArtifacts = computed(() => {
  const type = props.mode === 'archive' ? 'ARCHIVE' : 'BACKUP'
  const list = artifacts.value.filter((a) => (a.artifactType || 'BACKUP') === type)
  const kw = artifactKeyword.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter((a) =>
    (a.physicalTable || '').toLowerCase().includes(kw)
    || (a.fileName || '').toLowerCase().includes(kw)
    || (a.storageLocation || '').toLowerCase().includes(kw)
    || (a.message || '').toLowerCase().includes(kw),
  )
})

const {
  page: policyPage,
  pageSize: policyPageSize,
  paged: pagedPolicies,
  total: policyTotal,
  resetPage: resetPolicyPage,
} = useClientPager(filtered)

const {
  page: artifactPage,
  pageSize: artifactPageSize,
  paged: pagedArtifacts,
  total: artifactTotal,
  resetPage: resetArtifactPage,
} = useClientPager(filteredArtifacts)

watch(policyKeyword, () => resetPolicyPage())
watch(artifactKeyword, () => resetArtifactPage())

const dialogTitle = computed(() => {
  const noun = props.mode === 'backup' ? '备份策略' : props.mode === 'archive' ? '归档策略' : '销毁策略'
  if (dialogMode.value === 'create') return `新增${noun}`
  if (dialogMode.value === 'edit') return `编辑${noun}`
  return `查看${noun}`
})

const formReadonly = computed(() => dialogMode.value === 'view')

const artifactDialogVisible = ref(false)
const artifactSaving = ref(false)
const editingArtifactId = ref<number | null>(null)
const artifactForm = reactive({
  physicalTable: '',
  fileName: '',
  storageLocation: '',
  message: '',
  status: 'SUCCESS',
})

const tableName = (id?: number) => {
  if (!id) return '-'
  return managedTables.value.find((t) => t.id === id)?.physicalTable || String(id)
}

const libName = (id?: number) => {
  if (!id) return '-'
  return libraries.value.find((l) => l.id === id)?.libName || String(id)
}

function doPolicyQuery() {
  policyKeyword.value = policyQuery.value
  resetPolicyPage()
}

function doPolicyReset() {
  policyQuery.value = ''
  policyKeyword.value = ''
  resetPolicyPage()
}

function doArtifactQuery() {
  artifactKeyword.value = artifactQuery.value
  resetArtifactPage()
}

function doArtifactReset() {
  artifactQuery.value = ''
  artifactKeyword.value = ''
  resetArtifactPage()
}

function resetForm() {
  form.policyName = ''
  form.retentionDays = 30
  form.managedTableId = undefined
  form.storageStrategy = 'LOCAL'
  form.backupLibraryId = undefined
  form.tableRule = ''
  form.backupScope = 'FULL'
  form.timeColumn = ''
  form.timeBeforeDays = 30
  form.partitionName = ''
  form.compressEnabled = true
  form.compressType = 'GZIP'
  form.destroyRule = ''
  form.scheduleEnabled = false
  form.scheduleCron = '0 0 2 * * ?'
}

function fillForm(row: Policy) {
  form.policyName = row.policyName || ''
  form.retentionDays = row.retentionDays ?? 30
  form.managedTableId = row.managedTableId
  form.storageStrategy = row.storageStrategy || 'LOCAL'
  form.backupLibraryId = row.backupLibraryId
  decodeTableRule(row.tableRule)
  form.compressEnabled = row.compressEnabled === 1 || row.compressType === 'GZIP'
  form.compressType = row.compressType || 'GZIP'
  form.destroyRule = row.destroyRule || ''
  form.scheduleEnabled = row.scheduleEnabled === 1
  form.scheduleCron = row.scheduleCron || '0 0 2 * * ?'
}

async function reload() {
  loading.value = true
  try {
    const [tables, pols, arts, libs] = await Promise.all([
      api.get('/resource-center/platform/managed-tables'),
      api.get('/resource-center/platform/policies', { params: { actionType: actionType.value } }),
      api.get('/resource-center/platform/backups/artifacts'),
      api.get('/resource-center/platform/libraries'),
    ])
    managedTables.value = tables.data || []
    policies.value = pols.data || []
    artifacts.value = arts.data || []
    libraries.value = libs.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: Policy) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  fillForm(row)
  dialogVisible.value = true
}

function openView(row: Policy) {
  dialogMode.value = 'view'
  editingId.value = row.id
  fillForm(row)
  dialogVisible.value = true
}

async function submitDialog() {
  if (formReadonly.value) {
    dialogVisible.value = false
    return
  }
  if (!form.policyName.trim()) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if (!form.managedTableId) {
    ElMessage.warning(props.mode === 'backup' ? '请选择备份目标表' : '请选择关联纳管表')
    return
  }
  if (props.mode === 'backup' && !form.storageStrategy) {
    ElMessage.warning('请选择存储策略')
    return
  }
  if (form.scheduleEnabled && !form.scheduleCron.trim()) {
    ElMessage.warning('启用周期调度时请填写 Cron')
    return
  }
  if (props.mode === 'backup' && form.backupScope !== 'FULL') {
    if ((form.backupScope === 'BY_TIME' || form.backupScope === 'BY_BOTH') && !form.timeColumn.trim()) {
      ElMessage.warning('按时间备份请填写时间列')
      return
    }
    if ((form.backupScope === 'BY_PARTITION' || form.backupScope === 'BY_BOTH') && !form.partitionName.trim()) {
      ElMessage.warning('按分区备份请填写分区名')
      return
    }
  }
  const body = {
    policyName: form.policyName,
    actionType: actionType.value,
    retentionDays: form.retentionDays,
    managedTableId: form.managedTableId,
    storageStrategy: form.storageStrategy,
    backupLibraryId: form.backupLibraryId,
    tableRule: encodeTableRule(),
    compressEnabled: props.mode === 'archive' ? form.compressEnabled : false,
    compressType: props.mode === 'archive' ? form.compressType : 'NONE',
    destroyRule: props.mode === 'destroy' ? form.destroyRule : undefined,
    scheduleEnabled: form.scheduleEnabled,
    scheduleCron: form.scheduleEnabled ? form.scheduleCron : undefined,
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await api.post('/resource-center/platform/policies', body)
      ElMessage.success('策略已创建')
    } else if (editingId.value != null) {
      await api.put(`/resource-center/platform/policies/${editingId.value}`, body)
      ElMessage.success('策略已保存')
    }
    dialogVisible.value = false
    await reload()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || (e instanceof Error ? e.message : '保存失败'))
  } finally {
    saving.value = false
  }
}

async function runPolicy(id: number) {
  try {
    const res = await api.post(`/resource-center/platform/policies/${id}/execute`)
    lastRun.value = res.data
    const st = String(res.data?.status || '')
    if (st === 'LEDGER') {
      ElMessage.warning(String(res.data?.message || '已记台账，未改物理数据'))
      if (props.mode === 'backup' || props.mode === 'archive') {
        activeTab.value = 'artifacts'
      }
    } else {
      ElMessage.success(String(res.data?.message || '策略已执行'))
      if (props.mode === 'backup' || props.mode === 'archive') {
        activeTab.value = 'artifacts'
      }
    }
    await reload()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '策略执行失败（销毁场景可能被策略拒绝）')
    await reload()
  }
}

async function startSchedule(row: Policy) {
  try {
    if (!row.scheduleCron) {
      ElMessage.warning('请先编辑策略并配置执行周期')
      return
    }
    const res = await api.post(`/resource-center/platform/policies/${row.id}/schedule/start`)
    ElMessage.success(String(res.data?.message || '调度已启动'))
    await reload()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '启动调度失败')
  }
}

async function stopSchedule(row: Policy) {
  try {
    const res = await api.post(`/resource-center/platform/policies/${row.id}/schedule/stop`)
    ElMessage.success(String(res.data?.message || '调度已停止'))
    await reload()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '停止调度失败')
  }
}

async function removePolicy(row: Policy) {
  try {
    await ElMessageBox.confirm(`确认删除策略「${row.policyName}」？`, '删除确认', { type: 'warning' })
    await api.delete(`/resource-center/platform/policies/${row.id}`)
    ElMessage.success('已删除')
    await reload()
  } catch (e: unknown) {
    if (e === 'cancel' || String(e) === 'cancel') return
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '删除失败')
  }
}

async function verifyArtifact(id: number) {
  try {
    const res = await api.get(`/resource-center/platform/backups/artifacts/${id}/verify`)
    ElMessage.success(res.data?.match ? '校验通过' : '校验失败（种子产物可能无实体文件）')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.warning(msg || '校验未通过（种子产物可能仅有台账）')
  }
}

async function restoreArtifact(id: number) {
  try {
    const res = await api.post(`/resource-center/platform/backups/artifacts/${id}/restore`)
    ElMessage.success(String(res.data?.message || `已恢复 ${res.data?.rowCount ?? 0} 行`))
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '恢复失败')
  }
}

function openEditArtifact(row: Artifact) {
  editingArtifactId.value = row.id
  artifactForm.physicalTable = row.physicalTable || ''
  artifactForm.fileName = row.fileName || ''
  artifactForm.storageLocation = row.storageLocation || row.filePath || ''
  artifactForm.message = row.message || ''
  artifactForm.status = row.status || 'SUCCESS'
  artifactDialogVisible.value = true
}

async function submitArtifactDialog() {
  if (editingArtifactId.value == null) return
  artifactSaving.value = true
  try {
    await api.put(`/resource-center/platform/backups/artifacts/${editingArtifactId.value}`, {
      storageLocation: artifactForm.storageLocation || null,
      message: artifactForm.message || null,
      status: artifactForm.status,
    })
    ElMessage.success('产物已保存')
    artifactDialogVisible.value = false
    await reload()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '保存失败')
  } finally {
    artifactSaving.value = false
  }
}

async function removeArtifact(row: Artifact) {
  try {
    await ElMessageBox.confirm(`确认删除产物「${row.fileName || row.id}」？将同时尝试清理本地文件。`, '删除确认', {
      type: 'warning',
    })
    await api.delete(`/resource-center/platform/backups/artifacts/${row.id}`)
    ElMessage.success('已删除')
    await reload()
  } catch (e: unknown) {
    if (e === 'cancel' || String(e) === 'cancel') return
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '删除失败')
  }
}

const activeTab = ref<'policies' | 'artifacts'>('policies')

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert
      v-if="lastRun"
      :type="lastRun.status === 'LEDGER' ? 'warning' : 'success'"
      :closable="true"
      style="margin-bottom:12px"
      :title="`最近执行：${statusLabel(String(lastRun.actionType || ''))} · ${statusLabel(String(lastRun.status || ''))}`"
      @close="lastRun = null"
    >
      {{ lastRun.message || `产物行数 ${lastRun.rowCount ?? '-'}` }}
      <template v-if="lastRun.storageLocation">；位置 {{ lastRun.storageLocation }}</template>
    </el-alert>

    <!-- 备份 / 归档：策略与产物分 Tab -->
    <template v-if="mode === 'backup' || mode === 'archive'">
      <el-tabs v-model="activeTab" class="backup-tabs">
        <el-tab-pane :label="mode === 'backup' ? '备份策略' : '归档策略'" name="policies" />
        <el-tab-pane :label="mode === 'backup' ? '备份产物' : '归档产物'" name="artifacts" />
      </el-tabs>

      <div v-show="activeTab === 'policies'">
        <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="doPolicyQuery">
          <el-form-item label="关键字" class="portal-field-xl">
            <el-input
              v-model="policyQuery"
              clearable
              placeholder="策略名 / 编码 / 表名"
              @keyup.enter="doPolicyQuery"
            />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="doPolicyQuery">查询</el-button>
            <el-button @click="doPolicyReset">重置</el-button>
            <el-button type="primary" @click="openCreate">
              {{ mode === 'backup' ? '新增备份策略' : '新增归档策略' }}
            </el-button>
            <el-button @click="reload">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="pagedPolicies" stripe size="small">
          <el-table-column prop="policyName" label="策略" min-width="140" show-overflow-tooltip />
          <el-table-column label="纳管表" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ tableName(row.managedTableId) }}</template>
          </el-table-column>
          <el-table-column prop="retentionDays" label="保存天数" width="90" />
          <el-table-column v-if="mode === 'backup'" label="存储策略" width="100">
            <template #default="{ row }">{{ statusLabel(row.storageStrategy || 'LOCAL') }}</template>
          </el-table-column>
          <el-table-column v-if="mode === 'backup'" label="备份库" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ libName(row.backupLibraryId) }}</template>
          </el-table-column>
          <el-table-column v-if="mode === 'archive'" label="压缩" width="90">
            <template #default="{ row }">{{ statusLabel(row.compressType || (row.compressEnabled ? 'GZIP' : 'NONE')) }}</template>
          </el-table-column>
          <el-table-column label="调度" width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.scheduleEnabled === 1">{{ cycleLabel(row.scheduleCron) }}</span>
              <span v-else>手动</span>
            </template>
          </el-table-column>
          <el-table-column v-if="mode === 'backup'" label="DS状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.dsPublishStatus || 'DRAFT') }}</template>
          </el-table-column>
          <el-table-column label="最近状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.lastRunStatus" :type="statusTagType(row.lastRunStatus)" size="small">
                {{ statusLabel(row.lastRunStatus) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" :width="mode === 'backup' ? 320 : 220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openView(row)">查看</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="runPolicy(row.id)">执行</el-button>
              <el-button
                v-if="mode === 'backup' && row.scheduleEnabled !== 1"
                link
                type="success"
                @click="startSchedule(row)"
              >启动</el-button>
              <el-button
                v-if="mode === 'backup' && row.scheduleEnabled === 1"
                link
                type="warning"
                @click="stopSchedule(row)"
              >停止</el-button>
              <el-button link type="danger" @click="removePolicy(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-model:page="policyPage"
          v-model:page-size="policyPageSize"
          :total="policyTotal"
        />
        <el-empty v-if="!loading && !filtered.length" description="暂无策略，请点击新增" />
      </div>

      <div v-show="activeTab === 'artifacts'">
        <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="doArtifactQuery">
          <el-form-item label="关键字" class="portal-field-xl">
            <el-input
              v-model="artifactQuery"
              clearable
              placeholder="表名 / 文件名 / 说明"
              @keyup.enter="doArtifactQuery"
            />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="doArtifactQuery">查询</el-button>
            <el-button @click="doArtifactReset">重置</el-button>
            <el-button @click="reload">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="pagedArtifacts" stripe size="small">
          <el-table-column prop="physicalTable" label="表" width="160" />
          <el-table-column prop="fileName" label="文件" min-width="160" show-overflow-tooltip />
          <el-table-column label="存储位置" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ row.storageLocation || row.filePath || '-' }}</template>
          </el-table-column>
          <el-table-column prop="rowCount" label="行数" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" :width="mode === 'backup' ? 240 : 180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEditArtifact(row)">编辑</el-button>
              <el-button link type="primary" @click="verifyArtifact(row.id)">校验</el-button>
              <el-button
                v-if="mode === 'backup'"
                link
                type="success"
                @click="restoreArtifact(row.id)"
              >恢复</el-button>
              <el-button link type="danger" @click="removeArtifact(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-model:page="artifactPage"
          v-model:page-size="artifactPageSize"
          :total="artifactTotal"
        />
        <el-empty
          v-if="!loading && !filteredArtifacts.length"
          :description="mode === 'backup' ? '暂无备份产物：可在「备份策略」中执行策略生成' : '暂无归档产物'"
        />
      </div>
    </template>

    <!-- 销毁：仅策略列表 -->
    <template v-else>
      <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="doPolicyQuery">
        <el-form-item label="关键字" class="portal-field-xl">
          <el-input
            v-model="policyQuery"
            clearable
            placeholder="策略名 / 表名"
            @keyup.enter="doPolicyQuery"
          />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="doPolicyQuery">查询</el-button>
          <el-button @click="doPolicyReset">重置</el-button>
          <el-button type="danger" @click="openCreate">新增销毁策略</el-button>
          <el-button @click="reload">刷新</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="pagedPolicies" stripe size="small">
        <el-table-column prop="policyName" label="策略" min-width="140" show-overflow-tooltip />
        <el-table-column label="纳管表" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ tableName(row.managedTableId) }}</template>
        </el-table-column>
        <el-table-column prop="destroyRule" label="销毁规则" min-width="160" show-overflow-tooltip />
        <el-table-column label="调度" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.scheduleEnabled === 1">{{ cycleLabel(row.scheduleCron) }}</span>
            <span v-else>手动</span>
          </template>
        </el-table-column>
        <el-table-column label="最近状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.lastRunStatus" :type="statusTagType(row.lastRunStatus)" size="small">
              {{ statusLabel(row.lastRunStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="runPolicy(row.id)">尝试执行</el-button>
            <el-button link type="danger" @click="removePolicy(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-model:page="policyPage"
        v-model:page-size="policyPageSize"
        :total="policyTotal"
      />
      <el-empty v-if="!loading && !filtered.length" description="暂无策略，请点击新增" />
    </template>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="策略名称" required>
          <el-input v-model="form.policyName" :disabled="formReadonly" maxlength="128" />
        </el-form-item>
        <el-form-item :label="mode === 'backup' ? '备份表' : '纳管表'" required>
          <el-select
            v-model="form.managedTableId"
            filterable
            clearable
            :disabled="formReadonly"
            placeholder="输入表名筛选"
            style="width: 100%"
          >
            <el-option v-for="t in managedTables" :key="t.id" :label="t.physicalTable" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mode !== 'destroy'" label="保存天数">
          <el-input-number
            v-model="form.retentionDays"
            :min="1"
            :max="3650"
            :disabled="formReadonly"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item v-if="mode === 'backup'" label="存储策略">
          <el-select v-model="form.storageStrategy" :disabled="formReadonly" style="width: 100%">
            <el-option :label="statusLabel('LOCAL')" value="LOCAL" />
            <el-option :label="statusLabel('NAS')" value="NAS" />
            <el-option :label="statusLabel('OBJECT')" value="OBJECT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mode === 'backup'" label="备份库">
          <el-select
            v-model="form.backupLibraryId"
            filterable
            clearable
            :disabled="formReadonly"
            style="width: 100%"
          >
            <el-option
              v-for="l in libraries"
              :key="l.id"
              :label="`${l.libName}（${statusLabel(l.libType)}）`"
              :value="l.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mode === 'backup'" label="备份范围">
          <el-select v-model="form.backupScope" :disabled="formReadonly" style="width: 100%">
            <el-option label="整表（真实导出）" value="FULL" />
            <el-option label="按时间（台账）" value="BY_TIME" />
            <el-option label="按分区（台账）" value="BY_PARTITION" />
            <el-option label="按时间+分区（台账）" value="BY_BOTH" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="mode === 'backup' && (form.backupScope === 'BY_TIME' || form.backupScope === 'BY_BOTH')"
          label="时间列"
          required
        >
          <el-input v-model="form.timeColumn" :disabled="formReadonly" placeholder="如 created_at / dt" />
        </el-form-item>
        <el-form-item
          v-if="mode === 'backup' && (form.backupScope === 'BY_TIME' || form.backupScope === 'BY_BOTH')"
          label="早于天数"
        >
          <el-input-number
            v-model="form.timeBeforeDays"
            :min="1"
            :max="3650"
            :disabled="formReadonly"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item
          v-if="mode === 'backup' && (form.backupScope === 'BY_PARTITION' || form.backupScope === 'BY_BOTH')"
          label="分区名"
          required
        >
          <el-input v-model="form.partitionName" :disabled="formReadonly" placeholder="如 p202401" />
        </el-form-item>
        <el-alert
          v-if="mode === 'backup' && form.backupScope !== 'FULL' && !formReadonly"
          type="warning"
          :closable="false"
          show-icon
          title="按时间/分区为台账演示：执行后生成产物记录，不按条件真实导出数据。"
          style="margin-bottom: 12px"
        />
        <el-form-item v-if="mode === 'backup'" label="表规则">
          <el-input v-model="form.tableRule" :disabled="formReadonly" placeholder="备注说明（可选）" />
        </el-form-item>
        <el-form-item v-if="mode === 'archive'" label="压缩">
          <el-switch v-model="form.compressEnabled" :disabled="formReadonly" />
        </el-form-item>
        <el-form-item v-if="mode === 'archive' && form.compressEnabled" label="压缩方式">
          <el-select v-model="form.compressType" :disabled="formReadonly" style="width: 100%">
            <el-option :label="statusLabel('GZIP')" value="GZIP" />
            <el-option :label="statusLabel('NONE')" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mode === 'destroy'" label="销毁规则">
          <el-input v-model="form.destroyRule" :disabled="formReadonly" placeholder="如：超期且无在用订阅方可申请销毁" />
        </el-form-item>
        <el-form-item label="周期调度">
          <el-switch v-model="form.scheduleEnabled" :disabled="formReadonly" />
        </el-form-item>
        <el-form-item v-if="form.scheduleEnabled" label="执行周期">
          <ExecCycleSelect v-if="!formReadonly" v-model="form.scheduleCron" />
          <span v-else>{{ form.scheduleCron || '-' }}</span>
        </el-form-item>
        <el-alert
          v-if="mode === 'backup' && form.scheduleEnabled && !formReadonly"
          type="info"
          :closable="false"
          show-icon
          title="启动调度时优先发布到 DolphinScheduler；不可用则使用应用内定时。"
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ formReadonly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!formReadonly" type="primary" :loading="saving" @click="submitDialog">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="artifactDialogVisible" title="编辑备份产物" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="表">
          <el-input v-model="artifactForm.physicalTable" disabled />
        </el-form-item>
        <el-form-item label="文件">
          <el-input v-model="artifactForm.fileName" disabled />
        </el-form-item>
        <el-form-item label="存储位置">
          <el-input v-model="artifactForm.storageLocation" maxlength="512" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="artifactForm.message" type="textarea" :rows="2" maxlength="512" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="artifactForm.status" style="width: 100%">
            <el-option :label="statusLabel('SUCCESS')" value="SUCCESS" />
            <el-option :label="statusLabel('FAILED')" value="FAILED" />
            <el-option :label="statusLabel('PARTIAL')" value="PARTIAL" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="artifactDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="artifactSaving" @click="submitArtifactDialog">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.backup-tabs {
  margin-bottom: 4px;
}
</style>
