<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

const props = withDefaults(defineProps<{ catalogOrigin?: 'INGEST' | 'GOVERNANCE' }>(), {
  catalogOrigin: 'GOVERNANCE',
})

const pageTitle = computed(() => '数据资源分类')
const approvalEntryName = computed(() =>
  props.catalogOrigin === 'INGEST' ? '数据资源目录审批' : '资源目录审批',
)
/** 归集侧无目录审批，分类增删改直接生效 */
const directApply = computed(() => props.catalogOrigin === 'INGEST')

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

interface TableTreeRow extends CategoryRow {
  children?: TableTreeRow[]
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

/** 表格树数据：点击上级展开下级 */
const tableTreeData = computed<TableTreeRow[]>(() => {
  const q = keyword.value.trim().toLowerCase()
  const map = new Map<number, TableTreeRow & { parentId: number; sort: number }>()
  for (const r of rows.value) {
    map.set(r.id, {
      ...r,
      parentId: r.parentId || 0,
      sort: r.sortOrder ?? 0,
      children: [],
    })
  }
  const roots: Array<TableTreeRow & { parentId: number; sort: number }> = []
  for (const n of map.values()) {
    if (!n.parentId || !map.has(n.parentId)) roots.push(n)
    else map.get(n.parentId)!.children!.push(n)
  }
  const sortRec = (list: TableTreeRow[]) => {
    list.sort(
      (a, b) =>
        ((a as TableTreeRow & { sort?: number }).sort ?? 0) -
          ((b as TableTreeRow & { sort?: number }).sort ?? 0) || a.id - b.id,
    )
    list.forEach((c) => {
      if (c.children?.length) sortRec(c.children)
      else delete c.children
    })
  }
  sortRec(roots)

  if (!q) return roots

  const matchSelf = (r: CategoryRow) =>
    r.categoryName.toLowerCase().includes(q) ||
    (r.categoryCode || '').toLowerCase().includes(q) ||
    (r.categoryPath || '').toLowerCase().includes(q)

  const filterTree = (nodes: TableTreeRow[]): TableTreeRow[] => {
    const out: TableTreeRow[] = []
    for (const n of nodes) {
      const kids = n.children?.length ? filterTree(n.children) : []
      if (matchSelf(n) || kids.length) {
        out.push({ ...n, children: kids.length ? kids : undefined })
      }
    }
    return out
  }
  return filterTree(roots)
})

const expandAllOnSearch = computed(() => !!keyword.value.trim())

/** 树选择器数据：编辑时排除自身及子孙，避免环 */
const parentTreeSelectData = computed(() => {
  const blocked = new Set<number>()
  if (editingId.value != null) {
    const collect = (id: number) => {
      blocked.add(id)
      for (const r of rows.value) {
        if ((r.parentId || 0) === id) collect(r.id)
      }
    }
    collect(editingId.value)
  }
  const build = (nodes: TreeNode[]): TreeNode[] =>
    nodes
      .filter((n) => !blocked.has(n.id))
      .map((n) => ({
        id: n.id,
        label: n.label,
        children: n.children?.length ? build(n.children) : undefined,
      }))
  return [{ id: 0, label: '（顶级目录）', children: build(treeData.value) }]
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

function onReset() {
  keyword.value = ''
}

function openCreate(parentId = 0) {
  editingId.value = null
  Object.assign(form, {
    categoryName: '',
    categoryCode: `CAT_${Date.now().toString().slice(-8)}`,
    parentId: parentId || 0,
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
    parentId: form.parentId ?? 0,
    secretFlag: form.secretFlag,
    description: form.description,
    sortOrder: form.sortOrder,
    catalogOrigin: props.catalogOrigin,
    status: 'ACTIVE',
  }
  try {
    if (editingId.value != null) {
      await api.put(`/governance/catalog/categories/${editingId.value}`, body)
      ElMessage.success(
        directApply.value
          ? '分类已更新'
          : '已提交分类编辑审批，请到「' + approvalEntryName.value + '」处理',
      )
    } else {
      await api.post('/governance/catalog/categories', body)
      ElMessage.success(
        directApply.value ? '分类已创建' : '已提交分类新增审批，通过后才会出现在分类树中',
      )
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '提交失败')
  }
}

async function remove(row: CategoryRow) {
  await ElMessageBox.confirm(
    `删除分类「${row.categoryName}」后，其下关联资源将解除关联，可被其他分类再次关联。确认删除？`,
    '删除分类',
    { type: 'warning' },
  )
  try {
    await api.delete(`/governance/catalog/categories/${row.id}`)
    ElMessage.success(directApply.value ? '分类已删除' : '已提交分类删除审批')
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '删除失败')
  }
}

onMounted(load)

onActivated(() => {
  void load()
})
</script>

<template>
  <div class="classify-page">
    <PageCard :title="pageTitle">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="按分类名称/代码/路径模糊查询"
          style="width: 280px"
          @keyup.enter="load"
        />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="onReset">重置</el-button>
        <el-button type="primary" @click="openCreate(0)">新增资源分类</el-button>
      </div>
      <el-table
        :key="expandAllOnSearch ? `search:${keyword}` : 'tree'"
        v-loading="loading"
        :data="tableTreeData"
        row-key="id"
        border
        stripe
        height="520"
        :tree-props="{ children: 'children' }"
        :default-expand-all="expandAllOnSearch"
      >
        <el-table-column prop="categoryName" label="资源目录名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="categoryCode" label="分类代码" width="140" />
        <el-table-column prop="categoryPath" label="路径" min-width="200" show-overflow-tooltip />
        <el-table-column label="是否涉密" width="90">
          <template #default="{ row }">{{ row.secretFlag === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="description" label="描述" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCreate(row.id)">新增下级</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? '新增资源分类' : '编辑资源分类'"
      width="520px"
      append-to-body
      teleported
      destroy-on-close
      align-center
      :z-index="3200"
    >
      <el-form label-width="110px">
        <el-form-item label="资源目录名称" required>
          <el-input v-model="form.categoryName" placeholder="请输入资源目录名称" />
        </el-form-item>
        <el-form-item label="所属资源目录" required>
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeSelectData"
            check-strictly
            filterable
            default-expand-all
            :render-after-expand="false"
            node-key="id"
            :props="{ label: 'label', children: 'children', value: 'id' }"
            style="width: 100%"
            placeholder="请选择上级目录（可挂到基础/部门/主题等已生效分类下）"
          />
        </el-form-item>
        <el-form-item label="分类代码" required>
          <el-input v-model="form.categoryCode" placeholder="分类代码" />
        </el-form-item>
        <el-form-item label="是否涉密" required>
          <el-radio-group v-model="form.secretFlag">
            <el-radio :value="0">否</el-radio>
            <el-radio :value="1">是</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">{{ directApply ? '保存' : '提交审批' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint { margin: 0 0 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
</style>
