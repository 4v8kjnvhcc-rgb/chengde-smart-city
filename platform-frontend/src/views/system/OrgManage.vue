<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox, type ElTree } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'
import { leafKeysForTreeCheck } from '@/utils/menu-tree-check'
import { encryptTransportPayload } from '@/utils/transport-crypto'

interface Org {
  id: number
  orgCode: string
  orgName: string
  parentId: number
  orgType?: number
  status: number
  children?: Org[]
}

interface UserRow {
  id: number
  username: string
  displayName: string
  orgId: number
  status: number
  roleIds?: number[]
  roleNames?: string[]
}

interface Role {
  id: number
  roleName: string
  roleCode?: string
}

interface MenuRow {
  id: number
  parentId: number
  menuName: string
  menuType: number
}

interface CheckNode {
  id: number
  label: string
  children?: CheckNode[]
}

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const orgs = ref<Org[]>([])
const roles = ref<Role[]>([])
const selectedOrgId = ref<number | null>(null)
const users = ref<UserRow[]>([])
const usersLoading = ref(false)
const userTotal = ref(0)
const userPage = ref(1)
const userKeyword = ref('')
const orgFilterKeyword = ref('')
const orgTreeRef = ref<InstanceType<typeof ElTree>>()

const orgNameById = computed(() => {
  const m = new Map<number, string>()
  for (const o of orgs.value) m.set(o.id, o.orgName)
  return m
})

/** 根组织（parentId=0）；进入页默认选中根，右侧展示全部账号 */
const rootOrg = computed(() => orgs.value.find((o) => !o.parentId || o.parentId === 0) || null)
const isRootSelected = computed(() => {
  if (!selectedOrgId.value || !rootOrg.value) return false
  return selectedOrgId.value === rootOrg.value.id
})
/** 右侧是否展示跨单位账号（根节点，或关键字搜全部） */
const showingAllAccounts = computed(
  () => isRootSelected.value || !!userKeyword.value.trim(),
)

const orgDialogVisible = ref(false)
const orgEditVisible = ref(false)
const userDialogVisible = ref(false)
const userEditVisible = ref(false)
const permDialogVisible = ref(false)
const submitting = ref(false)

const orgForm = reactive({ orgCode: '', orgName: '', parentId: 0 as number })
const orgEditForm = reactive({ id: 0, orgName: '', parentId: 0 as number, status: 1 })
const userForm = reactive({
  username: '',
  password: 'Test@12345',
  displayName: '',
  phone: '',
  orgId: undefined as number | undefined,
  roleIds: [] as number[],
})
const userEditForm = reactive({
  id: 0,
  displayName: '',
  status: 1,
  orgId: undefined as number | undefined,
  roleIds: [] as number[],
})

const permTree = ref<CheckNode[]>([])
const permTreeRef = ref<InstanceType<typeof ElTree>>()
const permTitle = ref('')

const selectedOrg = computed(() => orgs.value.find((o) => o.id === selectedOrgId.value) || null)

const orgTree = computed(() => {
  const map = new Map<number, Org>()
  const roots: Org[] = []
  for (const o of orgs.value) {
    map.set(o.id, { ...o, children: [] })
  }
  for (const o of map.values()) {
    const p = o.parentId
    if (p && map.has(p)) map.get(p)!.children!.push(o)
    else roots.push(o)
  }
  const sortRec = (list: Org[]) => {
    list.sort((a, b) => (a.orgCode || '').localeCompare(b.orgCode || '', 'zh-CN'))
    list.forEach((n) => n.children && sortRec(n.children))
  }
  sortRec(roots)
  return roots
})

function buildCheckTree(rows: MenuRow[]): CheckNode[] {
  const map = new Map<number, CheckNode>()
  const roots: CheckNode[] = []
  for (const r of rows) {
    const suffix = r.menuType === 1 ? ' [目录]' : ''
    map.set(r.id, { id: r.id, label: `${r.menuName}${suffix}`, children: [] })
  }
  for (const r of rows) {
    const node = map.get(r.id)!
    if (!r.parentId || r.parentId === 0 || !map.has(r.parentId)) roots.push(node)
    else map.get(r.parentId)!.children!.push(node)
  }
  const prune = (nodes: CheckNode[]) => {
    for (const n of nodes) {
      if (n.children?.length === 0) delete n.children
      else if (n.children) prune(n.children)
    }
  }
  prune(roots)
  return roots
}

