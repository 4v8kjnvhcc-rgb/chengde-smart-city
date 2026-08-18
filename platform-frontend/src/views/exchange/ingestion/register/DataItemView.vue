<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { formatDateTime } from '@/utils/datetime'
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
  type Project,
} from '../useIngestionHub'

type EditCtrlKey =
  | 'columnCode'
  | 'columnName'
  | 'dataType'
  | 'lengthVal'
  | 'componentType'
  | 'nullableFlag'

function defaultEditCtrl(): Record<EditCtrlKey, boolean> {
  return {
    columnCode: true,
    columnName: true,
    dataType: true,
    lengthVal: true,
    componentType: true,
    nullableFlag: true,
  }
}

const route = useRoute()
const router = useRouter()
const { loading, loadError, withLoad } = useIngestionLoading()
const projects = ref<Project[]>([])
const systems = ref<BizSystem[]>([])
const sources = ref<DataSource[]>([])
const tables = ref<DataTable[]>([])
const columns = ref<DataColumn[]>([])
const selectedProjectId = ref<number>()
const selectedSystemId = ref<number>()
const selectedSourceId = ref<number>()
const selectedTableId = ref<number>()
const dialogVisible = ref(false)
const editingCol = ref<DataColumn | null>(null)
const saving = ref(false)
const colForm = reactive({
  columnCode: '',
  columnName: '',
  dataType: 'VARCHAR',
  lengthVal: 64,
  nullableFlag: 1,
  componentType: 'INPUT',
  requiredTip: '',
})

const editCtrl = reactive(defaultEditCtrl())

const componentOptions = [
  { value: 'INPUT', label: '文本输入' },
  { value: 'SELECT', label: '下拉选择' },
  { value: 'DATE', label: '日期' },
  { value: 'NUMBER', label: '数字' },
  { value: 'TEXTAREA', label: '多行文本' },
]

const DATA_TYPE_OPTIONS = [
  'TINYINT', 'SMALLINT', 'MEDIUMINT', 'INT', 'BIGINT',
  'FLOAT', 'DOUBLE', 'DECIMAL',
  'CHAR', 'VARCHAR', 'TEXT', 'ENUM', 'SET', 'BLOB',
  'DATE', 'TIME', 'DATETIME', 'TIMESTAMP', 'YEAR', 'JSON', 'BOOLEAN',
] as const

const LENGTH_TYPES = new Set(['CHAR', 'VARCHAR', 'DECIMAL', 'BINARY', 'VARBINARY'])

type CompareDiff = {
  columnCode: string
  leftName: string
  rightName: string
  leftType: string
  rightType: string
  leftLen: string
  rightLen: string
  leftNullable: string
  rightNullable: string
  diffType: string
}

const compareVisible = ref(false)
const compareLoading = ref(false)
const compareRightTableId = ref<number>()
const compareRightTables = ref<DataTable[]>([])
const compareDiffs = ref<CompareDiff[]>([])
const compareSummary = reactive({ leftCount: 0, rightCount: 0, same: 0, diff: 0, onlyLeft: 0, onlyRight: 0 })
const compareRanAt = ref('')

const dialogTitle = computed(() => (editingCol.value ? '编辑数据项' : '新建数据项'))
const isCreate = computed(() => !editingCol.value)

/** 新建时全部可填；编辑时按「内置属性管理」全局配置禁用 */
function canEditAttr(key: EditCtrlKey) {
  if (isCreate.value) return true
  return editCtrl[key] !== false
}

function componentLabel(code?: string) {
  return componentOptions.find((c) => c.value === code)?.label || code || '—'
}

function dataTypeLabel(row: DataColumn) {
  return (row.dataType || '—').replace(/\(\d+\)/, '') || '—'
}

function lengthLabel(row: DataColumn) {
  if (row.lengthVal != null && row.lengthVal > 0) return String(row.lengthVal)
  const m = (row.dataType || '').match(/\((\d+)\)/)
  return m ? m[1] : '—'
}

