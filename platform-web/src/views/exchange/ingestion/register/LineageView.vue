<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type ColumnLineage, type LineageEdge } from '../useIngestionHub'

interface GraphNode { id: string; label: string; type: string }

const NODE_W = 116
const NODE_H = 52
const COL_GAP = 200
const ROW_GAP = 72
const PAD_X = 48
const PAD_Y = 36

const { loading, loadError, withLoad } = useIngestionLoading()
const activeTab = ref('table')
const focusNode = ref<string | null>(null)
const nodes = ref<GraphNode[]>([])
const edges = ref<LineageEdge[]>([])
const fieldRows = ref<ColumnLineage[]>([])
const breadcrumb = ref<string[]>([])

function computeLayers(nodeList: GraphNode[], edgeList: LineageEdge[]) {
  const ids = nodeList.map(n => n.id)
  const inDeg: Record<string, number> = {}
  const adj: Record<string, string[]> = {}
  for (const id of ids) {
    inDeg[id] = 0
    adj[id] = []
  }
  for (const e of edgeList) {
    if (adj[e.fromNode]) adj[e.fromNode].push(e.toNode)
    if (inDeg[e.toNode] !== undefined) inDeg[e.toNode]++
  }
  const layer: Record<string, number> = {}
  const q = ids.filter(id => inDeg[id] === 0).map(id => ({ id, l: 0 }))
  if (!q.length && ids.length) q.push({ id: ids[0], l: 0 })
  const seen = new Set<string>()
  while (q.length) {
    const { id, l } = q.shift()!
    if (seen.has(id)) continue
    seen.add(id)
    layer[id] = Math.max(layer[id] ?? 0, l)
    for (const next of adj[id] || []) {
      q.push({ id: next, l: l + 1 })
    }
  }
  for (const n of nodeList) {
    if (layer[n.id] === undefined) layer[n.id] = 0
  }
  return layer
}

const layout = computed(() => {
  const layerMap = computeLayers(nodes.value, edges.value)
  const byLayer: Record<number, string[]> = {}
  for (const n of nodes.value) {
    const l = layerMap[n.id]
    if (!byLayer[l]) byLayer[l] = []
    byLayer[l].push(n.id)
  }
  const layers = Object.keys(byLayer).map(Number).sort((a, b) => a - b)
  const maxRows = Math.max(...layers.map(l => byLayer[l].length), 1)
  const pos: Record<string, { x: number; y: number; cx: number; cy: number }> = {}
  for (const l of layers) {
    const ids = byLayer[l]
    const blockH = (ids.length - 1) * ROW_GAP
    const startY = PAD_Y + (maxRows - 1) * ROW_GAP / 2 - blockH / 2
    ids.forEach((id, i) => {
      const x = PAD_X + l * COL_GAP
      const y = startY + i * ROW_GAP
      pos[id] = { x, y, cx: x + NODE_W / 2, cy: y + NODE_H / 2 }
    })
  }
  const width = PAD_X * 2 + (layers.length - 1) * COL_GAP + NODE_W
  const height = PAD_Y * 2 + (maxRows - 1) * ROW_GAP + NODE_H
  return { pos, width: Math.max(width, 400), height: Math.max(height, 180) }
})

const svgEdges = computed(() => edges.value.map(e => {
  const from = layout.value.pos[e.fromNode]
  const to = layout.value.pos[e.toNode]
  if (!from || !to) return null
  return {
    key: `${e.fromNode}-${e.toNode}`,
    x1: from.x + NODE_W,
    y1: from.cy,
    x2: to.x,
    y2: to.cy,
    label: e.fieldMapping || e.edgeType,
  }
}).filter(Boolean) as { key: string; x1: number; y1: number; x2: number; y2: number; label: string }[])

const allTableNodes = ref<GraphNode[]>([])

async function loadGraph(nodeId?: string) {
  await withLoad(async () => {
    if (nodeId) {
      const res = (await ingestionApi.lineageDrill(nodeId)).data
      nodes.value = res.nodes || []
      edges.value = res.edges || []
      focusNode.value = nodeId
    } else {
      const res = (await ingestionApi.lineage()).data
      nodes.value = res.nodes || []
      edges.value = res.edges || []
      focusNode.value = null
      breadcrumb.value = []
      allTableNodes.value = (res.nodes || []).filter((n: GraphNode) => n.type === 'TABLE')
    }
  })
}

async function drillNode(nodeId: string, label: string) {
  breadcrumb.value.push(label)
  await loadGraph(nodeId)
}

async function drillBack() {
  if (breadcrumb.value.length <= 1) {
    breadcrumb.value = []
    await loadGraph()
    return
  }
  breadcrumb.value.pop()
  const up = edges.value.find(e => e.toNode === focusNode.value)
  if (up) await loadGraph(up.fromNode)
  else await loadGraph()
}

