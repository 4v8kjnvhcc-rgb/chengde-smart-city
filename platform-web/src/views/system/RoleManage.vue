<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, type ElTree } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Role {
  id: number
  roleCode: string
  roleName: string
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
  children?: TreeNode[]
}

const roles = ref<Role[]>([])
const menuDialogVisible = ref(false)
const currentRoleId = ref<number | null>(null)
const menuTree = ref<TreeNode[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const saving = ref(false)

function buildTree(rows: MenuRow[]): TreeNode[] {
  const map = new Map<number, TreeNode>()
  const roots: TreeNode[] = []
  for (const r of rows) {
    if (r.menuType === 3) continue
    map.set(r.id, { id: r.id, label: r.menuName, children: [] })
  }
  for (const r of rows) {
    if (r.menuType === 3) continue
    const node = map.get(r.id)!
    if (!r.parentId || r.parentId === 0 || !map.has(r.parentId)) {
      roots.push(node)
    } else {
      map.get(r.parentId)!.children!.push(node)
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
  const res = await api.get('/system/roles')
  roles.value = res.data
}

async function openMenuConfig(role: Role) {
  currentRoleId.value = role.id
  menuDialogVisible.value = true
  const [menusRes, assignedRes] = await Promise.all([
    api.get('/system/menus'),
    api.get(`/system/roles/${role.id}/menus`),
  ])
  menuTree.value = buildTree(menusRes.data)
  await nextTick()
  treeRef.value?.setCheckedKeys(assignedRes.data, false)
}

async function saveMenus() {
  if (!currentRoleId.value || !treeRef.value) return
  saving.value = true
  try {
    const checked = treeRef.value.getCheckedKeys(false) as number[]
    const half = treeRef.value.getHalfCheckedKeys() as number[]
    const menuIds = [...new Set([...checked, ...half])]
    await api.put(`/system/roles/${currentRoleId.value}/menus`, { menuIds })
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
    <PageHeader title="角色管理" description="管理系统角色与菜单授权" />
    <PageCard>
      <el-table class="portal-table" :data="roles" stripe>
        <el-table-column prop="roleCode" label="编码" min-width="140" />
        <el-table-column prop="roleName" label="名称" min-width="140" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openMenuConfig(row)">配置菜单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog v-model="menuDialogVisible" title="配置菜单权限" width="520px" destroy-on-close>
      <el-tree
        ref="treeRef"
        :data="menuTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ label: 'label', children: 'children' }"
      />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveMenus">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
