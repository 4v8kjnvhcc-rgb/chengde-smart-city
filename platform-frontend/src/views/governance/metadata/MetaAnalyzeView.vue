<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'

interface Graph {
  nodes: { id: string; label: string; type: string }[]
  edges: { from: string; to: string; label: string; type: string }[]
  source?: string
}

interface TableEntry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
}

interface DataSource {
  id: number
  sourceName: string
  sourceType?: string
  layerHint?: string
}

interface Connector {
  id: number
  connectorName: string
}

const RELATION_OPTIONS = [
  { label: '血缘', value: 'LINEAGE' },
  { label: '影响', value: 'IMPACT' },
  { label: '关联', value: 'ASSOC' },
  { label: '外键', value: 'FK' },
]

const tableEntries = ref<TableEntry[]>([])
const relationType = ref('LINEAGE')
const graph = ref<Graph>({ nodes: [], edges: [] })

const startEntryCode = ref('')
const advancedEntryCode = ref('')

const relationForm = ref({
  fromCode: '',
  toCode: '',
  relationType: 'ASSOC',
  label: '',
})

const impactResult = ref<{
  count?: number
  impacted?: string[]
  nodes?: Graph['nodes']
  edges?: Graph['edges']
} | null>(null)
const offlineAssess = ref<{ canOffline?: boolean; hasDownstream?: boolean } | null>(null)
const relatedTasks = ref<Array<Record<string, unknown>>>([])

const dataSources = ref<DataSource[]>([])
const connectors = ref<Connector[]>([])
const fkConnectorId = ref<number | undefined>()
const fkSectionOpen = ref<string[]>([])
const connectorsLoaded = ref(false)

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const entrySelectOptions = computed(() =>
  tableEntries.value.map(e => ({
    value: e.entryCode,
    label: `${e.entryName}（${e.entryCode}）`,
  })),
)

function relationLabel(type: string) {
  return statusLabel(type)
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const nodes = (graph.value.nodes || []).map(n => ({
    id: n.id,
    name: n.label || n.id,
    category: n.type,
    symbolSize: n.type === 'ROOT' ? 48 : 36,
  }))
  const categories = [...new Set(nodes.map(n => n.category))].map(c => ({ name: c }))
  const links = (graph.value.edges || []).map(e => ({
    source: e.from,
    target: e.to,
    label: { show: true, formatter: e.label || relationLabel(e.type) },
  }))
  chart.setOption({
    tooltip: {},
    legend: [{ data: categories.map(c => c.name) }],
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      label: { show: true },
      force: { repulsion: 120 },
      categories,
      data: nodes,
      links,
    }],
  })
}

async function loadTableEntries() {
  const res = await api.get('/governance/platform/metadata/catalog/search', {
    params: { type: 'asset' },
  })
  tableEntries.value = ((res.data || []) as TableEntry[]).filter(e => e.entryType === 'TABLE')
  if (!startEntryCode.value && tableEntries.value.length) {
    startEntryCode.value = tableEntries.value[0].entryCode
  }
}

async function loadGraph() {
  graph.value = (await api.get('/governance/platform/metadata/analyze', {
    params: { relationType: relationType.value || undefined },
  })).data || { nodes: [], edges: [] }
  await nextTick()
  renderChart()
}

async function loadConnectors() {
  if (connectorsLoaded.value) return
  const ov = await api.get('/governance/platform/metadata/overview')
  connectors.value = ov.data.connectors || []
  if (connectors.value.length && !fkConnectorId.value) {
    fkConnectorId.value = connectors.value[0].id
  }
  connectorsLoaded.value = true
}

async function loadDataSources() {
  if (dataSources.value.length) return
  dataSources.value = (await api.get('/governance/platform/metadata/collect/data-sources')).data || []
}

async function onFkSectionChange(names: string[]) {
  fkSectionOpen.value = names
  if (names.includes('fk')) {
    await Promise.all([loadDataSources(), loadConnectors()])
  }
}

async function addRelation() {
  if (!relationForm.value.fromCode || !relationForm.value.toCode) {
    ElMessage.warning('请选择起点与终点')
    return
  }
  await api.post('/governance/platform/metadata/relations', relationForm.value)
  ElMessage.success('关联已添加')
  relationForm.value.fromCode = ''
  relationForm.value.toCode = ''
  relationForm.value.label = ''
  await loadGraph()
}

async function analyzeImpact() {
  const code = startEntryCode.value || advancedEntryCode.value
  if (!code) {
    ElMessage.warning('请选择分析起点表')
    return
  }
  const data = (await api.get('/governance/platform/metadata/analyze/impact', {
    params: { fromCode: code },
  })).data
  impactResult.value = data
  graph.value = {
    nodes: data?.nodes?.length
      ? data.nodes
      : (data?.impacted || []).map((id: string) => ({ id, label: id, type: 'IMPACT' })),
    edges: data?.edges || [],
    source: 'impact',
  }
  await nextTick()
  renderChart()
  ElMessage.success(`影响节点 ${impactResult.value?.count ?? 0} 个`)
}

async function assessOffline() {
  const code = startEntryCode.value || advancedEntryCode.value
  if (!code) return
  offlineAssess.value = (await api.get('/governance/platform/metadata/analyze/offline-assess', {
    params: { entryCode: code },
  })).data
}

async function loadRelatedTasks() {
  const code = startEntryCode.value || advancedEntryCode.value
  if (!code) return
  relatedTasks.value = (await api.get('/governance/platform/metadata/analyze/tasks', {
    params: { entryCode: code },
  })).data || []
}

