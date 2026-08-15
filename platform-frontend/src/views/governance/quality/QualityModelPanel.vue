<script setup lang="ts">
/**
 * Tab：质量模型管理（图1~3）
 * 数据源选择对照编目页「选择数据源」弹窗（元数据分类 + 列表）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'

interface ModelTableRow {
  id?: number
  tableName: string
  tableComment?: string | null
}

interface ModelRow {
  id: number
  modelName: string
  datasourceId: number
  datasourceName?: string
  description?: string | null
  tables?: ModelTableRow[]
}

interface MetaCategoryOption {
  id: number
  label: string
  layerCode?: string
}

interface BindSource {
  id: number
  sourceName: string
  categoryName?: string
  categoryId?: number
  providerOrg?: string
  versionLabel?: string
  systemName?: string
  sourceKind?: string
}

interface BindTable {
  tableName: string
  tableComment?: string
  chineseName?: string
  entryName?: string
}

const models = ref<ModelRow[]>([])
const loading = ref(false)
const {
  page,
  pageSize,
  paged,
  total,
  resetPage,
} = useClientPager(models)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  modelName: '',
  datasourceId: undefined as number | undefined,
  datasourceName: '',
  description: '',
  tables: [] as ModelTableRow[],
})

/** —— 选择数据源（对照 CatalogResourceView） —— */
const dsPickerVisible = ref(false)
const dsPickerLoading = ref(false)
const dsPickerKeyword = ref('')
const dsPickerName = ref('')
const dsPickerCategoryId = ref<number | null>(null)
const metaSourceCategories = ref<MetaCategoryOption[]>([])
const dsPickerRows = ref<BindSource[]>([])
const dsPickerSelected = ref<BindSource | null>(null)
const {
  page: dsPage,
  pageSize: dsPageSize,
  paged: dsPaged,
  total: dsTotal,
  resetPage: resetDsPage,
} = useClientPager(dsPickerRows, 20)

/** —— 选表 —— */
const pickTableVisible = ref(false)
const availableTables = ref<BindTable[]>([])
const tablesLoading = ref(false)
const pickTableKw = ref('')

const filteredAvailableTables = computed(() => {
  const kw = pickTableKw.value.trim().toLowerCase()
  if (!kw) return availableTables.value
  return availableTables.value.filter((t) =>
    `${t.tableName} ${t.tableComment || ''}`.toLowerCase().includes(kw),
  )
})
const pickedTables = ref<string[]>([])

async function load() {
  loading.value = true
  try {
    models.value = (await api.get('/governance/quality/models')).data || []
    resetPage()
  } catch {
    ElMessage.error('加载质量模型失败')
  } finally {
    loading.value = false
  }
}

function flattenMetaCategoryTree(
  nodes: Array<{ id: number; label: string; layerCode?: string; children?: typeof nodes }>,
  out: MetaCategoryOption[] = [],
): MetaCategoryOption[] {
  for (const n of nodes) {
    out.push({ id: n.id, label: n.label, layerCode: n.layerCode })
    if (n.children?.length) flattenMetaCategoryTree(n.children, out)
  }
  return out
}

function metaCategoryLabel(c: MetaCategoryOption) {
  return c.layerCode ? `${c.label}（${c.layerCode}）` : c.label
}

const filteredMetaCategories = computed(() => {
  const kw = dsPickerKeyword.value.trim()
  if (!kw) return metaSourceCategories.value
  return metaSourceCategories.value.filter((c) => metaCategoryLabel(c).includes(kw))
})

async function loadMetaSourceCategories() {
  try {
    const rows = (await api.get('/governance/platform/metadata/source-categories/tree')).data || []
    metaSourceCategories.value = flattenMetaCategoryTree(rows)
    if (!dsPickerCategoryId.value && metaSourceCategories.value.length) {
      dsPickerCategoryId.value = metaSourceCategories.value[0].id
    }
  } catch {
    metaSourceCategories.value = []
    ElMessage.error('加载数据源分类失败')
  }
}

async function loadBindSources() {
  dsPickerLoading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt/bind-sources', {
      params: {
        sourceKind: 'META',
        categoryId: dsPickerCategoryId.value || undefined,
        keyword: dsPickerName.value.trim() || undefined,
      },
    })
    dsPickerRows.value = res.data || []
    resetDsPage()
  } catch {
    dsPickerRows.value = []
    ElMessage.error('加载数据源失败')
  } finally {
    dsPickerLoading.value = false
  }
}

async function openDsPicker() {
  if (dialogMode.value === 'edit') return
  dsPickerVisible.value = true
  dsPickerSelected.value = null
  dsPickerKeyword.value = ''
  dsPickerName.value = ''
  await loadMetaSourceCategories()
  await loadBindSources()
}

function onDsPickerRowClick(row: BindSource) {
  dsPickerSelected.value = row
}

