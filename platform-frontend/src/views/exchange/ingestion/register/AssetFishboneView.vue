<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import {
  ingestionApi,
  useIngestionLoading,
  type AssetFishboneNode,
} from '../useIngestionHub'

type LayoutNode = {
  id: string
  type: string
  label: string
  code?: string | null
  x: number
  y: number
  w: number
  h: number
  expandable: boolean
  expanded: boolean
  depth: number
}

type LayoutEdge = { fromX: number; fromY: number; toX: number; toY: number }

const NODE_W = 188
const NODE_H = 52
const COL_GAP = 220
const ROW_GAP = 68
const PAD_X = 48
const PAD_Y = 40

const TYPE_LABEL: Record<string, string> = {
  PROJECT: '项目',
  SYSTEM: '系统',
  DATABASE: '数据库',
  TABLE: '数据表',
  COLUMN: '数据项',
  DICT: '数据字典',
  DEPT: '部门',
}

const TYPE_COLOR: Record<string, string> = {
  PROJECT: '#2563eb',
  SYSTEM: '#0d9488',
  DATABASE: '#7c3aed',
  TABLE: '#db2777',
  COLUMN: '#ea580c',
  DICT: '#65a30d',
  DEPT: '#1e293b',
  ORG: '#475569',
  ROOT: '#1e293b',
}

const { loading, loadError, withLoad } = useIngestionLoading()

const props = withDefaults(defineProps<{
  /** 嵌在其它 PageCard 内时不重复套标题卡片 */
  embedded?: boolean
}>(), {
  embedded: false,
})

const mode = ref<'PLATFORM' | 'DEPT'>('DEPT')
const rootOrg = ref<{ id: number | null; orgName: string } | null>(null)
const orgs = ref<Array<{ id: number; orgName: string; orgCode?: string }>>([])
const selectedOrgId = ref<number | null>(null)
const selectedOrg = ref<{ id: number | null; orgName: string } | null>(null)
const tree = ref<AssetFishboneNode[]>([])
const expanded = ref<Set<string>>(new Set())

const layoutNodes = ref<LayoutNode[]>([])
const layoutEdges = ref<LayoutEdge[]>([])
const svgW = ref(1200)
const svgH = ref(560)

const hintText = computed(() => {
  if (mode.value === 'PLATFORM' && selectedOrgId.value == null) {
    return '中心为「承德市高新区」，周围为各部门；点击部门后向右展开部门 → 项目 → 系统 → 数据库 → 数据表 → 数据项 → 数据字典（有关联时）鱼骨图，可用 +/- 展开或折叠。'
  }
  return '鱼骨图从左到右：部门 → 项目 → 系统 → 数据库 → 数据表 → 数据项 → 数据字典（若数据项已关联字典）。点击节点旁 +/- 展开或折叠。'
})

const selectedOrgName = computed(() => {
  if (selectedOrg.value?.orgName) return selectedOrg.value.orgName
  if (selectedOrgId.value == null) return ''
  const hit = orgs.value.find((o) => o.id === selectedOrgId.value)
  if (hit) return hit.orgName
  return ''
})

const deptRootName = computed(() => selectedOrgName.value || '本部门')

function typeLabel(type: string) {
  if (type === 'ORG' || type === 'ROOT') return ''
  return TYPE_LABEL[type] || type
}

const legendItems = computed(() =>
  Object.entries(TYPE_LABEL).filter(([key]) => key !== 'ORG' && key !== 'ROOT'),
)

function typeColor(type: string) {
  return TYPE_COLOR[type] || '#64748b'
}

/** 表/数据项尽量展示中英文；过长截断，完整文案在 title */
function formatNodeLabel(n: LayoutNode) {
  let text = n.label || ''
  if ((n.type === 'TABLE' || n.type === 'COLUMN') && n.code) {
    const code = String(n.code)
    if (text && !text.includes('/') && code && text.toLowerCase() !== code.toLowerCase()) {
      text = `${text} / ${code}`
    } else if (!text) {
      text = code
    }
  }
  return text.length > 18 ? `${text.slice(0, 18)}…` : text
}

function defaultExpandedIds(nodes: AssetFishboneNode[], depth = 0, acc = new Set<string>()) {
  for (const n of nodes) {
    const kids = n.children || []
    if (kids.length && depth < 2) {
      acc.add(n.id)
      defaultExpandedIds(kids, depth + 1, acc)
    }
  }
  return acc
}

async function load(orgId?: number | null) {
  await withLoad(async () => {
    const params = orgId != null ? { orgId } : undefined
    const data = (await ingestionApi.assetFishbone(params)).data
    mode.value = data.mode
    rootOrg.value = data.rootOrg
    orgs.value = data.orgs || []
    selectedOrgId.value = data.selectedOrgId ?? null
    selectedOrg.value = data.selectedOrg || null
    tree.value = data.tree || []
    if (data.mode === 'PLATFORM' && data.selectedOrgId == null) {
      expanded.value = new Set()
    } else {
      const deptId = `dept:${data.selectedOrgId ?? 'self'}`
      expanded.value = defaultExpandedIds([{
        id: deptId,
        type: 'DEPT',
        refId: data.selectedOrgId ?? 0,
        label: data.selectedOrg?.orgName || '部门',
        children: tree.value,
      }])
    }
  })
  await nextTick()
  rebuildLayout()
}

