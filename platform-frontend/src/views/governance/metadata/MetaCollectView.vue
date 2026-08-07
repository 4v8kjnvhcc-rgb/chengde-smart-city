<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { LAYER_OPTIONS } from './meta-labels'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'

interface DataSource {
  id: number
  sourceCode?: string
  sourceName: string
  sourceType?: string
  layerHint: string
  platformLayer?: boolean
  databaseName?: string
}

interface SourceTable {
  sourceTable?: string
  tableName?: string
}

interface MetaModel {
  id: number
  modelNameZh: string
  status: string
}

interface Task {
  id: number
  taskCode: string
  taskName: string
  ingDataSourceId?: number
  connectorId?: number
  modelId?: number
  cronExpr?: string
  scopeType: string
  tableList?: string
  status: string
  lastMessage?: string
}

const LAYER_GROUP_ORDER = ['EXTERNAL', 'ODS', 'DWD', 'DWS', 'ADS']

const dataSources = ref<DataSource[]>([])
const models = ref<MetaModel[]>([])
const tasks = ref<Task[]>([])
const {
  page: taskPage,
  pageSize: taskPageSize,
  paged: pagedTasks,
  total: taskTotal,
  resetPage: resetTaskPage,
} = useClientPager(tasks)

const sourceTables = ref<SourceTable[]>([])
const tablesLoading = ref(false)
const selectedTableNames = ref<string[]>([])

const cronEnabled = ref(false)

const form = reactive({
  taskName: '',
  ingDataSourceId: undefined as number | undefined,
  modelId: undefined as number | undefined,
  scopeType: 'FULL' as 'FULL' | 'TABLE',
  cronExpr: '',
})

const editVisible = ref(false)
const editing = ref<Task | null>(null)
const editCronEnabled = ref(false)
const editForm = reactive({
  taskName: '',
  modelId: undefined as number | undefined,
  cronExpr: '',
  scopeType: 'FULL' as 'FULL' | 'TABLE',
  tableList: '',
})
const editSelectedTables = ref<string[]>([])
const editSourceTables = ref<SourceTable[]>([])
const editTablesLoading = ref(false)

const connectorForm = reactive({
  connectorName: '',
  sourceType: 'MySQL',
  jdbcUrl: '',
  jdbcUser: '',
  jdbcPassword: '',
  jdbcDatabase: '',
})

const layerLabel = (layer: string) => {
  if (layer === 'EXTERNAL') return '外部数据源'
  return LAYER_OPTIONS.find(o => o.value === layer)?.label || layer
}

const groupedDataSources = computed(() => {
  const groups: { layer: string; label: string; items: DataSource[] }[] = []
  for (const layer of LAYER_GROUP_ORDER) {
    const items = dataSources.value.filter(ds => ds.layerHint === layer)
    if (items.length) {
      groups.push({ layer, label: layerLabel(layer), items })
    }
  }
  return groups
})

const dataSourceNameMap = computed(() => {
  const map = new Map<number, string>()
  for (const ds of dataSources.value) {
    map.set(ds.id, ds.sourceName)
  }
  return map
})

const modelNameMap = computed(() => {
  const map = new Map<number, string>()
  for (const m of models.value) {
    map.set(m.id, m.modelNameZh)
  }
  return map
})

const tableOptions = computed(() =>
  sourceTables.value.map(t => {
    const name = t.sourceTable || t.tableName || ''
    return { label: name, value: name }
  }).filter(o => o.value),
)

const editTableOptions = computed(() =>
  editSourceTables.value.map(t => {
    const name = t.sourceTable || t.tableName || ''
    return { label: name, value: name }
  }).filter(o => o.value),
)

