<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import {
  FIELD_TYPE_OPTIONS,
  fieldCountOf,
  parseFieldDefs,
  stringifyFieldDefs,
  type MetaFieldDef,
} from './meta-labels'
import { statusLabel } from '@/utils/status-label'

interface MetaModel {
  id: number
  modelCode: string
  modelNameZh: string
  modelNameEn?: string
  modelType: string
  status: string
  contentJson?: string
  metaDataSourceId?: number
  sourceTableName?: string
  sourceColumnName?: string
  publishedAt?: string
  createdBy?: string
  createdAt?: string
}

interface MetaDataSourceOption {
  id: number
  sourceName: string
  categoryId?: number
  categoryName?: string
  adapterType?: string
  dbName?: string
}

interface CategoryNode {
  id: number
  label: string
  layerCode?: string
  children?: CategoryNode[]
}

interface CategoryOption {
  id: number
  label: string
  layerCode?: string
}

interface SourceTableOption {
  tableName: string
}

interface ModelVersion {
  id: number
  versionNo: number
  changeSummary?: string
  createdBy?: string
  createdAt?: string
}

interface CatalogEntry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
}

interface FieldDiff {
  added?: string[]
  removed?: string[]
  changed?: string[]
}

interface BasicDiffRow {
  field: string
  left: string
  right: string
}

interface FieldAttrDiffRow {
  fieldCode: string
  changeType: string
  left: string
  right: string
}

const STATUS_FILTER_OPTIONS = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已下线', value: 'OFFLINE' },
]

interface BindingTable {
  id: number
  entryCode: string
  entryName: string
  conformStatus?: string
  dataLayer?: string
}

interface BindingTask {
  id: number
  taskName: string
  status: string
}

const MODEL_TYPE_OPTIONS = [
  { label: '表模型', value: 'TABLE' },
  { label: '字段模型', value: 'COLUMN' },
]

const COLUMN_ACTION_OPTIONS = [
  { label: '新增字段', value: 'ADD' },
  { label: '修改字段', value: 'MODIFY' },
]

const models = ref<MetaModel[]>([])
const allMetaDataSources = ref<MetaDataSourceOption[]>([])
const dialogMetaDataSources = ref<MetaDataSourceOption[]>([])
const categories = ref<CategoryOption[]>([])
const metaDataSourceMap = computed(() => {
  const m = new Map<number, string>()
  for (const s of allMetaDataSources.value) m.set(s.id, s.sourceName)
  return m
})
const queryStatus = ref('')
const queryKeyword = ref('')
const statusFilter = ref('')
const keyword = ref('')
const metaDataSourcesLoading = ref(false)
const categoriesLoading = ref(false)
const dialogBootstrapping = ref(false)

const filteredModels = computed(() => {
  let rows = models.value
  if (statusFilter.value) {
    rows = rows.filter(m => m.status === statusFilter.value)
  }
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    rows = rows.filter(m =>
      m.modelNameZh.toLowerCase().includes(kw)
      || (m.modelCode || '').toLowerCase().includes(kw)
      || (m.modelNameEn || '').toLowerCase().includes(kw),
    )
  }
  return rows
})

const {
  page: modelPage,
  pageSize: modelPageSize,
  paged: pagedModels,
  total: modelTotal,
  resetPage: resetModelPage,
} = useClientPager(filteredModels)
const compare = ref<Record<string, unknown> | null>(null)
const compareForm = reactive({ leftId: undefined as number | undefined, rightId: undefined as number | undefined })

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({
  modelNameZh: '',
  modelNameEn: '',
  modelType: 'TABLE' as 'TABLE' | 'COLUMN',
  columnAction: 'ADD' as 'ADD' | 'MODIFY',
  categoryId: undefined as number | undefined,
  metaDataSourceId: undefined as number | undefined,
  sourceTableName: '',
  sourceColumnName: '',
})
const fieldRows = ref<MetaFieldDef[]>([])
const existingTableFields = ref<MetaFieldDef[]>([])
const sourceTables = ref<SourceTableOption[]>([])
const sourceColumns = ref<string[]>([])
const sourceTablesLoading = ref(false)
const sourceColumnsLoading = ref(false)
const fieldsLoading = ref(false)

const importInputRef = ref<HTMLInputElement | null>(null)

const fromEntryVisible = ref(false)
const fromEntryLoading = ref(false)
const catalogEntries = ref<CatalogEntry[]>([])
const fromEntryId = ref<number | undefined>()

const detailVisible = ref(false)
const detailModel = ref<MetaModel | null>(null)
const detailTab = ref<'fields' | 'bindings' | 'versions'>('fields')
const modelVersions = ref<ModelVersion[]>([])
const bindingsLoading = ref(false)
const bindingTables = ref<BindingTable[]>([])
const bindingTasks = ref<BindingTask[]>([])
const recheckResult = ref<Record<string, unknown> | null>(null)
const rechecking = ref(false)
const syncingPhysical = ref(false)

const fieldDiff = computed(() => (compare.value?.fieldDiff as FieldDiff | undefined) ?? null)
const basicDiff = computed(() => (compare.value?.basicDiff as BasicDiffRow[] | undefined) ?? [])
const fieldAttrDiff = computed(() => (compare.value?.fieldAttrDiff as FieldAttrDiffRow[] | undefined) ?? [])

const detailFields = computed(() => parseFieldDefs(detailModel.value?.contentJson))

function emptyField(): MetaFieldDef {
  return { code: '', name: '', type: 'VARCHAR', length: 64, required: false, primaryKey: false, hint: '' }
}

function modelStatusLabel(status: string) {
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'OFFLINE') return '已下线'
  if (status === 'DRAFT') return '草稿'
  return statusLabel(status)
}