async function loadFields(tableNode: string) {
  fieldRows.value = (await ingestionApi.fieldLineage(tableNode)).data
}

function onNodeClick(n: GraphNode) {
  if (n.type === 'CATALOG') return
  drillNode(n.id, n.label)
  if (activeTab.value === 'field' && n.type === 'TABLE') loadFields(n.id)
}

onMounted(async () => {
  await loadGraph()
  const firstTable = allTableNodes.value[0] || nodes.value.find(n => n.type === 'TABLE')
  if (firstTable) await loadFields(firstTable.id)
})
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据资产图谱分析">
      <p class="hint">表级血缘按层级自动排布，连线对齐节点中心；点击表可下钻一级上下游。</p>
      <div style="margin-bottom:12px">
        <el-form inline class="portal-inline-form">
          <el-form-item class="portal-form-actions">
            <el-button v-if="focusNode" size="small" @click="drillBack">返回上级</el-button>
            <el-button size="small" @click="loadGraph()">项目全景</el-button>
          </el-form-item>
          <span v-if="breadcrumb.length" class="crumb">路径：{{ breadcrumb.join(' → ') }}</span>
        </el-form>
      </div>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="表血缘" name="table">
          <div class="graph-wrap" :style="{ height: `${layout.height}px` }">
            <svg
              class="graph-svg"
              :viewBox="`0 0 ${layout.width} ${layout.height}`"
              preserveAspectRatio="xMinYMid meet"
            >
              <defs>
                <marker id="lg-arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
                  <path d="M0,0 L7,3 L0,6 Z" fill="#409eff" />
                </marker>
              </defs>
              <line
                v-for="e in svgEdges"
                :key="e.key"
                :x1="e.x1"
                :y1="e.y1"
                :x2="e.x2"
                :y2="e.y2"
                stroke="#409eff"
                stroke-width="2"
                marker-end="url(#lg-arrow)"
              />
            </svg>
            <div class="graph-nodes" :style="{ width: `${layout.width}px`, height: `${layout.height}px` }">
              <div
                v-for="n in nodes"
                :key="n.id"
                class="graph-node"
                :class="[`type-${n.type.toLowerCase()}`, { focus: focusNode === n.id }]"
                :style="{
                  left: `${layout.pos[n.id]?.x}px`,
                  top: `${layout.pos[n.id]?.y}px`,
                  width: `${NODE_W}px`,
                  height: `${NODE_H}px`,
                }"
                @click="onNodeClick(n)"
              >
                <div class="node-type">{{ n.type }}</div>
                <div class="node-label">{{ n.label }}</div>
              </div>
            </div>
          </div>
          <el-table :data="edges" stripe size="small" style="margin-top:12px">
            <el-table-column prop="fromLabel" label="上游" />
            <el-table-column prop="toLabel" label="下游" />
            <el-table-column prop="edgeType" label="关系" width="100" />
            <el-table-column prop="fieldMapping" label="字段映射" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="字段血缘" name="field">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="物理表" class="portal-field-xl">
              <el-select
                :model-value="focusNode || allTableNodes[0]?.id"
                placeholder="选择表"
                @change="(v: string) => { focusNode = v; loadFields(v) }"
              >
                <el-option
                  v-for="n in (allTableNodes.length ? allTableNodes : nodes.filter(x => x.type === 'TABLE'))"
                  :key="n.id"
                  :label="n.label"
                  :value="n.id"
                />
              </el-select>
            </el-form-item>
          </el-form>
          <el-table :data="fieldRows" stripe>
            <el-table-column prop="columnName" label="字段" width="120" />
            <el-table-column prop="columnCode" label="编码" width="120" />
            <el-table-column label="上游">
              <template #default="{ row }">{{ row.upstreamTable ? `${row.upstreamTable}.${row.upstreamColumn}` : '-' }}</template>
            </el-table-column>
            <el-table-column label="下游">
              <template #default="{ row }">{{ row.downstreamTable ? `${row.downstreamTable}.${row.downstreamColumn}` : '-' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </PageCard>
  </div>
</template>

<style scoped>
.hint { font-size: 13px; color: #606266; margin: 0 0 12px; }
.crumb { margin-left: 12px; font-size: 13px; color: #909399; }
.graph-wrap {
  position: relative;
  overflow: auto;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.graph-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
.graph-nodes { position: relative; min-width: 100%; }
.graph-node {
  position: absolute;
  box-sizing: border-box;
  padding: 6px 8px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  transition: border-color 0.2s;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.graph-node:hover, .graph-node.focus { border-color: #409eff; }
.graph-node.type-source { border-left: 3px solid #e6a23c; }
.graph-node.type-table { border-left: 3px solid #409eff; }
.graph-node.type-catalog { border-left: 3px solid #67c23a; }
.node-type { font-size: 10px; color: #909399; }
.node-label { font-size: 12px; font-weight: 600; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
