<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
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
  type LineageEdge,
  type LineageGraphNode,
  type Project,
} from '../useIngestionHub'

type GraphNode = LineageGraphNode
type GraphEdge = LineageEdge
type NeighborNode = LineageGraphNode

const NODE_W = 148
const NODE_H = 62
const COL_GAP = 220
const ROW_GAP = 84
const PAD_X = 40
const PAD_Y = 40

const { loading, loadError, withLoad } = useIngestionLoading()
const projects = ref<Project[]>([])
const keyword = ref('')
const categoryTagId = ref<number | null>(null)
const categories = ref<Array<{ tagId: number; tagName: string }>>([])

/** panorama | drill */
const viewMode = ref<'panorama' | 'drill'>('panorama')
/** table | field — only meaningful in drill mode */
const drillSubTab = ref<'table' | 'field'>('table')

const focusNode = ref<string | null>(null)
const nodes = ref<GraphNode[]>([])
const edges = ref<GraphEdge[]>([])
const breadcrumb = ref<Array<{ id: string; label: string }>>([])
const focusMeta = ref<Record<string, unknown> | null>(null)
const focusCard = ref<GraphNode | null>(null)
const upstreamNodes = ref<NeighborNode[]>([])
const downstreamNodes = ref<NeighborNode[]>([])
const selectedEdge = ref<GraphEdge | null>(null)
const noMoreTip = ref(false)

const tableCount = ref(0)
const linkedTableCount = ref(0)
const isolatedCount = ref(0)
const crossDbEdgeCount = ref(0)

const fieldRows = ref<ColumnLineage[]>([])
const fieldEdges = ref<Array<{ from: string; to: string; direction: string }>>([])
const isolatedPanelRef = ref<HTMLElement | null>(null)

function typeLabel(type?: string) {
  if (type === 'SOURCE') return '数据源'
  if (type === 'CATALOG') return '目录'
  return '表'
}

function edgeTypeLabel(edgeType?: string, crossDb?: boolean) {
  if (crossDb || edgeType === 'CROSS_DB') return '跨库'
  if (!edgeType) return '血缘'
  if (edgeType === 'EXTRACT') return '抽取'
  if (edgeType === 'FLOW') return '流转'
  return edgeType
}

const linkedNodes = computed(() => {
  const endpointIds = new Set<string>()
  for (const e of edges.value) {
    endpointIds.add(e.fromNode)
    endpointIds.add(e.toNode)
  }
  return nodes.value.filter((n) => endpointIds.has(n.id) && !n.isolated)
})

const isolatedTables = computed(() =>
  nodes.value.filter((n) => n.type === 'TABLE' && n.isolated),
)

const hintText = computed(() => {
  if (viewMode.value === 'drill') {
    return drillSubTab.value === 'field'
      ? '字段血缘仅基于当前表；上游字段 → 本表字段 → 下游字段。'
      : '仅展示当前表的一级上游与下游；继续点击邻居可逐级下钻。'
  }
  return '连线表示表间血缘；右侧「独立表」与其他表无关系。点击有血缘的表可下钻。'
})

