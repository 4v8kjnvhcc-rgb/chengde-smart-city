<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'

export interface MenuRow {
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
}

const loading = ref(false)
const rows = ref<MenuRow[]>([])
const selectedParentId = ref<number>(1)
const tableSelection = ref<MenuRow[]>([])
const keyword = ref('')
const page = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const dialogTitle = ref('新增')
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({
  parentId: 1,
  sortOrder: 0,
  routeName: '',
  menuName: '',
  icon: '',
  path: '',
  component: '',
  permission: '',
  menuType: 2,
  visible: 1,
})

const formRules = {
  menuName: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  path: [
    {
      validator: (_: unknown, v: string, cb: (e?: Error) => void) => {
        // 目录、按钮可不填访问地址
        if (form.menuType === 1 || form.menuType === 3) return cb()
        if (!v?.trim()) return cb(new Error('请输入访问地址'))
        cb()
      },
      trigger: 'blur',
    },
  ],
}

/** 存量菜单多数无 route_name，编辑时用权限码/编码兜底，避免校验拦死增改 */
function resolveRouteName(row?: Pick<MenuRow, 'routeName' | 'permission' | 'mCode' | 'id' | 'menuName'>, fallbackTitle = '') {
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
    if (!n.parentId || !map.has(n.parentId)) {
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

const parentOptions = computed(() => {
  const opts: { id: number; label: string }[] = [{ id: 0, label: '（顶级）' }]
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

function displayName(row: MenuRow) {
  return row.routeName || row.mCode || row.permission || String(row.id)
}

function typeLabel(t: number) {
  if (t === 1) return '目录'
  if (t === 3) return '按钮'
  return '菜单项'
}

function matchKeyword(row: MenuRow, q: string) {
  const hay = `${displayName(row)} ${row.menuName || ''}`.toLowerCase()
  return hay.includes(q)
}

const filteredAll = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return null
  return rows.value
    .filter((r) => matchKeyword(r, q))
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
})

const siblingRows = computed(() =>
  rows.value
    .filter((r) => r.parentId === selectedParentId.value)
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id),
)

const displayRows = computed(() => filteredAll.value ?? siblingRows.value)

const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return displayRows.value.slice(start, start + pageSize.value)
})

const total = computed(() => displayRows.value.length)

watch([keyword, selectedParentId, pageSize], () => {
  page.value = 1
})

function canDelete(row: MenuRow) {
  return row.id !== 1
}

