<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox, type ElTree } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel } from '@/utils/status-label'
import { leafKeysForTreeCheck } from '@/utils/menu-tree-check'
import { useAuthStore } from '@/stores/auth'

interface Role {
  id: number
  roleCode: string
  roleName: string
  roleType?: number
  description?: string
  status: number
}

interface MenuRow {
  id: number
  parentId: number
  menuName: string
  menuType: number
}

interface TreeNode {
  id: number
  label: string
  disabled?: boolean
  children?: TreeNode[]
}

const auth = useAuthStore()
const roles = ref<Role[]>([])
const loading = ref(false)
const keyword = ref('')
const { page, pageSize, paged: pagedRoles, total: roleTotal, resetPage: resetRolePage } = useClientPager(roles)
const menuDialogVisible = ref(false)
const formDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const currentRoleId = ref<number | null>(null)
const currentRoleCode = ref<string>('')
const menuTree = ref<TreeNode[]>([])
/** 扁平菜单行，保存时剔除有下级的目录 id */
const menuFlatRows = ref<MenuRow[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const saving = ref(false)
const submitting = ref(false)
/** 系统管理员角色后端强制全量授权，勾选树仅供查看 */
const isSystemAdminRole = computed(() => currentRoleCode.value === 'SYSTEM_ADMIN')

const form = reactive({
  roleCode: '',
  roleName: '',
  description: '',
  status: 1,
})

/** 有角色管理菜单即可增删改（内置角色仍不可删） */
const canAdd = computed(() => true)
const canEdit = computed(() => true)
const canDelete = computed(() => true)

function isBuiltin(row: Role) {
  return row.roleCode === 'SYSTEM_ADMIN' || row.roleType === 1
}

function buildTree(rows: MenuRow[], lockAll = false): TreeNode[] {
  const map = new Map<number, TreeNode>()
  const roots: TreeNode[] = []
  for (const r of rows) {
    const id = Number(r.id)
    const suffix = r.menuType === 1 ? ' [目录]' : ''
    map.set(id, { id, label: `${r.menuName}${suffix}`, disabled: lockAll, children: [] })
  }
  for (const r of rows) {
    const id = Number(r.id)
    const parentId = Number(r.parentId || 0)
    const node = map.get(id)!
    if (!parentId || !map.has(parentId)) {
      roots.push(node)
    } else {
      map.get(parentId)!.children!.push(node)
    }
  }
  const prune = (nodes: TreeNode[]) => {
    for (const n of nodes) {
      if (n.children?.length === 0) delete n.children
      else if (n.children) prune(n.children)
    }
  }
  prune(roots)
  return roots
}

async function loadRoles() {
  loading.value = true
  try {
    const res = await api.get('/system/roles', {
      params: {
        keyword: keyword.value.trim() || undefined,
        includeDisabled: true,
      },
    })
    roles.value = res.data || []
    resetRolePage()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.roleCode = ''
  form.roleName = ''
  form.description = ''
  form.status = 1
  formDialogVisible.value = true
}

function openEdit(row: Role) {
  editingId.value = row.id
  form.roleCode = row.roleCode
  form.roleName = row.roleName
  form.description = row.description || ''
  form.status = row.status
  formDialogVisible.value = true
}

async function submitForm() {
  if (!form.roleName.trim()) {
    ElMessage.warning('请填写角色名称')
    return
  }
  if (!editingId.value && !form.roleCode.trim()) {
    ElMessage.warning('请填写角色编码')
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await api.put(`/system/roles/${editingId.value}`, {
        roleName: form.roleName.trim(),
        description: form.description.trim(),
        status: form.status,
      })
      ElMessage.success('角色已更新')
    } else {
      await api.post('/system/roles', {
        roleCode: form.roleCode.trim(),
        roleName: form.roleName.trim(),
        description: form.description.trim() || undefined,
        roleType: 2,
      })
      ElMessage.success('角色已创建')
    }
    formDialogVisible.value = false
    await loadRoles()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function removeRole(row: Role) {
  if (isBuiltin(row)) {
    ElMessage.warning('系统内置角色不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.roleName}」？删除后不可恢复。`, '删除角色', {
      type: 'warning',
    })
    await api.delete(`/system/roles/${row.id}`)
    ElMessage.success('角色已删除')
    await loadRoles()
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

async function openMenuConfig(role: Role) {
  currentRoleId.value = role.id
  currentRoleCode.value = role.roleCode || ''
  menuTree.value = []
  menuDialogVisible.value = true
  try {
    const menusRes = await api.get('/system/menus')
    const rows = Array.isArray(menusRes.data) ? (menusRes.data as MenuRow[]) : []
    menuFlatRows.value = rows
    menuTree.value = buildTree(rows, role.roleCode === 'SYSTEM_ADMIN')
    if (!rows.length) {
      ElMessage.warning('暂无可用菜单数据，请确认系统菜单已恢复')
      return
    }
    try {
      const assignedRes = await api.get(`/system/roles/${role.id}/menus`)
      await nextTick()
      const leafKeys = leafKeysForTreeCheck(rows, assignedRes.data || [])
      treeRef.value?.setCheckedKeys(leafKeys, false)
      if (role.roleCode === 'SYSTEM_ADMIN') {
        ElMessage.info('系统管理员固定全量菜单，取消勾选保存后也不会生效；请对业务角色配置')
      }
    } catch (e: unknown) {
      ElMessage.warning(e instanceof Error ? e.message : '已加载菜单树，但未能读取该角色已授权项')
    }
  } catch (e: unknown) {
    menuTree.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载菜单失败')
  }
}

async function saveMenus() {
  if (!currentRoleId.value || !treeRef.value) return
  if (isSystemAdminRole.value) {
    ElMessage.warning('系统管理员角色固定拥有全部菜单，无法按勾选削减。请改用业务角色配置菜单。')
    menuDialogVisible.value = false
    return
  }
  saving.value = true
  try {
    const checked = treeRef.value.getCheckedKeys(false) as number[]
    // 只存叶子/空目录；全选时 Element 会把有下级的父 id 一并返回，入库后 Hub 祖先 visible=0 会裁掉子项
    const menuIds = leafKeysForTreeCheck(menuFlatRows.value, checked)
    await api.put(`/system/roles/${currentRoleId.value}/menus`, { menuIds })
    await auth.fetchProfile()
    ElMessage.success('菜单权限已保存')
    menuDialogVisible.value = false
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadRoles)
</script>

<template>
  <div>
    <PageHeader title="角色管理" description="管理系统角色与菜单授权">
      <el-button v-if="canAdd" type="primary" @click="openCreate">新增角色</el-button>
    </PageHeader>
    <PageCard>
      <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent>
        <el-form-item label="关键字" class="portal-field-lg">
          <el-input
            v-model="keyword"
            clearable
            placeholder="编码 / 名称"
            @keyup.enter="loadRoles"
          />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :loading="loading" @click="loadRoles">查询</el-button>
          <el-button
            @click="
              keyword = '';
              loadRoles()
            "
          >
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-table class="portal-table" :data="pagedRoles" v-loading="loading" stripe>
        <el-table-column prop="roleCode" label="编码" min-width="140" />
        <el-table-column prop="roleName" label="名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ row.roleType === 1 ? '系统' : '业务' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openMenuConfig(row)">配置菜单</el-button>
            <el-button v-if="canEdit" type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="canDelete && !isBuiltin(row)"
              type="danger"
              link
              @click="removeRole(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-if="roleTotal"
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="roleTotal"
      />
    </PageCard>

    <el-dialog
      v-model="formDialogVisible"
      :title="editingId ? '编辑角色' : '新增角色'"
      width="480px"
      destroy-on-close
    >
      <el-form label-width="88px">
        <el-form-item label="角色编码" required>
          <el-input
            v-model="form.roleCode"
            :disabled="!!editingId"
            placeholder="如 DEPT_AUDITOR"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.roleName" placeholder="显示名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="可选"
            maxlength="255"
          />
        </el-form-item>
        <el-form-item v-if="editingId" label="状态">
          <el-select v-model="form.status" style="width: 160px">
            <el-option :value="1" label="启用" />
            <el-option :value="0" label="停用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="menuDialogVisible" title="配置菜单权限" width="520px" destroy-on-close>
      <el-alert
        v-if="isSystemAdminRole"
        type="warning"
        :closable="false"
        show-icon
        class="menu-role-alert"
        title="系统管理员固定拥有全部菜单权限，取消勾选后保存也不会生效。请对业务角色做权限裁剪。"
      />
      <el-tree
        v-if="menuTree.length"
        ref="treeRef"
        :data="menuTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
        empty-text="暂无菜单"
      />
      <el-empty v-else description="暂无菜单数据，请重新登录后再试" :image-size="72" />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!menuTree.length || isSystemAdminRole"
          @click="saveMenus"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.menu-role-alert {
  margin-bottom: 12px;
}
</style>
