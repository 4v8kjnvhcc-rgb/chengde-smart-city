<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'

interface GraphNode {
  id: string
  label: string
  type: string
  dataLayer?: string
  hop?: number
  tableCode?: string
}

interface GraphEdge {
  id?: number
  from: string
  to: string
  label: string
  type: string
  derived?: boolean
}

interface TableEntry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
  dataLayer?: string
}

interface RelationRow {
  id: number
  fromCode: string
  toCode: string
  fromName?: string
  toName?: string
  relationType: string
  label?: string
  createdAt?: string
}

interface ImpactDetail {
  entryCode: string
  entryName: string
  entryType: string
  dataLayer?: string
  hop: number
}

const activeTab = ref<'assoc' | 'lineage' | 'impact'>('assoc')
const kpi = reactive({
  tableCount: 0,
  assocCount: 0,
  lineageCount: 0,
  columnLineageCount: 0,
  impactEdgeCount: 0,
})

const tableEntries = ref<TableEntry[]>([])
const entryCode = ref('')
const loading = ref(false)

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const graph = ref<{ nodes: GraphNode[]; edges: GraphEdge[]; source?: string }>({ nodes: [], edges: [] })
const fieldGraph = ref<{ nodes: GraphNode[]; edges: GraphEdge[] }>({ nodes: [], edges: [] })
const lineageMeta = reactive({ upstreamCount: 0, downstreamCount: 0, level: 'TABLE' as 'TABLE' | 'COLUMN' })
const fieldDrill = ref(false)

const relations = ref<RelationRow[]>([])
const relationKeyword = ref('')
const relationForm = reactive({
  fromCode: '',
  toCode: '',
  relationType: 'ASSOC',
  label: '',
})

const connectors = ref<Array<{ id: number; connectorName: string }>>([])
const fkConnectorId = ref<number | undefined>()
const parseLoading = ref(false)

const impactDetails = ref<ImpactDetail[]>([])
const impactRisk = ref('')
const impactMaxHop = ref(0)
const offlineAssess = ref<{ canOffline?: boolean; hasDownstream?: boolean } | null>(null)
const relatedTasks = ref<Array<Record<string, unknown>>>([])
const maxDepth = ref(10)

const entrySelectOptions = computed(() =>
  tableEntries.value.map(e => ({
    value: e.entryCode,
    label: `${e.entryName}（${e.entryCode}）`,
  })),
)

const RELATION_OPTIONS = [
  { label: '关联', value: 'ASSOC' },
  { label: '外键', value: 'FK' },
  { label: '血缘', value: 'LINEAGE' },
  { label: '字段血缘', value: 'COLUMN_LINEAGE' },
  { label: '影响', value: 'IMPACT' },
]

function riskTagType(r?: string) {
  if (r === 'HIGH') return 'danger'
  if (r === 'MEDIUM') return 'warning'
  return 'success'
}

function formatTime(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

function renderChart(nodes: GraphNode[], edges: GraphEdge[], highlightId?: string) {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const cats = [...new Set(nodes.map(n => n.type || 'UNKNOWN'))].map(c => ({ name: statusLabel(c) || c }))
  const catIndex = new Map(cats.map((c, i) => [c.name, i]))
  const data = nodes.map(n => {
    const catName = statusLabel(n.type) || n.type
    return {
      id: n.id,
      name: n.label || n.id,
      category: catIndex.get(catName) ?? 0,
      symbolSize: n.type === 'ROOT' || n.id === highlightId ? 52 : n.type === 'COLUMN' ? 28 : 38,
      itemStyle: n.type === 'ROOT' || n.id === highlightId
        ? { color: '#1677ff' }
        : undefined,
    }
  })
  const links = edges.map(e => ({
    source: e.from,
    target: e.to,
    label: {
      show: true,
      formatter: e.label || statusLabel(e.type),
      fontSize: 10,
    },
    lineStyle: {
      color: e.type === 'COLUMN_LINEAGE' ? '#67c23a' : e.type === 'FK' ? '#e6a23c' : '#909399',
      curveness: 0.15,
    },
  }))
  chart.setOption({
    tooltip: {
      formatter: (p: { dataType?: string; name?: string; data?: { name?: string } }) => {
        if (p.dataType === 'edge') return String(p.name || '')
        return String(p.data?.name || p.name || '')
      },
    },
    legend: [{ data: cats.map(c => c.name), bottom: 0 }],
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      label: { show: true, position: 'right', fontSize: 11 },
      force: { repulsion: 160, edgeLength: [80, 160] },
      categories: cats,
      data,
      links,
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 8,
    }],
  }, true)
}

