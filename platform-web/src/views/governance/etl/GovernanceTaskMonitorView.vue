<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

const props = withDefaults(defineProps<{
  taskId?: number
  taskDomain?: 'GOVERNANCE' | 'FUSION'
}>(), {
  taskDomain: 'GOVERNANCE',
})

const route = useRoute()
const router = useRouter()

interface RunRow {
  id: number
  taskId: number
  status: string
  startedAt?: string
  endedAt?: string
  totalNodes?: number
  successNodes?: number
  failedNodes?: number
  rowCount?: number
  lineCount?: number
  message?: string
  triggeredBy?: string
  transName?: string
}

interface NodeLog {
  id: number
  nodeId: string
  nodeType: string
  nodeName?: string
  status: string
  inputRows: number
  outputRows: number
  message?: string
  detailJson?: string
  startedAt?: string
  endedAt?: string
}

interface ProcessInfo {
  status?: string
  statusDesc?: string
  runStatus?: string
  runMessage?: string
  linesInput?: number
  linesOutput?: number
  linesRejected?: number
  errors?: number
  stepCount?: number
  totalNodes?: number
  transName?: string
  carteId?: string
  log?: string
}

const runs = ref<RunRow[]>([])
const logs = ref<NodeLog[]>([])
const selectedRunId = ref<number | null>(null)
const loading = ref(false)
const taskStatus = ref('')
const actionLoading = ref(false)
const logDialogVisible = ref(false)
const currentLog = ref<NodeLog | null>(null)
const kettleLogText = ref('')
const processInfo = ref<ProcessInfo>({})
let pollTimer: ReturnType<typeof setInterval> | null = null

const selectedRun = computed(() => runs.value.find(r => r.id === selectedRunId.value) || null)

const processSummary = computed(() => {
  const p = processInfo.value
  const run = selectedRun.value
  const status = p.runStatus || p.status || run?.status || '—'
  const inRows = p.linesInput ?? run?.rowCount ?? 0
  const outRows = p.linesOutput ?? run?.lineCount ?? 0
  const steps = `${p.stepCount ?? run?.successNodes ?? 0}/${p.totalNodes ?? run?.totalNodes ?? 0}`
  return { status, inRows, outRows, steps, rejected: p.linesRejected ?? 0, errors: p.errors ?? 0 }
})

function displayMessage(msg?: string): string {
  if (!msg) return '—'
  // 隐藏内部 carteId= 前缀，只展示过程摘要
  const parts = msg.split('|')
  const body = parts.length > 1 ? parts.slice(1).join('|') : msg
  const cleaned = body.startsWith('carteId=')
    ? body.replace(/^carteId=[^|]*/, '').replace(/^\|/, '') || '—'
    : body
  return formatProcessZh(cleaned)
}

/** Carte 过程摘要英文化 → 中文（含耗时/吞吐单位） */
function formatProcessZh(raw?: string): string {
  if (!raw || raw === '—') return '—'
  let s = String(raw)
  const statusMap: Array<[RegExp, string]> = [
    [/Finished\s*\(with errors\)/gi, '完成（有错误）'],
    [/Finished/gi, '已完成'],
    [/Running/gi, '运行中'],
    [/Stopped/gi, '已停止'],
    [/Waiting/gi, '等待中'],
    [/Initializing/gi, '初始化中'],
    [/Preparing executing transformation/gi, '准备执行转换'],
    [/Halting/gi, '正在中止'],
    [/Idle/gi, '空闲'],
    [/Disposed/gi, '已释放'],
    [/Error/gi, '错误'],
  ]
  for (const [re, zh] of statusMap) {
    s = s.replace(re, zh)
  }
  // · 0.4s → · 0.4秒；· 529 r/s → · 529 行/秒
  s = s.replace(/·\s*([\d.]+)\s*s\b/gi, '· $1秒')
  s = s.replace(/·\s*([\d.,]+)\s*r\/s\b/gi, '· $1 行/秒')
  s = s.replace(/\b([\d.,]+)\s*r\/s\b/gi, '$1 行/秒')
  return s.trim() || '—'
}

