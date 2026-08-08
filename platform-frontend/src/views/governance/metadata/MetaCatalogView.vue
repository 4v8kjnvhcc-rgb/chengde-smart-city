<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { LAYER_OPTIONS } from './meta-labels'

interface CategoryNode {
  id: number
  label: string
  categoryCode?: string
  layerCode?: string
  children?: CategoryNode[]
}

interface DataSourceRow {
  id: number
  sourceName: string
  categoryId?: number
  adapterType?: string
  dbName?: string
  categoryName?: string
}

interface CatalogTreeNode {
  code: string
  label: string
  nodeType: 'CATEGORY' | 'SOURCE' | 'TABLE'
  categoryId?: number
  layerCode?: string
  metaDataSourceId?: number
  tableName?: string
  sourceName?: string
  adapterType?: string
  dbName?: string
  children?: CatalogTreeNode[]
  isLeaf?: boolean
}

interface ColumnField {
  code: string
  name: string
  type: string
  length?: number
  required?: boolean
  primaryKey?: boolean
  hint?: string
}

const catalogKind = ref<'source' | 'asset'>('source')
const keyword = ref('')
const treeLoading = ref(false)

const treeData = ref<CatalogTreeNode[]>([])
const selectedNode = ref<CatalogTreeNode | null>(null)

const columnsLoading = ref(false)
const columnFields = ref<ColumnField[]>([])
const tableMeta = ref<{ rowCount?: number; primaryKeys?: string[] }>({})

const kpi = ref({
  categoryCount: 0,
  sourceCount: 0,
  tableCount: 0,
})

const catalogKindHint = computed(() =>
  catalogKind.value === 'source'
    ? '数据源目录展示数据源管理中 ODS 层（原始库）的数据源及其表结构，左侧为分类→数据源→表，右侧为字段明细。'
    : '数据资产目录展示数据源管理中 DWD / DWS / ADS 层（治理库、主题库、基础库）的数据源及其表结构。',
)

const layerFilter = computed(() =>
  catalogKind.value === 'source' ? ['ODS'] : ['DWD', 'DWS', 'ADS'],
)

const inventoryBar = computed(() => {
  const layers = layerFilter.value
    .map(l => {
      const opt = LAYER_OPTIONS.find(o => o.value === l)
      return opt?.label || l
    })
    .join(' · ')
  return `分类 ${kpi.value.categoryCount} ｜ 数据源 ${kpi.value.sourceCount} ｜ 数据表 ${kpi.value.tableCount} ｜ ${layers}`
})

const selectedTitle = computed(() => {
  const n = selectedNode.value
  if (!n) return '请选择左侧数据表'
  if (n.nodeType === 'TABLE') return n.tableName || n.label
  return n.label
})

const selectedSubtitle = computed(() => {
  const n = selectedNode.value
  if (!n || n.nodeType !== 'TABLE') return ''
  const parts = [n.sourceName, n.dbName, n.layerCode].filter(Boolean)
  return parts.join(' · ')
})

function layerLabel(layer?: string) {
  if (!layer) return '—'
  const opt = LAYER_OPTIONS.find(o => o.value === layer)
  return opt?.label || layer
}

function filterRootsByLayer(nodes: CategoryNode[], layers: string[]): CategoryNode[] {
  return nodes.filter(n => n.layerCode && layers.includes(n.layerCode))
}

async function loadSourcesForCategory(categoryId: number): Promise<DataSourceRow[]> {
  const rows = (await api.get('/governance/platform/metadata/data-sources', {
    params: { categoryId, keyword: keyword.value.trim() || undefined },
  })).data || []
  return rows as DataSourceRow[]
}

async function enrichCategoryNode(node: CategoryNode, layerCode: string): Promise<CatalogTreeNode> {
  const childCategories = node.children || []
  const categoryChildren = await Promise.all(
    childCategories.map(c => enrichCategoryNode(c, c.layerCode || layerCode)),
  )
  let sources: DataSourceRow[] = []
  try {
    sources = await loadSourcesForCategory(node.id)
  } catch {
    sources = []
  }
  const sourceChildren: CatalogTreeNode[] = sources.map(s => ({
    code: `mds:${s.id}`,
    label: s.sourceName,
    nodeType: 'SOURCE',
    metaDataSourceId: s.id,
    categoryId: node.id,
    layerCode,
    sourceName: s.sourceName,
    adapterType: s.adapterType,
    dbName: s.dbName,
    isLeaf: false,
  }))
  return {
    code: `cat:${node.id}`,
    label: node.label,
    nodeType: 'CATEGORY',
    categoryId: node.id,
    layerCode,
    children: [...categoryChildren, ...sourceChildren],
    isLeaf: false,
  }
}

