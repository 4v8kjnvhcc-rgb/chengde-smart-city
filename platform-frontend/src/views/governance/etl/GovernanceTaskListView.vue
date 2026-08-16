<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'
import MetaDataSourcePickerDialog from '@/components/common/MetaDataSourcePickerDialog.vue'
import type { MetaBindSource } from '@/utils/meta-datasource-conn'
import { connectionKeyOf, displayNameOfConnection } from '@/utils/meta-datasource-conn'
import { useExecCycleLabel } from '@/utils/exec-cycle-label'

export type ListMode = 'mgmt' | 'run' | 'schedule'

interface TaskRow {
  id: number
  taskCode: string
  taskName: string
  description?: string
  status: string
  engineType?: string
  lockedBy?: string
  lastRunAt?: string
  lastMessage?: string
  lastRunStatus?: string
  lastRunId?: number
  updatedAt?: string
  scheduleEnabled?: boolean
  scheduleCron?: string
  scheduleMode?: string
  startTime?: string
  timeUnit?: string
  intervalValue?: number
  nextRunAt?: string
  dsScheduleId?: number | null
  dsProjectCode?: number | null
}

interface DsOption {
  value: string
  label: string
  sourceId?: number
  kind: 'platform' | 'external' | 'meta'
}

interface TableOption {
  id: number
  sourceId: number
  tableName: string
  tableCode?: string
  physicalTableName?: string
  sourceSchema?: string
}

const RULE_OPTIONS = [
  { type: 'FILTER', label: '过滤' },
  { type: 'FIELD_PROCESS', label: '字段处理' },
  { type: 'DEDUPLICATE', label: '去重' },
  { type: 'MASK', label: '脱敏' },
]

const PLATFORM_SOURCES_GOV: DsOption[] = [
  { value: 'smart_city_ods', label: '平台 ODS（smart_city_ods）', kind: 'platform' },
  { value: 'smart_city_dwd', label: '平台 DWD（smart_city_dwd）', kind: 'platform' },
]

const PLATFORM_SOURCES_FUSION: DsOption[] = [
  { value: 'smart_city_dwd', label: '平台 DWD（过程层，融合输入）', kind: 'platform' },
]

const TARGET_SOURCES_GOV: DsOption[] = [
  { value: 'smart_city_dwd', label: '平台 DWD（过程层产出）', kind: 'platform' },
]

const TARGET_SOURCES_FUSION: DsOption[] = [
  { value: 'smart_city_dws', label: '平台 DWS（主题/基础库）', kind: 'platform' },
  { value: 'smart_city_ads', label: '平台 ADS（专题/应用）', kind: 'platform' },
]

/** 与元数据采集侧平台分层虚拟源 ID 对齐 */
const PLATFORM_LAYER_IDS: Record<string, number> = {
  smart_city_ods: -1,
  smart_city_dwd: -2,
  smart_city_dws: -3,
  smart_city_ads: -4,
}

const props = withDefaults(defineProps<{
  mode?: ListMode
  taskDomain?: 'GOVERNANCE' | 'FUSION'
}>(), {
  mode: 'mgmt',
  taskDomain: 'GOVERNANCE',
})

const isFusion = computed(() => props.taskDomain === 'FUSION')
const PLATFORM_SOURCES = computed(() => (isFusion.value ? PLATFORM_SOURCES_FUSION : PLATFORM_SOURCES_GOV))
const TARGET_SOURCES = computed(() => (isFusion.value ? TARGET_SOURCES_FUSION : TARGET_SOURCES_GOV))

const emit = defineEmits<{
  design: [id: number]
  monitor: [id: number]
}>()

const route = useRoute()
const { label: cycleLabel } = useExecCycleLabel()
const router = useRouter()

const tasks = ref<TaskRow[]>([])
const loading = ref(false)
const createVisible = ref(false)
const creating = ref(false)
const renameVisible = ref(false)
const renameId = ref<number | null>(null)
const scheduleVisible = ref(false)
const scheduleId = ref<number | null>(null)
const selectedIds = ref<number[]>([])
const varDialogVisible = ref(false)
const runTargetId = ref<number | null>(null)
const varDefs = ref<Array<{ name: string; label?: string; defaultValue?: string; required?: boolean }>>([])
const varForm = ref<Record<string, string>>({})

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('查看结果')
const previewMeta = ref('')
const previewMessage = ref('')
const previewColumns = ref<string[]>([])
const previewRows = ref<Record<string, string | null>[]>([])
const previewTargets = ref<Array<{ database: string; table: string; layer: string }>>([])
const previewSelectedTable = ref('')
const previewTaskId = ref<number | null>(null)

const sourceOptions = ref<DsOption[]>([...PLATFORM_SOURCES_GOV])
const allTables = ref<TableOption[]>([])
const platformTables = ref<string[]>([])
const platformTablesLoading = ref(false)
const targetPlatformTables = ref<string[]>([])
const targetTablesLoading = ref(false)

