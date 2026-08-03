<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

const props = withDefaults(defineProps<{ catalogOrigin?: 'INGEST' | 'GOVERNANCE' }>(), {
  catalogOrigin: 'GOVERNANCE',
})

interface CategoryNode {
  id: number
  categoryCode: string
  categoryName: string
  parentId?: number
  categoryPath?: string
  children?: CategoryNode[]
}

interface CatalogRes {
  id: number
  resourceCode: string
  resourceName: string
  resourceType: string
  categoryId?: number
  categoryPath?: string
  publishStatus: string
  approvalStatus: string
  sourcePathType?: string
  metadataEntryCode?: string
  providerOrg?: string
}

const treeData = ref<CategoryNode[]>([])
const selectedCategoryId = ref<number | null>(null)
const boundRows = ref<CatalogRes[]>([])
const unboundRows = ref<CatalogRes[]>([])
const selectedBound = ref<CatalogRes[]>([])
const selectedUnbound = ref<CatalogRes[]>([])
const loading = ref(false)
const catDialog = ref(false)
const catForm = reactive({
  categoryName: '',
  categoryCode: '',
  parentId: undefined as number | undefined,
})

const flatCategories = computed(() => {
  const out: { id: number; label: string }[] = []
  const walk = (nodes: CategoryNode[], prefix = '') => {
    for (const n of nodes) {
      const label = prefix ? `${prefix} / ${n.categoryName}` : n.categoryName
      out.push({ id: n.id, label })
      if (n.children?.length) walk(n.children, label)
    }
  }
  walk(treeData.value)
  return out
})

async function loadTree() {
  const res = await api.get('/governance/catalog/categories/tree', {
    params: { catalogOrigin: props.catalogOrigin },
  })
  treeData.value = res.data || []
}

async function loadBound() {
  if (selectedCategoryId.value == null) {
    boundRows.value = []
    return
  }
  const res = await api.get('/governance/catalog/resources-mgmt', {
    params: { categoryId: selectedCategoryId.value, catalogOrigin: props.catalogOrigin },
  })
  boundRows.value = res.data || []
}

async function loadUnbound() {
  const res = await api.get('/governance/catalog/resources-mgmt', {
    params: { unboundOnly: true, catalogOrigin: props.catalogOrigin },
  })
  unboundRows.value = res.data || []
}

async function refreshLists() {
  loading.value = true
  try {
    await Promise.all([loadBound(), loadUnbound()])
  } finally {
    loading.value = false
  }
}

function onTreeClick(data: CategoryNode) {
  selectedCategoryId.value = data.id
  void loadBound()
}

function openCreateCategory(parentId?: number) {
  catForm.categoryName = ''
  catForm.categoryCode = ''
  catForm.parentId = parentId
  catDialog.value = true
}

async function saveCategory() {
  if (!catForm.categoryName.trim()) {
    ElMessage.warning('请填写分类名称')
    return
  }
  await api.post('/governance/catalog/categories', {
    categoryName: catForm.categoryName,
    categoryCode: catForm.categoryCode || undefined,
    parentId: catForm.parentId || 0,
    catalogOrigin: props.catalogOrigin,
  })
  ElMessage.success('分类已创建')
  catDialog.value = false
  await loadTree()
}

async function renameCategory(node: CategoryNode) {
  const { value } = await ElMessageBox.prompt('新分类名称', '重命名分类', {
    inputValue: node.categoryName,
    inputPattern: /\S+/,
    inputErrorMessage: '名称不能为空',
  })
  await api.put(`/governance/catalog/categories/${node.id}`, { categoryName: value })
  ElMessage.success('已更新')
  await loadTree()
}

async function removeCategory(node: CategoryNode) {
  await ElMessageBox.confirm(`确认删除分类「${node.categoryName}」？`, '删除分类', { type: 'warning' })
  await api.delete(`/governance/catalog/categories/${node.id}`)
  ElMessage.success('已删除')
  if (selectedCategoryId.value === node.id) selectedCategoryId.value = null
  await loadTree()
  await refreshLists()
}

async function bindSelected() {
  if (selectedCategoryId.value == null) {
    ElMessage.warning('请先选择左侧分类')
    return
  }
  if (!selectedUnbound.value.length) {
    ElMessage.warning('请勾选未挂载资源')
    return
  }
  await api.post('/governance/catalog/resources-mgmt/bind-category', {
    categoryId: selectedCategoryId.value,
    resourceIds: selectedUnbound.value.map((r) => r.id),
  })
  ElMessage.success('已关联到分类')
  selectedUnbound.value = []
  await refreshLists()
}

