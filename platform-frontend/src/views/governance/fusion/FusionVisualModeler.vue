<script setup lang="ts">
import { computed, markRaw, nextTick, reactive, ref, watch } from 'vue'
import {
  VueFlow,
  addEdge,
  useVueFlow,
  type Connection,
  type Edge,
  type Node,
  MarkerType,
} from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import FusionEntityNode from './FusionEntityNode.vue'

export type ModelLayer = 'ODS' | 'DWD' | 'DWS' | 'ADS'

const props = defineProps<{
  modelValue: boolean
  domainId: number | null
  domainName?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  changed: []
}>()

const nodeTypes = { entity: markRaw(FusionEntityNode) }

const { fitView, project } = useVueFlow({ id: 'fusion-visual-modeler' })

const LAYERS: Array<{ key: ModelLayer; label: string; y: number; hint: string }> = [
  { key: 'ODS', label: '源层 ODS', y: 40, hint: '直通可编目 · 登记源' },
  { key: 'DWD', label: '过程层 DWD', y: 280, hint: '治理过程 · 默认不进门户' },
  { key: 'DWS', label: '主题库 DWS', y: 520, hint: '多表融合主题 · 可编目' },
  { key: 'ADS', label: '专题库 ADS', y: 760, hint: '专题加工 · 可编目' },
]

const PALETTE = [
  { type: 'entity', layer: 'ODS' as ModelLayer, label: '源层实体', desc: 'ODS / 登记源' },
  { type: 'entity', layer: 'DWD' as ModelLayer, label: '过程实体', desc: 'DWD 过程数据' },
  { type: 'entity', layer: 'DWS' as ModelLayer, label: '主题实体', desc: 'DWS 主题库' },
  { type: 'entity', layer: 'ADS' as ModelLayer, label: '专题实体', desc: 'ADS 专题库' },
]

interface FieldRow {
  id?: number
  fieldCode: string
  fieldName: string
  dataType: string
  pkFlag?: number
}

interface EntityNodeData {
  entityId: number
  entityCode: string
  entityName: string
  layer: ModelLayer
  fields: FieldRow[]
  physical: Array<{ id?: number; tableName: string; physicalCode?: string }>
  description?: string
}

interface RelationRow {
  id: number
  relationCode: string
  relationName: string
  fromEntityId: number
  toEntityId: number
  relationType: string
}

const loading = ref(false)
const saving = ref(false)
const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
const selectedId = ref<string | null>(null)
const relations = ref<RelationRow[]>([])

const createDlg = ref(false)
const createForm = reactive({
  entityCode: '',
  entityName: '',
  description: '',
  layer: 'DWS' as ModelLayer,
  dropX: 120,
  dropY: 520,
})

const relationDlg = ref(false)
const relationForm = reactive({
  relationCode: '',
  relationName: '',
  relationType: 'ONE_TO_MANY',
  fromEntityId: 0,
  toEntityId: 0,
  pendingConnection: null as Connection | null,
})

const selectedNode = computed(() => nodes.value.find((n) => n.id === selectedId.value) || null)
const selectedData = computed(() => (selectedNode.value?.data || null) as EntityNodeData | null)

const lineageUp = computed(() => {
  if (!selectedData.value) return []
  const id = selectedData.value.entityId
  return relations.value
    .filter((r) => r.toEntityId === id)
    .map((r) => ({
      relation: r,
      peer: findEntityBrief(r.fromEntityId),
      direction: '上游',
    }))
})

const lineageDown = computed(() => {
  if (!selectedData.value) return []
  const id = selectedData.value.entityId
  return relations.value
    .filter((r) => r.fromEntityId === id)
    .map((r) => ({
      relation: r,
      peer: findEntityBrief(r.toEntityId),
      direction: '下游',
    }))
})

function findEntityBrief(entityId: number) {
  const n = nodes.value.find((x) => x.data?.entityId === entityId)
  return {
    id: entityId,
    name: String(n?.data?.entityName || entityId),
    code: String(n?.data?.entityCode || ''),
    layer: String(n?.data?.layer || ''),
  }
}

