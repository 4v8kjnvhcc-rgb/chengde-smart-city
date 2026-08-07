<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/http'

defineProps<{ module?: string }>()

export interface RegisterMenuRow {
  id: number
  parentId: number
  menuName: string
  routeName?: string
  menuType: number
  path?: string
  component?: string
  permission?: string
  icon?: string
  sortOrder?: number
  visible?: number
  status?: number
  mCode?: string
  seed?: boolean
}

const SEED_IDS = new Set([
  7000, 7001, 7002, 7003, 7004, 7005, 7006, 7007, 7008, 7009, 7010, 7011, 7012, 7013, 7014, 7015,
])

const loading = ref(false)
const rows = ref<RegisterMenuRow[]>([])
const selectedParentId = ref<number>(7000)
const tableSelection = ref<RegisterMenuRow[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({
  parentId: 7000,
  sortOrder: 0,
  routeName: '',
  menuName: '',
  icon: '',
  path: '',
  component: '',
  menuType: 2,
  visible: 1,
})

const formRules = {
  menuName: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  path: [
    {
      validator: (_: unknown, v: string, cb: (e?: Error) => void) => {
        if (form.menuType === 1 || form.menuType === 3) return cb()
        if (!v?.trim()) return cb(new Error('请输入访问地址'))
        cb()
      },
      trigger: 'blur',
    },
  ],
}

function resolveRouteName(row?: Pick<RegisterMenuRow, 'routeName' | 'permission' | 'mCode' | 'id' | 'menuName'>, fallbackTitle = '') {
  const fromRow = row?.routeName?.trim() || row?.permission?.trim() || row?.mCode?.trim()
  if (fromRow) return fromRow
  const title = (fallbackTitle || row?.menuName || '').trim()
  if (title) return title
  if (row?.id != null) return `menu_${row.id}`
  return ''
}

const formRef = ref<{ validate: () => Promise<void>; resetFields: () => void } | null>(null)

interface TreeNode {
  id: number
  label: string
  children?: TreeNode[]
}

const treeData = computed<TreeNode[]>(() => {
  const map = new Map<number, TreeNode & { parentId: number; sort: number }>()
  for (const r of rows.value) {
    if (r.menuType === 3) continue
    map.set(r.id, {
      id: r.id,
      label: r.menuName,
      parentId: r.parentId,
      sort: r.sortOrder ?? 0,
      children: [],
    })
  }
  const roots: TreeNode[] = []
  for (const n of map.values()) {
    if (n.id === 7000 || !map.has(n.parentId)) {
      roots.push(n)
    } else {
      map.get(n.parentId)!.children!.push(n)
    }
  }
  const sortRec = (list: TreeNode[]) => {
    list.sort((a, b) => {
      const aa = map.get(a.id)?.sort ?? 0
      const bb = map.get(b.id)?.sort ?? 0
      return aa - bb || a.id - b.id
    })
    list.forEach((c) => c.children && sortRec(c.children))
  }
  sortRec(roots)
  return roots
})

const childRows = computed(() =>
  rows.value
    .filter((r) => r.parentId === selectedParentId.value)
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id),
)

function typeLabel(t: number) {
  if (t === 1) return '目录'
  if (t === 3) return '按钮'
  return '菜单项'
}

function displayName(row: RegisterMenuRow) {
  return row.routeName || row.mCode || row.permission || String(row.id)
}

function isSeed(row: RegisterMenuRow) {
  if (SEED_IDS.has(row.id)) return true
  const p = row.permission || ''
  // 自定义权限码一律视为非种子，可删除
  if (p.includes(':custom:')) return false
  return false
}

function canDelete(row: RegisterMenuRow) {
  return !isSeed(row)
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/system/menus/register-scope')
    rows.value = (res.data || []).map((r: RegisterMenuRow) => ({
      ...r,
      seed: SEED_IDS.has(r.id) && !(r.permission || '').includes(':custom:'),
    }))
    if (!rows.value.some((r) => r.id === selectedParentId.value)) {
      selectedParentId.value = 7000
    }
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '加载菜单失败')
  } finally {
    loading.value = false
  }
}

function onTreeSelect(data: TreeNode) {
  selectedParentId.value = data.id
  tableSelection.value = []
}

