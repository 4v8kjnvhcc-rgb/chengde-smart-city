<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'
import { statusLabel } from '@/utils/status-label'
import { useExecCycleLabel } from '@/utils/exec-cycle-label'

interface CategoryNode {
  id: number
  label: string
  categoryCode?: string
  layerCode?: string
  children?: CategoryNode[]
}

interface MetaDataSource {
  id: number
  sourceName: string
  categoryId?: number
  categoryName?: string
  adapterType?: string
}

interface SourceTable {
  tableName?: string
  sourceTable?: string
  collected?: boolean
}

interface Task {
  id: number
  taskCode: string
  taskName: string
  ingDataSourceId?: number
  metaDataSourceId?: number
  categoryId?: number
  cronExpr?: string
  scopeType: string
  tableList?: string
  status: string
  scheduleType?: string
  publishStatus?: string
  lastMessage?: string
  createdAt?: string
}

interface TaskExtra {
  pendingChangeCount?: number
  needMetadataPublish?: boolean
  schedulePaused?: boolean
  versionCount?: number
}

const activeTab = ref<'manual' | 'scheduled'>('manual')
const router = useRouter()

const categories = ref<CategoryNode[]>([])
const flatCategories = computed(() => flattenTree(categories.value))
const categoryNameMap = computed(() => {
  const m = new Map<number, string>()
  for (const c of flatCategories.value) m.set(c.id, c.label)
  return m
})

const allMetaSources = ref<MetaDataSource[]>([])
const metaSourceNameMap = computed(() => {
  const m = new Map<number, string>()
  for (const s of allMetaSources.value) m.set(s.id, s.sourceName)
  return m
})

// ---------- 手动采集：弹窗三栏选择 ----------
const manualDialogVisible = ref(false)
const dlgCategoryId = ref<number | null>(null)
const dlgSourceId = ref<number | null>(null)
const dlgTableNames = ref<string[]>([])
const dlgCategoryKw = ref('')
const dlgSourceKw = ref('')
const dlgTableKw = ref('')
const dlgCollectFilter = ref('NOT_COLLECTED')
const dlgMetaSources = ref<MetaDataSource[]>([])
const dlgSourceTables = ref<SourceTable[]>([])
const dlgTablesLoading = ref(false)
const dlgSaving = ref(false)

const dlgFlatCategories = computed(() => flatCategories.value)
const dlgFilteredCategories = computed(() => {
  const kw = dlgCategoryKw.value.trim()
  if (!kw) return dlgFlatCategories.value
  return dlgFlatCategories.value.filter(c => c.label.includes(kw))
})
const dlgFilteredSources = computed(() => {
  const kw = dlgSourceKw.value.trim()
  if (!kw) return dlgMetaSources.value
  return dlgMetaSources.value.filter(s => s.sourceName.includes(kw))
})
const dlgFilteredTables = computed(() => {
  const kw = dlgTableKw.value.trim()
  let rows = dlgSourceTables.value
  if (dlgCollectFilter.value === 'COLLECTED') rows = rows.filter(t => t.collected)
  if (dlgCollectFilter.value === 'NOT_COLLECTED') rows = rows.filter(t => !t.collected)
  if (!kw) return rows
  return rows.filter(t => tableNameOf(t).includes(kw))
})

const canConfirmManual = computed(() => dlgTableNames.value.length > 0)

// ---------- 手动采集：列表 ----------
const manualTasks = ref<Task[]>([])
const manualFilter = reactive({ categoryId: undefined as number | undefined })
const selectedManualTaskIds = ref<number[]>([])
const {
  page: manualPage,
  pageSize: manualPageSize,
  paged: pagedManualTasks,
  total: manualTotal,
  resetPage: resetManualPage,
} = useClientPager(manualTasks)

// ---------- 定时任务 ----------
const schedCategoryId = ref<number | null>(null)
const schedSourceId = ref<number | null>(null)
const schedTableNames = ref<string[]>([])
const schedCategoryKw = ref('')
const schedSourceKw = ref('')
const schedTableKw = ref('')
const schedCollectFilter = ref('NOT_COLLECTED')
const schedMetaSources = ref<MetaDataSource[]>([])
const schedSourceTables = ref<SourceTable[]>([])
const schedTablesLoading = ref(false)

