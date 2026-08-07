<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type CategoryNode } from '../../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const rows = ref<CategoryNode[]>([])
const keyword = ref('')
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  nodeName: '',
  nodeCode: '',
  parentId: 0 as number,
  secretFlag: 0,
  description: '',
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
      label: `${r.nodeName}${r.secretFlag === 1 ? '（涉密）' : ''}`,
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
  return rows.value.filter((r) => r.nodeName.toLowerCase().includes(q) || r.nodeCode.toLowerCase().includes(q))
})

async function load() {
  await withLoad(async () => {
    const res = await ingestionApi.categories({ keyword: keyword.value || undefined })
    rows.value = res.data || []
  })
}

function onReset() {
  keyword.value = ''
  void load()
}

function openCreate(parentId = 0) {
  editingId.value = null
  Object.assign(form, {
    nodeName: '',
    nodeCode: `CAT_${Date.now().toString().slice(-8)}`,
    parentId,
    secretFlag: 0,
    description: '',
  })
  dialogVisible.value = true
}

function openEdit(row: CategoryNode) {
  editingId.value = row.id
  Object.assign(form, {
    nodeName: row.nodeName,
    nodeCode: row.nodeCode,
    parentId: row.parentId ?? 0,
    secretFlag: row.secretFlag === 1 ? 1 : 0,
    description: row.description || '',
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.nodeName.trim() || !form.nodeCode.trim()) {
    ElMessage.warning('资源目录名称与分类代码为必填')
    return
  }
  const body = { ...form, nodeName: form.nodeName.trim(), nodeCode: form.nodeCode.trim() }
  if (editingId.value == null) {
    await ingestionApi.createCategory(body)
    ElMessage.success('新增成功')
  } else {
    await ingestionApi.updateCategory(editingId.value, body)
    ElMessage.success('保存成功')
  }
  dialogVisible.value = false
  await load()
}

async function remove(row: CategoryNode) {
  await ElMessageBox.confirm(`删除「${row.nodeName}」将解除其下资源关联，确认？`, '删除分类', { type: 'warning' })
  await ingestionApi.deleteCategory(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <div class="cat-layout">
      <aside class="cat-side">
        <div class="cat-side__title">分类树</div>
        <el-tree :data="treeData" node-key="id" default-expand-all :props="{ label: 'label', children: 'children' }" />
      </aside>
      <section class="cat-main">
        <PageCard title="数据资源分类">
          <el-form inline>
            <el-form-item label="分类名称">
              <el-input v-model="keyword" clearable placeholder="模糊查询" style="width:180px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="load">查询</el-button>
              <el-button @click="onReset">重置</el-button>
              <el-button type="primary" @click="openCreate(0)">新增分类</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="tableRows" border stripe>
            <el-table-column prop="nodeName" label="资源目录名称" min-width="160" />
            <el-table-column prop="nodeCode" label="分类代码" width="140" />
            <el-table-column label="所属目录" min-width="140">
              <template #default="{ row }">
                {{ row.parentId === 0 ? '顶级' : (rows.find((r) => r.id === row.parentId)?.nodeName || row.parentId) }}
              </template>
            </el-table-column>
            <el-table-column label="是否涉密" width="90">
              <template #default="{ row }">{{ row.secretFlag === 1 ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button link type="primary" @click="openCreate(row.id)">加子类</el-button>
                <el-button link type="danger" @click="remove(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新增资源分类' : '编辑资源分类'" width="520px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="资源目录名称" required>
          <el-input v-model="form.nodeName" />
        </el-form-item>
        <el-form-item label="所属资源目录" required>
          <el-select v-model="form.parentId" filterable style="width:100%">
            <el-option v-for="o in parentOptions" :key="o.id" :label="o.label" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类代码" required>
          <el-input v-model="form.nodeCode" />
        </el-form-item>
        <el-form-item label="是否涉密" required>
          <el-radio-group v-model="form.secretFlag">
            <el-radio :value="0">否</el-radio>
            <el-radio :value="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cat-layout { display: flex; gap: 12px; min-height: 480px; }
.cat-side {
  width: 240px; flex-shrink: 0; background: #fafbfd; border: 1px solid #e5eaf2;
  border-radius: 4px; padding: 12px 8px; max-height: 640px; overflow: auto;
}
.cat-side__title { font-weight: 600; margin-bottom: 8px; padding-left: 8px; border-left: 3px solid #2f6fed; }
.cat-main { flex: 1; min-width: 0; }
</style>
