<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'

interface MonitorRow {
  task: { id: number; taskName: string; status: string; lastMessage?: string }
  connectorName?: string
  sourceType?: string
  lastRun?: {
    id: number
    status: string
    startedAt?: string
    endedAt?: string
    summary?: string
    tableCount?: number
    logText?: string
  }
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

const STATUS_FILTER_OPTIONS = [
  { label: '就绪', value: 'READY' },
  { label: '运行中', value: 'RUNNING' },
  { label: '已停止', value: 'STOPPED' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
]

const filter = reactive({ sourceKeyword: '', status: '' })
const items = ref<MonitorRow[]>([])
const runs = ref<Array<Record<string, unknown>>>([])
const omHealthy = ref(false)
const resultsVisible = ref(false)
const runResults = ref<RegistryEntry[]>([])
const runDiff = ref<{
  added?: string[]
  removed?: string[]
  changed?: string[]
  addedCount?: number
  removedCount?: number
  changedCount?: number
} | null>(null)
const activeRunId = ref<number | null>(null)

const omHealthLabel = computed(() => statusLabel(omHealthy.value ? 'UP' : 'DOWN'))

const failureAlert = computed(() => {
  const failedRuns = runs.value.filter(r => String(r.status) === 'FAILED')
  const failedTasks = items.value.filter(i => i.lastRun?.status === 'FAILED')
  const total = failedRuns.length + failedTasks.length
  if (total === 0) return null
  const names = [
    ...failedTasks.map(i => i.task.taskName),
    ...failedRuns.map(r => `运行 #${r.id}`),
  ].slice(0, 5)
  return { total, names }
})

function diffCount(list: string[] | undefined, count: number | undefined) {
  if (typeof count === 'number') return count
  return list?.length || 0
}

function formatSummary(summary?: string) {
  if (!summary) return '—'
  if (summary.startsWith('{')) {
    try {
      const d = JSON.parse(summary) as {
        addedCount?: number
        removedCount?: number
        changedCount?: number
      }
      if (typeof d.addedCount === 'number') {
        return `新增${d.addedCount} / 删除${d.removedCount ?? 0} / 变更${d.changedCount ?? 0}`
      }
    } catch { /* ignore */ }
  }
  return summary
}

async function loadMonitor() {
  const res = await api.get('/governance/platform/metadata/collect/monitor', {
    params: {
      sourceKeyword: filter.sourceKeyword || undefined,
      status: filter.status || undefined,
    },
  })
  items.value = res.data.items || []
  omHealthy.value = !!res.data.omHealthy
}

async function loadRuns() {
  runs.value = (await api.get('/governance/platform/metadata/collect/runs', {
    params: {
      status: filter.status || undefined,
      keyword: filter.sourceKeyword || undefined,
    },
  })).data || []
}

async function onSearch() {
  await Promise.all([loadMonitor(), loadRuns()])
}

async function stopRun(runId: number) {
  const res = await api.post(`/governance/platform/metadata/collect/runs/${runId}/stop`)
  ElMessage.success(`已停止：${statusLabel(res.data.status)}`)
  await onSearch()
}

async function showRunResults(run: Record<string, unknown>) {
  const runId = run.id as number
  activeRunId.value = runId
  runResults.value = (await api.get(`/governance/platform/metadata/collect/runs/${runId}/results`)).data || []
  runDiff.value = null
  const summary = String(run.summary || '')
  if (summary.startsWith('{')) {
    try { runDiff.value = JSON.parse(summary) } catch { /* ignore */ }
  }
  resultsVisible.value = true
}

onMounted(onSearch)
</script>

<template>
  <PageCard title="元数据采集监控">
    <el-alert
      v-if="failureAlert"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      :title="`最近有 ${failureAlert.total} 项采集失败，请及时处理`"
    >
      <template #default>
        <span>{{ failureAlert.names.join('、') }}</span>
      </template>
    </el-alert>

    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="数据源/名称" class="portal-field-lg">
        <el-input v-model="filter.sourceKeyword" clearable placeholder="关键字" />
      </el-form-item>
      <el-form-item label="状态" class="portal-field-sm">
        <el-select v-model="filter.status" clearable placeholder="全部">
          <el-option v-for="o in STATUS_FILTER_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="onSearch">查询</el-button>
      </el-form-item>
    </el-form>

    <el-tag :type="omHealthy ? 'success' : 'info'" size="small" style="margin-bottom: 8px">
      OpenMetadata {{ omHealthLabel }}
    </el-tag>

    <el-table :data="items" stripe size="small">
      <el-table-column prop="task.taskName" label="任务" min-width="140" />
      <el-table-column prop="connectorName" label="适配器" width="140" />
      <el-table-column label="源类型" width="100">
        <template #default="{ row }">{{ $statusLabel(row.sourceType) }}</template>
      </el-table-column>
      <el-table-column label="任务状态" width="100">
        <template #default="{ row }">
          <el-tag :type="$statusTagType(row.task.status)" size="small">{{ $statusLabel(row.task.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近运行" min-width="220">
        <template #default="{ row }">
          <span v-if="row.lastRun">
            <el-tag :type="$statusTagType(row.lastRun.status)" size="small">{{ $statusLabel(row.lastRun.status) }}</el-tag>
            · {{ formatSummary(row.lastRun.summary) }}
          </span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button v-if="row.lastRun" link type="primary" @click="showRunResults(row.lastRun as Record<string, unknown>)">本次元数据</el-button>
          <el-button v-if="row.lastRun?.status === 'RUNNING'" link type="danger" @click="stopRun(row.lastRun.id)">停止</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">运行日志</el-divider>
    <el-table :data="runs" stripe size="small">
      <el-table-column prop="id" label="运行ID" width="80" />
      <el-table-column prop="taskId" label="任务" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tableCount" label="表数" width="70" />
      <el-table-column label="摘要" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ formatSummary(row.summary as string | undefined) }}</template>
      </el-table-column>
      <el-table-column prop="startedAt" label="开始" width="160" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="showRunResults(row)">元数据</el-button>
          <el-button v-if="row.status === 'RUNNING'" link type="danger" @click="stopRun(row.id as number)">停止</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="resultsVisible" :title="`运行 #${activeRunId} 采集元数据`" size="55%">
      <div v-if="runDiff" style="margin-bottom: 12px">
        <el-tag type="success" size="small" style="margin-right: 6px">新增 {{ diffCount(runDiff.added, runDiff.addedCount) }}</el-tag>
        <el-tag type="danger" size="small" style="margin-right: 6px">删除 {{ diffCount(runDiff.removed, runDiff.removedCount) }}</el-tag>
        <el-tag type="warning" size="small">变更 {{ diffCount(runDiff.changed, runDiff.changedCount) }}</el-tag>
      </div>
      <el-table :data="runResults" stripe size="small" max-height="480">
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ $statusLabel(row.entryType) }}</template>
        </el-table-column>
        <el-table-column prop="entryCode" label="编码" width="180" show-overflow-tooltip />
        <el-table-column prop="entryName" label="名称" />
        <el-table-column prop="parentCode" label="父编码" width="140" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" show-overflow-tooltip />
        <el-table-column label="变更" width="90">
          <template #default="{ row }">{{ $statusLabel(row.changeFlag) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </PageCard>
</template>