function fieldLabel(field: string) {
  const map: Record<string, string> = {
    modelNameZh: '中文名称',
    modelNameEn: '英文名称',
    modelType: '模型类型',
    metaDataSourceId: '数据源',
    sourceTableName: '表名',
    sourceColumnName: '源字段',
    status: '状态',
  }
  return map[field] || field
}

function sourceLabel(row: MetaModel) {
  if (!row.metaDataSourceId) return ''
  const name = metaDataSourceMap.value.get(row.metaDataSourceId) || `数据源#${row.metaDataSourceId}`
  const parts = [name]
  if (row.sourceTableName) parts.push(row.sourceTableName)
  if (row.modelType === 'COLUMN') {
    const fields = parseFieldDefs(row.contentJson)
    const action = fields[0]?.action
    if (action === 'ADD' && fields.length > 1) {
      parts.push(`新增${fields.length}个字段`)
    } else {
      const col = row.sourceColumnName || fields[0]?.code
      if (col) {
        const prefix = action === 'ADD' ? '新增' : action === 'MODIFY' ? '修改' : ''
        const label = col.includes(',') ? col.replace(/,/g, '、') : col
        parts.push(prefix ? `${prefix}:${label}` : label)
      }
    }
  }
  return parts.join(' · ')
}

function columnActionLabel(action?: string) {
  if (action === 'ADD') return '新增字段'
  if (action === 'MODIFY') return '修改字段'
  return '—'
}

function mapProbeFields(raw: unknown[], action: 'ADD' | 'MODIFY' = 'MODIFY'): MetaFieldDef[] {
  return (raw || []).map((item) => {
    const o = item as Record<string, unknown>
    return {
      code: String(o.code || ''),
      name: String(o.name || o.code || ''),
      type: String(o.type || 'VARCHAR'),
      length: o.length == null ? undefined : Number(o.length),
      required: Boolean(o.required),
      primaryKey: Boolean(o.primaryKey),
      hint: o.hint != null ? String(o.hint) : '',
      action,
    }
  }).filter(f => f.code)
}

function flattenCategoryTree(nodes: CategoryNode[], out: CategoryOption[] = []): CategoryOption[] {
  for (const n of nodes) {
    out.push({ id: n.id, label: n.label, layerCode: n.layerCode })
    if (n.children?.length) flattenCategoryTree(n.children, out)
  }
  return out
}

function categoryOptionLabel(c: CategoryOption) {
  if (c.layerCode) return `${c.label}（${c.layerCode}）`
  return c.label
}

function resolveCategoryForSource(sourceId?: number) {
  if (!sourceId) return undefined
  return allMetaDataSources.value.find(s => s.id === sourceId)?.categoryId
}

function dataSourceOptionLabel(s: MetaDataSourceOption) {
  const parts = [s.sourceName]
  if (s.dbName) parts.push(s.dbName)
  return parts.join(' · ')
}

async function loadCategories() {
  categoriesLoading.value = true
  try {
    const rows = (await api.get('/governance/platform/metadata/source-categories/tree')).data || []
    categories.value = flattenCategoryTree(rows as CategoryNode[])
  } catch (e) {
    categories.value = []
    ElMessage.error((e as Error).message || '加载数据分类失败')
  } finally {
    categoriesLoading.value = false
  }
}

async function loadAllMetaDataSources() {
  try {
    allMetaDataSources.value = (await api.get('/governance/platform/metadata/data-sources')).data || []
  } catch {
    allMetaDataSources.value = []
  }
}

async function loadDialogMetaDataSources(categoryId?: number) {
  if (!categoryId) {
    dialogMetaDataSources.value = []
    return
  }
  metaDataSourcesLoading.value = true
  try {
    dialogMetaDataSources.value = (await api.get('/governance/platform/metadata/data-sources', {
      params: { categoryId },
    })).data || []
  } catch (e) {
    dialogMetaDataSources.value = []
    ElMessage.error((e as Error).message || '加载数据源失败')
  } finally {
    metaDataSourcesLoading.value = false
  }
}

function doQuery() {
  statusFilter.value = queryStatus.value
  keyword.value = queryKeyword.value.trim()
  resetModelPage()
}

async function loadSourceTables() {
  if (!form.metaDataSourceId) {
    sourceTables.value = []
    return
  }
  sourceTablesLoading.value = true
  try {
    const rows = (await api.get(`/governance/platform/metadata/collect/meta-data-sources/${form.metaDataSourceId}/tables`)).data || []
    sourceTables.value = rows.map((r: Record<string, unknown>) => ({
      tableName: String(r.tableName || r.sourceTable || ''),
    })).filter((t: SourceTableOption) => t.tableName)
  } finally {
    sourceTablesLoading.value = false
  }
}

async function loadExistingTableFields() {
  if (!form.metaDataSourceId || !form.sourceTableName || form.modelType !== 'COLUMN') {
    existingTableFields.value = []
    sourceColumns.value = []
    return
  }
  sourceColumnsLoading.value = true
  try {
    const data = (await api.get(
      `/governance/platform/metadata/models/meta-data-sources/${form.metaDataSourceId}/table-columns`,
      { params: { tableName: form.sourceTableName } },
    )).data
    const fields = mapProbeFields(data?.fields || [])
    existingTableFields.value = fields
    sourceColumns.value = fields.map(f => f.code)
  } catch (e) {
    existingTableFields.value = []
    sourceColumns.value = []
    ElMessage.error((e as Error).message || '加载现有字段失败')
  } finally {
    sourceColumnsLoading.value = false
  }
}

async function loadSourceColumnNames() {
  await loadExistingTableFields()
}