const schedFilteredCategories = computed(() => {
  const kw = schedCategoryKw.value.trim()
  if (!kw) return flatCategories.value
  return flatCategories.value.filter(c => c.label.includes(kw))
})
const schedFilteredSources = computed(() => {
  const kw = schedSourceKw.value.trim()
  if (!kw) return schedMetaSources.value
  return schedMetaSources.value.filter(s => s.sourceName.includes(kw))
})
const schedFilteredTables = computed(() => {
  const kw = schedTableKw.value.trim()
  let rows = schedSourceTables.value
  if (schedCollectFilter.value === 'COLLECTED') rows = rows.filter(t => t.collected)
  if (schedCollectFilter.value === 'NOT_COLLECTED') rows = rows.filter(t => !t.collected)
  if (!kw) return rows
  return rows.filter(t => tableNameOf(t).includes(kw))
})

const tasks = ref<Task[]>([])
const taskExtraMap = ref<Record<number, TaskExtra>>({})
const taskFilter = reactive({
  keyword: '',
  sourceType: '',
  metaDataSourceId: undefined as number | undefined,
  scheduleType: 'SCHEDULED',
})

const sourceTypeOptions = computed(() => {
  const set = new Set<string>()
  for (const s of allMetaSources.value) {
    const t = (s.adapterType || '').trim()
    if (t) set.add(t.toUpperCase())
  }
  return Array.from(set).sort()
})

const scheduledSourceOptions = computed(() => {
  const st = taskFilter.sourceType.trim().toUpperCase()
  if (!st) return allMetaSources.value
  return allMetaSources.value.filter((s) => (s.adapterType || '').toUpperCase() === st)
})
const selectedTaskIds = ref<number[]>([])
const runningTaskId = ref<number | null>(null)
const {
  page: taskPage,
  pageSize: taskPageSize,
  paged: pagedTasks,
  total: taskTotal,
  resetPage: resetTaskPage,
} = useClientPager(tasks)

const taskDialogVisible = ref(false)
const taskDialogMode = ref<'create' | 'edit'>('create')
const taskForm = reactive({
  id: 0,
  taskName: '',
  cronExpr: '',
  scopeType: 'TABLE' as 'FULL' | 'TABLE',
})

function flattenTree(nodes: CategoryNode[], out: CategoryNode[] = []): CategoryNode[] {
  for (const n of nodes) {
    out.push(n)
    if (n.children?.length) flattenTree(n.children, out)
  }
  return out
}

function tableNameOf(t: SourceTable) {
  return t.sourceTable || t.tableName || ''
}

function selectAllDlgTables() {
  const names = dlgFilteredTables.value.map(tableNameOf).filter(Boolean)
  dlgTableNames.value = Array.from(new Set([...dlgTableNames.value, ...names]))
}

function clearDlgTables() {
  const visible = new Set(dlgFilteredTables.value.map(tableNameOf))
  dlgTableNames.value = dlgTableNames.value.filter((n) => !visible.has(n))
}

function selectAllSchedTables() {
  const names = schedFilteredTables.value.map(tableNameOf).filter(Boolean)
  schedTableNames.value = Array.from(new Set([...schedTableNames.value, ...names]))
}

function clearSchedTables() {
  const visible = new Set(schedFilteredTables.value.map(tableNameOf))
  schedTableNames.value = schedTableNames.value.filter((n) => !visible.has(n))
}

function parseTableList(raw?: string | null): string[] {
  if (!raw) return []
  const trimmed = raw.trim()
  if (!trimmed) return []
  if (trimmed.startsWith('[')) {
    try {
      const arr = JSON.parse(trimmed)
      if (Array.isArray(arr)) return arr.map(String)
    } catch { /* fall through */ }
  }
  return trimmed.split(/[,，\s]+/).map(s => s.trim()).filter(Boolean)
}

function formatTableList(names: string[]) {
  return names.length ? JSON.stringify(names) : ''
}

function formatTableListDisplay(raw?: string | null) {
  const names = parseTableList(raw)
  if (!names.length) return '整库'
  if (names.length <= 3) return names.join('、')
  return `${names.slice(0, 3).join('、')} 等 ${names.length} 张`
}