const form = reactive({
  taskName: '',
  description: '',
  sourceConnection: '',
  sourceConnectionLabel: '',
  sourceTable: '',
  sourceTable2: '',
  joinKey: 'id',
  targetConnection: '',
  targetConnectionLabel: '',
  targetTable: '',
  rules: [] as string[],
  scheduleEnabled: false,
  scheduleMode: 'CRON' as 'CRON' | 'SIMPLE',
  scheduleCron: '0 0 2 * * ?',
  startTime: '',
  timeUnit: 'DAY',
  intervalValue: 1,
})

const dsPickerVisible = ref(false)
const dsPickerRole = ref<'source' | 'target'>('source')
const connLabelMap = ref<Record<string, string>>({})

function openDsPicker(role: 'source' | 'target') {
  dsPickerRole.value = role
  dsPickerVisible.value = true
}

function onDsPicked(row: MetaBindSource) {
  const key = connectionKeyOf(row)
  const label = `${row.sourceName}${row.databaseName ? `（${row.databaseName}）` : ''}`
  connLabelMap.value = { ...connLabelMap.value, [key]: label }
  const metaOpt: DsOption = { value: key, label, kind: key.startsWith('meta:') ? 'meta' : 'platform' }
  if (!sourceOptions.value.some(s => s.value === key)) {
    sourceOptions.value = [...sourceOptions.value, metaOpt]
  }
  if (dsPickerRole.value === 'source') {
    form.sourceConnection = key
    form.sourceConnectionLabel = label
    form.sourceTable = ''
    form.sourceTable2 = ''
    void loadPlatformTables(key)
  } else {
    form.targetConnection = key
    form.targetConnectionLabel = label
    form.targetTable = ''
    if (isFusion.value) void loadTargetPlatformTables(key)
  }
}

function sourceDisplay() {
  return form.sourceConnectionLabel
    || displayNameOfConnection(form.sourceConnection, connLabelMap.value)
    || form.sourceConnection
}

function targetDisplay() {
  return form.targetConnectionLabel
    || displayNameOfConnection(form.targetConnection, connLabelMap.value)
    || form.targetConnection
}

const renameForm = reactive({
  taskName: '',
})

const scheduleForm = reactive({
  scheduleEnabled: false,
  scheduleMode: 'CRON' as 'CRON' | 'SIMPLE',
  scheduleCron: '0 0 2 * * ?',
  startTime: '',
  timeUnit: 'DAY',
  intervalValue: 1,
  nextRunAt: '',
})

const pageTitle = computed(() => {
  const prefix = isFusion.value ? '融合加工' : '治理'
  if (props.mode === 'run') return `${prefix}任务运行`
  if (props.mode === 'schedule') return `${prefix}任务定时`
  return `${prefix}任务管理`
})

const createDialogTitle = computed(() =>
  isFusion.value ? '新建融合加工任务（DWD→DWS/ADS）' : '新增治理任务（ODS→DWD）',
)

/** 运行页：排除草稿与停用；各模式再叠加查询条件 */
const listQuery = reactive({
  keyword: '',
  status: '',
  scheduleFlag: '' as '' | 'ON' | 'OFF',
})

const displayTasks = computed(() => {
  let list = tasks.value
  if (props.mode === 'run') {
    list = list.filter((t) => t.status !== 'DRAFT' && t.status !== 'DISABLED')
  }
  const kw = listQuery.keyword.trim().toLowerCase()
  if (kw) {
    list = list.filter((t) =>
      `${t.taskName || ''} ${t.taskCode || ''} ${t.description || ''}`.toLowerCase().includes(kw),
    )
  }
  if (props.mode === 'mgmt' && listQuery.status) {
    list = list.filter((t) => t.status === listQuery.status)
  }
  if (props.mode === 'run' && listQuery.status) {
    list = list.filter((t) => String(t.lastRunStatus || '').toUpperCase() === listQuery.status)
  }
  if (props.mode === 'schedule' && listQuery.scheduleFlag) {
    const on = listQuery.scheduleFlag === 'ON'
    list = list.filter((t) => !!t.scheduleEnabled === on)
  }
  return list
})
const {
  page: taskPage,
  pageSize: taskPageSize,
  paged: pagedTasks,
  total: taskTotal,
  resetPage: resetTaskPage,
} = useClientPager(displayTasks)

async function searchTasks() {
  await load()
}

const selectedSource = computed(() => sourceOptions.value.find(s => s.value === form.sourceConnection))

/** 外部登记源：按 sourceId 过滤已登记表 */
const externalTableOptions = computed(() => {
  const sid = selectedSource.value?.sourceId
  if (sid == null) return []
  return allTables.value.filter(t => t.sourceId === sid)
})

/**
 * 平台分层库：优先用 JDBC 探到的实表；并补充登记资产中落在该库的物理表名
 * （避免探库暂时失败时选不到已采集表）
 */
