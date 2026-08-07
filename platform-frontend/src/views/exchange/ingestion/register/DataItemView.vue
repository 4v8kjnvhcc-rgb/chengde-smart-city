<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import {
  activeProjectId,
  projectOptionLabel,
  setActiveProjectId,
  syncActiveProject,
} from '../ingestion-project-scope'
import {
  ingestionApi,
  useIngestionLoading,
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
const sources = ref<DataSource[]>([])
const tables = ref<DataTable[]>([])
const columns = ref<DataColumn[]>([])
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

async function resolveTableContext(preferTableId?: number) {
  sources.value = (await ingestionApi.dataSources(activeProjectId.value!)).data || []
  const sourceIds = new Set(sources.value.map((s) => s.id))
  if (!sources.value.length) {
    tables.value = []
    columns.value = []
    selectedTableId.value = undefined
    return
  }
  const allTables = (await ingestionApi.tables()).data || []
  tables.value = allTables.filter((t) => sourceIds.has(t.sourceId))

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
    setActiveProjectId(src.projectId)
  }
  await resolveTableContext(preferTableId)
  await clearRouteTableId()
}

async function reload() {
  await withLoad(async () => {
    projects.value = (await ingestionApi.projects()).data || []
    syncActiveProject(projects.value)
    await loadEditCtrl()
    const preferTableId = parseRouteTableId()
    if (preferTableId) {
      await applyPreferTableId(preferTableId)
      return
    }
    if (!activeProjectId.value) {
      sources.value = []
      tables.value = []
      columns.value = []
      selectedTableId.value = undefined
      return
    }
    await resolveTableContext()
  })
}

watch(activeProjectId, () => {
  if (parseRouteTableId()) return
  selectedTableId.value = undefined
  void reload()
})

async function onTableChange(id: number) {
  selectedTableId.value = id
  await refreshColumns()
}

function openCreate() {
  if (!selectedTableId.value) {
    ElMessage.warning('请先选择物理表')
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

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据项管理">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="当前项目" class="portal-field-xl">
          <el-select
            :model-value="activeProjectId ?? undefined"
            filterable
            placeholder="选择项目 / 系统"
            style="width:100%"
            @update:model-value="(v: number) => setActiveProjectId(v)"
          >
            <el-option
              v-for="p in projects"
              :key="p.id"
              :label="projectOptionLabel(p)"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物理表" class="portal-field-xl">
          <el-select
            :model-value="selectedTableId"
            :disabled="!activeProjectId"
            placeholder="选择表"
            @change="onTableChange"
          >
            <el-option v-for="t in tables" :key="t.id" :label="t.tableName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :disabled="!selectedTableId" @click="openCreate">新建</el-button>
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
          <el-input
            v-model="colForm.dataType"
            :disabled="!canEditAttr('dataType')"
            placeholder="如 VARCHAR / BIGINT"
          />
        </el-form-item>
        <el-form-item label="长度">
          <el-input-number
            v-model="colForm.lengthVal"
            :min="1"
            style="width:100%"
            :disabled="!canEditAttr('lengthVal')"
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
