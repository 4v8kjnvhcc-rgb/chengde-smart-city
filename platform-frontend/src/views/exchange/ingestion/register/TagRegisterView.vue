<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  type AssetTag,
  type DataColumn,
  type DataSource,
  type DataTable,
  type Project,
} from '../useIngestionHub'

defineProps<{ module: string }>()
const { loading, loadError, withLoad } = useIngestionLoading()

const projects = ref<Project[]>([])
const standardTree = ref<AssetTag[]>([])
const customTags = ref<AssetTag[]>([])
const sources = ref<DataSource[]>([])
const tables = ref<DataTable[]>([])
const columns = ref<DataColumn[]>([])

const assetKeyword = ref('')
const sourceId = ref<number | null>(null)
const selectedTableId = ref<number | null>(null)

/** 打开抽屉时从服务端加载的已保存挂标 */
const savedTableTagIds = ref<number[]>([])
const savedColumnTagMap = ref<Record<number, number[]>>({})
/** 本地草稿选择（未点完成登记前不落库） */
const draftTableTagIds = ref<number[]>([])
const draftColumnTagMap = ref<Record<number, number[]>>({})

const tableTagLabels = ref<Record<number, string[]>>({})
const finishing = ref(false)

const sourceNameMap = computed(() => {
  const m = new Map<number, string>()
  sources.value.forEach((s) => m.set(s.id, s.sourceName))
  return m
})

const filteredTables = computed(() => {
  let list = tables.value
  if (sourceId.value) list = list.filter((t) => t.sourceId === sourceId.value)
  const kw = assetKeyword.value.trim().toLowerCase()
  if (!kw) return list
  return list.filter((t) => {
    const src = sourceNameMap.value.get(t.sourceId) || ''
    return [t.tableCode, t.tableName, src].some((s) => String(s || '').toLowerCase().includes(kw))
  })
})

const selectedTable = computed(() => tables.value.find((t) => t.id === selectedTableId.value) || null)
const drawerVisible = computed({
  get: () => selectedTableId.value != null,
  set: (v: boolean) => {
    if (!v) {
      selectedTableId.value = null
      resetDraft()
    }
  },
})

type TreeOption = { value: number; label: string; disabled?: boolean; children?: TreeOption[]; keywords?: string }

const treeSelectData = computed((): TreeOption[] => {
  const std = standardTree.value.map((n) => ({
    value: n.id,
    label: `${n.stdCode || ''} ${n.tagName}`.trim(),
    disabled: true,
    keywords: [n.stdCode, n.tagName, n.tagDesc].filter(Boolean).join(' '),
    children: (n.children || []).map((c) => ({
      value: c.id,
      label: `${c.stdCode || ''} ${c.tagName}`.trim(),
      keywords: [c.stdCode, c.tagCode, c.tagName, c.tagDesc].filter(Boolean).join(' '),
    })),
  }))
  if (!customTags.value.length) return std
  return [
    ...std,
    {
      value: -1,
      label: '扩展标签',
      disabled: true,
      keywords: '扩展',
      children: customTags.value.map((c) => ({
        value: c.id,
        label: c.tagName,
        keywords: [c.tagCode, c.tagName, c.tagDesc, c.ruleExpr].filter(Boolean).join(' '),
      })),
    },
  ]
})

function resetDraft() {
  savedTableTagIds.value = []
  savedColumnTagMap.value = {}
  draftTableTagIds.value = []
  draftColumnTagMap.value = {}
}

function filterTagNode(query: string, data: TreeOption) {
  const q = (query || '').trim().toLowerCase()
  if (!q) return true
  const text = `${data.label || ''} ${data.keywords || ''}`.toLowerCase()
  return text.includes(q)
}

async function loadBase() {
  await withLoad(async () => {
    projects.value = (await ingestionApi.projects()).data || []
    syncActiveProject(projects.value)
    const [treeRes, srcRes] = await Promise.all([
      ingestionApi.tagTree(),
      activeProjectId.value
        ? ingestionApi.dataSources(activeProjectId.value)
        : Promise.resolve({ data: [] as DataSource[] }),
    ])
    standardTree.value = treeRes.data.standardTree || []
    customTags.value = treeRes.data.customTags || []
    sources.value = srcRes.data || []
    const sourceIds = new Set(sources.value.map((s) => s.id))
    if (sourceId.value && !sourceIds.has(sourceId.value)) sourceId.value = null
    if (!activeProjectId.value || !sources.value.length) {
      tables.value = []
    } else {
      const allTables = (await ingestionApi.tables()).data || []
      tables.value = allTables.filter((t) => sourceIds.has(t.sourceId))
    }
  })
  tableTagLabels.value = {}
  await loadTableTagLabels(filteredTables.value.slice(0, 30))
}

watch(activeProjectId, () => {
  sourceId.value = null
  selectedTableId.value = null
  resetDraft()
  void loadBase()
})

