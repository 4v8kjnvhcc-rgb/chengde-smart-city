<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
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
  type ColumnLineage,
  type Project,
} from '../useIngestionHub'

type GraphNode = {
  id: string
  label: string
  type: string
  isolated?: boolean
  matched?: boolean
  dimmed?: boolean
  sourceName?: string
  tableCode?: string
  usageDesc?: string
  categories?: string[]
}

type GraphEdge = {
  fromNode: string
  toNode: string
  fromLabel?: string
  toLabel?: string
  edgeType?: string
  fieldMapping?: string
  crossDb?: boolean
  fromSourceName?: string
  toSourceName?: string
}

const NODE_W = 132
const NODE_H = 58
const COL_GAP = 210
const ROW_GAP = 78
const PAD_X = 48
const PAD_Y = 36

const { loading, loadError, withLoad } = useIngestionLoading()
const activeTab = ref('table')
const projects = ref<Project[]>([])
const keyword = ref('')
const categoryTagId = ref<number | null>(null)
const categories = ref<Array<{ tagId: number; tagName: string }>>([])

const focusNode = ref<string | null>(null)
const nodes = ref<GraphNode[]>([])
const edges = ref<GraphEdge[]>([])
const breadcrumb = ref<Array<{ id: string; label: string }>>([])
const focusMeta = ref<Record<string, unknown> | null>(null)
const drawerVisible = ref(false)

const fieldRows = ref<ColumnLineage[]>([])
const fieldEdges = ref<Array<{ from: string; to: string; direction: string }>>([])
const allTableNodes = ref<GraphNode[]>([])
const noMoreTip = ref(false)

function typeLabel(type?: string) {
  if (type === 'SOURCE') return '数据源'
  if (type === 'CATALOG') return '目录'
  return '表'
}

function computeLayers(nodeList: GraphNode[], edgeList: GraphEdge[]) {
  const linked = nodeList.filter((n) => !n.isolated)
  const isolated = nodeList.filter((n) => n.isolated)
  const ids = linked.map((n) => n.id)
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
  const q = ids.filter((id) => inDeg[id] === 0).map((id) => ({ id, l: 0 }))
  if (!q.length && ids.length) q.push({ id: ids[0], l: 0 })
  const seen = new Set<string>()
  while (q.length) {
    const { id, l } = q.shift()!
    if (seen.has(id)) continue
    seen.add(id)
    layer[id] = Math.max(layer[id] ?? 0, l)
    for (const next of adj[id] || []) q.push({ id: next, l: l + 1 })
  }
  for (const n of linked) {
    if (layer[n.id] === undefined) layer[n.id] = 0
  }
  const maxLinkedLayer = Math.max(0, ...Object.values(layer), 0)
  isolated.forEach((n, i) => {
    layer[n.id] = maxLinkedLayer + 1 + Math.floor(i / 8)
  })
  return layer
}

const layout = computed(() => {
  const layerMap = computeLayers(nodes.value, edges.value)
  const byLayer: Record<number, string[]> = {}
  for (const n of nodes.value) {
    const l = layerMap[n.id] ?? 0
    if (!byLayer[l]) byLayer[l] = []
    byLayer[l].push(n.id)
  }
  const layers = Object.keys(byLayer).map(Number).sort((a, b) => a - b)
  const maxRows = Math.max(...layers.map((l) => byLayer[l].length), 1)
  const pos: Record<string, { x: number; y: number; cx: number; cy: number }> = {}
  for (const l of layers) {
    const ids = byLayer[l]
    const blockH = (ids.length - 1) * ROW_GAP
    const startY = PAD_Y + ((maxRows - 1) * ROW_GAP) / 2 - blockH / 2
    ids.forEach((id, i) => {
      const x = PAD_X + l * COL_GAP
      const y = startY + i * ROW_GAP
      pos[id] = { x, y, cx: x + NODE_W / 2, cy: y + NODE_H / 2 }
    })
  }
  const width = PAD_X * 2 + Math.max(layers.length - 1, 0) * COL_GAP + NODE_W
  const height = PAD_Y * 2 + (maxRows - 1) * ROW_GAP + NODE_H
  return { pos, width: Math.max(width, 480), height: Math.max(height, 220) }
})