function relationTypeLabel(t: string) {
  if (t === 'ONE_TO_ONE') return '一对一'
  if (t === 'MANY_TO_MANY') return '多对多'
  return '一对多'
}

function layerOfY(y: number): ModelLayer {
  let best: ModelLayer = 'DWS'
  let dist = Number.POSITIVE_INFINITY
  for (const lane of LAYERS) {
    const d = Math.abs(y - lane.y)
    if (d < dist) {
      dist = d
      best = lane.key
    }
  }
  return best
}

function close() {
  emit('update:modelValue', false)
}

function onNodeClick(ev: { node: Node }) {
  if (ev.node.type === 'lane') return
  selectedId.value = ev.node.id
}

function onPaneClick() {
  selectedId.value = null
}

function onDragStart(ev: DragEvent, item: (typeof PALETTE)[0]) {
  if (!ev.dataTransfer) return
  ev.dataTransfer.setData('application/fusion-palette', JSON.stringify(item))
  ev.dataTransfer.effectAllowed = 'move'
}

function onDragOver(ev: DragEvent) {
  ev.preventDefault()
  if (ev.dataTransfer) ev.dataTransfer.dropEffect = 'move'
}

async function onDrop(ev: DragEvent) {
  ev.preventDefault()
  const raw = ev.dataTransfer?.getData('application/fusion-palette')
  if (!raw || !props.domainId) return
  let item: (typeof PALETTE)[0]
  try {
    item = JSON.parse(raw)
  } catch {
    return
  }
  const bounds = (ev.currentTarget as HTMLElement).getBoundingClientRect()
  const position = project({ x: ev.clientX - bounds.left, y: ev.clientY - bounds.top })
  createForm.layer = item.layer || layerOfY(position.y)
  createForm.dropX = position.x
  createForm.dropY = position.y
  createForm.entityCode = ''
  createForm.entityName = ''
  createForm.description = ''
  createDlg.value = true
}

async function confirmCreateEntity() {
  if (!props.domainId) return
  if (!createForm.entityCode.trim() || !createForm.entityName.trim()) {
    ElMessage.warning('请填写实体编码与名称')
    return
  }
  try {
    const res = await api.post('/governance/fusion/models/entities', {
      domainId: props.domainId,
      entityCode: createForm.entityCode.trim(),
      entityName: createForm.entityName.trim(),
      description: createForm.description || null,
      status: 'ACTIVE',
    })
    const entityId = Number(res.data)
    const node: Node = {
      id: `entity:${entityId}`,
      type: 'entity',
      position: { x: createForm.dropX, y: createForm.dropY },
      data: {
        entityId,
        entityCode: createForm.entityCode.trim(),
        entityName: createForm.entityName.trim(),
        layer: createForm.layer,
        fields: [],
        physical: [],
        description: createForm.description,
      } satisfies EntityNodeData,
    }
    nodes.value = [...nodes.value, node]
    createDlg.value = false
    selectedId.value = node.id
    ElMessage.success('逻辑实体已创建')
    emit('changed')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '创建实体失败')
  }
}

function onConnect(params: Connection) {
  if (!params.source || !params.target) return
  if (params.source === params.target) {
    ElMessage.warning('不能与自身建立关系')
    return
  }
  const from = nodes.value.find((n) => n.id === params.source)
  const to = nodes.value.find((n) => n.id === params.target)
  if (!from?.data?.entityId || !to?.data?.entityId) return
  const exists = relations.value.some(
    (r) => r.fromEntityId === from.data.entityId && r.toEntityId === to.data.entityId,
  )
  if (exists) {
    ElMessage.warning('两实体间已存在该方向关系')
    return
  }
  relationForm.fromEntityId = Number(from.data.entityId)
  relationForm.toEntityId = Number(to.data.entityId)
  relationForm.relationCode = `REL_${from.data.entityCode}_${to.data.entityCode}`.slice(0, 64)
  relationForm.relationName = `${from.data.entityName} → ${to.data.entityName}`
  relationForm.relationType = 'ONE_TO_MANY'
  relationForm.pendingConnection = params
  relationDlg.value = true
}

