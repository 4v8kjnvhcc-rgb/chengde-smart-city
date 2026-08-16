<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'
import { generateTaskName } from '@/utils/task-naming'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'
import { useExecCycleLabel } from '@/utils/exec-cycle-label'
import api from '@/api/http'
import MetaDataSourcePickerDialog from '@/components/common/MetaDataSourcePickerDialog.vue'
import type { MetaBindSource } from '@/utils/meta-datasource-conn'
import { connectionKeyOf } from '@/utils/meta-datasource-conn'
import {
  ingestionApi,
  type IngestTask,
  type IngestTaskRun,
  type IngestTaskVersion,
} from '../useIngestionHub'

type AccessMode = 'SINGLE' | 'MULTI' | 'SQL'
type MapPair = { source: string; target: string; dataType?: string; length?: number | null; columnName?: string }

const FIELD_TYPE_OPTIONS = [
  'VARCHAR',
  'INT',
  'BIGINT',
  'DECIMAL',
  'DOUBLE',
  'DATE',
  'DATETIME',
  'TEXT',
  'BOOLEAN',
  'JSON',
] as const

function typeNeedsLength(t?: string) {
  const u = String(t || '').toUpperCase()
  return u === 'VARCHAR' || u === 'CHAR' || u === 'DECIMAL' || u === 'NUMERIC'
}
type LifecycleStatus = 'DRAFT' | 'ONLINE' | 'STARTED' | 'STOPPED' | 'OFFLINE'

/** 接入任务状态色：成功绿 / 失败红 / 运行中橙 / 部分成功蓝 / 空闲灰 */
function jobStatusTagType(status: unknown): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  const key = String(status ?? '').trim().toUpperCase()
  if (key === 'SUCCESS') return 'success'
  if (key === 'FAILED' || key === 'ERROR') return 'danger'
  if (key === 'RUNNING') return 'warning'
  if (key === 'PARTIAL') return 'primary'
  return 'info' // IDLE 等
}

function lifecycleOf(row: IngestTask): LifecycleStatus {
  const s = String(row.lifecycleStatus || 'DRAFT').toUpperCase()
  if (s === 'ONLINE' || s === 'STARTED' || s === 'STOPPED' || s === 'OFFLINE') return s
  return 'DRAFT'
}

function lifecycleTagType(life: LifecycleStatus): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  if (life === 'STARTED') return 'success'
  if (life === 'ONLINE') return 'primary'
  if (life === 'STOPPED') return 'warning'
  if (life === 'OFFLINE') return 'info'
  return 'info'
}

function jobSyncMode(row: IngestTask): 'T+1' | 'REALTIME' {
  try {
    const cfg = row.configJson ? JSON.parse(row.configJson) : {}
    return cfg.syncMode === 'REALTIME' ? 'REALTIME' : 'T+1'
  } catch {
    return 'T+1'
  }
}

/** 定时调度是否生效：已启动 或 enabled=1 且非实时 */
function isScheduled(row: IngestTask): boolean {
  return lifecycleOf(row) === 'STARTED' || (row.enabled === 1 && jobSyncMode(row) !== 'REALTIME')
}

/** cron → 执行周期中文名（来自系统「执行周期管理」） */
const cronNameMap = ref<Record<string, string>>({})

async function loadCycleNames() {
  try {
    const list = (await api.get('/system/exec-cycles', { params: { status: 'ACTIVE' } })).data || []
    const map: Record<string, string> = {}
    for (const o of list as Array<{ cycleName?: string; cronExpr?: string }>) {
      if (o.cronExpr) map[o.cronExpr] = o.cycleName || o.cronExpr
    }
    cronNameMap.value = map
  } catch {
    cronNameMap.value = {}
  }
}

function cronDisplay(row: IngestTask): string {
  return cycleLabel(row.scheduleCron)
}

function hasRunHistory(row: IngestTask): boolean {
  return !!(row.lastRunAt || (row.status && row.status !== 'IDLE'))
}

function canView(row: IngestTask) { return true }
function canEdit(row: IngestTask) {
  const l = lifecycleOf(row)
  return (l === 'DRAFT' || l === 'OFFLINE') && row.status !== 'RUNNING'
}
function canPublish(row: IngestTask) {
  const l = lifecycleOf(row)
  return (l === 'DRAFT' || l === 'OFFLINE') && row.status !== 'RUNNING'
}
function canOffline(row: IngestTask) {
  const l = lifecycleOf(row)
  return (l === 'ONLINE' || l === 'STOPPED') && row.status !== 'RUNNING'
}
function canRun(row: IngestTask) {
  const l = lifecycleOf(row)
  return (l === 'ONLINE' || l === 'STARTED' || l === 'STOPPED') && row.status !== 'RUNNING'
}
function canStart(row: IngestTask) {
  const l = lifecycleOf(row)
  return (l === 'ONLINE' || l === 'STOPPED') && row.status !== 'RUNNING' && jobSyncMode(row) !== 'REALTIME'
}
function canStop(row: IngestTask) {
  const l = lifecycleOf(row)
  return (l === 'STARTED' || l === 'ONLINE') && row.status !== 'RUNNING'
}
function canDelete(row: IngestTask) {
  const l = lifecycleOf(row)
  return (l === 'DRAFT' || l === 'OFFLINE') && row.status !== 'RUNNING'
}
function canLog(row: IngestTask) {
  const l = lifecycleOf(row)
  if (l === 'DRAFT') return false
  return hasRunHistory(row) || l === 'STARTED' || l === 'STOPPED' || l === 'OFFLINE'
}

type OpKey =
  | 'view' | 'edit' | 'publish' | 'run' | 'offline' | 'start' | 'stop'
  | 'version' | 'log' | 'logDetail' | 'reset' | 'delete'