async function loadColumnFromSource() {
  if (form.modelType !== 'COLUMN' || form.columnAction !== 'MODIFY') return
  if (!form.metaDataSourceId || !form.sourceTableName) {
    ElMessage.warning('请先选择数据源与源表')
    return
  }
  if (!form.sourceColumnName) {
    ElMessage.warning('请先选择要修改的源字段')
    return
  }
  fieldsLoading.value = true
  try {
    const data = (await api.get(
      `/governance/platform/metadata/models/meta-data-sources/${form.metaDataSourceId}/table-columns`,
      {
        params: {
          tableName: form.sourceTableName,
          columnName: form.sourceColumnName,
        },
      },
    )).data
    const fields = mapProbeFields(data?.fields || [], 'MODIFY')
    if (!fields.length) {
      ElMessage.warning('未探测到该字段')
      return
    }
    fieldRows.value = fields.slice(0, 1)
    if (!form.modelNameZh.trim()) {
      form.modelNameZh = `${form.sourceTableName}.${form.sourceColumnName} 字段修改`
    }
    ElMessage.success('已加载源字段，可继续修改后保存')
  } finally {
    fieldsLoading.value = false
  }
}

function formatTime(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function resetDialogForm() {
  form.modelNameZh = ''
  form.modelNameEn = ''
  form.modelType = 'TABLE'
  form.columnAction = 'ADD'
  form.categoryId = undefined
  form.metaDataSourceId = undefined
  form.sourceTableName = ''
  form.sourceColumnName = ''
  fieldRows.value = [emptyField()]
  existingTableFields.value = []
  sourceTables.value = []
  sourceColumns.value = []
  dialogMetaDataSources.value = []
  editingId.value = null
}

function emptyColumnField(action: 'ADD' | 'MODIFY'): MetaFieldDef {
  return { ...emptyField(), action }
}

async function openCreateDialog() {
  dialogMode.value = 'create'
  resetDialogForm()
  dialogVisible.value = true
  dialogBootstrapping.value = true
  try {
    await Promise.all([loadCategories(), loadAllMetaDataSources()])
    form.categoryId = categories.value[0]?.id
    if (form.categoryId) {
      await loadDialogMetaDataSources(form.categoryId)
    }
  } finally {
    await nextTick()
    dialogBootstrapping.value = false
  }
}

async function openEditDialog(row: MetaModel) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  const savedSourceId = row.metaDataSourceId
  const savedTableName = row.sourceTableName || ''
  const savedColumnName = row.sourceColumnName || ''
  form.modelNameZh = row.modelNameZh
  form.modelNameEn = row.modelNameEn || ''
  form.modelType = (row.modelType === 'COLUMN' ? 'COLUMN' : 'TABLE')
  form.metaDataSourceId = undefined
  form.sourceTableName = ''
  form.sourceColumnName = ''
  const parsed = parseFieldDefs(row.contentJson)
  if (form.modelType === 'COLUMN') {
    form.columnAction = parsed[0]?.action === 'MODIFY' ? 'MODIFY' : 'ADD'
    fieldRows.value = parsed.length ? parsed : [emptyColumnField(form.columnAction)]
  } else {
    fieldRows.value = parsed.length ? parsed : [emptyField()]
  }
  dialogVisible.value = true
  dialogBootstrapping.value = true
  try {
    await Promise.all([loadCategories(), loadAllMetaDataSources()])
    form.categoryId = resolveCategoryForSource(savedSourceId)
    if (form.categoryId) {
      await loadDialogMetaDataSources(form.categoryId)
    }
    form.metaDataSourceId = savedSourceId
    form.sourceTableName = savedTableName
    form.sourceColumnName = savedColumnName
    if (form.modelType === 'COLUMN' && form.metaDataSourceId) {
      await loadSourceTables()
      if (form.sourceTableName) {
        await loadSourceColumnNames()
        if (parsed[0]?.action === 'ADD' || parsed[0]?.action === 'MODIFY') {
          form.columnAction = parsed[0].action
        } else if (form.sourceColumnName && sourceColumns.value.includes(form.sourceColumnName)) {
          form.columnAction = 'MODIFY'
        } else {
          form.columnAction = 'ADD'
        }
        if (fieldRows.value[0]) fieldRows.value[0].action = form.columnAction
      }
    }
  } finally {
    // 等 watch 回调排空后再关闭引导标志，避免回填的表名被清空
    await nextTick()
    dialogBootstrapping.value = false
  }
}

function addFieldRow() {
  if (form.modelType === 'COLUMN' && form.columnAction === 'MODIFY') {
    ElMessage.info('修改字段每次仅定义一个字段')
    return
  }
  if (form.modelType === 'COLUMN') {
    fieldRows.value.push(emptyColumnField('ADD'))
    return
  }
  fieldRows.value.push(emptyField())
}

function removeFieldRow(index: number) {
  if (fieldRows.value.length <= 1) {
    fieldRows.value[0] = form.modelType === 'COLUMN' && form.columnAction === 'ADD'
      ? emptyColumnField('ADD')
      : emptyField()
    return
  }
  fieldRows.value.splice(index, 1)
}

function suggestAddFieldModelName(validFields: MetaFieldDef[]) {
  if (!form.sourceTableName.trim()) return
  const codes = validFields.map(f => f.code.trim()).filter(Boolean)
  if (!codes.length) return
  if (codes.length === 1) {
    form.modelNameZh = `${form.sourceTableName.trim()}.${codes[0]} 新增字段`
  } else {
    form.modelNameZh = `${form.sourceTableName.trim()} 新增${codes.length}个字段`
  }
}

