<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow, type Connection, type Edge, type Node, MarkerType } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'

const props = defineProps<{ taskId: number }>()

const route = useRoute()
const router = useRouter()

const NODE_TYPES = [
  { type: 'INPUT', label: '输入', color: '#409eff' },
  { type: 'FILTER', label: '过滤', color: '#67c23a' },
  { type: 'FIELD_PROCESS', label: '字段处理', color: '#e6a23c' },
  { type: 'DEDUPLICATE', label: '去重', color: '#909399' },
  { type: 'MASK', label: '脱敏', color: '#f56c6c' },
  { type: 'OUTPUT', label: '输出', color: '#626aef' },
]

const FUSION_NODE_TYPES = [
  { type: 'JOIN', label: '关联 JOIN', color: '#337ecc' },
  { type: 'UNION', label: '合并 UNION', color: '#529b2e' },
  { type: 'SORT', label: '排序 SORT', color: '#b88230' },
  { type: 'AGGREGATE', label: '聚合 AGGREGATE', color: '#73767a' },
  { type: 'PIVOT', label: '透视 PIVOT', color: '#c45656' },
  { type: 'UNPIVOT', label: '逆透视 UNPIVOT', color: '#5156c6' },
  { type: 'SET_VARIABLE', label: '参数设置', color: '#9b59b6' },
]

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

// 当前登录用户（从登录态获取，这里用用户名占位）
const currentUser = localStorage.getItem('username') || 'system'

// 判断是否被他人锁定
const isLockedByOther = computed(() => status.value === 'LOCKED' && lockedBy.value !== currentUser && lockedBy.value !== null)
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
const selectedId = ref<string | null>(null)
const runLogs = ref<Array<{ nodeId: string; nodeType: string; nodeName?: string; status: string; inputRows: number; outputRows: number; message?: string }>>([])

function onConnect(params: Connection) {
  if (isLockedByOther.value) return
  if (!params.source || !params.target) return
  // @ts-expect-error vue-flow Edge type instantiation is excessively deep
  edges.value = [
    ...edges.value,
    {
      id: `e_${params.source}_${params.target}_${Date.now()}`,
      source: params.source,
      target: params.target,
      sourceHandle: params.sourceHandle,
      targetHandle: params.targetHandle,
      markerEnd: MarkerType.ArrowClosed,
    },
  ]
}