async function countTablesForSources(sources: CatalogTreeNode[]) {
  let count = 0
  const batch = sources.slice(0, 20)
  for (let i = 0; i < batch.length; i += 3) {
    const chunk = batch.slice(i, i + 3)
    const results = await Promise.all(chunk.map(async (s) => {
      if (!s.metaDataSourceId) return 0
      try {
        const tables = (await api.get(
          `/governance/platform/metadata/collect/meta-data-sources/${s.metaDataSourceId}/tables`,
        )).data || []
        return tables.length
      } catch {
        return 0
      }
    }))
    count += results.reduce((a, b) => a + b, 0)
  }
  return count
}

function collectSources(nodes: CatalogTreeNode[]): CatalogTreeNode[] {
  const out: CatalogTreeNode[] = []
  for (const n of nodes) {
    if (n.nodeType === 'SOURCE') out.push(n)
    if (n.children?.length) out.push(...collectSources(n.children))
  }
  return out
}

function countCategories(nodes: CatalogTreeNode[]): number {
  let n = 0
  for (const node of nodes) {
    if (node.nodeType === 'CATEGORY') n++
    if (node.children?.length) n += countCategories(node.children)
  }
  return n
}

async function loadCatalogTree() {
  treeLoading.value = true
  selectedNode.value = null
  columnFields.value = []
  try {
    const res = await api.get('/governance/platform/metadata/source-categories/tree', {
      params: { keyword: keyword.value.trim() || undefined },
    })
    const roots = filterRootsByLayer((res.data || []) as CategoryNode[], layerFilter.value)
    const built = await Promise.all(roots.map(r => enrichCategoryNode(r, r.layerCode || '')))
    treeData.value = built
    const sources = collectSources(built)
    kpi.value = {
      categoryCount: countCategories(built),
      sourceCount: sources.length,
      tableCount: sources.length ? await countTablesForSources(sources) : 0,
    }
  } catch {
    ElMessage.error('加载目录树失败')
    treeData.value = []
  } finally {
    treeLoading.value = false
  }
}

async function onTreeLazyLoad(node: { data: CatalogTreeNode }, resolve: (data: CatalogTreeNode[]) => void) {
  await loadTreeChildren(node.data, resolve)
}

async function loadTreeChildren(node: CatalogTreeNode, resolve: (data: CatalogTreeNode[]) => void) {
  if (node.nodeType !== 'SOURCE' || !node.metaDataSourceId) {
    resolve(node.children || [])
    return
  }
  try {
    const tables = (await api.get(
      `/governance/platform/metadata/collect/meta-data-sources/${node.metaDataSourceId}/tables`,
    )).data || []
    const kw = keyword.value.trim().toLowerCase()
    const rows = (tables as Array<{ tableName?: string; sourceTable?: string }>)
      .map(t => String(t.tableName || t.sourceTable || ''))
      .filter(Boolean)
      .filter(name => !kw || name.toLowerCase().includes(kw))
      .map(name => ({
        code: `tbl:${node.metaDataSourceId}:${name}`,
        label: name,
        nodeType: 'TABLE' as const,
        metaDataSourceId: node.metaDataSourceId,
        tableName: name,
        sourceName: node.sourceName,
        adapterType: node.adapterType,
        dbName: node.dbName,
        layerCode: node.layerCode,
        isLeaf: true,
      }))
    resolve(rows)
  } catch {
    ElMessage.warning(`数据源「${node.label}」探表失败，请检查连接配置`)
    resolve([])
  }
}

async function loadTableColumns(node: CatalogTreeNode) {
  if (node.nodeType !== 'TABLE' || !node.metaDataSourceId || !node.tableName) return
  columnsLoading.value = true
  columnFields.value = []
  tableMeta.value = {}
  try {
    const data = (await api.get(
      `/governance/platform/metadata/models/meta-data-sources/${node.metaDataSourceId}/table-columns`,
      { params: { tableName: node.tableName } },
    )).data
    const raw = (data?.fields || []) as Array<Record<string, unknown>>
    columnFields.value = raw.map(f => ({
      code: String(f.code || ''),
      name: String(f.name || f.code || ''),
      type: String(f.type || 'VARCHAR'),
      length: f.length == null ? undefined : Number(f.length),
      required: Boolean(f.required),
      primaryKey: Boolean(f.primaryKey),
      hint: f.hint != null ? String(f.hint) : '',
    })).filter(f => f.code)
    tableMeta.value = {
      rowCount: data?.rowCount != null ? Number(data.rowCount) : undefined,
      primaryKeys: Array.isArray(data?.primaryKeys) ? data.primaryKeys.map(String) : [],
    }
  } catch {
    ElMessage.error('加载字段失败')
  } finally {
    columnsLoading.value = false
  }
}