function buildPayload() {
  const validFields = fieldRows.value.filter(f => f.code.trim())
  if (form.modelType === 'COLUMN') {
    if (form.columnAction === 'MODIFY') {
      if (validFields.length > 1) validFields.splice(1)
      if (validFields[0]) validFields[0].action = 'MODIFY'
      const colCode = form.sourceColumnName.trim() || validFields[0]?.code
      return {
        modelNameZh: form.modelNameZh.trim(),
        modelNameEn: form.modelNameEn.trim() || undefined,
        modelType: form.modelType,
        metaDataSourceId: form.metaDataSourceId,
        sourceTableName: form.sourceTableName.trim() || undefined,
        sourceColumnName: colCode || undefined,
        contentJson: stringifyFieldDefs(validFields),
      }
    }
    validFields.forEach(f => { f.action = 'ADD' })
    const codes = validFields.map(f => f.code.trim())
    return {
      modelNameZh: form.modelNameZh.trim(),
      modelNameEn: form.modelNameEn.trim() || undefined,
      modelType: form.modelType,
      metaDataSourceId: form.metaDataSourceId,
      sourceTableName: form.sourceTableName.trim() || undefined,
      sourceColumnName: codes.length === 1 ? codes[0] : codes.join(','),
      contentJson: stringifyFieldDefs(validFields),
    }
  }
  return {
    modelNameZh: form.modelNameZh.trim(),
    modelNameEn: form.modelNameEn.trim() || undefined,
    modelType: form.modelType,
    metaDataSourceId: form.metaDataSourceId,
    sourceTableName: form.sourceTableName.trim() || undefined,
    contentJson: stringifyFieldDefs(validFields),
  }
}

async function loadModels() {
  try {
    models.value = (await api.get('/governance/platform/metadata/models')).data || []
    doQuery()
  } catch (e) {
    ElMessage.error((e as Error).message || '加载模型列表失败')
  }
}

watch(() => form.categoryId, async (id) => {
  if (dialogBootstrapping.value) return
  form.metaDataSourceId = undefined
  form.sourceTableName = ''
  form.sourceColumnName = ''
  sourceTables.value = []
  sourceColumns.value = []
  existingTableFields.value = []
  await loadDialogMetaDataSources(id)
})

watch(() => form.metaDataSourceId, async (id) => {
  if (dialogBootstrapping.value) return
  form.sourceTableName = ''
  form.sourceColumnName = ''
  sourceColumns.value = []
  existingTableFields.value = []
  if (id && form.modelType === 'COLUMN') await loadSourceTables()
  else sourceTables.value = []
})

watch(() => form.sourceTableName, async (tbl) => {
  if (form.modelType === 'TABLE') {
    form.sourceColumnName = ''
    sourceColumns.value = []
    if (tbl.trim() && !form.modelNameZh.trim()) {
      form.modelNameZh = `${tbl.trim()} 表模型`
    }
    return
  }
  form.sourceColumnName = ''
  if (tbl && form.metaDataSourceId) {
    await loadExistingTableFields()
    if (form.columnAction === 'ADD' && !form.modelNameZh.trim()) {
      form.modelNameZh = `${tbl.trim()} 新增字段`
    }
  } else {
    existingTableFields.value = []
    sourceColumns.value = []
  }
})

watch(() => form.modelType, async (t) => {
  if (dialogBootstrapping.value) return
  form.sourceTableName = ''
  form.sourceColumnName = ''
  form.columnAction = 'ADD'
  sourceColumns.value = []
  existingTableFields.value = []
  fieldRows.value = t === 'COLUMN' ? [emptyColumnField('ADD')] : [emptyField()]
  if (t === 'COLUMN' && form.metaDataSourceId) await loadSourceTables()
  else sourceTables.value = []
})

watch(() => form.columnAction, async (action) => {
  if (form.modelType !== 'COLUMN') return
  form.sourceColumnName = ''
  fieldRows.value = [emptyColumnField(action)]
  if (action === 'ADD' && form.sourceTableName && form.metaDataSourceId) {
    await loadExistingTableFields()
    if (!form.modelNameZh.trim()) {
      form.modelNameZh = `${form.sourceTableName} 新增字段`
    }
  }
})

watch(() => form.sourceColumnName, async (col) => {
  if (form.modelType !== 'COLUMN' || form.columnAction !== 'MODIFY') return
  if (!col || !form.metaDataSourceId || !form.sourceTableName) return
  fieldsLoading.value = true
  try {
    const data = (await api.get(
      `/governance/platform/metadata/models/meta-data-sources/${form.metaDataSourceId}/table-columns`,
      { params: { tableName: form.sourceTableName, columnName: col } },
    )).data
    const fields = mapProbeFields(data?.fields || [], 'MODIFY')
    if (fields.length) {
      fieldRows.value = fields
      if (!form.modelNameZh.trim()) {
        form.modelNameZh = `${form.sourceTableName}.${col} 字段修改`
      }
    }
  } finally {
    fieldsLoading.value = false
  }
})

onMounted(async () => {
  await Promise.all([loadModels(), loadAllMetaDataSources()])
})

