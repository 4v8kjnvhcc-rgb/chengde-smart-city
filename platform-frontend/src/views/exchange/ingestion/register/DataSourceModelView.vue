<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit } from '@element-plus/icons-vue'
import PageCard from '@/components/common/PageCard.vue'
import {
  activeProjectId,
  setActiveProjectId,
  syncActiveProject,
} from '../ingestion-project-scope'
import {
  ingestionApi,
  useIngestionLoading,
  type BizSystem,
  type DataColumn,
  type DataSource,
  type DataTable,
  type ProbeTable,
  type Project,
} from '../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const projects = ref<Project[]>([])
const bizSystems = ref<BizSystem[]>([])
const dataSources = ref<DataSource[]>([])
const tables = ref<DataTable[]>([])
const columns = ref<DataColumn[]>([])

const selectedProjectId = ref<number>()
const selectedSystemId = ref<number>()
const selectedSourceId = ref<number>()
const selectedTableId = ref<number>()

const selectedProject = computed(() => projects.value.find((p) => p.id === selectedProjectId.value) || null)
const selectedSystem = computed(() => bizSystems.value.find((s) => s.id === selectedSystemId.value) || null)
const selectedSource = computed(() => dataSources.value.find((s) => s.id === selectedSourceId.value) || null)
const selectedTable = computed(() => tables.value.find((t) => t.id === selectedTableId.value) || null)
const isSelectedForwardTable = computed(() => selectedTable.value?.modelingMode === 'FORWARD')
const projectSources = ref<DataSource[]>([])
function resetColumnForm() {
  columnForm.columnCode = ''
  columnForm.columnName = ''
  columnForm.dataType = 'VARCHAR'
  columnForm.lengthVal = 64
  columnForm.nullableFlag = 1
  columnForm.pkFlag = 0
  editingColumnId.value = null
}

function displayDataType(row: DataColumn) {
  const raw = (row.dataType || '—').replace(/\(\d+\)/, '')
  return raw || '—'
}

function displayLength(row: DataColumn) {
  if (row.lengthVal != null && row.lengthVal > 0) return String(row.lengthVal)
  const m = (row.dataType || '').match(/\((\d+)\)/)
  return m ? m[1] : '—'
}

/** —— 登记弹窗（原向导） —— */
const registerVisible = ref(false)
const FORWARD_DATA_TYPES = [
  'TINYINT', 'SMALLINT', 'MEDIUMINT', 'INT', 'BIGINT',
  'FLOAT', 'DOUBLE', 'DECIMAL',
  'CHAR', 'VARCHAR', 'TEXT', 'ENUM', 'SET', 'BLOB',
  'DATE', 'TIME', 'DATETIME', 'TIMESTAMP', 'YEAR', 'JSON',
] as const

const tableForm = reactive({ tableName: '', modelingMode: 'REVERSE' as 'FORWARD' | 'REVERSE' })
const columnForm = reactive({
  columnCode: '',
  columnName: '',
  dataType: 'VARCHAR',
  lengthVal: 64 as number | null,
  nullableFlag: 1,
  pkFlag: 0,
})
const editingColumnId = ref<number | null>(null)
const workflowStep = ref(0)
const probing = ref(false)
const registering = ref(false)
const savingTable = ref(false)
const savingColumn = ref(false)
const finalizing = ref(false)
const probeSchema = ref('')
const probeTables = ref<ProbeTable[]>([])
const selectedProbeTables = ref<string[]>([])
const regSourceId = ref<number>()
const regTables = ref<DataTable[]>([])
const regColumns = ref<DataColumn[]>([])
const regTableId = ref<number>()
const regTableName = ref('')

const regSource = computed(() => {
  const fromLocal = dataSources.value.find((s) => s.id === regSourceId.value)
  if (fromLocal) return fromLocal
  return projectSources.value.find((s) => s.id === regSourceId.value) || null
})
const isForward = computed(() => tableForm.modelingMode === 'FORWARD')
const isDbSource = computed(() => {
  const t = regSource.value?.sourceType
  return isDbSourceType(t)
})

function isDbSourceType(t?: string) {
  const u = String(t || '').toUpperCase()
  return ['MYSQL', 'ORACLE', 'POSTGRESQL', 'POSTGRES', 'CLICKHOUSE', 'HIVE', 'MONGODB'].includes(u)
}
const workflowSteps = computed(() => (isForward.value
  ? ['选择数据源', '定义物理表', '登记字段', '完成登记']
  : ['选择数据源', '扫描并勾选源表', '确认登记', '完成登记']))
const stepsActive = computed(() => {
  const last = workflowSteps.value.length - 1
  if (workflowStep.value >= last) return workflowSteps.value.length
  return workflowStep.value
})