async function loadOrgs() {
  const res = await api.get('/system/orgs')
  orgs.value = res.data || []
  if (!orgs.value.length) return
  const rootId = orgs.value.find((o) => !o.parentId || o.parentId === 0)?.id || orgs.value[0].id
  const fromQuery = Number(route.query.orgId)
  // 无合法 query 时默认根组织
  if (!selectedOrgId.value) {
    selectedOrgId.value =
      Number.isFinite(fromQuery) && fromQuery > 0 && orgs.value.some((o) => o.id === fromQuery)
        ? fromQuery
        : rootId
  }
}

async function loadRoles() {
  const res = await api.get('/system/roles')
  roles.value = res.data || []
}

async function loadUsers() {
  if (!selectedOrgId.value && !userKeyword.value.trim()) {
    users.value = []
    userTotal.value = 0
    return
  }
  const kw = userKeyword.value.trim()
  // 根组织或关键字搜索：不传 orgId → 全部账号；非根：仅该组织
  const orgIdParam =
    kw || isRootSelected.value ? undefined : selectedOrgId.value || undefined
  usersLoading.value = true
  try {
    const res = await api.get('/system/users', {
      params: {
        page: userPage.value,
        size: 20,
        orgId: orgIdParam,
        keyword: kw || undefined,
      },
    })
    users.value = res.data?.records || []
    userTotal.value = res.data?.total || 0
  } finally {
    usersLoading.value = false
  }
}

function selectOrg(id: number) {
  selectedOrgId.value = id
  userPage.value = 1
  userKeyword.value = ''
  router.replace({ query: { ...route.query, orgId: String(id) } })
}

function filterOrgNode(value: string, data: Org) {
  if (!value) return true
  const kw = value.trim().toLowerCase()
  return (
    (data.orgName || '').toLowerCase().includes(kw) ||
    (data.orgCode || '').toLowerCase().includes(kw)
  )
}

watch(orgFilterKeyword, (val) => {
  orgTreeRef.value?.filter(val)
})

function runUserSearch() {
  userPage.value = 1
  void loadUsers()
}

function resetUserSearch() {
  userKeyword.value = ''
  userPage.value = 1
  void loadUsers()
}

watch(selectedOrgId, () => {
  void loadUsers()
})

/** 在组织树选中节点下新增下级机构 */
async function openCreateChildOrg() {
  if (!selectedOrgId.value) {
    ElMessage.warning('请先在组织树中选择上级单位')
    return
  }
  orgForm.orgCode = ''
  orgForm.orgName = ''
  orgForm.parentId = selectedOrgId.value
  orgDialogVisible.value = true
}

function openEditOrg() {
  if (!selectedOrg.value) {
    ElMessage.warning('请先选择要编辑的单位')
    return
  }
  const row = selectedOrg.value
  orgEditForm.id = row.id
  orgEditForm.orgName = row.orgName
  orgEditForm.parentId = row.parentId
  orgEditForm.status = row.status ?? 1
  orgEditVisible.value = true
}

async function submitCreateOrg() {
  if (!orgForm.orgCode || !orgForm.orgName) {
    ElMessage.warning('请填写编码与名称')
    return
  }
  if (!orgForm.parentId) {
    ElMessage.warning('请选择上级单位')
    return
  }
  submitting.value = true
  try {
    await api.post('/system/orgs', orgForm)
    ElMessage.success('下级机构已创建')
    orgDialogVisible.value = false
    await loadOrgs()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    submitting.value = false
  }
}

async function submitEditOrg() {
  submitting.value = true
  try {
    await api.put(`/system/orgs/${orgEditForm.id}`, {
      orgName: orgEditForm.orgName,
      parentId: orgEditForm.parentId,
      status: orgEditForm.status,
    })
    ElMessage.success('机构已更新')
    orgEditVisible.value = false
    await loadOrgs()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    submitting.value = false
  }
}

