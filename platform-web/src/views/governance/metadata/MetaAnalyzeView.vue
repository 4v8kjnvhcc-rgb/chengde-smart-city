<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

interface Graph {
  nodes: { id: string; label: string; type: string }[]
  edges: { from: string; to: string; label: string; type: string }[]
  source?: string
}

interface Connector { id: number; connectorName: string }

const relationType = ref('LINEAGE')
const graph = ref<Graph>({ nodes: [], edges: [] })
const form = ref({ fromCode: '', toCode: '', relationType: 'ASSOC', label: '' })
const impactFrom = ref('')
const impactResult = ref<{ count?: number; impacted?: string[]; nodes?: Graph['nodes']; edges?: Graph['edges'] } | null>(null)
const connectors = ref<Connector[]>([])
const fkConnectorId = ref<number | undefined>()
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

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
    label: { show: true, formatter: e.label || e.type },
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

async function load() {
  graph.value = (await api.get('/governance/platform/metadata/analyze', {
    params: { relationType: relationType.value || undefined },
  })).data || { nodes: [], edges: [] }
  await nextTick()
  renderChart()
}

async function loadConnectors() {
  const ov = await api.get('/governance/platform/metadata/overview')
  connectors.value = ov.data.connectors || []
  if (connectors.value.length && !fkConnectorId.value) {
    fkConnectorId.value = connectors.value[0].id
  }
}

async function addRelation() {
  if (!form.value.fromCode || !form.value.toCode) return
  await api.post('/governance/platform/metadata/relations', form.value)
  ElMessage.success('关联已添加')
  form.value.fromCode = ''
  form.value.toCode = ''
  await load()
}

async function analyzeImpact() {
  if (!impactFrom.value) return
  impactResult.value = (await api.get('/governance/platform/metadata/analyze/impact', {
    params: { fromCode: impactFrom.value },
  })).data
  graph.value = {
    nodes: impactResult.value?.nodes as Graph['nodes'] || [],
    edges: impactResult.value?.edges as Graph['edges'] || [],
    source: 'impact',
  }
  await nextTick()
  renderChart()
  ElMessage.success(`影响节点 ${impactResult.value?.count ?? 0} 个`)
}

async function parseFk() {
  if (!fkConnectorId.value) return
  const n = (await api.post('/governance/platform/metadata/relations/parse-fk', null, {
    params: { connectorId: fkConnectorId.value },
  })).data
  ElMessage.success(`解析外键 ${n} 条`)
  await load()
}

function onResize() { chart?.resize() }

onMounted(async () => {
  await loadConnectors()
  await load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<template>
  <PageCard title="M096 关联 / 血缘 / 影响分析">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="分析类型" class="portal-field-md">
        <el-select v-model="relationType" @change="load">
          <el-option label="血缘" value="LINEAGE" />
          <el-option label="影响" value="IMPACT" />
          <el-option label="关联" value="ASSOC" />
          <el-option label="主外键" value="FK" />
        </el-select>
      </el-form-item>
      <el-form-item label="影响起点" class="portal-field-md"><el-input v-model="impactFrom" placeholder="entryCode" /></el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="load">刷新</el-button>
        <el-button @click="analyzeImpact">影响递归</el-button>
      </el-form-item>
    </el-form>
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="FK适配器" class="portal-field-lg">
        <el-select v-model="fkConnectorId" clearable>
          <el-option v-for="c in connectors" :key="c.id" :label="c.connectorName" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions"><el-button @click="parseFk">解析 FK</el-button></el-form-item>
    </el-form>
    <el-alert type="info" :closable="false" :title="`图来源=${graph.source || 'local'} · 节点 ${graph.nodes?.length || 0} · 边 ${graph.edges?.length || 0}`" style="margin-bottom:12px" />
    <div ref="chartRef" style="height:360px;margin-bottom:16px;border:1px solid var(--el-border-color-lighter)" />
    <el-row :gutter="12">
      <el-col :span="10">
        <h4>节点</h4>
        <el-table :data="graph.nodes" stripe size="small" max-height="240">
          <el-table-column prop="id" label="编码" width="140" />
          <el-table-column prop="label" label="名称" />
          <el-table-column prop="type" label="类型" width="90" />
        </el-table>
      </el-col>
      <el-col :span="14">
        <h4>边</h4>
        <el-table :data="graph.edges" stripe size="small" max-height="240">
          <el-table-column prop="from" label="从" width="140" />
          <el-table-column prop="to" label="到" width="140" />
          <el-table-column prop="type" label="类型" width="90" />
          <el-table-column prop="label" label="说明" />
        </el-table>
      </el-col>
    </el-row>
    <el-divider content-position="left">新增关联</el-divider>
    <el-form inline class="portal-inline-form">
      <el-form-item label="从" class="portal-field-md"><el-input v-model="form.fromCode" /></el-form-item>
      <el-form-item label="到" class="portal-field-md"><el-input v-model="form.toCode" /></el-form-item>
      <el-form-item label="类型" class="portal-field-sm">
        <el-select v-model="form.relationType">
          <el-option label="关联" value="ASSOC" />
          <el-option label="血缘" value="LINEAGE" />
          <el-option label="影响" value="IMPACT" />
          <el-option label="FK" value="FK" />
        </el-select>
      </el-form-item>
      <el-form-item label="说明" class="portal-field-md"><el-input v-model="form.label" /></el-form-item>
      <el-form-item class="portal-form-actions"><el-button type="primary" @click="addRelation">添加</el-button></el-form-item>
    </el-form>
  </PageCard>
</template>