async function parseFk() {
  if (!fkConnectorId.value) {
    ElMessage.warning('FK 解析需选择 OM 适配器（非数据源 ID）')
    return
  }
  const n = (await api.post('/governance/platform/metadata/relations/parse-fk', null, {
    params: { connectorId: fkConnectorId.value },
  })).data
  ElMessage.success(`解析外键 ${n} 条`)
  await loadGraph()
}

function onResize() {
  chart?.resize()
}

onMounted(async () => {
  await Promise.all([loadTableEntries(), loadGraph()])
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<template>
  <PageCard title="元数据分析">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="分析类型" class="portal-field-md">
        <el-select v-model="relationType" @change="loadGraph">
          <el-option v-for="o in RELATION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="起点表" class="portal-field-xl">
        <el-select v-model="startEntryCode" filterable clearable placeholder="选择 TABLE 条目">
          <el-option v-for="o in entrySelectOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="loadGraph">刷新图谱</el-button>
        <el-button @click="analyzeImpact">影响分析</el-button>
        <el-button @click="assessOffline">下线评估</el-button>
        <el-button @click="loadRelatedTasks">关联任务</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      type="info"
      :closable="false"
      :title="`节点 ${graph.nodes?.length || 0} · 边 ${graph.edges?.length || 0}${graph.source ? ' · 来源：' + graph.source : ''}`"
      style="margin-bottom: 12px"
    />

    <div v-if="offlineAssess" style="margin-bottom: 12px">
      <el-tag :type="offlineAssess.canOffline ? 'success' : 'warning'" size="small">
        {{ offlineAssess.canOffline ? '可下线' : '存在下游依赖' }}
      </el-tag>
      <span v-if="offlineAssess.hasDownstream" style="margin-left: 8px; font-size: 13px; color: var(--el-text-color-secondary)">
        检测到下游血缘，下线前请评估影响
      </span>
    </div>

    <div ref="chartRef" class="meta-analyze-chart" />

    <el-row :gutter="12" style="margin-bottom: 16px">
      <el-col :span="10">
        <h4>节点</h4>
        <el-table :data="graph.nodes" stripe size="small" max-height="240">
          <el-table-column prop="id" label="编码" width="140" show-overflow-tooltip />
          <el-table-column prop="label" label="名称" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ $statusLabel(row.type) }}</template>
          </el-table-column>
        </el-table>
      </el-col>
      <el-col :span="14">
        <h4>边</h4>
        <el-table :data="graph.edges" stripe size="small" max-height="240">
          <el-table-column prop="from" label="从" width="140" show-overflow-tooltip />
          <el-table-column prop="to" label="到" width="140" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ $statusLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="label" label="说明" show-overflow-tooltip />
        </el-table>
      </el-col>
    </el-row>

    <el-collapse v-if="relatedTasks.length" style="margin-bottom: 16px">
      <el-collapse-item title="关联采集任务" name="tasks">
        <el-table :data="relatedTasks" stripe size="small">
          <el-table-column prop="taskName" label="任务" />
          <el-table-column prop="taskCode" label="编码" width="140" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="targetTable" label="目标表" width="140" />
          <el-table-column prop="lastRunAt" label="最近运行" width="160" />
        </el-table>
      </el-collapse-item>
    </el-collapse>

    <el-divider content-position="left">新增关联</el-divider>
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="从" class="portal-field-xl">
        <el-select v-model="relationForm.fromCode" filterable clearable placeholder="起点条目">
          <el-option v-for="o in entrySelectOptions" :key="'f' + o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="到" class="portal-field-xl">
        <el-select v-model="relationForm.toCode" filterable clearable placeholder="终点条目">
          <el-option v-for="o in entrySelectOptions" :key="'t' + o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="类型" class="portal-field-sm">
        <el-select v-model="relationForm.relationType">
          <el-option v-for="o in RELATION_OPTIONS" :key="'r' + o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="说明" class="portal-field-md">
        <el-input v-model="relationForm.label" placeholder="可选" />
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="addRelation">添加</el-button>
      </el-form-item>
    </el-form>

    <el-collapse @change="onFkSectionChange">
      <el-collapse-item title="高级：外键解析 / 手工编码" name="fk">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="手工编码" class="portal-field-xl">
            <el-input v-model="advancedEntryCode" placeholder="可选，覆盖起点表编码" clearable />
          </el-form-item>
          <el-form-item label="登记数据源" class="portal-field-xl">
            <el-select placeholder="平台/外部数据源（参考）" disabled style="width: 100%">
              <el-option
                v-for="ds in dataSources"
                :key="ds.id"
                :label="`${ds.sourceName}（${$statusLabel(ds.layerHint || ds.sourceType)}）`"
                :value="ds.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="OM 适配器" class="portal-field-lg">
            <el-select v-model="fkConnectorId" clearable placeholder="FK 解析必填">
              <el-option v-for="c in connectors" :key="c.id" :label="c.connectorName" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="parseFk">解析外键</el-button>
          </el-form-item>
        </el-form>
        <el-text type="info" size="small">外键解析接口需 OM 适配器 ID，与登记数据源 ID 不同。</el-text>
      </el-collapse-item>
    </el-collapse>
  </PageCard>
</template>

<style scoped>
.meta-analyze-chart {
  height: 360px;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-lighter);
}
</style>
