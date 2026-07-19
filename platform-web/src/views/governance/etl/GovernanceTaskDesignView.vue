<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
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
const runLogs = ref<Array<{ nodeId: string; nodeType: string; nodeName?: string; status: string; inputRows: number; outputRows: number; message?: string }>>([])

function onConnect(params: Connection) {
  if (isLockedByOther.value) return
  if (!params.source || !params.target) return
  if (params.source === params.target) return
  // @ts-expect-error vue-flow Edge type instantiation is excessively deep
  edges.value = addEdge(
    {
      ...params,
      id: `e_${params.source}_${params.target}_${Date.now()}`,
      markerEnd: MarkerType.ArrowClosed,
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

async function resolveUpstreamFields() {
  upstreamFields.value = []
  if (!canEditFieldProps.value && selectedNodeType.value !== 'OUTPUT' && selectedNodeType.value !== 'INSERT_UPDATE') {
    return
  }
  const input = selectedNodeType.value === 'OUTPUT' || selectedNodeType.value === 'INSERT_UPDATE'
    ? findUpstreamInputNode(selectedId.value || '')
    : upstreamInputNode.value
  if (!input) return
  const cfg = (input.data?.config || {}) as Record<string, unknown>
  const mode = String(cfg.inputMode || 'SAMPLE')
  const tableName = String(cfg.tableName || '').trim()
  const connection = String(cfg.connection || '')

  if (mode === 'SAMPLE') {
    upstreamFields.value = ['id', 'name', 'phone', 'idCard', 'amount', 'email']
    return
  }

  loadingFields.value = true
  try {
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
        (t) => t.sourceId === sid && (t.tableName === tableName || t.tableCode === tableName),
      )
      tableId = hit?.id
    }
    if (tableId == null && tableName) {
      const hit = registeredTables.value.find((t) => t.tableName === tableName || t.tableCode === tableName)
      tableId = hit?.id
    }
    if (tableId != null) {
      const cols = await api.get(`/exchange/ingestion/register/tables/${tableId}/columns`)
      const names = ((cols.data || []) as Array<{ columnName?: string; columnCode?: string }>)
        .map((c) => c.columnName || c.columnCode || '')
        .filter(Boolean)
      upstreamFields.value = names
    }
  } catch {
    upstreamFields.value = []
  } finally {
    loadingFields.value = false
  }
}

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
    if (t !== 'OUTPUT' && t !== 'INSERT_UPDATE') continue
    const cfg = (n.data?.config || {}) as Record<string, unknown>
    const table = String(cfg.table || cfg.outputTable || '').trim()
    const conn = String(cfg.connection || cfg.outputConnection || '').trim()
    if (!table || table === 'output_table') {
      return `输出节点「${n.data?.label || n.id}」未配置目标表`
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

// 字段处理映射解析
const fieldMappings = computed(() => {
  if (!propForm.mappings) return []
  return propForm.mappings.split(/\n/).filter(Boolean).map((line) => {
    const [from, to, expr] = line.split(':').map((s) => s.trim())
    return { from: from || '', to: to || from || '', expr: expr || 'COPY' }
  })
})

// 样例数据字段预览
const sampleFields = computed(() => {
  const processedFields = new Set(fieldMappings.value.map((m) => m.from))
  const newFields = new Set(fieldMappings.value.filter((m) => m.from !== m.to).map((m) => m.to))
  return [
    { name: 'id', status: 'untouched', label: '未处理' },
    { name: 'name', status: processedFields.has('name') ? 'processed' : 'untouched', label: processedFields.has('name') ? '已处理' : '未处理' },
    { name: 'phone', status: processedFields.has('phone') ? 'processed' : 'untouched', label: processedFields.has('phone') ? '已处理' : '未处理' },
    { name: 'email', status: 'untouched', label: '未处理' },
  ].concat(
    Array.from(newFields)
      .filter((f) => !['id', 'name', 'phone', 'email'].includes(f))
      .map((f) => ({ name: f, status: 'new', label: '新增' }))
  )
})

const dataSources = ref<Array<{ id: number; name: string; code: string }>>([])
const tables = ref<Array<{ name: string; schema?: string }>>([])
const loadingTables = ref(false)

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
  // INPUT 节点扩展
  inputMode: 'SAMPLE' as 'SAMPLE' | 'SQL' | 'TABLE',
  connection: '',
  sql: 'SELECT * FROM table_name',
  tableName: '',
  limit: 0,
  // OUTPUT 节点扩展
  outputConnection: 'smart_city_dwd',
  outputTable: '',
  outputMode: 'INSERT' as 'INSERT' | 'TRUNCATE_INSERT' | 'UPDATE',
  commitSize: 1000,
  allowOdsWriteback: false,
  // SPLIT
  sourceField: '',
  delimiter: ',',
  targetFieldsCsv: '',
  // VALUE_MAPPER / STRING transforms
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
  script: '',
  filePath: '',
  separator: ',',
  table: '',
  keyField: '',
  lookupKey: '',
  url: '',
  method: 'GET',
})