type OpItem = {
  key: OpKey
  label: string
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

/** 操作列：主操作外露，次要收入「更多」，功能与权限判断不变 */
function rowOps(row: IngestTask): { primary: OpItem[]; more: OpItem[] } {
  const life = lifecycleOf(row)
  const primary: OpItem[] = []
  const more: OpItem[] = []
  if (canView(row)) primary.push({ key: 'view', label: '查看', type: 'primary' })

  if (life === 'DRAFT' || life === 'OFFLINE') {
    if (canEdit(row)) primary.push({ key: 'edit', label: '编辑', type: 'primary' })
    if (canPublish(row)) primary.push({ key: 'publish', label: '上线', type: 'success' })
  } else if (life === 'ONLINE') {
    if (canRun(row)) primary.push({ key: 'run', label: '执行', type: 'success' })
    if (canOffline(row)) primary.push({ key: 'offline', label: '下线', type: 'warning' })
    if (canStart(row)) more.push({ key: 'start', label: '启动', type: 'success' })
  } else if (life === 'STARTED') {
    if (canRun(row)) primary.push({ key: 'run', label: '执行', type: 'success' })
    if (canStop(row)) primary.push({ key: 'stop', label: '停止', type: 'warning' })
  } else if (life === 'STOPPED') {
    if (canRun(row)) primary.push({ key: 'run', label: '执行', type: 'success' })
    if (canStart(row)) primary.push({ key: 'start', label: '启动', type: 'success' })
    if (canOffline(row)) more.push({ key: 'offline', label: '下线', type: 'warning' })
  }

  more.push({ key: 'version', label: '版本', type: 'info' })
  if (canLog(row)) {
    more.push({ key: 'log', label: '日志', type: 'primary' })
    more.push({ key: 'logDetail', label: '日志详情', type: 'primary' })
  }
  more.push({ key: 'reset', label: '重置', type: 'warning' })
  if (canDelete(row)) more.push({ key: 'delete', label: '删除', type: 'danger' })

  return { primary, more }
}

function onRowOp(key: OpKey, row: IngestTask) {
  switch (key) {
    case 'view':
      openResult(row)
      break
    case 'edit':
      editJob(row)
      break
    case 'publish':
      void publishJob(row.id)
      break
    case 'run':
      void runJob(row.id)
      break
    case 'offline':
      void offlineJob(row.id)
      break
    case 'start':
      void startJob(row.id)
      break
    case 'stop':
      void stopJob(row.id)
      break
    case 'version':
      openVersions(row)
      break
    case 'log':
      openLogs(row, false)
      break
    case 'logDetail':
      openLogs(row, true)
      break
    case 'reset':
      void resetJob(row.id)
      break
    case 'delete':
      void deleteJob(row)
      break
  }
}

const step = ref(0)
const busy = ref(false)
const runBusy = ref(false)
const batchBusy = ref(false)
const jobs = ref<IngestTask[]>([])
const selectedIds = ref<number[]>([])
/** 元数据源下 JDBC 探到的全表（不论是否采集元数据） */
const metaTables = ref<string[]>([])
const metaTablesLoading = ref(false)

const versionVisible = ref(false)
const versionRows = ref<IngestTaskVersion[]>([])
const versionTaskName = ref('')
const versionBusy = ref(false)

const logVisible = ref(false)
const logDetailVisible = ref(false)
const runDetailVisible = ref(false)
const logTask = ref<IngestTask | null>(null)
const logRuns = ref<IngestTaskRun[]>([])
const logFilter = reactive({
  runStatus: 'ALL',
  from: '',
  to: '',
})
const runDetail = ref<Record<string, unknown> | null>(null)
const logBusy = ref(false)
const { label: cycleLabel } = useExecCycleLabel()

const form = reactive({
  taskName: '',
  accessMode: 'SINGLE' as AccessMode,
  sourceId: undefined as number | undefined,
  metaDataSourceId: undefined as number | undefined,
  sourceConnection: '',
  sourceName: '',
  syncMode: 'T+1',
  writeMode: 'FULL' as 'FULL' | 'INCREMENTAL',
  // single
  sourceTable: '',
  sourceTableMode: 'FIXED' as 'FIXED' | 'PREFIX_DATE',
  tablePrefix: '',
  datePattern: 'yyyyMMdd',
  dateOffsetDays: -1,
  incrementColumn: '',
  targetTable: '',
  targetConnection: '',
  targetConnectionLabel: '',
  // multi
  sourceTables: [] as string[],
  targetTableRule: 'ods_{sourceTable}',
  // sql
  selectSql: '',
  sqlTargetTable: '',
  paramBizDate: 'DATE_OFFSET:-1',
  // mapping
  mappingMode: 'NAME' as 'ORDER' | 'NAME' | 'MANUAL',
  pairs: [] as MapPair[],
  // schedule
  scheduleCron: '0 0 2 * * ?',
  enabled: false,
})

const targetDsPickerVisible = ref(false)
const sourceDsPickerVisible = ref(false)

function openTargetDsPicker() {
  targetDsPickerVisible.value = true
}

function openSourceDsPicker() {
  sourceDsPickerVisible.value = true
}

function onTargetDsPicked(row: MetaBindSource) {
  form.targetConnection = connectionKeyOf(row)
  form.targetConnectionLabel = `${row.sourceName}${row.databaseName ? `（${row.databaseName}）` : ''}`
}

async function onSourceDsPicked(row: MetaBindSource) {
  form.metaDataSourceId = row.id
  form.sourceName = row.sourceName
  form.sourceConnection = connectionKeyOf(row)
  const ingId = row.ingSourceId != null ? Number(row.ingSourceId) : NaN
  form.sourceId = Number.isFinite(ingId) && ingId > 0 ? ingId : undefined
  form.sourceTable = ''
  form.sourceTables = []
  form.pairs = []
  await loadMetaTables(row.id)
}

async function loadMetaTables(metaId?: number) {
  metaTables.value = []
  const id = metaId ?? form.metaDataSourceId
  if (id == null || id <= 0) return
  metaTablesLoading.value = true
  try {
    const rows = (await api.get(`/governance/platform/metadata/collect/meta-data-sources/${id}/tables`)).data || []
    metaTables.value = (rows as Array<{ sourceTable?: string; tableName?: string }>)
      .map((r) => String(r.sourceTable || r.tableName || '').trim())
      .filter(Boolean)
      .sort((a, b) => a.localeCompare(b))
  } catch {
    metaTables.value = []
    ElMessage.error('加载数据源表清单失败')
  } finally {
    metaTablesLoading.value = false
  }
}

const resultVisible = ref(false)
const resultTitle = ref('运行结果')
const resultRow = ref<IngestTask | null>(null)
const resultPayload = ref<Record<string, unknown> | null>(null)
const editingJobId = ref<number | undefined>()
const taskNameManual = ref(false)
const taskNameGenerating = ref(false)
const dialogVisible = ref(false)
const dialogTitle = computed(() => (editingJobId.value ? '编辑接入任务' : '新建接入任务'))

const steps = ['接入模式', '数据来源', '同步策略', '数据去向', '字段映射', '调度执行']

function formatDuration(ms?: number | null) {
  if (ms == null || !Number.isFinite(Number(ms))) return '—'
  const n = Number(ms)
  if (n < 1000) return `${n} 毫秒`
  const sec = n / 1000
  if (sec < 60) return `${sec.toFixed(1)} 秒`
  const m = Math.floor(sec / 60)
  const s = Math.round(sec % 60)
  return `${m} 分 ${s} 秒`
}

function openResult(row: IngestTask, payload?: Record<string, unknown> | null) {
  resultRow.value = row
  resultPayload.value = payload || null
  resultTitle.value = `运行结果 · ${row.taskName || row.id}`
  resultVisible.value = true
}

function suggestOds(sourceTable?: string) {
  const raw = String(sourceTable || '').trim()
  const sanitized = raw.replace(/[^A-Za-z0-9_]/g, '')
  if (!sanitized) return ''
  return sanitized.toLowerCase().startsWith('ods_') ? sanitized : `ods_${sanitized}`
}

function applyTargetTableRule(sourceTable?: string) {
  const source = String(sourceTable || '').trim()
  const sanitized = source.replace(/[^A-Za-z0-9_]/g, '')
  if (!sanitized) return ''
  return (form.targetTableRule || 'ods_{sourceTable}').replace('{sourceTable}', sanitized).toLowerCase()
}

function resolveTargetTableForNaming(): string {
  if (form.accessMode === 'SINGLE') {
    return form.targetTable.trim() || suggestOds(form.sourceTable)
  }
  if (form.accessMode === 'SQL') {
    return form.sqlTargetTable.trim()
  }
  if (form.accessMode === 'MULTI' && form.sourceTables.length) {
    return applyTargetTableRule(form.sourceTables[0])
  }
  return ''
}

async function refreshTaskName(force = false) {
  if (editingJobId.value && !force) return
  if (taskNameManual.value && !force) return

  const targetTable = resolveTargetTableForNaming()
  if (!targetTable) {
    if (!editingJobId.value) form.taskName = ''
    return
  }

  taskNameGenerating.value = true
  try {
    form.taskName = await generateTaskName({
      taskCategory: 'GJ',
      targetTable,
    })
  } finally {
    taskNameGenerating.value = false
  }
}

function onTaskNameInput() {
  taskNameManual.value = true
}

function buildConfig() {
  const mapping = {
    mode: form.mappingMode,
    pairs: form.pairs.map((p) => ({
      source: p.source,
      target: p.target || p.source,
      dataType: p.dataType,
      length: p.length,
    })),
  }
  const config: Record<string, unknown> = {
    accessMode: form.accessMode,
    sourceId: form.sourceId,
    syncMode: form.syncMode,
    writeMode: form.writeMode,
    mapping,
    scheduleCron: form.scheduleCron,
    targetConnection: form.targetConnection,
    targetConnectionLabel: form.targetConnectionLabel || '',
    metaDataSourceId: form.metaDataSourceId,
    sourceConnection: form.sourceConnection
      || (form.metaDataSourceId ? `meta:${form.metaDataSourceId}` : ''),
    sourceName: form.sourceName || '',
  }
  if (form.accessMode === 'SINGLE') {
    config.single = {
      sourceTable: form.sourceTable,
      sourceTableMode: form.sourceTableMode,
      tablePrefix: form.tablePrefix,
      datePattern: form.datePattern,
      dateOffsetDays: form.dateOffsetDays,
      incrementColumn: form.incrementColumn,
      targetTable: form.targetTable,
    }
  } else if (form.accessMode === 'MULTI') {
    config.multi = {
      sourceTables: form.sourceTables,
      targetTableRule: form.targetTableRule,
    }
  } else {
    config.sql = {
      sourceId: form.sourceId,
      sourceConnection: form.sourceConnection
        || (form.metaDataSourceId ? `meta:${form.metaDataSourceId}` : ''),
      selectSql: form.selectSql,
      targetTable: form.sqlTargetTable,
      paramBindings: { biz_date: form.paramBizDate },
    }
  }
  return config
}

async function loadBase() {
  jobs.value = (await ingestionApi.jobs()).data || []
}

async function ensureDialogData() {
  if (form.metaDataSourceId && !metaTables.value.length) {
    await loadMetaTables(form.metaDataSourceId)
  }
}

async function openCreateDialog() {
  resetWizard()
  dialogVisible.value = true
  await ensureDialogData()
}

function onModeChange() {
  form.pairs = []
}

function onSyncModeChange() {
  if (form.syncMode === 'REALTIME') {
    form.enabled = false
  }
}

function onSingleTableChange() {
  form.targetTable = ''
  form.pairs = []
  void refreshTaskName()
}

async function loadMapping(mode: 'ORDER' | 'NAME' | 'MANUAL' = form.mappingMode) {
  form.mappingMode = mode
  if (form.accessMode === 'SQL') {
    if (!form.pairs.length) {
      ElMessage.info('条件 SQL 请手工填写源列→目标列')
    }
    return
  }
  const tableName = form.accessMode === 'SINGLE' ? form.sourceTable : form.sourceTables[0]
  if (!tableName) {
    ElMessage.warning('请先选择源表')
    return
  }
  if (!form.metaDataSourceId) {
    ElMessage.warning('请先选择数据源')
    return
  }
  busy.value = true
  try {
    const res = await api.get(`/governance/platform/metadata/models/meta-data-sources/${form.metaDataSourceId}/table-columns`, {
      params: { tableName },
    })
    const fields = (res.data?.fields || []) as Array<{
      code?: string
      nameEn?: string
      type?: string
      dataType?: string
      length?: number
    }>
    form.pairs = fields
      .map((f) => {
        const col = String(f.code || f.nameEn || '').trim()
        const dataType = String(f.dataType || f.type || 'VARCHAR').toUpperCase() || 'VARCHAR'
        return {
          source: col,
          target: col,
          dataType,
          length: f.length ?? (typeNeedsLength(dataType) ? 255 : undefined),
          columnName: col,
        }
      })
      .filter((p) => p.source)
    if (!form.pairs.length) {
      ElMessage.warning('该表未探测到字段')
    }
  } finally {
    busy.value = false
  }
}

async function saveJob(andRun = false) {
  await refreshTaskName(true)
  if (!form.taskName.trim()) {
    ElMessage.warning('任务名称未生成，请先配置数据去向中的目标表')
    return
  }
  if (form.accessMode === 'SINGLE' && !form.sourceTable) {
    ElMessage.warning('请选择源表')
    return
  }
  if (form.accessMode === 'MULTI' && !form.sourceTables.length) {
    ElMessage.warning('请至少选择一张表')
    return
  }
  if (form.accessMode === 'SQL' && !form.selectSql.trim()) {
    ElMessage.warning('请填写 SELECT SQL')
    return
  }
  if (!form.metaDataSourceId && !form.sourceConnection) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!form.pairs.length && form.accessMode !== 'MULTI') {
    ElMessage.warning('请配置字段映射')
    return
  }
  busy.value = true
  try {
    if (form.syncMode === 'REALTIME') {
      form.enabled = false
    }
    const body: Record<string, unknown> = {
      taskName: form.taskName.trim(),
      accessMode: form.accessMode,
      writeMode: form.writeMode,
      scheduleCron: form.scheduleCron,
      // 启停由生命周期控制；保存草稿/下线编辑时不下发定时
      enabled: false,
      sourceId: form.sourceId,
      targetTable: form.accessMode === 'SQL' ? form.sqlTargetTable : form.targetTable,
      config: buildConfig(),
    }
    let id = editingJobId.value
    if (id) {
      await ingestionApi.updateJob(id, body)
    } else {
      const res = await ingestionApi.createJob(body)
      id = res.data
      editingJobId.value = id
    }
    ElMessage.success(andRun ? '已保存' : '任务已保存（草稿）')
    dialogVisible.value = false
    await reloadJobs()
    if (andRun && id) {
      await publishJob(id)
      await runJob(id)
    }
  } catch {
    /* interceptor */
  } finally {
    busy.value = false
  }
}