async function confirmRelation() {
  if (!props.domainId || !relationForm.pendingConnection) return
  if (!relationForm.relationCode.trim()) {
    ElMessage.warning('请填写关系编码')
    return
  }
  try {
    const res = await api.post('/governance/fusion/models/relations', {
      domainId: props.domainId,
      relationCode: relationForm.relationCode.trim(),
      relationName: relationForm.relationName.trim() || relationForm.relationCode.trim(),
      fromEntityId: relationForm.fromEntityId,
      toEntityId: relationForm.toEntityId,
      relationType: relationForm.relationType,
    })
    const relId = Number(res.data)
    const conn = relationForm.pendingConnection
    // @ts-expect-error vue-flow Edge type instantiation is excessively deep
    edges.value = addEdge(
      {
        ...conn,
        id: `rel:${relId}`,
        label: relationTypeLabel(relationForm.relationType),
        markerEnd: MarkerType.ArrowClosed,
        animated: true,
        style: { stroke: '#409eff' },
        data: {
          relationId: relId,
          relationType: relationForm.relationType,
          relationName: relationForm.relationName,
        },
      },
      edges.value,
    )
    relations.value = [
      ...relations.value,
      {
        id: relId,
        relationCode: relationForm.relationCode.trim(),
        relationName: relationForm.relationName.trim(),
        fromEntityId: relationForm.fromEntityId,
        toEntityId: relationForm.toEntityId,
        relationType: relationForm.relationType,
      },
    ]
    relationDlg.value = false
    ElMessage.success('实体关系已建立（血缘边）')
    emit('changed')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '创建关系失败')
  }
}

async function removeRelation(relId: number) {
  await ElMessageBox.confirm('删除该实体关系（血缘边）？', '确认')
  await api.delete(`/governance/fusion/models/relations/${relId}`)
  relations.value = relations.value.filter((r) => r.id !== relId)
  edges.value = edges.value.filter((e) => Number(e.data?.relationId) !== relId && e.id !== `rel:${relId}`)
  ElMessage.success('关系已删除')
  emit('changed')
}

async function removeSelectedEntity() {
  if (!selectedData.value) return
  const name = selectedData.value.entityName
  await ElMessageBox.confirm(`删除逻辑实体「${name}」及其字段、物理映射与关系？`, '确认')
  await api.delete(`/governance/fusion/models/entities/${selectedData.value.entityId}`)
  const eid = selectedData.value.entityId
  nodes.value = nodes.value.filter((n) => n.data?.entityId !== eid)
  edges.value = edges.value.filter(
    (e) => e.source !== selectedId.value && e.target !== selectedId.value,
  )
  relations.value = relations.value.filter((r) => r.fromEntityId !== eid && r.toEntityId !== eid)
  selectedId.value = null
  ElMessage.success('已删除')
  emit('changed')
}

function buildLaneNodes(): Node[] {
  return LAYERS.map((lane, idx) => ({
    id: `lane:${lane.key}`,
    type: 'default',
    position: { x: 0, y: lane.y - 24 },
    draggable: false,
    selectable: false,
    connectable: false,
    zIndex: -1,
    style: {
      width: '1600px',
      height: '220px',
      background:
        idx % 2 === 0
          ? 'color-mix(in srgb, var(--el-color-primary) 4%, transparent)'
          : 'color-mix(in srgb, var(--el-fill-color) 60%, transparent)',
      border: '1px dashed var(--el-border-color)',
      borderRadius: '8px',
      pointerEvents: 'none',
    },
    data: { label: `${lane.label}  ·  ${lane.hint}` },
    class: 'fusion-lane-node',
  }))
}