watch(() => tableForm.modelingMode, () => {
  workflowStep.value = 0
  probeTables.value = []
  selectedProbeTables.value = []
  probeSchema.value = ''
})

async function loadProjects() {
  await withLoad(async () => {
    projects.value = (await ingestionApi.projects()).data || []
    syncActiveProject(projects.value)
    if (activeProjectId.value && projects.value.some((p) => p.id === activeProjectId.value)) {
      selectedProjectId.value = activeProjectId.value
    } else if (projects.value.length && !selectedProjectId.value) {
      selectedProjectId.value = projects.value[0].id
      setActiveProjectId(projects.value[0].id)
    }
  })
}

async function loadBizSystems() {
  if (!selectedProjectId.value) {
    bizSystems.value = []
    selectedSystemId.value = undefined
    dataSources.value = []
    selectedSourceId.value = undefined
    tables.value = []
    columns.value = []
    selectedTableId.value = undefined
    return
  }
  bizSystems.value = (await ingestionApi.systems(selectedProjectId.value)).data || []
  if (selectedSystemId.value && !bizSystems.value.some((s) => s.id === selectedSystemId.value)) {
    selectedSystemId.value = undefined
  }
  if (!selectedSystemId.value && bizSystems.value.length) {
    selectedSystemId.value = bizSystems.value[0].id
  }
  await loadDataSources()
}

async function loadDataSources() {
  if (!selectedSystemId.value) {
    dataSources.value = []
    selectedSourceId.value = undefined
    tables.value = []
    columns.value = []
    selectedTableId.value = undefined
    return
  }
  dataSources.value = (await ingestionApi.dataSources(undefined, selectedSystemId.value)).data || []
  if (selectedSourceId.value && !dataSources.value.some((s) => s.id === selectedSourceId.value)) {
    selectedSourceId.value = undefined
  }
  if (!selectedSourceId.value && dataSources.value.length) {
    selectedSourceId.value = dataSources.value[0].id
  }
  await loadTables()
}

async function loadTables(preferTableId?: number) {
  if (!selectedSourceId.value) {
    tables.value = []
    columns.value = []
    selectedTableId.value = undefined
    return
  }
  tables.value = (await ingestionApi.tables(selectedSourceId.value)).data || []
  if (preferTableId && tables.value.some((t) => t.id === preferTableId)) {
    selectedTableId.value = preferTableId
  } else if (!selectedTableId.value || !tables.value.some((t) => t.id === selectedTableId.value)) {
    selectedTableId.value = tables.value[0]?.id
  }
  await loadColumns()
}

async function loadColumns() {
  if (!selectedTableId.value) {
    columns.value = []
    return
  }
  columns.value = (await ingestionApi.columns(selectedTableId.value)).data || []
}

function selectProject(row: Project) {
  selectedProjectId.value = row.id
  setActiveProjectId(row.id)
  selectedSystemId.value = undefined
  selectedSourceId.value = undefined
  selectedTableId.value = undefined
  void loadBizSystems()
}

function selectSystem(row: BizSystem) {
  selectedSystemId.value = row.id
  selectedSourceId.value = undefined
  selectedTableId.value = undefined
  void loadDataSources()
}

function selectSource(row: DataSource) {
  selectedSourceId.value = row.id
  selectedTableId.value = undefined
  void loadTables()
}

function selectTable(row: DataTable) {
  selectedTableId.value = row.id
  void loadColumns()
}

async function editSelectedTable() {
  const t = selectedTable.value
  if (!t || t.modelingMode !== 'FORWARD') return
  try {
    const { value } = await ElMessageBox.prompt('请输入新的表名', '编辑表', {
      inputValue: t.tableName,
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValidator: (v) => (!!v?.trim() ? true : '表名不能为空'),
    })
    const name = String(value || '').trim()
    await ingestionApi.updateTable(t.id, { tableName: name })
    ElMessage.success('表已更新')
    await loadTables(t.id)
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '更新表失败')
  }
}

async function deleteSelectedTable() {
  const t = selectedTable.value
  if (!t || t.modelingMode !== 'FORWARD') return
  try {
    await ElMessageBox.confirm(`确认删除正向建模表「${t.tableName}」及其字段？`, '删除确认', { type: 'warning' })
    await ingestionApi.deleteTable(t.id)
    ElMessage.success('表已删除')
    selectedTableId.value = undefined
    await loadTables()
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除表失败')
  }
}

watch(selectedProjectId, (id) => {
  if (id) setActiveProjectId(id)
})