function formatTime(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

const { label: cycleLabel } = useExecCycleLabel()

async function loadCategories() {
  const res = await api.get('/governance/platform/metadata/source-categories/tree')
  categories.value = res.data || []
}

async function loadAllMetaSources() {
  allMetaSources.value = (await api.get('/governance/platform/metadata/data-sources')).data || []
}

async function loadMetaSourcesByCategory(categoryId: number | null, target: MetaDataSource[]) {
  if (!categoryId) {
    target.splice(0, target.length)
    return
  }
  const rows = (await api.get('/governance/platform/metadata/data-sources', {
    params: { categoryId },
  })).data || []
  target.splice(0, target.length, ...rows)
}

async function loadSourceTablesFor(
  sourceId: number,
  collectFilter: string,
  target: SourceTable[],
  loading: { value: boolean },
) {
  loading.value = true
  target.splice(0, target.length)
  try {
    const rows = (await api.get(
      `/governance/platform/metadata/collect/meta-data-sources/${sourceId}/tables`,
      { params: { collectFilter: collectFilter || undefined } },
    )).data || []
    target.splice(0, target.length, ...rows)
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '加载表列表失败，请检查数据源连接配置')
  } finally {
    loading.value = false
  }
}

async function loadManualTasks() {
  let rows: Task[] = (await api.get('/governance/platform/metadata/collect/tasks', {
    params: { scheduleType: 'MANUAL' },
  })).data || []
  if (manualFilter.categoryId) {
    rows = rows.filter(t => t.categoryId === manualFilter.categoryId)
  }
  manualTasks.value = rows
  resetManualPage()
}

async function loadScheduledTasks() {
  let rows = (await api.get('/governance/platform/metadata/collect/tasks', {
    params: {
      scheduleType: 'SCHEDULED',
      keyword: taskFilter.keyword || undefined,
      enriched: true,
    },
  })).data || []
  const extra: Record<number, TaskExtra> = {}
  let tasksMapped = rows.map((r: { task: Task; pendingChangeCount?: number; needMetadataPublish?: boolean; schedulePaused?: boolean; versionCount?: number }) => {
    extra[r.task.id] = {
      pendingChangeCount: r.pendingChangeCount,
      needMetadataPublish: r.needMetadataPublish,
      schedulePaused: r.schedulePaused,
      versionCount: r.versionCount,
    }
    return r.task
  })
  const st = taskFilter.sourceType.trim().toUpperCase()
  if (st) {
    const allowed = new Set(
      allMetaSources.value
        .filter((s) => (s.adapterType || '').toUpperCase() === st)
        .map((s) => s.id),
    )
    tasksMapped = tasksMapped.filter((t) => t.metaDataSourceId != null && allowed.has(t.metaDataSourceId))
  }
  if (taskFilter.metaDataSourceId) {
    tasksMapped = tasksMapped.filter((t) => t.metaDataSourceId === taskFilter.metaDataSourceId)
  }
  tasks.value = tasksMapped
  taskExtraMap.value = extra
  resetTaskPage()
}

function taskExtra(taskId: number): TaskExtra {
  return taskExtraMap.value[taskId] || {}
}

function goTaskVersions(row: Task) {
  router.push({
    path: '/governance',
    query: { tab: 'metadata', section: 'version', collectTaskId: row.id, collectTaskName: row.taskName },
  })
}

watch(dlgCategoryId, async (id) => {
  dlgSourceId.value = null
  dlgTableNames.value = []
  dlgSourceTables.value = []
  await loadMetaSourcesByCategory(id, dlgMetaSources.value)
})

watch(dlgSourceId, async (id) => {
  dlgTableNames.value = []
  if (!id) {
    dlgSourceTables.value = []
    return
  }
  await loadSourceTablesFor(id, dlgCollectFilter.value, dlgSourceTables.value, dlgTablesLoading)
})

watch(dlgCollectFilter, async () => {
  if (dlgSourceId.value) {
    await loadSourceTablesFor(dlgSourceId.value, dlgCollectFilter.value, dlgSourceTables.value, dlgTablesLoading)
  }
})

watch(schedCategoryId, async (id) => {
  schedSourceId.value = null
  schedTableNames.value = []
  schedSourceTables.value = []
  await loadMetaSourcesByCategory(id, schedMetaSources.value)
})

