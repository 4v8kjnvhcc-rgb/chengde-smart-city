<script setup lang="ts">
/**
 * 资源中心存储生命周期：备份 / 归档 / 销毁。
 * 多表按日快照写入 *_bak.{表}{yyyyMMdd}（跑到哪天打哪天），源表不改；归档同名 tsv.gz；满 6 个月才销毁。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'
import { useExecCycleLabel } from '@/utils/exec-cycle-label'

const props = defineProps<{
  mode: 'backup' | 'archive' | 'destroy'
}>()
const { label: cycleLabel } = useExecCycleLabel()

interface SourceDb {
  database: string
  backupDatabase: string
  layer: string
}
interface Policy {
  id: number
  policyName: string
  policyCode?: string
  actionType: string
  retentionDays?: number
  managedTableId?: number
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
interface RunLog {
  id: number
  policyId?: number
  actionType?: string
  runStatus?: string
  rowCount?: number
  artifactId?: number
  storageLocation?: string
  message?: string
  createdBy?: string
  createdAt?: string
}

const SOURCE_DB_LABEL: Record<string, string> = {
  smart_city: '控制面 smart_city',
  smart_city_ods: 'ODS smart_city_ods',
  smart_city_dwd: 'DWD smart_city_dwd',
  smart_city_dws: 'DWS smart_city_dws',
  smart_city_ads: 'ADS smart_city_ads',
}

const loading = ref(false)
const saving = ref(false)
const sourceDbs = ref<SourceDb[]>([])
const tableNames = ref<string[]>([])
const tablesLoading = ref(false)
const policies = ref<Policy[]>([])
const artifacts = ref<Artifact[]>([])

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit' | 'view'>('create')
const editingId = ref<number | null>(null)

const logVisible = ref(false)
const logLoading = ref(false)
const logRows = ref<RunLog[]>([])
const logPolicyName = ref('')
const logPolicyId = ref<number | null>(null)

const form = reactive({
  policyName: '',
  sourceDb: 'smart_city_ods',
  tableNames: [] as string[],
  backupScope: 'TABLE' as 'TABLE' | 'PARTITION',
  compressEnabled: true,
  compressType: 'GZIP',
  scheduleCron: '0 0 2 * * ?',
})

function parseRule(raw?: string): Record<string, unknown> {
  if (!raw || !raw.trim().startsWith('{')) return {}
  try {
    return JSON.parse(raw) as Record<string, unknown>
  } catch {
    return {}
  }
}

function policyMeta(row: Policy) {
  const o = parseRule(row.tableRule)
  const names = Array.isArray(o.tableNames)
    ? (o.tableNames as unknown[]).map((x) => String(x))
    : (o.tableName ? [String(o.tableName)] : [])
  const scope = String(o.backupScope || 'TABLE').toUpperCase()
  return {
    sourceDb: String(o.sourceDb || ''),
    tableName: names.join('、') || String(o.tableName || ''),
    tableNames: names,
    backupDb: String(o.backupDatabase || (o.sourceDb ? `${o.sourceDb}_bak` : '')),
    backupScope: scope === 'PARTITION' || scope === 'BY_PARTITION' || scope === 'BY_BOTH' ? 'PARTITION' : 'TABLE',
  }
}

function encodeRule(): string {
  return JSON.stringify({
    v: 3,
    sourceDb: form.sourceDb,
    tableNames: form.tableNames,
    tableName: form.tableNames[0] || '',
    backupDatabase: `${form.sourceDb}_bak`,
    backupScope: form.backupScope,
  })
}

function fillForm(row: Policy) {
  const m = policyMeta(row)
  form.policyName = row.policyName || ''
  form.sourceDb = m.sourceDb || 'smart_city_ods'
  form.tableNames = [...m.tableNames]
  form.backupScope = m.backupScope === 'PARTITION' ? 'PARTITION' : 'TABLE'
  form.compressEnabled = row.compressEnabled === 1 || row.compressType === 'GZIP'
  form.compressType = row.compressType || 'GZIP'
  form.scheduleCron = row.scheduleCron || '0 0 2 * * ?'
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
    const m = policyMeta(p)
    return (
      (p.policyName || '').toLowerCase().includes(kw)
      || (p.policyCode || '').toLowerCase().includes(kw)
      || m.tableName.toLowerCase().includes(kw)
      || m.sourceDb.toLowerCase().includes(kw)
    )
  })
})

const filteredArtifacts = computed(() => {
  let list = artifacts.value
  if (props.mode === 'archive') {
    list = list.filter((a) => (a.artifactType || '') === 'ARCHIVE')
  } else if (props.mode === 'destroy') {
    list = list.filter((a) =>
      (a.artifactType || '') === 'DESTROY'
      || (a.status || '').toUpperCase() === 'DESTROYED',
    )
  } else {
    list = list.filter((a) => (a.artifactType || 'BACKUP') === 'BACKUP')
  }
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
const bakDbLabel = computed(() => (form.sourceDb ? `${form.sourceDb}_bak` : '-'))

async function loadTables(db: string) {
  if (!db) {
    tableNames.value = []
    return
  }
  tablesLoading.value = true
  try {
    const res = await api.get('/resource-center/platform/lifecycle/tables', { params: { database: db } })
    tableNames.value = res.data || []
    const allowed = new Set(tableNames.value)
    form.tableNames = form.tableNames.filter((t) => allowed.has(t))
  } catch (e: unknown) {
    tableNames.value = []
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.warning(msg || '无法列出该库表，请确认分层库连接')
  } finally {
    tablesLoading.value = false
  }
}

watch(() => form.sourceDb, (db) => {
  if (dialogVisible.value) loadTables(db)
})

function resetForm() {
  form.policyName = ''
  form.sourceDb = 'smart_city_ods'
  form.tableNames = []
  form.backupScope = 'TABLE'
  form.compressEnabled = true
  form.compressType = 'GZIP'
  form.scheduleCron = '0 0 2 * * ?'
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

async function reload() {
  loading.value = true
  try {
    // 先同步产物/回填状态，再并行拉列表，避免并发双写产物
    await api.post('/resource-center/platform/lifecycle/sync-artifacts')
    const [dbs, pols, arts] = await Promise.all([
      api.get('/resource-center/platform/lifecycle/databases'),
      api.get('/resource-center/platform/policies', { params: { actionType: actionType.value } }),
      api.get('/resource-center/platform/backups/artifacts'),
    ])
    sourceDbs.value = dbs.data || []
    policies.value = pols.data || []
    artifacts.value = arts.data || []
    if (props.mode === 'destroy') {
      const latest = [...policies.value]
        .filter((p) => p.actionType === 'DESTROY' && p.lastRunMessage && p.lastRunAt)
        .sort((a, b) => String(b.lastRunAt || '').localeCompare(String(a.lastRunAt || '')))[0]
      if (latest?.lastRunMessage) {
        const t = Date.parse(String(latest.lastRunAt).replace(' ', 'T'))
        if (!Number.isNaN(t) && Date.now() - t < 15 * 60 * 1000) {
          ElMessage[latest.lastRunStatus === 'FAILED' ? 'error' : 'success'](latest.lastRunMessage)
        }
      }
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function openLogs(row: Policy) {
  logPolicyId.value = row.id
  logPolicyName.value = row.policyName || `策略#${row.id}`
  logVisible.value = true
  logLoading.value = true
  logRows.value = []
  try {
    const res = await api.get('/resource-center/platform/policies/runs', { params: { policyId: row.id } })
    logRows.value = res.data || []
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '加载日志失败')
  } finally {
    logLoading.value = false
  }
}

async function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
  await loadTables(form.sourceDb)
}

async function openEdit(row: Policy) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  fillForm(row)
  dialogVisible.value = true
  await loadTables(form.sourceDb)
}

async function openView(row: Policy) {
  dialogMode.value = 'view'
  editingId.value = row.id
  fillForm(row)
  dialogVisible.value = true
  await loadTables(form.sourceDb)
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
  if (!form.sourceDb || !form.tableNames.length) {
    ElMessage.warning('请选择源库和表')
    return
  }
  if (!form.scheduleCron.trim()) {
    ElMessage.warning('请选择执行周期')
    return
  }
  const body = {
    policyName: form.policyName,
    actionType: actionType.value,
    retentionDays: 180,
    sourceDb: form.sourceDb,
    tableNames: form.tableNames,
    tableName: form.tableNames[0],
    backupScope: form.backupScope,
    tableRule: encodeRule(),
    compressEnabled: props.mode === 'archive' ? form.compressEnabled : false,
    compressType: props.mode === 'archive' ? form.compressType : 'NONE',
    scheduleEnabled: true,
    scheduleCron: form.scheduleCron,
  }
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await api.post('/resource-center/platform/policies', body)
      ElMessage.success('策略已创建，请启动调度后按周期执行')
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
    ElMessage.success(res.data?.match ? '校验通过' : '校验失败')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.warning(msg || '校验未通过')
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

async function removeArtifact(row: Artifact) {
  try {
    await ElMessageBox.confirm(`确认删除产物记录「${row.fileName || row.id}」？`, '删除确认', { type: 'warning' })
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
    <el-tabs v-model="activeTab" class="backup-tabs">
      <el-tab-pane
        :label="mode === 'backup' ? '备份策略' : mode === 'archive' ? '归档策略' : '销毁策略'"
        name="policies"
      />
      <el-tab-pane
        :label="mode === 'backup' ? '备份产物' : mode === 'archive' ? '归档产物' : '销毁记录'"
        name="artifacts"
      />
    </el-tabs>

    <div v-show="activeTab === 'policies'">
      <el-alert
        v-if="mode === 'archive'"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        title="归档只处理已备份的日快照表（*_bak.{表}{yyyyMMdd}），未备份的表不会归档。"
      />
      <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="doPolicyQuery">
        <el-form-item label="关键字" class="portal-field-xl">
          <el-input
            v-model="policyQuery"
            clearable
            placeholder="策略名 / 库 / 表名"
            @keyup.enter="doPolicyQuery"
          />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="doPolicyQuery">查询</el-button>
          <el-button @click="doPolicyReset">重置</el-button>
          <el-button :type="mode === 'destroy' ? 'danger' : 'primary'" @click="openCreate">
            {{ mode === 'backup' ? '新增备份策略' : mode === 'archive' ? '新增归档策略' : '新增销毁策略' }}
          </el-button>
          <el-button @click="reload">刷新</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="pagedPolicies" stripe border class="portal-table" size="small">
        <el-table-column prop="policyName" label="策略" min-width="140" show-overflow-tooltip />
        <el-table-column label="源库" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ policyMeta(row).sourceDb || '-' }}</template>
        </el-table-column>
        <el-table-column label="表" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ policyMeta(row).tableName || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="mode === 'backup'" label="备份库" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ policyMeta(row).backupDb || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="mode === 'backup' || mode === 'destroy'" label="方式" width="110">
          <template #default="{ row }">{{ policyMeta(row).backupScope === 'PARTITION' ? '按区' : '按表' }}</template>
        </el-table-column>
        <el-table-column v-if="mode === 'archive'" label="压缩" width="90">
          <template #default="{ row }">{{ statusLabel(row.compressType || (row.compressEnabled ? 'GZIP' : 'NONE')) }}</template>
        </el-table-column>
        <el-table-column label="执行周期" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.scheduleEnabled === 1">{{ cycleLabel(row.scheduleCron) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="调度" width="90">
          <template #default="{ row }">{{ row.scheduleEnabled === 1 ? '已启动' : '已停止' }}</template>
        </el-table-column>
        <el-table-column label="最近状态" min-width="160">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.lastRunStatus"
              :content="`${formatDateTime(row.lastRunAt)} ${row.lastRunMessage || ''}`.trim()"
              placement="top"
            >
              <el-tag :type="statusTagType(row.lastRunStatus)" size="small">
                {{ statusLabel(row.lastRunStatus) }}
              </el-tag>
            </el-tooltip>
            <span v-else>-</span>
            <div v-if="row.lastRunMessage" class="run-msg">{{ row.lastRunMessage }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openView(row)">查看</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openLogs(row)">查看日志</el-button>
            <el-button
              v-if="row.scheduleEnabled !== 1"
              link
              type="success"
              @click="startSchedule(row)"
            >启动</el-button>
            <el-button
              v-else
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
      <el-empty v-if="!loading && !filtered.length" description="暂无策略，请点击新增并启动调度" />
    </div>

    <div v-show="activeTab === 'artifacts'">
      <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="doArtifactQuery">
        <el-form-item label="关键字" class="portal-field-xl">
          <el-input
            v-model="artifactQuery"
            clearable
            placeholder="表名 / 文件名 / 路径"
            @keyup.enter="doArtifactQuery"
          />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="doArtifactQuery">查询</el-button>
          <el-button @click="doArtifactReset">重置</el-button>
          <el-button @click="reload">刷新</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="pagedArtifacts" stripe border class="portal-table" size="small">
        <el-table-column prop="physicalTable" label="表" width="160" show-overflow-tooltip />
        <el-table-column prop="fileName" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="存储位置" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.storageLocation || row.filePath || '-' }}</template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="mode !== 'destroy'" label="操作" :width="mode === 'backup' ? 200 : 140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="mode === 'archive'" link type="primary" @click="verifyArtifact(row.id)">校验</el-button>
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
        :description="mode === 'backup'
          ? '暂无备份产物：启动策略等待定时执行，或刷新以同步备份库已有快照'
          : mode === 'archive'
            ? '暂无归档产物：请先备份再跑归档策略'
            : '暂无销毁记录：销毁执行后会在此留下结果说明'"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="策略名称" required>
          <el-input v-model="form.policyName" :disabled="formReadonly" maxlength="128" />
        </el-form-item>
        <el-form-item label="源库" required>
          <el-select v-model="form.sourceDb" :disabled="formReadonly" filterable style="width: 100%">
            <el-option
              v-for="d in sourceDbs"
              :key="d.database"
              :label="SOURCE_DB_LABEL[d.database] || d.database"
              :value="d.database"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="表" required>
          <el-select
            v-model="form.tableNames"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :disabled="formReadonly"
            :loading="tablesLoading"
            placeholder="可多选"
            style="width: 100%"
          >
            <el-option v-for="t in tableNames" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mode === 'backup'" label="备份库">
          <el-input :model-value="bakDbLabel" disabled />
        </el-form-item>
        <el-form-item v-if="mode === 'backup' || mode === 'destroy'" label="方式">
          <el-select v-model="form.backupScope" :disabled="formReadonly" style="width: 100%">
            <el-option label="按表" value="TABLE" />
            <el-option label="按区" value="PARTITION" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="(mode === 'backup' || mode === 'destroy') && form.backupScope === 'PARTITION'">
          <el-alert type="info" :closable="false" show-icon title="按区仅登记台账，不导出分区、不 DROP PARTITION。" />
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
        <el-form-item label="执行周期" required>
          <ExecCycleSelect v-if="!formReadonly" v-model="form.scheduleCron" />
          <span v-else>{{ form.scheduleCron || '-' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ formReadonly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!formReadonly" type="primary" :loading="saving" @click="submitDialog">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="logVisible"
      :title="`执行日志 — ${logPolicyName}`"
      width="820px"
      destroy-on-close
    >
      <el-table v-loading="logLoading" :data="logRows" stripe border size="small" max-height="420">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.runStatus)" size="small">{{ statusLabel(row.runStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="80" />
        <el-table-column prop="createdBy" label="执行人" width="100" show-overflow-tooltip />
        <el-table-column prop="storageLocation" label="位置" min-width="140" show-overflow-tooltip />
        <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!logLoading && !logRows.length" description="暂无执行日志" />
      <template #footer>
        <el-button @click="logVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="logLoading"
          @click="logPolicyId != null && openLogs({ id: logPolicyId, policyName: logPolicyName } as Policy)"
        >刷新</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.backup-tabs {
  margin-bottom: 4px;
}
.run-msg {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.35;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