const selectedNode = computed(() => nodes.value.find((n) => n.id === selectedId.value) || null)

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
  keys: 'id',
  sortKeys: '',
  keepStrategy: 'FIRST',
  fields: 'phone,idCard',
  maskChar: '*',
  maskType: 'BLUR',
  rowCount: 10,
  mappings: 'name:name_upper:UPPER',
  leftKey: 'id',
  rightKey: 'id',
  joinType: 'INNER',
  sortField: 'id',
  sortOrder: 'ASC',
  groupBy: 'id',
  aggs: 'amount:SUM:sum_amount',
  pivotField: 'category',
  valueField: 'amount',
  groupFields: 'id',
  keyFields: 'id',
  unpivotColumns: 'col_a,col_b',
  valueColumnName: 'value',
  nameColumnName: 'attribute',
  // INPUT 节点扩展
  inputMode: 'SAMPLE' as 'SAMPLE' | 'SQL' | 'TABLE',
  connection: '',
  sql: 'SELECT * FROM table_name',
  tableName: '',
  limit: 0,
  // OUTPUT 节点扩展
  outputConnection: '',
  outputTable: '',
  outputMode: 'INSERT' as 'INSERT' | 'TRUNCATE_INSERT' | 'UPDATE',
  commitSize: 1000,
})

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
    : Array.isArray(cfg.dedupKeys) ? (cfg.dedupKeys as string[]).join(',') : 'id'
  propForm.sortKeys = Array.isArray(cfg.sortFields) ? (cfg.sortFields as string[]).join(',') : String(cfg.sortFields || '')
  propForm.keepStrategy = String(cfg.keepStrategy || 'FIRST')
  propForm.fields = Array.isArray(cfg.fields) ? (cfg.fields as string[]).join(',') : 'phone,idCard'
  propForm.maskChar = String(cfg.maskChar || '*')
  propForm.maskType = String(cfg.maskType || 'BLUR')
  propForm.rowCount = Number(cfg.rowCount || 10)
  if (Array.isArray(cfg.mappings)) {
    propForm.mappings = (cfg.mappings as Array<{ from: string; to: string; expr: string }>)
      .map((m) => `${m.from}:${m.to}:${m.expr}`).join('\n')
  } else {
    propForm.mappings = 'name:name_upper:UPPER'
  }
  propForm.leftKey = String(cfg.leftKey || 'id')
  propForm.rightKey = String(cfg.rightKey || 'id')
  propForm.joinType = String(cfg.joinType || 'INNER')
  propForm.sortField = String(cfg.field || 'id')
  propForm.sortOrder = String(cfg.order || 'ASC')
  propForm.groupBy = Array.isArray(cfg.groupBy) ? (cfg.groupBy as string[]).join(',') : String(cfg.groupBy || 'id')
  if (Array.isArray(cfg.aggs)) {
    propForm.aggs = (cfg.aggs as Array<{ field: string; op: string; alias: string }>)
      .map((a) => `${a.field}:${a.op}:${a.alias}`).join('\n')
  } else {
    propForm.aggs = 'amount:SUM:sum_amount'
  }
  propForm.pivotField = String(cfg.pivotField || 'category')
  propForm.valueField = String(cfg.valueField || 'amount')
  propForm.groupFields = Array.isArray(cfg.groupFields) ? (cfg.groupFields as string[]).join(',') : String(cfg.groupFields || 'id')
  propForm.keyFields = Array.isArray(cfg.keyFields) ? (cfg.keyFields as string[]).join(',') : String(cfg.keyFields || 'id')
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
  propForm.outputConnection = String(cfg.connection || cfg.outputConnection || '')
  propForm.outputTable = String(cfg.table || cfg.outputTable || '')
  propForm.outputMode = (cfg.outputMode as 'INSERT' | 'TRUNCATE_INSERT' | 'UPDATE') || 'INSERT'
  propForm.commitSize = Number(cfg.commit || cfg.commitSize || 1000)
}

watch(selectedNode, (n) => syncPropFromNode(n))

function applyProps() {
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
  } else if (type === 'OUTPUT') {
    config.connection = propForm.outputConnection
    config.table = propForm.outputTable
    config.outputMode = propForm.outputMode
    config.commitSize = propForm.commitSize
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
  }
  n.data.config = config
  ElMessage.success('属性已应用')
}

let seq = 1
function addNode(type: string, label: string) {
  if (isLockedByOther.value) {
    ElMessage.warning('任务已被他人锁定，不可编辑')
    return
  }
  const id = `n_${type}_${seq++}_${Date.now()}`
  const pos = { x: 120 + (seq % 5) * 140, y: 80 + (seq % 4) * 90 }
  const allTypes = [...NODE_TYPES, ...FUSION_NODE_TYPES]
  nodes.value.push({
    id,
    type: 'default',
    position: pos,
    label,
    data: {
      nodeType: type,
      label,
      config: type === 'INPUT' ? { rowCount: 10 } : {},
    },
    style: {
      border: `2px solid ${allTypes.find((t) => t.type === type)?.color || '#909399'}`,
      borderRadius: '8px',
      padding: '6px 10px',
      fontSize: '12px',
      background: '#fff',
      minWidth: '100px',
    },
  })
  selectedId.value = id
}

function onNodeClick(ev: { node: Node }) {
  selectedId.value = ev.node.id
}