async function loadTableTagLabels(rows: DataTable[]) {
  const next = { ...tableTagLabels.value }
  const batchSize = 3
  for (let i = 0; i < rows.length; i += batchSize) {
    const batch = rows.slice(i, i + batchSize)
    await Promise.all(batch.map(async (t) => {
      const binds = (await ingestionApi.tagBindings('TABLE', t.id)).data
      next[t.id] = binds.map((b) => b.stdCode ? `${b.stdCode} ${b.tagName}` : (b.tagName || String(b.tagId)))
    }))
  }
  tableTagLabels.value = next
}

async function selectTable(row: DataTable) {
  selectedTableId.value = row.id
  const ctx = (await ingestionApi.tagMatchContext(row.id)).data
  columns.value = ctx.columns || []
  const tableIds = (ctx.tableTagIds || []).map(Number)
  savedTableTagIds.value = [...tableIds]
  draftTableTagIds.value = [...tableIds]

  const saved: Record<number, number[]> = {}
  const draft: Record<number, number[]> = {}
  for (const col of columns.value) {
    saved[col.id] = []
    draft[col.id] = []
  }
  for (const [k, v] of Object.entries(ctx.columnTagMap || {})) {
    const colId = Number(k)
    if (!Number.isFinite(colId)) continue
    const ids = (v || []).map(Number)
    saved[colId] = [...ids]
    draft[colId] = [...ids]
  }
  savedColumnTagMap.value = saved
  draftColumnTagMap.value = draft

  const nameById = new Map<number, string>()
  const walk = (nodes: AssetTag[]) => {
    for (const n of nodes) {
      nameById.set(n.id, n.stdCode ? `${n.stdCode} ${n.tagName}` : n.tagName)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(standardTree.value)
  customTags.value.forEach((t) => nameById.set(t.id, t.tagName))
  tableTagLabels.value = {
    ...tableTagLabels.value,
    [row.id]: tableIds.map((id) => nameById.get(id) || String(id)),
  }
}

function onTableTagsChange(ids: number[]) {
  draftTableTagIds.value = ids
}

function onColumnTagsChange(colId: number, ids: number[]) {
  draftColumnTagMap.value = { ...draftColumnTagMap.value, [colId]: ids }
}

async function persistDraftBindings() {
  if (!selectedTableId.value) return [] as number[]
  const tableId = selectedTableId.value
  const affected = new Set<number>()

  const tableAdd = draftTableTagIds.value.filter((id) => !savedTableTagIds.value.includes(id))
  const tableRemove = savedTableTagIds.value.filter((id) => !draftTableTagIds.value.includes(id))
  for (const tagId of tableAdd) {
    await ingestionApi.bindTag({ tagId, assetType: 'TABLE', assetId: tableId })
    affected.add(tagId)
  }
  for (const tagId of tableRemove) {
    await ingestionApi.unbindTag({ tagId, assetType: 'TABLE', assetId: tableId })
    affected.add(tagId)
  }

  const colIds = new Set([
    ...Object.keys(draftColumnTagMap.value),
    ...Object.keys(savedColumnTagMap.value),
  ].map(Number))
  for (const colId of colIds) {
    const next = draftColumnTagMap.value[colId] || []
    const prev = savedColumnTagMap.value[colId] || []
    const add = next.filter((id) => !prev.includes(id))
    const remove = prev.filter((id) => !next.includes(id))
    for (const tagId of add) {
      await ingestionApi.bindTag({ tagId, assetType: 'COLUMN', assetId: colId })
      affected.add(tagId)
    }
    for (const tagId of remove) {
      await ingestionApi.unbindTag({ tagId, assetType: 'COLUMN', assetId: colId })
      affected.add(tagId)
    }
  }

  draftTableTagIds.value.forEach((id) => affected.add(id))
  Object.values(draftColumnTagMap.value).forEach((list) => list.forEach((id) => affected.add(id)))
  return [...affected]
}

async function applyRulesForTags(tagIds: number[]) {
  let ok = 0
  let skip = 0
  for (const tagId of tagIds) {
    try {
      const sug = (await ingestionApi.suggestTagRule(tagId)).data
      const rule = String(sug.suggestedRule || '').trim()
      if (!rule) {
        skip += 1
        continue
      }
      await ingestionApi.applySuggestedTagRule(tagId, { ruleExpr: rule })
      ok += 1
    } catch {
      skip += 1
    }
  }
  return { ok, skip }
}

async function finishRegister() {
  if (!selectedTableId.value) {
    ElMessage.warning('请先打开一张表进行标签匹配，再点完成登记')
    return
  }
  finishing.value = true
  try {
    const tagIds = await persistDraftBindings()
    savedTableTagIds.value = [...draftTableTagIds.value]
    savedColumnTagMap.value = Object.fromEntries(
      Object.entries(draftColumnTagMap.value).map(([k, v]) => [Number(k), [...v]]),
    )

    let writeRules = false
    try {
      await ElMessageBox.confirm(
        tagIds.length
          ? `挂标已保存。是否根据本次标签（${tagIds.length} 个）生成识别规则并写入标签库？`
          : '挂标已保存（无标签变更）。是否仍结束本次登记？',
        '完成登记',
        {
          confirmButtonText: tagIds.length ? '是，写入规则' : '完成',
          cancelButtonText: tagIds.length ? '否，不写入' : '完成',
          distinguishCancelAndClose: true,
          type: 'info',
        },
      )
      writeRules = tagIds.length > 0
    } catch (action) {
      if (action === 'cancel' || action === 'close') {
        ElMessage.success('挂标已保存（未写入识别规则）')
        selectedTableId.value = null
        resetDraft()
        await loadBase()
        return
      }
      ElMessage.success('挂标已保存（未写入识别规则）')
      selectedTableId.value = null
      resetDraft()
      await loadBase()
      return
    }

    if (writeRules) {
      const { ok, skip } = await applyRulesForTags(tagIds)
      ElMessage.success(skip ? `登记完成：已写入 ${ok} 条规则，${skip} 个标签无有效规则可写` : `登记完成：已写入 ${ok} 条识别规则`)
    } else {
      ElMessage.success('登记已完成')
    }
    selectedTableId.value = null
    resetDraft()
    await loadBase()
  } catch {
    ElMessage.error('保存挂标失败')
  } finally {
    finishing.value = false
  }
}

watch(filteredTables, (list) => {
  void loadTableTagLabels(list.slice(0, 30))
})

onMounted(loadBase)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据资产标签登记">
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
        <el-form-item label="数据源" class="portal-field-xl">
          <el-select v-model="sourceId" clearable filterable placeholder="全部数据源" :disabled="!activeProjectId">
            <el-option v-for="s in sources" :key="s.id" :label="s.sourceName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检索" class="portal-field-xl">
          <el-input v-model="assetKeyword" clearable placeholder="表名/编码/数据源模糊搜索" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :loading="finishing" @click="finishRegister">完成登记</el-button>
        </el-form-item>
      </el-form>

      <el-table
        :data="filteredTables"
        stripe
        highlight-current-row
        :current-row-key="selectedTableId ?? undefined"
        row-key="id"
        @row-click="selectTable"
      >
        <el-table-column label="数据源" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ sourceNameMap.get(row.sourceId) || row.sourceId }}</template>
        </el-table-column>
        <el-table-column prop="tableCode" label="表编码" width="140" show-overflow-tooltip />
        <el-table-column prop="tableName" label="表名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="columnCount" label="字段数" width="80" />
        <el-table-column label="已挂标签" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="(tableTagLabels[row.id] || []).length">
              <el-tag
                v-for="(name, idx) in tableTagLabels[row.id]"
                :key="idx"
                size="small"
                style="margin: 0 4px 4px 0"
              >{{ name }}</el-tag>
            </template>
            <span v-else class="muted">未挂标</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="selectTable(row)">匹配标签</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-drawer
      v-model="drawerVisible"
      :title="selectedTable ? `匹配标签 · ${selectedTable.tableName || selectedTable.tableCode}` : '匹配标签'"
      size="560px"
      destroy-on-close
    >
      <template v-if="selectedTable">
        <div class="bind-block">
          <div class="bind-title">表级标签</div>
          <el-tree-select
            :model-value="draftTableTagIds"
            multiple
            filterable
            clearable
            show-checkbox
            check-strictly
            default-expand-all
            :data="treeSelectData"
            :filter-node-method="filterTagNode"
            :render-after-expand="false"
            placeholder="编码/名称模糊搜索后勾选二级类目"
            style="width: 100%"
            @update:model-value="onTableTagsChange"
          />
        </div>
        <div class="bind-block">
          <div class="bind-title">字段（数据项）标签</div>
          <el-table :data="columns" stripe size="small" max-height="420">
            <el-table-column prop="columnCode" label="编码" width="110" show-overflow-tooltip />
            <el-table-column prop="columnName" label="名称" width="110" show-overflow-tooltip />
            <el-table-column label="匹配标签" min-width="220">
              <template #default="{ row }">
                <el-tree-select
                  :model-value="draftColumnTagMap[row.id] || []"
                  multiple
                  filterable
                  clearable
                  show-checkbox
                  check-strictly
                  :data="treeSelectData"
                  :filter-node-method="filterTagNode"
                  :render-after-expand="false"
                  placeholder="模糊搜索标签"
                  style="width: 100%"
                  @update:model-value="(ids: number[]) => onColumnTagsChange(row.id, ids)"
                />
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div class="drawer-actions">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="finishing" @click="finishRegister">完成登记</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.muted { color: #c0c4cc; font-size: 12px; }
.bind-block { margin-bottom: 20px; }
.bind-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; }
.drawer-actions {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