async function loadOverview() {
  const res = await api.get('/governance/platform/metadata/analyze/overview')
  const k = res.data?.kpi || {}
  kpi.tableCount = Number(k.tableCount || 0)
  kpi.assocCount = Number(k.assocCount || 0)
  kpi.lineageCount = Number(k.lineageCount || 0)
  kpi.columnLineageCount = Number(k.columnLineageCount || 0)
  kpi.impactEdgeCount = Number(k.impactEdgeCount || 0)
}

async function loadTables() {
  tableEntries.value = (await api.get('/governance/platform/metadata/analyze/tables')).data || []
  if (!entryCode.value && tableEntries.value.length) {
    entryCode.value = tableEntries.value[0].entryCode
  }
}

async function loadConnectors() {
  if (connectors.value.length) return
  const ov = await api.get('/governance/platform/metadata/overview')
  connectors.value = ov.data?.connectors || []
  if (connectors.value.length && !fkConnectorId.value) {
    fkConnectorId.value = connectors.value[0].id
  }
}

async function loadAssoc() {
  loading.value = true
  try {
    const [g, rels] = await Promise.all([
      api.get('/governance/platform/metadata/analyze', {
        params: { relationType: 'ASSOC', focusCode: entryCode.value || undefined },
      }),
      api.get('/governance/platform/metadata/relations', {
        params: { relationType: 'ASSOC', keyword: relationKeyword.value || undefined },
      }),
    ])
    graph.value = {
      nodes: g.data?.nodes || [],
      edges: g.data?.edges || [],
      source: g.data?.source,
    }
    relations.value = rels.data || []
    fieldGraph.value = { nodes: [], edges: [] }
    await nextTick()
    renderChart(graph.value.nodes, graph.value.edges, entryCode.value)
  } finally {
    loading.value = false
  }
}

async function loadLineage() {
  if (!entryCode.value) {
    ElMessage.warning('请选择分析表')
    return
  }
  loading.value = true
  try {
    const level = fieldDrill.value ? 'COLUMN' : 'TABLE'
    const data = (await api.get('/governance/platform/metadata/analyze/lineage', {
      params: { entryCode: entryCode.value, level },
    })).data
    lineageMeta.upstreamCount = Number(data?.upstreamCount || 0)
    lineageMeta.downstreamCount = Number(data?.downstreamCount || 0)
    lineageMeta.level = level
    graph.value = { nodes: data?.nodes || [], edges: data?.edges || [], source: 'lineage' }
    fieldGraph.value = {
      nodes: data?.fieldNodes || [],
      edges: data?.fieldEdges || [],
    }
    await nextTick()
    if (fieldDrill.value && fieldGraph.value.edges.length) {
      renderChart(fieldGraph.value.nodes, fieldGraph.value.edges)
    } else {
      renderChart(graph.value.nodes, graph.value.edges, entryCode.value)
    }
  } finally {
    loading.value = false
  }
}

async function loadImpact() {
  if (!entryCode.value) {
    ElMessage.warning('请选择分析起点')
    return
  }
  loading.value = true
  try {
    const data = (await api.get('/governance/platform/metadata/analyze/impact', {
      params: { fromCode: entryCode.value, maxDepth: maxDepth.value },
    })).data
    impactDetails.value = data?.impactedDetails || []
    impactRisk.value = data?.riskLevel || ''
    impactMaxHop.value = Number(data?.maxHop || 0)
    offlineAssess.value = data?.offlineAssess || null
    graph.value = {
      nodes: data?.nodes || [],
      edges: data?.edges || [],
      source: 'impact',
    }
    fieldGraph.value = { nodes: [], edges: [] }
    await nextTick()
    renderChart(graph.value.nodes, graph.value.edges, entryCode.value)
    ElMessage.success(`下游影响 ${data?.count ?? 0} 个节点`)
  } finally {
    loading.value = false
  }
}