function removeSelected() {
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
  // 同步引擎类型
  try {
    const detail = await api.get(`/governance/gov-tasks/${props.taskId}`)
    engineType.value = detail.data?.engineType || 'KETTLE'
  } catch {
    engineType.value = 'KETTLE'
  }
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
    await api.put(`/governance/gov-tasks/${props.taskId}/graph`, {
      graphJson: buildGraphJson(),
    })
    ElMessage.success('画布已保存')
    status.value = 'READY'
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

async function switchEngine(val: string) {
  try {
    await api.put(`/governance/gov-tasks/${props.taskId}/engine`, { engineType: val })
    engineType.value = val
    ElMessage.success(val === 'KETTLE' ? '已切换为 Kettle 引擎' : '已切换为内存引擎')
  } catch {
    ElMessage.error('切换引擎失败')
  }
}

async function exportKtr() {
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

function backToList() {
  const q: Record<string, any> = { ...route.query, tab: 'etl' }
  delete q.etlView
  delete q.taskId
  router.replace({ query: q })
}

onMounted(async () => {
  await nextTick()
  await loadGraph()
})
</script>

<template>
  <PageCard :title="`任务设计 · ${taskName || taskId}`">
    <div class="design-toolbar">
      <el-button @click="backToList">返回列表</el-button>
      <el-tag size="small">{{ statusLabel(status) }}</el-tag>
      <el-tag v-if="lockedBy && lockedBy !== currentUser" type="warning" size="small">已被 {{ lockedBy }} 锁定</el-tag>
      <el-select
        v-model="engineType"
        size="small"
        style="width: 130px"
        :disabled="isLockedByOther"
        @change="switchEngine"
      >
        <el-option label="Kettle引擎" value="KETTLE" />
        <el-option label="内存引擎" value="IN_MEMORY" />
      </el-select>
      <el-button type="primary" :loading="saving" :disabled="isLockedByOther" @click="saveGraph">保存画布</el-button>
      <el-button type="warning" :loading="publishing" :disabled="isLockedByOther" @click="publishTask">发布并解锁</el-button>
      <el-button type="success" :loading="running" :disabled="isLockedByOther" @click="runTask">运行</el-button>
      <el-button :disabled="isLockedByOther" @click="exportKtr">导出KTR</el-button>
      <el-button :disabled="isLockedByOther" @click="triggerImport">导入KTR</el-button>
      <input ref="importInput" type="file" accept=".ktr,.xml" style="display:none" @change="onImportFile" />
      <el-button type="danger" plain :disabled="!selectedId || isLockedByOther" @click="removeSelected">删除节点</el-button>
    </div>

    <div class="design-layout">
      <aside class="palette">
        <div class="palette-title">治理组件</div>
        <el-button
          v-for="item in NODE_TYPES"
          :key="item.type"
          class="palette-btn"
          :disabled="isLockedByOther"
          @click="addNode(item.type, item.label)"
        >
          <span class="dot" :style="{ background: item.color }" />
          {{ item.label }}
        </el-button>
        <el-divider />
        <div class="palette-title">融合组件</div>
        <el-button
          v-for="item in FUSION_NODE_TYPES"
          :key="item.type"
          class="palette-btn"
          :disabled="isLockedByOther"
          @click="addNode(item.type, item.label)"
        >
          <span class="dot" :style="{ background: item.color }" />
          {{ item.label }}
        </el-button>
      </aside>

      <div class="canvas">
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          fit-view-on-init
          :nodes-draggable="!isLockedByOther"
          :nodes-connectable="!isLockedByOther"
          :elements-selectable="!isLockedByOther"
          @connect="onConnect"
          @node-click="onNodeClick"
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
              <el-tag>{{ selectedNode.data?.nodeType }}</el-tag>
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="propForm.label" />
            </el-form-item>
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
                <el-alert type="info" title="提示" :closable="false" show-icon>
                  样例数据模式将生成包含 id, name, phone, idCard, amount, email 等字段的模拟数据
                </el-alert>
              </template>
              <template v-else-if="propForm.inputMode === 'SQL'">
                <el-form-item label="数据源">
                  <el-select v-model="propForm.connection" placeholder="选择数据源" clearable>
                    <el-option label="默认数据源" value="default" />
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
                  <el-select v-model="propForm.connection" placeholder="选择数据源" clearable>
                    <el-option label="默认数据源" value="default" />
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
                  <el-radio-button value="SIMPLE">条件选择</el-radio-button>
                  <el-radio-button value="SQL">SQL表达式</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <template v-if="propForm.filterMode === 'SIMPLE'">
                <el-form-item label="字段"><el-input v-model="propForm.field" /></el-form-item>
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
              <el-form-item v-else label="SQL WHERE">
                <el-input
                  v-model="propForm.sqlExpr"
                  type="textarea"
                  :rows="3"
                  placeholder="如: age > 18 AND status = 'active'"
                />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'FIELD_PROCESS'">
              <el-form-item label="映射(from:to:expr)">
                <el-input v-model="propForm.mappings" type="textarea" :rows="4" placeholder="每行一条&#10;格式：原字段:新字段:操作符&#10;操作符：COPY/UPPER/LOWER/TRIM" />
              </el-form-item>
              <el-divider />
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
                <span class="legend-item untouched">未处理：黑色</span>
                <span class="legend-item processed">已处理：绿色</span>
                <span class="legend-item new">新增列：红色</span>
              </div>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'DEDUPLICATE'">
              <el-form-item label="去重字段(逗号分隔)">
                <el-input v-model="propForm.keys" placeholder="id,name" />
              </el-form-item>
              <el-form-item label="排序字段(逗号分隔)">
                <el-input v-model="propForm.sortKeys" placeholder="updated_at" />
              </el-form-item>
              <el-form-item label="保留策略">
                <el-radio-group v-model="propForm.keepStrategy">
                  <el-radio value="FIRST">保留第一条</el-radio>
                  <el-radio value="LAST">保留最后一条</el-radio>
                </el-radio-group>
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'MASK'">
              <el-form-item label="脱敏字段"><el-input v-model="propForm.fields" placeholder="phone,idCard" /></el-form-item>
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
              <el-form-item label="左键"><el-input v-model="propForm.leftKey" /></el-form-item>
              <el-form-item label="右键"><el-input v-model="propForm.rightKey" /></el-form-item>
              <el-form-item label="关联类型">
                <el-select v-model="propForm.joinType">
                  <el-option label="内连接" value="INNER" />
                  <el-option label="左连接" value="LEFT" />
                  <el-option label="全连接" value="FULL" />
                </el-select>
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'UNION'">
              <el-alert type="info" :closable="false" title="连接多个上游节点，按行合并" />
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SORT'">
              <el-form-item label="排序字段"><el-input v-model="propForm.sortField" /></el-form-item>
              <el-form-item label="顺序">
                <el-select v-model="propForm.sortOrder">
                  <el-option label="升序" value="ASC" />
                  <el-option label="降序" value="DESC" />
                </el-select>
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'AGGREGATE'">
              <el-form-item label="分组字段"><el-input v-model="propForm.groupBy" /></el-form-item>
              <el-form-item label="聚合(field:op:alias)">
                <el-input v-model="propForm.aggs" type="textarea" :rows="3" />
              </el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'PIVOT'">
              <el-form-item label="透视列"><el-input v-model="propForm.pivotField" /></el-form-item>
              <el-form-item label="值列"><el-input v-model="propForm.valueField" /></el-form-item>
              <el-form-item label="分组列"><el-input v-model="propForm.groupFields" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'UNPIVOT'">
              <el-form-item label="保留键"><el-input v-model="propForm.keyFields" /></el-form-item>
              <el-form-item label="逆透视列"><el-input v-model="propForm.unpivotColumns" /></el-form-item>
              <el-form-item label="属性列名"><el-input v-model="propForm.nameColumnName" /></el-form-item>
              <el-form-item label="值列名"><el-input v-model="propForm.valueColumnName" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'SET_VARIABLE'">
              <el-form-item label="变量名"><el-input v-model="propForm.field" placeholder="var_name" /></el-form-item>
              <el-form-item label="变量值"><el-input v-model="propForm.value" placeholder="默认值或表达式" /></el-form-item>
            </template>
            <template v-else-if="selectedNode.data?.nodeType === 'OUTPUT'">
              <el-form-item label="数据源">
                <el-select v-model="propForm.outputConnection" placeholder="选择数据源" clearable>
                  <el-option label="默认数据源" value="default" />
                </el-select>
              </el-form-item>
              <el-form-item label="目标表名">
                <el-input v-model="propForm.outputTable" placeholder="target_table" />
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
            <el-button type="primary" size="small" :disabled="isLockedByOther" @click="applyProps">应用属性</el-button>
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
  grid-template-columns: 140px 1fr 260px;
  gap: 10px;
  height: 560px;
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
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
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
