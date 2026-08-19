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
  lines?: string[]
}

type LayoutEdge = { fromX: number; fromY: number; toX: number; toY: number; straight?: boolean }

const NODE_W = 188
const NODE_H = 52
const COL_GAP = 220
const ROW_GAP = 68
const PAD_X = 48
const PAD_Y = 40
const RADIAL_MIN_W = 112
const RADIAL_MAX_W = 168
const RADIAL_TEXT_INNER = 140
const RADIAL_LINE_H = 15
const RADIAL_GAP = 14

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

const isRadialOverview = computed(() => mode.value === 'PLATFORM' && selectedOrgId.value == null)

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

function estimateTextWidth(text: string, fontSize = 12): number {
  let w = 0
  for (const ch of text) {
    w += ch.charCodeAt(0) > 255 ? fontSize : fontSize * 0.58
  }
  return w
}

function wrapLabel(text: string, maxInner = RADIAL_TEXT_INNER, maxLines = 2): string[] {
  const chars = [...(text || '').trim()]
  if (!chars.length) return ['']
  const lines: string[] = []
  let i = 0
  while (i < chars.length && lines.length < maxLines) {
    let line = ''
    while (i < chars.length) {
      const trial = line + chars[i]
      if (line && estimateTextWidth(trial) > maxInner) break
      line = trial
      i++
    }
    const last = lines.length === maxLines - 1 && i < chars.length
    if (last) {
      while (line.length && estimateTextWidth(`${line}…`) > maxInner) {
        line = [...line].slice(0, -1).join('')
      }
      line += '…'
      i = chars.length
    }
    lines.push(line)
  }
  return lines
}

function sizeForLabel(label: string) {
  const lines = wrapLabel(label)
  const textW = Math.max(...lines.map((l) => estimateTextWidth(l)), 72)
  const w = Math.min(RADIAL_MAX_W, Math.max(RADIAL_MIN_W, Math.ceil(textW + 24)))
  const h = 12 + lines.length * RADIAL_LINE_H + 10
  return { w, h, lines }
}

function nodeLines(n: LayoutNode) {
  return n.lines?.length ? n.lines : [formatNodeLabel(n)]
}

function labelBaseY(n: LayoutNode) {
  if (typeLabel(n.type)) return 34
  const blockH = nodeLines(n).length * RADIAL_LINE_H
  return Math.round((n.h - blockH) / 2 + 13)
}

function boxesOverlap(a: LayoutNode, b: LayoutNode, gap: number) {
  return a.x < b.x + b.w + gap
    && a.x + a.w + gap > b.x
    && a.y < b.y + b.h + gap
    && a.y + a.h + gap > b.y
}

function pushOut(node: LayoutNode, cx: number, cy: number, dist: number) {
  const acx = node.x + node.w / 2
  const acy = node.y + node.h / 2
  const d = Math.hypot(acx - cx, acy - cy) || 1
  node.x += ((acx - cx) / d) * dist
  node.y += ((acy - cy) / d) * dist
}