function defaultLayout(entityNodes: EntityNodeData[]): { nodes: Node[]; edges: Edge[] } {
  const byLayer: Record<ModelLayer, EntityNodeData[]> = {
    ODS: [],
    DWD: [],
    DWS: [],
    ADS: [],
  }
  for (const e of entityNodes) {
    const layer = (e.layer || 'DWS') as ModelLayer
    byLayer[layer]?.push(e) || byLayer.DWS.push(e)
  }
  const outNodes: Node[] = [...buildLaneNodes()]
  for (const lane of LAYERS) {
    const list = byLayer[lane.key]
    list.forEach((e, i) => {
      outNodes.push({
        id: `entity:${e.entityId}`,
        type: 'entity',
        position: { x: 80 + i * 260, y: lane.y },
        data: { ...e },
      })
    })
  }
  const outEdges: Edge[] = relations.value.map((r) => ({
    id: `rel:${r.id}`,
    source: `entity:${r.fromEntityId}`,
    target: `entity:${r.toEntityId}`,
    label: relationTypeLabel(r.relationType),
    markerEnd: MarkerType.ArrowClosed,
    animated: true,
    style: { stroke: '#409eff' },
    data: {
      relationId: r.id,
      relationType: r.relationType,
      relationName: r.relationName,
    },
  }))
  return { nodes: outNodes, edges: outEdges }
}

function inferLayerFromPhysical(physical: Array<{ datasourceId?: number }>): ModelLayer {
  const ds = physical[0]?.datasourceId
  if (ds === -1) return 'ODS'
  if (ds === -2) return 'DWD'
  if (ds === -4) return 'ADS'
  return 'DWS'
}

async function loadModel() {
  if (!props.domainId) return
  loading.value = true
  try {
    const tree = (await api.get(`/governance/fusion/models/domains/${props.domainId}/tree`)).data
    const entityNodes: EntityNodeData[] = (tree.entities || []).map(
      (n: {
        entity: {
          id: number
          entityCode: string
          entityName: string
          description?: string
        }
        fields?: FieldRow[]
        physical?: Array<{ id?: number; tableName: string; physicalCode?: string; datasourceId?: number }>
      }) => {
        const physical = n.physical || []
        return {
          entityId: n.entity.id,
          entityCode: n.entity.entityCode,
          entityName: n.entity.entityName,
          description: n.entity.description,
          layer: inferLayerFromPhysical(physical),
          fields: n.fields || [],
          physical,
        }
      },
    )
    relations.value = tree.relations || []

    let layout: { nodes?: Array<{ id: string; position: { x: number; y: number }; layer?: ModelLayer }>; viewport?: unknown } | null =
      null
    try {
      const layoutRes = (await api.get(`/governance/fusion/models/domains/${props.domainId}/canvas`)).data
      if (layoutRes?.layoutJson) {
        layout = typeof layoutRes.layoutJson === 'string'
          ? JSON.parse(layoutRes.layoutJson)
          : layoutRes.layoutJson
      }
    } catch {
      layout = null
    }

    if (layout?.nodes?.length) {
      const posMap = new Map(layout.nodes.map((n) => [n.id, n]))
      const built = defaultLayout(entityNodes)
      built.nodes = built.nodes.map((node) => {
        if (node.type !== 'entity') return node
        const saved = posMap.get(node.id)
        if (saved?.position) {
          const layer = (saved.layer || node.data?.layer || layerOfY(saved.position.y)) as ModelLayer
          return {
            ...node,
            position: saved.position,
            data: { ...node.data, layer },
          }
        }
        return node
      })
      nodes.value = built.nodes
      edges.value = built.edges
    } else {
      const built = defaultLayout(entityNodes)
      nodes.value = built.nodes
      edges.value = built.edges
    }

    await nextTick()
    try {
      fitView({ padding: 0.15 })
    } catch {
      /* 画布未就绪时忽略 */
    }
  } catch {
    ElMessage.error('加载可视化模型失败')
  } finally {
    loading.value = false
  }
}

async function saveCanvas() {
  if (!props.domainId) return
  saving.value = true
  try {
    const layout = {
      nodes: nodes.value
        .filter((n) => n.type === 'entity')
        .map((n) => ({
          id: n.id,
          position: n.position,
          layer: (n.data?.layer as ModelLayer) || layerOfY(n.position.y),
          entityId: n.data?.entityId,
        })),
      updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
    }
    await api.put(`/governance/fusion/models/domains/${props.domainId}/canvas`, {
      layoutJson: JSON.stringify(layout),
    })
    // 拖拽后按 Y 刷新分层标签
    nodes.value = nodes.value.map((n) => {
      if (n.type !== 'entity') return n
      const layer = layerOfY(n.position.y)
      return { ...n, data: { ...n.data, layer } }
    })
    ElMessage.success('画布布局已保存')
  } catch {
    ElMessage.error('保存画布失败')
  } finally {
    saving.value = false
  }
}