async function removeOrg() {
  if (!selectedOrg.value) {
    ElMessage.warning('请先选择要删除的单位')
    return
  }
  const row = selectedOrg.value
  try {
    await ElMessageBox.confirm(`确认删除机构「${row.orgName}」？`, '删除机构', { type: 'warning' })
    await api.delete(`/system/orgs/${row.id}`)
    ElMessage.success('机构已删除')
    if (selectedOrgId.value === row.id) selectedOrgId.value = null
    orgEditVisible.value = false
    await loadOrgs()
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

function openCreateUser() {
  if (!selectedOrgId.value) {
    ElMessage.warning('请先选择左侧组织')
    return
  }
  userForm.username = ''
  userForm.displayName = ''
  userForm.phone = ''
  userForm.password = 'Test@12345'
  userForm.orgId = selectedOrgId.value
  userForm.roleIds = []
  userDialogVisible.value = true
}

async function openEditUser(row: UserRow) {
  userEditForm.id = row.id
  userEditForm.displayName = row.displayName
  userEditForm.status = row.status
  userEditForm.orgId = row.orgId
  userEditForm.roleIds = row.roleIds?.length
    ? [...row.roleIds]
    : ((await api.get(`/system/users/${row.id}/roles`)).data || [])
  userEditVisible.value = true
}

async function submitCreateUser() {
  if (!userForm.username || !userForm.password || !userForm.displayName || !userForm.phone?.trim() || !userForm.orgId) {
    ElMessage.warning('请填写完整信息（含联系方式）')
    return
  }
  submitting.value = true
  try {
    const passwordTransport = await encryptTransportPayload({ password: userForm.password })
    await api.post('/system/users', {
      username: userForm.username,
      passwordTransport,
      displayName: userForm.displayName,
      phone: userForm.phone,
      orgId: userForm.orgId,
      roleIds: userForm.roleIds,
    })
    ElMessage.success('用户已创建')
    userDialogVisible.value = false
    await loadUsers()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    submitting.value = false
  }
}

async function submitEditUser() {
  submitting.value = true
  try {
    await api.put(`/system/users/${userEditForm.id}`, {
      displayName: userEditForm.displayName,
      status: userEditForm.status,
      orgId: userEditForm.orgId,
      roleIds: userEditForm.roleIds,
    })
    ElMessage.success('用户已更新')
    userEditVisible.value = false
    await loadUsers()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '更新失败')
  } finally {
    submitting.value = false
  }
}

async function disableUser(row: UserRow) {
  try {
    await ElMessageBox.confirm(`确认禁用用户「${row.username}」？`, '禁用用户', { type: 'warning' })
    await api.delete(`/system/users/${row.id}`)
    ElMessage.success('用户已禁用')
    await loadUsers()
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '禁用失败')
    }
  }
}

async function resetPassword(row: UserRow) {
  try {
    const { value } = await ElMessageBox.prompt(`为用户「${row.username}」设置新密码`, '重置密码', {
      inputType: 'password',
      inputValue: 'Test@12345',
    })
    const envelope = await encryptTransportPayload({ password: value })
    await api.put(`/system/users/${row.id}/password`, envelope)
    ElMessage.success('密码已重置')
  } catch (e: unknown) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e instanceof Error ? e.message : '重置失败')
    }
  }
}

/** 只读查看：该用户角色合并后的菜单权限 */
async function viewUserPerms(row: UserRow) {
  permTitle.value = `${row.displayName || row.username} · 菜单权限（只读）`
  const roleIds = row.roleIds?.length
    ? row.roleIds
    : ((await api.get(`/system/users/${row.id}/roles`)).data as number[]) || []
  const [menusRes, ...assigned] = await Promise.all([
    api.get('/system/menus'),
    ...roleIds.map((rid) => api.get(`/system/roles/${rid}/menus`)),
  ])
  const menuIds = new Set<number>()
  for (const res of assigned) {
    for (const id of res.data || []) menuIds.add(Number(id))
  }
  const rows = (menusRes.data || []) as MenuRow[]
  permTree.value = buildCheckTree(rows)
  permDialogVisible.value = true
  await nextTick()
  permTreeRef.value?.setCheckedKeys(leafKeysForTreeCheck(rows, [...menuIds]), false)
}