const platformTableOptions = computed(() => {
  const db = form.sourceConnection
  if (!db || selectedSource.value?.kind !== 'platform') return []
  const names = new Set<string>(platformTables.value)
  const prefix =
    db === 'smart_city_ods' ? 'ods_'
      : db === 'smart_city_dwd' ? 'dwd_'
        : db === 'smart_city_dws' ? 'dws_'
          : db === 'smart_city_ads' ? 'ads_'
            : ''
  for (const t of allTables.value) {
    const physical = (t.physicalTableName || '').trim()
    if (!physical) continue
    const schema = (t.sourceSchema || '').trim().toLowerCase()
    if (schema === db.toLowerCase() || (prefix && physical.toLowerCase().startsWith(prefix))) {
      names.add(physical)
    }
  }
  return Array.from(names).sort((a, b) => a.localeCompare(b))
})

const sourceTableSelectOptions = computed(() => {
  const conn = form.sourceConnection
  if (conn.startsWith('meta:') || selectedSource.value?.kind === 'meta') {
    return platformTables.value.map(name => ({ value: name, label: name }))
  }
  if (selectedSource.value?.kind === 'platform' || PLATFORM_LAYER_IDS[conn] != null) {
    return platformTableOptions.value.map(name => ({ value: name, label: name }))
  }
  return externalTableOptions.value.map(t => ({
    value: t.physicalTableName || t.tableName,
    label: t.physicalTableName && t.physicalTableName !== t.tableName
      ? `${t.tableName}（${t.physicalTableName}）`
      : t.tableName,
  }))
})

/** 目标库已有表：输入时可实时筛选；allow-create 仍可写新表名 */
const targetTableSelectOptions = computed(() =>
  targetPlatformTables.value.map(name => ({ value: name, label: name })),
)

async function loadPlatformTables(connection: string) {
  platformTables.value = []
  if (!connection) return
  platformTablesLoading.value = true
  try {
    platformTables.value = await fetchConnectionTables(connection)
  } catch (e: unknown) {
    platformTables.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载源表失败')
  } finally {
    platformTablesLoading.value = false
  }
}

async function loadTargetPlatformTables(connection: string) {
  targetPlatformTables.value = []
  if (!connection) return
  targetTablesLoading.value = true
  try {
    targetPlatformTables.value = await fetchConnectionTables(connection)
  } catch (e: unknown) {
    targetPlatformTables.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载目标表失败')
  } finally {
    targetTablesLoading.value = false
  }
}

async function fetchConnectionTables(connection: string): Promise<string[]> {
  if (connection.startsWith('meta:')) {
    const metaId = Number(connection.slice(5))
    if (!Number.isFinite(metaId) || metaId <= 0) return []
    const rows = (await api.get(`/governance/platform/metadata/collect/meta-data-sources/${metaId}/tables`)).data || []
    return (rows as Array<{ sourceTable?: string; tableName?: string }>)
      .map(r => String(r.sourceTable || r.tableName || '').trim())
      .filter(Boolean)
      .sort((a, b) => a.localeCompare(b))
  }
  const layerId = PLATFORM_LAYER_IDS[connection]
  if (layerId == null) return []
  const rows = (await api.get(`/governance/platform/metadata/collect/data-sources/${layerId}/tables`)).data || []
  return (rows as Array<{ sourceTable?: string }>)
    .map(r => String(r.sourceTable || '').trim())
    .filter(Boolean)
    .sort((a, b) => a.localeCompare(b))
}

watch(() => form.sourceConnection, (conn) => {
  form.sourceTable = ''
  platformTables.value = []
  if (conn && (PLATFORM_LAYER_IDS[conn] != null || conn.startsWith('meta:'))) {
    void loadPlatformTables(conn)
  }
})

watch(() => form.targetConnection, (conn) => {
  form.targetTable = ''
  targetPlatformTables.value = []
  if (isFusion.value && conn && (PLATFORM_LAYER_IDS[conn] != null || conn.startsWith('meta:'))) {
    void loadTargetPlatformTables(conn)
  }
})

function goEtlSub(sub: string, taskId?: number) {
  if (isFusion.value) {
    if (taskId != null) {
      emit('monitor', taskId)
      return
    }
    // 融合域：侧栏在「数据融合处理」下，不能写 tab=etl（会跳到数据治理）
    const q: Record<string, unknown> = { ...route.query, tab: 'model' }
    delete q.etlView
    delete q.taskId
    if (sub === 'task-run') {
      q.mSub = 'execute'
      q.execTab = 'run'
    } else if (sub === 'task-schedule') {
      q.mSub = 'schedule'
      delete q.execTab
    } else {
      q.mSub = 'clean'
      delete q.execTab
    }
    router.replace({ query: q as Record<string, string> })
    return
  }
  const q: Record<string, unknown> = { ...route.query, tab: 'etl', etlSub: sub }
  delete q.etlView
  if (taskId != null) {
    q.etlView = 'monitor'
    q.taskId = String(taskId)
  } else {
    delete q.taskId
  }
  router.replace({ query: q as Record<string, string> })
}