/** 重叠时只把更靠外的节点外移，保持内圈紧凑 */
function resolveRadialOverlaps(nodes: LayoutNode[], cx: number, cy: number) {
  const root = nodes[0]
  for (let iter = 0; iter < 24; iter++) {
    let moved = false
    for (let i = 1; i < nodes.length; i++) {
      const a = nodes[i]
      if (root && boxesOverlap(root, a, 10)) {
        pushOut(a, cx, cy, 5)
        moved = true
      }
      for (let j = i + 1; j < nodes.length; j++) {
        const b = nodes[j]
        if (!boxesOverlap(a, b, 8)) continue
        const ra = Math.hypot(a.x + a.w / 2 - cx, a.y + a.h / 2 - cy)
        const rb = Math.hypot(b.x + b.w / 2 - cx, b.y + b.h / 2 - cy)
        if (ra >= rb) pushOut(a, cx, cy, 4)
        else pushOut(b, cx, cy, 4)
        moved = true
      }
    }
    if (!moved) break
  }
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
  const n = list.length
  const pad = 32
  const leafNodeW = 148
  const leafNodeH = 38
  const rootW = 180
  const rootH = 52
  const rowGap = 12
  const colGap = 160

  const half = Math.ceil(n / 2)
  const leftList = list.slice(0, half)
  const rightList = list.slice(half)

  const leftH = leftList.length * (leafNodeH + rowGap) - rowGap
  const rightH = rightList.length * (leafNodeH + rowGap) - rowGap
  const totalH = Math.max(leftH, rightH, rootH) + pad * 2
  const totalW = pad + leafNodeW + colGap + rootW + colGap + leafNodeW + pad

  const cx = totalW / 2
  const cy = totalH / 2

  const nodes: LayoutNode[] = [{
    id: 'root-org',
    type: 'ROOT',
    label: rootLabel,
    x: cx - rootW / 2,
    y: cy - rootH / 2,
    w: rootW,
    h: rootH,
    expandable: false,
    expanded: false,
    depth: 0,
    lines: wrapLabel(rootLabel, rootW - 24, 2),
  }]

  const leftStartY = cy - leftH / 2
  leftList.forEach((org, i) => {
    const x = pad
    const y = leftStartY + i * (leafNodeH + rowGap)
    nodes.push({
      id: `org:${org.id}`,
      type: 'ORG',
      label: org.orgName,
      code: org.orgCode,
      x,
      y,
      w: leafNodeW,
      h: leafNodeH,
      expandable: false,
      expanded: false,
      depth: 1,
      lines: wrapLabel(org.orgName, leafNodeW - 20, 1),
    })
  })

  const rightStartY = cy - rightH / 2
  rightList.forEach((org, i) => {
    const x = totalW - pad - leafNodeW
    const y = rightStartY + i * (leafNodeH + rowGap)
    nodes.push({
      id: `org:${org.id}`,
      type: 'ORG',
      label: org.orgName,
      code: org.orgCode,
      x,
      y,
      w: leafNodeW,
      h: leafNodeH,
      expandable: false,
      expanded: false,
      depth: 1,
      lines: wrapLabel(org.orgName, leafNodeW - 20, 1),
    })
  })

  layoutNodes.value = nodes
  layoutEdges.value = nodes.slice(1).map((nd) => ({
    fromX: nd.x < cx ? nd.x + nd.w : nd.x,
    fromY: nd.y + nd.h / 2,
    toX: nd.x < cx ? cx - rootW / 2 : cx + rootW / 2,
    toY: cy,
  }))
  svgW.value = totalW
  svgH.value = totalH
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
  if (e.straight) {
    const mx = (e.fromX + e.toX) / 2
    return `M ${e.fromX} ${e.fromY} C ${mx} ${e.fromY}, ${mx} ${e.toY}, ${e.toX} ${e.toY}`
  }
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
      <!-- <p class="hint">{{ hintText }}</p> -->
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

      <div class="canvas-wrap" :class="{ 'is-radial': isRadialOverview }">
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
          :class="{ 'is-radial': isRadialOverview }"
          :viewBox="`0 0 ${svgW} ${svgH}`"
          :width="isRadialOverview ? undefined : svgW"
          :height="isRadialOverview ? undefined : svgH"
          preserveAspectRatio="xMidYMid meet"
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
              :rx="n.type === 'ROOT' ? 12 : 18"
              :fill="n.type === 'ROOT' ? '#d1e9ff' : '#fff'"
              :stroke="n.type === 'ROOT' ? '#8fbfe8' : '#b0c4d8'"
              :stroke-width="n.type === 'ROOT' ? 2 : 1.2"
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
              :x="n.type === 'ROOT' || n.type === 'ORG' ? n.w / 2 : 12"
              :y="labelBaseY(n)"
              class="label-text"
              :text-anchor="n.type === 'ROOT' || n.type === 'ORG' ? 'middle' : undefined"
            >
              <tspan
                v-for="(line, li) in nodeLines(n)"
                :key="li"
                :x="n.type === 'ROOT' || n.type === 'ORG' ? n.w / 2 : 12"
                :dy="li === 0 ? 0 : RADIAL_LINE_H"
              >{{ line }}</tspan>
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
  background: #fff;
  min-height: 360px;
}
.canvas-wrap.is-radial {
  height: min(520px, 56vh);
  min-height: 380px;
  overflow: hidden;
}
.fishbone-svg { display: block; min-width: 100%; }
.fishbone-svg.is-radial {
  min-width: 0;
  width: 100%;
  height: 100%;
}
.edge { stroke: #7eabc9; stroke-width: 1.4; }
.node { cursor: pointer; }
.node:hover rect { filter: brightness(0.96); }
.type-text { font-size: 10px; font-weight: 600; }
.label-text { font-size: 12px; fill: #1e293b; }
.toggle { cursor: pointer; }
.toggle-text { font-size: 14px; fill: #334155; font-weight: 700; }
.empty {
  padding: 64px 24px;
  text-align: center;
  color: #94a3b8;
  font-size: 14px;
}
</style>