function openRegister() {
  if (!selectedProjectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  void (async () => {
    projectSources.value = (await ingestionApi.dataSources(selectedProjectId.value)).data || []
    if (!projectSources.value.length) {
      ElMessage.warning('当前项目暂无数据源，请先到「项目/系统信息登记」创建系统与数据源并测试连接')
      return
    }
    workflowStep.value = 0
    tableForm.modelingMode = 'REVERSE'
    tableForm.tableName = ''
    probeTables.value = []
    selectedProbeTables.value = []
    probeSchema.value = ''
    regSourceId.value = selectedSourceId.value || projectSources.value[0]?.id
    regTableId.value = undefined
    regTableName.value = ''
    regTables.value = []
    regColumns.value = []
    resetColumnForm()
    registerVisible.value = true
  })()
}

function goNextFromSource() {
  if (!regSourceId.value) {
    ElMessage.warning('请先选择数据源')
    return
  }
  if (!isForward.value && !isDbSource.value) {
    ElMessage.warning('逆向扫描仅支持 MySQL/Oracle 数据源；文件/API 请用正向建模')
    return
  }
  if (!isForward.value && regSource.value?.connStatus !== 'OK') {
    ElMessage.warning('请先在「项目/系统信息登记」中对该数据源测试连接成功后再扫描')
    return
  }
  workflowStep.value = 1
}

async function createTable() {
  if (!tableForm.tableName.trim() || !regSourceId.value) {
    ElMessage.warning('请填写表名并选择数据源')
    return
  }
  savingTable.value = true
  try {
    const name = tableForm.tableName.trim()
    const res = await ingestionApi.createTable({
      sourceId: regSourceId.value,
      tableName: name,
      modelingMode: tableForm.modelingMode,
    })
    const newId = Number(res.data)
    if (!newId) {
      ElMessage.error('表登记未返回有效 ID')
      return
    }
    tableForm.tableName = ''
    regTableName.value = name
    regTableId.value = newId
    try {
      regTables.value = (await ingestionApi.tables(regSourceId.value)).data || []
    } catch {
      regTables.value = [{ id: newId, tableName: name, modelingMode: 'FORWARD', columnCount: 0 } as DataTable]
    }
    if (!regTables.value.some((t) => t.id === newId)) {
      regTables.value = [
        ...regTables.value,
        { id: newId, tableName: name, modelingMode: 'FORWARD', columnCount: 0 } as DataTable,
      ]
    }
    regColumns.value = (await ingestionApi.columns(newId)).data || []
    workflowStep.value = 2
    ElMessage.success('表已写入平台登记库，请继续登记字段')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '表登记失败')
  } finally {
    savingTable.value = false
  }
}

async function createColumn() {
  if (!regTableId.value) {
    ElMessage.warning('请先登记或选择一张表')
    return
  }
  if (!columnForm.columnCode.trim() || !columnForm.columnName.trim()) {
    ElMessage.warning('请填写字段编码与字段名称')
    return
  }
  savingColumn.value = true
  try {
    const payload = {
      columnCode: columnForm.columnCode.trim(),
      columnName: columnForm.columnName.trim(),
      dataType: columnForm.dataType,
      lengthVal: columnForm.lengthVal ?? 64,
      nullableFlag: columnForm.nullableFlag,
      pkFlag: columnForm.pkFlag,
    }
    if (editingColumnId.value) {
      await ingestionApi.updateColumn(editingColumnId.value, payload)
      ElMessage.success('字段已更新')
    } else {
      await ingestionApi.createColumn(regTableId.value, payload)
      ElMessage.success('字段已添加')
    }
    resetColumnForm()
    regColumns.value = (await ingestionApi.columns(regTableId.value)).data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : (editingColumnId.value ? '更新字段失败' : '添加字段失败'))
  } finally {
    savingColumn.value = false
  }
}

function startEditRegColumn(row: DataColumn) {
  editingColumnId.value = row.id
  columnForm.columnCode = row.columnCode
  columnForm.columnName = row.columnName || ''
  columnForm.dataType = displayDataType(row) === '—' ? 'VARCHAR' : displayDataType(row)
  columnForm.lengthVal = row.lengthVal ?? (Number(displayLength(row)) || 64)
  columnForm.nullableFlag = row.nullableFlag ?? 1
  columnForm.pkFlag = row.pkFlag ?? 0
}

async function deleteRegColumn(row: DataColumn) {
  try {
    await ElMessageBox.confirm(`确认删除字段「${row.columnCode}」？`, '删除确认', { type: 'warning' })
    await ingestionApi.deleteColumn(row.id)
    if (editingColumnId.value === row.id) resetColumnForm()
    if (regTableId.value) {
      regColumns.value = (await ingestionApi.columns(regTableId.value)).data || []
    }
    ElMessage.success('字段已删除')
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除字段失败')
  }
}