async function load() {
  loading.value = true
  try {
    tasks.value = (await api.get('/governance/gov-tasks', {
      params: { taskDomain: props.taskDomain },
    })).data || []
    resetTaskPage()
  } catch {
    ElMessage.error(isFusion.value ? '加载融合任务失败' : '加载治理任务失败')
  } finally {
    loading.value = false
  }
}

async function loadCreateOptions() {
  // 来源/目标均通过「选择数据源」弹窗对接元数据数据源管理；此处仅保留平台分层快捷项
  sourceOptions.value = [...(isFusion.value ? PLATFORM_SOURCES_FUSION : PLATFORM_SOURCES_GOV)]
  allTables.value = []
}

function openCreate() {
  form.taskName = ''
  form.description = ''
  form.sourceConnection = ''
  form.sourceConnectionLabel = ''
  form.sourceTable = ''
  form.sourceTable2 = ''
  form.joinKey = 'id'
  form.targetConnection = ''
  form.targetConnectionLabel = ''
  form.targetTable = ''
  form.rules = []
  form.scheduleEnabled = false
  form.scheduleMode = 'CRON'
  form.scheduleCron = '0 0 2 * * ?'
  form.startTime = ''
  form.timeUnit = 'DAY'
  form.intervalValue = 1
  platformTables.value = []
  targetPlatformTables.value = []
  createVisible.value = true
  void loadCreateOptions()
}

async function submitCreate() {
  if (!form.taskName.trim()) {
    ElMessage.warning('请输入任务名称')
    return
  }
  if (!form.sourceConnection) {
    ElMessage.warning(isFusion.value ? '请选择源库' : '请选择来源库')
    return
  }
  if (!form.targetConnection) {
    ElMessage.warning('请选择目标库')
    return
  }
  if (isFusion.value && !form.sourceTable.trim()) {
    ElMessage.warning('请选择至少一张 DWD 源表')
    return
  }
  if (isFusion.value && form.sourceTable2 && form.sourceTable2 === form.sourceTable) {
    ElMessage.warning('第二张源表不能与第一张相同')
    return
  }
  if (form.scheduleEnabled && form.scheduleMode === 'CRON' && !form.scheduleCron.trim()) {
    ElMessage.warning('请填写 Cron 表达式')
    return
  }
  if (form.scheduleEnabled && form.scheduleMode === 'SIMPLE' && !form.startTime) {
    ElMessage.warning('请选择起始时间')
    return
  }
  creating.value = true
  try {
    const id = (await api.post('/governance/gov-tasks', {
      taskDomain: props.taskDomain,
      taskName: form.taskName.trim(),
      description: form.description || undefined,
      sourceConnection: form.sourceConnection || undefined,
      sourceTable: form.sourceTable || undefined,
      sourceTable2: form.sourceTable2 || undefined,
      joinKey: form.joinKey || 'id',
      targetConnection: form.targetConnection || undefined,
      targetTable: form.targetTable || undefined,
      rules: form.rules,
      scheduleEnabled: form.scheduleEnabled,
      scheduleMode: form.scheduleMode,
      scheduleCron: form.scheduleCron.trim(),
      startTime: form.startTime || undefined,
      timeUnit: form.timeUnit,
      intervalValue: form.intervalValue,
    })).data
    ElMessage.success(isFusion.value ? '融合任务已创建' : '已创建，可进入开发调整')
    createVisible.value = false
    await load()
    openDesign(id as number)
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '创建失败')
  } finally {
    creating.value = false
  }
}

function openDesign(id: number) {
  emit('design', id)
}

function openMonitor(id: number) {
  emit('monitor', id)
}

function openRename(row: TaskRow) {
  renameId.value = row.id
  renameForm.taskName = row.taskName
  renameVisible.value = true
}

async function submitRename() {
  if (!renameId.value || !renameForm.taskName.trim()) return
  await api.post(`/governance/gov-tasks/${renameId.value}/rename`, {
    taskName: renameForm.taskName.trim(),
  })
  ElMessage.success('已重命名')
  renameVisible.value = false
  await load()
}

async function lockTask(row: TaskRow) {
  await api.post(`/governance/gov-tasks/${row.id}/lock`)
  ElMessage.success('已锁定')
  await load()
}

async function unlockTask(row: TaskRow) {
  await api.post(`/governance/gov-tasks/${row.id}/unlock`)
  ElMessage.success('已解锁')
  await load()
}

async function runTask(row: TaskRow) {
  try {
    const vres = await api.get(`/governance/tasks/${row.id}/variables`)
    const list = (vres.data || []) as Array<{ name: string; label?: string; defaultValue?: string; required?: boolean }>
    if (list.length > 0) {
      runTargetId.value = row.id
      varDefs.value = list
      varForm.value = {}
      list.forEach((v) => { varForm.value[v.name] = v.defaultValue || '' })
      varDialogVisible.value = true
      return
    }
    const res = await api.post(`/governance/kettle/tasks/${row.id}/execute`)
    const data = res.data as { status?: string; message?: string } | undefined
    if (String(data?.status || '').toUpperCase() === 'FAILED') {
      ElMessage.error(data?.message || '启动执行失败')
      return
    }
    ElMessage.success(data?.message || '执行已启动')
    await load()
    openMonitor(row.id)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '启动执行失败')
  }
}

