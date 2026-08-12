<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { statusLabel } from '@/utils/status-label'

interface DiffInfo {
  added?: string[]
  removed?: string[]
  changed?: string[]
  addedCount?: number
  removedCount?: number
  changedCount?: number
  prevRunId?: number
  truncated?: boolean
}

interface LastRun {
  id: number
  taskId?: number
  taskName?: string
  status: string
  startedAt?: string
  endedAt?: string
  summary?: string
  tableCount?: number
  logText?: string
  triggerType?: string
  dsInstanceId?: number
  dsState?: string
  durationSeconds?: number
  diff?: DiffInfo | null
  connectorName?: string
  sourceType?: string
}

interface MonitorRow {
  task: {
    id: number
    taskName: string
    status: string
    lastMessage?: string
    taskCode?: string
    scheduleType?: string
    publishStatus?: string
  }
  connectorName?: string
  sourceType?: string
  sourceId?: number
  lastRun?: LastRun | null
  execStatus?: string
  scheduleType?: string
  publishStatus?: string
  canStop?: boolean
  canStart?: boolean
}

interface RegistryEntry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
  parentCode?: string
  description?: string
  changeFlag?: string
}

interface DataSource {
  id: number
  sourceName: string
  sourceCode?: string
  sourceType?: string
}

const RUN_STATUS_OPTIONS = [
  { label: '运行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
  { label: '已停止', value: 'STOPPED' },
  { label: '未执行', value: 'IDLE' },
]

const filter = reactive({
  sourceId: undefined as number | undefined,
  sourceKeyword: '',
  taskKeyword: '',
  runStatus: '',
})
const dataSources = ref<DataSource[]>([])
const items = ref<MonitorRow[]>([])
const runs = ref<LastRun[]>([])
const kpi = reactive({ total: 0, running: 0, success: 0, failed: 0, stopped: 0, idle: 0 })
const omHealthy = ref(false)
const dsHealthy = ref(false)
const refreshedAt = ref('')
const loading = ref(false)
const stoppingTaskId = ref<number | null>(null)
const startingTaskId = ref<number | null>(null)
const stoppingRunId = ref<number | null>(null)
const autoRefresh = ref(true)
let timer: ReturnType<typeof setInterval> | null = null

const page = ref(1)
const pageSize = ref(10)

const logVisible = ref(false)
const logDetail = ref<Record<string, unknown> | null>(null)
const logLoading = ref(false)

const metaVisible = ref(false)
const metaRows = ref<RegistryEntry[]>([])
const metaDiff = ref<DiffInfo | null>(null)
const metaRunId = ref<number | null>(null)
const metaRunTitle = ref('')
const metaTypeFilter = ref('')
const metaPage = ref(1)
const metaPageSize = ref(20)

const pagedItems = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return items.value.slice(start, start + pageSize.value)
})

const metaTypeOptions = computed(() => {
  const set = new Set(metaRows.value.map((r) => r.entryType).filter(Boolean))
  return Array.from(set)
})

const filteredMeta = computed(() => {
  if (!metaTypeFilter.value) return metaRows.value
  return metaRows.value.filter((r) => r.entryType === metaTypeFilter.value)
})

const pagedMeta = computed(() => {
  const start = (metaPage.value - 1) * metaPageSize.value
  return filteredMeta.value.slice(start, start + metaPageSize.value)
})

const hasRunning = computed(() => kpi.running > 0 || items.value.some((i) => isTaskRunning(i)))

function diffCount(list: string[] | undefined, count: number | undefined) {
  if (typeof count === 'number') return count
  return list?.length || 0
}

function formatDuration(sec?: number | null) {
  if (sec == null || Number.isNaN(Number(sec))) return '—'
  const s = Math.max(0, Math.floor(Number(sec)))
  if (s < 60) return `${s} 秒`
  const m = Math.floor(s / 60)
  const r = s % 60
  if (m < 60) return `${m} 分 ${r} 秒`
  const h = Math.floor(m / 60)
  return `${h} 时 ${m % 60} 分`
}