function computeLayers(nodeList: GraphNode[], edgeList: GraphEdge[]) {
  const ids = nodeList.map((n) => n.id)
  const idSet = new Set(ids)
  const inDeg: Record<string, number> = {}
  const adj: Record<string, string[]> = {}
  for (const id of ids) {
    inDeg[id] = 0
    adj[id] = []
  }
  for (const e of edgeList) {
    if (!idSet.has(e.fromNode) || !idSet.has(e.toNode)) continue
    adj[e.fromNode].push(e.toNode)
    inDeg[e.toNode]++
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
  for (const n of nodeList) {
    if (layer[n.id] === undefined) layer[n.id] = 0
  }
  return layer
}

const layout = computed(() => {
  const list = linkedNodes.value
  if (!list.length) {
    return { pos: {} as Record<string, { x: number; y: number; cx: number; cy: number }>, width: 480, height: 220 }
  }
  const layerMap = computeLayers(list, edges.value)
  const byLayer: Record<number, string[]> = {}
  for (const n of list) {
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
  const x1 = from.x + NODE_W
  const y1 = from.cy
  const x2 = to.x
  const y2 = to.cy
  const mx = (x1 + x2) / 2
  const path = `M ${x1} ${y1} C ${mx} ${y1}, ${mx} ${y2}, ${x2} ${y2}`
  const crossDb = !!e.crossDb || e.edgeType === 'CROSS_DB'
  return {
    key: `${e.fromNode}-${e.toNode}-${e.edgeType || ''}-${e.id || ''}`,
    path,
    midX: mx,
    midY: (y1 + y2) / 2,
    crossDb,
    edge: e,
    label: crossDb
      ? `跨库：${e.fromSourceName || ''} → ${e.toSourceName || ''}`
      : (e.fieldMapping || edgeTypeLabel(e.edgeType, false)),
  }
}).filter(Boolean) as Array<{
  key: string
  path: string
  midX: number
  midY: number
  crossDb: boolean
  edge: GraphEdge
  label: string
}>)

const fieldUpstream = computed(() => {
  const set = new Set<string>()
  for (const r of fieldRows.value) {
    if (r.upstreamTable && r.upstreamColumn) set.add(`${r.upstreamTable}.${r.upstreamColumn}`)
  }
  for (const e of fieldEdges.value) {
    if (e.direction === 'UP') set.add(e.from)
  }
  return [...set]
})

const fieldCurrent = computed(() => {
  const set = new Set<string>()
  for (const r of fieldRows.value) {
    set.add(r.columnName || r.columnCode)
  }
  if (!set.size) {
    for (const e of fieldEdges.value) {
      if (e.direction === 'UP') set.add(e.to.split('.').pop() || e.to)
      if (e.direction === 'DOWN') set.add(e.from.split('.').pop() || e.from)
    }
  }
  return [...set]
})

const fieldDownstream = computed(() => {
  const set = new Set<string>()
  for (const r of fieldRows.value) {
    if (r.downstreamTable && r.downstreamColumn) set.add(`${r.downstreamTable}.${r.downstreamColumn}`)
  }
  for (const e of fieldEdges.value) {
    if (e.direction === 'DOWN') set.add(e.to)
  }
  return [...set]
})

async function loadProjects() {
  projects.value = (await ingestionApi.projects()).data
  syncActiveProject(projects.value)
}

async function loadPanorama() {
  if (!activeProjectId.value) return
  noMoreTip.value = false
  selectedEdge.value = null
  await withLoad(async () => {
    const res = (await ingestionApi.lineage({
      projectId: activeProjectId.value!,
      keyword: keyword.value || undefined,
      categoryTagId: categoryTagId.value || undefined,
    })).data
    nodes.value = res.nodes || []
    edges.value = res.edges || []
    categories.value = res.categories || []
    tableCount.value = res.tableCount ?? 0
    linkedTableCount.value = res.linkedTableCount ?? 0
    isolatedCount.value = res.isolatedCount ?? 0
    crossDbEdgeCount.value = res.crossDbEdgeCount ?? 0
    viewMode.value = 'panorama'
    drillSubTab.value = 'table'
    focusNode.value = null
    focusCard.value = null
    breadcrumb.value = []
    focusMeta.value = null
    upstreamNodes.value = []
    downstreamNodes.value = []
    fieldRows.value = []
    fieldEdges.value = []

    await nextTick()
    scrollToMatchedIsolated()
  })
}

function scrollToMatchedIsolated() {
  const hit = isolatedTables.value.find((n) => n.matched && !n.dimmed)
  if (!hit || !isolatedPanelRef.value) return
  const el = isolatedPanelRef.value.querySelector(`[data-node-id="${hit.id}"]`) as HTMLElement | null
  el?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
}

async function loadDrill(nodeId: string, label: string, pushCrumb = true) {
  noMoreTip.value = false
  selectedEdge.value = null
  await withLoad(async () => {
    const res = await ingestionApi.lineageDrill(nodeId)
    const data = res.data
    focusNode.value = nodeId
    focusMeta.value = data.focusMeta || null
    focusCard.value = data.focus || data.nodes?.find((n) => n.id === nodeId) || {
      id: nodeId,
      label,
      type: 'TABLE',
    }
    upstreamNodes.value = data.upstreamNodes || []
    downstreamNodes.value = data.downstreamNodes || []
    edges.value = data.edges || []
    nodes.value = data.nodes || []
    viewMode.value = 'drill'

    if (pushCrumb) {
      if (!breadcrumb.value.length || breadcrumb.value[breadcrumb.value.length - 1].id !== nodeId) {
        breadcrumb.value.push({ id: nodeId, label })
      }
    }
    if (!data.hasMore) {
      noMoreTip.value = true
    }
    if (drillSubTab.value === 'field' && nodeId.startsWith('tbl-')) {
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

async function openIsolatedMeta(n: GraphNode) {
  if (!n.id.startsWith('tbl-')) return
  await withLoad(async () => {
    const res = (await ingestionApi.lineageTableMeta(n.id)).data
    focusMeta.value = res
    focusNode.value = n.id
    focusCard.value = n
    ElMessage.info('该表暂无表间血缘')
  })
}

function onLinkedNodeClick(n: GraphNode) {
  if (n.type === 'CATALOG') return
  if (n.type === 'SOURCE') {
    ElMessage.info('可继续点击下游表节点下钻')
  }
  void loadDrill(n.id, n.label)
}

function onNeighborClick(n: NeighborNode) {
  if (n.type === 'CATALOG') return
  void loadDrill(n.id, n.label || String(n.id))
}

function onIsolatedClick(n: GraphNode) {
  void openIsolatedMeta(n)
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

function onEdgeClick(edge: GraphEdge) {
  selectedEdge.value = edge
}

function closeMeta() {
  if (viewMode.value === 'panorama') {
    focusMeta.value = null
    focusNode.value = null
    focusCard.value = null
  }
}

watch(activeProjectId, () => {
  categoryTagId.value = null
  void loadPanorama()
})

watch(drillSubTab, async (tab) => {
  if (tab !== 'field') return
  if (viewMode.value !== 'drill') return
  const node = focusNode.value && focusNode.value.startsWith('tbl-') ? focusNode.value : null
  if (node) await loadFields(node)
})

onMounted(async () => {
  await loadProjects()
  await loadPanorama()
})
</script>

<template>
  <div v-loading="loading" class="lineage-page">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据资产图谱分析">
      <p class="hint">{{ hintText }}</p>

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
          <el-button v-if="viewMode === 'drill'" type="primary" plain @click="resetPanorama">返回全景</el-button>
        </el-form-item>
      </el-form>

      <div class="stats-bar">
        <div class="stat"><span class="stat-n">{{ tableCount }}</span><span class="stat-l">表总数</span></div>
        <div class="stat"><span class="stat-n">{{ linkedTableCount }}</span><span class="stat-l">有血缘</span></div>
        <div class="stat"><span class="stat-n">{{ isolatedCount }}</span><span class="stat-l">独立表</span></div>
        <div class="stat"><span class="stat-n">{{ crossDbEdgeCount }}</span><span class="stat-l">跨库边</span></div>
        <div class="legend">
          <span class="lg-item"><i class="lg-line same" />同库血缘</span>
          <span class="lg-item"><i class="lg-line cross" />跨库血缘</span>
          <span class="lg-item"><i class="lg-box" />独立表</span>
        </div>
      </div>

      <div v-if="viewMode === 'drill' && breadcrumb.length" class="crumb-bar">
        <el-button link type="primary" @click="crumbJump(-1)">全景</el-button>
        <span v-for="(c, i) in breadcrumb" :key="`${c.id}-${i}`">
          <span class="sep">/</span>
          <el-button link type="primary" @click="crumbJump(i)">{{ c.label }}</el-button>
        </span>
        <el-tag v-if="noMoreTip" size="small" type="info" style="margin-left:8px">已无更多血缘</el-tag>
      </div>

      <!-- 全景模式 -->
      <template v-if="viewMode === 'panorama'">
        <el-row :gutter="12">
          <el-col :span="focusMeta ? 16 : 24">
            <div class="panorama-layout">
              <div class="linked-pane">
                <div class="pane-title">有血缘子图</div>
                <div v-if="!tableCount" class="empty-block">
                  <el-empty description="当前项目暂无登记表，请先完成数据资产登记" :image-size="64" />
                </div>
                <div v-else-if="!linkedNodes.length" class="empty-block">
                  <el-empty :image-size="64">
                    <template #description>
                      <p>本项目暂无表间血缘</p>
                      <p class="empty-sub">右侧「独立表」列出与其他表无关系的表，可点击查看使用说明</p>
                    </template>
                  </el-empty>
                </div>
                <div v-else class="graph-wrap" :style="{ height: `${layout.height}px` }">
                  <svg class="graph-svg" :viewBox="`0 0 ${layout.width} ${layout.height}`" preserveAspectRatio="xMinYMid meet">
                    <defs>
                      <marker id="lg-arrow" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
                        <path d="M0,0 L7,3 L0,6 Z" fill="#409eff" />
                      </marker>
                      <marker id="lg-arrow-cross" markerWidth="8" markerHeight="8" refX="7" refY="3" orient="auto">
                        <path d="M0,0 L7,3 L0,6 Z" fill="#e6a23c" />
                      </marker>
                    </defs>
                    <g
                      v-for="e in svgEdges"
                      :key="e.key"
                      class="edge-hit"
                      @click.stop="onEdgeClick(e.edge)"
                    >
                      <path
                        :d="e.path"
                        fill="none"
                        :stroke="e.crossDb ? '#e6a23c' : '#409eff'"
                        :stroke-width="selectedEdge && selectedEdge.fromNode === e.edge.fromNode && selectedEdge.toNode === e.edge.toNode ? 3 : 2"
                        :stroke-dasharray="e.crossDb ? '6 4' : undefined"
                        :marker-end="e.crossDb ? 'url(#lg-arrow-cross)' : 'url(#lg-arrow)'"
                      />
                      <title>{{ e.label }}</title>
                      <text
                        v-if="e.crossDb"
                        :x="e.midX"
                        :y="e.midY - 6"
                        text-anchor="middle"
                        class="edge-label"
                      >跨库</text>
                    </g>
                  </svg>
                  <div class="graph-nodes" :style="{ width: `${layout.width}px`, height: `${layout.height}px` }">
                    <div
                      v-for="n in linkedNodes"
                      :key="n.id"
                      class="graph-node"
                      :class="[
                        `type-${(n.type || 'table').toLowerCase()}`,
                        { matched: n.matched, dimmed: n.dimmed },
                      ]"
                      :style="{
                        left: `${layout.pos[n.id]?.x}px`,
                        top: `${layout.pos[n.id]?.y}px`,
                        width: `${NODE_W}px`,
                        height: `${NODE_H}px`,
                      }"
                      :title="n.sourceName || n.label"
                      @click="onLinkedNodeClick(n)"
                    >
                      <div class="node-type">{{ typeLabel(n.type) }}</div>
                      <div class="node-label">{{ n.label }}</div>
                      <div v-if="n.sourceName" class="node-sub">{{ n.sourceName }}</div>
                    </div>
                  </div>
                </div>
              </div>

              <div ref="isolatedPanelRef" class="isolated-pane">
                <div class="pane-title">独立表（{{ isolatedTables.length }}）</div>
                <div v-if="!isolatedTables.length" class="isolated-empty">暂无独立表</div>
                <div v-else class="isolated-grid">
                  <div
                    v-for="n in isolatedTables"
                    :key="n.id"
                    class="iso-card"
                    :class="{ matched: n.matched, dimmed: n.dimmed }"
                    :data-node-id="n.id"
                    :title="'与其他表无血缘关系'"
                    @click="onIsolatedClick(n)"
                  >
                    <div class="node-type">表 · 独立</div>
                    <div class="node-label">{{ n.label }}</div>
                    <div v-if="n.sourceName" class="node-sub">{{ n.sourceName }}</div>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="selectedEdge" class="edge-detail">
              <strong>关系详情</strong>
              <span>{{ selectedEdge.fromLabel || selectedEdge.fromNode }} → {{ selectedEdge.toLabel || selectedEdge.toNode }}</span>
              <el-tag size="small" :type="selectedEdge.crossDb ? 'warning' : 'info'">
                {{ edgeTypeLabel(selectedEdge.edgeType, selectedEdge.crossDb) }}
              </el-tag>
              <span v-if="selectedEdge.fieldMapping" class="edge-map">映射：{{ selectedEdge.fieldMapping }}</span>
              <el-button link type="primary" @click="selectedEdge = null">关闭</el-button>
            </div>
          </el-col>

          <el-col v-if="focusMeta" :span="8">
            <div class="meta-panel">
              <h4>表使用说明</h4>
              <p class="meta-desc">{{ focusMeta.usageDesc || '暂无使用说明' }}</p>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="表名称">{{ focusMeta.tableName || focusCard?.label || '—' }}</el-descriptions-item>
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
              <el-button style="margin-top:12px" @click="closeMeta">收起</el-button>
            </div>
          </el-col>
        </el-row>
      </template>

      <!-- 下钻模式 -->
      <template v-else>
        <el-tabs v-model="drillSubTab" class="drill-tabs">
          <el-tab-pane label="表血缘" name="table">
            <div class="drill-board">
              <div class="drill-col">
                <div class="col-head">上游（{{ upstreamNodes.length }}）</div>
                <div v-if="!upstreamNodes.length" class="col-empty">暂无上游</div>
                <div
                  v-for="n in upstreamNodes"
                  :key="`up-${n.id}`"
                  class="drill-card"
                  :class="[`type-${(n.type || 'table').toLowerCase()}`, { cross: n.crossDb }]"
                  @click="onNeighborClick(n)"
                >
                  <div class="node-type">{{ typeLabel(n.type) }}<template v-if="n.crossDb"> · 跨库</template></div>
                  <div class="node-label">{{ n.label }}</div>
                  <div v-if="n.sourceName" class="node-sub">{{ n.sourceName }}</div>
                  <div class="card-tip">点击继续下钻</div>
                </div>
              </div>

              <div class="drill-arrows">
                <div v-for="n in Math.max(upstreamNodes.length, 1)" :key="`ua-${n}`" class="arrow-row">
                  <span class="flow-arrow">→</span>
                </div>
              </div>

              <div class="drill-col drill-focus">
                <div class="col-head">当前表</div>
                <div class="focus-card">
                  <div class="node-type">{{ typeLabel(focusCard?.type) }}</div>
                  <div class="focus-title">{{ focusCard?.label || focusMeta?.tableName || focusNode }}</div>
                  <div v-if="focusMeta?.sourceName || focusCard?.sourceName" class="node-sub">
                    {{ focusMeta?.sourceName || focusCard?.sourceName }}
                  </div>
                  <div v-if="(focusMeta?.categories as string[] | undefined)?.length" class="focus-tags">
                    <el-tag
                      v-for="(c, i) in (focusMeta?.categories as string[])"
                      :key="i"
                      size="small"
                      style="margin:0 4px 4px 0"
                    >{{ c }}</el-tag>
                  </div>
                  <p class="focus-desc">{{ focusMeta?.usageDesc || '暂无使用说明' }}</p>
                  <el-tag v-if="noMoreTip" size="small" type="info">已无更多血缘</el-tag>
                </div>
              </div>

              <div class="drill-arrows">
                <div v-for="n in Math.max(downstreamNodes.length, 1)" :key="`da-${n}`" class="arrow-row">
                  <span class="flow-arrow">→</span>
                </div>
              </div>

              <div class="drill-col">
                <div class="col-head">下游（{{ downstreamNodes.length }}）</div>
                <div v-if="!downstreamNodes.length" class="col-empty">暂无下游</div>
                <div
                  v-for="n in downstreamNodes"
                  :key="`down-${n.id}`"
                  class="drill-card"
                  :class="[`type-${(n.type || 'table').toLowerCase()}`, { cross: n.crossDb }]"
                  @click="onNeighborClick(n)"
                >
                  <div class="node-type">{{ typeLabel(n.type) }}<template v-if="n.crossDb"> · 跨库</template></div>
                  <div class="node-label">{{ n.label }}</div>
                  <div v-if="n.sourceName" class="node-sub">{{ n.sourceName }}</div>
                  <div class="card-tip">点击继续下钻</div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="字段血缘" name="field">
            <div v-if="!focusNode || !String(focusNode).startsWith('tbl-')" class="empty-block">
              <el-empty description="请先在全景选择一张表" :image-size="56" />
            </div>
            <template v-else>
              <div class="drill-board field-board">
                <div class="drill-col">
                  <div class="col-head">上游字段（{{ fieldUpstream.length }}）</div>
                  <div v-if="!fieldUpstream.length" class="col-empty">暂无上游字段</div>
                  <div v-for="f in fieldUpstream" :key="`fu-${f}`" class="field-chip">{{ f }}</div>
                </div>
                <div class="drill-arrows"><span class="flow-arrow">→</span></div>
                <div class="drill-col drill-focus">
                  <div class="col-head">本表字段（{{ fieldCurrent.length }}）</div>
                  <div v-if="!fieldCurrent.length" class="col-empty">暂无字段记录</div>
                  <div v-for="f in fieldCurrent" :key="`fc-${f}`" class="field-chip current">{{ f }}</div>
                </div>
                <div class="drill-arrows"><span class="flow-arrow">→</span></div>
                <div class="drill-col">
                  <div class="col-head">下游字段（{{ fieldDownstream.length }}）</div>
                  <div v-if="!fieldDownstream.length" class="col-empty">暂无下游字段</div>
                  <div v-for="f in fieldDownstream" :key="`fd-${f}`" class="field-chip">{{ f }}</div>
                </div>
              </div>

              <el-table :data="fieldRows" stripe size="small" style="margin-top:12px">
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
            </template>
          </el-tab-pane>
        </el-tabs>

        <el-row :gutter="12" style="margin-top:12px">
          <el-col :span="24">
            <div v-if="focusMeta" class="meta-panel meta-panel--inline">
              <h4>表使用说明</h4>
              <p class="meta-desc">{{ focusMeta.usageDesc || '暂无使用说明' }}</p>
              <el-descriptions :column="3" border size="small">
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
            </div>
          </el-col>
        </el-row>
      </template>
    </PageCard>
  </div>
</template>

<style scoped>
.hint { font-size: 13px; color: #606266; margin: 0 0 12px; line-height: 1.6; }
.stats-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.stat { display: flex; align-items: baseline; gap: 6px; }
.stat-n { font-size: 18px; font-weight: 600; color: #303133; }
.stat-l { font-size: 12px; color: #909399; }
.legend { display: flex; flex-wrap: wrap; gap: 12px; margin-left: auto; font-size: 12px; color: #606266; }
.lg-item { display: inline-flex; align-items: center; gap: 6px; }
.lg-line {
  display: inline-block;
  width: 22px;
  height: 0;
  border-top: 2px solid #409eff;
}
.lg-line.cross { border-top: 2px dashed #e6a23c; }
.lg-box {
  display: inline-block;
  width: 14px;
  height: 10px;
  border: 1px dashed #c0c4cc;
  border-radius: 2px;
  background: #f5f7fa;
}
.crumb-bar { margin-bottom: 10px; font-size: 13px; }
.sep { margin: 0 4px; color: #c0c4cc; }

.panorama-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 12px;
  align-items: start;
}
@media (max-width: 1100px) {
  .panorama-layout { grid-template-columns: 1fr; }
}
.pane-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.linked-pane, .isolated-pane {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
  background: #fff;
  min-height: 240px;
}
.isolated-pane { max-height: 520px; overflow: auto; }
.isolated-empty { font-size: 12px; color: #909399; padding: 12px 0; }
.isolated-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}
.iso-card {
  border: 1px dashed #c0c4cc;
  border-radius: 6px;
  padding: 8px 10px;
  background: #f5f7fa;
  color: #909399;
  cursor: pointer;
}
.iso-card:hover { border-color: #409eff; color: #606266; }
.iso-card.matched { border-color: #409eff; background: #ecf5ff; color: #303133; }
.iso-card.dimmed { opacity: 0.45; }

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
}
.edge-hit { cursor: pointer; pointer-events: stroke; }
.edge-label { font-size: 10px; fill: #e6a23c; }
.graph-nodes { position: relative; min-width: 100%; pointer-events: none; }
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
  pointer-events: auto;
}
.graph-node:hover { border-color: #409eff; }
.graph-node.matched { box-shadow: 0 0 0 2px rgba(64,158,255,.35); }
.graph-node.dimmed { opacity: 0.4; }
.graph-node.type-source { border-left: 3px solid #e6a23c; }
.graph-node.type-table { border-left: 3px solid #409eff; }
.graph-node.type-catalog { border-left: 3px solid #67c23a; }
.node-type { font-size: 10px; color: #909399; }
.node-label { font-size: 12px; font-weight: 600; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.node-sub { font-size: 10px; color: #a8abb2; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.edge-detail {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: 8px 10px;
  background: #f5f7fa;
  border-radius: 6px;
}
.edge-map { color: #606266; }

.empty-block { padding: 24px 0; }
.empty-sub { font-size: 12px; color: #909399; margin: 4px 0 0; }

.meta-panel {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  background: #fff;
  min-height: 200px;
}
.meta-panel--inline { min-height: auto; }
.meta-panel h4 { margin: 0 0 8px; font-size: 14px; }
.meta-desc { font-size: 13px; color: #606266; line-height: 1.6; margin: 0 0 12px; }

.drill-tabs { margin-top: 4px; }
.drill-board {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) 36px minmax(200px, 1.2fr) 36px minmax(160px, 1fr);
  gap: 8px;
  align-items: start;
  min-height: 280px;
}
@media (max-width: 960px) {
  .drill-board {
    grid-template-columns: 1fr;
  }
  .drill-arrows { display: none; }
}
.drill-col {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
  background: #fafafa;
  min-height: 200px;
}
.drill-focus { background: #fff; border-color: #409eff; }
.col-head {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 10px;
  color: #303133;
}
.col-empty { font-size: 12px; color: #909399; padding: 16px 0; text-align: center; }
.drill-card {
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  cursor: pointer;
}
.drill-card:hover { border-color: #409eff; }
.drill-card.cross { border-color: #e6a23c; }
.drill-card.type-source { border-left: 3px solid #e6a23c; }
.drill-card.type-table { border-left: 3px solid #409eff; }
.card-tip { font-size: 10px; color: #a8abb2; margin-top: 4px; }
.focus-card { padding: 4px 2px; }
.focus-title { font-size: 16px; font-weight: 700; margin: 4px 0 6px; color: #303133; }
.focus-tags { margin: 6px 0; }
.focus-desc { font-size: 13px; color: #606266; line-height: 1.6; margin: 8px 0; }
.drill-arrows {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding-top: 36px;
  gap: 66px;
}
.flow-arrow { color: #409eff; font-size: 20px; font-weight: 600; }
.field-chip {
  display: block;
  padding: 6px 8px;
  margin-bottom: 6px;
  background: #ecf5ff;
  border-radius: 4px;
  font-size: 12px;
  color: #409eff;
  word-break: break-all;
}
.field-chip.current { background: #f0f9eb; color: #67c23a; }
</style>
