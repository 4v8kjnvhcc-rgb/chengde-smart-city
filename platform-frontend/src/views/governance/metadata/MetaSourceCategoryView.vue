<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'

interface CategoryNode {
  id: number
  label: string
  categoryCode?: string
  layerCode?: string
  systemFlag?: number
  children?: CategoryNode[]
}

interface CategoryRow {
  id: number
  categoryCode: string
  categoryName: string
  parentId: number
  layerCode?: string
  description?: string
  sortOrder?: number
  systemFlag?: number
  status: string
}

const treeKeyword = ref('')
const tableKeyword = ref('')
const treeLoading = ref(false)
const tableLoading = ref(false)
const treeData = ref<CategoryNode[]>([])
const rows = ref<CategoryRow[]>([])
const selectedNode = ref<CategoryNode | null>(null)
const treeRef = ref<{ setCurrentKey: (key: number) => void } | null>(null)

const dialogVisible = ref(false)
const editing = ref<CategoryRow | null>(null)
const form = reactive({
  categoryName: '',
  categoryCode: '',
  description: '',
})

const {
  page,
  pageSize,
  paged: pagedRows,
  total,
  resetPage,
} = useClientPager(rows)

const selectedLabel = computed(() => selectedNode.value?.label || '—')

const treeWithRoot = computed(() => [{
  id: 0,
  label: '数据分类',
  children: treeData.value,
}])

async function loadTree() {
  treeLoading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/source-categories/tree', {
      params: { keyword: treeKeyword.value.trim() || undefined },
    })
    treeData.value = res.data || []
    await nextTick()
    if (!selectedNode.value && treeData.value.length) {
      selectNode(treeData.value[0])
    } else if (selectedNode.value) {
      const still = findNode(treeData.value, selectedNode.value.id)
      if (still) {
        selectNode(still)
      } else if (treeData.value.length) {
        selectNode(treeData.value[0])
      } else {
        selectedNode.value = null
        rows.value = []
      }
    }
  } catch {
    ElMessage.error('加载分类树失败')
  } finally {
    treeLoading.value = false
  }
}

function findNode(nodes: CategoryNode[], id: number): CategoryNode | null {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children?.length) {
      const hit = findNode(n.children, id)
      if (hit) return hit
    }
  }
  return null
}

async function loadChildren() {
  if (!selectedNode.value) {
    rows.value = []
    return
  }
  tableLoading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/source-categories', {
      params: {
        parentId: selectedNode.value.id,
        keyword: tableKeyword.value.trim() || undefined,
      },
    })
    rows.value = res.data || []
    resetPage()
  } catch {
    ElMessage.error('加载子分类失败')
  } finally {
    tableLoading.value = false
  }
}

function selectNode(node: CategoryNode) {
  selectedNode.value = node
  treeRef.value?.setCurrentKey(node.id)
  loadChildren()
}

function onTreeClick(node: CategoryNode) {
  if (node.id === 0) return
  selectNode(node)
}

function onSearchTree() {
  loadTree()
}

function onSearchTable() {
  loadChildren()
}

function resetTableFilter() {
  tableKeyword.value = ''
  loadChildren()
}