async function loadRelatedTasks() {
  if (!entryCode.value) return
  relatedTasks.value = (await api.get('/governance/platform/metadata/analyze/tasks', {
    params: { entryCode: entryCode.value },
  })).data || []
}

async function refreshCurrent() {
  if (activeTab.value === 'assoc') await loadAssoc()
  else if (activeTab.value === 'lineage') await loadLineage()
  else await loadImpact()
  await loadOverview()
}

async function addRelation() {
  if (!relationForm.fromCode || !relationForm.toCode) {
    ElMessage.warning('请选择起点与终点')
    return
  }
  await api.post('/governance/platform/metadata/relations', { ...relationForm })
  ElMessage.success('关联已添加')
  relationForm.fromCode = ''
  relationForm.toCode = ''
  relationForm.label = ''
  await refreshCurrent()
}

async function removeRelation(row: RelationRow) {
  try {
    await ElMessageBox.confirm(`确认删除关系 ${row.fromName || row.fromCode} → ${row.toName || row.toCode}？`, '删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  await api.delete(`/governance/platform/metadata/relations/${row.id}`)
  ElMessage.success('已删除')
  await refreshCurrent()
}

async function parseFk() {
  await loadConnectors()
  if (!fkConnectorId.value) {
    ElMessage.warning('请选择 OM 适配器')
    return
  }
  parseLoading.value = true
  try {
    const n = (await api.post('/governance/platform/metadata/relations/parse-fk', null, {
      params: { connectorId: fkConnectorId.value },
    })).data
    ElMessage.success(`解析外键/字段关联 ${n} 条`)
    await refreshCurrent()
  } finally {
    parseLoading.value = false
  }
}

async function parseLineage() {
  parseLoading.value = true
  try {
    const data = (await api.post('/governance/platform/metadata/relations/parse-lineage')).data
    ElMessage.success(`自动解析：表级 ${data?.tableEdges ?? 0} · 字段级 ${data?.fieldEdges ?? 0}`)
    await refreshCurrent()
  } finally {
    parseLoading.value = false
  }
}

function switchFieldView(on: boolean) {
  fieldDrill.value = on
  loadLineage()
}

function onResize() {
  chart?.resize()
}

watch(activeTab, async (tab) => {
  if (tab === 'assoc') await loadAssoc()
  else if (tab === 'lineage') await loadLineage()
  else {
    await loadImpact()
    await loadRelatedTasks()
  }
})

onMounted(async () => {
  await Promise.all([loadOverview(), loadTables()])
  await loadAssoc()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<template>
  <div>
    <div class="manalyze-kpi">
      <div class="manalyze-kpi__card">
        <span>元数据表</span>
        <b>{{ kpi.tableCount }}</b>
      </div>
      <div class="manalyze-kpi__card tone-warn">
        <span>关联/外键</span>
        <b>{{ kpi.assocCount }}</b>
      </div>
      <div class="manalyze-kpi__card tone-ok">
        <span>表级血缘</span>
        <b>{{ kpi.lineageCount }}</b>
      </div>
      <div class="manalyze-kpi__card tone-info">
        <span>字段血缘</span>
        <b>{{ kpi.columnLineageCount }}</b>
      </div>
    </div>

    <PageCard title="元数据分析">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="关联分析" name="assoc" />
        <el-tab-pane label="血缘分析" name="lineage" />
        <el-tab-pane label="影响分析" name="impact" />
      </el-tabs>

      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="分析表" class="portal-field-xl">
          <el-select v-model="entryCode" filterable clearable placeholder="选择 TABLE">
            <el-option v-for="o in entrySelectOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="activeTab === 'impact'" label="递归深度" class="portal-field-sm">
          <el-input-number v-model="maxDepth" :min="1" :max="30" controls-position="right" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :loading="loading" @click="refreshCurrent">
            {{ activeTab === 'impact' ? '执行影响分析' : '刷新图谱' }}
          </el-button>
          <el-button
            v-if="activeTab === 'lineage'"
            :type="fieldDrill ? 'success' : 'default'"
            @click="switchFieldView(!fieldDrill)"
          >
            {{ fieldDrill ? '表级血缘' : '下钻字段血缘' }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="activeTab === 'assoc'"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="基于主外键自动解析，可视化表间关联；可手工维护关联关系，支撑业务梳理。"
      />
      <el-alert
        v-else-if="activeTab === 'lineage'"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        :title="`反映数据来源与加工过程。上游 ${lineageMeta.upstreamCount} · 下游 ${lineageMeta.downstreamCount}${fieldDrill ? ' · 当前为字段级视图' : ''}`"
      />
      <el-alert
        v-else
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="从选定实体出发递归查找下游依赖，评估变更风险，支撑资产清理与维护。"
      />

      <div v-if="activeTab === 'impact' && impactRisk" class="manalyze-impact-bar">
        <el-tag :type="riskTagType(impactRisk)" size="small">风险 {{ statusLabel(impactRisk) }}</el-tag>
        <span>影响节点 {{ impactDetails.length }} · 最大跳数 {{ impactMaxHop }}</span>
        <el-tag v-if="offlineAssess" :type="offlineAssess.canOffline ? 'success' : 'warning'" size="small">
          {{ offlineAssess.canOffline ? '可下线' : '存在下游依赖' }}
        </el-tag>
      </div>

      <div ref="chartRef" v-loading="loading" class="manalyze-chart" />

      <el-row :gutter="12" style="margin-bottom: 12px">
        <el-col :span="10">
          <div class="manalyze-section">节点</div>
          <el-table
            :data="fieldDrill && activeTab === 'lineage' ? fieldGraph.nodes : graph.nodes"
            stripe
            size="small"
            max-height="240"
          >
            <el-table-column prop="id" label="编码" width="140" show-overflow-tooltip />
            <el-table-column prop="label" label="名称" show-overflow-tooltip />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ statusLabel(row.type) }}</template>
            </el-table-column>
            <el-table-column v-if="activeTab === 'impact'" label="跳数" width="70">
              <template #default="{ row }">{{ row.hop ?? (row.type === 'ROOT' ? 0 : '—') }}</template>
            </el-table-column>
          </el-table>
        </el-col>
        <el-col :span="14">
          <div class="manalyze-section">边</div>
          <el-table
            :data="fieldDrill && activeTab === 'lineage' ? fieldGraph.edges : graph.edges"
            stripe
            size="small"
            max-height="240"
          >
            <el-table-column prop="from" label="从" width="140" show-overflow-tooltip />
            <el-table-column prop="to" label="到" width="140" show-overflow-tooltip />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ statusLabel(row.type) }}</template>
            </el-table-column>
            <el-table-column prop="label" label="说明" show-overflow-tooltip />
          </el-table>
        </el-col>
      </el-row>

      <!-- 关联分析：自动解析 + 手工维护 -->
      <template v-if="activeTab === 'assoc'">
        <div class="manalyze-section">自动解析</div>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="OM 适配器" class="portal-field-lg">
            <el-select v-model="fkConnectorId" clearable placeholder="外键解析" @focus="loadConnectors">
              <el-option v-for="c in connectors" :key="c.id" :label="c.connectorName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button :loading="parseLoading" @click="parseFk">解析主外键</el-button>
            <el-button :loading="parseLoading" type="primary" @click="parseLineage">补齐归属血缘</el-button>
          </el-form-item>
        </el-form>

        <div class="manalyze-section">手工维护关联</div>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="从" class="portal-field-xl">
            <el-select v-model="relationForm.fromCode" filterable clearable>
              <el-option v-for="o in entrySelectOptions" :key="'f' + o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="到" class="portal-field-xl">
            <el-select v-model="relationForm.toCode" filterable clearable>
              <el-option v-for="o in entrySelectOptions" :key="'t' + o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="relationForm.relationType">
              <el-option v-for="o in RELATION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="说明" class="portal-field-md">
            <el-input v-model="relationForm.label" placeholder="如 col→refCol" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="addRelation">添加</el-button>
          </el-form-item>
        </el-form>

        <el-form inline class="portal-inline-form" style="margin-bottom:8px">
          <el-form-item label="关系检索" class="portal-field-lg">
            <el-input v-model="relationKeyword" clearable placeholder="编码/说明" @keyup.enter="loadAssoc" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="loadAssoc">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="relations" stripe size="small" max-height="280" empty-text="暂无关联关系">
          <el-table-column label="从" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.fromName || row.fromCode }}</template>
          </el-table-column>
          <el-table-column label="到" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.toName || row.toCode }}</template>
          </el-table-column>
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ statusLabel(row.relationType) }}</template>
          </el-table-column>
          <el-table-column prop="label" label="说明" min-width="120" show-overflow-tooltip />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeRelation(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 血缘：自动解析入口 -->
      <template v-else-if="activeTab === 'lineage'">
        <div class="manalyze-section">血缘自动解析</div>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :loading="parseLoading" @click="parseLineage">
              自动梳理表级/字段级血缘
            </el-button>
            <el-button :loading="parseLoading" @click="parseFk">从外键补充字段血缘</el-button>
          </el-form-item>
        </el-form>
        <el-text type="info" size="small">
          自动解析将补齐数据源归属、分层推移（ODS→DWD→DWS→ADS），并按同名列/外键生成字段血缘，使数据流程可追溯。
        </el-text>
      </template>

      <!-- 影响分析明细 -->
      <template v-else>
        <div class="manalyze-section">下游影响清单</div>
        <el-table :data="impactDetails" stripe size="small" max-height="280" empty-text="无下游依赖">
          <el-table-column prop="entryName" label="实体" min-width="140" show-overflow-tooltip />
          <el-table-column prop="entryCode" label="编码" min-width="140" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ statusLabel(row.entryType) }}</template>
          </el-table-column>
          <el-table-column label="分层" width="90">
            <template #default="{ row }">{{ row.dataLayer ? statusLabel(row.dataLayer) : '—' }}</template>
          </el-table-column>
          <el-table-column prop="hop" label="跳数" width="70" />
        </el-table>

        <div class="manalyze-section">关联采集任务</div>
        <el-button size="small" style="margin-bottom:8px" @click="loadRelatedTasks">加载关联任务</el-button>
        <el-table :data="relatedTasks" stripe size="small" max-height="200" empty-text="暂无关联任务">
          <el-table-column prop="taskName" label="任务" min-width="140" />
          <el-table-column prop="taskCode" label="编码" width="140" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ statusLabel(String(row.status || '')) }}</template>
          </el-table-column>
          <el-table-column prop="targetTable" label="目标表" width="140" />
          <el-table-column label="最近运行" width="160">
            <template #default="{ row }">{{ formatTime(String(row.lastRunAt || '')) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </PageCard>
  </div>
</template>

<style scoped>
.manalyze-kpi {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 12px;
}
.manalyze-kpi__card {
  border: 1px solid #e8edf5; background: #fff; border-radius: 10px;
  padding: 12px 14px; box-shadow: 0 1px 4px rgba(15, 40, 80, .04);
}
.manalyze-kpi__card span { display: block; font-size: 12px; color: #606266; }
.manalyze-kpi__card b { font-size: 24px; font-weight: 700; color: #303133; }
.manalyze-kpi__card.tone-warn b { color: #ef6c00; }
.manalyze-kpi__card.tone-info b { color: #1677ff; }
.manalyze-kpi__card.tone-ok b { color: #2e7d32; }
.manalyze-chart {
  height: 400px;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fafbfd;
}
.manalyze-section {
  margin: 14px 0 8px; font-size: 13px; font-weight: 600;
  padding-left: 8px; border-left: 3px solid #1677ff;
}
.manalyze-impact-bar {
  display: flex; flex-wrap: wrap; gap: 10px; align-items: center;
  margin-bottom: 12px; font-size: 13px; color: #606266;
}
@media (max-width: 1100px) {
  .manalyze-kpi { grid-template-columns: repeat(2, 1fr); }
}
</style>
