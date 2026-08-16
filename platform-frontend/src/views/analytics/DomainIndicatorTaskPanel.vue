<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshRight, Search, VideoPlay, SwitchButton, CircleClose } from '@element-plus/icons-vue'
import api from '@/api/http'
import { statusLabel } from '@/utils/status-label'

const props = defineProps<{ domain: string }>()

interface TaskRow {
  id: string
  groupId: string
  taskName: string
  execCycle: string
  scheduleStatus: string
  execStatus: string
  calcResult: string
  publishStatus: string
  publishedAt?: string
  lastRunAt?: string
  lastRunMessage?: string
  lastLog?: string
}

interface IndicatorRow {
  id: string
  indicatorName: string
  fieldName?: string
  resultField?: string
  fieldType?: string
  queryNo?: string
  indicatorFlag?: string
}

const loading = ref(false)
const rows = ref<TaskRow[]>([])
const selected = ref<TaskRow[]>([])
const query = reactive({
  taskName: '',
  scheduleStatus: '',
  execStatus: '',
  calcResult: '',
})

const logDialog = ref(false)
const logTitle = ref('')
const logText = ref('')
const logRuns = ref<Array<{ id: number; triggerType: string; execStatus: string; calcResult: string; message?: string; startedAt?: string }>>([])

const indDialog = ref(false)
const indRows = ref<IndicatorRow[]>([])
const indLoading = ref(false)

function cycleLabel(code: string) {
  return statusLabel(code) || code || '—'
}

async function load() {
  loading.value = true
  try {
    const res = await api.get(`/analytics/domain/${props.domain}/indicator-tasks`, {
      params: {
        taskName: query.taskName || undefined,
        scheduleStatus: query.scheduleStatus || undefined,
        execStatus: query.execStatus || undefined,
        calcResult: query.calcResult || undefined,
      },
    })
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.taskName = ''
  query.scheduleStatus = ''
  query.execStatus = ''
  query.calcResult = ''
  load()
}

function onSelectionChange(val: TaskRow[]) {
  selected.value = val
}

async function batchAction(action: 'EXECUTE' | 'START' | 'STOP') {
  if (!selected.value.length) {
    ElMessage.warning('请先勾选任务')
    return
  }
  const label = action === 'EXECUTE' ? '执行' : action === 'START' ? '启动' : '停止'
  await ElMessageBox.confirm(`确认对选中的 ${selected.value.length} 个任务执行「${label}」？`, '确认', { type: 'warning' })
  try {
    const res = await api.post('/analytics/domain/indicator-tasks/batch', {
      action,
      ids: selected.value.map((r) => r.id),
    })
    const ok = res.data?.ok ?? 0
    const fail = res.data?.fail ?? 0
    if (fail > 0) {
      ElMessage.warning(`${label}完成：成功 ${ok}，失败 ${fail}`)
    } else {
      ElMessage.success(`${label}成功（${ok}）`)
    }
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || `${label}失败`)
  }
}

async function openLog(row: TaskRow, detail = false) {
  logTitle.value = detail ? `日志详情 — ${row.taskName}` : `日志 — ${row.taskName}`
  try {
    const res = await api.get(`/analytics/domain/indicator-tasks/${row.id}/log`)
    logText.value = res.data?.lastLog || row.lastLog || '暂无日志'
    logRuns.value = res.data?.runs || []
    logDialog.value = true
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载日志失败')
  }
}

async function openIndicators(row: TaskRow) {
  indLoading.value = true
  indDialog.value = true
  try {
    const res = await api.get(`/analytics/domain/indicator-tasks/${row.id}/indicators`)
    indRows.value = res.data || []
  } finally {
    indLoading.value = false
  }
}

async function offline(row: TaskRow) {
  await ElMessageBox.confirm(`确认下线任务「${row.taskName}」？下线后将停止调度。`, '下线确认', { type: 'warning' })
  await api.post(`/analytics/domain/indicator-tasks/${row.id}/offline`)
  ElMessage.success('已下线')
  await load()
}