async function publishJob(id: number) {
  try {
    await ingestionApi.publishJob(id)
    ElMessage.success('已上线')
    await reloadJobs()
  } catch { /* interceptor */ }
}

async function offlineJob(id: number) {
  try {
    await ingestionApi.offlineJob(id)
    ElMessage.success('已下线')
    await reloadJobs()
  } catch { /* interceptor */ }
}

async function startJob(id: number) {
  try {
    await ingestionApi.startJob(id)
    ElMessage.success('已启动定时（DolphinScheduler）')
    await reloadJobs()
  } catch { /* interceptor */ }
}

async function stopJob(id: number) {
  try {
    await ingestionApi.stopJob(id)
    ElMessage.success('已停止定时')
    await reloadJobs()
  } catch { /* interceptor */ }
}

function onSelectionChange(rows: IngestTask[]) {
  selectedIds.value = rows.map((r) => r.id)
}

async function batchAction(action: 'run' | 'start' | 'stop' | 'delete') {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选任务')
    return
  }
  batchBusy.value = true
  try {
    const res = await ingestionApi.batchJobs(action, selectedIds.value)
    const d = res.data
    if (d.failed > 0) {
      ElMessage.warning(`成功 ${d.success}，失败 ${d.failed}${d.errors?.[0] ? '：' + d.errors[0] : ''}`)
    } else {
      const label =
        action === 'run' ? '执行' : action === 'start' ? '启动' : action === 'stop' ? '停止' : '删除'
      ElMessage.success(`批量${label}成功：${d.success}`)
    }
    selectedIds.value = []
    await reloadJobs()
  } catch {
    /* interceptor */
  } finally {
    batchBusy.value = false
  }
}