function onNodeDragStop() {
  nodes.value = nodes.value.map((n) => {
    if (n.type !== 'entity') return n
    return { ...n, data: { ...n.data, layer: layerOfY(n.position.y) } }
  })
}

watch(
  () => [props.modelValue, props.domainId] as const,
  ([open, id]) => {
    if (open && id) {
      selectedId.value = null
      void loadModel()
    }
  },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    fullscreen
    destroy-on-close
    class="fusion-visual-dialog"
    :show-close="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="vm-header">
        <div class="vm-title">
          <span class="vm-title-main">可视化建模</span>
          <span class="vm-title-sub">{{ domainName || '业务域' }} · 分层拖拽 · 业务属性血缘</span>
        </div>
        <div class="vm-actions">
          <el-button type="primary" :loading="saving" @click="saveCanvas">保存布局</el-button>
          <el-button @click="loadModel">重新加载</el-button>
          <el-button @click="close">关闭</el-button>
        </div>
      </div>
    </template>

    <div v-loading="loading" class="vm-body">
      <aside class="vm-palette">
        <div class="pane-cap">组件库</div>
        <p class="pane-hint">拖拽到对应分层泳道，遵循数仓建设：源 → 过程 → 主题 → 专题</p>
        <div
          v-for="item in PALETTE"
          :key="item.layer"
          class="palette-item"
          draggable="true"
          @dragstart="onDragStart($event, item)"
        >
          <div class="palette-label">{{ item.label }}</div>
          <div class="palette-desc">{{ item.desc }}</div>
        </div>
        <el-divider />
        <div class="pane-cap">操作说明</div>
        <ul class="hint-list">
          <li>拖拽组件到泳道创建逻辑实体</li>
          <li>节点间连线建立实体关系（血缘）</li>
          <li>选中节点查看业务属性与上下游</li>
          <li>跨泳道拖动自动识别分层</li>
        </ul>
      </aside>

      <main class="vm-canvas" @dragover="onDragOver" @drop="onDrop">
        <VueFlow
          id="fusion-visual-modeler"
          v-model:nodes="nodes"
          v-model:edges="edges"
          :node-types="nodeTypes"
          :default-viewport="{ zoom: 0.85 }"
          :min-zoom="0.3"
          :max-zoom="1.6"
          fit-view-on-init
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
          @connect="onConnect"
          @node-drag-stop="onNodeDragStop"
        >
          <Background pattern-color="#dcdfe6" :gap="18" />
          <Controls />
          <MiniMap pannable zoomable />
        </VueFlow>
      </main>

      <aside class="vm-props">
        <div class="pane-cap">业务属性与血缘</div>
        <template v-if="selectedData">
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="名称">{{ selectedData.entityName }}</el-descriptions-item>
            <el-descriptions-item label="编码">{{ selectedData.entityCode }}</el-descriptions-item>
            <el-descriptions-item label="分层">{{ selectedData.layer }}</el-descriptions-item>
            <el-descriptions-item label="描述">{{ selectedData.description || '—' }}</el-descriptions-item>
          </el-descriptions>

          <div class="sec-title">业务属性（字段）</div>
          <el-table :data="selectedData.fields" size="small" max-height="200" empty-text="暂无字段">
            <el-table-column prop="fieldCode" label="编码" min-width="90" show-overflow-tooltip />
            <el-table-column prop="fieldName" label="名称" min-width="90" show-overflow-tooltip />
            <el-table-column prop="dataType" label="类型" width="80" />
            <el-table-column label="主键" width="56">
              <template #default="{ row }">{{ row.pkFlag ? '是' : '—' }}</template>
            </el-table-column>
          </el-table>

          <div class="sec-title">物理映射</div>
          <el-table :data="selectedData.physical" size="small" max-height="120" empty-text="未绑定物理表">
            <el-table-column prop="tableName" label="表名" min-width="120" show-overflow-tooltip />
            <el-table-column prop="physicalCode" label="编码" min-width="100" show-overflow-tooltip />
          </el-table>

          <div class="sec-title">数据血缘（实体关系）</div>
          <div class="lineage-block">
            <div class="lineage-cap">上游</div>
            <div v-if="!lineageUp.length" class="lineage-empty">无</div>
            <div v-for="item in lineageUp" :key="item.relation.id" class="lineage-row">
              <el-tag size="small" type="info">{{ item.peer.layer || '实体' }}</el-tag>
              <span class="lineage-name">{{ item.peer.name }}</span>
              <span class="muted">{{ relationTypeLabel(item.relation.relationType) }}</span>
              <el-button link type="danger" @click="removeRelation(item.relation.id)">删</el-button>
            </div>
          </div>
          <div class="lineage-block">
            <div class="lineage-cap">下游</div>
            <div v-if="!lineageDown.length" class="lineage-empty">无</div>
            <div v-for="item in lineageDown" :key="item.relation.id" class="lineage-row">
              <el-tag size="small" type="success">{{ item.peer.layer || '实体' }}</el-tag>
              <span class="lineage-name">{{ item.peer.name }}</span>
              <span class="muted">{{ relationTypeLabel(item.relation.relationType) }}</span>
              <el-button link type="danger" @click="removeRelation(item.relation.id)">删</el-button>
            </div>
          </div>

          <el-button type="danger" plain style="margin-top: 12px; width: 100%" @click="removeSelectedEntity">
            删除此实体
          </el-button>
        </template>
        <el-empty v-else description="选中画布中的逻辑实体" :image-size="64" />
      </aside>
    </div>

    <el-dialog v-model="createDlg" title="新建逻辑实体" width="420px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="分层">
          <el-select v-model="createForm.layer" style="width: 100%">
            <el-option v-for="l in LAYERS" :key="l.key" :label="l.label" :value="l.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="createForm.entityCode" placeholder="如 ENT_ENTERPRISE" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="createForm.entityName" placeholder="实体中文名" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDlg = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateEntity">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="relationDlg" title="建立实体关系（血缘）" width="460px" append-to-body>
      <el-form label-width="88px">
        <el-form-item label="编码" required>
          <el-input v-model="relationForm.relationCode" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="relationForm.relationName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="relationForm.relationType" style="width: 100%">
            <el-option label="一对一" value="ONE_TO_ONE" />
            <el-option label="一对多" value="ONE_TO_MANY" />
            <el-option label="多对多" value="MANY_TO_MANY" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="relationDlg = false">取消</el-button>
        <el-button type="primary" @click="confirmRelation">确定</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<style scoped>
.vm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding-right: 8px;
}
.vm-title-main {
  font-size: 16px;
  font-weight: 650;
  margin-right: 10px;
}
.vm-title-sub {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.vm-body {
  display: flex;
  height: calc(100vh - 72px);
  gap: 0;
  margin: -8px -16px -16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.vm-palette,
.vm-props {
  width: 260px;
  flex-shrink: 0;
  padding: 12px;
  overflow: auto;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-lighter);
}
.vm-props {
  border-right: none;
  border-left: 1px solid var(--el-border-color-lighter);
}
.vm-canvas {
  flex: 1;
  min-width: 0;
  position: relative;
  background: #f7f8fa;
}
.vm-canvas :deep(.vue-flow) {
  width: 100%;
  height: 100%;
}
.vm-canvas :deep(.fusion-lane-node) {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding: 8px 12px;
}
.pane-cap {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
}
.pane-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  margin: 0 0 12px;
}
.palette-item {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  cursor: grab;
  background: #fff;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.palette-item:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 1px 4px rgba(64, 158, 255, 0.15);
}
.palette-label {
  font-weight: 600;
  font-size: 13px;
}
.palette-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.hint-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.7;
}
.sec-title {
  margin: 14px 0 8px;
  font-weight: 600;
  font-size: 13px;
}
.lineage-block {
  margin-bottom: 10px;
}
.lineage-cap {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.lineage-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 4px 0;
}
.lineage-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lineage-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
.muted {
  color: var(--el-text-color-secondary);
}
</style>

<style>
.fusion-visual-dialog .el-dialog__body {
  padding-top: 0;
}
</style>