const overviewColumnDialog = ref(false)
const overviewColumnSaving = ref(false)
const overviewColumnForm = reactive({
  id: 0,
  columnCode: '',
  columnName: '',
  dataType: 'VARCHAR',
  lengthVal: 64 as number | null,
  nullableFlag: 1,
  pkFlag: 0,
})
function startEditOverviewColumn(row: DataColumn) {
  if (!isSelectedForwardTable.value) return
  overviewColumnForm.id = row.id
  overviewColumnForm.columnCode = row.columnCode
  overviewColumnForm.columnName = row.columnName || ''
  overviewColumnForm.dataType = displayDataType(row) === '—' ? 'VARCHAR' : displayDataType(row)
  overviewColumnForm.lengthVal = row.lengthVal ?? (Number(displayLength(row)) || 64)
  overviewColumnForm.nullableFlag = row.nullableFlag ?? 1
  overviewColumnForm.pkFlag = row.pkFlag ?? 0
  overviewColumnDialog.value = true
}

async function saveOverviewColumn() {
  if (!overviewColumnForm.columnCode.trim() || !overviewColumnForm.columnName.trim()) {
    ElMessage.warning('请填写字段编码与字段名称')
    return
  }
  overviewColumnSaving.value = true
  try {
    await ingestionApi.updateColumn(overviewColumnForm.id, {
      columnCode: overviewColumnForm.columnCode.trim(),
      columnName: overviewColumnForm.columnName.trim(),
      dataType: overviewColumnForm.dataType,
      lengthVal: overviewColumnForm.lengthVal ?? 64,
      nullableFlag: overviewColumnForm.nullableFlag,
      pkFlag: overviewColumnForm.pkFlag,
    })
    ElMessage.success('字段已更新')
    overviewColumnDialog.value = false
    await loadColumns()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '更新字段失败')
  } finally {
    overviewColumnSaving.value = false
  }
}

async function deleteOverviewColumn(row: DataColumn) {
  if (!isSelectedForwardTable.value) return
  try {
    await ElMessageBox.confirm(`确认删除字段「${row.columnCode}」？`, '删除确认', { type: 'warning' })
    await ingestionApi.deleteColumn(row.id)
    ElMessage.success('字段已删除')
    await loadColumns()
    if (selectedSourceId.value) await loadTables(selectedTableId.value)
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除字段失败')
  }
}

async function realProbe() {
  if (!regSourceId.value) return
  probing.value = true
  probeTables.value = []
  selectedProbeTables.value = []
  try {
    const res = await ingestionApi.probeDataSource(regSourceId.value)
    probeSchema.value = res.data.schema
    probeTables.value = res.data.tables || []
    if (!probeTables.value.length) {
      ElMessage.warning('源库未探测到表，请确认连接库名与账号权限')
    } else {
      ElMessage.success(`已扫描到 ${probeTables.value.length} 张表（库：${probeSchema.value || '-'}）`)
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '扫描失败，请先测试数据源连接')
  } finally {
    probing.value = false
  }
}

async function registerProbed() {
  if (!regSourceId.value || !selectedProbeTables.value.length) {
    ElMessage.warning('请勾选要登记的源表')
    return
  }
  registering.value = true
  try {
    const tablesBody = selectedProbeTables.value.map((name) => ({ sourceTable: name }))
    const res = await ingestionApi.registerTables(regSourceId.value, { tables: tablesBody })
    const n = ((res.data.registered as unknown[]) || []).length
    ElMessage.success(`已登记 ${n} 张源表`)
    regTables.value = (await ingestionApi.tables(regSourceId.value)).data || []
    workflowStep.value = 2
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '登记失败')
  } finally {
    registering.value = false
  }
}

async function finishRegister() {
  if (isForward.value) {
    if (!regTableId.value) {
      ElMessage.warning('请先完成表登记')
      return
    }
    if (!regColumns.value.length) {
      ElMessage.warning('请至少登记一个字段后再完成登记')
      return
    }
    finalizing.value = true
    try {
      const res = await ingestionApi.finalizeForwardTable(regTableId.value)
      const msg = String(res.data?.message || '正向建模已完成平台登记').replace(/[（(][^）)]*[）)]/g, '').trim()
      ElMessage.success(msg || '正向建模已完成平台登记')
      workflowStep.value = 3
      selectedSourceId.value = regSourceId.value
      await loadTables(regTableId.value)
    } catch (e: unknown) {
      ElMessage.error(e instanceof Error ? e.message : '完成登记失败')
    } finally {
      finalizing.value = false
    }
    return
  }
  workflowStep.value = 3
  selectedSourceId.value = regSourceId.value
  await loadTables()
}

async function closeRegister() {
  registerVisible.value = false
  await loadBizSystems()
}

onMounted(async () => {
  await loadProjects()
  await loadBizSystems()
})
</script>