const svgEdges = computed(() => edges.value.map((e) => {
  const from = layout.value.pos[e.fromNode]
  const to = layout.value.pos[e.toNode]
  if (!from || !to) return null
  return {
    key: `${e.fromNode}-${e.toNode}-${e.edgeType || ''}`,
    x1: from.x + NODE_W,
    y1: from.cy,
    x2: to.x,
    y2: to.cy,
    crossDb: !!e.crossDb,
    label: e.crossDb
      ? `${e.fromSourceName || ''} → ${e.toSourceName || ''}`
      : (e.fieldMapping || e.edgeType || ''),
  }
}).filter(Boolean) as Array<{ key: string; x1: number; y1: number; x2: number; y2: number; crossDb: boolean; label: string }>)

async function loadProjects() {
  projects.value = (await ingestionApi.projects()).data
  syncActiveProject(projects.value)
}

async function loadPanorama() {
  if (!activeProjectId.value) return
  noMoreTip.value = false
  await withLoad(async () => {
    const res = (await ingestionApi.lineage({
      projectId: activeProjectId.value!,
      keyword: keyword.value || undefined,
      categoryTagId: categoryTagId.value || undefined,
    })).data
    nodes.value = (res.nodes || []) as GraphNode[]
    edges.value = (res.edges || []) as GraphEdge[]
    categories.value = res.categories || []
    focusNode.value = null
    breadcrumb.value = []
    focusMeta.value = null
    drawerVisible.value = false
    allTableNodes.value = nodes.value.filter((n) => n.type === 'TABLE')
  })
}

async function loadDrill(nodeId: string, label: string, pushCrumb = true) {
  noMoreTip.value = false
  await withLoad(async () => {
    const res = await ingestionApi.lineageDrill(nodeId)
    nodes.value = (res.data.nodes || []) as GraphNode[]
    edges.value = (res.data.edges || []) as GraphEdge[]
    focusNode.value = nodeId
    focusMeta.value = res.data.focusMeta || null
    drawerVisible.value = true
    if (pushCrumb) {
      if (!breadcrumb.value.length || breadcrumb.value[breadcrumb.value.length - 1].id !== nodeId) {
        breadcrumb.value.push({ id: nodeId, label })
      }
    }
    if (!res.data.hasMore) {
      noMoreTip.value = true
      ElMessage.info('已无更多血缘关系')
    }
    if (activeTab.value === 'field' && nodeId.startsWith('tbl-')) {
      await loadFields(nodeId)
    }
  })
}

async function loadFields(tableNode: string) {
  const res = (await ingestionApi.fieldLineage(tableNode)).data
  fieldRows.value = res.fields || []
  fieldEdges.value = res.fieldEdges || []
  if (res.focusMeta) focusMeta.value = res.focusMeta
}

function onNodeClick(n: GraphNode) {
  if (n.type === 'CATALOG') return
  if (n.type === 'SOURCE') {
    ElMessage.info('可继续点击下游表节点下钻')
  }
  void loadDrill(n.id, n.label)
  if (activeTab.value === 'field' && n.type === 'TABLE') void loadFields(n.id)
}

function resetPanorama() {
  void loadPanorama()
}

function crumbJump(idx: number) {
  if (idx < 0) {
    resetPanorama()
    return
  }
  const item = breadcrumb.value[idx]
  breadcrumb.value = breadcrumb.value.slice(0, idx + 1)
  void loadDrill(item.id, item.label, false)
}

watch(activeProjectId, () => {
  categoryTagId.value = null
  void loadPanorama()
})

watch(activeTab, async (tab) => {
  if (tab !== 'field') return
  const node = focusNode.value && focusNode.value.startsWith('tbl-')
    ? focusNode.value
    : allTableNodes.value[0]?.id
  if (node) await loadFields(node)
})

