<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const route = useRoute()
const router = useRouter()
const { loading, loadError, withLoad } = useIngestionLoading()
const projects = ref<Project[]>([])
const sources = ref<DataSource[]>([])
const tables = ref<DataTable[]>([])
const columns = ref<DataColumn[]>([])
const selectedTableId = ref<number>()
const selectedCols = ref<DataColumn[]>([])
const dialogVisible = ref(false)
const editingCol = ref<DataColumn | null>(null)
const saving = ref(false)
const batchDialogVisible = ref(false)
const batchSaving = ref(false)
const batchRows = ref<{ id: number; columnCode: string; columnName: string; semanticDesc: string }[]>([])
const colForm = reactive({
  columnCode: '',
  columnName: '',
  dataType: 'VARCHAR(64)',
  lengthVal: 64,
  nullableFlag: 1,
  semanticDesc: '',
  componentType: 'INPUT',
  requiredTip: '',
  builtInFlag: 0 as 0 | 1,
})

const componentOptions = [
  { value: 'INPUT', label: '文本输入' },
  { value: 'SELECT', label: '下拉选择' },
  { value: 'DATE', label: '日期' },
  { value: 'NUMBER', label: '数字' },
  { value: 'TEXTAREA', label: '多行文本' },
]

function isBuiltIn(row: DataColumn) {
  return row.builtInFlag === 1
}

function componentLabel(code?: string) {
  return componentOptions.find((c) => c.value === code)?.label || code || '—'
}

function typeLengthLabel(row: DataColumn) {
  if (row.lengthVal != null && row.lengthVal > 0 && !/\(\d+\)/.test(row.dataType || '')) {
    return `${row.dataType || '—'}(${row.lengthVal})`
  }
  return row.dataType || '—'
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
  if (selectedTableId.value) {
    columns.value = (await ingestionApi.columns(selectedTableId.value)).data || []
  } else {
    columns.value = []
  }
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
  selectedCols.value = []
  void reload()
})

async function onTableChange(id: number) {
  selectedTableId.value = id
  selectedCols.value = []
  columns.value = (await ingestionApi.columns(id)).data
}

function onSelectionChange(rows: DataColumn[]) {
  selectedCols.value = rows
}

function openCreate() {
  if (!selectedTableId.value) {
    ElMessage.warning('请先选择物理表')
    return
  }
  editingCol.value = null
  colForm.columnCode = ''
  colForm.columnName = ''
  colForm.dataType = 'VARCHAR(64)'
  colForm.lengthVal = 64
  colForm.nullableFlag = 1
  colForm.semanticDesc = ''
  colForm.componentType = 'INPUT'
  colForm.requiredTip = ''
  colForm.builtInFlag = 0
  dialogVisible.value = true
}

function openEdit(row: DataColumn) {
  editingCol.value = row
  colForm.columnCode = row.columnCode
  colForm.columnName = row.columnName
  colForm.dataType = row.dataType
  colForm.lengthVal = row.lengthVal ?? 64
  colForm.nullableFlag = row.nullableFlag
  colForm.semanticDesc = row.semanticDesc || ''
  colForm.componentType = row.componentType || 'INPUT'
  colForm.requiredTip = row.requiredTip || ''
  colForm.builtInFlag = isBuiltIn(row) ? 1 : 0
  dialogVisible.value = true
}

function openBatchEdit() {
  ElMessage.info('数据项仅支持查看，不可批量编辑')
}

async function confirmIrreversible(count = 1) {
  const tip = count > 1
    ? `将覆盖 ${count} 个自定义数据项。保存后，元数据维护中对应属性信息不可恢复，是否继续？`
    : '保存后，元数据维护中对应的属性信息不可恢复，是否继续？'
  await ElMessageBox.confirm(tip, '编辑确认', {
    type: 'warning',
    confirmButtonText: '确认保存',
    cancelButtonText: '取消',
  })
}