function typeNeedsLength(dt: string) {
  return LENGTH_TYPES.has((dt || '').toUpperCase())
}

async function openCompare() {
  if (!selectedTableId.value) {
    ElMessage.warning('请先选择基准数据表')
    return
  }
  compareVisible.value = true
  compareDiffs.value = []
  compareRanAt.value = ''
  compareRightTableId.value = undefined
  try {
    const all = (await ingestionApi.tables(selectedSourceId.value)).data || []
    compareRightTables.value = all.filter((t) => t.id !== selectedTableId.value)
    if (!compareRightTables.value.length) {
      const global = (await ingestionApi.tables()).data || []
      compareRightTables.value = global.filter((t) => t.id !== selectedTableId.value)
    }
  } catch {
    compareRightTables.value = tables.value.filter((t) => t.id !== selectedTableId.value)
  }
}

async function runCompare() {
  if (!selectedTableId.value || !compareRightTableId.value) {
    ElMessage.warning('请选择对照表')
    return
  }
  compareLoading.value = true
  try {
    const [leftCols, rightCols] = await Promise.all([
      ingestionApi.columns(selectedTableId.value).then((r) => r.data || []),
      ingestionApi.columns(compareRightTableId.value).then((r) => r.data || []),
    ])
    const leftMap = new Map(leftCols.map((c) => [c.columnCode.toUpperCase(), c]))
    const rightMap = new Map(rightCols.map((c) => [c.columnCode.toUpperCase(), c]))
    const codes = new Set([...leftMap.keys(), ...rightMap.keys()])
    const rows: CompareDiff[] = []
    let same = 0
    let diff = 0
    let onlyLeft = 0
    let onlyRight = 0
    for (const code of [...codes].sort()) {
      const L = leftMap.get(code)
      const R = rightMap.get(code)
      if (L && !R) {
        onlyLeft++
        rows.push({
          columnCode: L.columnCode,
          leftName: L.columnName || '—',
          rightName: '—',
          leftType: dataTypeLabel(L),
          rightType: '—',
          leftLen: lengthLabel(L),
          rightLen: '—',
          leftNullable: L.nullableFlag ? '可空' : '必填',
          rightNullable: '—',
          diffType: '仅基准表',
        })
        continue
      }
      if (!L && R) {
        onlyRight++
        rows.push({
          columnCode: R.columnCode,
          leftName: '—',
          rightName: R.columnName || '—',
          leftType: '—',
          rightType: dataTypeLabel(R),
          leftLen: '—',
          rightLen: lengthLabel(R),
          leftNullable: '—',
          rightNullable: R.nullableFlag ? '可空' : '必填',
          diffType: '仅对照表',
        })
        continue
      }
      if (!L || !R) continue
      const typeSame = dataTypeLabel(L) === dataTypeLabel(R)
      const lenSame = lengthLabel(L) === lengthLabel(R)
      const nullSame = L.nullableFlag === R.nullableFlag
      if (typeSame && lenSame && nullSame) {
        same++
        rows.push({
          columnCode: L.columnCode,
          leftName: L.columnName || '—',
          rightName: R.columnName || '—',
          leftType: dataTypeLabel(L),
          rightType: dataTypeLabel(R),
          leftLen: lengthLabel(L),
          rightLen: lengthLabel(R),
          leftNullable: L.nullableFlag ? '可空' : '必填',
          rightNullable: R.nullableFlag ? '可空' : '必填',
          diffType: '一致',
        })
      } else {
        diff++
        const parts: string[] = []
        if (!typeSame) parts.push('类型')
        if (!lenSame) parts.push('长度')
        if (!nullSame) parts.push('必填')
        rows.push({
          columnCode: L.columnCode,
          leftName: L.columnName || '—',
          rightName: R.columnName || '—',
          leftType: dataTypeLabel(L),
          rightType: dataTypeLabel(R),
          leftLen: lengthLabel(L),
          rightLen: lengthLabel(R),
          leftNullable: L.nullableFlag ? '可空' : '必填',
          rightNullable: R.nullableFlag ? '可空' : '必填',
          diffType: `差异（${parts.join('/')}）`,
        })
      }
    }
    compareDiffs.value = rows
    compareSummary.leftCount = leftCols.length
    compareSummary.rightCount = rightCols.length
    compareSummary.same = same
    compareSummary.diff = diff
    compareSummary.onlyLeft = onlyLeft
    compareSummary.onlyRight = onlyRight
    compareRanAt.value = formatDateTime(new Date())
    ElMessage.success(`比对完成：一致 ${same}，差异 ${diff}，仅基准 ${onlyLeft}，仅对照 ${onlyRight}`)
  } catch {
    ElMessage.error('模型比对失败')
  } finally {
    compareLoading.value = false
  }
}