async function selectOrg(orgId: number) {
  await load(orgId)
}

async function clearOrgSelection() {
  selectedOrgId.value = null
  selectedOrg.value = null
  tree.value = []
  expanded.value = new Set()
  await load(null)
}

function toggle(id: string) {
  const next = new Set(expanded.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expanded.value = next
  rebuildLayout()
}

function rebuildLayout() {
  if (mode.value === 'PLATFORM' && selectedOrgId.value == null) {
    buildOrgRadialLayout()
    return
  }
  const deptRoot: AssetFishboneNode = {
    id: `dept:${selectedOrgId.value ?? 'self'}`,
    type: 'DEPT',
    refId: selectedOrgId.value ?? 0,
    label: deptRootName.value,
    children: tree.value,
  }
  buildFishboneLayout([deptRoot])
}

function buildOrgRadialLayout() {
  const rootLabel = rootOrg.value?.orgName || '承德市高新区'
  const list = orgs.value
  const cx = 280
  const cy = Math.max(260, 80 + list.length * 28)
  const nodes: LayoutNode[] = [{
    id: 'root-org',
    type: 'ROOT',
    label: rootLabel,
    x: cx - NODE_W / 2,
    y: cy - NODE_H / 2,
    w: NODE_W,
    h: NODE_H,
    expandable: false,
    expanded: false,
    depth: 0,
  }]
  const edges: LayoutEdge[] = []
  const radius = Math.max(180, 90 + list.length * 12)
  list.forEach((org, i) => {
    const angle = list.length === 1
      ? -Math.PI / 2
      : -Math.PI * 0.85 + (Math.PI * 1.7 * i) / Math.max(1, list.length - 1)
    const x = cx + Math.cos(angle) * radius - NODE_W / 2
    const y = cy + Math.sin(angle) * radius - NODE_H / 2
    nodes.push({
      id: `org:${org.id}`,
      type: 'ORG',
      label: org.orgName,
      code: org.orgCode,
      x,
      y,
      w: NODE_W,
      h: NODE_H,
      expandable: false,
      expanded: false,
      depth: 1,
    })
    edges.push({
      fromX: cx,
      fromY: cy,
      toX: x + NODE_W / 2,
      toY: y + NODE_H / 2,
    })
  })
  layoutNodes.value = nodes
  layoutEdges.value = edges
  svgW.value = Math.max(900, ...nodes.map((n) => n.x + n.w + PAD_X))
  svgH.value = Math.max(520, ...nodes.map((n) => n.y + n.h + PAD_Y))
}

function buildFishboneLayout(roots: AssetFishboneNode[]) {
  const nodes: LayoutNode[] = []
  const edges: LayoutEdge[] = []
  let cursorY = PAD_Y

  function subtreeHeight(node: AssetFishboneNode): number {
    const kids = node.children || []
    const isOpen = expanded.value.has(node.id)
    if (!kids.length || !isOpen) return ROW_GAP
    return Math.max(ROW_GAP, kids.reduce((sum, c) => sum + subtreeHeight(c), 0))
  }

  function place(node: AssetFishboneNode, depth: number, top: number): number {
    const kids = node.children || []
    const isOpen = expanded.value.has(node.id)
    const height = subtreeHeight(node)
    const x = PAD_X + depth * COL_GAP
    const y = top + height / 2 - NODE_H / 2
    nodes.push({
      id: node.id,
      type: node.type,
      label: node.label,
      code: node.code,
      x,
      y,
      w: NODE_W,
      h: NODE_H,
      expandable: kids.length > 0,
      expanded: isOpen,
      depth,
    })
    if (kids.length && isOpen) {
      let childTop = top
      for (const child of kids) {
        const ch = subtreeHeight(child)
        const childMid = place(child, depth + 1, childTop)
        edges.push({
          fromX: x + NODE_W,
          fromY: y + NODE_H / 2,
          toX: PAD_X + (depth + 1) * COL_GAP,
          toY: childMid,
        })
        childTop += ch
      }
    }
    return y + NODE_H / 2
  }

  for (const root of roots) {
    const h = subtreeHeight(root)
    place(root, 0, cursorY)
    cursorY += h + 16
  }

  if (!roots.length) {
    layoutNodes.value = []
    layoutEdges.value = []
    svgW.value = 900
    svgH.value = 360
    return
  }

  layoutNodes.value = nodes
  layoutEdges.value = edges
  svgW.value = Math.max(900, ...nodes.map((n) => n.x + n.w + PAD_X))
  svgH.value = Math.max(360, cursorY + PAD_Y)
}

function onNodeClick(n: LayoutNode) {
  if (n.type === 'ORG') {
    const id = Number(String(n.id).replace('org:', ''))
    if (id) void selectOrg(id)
    return
  }
  if (n.expandable) toggle(n.id)
}

function edgePath(e: LayoutEdge) {
  const mx = (e.fromX + e.toX) / 2
  return `M ${e.fromX} ${e.fromY} C ${mx} ${e.fromY}, ${mx} ${e.toY}, ${e.toX} ${e.toY}`
}

watch(expanded, () => rebuildLayout())

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="fishbone-page" v-loading="loading">
    <component :is="embedded ? 'div' : PageCard" v-bind="embedded ? {} : { title: '数据资产图谱分析' }">
      <p class="hint">{{ hintText }}</p>
      <div v-if="loadError" class="error">{{ loadError }}</div>

      <div class="toolbar">
        <div class="legend">
          <span v-for="[key, label] in legendItems" :key="key" class="leg-item">
            <i class="dot" :style="{ background: typeColor(key) }" />{{ label }}
          </span>
        </div>
        <div class="actions">
          <template v-if="mode === 'PLATFORM' && selectedOrgId != null">
            <el-tag type="info">当前部门：{{ selectedOrgName }}</el-tag>
            <el-button size="small" @click="clearOrgSelection">返回部门总览</el-button>
          </template>
          <el-button size="small" :loading="loading" @click="load(selectedOrgId)">刷新</el-button>
        </div>
      </div>

      <div class="canvas-wrap">
        <div
          v-if="!layoutNodes.length && !loading"
          class="empty"
        >
          {{ mode === 'PLATFORM' && selectedOrgId == null
            ? '暂无下级部门'
            : '当前范围暂无项目/系统/库表登记数据' }}
        </div>
        <svg
          v-else
          class="fishbone-svg"
          :viewBox="`0 0 ${svgW} ${svgH}`"
          :width="svgW"
          :height="svgH"
        >
          <path
            v-for="(e, idx) in layoutEdges"
            :key="`e-${idx}`"
            :d="edgePath(e)"
            class="edge"
            fill="none"
          />
          <g
            v-for="n in layoutNodes"
            :key="n.id"
            class="node"
            :transform="`translate(${n.x}, ${n.y})`"
            @click="onNodeClick(n)"
          >
            <rect
              :width="n.w"
              :height="n.h"
              rx="8"
              :fill="n.type === 'ORG' || n.type === 'ROOT' || n.type === 'DEPT' ? '#f8fafc' : '#fff'"
              :stroke="typeColor(n.type)"
              stroke-width="1.6"
            />
            <text
              v-if="typeLabel(n.type)"
              x="12"
              y="18"
              class="type-text"
              :fill="typeColor(n.type)"
            >
              {{ typeLabel(n.type) }}
            </text>
            <text
              x="12"
              :y="typeLabel(n.type) ? 36 : 30"
              class="label-text"
            >
              {{ formatNodeLabel(n) }}
            </text>
            <g v-if="n.expandable" class="toggle" @click.stop="toggle(n.id)">
              <circle :cx="n.w - 18" cy="26" r="10" fill="#fff" :stroke="typeColor(n.type)" />
              <text :x="n.w - 18" y="30" text-anchor="middle" class="toggle-text">
                {{ n.expanded ? '−' : '+' }}
              </text>
            </g>
            <title>{{ typeLabel(n.type) ? `${typeLabel(n.type)}：` : '' }}{{ n.label }}{{ n.code ? `（${n.code}）` : '' }}</title>
          </g>
        </svg>
      </div>
    </component>
  </div>
</template>

<style scoped>
.fishbone-page { min-height: 420px; }
.hint { margin: 0 0 12px; color: #64748b; font-size: 13px; line-height: 1.6; }
.error { color: #dc2626; margin-bottom: 8px; }
.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.legend { display: flex; flex-wrap: wrap; gap: 10px 14px; }
.leg-item { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: #475569; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.actions { display: flex; gap: 8px; align-items: center; }
.canvas-wrap {
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: linear-gradient(180deg, #f8fafc 0%, #fff 40%);
  min-height: 360px;
}
.fishbone-svg { display: block; min-width: 100%; }
.edge { stroke: #94a3b8; stroke-width: 1.5; }
.node { cursor: pointer; }
.node:hover rect { filter: brightness(0.98); }
.type-text { font-size: 10px; font-weight: 600; }
.label-text { font-size: 12px; fill: #0f172a; }
.toggle { cursor: pointer; }
.toggle-text { font-size: 14px; fill: #334155; font-weight: 700; }
.empty {
  padding: 64px 24px;
  text-align: center;
  color: #94a3b8;
  font-size: 14px;
}
</style>