watch(() => props.domain, () => load())
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="ind-task-panel">
    <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="load">
      <el-form-item label="任务名称" class="portal-field-md">
        <el-input v-model="query.taskName" clearable placeholder="请输入任务名称" />
      </el-form-item>
      <el-form-item label="调度状态" class="portal-field-sm">
        <el-select v-model="query.scheduleStatus" clearable placeholder="请选择">
          <el-option label="已停止" value="STOPPED" />
          <el-option label="已启动" value="STARTED" />
        </el-select>
      </el-form-item>
      <el-form-item label="执行状态" class="portal-field-sm">
        <el-select v-model="query.execStatus" clearable placeholder="请选择">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="运行中" value="RUNNING" />
          <el-option label="未执行" value="NONE" />
        </el-select>
      </el-form-item>
      <el-form-item label="计算结果" class="portal-field-sm">
        <el-select v-model="query.calcResult" clearable placeholder="请选择">
          <el-option label="全部成功" value="ALL_SUCCESS" />
          <el-option label="部分成功" value="PARTIAL" />
          <el-option label="失败" value="FAILED" />
          <el-option label="未计算" value="NONE" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        <el-button :icon="RefreshRight" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-form inline class="portal-inline-form">
      <el-form-item class="portal-form-actions">
        <el-button type="primary" :icon="VideoPlay" @click="batchAction('EXECUTE')">执行</el-button>
        <el-button type="primary" :icon="SwitchButton" @click="batchAction('START')">启动</el-button>
        <el-button type="primary" :icon="CircleClose" @click="batchAction('STOP')">停止</el-button>
      </el-form-item>
    </el-form>

    <el-table
      class="portal-table"
      :data="rows"
      stripe
      size="small"
      empty-text="暂无指标任务。请先在「指标组管理」中发布指标组。"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="执行周期" width="90">
        <template #default="{ row }">{{ cycleLabel(row.execCycle) }}</template>
      </el-table-column>
      <el-table-column label="调度状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.scheduleStatus) }}</template>
      </el-table-column>
      <el-table-column label="执行状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.execStatus) }}</template>
      </el-table-column>
      <el-table-column label="计算结果" width="110">
        <template #default="{ row }">{{ statusLabel(row.calcResult) }}</template>
      </el-table-column>
      <el-table-column label="发布状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.publishStatus) }}</template>
      </el-table-column>
      <el-table-column prop="publishedAt" label="发布时间" width="170" show-overflow-tooltip />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openLog(row, false)">日志</el-button>
          <el-button link type="primary" @click="openIndicators(row)">查看指标</el-button>
          <el-button link type="primary" @click="offline(row)">下线</el-button>
          <el-button link type="primary" @click="openLog(row, true)">日志详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="list-footer">共 {{ rows.length }} 条</div>

    <el-dialog v-model="logDialog" :title="logTitle" width="720px" destroy-on-close>
      <el-input v-model="logText" type="textarea" :rows="14" readonly class="log-box" />
      <el-table v-if="logRuns.length" :data="logRuns" size="small" stripe style="margin-top: 12px" max-height="220">
        <el-table-column prop="startedAt" label="时间" width="170" />
        <el-table-column prop="triggerType" label="触发" width="110" />
        <el-table-column label="执行" width="90">
          <template #default="{ row }">{{ statusLabel(row.execStatus) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="100">
          <template #default="{ row }">{{ statusLabel(row.calcResult) }}</template>
        </el-table-column>
        <el-table-column prop="message" label="摘要" min-width="160" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="logDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="indDialog" title="查看指标" width="860px" destroy-on-close>
      <el-table v-loading="indLoading" :data="indRows" stripe size="small" empty-text="暂无指标">
        <el-table-column prop="indicatorName" label="指标名称" min-width="140" />
        <el-table-column prop="fieldName" label="字段名" min-width="120" />
        <el-table-column prop="resultField" label="结果字段" min-width="120" />
        <el-table-column prop="fieldType" label="字段类型" width="90" />
        <el-table-column prop="indicatorFlag" label="指标标识" min-width="120" />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="indDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.list-footer {
  margin-top: 10px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.log-box :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
}
</style>
