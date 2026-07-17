<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

const props = defineProps<{ taskId?: number }>()

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

const runs = ref<RunRow[]>([])
const logs = ref<NodeLog[]>([])
const selectedRunId = ref<number | null>(null)
const loading = ref(false)
const taskStatus = ref('')
const actionLoading = ref(false)
const logDialogVisible = ref(false)
const currentLog = ref<NodeLog | null>(null)
const kettleLogText = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null

function calcDuration(start?: string, end?: string): string {
  if (!start) return '—'
  const s = new Date(start).getTime()
  const e = end ? new Date(end).getTime() : Date.now()
  if (Number.isNaN(s) || Number.isNaN(e)) return '—'
  const sec = Math.max(0, Math.round((e - s) / 1000))
  if (sec < 60) return `${sec}秒`
  const m = Math.floor(sec / 60)
  const r = sec % 60
  return `${m}分${r}秒`
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
    const params = props.taskId ? { taskId: props.taskId } : {}
    runs.value = (await api.get('/governance/gov-tasks/runs', { params })).data || []
    if (runs.value.length) {
      const current = selectedRunId.value && runs.value.some(r => r.id === selectedRunId.value)
        ? selectedRunId.value
        : runs.value[0].id
      await openRun(current!)
    } else {
      selectedRunId.value = null
      logs.value = []
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
  logs.value = (await api.get(`/governance/gov-tasks/runs/${runId}/node-logs`)).data || []
  try {
    await api.get(`/governance/kettle/runs/${runId}/status`)
  } catch {
    /* kettle 可选 */
  }
}

async function rerunTask() {
  if (!props.taskId) return
  actionLoading.value = true
  try {
    // 重跑：清理旧转换后重新执行
    if (selectedRunId.value) {
      try {
        await api.delete(`/governance/kettle/runs/${selectedRunId.value}/cleanup`)
      } catch { /* ignore */ }
    }
    const res = await api.post(`/governance/kettle/tasks/${props.taskId}/execute`)
    ElMessage.success(res.data?.message || '已重新运行')
    selectedRunId.value = null
    await loadRuns()
  } catch {
    ElMessage.error('运行失败')
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
  kettleLogText.value = ''
  if (selectedRunId.value) {
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
  if (taskStatus.value === 'RUNNING') {
    pollTimer = setInterval(() => { void loadRuns() }, 3000)
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

    <el-row :gutter="12">
      <el-col :span="10">
        <el-table
          v-loading="loading"
          :data="runs"
          stripe
          size="small"
          highlight-current-row
          :row-class-name="({ row }: { row: RunRow }) => (row.id === selectedRunId ? 'is-current' : '')"
          @row-click="(row: RunRow) => openRun(row.id)"
        >
          <el-table-column prop="id" label="实例标识" width="80" />
          <el-table-column label="状态" width="88">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="节点" width="72">
            <template #default="{ row }">{{ row.successNodes ?? 0 }}/{{ row.totalNodes ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="行数" width="60">
            <template #default="{ row }">{{ row.lineCount ?? row.rowCount ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="时长" width="80">
            <template #default="{ row }">{{ calcDuration(row.startedAt, row.endedAt) }}</template>
          </el-table-column>
          <el-table-column prop="triggeredBy" label="触发" width="72" show-overflow-tooltip />
          <el-table-column prop="startedAt" label="开始时间" width="140" />
          <el-table-column prop="endedAt" label="结束时间" width="140" />
        </el-table>
      </el-col>
      <el-col :span="14">
        <div style="margin-bottom:8px;font-weight:600">
          节点运行情况 {{ selectedRunId ? `#${selectedRunId}` : '' }}
          <span style="font-weight:400;font-size:12px;color:#909399;margin-left:8px">右键或点击「查看日志」</span>
        </div>
        <el-table
          :data="logs"
          stripe
          size="small"
          max-height="420"
          :row-class-name="({ row }: { row: NodeLog }) => (row.status === 'FAILED' ? 'is-failed' : '')"
          @row-contextmenu="(row: NodeLog, col: unknown, e: Event) => { e.preventDefault(); onNodeContextMenu(row, col, e) }"
        >
          <el-table-column label="实例标识" width="88">
            <template #default>{{ selectedRunId }}</template>
          </el-table-column>
          <el-table-column prop="nodeName" label="节点名称" width="96" />
          <el-table-column prop="nodeType" label="类型" width="100" />
          <el-table-column label="节点状态" width="88">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="inputRows" label="入行" width="56" />
          <el-table-column prop="outputRows" label="出行" width="56" />
          <el-table-column prop="startedAt" label="开始时间" width="140" />
          <el-table-column prop="endedAt" label="结束时间" width="140" />
          <el-table-column label="时长" width="72">
            <template #default="{ row }">{{ calcDuration(row.startedAt, row.endedAt) }}</template>
          </el-table-column>
          <el-table-column label="日志" width="90" fixed="right">
            <template #default="{ row }">
              <el-dropdown trigger="contextmenu" @command="() => viewNodeLog(row)">
                <el-button link type="primary" @click="viewNodeLog(row)">查看日志</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="log">查看日志</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>

    <el-dialog v-model="logDialogVisible" title="节点日志详情" width="720px">
      <template v-if="currentLog">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="节点名称">{{ currentLog.nodeName }}</el-descriptions-item>
          <el-descriptions-item label="节点类型">{{ currentLog.nodeType }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(currentLog.status) }}</el-descriptions-item>
          <el-descriptions-item label="时长">{{ calcDuration(currentLog.startedAt, currentLog.endedAt) }}</el-descriptions-item>
          <el-descriptions-item label="输入行数">{{ currentLog.inputRows }}</el-descriptions-item>
          <el-descriptions-item label="输出行数">{{ currentLog.outputRows }}</el-descriptions-item>
          <el-descriptions-item label="消息" :span="2">{{ currentLog.message || '—' }}</el-descriptions-item>
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
:deep(.el-table .is-current > td) {
  background-color: var(--el-color-primary-light-9) !important;
}
:deep(.el-table .is-failed > td) {
  background-color: var(--el-color-danger-light-9) !important;
}
</style>