function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增'
  Object.assign(form, {
    parentId: selectedParentId.value,
    sortOrder: (childRows.value.at(-1)?.sortOrder ?? 0) + 1,
    routeName: '',
    menuName: '',
    icon: '',
    path: '/exchange/ingestion?system=register&module=',
    component: '',
    menuType: 2,
    visible: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: RegisterMenuRow) {
  editingId.value = row.id
  dialogTitle.value = '编辑'
  Object.assign(form, {
    parentId: row.parentId,
    sortOrder: row.sortOrder ?? 0,
    routeName: resolveRouteName(row),
    menuName: row.menuName || '',
    icon: row.icon || '',
    path: row.path || '',
    component: row.component || '',
    menuType: row.menuType ?? 2,
    visible: row.visible === 0 ? 0 : 1,
  })
  dialogVisible.value = true
}

async function submitForm() {
  await formRef.value?.validate()
  saving.value = true
  try {
    const menuName = form.menuName.trim()
    const routeName =
      form.routeName.trim() ||
      resolveRouteName(
        editingId.value != null
          ? { id: editingId.value, menuName, routeName: form.routeName, permission: undefined }
          : { menuName, routeName: form.routeName },
        menuName,
      )
    const body = {
      parentId: form.parentId,
      sortOrder: form.sortOrder,
      routeName,
      menuName,
      icon: form.icon,
      path: form.path,
      component: form.component,
      menuType: form.menuType,
      visible: form.visible,
    }
    if (editingId.value == null) {
      await api.post('/system/menus/register-scope', body)
      ElMessage.success('新增成功')
    } else {
      await api.put(`/system/menus/register-scope/${editingId.value}`, body)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await load()
    window.dispatchEvent(new CustomEvent('register-menus-changed'))
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRows(list: RegisterMenuRow[]) {
  if (!list.length) {
    ElMessage.warning('请先勾选要删除的菜单')
    return
  }
  const blocked = list.filter((r) => !canDelete(r))
  if (blocked.length) {
    ElMessage.warning('系统初始化菜单不可删除；自定义菜单可删')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${list.length} 项？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.delete('/system/menus/register-scope', { data: { ids: list.map((r) => r.id) } })
    ElMessage.success('删除成功')
    await load()
    window.dispatchEvent(new CustomEvent('register-menus-changed'))
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="menu-mgmt" v-loading="loading">
    <aside class="menu-mgmt__side">
      <div class="menu-mgmt__side-title">菜单管理</div>
      <el-tree
        :data="treeData"
        node-key="id"
        default-expand-all
        highlight-current
        :current-node-key="selectedParentId"
        :props="{ label: 'label', children: 'children' }"
        @node-click="onTreeSelect"
      />
    </aside>
    <section class="menu-mgmt__main">
      <div class="menu-mgmt__toolbar">
        <el-button type="primary" @click="openCreate">+ 新增</el-button>
        <el-button type="primary" @click="removeRows(tableSelection)">删除</el-button>
      </div>
      <el-table
        class="portal-table"
        :data="childRows"
        border
        stripe
        height="100%"
        @selection-change="(v: RegisterMenuRow[]) => (tableSelection = v)"
      >
        <el-table-column type="selection" width="48" :selectable="(row: RegisterMenuRow) => canDelete(row)" />
        <el-table-column label="名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ displayName(row) }}</template>
        </el-table-column>
        <el-table-column prop="menuName" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ typeLabel(row.menuType) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">修改</el-button>
            <el-button link type="primary" :disabled="!canDelete(row)" @click="removeRows([row])">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="名称" prop="routeName">
          <el-input v-model="form.routeName" placeholder="可选；空则按标题自动生成" />
        </el-form-item>
        <el-form-item label="标题" prop="menuName">
          <el-input v-model="form.menuName" placeholder="显示标题" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="可选" />
        </el-form-item>
        <el-form-item label="访问地址" prop="path">
          <el-input v-model="form.path" placeholder="目录/按钮可空；菜单项如 /exchange/ingestion?system=register&module=..." />
        </el-form-item>
        <el-form-item label="文件地址">
          <el-input v-model="form.component" placeholder="组件路径，可空" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.menuType" style="width: 100%">
            <el-option :value="1" label="目录" />
            <el-option :value="2" label="菜单项" />
            <el-option :value="3" label="按钮" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否隐藏">
          <el-select v-model="form.visible" style="width: 100%">
            <el-option :value="1" label="否" />
            <el-option :value="0" label="是" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.menu-mgmt {
  display: flex;
  gap: 0;
  min-height: 520px;
  background: #fff;
  border: 1px solid #e5eaf2;
  border-radius: 4px;
  overflow: hidden;
}
.menu-mgmt__side {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid #e5eaf2;
  padding: 12px 8px 16px;
  background: #fafbfd;
}
.menu-mgmt__side-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  padding: 4px 8px 8px;
  border-left: 3px solid #2f6fed;
  margin-bottom: 4px;
  line-height: 1.2;
}
.menu-mgmt__side-hint {
  margin: 0 8px 10px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}
.menu-mgmt__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 12px 16px 16px;
}
.menu-mgmt__toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.menu-mgmt :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #e8f1ff;
  color: #1d4f91;
}
</style>