function selectCategory(id: number) {
  dsPickerCategoryId.value = id
  void loadBindSources()
}

function resetDsQuery() {
  dsPickerName.value = ''
  void loadBindSources()
}

function confirmDsPicker() {
  const row = dsPickerSelected.value
  if (!row) {
    ElMessage.warning('请先选择一条数据源')
    return
  }
  const changed = form.datasourceId !== row.id
  form.datasourceId = row.id
  form.datasourceName = row.sourceName
  if (changed) form.tables = []
  dsPickerVisible.value = false
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  form.modelName = ''
  form.datasourceId = undefined
  form.datasourceName = ''
  form.description = ''
  form.tables = []
  dialogVisible.value = true
}

async function openEdit(row: ModelRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  dialogVisible.value = true
  try {
    const detail = (await api.get(`/governance/quality/models/${row.id}`)).data as ModelRow
    form.modelName = detail.modelName
    form.datasourceId = detail.datasourceId
    form.datasourceName = detail.datasourceName || ''
    form.description = detail.description || ''
    form.tables = (detail.tables || []).map((t) => ({
      id: t.id,
      tableName: t.tableName,
      tableComment: t.tableComment,
    }))
  } catch {
    ElMessage.error('加载模型详情失败')
    dialogVisible.value = false
  }
}

async function openPickTables() {
  if (form.datasourceId == null) {
    ElMessage.warning('请先选择数据源')
    return
  }
  tablesLoading.value = true
  pickTableVisible.value = true
  pickTableKw.value = ''
  pickedTables.value = form.tables.map((t) => t.tableName)
  try {
    const res = await api.get(`/governance/catalog/resources-mgmt/bind-sources/${form.datasourceId}/tables`, {
      params: { sourceKind: 'META' },
    })
    availableTables.value = (res.data || []).map((t: BindTable) => ({
      tableName: t.tableName || (t as { sourceTable?: string }).sourceTable || '',
      tableComment: t.tableComment || t.chineseName || t.entryName || '',
    })).filter((t: BindTable) => !!t.tableName)
  } catch {
    // 回退：元数据采集探表
    try {
      const rows = (await api.get(`/governance/platform/metadata/collect/data-sources/${form.datasourceId}/tables`)).data || []
      availableTables.value = (rows as Array<Record<string, unknown>>).map((r) => ({
        tableName: String(r.sourceTable || r.tableName || r.name || '').trim(),
        tableComment: String(r.tableComment || r.comment || r.remarks || '').trim(),
      })).filter((t) => !!t.tableName)
    } catch {
      availableTables.value = []
      ElMessage.warning('加载表清单失败')
    }
  } finally {
    tablesLoading.value = false
  }
}

function confirmPickTables() {
  const selected = new Set(pickedTables.value)
  const keep = form.tables.filter((t) => selected.has(t.tableName))
  const keepNames = new Set(keep.map((t) => t.tableName))
  for (const name of pickedTables.value) {
    if (keepNames.has(name)) continue
    const meta = availableTables.value.find((t) => t.tableName === name)
    keep.push({
      tableName: name,
      tableComment: meta?.tableComment || '',
    })
  }
  form.tables = keep
  pickTableVisible.value = false
}

function removeTable(idx: number) {
  form.tables.splice(idx, 1)
}