async function unbindSelected() {
  if (!selectedBound.value.length) {
    ElMessage.warning('请勾选已挂载资源')
    return
  }
  await api.post('/governance/catalog/resources-mgmt/unbind-category', {
    resourceIds: selectedBound.value.map((r) => r.id),
  })
  ElMessage.success('已解除关联')
  selectedBound.value = []
  await refreshLists()
}

onMounted(async () => {
  try {
    await loadTree()
    await loadUnbound()
  } catch {
    ElMessage.error('加载目录注册数据失败')
  }
})
</script>

<template>
  <PageCard title="目录注册发布">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="将「资源目录编制」产出的未挂载资源关联到分类，形成可提交审批的标准目录。已发布资源须先下线再改挂。"
      style="margin-bottom: 12px"
    />
    <div class="reg-layout">
      <aside class="reg-tree">
        <div class="tree-toolbar">
          <span>资源分类</span>
          <el-button link type="primary" @click="openCreateCategory()">新建</el-button>
        </div>
        <el-tree
          :data="treeData"
          node-key="id"
          :props="{ label: 'categoryName', children: 'children' }"
          highlight-current
          default-expand-all
          @node-click="onTreeClick"
        >
          <template #default="{ data }">
            <span class="tree-node">
              <span>{{ data.categoryName }}</span>
              <span class="tree-acts" @click.stop>
                <el-button link size="small" @click="openCreateCategory(data.id)">子类</el-button>
                <el-button link size="small" @click="renameCategory(data)">改</el-button>
                <el-button link size="small" type="danger" @click="removeCategory(data)">删</el-button>
              </span>
            </span>
          </template>
        </el-tree>
      </aside>
      <main class="reg-main" v-loading="loading">
        <h4>分类下已关联资源</h4>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="danger" plain :disabled="!selectedBound.length" @click="unbindSelected">批量解除关联</el-button>
            <el-button @click="refreshLists">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-table
          :data="boundRows"
          stripe
          size="small"
          @selection-change="(rows: CatalogRes[]) => (selectedBound = rows)"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column prop="resourceCode" label="编码" width="130" />
          <el-table-column prop="resourceName" label="名称" min-width="140" />
          <el-table-column prop="metadataEntryCode" label="元数据" width="140" show-overflow-tooltip />
          <el-table-column label="发布" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.publishStatus)">{{ statusLabel(row.publishStatus) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!boundRows.length" :description="selectedCategoryId ? '该分类暂无关联资源' : '请选择左侧分类'" />

        <h4 style="margin-top: 20px">未挂载资源（可关联）</h4>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :disabled="!selectedUnbound.length || selectedCategoryId == null" @click="bindSelected">
              关联到当前分类
            </el-button>
          </el-form-item>
        </el-form>
        <el-table
          :data="unboundRows"
          stripe
          size="small"
          @selection-change="(rows: CatalogRes[]) => (selectedUnbound = rows)"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column prop="resourceCode" label="编码" width="130" />
          <el-table-column prop="resourceName" label="名称" min-width="140" />
          <el-table-column prop="metadataEntryCode" label="元数据" width="140" show-overflow-tooltip />
          <el-table-column label="来源" width="90">
            <template #default="{ row }">
              {{ row.sourcePathType === 'PROCESSED' ? '加工' : row.sourcePathType === 'DIRECT' ? '直通' : '—' }}
            </template>
          </el-table-column>
        </el-table>
      </main>
    </div>

    <el-dialog v-model="catDialog" title="新建分类" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="上级分类">
          <el-select v-model="catForm.parentId" clearable filterable style="width: 100%" placeholder="无（根分类）">
            <el-option v-for="c in flatCategories" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="catForm.categoryName" />
        </el-form-item>
        <el-form-item label="分类编码">
          <el-input v-model="catForm.categoryCode" placeholder="可空，自动生成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCategory">保存</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.reg-layout {
  display: flex;
  gap: 16px;
  min-height: 480px;
}
.reg-tree {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  padding-right: 12px;
}
.tree-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
}
.tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 4px;
  gap: 4px;
}
.tree-acts {
  opacity: 0.75;
  flex-shrink: 0;
}
.reg-main {
  flex: 1;
  min-width: 0;
}
.reg-main h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
</style>