const keysSelected = computed({
  get: () => csvToList(propForm.keys),
  set: (v: string[]) => { propForm.keys = listToCsv(v) },
})
const fieldsSelected = computed({
  get: () => csvToList(propForm.fields),
  set: (v: string[]) => { propForm.fields = listToCsv(v) },
})
const sortKeysSelected = computed({
  get: () => csvToList(propForm.sortKeys),
  set: (v: string[]) => { propForm.sortKeys = listToCsv(v) },
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

const invalidSelectedFields = computed(() => {
  if (allowCustomField.value || !upstreamFields.value.length) return []
  const t = selectedNodeType.value
  const candidates: string[] = []
  switch (t) {
    case 'FILTER':
      if (propForm.filterMode === 'SIMPLE') candidates.push(propForm.field)
      break
    case 'DEDUPLICATE':
      candidates.push(...csvToList(propForm.keys), ...csvToList(propForm.sortKeys))
      break
    case 'MASK':
      candidates.push(...csvToList(propForm.fields))
      break
    case 'JOIN':
      candidates.push(propForm.leftKey, propForm.rightKey)
      break
    case 'SORT':
      candidates.push(propForm.sortField)
      break
    case 'AGGREGATE':
      candidates.push(...csvToList(propForm.groupBy))
      break
    case 'PIVOT':
      candidates.push(propForm.pivotField, propForm.valueField, ...csvToList(propForm.groupFields))
      break
    case 'UNPIVOT':
      candidates.push(...csvToList(propForm.keyFields), ...csvToList(propForm.unpivotColumns))
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
  propForm.label = String(n.data?.label || '')
  propForm.field = String(cfg.field || '')
  propForm.op = String(cfg.op || 'EQ')
  propForm.value = String(cfg.value ?? '')
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
  if (Array.isArray(cfg.mappings)) {
    propForm.mappings = (cfg.mappings as Array<{ from: string; to: string; expr: string }>)
      .map((m) => `${m.from}:${m.to}:${m.expr}`).join('\n')
  } else {
    propForm.mappings = String(cfg.mappings || '')
  }
  propForm.leftKey = String(cfg.leftKey || '')
  propForm.rightKey = String(cfg.rightKey || '')
  propForm.joinType = String(cfg.joinType || 'INNER')
  propForm.sortField = String(cfg.field || cfg.sortField || '')
  propForm.sortOrder = String(cfg.order || 'ASC')
  propForm.groupBy = Array.isArray(cfg.groupBy) ? (cfg.groupBy as string[]).join(',') : String(cfg.groupBy || '')
  if (Array.isArray(cfg.aggs)) {
    propForm.aggs = (cfg.aggs as Array<{ field: string; op: string; alias: string }>)
      .map((a) => `${a.field}:${a.op}:${a.alias}`).join('\n')
  } else {
    propForm.aggs = String(cfg.aggs || '')
  }
  propForm.pivotField = String(cfg.pivotField || '')
  propForm.valueField = String(cfg.valueField || '')
  propForm.groupFields = Array.isArray(cfg.groupFields) ? (cfg.groupFields as string[]).join(',') : String(cfg.groupFields || '')
  propForm.keyFields = Array.isArray(cfg.keyFields) ? (cfg.keyFields as string[]).join(',') : String(cfg.keyFields || '')
  propForm.unpivotColumns = Array.isArray(cfg.unpivotColumns) ? (cfg.unpivotColumns as string[]).join(',') : String(cfg.unpivotColumns || '')
  propForm.valueColumnName = String(cfg.valueColumnName || 'value')
  propForm.nameColumnName = String(cfg.nameColumnName || 'attribute')
  // INPUT 节点扩展属性
  propForm.inputMode = (cfg.inputMode as 'SAMPLE' | 'SQL' | 'TABLE') || 'SAMPLE'
  propForm.connection = String(cfg.connection || '')
  propForm.sql = String(cfg.sql || 'SELECT * FROM table_name')
  propForm.tableName = String(cfg.tableName || '')
  propForm.limit = Number(cfg.limit || 0)
  // OUTPUT 节点扩展属性
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
  propForm.script = String(cfg.script || '')
  propForm.filePath = String(cfg.filePath || '')
  propForm.separator = String(cfg.separator || ',')
  propForm.table = String(cfg.table || '')
  propForm.keyField = String(cfg.keyField || '')
  propForm.lookupKey = String(cfg.lookupKey || '')
  propForm.url = String(cfg.url || '')
  propForm.method = String(cfg.method || 'GET')
}

watch(selectedNode, (n) => {
  syncingFromNode = true
  syncPropFromNode(n)
  nextTick(() => { syncingFromNode = false })
})

watch(
  propForm,
  () => {
    if (syncingFromNode || !selectedNode.value || isLockedByOther.value) return
    if (propDebounceTimer) clearTimeout(propDebounceTimer)
    propDebounceTimer = setTimeout(() => applyProps(true), 300)
  },
  { deep: true },
)

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
  const config: Record<string, unknown> = {}
  if (type === 'INPUT') {
    config.inputMode = propForm.inputMode
    config.rowCount = propForm.rowCount
    config.connection = propForm.connection
    config.sql = propForm.sql
    config.tableName = propForm.tableName
    config.limit = propForm.limit
  } else if (type === 'OUTPUT' || type === 'INSERT_UPDATE') {
    config.connection = propForm.outputConnection
    config.outputConnection = propForm.outputConnection
    config.table = propForm.outputTable
    config.outputTable = propForm.outputTable
    config.outputMode = propForm.outputMode
    config.commitSize = propForm.commitSize
    config.allowOdsWriteback = propForm.allowOdsWriteback
  } else if (type === 'FILTER') {
    config.mode = propForm.filterMode
    if (propForm.filterMode === 'SQL') {
      config.sqlExpr = propForm.sqlExpr
    } else {
      config.field = propForm.field
      config.op = propForm.op
      config.value = propForm.value
    }
  } else if (type === 'FIELD_PROCESS') {
    config.mappings = propForm.mappings.split(/\n/).map((line) => {
      const [from, to, expr] = line.split(':').map((s) => s.trim())
      return { from, to: to || from, expr: expr || 'COPY' }
    }).filter((m) => m.from)
  } else if (type === 'DEDUPLICATE') {
    config.dedupKeys = propForm.keys.split(',').map((s) => s.trim()).filter(Boolean)
    config.keys = config.dedupKeys
    config.sortFields = propForm.sortKeys.split(',').map((s) => s.trim()).filter(Boolean)
    config.keepStrategy = propForm.keepStrategy
  } else if (type === 'MASK') {
    config.fields = propForm.fields.split(',').map((s) => s.trim()).filter(Boolean)
    config.maskType = propForm.maskType
    config.maskChar = propForm.maskChar
  } else if (type === 'JOIN') {
    config.leftKey = propForm.leftKey
    config.rightKey = propForm.rightKey
    config.joinType = propForm.joinType
  } else if (type === 'SORT') {
    config.field = propForm.sortField
    config.order = propForm.sortOrder
  } else if (type === 'AGGREGATE') {
    config.groupBy = propForm.groupBy.split(',').map((s) => s.trim()).filter(Boolean)
    config.aggs = propForm.aggs.split(/\n/).map((line) => {
      const [field, op, alias] = line.split(':').map((s) => s.trim())
      return { field, op: op || 'COUNT', alias: alias || `${field}_${op || 'COUNT'}` }
    }).filter((a) => a.field)
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
    config.variableValue = propForm.value || ''
  } else if (type === 'SPLIT') {
    config.sourceField = propForm.sourceField
    config.delimiter = propForm.delimiter
    config.targetFieldsCsv = propForm.targetFieldsCsv
  } else if (type === 'VALUE_MAPPER') {
    config.field = propForm.field
    config.targetField = propForm.targetField
    config.fromValue = propForm.fromValue
    config.toValue = propForm.toValue
    config.defaultValue = propForm.defaultValue
  } else if (type === 'CONSTANT') {
    config.field = propForm.field
    config.value = propForm.value
  } else if (type === 'FORMULA') {
    config.field = propForm.field
    config.formula = propForm.formula
  } else if (type === 'STRING_CUT') {
    config.field = propForm.field
    config.targetField = propForm.targetField
    config.cutFrom = propForm.cutFrom
    config.cutTo = propForm.cutTo
  } else if (type === 'REPLACE_STRING') {
    config.field = propForm.field
    config.targetField = propForm.targetField
    config.search = propForm.search
    config.replace = propForm.replace
  } else if (type === 'NULL_IF') {
    config.field = propForm.field
    config.value = propForm.value
  } else if (type === 'IF_NULL') {
    config.field = propForm.field
    config.replaceValue = propForm.replaceValue
  } else if (type === 'SELECT_FIELDS' || type === 'TYPE_CONVERT') {
    config.mappings = propForm.mappings.split(/\n/).map((line) => {
      const [from, to, expr] = line.split(':').map((s) => s.trim())
      return { from, to: to || from, expr: expr || 'COPY' }
    }).filter((m) => m.from)
  } else if (type === 'SWITCH_CASE' || type === 'VALIDATOR') {
    config.field = propForm.field
  } else if (type === 'SCRIPT') {
    config.script = propForm.script
  } else if (type === 'TEXT_INPUT' || type === 'EXCEL_INPUT' || type === 'TEXT_OUTPUT') {
    config.filePath = propForm.filePath
    config.separator = propForm.separator
  } else if (type === 'INSERT_UPDATE') {
    config.connection = propForm.outputConnection
    config.table = propForm.outputTable
    config.outputMode = propForm.outputMode
    config.commitSize = propForm.commitSize
  } else if (type === 'DB_LOOKUP') {
    config.connection = propForm.connection
    config.table = propForm.table
    config.keyField = propForm.keyField
    config.lookupKey = propForm.lookupKey
  } else if (type === 'HTTP') {
    config.url = propForm.url
    config.method = propForm.method
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
  const color = findComponent(type)?.color || '#909399'
  nodes.value.push({
    id,
    type: 'default',
    position: pos,
    label,
    data: {
      nodeType: type,
      label,
      config: defaultNodeConfig(type),
    },
    style: {
      border: `2px solid ${color}`,
      borderRadius: '8px',
      padding: '6px 10px',
      fontSize: '12px',
      background: '#fff',
      minWidth: '100px',
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
        markerEnd: MarkerType.ArrowClosed,
      },
      {
        id: `e_${id}_${insertEdge.target}_${ts + 1}`,
        source: id,
        target: insertEdge.target,
        markerEnd: MarkerType.ArrowClosed,
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
  nodes.value = (parsed.nodes || []).map((n) => ({
    ...n,
    type: n.type || 'default',
    label: (n.data as { label?: string })?.label || n.label || n.id,
  }))
  edges.value = (parsed.edges || []).map((e) => ({
    ...e,
    markerEnd: e.markerEnd || MarkerType.ArrowClosed,
  }))
  seq = nodes.value.length + 1
}

function buildGraphJson() {
  return JSON.stringify({
    nodes: nodes.value.map((n) => ({
      id: n.id,
      type: 'default',
      position: n.position,
      label: n.data?.label || n.label,
      data: {
        nodeType: n.data?.nodeType,
        label: n.data?.label || n.label,
        config: n.data?.config || {},
      },
      style: n.style,
    })),
    edges: edges.value.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
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

async function switchEngine(_val?: string) {
  engineType.value = 'KETTLE'
}

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
      <el-button type="primary" :loading="saving" :disabled="isLockedByOther" @click="saveGraph">保存画布</el-button>
      <el-button type="warning" :loading="publishing" :disabled="isLockedByOther" @click="publishTask">发布并解锁</el-button>
      <el-button type="success" :loading="running" :disabled="isLockedByOther" @click="runTask">运行</el-button>
      <el-button :disabled="isLockedByOther" @click="exportKtr">导出KTR</el-button>
      <el-button :disabled="isLockedByOther" @click="triggerImport">导入KTR</el-button>
      <input ref="importInput" type="file" accept=".ktr,.xml" style="display:none" @change="onImportFile" />
      <el-button type="danger" plain :disabled="(!selectedId && !selectedEdgeId) || isLockedByOther" @click="removeSelected">
        {{ selectedEdgeId ? '删除连线' : '删除节点' }}
      </el-button>
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
          fit-view-on-init
          :nodes-draggable="!isLockedByOther"
          :nodes-connectable="!isLockedByOther"
          :edges-updatable="!isLockedByOther"
          :elements-selectable="true"
          :delete-key-code="isLockedByOther ? null : ['Backspace', 'Delete']"
          @connect="onConnect"
          @edge-update="onEdgeUpdate"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @pane-click="onPaneClick"
        >
          <Background />
          <Controls />
          <MiniMap />
        </VueFlow>
      </div>

      <aside class="props">
        <div class="palette-title">属性</div>
        <template v-if="selectedNode">
          <el-form label-position="top" size="small">
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
                  <el-select v-model="propForm.connection" placeholder="选择数据源" clearable filterable>
                    <el-option
                      v-for="opt in connectionOptions"
                      :key="opt.value"
                      :label="opt.label"
                      :value="opt.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="表名">
                  <el-input v-model="propForm.tableName" placeholder="输入表名" />
                </el-form-item>
                <el-form-item label="结果限制(0=不限制)">
                  <el-input-number v-model="propForm.limit" :min="0" :max="1000000" />
                </el-form-item>
              </template>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'FILTER'">
              <el-form-item label="过滤方式">
                <el-radio-group v-model="propForm.filterMode">
                  <el-radio-button value="SIMPLE">选择过滤条件</el-radio-button>
                  <el-radio-button value="SQL">自定义表达式</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <template v-if="propForm.filterMode === 'SIMPLE'">
                <el-form-item label="字段">
                  <el-select
                    v-if="!allowCustomField && upstreamFields.length"
                    v-model="propForm.field"
                    filterable
                    clearable
                    placeholder="选择上游字段"
                    style="width:100%"
                  >
                    <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                  </el-select>
                  <el-input v-else v-model="propForm.field" placeholder="字段名" />
                </el-form-item>
                <el-form-item label="运算符">
                  <el-select v-model="propForm.op">
                    <el-option label="等于" value="EQ" />
                    <el-option label="不等于" value="NE" />
                    <el-option label="包含" value="CONTAINS" />
                    <el-option label="大于" value="GT" />
                    <el-option label="小于" value="LT" />
                    <el-option label="非空" value="NOT_NULL" />
                  </el-select>
                </el-form-item>
                <el-form-item label="值"><el-input v-model="propForm.value" /></el-form-item>
              </template>
              <el-form-item v-else label="自定义表达式">
                <el-input
                  v-model="propForm.sqlExpr"
                  type="textarea"
                  :rows="3"
                  placeholder="如: age > 18 AND status = 'active'"
                />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'FIELD_PROCESS'">
              <el-form-item label="操作记录(from:to:expr)">
                <el-input v-model="propForm.mappings" type="textarea" :rows="4" placeholder="每行一条&#10;格式：原字段:新字段:操作符&#10;操作符：COPY/UPPER/LOWER/TRIM" />
              </el-form-item>
              <el-divider content-position="left">数据与日志预览</el-divider>
              <div class="preview-title">字段预览</div>
              <div class="field-preview">
                <div
                  v-for="f in sampleFields"
                  :key="f.name"
                  class="field-item"
                  :class="f.status"
                >
                  <span class="field-name">{{ f.name }}</span>
                  <span class="field-status">{{ f.label }}</span>
                </div>
              </div>
              <div class="preview-legend">
                <span class="legend-item untouched">未处理（黑）</span>
                <span class="legend-item processed">已处理（绿）</span>
                <span class="legend-item new">新增列（红）</span>
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
                <el-radio-group v-model="propForm.maskType">
                  <el-radio value="BLUR">模糊处理</el-radio>
                  <el-radio value="MD5">MD5哈希</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="propForm.maskType === 'BLUR'" label="掩码字符">
                <el-input v-model="propForm.maskChar" maxlength="1" />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'JOIN'">
              <el-form-item label="左键">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.leftKey"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.leftKey" />
              </el-form-item>
              <el-form-item label="右键">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.rightKey"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.rightKey" />
              </el-form-item>
              <el-form-item label="关联类型">
                <el-select v-model="propForm.joinType">
                  <el-option label="内连接" value="INNER" />
                  <el-option label="左连接" value="LEFT" />
                  <el-option label="全连接" value="FULL" />
                </el-select>
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'UNION'">
              <!-- 无额外参数，靠多路上游连线合并 -->
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SORT'">
              <el-form-item label="排序字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.sortField"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.sortField" />
              </el-form-item>
              <el-form-item label="顺序">
                <el-select v-model="propForm.sortOrder">
                  <el-option label="升序" value="ASC" />
                  <el-option label="降序" value="DESC" />
                </el-select>
              </el-form-item>
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
              <el-form-item label="聚合(field:op:alias)">
                <el-input v-model="propForm.aggs" type="textarea" :rows="3" />
              </el-form-item>
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
              <el-form-item label="变量值"><el-input v-model="propForm.value" placeholder="默认值或表达式" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SPLIT'">
              <el-form-item label="源字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.sourceField"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.sourceField" />
              </el-form-item>
              <el-form-item label="分隔符"><el-input v-model="propForm.delimiter" /></el-form-item>
              <el-form-item label="目标字段(逗号)"><el-input v-model="propForm.targetFieldsCsv" placeholder="field1,field2" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'VALUE_MAPPER'">
              <el-form-item label="源字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.field"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.field" />
              </el-form-item>
              <el-form-item label="目标字段"><el-input v-model="propForm.targetField" /></el-form-item>
              <el-form-item label="原值"><el-input v-model="propForm.fromValue" /></el-form-item>
              <el-form-item label="新值"><el-input v-model="propForm.toValue" /></el-form-item>
              <el-form-item label="默认值"><el-input v-model="propForm.defaultValue" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'CONSTANT'">
              <el-form-item label="字段名"><el-input v-model="propForm.field" /></el-form-item>
              <el-form-item label="常量值"><el-input v-model="propForm.value" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'FORMULA'">
              <el-form-item label="字段名"><el-input v-model="propForm.field" /></el-form-item>
              <el-form-item label="公式"><el-input v-model="propForm.formula" type="textarea" :rows="3" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'STRING_CUT'">
              <el-form-item label="源字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.field"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.field" />
              </el-form-item>
              <el-form-item label="目标字段"><el-input v-model="propForm.targetField" /></el-form-item>
              <el-form-item label="起始位置"><el-input-number v-model="propForm.cutFrom" :min="0" /></el-form-item>
              <el-form-item label="结束位置"><el-input-number v-model="propForm.cutTo" :min="0" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'REPLACE_STRING'">
              <el-form-item label="源字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.field"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.field" />
              </el-form-item>
              <el-form-item label="目标字段"><el-input v-model="propForm.targetField" /></el-form-item>
              <el-form-item label="查找"><el-input v-model="propForm.search" /></el-form-item>
              <el-form-item label="替换为"><el-input v-model="propForm.replace" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'NULL_IF'">
              <el-form-item label="字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.field"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.field" />
              </el-form-item>
              <el-form-item label="空值条件"><el-input v-model="propForm.value" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'IF_NULL'">
              <el-form-item label="字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.field"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.field" />
              </el-form-item>
              <el-form-item label="填充值"><el-input v-model="propForm.replaceValue" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SELECT_FIELDS' || selectedNode.data?.nodeType === 'TYPE_CONVERT'">
              <el-form-item label="字段映射(from:to:expr)">
                <el-input v-model="propForm.mappings" type="textarea" :rows="4" placeholder="每行一条&#10;格式：原字段:新字段:操作符" />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SWITCH_CASE'">
              <el-form-item label="分流字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.field"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.field" />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'VALIDATOR'">
              <el-form-item label="校验字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.field"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.field" />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SCRIPT'">
              <el-form-item label="脚本">
                <el-input v-model="propForm.script" type="textarea" :rows="6" placeholder="JavaScript 脚本" />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'TEXT_INPUT' || selectedNode.data?.nodeType === 'EXCEL_INPUT' || selectedNode.data?.nodeType === 'TEXT_OUTPUT'">
              <el-form-item label="文件路径"><el-input v-model="propForm.filePath" placeholder="/path/to/file.csv" /></el-form-item>
              <el-form-item label="分隔符"><el-input v-model="propForm.separator" /></el-form-item>
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
              <el-form-item label="键字段">
                <el-select
                  v-if="!allowCustomField && upstreamFields.length"
                  v-model="propForm.keyField"
                  filterable
                  clearable
                  style="width:100%"
                >
                  <el-option v-for="f in upstreamFields" :key="f" :label="f" :value="f" />
                </el-select>
                <el-input v-else v-model="propForm.keyField" />
              </el-form-item>
              <el-form-item label="查找键"><el-input v-model="propForm.lookupKey" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'HTTP'">
              <el-form-item label="URL"><el-input v-model="propForm.url" placeholder="https://..." /></el-form-item>
              <el-form-item label="方法">
                <el-select v-model="propForm.method">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                </el-select>
              </el-form-item>
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
              <el-form-item label="提交批次">
                <el-input-number v-model="propForm.commitSize" :min="100" :max="10000" />
              </el-form-item>
            </template>
            </template>
            <el-button type="primary" size="small" :disabled="isLockedByOther" @click="applyProps()">应用属性</el-button>
          </el-form>
        </template>
        <el-empty v-else description="选中节点编辑属性" :image-size="60" />

        <el-divider />
        <div class="palette-title">最近运行日志</div>
        <el-table :data="runLogs" size="small" max-height="220">
          <el-table-column prop="nodeName" label="节点" width="80" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="outputRows" label="输出行" width="70" />
        </el-table>
      </aside>
    </div>

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
  grid-template-columns: 160px 1fr 320px;
  gap: 10px;
  height: 560px;
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
.palette, .props {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 10px;
  overflow: auto;
  background: #fafafa;
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
</style>
