<script setup lang="ts">
import { computed, markRaw, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow, addEdge, updateEdge, type Connection, type Edge, type Node, MarkerType } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'
import { useAuthStore } from '@/stores/auth'
import {
  GOVERNANCE_COMPONENTS,
  GROUP_LABELS,
  loadEnabledTypes,
  findComponent,
  type CompGroup,
} from './governance-components'
import GovFlowNode from './props/GovFlowNode.vue'
import GovFieldSelect from './props/GovFieldSelect.vue'
import {
  AGG_OPS,
  DELIMITER_OPTIONS,
  FIELD_PROCESS_EXPRS,
  FILTER_OPS,
  TYPE_OPTIONS,
  parseAggs,
  parseFilterConditions,
  parseJoinKeys,
  parseMappings,
  parseSortKeys,
  parseStringList,
  parseSwitchCases,
  parseValueMaps,
  roleFromHandles,
  type AggRow,
  type ConstantRow,
  type FilterCondRow,
  type JoinKeyRow,
  type LookupReturnRow,
  type MappingRow,
  type SelectFieldRow,
  type SortKeyRow,
  type SwitchCaseRow,
  type TypeConvertRow,
  type ValidatorRow,
  type ValueMapRow,
} from './props/gov-prop-utils'

const nodeTypes = { gov: markRaw(GovFlowNode) }

const props = defineProps<{ taskId: number }>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
/** 本人用户名：优先 Pinia，兼容 localStorage；启用组件存储键与组件库共用 */
const currentUser = computed(() =>
  String(auth.user?.username || localStorage.getItem('username') || '').trim(),
)
const enabledStorageUser = computed(() => currentUser.value || 'system')

const PLATFORM_CONNECTIONS = [
  { value: 'smart_city_ods', label: '平台 ODS（smart_city_ods）' },
  { value: 'smart_city_dwd', label: '平台 DWD（smart_city_dwd）' },
  { value: 'smart_city_dws', label: '平台 DWS（smart_city_dws）' },
  { value: 'smart_city_ads', label: '平台 ADS（smart_city_ads）' },
]

const paletteItems = computed(() => {
  const enabled = new Set(loadEnabledTypes(enabledStorageUser.value))
  return GOVERNANCE_COMPONENTS.filter((c) => enabled.has(c.type))
})

const paletteGroups = computed(() => {
  const groups: CompGroup[] = ['io', 'govern', 'transform', 'extend']
  return groups
    .map((g) => ({
      key: g,
      label: GROUP_LABELS[g],
      items: paletteItems.value.filter((c) => c.group === g),
    }))
    .filter((g) => g.items.length > 0)
})

const connectionOptions = computed(() => [
  ...PLATFORM_CONNECTIONS,
  ...dataSources.value.map((ds) => ({
    value: `ds:${ds.id}`,
    label: `${ds.name}（${ds.code}）`,
  })),
])

const taskName = ref('')
const status = ref('')
const lockedBy = ref('')
const engineType = ref('KETTLE')
const saving = ref(false)
const running = ref(false)
const publishing = ref(false)
const importInput = ref<HTMLInputElement | null>(null)
const varDialogVisible = ref(false)
const varDefs = ref<Array<{ name: string; label?: string; defaultValue?: string; required?: boolean; description?: string }>>([])
const varForm = ref<Record<string, string>>({})

// 仅当确有锁定人且不是本人时禁用编辑
const isLockedByOther = computed(() => {
  if (status.value !== 'LOCKED') return false
  const by = String(lockedBy.value || '').trim()
  if (!by) return false
  const me = currentUser.value
  if (!me) return false
  return by !== me
})
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
const selectedId = ref<string | null>(null)
const selectedEdgeId = ref<string | null>(null)
const propsDialogVisible = ref(false)
const runLogVisible = ref(false)
const runLogs = ref<Array<{ nodeId: string; nodeType: string; nodeName?: string; status: string; inputRows: number; outputRows: number; message?: string }>>([])

function onConnect(params: Connection) {
  if (isLockedByOther.value) return
  if (!params.source || !params.target) return
  if (params.source === params.target) return
  const edgeRole = roleFromHandles(params.sourceHandle, params.targetHandle)
  const data: Record<string, unknown> = { edgeRole }
  if (edgeRole === 'CASE' && params.sourceHandle) {
    const src = nodes.value.find((n) => n.id === params.source)
    const cases = parseSwitchCases((src?.data?.config || {}) as Record<string, unknown>)
    const m = String(params.sourceHandle).match(/out_case_(\d+)/)
    if (m) {
      const idx = Number(m[1])
      if (cases[idx]) data.caseValue = cases[idx].value
    }
  }
  // @ts-expect-error vue-flow Edge type instantiation is excessively deep
  edges.value = addEdge(
    {
      ...params,
      id: `e_${params.source}_${params.target}_${Date.now()}`,
      markerEnd: MarkerType.ArrowClosed,
      label: edgeRole === 'COPY' ? undefined : edgeRole,
      data,
    },
    edges.value,
  )
}

function onEdgeClick(ev: { edge: Edge }) {
  if (isLockedByOther.value) return
  selectedEdgeId.value = ev.edge.id
  selectedId.value = null
}

function onEdgeUpdate(args: { edge: Edge; connection: Connection }) {
  if (isLockedByOther.value) return
  // @ts-expect-error vue-flow Edge type instantiation is excessively deep
  edges.value = updateEdge(args.edge, args.connection, edges.value)
}

function onPaneClick() {
  selectedEdgeId.value = null
}

const selectedNode = computed(() => nodes.value.find((n) => n.id === selectedId.value) || null)

/** 不需要上游连线即可配置的源节点 */
const SOURCE_NODE_TYPES = new Set(['INPUT', 'TEXT_INPUT', 'EXCEL_INPUT', 'HTTP', 'CONSTANT', 'SET_VARIABLE'])

const selectedNodeType = computed(() => String(selectedNode.value?.data?.nodeType || ''))

const needsUpstream = computed(() => {
  const t = selectedNodeType.value
  return !!t && !SOURCE_NODE_TYPES.has(t)
})

const hasUpstreamEdge = computed(() => {
  if (!selectedId.value) return false
  return edges.value.some((e) => e.target === selectedId.value)
})

/** 当前选中节点是否允许展示字段类属性 */
const canEditFieldProps = computed(() => {
  if (!selectedNode.value) return false
  if (!needsUpstream.value) return true
  return hasUpstreamEdge.value
})

const registeredTables = ref<Array<{ id: number; sourceId: number; tableName: string; tableCode?: string }>>([])
const upstreamFields = ref<string[]>([])
const loadingFields = ref(false)
const allowCustomField = ref(false)

/** INPUT 探表缓存：表名 + 列，供表名下拉与上游字段自动带入 */
const inputTableMeta = ref<Array<{ sourceTable: string; columns: string[] }>>([])
const inputTableOptions = computed(() => inputTableMeta.value.map((t) => t.sourceTable))
const loadingInputTables = ref(false)
const probedConnection = ref('')

const PLATFORM_LAYER_IDS: Record<string, number> = {
  smart_city_ods: -1,
  smart_city_dwd: -2,
  smart_city_dws: -3,
  smart_city_ads: -4,
}

function extractColumnNames(raw: unknown): string[] {
  if (!Array.isArray(raw)) return []
  return (raw as Array<Record<string, unknown>>)
    .map((c) => String(c.columnName || c.columnCode || c.name || '').trim())
    .filter(Boolean)
}

async function loadInputTables(connection?: string) {
  const conn = String(connection || propForm.connection || '').trim()
  if (!conn) {
    inputTableMeta.value = []
    probedConnection.value = ''
    return
  }
  loadingInputTables.value = true
  try {
    let sourceId: number | null = PLATFORM_LAYER_IDS[conn] ?? null
    if (sourceId == null && conn.startsWith('ds:')) {
      const n = Number(conn.slice(3))
      sourceId = Number.isFinite(n) ? n : null
    }
    if (sourceId == null) {
      inputTableMeta.value = []
      probedConnection.value = conn
      return
    }
    const rows = (await api.get(`/governance/platform/metadata/collect/data-sources/${sourceId}/tables`)).data || []
    inputTableMeta.value = (rows as Array<Record<string, unknown>>)
      .map((r) => ({
        sourceTable: String(r.sourceTable || r.tableName || r.name || '').trim(),
        columns: extractColumnNames(r.columns),
      }))
      .filter((t) => !!t.sourceTable)
    probedConnection.value = conn
  } catch {
    inputTableMeta.value = []
    probedConnection.value = conn
  } finally {
    loadingInputTables.value = false
  }
}

function readInputSourceConfig(): { mode: string; tableName: string; connection: string } | null {
  if (selectedNodeType.value === 'INPUT') {
    // 选中 INPUT 时以右侧表单为准（避免 debounce 未写回节点导致探表滞后）
    return {
      mode: String(propForm.inputMode || 'SAMPLE'),
      tableName: String(propForm.tableName || '').trim(),
      connection: String(propForm.connection || '').trim(),
    }
  }
  const input = selectedNodeType.value === 'OUTPUT' || selectedNodeType.value === 'INSERT_UPDATE'
    ? findUpstreamInputNode(selectedId.value || '')
    : upstreamInputNode.value
  if (!input) return null
  const cfg = (input.data?.config || {}) as Record<string, unknown>
  return {
    mode: String(cfg.inputMode || 'SAMPLE'),
    tableName: String(cfg.tableName || '').trim(),
    connection: String(cfg.connection || '').trim(),
  }
}