function canMove(row: MenuRow, direction: -1 | 1) {
  if (filteredAll.value) return false
  const list = siblingRows.value
  const idx = list.findIndex((r) => r.id === row.id)
  if (idx < 0) return false
  const next = idx + direction
  return next >= 0 && next < list.length
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/system/menus', { params: { manage: true } })
    rows.value = res.data || []
    if (!rows.value.some((r) => r.id === selectedParentId.value)) {
      selectedParentId.value = treeData.value[0]?.id ?? 1
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
  keyword.value = ''
}

function openCreate() {
  editingId.value = null
  dialogTitle.value = '新增'
  Object.assign(form, {
    parentId: selectedParentId.value,
    sortOrder: (siblingRows.value.at(-1)?.sortOrder ?? 0) + 1,
    routeName: '',
    menuName: '',
    icon: '',
    path: '',
    component: '',
    permission: '',
    menuType: 2,
    visible: 1,
  })
  dialogVisible.value = true
}

function openEdit(row: MenuRow) {
  editingId.value = row.id
  dialogTitle.value = '编辑'
  Object.assign(form, {
    parentId: row.parentId ?? 0,
    sortOrder: row.sortOrder ?? 0,
    routeName: resolveRouteName(row),
    menuName: row.menuName || '',
    icon: row.icon || '',
    path: row.path || '',
    component: row.component || '',
    permission: row.permission || '',
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
          ? { id: editingId.value, menuName, routeName: form.routeName, permission: form.permission }
          : { menuName, routeName: form.routeName, permission: form.permission },
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
      permission: form.permission.trim() || undefined,
      menuType: form.menuType,
      visible: form.visible,
    }
    if (editingId.value == null) {
      await api.post('/system/menus', body)
      ElMessage.success('新增成功')
    } else {
      await api.put(`/system/menus/${editingId.value}`, body)
      ElMessage.success('保存成功')
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRows(list: MenuRow[]) {
  if (!list.length) {
    ElMessage.warning('请先勾选要删除的菜单')
    return
  }
  const blocked = list.filter((r) => !canDelete(r))
  if (blocked.length) {
    ElMessage.warning('根菜单不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${list.length} 项？有子菜单的节点需先删子项。`, '删除确认', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await api.delete('/system/menus', { data: { ids: list.map((r) => r.id) } })
    ElMessage.success('删除成功')
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '删除失败')
  }
}

async function moveRow(row: MenuRow, direction: -1 | 1) {
  if (!canMove(row, direction)) return
  try {
    await api.put(`/system/menus/${row.id}/move`, null, { params: { direction } })
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '调整排序失败')
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="菜单管理"
      description="维护各子系统菜单树（增删改查落库 sys_menu）；左侧选节点查看子项，支持检索与同级排序。"
    />
    <div class="menu-mgmt" v-loading="loading">
      <aside class="menu-mgmt__side">
        <div class="menu-mgmt__side-title">菜单结构</div>
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
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索菜单名称/标题"
            class="menu-mgmt__search"
          />
          <el-button type="primary" @click="openCreate">+ 新增</el-button>
          <el-button type="danger" @click="removeRows(tableSelection)">删除</el-button>
        </div>
        <el-table
          class="portal-table"
          :data="pagedRows"
          border
          stripe
          height="100%"
          @selection-change="(v: MenuRow[]) => (tableSelection = v)"
        >
          <el-table-column type="selection" width="48" :selectable="(row: MenuRow) => canDelete(row)" />
          <el-table-column label="名称" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ displayName(row) }}</template>
          </el-table-column>
          <el-table-column prop="menuName" label="标题" min-width="140" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ typeLabel(row.menuType) }}</template>
          </el-table-column>
          <el-table-column label="访问地址" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.path || '—' }}</template>
          </el-table-column>
          <el-table-column label="排序" width="120">
            <template #default="{ row }">
              <span class="menu-mgmt__sort">
                <span class="menu-mgmt__sort-num">{{ row.sortOrder ?? 0 }}</span>
                <span class="menu-mgmt__sort-arrows">
                  <el-button
                    link
                    type="danger"
                    :disabled="!canMove(row, -1)"
                    :icon="ArrowUp"
                    @click="moveRow(row, -1)"
                  />
                  <el-button
                    link
                    type="danger"
                    :disabled="!canMove(row, 1)"
                    :icon="ArrowDown"
                    @click="moveRow(row, 1)"
                  />
                </span>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" :disabled="!canDelete(row)" @click="removeRows([row])">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="menu-mgmt__pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            background
          />
        </div>
      </section>

      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
          <el-form-item label="上级菜单">
            <el-select v-model="form.parentId" filterable style="width: 100%">
              <el-option v-for="o in parentOptions" :key="o.id" :value="o.id" :label="o.label" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
          <el-form-item label="标题" prop="menuName">
            <el-input v-model="form.menuName" placeholder="侧栏/页面显示标题" />
          </el-form-item>
          <el-form-item label="名称" prop="routeName">
            <el-input
              v-model="form.routeName"
              placeholder="可选；空则按标题/权限码自动生成"
            />
          </el-form-item>
          <el-form-item label="权限码">
            <el-input
              v-model="form.permission"
              placeholder="可选；新增空则自动生成 system:menu:custom:..."
            />
          </el-form-item>
          <el-form-item label="图标">
            <el-input v-model="form.icon" placeholder="可选" />
          </el-form-item>
          <el-form-item label="访问地址" prop="path">
            <el-input
              v-model="form.path"
              placeholder="目录/按钮可空；菜单项必填，如 /analytics/support"
            />
          </el-form-item>
          <el-form-item label="组件路径">
            <el-input v-model="form.component" placeholder="可选" />
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
  max-height: 640px;
  overflow: auto;
}
.menu-mgmt__side-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  padding: 4px 8px 12px;
  border-left: 3px solid #2f6fed;
  margin-bottom: 4px;
  line-height: 1.2;
}
.menu-mgmt__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 12px 16px 16px;
  min-height: 520px;
}
.menu-mgmt__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}
.menu-mgmt__search {
  width: 240px;
  margin-right: auto;
}
.menu-mgmt__sort {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.menu-mgmt__sort-num {
  min-width: 1.5em;
}
.menu-mgmt__sort-arrows {
  display: inline-flex;
  flex-direction: column;
  line-height: 1;
}
.menu-mgmt__sort-arrows :deep(.el-button) {
  margin: 0;
  padding: 0;
  height: 14px;
}
.menu-mgmt__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.menu-mgmt :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #e8f1ff;
  color: #1d4f91;
}
</style>