async function confirmVarRun() {
  if (!runTargetId.value) return
  for (const v of varDefs.value) {
    if (v.required && !String(varForm.value[v.name] || '').trim()) {
      ElMessage.warning(`请填写变量：${v.label || v.name}`)
      return
    }
  }
  try {
    const res = await api.post(`/governance/kettle/tasks/${runTargetId.value}/execute`, { ...varForm.value })
    const data = res.data as { status?: string; message?: string } | undefined
    if (String(data?.status || '').toUpperCase() === 'FAILED') {
      ElMessage.error(data?.message || '启动执行失败')
      return
    }
    ElMessage.success(data?.message || '执行已启动')
    varDialogVisible.value = false
    const id = runTargetId.value
    runTargetId.value = null
    await load()
    openMonitor(id)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '启动执行失败')
  }
}

async function stopTask(row: TaskRow) {
  await api.post(`/governance/gov-tasks/${row.id}/stop`)
  ElMessage.success('已停止')
  await load()
}

async function removeTask(row: TaskRow) {
  await ElMessageBox.confirm(`确认删除任务「${row.taskName}」？删除后不可恢复。`, '删除确认', { type: 'warning' })
  await api.delete(`/governance/gov-tasks/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

async function batchDelete() {
  await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 个任务？删除后不可恢复。`, '批量删除确认', { type: 'warning' })
  try {
    await api.post('/governance/gov-tasks/batch-delete', { ids: selectedIds.value })
    ElMessage.success(`已删除 ${selectedIds.value.length} 个任务`)
    selectedIds.value = []
    await load()
  } catch {
    ElMessage.error('删除失败')
  }
}

function openSchedule(row: TaskRow) {
  scheduleId.value = row.id
  scheduleForm.scheduleEnabled = !!row.scheduleEnabled
  scheduleForm.scheduleMode = row.scheduleMode === 'SIMPLE' ? 'SIMPLE' : 'CRON'
  scheduleForm.scheduleCron = row.scheduleCron || '0 0 2 * * ?'
  scheduleForm.startTime = row.startTime || ''
  scheduleForm.timeUnit = row.timeUnit || 'DAY'
  scheduleForm.intervalValue = Number(row.intervalValue || 1)
  scheduleForm.nextRunAt = row.nextRunAt || ''
  scheduleVisible.value = true
}

async function submitSchedule() {
  if (!scheduleId.value) return
  if (scheduleForm.scheduleEnabled) {
    if (scheduleForm.scheduleMode === 'CRON' && !scheduleForm.scheduleCron.trim()) {
      ElMessage.warning('请填写 Cron 表达式')
      return
    }
    if (scheduleForm.scheduleMode === 'SIMPLE' && !scheduleForm.startTime) {
      ElMessage.warning('请选择起始时间')
      return
    }
  }
  const res = await api.put(`/governance/gov-tasks/${scheduleId.value}/schedule`, {
    scheduleEnabled: scheduleForm.scheduleEnabled,
    scheduleMode: scheduleForm.scheduleMode,
    scheduleCron: scheduleForm.scheduleCron.trim(),
    startTime: scheduleForm.startTime || undefined,
    timeUnit: scheduleForm.timeUnit,
    intervalValue: scheduleForm.intervalValue,
  })
  scheduleForm.nextRunAt = res.data?.nextRunAt || ''
  ElMessage.success(scheduleForm.scheduleEnabled
    ? '定时已保存并发布到 DolphinScheduler'
    : '定时已停止（已下线 DolphinScheduler 调度）')
  scheduleVisible.value = false
  await load()
}

function scheduleModeLabel(row: TaskRow) {
  if (!row.scheduleEnabled) return '—'
  if (row.scheduleMode === 'SIMPLE') {
    const unitMap: Record<string, string> = { HOUR: '小时', DAY: '天', WEEK: '周', MONTH: '月' }
    return `每 ${row.intervalValue || 1} ${unitMap[row.timeUnit || 'DAY'] || row.timeUnit}`
  }
  return cycleLabel(row.scheduleCron)
}

async function openOutputPreview(row: TaskRow, table?: string) {
  previewTaskId.value = row.id
  previewTitle.value = `治理后数据 · ${row.taskName}`
  previewSelectedTable.value = ''
  previewVisible.value = true
  await loadOutputPreview(table)
}