async function batchDelete() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选任务')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除已勾选的 ${selectedIds.value.length} 个任务？仅删除任务配置，不会删除 ODS 已落库数据。仅草稿或已下线任务可删除。`,
      '批量删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  await batchAction('delete')
}

async function openVersions(row: IngestTask) {
  versionBusy.value = true
  versionTaskName.value = row.taskName
  versionVisible.value = true
  try {
    versionRows.value = (await ingestionApi.jobVersions(row.id)).data || []
  } catch {
    versionRows.value = []
  } finally {
    versionBusy.value = false
  }
}

async function openLogs(row: IngestTask, detailMode = false) {
  logTask.value = row
  logFilter.runStatus = 'ALL'
  logFilter.from = ''
  logFilter.to = ''
  if (detailMode) {
    logDetailVisible.value = true
  } else {
    logVisible.value = true
  }
  await reloadRuns()
}

async function reloadRuns() {
  if (!logTask.value) return
  logBusy.value = true
  try {
    const params: { runStatus?: string; from?: string; to?: string } = {}
    if (logFilter.runStatus && logFilter.runStatus !== 'ALL') params.runStatus = logFilter.runStatus
    if (logFilter.from) params.from = logFilter.from
    if (logFilter.to) params.to = logFilter.to
    logRuns.value = (await ingestionApi.jobRuns(logTask.value.id, params)).data || []
  } catch {
    logRuns.value = []
  } finally {
    logBusy.value = false
  }
}

async function openRunDetail(runId: number) {
  logBusy.value = true
  try {
    runDetail.value = (await ingestionApi.jobRunDetail(runId)).data || null
    runDetailVisible.value = true
  } catch {
    runDetail.value = null
  } finally {
    logBusy.value = false
  }
}

async function runJob(id: number) {
  runBusy.value = true
  try {
    const res = await ingestionApi.runJob(id)
    const d = (res.data || {}) as Record<string, unknown>
    await reloadJobs()
    const row = jobs.value.find((j) => j.id === id) || null
    if (row) {
      openResult(row, d)
    }
    if (d.accessMode === 'MULTI') {
      if (d.status === 'PARTIAL') {
        ElMessage.warning(String(d.message || '多表部分成功，请查看运行结果'))
      } else {
        ElMessage.success(`多表完成：${d.tableCount ?? 0} 张表，共 ${d.collectedRows ?? 0} 行`)
      }
    } else if (String(d.status || row?.status || '').toUpperCase() === 'FAILED') {
      ElMessage.error(String(d.message || row?.lastRunMessage || '汇聚失败'))
    } else {
      ElMessage.success(`汇聚完成：${d.collectedRows ?? row?.collectedRows ?? 0} 行，耗时 ${formatDuration(Number(d.durationMs ?? row?.durationMs))}`)
    }
  } catch {
    await reloadJobs()
    const row = jobs.value.find((j) => j.id === id)
    if (row) openResult(row, null)
  } finally {
    runBusy.value = false
  }
}

async function resetJob(id: number) {
  try {
    await ingestionApi.resetJob(id)
    ElMessage.success('已重置，可重新执行')
    await reloadJobs()
  } catch {
    /* interceptor */
  }
}