<template>
  <div v-loading="loading" class="model-page">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />

    <PageCard>
      <template #header>
        <div class="card-head">
          <div class="card-head__text">
            <div class="card-title">数据库 / 表 / 项总览</div>
            <div class="card-sub">从左到右选择项目与数据源，右侧查看已登记表与字段</div>
          </div>
          <el-button type="primary" :disabled="!selectedProjectId" @click="openRegister">登记</el-button>
        </div>
      </template>

      <div class="path-bar" v-if="selectedProject">
        <span class="path-chip">{{ selectedProject.projectName }}</span>
        <template v-if="selectedSystem">
          <span class="path-sep">/</span>
          <span class="path-chip path-chip--muted">{{ selectedSystem.systemName }}</span>
        </template>
        <template v-if="selectedSource">
          <span class="path-sep">/</span>
          <span class="path-chip">{{ selectedSource.sourceName }}</span>
        </template>
        <template v-if="selectedTableId">
          <span class="path-sep">/</span>
          <span class="path-chip path-chip--accent">{{ tables.find((t) => t.id === selectedTableId)?.tableName }}</span>
        </template>
      </div>

      <div class="overview-shell">
        <aside class="cascade">
          <section class="lane">
            <header class="lane-head">
              <span>项目</span>
              <em>{{ projects.length }}</em>
            </header>
            <div class="lane-body">
              <button
                v-for="p in projects"
                :key="p.id"
                type="button"
                class="lane-item"
                :class="{ 'is-active': p.id === selectedProjectId }"
                @click="selectProject(p)"
              >
                <span class="lane-item__name">{{ p.projectName }}</span>
                <span class="lane-item__meta">{{ p.boundOrgName || '—' }}</span>
              </button>
              <div v-if="!projects.length" class="lane-empty">暂无项目</div>
            </div>
          </section>

          <section class="lane">
            <header class="lane-head">
              <span>系统</span>
              <em>{{ bizSystems.length }}</em>
            </header>
            <div class="lane-body">
              <button
                v-for="s in bizSystems"
                :key="'sys-' + s.id"
                type="button"
                class="lane-item"
                :class="{ 'is-active': s.id === selectedSystemId }"
                @click="selectSystem(s)"
              >
                <span class="lane-item__name">{{ s.systemName }}</span>
                <span class="lane-item__meta">数据源 {{ s.dataSourceCount ?? 0 }}</span>
              </button>
              <div v-if="selectedProjectId && !bizSystems.length" class="lane-empty">暂无系统，请在项目详情中新增</div>
              <div v-else-if="!selectedProjectId" class="lane-empty">请选择项目</div>
            </div>
          </section>

          <section class="lane">
            <header class="lane-head">
              <span>数据源</span>
              <em>{{ dataSources.length }}</em>
            </header>
            <div class="lane-body">
              <button
                v-for="s in dataSources"
                :key="s.id"
                type="button"
                class="lane-item"
                :class="{ 'is-active': s.id === selectedSourceId }"
                @click="selectSource(s)"
              >
                <span class="lane-item__name">{{ s.sourceName || '未命名' }}</span>
                <span class="lane-item__meta">
                  <el-tag size="small" effect="plain" type="info">{{ $statusLabel(s.sourceType) }}</el-tag>
                  <el-tag
                    v-if="isDbSourceType(s.sourceType)"
                    size="small"
                    effect="plain"
                    :type="s.connStatus === 'OK' ? 'success' : s.connStatus === 'FAILED' ? 'danger' : 'warning'"
                  >
                    {{ s.connStatus === 'OK' ? '已通' : s.connStatus === 'FAILED' ? '失败' : '未测' }}
                  </el-tag>
                </span>
              </button>
              <div v-if="selectedSystemId && !dataSources.length" class="lane-empty">暂无数据源</div>
              <div v-else-if="!selectedSystemId" class="lane-empty">请选择系统</div>
            </div>
          </section>

          <section class="lane">
            <header class="lane-head">
              <span>表</span>
              <em>{{ tables.length }}</em>
              <span v-if="isSelectedForwardTable" class="lane-head__actions">
                <el-button link type="primary" :icon="Edit" title="编辑表" @click.stop="editSelectedTable" />
                <el-button link type="danger" :icon="Delete" title="删除表" @click.stop="deleteSelectedTable" />
              </span>
            </header>
            <div class="lane-body">
              <button
                v-for="t in tables"
                :key="t.id"
                type="button"
                class="lane-item"
                :class="{ 'is-active': t.id === selectedTableId }"
                @click="selectTable(t)"
              >
                <span class="lane-item__name">{{ t.tableName }}</span>
                <span class="lane-item__meta">
                  {{ t.modelingMode === 'REVERSE' ? '逆向' : '正向' }} · {{ t.columnCount ?? 0 }} 字段
                </span>
              </button>
              <div v-if="selectedSourceId && !tables.length" class="lane-empty">暂无登记表</div>
              <div v-else-if="!selectedSourceId" class="lane-empty">请选择数据源</div>
            </div>
          </section>
        </aside>

        <section class="detail">
          <header class="detail-head">
            <div>
              <div class="detail-title">字段详情</div>
              <div class="detail-sub">
                <template v-if="selectedTableId">
                  共 {{ columns.length }} 个字段
                </template>
                <template v-else>选择左侧表后在此查看字段结构</template>
              </div>
            </div>
          </header>
          <div class="detail-body">
            <el-table
              v-if="columns.length"
              :data="columns"
              stripe
              size="small"
              height="100%"
              class="detail-table"
            >
              <el-table-column prop="columnCode" label="字段编码" min-width="120" show-overflow-tooltip />
              <el-table-column prop="columnName" label="字段名称" min-width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.columnName || '—' }}</template>
              </el-table-column>
              <el-table-column label="类型" width="100" show-overflow-tooltip>
                <template #default="{ row }">{{ displayDataType(row) }}</template>
              </el-table-column>
              <el-table-column label="长度" width="72" align="center">
                <template #default="{ row }">{{ displayLength(row) }}</template>
              </el-table-column>
              <el-table-column label="是否为空" width="88" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.nullableFlag ? 'info' : 'warning'" effect="plain">
                    {{ row.nullableFlag ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="是否主键" width="88" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.pkFlag ? 'success' : 'info'" effect="plain">
                    {{ row.pkFlag ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column v-if="isSelectedForwardTable" label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="startEditOverviewColumn(row)">编辑</el-button>
                  <el-button link type="danger" @click="deleteOverviewColumn(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="detail-empty">
              <p>{{ selectedTableId ? '该表暂无字段' : '从左侧依次选择项目 → 数据源 → 表' }}</p>
            </div>
          </div>
        </section>
      </div>
    </PageCard>

    <el-dialog
      v-model="registerVisible"
      title="登记数据库 / 表 / 项"
      width="860px"
      destroy-on-close
      @closed="closeRegister"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        :title="`当前项目：${selectedProject?.projectName || '—'} / ${selectedSystem?.systemName || '—'}`"
      />
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="建模方式">
          <el-radio-group v-model="tableForm.modelingMode">
            <el-radio-button value="FORWARD">正向（业务需求先建模）</el-radio-button>
            <el-radio-button value="REVERSE">逆向（从已有库导入）</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-steps :active="stepsActive" finish-status="success" process-status="process" simple style="margin-bottom:16px">
        <el-step v-for="s in workflowSteps" :key="s" :title="s" />
      </el-steps>

      <div v-show="workflowStep === 0">
        <el-form inline class="portal-inline-form">
          <el-form-item label="数据源" class="portal-field-xl">
            <el-select v-model="regSourceId" placeholder="请选择">
              <el-option
                v-for="s in projectSources"
                :key="s.id"
                :label="`${s.systemName || '系统'} / ${s.sourceName || '未命名'}（${$statusLabel(s.sourceType)}${s.connStatus ? ' · ' + $statusLabel(s.connStatus) : ''}）`"
                :value="s.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :disabled="!regSourceId" @click="goNextFromSource">下一步</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-show="workflowStep === 1 && isForward">
        <el-form inline class="portal-inline-form">
          <el-form-item label="表名" class="portal-field-lg">
            <el-select
              v-model="tableForm.tableName"
              filterable
              allow-create
              default-first-option
              clearable
              placeholder="输入表名筛选，或新建物理表名"
              style="width: 100%"
            >
              <el-option
                v-for="t in tables"
                :key="t.id"
                :label="t.physicalTableName || t.tableName"
                :value="t.physicalTableName || t.tableName"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="workflowStep = 0">上一步</el-button>
            <el-button type="primary" :loading="savingTable" :disabled="!tableForm.tableName.trim()" @click="createTable">
              登记表并继续
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-show="workflowStep === 1 && !isForward" v-loading="probing">
        <p class="step-hint">对数据源发起真实 JDBC 探库，勾选后登记到平台（含字段结构）。</p>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button @click="workflowStep = 0">上一步</el-button>
            <el-button type="primary" :loading="probing" @click="realProbe">扫描数据库</el-button>
            <el-button
              type="success"
              :loading="registering"
              :disabled="!selectedProbeTables.length"
              @click="registerProbed"
            >
              登记勾选表
            </el-button>
          </el-form-item>
        </el-form>
        <p v-if="probeSchema" class="scan-result">源库：{{ probeSchema }}，共 {{ probeTables.length }} 张表</p>
        <el-table
          v-if="probeTables.length"
          :data="probeTables"
          size="small"
          max-height="280"
          style="margin-top:8px"
          @selection-change="(rows: ProbeTable[]) => selectedProbeTables = rows.map((r) => r.sourceTable)"
        >
          <el-table-column type="selection" width="46" />
          <el-table-column prop="sourceTable" label="源表" min-width="160" />
          <el-table-column label="列数" width="70">
            <template #default="{ row }">{{ row.columns?.length ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="主键" min-width="120">
            <template #default="{ row }">{{ (row.primaryKeys || []).join(', ') || '—' }}</template>
          </el-table-column>
        </el-table>
        <el-form v-if="regTables.length" inline class="portal-inline-form" style="margin-top:12px">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="workflowStep = 2">下一步（确认登记）</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-show="workflowStep === 2 && isForward">
        <p class="step-hint">当前表：{{ regTableName || regTables.find((t) => t.id === regTableId)?.tableName || '未选择' }}</p>
        <el-form inline size="small" class="portal-inline-form portal-inline-form--sm portal-inline-form--block">
          <el-form-item label="字段编码" class="portal-field-xs">
            <el-input v-model="columnForm.columnCode" placeholder="如 user_id" />
          </el-form-item>
          <el-form-item label="字段名称" class="portal-field-xs">
            <el-input v-model="columnForm.columnName" />
          </el-form-item>
          <el-form-item label="类型" class="portal-field-xs">
            <el-select v-model="columnForm.dataType" filterable style="width:120px">
              <el-option v-for="t in FORWARD_DATA_TYPES" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <el-form-item label="长度" class="portal-field-xs">
            <el-input-number v-model="columnForm.lengthVal" :min="1" :max="65535" controls-position="right" />
          </el-form-item>
          <el-form-item label="是否为空" class="portal-field-xs">
            <el-select v-model="columnForm.nullableFlag" style="width:88px">
              <el-option :value="1" label="是" />
              <el-option :value="0" label="否" />
            </el-select>
          </el-form-item>
          <el-form-item label="是否主键" class="portal-field-xs">
            <el-select v-model="columnForm.pkFlag" style="width:88px">
              <el-option :value="0" label="否" />
              <el-option :value="1" label="是" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" size="small" :loading="savingColumn" @click="createColumn">
              {{ editingColumnId ? '保存字段' : '添加字段' }}
            </el-button>
            <el-button v-if="editingColumnId" size="small" @click="resetColumnForm">取消编辑</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="regColumns" stripe size="small" max-height="220" style="margin:8px 0">
          <el-table-column prop="columnCode" label="字段编码" min-width="100" />
          <el-table-column prop="columnName" label="字段名称" min-width="100" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ displayDataType(row) }}</template>
          </el-table-column>
          <el-table-column label="长度" width="70" align="center">
            <template #default="{ row }">{{ displayLength(row) }}</template>
          </el-table-column>
          <el-table-column label="是否为空" width="88" align="center">
            <template #default="{ row }">{{ row.nullableFlag ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="是否主键" width="88" align="center">
            <template #default="{ row }">{{ row.pkFlag ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="startEditRegColumn(row)">编辑</el-button>
              <el-button link type="danger" @click="deleteRegColumn(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-form inline class="portal-inline-form">
          <el-form-item class="portal-form-actions">
            <el-button @click="workflowStep = 1">上一步</el-button>
            <el-button type="success" :loading="finalizing" :disabled="!regColumns.length" @click="finishRegister">
              完成登记
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-show="workflowStep === 2 && !isForward">
        <p class="step-hint">请核对已登记的表结构，确认无误后完成登记。</p>
        <el-table :data="regTables" stripe size="small" max-height="220" style="margin-bottom:8px">
          <el-table-column prop="tableName" label="表名" />
          <el-table-column prop="columnCount" label="字段数" width="80" />
          <el-table-column label="建模" width="70">
            <template #default="{ row }">{{ row.modelingMode === 'REVERSE' ? '逆向' : '正向' }}</template>
          </el-table-column>
        </el-table>
        <el-form inline class="portal-inline-form">
          <el-form-item class="portal-form-actions">
            <el-button @click="workflowStep = 1">上一步</el-button>
            <el-button type="success" @click="finishRegister">完成登记</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-show="workflowStep === 3">
        <el-alert
          :title="isForward ? '正向建模已完成平台登记' : '登记完成，可关闭窗口查看总览。'"
          type="success"
          :closable="false"
        />
        <el-button style="margin-top:12px" type="primary" @click="closeRegister">关闭并刷新总览</el-button>
      </div>
    </el-dialog>

    <el-dialog v-model="overviewColumnDialog" title="编辑字段" width="520px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="字段编码" required>
          <el-input v-model="overviewColumnForm.columnCode" />
        </el-form-item>
        <el-form-item label="字段名称" required>
          <el-input v-model="overviewColumnForm.columnName" />
        </el-form-item>
        <el-form-item label="类型" required>
          <el-select v-model="overviewColumnForm.dataType" filterable style="width:100%">
            <el-option v-for="t in FORWARD_DATA_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="长度">
          <el-input-number v-model="overviewColumnForm.lengthVal" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="是否为空">
          <el-radio-group v-model="overviewColumnForm.nullableFlag">
            <el-radio :value="1">是</el-radio>
            <el-radio :value="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="是否主键">
          <el-radio-group v-model="overviewColumnForm.pkFlag">
            <el-radio :value="0">否</el-radio>
            <el-radio :value="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="overviewColumnDialog = false">取消</el-button>
        <el-button type="primary" :loading="overviewColumnSaving" @click="saveOverviewColumn">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.model-page {
  min-height: 100%;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.card-head__text {
  min-width: 0;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--portal-text);
  letter-spacing: 0.02em;
}
.card-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--portal-text-secondary);
}