async function resolveUpstreamFields() {
  upstreamFields.value = []
  if (!canEditFieldProps.value && selectedNodeType.value !== 'OUTPUT' && selectedNodeType.value !== 'INSERT_UPDATE' && selectedNodeType.value !== 'INPUT') {
    return
  }
  const src = readInputSourceConfig()
  if (!src) return
  const { mode, tableName, connection } = src

  if (mode === 'SAMPLE') {
    upstreamFields.value = ['id', 'name', 'phone', 'idCard', 'amount', 'email']
    return
  }
  // SQL 模式无表名时无法静态探列；有 FROM 表名时可尝试
  let resolvedTable = tableName
  if (mode === 'SQL' && !resolvedTable && selectedNodeType.value === 'INPUT') {
    const m = String(propForm.sql || '').match(/\bfrom\s+[`"]?([a-zA-Z0-9_]+)[`"]?/i)
    if (m) resolvedTable = m[1]
  }
  if (!resolvedTable && mode !== 'TABLE') return
  if (!resolvedTable) return

  loadingFields.value = true
  try {
    if (connection && probedConnection.value !== connection) {
      await loadInputTables(connection)
    }
    const cached = inputTableMeta.value.find((t) => t.sourceTable === resolvedTable)
    if (cached?.columns?.length) {
      upstreamFields.value = [...cached.columns]
      return
    }
    if (connection) {
      await loadInputTables(connection)
      const again = inputTableMeta.value.find((t) => t.sourceTable === resolvedTable)
      if (again?.columns?.length) {
        upstreamFields.value = [...again.columns]
        return
      }
    }
    if (!registeredTables.value.length) {
      const res = await api.get('/exchange/ingestion/register/tables')
      registeredTables.value = (res.data || []).map((t: { id: number; sourceId: number; tableName: string; tableCode?: string }) => ({
        id: t.id,
        sourceId: t.sourceId,
        tableName: t.tableName,
        tableCode: t.tableCode,
      }))
    }
    let tableId: number | undefined
    if (connection.startsWith('ds:')) {
      const sid = Number(connection.slice(3))
      const hit = registeredTables.value.find(
        (t) => t.sourceId === sid && (t.tableName === resolvedTable || t.tableCode === resolvedTable),
      )
      tableId = hit?.id
    }
    if (tableId == null && resolvedTable) {
      const hit = registeredTables.value.find((t) => t.tableName === resolvedTable || t.tableCode === resolvedTable)
      tableId = hit?.id
    }
    if (tableId != null) {
      const cols = await api.get(`/exchange/ingestion/register/tables/${tableId}/columns`)
      upstreamFields.value = extractColumnNames(cols.data)
    }
  } catch {
    upstreamFields.value = []
  } finally {
    loadingFields.value = false
  }
}

async function onInputConnectionChange(conn: string | number | boolean | undefined) {
  const c = String(conn || '').trim()
  propForm.tableName = ''
  upstreamFields.value = []
  await loadInputTables(c)
  applyProps(true)
}

async function onInputTableChange() {
  applyProps(true)
  await resolveUpstreamFields()
}

function findUpstreamInputNode(nodeId: string, visited = new Set<string>()): Node | null {
  if (visited.has(nodeId)) return null
  visited.add(nodeId)
  const preds = edges.value.filter((e) => e.target === nodeId).map((e) => e.source)
  for (const pid of preds) {
    const n = nodes.value.find((x) => x.id === pid)
    if (!n) continue
    const t = String(n.data?.nodeType || '')
    if (t === 'INPUT' || t === 'TEXT_INPUT' || t === 'EXCEL_INPUT') return n
    const deeper = findUpstreamInputNode(pid, visited)
    if (deeper) return deeper
  }
  return null
}

const upstreamInputNode = computed(() => {
  if (!selectedId.value) return null
  if (!needsUpstream.value && selectedNodeType.value === 'OUTPUT') {
    return findUpstreamInputNode(selectedId.value)
  }
  if (!needsUpstream.value) return null
  return findUpstreamInputNode(selectedId.value)
})

const upstreamSourceTableName = computed(() => {
  const n = upstreamInputNode.value
  if (!n) return ''
  const cfg = (n.data?.config || {}) as Record<string, unknown>
  return String(cfg.tableName || '').trim()
})

watch(
  () => [selectedId.value, edges.value.length, nodes.value.map((n) => JSON.stringify(n.data?.config)).join('|')],
  () => { void resolveUpstreamFields() },
)

watch(upstreamSourceTableName, (tbl) => {
  if (!selectedNode.value) return
  const t = selectedNodeType.value
  if (t !== 'OUTPUT' && t !== 'INSERT_UPDATE') return
  if (propForm.outputTable && propForm.outputTable !== 'output_table') return
  suggestOutputTable(tbl)
})

function suggestOutputTable(sourceTable: string) {
  if (!sourceTable) return
  const base = sourceTable.replace(/^(ods_|dwd_|dws_|ads_)/i, '')
  if (propForm.allowOdsWriteback && propForm.outputConnection === 'smart_city_ods') {
    propForm.outputTable = `ods_${base}_gov`
  } else {
    if (!propForm.outputConnection || propForm.outputConnection === 'smart_city_ods') {
      propForm.outputConnection = 'smart_city_dwd'
    }
    const prefix = propForm.outputConnection.includes('dws')
      ? 'dws_'
      : propForm.outputConnection.includes('ads')
        ? 'ads_'
        : 'dwd_'
    propForm.outputTable = `${prefix}${base}`
  }
}

const outputConnectionOptions = computed(() => {
  const base = [
    { value: 'smart_city_dwd', label: '平台 DWD（smart_city_dwd）— 默认治理产出' },
    { value: 'smart_city_dws', label: '平台 DWS（smart_city_dws）' },
    { value: 'smart_city_ads', label: '平台 ADS（smart_city_ads）' },
  ]
  if (propForm.allowOdsWriteback) {
    return [
      { value: 'smart_city_ods', label: '平台 ODS（smart_city_ods）— 显式回写' },
      ...base,
    ]
  }
  return base
})

function validateGraphForRun(): string | null {
  const types = nodes.value.map((n) => String(n.data?.nodeType || ''))
  const hasOut = types.some((t) => t === 'OUTPUT' || t === 'INSERT_UPDATE' || t === 'TEXT_OUTPUT')
  if (!hasOut) {
    return '请先添加「输出」节点（或文本输出试跑），治理结果须明确写出目标'
  }
  for (const n of nodes.value) {
    const t = String(n.data?.nodeType || '')
    const label = String(n.data?.label || n.id)
    const cfg = (n.data?.config || {}) as Record<string, unknown>
    if (t === 'INPUT') {
      const mode = String(cfg.inputMode || 'TABLE')
      if (mode === 'TABLE') {
        const table = String(cfg.tableName || '').trim()
        const conn = String(cfg.connection || '').trim()
        if (!conn) return `输入节点「${label}」未选择数据源`
        if (!table || table === 'table_name') return `输入节点「${label}」指定表模式下未配置真实表名`
      } else if (mode === 'SQL') {
        const sql = String(cfg.sql || '').trim().toLowerCase().replace(/\s+/g, ' ')
        if (!sql || sql.includes('from table_name')) {
          return `输入节点「${label}」SQL 模式下请填写有效查询`
        }
      }
      continue
    }
    if (t !== 'OUTPUT' && t !== 'INSERT_UPDATE') continue
    const table = String(cfg.table || cfg.outputTable || '').trim()
    const conn = String(cfg.connection || cfg.outputConnection || '').trim()
    if (!table || table === 'output_table') {
      return `输出节点「${label}」未配置目标表`
    }
    if (conn === 'smart_city_ods' && !cfg.allowOdsWriteback) {
      return '写回 ODS 须在输出节点勾选「允许回写 ODS」'
    }
  }
  return null
}

function csvToList(csv: string): string[] {
  return String(csv || '').split(',').map((s) => s.trim()).filter(Boolean)
}

function listToCsv(list: string[]): string {
  return list.filter(Boolean).join(',')
}

// 字段处理映射（结构化）
const fieldMappings = computed(() => mappingRows.value)

// 样例数据字段预览（真实上游）
const sampleFields = computed(() => {
  const processedFields = new Set(mappingRows.value.map((m) => m.from))
  const newFields = new Set(mappingRows.value.filter((m) => m.from !== m.to).map((m) => m.to))
  const base = (upstreamFields.value.length ? upstreamFields.value : ['id', 'name']).map((name) => ({
    name,
    status: processedFields.has(name) ? 'processed' : 'untouched',
    label: processedFields.has(name) ? '已处理' : '未处理',
  }))
  const extras = Array.from(newFields)
    .filter((f) => !base.some((b) => b.name === f))
    .map((f) => ({ name: f, status: 'new', label: '新增' }))
  return base.concat(extras)
})

const dataSources = ref<Array<{ id: number; name: string; code: string }>>([])
const tables = ref<Array<{ name: string; schema?: string }>>([])
const loadingTables = ref(false)

const mappingRows = ref<MappingRow[]>([])
const selectFieldRows = ref<SelectFieldRow[]>([])
const typeConvertRows = ref<TypeConvertRow[]>([])
const filterConditions = ref<FilterCondRow[]>([])
const aggRows = ref<AggRow[]>([])
const sortKeyRows = ref<SortKeyRow[]>([])
const joinKeyRows = ref<JoinKeyRow[]>([])
const valueMapRows = ref<ValueMapRow[]>([])
const switchCases = ref<SwitchCaseRow[]>([])
const constantRows = ref<ConstantRow[]>([])
const validatorRows = ref<ValidatorRow[]>([])
const lookupReturnRows = ref<LookupReturnRow[]>([])
const splitTargetFields = ref<string[]>([])
const keepSource = ref(true)
const maskWriteMode = ref<'OVERWRITE' | 'NEW_COLUMN'>('OVERWRITE')
const maskTargetSuffix = ref('_masked')
const overwriteTarget = ref(true)
const filterLogic = ref<'AND' | 'OR'>('AND')
const varValueSource = ref<'CONST' | 'FIELD'>('CONST')
const delimiterMode = ref(',')
const encoding = ref('UTF-8')
const sheetName = ref('')
const matchKeysSelected = ref<string[]>([])
const updateFieldsSelected = ref<string[]>([])
const leftUpstreamFields = ref<string[]>([])
const rightUpstreamFields = ref<string[]>([])

const propForm = reactive({
  label: '',
  field: '',
  op: 'EQ',
  value: '',
  filterMode: 'SIMPLE' as 'SIMPLE' | 'SQL',
  sqlExpr: '',
  keys: '',
  sortKeys: '',
  keepStrategy: 'FIRST',
  fields: '',
  maskChar: '*',
  maskType: 'BLUR',
  rowCount: 10,
  mappings: '',
  leftKey: '',
  rightKey: '',
  joinType: 'INNER',
  sortField: '',
  sortOrder: 'ASC',
  groupBy: '',
  aggs: '',
  pivotField: '',
  valueField: '',
  groupFields: '',
  keyFields: '',
  unpivotColumns: '',
  valueColumnName: 'value',
  nameColumnName: 'attribute',
  inputMode: 'SAMPLE' as 'SAMPLE' | 'SQL' | 'TABLE',
  connection: '',
  sql: 'SELECT * FROM table_name',
  tableName: '',
  limit: 0,
  outputConnection: 'smart_city_dwd',
  outputTable: '',
  outputMode: 'INSERT' as 'INSERT' | 'TRUNCATE_INSERT' | 'UPDATE',
  commitSize: 1000,
  allowOdsWriteback: false,
  sourceField: '',
  delimiter: ',',
  targetFieldsCsv: '',
  targetField: '',
  fromValue: '',
  toValue: '',
  defaultValue: '',
  formula: '',
  cutFrom: 0,
  cutTo: 0,
  search: '',
  replace: '',
  replaceValue: '',
  replaceValueType: 'String',
  script: '',
  filePath: '',
  separator: ',',
  table: '',
  keyField: '',
  lookupKey: '',
  url: '',
  method: 'GET',
  httpTimeout: 30,
  resultField: '',
})

const keysSelected = computed({
  get: () => csvToList(propForm.keys),
  set: (v: string[]) => { propForm.keys = listToCsv(v) },
})
const sortKeysSelected = computed({
  get: () => csvToList(propForm.sortKeys),
  set: (v: string[]) => { propForm.sortKeys = listToCsv(v) },
})
const fieldsSelected = computed({
  get: () => csvToList(propForm.fields),
  set: (v: string[]) => { propForm.fields = listToCsv(v) },
})
const groupBySelected = computed({
  get: () => csvToList(propForm.groupBy),
  set: (v: string[]) => { propForm.groupBy = listToCsv(v) },
})
const groupFieldsSelected = computed({
  get: () => csvToList(propForm.groupFields),
  set: (v: string[]) => { propForm.groupFields = listToCsv(v) },
})
const keyFieldsSelected = computed({
  get: () => csvToList(propForm.keyFields),
  set: (v: string[]) => { propForm.keyFields = listToCsv(v) },
})
const unpivotColumnsSelected = computed({
  get: () => csvToList(propForm.unpivotColumns),
  set: (v: string[]) => { propForm.unpivotColumns = listToCsv(v) },
})

function addMappingRow() {
  mappingRows.value.push({ from: upstreamFields.value[0] || '', to: '', expr: 'COPY' })
}
function seedAllMappings() {
  mappingRows.value = upstreamFields.value.map((f) => ({ from: f, to: f, expr: 'COPY' }))
}
function addFilterCond() {
  filterConditions.value.push({ field: '', op: 'EQ', value: '', logic: filterLogic.value })
}
function addAggRow() {
  aggRows.value.push({ field: '', op: 'COUNT', alias: '' })
}
function addSortKey() {
  sortKeyRows.value.push({ field: '', order: 'ASC' })
}
function addJoinKey() {
  joinKeyRows.value.push({ leftKey: '', rightKey: '' })
}
function addValueMap() {
  valueMapRows.value.push({ fromValue: '', toValue: '' })
}
function addSwitchCase() {
  switchCases.value.push({ value: '', label: '' })
}
function addConstantRow() {
  constantRows.value.push({ field: '', value: '', valueType: 'String' })
}
function addValidatorRow() {
  validatorRows.value.push({ field: '', ruleType: 'NOT_NULL', param: '', onFail: 'REJECT' })
}
function addLookupReturn() {
  lookupReturnRows.value.push({ sourceColumn: '', targetField: '' })
}
function addSelectFieldRow() {
  selectFieldRows.value.push({ from: '', to: '', action: 'KEEP' })
}
function addTypeConvertRow() {
  typeConvertRows.value.push({ from: '', to: '', targetType: 'String', dateFormat: '' })
}
function addSplitTarget() {
  splitTargetFields.value.push(`col_${splitTargetFields.value.length + 1}`)
}
function insertFieldToFormula(f: string) {
  propForm.formula = `${propForm.formula || ''}[${f}]`
}
function insertFieldToScript(f: string) {
  propForm.script = `${propForm.script || ''}${f}`
}

const invalidSelectedFields = computed(() => {
  if (allowCustomField.value || !upstreamFields.value.length) return []
  const t = selectedNodeType.value
  const candidates: string[] = []
  switch (t) {
    case 'FILTER':
      if (propForm.filterMode === 'SIMPLE') candidates.push(...filterConditions.value.map((c) => c.field))
      break
    case 'DEDUPLICATE':
      candidates.push(...csvToList(propForm.keys), ...csvToList(propForm.sortKeys))
      break
    case 'MASK':
      candidates.push(...csvToList(propForm.fields))
      break
    case 'JOIN':
      candidates.push(...joinKeyRows.value.flatMap((j) => [j.leftKey, j.rightKey]))
      break
    case 'SORT':
      candidates.push(...sortKeyRows.value.map((s) => s.field))
      break
    case 'AGGREGATE':
      candidates.push(...csvToList(propForm.groupBy), ...aggRows.value.map((a) => a.field))
      break
    case 'PIVOT':
      candidates.push(propForm.pivotField, propForm.valueField, ...csvToList(propForm.groupFields))
      break
    case 'UNPIVOT':
      candidates.push(...csvToList(propForm.keyFields), ...csvToList(propForm.unpivotColumns))
      break
    case 'FIELD_PROCESS':
      candidates.push(...mappingRows.value.map((m) => m.from))
      break
    case 'SELECT_FIELDS':
      candidates.push(...selectFieldRows.value.map((m) => m.from))
      break
    case 'TYPE_CONVERT':
      candidates.push(...typeConvertRows.value.map((m) => m.from))
      break
    case 'SPLIT':
      candidates.push(propForm.sourceField)
      break
    case 'VALUE_MAPPER':
    case 'STRING_CUT':
    case 'REPLACE_STRING':
    case 'NULL_IF':
    case 'IF_NULL':
    case 'SWITCH_CASE':
    case 'VALIDATOR':
      candidates.push(propForm.field)
      break
    case 'DB_LOOKUP':
      candidates.push(propForm.keyField)
      break
    default:
      return []
  }
  return [...new Set(candidates.filter((f) => !!f && !upstreamFields.value.includes(f)))]
})

async function onOdsWritebackChange(val: boolean | string | number) {
  if (val) {
    try {
      await ElMessageBox.confirm(
        '回写 ODS 仅用于「同层贴源规范化」场景，确认允许？默认治理产出应写入 DWD。',
        '允许回写 ODS',
        { type: 'warning', confirmButtonText: '确认允许', cancelButtonText: '取消' },
      )
    } catch {
      propForm.allowOdsWriteback = false
      return
    }
  } else if (propForm.outputConnection === 'smart_city_ods') {
    propForm.outputConnection = 'smart_city_dwd'
  }
  if (!propForm.outputTable || propForm.outputTable === 'output_table') {
    suggestOutputTable(upstreamSourceTableName.value)
  }
}

function onOutputConnectionChange() {
  if (!propForm.outputTable || /^(ods_|dwd_|dws_|ads_)/i.test(propForm.outputTable)) {
    suggestOutputTable(upstreamSourceTableName.value)
  }
}

let syncingFromNode = false
let propDebounceTimer: ReturnType<typeof setTimeout> | null = null

function syncPropFromNode(n: Node | null) {
  if (!n) return
  const cfg = (n.data?.config || {}) as Record<string, unknown>
  const type = String(n.data?.nodeType || '')
  propForm.label = String(n.data?.label || '')
  propForm.field = String(cfg.field || cfg.variableName || '')
  propForm.op = String(cfg.op || 'EQ')
  propForm.value = String(cfg.value ?? cfg.variableValue ?? '')
  propForm.filterMode = (cfg.mode as 'SIMPLE' | 'SQL') || 'SIMPLE'
  propForm.sqlExpr = String(cfg.sqlExpr || '')
  propForm.keys = Array.isArray(cfg.keys) ? (cfg.keys as string[]).join(',')
    : Array.isArray(cfg.dedupKeys) ? (cfg.dedupKeys as string[]).join(',') : ''
  propForm.sortKeys = Array.isArray(cfg.sortFields) ? (cfg.sortFields as string[]).join(',') : String(cfg.sortFields || '')
  propForm.keepStrategy = String(cfg.keepStrategy || 'FIRST')
  propForm.fields = Array.isArray(cfg.fields) ? (cfg.fields as string[]).join(',') : String(cfg.fields || '')
  propForm.maskChar = String(cfg.maskChar || '*')
  propForm.maskType = String(cfg.maskType || 'BLUR')
  propForm.rowCount = Number(cfg.rowCount || 10)
  propForm.leftKey = String(cfg.leftKey || '')
  propForm.rightKey = String(cfg.rightKey || '')
  propForm.joinType = String(cfg.joinType || 'INNER')
  propForm.sortField = String(cfg.field || cfg.sortField || '')
  propForm.sortOrder = String(cfg.order || 'ASC')
  propForm.groupBy = Array.isArray(cfg.groupBy) ? (cfg.groupBy as string[]).join(',') : String(cfg.groupBy || '')
  propForm.pivotField = String(cfg.pivotField || '')
  propForm.valueField = String(cfg.valueField || '')
  propForm.groupFields = Array.isArray(cfg.groupFields) ? (cfg.groupFields as string[]).join(',') : String(cfg.groupFields || '')
  propForm.keyFields = Array.isArray(cfg.keyFields) ? (cfg.keyFields as string[]).join(',') : String(cfg.keyFields || '')
  propForm.unpivotColumns = Array.isArray(cfg.unpivotColumns) ? (cfg.unpivotColumns as string[]).join(',') : String(cfg.unpivotColumns || '')
  propForm.valueColumnName = String(cfg.valueColumnName || 'value')
  propForm.nameColumnName = String(cfg.nameColumnName || 'attribute')
  propForm.inputMode = (cfg.inputMode as 'SAMPLE' | 'SQL' | 'TABLE') || 'SAMPLE'
  propForm.connection = String(cfg.connection || '')
  propForm.sql = String(cfg.sql || 'SELECT * FROM table_name')
  propForm.tableName = String(cfg.tableName || '')
  propForm.limit = Number(cfg.limit || 0)
  propForm.outputConnection = String(cfg.connection || cfg.outputConnection || 'smart_city_dwd')
  propForm.outputTable = String(cfg.table || cfg.outputTable || '')
  propForm.outputMode = (cfg.outputMode as 'INSERT' | 'TRUNCATE_INSERT' | 'UPDATE') || 'INSERT'
  propForm.commitSize = Number(cfg.commit || cfg.commitSize || 1000)
  propForm.allowOdsWriteback = !!cfg.allowOdsWriteback
  if (propForm.outputConnection === 'smart_city_ods' && !propForm.allowOdsWriteback) {
    propForm.outputConnection = 'smart_city_dwd'
  }
  propForm.sourceField = String(cfg.sourceField || '')
  propForm.delimiter = String(cfg.delimiter || ',')
  propForm.targetFieldsCsv = String(cfg.targetFieldsCsv || '')
  propForm.targetField = String(cfg.targetField || '')
  propForm.fromValue = String(cfg.fromValue || '')
  propForm.toValue = String(cfg.toValue || '')
  propForm.defaultValue = String(cfg.defaultValue || '')
  propForm.formula = String(cfg.formula || '')
  propForm.cutFrom = Number(cfg.cutFrom ?? 0)
  propForm.cutTo = Number(cfg.cutTo ?? 0)
  propForm.search = String(cfg.search || '')
  propForm.replace = String(cfg.replace || '')
  propForm.replaceValue = String(cfg.replaceValue || '')
  propForm.replaceValueType = String(cfg.replaceValueType || 'String')
  propForm.script = String(cfg.script || '')
  propForm.filePath = String(cfg.filePath || '')
  propForm.separator = String(cfg.separator || ',')
  propForm.table = String(cfg.table || '')
  propForm.keyField = String(cfg.keyField || '')
  propForm.lookupKey = String(cfg.lookupKey || '')
  propForm.url = String(cfg.url || '')
  propForm.method = String(cfg.method || 'GET')
  propForm.httpTimeout = Number(cfg.httpTimeout || 30)
  propForm.resultField = String(cfg.resultField || '')

  mappingRows.value = parseMappings(cfg.mappings)
  if (type === 'SELECT_FIELDS') {
    selectFieldRows.value = mappingRows.value.map((m) => ({
      from: m.from,
      to: m.to,
      action: (m.expr === 'DROP' ? 'DROP' : 'KEEP') as 'KEEP' | 'DROP',
    }))
  }
  if (type === 'TYPE_CONVERT') {
    typeConvertRows.value = (Array.isArray(cfg.mappings) ? cfg.mappings as Record<string, unknown>[] : [])
      .map((m) => ({
        from: String(m.from || ''),
        to: String(m.to || m.from || ''),
        targetType: String(m.targetType || m.expr || 'String'),
        dateFormat: String(m.dateFormat || ''),
      }))
      .filter((m) => m.from)
  }
  filterConditions.value = parseFilterConditions(cfg)
  filterLogic.value = (String(cfg.logic || 'AND').toUpperCase() === 'OR' ? 'OR' : 'AND')
  aggRows.value = parseAggs(cfg.aggs)
  sortKeyRows.value = parseSortKeys(cfg)
  joinKeyRows.value = parseJoinKeys(cfg)
  valueMapRows.value = type === 'VALUE_MAPPER' ? parseValueMaps(cfg) : []
  switchCases.value = parseSwitchCases(cfg)
  keepSource.value = cfg.keepSource !== false
  maskWriteMode.value = cfg.writeMode === 'NEW_COLUMN' ? 'NEW_COLUMN' : 'OVERWRITE'
  maskTargetSuffix.value = String(cfg.targetSuffix || '_masked')
  overwriteTarget.value = cfg.overwrite !== false
  varValueSource.value = cfg.valueSource === 'FIELD' ? 'FIELD' : 'CONST'
  delimiterMode.value = [',', '\\t', '|', ';'].includes(propForm.delimiter) ? propForm.delimiter : '__CUSTOM__'
  encoding.value = String(cfg.encoding || 'UTF-8')
  sheetName.value = String(cfg.sheetName || '')
  splitTargetFields.value = parseStringList(cfg.targetFields || cfg.targetFieldsCsv)
  matchKeysSelected.value = parseStringList(cfg.matchKeys)
  updateFieldsSelected.value = parseStringList(cfg.updateFields)
  constantRows.value = Array.isArray(cfg.constants)
    ? (cfg.constants as Record<string, unknown>[]).map((c) => ({
      field: String(c.field || ''),
      value: String(c.value ?? ''),
      valueType: String(c.valueType || 'String'),
    }))
    : (cfg.field ? [{ field: String(cfg.field), value: String(cfg.value ?? ''), valueType: String(cfg.valueType || 'String') }] : [])
  validatorRows.value = Array.isArray(cfg.rules)
    ? (cfg.rules as Record<string, unknown>[]).map((r) => ({
      field: String(r.field || ''),
      ruleType: String(r.ruleType || 'NOT_NULL'),
      param: String(r.param || ''),
      onFail: String(r.onFail || 'REJECT'),
    }))
    : (cfg.field ? [{ field: String(cfg.field), ruleType: 'NOT_NULL', param: '', onFail: 'REJECT' }] : [])
  lookupReturnRows.value = Array.isArray(cfg.returnFields)
    ? (cfg.returnFields as Record<string, unknown>[]).map((r) => ({
      sourceColumn: String(r.sourceColumn || r.from || ''),
      targetField: String(r.targetField || r.to || ''),
    }))
    : []

  if (type === 'INPUT' && (propForm.inputMode === 'TABLE' || propForm.inputMode === 'SQL') && propForm.connection) {
    void loadInputTables(propForm.connection).then(() => {
      if (propForm.tableName || propForm.inputMode === 'SAMPLE') {
        void resolveUpstreamFields()
      }
    })
  }
}

watch(selectedNode, (n) => {
  syncingFromNode = true
  syncPropFromNode(n)
  nextTick(() => { syncingFromNode = false })
})

watch(
  () => [selectedNodeType.value, propForm.inputMode] as const,
  ([type, mode]) => {
    if (type === 'INPUT' && (mode === 'TABLE' || mode === 'SQL') && propForm.connection) {
      void loadInputTables(propForm.connection)
    }
    if (type === 'INPUT' && mode === 'SAMPLE') {
      void resolveUpstreamFields()
    }
  },
)

function scheduleApply() {
  if (syncingFromNode || !selectedNode.value || isLockedByOther.value) return
  if (propDebounceTimer) clearTimeout(propDebounceTimer)
  propDebounceTimer = setTimeout(() => applyProps(true), 300)
}

watch(propForm, scheduleApply, { deep: true })
watch([
  mappingRows, selectFieldRows, typeConvertRows, filterConditions, aggRows, sortKeyRows,
  joinKeyRows, valueMapRows, switchCases, constantRows, validatorRows, lookupReturnRows,
  splitTargetFields, keepSource, maskWriteMode, maskTargetSuffix, overwriteTarget,
  filterLogic, varValueSource, matchKeysSelected, updateFieldsSelected, encoding, sheetName, delimiterMode,
], scheduleApply, { deep: true })

function applyProps(silent = false) {
  if (isLockedByOther.value) {
    ElMessage.warning('任务已被他人锁定，不可编辑')
    return
  }
  const n = selectedNode.value
  if (!n) return
  n.data = n.data || {}
  n.data.label = propForm.label
  const type = String(n.data.nodeType || 'FILTER')
  if (delimiterMode.value !== '__CUSTOM__') {
    propForm.delimiter = delimiterMode.value === '\\t' ? '\t' : delimiterMode.value
  }
  const config: Record<string, unknown> = {}
  if (type === 'INPUT') {
    config.inputMode = propForm.inputMode
    config.connection = propForm.connection
    config.tableName = propForm.tableName
    config.limit = propForm.limit
    if (propForm.inputMode === 'SAMPLE') {
      config.rowCount = propForm.rowCount
      // 样例模式不落库占位 SQL，避免转换时误用 SELECT * FROM table_name
      config.sql = ''
    } else if (propForm.inputMode === 'SQL') {
      config.sql = propForm.sql
    } else {
      // TABLE：只认 tableName，清空占位 sql
      config.sql = ''
      config.rowCount = 0
    }
  } else if (type === 'OUTPUT' || type === 'INSERT_UPDATE') {
    config.connection = propForm.outputConnection
    config.outputConnection = propForm.outputConnection
    config.table = propForm.outputTable
    config.outputTable = propForm.outputTable
    config.outputMode = propForm.outputMode
    config.commitSize = propForm.commitSize
    config.allowOdsWriteback = propForm.allowOdsWriteback
    if (type === 'INSERT_UPDATE') {
      config.matchKeys = [...matchKeysSelected.value]
      config.updateFields = [...updateFieldsSelected.value]
    }
  } else if (type === 'FILTER') {
    config.mode = propForm.filterMode
    config.logic = filterLogic.value
    if (propForm.filterMode === 'SQL') {
      config.sqlExpr = propForm.sqlExpr
    } else {
      config.conditions = filterConditions.value.filter((c) => c.field).map((c, i) => ({
        field: c.field,
        op: c.op,
        value: c.value,
        logic: i === 0 ? 'AND' : c.logic,
      }))
      const first = config.conditions as FilterCondRow[]
      if (first[0]) {
        config.field = first[0].field
        config.op = first[0].op
        config.value = first[0].value
      }
    }
  } else if (type === 'FIELD_PROCESS') {
    config.mappings = mappingRows.value
      .filter((m) => m.from)
      .map((m) => ({ from: m.from, to: m.to || m.from, expr: m.expr || 'COPY' }))
    config.keepSource = keepSource.value
  } else if (type === 'SELECT_FIELDS') {
    config.mappings = selectFieldRows.value
      .filter((m) => m.from)
      .map((m) => ({ from: m.from, to: m.to || m.from, expr: m.action === 'DROP' ? 'DROP' : 'COPY' }))
  } else if (type === 'TYPE_CONVERT') {
    config.mappings = typeConvertRows.value
      .filter((m) => m.from)
      .map((m) => ({
        from: m.from,
        to: m.to || m.from,
        expr: m.targetType,
        targetType: m.targetType,
        dateFormat: m.dateFormat,
      }))
  } else if (type === 'DEDUPLICATE') {
    config.dedupKeys = propForm.keys.split(',').map((s) => s.trim()).filter(Boolean)
    config.keys = config.dedupKeys
    config.sortFields = propForm.sortKeys.split(',').map((s) => s.trim()).filter(Boolean)
    config.keepStrategy = propForm.keepStrategy
  } else if (type === 'MASK') {
    config.fields = propForm.fields.split(',').map((s) => s.trim()).filter(Boolean)
    config.maskType = propForm.maskType
    config.maskChar = propForm.maskChar
    // MD5 为字符串哈希，禁止覆盖 DATE/数值原列，统一写新列
    config.writeMode = propForm.maskType === 'MD5' ? 'NEW_COLUMN' : maskWriteMode.value
    if (propForm.maskType === 'MD5') {
      maskWriteMode.value = 'NEW_COLUMN'
    }
    config.targetSuffix = maskTargetSuffix.value || '_masked'
  } else if (type === 'JOIN') {
    config.joinType = propForm.joinType
    config.joinKeys = joinKeyRows.value.filter((j) => j.leftKey && j.rightKey)
    if (joinKeyRows.value[0]) {
      config.leftKey = joinKeyRows.value[0].leftKey
      config.rightKey = joinKeyRows.value[0].rightKey
    }
  } else if (type === 'SORT') {
    config.sortKeys = sortKeyRows.value.filter((s) => s.field)
    if (sortKeyRows.value[0]) {
      config.field = sortKeyRows.value[0].field
      config.order = sortKeyRows.value[0].order
    }
  } else if (type === 'AGGREGATE') {
    config.groupBy = propForm.groupBy.split(',').map((s) => s.trim()).filter(Boolean)
    config.aggs = aggRows.value.filter((a) => a.field).map((a) => ({
      field: a.field,
      op: a.op || 'COUNT',
      alias: a.alias || `${a.field}_${a.op || 'COUNT'}`,
    }))
  } else if (type === 'PIVOT') {
    config.pivotField = propForm.pivotField
    config.valueField = propForm.valueField
    config.groupFields = propForm.groupFields.split(',').map((s) => s.trim()).filter(Boolean)
  } else if (type === 'UNPIVOT') {
    config.keyFields = propForm.keyFields.split(',').map((s) => s.trim()).filter(Boolean)
    config.unpivotColumns = propForm.unpivotColumns.split(',').map((s) => s.trim()).filter(Boolean)
    config.valueColumnName = propForm.valueColumnName
    config.nameColumnName = propForm.nameColumnName
  } else if (type === 'SET_VARIABLE') {
    config.variableName = propForm.field || 'var1'
    config.valueSource = varValueSource.value
    config.variableValue = propForm.value || ''
    config.field = propForm.field
  } else if (type === 'SPLIT') {
    config.sourceField = propForm.sourceField
    config.delimiter = propForm.delimiter
    config.targetFields = [...splitTargetFields.value]
    config.targetFieldsCsv = splitTargetFields.value.join(',')
  } else if (type === 'VALUE_MAPPER') {
    config.field = propForm.field
    config.targetField = propForm.targetField || propForm.field
    config.mappings = valueMapRows.value
    config.defaultValue = propForm.defaultValue
    if (valueMapRows.value[0]) {
      config.fromValue = valueMapRows.value[0].fromValue
      config.toValue = valueMapRows.value[0].toValue
    }
  } else if (type === 'CONSTANT') {
    config.constants = constantRows.value.filter((c) => c.field)
    if (constantRows.value[0]) {
      config.field = constantRows.value[0].field
      config.value = constantRows.value[0].value
      config.valueType = constantRows.value[0].valueType
    }
  } else if (type === 'FORMULA') {
    config.field = propForm.field
    config.formula = propForm.formula
  } else if (type === 'STRING_CUT') {
    config.field = propForm.field
    config.targetField = overwriteTarget.value ? propForm.field : (propForm.targetField || propForm.field)
    config.cutFrom = propForm.cutFrom
    config.cutTo = propForm.cutTo
    config.overwrite = overwriteTarget.value
  } else if (type === 'REPLACE_STRING') {
    config.field = propForm.field
    config.targetField = overwriteTarget.value ? propForm.field : (propForm.targetField || propForm.field)
    config.search = propForm.search
    config.replace = propForm.replace
    config.overwrite = overwriteTarget.value
  } else if (type === 'NULL_IF') {
    config.field = propForm.field
    config.value = propForm.value
  } else if (type === 'IF_NULL') {
    config.field = propForm.field
    config.replaceValue = propForm.replaceValue
    config.replaceValueType = propForm.replaceValueType
  } else if (type === 'SWITCH_CASE') {
    config.field = propForm.field
    config.cases = switchCases.value.filter((c) => c.value !== '')
  } else if (type === 'VALIDATOR') {
    config.rules = validatorRows.value.filter((r) => r.field)
    if (validatorRows.value[0]) config.field = validatorRows.value[0].field
  } else if (type === 'SCRIPT') {
    config.script = propForm.script
  } else if (type === 'TEXT_INPUT' || type === 'EXCEL_INPUT' || type === 'TEXT_OUTPUT') {
    config.filePath = propForm.filePath
    config.separator = propForm.separator === '\\t' ? '\t' : propForm.separator
    config.encoding = encoding.value
    if (type === 'EXCEL_INPUT') config.sheetName = sheetName.value
  } else if (type === 'DB_LOOKUP') {
    config.connection = propForm.connection
    config.table = propForm.table
    config.keyField = propForm.keyField
    config.lookupKey = propForm.lookupKey
    config.returnFields = lookupReturnRows.value.filter((r) => r.sourceColumn)
  } else if (type === 'HTTP') {
    config.url = propForm.url
    config.method = propForm.method
    config.httpTimeout = propForm.httpTimeout
    config.resultField = propForm.resultField
  }
  n.data.config = config
  if (!silent) ElMessage.success('属性已应用')
}

let seq = 1

function defaultNodeConfig(type: string): Record<string, unknown> {
  switch (type) {
    case 'INPUT':
      return { rowCount: 10 }
    case 'SPLIT':
      return { sourceField: '', delimiter: ',', targetFieldsCsv: '' }
    case 'VALUE_MAPPER':
      return { field: '', targetField: '', fromValue: '', toValue: '', defaultValue: '' }
    case 'CONSTANT':
      return { field: '', value: '' }
    case 'FORMULA':
      return { field: '', formula: '' }
    case 'STRING_CUT':
      return { field: '', targetField: '', cutFrom: 0, cutTo: 0 }
    case 'REPLACE_STRING':
      return { field: '', targetField: '', search: '', replace: '' }
    case 'NULL_IF':
      return { field: '', value: '' }
    case 'IF_NULL':
      return { field: '', replaceValue: '' }
    case 'SELECT_FIELDS':
    case 'TYPE_CONVERT':
    case 'FIELD_PROCESS':
      return { mappings: [{ from: 'name', to: 'name', expr: 'COPY' }] }
    case 'SCRIPT':
      return { script: '' }
    case 'TEXT_INPUT':
    case 'EXCEL_INPUT':
    case 'TEXT_OUTPUT':
      return { filePath: '', separator: ',' }
    case 'OUTPUT':
    case 'INSERT_UPDATE':
      return {
        connection: 'smart_city_dwd',
        outputConnection: 'smart_city_dwd',
        table: '',
        outputTable: '',
        outputMode: 'INSERT',
        commitSize: 1000,
        allowOdsWriteback: false,
      }
    case 'DB_LOOKUP':
      return { connection: '', table: '', keyField: '', lookupKey: '' }
    case 'HTTP':
      return { url: '', method: 'GET' }
    default:
      return {}
  }
}

function addNode(type: string, label: string) {
  if (isLockedByOther.value) {
    ElMessage.warning('任务已被他人锁定，不可编辑')
    return
  }
  const id = `n_${type}_${seq++}_${Date.now()}`
  let pos = { x: 120 + (seq % 5) * 140, y: 80 + (seq % 4) * 90 }
  const insertEdge = selectedEdgeId.value
    ? edges.value.find((e) => e.id === selectedEdgeId.value)
    : null
  if (insertEdge) {
    const src = nodes.value.find((n) => n.id === insertEdge.source)
    const tgt = nodes.value.find((n) => n.id === insertEdge.target)
    if (src && tgt) {
      pos = {
        x: (src.position.x + tgt.position.x) / 2,
        y: (src.position.y + tgt.position.y) / 2 + 24,
      }
    }
  }
  nodes.value.push({
    id,
    type: 'gov',
    position: pos,
    label,
    data: {
      nodeType: type,
      label,
      config: defaultNodeConfig(type),
    },
  })
  if (insertEdge) {
    edges.value = edges.value.filter((e) => e.id !== insertEdge.id)
    const ts = Date.now()
    edges.value = [
      ...edges.value,
      {
        id: `e_${insertEdge.source}_${id}_${ts}`,
        source: insertEdge.source,
        target: id,
        sourceHandle: insertEdge.sourceHandle,
        targetHandle: 'in',
        markerEnd: MarkerType.ArrowClosed,
        data: { edgeRole: roleFromHandles(insertEdge.sourceHandle, 'in') },
      },
      {
        id: `e_${id}_${insertEdge.target}_${ts + 1}`,
        source: id,
        target: insertEdge.target,
        sourceHandle: 'out',
        targetHandle: insertEdge.targetHandle,
        markerEnd: MarkerType.ArrowClosed,
        data: { edgeRole: roleFromHandles('out', insertEdge.targetHandle) },
      },
    ]
    selectedEdgeId.value = null
    ElMessage.success(`已将「${label}」插入连线中间`)
  }
  selectedId.value = id
}

function onNodeClick(ev: { node: Node }) {
  selectedId.value = ev.node.id
  selectedEdgeId.value = null
}

function onNodeDoubleClick(ev: { node: Node }) {
  selectedId.value = ev.node.id
  selectedEdgeId.value = null
  propsDialogVisible.value = true
  nextTick(() => { void resolveUpstreamFields() })
}

function finishPropsDialog() {
  propsDialogVisible.value = false
}

function onPropsDialogClosed() {
  if (selectedNode.value && !isLockedByOther.value) {
    applyProps(true)
  }
}

function removeSelected() {
  if (selectedEdgeId.value) {
    edges.value = edges.value.filter((e) => e.id !== selectedEdgeId.value)
    selectedEdgeId.value = null
    ElMessage.success('已删除连线')
    return
  }
  if (!selectedId.value) return
  const id = selectedId.value
  edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
  nodes.value = nodes.value.filter((n) => n.id !== id)
  selectedId.value = null
  propsDialogVisible.value = false
}

async function loadGraph() {
  const res = await api.get(`/governance/gov-tasks/${props.taskId}/graph`)
  taskName.value = res.data.taskName || ''
  status.value = res.data.status || ''
  lockedBy.value = res.data.lockedBy || ''
  engineType.value = 'KETTLE'
  const raw = res.data.graphJson
  let parsed: { nodes?: Node[]; edges?: Edge[] } = { nodes: [], edges: [] }
  try {
    parsed = typeof raw === 'string' ? JSON.parse(raw || '{}') : (raw || {})
  } catch {
    parsed = { nodes: [], edges: [] }
  }
  nodes.value = (parsed.nodes || []).map((n) => {
    const { style: _legacyStyle, ...rest } = n as Node & { style?: unknown }
    return {
      ...rest,
      type: 'gov',
      style: undefined,
      label: (n.data as { label?: string })?.label || n.label || n.id,
    }
  })
  edges.value = (parsed.edges || []).map((e) => {
    const sourceHandle = (e as Edge).sourceHandle || 'out'
    const targetHandle = (e as Edge).targetHandle || 'in'
    const edgeRole = (e as Edge & { data?: { edgeRole?: string } }).data?.edgeRole
      || roleFromHandles(sourceHandle, targetHandle)
    return {
      ...e,
      sourceHandle,
      targetHandle,
      markerEnd: e.markerEnd || MarkerType.ArrowClosed,
      label: edgeRole === 'COPY' ? undefined : edgeRole,
      data: { ...(typeof e.data === 'object' && e.data ? e.data : {}), edgeRole },
    }
  })
  seq = nodes.value.length + 1
}

function buildGraphJson() {
  return JSON.stringify({
    nodes: nodes.value.map((n) => ({
      id: n.id,
      type: 'gov',
      position: n.position,
      label: n.data?.label || n.label,
      data: {
        nodeType: n.data?.nodeType,
        label: n.data?.label || n.label,
        config: n.data?.config || {},
      },
    })),
    edges: edges.value.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle || 'out',
      targetHandle: e.targetHandle || 'in',
      data: e.data || { edgeRole: roleFromHandles(e.sourceHandle, e.targetHandle) },
    })),
  })
}