onMounted(async () => {
  await Promise.all([loadOrgs(), loadRoles()])
  await loadUsers()
})
</script>

<template>
  <div>
    <PageHeader title="组织与账号" description="在组织树中点选单位，查看并管理该单位下的账号、角色与菜单权限">
      <el-button
        type="primary"
        :disabled="!selectedOrgId"
        @click="openCreateUser"
      >
        新建账号
      </el-button>
    </PageHeader>

    <div class="org-user-layout">
      <PageCard class="org-pane" title="组织机构">
        <div class="org-search">
          <el-input
            v-model="orgFilterKeyword"
            clearable
            size="small"
            placeholder="搜索组织（名称/编码，含各级）"
            @clear="orgTreeRef?.filter('')"
          />
        </div>
        <div class="org-toolbar">
          <el-button
            type="primary"
            size="small"
            :disabled="!selectedOrgId"
            @click="openCreateChildOrg"
          >
            新增下级
          </el-button>
          <el-button
            size="small"
            :disabled="!selectedOrgId"
            @click="openEditOrg"
          >
            编辑
          </el-button>
          <el-button
            type="danger"
            plain
            size="small"
            :disabled="!selectedOrgId"
            @click="removeOrg"
          >
            删除
          </el-button>
        </div>
        <el-tree
          ref="orgTreeRef"
          :data="orgTree"
          node-key="id"
          default-expand-all
          highlight-current
          :current-node-key="selectedOrgId ?? undefined"
          :filter-node-method="filterOrgNode"
          :props="{ label: 'orgName', children: 'children' }"
          @node-click="(data: Org) => selectOrg(data.id)"
        >
          <template #default="{ data }">
            <span class="org-node">{{ data.orgName }}</span>
          </template>
        </el-tree>
      </PageCard>

      <PageCard class="user-pane">
        <template #header>
          <div class="user-pane-header">
            <span class="user-pane-title">
              {{
                userKeyword.trim()
                  ? '全部账号（搜索）'
                  : isRootSelected && selectedOrg
                    ? `${selectedOrg.orgName} · 全部账号`
                    : selectedOrg
                      ? `${selectedOrg.orgName} · 账号`
                      : '请选择组织'
              }}
            </span>
            <div class="user-pane-search">
              <el-input
                v-model="userKeyword"
                clearable
                size="small"
                placeholder="搜索全部账号（用户名/姓名）"
                style="width: 220px"
                @clear="runUserSearch"
                @keyup.enter="runUserSearch"
              />
              <el-button type="primary" size="small" @click="runUserSearch">查询</el-button>
              <el-button size="small" @click="resetUserSearch">重置</el-button>
            </div>
          </div>
        </template>

        <template v-if="selectedOrg">
          <el-table class="portal-table" :data="users" v-loading="usersLoading" stripe>
            <el-table-column prop="username" label="用户名" min-width="110" />
            <el-table-column prop="displayName" label="姓名" min-width="100" />
            <el-table-column v-if="showingAllAccounts" label="所属组织" min-width="160">
              <template #default="{ row }">
                {{ orgNameById.get(row.orgId) || row.orgId || '—' }}
              </template>
            </el-table-column>
            <el-table-column label="角色" min-width="160">
              <template #default="{ row }">
                <template v-if="row.roleNames?.length">
                  <el-tag
                    v-for="name in row.roleNames"
                    :key="name"
                    size="small"
                    class="role-tag"
                  >
                    {{ name }}
                  </el-tag>
                </template>
                <span v-else class="muted">未分配</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ statusLabel(row.status === 1 ? 'ACTIVE' : 'DISABLED') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  @click="openEditUser(row)"
                >
                  编辑角色
                </el-button>
                <el-button link type="primary" @click="viewUserPerms(row)">看权限</el-button>
                <el-button
                  link
                  type="primary"
                  @click="resetPassword(row)"
                >
                  重置密码
                </el-button>
                <el-button
                  v-if="row.status === 1"
                  link
                  type="danger"
                  @click="disableUser(row)"
                >
                  禁用
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pager"
            layout="total, prev, pager, next"
            :total="userTotal"
            :current-page="userPage"
            @current-change="(p: number) => { userPage = p; loadUsers() }"
          />
        </template>
        <el-empty v-else description="请从左侧选择一个组织单位，或在右上角搜索全部账号" />
      </PageCard>
    </div>

    <el-dialog v-model="orgDialogVisible" title="新增下级机构" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="上级单位">
          <el-input :model-value="selectedOrg?.orgName" disabled />
        </el-form-item>
        <el-form-item label="编码" required><el-input v-model="orgForm.orgCode" placeholder="机构编码，需唯一" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="orgForm.orgName" placeholder="机构名称" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orgDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreateOrg">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="orgEditVisible" title="编辑机构" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="名称" required><el-input v-model="orgEditForm.orgName" /></el-form-item>
        <el-form-item label="上级机构">
          <el-select v-model="orgEditForm.parentId" filterable style="width: 100%">
            <el-option :value="0" label="无（顶级）" />
            <el-option
              v-for="o in orgs.filter((x) => x.id !== orgEditForm.id)"
              :key="o.id"
              :label="o.orgName"
              :value="o.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="orgEditForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orgEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEditOrg">保存</el-button>
        <el-button
          type="danger"
          plain
          @click="removeOrg"
        >
          删除
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userDialogVisible" title="为本单位新建账号" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="所属单位">
          <el-input :model-value="selectedOrg?.orgName" disabled />
        </el-form-item>
        <el-form-item label="用户名" required><el-input v-model="userForm.username" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="userForm.displayName" /></el-form-item>
        <el-form-item label="联系方式" required>
          <el-input v-model="userForm.phone" placeholder="手机号或座机，必填" maxlength="32" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.roleIds" multiple filterable placeholder="如：机构管理员" style="width: 100%">
            <el-option
              v-for="r in roles"
              :key="r.id"
              :label="r.roleCode ? `${r.roleName}（${r.roleCode}）` : r.roleName"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreateUser">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userEditVisible" title="编辑账号与角色" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="姓名" required><el-input v-model="userEditForm.displayName" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="userEditForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所属机构">
          <el-select v-model="userEditForm.orgId" filterable style="width: 100%">
            <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userEditForm.roleIds" multiple filterable style="width: 100%">
            <el-option
              v-for="r in roles"
              :key="r.id"
              :label="r.roleCode ? `${r.roleName}（${r.roleCode}）` : r.roleName"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEditUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permDialogVisible" :title="permTitle" width="520px" destroy-on-close>
      <p class="hint">勾选状态为当前用户通过角色合并得到的菜单权限（只读，改权限请到「用户中心 · 角色菜单权限」）。</p>
      <el-tree
        ref="permTreeRef"
        :data="permTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ label: 'label', children: 'children', disabled: () => true }"
      />
      <template #footer>
        <el-button type="primary" @click="permDialogVisible = false">关闭</el-button>
        <el-button @click="router.push('/system/uum?tab=users')">去改角色菜单</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<style scoped>
.org-user-layout {
  display: grid;
  grid-template-columns: minmax(260px, 320px) 1fr;
  gap: 16px;
  align-items: start;
}
.org-pane {
  max-height: calc(100vh - 180px);
  overflow: auto;
}
.org-search {
  margin-bottom: 10px;
}
.org-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.user-pane {
  min-width: 0;
}
.user-pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.user-pane-title {
  font-weight: 600;
  flex-shrink: 0;
}
.user-pane-search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.org-node {
  font-size: 13px;
}
.role-tag { margin-right: 4px; margin-bottom: 2px; }
.muted { color: var(--el-text-color-secondary); }
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; line-height: 1.5; font-size: 13px; }
.pager { margin-top: 16px; justify-content: flex-end; }

@media (max-width: 960px) {
  .org-user-layout { grid-template-columns: 1fr; }
  .user-pane-header { flex-direction: column; align-items: stretch; }
  .user-pane-search { margin-left: 0; }
  .user-pane-search .el-input { width: 100% !important; }
}
</style>