.path-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: linear-gradient(90deg, #f5f9ff 0%, #f8fafc 100%);
  border: 1px solid #e8eef7;
}
.path-chip {
  font-size: 13px;
  font-weight: 500;
  color: var(--portal-text);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.path-chip--muted {
  color: var(--portal-text-secondary);
  font-weight: 400;
}
.path-chip--accent {
  color: var(--portal-primary);
}
.path-sep {
  color: #c0c4cc;
  font-size: 12px;
}

.overview-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.95fr);
  gap: 12px;
  height: calc(100vh - 260px);
  min-height: 420px;
  max-height: 640px;
}

.cascade {
  display: grid;
  grid-template-columns: 1.15fr 0.72fr 1.2fr 1.2fr;
  gap: 0;
  height: 100%;
  min-height: 0;
  border: 1px solid var(--portal-border);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.lane {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--portal-border);
  background: #fcfcfd;
}
.lane:last-child {
  border-right: none;
}
.lane-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 40px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #4b5563;
  letter-spacing: 0.04em;
  background: #f3f6fb;
  border-bottom: 1px solid var(--portal-border);
}
.lane-head__actions {
  display: inline-flex;
  align-items: center;
  margin-left: auto;
  gap: 0;
}
.lane-head em {
  font-style: normal;
  min-width: 20px;
  height: 18px;
  padding: 0 6px;
  border-radius: 999px;
  background: #e8eef8;
  color: #3b6fb6;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}
.lane-body {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 8px;
}
.lane-item,
.system-card {
  box-sizing: border-box;
  width: 100%;
  height: 56px;
  padding: 8px 10px;
  margin-bottom: 6px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.lane-item {
  appearance: none;
  text-align: left;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: background 140ms ease, border-color 140ms ease, box-shadow 140ms ease;
}
.lane-item:hover {
  background: #f5f8fd;
  border-color: #dbe7f7;
}
.lane-item.is-active {
  background: #eef5ff;
  border-color: #bcd4f8;
  box-shadow: inset 3px 0 0 var(--portal-primary);
}
.lane-item__name,
.system-card__value {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--portal-text);
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lane-item__meta,
.system-card__code {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  height: 18px;
  font-size: 11px;
  color: var(--portal-text-secondary);
  line-height: 18px;
  overflow: hidden;
}
.lane-item__meta :deep(.el-tag) {
  height: 18px;
  padding: 0 5px;
  line-height: 16px;
  flex-shrink: 0;
}
.lane-empty {
  padding: 28px 8px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}

.system-card {
  margin: 0;
  background: #eef5ff;
  border: 1px solid #bcd4f8;
  box-shadow: inset 3px 0 0 var(--portal-primary);
}
.system-card__code {
  color: #9aa3b2;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.detail {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100%;
  border: 1px solid var(--portal-border);
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}
.detail-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--portal-border);
  background: #f7f9fc;
}
.detail-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--portal-text);
}
.detail-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--portal-text-secondary);
}
.detail-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.detail-table {
  flex: 1;
  min-height: 0;
  --el-table-header-bg-color: #f7f9fc;
}
.detail-table :deep(.el-table__inner-wrapper),
.detail-table :deep(.el-scrollbar__wrap) {
  max-height: 100%;
}
.detail-table :deep(th.el-table__cell) {
  color: #4b5563;
  font-weight: 600;
}
.detail-empty {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 30% 20%, rgba(22, 119, 255, 0.06), transparent 40%),
    radial-gradient(circle at 80% 70%, rgba(22, 119, 255, 0.04), transparent 35%);
}
.detail-empty p {
  margin: 0;
  font-size: 13px;
  color: #9aa3b2;
}

.step-hint { font-size: 13px; color: #606266; margin: 0 0 8px; }
.scan-result { margin-top: 8px; font-size: 13px; color: #409eff; }

@media (max-width: 1280px) {
  .overview-shell {
    grid-template-columns: 1fr;
    height: auto;
    max-height: none;
  }
  .cascade {
    grid-template-columns: 1fr 0.7fr 1fr 1fr;
    height: 360px;
  }
  .detail {
    height: 420px;
  }
}
</style>