watch(schedSourceId, async (id) => {
  schedTableNames.value = []
  if (!id) {
    schedSourceTables.value = []
    return
  }
  await loadSourceTablesFor(id, schedCollectFilter.value, schedSourceTables.value, schedTablesLoading)
})

watch(schedCollectFilter, async () => {
  if (schedSourceId.value) {
    await loadSourceTablesFor(schedSourceId.value, schedCollectFilter.value, schedSourceTables.value, schedTablesLoading)
  }
})

watch(activeTab, async (tab) => {
  if (tab === 'manual') await loadManualTasks()
  else await Promise.all([loadScheduledTasks(), loadAllMetaSources()])
})

function openManualDialog() {
  dlgCategoryKw.value = ''
  dlgSourceKw.value = ''
  dlgTableKw.value = ''
  dlgCollectFilter.value = ''
  dlgCategoryId.value = flatCategories.value[0]?.id ?? null
  dlgSourceId.value = null
  dlgTableNames.value = []
  dlgMetaSources.value = []
  dlgSourceTables.value = []
  manualDialogVisible.value = true
}

async function confirmManualDialog() {
  if (!dlgSourceId.value) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!dlgTableNames.value.length) {
    ElMessage.warning('请至少选择一张表')
    return
  }
  const sourceName = metaSourceNameMap.value.get(dlgSourceId.value) || '数据源'
  dlgSaving.value = true
  try {
    const res = await api.post('/governance/platform/metadata/collect/manual', {
      taskName: `手动采集_${sourceName}_${Date.now()}`,
      categoryId: dlgCategoryId.value,
      metaDataSourceId: dlgSourceId.value,
      scopeType: 'TABLE',
      tableList: formatTableList(dlgTableNames.value),
    })
    const status = res.data?.status
    if (status === 'RUNNING') {
      ElMessage.success(res.data?.message || '采集已启动，请到「元数据采集监控」查看或停止')
    } else {
      ElMessage.success(status ? `采集完成：${statusLabel(status)}` : '手动采集已执行')
    }
    manualDialogVisible.value = false
    await loadManualTasks()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '手动采集失败')
  } finally {
    dlgSaving.value = false
  }
}

function openCreateTaskDialog() {
  taskDialogMode.value = 'create'
  taskForm.id = 0
  taskForm.taskName = ''
  taskForm.cronExpr = ''
  taskForm.scopeType = 'TABLE'
  schedCategoryId.value = flatCategories.value[0]?.id ?? null
  schedSourceId.value = null
  schedTableNames.value = []
  taskDialogVisible.value = true
}

function openEditTaskDialog(row: Task) {
  taskDialogMode.value = 'edit'
  taskForm.id = row.id
  taskForm.taskName = row.taskName
  taskForm.cronExpr = row.cronExpr || ''
  taskForm.scopeType = row.scopeType === 'FULL' ? 'FULL' : 'TABLE'
  schedCategoryId.value = row.categoryId || null
  schedSourceId.value = row.metaDataSourceId || null
  schedTableNames.value = parseTableList(row.tableList)
  taskDialogVisible.value = true
  if (schedCategoryId.value) {
    loadMetaSourcesByCategory(schedCategoryId.value, schedMetaSources.value).then(() => {
      if (schedSourceId.value) {
        loadSourceTablesFor(schedSourceId.value, schedCollectFilter.value, schedSourceTables.value, schedTablesLoading)
      }
    })
  }
}

async function saveTaskDialog() {
  if (!taskForm.taskName.trim()) {
    ElMessage.warning('请填写任务名称')
    return
  }
  if (!taskForm.cronExpr.trim()) {
    ElMessage.warning('请选择或填写执行周期')
    return
  }
  if (!schedSourceId.value) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (taskForm.scopeType === 'TABLE' && !schedTableNames.value.length) {
    ElMessage.warning('请至少选择一张表')
    return
  }
  const body = {
    taskName: taskForm.taskName.trim(),
    categoryId: schedCategoryId.value,
    metaDataSourceId: schedSourceId.value,
    cronExpr: taskForm.cronExpr,
    scopeType: taskForm.scopeType,
    tableList: taskForm.scopeType === 'TABLE' ? formatTableList(schedTableNames.value) : null,
    scheduleType: 'SCHEDULED',
  }
  if (taskDialogMode.value === 'create') {
    await api.post('/governance/platform/metadata/collect/tasks', body)
    ElMessage.success('定时任务已创建')
  } else {
    await api.put(`/governance/platform/metadata/collect/tasks/${taskForm.id}`, body)
    ElMessage.success('任务已更新')
  }
  taskDialogVisible.value = false
  await loadScheduledTasks()
}