function formatTime(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function formatDiff(diff?: DiffInfo | null) {
  if (!diff) return '—'
  const a = diffCount(diff.added, diff.addedCount)
  const r = diffCount(diff.removed, diff.removedCount)
  const c = diffCount(diff.changed, diff.changedCount)
  return `+${a} / -${r} / ~${c}`
}

function formatSummary(summary?: string, diff?: DiffInfo | null) {
  if (diff) return formatDiff(diff)
  if (!summary) return '—'
  if (summary.startsWith('{')) {
    try {
      const d = JSON.parse(summary) as DiffInfo
      return formatDiff(d)
    } catch { /* ignore */ }
  }
  return summary
}

function isTaskRunning(row: MonitorRow) {
  return row.execStatus === 'RUNNING'
    || row.task?.status === 'RUNNING'
    || row.lastRun?.status === 'RUNNING'
}

function isScheduled(row: MonitorRow) {
  return String(row.scheduleType || row.task?.scheduleType || '').toUpperCase() === 'SCHEDULED'
}

function isPublished(row: MonitorRow) {
  return String(row.publishStatus || row.task?.publishStatus || '').toUpperCase() === 'PUBLISHED'
}

function canStopTask(row: MonitorRow) {
  if (row.canStop != null) return !!row.canStop
  return isTaskRunning(row) || (isScheduled(row) && isPublished(row))
}

function canStartTask(row: MonitorRow) {
  if (row.canStart != null) return !!row.canStart
  return isScheduled(row) && !isPublished(row)
}

function stopDisabledTip(row: MonitorRow) {
  if (canStopTask(row)) return ''
  if (isScheduled(row)) return '仅已发布的定时任务或运行中任务可停止'
  return '仅运行中任务可停止'
}

function startDisabledTip(row: MonitorRow) {
  if (!isScheduled(row)) return '仅定时任务可启动调度'
  if (isPublished(row)) return '定时调度已在运行中'
  return ''
}

function execPulse(status?: string) {
  if (status === 'RUNNING') return 'is-run'
  if (status === 'SUCCESS') return 'is-ok'
  if (status === 'FAILED') return 'is-fail'
  if (status === 'STOPPED') return 'is-stop'
  return 'is-idle'
}

async function loadSources() {
  try {
    dataSources.value = (await api.get('/governance/platform/metadata/collect/data-sources')).data || []
  } catch {
    dataSources.value = []
  }
}

async function loadMonitor(silent = false) {
  if (!silent) loading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/collect/monitor', {
      params: {
        sourceId: filter.sourceId || undefined,
        sourceKeyword: filter.sourceKeyword || undefined,
        taskKeyword: filter.taskKeyword || undefined,
        runStatus: filter.runStatus || undefined,
      },
    })
    items.value = res.data.items || []
    omHealthy.value = !!res.data.omHealthy
    dsHealthy.value = !!res.data.dsHealthy
    refreshedAt.value = res.data.refreshedAt || ''
    const k = res.data.kpi || {}
    kpi.total = Number(k.total || items.value.length)
    kpi.running = Number(k.running || 0)
    kpi.success = Number(k.success || 0)
    kpi.failed = Number(k.failed || 0)
    kpi.stopped = Number(k.stopped || 0)
    kpi.idle = Number(k.idle || 0)
  } finally {
    if (!silent) loading.value = false
  }
}

async function loadRuns(silent = false) {
  try {
    runs.value = (await api.get('/governance/platform/metadata/collect/runs', {
      params: {
        status: filter.runStatus && filter.runStatus !== 'IDLE' ? filter.runStatus : undefined,
        keyword: filter.taskKeyword || filter.sourceKeyword || undefined,
      },
    })).data || []
  } catch {
    if (!silent) runs.value = []
  }
}

async function onSearch() {
  page.value = 1
  await Promise.all([loadMonitor(), loadRuns()])
}

function resetFilter() {
  filter.sourceId = undefined
  filter.sourceKeyword = ''
  filter.taskKeyword = ''
  filter.runStatus = ''
  void onSearch()
}