async function loadOutputPreview(table?: string) {
  if (!previewTaskId.value) return
  previewLoading.value = true
  previewMessage.value = ''
  previewColumns.value = []
  previewRows.value = []
  try {
    const res = await api.get(`/governance/gov-tasks/${previewTaskId.value}/output-preview`, {
      params: {
        limit: 100,
        table: table || previewSelectedTable.value || undefined,
      },
    })
    const d = res.data || {}
    previewTargets.value = d.targets || []
    previewSelectedTable.value = d.table || ''
    previewMeta.value = d.qualifiedName
      ? `治理输出 ${d.layer || ''} · ${d.qualifiedName}`.replace(/\s+/g, ' ').trim()
      : ''
    previewColumns.value = d.columns || []
    previewRows.value = d.rows || []
    previewMessage.value = d.message || '仅展示写入 DWD/DWS/ADS 的治理结果，不含 ODS 原始数据'
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message
      || (e as { message?: string })?.message
      || '加载治理结果失败'
    previewMessage.value = msg
    ElMessage.error(msg)
  } finally {
    previewLoading.value = false
  }
}

async function onPreviewTableChange(table: string) {
  previewSelectedTable.value = table
  await loadOutputPreview(table)
}

onMounted(load)
watch(() => props.taskDomain, () => { void load() })
watch(() => props.mode, () => {
  listQuery.keyword = ''
  listQuery.status = ''
  listQuery.scheduleFlag = ''
  resetTaskPage()
})

defineExpose({ reload: load })
</script>

