<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type CategoryNode, type Registry } from '../../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const categories = ref<CategoryNode[]>([])
const selectedCategoryId = ref<number | null>(null)
const bound = ref<Registry[]>([])
const unbound = ref<Registry[]>([])
const selectedBound = ref<Registry[]>([])
const selectedUnbound = ref<Registry[]>([])

interface TreeNode {
  id: number
  label: string
  children?: TreeNode[]
}

const treeData = computed<TreeNode[]>(() => {
  const map = new Map<number, TreeNode & { parentId: number; sort: number }>()
  for (const r of categories.value) {
    map.set(r.id, {
      id: r.id,
      label: r.nodeName,
      parentId: r.parentId,
      sort: r.sortOrder ?? 0,
      children: [],
    })
  }
  const roots: TreeNode[] = []
  for (const n of map.values()) {
    if (!n.parentId || !map.has(n.parentId)) roots.push(n)
    else map.get(n.parentId)!.children!.push(n)
  }
  const sortRec = (list: TreeNode[]) => {
    list.sort((a, b) => (map.get(a.id)?.sort ?? 0) - (map.get(b.id)?.sort ?? 0) || a.id - b.id)
    list.forEach((c) => c.children && sortRec(c.children))
  }
  sortRec(roots)
  return roots
})

async function loadCategories() {
  const res = await ingestionApi.categories()
  categories.value = res.data || []
}

async function loadBound() {
  if (selectedCategoryId.value == null) {
    bound.value = []
    return
  }
  const res = await ingestionApi.boundResources(selectedCategoryId.value)
  bound.value = res.data || []
}

async function loadUnbound() {
  const res = await ingestionApi.registries({ unboundOnly: true })
  unbound.value = res.data || []
}

async function refresh() {
  await withLoad(async () => {
    await Promise.all([loadBound(), loadUnbound()])
  })
}

function onTreeClick(data: TreeNode) {
  selectedCategoryId.value = data.id
  void loadBound()
}

async function bindSelected() {
  if (selectedCategoryId.value == null) {
    ElMessage.warning('请先选择资源分类')
    return
  }
  const ids = selectedUnbound.value.map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('请勾选未关联资源')
    return
  }
  await ingestionApi.bindResources(selectedCategoryId.value, ids)
  ElMessage.success('关联成功')
  selectedUnbound.value = []
  await refresh()
}

async function unbindSelected() {
  const ids = selectedBound.value.map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('请勾选已关联资源')
    return
  }
  await ElMessageBox.confirm(`确认解除 ${ids.length} 项关联？`, '解除关联', { type: 'warning' })
  await ingestionApi.unbindResources(ids)
  ElMessage.success('已解除')
  selectedBound.value = []
  await refresh()
}

async function submitPublish() {
  const ids = selectedBound.value.filter((r) => r.publishStatus !== 'PUBLISHED').map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('请勾选待发布的已关联资源')
    return
  }
  await ElMessageBox.confirm(`提交 ${ids.length} 条发布审批？`, '注册发布', { type: 'info' })
  await ingestionApi.submitPublish({ ids, comment: '申请发布到部门共享门户' })
  ElMessage.success('已提交审批')
  selectedBound.value = []
  await refresh()
}

async function submitOffline() {
  const ids = selectedBound.value.filter((r) => r.publishStatus === 'PUBLISHED').map((r) => r.id)
  if (!ids.length) {
    ElMessage.warning('请勾选已发布资源')
    return
  }
  await ElMessageBox.confirm(`提交 ${ids.length} 条下线审批？`, '下线', { type: 'warning' })
  await ingestionApi.submitOffline({ ids, comment: '申请下线' })
  ElMessage.success('已提交下线审批')
  selectedBound.value = []
  await refresh()
}

onMounted(async () => {
  await withLoad(async () => {
    await loadCategories()
    await loadUnbound()
  })
})
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom:12px"
      title="在分类下关联未挂载的编目资源，再提交发布审批；审批通过后自动同步到部门数据共享门户。"
    />
    <div class="pub-layout">
      <aside class="pub-side">
        <div class="pub-side__title">资源分类</div>
        <el-tree
          :data="treeData"
          node-key="id"
          default-expand-all
          highlight-current
          :props="{ label: 'label', children: 'children' }"
          @node-click="onTreeClick"
        />
      </aside>
      <section class="pub-main">
        <PageCard title="已关联资源">
          <div style="margin-bottom:8px; display:flex; gap:8px; flex-wrap:wrap">
            <el-button type="danger" plain :disabled="!selectedBound.length" @click="unbindSelected">批量解除关联</el-button>
            <el-button type="primary" :disabled="!selectedBound.length" @click="submitPublish">提交发布审批</el-button>
            <el-button :disabled="!selectedBound.length" @click="submitOffline">提交下线审批</el-button>
            <el-button @click="refresh">刷新</el-button>
          </div>
          <el-table :data="bound" border stripe height="260" @selection-change="(v: Registry[]) => (selectedBound = v)">
            <el-table-column type="selection" width="42" />
            <el-table-column prop="title" label="资源名称" min-width="140" />
            <el-table-column label="代码" width="120">
              <template #default="{ row }">{{ row.resourceCode || row.registryCode }}</template>
            </el-table-column>
            <el-table-column prop="publishStatus" label="发布状态" width="110" />
            <el-table-column prop="approvalStatus" label="审批状态" width="100" />
          </el-table>
        </PageCard>
        <PageCard title="可关联资源（未挂载）" style="margin-top:12px">
          <div style="margin-bottom:8px">
            <el-button type="primary" :disabled="!selectedUnbound.length || selectedCategoryId == null" @click="bindSelected">
              关联到当前分类
            </el-button>
          </div>
          <el-table :data="unbound" border stripe height="240" @selection-change="(v: Registry[]) => (selectedUnbound = v)">
            <el-table-column type="selection" width="42" />
            <el-table-column prop="title" label="资源名称" min-width="140" />
            <el-table-column label="代码" width="120">
              <template #default="{ row }">{{ row.resourceCode || row.registryCode }}</template>
            </el-table-column>
            <el-table-column prop="shareType" label="共享方式" width="110" />
            <el-table-column prop="providerOrg" label="提供方" width="120" show-overflow-tooltip />
          </el-table>
        </PageCard>
      </section>
    </div>
  </div>
</template>

<style scoped>
.pub-layout { display: flex; gap: 12px; }
.pub-side {
  width: 240px; flex-shrink: 0; background: #fafbfd; border: 1px solid #e5eaf2;
  border-radius: 4px; padding: 12px 8px; max-height: 720px; overflow: auto;
}
.pub-side__title { font-weight: 600; margin-bottom: 8px; padding-left: 8px; border-left: 3px solid #2f6fed; }
.pub-main { flex: 1; min-width: 0; }
</style>