function onTreeClick(node: CatalogTreeNode) {
  selectedNode.value = node
  if (node.nodeType === 'TABLE') {
    loadTableColumns(node)
  } else {
    columnFields.value = []
  }
}

function resetFilter() {
  keyword.value = ''
  loadCatalogTree()
}

watch(catalogKind, () => loadCatalogTree())

onMounted(() => loadCatalogTree())
</script>

<template>
  <div class="mcatalog-page">
    <div class="mcatalog-kpi">
      <div class="mcatalog-kpi__card">
        <span>{{ catalogKind === 'source' ? 'ODS 数据源' : '资产数据源' }}</span>
        <b>{{ kpi.sourceCount }}</b>
      </div>
      <div class="mcatalog-kpi__card tone-info">
        <span>分类节点</span>
        <b>{{ kpi.categoryCount }}</b>
      </div>
      <div class="mcatalog-kpi__card tone-ok">
        <span>数据表</span>
        <b>{{ kpi.tableCount }}</b>
      </div>
      <div class="mcatalog-kpi__card tone-layer">
        <span>当前范围</span>
        <b class="mcatalog-kpi__layer">{{ catalogKind === 'source' ? 'ODS' : 'DWD·DWS·ADS' }}</b>
      </div>
    </div>

    <PageCard title="元数据目录">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="目录" class="portal-field-md">
          <el-radio-group v-model="catalogKind" size="default">
            <el-radio-button value="source">数据源目录</el-radio-button>
            <el-radio-button value="asset">数据资产目录</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关键字" class="portal-field-lg">
          <el-input
            v-model="keyword"
            clearable
            placeholder="数据源名称 / 表名"
            @keyup.enter="loadCatalogTree"
          />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :loading="treeLoading" @click="loadCatalogTree">搜索</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" :title="catalogKindHint" class="mcatalog-hint" />
      <el-alert type="success" :closable="false" :title="inventoryBar" class="mcatalog-stats" />

      <div class="mcatalog-workspace">
        <aside class="mcatalog-tree-panel" v-loading="treeLoading">
          <div class="mcatalog-panel-head">
            <span class="mcatalog-panel-head__title">目录检索</span>
            <span class="mcatalog-panel-head__sub">分类 · 数据源 · 表</span>
          </div>
          <el-tree
            v-if="treeData.length"
            :data="treeData"
            node-key="code"
            highlight-current
            :props="{ label: 'label', children: 'children', isLeaf: 'isLeaf' }"
            lazy
            :load="onTreeLazyLoad"
            class="mcatalog-tree"
            @node-click="onTreeClick"
          >
            <template #default="{ data }">
              <span class="mcatalog-tree-node">
                <span
                  class="mcatalog-tree-node__icon"
                  :class="{
                    'is-cat': data.nodeType === 'CATEGORY',
                    'is-src': data.nodeType === 'SOURCE',
                    'is-tbl': data.nodeType === 'TABLE',
                  }"
                />
                <span class="mcatalog-tree-node__label">{{ data.label }}</span>
                <el-tag v-if="data.layerCode && data.nodeType === 'CATEGORY'" size="small" type="info" class="mcatalog-tree-node__tag">
                  {{ layerLabel(data.layerCode) }}
                </el-tag>
              </span>
            </template>
          </el-tree>
          <el-empty v-else description="当前分层下暂无数据源，请先在「数据源管理」中配置" :image-size="72" />
        </aside>

        <section class="mcatalog-field-panel">
          <div class="mcatalog-panel-head">
            <div>
              <span class="mcatalog-panel-head__title">{{ selectedTitle }}</span>
              <div v-if="selectedSubtitle" class="mcatalog-panel-head__sub">{{ selectedSubtitle }}</div>
            </div>
            <div v-if="selectedNode?.nodeType === 'TABLE'" class="mcatalog-panel-head__meta">
              <el-tag v-if="selectedNode.layerCode" size="small">{{ layerLabel(selectedNode.layerCode) }}</el-tag>
              <el-tag v-if="tableMeta.rowCount != null" size="small" type="info">约 {{ tableMeta.rowCount }} 行</el-tag>
              <el-tag v-if="tableMeta.primaryKeys?.length" size="small" type="warning">
                主键 {{ tableMeta.primaryKeys.join(', ') }}
              </el-tag>
            </div>
          </div>

          <div v-if="selectedNode?.nodeType === 'TABLE'" v-loading="columnsLoading" class="mcatalog-field-body">
            <el-table :data="columnFields" stripe class="mcatalog-field-table" empty-text="暂无字段">
              <el-table-column prop="code" label="字段编码" min-width="140" show-overflow-tooltip />
              <el-table-column prop="name" label="字段名称" min-width="140" show-overflow-tooltip />
              <el-table-column prop="type" label="类型" width="120" />
              <el-table-column label="长度" width="80" align="center">
                <template #default="{ row }">{{ row.length ?? '—' }}</template>
              </el-table-column>
              <el-table-column label="必填" width="72" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.required ? 'danger' : 'info'" size="small">{{ row.required ? '是' : '否' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="主键" width="72" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.primaryKey" type="warning" size="small">是</el-tag>
                  <span v-else>—</span>
                </template>
              </el-table-column>
              <el-table-column prop="hint" label="说明" min-width="160" show-overflow-tooltip />
            </el-table>
          </div>

          <div v-else class="mcatalog-field-empty">
            <el-empty description="在左侧展开数据源并选择数据表，此处展示字段结构" :image-size="96" />
          </div>
        </section>
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.mcatalog-page {
  --mc-border: #e8edf5;
  --mc-bg: #f7f9fc;
}