<template>
  <PageCard :title="pageTitle">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="任务名称" class="portal-field-lg">
        <el-input
          v-model="listQuery.keyword"
          clearable
          placeholder="名称 / 编码"
          @keyup.enter="searchTasks"
        />
      </el-form-item>
      <el-form-item v-if="mode === 'mgmt'" label="生命周期" class="portal-field-md">
        <el-select v-model="listQuery.status" clearable placeholder="全部">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已配置" value="CONFIGURED" />
          <el-option label="锁定" value="LOCKED" />
          <el-option label="已发布" value="PUBLISHED" />
        </el-select>
      </el-form-item>
      <el-form-item v-else-if="mode === 'run'" label="运行状态" class="portal-field-md">
        <el-select v-model="listQuery.status" clearable placeholder="全部">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="运行中" value="RUNNING" />
        </el-select>
      </el-form-item>
      <el-form-item v-else-if="mode === 'schedule'" label="定时状态" class="portal-field-md">
        <el-select v-model="listQuery.scheduleFlag" clearable placeholder="全部">
          <el-option label="已启用" value="ON" />
          <el-option label="未启用" value="OFF" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="searchTasks">查询</el-button>
        <el-button v-if="mode === 'mgmt'" type="primary" plain @click="openCreate">
          {{ isFusion ? '新建融合任务' : '新增治理任务' }}
        </el-button>
        <el-button
          v-if="mode === 'mgmt'"
          type="danger"
          :disabled="selectedIds.length === 0"
          @click="batchDelete"
        >
          批量删除 ({{ selectedIds.length }})
        </el-button>
      </el-form-item>
    </el-form>

    <el-table
      v-loading="loading"
      :data="pagedTasks"
      stripe
      size="small"
      @selection-change="(val: TaskRow[]) => selectedIds = val.map(r => r.id)"
    >
      <el-table-column v-if="mode === 'mgmt'" type="selection" width="48" />
      <el-table-column prop="taskName" label="任务名称" min-width="140" />
      <el-table-column v-if="mode === 'mgmt'" prop="description" label="描述" min-width="120" show-overflow-tooltip />
      <el-table-column v-if="mode === 'mgmt' || mode === 'run'" label="查看数据" width="100">
        <template #default="{ row }">
          <el-button link type="primary" @click="openOutputPreview(row)">查看</el-button>
        </template>
      </el-table-column>
      <el-table-column v-if="mode === 'mgmt'" label="生命周期" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="mode === 'run'" label="最近运行状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.lastRunStatus" :type="statusTagType(row.lastRunStatus)" size="small">
            {{ statusLabel(row.lastRunStatus) }}
          </el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column v-if="mode === 'mgmt'" prop="lockedBy" label="锁定人" width="100">
        <template #default="{ row }">{{ row.lockedBy || '—' }}</template>
      </el-table-column>
      <el-table-column v-if="mode === 'mgmt'" prop="updatedAt" label="更新时间" width="160" />

      <el-table-column
        v-if="mode === 'run'"
        prop="lastMessage"
        label="最近结果"
        min-width="160"
        show-overflow-tooltip
      >
        <template #header>
          <span title="最近一次运行的摘要说明（成功行数 / 失败原因等）">最近结果</span>
        </template>
        <template #default="{ row }">{{ row.lastMessage || '—' }}</template>
      </el-table-column>
      <el-table-column v-if="mode === 'run'" prop="lastRunAt" label="最近运行时间" width="160" />

      <el-table-column v-if="mode === 'schedule'" label="定时状态" width="140">
        <template #default="{ row }">
          <el-tag v-if="row.scheduleEnabled && row.dsScheduleId" type="success" size="small">DS 已上线</el-tag>
          <el-tag v-else-if="row.scheduleEnabled" type="warning" size="small">已启用</el-tag>
          <el-tag v-else type="info" size="small">未启用</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="mode === 'schedule'" label="调度计划" min-width="160">
        <template #default="{ row }">{{ scheduleModeLabel(row) }}</template>
      </el-table-column>
      <el-table-column v-if="mode === 'schedule'" prop="nextRunAt" label="下次执行" width="160">
        <template #default="{ row }">{{ row.nextRunAt || '—' }}</template>
      </el-table-column>

      <el-table-column label="操作" :width="mode === 'mgmt' ? 360 : mode === 'run' ? 220 : 160" fixed="right">
        <template #default="{ row }">
          <!-- 任务管理 -->
          <template v-if="mode === 'mgmt'">
            <el-button link type="primary" @click="openDesign(row.id)">开发</el-button>
            <el-button link @click="openRename(row)">重命名</el-button>
            <el-button v-if="row.status !== 'LOCKED'" link @click="lockTask(row)">锁定</el-button>
            <el-button v-else link @click="unlockTask(row)">解锁</el-button>
            <el-button link @click="goEtlSub('task-run')">去运行</el-button>
            <el-button link @click="goEtlSub('task-schedule')">去定时</el-button>
            <el-button link type="danger" @click="removeTask(row)">删除</el-button>
          </template>
          <!-- 任务运行 -->
          <template v-else-if="mode === 'run'">
            <el-button v-if="row.status === 'RUNNING'" link type="warning" @click="stopTask(row)">停止</el-button>
            <el-button v-else link type="success" @click="runTask(row)">运行</el-button>
            <el-button link @click="openMonitor(row.id)">查看监控</el-button>
          </template>
          <!-- 任务定时 -->
          <template v-else>
            <el-button link type="primary" @click="openSchedule(row)">定时</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination
      v-model:page="taskPage"
      v-model:page-size="taskPageSize"
      :total="taskTotal"
    />

    <el-dialog v-model="createVisible" :title="createDialogTitle" width="640px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.taskName" maxlength="128" placeholder="支持中文" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item :label="isFusion ? '源库' : '来源库'" required>
          <div class="conn-pick">
            <el-input
              :model-value="sourceDisplay()"
              readonly
              :placeholder="isFusion ? '点击选择源库' : '点击选择来源库'"
            />
            <el-button type="primary" @click="openDsPicker('source')">选择</el-button>
          </div>
        </el-form-item>
        <el-form-item :label="isFusion ? '源表1' : '来源表'" :required="isFusion">
          <el-select
            v-if="form.sourceConnection"
            v-model="form.sourceTable"
            clearable
            filterable
            allow-create
            default-first-option
            :loading="platformTablesLoading"
            placeholder="输入表名筛选，或选择/新建"
            style="width:100%"
          >
            <el-option
              v-for="t in sourceTableSelectOptions"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
          <el-input v-else v-model="form.sourceTable" placeholder="请先选择来源库" disabled />
        </el-form-item>
        <el-form-item v-if="isFusion" label="源表2(可选)">
          <el-select
            v-model="form.sourceTable2"
            clearable
            filterable
            allow-create
            default-first-option
            :loading="platformTablesLoading"
            placeholder="输入表名筛选；选第二张表则生成横连接"
            style="width:100%"
          >
            <el-option
              v-for="t in sourceTableSelectOptions"
              :key="`b-${t.value}`"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isFusion && form.sourceTable2" label="关联键">
          <el-input v-model="form.joinKey" placeholder="如 id" />
        </el-form-item>
        <el-form-item label="目标库" required>
          <div class="conn-pick">
            <el-input
              :model-value="targetDisplay()"
              readonly
              placeholder="点击选择目标库"
            />
            <el-button type="primary" @click="openDsPicker('target')">选择</el-button>
          </div>
          <div style="font-size:12px;color:var(--el-text-color-secondary);margin-top:4px">
            {{ isFusion ? '融合产出建议主题/专题库；可从数据源管理任选' : '治理产出建议过程层；可从数据源管理任选' }}
          </div>
        </el-form-item>
        <el-form-item label="目标表">
          <template v-if="form.targetConnection">
            <el-select
              v-if="isFusion"
              v-model="form.targetTable"
              clearable
              filterable
              allow-create
              default-first-option
              :loading="targetTablesLoading"
              placeholder="输入表名筛选，或新建如 dws_xxx"
              style="width:100%"
            >
              <el-option
                v-for="t in targetTableSelectOptions"
                :key="t.value"
                :label="t.label"
                :value="t.value"
              />
            </el-select>
            <el-input
              v-else
              v-model="form.targetTable"
              clearable
              placeholder="请手动填写，须以 dwd_ 开头"
            />
          </template>
          <el-input v-else v-model="form.targetTable" placeholder="请先选择目标库" disabled />
        </el-form-item>
        <el-form-item :label="isFusion ? '清洗规则' : '治理规则'">
          <el-checkbox-group v-model="form.rules">
            <el-checkbox v-for="r in RULE_OPTIONS" :key="r.type" :label="r.type">
              {{ r.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="开启定时">
          <el-switch v-model="form.scheduleEnabled" />
        </el-form-item>
        <template v-if="form.scheduleEnabled">
          <el-form-item label="定时方式">
            <el-radio-group v-model="form.scheduleMode">
              <el-radio-button value="SIMPLE">定时器</el-radio-button>
              <el-radio-button value="CRON">Cron</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <template v-if="form.scheduleMode === 'SIMPLE'">
            <el-form-item label="起始时间">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择起始时间"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="间隔">
              <el-input-number v-model="form.intervalValue" :min="1" :max="999" />
            </el-form-item>
            <el-form-item label="单位">
              <el-radio-group v-model="form.timeUnit">
                <el-radio value="HOUR">小时</el-radio>
                <el-radio value="DAY">天</el-radio>
                <el-radio value="WEEK">周</el-radio>
                <el-radio value="MONTH">月</el-radio>
              </el-radio-group>
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="执行周期">
              <ExecCycleSelect v-model="form.scheduleCron" :allow-custom="false" />
            </el-form-item>
          </template>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建并进入开发</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="renameVisible" title="重命名" width="400px">
      <el-input v-model="renameForm.taskName" maxlength="128" />
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRename">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="scheduleVisible" title="添加定时计划" width="520px">
      <el-form label-width="100px">
        <el-form-item label="启用定时">
          <el-switch v-model="scheduleForm.scheduleEnabled" />
        </el-form-item>
        <el-form-item v-if="scheduleForm.scheduleEnabled" label="定时器选择">
          <el-radio-group v-model="scheduleForm.scheduleMode">
            <el-radio-button value="SIMPLE">定时器选择</el-radio-button>
            <el-radio-button value="CRON">自定义脚本</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="scheduleForm.scheduleEnabled && scheduleForm.scheduleMode === 'SIMPLE'">
          <el-form-item label="起始时间">
            <el-date-picker
              v-model="scheduleForm.startTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择起始时间"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="偏移量">
            <el-input-number v-model="scheduleForm.intervalValue" :min="1" :max="999" />
          </el-form-item>
          <el-form-item label="时间单位">
            <el-radio-group v-model="scheduleForm.timeUnit">
              <el-radio value="HOUR">小时</el-radio>
              <el-radio value="DAY">天</el-radio>
              <el-radio value="WEEK">周</el-radio>
              <el-radio value="MONTH">月</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>
        <template v-if="scheduleForm.scheduleEnabled && scheduleForm.scheduleMode === 'CRON'">
          <el-form-item label="执行周期">
            <ExecCycleSelect v-model="scheduleForm.scheduleCron" :allow-custom="false" />
          </el-form-item>
        </template>
        <el-form-item v-if="scheduleForm.nextRunAt" label="下次运行">
          <span>{{ scheduleForm.nextRunAt }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scheduleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSchedule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="varDialogVisible" title="添加变量" width="480px">
      <el-form label-width="100px">
        <el-form-item
          v-for="v in varDefs"
          :key="v.name"
          :label="v.label || v.name"
          :required="!!v.required"
        >
          <el-input v-model="varForm[v.name]" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="varDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmVarRun">运行</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="previewVisible"
      :title="previewTitle"
      size="72%"
      destroy-on-close
    >
      <div v-loading="previewLoading" class="output-preview">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item v-if="previewTargets.length > 1" label="治理输出表" class="portal-field-xl">
            <el-select
              :model-value="previewSelectedTable"
              filterable
              placeholder="输入表名筛选"
              @change="onPreviewTableChange"
            >
              <el-option
                v-for="t in previewTargets"
                :key="`${t.database}.${t.table}`"
                :label="`${t.layer} · ${t.database}.${t.table}`"
                :value="t.table"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="previewMeta" label="当前表">
            <span>{{ previewMeta }}</span>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="loadOutputPreview()">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-alert
          type="warning"
          show-icon
          :closable="false"
          class="output-preview__msg"
          title="此处只读治理后落层结果（DWD/DWS/ADS），不会展示 ODS 原始数据"
        />
        <el-alert
          v-if="previewMessage"
          :title="previewMessage"
          :type="previewRows.length ? 'success' : 'info'"
          show-icon
          :closable="false"
          class="output-preview__msg"
        />
        <el-table
          v-if="previewColumns.length"
          :data="previewRows"
          stripe
          size="small"
          border
          max-height="560"
        >
          <el-table-column
            v-for="col in previewColumns"
            :key="col"
            :prop="col"
            :label="col"
            min-width="120"
            show-overflow-tooltip
          />
        </el-table>
        <el-empty v-else-if="!previewLoading" description="暂无治理后数据，请先成功运行任务写入 DWD/DWS/ADS" />
      </div>
    </el-drawer>

    <MetaDataSourcePickerDialog v-model="dsPickerVisible" @confirm="onDsPicked" />
  </PageCard>
</template>

<style scoped>
.output-preview__msg {
  margin-bottom: 12px;
}
.conn-pick {
  display: flex;
  gap: 8px;
  width: 100%;
}
.conn-pick .el-input {
  flex: 1;
}
</style>