function tableNameOf(t: SourceTable) {
  return t.sourceTable || t.tableName || ''
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

async function loadDataSources() {
  dataSources.value = (await api.get('/governance/platform/metadata/collect/data-sources')).data || []
}

async function loadModels() {
  models.value = (await api.get('/governance/platform/metadata/models', { params: { status: 'PUBLISHED' } })).data || []
}

async function loadTasks() {
  tasks.value = (await api.get('/governance/platform/metadata/collect/tasks')).data || []
  resetTaskPage()
}

async function loadSourceTables(sourceId: number) {
  tablesLoading.value = true
  sourceTables.value = []
  try {
    sourceTables.value = (await api.get(`/governance/platform/metadata/collect/data-sources/${sourceId}/tables`)).data || []
  } finally {
    tablesLoading.value = false
  }
}

watch(() => form.ingDataSourceId, async (id) => {
  selectedTableNames.value = []
  sourceTables.value = []
  if (!id) return
  await loadSourceTables(id)
})

watch(() => form.scopeType, (scope) => {
  if (scope === 'FULL') {
    selectedTableNames.value = []
  }
})

watch(cronEnabled, (on) => {
  if (!on) form.cronExpr = ''
})

async function createTask() {
  if (!form.taskName.trim()) {
    ElMessage.warning('请填写任务名称')
    return
  }
  if (!form.ingDataSourceId) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (form.scopeType === 'TABLE' && !selectedTableNames.value.length) {
    ElMessage.warning('请至少选择一张表')
    return
  }
  if (cronEnabled.value && !form.cronExpr.trim()) {
    ElMessage.warning('请选择执行周期')
    return
  }
  const cronExpr = cronEnabled.value ? form.cronExpr : null
  await api.post('/governance/platform/metadata/collect/tasks', {
    taskName: form.taskName.trim(),
    ingDataSourceId: form.ingDataSourceId,
    modelId: form.modelId || null,
    scopeType: form.scopeType,
    tableList: form.scopeType === 'TABLE' ? formatTableList(selectedTableNames.value) : null,
    cronExpr,
  })
  ElMessage.success('采集任务已创建')
  form.taskName = ''
  form.modelId = undefined
  form.scopeType = 'FULL'
  form.cronExpr = ''
  selectedTableNames.value = []
  cronEnabled.value = false
  await loadTasks()
}

async function runTask(id: number) {
  const res = await api.post(`/governance/platform/metadata/collect/tasks/${id}/run`)
  ElMessage.success(res.data?.message || '采集完成')
  await loadTasks()
}

function initEditCronFromExpr(expr?: string) {
  editCronEnabled.value = !!expr
  editForm.cronExpr = expr || ''
}

async function openEdit(row: Task) {
  editing.value = row
  editForm.taskName = row.taskName
  editForm.modelId = row.modelId
  editForm.scopeType = (row.scopeType === 'TABLE' ? 'TABLE' : 'FULL') as 'FULL' | 'TABLE'
  initEditCronFromExpr(row.cronExpr)
  const names = parseTableList(row.tableList)
  editSelectedTables.value = names
  editForm.tableList = row.tableList || ''
  editVisible.value = true
  editSourceTables.value = []
  if (row.ingDataSourceId && editForm.scopeType === 'TABLE') {
    editTablesLoading.value = true
    try {
      const tables: SourceTable[] = (await api.get(`/governance/platform/metadata/collect/data-sources/${row.ingDataSourceId}/tables`)).data || []
      editSourceTables.value = tables
      editSelectedTables.value = names.filter(n => tables.some(t => tableNameOf(t) === n))
    } finally {
      editTablesLoading.value = false
    }
  }
}

async function saveEdit() {
  if (!editing.value) return
  if (editCronEnabled.value && !editForm.cronExpr.trim()) {
    ElMessage.warning('请选择执行周期')
    return
  }
  const cronExpr = editCronEnabled.value ? editForm.cronExpr : null
  await api.put(`/governance/platform/metadata/collect/tasks/${editing.value.id}`, {
    taskName: editForm.taskName.trim(),
    modelId: editForm.modelId || null,
    scopeType: editForm.scopeType,
    tableList: editForm.scopeType === 'TABLE' ? formatTableList(editSelectedTables.value) : null,
    cronExpr,
  })
  ElMessage.success('任务已更新')
  editVisible.value = false
  await loadTasks()
}

async function removeTask(row: Task) {
  await ElMessageBox.confirm(`确认删除任务「${row.taskName}」？`, '删除确认')
  await api.delete(`/governance/platform/metadata/collect/tasks/${row.id}`)
  ElMessage.success('已删除')
  await loadTasks()
}

async function createConnector() {
  if (!connectorForm.connectorName) return
  await api.post('/governance/connectors', { ...connectorForm })
  ElMessage.success('适配器已创建')
  connectorForm.connectorName = ''
  connectorForm.jdbcUrl = ''
  connectorForm.jdbcUser = ''
  connectorForm.jdbcPassword = ''
  connectorForm.jdbcDatabase = ''
}

onMounted(async () => {
  await Promise.all([loadDataSources(), loadModels(), loadTasks()])
})
</script>

<template>
  <PageCard title="元数据采集">
    <el-form label-width="96px" class="meta-collect-form">
      <el-form-item label="任务名称" required>
        <el-input v-model="form.taskName" placeholder="采集任务名称" style="max-width:360px" />
      </el-form-item>
      <el-form-item label="数据源" required>
        <el-select
          v-model="form.ingDataSourceId"
          filterable
          clearable
          placeholder="选择登记数据源或平台分层库"
          style="max-width:480px"
        >
          <el-option-group v-for="g in groupedDataSources" :key="g.layer" :label="g.label">
            <el-option
              v-for="ds in g.items"
              :key="ds.id"
              :label="`${ds.sourceName}${ds.databaseName ? ' · ' + ds.databaseName : ''}`"
              :value="ds.id"
            />
          </el-option-group>
        </el-select>
      </el-form-item>
      <el-form-item label="采集范围">
        <el-radio-group v-model="form.scopeType">
          <el-radio value="FULL">整库</el-radio>
          <el-radio value="TABLE">选表</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.scopeType === 'TABLE'" label="选择表">
        <div v-loading="tablesLoading" style="width:100%;max-width:560px">
          <el-select
            v-model="selectedTableNames"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请先选择数据源"
            style="width:100%"
            :disabled="!form.ingDataSourceId"
          >
            <el-option v-for="o in tableOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <div v-if="form.ingDataSourceId && !tablesLoading && !tableOptions.length" class="meta-hint">
            未探测到可用表，请确认数据源连接正常
          </div>
        </div>
      </el-form-item>
      <el-form-item label="元模型">
        <el-select v-model="form.modelId" clearable filterable placeholder="可选，绑定已发布模型" style="max-width:360px">
          <el-option v-for="m in models" :key="m.id" :label="m.modelNameZh" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="定时调度">
        <el-switch v-model="cronEnabled" active-text="启用定时" inactive-text="手动执行" />
      </el-form-item>
      <el-form-item v-if="cronEnabled" label="执行周期">
        <ExecCycleSelect v-model="form.cronExpr" style="max-width:420px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="createTask">创建任务</el-button>
      </el-form-item>
    </el-form>

    <el-divider content-position="left">采集任务</el-divider>
    <el-table :data="pagedTasks" stripe size="small">
      <el-table-column prop="taskName" label="任务" min-width="140" />
      <el-table-column label="数据源" min-width="120">
        <template #default="{ row }">
          {{ row.ingDataSourceId ? (dataSourceNameMap.get(row.ingDataSourceId) || '—') : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="范围" width="80">
        <template #default="{ row }">{{ $statusLabel(row.scopeType) }}</template>
      </el-table-column>
      <el-table-column label="模型" min-width="100">
        <template #default="{ row }">
          {{ row.modelId ? (modelNameMap.get(row.modelId) || '—') : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="调度" width="130">
        <template #default="{ row }">{{ row.cronExpr || '手动' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastMessage" label="最近结果" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="runTask(row.id)">执行</el-button>
          <el-button link @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="removeTask(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination
      v-model:page="taskPage"
      v-model:page-size="taskPageSize"
      :total="taskTotal"
    />

    <el-collapse style="margin-top:20px">
      <el-collapse-item title="高级：适配器" name="adapter">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="适配器" class="portal-field-lg">
            <el-input v-model="connectorForm.connectorName" placeholder="名称" />
          </el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="connectorForm.sourceType">
              <el-option label="MySQL" value="MySQL" />
              <el-option label="PostgreSQL" value="PostgreSQL" />
            </el-select>
          </el-form-item>
          <el-form-item label="JDBC URL" class="portal-field-xl">
            <el-input v-model="connectorForm.jdbcUrl" placeholder="可选" />
          </el-form-item>
          <el-form-item label="库名" class="portal-field-sm">
            <el-input v-model="connectorForm.jdbcDatabase" />
          </el-form-item>
          <el-form-item label="用户" class="portal-field-sm">
            <el-input v-model="connectorForm.jdbcUser" />
          </el-form-item>
          <el-form-item label="密码" class="portal-field-sm">
            <el-input v-model="connectorForm.jdbcPassword" type="password" show-password />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="createConnector">新增适配器</el-button>
          </el-form-item>
        </el-form>
        <div class="meta-hint">常规采集请使用上方「登记数据源」；适配器仅用于特殊 JDBC 连接器场景。</div>
      </el-collapse-item>
    </el-collapse>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editVisible" title="编辑采集任务" width="560px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="任务名称">
          <el-input v-model="editForm.taskName" />
        </el-form-item>
        <el-form-item label="数据源">
          <el-input
            :model-value="editing?.ingDataSourceId ? (dataSourceNameMap.get(editing.ingDataSourceId) || '—') : '—'"
            disabled
          />
        </el-form-item>
        <el-form-item label="采集范围">
          <el-radio-group v-model="editForm.scopeType">
            <el-radio value="FULL">整库</el-radio>
            <el-radio value="TABLE">选表</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editForm.scopeType === 'TABLE'" label="选择表">
          <div v-loading="editTablesLoading" style="width:100%">
            <el-select
              v-model="editSelectedTables"
              multiple
              filterable
              collapse-tags
              style="width:100%"
            >
              <el-option v-for="o in editTableOptions" :key="'e' + o.value" :label="o.label" :value="o.value" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="元模型">
          <el-select v-model="editForm.modelId" clearable filterable style="width:100%">
            <el-option v-for="m in models" :key="m.id" :label="m.modelNameZh" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="定时调度">
          <el-switch v-model="editCronEnabled" active-text="启用" inactive-text="手动" />
        </el-form-item>
        <el-form-item v-if="editCronEnabled" label="执行周期">
          <ExecCycleSelect v-model="editForm.cronExpr" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.meta-collect-form {
  max-width: 720px;
}
.meta-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
}
</style>