function openCreate() {
  if (!selectedNode.value) {
    ElMessage.warning('请先在左侧选择上级分类')
    return
  }
  editing.value = null
  form.categoryName = ''
  form.categoryCode = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(row: CategoryRow) {
  editing.value = row
  form.categoryName = row.categoryName
  form.categoryCode = row.categoryCode
  form.description = row.description || ''
  dialogVisible.value = true
}

async function saveCategory() {
  if (!form.categoryName.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  try {
    if (editing.value) {
      await api.put(`/governance/platform/metadata/source-categories/${editing.value.id}`, {
        categoryName: form.categoryName.trim(),
        description: form.description.trim() || undefined,
      })
      ElMessage.success('已保存')
    } else {
      await api.post('/governance/platform/metadata/source-categories', {
        parentId: selectedNode.value?.id,
        categoryName: form.categoryName.trim(),
        categoryCode: form.categoryCode.trim() || undefined,
        description: form.description.trim() || undefined,
        layerCode: selectedNode.value?.layerCode,
      })
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    await loadTree()
    await loadChildren()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  }
}

function openEditSelectedNode() {
  if (!selectedNode.value) return
  openEdit({
    id: selectedNode.value.id,
    categoryCode: selectedNode.value.categoryCode || '',
    categoryName: selectedNode.value.label,
    parentId: 0,
    systemFlag: selectedNode.value.systemFlag,
    status: 'ACTIVE',
  })
}

async function removeSelectedNode() {
  if (!selectedNode.value) return
  await removeRow({
    id: selectedNode.value.id,
    categoryCode: selectedNode.value.categoryCode || '',
    categoryName: selectedNode.value.label,
    parentId: 0,
    systemFlag: selectedNode.value.systemFlag,
    status: 'ACTIVE',
  })
}

async function removeRow(row: CategoryRow) {
  if (row.systemFlag === 1) {
    ElMessage.warning('系统内置分类不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除分类「${row.categoryName}」？`, '删除确认', { type: 'warning' })
    await api.delete(`/governance/platform/metadata/source-categories/${row.id}`)
    ElMessage.success('已删除')
    await loadTree()
    await loadChildren()
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

watch(treeKeyword, () => {
  /* 由查询按钮触发，避免输入时频繁请求 */
})

onMounted(async () => {
  await loadTree()
})
</script>

<template>
  <PageCard title="数据源分类">
    <div class="msc-layout">
      <aside class="msc-tree-pane" v-loading="treeLoading">
        <div class="msc-pane-title">数据源分类</div>
        <el-input
          v-model="treeKeyword"
          clearable
          placeholder="请输入名称"
          class="msc-search"
          @keyup.enter="onSearchTree"
        />
        <el-tree
          ref="treeRef"
          class="msc-tree"
          node-key="id"
          highlight-current
          default-expand-all
          :data="treeWithRoot"
          :props="{ label: 'label', children: 'children' }"
          @node-click="onTreeClick"
        />
      </aside>

      <section class="msc-table-pane" v-loading="tableLoading">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="名称" class="portal-field-lg">
            <el-input v-model="tableKeyword" clearable placeholder="请输入名称" @keyup.enter="onSearchTable" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="onSearchTable">查询</el-button>
            <el-button @click="resetTableFilter">重置</el-button>
            <el-button type="primary" :disabled="!selectedNode" @click="openCreate">+ 新增</el-button>
          </el-form-item>
        </el-form>

        <div v-if="selectedNode" class="msc-context">
          <span>当前分类：{{ selectedLabel }}</span>
          <el-button link type="primary" @click="openEditSelectedNode">编辑</el-button>
          <el-button
            v-if="selectedNode.systemFlag !== 1"
            link
            type="danger"
            @click="removeSelectedNode"
          >
            删除
          </el-button>
        </div>

        <el-table :data="pagedRows" stripe size="small" empty-text="暂无数据">
          <el-table-column type="index" label="序号" width="70" :index="(i: number) => (page - 1) * pageSize + i + 1" />
          <el-table-column prop="categoryName" label="名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="categoryCode" label="编码" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                link
                type="danger"
                :disabled="row.systemFlag === 1"
                @click="removeRow(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination v-if="total" v-model:page="page" v-model:page-size="pageSize" :total="total" />
      </section>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑分类' : '新增分类'"
      width="480px"
      destroy-on-close
    >
      <el-form label-width="88px">
        <el-form-item v-if="!editing" label="上级分类">
          <el-input :model-value="selectedLabel" disabled />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.categoryName" placeholder="分类名称" maxlength="128" />
        </el-form-item>
        <el-form-item v-if="!editing" label="编码">
          <el-input v-model="form.categoryCode" placeholder="留空则自动生成" maxlength="64" />
        </el-form-item>
        <el-form-item v-else label="编码">
          <el-input v-model="form.categoryCode" disabled />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="512" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">确定</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.msc-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 16px;
  min-height: 520px;
}

.msc-tree-pane,
.msc-table-pane {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.msc-pane-title {
  font-weight: 600;
  margin-bottom: 10px;
}

.msc-search {
  margin-bottom: 10px;
}

.msc-tree {
  max-height: 460px;
  overflow: auto;
}

.msc-context {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

@media (max-width: 960px) {
  .msc-layout {
    grid-template-columns: 1fr;
  }
}
</style>