async function saveDialog() {
  if (!form.categoryId) {
    ElMessage.warning('请选择数据分类')
    return
  }
  if (!form.metaDataSourceId) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!form.sourceTableName.trim()) {
    ElMessage.warning(form.modelType === 'TABLE' ? '请填写新表名' : '请选择源表')
    return
  }
  const validFields = fieldRows.value.filter(f => f.code.trim())
  if (form.modelType === 'TABLE' && !validFields.length) {
    ElMessage.warning('表模型请至少设计一个字段')
    return
  }
  if (form.modelType === 'COLUMN') {
    if (!validFields.length) {
      ElMessage.warning(form.columnAction === 'ADD' ? '请至少填写一个新增字段编码' : '请加载或填写字段规范')
      return
    }
    if (form.columnAction === 'MODIFY') {
      if (!form.sourceColumnName.trim()) {
        ElMessage.warning('修改字段请选择源字段')
        return
      }
    } else {
      const existingSet = new Set(existingTableFields.value.map(f => f.code.toLowerCase()))
      const seen = new Set<string>()
      for (const f of validFields) {
        const code = f.code.trim().toLowerCase()
        if (seen.has(code)) {
          ElMessage.warning(`新增字段编码重复: ${f.code}`)
          return
        }
        seen.add(code)
        if (existingSet.has(code)) {
          ElMessage.warning(`字段 ${f.code} 已存在于源表中，请使用「修改字段」或更换编码`)
          return
        }
      }
      if (!form.modelNameZh.trim()) {
        suggestAddFieldModelName(validFields)
      }
    }
  }
  if (!form.modelNameZh.trim()) {
    ElMessage.warning('请填写中文名称')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (dialogMode.value === 'create') {
      await api.post('/governance/platform/metadata/models', payload)
      ElMessage.success('元模型已保存为草稿，物理库表结构已同步；请发布后生效')
    } else if (editingId.value) {
      await api.put(`/governance/platform/metadata/models/${editingId.value}`, payload)
      ElMessage.success('元模型已保存，物理库表结构已同步')
    }
    dialogVisible.value = false
    await loadModels()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function syncPhysical() {
  if (!detailModel.value) return
  syncingPhysical.value = true
  try {
    const res = (await api.post(`/governance/platform/metadata/models/${detailModel.value.id}/sync-physical`)).data
    ElMessage.success(String(res?.message || '物理库表结构已同步'))
  } catch (e) {
    ElMessage.error((e as Error).message || '同步物理库失败')
  } finally {
    syncingPhysical.value = false
  }
}

async function publishModel(row: MetaModel) {
  try {
    await api.post(`/governance/platform/metadata/models/${row.id}/publish`)
    ElMessage.success('元模型已发布')
    await loadModels()
  } catch (e) {
    ElMessage.error((e as Error).message || '发布失败')
  }
}

async function offlineModel(row: MetaModel) {
  try {
    await ElMessageBox.confirm(`确认下线元模型「${row.modelNameZh}」？`, '下线确认', { type: 'warning' })
    await api.post(`/governance/platform/metadata/models/${row.id}/offline`)
    ElMessage.success('元模型已下线')
    await loadModels()
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '下线失败')
  }
}