async function saveGraph() {
  saving.value = true
  try {
    const warn = validateGraphForRun()
    await api.put(`/governance/gov-tasks/${props.taskId}/graph`, {
      graphJson: buildGraphJson(),
    })
    if (warn) {
      ElMessage.warning(`已保存草稿：${warn}`)
    } else {
      ElMessage.success('画布已保存')
    }
    status.value = 'CONFIGURED'
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function publishTask() {
  publishing.value = true
  try {
    await api.post(`/governance/gov-tasks/${props.taskId}/publish`, {
      graphJson: buildGraphJson(),
    })
    ElMessage.success('发布成功，任务已解锁')
    status.value = 'PUBLISHED'
    lockedBy.value = ''
  } catch {
    ElMessage.error('发布失败')
  } finally {
    publishing.value = false
  }
}

/** 治理任务仅 Kettle；运行前强制纠正引擎字段 */
async function ensureKettleEngine() {
  try {
    await api.put(`/governance/gov-tasks/${props.taskId}/engine`, { engineType: 'KETTLE' })
  } catch {
    /* ignore */
  }
  engineType.value = 'KETTLE'
}

async function exportKtr() {
  const err = validateGraphForRun()
  if (err) {
    ElMessage.warning(err)
    return
  }
  try {
    await saveGraph()
    const res = await api.get(`/governance/gov-tasks/${props.taskId}/export-ktr`, {
      responseType: 'blob',
    })
    const blob = new Blob([res.data], { type: 'application/xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `task_${props.taskId}.ktr`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出 .ktr')
  } catch {
    ElMessage.error('导出失败')
  }
}

function triggerImport() {
  importInput.value?.click()
}

async function onImportFile(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const form = new FormData()
    form.append('file', file)
    const res = await api.post(`/governance/gov-tasks/${props.taskId}/import-ktr`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    ElMessage.success(res.data?.message || '导入成功')
    await loadGraph()
  } catch {
    ElMessage.error('导入失败')
  } finally {
    input.value = ''
  }
}

async function runTask() {
  const err = validateGraphForRun()
  if (err) {
    ElMessage.warning(err)
    return
  }
  await saveGraph()
  // 若有变量定义则弹窗取值
  try {
    const vres = await api.get(`/governance/tasks/${props.taskId}/variables`)
    const list = (vres.data || []) as Array<{ name: string; label?: string; defaultValue?: string; required?: boolean }>
    if (list.length > 0) {
      varDefs.value = list
      varForm.value = {}
      list.forEach((v) => { varForm.value[v.name] = v.defaultValue || '' })
      varDialogVisible.value = true
      return
    }
  } catch { /* ignore */ }
  await doRun()
}

async function doRun(variables?: Record<string, string>) {
  running.value = true
  try {
    const res = await api.post(`/governance/gov-tasks/${props.taskId}/run`, variables ? { variables } : {})
    ElMessage.success(res.data?.message || '执行完成')
    const runId = res.data?.runId
    if (runId) {
      runLogs.value = (await api.get(`/governance/gov-tasks/runs/${runId}/node-logs`)).data || []
    }
    status.value = res.data?.status === 'SUCCESS' ? 'READY' : (res.data?.status || status.value)
  } catch {
    ElMessage.error('执行失败')
  } finally {
    running.value = false
  }
}

async function confirmVarRun() {
  for (const v of varDefs.value) {
    if (v.required && !String(varForm.value[v.name] || '').trim()) {
      ElMessage.warning(`请填写变量：${v.label || v.name}`)
      return
    }
  }
  varDialogVisible.value = false
  await doRun({ ...varForm.value })
}

function goEtlSub(sub: string) {
  const q: Record<string, unknown> = { ...route.query, tab: 'etl', etlSub: sub }
  delete q.etlView
  delete q.taskId
  router.replace({ query: q as Record<string, string> })
}

function backToList() {
  const q: Record<string, any> = { ...route.query, tab: 'etl' }
  delete q.etlView
  delete q.taskId
  if (!q.etlSub) q.etlSub = 'task-mgmt'
  router.replace({ query: q })
}

async function loadDataSources() {
  try {
    const res = await api.get('/exchange/ingestion/data-sources')
    dataSources.value = (res.data || []) as Array<{ id: number; name: string; code: string }>
  } catch {
    dataSources.value = []
  }
}

onMounted(async () => {
  await nextTick()
  await Promise.all([loadGraph(), loadDataSources()])
  await ensureKettleEngine()
})
</script>

<template>
  <PageCard :title="`任务设计 · ${taskName || taskId}`">
    <div class="design-toolbar">
      <el-button @click="backToList">返回列表</el-button>
      <el-tag size="small">{{ statusLabel(status) }}</el-tag>
      <el-tag v-if="isLockedByOther" type="warning" size="small">已被 {{ lockedBy }} 锁定</el-tag>
      <el-tag v-else-if="selectedEdgeId" type="success" size="small">已选连线：点左侧组件可插入中间</el-tag>
      <el-tag v-else-if="selectedId" type="info" size="small">已选节点：双击编辑属性</el-tag>
      <el-button type="primary" :loading="saving" :disabled="isLockedByOther" @click="saveGraph">保存画布</el-button>
      <el-button type="warning" :loading="publishing" :disabled="isLockedByOther" @click="publishTask">发布并解锁</el-button>
      <el-button type="success" :loading="running" :disabled="isLockedByOther" @click="runTask">运行</el-button>
      <el-button :disabled="isLockedByOther" @click="exportKtr">导出KTR</el-button>
      <el-button :disabled="isLockedByOther" @click="triggerImport">导入KTR</el-button>
      <input ref="importInput" type="file" accept=".ktr,.xml" style="display:none" @change="onImportFile" />
      <el-button type="danger" plain :disabled="(!selectedId && !selectedEdgeId) || isLockedByOther" @click="removeSelected">
        {{ selectedEdgeId ? '删除连线' : '删除节点' }}
      </el-button>
      <el-button plain @click="runLogVisible = true">运行日志</el-button>
    </div>

    <div class="design-layout">
      <aside class="palette">
        <div class="palette-header">
          <div class="palette-title">治理组件</div>
          <el-button link type="primary" @click="goEtlSub('components')">管理组件</el-button>
        </div>
        <div v-if="selectedEdgeId" class="palette-tip">已选连线，点击下方组件插入中间</div>
        <template v-for="grp in paletteGroups" :key="grp.key">
          <div class="palette-subtitle">{{ grp.label }}</div>
          <el-button
            v-for="item in grp.items"
            :key="item.type"
            class="palette-btn"
            :disabled="isLockedByOther"
            @click="addNode(item.type, item.name)"
          >
            <span class="dot" :style="{ background: item.color }" />
            {{ item.name }}
          </el-button>
        </template>
      </aside>

      <div class="canvas">
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :node-types="nodeTypes"
          fit-view-on-init
          :nodes-draggable="!isLockedByOther"
          :nodes-connectable="!isLockedByOther"
          :edges-updatable="!isLockedByOther"
          :elements-selectable="true"
          :delete-key-code="isLockedByOther ? null : ['Backspace', 'Delete']"
          @connect="onConnect"
          @edge-update="onEdgeUpdate"
          @node-click="onNodeClick"
          @node-double-click="onNodeDoubleClick"
          @edge-click="onEdgeClick"
          @pane-click="onPaneClick"
        >
          <Background />
          <Controls />
          <MiniMap />
        </VueFlow>
      </div>
    </div>

    <el-dialog
      v-model="propsDialogVisible"
      :title="`节点属性 · ${propForm.label || selectedNodeType || ''}`"
      width="720px"
      class="gov-props-dialog"
      append-to-body
      @closed="onPropsDialogClosed"
    >
      <template v-if="selectedNode">
          <el-form label-position="top" size="small" class="props-form">
            <el-form-item label="类型">
              <el-tag>{{ statusLabel(String(selectedNode.data?.nodeType || '')) }}</el-tag>
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="propForm.label" />
            </el-form-item>
            <el-alert
              v-if="needsUpstream && !hasUpstreamEdge"
              type="warning"
              :closable="false"
              show-icon
              title="请先连接上游节点，规划好数据流程后再配置组件属性"
              style="margin-bottom: 12px"
            />
            <template v-else>
            <div v-if="needsUpstream" class="field-toolbar">
              <el-button link type="primary" size="small" :loading="loadingFields" @click="resolveUpstreamFields">
                刷新上游字段
              </el-button>
              <el-checkbox v-model="allowCustomField" size="small">自定义输入</el-checkbox>
              <span class="field-hint">
                {{ upstreamFields.length ? `已探到 ${upstreamFields.length} 个字段` : '未探到字段，可开启自定义' }}
              </span>
            </div>
            <el-alert
              v-if="invalidSelectedFields.length"
              type="error"
              :closable="false"
              show-icon
              :title="`无效字段：${invalidSelectedFields.join(', ')}（上游已变更，请重新选择）`"
              style="margin-bottom: 8px"
            />
            <template v-if="selectedNode.data?.nodeType === 'INPUT'">
              <el-form-item label="输入方式">
                <el-radio-group v-model="propForm.inputMode">
                  <el-radio-button value="SAMPLE">样例数据</el-radio-button>
                  <el-radio-button value="SQL">SQL查询</el-radio-button>
                  <el-radio-button value="TABLE">指定表</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <template v-if="propForm.inputMode === 'SAMPLE'">
                <el-form-item label="样例行数">
                  <el-input-number v-model="propForm.rowCount" :min="1" :max="10000" />
                </el-form-item>
              </template>
              <template v-else-if="propForm.inputMode === 'SQL'">
                <el-form-item label="数据源">
                  <el-select v-model="propForm.connection" placeholder="选择数据源" clearable filterable>
                    <el-option
                      v-for="opt in connectionOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="SQL查询">
                  <el-input v-model="propForm.sql" type="textarea" :rows="4" placeholder="SELECT * FROM table_name WHERE ..." />
                </el-form-item>
                <el-form-item label="结果限制(0=不限制)">
                  <el-input-number v-model="propForm.limit" :min="0" :max="1000000" />
                </el-form-item>
              </template>
              <template v-else-if="propForm.inputMode === 'TABLE'">
                <el-form-item label="数据源">
                  <el-select
                    v-model="propForm.connection"
                    placeholder="选择数据源"
                    clearable
                    filterable
                    @change="onInputConnectionChange"
                  >
                    <el-option
                      v-for="opt in connectionOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="表名">
                  <el-select
                    v-model="propForm.tableName"
                    filterable
                    allow-create
                    default-first-option
                    clearable
                    :disabled="!propForm.connection"
                    :loading="loadingInputTables"
                    :placeholder="propForm.connection ? (loadingInputTables ? '正在加载表…' : '搜索或选择表名') : '请先选择数据源'"
                    style="width:100%"
                    @change="onInputTableChange"
                  >
                    <el-option v-for="t in inputTableOptions" :key="t" :label="t" :value="t" />
                  </el-select>
                </el-form-item>
                <el-form-item label="结果限制(0=不限制)">
                  <el-input-number v-model="propForm.limit" :min="0" :max="1000000" />
                </el-form-item>
                <div v-if="propForm.tableName" class="field-toolbar" style="margin-bottom: 8px">
                  <el-button link type="primary" size="small" :loading="loadingFields" @click="resolveUpstreamFields">
                    刷新字段
                  </el-button>
                  <span class="field-hint">
                    {{ upstreamFields.length ? `已探到 ${upstreamFields.length} 个字段` : '未探到字段（下游组件将无法自动带入）' }}
                  </span>
                </div>
              </template>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'FILTER'">
              <el-form-item label="过滤方式">
                <el-radio-group v-model="propForm.filterMode">
                  <el-radio-button value="SIMPLE">条件组合</el-radio-button>
                  <el-radio-button value="SQL">自定义表达式</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <template v-if="propForm.filterMode === 'SIMPLE'">
                <el-form-item label="条件关系">
                  <el-radio-group v-model="filterLogic">
                    <el-radio-button value="AND">全部满足(AND)</el-radio-button>
                    <el-radio-button value="OR">任一满足(OR)</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <div v-for="(c, idx) in filterConditions" :key="idx" class="rule-row">
                  <GovFieldSelect v-model="c.field" :fields="upstreamFields" :allow-custom="allowCustomField" placeholder="字段" />
                  <el-select v-model="c.op" style="width:110px">
                    <el-option v-for="o in FILTER_OPS" :key="o.value" :label="o.label" :value="o.value" />
                  </el-select>
                  <el-input v-if="c.op !== 'IS_NULL' && c.op !== 'NOT_NULL'" v-model="c.value" placeholder="值" />
                  <el-button link type="danger" @click="filterConditions.splice(idx, 1)">删</el-button>
                </div>
                <el-button size="small" @click="addFilterCond">添加条件</el-button>
                <el-alert type="info" :closable="false" show-icon title="出口：右侧「是/否」端口分别连接满足与不满足支路" style="margin-top:8px" />
              </template>
              <el-form-item v-else label="自定义表达式">
                <el-input v-model="propForm.sqlExpr" type="textarea" :rows="3" placeholder="如: age > 18 AND status = 'active'" />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'FIELD_PROCESS'">
              <div class="rule-toolbar">
                <el-button size="small" type="primary" @click="addMappingRow">添加规则</el-button>
                <el-button size="small" @click="seedAllMappings">一键带入全部字段</el-button>
              </div>
              <div v-for="(m, idx) in mappingRows" :key="idx" class="rule-row">
                <GovFieldSelect v-model="m.from" :fields="upstreamFields" :allow-custom="allowCustomField" placeholder="源字段" />
                <el-select v-model="m.expr" style="width:120px">
                  <el-option v-for="o in FIELD_PROCESS_EXPRS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
                <el-input v-model="m.to" placeholder="目标字段(默认同源)" />
                <el-button link type="danger" @click="mappingRows.splice(idx, 1)">删</el-button>
              </div>
              <el-form-item label="重命名后保留原列">
                <el-switch v-model="keepSource" />
              </el-form-item>
              <el-divider content-position="left">字段预览</el-divider>
              <div class="field-preview">
                <div v-for="f in sampleFields" :key="f.name" class="field-item" :class="f.status">
                  <span class="field-name">{{ f.name }}</span>
                  <span class="field-status">{{ f.label }}</span>
                </div>
              </div>
              <div class="preview-legend">
                <span class="legend-item untouched">未处理</span>
                <span class="legend-item processed">已处理</span>
                <span class="legend-item new">新增列</span>
              </div>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'DEDUPLICATE'">
              <el-form-item label="去重字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="keysSelected"
                  multiple
                  filterable
                  collapse-tags
                  placeholder="选择去重键"
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.keys" placeholder="id,name" />
              </el-form-item>
              <el-form-item label="排序字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="sortKeysSelected"
                  multiple
                  filterable
                  collapse-tags
                  placeholder="选择排序字段"
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.sortKeys" placeholder="updated_at" />
              </el-form-item>
              <el-form-item label="保留策略">
                <el-radio-group v-model="propForm.keepStrategy">
                  <el-radio value="FIRST">保留第一条</el-radio>
                  <el-radio value="LAST">保留最后一条</el-radio>
                </el-radio-group>
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'MASK'">
              <el-form-item label="脱敏字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="fieldsSelected"
                  multiple
                  filterable
                  collapse-tags
                  placeholder="选择脱敏字段"
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.fields" placeholder="phone,idCard" />
              </el-form-item>
              <el-form-item label="脱敏类型">
                <el-radio-group
                  v-model="propForm.maskType"
                  @change="(v) => { if (String(v) === 'MD5') maskWriteMode = 'NEW_COLUMN' }"
                >
                  <el-radio value="BLUR">模糊处理</el-radio>
                  <el-radio value="MD5">MD5哈希</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="propForm.maskType === 'BLUR'" label="掩码字符">
                <el-input v-model="propForm.maskChar" maxlength="1" />
              </el-form-item>
              <el-form-item label="写回方式">
                <el-select v-model="maskWriteMode" style="width:100%" :disabled="propForm.maskType === 'MD5'">
                  <el-option label="覆盖原字段" value="OVERWRITE" :disabled="propForm.maskType === 'MD5'" />
                  <el-option label="写到新列" value="NEW_COLUMN" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="maskWriteMode === 'NEW_COLUMN' || propForm.maskType === 'MD5'" label="新列后缀">
                <el-input v-model="maskTargetSuffix" placeholder="_masked" />
              </el-form-item>
              <el-alert
                v-if="propForm.maskType === 'MD5'"
                type="warning"
                :closable="false"
                show-icon
                title="MD5 结果为字符串，将写入新列（如 birth_date_masked），不会覆盖原 DATE/数值字段"
                style="margin-bottom: 8px"
              />
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'JOIN'">
              <el-form-item label="关联类型">
                <el-select v-model="propForm.joinType" style="width:100%">
                  <el-option label="内连接" value="INNER" />
                  <el-option label="左连接" value="LEFT" />
                  <el-option label="全连接" value="FULL" />
                </el-select>
              </el-form-item>
              <el-alert type="info" :closable="false" show-icon title="请将左/右上游分别接到节点左侧「左」「右」端口" style="margin-bottom:8px" />
              <div v-for="(j, idx) in joinKeyRows" :key="idx" class="rule-row">
                <GovFieldSelect v-model="j.leftKey" :fields="upstreamFields" :allow-custom="allowCustomField" placeholder="左键" />
                <GovFieldSelect v-model="j.rightKey" :fields="upstreamFields" :allow-custom="allowCustomField" placeholder="右键" />
                <el-button link type="danger" @click="joinKeyRows.splice(idx, 1)">删</el-button>
              </div>
              <el-button size="small" @click="addJoinKey">添加关联键</el-button>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'UNION'">
              <el-alert type="info" :closable="false" show-icon title="将多条入边按行合并，请保证各路上游字段对齐。可从多个上游连入本节点。" />
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SORT'">
              <div v-for="(s, idx) in sortKeyRows" :key="idx" class="rule-row">
                <GovFieldSelect v-model="s.field" :fields="upstreamFields" :allow-custom="allowCustomField" placeholder="排序字段" />
                <el-select v-model="s.order" style="width:100px">
                  <el-option label="升序" value="ASC" />
                  <el-option label="降序" value="DESC" />
                </el-select>
                <el-button link type="danger" @click="sortKeyRows.splice(idx, 1)">删</el-button>
              </div>
              <el-button size="small" @click="addSortKey">添加排序键</el-button>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'AGGREGATE'">
              <el-form-item label="分组字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="groupBySelected"
                  multiple
                  filterable
                  collapse-tags
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.groupBy" />
              </el-form-item>
              <div v-for="(a, idx) in aggRows" :key="idx" class="rule-row">
                <GovFieldSelect v-model="a.field" :fields="upstreamFields" :allow-custom="allowCustomField" placeholder="字段" />
                <el-select v-model="a.op" style="width:100px">
                  <el-option v-for="o in AGG_OPS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
                <el-input v-model="a.alias" placeholder="别名" />
                <el-button link type="danger" @click="aggRows.splice(idx, 1)">删</el-button>
              </div>
              <el-button size="small" @click="addAggRow">添加聚合</el-button>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'PIVOT'">
              <el-form-item label="透视列">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.pivotField"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.pivotField" />
              </el-form-item>
              <el-form-item label="值列">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.valueField"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.valueField" />
              </el-form-item>
              <el-form-item label="分组列">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="groupFieldsSelected"
                  multiple
                  filterable
                  collapse-tags
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.groupFields" />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'UNPIVOT'">
              <el-form-item label="保留键">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="keyFieldsSelected"
                  multiple
                  filterable
                  collapse-tags
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.keyFields" />
              </el-form-item>
              <el-form-item label="逆透视列">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="unpivotColumnsSelected"
                  multiple
                  filterable
                  collapse-tags
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.unpivotColumns" />
              </el-form-item>
              <el-form-item label="属性列名"><el-input v-model="propForm.nameColumnName" /></el-form-item>
              <el-form-item label="值列名"><el-input v-model="propForm.valueColumnName" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SET_VARIABLE'">
              <el-form-item label="变量名"><el-input v-model="propForm.field" placeholder="var_name" /></el-form-item>
              <el-form-item label="值来源">
                <el-radio-group v-model="varValueSource">
                  <el-radio-button value="CONST">常量</el-radio-button>
                  <el-radio-button value="FIELD">上游字段</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="varValueSource === 'CONST'" label="变量值">
                <el-input v-model="propForm.value" placeholder="默认值" />
              </el-form-item>
              <el-form-item v-else label="取值字段">
                <GovFieldSelect v-model="propForm.value" :fields="upstreamFields" :allow-custom="allowCustomField" />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SPLIT'">
              <el-form-item label="源字段">
                <GovFieldSelect v-model="propForm.sourceField" :fields="upstreamFields" :allow-custom="allowCustomField" />
              </el-form-item>
              <el-form-item label="分隔符">
                <el-select v-model="delimiterMode" style="width:100%">
                  <el-option v-for="d in DELIMITER_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="delimiterMode === '__CUSTOM__'" label="自定义分隔符">
                <el-input v-model="propForm.delimiter" />
              </el-form-item>
              <el-form-item label="目标字段">
                <div v-for="(t, idx) in splitTargetFields" :key="idx" class="rule-row">
                  <el-input v-model="splitTargetFields[idx]" placeholder="列名" />
                  <el-button link type="danger" @click="splitTargetFields.splice(idx, 1)">删</el-button>
                </div>
                <el-button size="small" @click="addSplitTarget">添加目标列</el-button>
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'VALUE_MAPPER'">
              <el-form-item label="源字段">
                <GovFieldSelect v-model="propForm.field" :fields="upstreamFields" :allow-custom="allowCustomField" />
              </el-form-item>
              <el-form-item label="目标字段"><el-input v-model="propForm.targetField" placeholder="默认同源字段" /></el-form-item>
              <div v-for="(vm, idx) in valueMapRows" :key="idx" class="rule-row">
                <el-input v-model="vm.fromValue" placeholder="原值" />
                <el-input v-model="vm.toValue" placeholder="新值" />
                <el-button link type="danger" @click="valueMapRows.splice(idx, 1)">删</el-button>
              </div>
              <el-button size="small" @click="addValueMap">添加映射</el-button>
              <el-form-item label="默认值" style="margin-top:8px"><el-input v-model="propForm.defaultValue" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'CONSTANT'">
              <div v-for="(c, idx) in constantRows" :key="idx" class="rule-row">
                <el-input v-model="c.field" placeholder="字段名" />
                <el-input v-model="c.value" placeholder="常量值" />
                <el-select v-model="c.valueType" style="width:100px">
                  <el-option v-for="o in TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
                <el-button link type="danger" @click="constantRows.splice(idx, 1)">删</el-button>
              </div>
              <el-button size="small" @click="addConstantRow">添加常量列</el-button>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'FORMULA'">
              <el-form-item label="结果字段"><el-input v-model="propForm.field" /></el-form-item>
              <el-form-item label="公式">
                <el-input v-model="propForm.formula" type="textarea" :rows="3" />
              </el-form-item>
              <div class="chip-row">
                <el-tag
                  v-for="f in upstreamFields.slice(0, 12)"
                  :key="f"
                  size="small"
                  class="field-chip"
                  @click="insertFieldToFormula(f)"
                >{{ f }}</el-tag>
              </div>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'STRING_CUT'">
              <el-form-item label="源字段">
                <GovFieldSelect v-model="propForm.field" :fields="upstreamFields" :allow-custom="allowCustomField" />
              </el-form-item>
              <el-form-item label="覆盖原字段"><el-switch v-model="overwriteTarget" /></el-form-item>
              <el-form-item v-if="!overwriteTarget" label="目标字段"><el-input v-model="propForm.targetField" /></el-form-item>
              <el-form-item label="起始位置"><el-input-number v-model="propForm.cutFrom" :min="0" /></el-form-item>
              <el-form-item label="结束位置"><el-input-number v-model="propForm.cutTo" :min="0" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'REPLACE_STRING'">
              <el-form-item label="源字段">
                <GovFieldSelect v-model="propForm.field" :fields="upstreamFields" :allow-custom="allowCustomField" />
              </el-form-item>
              <el-form-item label="覆盖原字段"><el-switch v-model="overwriteTarget" /></el-form-item>
              <el-form-item v-if="!overwriteTarget" label="目标字段"><el-input v-model="propForm.targetField" /></el-form-item>
              <el-form-item label="查找"><el-input v-model="propForm.search" /></el-form-item>
              <el-form-item label="替换为"><el-input v-model="propForm.replace" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'NULL_IF'">
              <el-form-item label="字段">
                <GovFieldSelect v-model="propForm.field" :fields="upstreamFields" :allow-custom="allowCustomField" />
              </el-form-item>
              <el-form-item label="等于此值则置空"><el-input v-model="propForm.value" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'IF_NULL'">
              <el-form-item label="字段">
                <GovFieldSelect v-model="propForm.field" :fields="upstreamFields" :allow-custom="allowCustomField" />
              </el-form-item>
              <el-form-item label="填充值"><el-input v-model="propForm.replaceValue" /></el-form-item>
              <el-form-item label="填充类型">
                <el-select v-model="propForm.replaceValueType" style="width:100%">
                  <el-option v-for="o in TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SELECT_FIELDS'">
              <div class="rule-toolbar">
                <el-button size="small" type="primary" @click="addSelectFieldRow">添加</el-button>
                <el-button size="small" @click="selectFieldRows = upstreamFields.map(f => ({ from: f, to: f, action: 'KEEP' as const }))">全部保留</el-button>
              </div>
              <div v-for="(m, idx) in selectFieldRows" :key="idx" class="rule-row">
                <GovFieldSelect v-model="m.from" :fields="upstreamFields" :allow-custom="allowCustomField" />
                <el-select v-model="m.action" style="width:100px">
                  <el-option label="保留" value="KEEP" />
                  <el-option label="删除" value="DROP" />
                </el-select>
                <el-input v-if="m.action === 'KEEP'" v-model="m.to" placeholder="重命名(可选)" />
                <el-button link type="danger" @click="selectFieldRows.splice(idx, 1)">删</el-button>
              </div>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'TYPE_CONVERT'">
              <div class="rule-toolbar">
                <el-button size="small" type="primary" @click="addTypeConvertRow">添加转换</el-button>
              </div>
              <div v-for="(m, idx) in typeConvertRows" :key="idx" class="rule-row rule-row--wrap">
                <GovFieldSelect v-model="m.from" :fields="upstreamFields" :allow-custom="allowCustomField" />
                <el-select v-model="m.targetType" style="width:110px">
                  <el-option v-for="o in TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
                <el-input v-model="m.to" placeholder="目标字段" />
                <el-input v-if="m.targetType === 'Date'" v-model="m.dateFormat" placeholder="日期格式" />
                <el-button link type="danger" @click="typeConvertRows.splice(idx, 1)">删</el-button>
              </div>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SWITCH_CASE'">
              <el-form-item label="分流字段">
                <GovFieldSelect v-model="propForm.field" :fields="upstreamFields" :allow-custom="allowCustomField" />
              </el-form-item>
              <div v-for="(c, idx) in switchCases" :key="idx" class="rule-row">
                <el-input v-model="c.value" placeholder="匹配值" />
                <el-input v-model="c.label" placeholder="出口标签" />
                <el-button link type="danger" @click="switchCases.splice(idx, 1)">删</el-button>
              </div>
              <el-button size="small" @click="addSwitchCase">添加分支</el-button>
              <el-alert type="info" :closable="false" show-icon title="右侧会按分支生成出口端口，另有「默认」出口" style="margin-top:8px" />
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'VALIDATOR'">
              <div v-for="(r, idx) in validatorRows" :key="idx" class="rule-row rule-row--wrap">
                <GovFieldSelect v-model="r.field" :fields="upstreamFields" :allow-custom="allowCustomField" />
                <el-select v-model="r.ruleType" style="width:110px">
                  <el-option label="非空" value="NOT_NULL" />
                  <el-option label="正则" value="REGEX" />
                  <el-option label="数值范围" value="RANGE" />
                  <el-option label="枚举" value="ENUM" />
                </el-select>
                <el-input v-model="r.param" placeholder="参数" />
                <el-select v-model="r.onFail" style="width:90px">
                  <el-option label="剔除" value="REJECT" />
                  <el-option label="告警" value="WARN" />
                </el-select>
                <el-button link type="danger" @click="validatorRows.splice(idx, 1)">删</el-button>
              </div>
              <el-button size="small" @click="addValidatorRow">添加规则</el-button>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SCRIPT'">
              <el-alert type="warning" :closable="false" show-icon title="脚本节点请谨慎使用，优先用可视化组件" style="margin-bottom:8px" />
              <el-form-item label="脚本">
                <el-input v-model="propForm.script" type="textarea" :rows="6" placeholder="JavaScript 脚本" />
              </el-form-item>
              <div class="chip-row">
                <el-tag v-for="f in upstreamFields.slice(0, 12)" :key="f" size="small" class="field-chip" @click="insertFieldToScript(f)">{{ f }}</el-tag>
              </div>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'TEXT_INPUT' || selectedNode.data?.nodeType === 'EXCEL_INPUT' || selectedNode.data?.nodeType === 'TEXT_OUTPUT'">
              <el-form-item label="文件路径"><el-input v-model="propForm.filePath" placeholder="/path/to/file.csv" /></el-form-item>
              <el-form-item label="分隔符">
                <el-select v-model="propForm.separator" style="width:100%">
                  <el-option v-for="d in DELIMITER_OPTIONS.filter(x => x.value !== '__CUSTOM__')" :key="d.value" :label="d.label" :value="d.value === '\\t' ? '\\t' : d.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="编码">
                <el-select v-model="encoding" style="width:100%">
                  <el-option label="UTF-8" value="UTF-8" />
                  <el-option label="GBK" value="GBK" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="selectedNode.data?.nodeType === 'EXCEL_INPUT'" label="工作表">
                <el-input v-model="sheetName" placeholder="Sheet1" />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'DB_LOOKUP'">
              <el-form-item label="数据源">
                <el-select v-model="propForm.connection" placeholder="选择数据源" clearable filterable>
                  <el-option
                    v-for="opt in connectionOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="表名"><el-input v-model="propForm.table" /></el-form-item>
              <el-form-item label="流字段(键)">
                <GovFieldSelect v-model="propForm.keyField" :fields="upstreamFields" :allow-custom="allowCustomField" />
              </el-form-item>
              <el-form-item label="查找键(维表列)"><el-input v-model="propForm.lookupKey" /></el-form-item>
              <div v-for="(r, idx) in lookupReturnRows" :key="idx" class="rule-row">
                <el-input v-model="r.sourceColumn" placeholder="维表列" />
                <el-input v-model="r.targetField" placeholder="写入字段" />
                <el-button link type="danger" @click="lookupReturnRows.splice(idx, 1)">删</el-button>
              </div>
              <el-button size="small" @click="addLookupReturn">添加返回列</el-button>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'HTTP'">
              <el-form-item label="URL"><el-input v-model="propForm.url" placeholder="https://..." /></el-form-item>
              <el-form-item label="方法">
                <el-select v-model="propForm.method" style="width:100%">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                </el-select>
              </el-form-item>
              <el-form-item label="超时(秒)"><el-input-number v-model="propForm.httpTimeout" :min="1" :max="300" /></el-form-item>
              <el-form-item label="结果字段"><el-input v-model="propForm.resultField" placeholder="response" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'OUTPUT' || selectedNode.data?.nodeType === 'INSERT_UPDATE'">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="标准治理默认写入 DWD；回写 ODS 仅贴源规范化且须显式勾选。"
                style="margin-bottom: 10px"
              />
              <el-form-item label="允许回写 ODS">
                <el-checkbox v-model="propForm.allowOdsWriteback" @change="onOdsWritebackChange">
                  勾选后可选 ODS（二次确认）
                </el-checkbox>
              </el-form-item>
              <el-form-item label="目标库">
                <el-select
                  v-model="propForm.outputConnection"
                  placeholder="选择目标分层库"
                  filterable
                  style="width:100%"
                  @change="onOutputConnectionChange"
                >
                  <el-option
                    v-for="opt in outputConnectionOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="目标表名">
                <div class="output-table-row">
                  <el-input v-model="propForm.outputTable" placeholder="dwd_源表名" />
                  <el-button size="small" @click="suggestOutputTable(upstreamSourceTableName)">建议表名</el-button>
                </div>
              </el-form-item>
              <el-form-item label="写入模式">
                <el-select v-model="propForm.outputMode">
                  <el-option label="插入(INSERT)" value="INSERT" />
                  <el-option label="清空后插入" value="TRUNCATE_INSERT" />
                  <el-option label="更新(UPDATE)" value="UPDATE" />
                </el-select>
              </el-form-item>
              <template v-if="selectedNode.data?.nodeType === 'INSERT_UPDATE'">
                <el-form-item label="匹配键">
                  <el-select v-model="matchKeysSelected" multiple filterable collapse-tags style="width:100%">
                    <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                  </el-select>
                </el-form-item>
                <el-form-item label="更新字段">
                  <el-select v-model="updateFieldsSelected" multiple filterable collapse-tags style="width:100%">
                    <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                  </el-select>
                </el-form-item>
              </template>
              <el-form-item label="提交批次">
                <el-input-number v-model="propForm.commitSize" :min="100" :max="10000" />
              </el-form-item>
            </template>
            </template>
          </el-form>
      </template>
      <el-empty v-else description="未选中节点" :image-size="60" />
      <template #footer>
        <el-button @click="finishPropsDialog">取消</el-button>
        <el-button type="primary" :disabled="isLockedByOther || !selectedNode" @click="finishPropsDialog">完成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="runLogVisible" title="最近运行日志" width="560px" append-to-body>
      <el-table :data="runLogs" size="small" max-height="360">
        <el-table-column prop="nodeName" label="节点" min-width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="outputRows" label="输出行" width="90" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="varDialogVisible" title="添加变量" width="480px">
      <el-form label-width="100px">
        <el-form-item
          v-for="v in varDefs"
          :key="v.name"
          :label="v.label || v.name"
          :required="v.required"
        >
          <el-input v-model="varForm[v.name]" :placeholder="v.description || v.name" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="varDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="running" @click="confirmVarRun">运行</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.design-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.design-layout {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 10px;
  height: min(720px, calc(100vh - 220px));
  min-height: 560px;
}
.palette-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  margin-bottom: 8px;
}
.palette-subtitle {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 8px 0 4px;
}
.palette-tip {
  font-size: 12px;
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  border-radius: 4px;
  padding: 6px 8px;
  margin-bottom: 8px;
  line-height: 1.4;
}
.palette {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px;
  overflow: auto;
  background: #fafafa;
}
.props-form {
  max-height: min(62vh, 560px);
  overflow: auto;
  padding-right: 4px;
}
.palette-title {
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 13px;
}
.field-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.field-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.output-table-row {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;
}
.output-table-row .el-input {
  flex: 1;
}
.palette-btn {
  width: 100%;
  margin: 0 0 6px;
  justify-content: flex-start;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}
.canvas {
  height: 100%;
  min-height: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.canvas :deep(.vue-flow) {
  width: 100%;
  height: 100%;
}
/* 去掉 VueFlow 默认外圈，选中态由节点内部样式承担 */
.canvas :deep(.vue-flow__node) {
  padding: 0 !important;
  border: none !important;
  background: transparent !important;
  box-shadow: none !important;
  outline: none !important;
}
.canvas :deep(.vue-flow__node.selected),
.canvas :deep(.vue-flow__node:focus),
.canvas :deep(.vue-flow__node:focus-visible) {
  outline: none !important;
  box-shadow: none !important;
}

/* 字段处理预览 */
.preview-title {
  font-size: 13px;
  font-weight: 600;
  margin: 8px 0;
}
.field-preview {
  max-height: 160px;
  overflow-y: auto;
}
.field-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px;
  border-radius: 4px;
  margin-bottom: 2px;
  font-size: 12px;
}
.field-item.untouched {
  color: #303133;
  background: #f5f7fa;
}
.field-item.processed {
  color: #67c23a;
  background: #f0f9eb;
}
.field-item.new {
  color: #f56c6c;
  background: #fef0f0;
}
.field-status {
  font-size: 11px;
  opacity: 0.7;
}
.preview-legend {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.legend-item {
  display: inline-block;
  font-size: 11px;
  margin-right: 8px;
}
.legend-item.untouched { color: #303133; }
.legend-item.processed { color: #67c23a; }
.legend-item.new { color: #f56c6c; }
.rule-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.rule-row {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}
.rule-row > * {
  flex: 1;
  min-width: 0;
}
.rule-row--wrap {
  flex-wrap: wrap;
}
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}
.field-chip {
  cursor: pointer;
}
</style>