onMounted(async () => {
  await loadProjects()
  await loadPanorama()
})
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据资产图谱分析">
      <p class="hint">
        按项目查看表与表血缘全景；有连线表示存在血缘，无连线的独立表表示与其他表无关系。支持跨库溯源与连续下钻。
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
        <el-form-item label="检索" class="portal-field-xl">
          <el-input v-model="keyword" clearable placeholder="表名/编码/数据源" @keyup.enter="loadPanorama" />
        </el-form-item>
        <el-form-item label="数据类目" class="portal-field-lg">
          <el-select v-model="categoryTagId" clearable filterable placeholder="全部类目">
            <el-option v-for="c in categories" :key="c.tagId" :label="c.tagName" :value="c.tagId" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="loadPanorama">查询</el-button>
          <el-button @click="resetPanorama">项目全景</el-button>
        </el-form-item>
      </el-form>

      <div v-if="breadcrumb.length" class="crumb-bar">
        <el-button link type="primary" @click="crumbJump(-1)">全景</el-button>
        <span v-for="(c, i) in breadcrumb" :key="c.id">
          <span class="sep">/</span>
          <el-button link type="primary" @click="crumbJump(i)">{{ c.label }}</el-button>
        </span>
        <el-tag v-if="noMoreTip" size="small" type="info" style="margin-left:8px">已无更多血缘</el-tag>
      </div>

      <el-row :gutter="12">
        <el-col :span="drawerVisible ? 16 : 24">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="表血缘" name="table">
              <div class="graph-wrap" :style="{ height: `${layout.height}px` }">
                <svg class="graph-svg" :viewBox="`0 0 ${layout.width} ${layout.height}`" preserveAspectRatio="xMinYMid meet">
                  <defs>
                    <marker id="lg-arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
                      <path d="M0,0 L7,3 L0,6 Z" fill="#409eff" />
                    </marker>
                    <marker id="lg-arrow-cross" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
                      <path d="M0,0 L7,3 L0,6 Z" fill="#e6a23c" />
                    </marker>
                  </defs>
                  <g v-for="e in svgEdges" :key="e.key">
                    <line
                      :x1="e.x1" :y1="e.y1" :x2="e.x2" :y2="e.y2"
                      :stroke="e.crossDb ? '#e6a23c' : '#409eff'"
                      stroke-width="2"
                      :marker-end="e.crossDb ? 'url(#lg-arrow-cross)' : 'url(#lg-arrow)'"
                    />
                    <title>{{ e.label }}</title>
                  </g>
                </svg>
                <div class="graph-nodes" :style="{ width: `${layout.width}px`, height: `${layout.height}px` }">
                  <div
                    v-for="n in nodes"
                    :key="n.id"
                    class="graph-node"
                    :class="[
                      `type-${(n.type || 'table').toLowerCase()}`,
                      { focus: focusNode === n.id, isolated: n.isolated, dimmed: n.dimmed },
                    ]"
                    :style="{
                      left: `${layout.pos[n.id]?.x}px`,
                      top: `${layout.pos[n.id]?.y}px`,
                      width: `${NODE_W}px`,
                      height: `${NODE_H}px`,
                    }"
                    :title="n.isolated ? '与其他表无血缘关系' : (n.sourceName || '')"
                    @click="onNodeClick(n)"
                  >
                    <div class="node-type">{{ typeLabel(n.type) }}{{ n.isolated ? ' · 独立' : '' }}</div>
                    <div class="node-label">{{ n.label }}</div>
                    <div v-if="n.sourceName" class="node-sub">{{ n.sourceName }}</div>
                  </div>
                </div>
              </div>
              <el-table :data="edges" stripe size="small" style="margin-top:12px">
                <el-table-column prop="fromLabel" label="上游" min-width="120" show-overflow-tooltip />
                <el-table-column prop="toLabel" label="下游" min-width="120" show-overflow-tooltip />
                <el-table-column label="关系" width="100">
                  <template #default="{ row }">{{ row.edgeType === 'CROSS_DB' ? '跨库' : (row.edgeType || '—') }}</template>
                </el-table-column>
                <el-table-column label="跨库" width="70">
                  <template #default="{ row }">{{ row.crossDb ? '是' : '否' }}</template>
                </el-table-column>
                <el-table-column prop="fieldMapping" label="字段映射" min-width="120" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="字段血缘" name="field">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item label="物理表" class="portal-field-xl">
                  <el-select
                    :model-value="focusNode && String(focusNode).startsWith('tbl-') ? focusNode : allTableNodes[0]?.id"
                    placeholder="选择表"
                    filterable
                    @change="(v: string) => { focusNode = v; loadFields(v); drawerVisible = true }"
                  >
                    <el-option v-for="n in allTableNodes" :key="n.id" :label="`${n.label}（${n.tableCode || n.id}）`" :value="n.id" />
                  </el-select>
                </el-form-item>
              </el-form>

              <div v-if="fieldEdges.length" class="field-flow">
                <div v-for="(e, i) in fieldEdges" :key="i" class="field-flow-row">
                  <span class="chip">{{ e.from }}</span>
                  <span class="arrow">→</span>
                  <span class="chip">{{ e.to }}</span>
                  <el-tag size="small" type="info">{{ e.direction === 'UP' ? '上游' : '下游' }}</el-tag>
                </div>
              </div>
              <el-empty v-else description="暂无字段血缘连线" :image-size="56" />

              <el-table :data="fieldRows" stripe style="margin-top:12px">
                <el-table-column prop="columnName" label="字段" width="120" show-overflow-tooltip />
                <el-table-column prop="columnCode" label="编码" width="120" show-overflow-tooltip />
                <el-table-column label="上游" min-width="160">
                  <template #default="{ row }">
                    {{ row.upstreamTable ? `${row.upstreamTable}.${row.upstreamColumn}` : '—' }}
                  </template>
                </el-table-column>
                <el-table-column label="下游" min-width="160">
                  <template #default="{ row }">
                    {{ row.downstreamTable ? `${row.downstreamTable}.${row.downstreamColumn}` : '—' }}
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-col>

        <el-col v-if="drawerVisible && focusMeta" :span="8">
          <div class="meta-panel">
            <h4>表使用说明</h4>
            <p class="meta-desc">{{ focusMeta.usageDesc || '暂无使用说明' }}</p>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="表名称">{{ focusMeta.tableName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="表编码">{{ focusMeta.tableCode || '—' }}</el-descriptions-item>
              <el-descriptions-item label="数据源">{{ focusMeta.sourceName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="Schema">{{ focusMeta.sourceSchema || '—' }}</el-descriptions-item>
              <el-descriptions-item label="数据类目">
                <template v-if="(focusMeta.categories as string[] || []).length">
                  <el-tag v-for="(c, i) in (focusMeta.categories as string[])" :key="i" size="small" style="margin:0 4px 4px 0">{{ c }}</el-tag>
                </template>
                <span v-else>—</span>
              </el-descriptions-item>
            </el-descriptions>
            <el-button style="margin-top:12px" @click="drawerVisible = false">收起</el-button>
          </div>
        </el-col>
      </el-row>
    </PageCard>
  </div>
</template>

<style scoped>
.hint { font-size: 13px; color: #606266; margin: 0 0 12px; line-height: 1.6; }
.crumb-bar { margin-bottom: 10px; font-size: 13px; }
.sep { margin: 0 4px; color: #c0c4cc; }
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
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.graph-node:hover, .graph-node.focus { border-color: #409eff; }
.graph-node.isolated { border-style: dashed; background: #f5f7fa; color: #909399; }
.graph-node.dimmed { opacity: 0.45; }
.graph-node.type-source { border-left: 3px solid #e6a23c; }
.graph-node.type-table { border-left: 3px solid #409eff; }
.graph-node.type-catalog { border-left: 3px solid #67c23a; }
.node-type { font-size: 10px; color: #909399; }
.node-label { font-size: 12px; font-weight: 600; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.node-sub { font-size: 10px; color: #a8abb2; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.meta-panel {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  background: #fff;
  min-height: 280px;
}
.meta-panel h4 { margin: 0 0 8px; font-size: 14px; }
.meta-desc { font-size: 13px; color: #606266; line-height: 1.6; margin: 0 0 12px; }
.field-flow { display: flex; flex-direction: column; gap: 8px; margin-bottom: 8px; }
.field-flow-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.chip {
  display: inline-block;
  padding: 4px 8px;
  background: #ecf5ff;
  border-radius: 4px;
  font-size: 12px;
  color: #409eff;
}
.arrow { color: #909399; }
</style>