async function deleteModel(row: MetaModel) {
  try {
    await ElMessageBox.confirm(
      `确认删除元模型「${row.modelNameZh}」？仅删除登记记录，不会 DROP 物理表。`,
      '删除确认',
      { type: 'warning' },
    )
    await api.delete(`/governance/platform/metadata/models/${row.id}`)
    ElMessage.success('元模型已删除')
    await loadModels()
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function doCompare() {
  if (!compareForm.leftId || !compareForm.rightId) {
    ElMessage.warning('请选择两个模型')
    return
  }
  compare.value = (await api.get('/governance/platform/metadata/models/compare', {
    params: { leftId: compareForm.leftId, rightId: compareForm.rightId },
  })).data
}

async function doExport() {
  const res = await api.get('/governance/platform/metadata/models/export')
  const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'meta-models.json'
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${res.data.count ?? 0} 个模型`)
}

function triggerImport() {
  importInputRef.value?.click()
}

async function onImportFile(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const text = await file.text()
    const parsed = JSON.parse(text) as { models?: unknown[] }
    const modelsPayload = Array.isArray(parsed.models) ? parsed.models : (Array.isArray(parsed) ? parsed : [])
    if (!modelsPayload.length) {
      ElMessage.warning('JSON 中未找到 models 数组')
      return
    }
    const n = (await api.post('/governance/platform/metadata/models/import', { models: modelsPayload })).data
    ElMessage.success(`已导入 ${n} 个模型`)
    await loadModels()
  } catch {
    ElMessage.error('导入失败，请检查 JSON 格式')
  }
}

async function openFromEntryDialog() {
  fromEntryVisible.value = true
  fromEntryId.value = undefined
  fromEntryLoading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/catalog/search', { params: { type: 'asset' } })
    const list: CatalogEntry[] = res.data || []
    catalogEntries.value = list.filter(e => e.entryType === 'TABLE')
  } finally {
    fromEntryLoading.value = false
  }
}

async function submitFromEntry() {
  if (!fromEntryId.value) {
    ElMessage.warning('请选择数据表条目')
    return
  }
  await api.post('/governance/platform/metadata/models/from-entry', { entryId: fromEntryId.value })
  ElMessage.success('已从目录条目生成元模型')
  fromEntryVisible.value = false
  await loadModels()
}

async function openDetail(row: MetaModel) {
  detailModel.value = row
  detailTab.value = 'fields'
  detailVisible.value = true
  recheckResult.value = null
  modelVersions.value = []
  await Promise.all([loadBindings(row.id), loadModelVersions(row.id)])
}

async function loadModelVersions(modelId: number) {
  modelVersions.value = (await api.get('/governance/platform/metadata/versions', {
    params: { targetType: 'MODEL', targetId: modelId },
  })).data || []
}

async function loadBindings(modelId: number) {
  bindingsLoading.value = true
  try {
    const res = await api.get(`/governance/platform/metadata/models/${modelId}/bindings`)
    bindingTables.value = res.data?.tables || []
    bindingTasks.value = res.data?.collectTasks || []
  } finally {
    bindingsLoading.value = false
  }
}

async function doRecheck() {
  if (!detailModel.value) return
  rechecking.value = true
  try {
    recheckResult.value = (await api.post(`/governance/platform/metadata/models/${detailModel.value.id}/recheck`)).data
    ElMessage.success('符合度复检完成')
    await loadBindings(detailModel.value.id)
  } finally {
    rechecking.value = false
  }
}
</script>

<template>
  <PageCard title="元模型管理">
    <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="doQuery">
      <el-form-item label="状态" class="portal-field-sm">
        <el-select v-model="queryStatus" clearable placeholder="全部">
          <el-option v-for="o in STATUS_FILTER_OPTIONS" :key="o.value || 'all'" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="关键字" class="portal-field-lg">
        <el-input v-model="queryKeyword" clearable placeholder="中文名/英文名/编码" @keyup.enter="doQuery" />
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="doQuery">查询</el-button>
        <el-button type="primary" @click="openCreateDialog">新建</el-button>
        <el-button @click="doExport">导出</el-button>
        <el-button @click="triggerImport">导入</el-button>
        <el-button @click="openFromEntryDialog">从目录条目生成</el-button>
      </el-form-item>
    </el-form>
    <input ref="importInputRef" type="file" accept=".json,application/json" style="display:none" @change="onImportFile">

    <el-table :data="pagedModels" stripe size="small">
      <el-table-column label="名称" min-width="180">
        <template #default="{ row }">
          <div>{{ row.modelNameZh }}</div>
          <div class="meta-secondary">{{ row.modelCode }} · {{ row.modelNameEn || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          {{ MODEL_TYPE_OPTIONS.find(o => o.value === row.modelType)?.label || statusLabel(row.modelType) }}
        </template>
      </el-table-column>
      <el-table-column label="数据源/表名" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="sourceLabel(row)">{{ sourceLabel(row) }}</span>
          <span v-else class="meta-secondary">—</span>
        </template>
      </el-table-column>
      <el-table-column label="字段数" width="80" align="center">
        <template #default="{ row }">{{ fieldCountOf(row.contentJson) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="$statusTagType(row.status)" size="small">
            {{ modelStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button
            v-if="row.status === 'DRAFT' || row.status === 'OFFLINE'"
            link
            type="success"
            @click="publishModel(row)"
          >发布</el-button>
          <el-button
            v-if="row.status === 'PUBLISHED'"
            link
            type="warning"
            @click="offlineModel(row)"
          >下线</el-button>
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="row.status !== 'PUBLISHED'"
            link
            type="danger"
            @click="deleteModel(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination
      v-model:page="modelPage"
      v-model:page-size="modelPageSize"
      :total="modelTotal"
    />

    <el-divider content-position="left">模型比对</el-divider>
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="基准模型" class="portal-field-lg">
        <el-select v-model="compareForm.leftId" clearable filterable placeholder="选择模型">
          <el-option v-for="m in models" :key="m.id" :label="`${m.modelNameZh} (${m.modelCode})`" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="对比模型" class="portal-field-lg">
        <el-select v-model="compareForm.rightId" clearable filterable placeholder="选择模型">
          <el-option v-for="m in models" :key="'r' + m.id" :label="`${m.modelNameZh} (${m.modelCode})`" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="doCompare">开始比对</el-button>
      </el-form-item>
    </el-form>
    <template v-if="compare">
      <el-alert
        :type="compare.sameContent ? 'success' : 'warning'"
        :closable="false"
        :title="compare.sameContent
          ? '两模型结构与基本信息一致'
          : `结构/属性存在差异 · 左 ${compare.leftFieldCount ?? '—'} 字段 · 右 ${compare.rightFieldCount ?? '—'} 字段`"
        style="margin-bottom:12px"
      />
      <div v-if="basicDiff.length" class="meta-section">基本信息差异</div>
      <el-table v-if="basicDiff.length" :data="basicDiff" stripe size="small" style="margin-bottom:12px">
        <el-table-column label="项" width="120">
          <template #default="{ row }">{{ fieldLabel(row.field) }}</template>
        </el-table-column>
        <el-table-column prop="left" label="基准" min-width="140" show-overflow-tooltip />
        <el-table-column prop="right" label="对比" min-width="140" show-overflow-tooltip />
      </el-table>
      <div v-if="fieldDiff" class="meta-diff">
        <div v-if="fieldDiff.added?.length" class="meta-diff__block">
          <span class="meta-diff__label">新增字段：</span>
          <el-tag v-for="f in fieldDiff.added" :key="'a' + f" size="small" type="success" style="margin:2px">{{ f }}</el-tag>
        </div>
        <div v-if="fieldDiff.removed?.length" class="meta-diff__block">
          <span class="meta-diff__label">移除字段：</span>
          <el-tag v-for="f in fieldDiff.removed" :key="'r' + f" size="small" type="danger" style="margin:2px">{{ f }}</el-tag>
        </div>
        <div v-if="fieldDiff.changed?.length" class="meta-diff__block">
          <span class="meta-diff__label">变更字段：</span>
          <el-tag v-for="f in fieldDiff.changed" :key="'c' + f" size="small" type="warning" style="margin:2px">{{ f }}</el-tag>
        </div>
      </div>
      <div v-if="fieldAttrDiff.length" class="meta-section">字段属性差异明细</div>
      <el-table v-if="fieldAttrDiff.length" :data="fieldAttrDiff" stripe size="small" max-height="280">
        <el-table-column prop="fieldCode" label="字段" width="120" />
        <el-table-column label="变更" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.changeType === 'added' ? 'success' : row.changeType === 'removed' ? 'danger' : 'warning'">
              {{ row.changeType === 'added' ? '新增' : row.changeType === 'removed' ? '删除' : '变更' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="left" label="基准(JSON)" min-width="180" show-overflow-tooltip />
        <el-table-column prop="right" label="对比(JSON)" min-width="180" show-overflow-tooltip />
      </el-table>
      <el-empty
        v-if="!basicDiff.length && !fieldDiff?.added?.length && !fieldDiff?.removed?.length && !fieldDiff?.changed?.length && !fieldAttrDiff.length"
        description="两模型无差异"
        :image-size="48"
      />
    </template>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建元模型' : '编辑元模型'"
      width="920px"
      destroy-on-close
    >
      <el-form label-width="96px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="模型类型" required>
              <el-select v-model="form.modelType" style="width:100%" :disabled="dialogMode === 'edit'">
                <el-option v-for="o in MODEL_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据分类" required>
              <el-select
                v-model="form.categoryId"
                filterable
                clearable
                placeholder="先选择分类"
                style="width:100%"
                :loading="categoriesLoading"
              >
                <el-option
                  v-for="c in categories"
                  :key="c.id"
                  :label="categoryOptionLabel(c)"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据源" required>
              <el-select
                v-model="form.metaDataSourceId"
                filterable
                clearable
                placeholder="再选择该分类下的数据源"
                style="width:100%"
                :loading="metaDataSourcesLoading"
                :disabled="!form.categoryId"
              >
                <el-option
                  v-for="s in dialogMetaDataSources"
                  :key="s.id"
                  :label="dataSourceOptionLabel(s)"
                  :value="s.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.modelType === 'TABLE'" :span="12">
            <el-form-item label="表名" required>
              <el-input
                v-model="form.sourceTableName"
                placeholder="在该数据源下新建表的物理表名"
                :disabled="!form.metaDataSourceId || dialogMode === 'edit'"
              />
            </el-form-item>
          </el-col>
          <el-col v-else :span="12">
            <el-form-item label="源表" required>
              <el-select
                v-model="form.sourceTableName"
                filterable
                clearable
                placeholder="选择已有表"
                style="width:100%"
                :loading="sourceTablesLoading"
                :disabled="!form.metaDataSourceId"
              >
                <el-option
                  v-for="t in sourceTables"
                  :key="t.tableName"
                  :label="t.tableName"
                  :value="t.tableName"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="form.modelType === 'COLUMN'" :span="12">
            <el-form-item label="操作类型" required>
              <el-radio-group v-model="form.columnAction">
                <el-radio v-for="o in COLUMN_ACTION_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-if="form.modelType === 'COLUMN' && form.columnAction === 'MODIFY'" :span="12">
            <el-form-item label="源字段" required>
              <el-select
                v-model="form.sourceColumnName"
                filterable
                clearable
                placeholder="选择要修改的已有字段"
                style="width:100%"
                :loading="sourceColumnsLoading"
                :disabled="!form.sourceTableName"
              >
                <el-option v-for="c in sourceColumns" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="中文名称" required>
              <el-input v-model="form.modelNameZh" placeholder="业务可读名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="英文名称">
              <el-input
                v-model="form.modelNameEn"
                :disabled="dialogMode === 'edit'"
                :placeholder="dialogMode === 'edit' ? '唯一标识，编辑时不可修改' : '唯一标识，留空则保存时用编码'"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-alert
          v-if="!categoriesLoading && !categories.length"
          type="warning"
          :closable="false"
          title="暂无数据分类，请先在「数据源分类管理」中创建分类。"
          style="margin-bottom:12px"
        />
        <el-alert
          v-else-if="form.categoryId && !metaDataSourcesLoading && !dialogMetaDataSources.length"
          type="warning"
          :closable="false"
          title="该分类下暂无数据源，请先在「数据源管理」中在该分类下创建并配置 JDBC。"
          style="margin-bottom:12px"
        />
        <template v-if="form.modelType === 'COLUMN' && form.columnAction === 'ADD'">
          <el-divider content-position="left">
            现有字段（{{ existingTableFields.length }}）
          </el-divider>
          <el-table
            v-loading="sourceColumnsLoading"
            :data="existingTableFields"
            size="small"
            stripe
            border
            max-height="200"
            empty-text="请先选择源表，或该表暂无字段"
            style="margin-bottom:12px"
          >
            <el-table-column prop="code" label="编码" width="130" />
            <el-table-column prop="name" label="名称" width="130" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="length" label="长度" width="70" />
            <el-table-column label="必填" width="70">
              <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="hint" label="说明" min-width="120" show-overflow-tooltip />
          </el-table>
          <el-divider content-position="left">待新增字段</el-divider>
        </template>
        <el-divider v-else content-position="left">{{ form.modelType === 'TABLE' ? '表字段设计' : '字段规范' }}</el-divider>
        <div style="margin-bottom:8px;display:flex;gap:8px;align-items:center;flex-wrap:wrap">
          <el-button
            v-if="form.modelType === 'TABLE' || (form.modelType === 'COLUMN' && form.columnAction === 'ADD')"
            size="small"
            type="primary"
            @click="addFieldRow"
          >
            添加字段
          </el-button>
          <el-button
            v-else-if="form.columnAction === 'MODIFY'"
            type="primary"
            size="small"
            :loading="fieldsLoading"
            :disabled="!form.metaDataSourceId || !form.sourceTableName || !form.sourceColumnName"
            @click="loadColumnFromSource"
          >
            从源加载字段
          </el-button>
          <el-text v-if="form.modelType === 'TABLE'" type="info" size="small">逐行添加字段编码、类型、长度等，保存为表结构规范</el-text>
          <el-text v-else-if="form.columnAction === 'ADD'" type="info" size="small">在下方逐行填写待新增字段，可与现有字段对照避免重名</el-text>
          <el-text v-else type="info" size="small">选择源字段后自动加载，或点击加载按钮，修改后保存</el-text>
        </div>
        <el-table v-loading="fieldsLoading" :data="fieldRows" size="small" border max-height="320">
          <el-table-column v-if="form.modelType === 'COLUMN'" label="操作" width="80">
            <template #default="{ row }">{{ columnActionLabel(row.action || form.columnAction) }}</template>
          </el-table-column>
          <el-table-column label="编码" width="130">
            <template #default="{ row }">
              <el-input
                v-model="row.code"
                size="small"
                :placeholder="form.modelType === 'COLUMN' && form.columnAction === 'ADD' ? '新字段编码' : 'code'"
                :disabled="form.modelType === 'COLUMN' && form.columnAction === 'MODIFY'"
              />
            </template>
          </el-table-column>
          <el-table-column label="名称" width="130">
            <template #default="{ row }">
              <el-input v-model="row.name" size="small" placeholder="名称" />
            </template>
          </el-table-column>
          <el-table-column label="类型" width="130">
            <template #default="{ row }">
              <el-select v-model="row.type" size="small" filterable allow-create style="width:100%">
                <el-option v-for="t in FIELD_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="长度" width="90">
            <template #default="{ row }">
              <el-input-number v-model="row.length" size="small" :min="0" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="必填" width="70" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.required" />
            </template>
          </el-table-column>
          <el-table-column label="提示说明" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.hint" size="small" placeholder="必填项提示" />
            </template>
          </el-table-column>
          <el-table-column label="主键" width="70" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.primaryKey" />
            </template>
          </el-table-column>
          <el-table-column
            v-if="form.modelType === 'TABLE' || (form.modelType === 'COLUMN' && form.columnAction === 'ADD')"
            label=""
            width="60"
            align="center"
          >
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeFieldRow($index)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDialog">保存</el-button>
      </template>
    </el-dialog>

    <!-- 从目录条目生成 -->
    <el-dialog v-model="fromEntryVisible" title="从目录条目生成元模型" width="520px">
      <el-form label-width="88px">
        <el-form-item label="数据表">
          <el-select
            v-model="fromEntryId"
            filterable
            clearable
            placeholder="选择 TABLE 类型条目"
            style="width:100%"
            :loading="fromEntryLoading"
          >
            <el-option
              v-for="e in catalogEntries"
              :key="e.id"
              :label="`${e.entryName} (${e.entryCode})`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fromEntryVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFromEntry">生成</el-button>
      </template>
    </el-dialog>

    <!-- 详情：字段 / 绑定 / 版本 -->
    <el-drawer v-model="detailVisible" :title="detailModel ? `元模型 · ${detailModel.modelNameZh}` : '元模型详情'" size="640px">
      <div v-loading="bindingsLoading">
        <div v-if="detailModel" style="margin-bottom:12px;display:flex;gap:8px;flex-wrap:wrap">
          <el-button
            type="primary"
            size="small"
            :loading="syncingPhysical"
            :disabled="!detailModel.metaDataSourceId || !detailModel.sourceTableName"
            @click="syncPhysical"
          >
            同步到物理库
          </el-button>
          <el-text type="info" size="small">在 {{ detailModel.sourceTableName || '—' }} 所在库执行建表/加列</el-text>
        </div>
        <el-descriptions v-if="detailModel" :column="2" border size="small" style="margin-bottom:12px">
          <el-descriptions-item label="编码">{{ detailModel.modelCode }}</el-descriptions-item>
          <el-descriptions-item label="英文名">{{ detailModel.modelNameEn || '—' }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            {{ MODEL_TYPE_OPTIONS.find(o => o.value === detailModel.modelType)?.label || statusLabel(detailModel.modelType) }}
          </el-descriptions-item>
          <el-descriptions-item label="数据源/表名">
            {{ sourceLabel(detailModel) || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">{{ modelStatusLabel(detailModel.status) }}</el-descriptions-item>
          <el-descriptions-item label="字段数">{{ detailFields.length }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailTab">
          <el-tab-pane label="字段设计" name="fields" />
          <el-tab-pane label="绑定与符合度" name="bindings" />
          <el-tab-pane label="版本历史" name="versions" />
        </el-tabs>

        <template v-if="detailTab === 'fields'">
          <el-table :data="detailFields" size="small" stripe max-height="360" empty-text="暂无字段">
            <el-table-column v-if="detailModel?.modelType === 'COLUMN'" label="操作" width="90">
              <template #default="{ row }">{{ columnActionLabel(row.action) }}</template>
            </el-table-column>
            <el-table-column prop="code" label="编码" width="120" />
            <el-table-column prop="name" label="名称" width="120" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="length" label="长度" width="70" />
            <el-table-column label="必填" width="70">
              <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="hint" label="提示说明" min-width="120" show-overflow-tooltip />
          </el-table>
        </template>

        <template v-else-if="detailTab === 'bindings'">
          <div style="margin-bottom:12px">
            <el-button type="primary" size="small" :loading="rechecking" @click="doRecheck">符合度复检</el-button>
            <el-text type="info" size="small" style="margin-left:8px">保存后的模型可被采集任务绑定</el-text>
          </div>
          <el-alert
            v-if="recheckResult"
            type="success"
            :closable="false"
            :title="`复检：共 ${recheckResult.tableCount} 表 · 通过 ${recheckResult.pass} · 部分 ${recheckResult.partial} · 不通过 ${recheckResult.fail}`"
            style="margin-bottom:12px"
          />
          <div class="meta-section">绑定数据表</div>
          <el-table :data="bindingTables" size="small" empty-text="暂无绑定表">
            <el-table-column prop="entryName" label="表名" />
            <el-table-column label="分层" width="80">
              <template #default="{ row }">{{ statusLabel(row.dataLayer || '') }}</template>
            </el-table-column>
            <el-table-column label="符合度" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.conformStatus" :type="$statusTagType(row.conformStatus)" size="small">
                  {{ statusLabel(row.conformStatus) }}
                </el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="meta-section">关联采集任务</div>
          <el-table :data="bindingTasks" size="small" empty-text="暂无关联任务">
            <el-table-column prop="taskName" label="任务" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="$statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </template>

        <template v-else>
          <el-table :data="modelVersions" size="small" stripe empty-text="暂无版本">
            <el-table-column label="版本" width="80">
              <template #default="{ row }">v{{ row.versionNo }}</template>
            </el-table-column>
            <el-table-column prop="changeSummary" label="说明" min-width="160" show-overflow-tooltip />
            <el-table-column prop="createdBy" label="操作人" width="100" />
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>
  </PageCard>
</template>

<style scoped>
.meta-secondary {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.meta-diff__block {
  margin-bottom: 8px;
}
.meta-diff__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-right: 6px;
}
.meta-section {
  margin: 12px 0 8px;
  font-size: 13px;
  font-weight: 600;
  padding-left: 8px;
  border-left: 3px solid #1677ff;
}
</style>