async function deleteJob(row: IngestTask) {
  try {
    await ElMessageBox.confirm(
      `确定删除任务「${row.taskName}」？仅删除任务配置，不会删除 ODS 已落库数据。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await ingestionApi.deleteJob(row.id)
    ElMessage.success('任务已删除')
    if (editingJobId.value === row.id) {
      dialogVisible.value = false
    }
    await reloadJobs()
  } catch {
    /* interceptor */
  }
}

async function reloadJobs() {
  jobs.value = (await ingestionApi.jobs()).data || []
}

async function editJob(row: IngestTask) {
  editingJobId.value = row.id
  taskNameManual.value = true
  form.taskName = row.taskName
  form.accessMode = (row.accessMode as AccessMode) || 'SINGLE'
  form.writeMode = (row.writeMode as 'FULL' | 'INCREMENTAL') || 'FULL'
  form.scheduleCron = row.scheduleCron || '0 0 2 * * ?'
  form.enabled = row.enabled === 1
  form.sourceId = row.sourceId
  form.targetTable = row.targetTable || ''
  try {
    const cfg = row.configJson ? JSON.parse(row.configJson) : {}
    form.syncMode = cfg.syncMode || 'T+1'
    form.targetConnection = cfg.targetConnection || 'smart_city_ods'
    form.targetConnectionLabel = cfg.targetConnectionLabel || form.targetConnection
    form.metaDataSourceId = cfg.metaDataSourceId || undefined
    form.sourceConnection = cfg.sourceConnection
      || (form.metaDataSourceId ? `meta:${form.metaDataSourceId}` : '')
    form.sourceName = cfg.sourceName || ''
    if (cfg.single) {
      form.sourceTable = cfg.single.sourceTable || ''
      form.sourceTableMode = cfg.single.sourceTableMode || 'FIXED'
      form.tablePrefix = cfg.single.tablePrefix || ''
      form.datePattern = cfg.single.datePattern || 'yyyyMMdd'
      form.dateOffsetDays = cfg.single.dateOffsetDays ?? -1
      form.incrementColumn = cfg.single.incrementColumn || ''
      form.targetTable = cfg.single.targetTable || form.targetTable
    }
    if (cfg.multi) {
      form.sourceTables = cfg.multi.sourceTables || []
      form.targetTableRule = cfg.multi.targetTableRule || 'ods_{sourceTable}'
    }
    if (cfg.sql) {
      form.selectSql = cfg.sql.selectSql || ''
      form.sqlTargetTable = cfg.sql.targetTable || 'ods_sql_result'
      form.paramBizDate = cfg.sql.paramBindings?.biz_date || 'DATE_OFFSET:-1'
      form.sourceId = cfg.sql.sourceId || form.sourceId
      if (cfg.sql.sourceConnection) form.sourceConnection = cfg.sql.sourceConnection
    }
    if (cfg.mapping?.pairs) {
      form.pairs = cfg.mapping.pairs
      form.mappingMode = cfg.mapping.mode || 'NAME'
    } else {
      form.pairs = []
    }
  } catch {
    /* ignore */
  }
  step.value = 0
  dialogVisible.value = true
  await ensureDialogData()
}

function resetWizard() {
  editingJobId.value = undefined
  taskNameManual.value = false
  form.taskName = ''
  form.accessMode = 'SINGLE'
  form.sourceId = undefined
  form.metaDataSourceId = undefined
  form.sourceConnection = ''
  form.sourceName = ''
  form.syncMode = 'T+1'
  form.writeMode = 'FULL'
  form.sourceTable = ''
  form.sourceTableMode = 'FIXED'
  form.tablePrefix = ''
  form.datePattern = 'yyyyMMdd'
  form.dateOffsetDays = -1
  form.incrementColumn = ''
  form.sourceTables = []
  form.targetTableRule = 'ods_{sourceTable}'
  form.pairs = []
  form.selectSql = ''
  form.sqlTargetTable = ''
  form.paramBizDate = 'DATE_OFFSET:-1'
  form.targetTable = ''
  form.targetConnection = ''
  form.targetConnectionLabel = ''
  form.scheduleCron = '0 0 2 * * ?'
  form.enabled = false
  metaTables.value = []
  step.value = 0
}

function onDialogClosed() {
  resetWizard()
}

function canGoNext(): boolean {
  if (step.value === 1) {
    if (!form.metaDataSourceId && !form.sourceConnection) {
      ElMessage.warning('请从数据源管理选择数据源')
      return false
    }
    if (form.accessMode === 'SINGLE' && !form.sourceTable) {
      ElMessage.warning('请选择源表')
      return false
    }
    if (form.accessMode === 'MULTI' && !form.sourceTables.length) {
      ElMessage.warning('请至少选择一张表')
      return false
    }
    if (form.accessMode === 'SQL' && !form.selectSql.trim()) {
      ElMessage.warning('请填写 SELECT SQL')
      return false
    }
  }
  if (step.value === 3) {
    if (!form.targetConnection) {
      ElMessage.warning('请选择目标库')
      return false
    }
    if (form.accessMode === 'SINGLE' && !form.targetTable.trim()) {
      ElMessage.warning('请填写目标表')
      return false
    }
    if (form.accessMode === 'SQL' && !form.sqlTargetTable.trim()) {
      ElMessage.warning('请填写目标表')
      return false
    }
    if (form.accessMode === 'MULTI' && !form.targetTableRule.trim()) {
      ElMessage.warning('请填写目标命名规则')
      return false
    }
  }
  return true
}

function nextStep() {
  if (!canGoNext()) return
  const leaving = step.value
  if (step.value < steps.length - 1) step.value += 1
  if (leaving === 3) {
    void refreshTaskName()
  }
  if (step.value === 4 && form.accessMode !== 'MULTI' && !form.pairs.length) {
    loadMapping('NAME')
  }
}

watch(
  () => [form.targetTable, form.sqlTargetTable, form.targetTableRule, form.sourceTables.join(','), form.sourceTable] as const,
  () => {
    void refreshTaskName()
  },
)

watch(
  () => form.accessMode,
  () => {
    void refreshTaskName()
  },
)

onMounted(async () => {
  await loadCycleNames()
  loadBase()
})
</script>

<template>
  <div class="table-wizard">
    <PageCard>
      <template #header>
        <div class="wiz-head">
          <div>
            <div class="wiz-title">库表接入配置</div>
          </div>
          <div class="wiz-actions">
            <el-button
              :loading="batchBusy"
              :disabled="!selectedIds.length"
              type="danger"
              plain
              @click="batchDelete"
            >批量删除</el-button>
            <el-button :loading="batchBusy" :disabled="!selectedIds.length" @click="batchAction('run')">执行</el-button>
            <el-button :loading="batchBusy" :disabled="!selectedIds.length" type="success" @click="batchAction('start')">启动</el-button>
            <el-button :loading="batchBusy" :disabled="!selectedIds.length" type="warning" @click="batchAction('stop')">停止</el-button>
            <el-button type="primary" @click="openCreateDialog">新建任务</el-button>
          </div>
        </div>
      </template>

      <el-table :data="jobs" stripe size="small" empty-text="暂无接入任务，请点击右上角新建" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="42" align="center" />
        <el-table-column prop="taskName" label="任务" min-width="140" align="center" header-align="center" />
        <el-table-column label="模式" width="72" align="center" header-align="center">
          <template #default="{ row }">
            {{ row.accessMode === 'MULTI' ? '多表' : row.accessMode === 'SQL' ? '条件' : '单表' }}
          </template>
        </el-table-column>
        <el-table-column prop="targetTable" label="目标表" min-width="110" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column label="同步" width="70" align="center" header-align="center">
          <template #default="{ row }">
            {{ jobSyncMode(row) === 'REALTIME' ? '实时' : 'T+1' }}
          </template>
        </el-table-column>
        <el-table-column label="调度" width="70" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="isScheduled(row) ? 'success' : 'info'" size="small" effect="plain">
              {{ isScheduled(row) ? '定时' : '手动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行周期" min-width="120" align="center" header-align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ cronDisplay(row) }}</template>
        </el-table-column>
        <el-table-column label="任务状态" width="88" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="lifecycleTagType(lifecycleOf(row))" size="small">
              {{ statusLabel(lifecycleOf(row)) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近运行" width="88" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="jobStatusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button
                v-for="op in rowOps(row).primary"
                :key="op.key"
                link
                :type="op.type || 'primary'"
                :loading="op.key === 'run' && runBusy"
                @click="onRowOp(op.key, row)"
              >{{ op.label }}</el-button>
              <el-dropdown
                v-if="rowOps(row).more.length"
                trigger="click"
                @command="(cmd: OpKey) => onRowOp(cmd, row)"
              >
                <el-button link type="primary">
                  更多
                  <el-icon class="op-more-icon"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      v-for="op in rowOps(row).more"
                      :key="op.key"
                      :command="op.key"
                      :divided="op.key === 'delete'"
                      :class="{ 'op-danger': op.key === 'delete', 'op-warning': op.key === 'reset' }"
                    >{{ op.label }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="860px"
      destroy-on-close
      append-to-body
      class="ingest-job-dialog"
      @closed="onDialogClosed"
    >
      <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 16px">
        <el-step v-for="s in steps" :key="s" :title="s" />
      </el-steps>

      <el-form label-width="120px" class="portal-inline-form portal-inline-form--block">
        <template v-if="step === 0">
          <el-form-item label="任务名称" class="portal-field-xl" required>
            <div class="task-name-row">
              <el-input
                v-model="form.taskName"
                :readonly="!editingJobId && !taskNameManual"
                placeholder="按命名规范自动生成：t_gj_+目标表名"
                maxlength="80"
                @input="onTaskNameInput"
                @keyup.enter="nextStep"
              >
                <template v-if="taskNameGenerating" #suffix>
                  <span class="task-name-hint">生成中…</span>
                </template>
              </el-input>
              <el-button
                v-if="!editingJobId && !taskNameManual"
                link
                type="primary"
                @click="taskNameManual = true"
              >
                手动修改
              </el-button>
              <el-button
                v-if="!editingJobId && taskNameManual"
                link
                type="primary"
                @click="taskNameManual = false; refreshTaskName(true)"
              >
                恢复自动生成
              </el-button>
            </div>
            <div v-if="!editingJobId" class="hint">归集任务命名规则：t_gj_ + 目标表表名（配置数据去向后自动填充）</div>
          </el-form-item>
          <el-form-item label="接入模式">
            <el-radio-group v-model="form.accessMode" @change="onModeChange">
              <el-radio-button value="SINGLE">单表接入</el-radio-button>
              <el-radio-button value="MULTI">多表批量</el-radio-button>
              <el-radio-button value="SQL">条件接入</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </template>

        <template v-else-if="step === 1">
          <el-form-item label="数据源" class="portal-field-xl">
            <div class="conn-pick">
              <el-input
                :model-value="form.sourceName || (form.metaDataSourceId ? `元数据源 #${form.metaDataSourceId}` : '')"
                readonly
                placeholder="点击选择（数据来自元数据 · 数据源管理）"
              />
              <el-button type="primary" @click="openSourceDsPicker">选择</el-button>
            </div>
          </el-form-item>
          <template v-if="form.accessMode === 'SINGLE'">
            <el-form-item label="源表" class="portal-field-xl">
              <el-select
                v-model="form.sourceTable"
                filterable
                allow-create
                default-first-option
                :loading="metaTablesLoading"
                :disabled="!form.metaDataSourceId"
                :placeholder="form.metaDataSourceId ? '输入表名筛选，或选择/新建' : '请先选择数据源'"
                @change="onSingleTableChange"
              >
                <el-option
                  v-for="t in metaTables"
                  :key="t"
                  :label="t"
                  :value="t"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="表名模式">
              <el-radio-group v-model="form.sourceTableMode">
                <el-radio value="FIXED">固定表名</el-radio>
                <el-radio value="PREFIX_DATE">分表前缀+日期</el-radio>
              </el-radio-group>
            </el-form-item>
            <template v-if="form.sourceTableMode === 'PREFIX_DATE'">
              <el-form-item label="表前缀" class="portal-field-lg">
                <el-input v-model="form.tablePrefix" placeholder="如 biz_order_" />
              </el-form-item>
              <el-form-item label="日期格式" class="portal-field-md">
                <el-input v-model="form.datePattern" placeholder="yyyyMMdd" />
              </el-form-item>
              <el-form-item label="日期偏移" class="portal-field-sm">
                <el-input-number v-model="form.dateOffsetDays" :min="-30" :max="0" />
              </el-form-item>
            </template>
          </template>
          <template v-else-if="form.accessMode === 'MULTI'">
            <el-form-item label="选择表" class="portal-field-xl">
              <el-select
                v-model="form.sourceTables"
                multiple
                filterable
                collapse-tags
                :loading="metaTablesLoading"
                :disabled="!form.metaDataSourceId"
                placeholder="输入表名筛选，可多选"
                @change="refreshTaskName"
              >
                <el-option
                  v-for="t in metaTables"
                  :key="t"
                  :label="t"
                  :value="t"
                />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button :disabled="!metaTables.length" @click="form.sourceTables = [...metaTables]">全选当前数据源</el-button>
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="SELECT SQL">
              <el-input
                v-model="form.selectSql"
                type="textarea"
                :rows="5"
                placeholder="SELECT a.id, b.name FROM t1 a JOIN t2 b ON ... WHERE dt = ${biz_date}"
                style="width: min(720px, 100%)"
              />
            </el-form-item>
            <el-form-item label="biz_date" class="portal-field-lg">
              <el-input v-model="form.paramBizDate" placeholder="DATE_OFFSET:-1" />
            </el-form-item>
          </template>
        </template>

        <template v-else-if="step === 2">
          <el-form-item label="同步约定">
            <el-radio-group v-model="form.syncMode" @change="onSyncModeChange">
              <el-radio value="T+1">T+1 日批</el-radio>
              <el-radio value="REALTIME">实时/立即</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="写入方式">
            <el-radio-group v-model="form.writeMode">
              <el-radio value="FULL">全量（重建目标表）</el-radio>
              <el-radio value="INCREMENTAL">增量（追加）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.writeMode === 'INCREMENTAL' && form.accessMode === 'SINGLE'" label="增量列" class="portal-field-lg">
            <el-input v-model="form.incrementColumn" placeholder="如 create_time / update_time" />
          </el-form-item>
        </template>

        <template v-else-if="step === 3">
          <el-form-item label="目标库" class="portal-field-xl" required>
            <div class="conn-pick">
              <el-input
                :model-value="form.targetConnectionLabel || form.targetConnection"
                readonly
                placeholder="点击选择目标库"
              />
              <el-button type="primary" @click="openTargetDsPicker">选择</el-button>
            </div>
          </el-form-item>
          <template v-if="form.accessMode === 'MULTI'">
            <el-form-item label="目标命名" class="portal-field-xl">
              <el-input v-model="form.targetTableRule" placeholder="ods_{sourceTable}" />
            </el-form-item>
          </template>
          <template v-else-if="form.accessMode === 'SQL'">
            <el-form-item label="目标表" class="portal-field-xl">
              <el-input
                v-model="form.sqlTargetTable"
                clearable
                placeholder="请手动填写，须以 ods_ 开头"
              />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="目标表" class="portal-field-xl">
              <el-input
                v-model="form.targetTable"
                clearable
                placeholder="请手动填写，须以 ods_ 开头"
              />
            </el-form-item>
          </template>
        </template>

        <template v-else-if="step === 4">
          <p v-if="form.accessMode === 'MULTI'" class="hint">多表批量将按各表自己的登记字段自动同名映射，无需在此配置。</p>
          <template v-if="form.accessMode !== 'MULTI'">
            <el-form-item class="portal-form-actions">
              <el-button :loading="busy" @click="loadMapping('ORDER')">顺序映射</el-button>
              <el-button type="primary" :loading="busy" @click="loadMapping('NAME')">同名映射</el-button>
              <el-button @click="form.pairs.push({ source: '', target: '', dataType: 'VARCHAR', length: 255 })">手工加行</el-button>
            </el-form-item>
            <el-table :data="form.pairs" stripe size="small" style="width: 100%; margin-bottom: 12px">
              <el-table-column label="源字段" min-width="140">
                <template #default="{ row }">
                  <el-input v-model="row.source" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="目标字段" min-width="140">
                <template #default="{ row }">
                  <el-input v-model="row.target" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="类型" width="130">
                <template #default="{ row }">
                  <el-select
                    v-model="row.dataType"
                    size="small"
                    filterable
                    allow-create
                    default-first-option
                    style="width: 100%"
                    @change="(v: string) => { if (!typeNeedsLength(v)) row.length = undefined; else if (!row.length) row.length = 255 }"
                  >
                    <el-option v-for="t in FIELD_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="长度" width="100">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.length"
                    size="small"
                    :min="1"
                    :max="4000"
                    :disabled="!typeNeedsLength(row.dataType)"
                    controls-position="right"
                    style="width: 100%"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="form.pairs.splice($index, 1)">删</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </template>

        <template v-else>
          <template v-if="form.syncMode === 'REALTIME'">
            <p class="hint">当前为「实时/立即」：不启用定时调度，仅可通过列表「执行」或保存后立即执行触发。</p>
          </template>
          <template v-else>
            <el-form-item label="执行周期" class="portal-field-cron">
              <ExecCycleSelect v-model="form.scheduleCron" :allow-custom="false" />
            </el-form-item>
            <p class="hint">保存为草稿后先「上线」，再点「启动」将周期发布到调度；实时任务仅支持手动执行。</p>
          </template>
        </template>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button :disabled="step === 0" @click="step -= 1">上一步</el-button>
          <el-button v-if="step < steps.length - 1" type="primary" @click="nextStep">下一步</el-button>
          <template v-else>
            <el-button type="primary" :loading="busy" @click="saveJob(false)">保存任务</el-button>
            <el-button type="success" :loading="busy || runBusy" @click="saveJob(true)">保存、上线并执行</el-button>
          </template>
          <el-button @click="dialogVisible = false">取消</el-button>
        </div>
      </template>
    </el-dialog>
    <MetaDataSourcePickerDialog v-model="targetDsPickerVisible" title="选择目标库" @confirm="onTargetDsPicked" />
    <MetaDataSourcePickerDialog v-model="sourceDsPickerVisible" title="选择数据源" @confirm="onSourceDsPicked" />

    <el-dialog
      v-model="resultVisible"
      :title="resultTitle"
      width="640px"
      append-to-body
      destroy-on-close
    >
      <el-descriptions v-if="resultRow" :column="2" border size="small">
        <el-descriptions-item label="任务名称" :span="2">{{ resultRow.taskName }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">
          <el-tag :type="lifecycleTagType(lifecycleOf(resultRow))" size="small">{{ statusLabel(lifecycleOf(resultRow)) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最近运行">
          <el-tag :type="jobStatusTagType(resultRow.status)" size="small">{{ statusLabel(resultRow.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最近执行">{{ resultRow.lastRunAt || '—' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ resultRow.versionNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="抽取行数">
          {{ resultPayload?.collectedRows ?? resultRow.collectedRows ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="耗时">
          {{ formatDuration(Number(resultPayload?.durationMs ?? resultRow.durationMs)) }}
        </el-descriptions-item>
        <el-descriptions-item label="读取行数">
          {{ resultPayload?.linesInput ?? resultRow.linesInput ?? '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="写入行数">
          {{ resultPayload?.linesOutput ?? resultRow.linesOutput ?? '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="拒绝行数">
          {{ resultPayload?.linesRejected ?? resultRow.linesRejected ?? '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="目标表">
          {{ resultPayload?.odsTable || resultRow.targetTable || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="运行日志" :span="2">
          {{ resultRow.lastRunMessage || '—' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="resultRow.errorDetail" label="错误明细" :span="2">
          <pre class="result-error">{{ resultRow.errorDetail }}</pre>
        </el-descriptions-item>
        <el-descriptions-item
          v-if="Array.isArray(resultPayload?.errors) && (resultPayload?.errors as unknown[]).length"
          label="多表失败明细"
          :span="2"
        >
          <ul class="result-list">
            <li v-for="(err, idx) in (resultPayload?.errors as string[])" :key="idx">{{ err }}</li>
          </ul>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="versionVisible" :title="`版本历史 · ${versionTaskName}`" width="640px" append-to-body destroy-on-close>
      <el-table v-loading="versionBusy" :data="versionRows" size="small" stripe empty-text="暂无版本（上线后生成）">
        <el-table-column prop="versionNo" label="版本" width="80" align="center" />
        <el-table-column prop="changeSummary" label="说明" min-width="160" />
        <el-table-column prop="publishedBy" label="上线人" width="100" align="center" />
        <el-table-column prop="publishedAt" label="上线时间" width="170" align="center" />
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="logVisible"
      :title="`运行日志 · ${logTask?.taskName || ''}`"
      width="860px"
      append-to-body
      destroy-on-close
    >
      <el-table v-loading="logBusy" :data="logRuns" size="small" stripe empty-text="暂无运行记录">
        <el-table-column label="采集数量" width="100" align="center">
          <template #default="{ row }">{{ row.collectedRows ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="新增数量" width="100" align="center">
          <template #default="{ row }">{{ row.insertRows ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="更新数量" width="100" align="center">
          <template #default="{ row }">{{ row.updateRows ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" min-width="160" align="center" />
        <el-table-column prop="finishedAt" label="结束时间" min-width="160" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="jobStatusTagType(row.runStatus)" size="small">{{ statusLabel(row.runStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRunDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="logDetailVisible"
      :title="`日志详情 · ${logTask?.taskName || ''}`"
      width="920px"
      append-to-body
      destroy-on-close
    >
      <div class="log-filter">
        <el-select v-model="logFilter.runStatus" style="width: 140px" placeholder="执行结果">
          <el-option label="全部" value="ALL" />
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="部分成功" value="PARTIAL" />
        </el-select>
        <el-date-picker
          v-model="logFilter.from"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm:ss"
          placeholder="开始时间"
          style="width: 200px"
        />
        <el-date-picker
          v-model="logFilter.to"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          format="YYYY-MM-DD HH:mm:ss"
          placeholder="结束时间"
          style="width: 200px"
        />
        <el-button type="primary" :loading="logBusy" @click="reloadRuns">查询</el-button>
        <el-button @click="logFilter.runStatus = 'ALL'; logFilter.from = ''; logFilter.to = ''; reloadRuns()">重置</el-button>
      </div>
      <el-table v-loading="logBusy" :data="logRuns" size="small" stripe empty-text="暂无记录" style="margin-top: 12px">
        <el-table-column prop="id" label="调度任务ID" width="110" align="center" />
        <el-table-column prop="scheduleTime" label="调度时间" min-width="160" align="center" />
        <el-table-column label="调度结果" width="100" align="center">
          <template #default="{ row }">{{ statusLabel(row.scheduleResult || '—') }}</template>
        </el-table-column>
        <el-table-column prop="startedAt" label="执行时间" min-width="160" align="center" />
        <el-table-column label="执行结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="jobStatusTagType(row.runStatus)" size="small">{{ statusLabel(row.runStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRunDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="logDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="runDetailVisible" title="采集任务日志详情" width="760px" append-to-body destroy-on-close>
      <template v-if="runDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="运行ID">{{ runDetail.runId }}</el-descriptions-item>
          <el-descriptions-item label="任务">{{ runDetail.taskName }}</el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ runDetail.triggerType === 'SCHEDULE' ? '定时' : '手动' }}</el-descriptions-item>
          <el-descriptions-item label="执行状态">
            <el-tag :type="jobStatusTagType(runDetail.runStatus)" size="small">{{ statusLabel(runDetail.runStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ runDetail.startedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ runDetail.finishedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="执行耗时">{{ formatDuration(Number(runDetail.durationMs)) }}</el-descriptions-item>
          <el-descriptions-item label="目标表">{{ runDetail.targetTable || '—' }}</el-descriptions-item>
          <el-descriptions-item label="采集表数">{{ runDetail.tableCount ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="采集行数">{{ runDetail.collectedRows ?? 0 }}</el-descriptions-item>
        </el-descriptions>
        <div class="diff-cards">
          <div class="diff-card diff-card--add">新增 {{ runDetail.added ?? 0 }}</div>
          <div class="diff-card diff-card--del">删除 {{ runDetail.deleted ?? 0 }}</div>
          <div class="diff-card diff-card--chg">变更 {{ runDetail.changed ?? 0 }}</div>
        </div>
        <pre class="run-log">{{ runDetail.logText || runDetail.message || '—' }}</pre>
      </template>
      <template #footer>
        <el-button type="primary" @click="runDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.op-cell {
  display: inline-flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: center;
  gap: 2px;
  white-space: nowrap;
}
.op-more-icon {
  margin-left: 2px;
  font-size: 12px;
}
.op-danger {
  color: var(--el-color-danger) !important;
}
.op-warning {
  color: var(--el-color-warning) !important;
}
.conn-pick {
  display: flex;
  gap: 8px;
  width: 100%;
}
.conn-pick .el-input {
  flex: 1;
}
.task-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.task-name-row .el-input {
  flex: 1;
}
.task-name-hint {
  font-size: 12px;
  color: var(--portal-text-secondary, #909399);
}
.wiz-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.wiz-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.wiz-title {
  font-size: 15px;
  font-weight: 600;
}
.dialog-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
}
.result-error {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  color: var(--el-color-danger);
  max-height: 180px;
  overflow: auto;
}
.result-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
}
.log-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.diff-cards {
  display: flex;
  gap: 12px;
  margin: 16px 0;
}
.diff-card {
  flex: 1;
  padding: 12px;
  border-radius: 6px;
  text-align: center;
  font-weight: 600;
}
.diff-card--add { background: #f0f9eb; color: #67c23a; }
.diff-card--del { background: #fef0f0; color: #f56c6c; }
.diff-card--chg { background: #fdf6ec; color: #e6a23c; }
.run-log {
  margin: 0;
  padding: 12px;
  max-height: 260px;
  overflow: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  font-size: 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