function exportCompareJson() {
  if (!compareDiffs.value.length) {
    ElMessage.warning('请先执行比对')
    return
  }
  const leftName = tables.value.find((t) => t.id === selectedTableId.value)?.tableName || String(selectedTableId.value)
  const rightName = compareRightTables.value.find((t) => t.id === compareRightTableId.value)?.tableName
    || String(compareRightTableId.value)
  const blob = new Blob([JSON.stringify({
    comparedAt: compareRanAt.value,
    leftTable: leftName,
    rightTable: rightName,
    summary: { ...compareSummary },
    diffs: compareDiffs.value,
  }, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `model-compare-${leftName}-vs-${rightName}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出比对结果')
}

function parseRouteTableId(): number | undefined {
  const raw = route.query.tableId
  const s = Array.isArray(raw) ? raw[0] : raw
  if (s == null || s === '') return undefined
  const n = Number(s)
  return Number.isFinite(n) && n > 0 ? n : undefined
}

async function clearRouteTableId() {
  if (route.query.tableId == null) return
  const q = { ...route.query }
  delete q.tableId
  await router.replace({ path: route.path, query: q })
}

function applyEditCtrl(raw?: Record<string, boolean> | null) {
  const d = defaultEditCtrl()
  for (const k of Object.keys(d) as EditCtrlKey[]) {
    editCtrl[k] = raw && typeof raw[k] === 'boolean' ? raw[k] : true
  }
}

async function loadEditCtrl() {
  try {
    const data = (await ingestionApi.builtinAttrConfig()).data
    applyEditCtrl(data)
  } catch {
    applyEditCtrl(null)
  }
}

async function refreshColumns() {
  if (!selectedTableId.value) {
    columns.value = []
    return
  }
  columns.value = (await ingestionApi.columns(selectedTableId.value)).data || []
}

async function loadSystems() {
  if (!selectedProjectId.value) {
    systems.value = []
    selectedSystemId.value = undefined
    sources.value = []
    selectedSourceId.value = undefined
    tables.value = []
    selectedTableId.value = undefined
    columns.value = []
    return
  }
  systems.value = (await ingestionApi.systems(selectedProjectId.value)).data || []
  if (selectedSystemId.value && !systems.value.some((s) => s.id === selectedSystemId.value)) {
    selectedSystemId.value = undefined
  }
  if (!selectedSystemId.value && systems.value.length) {
    selectedSystemId.value = systems.value[0].id
  }
  await loadSources()
}

async function loadSources() {
  if (!selectedSystemId.value) {
    sources.value = []
    selectedSourceId.value = undefined
    tables.value = []
    selectedTableId.value = undefined
    columns.value = []
    return
  }
  sources.value = (await ingestionApi.dataSources(undefined, selectedSystemId.value)).data || []
  if (selectedSourceId.value && !sources.value.some((s) => s.id === selectedSourceId.value)) {
    selectedSourceId.value = undefined
  }
  if (!selectedSourceId.value && sources.value.length) {
    selectedSourceId.value = sources.value[0].id
  }
  await loadTables()
}

async function loadTables(preferTableId?: number) {
  if (!selectedSourceId.value) {
    tables.value = []
    selectedTableId.value = undefined
    columns.value = []
    return
  }
  tables.value = (await ingestionApi.tables(selectedSourceId.value)).data || []
  if (preferTableId && tables.value.some((t) => t.id === preferTableId)) {
    selectedTableId.value = preferTableId
  } else if (selectedTableId.value && !tables.value.some((t) => t.id === selectedTableId.value)) {
    selectedTableId.value = undefined
  }
  if (!selectedTableId.value) selectedTableId.value = tables.value[0]?.id
  await refreshColumns()
}

async function applyPreferTableId(preferTableId: number) {
  const allTables = (await ingestionApi.tables()).data || []
  const hit = allTables.find((t) => t.id === preferTableId)
  if (!hit) {
    ElMessage.warning(`未找到数据表 #${preferTableId}`)
    await clearRouteTableId()
    return
  }
  const allSources = (await ingestionApi.dataSources()).data || []
  const src = allSources.find((s) => s.id === hit.sourceId)
  if (src?.projectId) {
    selectedProjectId.value = src.projectId
    setActiveProjectId(src.projectId)
  }
  await loadSystems()
  if (src?.systemId) {
    selectedSystemId.value = src.systemId
    await loadSources()
  }
  selectedSourceId.value = hit.sourceId
  await loadTables(preferTableId)
  await clearRouteTableId()
}

async function reload() {
  await withLoad(async () => {
    projects.value = (await ingestionApi.projects()).data || []
    syncActiveProject(projects.value)
    await loadEditCtrl()
    if (activeProjectId.value && projects.value.some((p) => p.id === activeProjectId.value)) {
      selectedProjectId.value = activeProjectId.value
    } else if (projects.value.length && !selectedProjectId.value) {
      selectedProjectId.value = projects.value[0].id
      setActiveProjectId(projects.value[0].id)
    }
    const preferTableId = parseRouteTableId()
    if (preferTableId) {
      await applyPreferTableId(preferTableId)
      return
    }
    if (!selectedProjectId.value) {
      systems.value = []
      sources.value = []
      tables.value = []
      columns.value = []
      selectedTableId.value = undefined
      return
    }
    await loadSystems()
  })
}

async function onProjectChange(id: number) {
  selectedProjectId.value = id
  setActiveProjectId(id)
  selectedSystemId.value = undefined
  selectedSourceId.value = undefined
  selectedTableId.value = undefined
  await loadSystems()
}

async function onSystemChange(id: number) {
  selectedSystemId.value = id
  selectedSourceId.value = undefined
  selectedTableId.value = undefined
  await loadSources()
}

async function onSourceChange(id: number) {
  selectedSourceId.value = id
  selectedTableId.value = undefined
  await loadTables()
}

async function onTableChange(id: number) {
  selectedTableId.value = id
  await refreshColumns()
}

function openCreate() {
  if (!selectedTableId.value) {
    ElMessage.warning('请先选择数据表')
    return
  }
  editingCol.value = null
  colForm.columnCode = ''
  colForm.columnName = ''
  colForm.dataType = 'VARCHAR'
  colForm.lengthVal = 64
  colForm.nullableFlag = 1
  colForm.componentType = 'INPUT'
  colForm.requiredTip = ''
  dialogVisible.value = true
}

function openEdit(row: DataColumn) {
  editingCol.value = row
  colForm.columnCode = row.columnCode
  colForm.columnName = row.columnName || ''
  colForm.dataType = (row.dataType || 'VARCHAR').replace(/\(\d+\)/, '')
  colForm.lengthVal = row.lengthVal ?? 64
  colForm.nullableFlag = row.nullableFlag
  colForm.componentType = row.componentType || 'INPUT'
  colForm.requiredTip = row.requiredTip || ''
  dialogVisible.value = true
}

async function saveColumn() {
  if (canEditAttr('columnName') && !colForm.columnName?.trim()) {
    ElMessage.warning('请填写属性名称')
    return
  }
  if (isCreate.value && !colForm.columnCode?.trim()) {
    ElMessage.warning('请填写属性代码')
    return
  }
  saving.value = true
  try {
    if (editingCol.value) {
      const payload: Record<string, unknown> = {
        requiredTip: colForm.requiredTip.trim() || null,
      }
      if (canEditAttr('columnName')) payload.columnName = colForm.columnName.trim()
      if (canEditAttr('dataType')) payload.dataType = colForm.dataType
      if (canEditAttr('lengthVal')) payload.lengthVal = colForm.lengthVal
      if (canEditAttr('nullableFlag')) payload.nullableFlag = colForm.nullableFlag
      if (canEditAttr('componentType')) payload.componentType = colForm.componentType
      await ingestionApi.updateColumn(editingCol.value.id, payload)
      ElMessage.success('数据项已保存')
    } else if (selectedTableId.value) {
      await ingestionApi.createColumn(selectedTableId.value, {
        columnCode: colForm.columnCode.trim(),
        columnName: colForm.columnName.trim(),
        dataType: colForm.dataType,
        lengthVal: colForm.lengthVal,
        nullableFlag: colForm.nullableFlag,
        componentType: colForm.componentType,
        requiredTip: colForm.requiredTip.trim() || null,
      })
      ElMessage.success('数据项已新建')
    }
    dialogVisible.value = false
    await refreshColumns()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

watch(activeProjectId, (id) => {
  if (parseRouteTableId()) return
  if (id && id !== selectedProjectId.value) {
    selectedProjectId.value = id
    selectedSystemId.value = undefined
    selectedSourceId.value = undefined
    selectedTableId.value = undefined
    void loadSystems()
  }
})

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据项管理">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="项目" class="portal-field-lg">
          <el-select
            :model-value="selectedProjectId"
            filterable
            placeholder="选择项目"
            style="width:100%"
            @update:model-value="onProjectChange"
          >
            <el-option
              v-for="p in projects"
              :key="p.id"
              :label="p.projectName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="系统" class="portal-field-lg">
          <el-select
            :model-value="selectedSystemId"
            filterable
            placeholder="选择系统"
            :disabled="!selectedProjectId"
            style="width:100%"
            @update:model-value="onSystemChange"
          >
            <el-option v-for="s in systems" :key="s.id" :label="s.systemName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据库" class="portal-field-lg">
          <el-select
            :model-value="selectedSourceId"
            filterable
            placeholder="选择数据库"
            :disabled="!selectedSystemId"
            style="width:100%"
            @update:model-value="onSourceChange"
          >
            <el-option v-for="s in sources" :key="s.id" :label="s.sourceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据表" class="portal-field-lg">
          <el-select
            :model-value="selectedTableId"
            :disabled="!selectedSourceId"
            filterable
            placeholder="输入表名筛选"
            style="width:100%"
            @change="onTableChange"
          >
            <el-option v-for="t in tables" :key="t.id" :label="t.tableName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :disabled="!selectedTableId" @click="openCreate">新增</el-button>
          <el-button :disabled="!selectedTableId" @click="openCompare">模型比对</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="columns" stripe>
        <el-table-column prop="columnCode" label="属性代码" width="140" show-overflow-tooltip />
        <el-table-column prop="columnName" label="属性名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="数据类型" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ dataTypeLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="长度" width="80" align="center">
          <template #default="{ row }">{{ lengthLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="组件类型" width="100">
          <template #default="{ row }">{{ componentLabel(row.componentType) }}</template>
        </el-table-column>
        <el-table-column label="必填" width="70" align="center">
          <template #default="{ row }">{{ row.nullableFlag ? '否' : '是' }}</template>
        </el-table-column>
        <el-table-column prop="requiredTip" label="必填提示" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.requiredTip || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="属性代码" required>
          <el-input
            v-model="colForm.columnCode"
            :disabled="!!editingCol || !canEditAttr('columnCode')"
            placeholder="如 ENT_CODE"
          />
        </el-form-item>
        <el-form-item label="属性名称" required>
          <el-input
            v-model="colForm.columnName"
            :disabled="!canEditAttr('columnName')"
            placeholder="显示名称"
          />
        </el-form-item>
        <el-form-item label="数据类型" required>
          <el-select
            v-model="colForm.dataType"
            filterable
            allow-create
            default-first-option
            style="width:100%"
            :disabled="!canEditAttr('dataType')"
            placeholder="选择或输入类型"
          >
            <el-option v-for="t in DATA_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="长度">
          <el-input-number
            v-model="colForm.lengthVal"
            :min="1"
            style="width:100%"
            :disabled="!canEditAttr('lengthVal') || !typeNeedsLength(colForm.dataType)"
          />
        </el-form-item>
        <el-form-item label="组件类型">
          <el-select
            v-model="colForm.componentType"
            style="width:100%"
            :disabled="!canEditAttr('componentType')"
          >
            <el-option v-for="c in componentOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填">
          <el-switch
            :model-value="colForm.nullableFlag === 0"
            :disabled="!canEditAttr('nullableFlag')"
            @change="(v: boolean) => (colForm.nullableFlag = v ? 0 : 1)"
          />
        </el-form-item>
        <el-form-item label="必填项提示说明">
          <el-input
            v-model="colForm.requiredTip"
            type="textarea"
            :rows="2"
            placeholder="必填时的提示文案"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveColumn">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="compareVisible" title="模型间检查与比对" size="880px" destroy-on-close>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="基准表" class="portal-field-lg">
          <el-input
            :model-value="tables.find((t) => t.id === selectedTableId)?.tableName || '—'"
            readonly
          />
        </el-form-item>
        <el-form-item label="对照表" class="portal-field-lg">
          <el-select
            v-model="compareRightTableId"
            filterable
            placeholder="选择对照模型/表"
            style="width:100%"
          >
            <el-option
              v-for="t in compareRightTables"
              :key="t.id"
              :label="`${t.tableName}（${t.tableCode || t.id}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :loading="compareLoading" @click="runCompare">执行比对</el-button>
          <el-button :disabled="!compareDiffs.length" @click="exportCompareJson">导出结果</el-button>
        </el-form-item>
      </el-form>
      <el-descriptions v-if="compareRanAt" :column="3" size="small" border style="margin-bottom:12px">
        <el-descriptions-item label="比对时间">{{ compareRanAt }}</el-descriptions-item>
        <el-descriptions-item label="基准字段数">{{ compareSummary.leftCount }}</el-descriptions-item>
        <el-descriptions-item label="对照字段数">{{ compareSummary.rightCount }}</el-descriptions-item>
        <el-descriptions-item label="一致">{{ compareSummary.same }}</el-descriptions-item>
        <el-descriptions-item label="差异">{{ compareSummary.diff }}</el-descriptions-item>
        <el-descriptions-item label="仅一侧存在">
          基准 {{ compareSummary.onlyLeft }} / 对照 {{ compareSummary.onlyRight }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table v-loading="compareLoading" :data="compareDiffs" stripe border max-height="520">
        <el-table-column prop="columnCode" label="属性代码" width="120" show-overflow-tooltip />
        <el-table-column prop="leftName" label="基准名称" min-width="100" show-overflow-tooltip />
        <el-table-column prop="rightName" label="对照名称" min-width="100" show-overflow-tooltip />
        <el-table-column prop="leftType" label="基准类型" width="90" />
        <el-table-column prop="rightType" label="对照类型" width="90" />
        <el-table-column prop="leftLen" label="基准长度" width="80" />
        <el-table-column prop="rightLen" label="对照长度" width="80" />
        <el-table-column prop="diffType" label="比对结论" width="120" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!compareLoading && !compareDiffs.length" description="选择对照表后执行比对" />
    </el-drawer>
  </div>
</template>

<style scoped>
.hint {
  margin: 0 0 12px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
</style>