async function saveColumn() {
  if (!colForm.columnName?.trim()) {
    ElMessage.warning('请填写字段名称')
    return
  }
  if (editingCol.value && isBuiltIn(editingCol.value)) {
    ElMessage.warning('系统内置属性不可编辑')
    return
  }
  saving.value = true
  try {
    if (editingCol.value) {
      if (colForm.builtInFlag !== 1) {
        await confirmIrreversible(1)
      } else {
        await ElMessageBox.confirm(
          '设为内置属性后将不可再编辑，列表将显示「内置=是」。是否继续？',
          '设为内置',
          { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
        )
      }
      await ingestionApi.updateColumn(editingCol.value.id, {
        columnName: colForm.columnName.trim(),
        dataType: colForm.dataType,
        lengthVal: colForm.lengthVal,
        nullableFlag: colForm.nullableFlag,
        semanticDesc: colForm.semanticDesc,
        componentType: colForm.componentType,
        requiredTip: colForm.requiredTip,
        builtInFlag: colForm.builtInFlag,
      })
      ElMessage.success(
        colForm.builtInFlag === 1
          ? '已设为内置属性，之后不可编辑'
          : '已保存；元数据维护中对应属性已覆盖且不可恢复',
      )
    } else if (selectedTableId.value) {
      if (!colForm.columnCode?.trim()) {
        ElMessage.warning('请填写字段编码')
        return
      }
      if (colForm.builtInFlag === 1) {
        await ElMessageBox.confirm(
          '设为内置属性后将不可再编辑，列表将显示「内置=是」。是否继续？',
          '设为内置',
          { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
        )
      }
      await ingestionApi.createColumn(selectedTableId.value, {
        ...colForm,
        columnCode: colForm.columnCode.trim(),
        columnName: colForm.columnName.trim(),
        builtInFlag: colForm.builtInFlag,
      })
      ElMessage.success(colForm.builtInFlag === 1 ? '已新建为内置属性' : '数据项已新建')
    }
    dialogVisible.value = false
    if (selectedTableId.value) columns.value = (await ingestionApi.columns(selectedTableId.value)).data
  } catch (e: unknown) {
    if (e === 'cancel' || (e as { message?: string })?.message === 'cancel') return
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function saveBatchEdit() {
  if (!batchRows.value.length) return
  for (const row of batchRows.value) {
    if (!row.columnName?.trim()) {
      ElMessage.warning(`字段「${row.columnCode}」名称不能为空`)
      return
    }
  }
  batchSaving.value = true
  try {
    await confirmIrreversible(batchRows.value.length)
    const chunk = 3
    for (let i = 0; i < batchRows.value.length; i += chunk) {
      const part = batchRows.value.slice(i, i + chunk)
      await Promise.all(
        part.map((r) =>
          ingestionApi.updateColumn(r.id, {
            columnName: r.columnName.trim(),
            semanticDesc: r.semanticDesc?.trim() || '',
          }),
        ),
      )
    }
    ElMessage.success(`已批量更新 ${batchRows.value.length} 个数据项；元数据对应属性已覆盖且不可恢复`)
    batchDialogVisible.value = false
    selectedCols.value = []
    if (selectedTableId.value) columns.value = (await ingestionApi.columns(selectedTableId.value)).data
  } catch (e: unknown) {
    if (e === 'cancel' || (e as { message?: string })?.message === 'cancel') return
    ElMessage.error(e instanceof Error ? e.message : '批量保存失败')
  } finally {
    batchSaving.value = false
  }
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据项管理">
      <p class="hint">
        按当前项目过滤物理表。数据项仅支持查看，不可新增、修改或删除（字段随数据库表登记同步）。
      </p>
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
      </el-form>
      <el-table :data="columns" stripe>
        <el-table-column prop="columnCode" label="字段编码" width="120" />
        <el-table-column prop="columnName" label="字段名称" min-width="120" />
        <el-table-column label="数据类型 & 长度" min-width="140">
          <template #default="{ row }">{{ typeLengthLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="控件类型" width="100">
          <template #default="{ row }">{{ componentLabel(row.componentType) }}</template>
        </el-table-column>
        <el-table-column label="必填" width="60">
          <template #default="{ row }">{{ row.nullableFlag ? '否' : '是' }}</template>
        </el-table-column>
        <el-table-column label="内置" width="70">
          <template #default="{ row }">{{ isBuiltIn(row) ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="semanticDesc" label="注释" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog
      v-model="dialogVisible"
      title="查看数据项"
      width="520px"
      destroy-on-close
    >
      <el-alert
        v-if="editingCol && isBuiltIn(editingCol)"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="该字段已是内置属性，不可再编辑。"
      />
      <el-alert
        v-else-if="editingCol"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="编辑后元数据维护中对应属性不可恢复；若设为内置并保存，之后将不可再编辑。"
      />
      <el-form label-width="100px">
        <el-form-item label="字段编码" required>
          <el-input v-model="colForm.columnCode" :disabled="!!editingCol" placeholder="如 ENT_CODE" />
        </el-form-item>
        <el-form-item label="内置属性">
          <el-radio-group
            v-model="colForm.builtInFlag"
            :disabled="editingCol != null && isBuiltIn(editingCol)"
          >
            <el-radio-button :value="1">是</el-radio-button>
            <el-radio-button :value="0">否</el-radio-button>
          </el-radio-group>
          <div class="field-tip">
            <template v-if="editingCol && isBuiltIn(editingCol)">已锁定为内置，不可修改</template>
            <template v-else-if="colForm.builtInFlag === 1">保存后列表显示「内置=是」，且不可再编辑</template>
            <template v-else>自定义属性可编辑；保存后元数据对应信息不可恢复</template>
          </div>
        </el-form-item>
        <el-form-item label="字段名称" required>
          <el-input v-model="colForm.columnName" :disabled="editingCol != null && isBuiltIn(editingCol)" />
        </el-form-item>
        <el-form-item label="数据类型">
          <el-input v-model="colForm.dataType" :disabled="editingCol != null && isBuiltIn(editingCol)" />
        </el-form-item>
        <el-form-item label="长度">
          <el-input-number
            v-model="colForm.lengthVal"
            :min="1"
            style="width:100%"
            :disabled="editingCol != null && isBuiltIn(editingCol)"
          />
        </el-form-item>
        <el-form-item label="控件类型">
          <el-select
            v-model="colForm.componentType"
            style="width:100%"
            :disabled="editingCol != null && isBuiltIn(editingCol)"
          >
            <el-option v-for="c in componentOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填">
          <el-switch
            :model-value="colForm.nullableFlag === 0"
            :disabled="editingCol != null && isBuiltIn(editingCol)"
            @change="(v: boolean) => colForm.nullableFlag = v ? 0 : 1"
          />
        </el-form-item>
        <el-form-item label="必填提示">
          <el-input v-model="colForm.requiredTip" :disabled="editingCol != null && isBuiltIn(editingCol)" />
        </el-form-item>
        <el-form-item label="语义说明">
          <el-input
            v-model="colForm.semanticDesc"
            type="textarea"
            :rows="2"
            :disabled="editingCol != null && isBuiltIn(editingCol)"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ editingCol && isBuiltIn(editingCol) ? '关闭' : '取消' }}</el-button>
        <el-button
          v-if="!(editingCol && isBuiltIn(editingCol))"
          type="primary"
          :loading="saving"
          @click="saveColumn"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchDialogVisible" title="批量编辑字段名称 / 注释" width="720px" destroy-on-close>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="仅可编辑自定义属性；保存后元数据维护中对应属性信息不可恢复。"
      />
      <p class="hint">仅修改字段名称与语义说明；字段编码不变。内置属性不会出现在此列表。</p>
      <el-table :data="batchRows" stripe size="small" max-height="420">
        <el-table-column prop="columnCode" label="字段编码" width="140" />
        <el-table-column label="字段名称" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.columnName" placeholder="字段名称" />
          </template>
        </el-table-column>
        <el-table-column label="语义说明（注释）" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.semanticDesc" placeholder="注释 / 语义说明" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSaving" @click="saveBatchEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint { margin: 0 0 12px; color: #606266; font-size: 13px; line-height: 1.5; }
.field-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>