async function publishTasks() {
  if (!selectedTaskIds.value.length) {
    ElMessage.warning('请选择要发布的任务')
    return
  }
  const hasChange = selectedTaskIds.value.some((id) => taskExtra(id).needMetadataPublish)
  const tip = hasChange
    ? `将发布 ${selectedTaskIds.value.length} 个任务的元数据定版（含变更表），并恢复 DolphinScheduler 调度。是否继续？`
    : `将发布 ${selectedTaskIds.value.length} 个定时任务到 DolphinScheduler。是否继续？`
  try {
    await ElMessageBox.confirm(tip, '发布定版', { type: 'warning', confirmButtonText: '发布', cancelButtonText: '取消' })
  } catch {
    return
  }
  for (const id of selectedTaskIds.value) {
    const res = await api.post(`/governance/platform/metadata/collect/tasks/${id}/publish-all`)
    ElMessage.success(res.data?.message || '发布成功')
  }
  selectedTaskIds.value = []
  await loadScheduledTasks()
}

async function publishOneTask(row: Task) {
  const extra = taskExtra(row.id)
  const tip = extra.needMetadataPublish
    ? `任务「${row.taskName}」检测到元数据变更，将发布定版并恢复调度。是否继续？`
    : `确认发布任务「${row.taskName}」到 DolphinScheduler？`
  try {
    await ElMessageBox.confirm(tip, '发布定版', { type: 'warning', confirmButtonText: '发布', cancelButtonText: '取消' })
  } catch {
    return
  }
  const res = await api.post(`/governance/platform/metadata/collect/tasks/${row.id}/publish-all`)
  ElMessage.success(res.data?.message || '发布成功')
  await loadScheduledTasks()
}

function canDeleteTask(row: Task) {
  return row.status !== 'RUNNING' && row.publishStatus !== 'PUBLISHED'
}