async function stopTask(row: MonitorRow) {
  if (!canStopTask(row)) return
  const name = row.task.taskName
  const tip = isScheduled(row) && isPublished(row)
    ? `确认停止任务「${name}」？将终止本次采集（如有），并下线定时调度，之后不再自动运行。`
    : `确认停止任务「${name}」？停止后将终止本次元数据采集。`
  try {
    await ElMessageBox.confirm(tip, '停止', {
      type: 'warning',
      confirmButtonText: '停止',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  stoppingTaskId.value = row.task.id
  try {
    // 统一走任务级停止：运行中终止采集；已发布定时则下线 DS 调度
    const res = await api.post(`/governance/platform/metadata/collect/tasks/${row.task.id}/stop`)
    ElMessage.success(res.data?.message || `已停止：${statusLabel(res.data?.status || 'STOPPED')}`)
    await onSearch()
  } catch (e: unknown) {
    const err = e as Error & { message?: string }
    ElMessage.error(err.message || '停止失败')
  } finally {
    stoppingTaskId.value = null
  }
}

async function startTask(row: MonitorRow) {
  if (!canStartTask(row)) return
  try {
    await ElMessageBox.confirm(
      `确认启动定时任务「${row.task.taskName}」？将恢复 DolphinScheduler 周期调度。`,
      '启动定时调度',
      { type: 'info', confirmButtonText: '启动', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  startingTaskId.value = row.task.id
  try {
    const res = await api.post(`/governance/platform/metadata/collect/tasks/${row.task.id}/start`)
    ElMessage.success(res.data?.message || '定时调度已启动')
    await onSearch()
  } catch (e: unknown) {
    const err = e as Error & { message?: string }
    ElMessage.error(err.message || '启动失败')
  } finally {
    startingTaskId.value = null
  }
}

async function stopRun(runId: number, status?: string) {
  if (status && status !== 'RUNNING') return
  try {
    await ElMessageBox.confirm('确认停止该次采集运行？停止后将终止元数据采集。', '停止采集', {
      type: 'warning',
      confirmButtonText: '停止',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  stoppingRunId.value = runId
  try {
    const res = await api.post(`/governance/platform/metadata/collect/runs/${runId}/stop`)
    ElMessage.success(`已停止：${statusLabel(res.data.status)}`)
    await onSearch()
  } catch (e: unknown) {
    const err = e as Error & { message?: string }
    ElMessage.error(err.message || '停止失败')
  } finally {
    stoppingRunId.value = null
  }
}

async function openLogDetail(runId: number) {
  logVisible.value = true
  logLoading.value = true
  logDetail.value = null
  try {
    logDetail.value = (await api.get(`/governance/platform/metadata/collect/runs/${runId}`)).data
  } catch {
    ElMessage.error('加载日志详情失败')
    logVisible.value = false
  } finally {
    logLoading.value = false
  }
}

async function openMetadata(run: LastRun | Record<string, unknown>, title?: string) {
  const runId = Number(run.id)
  if (!runId) return
  metaRunId.value = runId
  metaRunTitle.value = title || String((run as LastRun).taskName || `运行 #${runId}`)
  metaTypeFilter.value = ''
  metaPage.value = 1
  metaVisible.value = true
  metaRows.value = (await api.get(`/governance/platform/metadata/collect/runs/${runId}/results`)).data || []
  metaDiff.value = ((run as LastRun).diff as DiffInfo) || null
  if (!metaDiff.value) {
    const summary = String((run as LastRun).summary || '')
    if (summary.startsWith('{')) {
      try { metaDiff.value = JSON.parse(summary) } catch { /* ignore */ }
    }
  }
}

function applyKpiFilter(status: string) {
  filter.runStatus = filter.runStatus === status ? '' : status
  void onSearch()
}

function setupTimer() {
  if (timer) clearInterval(timer)
  const interval = hasRunning.value ? 2000 : 5000
  timer = setInterval(() => {
    if (!autoRefresh.value) return
    void loadMonitor(true)
    if (hasRunning.value) void loadRuns(true)
  }, interval)
}

watch(hasRunning, () => {
  setupTimer()
  if (hasRunning.value && autoRefresh.value) void loadMonitor(true)
})

onMounted(async () => {
  await loadSources()
  await onSearch()
  setupTimer()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div v-loading="loading" class="mm">
    <div class="mm-kpi">
      <button type="button" class="mm-kpi__card tone-all" @click="applyKpiFilter('')">
        <span class="mm-kpi__lab">监控任务</span>
        <b>{{ kpi.total }}</b>
      </button>
      <button type="button" class="mm-kpi__card tone-run" :class="{ 'is-on': filter.runStatus === 'RUNNING' }" @click="applyKpiFilter('RUNNING')">
        <span class="mm-kpi__lab">运行中</span>
        <b>{{ kpi.running }}</b>
        <i v-if="kpi.running" class="mm-kpi__pulse" />
      </button>
      <button type="button" class="mm-kpi__card tone-ok" :class="{ 'is-on': filter.runStatus === 'SUCCESS' }" @click="applyKpiFilter('SUCCESS')">
        <span class="mm-kpi__lab">最近成功</span>
        <b>{{ kpi.success }}</b>
      </button>
      <button type="button" class="mm-kpi__card tone-fail" :class="{ 'is-on': filter.runStatus === 'FAILED' }" @click="applyKpiFilter('FAILED')">
        <span class="mm-kpi__lab">最近失败</span>
        <b>{{ kpi.failed }}</b>
      </button>
      <button type="button" class="mm-kpi__card tone-stop" :class="{ 'is-on': filter.runStatus === 'STOPPED' }" @click="applyKpiFilter('STOPPED')">
        <span class="mm-kpi__lab">已停止</span>
        <b>{{ kpi.stopped }}</b>
      </button>
    </div>

    <PageCard title="元数据采集监控">
      <div class="mm-toolbar">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="数据源" class="portal-field-xl">
            <el-select v-model="filter.sourceId" clearable filterable placeholder="全部数据源">
              <el-option
                v-for="ds in dataSources"
                :key="ds.id"
                :label="`${ds.sourceName}${ds.sourceType ? ' · ' + statusLabel(ds.sourceType) : ''}`"
                :value="ds.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="任务名称" class="portal-field-lg">
            <el-input v-model="filter.taskKeyword" clearable placeholder="任务名称关键字" @keyup.enter="onSearch" />
          </el-form-item>
          <el-form-item label="执行状态" class="portal-field-md">
            <el-select v-model="filter.runStatus" clearable placeholder="全部">
              <el-option v-for="o in RUN_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="resetFilter">重置</el-button>
          </el-form-item>
        </el-form>
        <div class="mm-toolbar__right">
          <el-tag :type="omHealthy ? 'success' : 'info'" size="small">
            OpenMetadata {{ $statusLabel(omHealthy ? 'UP' : 'DOWN') }}
          </el-tag>
          <el-tag :type="dsHealthy ? 'success' : 'info'" size="small">
            DolphinScheduler {{ $statusLabel(dsHealthy ? 'UP' : 'DOWN') }}
          </el-tag>
          <el-switch v-model="autoRefresh" inline-prompt active-text="实时" inactive-text="暂停" />
          <el-button size="small" @click="onSearch">刷新</el-button>
          <span v-if="refreshedAt" class="mm-refreshed">更新于 {{ formatTime(refreshedAt) }}</span>
        </div>
      </div>

      <div class="mm-section-title">任务最近执行（每任务一条）</div>
      <el-table :data="pagedItems" stripe size="small" empty-text="暂无采集任务">
        <el-table-column label="执行" width="56" align="center">
          <template #default="{ row }">
            <span class="mm-dot" :class="execPulse(row.execStatus)" :title="$statusLabel(row.execStatus)" />
          </template>
        </el-table-column>
        <el-table-column label="任务名称" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="mm-task-name">{{ row.task.taskName }}</div>
            <div class="mm-task-code">{{ row.task.taskCode || `#${row.task.id}` }}</div>
          </template>
        </el-table-column>
        <el-table-column label="数据源" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.connectorName || '—' }}</template>
        </el-table-column>
        <el-table-column label="源类型" width="100">
          <template #default="{ row }">{{ $statusLabel(row.sourceType) }}</template>
        </el-table-column>
        <el-table-column label="执行状态" width="100">
          <template #default="{ row }">
            <el-tag :type="$statusTagType(row.execStatus === 'IDLE' ? 'DRAFT' : row.execStatus)" size="small">
              {{ row.execStatus === 'IDLE' ? '未执行' : $statusLabel(row.execStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="比较分析" width="130">
          <template #default="{ row }">
            <span class="mm-diff">{{ formatSummary(row.lastRun?.summary, row.lastRun?.diff) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="表数" width="70">
          <template #default="{ row }">{{ row.lastRun?.tableCount ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ formatTime(row.lastRun?.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="执行耗时" width="110">
          <template #default="{ row }">
            <template v-if="row.execStatus === 'RUNNING'">
              <el-progress :percentage="70" :indeterminate="true" :show-text="false" style="width:72px;display:inline-flex" />
              <span class="mm-dur">{{ formatDuration(row.lastRun?.durationSeconds) }}</span>
            </template>
            <span v-else>{{ formatDuration(row.lastRun?.durationSeconds) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="调度" width="100">
          <template #default="{ row }">
            <template v-if="isScheduled(row)">
              <el-tag :type="isPublished(row) ? 'success' : 'info'" size="small">
                {{ isPublished(row) ? '已发布' : '已停止' }}
              </el-tag>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.lastRun"
              link
              type="primary"
              @click="openLogDetail(row.lastRun.id)"
            >日志详情</el-button>
            <el-button
              v-if="row.lastRun && ['SUCCESS', 'FAILED', 'STOPPED'].includes(row.lastRun.status)"
              link
              type="primary"
              @click="openMetadata(row.lastRun, row.task.taskName)"
            >元数据</el-button>
            <el-button
              v-if="isScheduled(row)"
              link
              type="success"
              :title="startDisabledTip(row)"
              :disabled="!canStartTask(row) || (startingTaskId != null && startingTaskId !== row.task.id)"
              :loading="startingTaskId === row.task.id"
              @click="startTask(row)"
            >启动</el-button>
            <el-button
              link
              type="danger"
              :title="stopDisabledTip(row)"
              :disabled="!canStopTask(row) || (stoppingTaskId != null && stoppingTaskId !== row.task.id)"
              :loading="stoppingTaskId === row.task.id"
              @click="stopTask(row)"
            >停止</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-if="items.length"
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="items.length"
      />
    </PageCard>

    <PageCard title="采集运行日志" style="margin-top:12px">
      <el-table :data="runs.slice(0, 50)" stripe size="small" empty-text="暂无运行日志">
        <el-table-column prop="id" label="运行ID" width="80" />
        <el-table-column label="任务" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taskName || `任务#${row.taskId}` }}</template>
        </el-table-column>
        <el-table-column label="数据源" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.connectorName || '—' }}</template>
        </el-table-column>
        <el-table-column label="触发" width="80">
          <template #default="{ row }">{{ row.triggerType === 'SCHEDULED' ? '定时' : '手动' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="比较分析" width="130">
          <template #default="{ row }">{{ formatSummary(row.summary, row.diff) }}</template>
        </el-table-column>
        <el-table-column prop="tableCount" label="表数" width="70" />
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="执行耗时" width="100">
          <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openLogDetail(row.id)">日志详情</el-button>
            <el-button link type="primary" @click="openMetadata(row)">元数据</el-button>
            <el-button
              link
              type="danger"
              :title="row.status === 'RUNNING' ? '' : '仅运行中任务可停止'"
              :disabled="row.status !== 'RUNNING'"
              :loading="stoppingRunId === row.id"
              @click="stopRun(row.id, row.status)"
            >停止</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-drawer v-model="logVisible" title="采集任务日志详情" size="560px">
      <div v-loading="logLoading">
        <template v-if="logDetail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="运行ID">{{ logDetail.id }}</el-descriptions-item>
            <el-descriptions-item label="任务">{{ logDetail.taskName || `任务#${logDetail.taskId}` }}</el-descriptions-item>
            <el-descriptions-item label="数据源">{{ logDetail.connectorName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="源类型">{{ $statusLabel(logDetail.sourceType) }}</el-descriptions-item>
            <el-descriptions-item label="执行状态">
              <el-tag :type="$statusTagType(logDetail.status)" size="small">{{ $statusLabel(logDetail.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="logDetail.triggerType" label="触发方式">
              {{ logDetail.triggerType === 'SCHEDULED' ? '定时调度' : '手动' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="logDetail.dsInstanceId" label="DS 实例">
              #{{ logDetail.dsInstanceId }}
              <span v-if="logDetail.dsState">（{{ logDetail.dsState }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatTime(logDetail.startedAt as string) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ formatTime(logDetail.endedAt as string) }}</el-descriptions-item>
            <el-descriptions-item label="执行耗时">{{ formatDuration(logDetail.durationSeconds as number) }}</el-descriptions-item>
            <el-descriptions-item label="采集表数">{{ logDetail.tableCount ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="元数据条数">{{ logDetail.metadataCount ?? '—' }}</el-descriptions-item>
          </el-descriptions>

          <div class="mm-block-title">任务比较分析结果</div>
          <div v-if="logDetail.diff" class="mm-diff-cards">
            <div class="mm-diff-card is-add">
              <span>新增</span>
              <b>{{ diffCount((logDetail.diff as DiffInfo).added, (logDetail.diff as DiffInfo).addedCount) }}</b>
            </div>
            <div class="mm-diff-card is-del">
              <span>删除</span>
              <b>{{ diffCount((logDetail.diff as DiffInfo).removed, (logDetail.diff as DiffInfo).removedCount) }}</b>
            </div>
            <div class="mm-diff-card is-chg">
              <span>变更</span>
              <b>{{ diffCount((logDetail.diff as DiffInfo).changed, (logDetail.diff as DiffInfo).changedCount) }}</b>
            </div>
          </div>
          <el-empty v-else description="暂无比较分析结果" :image-size="48" />

          <div class="mm-block-title">执行日志</div>
          <pre class="mm-log">{{ logDetail.logText || '暂无日志' }}</pre>

          <div class="mm-drawer-actions">
            <el-button
              type="primary"
              @click="openMetadata(logDetail as LastRun, String(logDetail.taskName || ''))"
            >查看本次元数据</el-button>
            <el-button
              type="danger"
              :disabled="logDetail.status !== 'RUNNING'"
              :title="logDetail.status === 'RUNNING' ? '' : '仅运行中任务可停止'"
              @click="stopRun(Number(logDetail.id), String(logDetail.status))"
            >停止采集</el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-drawer v-model="metaVisible" :title="`采集元数据 · ${metaRunTitle}`" size="64%">
      <div v-if="metaDiff" class="mm-diff-cards" style="margin-bottom:12px">
        <div class="mm-diff-card is-add"><span>新增</span><b>{{ diffCount(metaDiff.added, metaDiff.addedCount) }}</b></div>
        <div class="mm-diff-card is-del"><span>删除</span><b>{{ diffCount(metaDiff.removed, metaDiff.removedCount) }}</b></div>
        <div class="mm-diff-card is-chg"><span>变更</span><b>{{ diffCount(metaDiff.changed, metaDiff.changedCount) }}</b></div>
      </div>
      <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
        <el-form-item label="类型" class="portal-field-sm">
          <el-select v-model="metaTypeFilter" clearable placeholder="全部">
            <el-option v-for="t in metaTypeOptions" :key="t" :label="$statusLabel(t)" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-text type="info">共 {{ filteredMeta.length }} 条（运行 #{{ metaRunId }}）</el-text>
        </el-form-item>
      </el-form>
      <el-table :data="pagedMeta" stripe size="small" max-height="520" empty-text="本次采集暂无元数据">
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ $statusLabel(row.entryType) }}</template>
        </el-table-column>
        <el-table-column prop="entryCode" label="编码" min-width="160" show-overflow-tooltip />
        <el-table-column prop="entryName" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="parentCode" label="父编码" width="140" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="120" show-overflow-tooltip />
        <el-table-column label="变更" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.changeFlag" size="small" :type="$statusTagType(row.changeFlag)">{{ $statusLabel(row.changeFlag) }}</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-if="filteredMeta.length"
        v-model:page="metaPage"
        v-model:page-size="metaPageSize"
        :total="filteredMeta.length"
      />
    </el-drawer>
  </div>
</template>

<style scoped>
.mm { display: flex; flex-direction: column; gap: 0; }
.mm-kpi {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}
.mm-kpi__card {
  appearance: none;
  border: 1px solid #e8edf5;
  background: #fff;
  border-radius: 10px;
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(15, 40, 80, .04);
}
.mm-kpi__card.is-on { outline: 2px solid currentColor; }
.mm-kpi__lab { display: block; font-size: 12px; color: #606266; }
.mm-kpi__card b { font-size: 26px; font-weight: 700; line-height: 1.2; }
.mm-kpi__card.tone-all b { color: #303133; }
.mm-kpi__card.tone-run b { color: #1677ff; }
.mm-kpi__card.tone-ok b { color: #2e7d32; }
.mm-kpi__card.tone-fail b { color: #cf1322; }
.mm-kpi__card.tone-stop b { color: #ef6c00; }
.mm-kpi__pulse {
  position: absolute; right: 12px; top: 14px;
  width: 10px; height: 10px; border-radius: 50%;
  background: #1677ff;
  box-shadow: 0 0 0 0 rgba(22, 119, 255, .55);
  animation: mm-pulse 1.4s infinite;
}
@keyframes mm-pulse {
  0% { box-shadow: 0 0 0 0 rgba(22, 119, 255, .45); }
  70% { box-shadow: 0 0 0 10px rgba(22, 119, 255, 0); }
  100% { box-shadow: 0 0 0 0 rgba(22, 119, 255, 0); }
}
.mm-toolbar {
  display: flex; flex-wrap: wrap; justify-content: space-between; gap: 8px; align-items: flex-start;
  margin-bottom: 8px;
}
.mm-toolbar__right {
  display: flex; align-items: center; gap: 10px; flex-shrink: 0; padding-top: 4px;
}
.mm-refreshed { font-size: 12px; color: #909399; }
.mm-section-title {
  font-size: 13px; font-weight: 600; color: #303133;
  margin: 4px 0 10px; padding-left: 8px; border-left: 3px solid #1677ff;
}
.mm-dot {
  display: inline-block; width: 10px; height: 10px; border-radius: 50%;
  background: #c0c4cc;
}
.mm-dot.is-run { background: #1677ff; box-shadow: 0 0 0 3px rgba(22,119,255,.2); }
.mm-dot.is-ok { background: #52c41a; }
.mm-dot.is-fail { background: #ff4d4f; }
.mm-dot.is-stop { background: #fa8c16; }
.mm-dot.is-idle { background: #d9d9d9; }
.mm-task-name { font-weight: 600; color: #303133; }
.mm-task-code { font-size: 12px; color: #909399; }
.mm-diff { font-variant-numeric: tabular-nums; color: #606266; }
.mm-dur { margin-left: 6px; font-size: 12px; color: #1677ff; }
.mm-block-title {
  margin: 16px 0 8px; font-size: 13px; font-weight: 600;
  padding-left: 8px; border-left: 3px solid #1677ff;
}
.mm-diff-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.mm-diff-card {
  border-radius: 8px; padding: 10px 12px; display: flex; justify-content: space-between; align-items: center;
  font-size: 12px;
}
.mm-diff-card b { font-size: 20px; }
.mm-diff-card.is-add { background: #f6ffed; color: #389e0d; }
.mm-diff-card.is-del { background: #fff2f0; color: #cf1322; }
.mm-diff-card.is-chg { background: #fff7e6; color: #d46b08; }
.mm-log {
  margin: 0; padding: 12px; background: #0f172a; color: #e2e8f0;
  border-radius: 8px; font-size: 12px; line-height: 1.55;
  max-height: 280px; overflow: auto; white-space: pre-wrap; word-break: break-all;
}
.mm-drawer-actions { margin-top: 16px; display: flex; gap: 8px; }
@media (max-width: 1100px) {
  .mm-kpi { grid-template-columns: repeat(2, 1fr); }
}
</style>
