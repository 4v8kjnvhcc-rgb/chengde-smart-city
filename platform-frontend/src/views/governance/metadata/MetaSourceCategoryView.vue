<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { LAYER_OPTIONS } from './meta-labels'

const LAYER_CODE_OPTIONS = [
  ...LAYER_OPTIONS,
  { label: 'SOURCE 来源', value: 'SOURCE' },
  { label: 'DICT 字典', value: 'DICT' },
  { label: 'OTHER 其他', value: 'OTHER' },
]

interface CategoryNode {
  id: number
  label: string
  categoryCode?: string
  layerCode?: string
  systemFlag?: number
  description?: string
  children?: CategoryNode[]
}

interface ParentOption {
  id: number
  label: string
}

const keyword = ref('')
const loading = ref(false)
const treeData = ref<CategoryNode[]>([])

const dialogVisible = ref(false)
const editing = ref<CategoryNode | null>(null)
const form = reactive({
  parentId: 0 as number,
  categoryName: '',
  categoryCode: '',
  layerCode: '',
  description: '',
})

const parentOptions = computed<ParentOption[]>(() => {
  const opts: ParentOption[] = [{ id: 0, label: '（顶级分类）' }]
  const walk = (nodes: CategoryNode[], prefix: string) => {
    for (const n of nodes) {
      // 编辑时不能把自己或子孙选为上级
      if (editing.value && (n.id === editing.value.id || isDescendantOf(editing.value, n.id))) {
        continue
      }
      opts.push({ id: n.id, label: prefix ? `${prefix} / ${n.label}` : n.label })
      if (n.children?.length) walk(n.children, prefix ? `${prefix} / ${n.label}` : n.label)
    }
  }
  walk(treeData.value, '')
  return opts
})

function isDescendantOf(ancestor: CategoryNode, id: number): boolean {
  if (!ancestor.children?.length) return false
  for (const c of ancestor.children) {
    if (c.id === id || isDescendantOf(c, id)) return true
  }
  return false
}

async function loadList() {
  loading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/source-categories/tree', {
      params: { keyword: keyword.value.trim() || undefined },
    })
    treeData.value = res.data || []
  } catch {
    ElMessage.error('加载分类列表失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  loadList()
}

function resetFilter() {
  keyword.value = ''
  loadList()
}

function openCreate(parentId = 0) {
  editing.value = null
  form.parentId = parentId
  form.categoryName = ''
  form.categoryCode = ''
  form.layerCode = ''
  form.description = ''
  dialogVisible.value = true
}

function openEdit(row: CategoryNode) {
  editing.value = row
  form.parentId = findParentId(treeData.value, row.id) ?? 0
  form.categoryName = row.label
  form.categoryCode = row.categoryCode || ''
  form.layerCode = row.layerCode || ''
  form.description = row.description || ''
  dialogVisible.value = true
}

function findParentId(nodes: CategoryNode[], id: number, parentId = 0): number | null {
  for (const n of nodes) {
    if (n.id === id) return parentId
    if (n.children?.length) {
      const hit = findParentId(n.children, id, n.id)
      if (hit !== null) return hit
    }
  }
  return null
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
        layerCode: form.layerCode.trim() || null,
        description: form.description.trim() || undefined,
      })
      ElMessage.success('已保存')
    } else {
      await api.post('/governance/platform/metadata/source-categories', {
        parentId: form.parentId,
        categoryName: form.categoryName.trim(),
        categoryCode: form.categoryCode.trim() || undefined,
        layerCode: form.layerCode.trim() || undefined,
        description: form.description.trim() || undefined,
      })
      ElMessage.success('已新增')
    }
    dialogVisible.value = false
    await loadList()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  }
}

async function removeRow(row: CategoryNode) {
  if (row.systemFlag === 1) {
    ElMessage.warning('系统内置分类不可删除')
    return
  }
  if (row.children?.length) {
    ElMessage.warning('请先删除子分类')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除分类「${row.label}」？`, '删除确认', { type: 'warning' })
    await api.delete(`/governance/platform/metadata/source-categories/${row.id}`)
    ElMessage.success('已删除')
    await loadList()
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <PageCard title="数据源分类">
    <el-form inline class="portal-inline-form portal-inline-form--block msc-toolbar" @submit.prevent>
      <el-form-item label="名称" class="portal-field-lg">
        <el-input
          v-model="keyword"
          clearable
          placeholder="请输入名称"
          @keyup.enter="onSearch"
        />
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="resetFilter">重置</el-button>
        <el-button type="primary" @click="openCreate(0)">+ 新增</el-button>
      </el-form-item>
    </el-form>

    <el-table
      v-loading="loading"
      :data="treeData"
      row-key="id"
      stripe
      size="small"
      default-expand-all
      :tree-props="{ children: 'children' }"
      empty-text="暂无数据"
    >
      <el-table-column prop="label" label="名称" min-width="240" show-overflow-tooltip />
      <el-table-column prop="categoryCode" label="编码" min-width="160" show-overflow-tooltip />
      <el-table-column prop="layerCode" label="层级编码" min-width="120" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.systemFlag === 1" size="small" type="info">系统</el-tag>
          <el-tag v-else size="small">自定义</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openCreate(row.id)">新增子级</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑分类' : '新增分类'"
      width="480px"
      destroy-on-close
    >
      <el-form label-width="88px">
        <el-form-item v-if="!editing" label="上级分类">
          <el-select v-model="form.parentId" filterable style="width: 100%">
            <el-option
              v-for="opt in parentOptions"
              :key="opt.id"
              :label="opt.label"
              :value="opt.id"
            />
          </el-select>
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
        <el-form-item label="层级编码">
          <el-select
            v-model="form.layerCode"
            clearable
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入层级编码"
            style="width: 100%"
          >
            <el-option
              v-for="opt in LAYER_CODE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
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
.msc-toolbar {
  margin-bottom: 12px;
}
</style>