async function submit() {
  if (!form.modelName.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  if (form.datasourceId == null) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!form.tables.length) {
    ElMessage.warning('请至少添加一张表')
    return
  }
  saving.value = true
  try {
    const body = {
      modelName: form.modelName.trim(),
      datasourceId: form.datasourceId,
      datasourceName: form.datasourceName,
      description: form.description.trim() || null,
      tables: form.tables.map((t) => ({
        tableName: t.tableName,
        tableComment: t.tableComment || null,
      })),
    }
    if (dialogMode.value === 'create') {
      await api.post('/governance/quality/models', body)
      ElMessage.success('已新增质量模型')
    } else if (editingId.value != null) {
      await api.put(`/governance/quality/models/${editingId.value}`, body)
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeModel(id: number) {
  await ElMessageBox.confirm('确认删除该质量模型及其规则？', '删除确认', { type: 'warning' })
  await api.delete(`/governance/quality/models/${id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">+ 新增</el-button>
    </div>

    <el-table v-loading="loading" :data="paged" stripe border>
      <el-table-column prop="modelName" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="数据源" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.datasourceName || '—' }}</template>
      </el-table-column>
      <el-table-column label="描述" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button link type="danger" @click="removeModel(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    <el-empty v-if="!loading && !models.length" description="暂无质量模型，请点击新增" />

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增数据质量-模型' : '编辑数据质量-模型'"
      width="640px"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.modelName" maxlength="128" placeholder="请输入模型名称" />
        </el-form-item>
        <el-form-item label="数据源" required>
          <el-input
            :model-value="form.datasourceName"
            readonly
            :disabled="dialogMode === 'edit'"
            placeholder="请选择数据源"
            class="ds-trigger"
            @click="openDsPicker"
          >
            <template #suffix>
              <span class="ds-caret">▾</span>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="512" />
        </el-form-item>
      </el-form>

      <div class="table-block">
        <div class="table-block__head">
          <span>表名</span>
          <span>表注释</span>
          <el-button type="primary" circle size="small" @click="openPickTables">+</el-button>
        </div>
        <el-table :data="form.tables" border size="small" max-height="260">
          <el-table-column prop="tableName" label="表名" min-width="200" show-overflow-tooltip />
          <el-table-column prop="tableComment" label="表注释" min-width="160" show-overflow-tooltip />
          <el-table-column label="" width="80" align="right">
            <template #default="{ $index }">
              <el-button link type="primary" @click="removeTable($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!form.tables.length" description="点击右上角 + 添加表" :image-size="56" />
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 对照编目页：选择数据源 -->
    <el-dialog
      v-model="dsPickerVisible"
      title="选择数据源"
      width="900px"
      destroy-on-close
      append-to-body
      :close-on-click-modal="false"
    >
      <div class="ds-picker">
        <aside class="ds-picker-side">
          <el-input v-model="dsPickerKeyword" clearable placeholder="请输入名称" size="small" style="margin-bottom: 8px" />
          <div
            v-for="c in filteredMetaCategories"
            :key="c.id"
            class="ds-cat"
            :class="{ active: dsPickerCategoryId === c.id }"
            @click="selectCategory(c.id)"
          >
            {{ metaCategoryLabel(c) }}
          </div>
          <el-empty v-if="!filteredMetaCategories.length" description="暂无分类" :image-size="48" />
        </aside>
        <div class="ds-picker-main">
          <el-form inline class="portal-inline-form portal-inline-form--sm" size="small" @submit.prevent="loadBindSources">
            <el-form-item label="数据源名称" class="portal-field-lg">
              <el-input v-model="dsPickerName" clearable placeholder="请输入数据源名称" @keyup.enter="loadBindSources" />
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="loadBindSources">查询</el-button>
              <el-button @click="resetDsQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-table
            v-loading="dsPickerLoading"
            :data="dsPaged"
            size="small"
            stripe
            highlight-current-row
            max-height="380"
            @row-click="onDsPickerRowClick"
          >
            <el-table-column prop="sourceName" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="版本" width="90">
              <template #default="{ row }">{{ row.versionLabel || '—' }}</template>
            </el-table-column>
            <el-table-column label="所属分类" width="100">
              <template #default="{ row }">{{ row.categoryName || '—' }}</template>
            </el-table-column>
            <el-table-column label="提供部门" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.providerOrg || row.systemName || '—' }}</template>
            </el-table-column>
          </el-table>
          <PortalPagination v-model:page="dsPage" v-model:page-size="dsPageSize" :total="dsTotal" />
        </div>
      </div>
      <template #footer>
        <el-button @click="dsPickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmDsPicker">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pickTableVisible" title="选择表" width="520px" destroy-on-close append-to-body>
      <el-form inline class="portal-inline-form portal-inline-form--block" size="small">
        <el-form-item label="表名" class="portal-field-xl">
          <el-input v-model="pickTableKw" clearable placeholder="输入表名筛选" />
        </el-form-item>
      </el-form>
      <el-checkbox-group v-loading="tablesLoading" v-model="pickedTables" class="pick-list">
        <el-checkbox
          v-for="t in filteredAvailableTables"
          :key="t.tableName"
          :value="t.tableName"
        >
          {{ t.tableName }}
          <span v-if="t.tableComment" class="muted">（{{ t.tableComment }}）</span>
        </el-checkbox>
      </el-checkbox-group>
      <el-empty v-if="!tablesLoading && !filteredAvailableTables.length" description="该数据源暂无可选表" />
      <template #footer>
        <el-button @click="pickTableVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPickTables">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
.ds-trigger {
  cursor: pointer;
}
.ds-trigger :deep(.el-input__wrapper) {
  cursor: pointer;
}
.ds-caret {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  margin-right: 4px;
}
.table-block {
  margin-top: 8px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 10px;
}
.table-block__head {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}
.pick-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 360px;
  overflow: auto;
}
.muted {
  color: var(--el-text-color-secondary);
  margin-left: 4px;
}
.ds-picker {
  display: flex;
  gap: 12px;
  min-height: 420px;
}
.ds-picker-side {
  width: 160px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  padding-right: 10px;
  overflow: auto;
}
.ds-cat {
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.ds-cat:hover {
  background: var(--el-fill-color-light);
}
.ds-cat.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}
.ds-picker-main {
  flex: 1;
  min-width: 0;
}
</style>
