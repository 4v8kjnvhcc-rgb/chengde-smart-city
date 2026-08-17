<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshRight, Search, VideoPlay, SwitchButton, CircleClose } from '@element-plus/icons-vue'
import api from '@/api/http'
import { formatDateTime } from '@/utils/datetime'
import { statusLabel } from '@/utils/status-label'
import { useExecCycleLabel } from '@/utils/exec-cycle-label'

const props = defineProps<{
  domain: string
  /** 父级 Tab 是否当前展示；切回时重新拉取 */
  active?: boolean
  /**
   * 仅展示指定指标域的任务（业务支撑四域 Hub 传入对应系统名；
   * 治理平台「指标任务」不传，显示全部）。
   */
  scopeDomainName?: string
}>()

interface TaskRow {
  id: string
  groupId: string
  taskName: string
  targetTable?: string
  indicatorDomainName?: string
  execCycle: string
  scheduleCron?: string
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

const { label: cycleNameLabel } = useExecCycleLabel()

const scopedDomain = computed(() => (props.scopeDomainName || '').trim())
const isScoped = computed(() => !!scopedDomain.value)

const loading = ref(false)
const rows = ref<TaskRow[]>([])
const selected = ref<TaskRow[]>([])
const domainOptions = ref<string[]>([])
const tableOptions = ref<string[]>([])
const query = reactive({
  taskName: '',
  targetTable: '',
  indicatorDomainName: '',
  scheduleStatus: '',
  execStatus: '',
})

function applyScopeToQuery() {
  if (isScoped.value) {
    query.indicatorDomainName = scopedDomain.value
  }
}

const logDialog = ref(false)
const logTitle = ref('')
const logText = ref('')
const logRuns = ref<Array<{ id: number; triggerType: string; execStatus: string; calcResult: string; message?: string; startedAt?: string }>>([])

const indDialog = ref(false)
const indRows = ref<IndicatorRow[]>([])
const indLoading = ref(false)

const domainSelectOptions = computed(() => {
  const set = new Set<string>(domainOptions.value)
  if (query.indicatorDomainName.trim()) set.add(query.indicatorDomainName.trim())
  return Array.from(set).filter(Boolean).sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

const tableSelectOptions = computed(() => {
  const set = new Set<string>(tableOptions.value)
  if (query.targetTable.trim()) set.add(query.targetTable.trim())
  return Array.from(set).filter(Boolean).sort((a, b) => a.localeCompare(b, 'zh-CN'))
})

function isPublished(row: TaskRow) {
  return String(row.publishStatus || '').toUpperCase() === 'PUBLISHED'
}

function isOffline(row: TaskRow) {
  return String(row.publishStatus || '').toUpperCase() === 'OFFLINE'
}

function cycleLabel(row: TaskRow) {
  const cron = row.scheduleCron || row.execCycle
  const named = cycleNameLabel(cron)
  if (named && named !== cron) return named
  // 兼容历史 DAILY/MONTHLY
  return statusLabel(row.execCycle) || cron || '—'
}

async function loadFilterOptions() {
  const [domainRes, groupRes] = await Promise.all([
    api.get(`/analytics/domain/${props.domain}/indicator-domains`),
    api.get(`/analytics/domain/${props.domain}/indicator-groups`),
  ])
  let domains = (domainRes.data || []) as Array<{ id?: string; domainName?: string }>
  if (isScoped.value) {
    domains = domains.filter((d) => String(d.domainName || '').includes(scopedDomain.value))
  }
  domainOptions.value = Array.from(
    new Set(domains.map((d) => (d.domainName || '').trim()).filter(Boolean)),
  )
  const scopedIds = new Set(domains.map((d) => String(d.id || '')).filter(Boolean))
  const groups = (groupRes.data || []) as Array<{ indicatorDomainId?: string; targetTable?: string }>
  const scopedGroups = isScoped.value
    ? groups.filter((g) => scopedIds.has(String(g.indicatorDomainId || '')))
    : groups
  tableOptions.value = Array.from(
    new Set(scopedGroups.map((g) => (g.targetTable || '').trim()).filter(Boolean)),
  )
}

async function load() {
  applyScopeToQuery()
  loading.value = true
  try {
    const res = await api.get(`/analytics/domain/${props.domain}/indicator-tasks`, {
      params: {
        taskName: query.taskName || undefined,
        targetTable: query.targetTable || undefined,
        indicatorDomainName: (isScoped.value ? scopedDomain.value : query.indicatorDomainName) || undefined,
        scheduleStatus: query.scheduleStatus || undefined,
        execStatus: query.execStatus || undefined,
      },
    })
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.taskName = ''
  query.targetTable = ''
  query.indicatorDomainName = isScoped.value ? scopedDomain.value : ''
  query.scheduleStatus = ''
  query.execStatus = ''
  void load()
}

function onSelectionChange(val: TaskRow[]) {
  selected.value = val
}

async function batchAction(action: 'EXECUTE' | 'START' | 'STOP' | 'OFFLINE' | 'PUBLISH') {
  if (!selected.value.length) {
    ElMessage.warning('请先勾选任务')
    return
  }
  if (action === 'EXECUTE' || action === 'START') {
    const bad = selected.value.filter((r) => !isPublished(r))
    if (bad.length) {
      ElMessage.warning('仅已发布任务可执行/启动，请先取消勾选未发布或已下线任务')
      return
    }
  }
  if (action === 'OFFLINE') {
    const bad = selected.value.filter((r) => !isPublished(r))
    if (bad.length) {
      ElMessage.warning('仅已发布任务可下线')
      return
    }
  }
  if (action === 'PUBLISH') {
    const bad = selected.value.filter((r) => !isOffline(r))
    if (bad.length) {
      ElMessage.warning('仅已下线任务可发布')
      return
    }
  }
  const labelMap: Record<string, string> = {
    EXECUTE: '执行',
    START: '启动',
    STOP: '停止',
    OFFLINE: '下线',
    PUBLISH: '发布',
  }
  const label = labelMap[action]
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
  if (!isPublished(row)) {
    ElMessage.warning('仅已发布任务可下线')
    return
  }
  await ElMessageBox.confirm(`确认下线任务「${row.taskName}」？下线后将停止调度。`, '下线确认', { type: 'warning' })
  await api.post(`/analytics/domain/indicator-tasks/${row.id}/offline`)
  ElMessage.success('已下线')
  await load()
}

async function publish(row: TaskRow) {
  if (!isOffline(row)) {
    ElMessage.warning('仅已下线任务可发布')
    return
  }
  await ElMessageBox.confirm(`确认发布任务「${row.taskName}」？发布后可执行/启动。`, '发布确认', { type: 'warning' })
  await api.post(`/analytics/domain/indicator-tasks/${row.id}/publish`, {})
  ElMessage.success('已发布')
  await load()
}

watch(
  () => [props.domain, scopedDomain.value] as const,
  async () => {
    applyScopeToQuery()
    await loadFilterOptions()
    await load()
  },
)

watch(
  () => props.active,
  async (v, prev) => {
    if (v && prev === false) {
      applyScopeToQuery()
      await loadFilterOptions()
      await load()
    }
  },
)

onMounted(async () => {
  applyScopeToQuery()
  await loadFilterOptions()
  await load()
})
</script>

<template>
  <div v-loading="loading" class="ind-task-panel">
    <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="load">
      <el-form-item label="任务名称" class="portal-field-md">
        <el-input v-model="query.taskName" clearable placeholder="请输入任务名称" />
      </el-form-item>
      <el-form-item label="指标表名" class="portal-field-lg">
        <el-select
          v-model="query.targetTable"
          clearable
          filterable
          allow-create
          default-first-option
          placeholder="请选择或输入指标表名"
        >
          <el-option v-for="t in tableSelectOptions" :key="t" :label="t" :value="t" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="!isScoped" label="指标域" class="portal-field-lg">
        <el-select
          v-model="query.indicatorDomainName"
          clearable
          filterable
          allow-create
          default-first-option
          placeholder="请选择或输入指标域"
        >
          <el-option v-for="d in domainSelectOptions" :key="d" :label="d" :value="d" />
        </el-select>
      </el-form-item>
      <el-form-item label="调度状态" class="portal-field-sm">
        <el-select v-model="query.scheduleStatus" clearable placeholder="全部">
          <el-option label="已停止" value="STOPPED" />
          <el-option label="已启动" value="STARTED" />
        </el-select>
      </el-form-item>
      <el-form-item label="执行状态" class="portal-field-sm">
        <el-select v-model="query.execStatus" clearable placeholder="全部">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="运行中" value="RUNNING" />
          <el-option label="未执行" value="NONE" />
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
        <el-button type="warning" @click="batchAction('OFFLINE')">批量下线</el-button>
        <el-button type="success" @click="batchAction('PUBLISH')">批量发布</el-button>
      </el-form-item>
    </el-form>

    <el-table
      class="portal-table"
      :data="rows"
      stripe
      border
      size="small"
      empty-text="暂无指标任务。请先在「指标组管理」中发布指标组。"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="48" />
      <el-table-column type="index" label="序号" width="60" />
      <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="targetTable" label="指标表名" min-width="160" show-overflow-tooltip />
      <el-table-column prop="indicatorDomainName" label="指标域" min-width="180" show-overflow-tooltip />
      <el-table-column label="执行周期" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ cycleLabel(row) }}</template>
      </el-table-column>
      <el-table-column label="调度状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.scheduleStatus) }}</template>
      </el-table-column>
      <el-table-column label="执行状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.execStatus) }}</template>
      </el-table-column>
      <el-table-column label="最近执行时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.lastRunAt) || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openIndicators(row)">查看指标</el-button>
          <el-button v-if="isOffline(row)" link type="primary" @click="publish(row)">发布</el-button>
          <el-button v-if="isPublished(row)" link type="primary" @click="offline(row)">下线</el-button>
          <el-button link type="primary" @click="openLog(row, true)">日志详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="list-footer">共 {{ rows.length }} 条</div>

    <el-dialog v-model="logDialog" :title="logTitle" width="720px" destroy-on-close>
      <el-input v-model="logText" type="textarea" :rows="14" readonly class="log-box" />
      <el-table v-if="logRuns.length" :data="logRuns" size="small" stripe style="margin-top: 12px" max-height="220">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
        </el-table-column>
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