function formatSeconds(sec: number): string {
  const s = Math.max(0, Math.round(sec))
  if (s < 60) return `${s}秒`
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}分${r}秒`
}

function calcDuration(start?: string, end?: string, frozenStatus?: string): string {
  if (!start) return '—'
  const s = new Date(start).getTime()
  if (Number.isNaN(s)) return '—'
  let e: number
  if (end) {
    e = new Date(end).getTime()
  } else if (frozenStatus && isTerminal(frozenStatus)) {
    return '—'
  } else {
    e = Date.now()
  }
  if (Number.isNaN(e)) return '—'
  return formatSeconds((e - s) / 1000)
}

const TERMINAL = ['SUCCESS', 'FAILED', 'STOPPED', 'FINISHED']

function isTerminal(status?: string) {
  return !!status && TERMINAL.includes(status)
}

async function loadTaskStatus() {
  if (!props.taskId) return
  try {
    const res = await api.get(`/governance/gov-tasks/${props.taskId}`)
    taskStatus.value = res.data?.status || ''
  } catch {
    /* ignore */
  }
}

async function loadRuns() {
  loading.value = true
  try {
    const params: Record<string, string | number> = { taskDomain: props.taskDomain }
    if (props.taskId) params.taskId = props.taskId
    runs.value = (await api.get('/governance/gov-tasks/runs', { params })).data || []
    if (runs.value.length) {
      const current = selectedRunId.value && runs.value.some(r => r.id === selectedRunId.value)
        ? selectedRunId.value
        : runs.value[0].id
      await openRun(current!)
    } else {
      selectedRunId.value = null
      logs.value = []
      processInfo.value = {}
      kettleLogText.value = ''
    }
    await loadTaskStatus()
  } catch {
    ElMessage.error('加载运行记录失败')
  } finally {
    loading.value = false
  }
}

async function openRun(runId: number) {
  selectedRunId.value = runId
  const existing = runs.value.find(r => r.id === runId)
  // 已成功/失败的实例：只读库内节点日志，不再打会改写 endedAt 的状态接口
  if (existing && isTerminal(existing.status)) {
    processInfo.value = {
      runStatus: existing.status,
      runMessage: existing.message,
      transName: existing.transName,
      linesInput: existing.rowCount,
      linesOutput: existing.lineCount,
      stepCount: existing.successNodes,
      totalNodes: existing.totalNodes,
    }
    logs.value = (await api.get(`/governance/gov-tasks/runs/${runId}/node-logs`)).data || []
    try {
      const res = await api.get(`/governance/kettle/runs/${runId}/log`)
      kettleLogText.value = String(res.data?.log || '')
    } catch {
      kettleLogText.value = ''
    }
    clearPoll()
    return
  }
  try {
    const statusRes = await api.get(`/governance/kettle/runs/${runId}/status`)
    processInfo.value = (statusRes.data || {}) as ProcessInfo
    if (statusRes.data?.log) {
      kettleLogText.value = String(statusRes.data.log)
    }
    const idx = runs.value.findIndex(r => r.id === runId)
    if (idx >= 0) {
      const row = { ...runs.value[idx] }
      if (statusRes.data?.runStatus) row.status = String(statusRes.data.runStatus)
      if (statusRes.data?.runMessage) row.message = String(statusRes.data.runMessage)
      if (statusRes.data?.linesInput != null) row.rowCount = Number(statusRes.data.linesInput)
      if (statusRes.data?.linesOutput != null) row.lineCount = Number(statusRes.data.linesOutput)
      if (statusRes.data?.stepCount != null) row.successNodes = Number(statusRes.data.stepCount)
      if (statusRes.data?.endedAt) row.endedAt = String(statusRes.data.endedAt)
      runs.value[idx] = row
    }
  } catch {
    processInfo.value = {}
  }
  logs.value = (await api.get(`/governance/gov-tasks/runs/${runId}/node-logs`)).data || []
  if (!kettleLogText.value) {
    try {
      const res = await api.get(`/governance/kettle/runs/${runId}/log`)
      kettleLogText.value = String(res.data?.log || '')
    } catch {
      kettleLogText.value = ''
    }
  }
  setupPoll()
}

async function rerunTask() {
  if (!props.taskId) return
  actionLoading.value = true
  try {
    if (selectedRunId.value) {
      try {
        await api.delete(`/governance/kettle/runs/${selectedRunId.value}/cleanup`)
      } catch { /* ignore */ }
    }
    const res = await api.post(`/governance/kettle/tasks/${props.taskId}/execute`)
    if (res.data?.status === 'FAILED') {
      ElMessage.error(res.data?.message || '运行失败')
    } else {
      ElMessage.success(res.data?.message || '已重新运行')
    }
    selectedRunId.value = null
    await loadRuns()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '运行失败')
  } finally {
    actionLoading.value = false
  }
}

async function stopTask() {
  if (!props.taskId) return
  await ElMessageBox.confirm('确认结束当前运行实例？', '结束任务', { type: 'warning' })
  actionLoading.value = true
  try {
    await api.post(`/governance/kettle/tasks/${props.taskId}/stop`)
    ElMessage.success('已结束任务')
    await loadRuns()
  } catch {
    ElMessage.error('停止失败')
  } finally {
    actionLoading.value = false
  }
}

async function viewNodeLog(row: NodeLog) {
  currentLog.value = row
  if (selectedRunId.value && !kettleLogText.value) {
    try {
      const res = await api.get(`/governance/kettle/runs/${selectedRunId.value}/log`)
      kettleLogText.value = String(res.data?.log || '')
    } catch {
      kettleLogText.value = ''
    }
  }
  logDialogVisible.value = true
}

function onNodeContextMenu(row: NodeLog, _col: unknown, _e: Event) {
  viewNodeLog(row)
}

function backToList() {
  const q: Record<string, any> = { ...route.query, tab: 'etl' }
  delete q.etlView
  delete q.taskId
  router.replace({ query: q })
}

function setupPoll() {
  clearPoll()
  const runStatus = processInfo.value.runStatus || selectedRun.value?.status
  if (isTerminal(runStatus)) {
    return
  }
  const runRunning = runStatus === 'RUNNING'
  const taskRunning = taskStatus.value === 'RUNNING'
  if (runRunning || taskRunning) {
    pollTimer = setInterval(() => {
      if (selectedRunId.value) {
        void openRun(selectedRunId.value)
      } else {
        void loadRuns()
      }
    }, 3000)
  }
}

function clearPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const formattedDetail = computed(() => {
  if (!currentLog.value?.detailJson) return ''
  try {
    return JSON.stringify(JSON.parse(currentLog.value.detailJson), null, 2)
  } catch {
    return currentLog.value.detailJson
  }
})

watch(() => props.taskId, () => {
  selectedRunId.value = null
  void loadRuns()
})

watch(taskStatus, setupPoll)

onMounted(() => {
  void loadRuns()
})

onUnmounted(clearPoll)
</script>

<template>
  <PageCard :title="taskId ? `ETL监控 · 任务 ${taskId}` : 'ETL监控'">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="monitor-tip"
      title="不同治理任务可以同时运行；同一任务运行中不可再次启动。写入 DWD/DWS/ADS 时，若目标表不存在，系统会按 ODS 源表结构自动建表。"
    />

    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item class="portal-form-actions">
        <el-button @click="backToList">返回列表</el-button>
        <el-button @click="loadRuns">刷新</el-button>
        <el-button
          v-if="taskId && taskStatus !== 'RUNNING'"
          type="success"
          :loading="actionLoading"
          @click="rerunTask"
        >重跑任务</el-button>
        <el-button
          v-if="taskId && taskStatus === 'RUNNING'"
          type="warning"
          :loading="actionLoading"
          @click="stopTask"
        >结束任务</el-button>
      </el-form-item>
    </el-form>

    <!-- 过程摘要 -->
    <div v-if="selectedRunId" class="process-panel">
      <div class="process-title">执行过程 · 实例 #{{ selectedRunId }}</div>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="运行状态">
          <el-tag :type="statusTagType(processSummary.status)" size="small">
            {{ statusLabel(processSummary.status) }}
          </el-tag>
          <span v-if="processInfo.statusDesc" class="muted">（{{ formatProcessZh(processInfo.statusDesc) }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="步骤数">{{ processSummary.steps }}</el-descriptions-item>
        <el-descriptions-item label="输入行">{{ processSummary.inRows }}</el-descriptions-item>
        <el-descriptions-item label="输出行">{{ processSummary.outRows }}</el-descriptions-item>
        <el-descriptions-item label="拒绝/错误">
          {{ processSummary.rejected }} / {{ processSummary.errors }}
        </el-descriptions-item>
        <el-descriptions-item label="转换名" :span="2">
          {{ processInfo.transName || selectedRun?.transName || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="时长">
          {{ calcDuration(selectedRun?.startedAt, selectedRun?.endedAt, processSummary.status) }}
        </el-descriptions-item>
        <el-descriptions-item label="过程说明" :span="4">
          {{ displayMessage(processInfo.runMessage || selectedRun?.message) }}
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <el-row :gutter="12" class="monitor-row">
      <el-col :span="15" class="monitor-col">
        <div class="section-title">运行实例</div>
        <el-table
          v-loading="loading"
          class="monitor-table"
          :data="runs"
          stripe
          size="small"
          table-layout="fixed"
          highlight-current-row
          :row-class-name="({ row }: { row: RunRow }) => (row.id === selectedRunId ? 'is-current' : '')"
          @row-click="(row: RunRow) => openRun(row.id)"
        >
          <el-table-column prop="id" label="实例" width="64" />
          <el-table-column label="状态" width="76">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="节点" width="56">
            <template #default="{ row }">{{ row.successNodes ?? 0 }}/{{ row.totalNodes ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="行数" width="64">
            <template #default="{ row }">{{ row.lineCount ?? row.rowCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="时长" width="64">
            <template #default="{ row }">{{ calcDuration(row.startedAt, row.endedAt, row.status) }}</template>
          </el-table-column>
          <el-table-column label="过程信息" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ displayMessage(row.message) }}</template>
          </el-table-column>
          <el-table-column prop="triggeredBy" label="触发" width="64" show-overflow-tooltip />
          <el-table-column prop="startedAt" label="开始时间" width="148" />
        </el-table>
      </el-col>
      <el-col :span="9" class="monitor-col">
        <div class="section-title">
          节点运行情况 {{ selectedRunId ? `#${selectedRunId}` : '' }}
        </div>
        <el-table
          class="monitor-table"
          :data="logs"
          stripe
          size="small"
          table-layout="fixed"
          max-height="320"
          empty-text="暂无步骤过程（运行中将自动从 Carte 同步）"
          :row-class-name="({ row }: { row: NodeLog }) => (row.status === 'FAILED' ? 'is-failed' : '')"
          @row-contextmenu="(row: NodeLog, col: unknown, e: Event) => { e.preventDefault(); onNodeContextMenu(row, col, e) }"
        >
          <el-table-column prop="nodeName" label="节点" min-width="90" show-overflow-tooltip />
          <el-table-column label="状态" width="72">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="inputRows" label="入行" width="56" />
          <el-table-column prop="outputRows" label="出行" width="56" />
          <el-table-column label="过程" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ formatProcessZh(row.message) }}</template>
          </el-table-column>
          <el-table-column label="日志" width="52" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="viewNodeLog(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="section-title kettle-log-title">Kettle 执行日志</div>
        <el-input
          type="textarea"
          :rows="10"
          :model-value="kettleLogText || '暂无日志'"
          readonly
          class="kettle-log"
        />
      </el-col>
    </el-row>

    <el-dialog v-model="logDialogVisible" title="节点日志详情" width="720px">
      <template v-if="currentLog">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="节点名称">{{ currentLog.nodeName }}</el-descriptions-item>
          <el-descriptions-item label="节点类型">{{ statusLabel(currentLog.nodeType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(currentLog.status) }}</el-descriptions-item>
          <el-descriptions-item label="输入行数">{{ currentLog.inputRows }}</el-descriptions-item>
          <el-descriptions-item label="输出行数">{{ currentLog.outputRows }}</el-descriptions-item>
          <el-descriptions-item label="过程" :span="2">{{ formatProcessZh(currentLog.message) }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="formattedDetail" style="margin-top:12px">
          <div style="font-weight:600;margin-bottom:6px">详情 JSON</div>
          <el-input type="textarea" :rows="6" :model-value="formattedDetail" readonly />
        </div>
        <div v-if="kettleLogText" style="margin-top:12px">
          <div style="font-weight:600;margin-bottom:6px">Kettle 执行日志</div>
          <el-input type="textarea" :rows="10" :model-value="kettleLogText" readonly />
        </div>
      </template>
      <template #footer>
        <el-button @click="logDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.monitor-tip {
  margin-bottom: 12px;
}
.process-panel {
  margin-bottom: 12px;
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.process-title,
.section-title {
  margin-bottom: 8px;
  font-weight: 600;
}
.monitor-row {
  min-width: 0;
}
.monitor-col {
  min-width: 0;
  overflow-x: hidden;
}
.monitor-table {
  width: 100%;
}
.monitor-table :deep(.el-table__inner-wrapper),
.monitor-table :deep(.el-table__header-wrapper),
.monitor-table :deep(.el-table__body-wrapper) {
  width: 100% !important;
}
.monitor-table :deep(.el-scrollbar__wrap) {
  overflow-x: hidden !important;
}
.kettle-log-title {
  margin-top: 12px;
}
.muted {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.kettle-log :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
:deep(.el-table .is-current > td) {
  background-color: var(--el-color-primary-light-9) !important;
}
:deep(.el-table .is-failed > td) {
  background-color: var(--el-color-danger-light-9) !important;
}
</style>