.mcatalog-kpi {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 14px;
}

.mcatalog-kpi__card {
  border: 1px solid var(--mc-border);
  background: linear-gradient(180deg, #fff 0%, #fafbfd 100%);
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 2px 8px rgba(15, 40, 80, 0.05);
}

.mcatalog-kpi__card span {
  display: block;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}

.mcatalog-kpi__card b {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.1;
}

.mcatalog-kpi__layer {
  font-size: 18px !important;
  letter-spacing: 0.5px;
}

.mcatalog-kpi__card.tone-warn b { color: #ef6c00; }
.mcatalog-kpi__card.tone-info b { color: #1677ff; }
.mcatalog-kpi__card.tone-ok b { color: #2e7d32; }
.mcatalog-kpi__card.tone-layer b { color: #5c6bc0; }

.mcatalog-hint { margin-bottom: 10px; }
.mcatalog-stats { margin-bottom: 16px; }

.mcatalog-workspace {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  min-height: 560px;
}

.mcatalog-tree-panel,
.mcatalog-field-panel {
  border: 1px solid var(--mc-border);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 4px 16px rgba(15, 40, 80, 0.06);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.mcatalog-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--mc-border);
  background: linear-gradient(180deg, #f8fafc 0%, #fff 100%);
}

.mcatalog-panel-head__title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.mcatalog-panel-head__sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.mcatalog-panel-head__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}

.mcatalog-tree {
  flex: 1;
  padding: 12px 10px 16px;
  overflow: auto;
  max-height: 620px;
}

.mcatalog-tree-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
  padding-right: 8px;
}

.mcatalog-tree-node__icon {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #94a3b8;
}

.mcatalog-tree-node__icon.is-cat { background: #1677ff; }
.mcatalog-tree-node__icon.is-src { background: #52c41a; }
.mcatalog-tree-node__icon.is-tbl { background: #fa8c16; }

.mcatalog-tree-node__label {
  font-size: 13px;
  color: #303133;
}

.mcatalog-tree-node__tag {
  transform: scale(0.92);
}

.mcatalog-field-body {
  flex: 1;
  padding: 12px 16px 16px;
}

.mcatalog-field-table {
  border-radius: 10px;
  overflow: hidden;
}

.mcatalog-field-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
  background: var(--mc-bg);
}

:deep(.mcatalog-tree .el-tree-node__content) {
  height: 34px;
  border-radius: 8px;
}

:deep(.mcatalog-tree .el-tree-node.is-current > .el-tree-node__content) {
  background: #eef4ff;
}

@media (max-width: 1100px) {
  .mcatalog-kpi { grid-template-columns: repeat(2, 1fr); }
  .mcatalog-workspace { grid-template-columns: 1fr; }
}
</style>
