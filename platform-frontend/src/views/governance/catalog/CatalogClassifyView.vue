<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

const props = withDefaults(defineProps<{ catalogOrigin?: 'INGEST' | 'GOVERNANCE' }>(), {
  catalogOrigin: 'GOVERNANCE',
})

interface CategoryRow {
  id: number
  categoryCode: string
  categoryName: string
  parentId?: number
  categoryPath?: string
  secretFlag?: number
  description?: string
  sortOrder?: number
  status?: string
  catalogOrigin?: string
}

const rows = ref<CategoryRow[]>([])
const keyword = ref('')
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  categoryName: '',
  categoryCode: '',
  parentId: 0 as number,
  secretFlag: 0,
  description: '',
  sortOrder: 0,
})

interface TreeNode {
  id: number
  label: string
  children?: TreeNode[]
}

const treeData = computed<TreeNode[]>(() => {
  const map = new Map<number, TreeNode & { parentId: number; sort: number }>()
  for (const r of rows.value) {
    map.set(r.id, {
      id: r.id,
      label: `${r.categoryName}${r.secretFlag === 1 ? '（涉密）' : ''}`,
      parentId: r.parentId || 0,
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

const parentOptions = computed(() => {
  const opts: { id: number; label: string }[] = [{ id: 0, label: '（顶级目录）' }]
  const walk = (nodes: TreeNode[], depth: number) => {
    for (const n of nodes) {
      if (editingId.value != null && n.id === editingId.value) continue
      opts.push({ id: n.id, label: `${'　'.repeat(depth)}${n.label}` })
      if (n.children?.length) walk(n.children, depth + 1)
    }
  }
  walk(treeData.value, 0)
  return opts
})

const tableRows = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter(
    (r) =>
      r.categoryName.toLowerCase().includes(q) ||
      (r.categoryCode || '').toLowerCase().includes(q),
  )
})

async function load() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/categories', {
      params: { catalogOrigin: props.catalogOrigin },
    })
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openCreate(parentId = 0) {
  editingId.value = null
  Object.assign(form, {
    categoryName: '',
    categoryCode: `CAT_${Date.now().toString().slice(-8)}`,
    parentId,
    secretFlag: 0,
    description: '',
    sortOrder: 0,
  })
  dialogVisible.value = true
}

function openEdit(row: CategoryRow) {
  editingId.value = row.id
  Object.assign(form, {
    categoryName: row.categoryName,
    categoryCode: row.categoryCode,
    parentId: row.parentId ?? 0,
    secretFlag: row.secretFlag === 1 ? 1 : 0,
    description: row.description || '',
    sortOrder: row.sortOrder ?? 0,
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.categoryName.trim()) {
    ElMessage.warning('资源目录名称为必填')
    return
  }
  if (!form.categoryCode.trim()) {
    ElMessage.warning('分类代码为必填')
    return
  }
  const body = {
    categoryName: form.categoryName.trim(),
    categoryCode: form.categoryCode.trim(),
    parentId: form.parentId || 0,
    secretFlag: form.secretFlag,
    description: form.description,
    sortOrder: form.sortOrder,
    catalogOrigin: props.catalogOrigin,
    status: 'ACTIVE',
  }
  if (editingId.value != null) {
    await api.put(`/governance/catalog/categories/${editingId.value}`, body)
    ElMessage.success('已更新')
  } else {
    await api.post('/governance/catalog/categories', body)
    ElMessage.success('已新增')
  }
  dialogVisible.value = false
  await load()
}

async function remove(row: CategoryRow) {
  await ElMessageBox.confirm(
    `删除分类「${row.categoryName}」后，其下关联资源将解除关联，可被其他分类再次关联。确认删除？`,
    '删除分类',
    { type: 'warning' },
  )
  await api.delete(`/governance/catalog/categories/${row.id}`)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="classify-page">
    <PageCard title="数据资源分类">
      <p class="hint">
        按类/项/目/细目维护资源目录树；涉密分类从编目源头控制可见性。删除分类会自动解除资源关联。
      </p>
      <div class="toolbar">
        <el-input v-model="keyword" clearable placeholder="按分类名称/代码模糊查询" style="width: 260px" @keyup.enter="load" />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="primary" @click="openCreate(0)">新增资源分类</el-button>
      </div>
      <div class="layout">
        <el-tree :data="treeData" node-key="id" default-expand-all class="tree" />
        <el-table v-loading="loading" :data="tableRows" stripe border height="480">
          <el-table-column prop="categoryCode" label="分类代码" width="140" />
          <el-table-column prop="categoryName" label="资源目录名称" min-width="160" />
          <el-table-column prop="categoryPath" label="路径" min-width="180" show-overflow-tooltip />
          <el-table-column label="是否涉密" width="90">
            <template #default="{ row }">{{ row.secretFlag === 1 ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCreate(row.id)">下级</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </PageCard>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑资源分类' : '新增资源分类'" width="520px">
      <el-form label-width="110px">
        <el-form-item label="资源目录名称" required>
          <el-input v-model="form.categoryName" />
        </el-form-item>
        <el-form-item label="所属资源目录" required>
          <el-select v-model="form.parentId" style="width: 100%">
            <el-option v-for="o in parentOptions" :key="o.id" :label="o.label" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类代码" required>
          <el-input v-model="form.categoryCode" />
        </el-form-item>
        <el-form-item label="是否涉密" required>
          <el-radio-group v-model="form.secretFlag">
            <el-radio :value="0">否</el-radio>
            <el-radio :value="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint { margin: 0 0 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.layout { display: grid; grid-template-columns: 240px 1fr; gap: 12px; }
.tree { border: 1px solid var(--el-border-color); border-radius: 6px; padding: 8px; max-height: 480px; overflow: auto; }
@media (max-width: 960px) {
  .layout { grid-template-columns: 1fr; }
}
</style>