async function batchDeleteTasks(ids: number[], reload: () => Promise<void>, clearSelection: () => void) {
  if (!ids.length) {
    ElMessage.warning('请选择要删除的任务')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 个任务？`, '批量删除确认', { type: 'warning' })
    await api.post('/governance/platform/metadata/collect/tasks/batch-delete', { ids })
    ElMessage.success('已删除')
    clearSelection()
    await reload()
  } catch (e: unknown) {
    if (e === 'cancel' || (e as { message?: string })?.message === 'cancel') return
    const err = e as Error & { message?: string }
    ElMessage.error(err.message || '删除失败')
  }
}

async function deleteScheduledTasks() {
  await batchDeleteTasks(selectedTaskIds.value, loadScheduledTasks, () => { selectedTaskIds.value = [] })
}

async function deleteManualTasksBatch() {
  await batchDeleteTasks(selectedManualTaskIds.value, loadManualTasks, () => { selectedManualTaskIds.value = [] })
}

async function runTaskNow(id: number) {
  if (runningTaskId.value != null) return
  runningTaskId.value = id
  try {
    const res = await api.post(
      `/governance/platform/metadata/collect/tasks/${id}/run`,
      {},
    )
    const status = res.data?.status
    const msg = res.data?.message
    if (status === 'RUNNING') {
      ElMessage.success(msg || '采集已启动，请到「元数据采集监控」查看或停止')
    } else if (status === 'FAILED') {
      ElMessage.error(msg || '采集失败')
    } else {
      ElMessage.success(msg || `采集完成：${statusLabel(status || 'SUCCESS')}`)
    }
    await loadScheduledTasks()
  } catch (e: unknown) {
    const err = e as Error & { message?: string }
    ElMessage.error(err.message || '触发采集失败')
  } finally {
    runningTaskId.value = null
  }
}

async function unpublishTask(row: Task) {
  await api.post(`/governance/platform/metadata/collect/tasks/${row.id}/unpublish`)
  ElMessage.success('已下线')
  await loadScheduledTasks()
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadAllMetaSources(), loadManualTasks()])
})
</script>

<template>
  <PageCard title="元数据采集">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="手动采集" name="manual">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="openManualDialog">+ 创建</el-button>
            <el-button
              type="danger"
              plain
              :disabled="!selectedManualTaskIds.length"
              @click="deleteManualTasksBatch"
            >批量删除</el-button>
          </el-form-item>
          <el-form-item label="数据分类" class="portal-field-md">
            <el-select
              v-model="manualFilter.categoryId"
              clearable
              filterable
              placeholder="全部分类"
            >
              <el-option
                v-for="c in flatCategories"
                :key="c.id"
                :label="c.label"
                :value="c.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="loadManualTasks">查询</el-button>
          </el-form-item>
        </el-form>

        <div class="meta-section-title">手动采集列表</div>
        <el-table
          :data="pagedManualTasks"
          stripe
          size="small"
          empty-text="暂无手动采集，请点击「创建」新增"
          @selection-change="(rows: Task[]) => { selectedManualTaskIds = rows.map(r => r.id) }"
        >
          <el-table-column type="selection" width="42" :selectable="(row: Task) => canDeleteTask(row)" />
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
          <el-table-column label="分类" width="100" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.categoryId ? (categoryNameMap.get(row.categoryId) || '—') : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="数据源" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.metaDataSourceId ? (metaSourceNameMap.get(row.metaDataSourceId) || '—') : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="采集表" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ formatTableListDisplay(row.tableList) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="$statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="最近结果" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.lastMessage || '—' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-model:page="manualPage"
          v-model:page-size="manualPageSize"
          :total="manualTotal"
        />
      </el-tab-pane>

      <el-tab-pane label="定时任务采集" name="scheduled">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="openCreateTaskDialog">+ 新增</el-button>
            <el-button type="danger" plain :disabled="!selectedTaskIds.length" @click="deleteScheduledTasks">批量删除</el-button>
          </el-form-item>
          <el-form-item label="关键字" class="portal-field-md">
            <el-input v-model="taskFilter.keyword" placeholder="任务名称" clearable @keyup.enter="loadScheduledTasks" />
          </el-form-item>
          <el-form-item label="数据源类型" class="portal-field-md">
            <el-select
              v-model="taskFilter.sourceType"
              clearable
              placeholder="全部"
              @change="taskFilter.metaDataSourceId = undefined"
            >
              <el-option v-for="t in sourceTypeOptions" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据源" class="portal-field-lg">
            <el-select v-model="taskFilter.metaDataSourceId" clearable filterable placeholder="全部">
              <el-option
                v-for="s in scheduledSourceOptions"
                :key="s.id"
                :label="s.sourceName"
                :value="s.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="loadScheduledTasks">查询</el-button>
          </el-form-item>
        </el-form>

        <el-table
          :data="pagedTasks"
          stripe
          size="small"
          @selection-change="(rows: Task[]) => { selectedTaskIds = rows.map(r => r.id) }"
        >
          <el-table-column type="selection" width="42" :selectable="(row: Task) => canDeleteTask(row)" />
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="taskName" label="任务名称" min-width="140" />
          <el-table-column label="数据源" min-width="120">
            <template #default="{ row }">
              {{ row.metaDataSourceId ? (metaSourceNameMap.get(row.metaDataSourceId) || '—') : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="执行周期" min-width="120">
            <template #default="{ row }">{{ cycleLabel(row.cronExpr) }}</template>
          </el-table-column>
          <el-table-column label="发布状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.publishStatus === 'PUBLISHED' ? 'success' : 'info'" size="small">
                {{ statusLabel(row.publishStatus || 'DRAFT') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="变更/版本" width="120">
            <template #default="{ row }">
              <el-tag v-if="taskExtra(row.id).needMetadataPublish" type="warning" size="small">待发布变更</el-tag>
              <el-tag v-else-if="taskExtra(row.id).schedulePaused" type="info" size="small">调度已暂停</el-tag>
              <span v-else-if="taskExtra(row.id).versionCount">版本 {{ taskExtra(row.id).versionCount }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="任务状态" width="90">
            <template #default="{ row }">
              <el-tag :type="$statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="success"
                @click="publishOneTask(row)"
              >发布定版</el-button>
              <el-button
                link
                type="primary"
                :loading="runningTaskId === row.id"
                :disabled="runningTaskId != null && runningTaskId !== row.id"
                @click="runTaskNow(row.id)"
              >执行</el-button>
              <el-button link @click="openEditTaskDialog(row)">编辑</el-button>
              <el-button link type="primary" @click="goTaskVersions(row)">版本</el-button>
              <el-button
                v-if="row.publishStatus === 'PUBLISHED'"
                link
                type="warning"
                @click="unpublishTask(row)"
              >下线</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination v-model:page="taskPage" v-model:page-size="taskPageSize" :total="taskTotal" />
      </el-tab-pane>
    </el-tabs>

    <!-- 手动采集：采集元数据弹窗 -->
    <el-dialog v-model="manualDialogVisible" title="采集元数据" width="920px" destroy-on-close>
      <div class="meta-picker meta-picker--dialog">
        <div class="meta-picker-col">
          <div class="meta-picker-title">元数据-分类</div>
          <el-input v-model="dlgCategoryKw" placeholder="请输入分类名称" clearable size="small" />
          <el-scrollbar class="meta-picker-list">
            <div
              v-for="c in dlgFilteredCategories"
              :key="'m' + c.id"
              class="meta-picker-item"
              :class="{ active: dlgCategoryId === c.id }"
              @click="dlgCategoryId = c.id"
            >
              {{ c.label }}
            </div>
          </el-scrollbar>
        </div>
        <div class="meta-picker-col">
          <div class="meta-picker-title">元数据-数据源</div>
          <el-input v-model="dlgSourceKw" placeholder="请输入数据源名称" clearable size="small" />
          <el-scrollbar class="meta-picker-list">
            <el-empty v-if="!dlgFilteredSources.length" description="暂无数据" :image-size="48" />
            <div
              v-for="s in dlgFilteredSources"
              :key="'m' + s.id"
              class="meta-picker-item"
              :class="{ active: dlgSourceId === s.id }"
              @click="dlgSourceId = s.id"
            >
              {{ s.sourceName }}
            </div>
          </el-scrollbar>
        </div>
        <div class="meta-picker-col">
          <div class="meta-picker-title">元数据-表</div>
          <div class="meta-picker-toolbar">
            <el-input v-model="dlgTableKw" placeholder="请输入表名称" clearable size="small" />
            <el-select v-model="dlgCollectFilter" clearable placeholder="采集状态" size="small" style="width:110px">
              <el-option label="已采集" value="COLLECTED" />
              <el-option label="未采集" value="NOT_COLLECTED" />
            </el-select>
            <el-button size="small" @click="selectAllDlgTables">全选</el-button>
            <el-button size="small" @click="clearDlgTables">取消</el-button>
          </div>
          <el-scrollbar v-loading="dlgTablesLoading" class="meta-picker-list">
            <el-empty v-if="!dlgFilteredTables.length && !dlgTablesLoading" description="暂无数据" :image-size="48" />
            <el-checkbox-group v-model="dlgTableNames">
              <div v-for="t in dlgFilteredTables" :key="'m' + tableNameOf(t)" class="meta-table-row">
                <el-checkbox :value="tableNameOf(t)">
                  {{ tableNameOf(t) }}
                  <el-tag v-if="t.collected" size="small" type="success" class="meta-tag">已采集</el-tag>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </el-scrollbar>
        </div>
      </div>
      <template #footer>
        <div class="dlg-footer">
          <span v-if="!canConfirmManual" class="dlg-footer-hint">请先在「元数据-表」中勾选至少一张表</span>
          <div class="dlg-footer-actions">
            <el-button @click="manualDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="dlgSaving" :disabled="!canConfirmManual" @click="confirmManualDialog">
              确定
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 定时任务弹窗 -->
    <el-dialog
      v-model="taskDialogVisible"
      :title="taskDialogMode === 'create' ? '新增定时采集任务' : '编辑定时采集任务'"
      width="920px"
      destroy-on-close
    >
      <el-form label-width="96px" class="task-basic-form">
        <el-form-item label="任务名称" required>
          <el-input v-model="taskForm.taskName" placeholder="采集任务名称" />
        </el-form-item>
        <el-form-item label="执行周期" required>
          <ExecCycleSelect v-model="taskForm.cronExpr" :allow-custom="false" style="max-width:420px" />
        </el-form-item>
        <el-form-item label="采集范围">
          <el-radio-group v-model="taskForm.scopeType">
            <el-radio value="FULL">整库</el-radio>
            <el-radio value="TABLE">选表</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <div class="meta-picker meta-picker--dialog">
        <div class="meta-picker-col">
          <div class="meta-picker-title">元数据-分类</div>
          <el-input v-model="schedCategoryKw" placeholder="请输入分类名称" clearable size="small" />
          <el-scrollbar class="meta-picker-list">
            <div
              v-for="c in schedFilteredCategories"
              :key="'s' + c.id"
              class="meta-picker-item"
              :class="{ active: schedCategoryId === c.id }"
              @click="schedCategoryId = c.id"
            >
              {{ c.label }}
            </div>
          </el-scrollbar>
        </div>
        <div class="meta-picker-col">
          <div class="meta-picker-title">元数据-数据源</div>
          <el-input v-model="schedSourceKw" placeholder="请输入数据源名称" clearable size="small" />
          <el-scrollbar class="meta-picker-list">
            <el-empty v-if="!schedFilteredSources.length" description="暂无数据" :image-size="48" />
            <div
              v-for="s in schedFilteredSources"
              :key="'s' + s.id"
              class="meta-picker-item"
              :class="{ active: schedSourceId === s.id }"
              @click="schedSourceId = s.id"
            >
              {{ s.sourceName }}
            </div>
          </el-scrollbar>
        </div>
        <div class="meta-picker-col">
          <div class="meta-picker-title">元数据-表</div>
          <div class="meta-picker-toolbar">
            <el-input v-model="schedTableKw" placeholder="请输入表名称" clearable size="small" />
            <el-select v-model="schedCollectFilter" clearable placeholder="采集状态" size="small" style="width:110px">
              <el-option label="已采集" value="COLLECTED" />
              <el-option label="未采集" value="NOT_COLLECTED" />
            </el-select>
            <el-button size="small" :disabled="taskForm.scopeType !== 'TABLE'" @click="selectAllSchedTables">全选</el-button>
            <el-button size="small" :disabled="taskForm.scopeType !== 'TABLE'" @click="clearSchedTables">取消</el-button>
          </div>
          <el-scrollbar v-loading="schedTablesLoading" class="meta-picker-list">
            <el-empty v-if="taskForm.scopeType === 'FULL'" description="整库采集无需选表" :image-size="48" />
            <template v-else>
              <el-empty v-if="!schedFilteredTables.length && !schedTablesLoading" description="暂无数据" :image-size="48" />
              <el-checkbox-group v-model="schedTableNames">
                <div v-for="t in schedFilteredTables" :key="'s' + tableNameOf(t)" class="meta-table-row">
                  <el-checkbox :value="tableNameOf(t)">{{ tableNameOf(t) }}</el-checkbox>
                </div>
              </el-checkbox-group>
            </template>
          </el-scrollbar>
        </div>
      </div>

      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTaskDialog">确定</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.meta-section-title {
  font-weight: 600;
  font-size: 14px;
  margin: 16px 0 10px;
}
.meta-picker {
  display: grid;
  grid-template-columns: 1fr 1fr 1.2fr;
  gap: 12px;
  min-height: 360px;
}
.meta-picker--dialog {
  height: 420px;
  min-height: 320px;
}
.meta-picker-col {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow: hidden;
  background: var(--el-fill-color-blank);
}
.meta-picker-title {
  font-weight: 600;
  font-size: 13px;
}
.meta-picker-toolbar {
  display: flex;
  gap: 8px;
}
.meta-picker-list {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.meta-picker-list :deep(.el-scrollbar) {
  height: 100%;
}
.meta-picker-list :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}
.meta-picker-item {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}
.meta-picker-item:hover {
  background: var(--el-fill-color-light);
}
.meta-picker-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.meta-table-row {
  padding: 4px 2px;
}
.meta-tag {
  margin-left: 6px;
}
.task-basic-form {
  max-width: 640px;
  margin-bottom: 12px;
}
.dlg-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 100%;
  gap: 12px;
}
.dlg-footer-hint {
  margin-right: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.dlg-footer-actions {
  display: flex;
  gap: 8px;
}
</style>
