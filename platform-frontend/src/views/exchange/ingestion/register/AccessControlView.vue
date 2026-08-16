<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type ElTree } from 'element-plus'
import api from '@/api/http'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel } from '@/utils/status-label'
import { leafKeysForTreeCheck } from '@/utils/menu-tree-check'
import { ingestionApi, useIngestionLoading, type Project } from '../useIngestionHub'

/** 归集 Hub 内访问控制入口 */
const HUB_ACCESS_ROUTE = { path: '/exchange/ingestion', query: { system: 'register', module: 'm048' } }

interface RoleRow {
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

const route = useRoute()
const router = useRouter()
const { loading, loadError, withLoad } = useIngestionLoading()

/** 有访问控制菜单即可使用页内全部能力 */
const canManageProjectGrant = computed(() => true)
const canManageDataGrant = computed(() => true)
const canApplyCross = computed(() => true)
const canApproveCross = computed(() => true)

const activeTab = ref('overview')
const overview = ref<Record<string, unknown> | null>(null)

/** 项目授权下拉用 */
const roles = ref<Array<{ id: number; roleCode: string; roleName: string }>>([])
/** 功能权限 Tab：与统一用户「角色管理」同源，仅查看 */
const functionRoles = ref<RoleRow[]>([])
const functionKeyword = ref('')
const functionLoading = ref(false)
const {
  page: functionPage,
  pageSize: functionPageSize,
  paged: pagedFunctionRoles,
  total: functionRoleTotal,
  resetPage: resetFunctionRolePage,
} = useClientPager(functionRoles)

const menuViewVisible = ref(false)
const menuViewLoading = ref(false)
const menuViewRoleName = ref('')
const menuViewTree = ref<TreeNode[]>([])
const menuViewTreeRef = ref<InstanceType<typeof ElTree>>()

const orgs = ref<Array<{ id: number; orgName: string }>>([])
const users = ref<Array<{ id: number; displayName: string; username: string; orgId: number }>>([])
const projects = ref<Project[]>([])

const projectGrants = ref<Record<string, unknown>[]>([])
const dataGrants = ref<Record<string, unknown>[]>([])
const crossRequests = ref<Record<string, unknown>[]>([])

const projectGrantForm = reactive({
  projectId: undefined as number | undefined,
  granteeType: 'USER',
  granteeId: undefined as number | undefined,
  perm: 'VIEW',
})
const dataGrantForm = reactive({
  scopeType: 'TABLE',
  scopeId: undefined as number | undefined,
  granteeType: 'USER',
  granteeId: undefined as number | undefined,
  perm: 'READ',
})
const tables = ref<Array<{ id: number; tableName: string; tableCode: string }>>([])
const crossForm = reactive({
  targetOrgId: undefined as number | undefined,
  resourceType: 'PROJECT',
  resourceId: '',
  reason: '',
})

function buildMenuTree(rows: MenuRow[], lockAll = false): TreeNode[] {
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

async function loadOverview() {
  await withLoad(async () => {
    overview.value = (await api.get('/system/access/overview')).data
  })
}

async function loadRoles() {
  roles.value = (await api.get('/system/roles')).data || []
}

async function loadFunctionRoles() {
  functionLoading.value = true
  try {
    const res = await api.get('/system/roles', {
      params: {
        keyword: functionKeyword.value.trim() || undefined,
        includeDisabled: true,
      },
    })
    functionRoles.value = (res.data || []) as RoleRow[]
    resetFunctionRolePage()
  } catch (e: unknown) {
    functionRoles.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载角色失败')
  } finally {
    functionLoading.value = false
  }
}

async function openMenuView(role: RoleRow) {
  menuViewRoleName.value = role.roleName
  menuViewTree.value = []
  menuViewVisible.value = true
  menuViewLoading.value = true
  try {
    const menusRes = await api.get('/system/menus')
    const rows = Array.isArray(menusRes.data) ? (menusRes.data as MenuRow[]) : []
    menuViewTree.value = buildMenuTree(rows, true)
    if (!rows.length) {
      ElMessage.warning('暂无可用菜单数据')
      return
    }
    const assignedRes = await api.get(`/system/roles/${role.id}/menus`)
    await nextTick()
    const leafKeys = leafKeysForTreeCheck(rows, assignedRes.data || [])
    menuViewTreeRef.value?.setCheckedKeys(leafKeys, false)
  } catch (e: unknown) {
    menuViewTree.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载菜单权限失败')
  } finally {
    menuViewLoading.value = false
  }
}

async function loadOrgs() {
  try {
    orgs.value = (await api.get('/system/orgs')).data || []
  } catch {
    orgs.value = []
  }
}

async function loadUsers() {
  try {
    const res = await api.get('/system/access/users-for-project-grant')
    users.value = (res.data || []) as typeof users.value
  } catch {
    users.value = []
  }
}

async function loadProjects() {
  projects.value = (await ingestionApi.projects()).data || []
}

async function loadTables() {
  tables.value = ((await ingestionApi.tables()).data || []) as typeof tables.value
}

async function loadProjectGrants() {
  projectGrants.value = (await api.get('/system/access/project-grants')).data || []
}

async function loadDataGrants() {
  dataGrants.value = (await api.get('/system/access/data-grants')).data || []
}

async function loadCross() {
  crossRequests.value = (await api.get('/system/access/cross-dept/requests')).data || []
}

watch(activeTab, async (tab) => {
  if (tab === 'overview') await loadOverview()
  else if (tab === 'function') await loadFunctionRoles()
  else if (tab === 'resource') {
    await Promise.all([loadProjects(), loadUsers(), loadProjectGrants()])
    await loadRoles()
  } else if (tab === 'data') {
    await Promise.all([loadTables(), loadUsers(), loadDataGrants()])
  } else if (tab === 'cross') {
    await Promise.all([loadOrgs(), loadCross()])
  }
})

const ACCESS_TABS = new Set(['overview', 'function', 'resource', 'data', 'cross'])

function applyProjectFromQuery() {
  const raw = route.query.projectId
  const pid = Number(Array.isArray(raw) ? raw[0] : raw)
  if (Number.isFinite(pid) && pid > 0) {
    projectGrantForm.projectId = pid
  }
}

function applyTabFromQuery(): string | null {
  const raw = route.query.accessTab
  const tab = String(Array.isArray(raw) ? raw[0] : raw || '')
  return ACCESS_TABS.has(tab) ? tab : null
}

watch(
  () => [route.query.accessTab, route.query.projectId] as const,
  () => {
    applyProjectFromQuery()
    const tab = applyTabFromQuery()
    if (tab && activeTab.value !== tab) activeTab.value = tab
  },
)

async function createProjectGrant() {
  if (!projectGrantForm.projectId || !projectGrantForm.granteeId) {
    ElMessage.warning('请选择项目与授权对象')
    return
  }
  try {
    await api.post('/system/access/project-grants', { ...projectGrantForm })
    ElMessage.success('项目授权已保存')
    await loadProjectGrants()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '授权失败')
  }
}

async function removeProjectGrant(id: number) {
  await ElMessageBox.confirm('确认删除该项目授权？', '提示')
  await api.delete(`/system/access/project-grants/${id}`)
  ElMessage.success('已删除')
  await loadProjectGrants()
}

async function createDataGrant() {
  if (!dataGrantForm.scopeId || !dataGrantForm.granteeId) {
    ElMessage.warning('请选择数据范围与授权对象')
    return
  }
  try {
    await api.post('/system/access/data-grants', { ...dataGrantForm })
    ElMessage.success('数据授权已保存')
    await loadDataGrants()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '授权失败')
  }
}

async function removeDataGrant(id: number) {
  await ElMessageBox.confirm('确认删除该数据授权？', '提示')
  await api.delete(`/system/access/data-grants/${id}`)
  ElMessage.success('已删除')
  await loadDataGrants()
}

async function applyCross() {
  if (!crossForm.targetOrgId || !crossForm.resourceId) {
    ElMessage.warning('请填写目标机构与资源')
    return
  }
  await api.post('/system/access/cross-dept/requests', { ...crossForm })
  ElMessage.success('已提交跨部门申请')
  await loadCross()
}

async function approveCross(id: number, pass: boolean) {
  const path = pass ? 'approve' : 'reject'
  await api.post(`/system/access/cross-dept/requests/${id}/${path}`, { comment: pass ? '同意' : '拒绝' })
  ElMessage.success(pass ? '已通过' : '已拒绝')
  await loadCross()
}

onMounted(async () => {
  // 系统管理旧入口 → 归集平台「访问控制管理」
  if (route.name === 'system-access' || route.path.endsWith('/system/access')) {
    await router.replace({
      ...HUB_ACCESS_ROUTE,
      query: {
        ...HUB_ACCESS_ROUTE.query,
        ...(route.query.accessTab ? { accessTab: String(route.query.accessTab) } : {}),
        ...(route.query.projectId ? { projectId: String(route.query.projectId) } : {}),
      },
    })
    return
  }
  applyProjectFromQuery()
  const tab = applyTabFromQuery()
  if (tab && tab !== 'overview') {
    activeTab.value = tab
    return
  }
  await loadOverview()
})
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="访问控制管理">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="总览" name="overview">
          <el-descriptions v-if="overview" :column="3" border>
            <el-descriptions-item label="角色数">{{ overview.roleCount }}</el-descriptions-item>
            <el-descriptions-item label="项目授权数">{{ overview.projectGrantCount }}</el-descriptions-item>
            <el-descriptions-item label="数据授权数">{{ overview.dataGrantCount }}</el-descriptions-item>
            <el-descriptions-item label="待审跨部门">{{ overview.pendingCrossDept }}</el-descriptions-item>
            <el-descriptions-item label="我的有效项目数">{{ (overview.myProjectIds as number[] || []).length }}</el-descriptions-item>
            <el-descriptions-item label="当前身份">
              <el-tag v-if="overview.isSystemAdmin" type="danger" size="small">系统管理员</el-tag>
              <el-tag v-if="overview.isDeptAdmin" type="warning" size="small" style="margin-left:4px">部门管理员</el-tag>
              <span v-if="!overview.isSystemAdmin && !overview.isDeptAdmin">普通用户</span>
            </el-descriptions-item>
          </el-descriptions>
          <div style="margin-top:16px">
            <el-button @click="router.push('/system/roles')">角色管理</el-button>
            <el-button @click="router.push('/system/users')">用户管理</el-button>
            <el-button @click="router.push('/system/orgs')">机构管理</el-button>
            <el-button @click="router.push('/system/menus')">菜单管理</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="功能权限" name="function">
          <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent>
            <el-form-item label="关键字" class="portal-field-lg">
              <el-input
                v-model="functionKeyword"
                clearable
                placeholder="编码 / 名称"
                @keyup.enter="loadFunctionRoles"
              />
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" :loading="functionLoading" @click="loadFunctionRoles">查询</el-button>
              <el-button
                @click="
                  functionKeyword = '';
                  loadFunctionRoles()
                "
              >
                重置
              </el-button>
            </el-form-item>
          </el-form>
          <el-table class="portal-table" :data="pagedFunctionRoles" v-loading="functionLoading" stripe>
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
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="openMenuView(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <PortalPagination
            v-if="functionRoleTotal"
            v-model:page="functionPage"
            v-model:page-size="functionPageSize"
            :total="functionRoleTotal"
          />
        </el-tab-pane>

        <el-tab-pane label="项目授权" name="resource">
          <el-form
            v-if="canManageProjectGrant"
            inline
            class="portal-inline-form portal-inline-form--block"
          >
            <el-form-item label="项目" class="portal-field-lg">
              <el-select v-model="projectGrantForm.projectId" filterable placeholder="选择项目">
                <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="对象类型" class="portal-field-sm">
              <el-select v-model="projectGrantForm.granteeType">
                <el-option label="用户" value="USER" />
                <el-option label="角色" value="ROLE" />
              </el-select>
            </el-form-item>
            <el-form-item label="对象" class="portal-field-lg">
              <el-select v-if="projectGrantForm.granteeType === 'USER'" v-model="projectGrantForm.granteeId" filterable>
                <el-option v-for="u in users" :key="u.id" :label="u.displayName || u.username" :value="u.id" />
              </el-select>
              <el-select v-else v-model="projectGrantForm.granteeId" filterable>
                <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="权限" class="portal-field-sm">
              <el-select v-model="projectGrantForm.perm">
                <el-option label="查看" value="VIEW" />
                <el-option label="编辑" value="EDIT" />
                <el-option label="管理" value="ADMIN" />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="createProjectGrant">授权</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="projectGrants" stripe size="small">
            <el-table-column prop="projectName" label="项目" />
            <el-table-column prop="granteeType" label="对象类型" width="100">
              <template #default="{ row }">{{ row.granteeType === 'USER' ? '用户' : '角色' }}</template>
            </el-table-column>
            <el-table-column prop="granteeName" label="对象" width="140" />
            <el-table-column prop="perm" label="权限" width="90">
              <template #default="{ row }">{{ statusLabel(row.perm) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button
                  v-if="canManageProjectGrant"
                  link
                  type="danger"
                  @click="removeProjectGrant(row.id as number)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="数据权限" name="data">
          <el-form
            v-if="canManageDataGrant"
            inline
            class="portal-inline-form portal-inline-form--block"
          >
            <el-form-item label="范围" class="portal-field-sm">
              <el-select v-model="dataGrantForm.scopeType">
                <el-option label="表" value="TABLE" />
                <el-option label="数据源" value="SOURCE" />
              </el-select>
            </el-form-item>
            <el-form-item label="对象表" class="portal-field-xl">
              <el-select v-model="dataGrantForm.scopeId" filterable placeholder="输入表名筛选">
                <el-option v-for="t in tables" :key="t.id" :label="`${t.tableName} (${t.tableCode})`" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="授权给" class="portal-field-lg">
              <el-select v-model="dataGrantForm.granteeId" filterable>
                <el-option v-for="u in users" :key="u.id" :label="u.displayName || u.username" :value="u.id" />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="createDataGrant">授权</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="dataGrants" stripe size="small">
            <el-table-column prop="scopeType" label="范围类型" width="100">
              <template #default="{ row }">{{ row.scopeType === 'TABLE' ? '表' : '数据源' }}</template>
            </el-table-column>
            <el-table-column prop="scopeLabel" label="资源" />
            <el-table-column prop="granteeType" label="对象类型" width="100">
              <template #default="{ row }">{{ $statusLabel(row.granteeType) }}</template>
            </el-table-column>
            <el-table-column prop="granteeName" label="对象" width="140" />
            <el-table-column prop="perm" label="权限" width="90">
              <template #default="{ row }">{{ statusLabel(row.perm) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button
                  v-if="canManageDataGrant"
                  link
                  type="danger"
                  @click="removeDataGrant(row.id as number)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="跨部门审批" name="cross">
          <el-form
            v-if="canApplyCross"
            inline
            class="portal-inline-form portal-inline-form--block"
          >
            <el-form-item label="目标机构" class="portal-field-lg">
              <el-select v-model="crossForm.targetOrgId" filterable>
                <el-option v-for="o in orgs" :key="o.id" :label="o.orgName" :value="o.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="资源类型" class="portal-field-sm">
              <el-select v-model="crossForm.resourceType">
                <el-option label="项目" value="PROJECT" />
                <el-option label="表" value="TABLE" />
                <el-option label="数据源" value="SOURCE" />
              </el-select>
            </el-form-item>
            <el-form-item label="资源ID" class="portal-field-md">
              <el-input v-model="crossForm.resourceId" placeholder="项目/表/源主键" />
            </el-form-item>
            <el-form-item label="原因" class="portal-field-xl">
              <el-input v-model="crossForm.reason" />
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="applyCross">提交申请</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="crossRequests" stripe size="small">
            <el-table-column prop="applicantName" label="申请人" width="120" />
            <el-table-column prop="targetOrgId" label="目标机构" width="100" />
            <el-table-column label="资源类型" width="100">
              <template #default="{ row }">{{ $statusLabel(row.resourceType) }}</template>
            </el-table-column>
            <el-table-column prop="resourceId" label="资源ID" width="100" />
            <el-table-column prop="reason" label="原因" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ row.statusLabel || statusLabel(row.status) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <template v-if="row.status === 0 && canApproveCross">
                  <el-button link type="primary" @click="approveCross(row.id as number, true)">通过</el-button>
                  <el-button link type="danger" @click="approveCross(row.id as number, false)">拒绝</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </PageCard>

    <el-dialog
      v-model="menuViewVisible"
      :title="`查看菜单权限 · ${menuViewRoleName}`"
      width="520px"
      destroy-on-close
    >
      <div v-loading="menuViewLoading">
        <el-tree
          v-if="menuViewTree.length"
          ref="menuViewTreeRef"
          :data="menuViewTree"
          show-checkbox
          node-key="id"
          default-expand-all
          :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
          empty-text="暂无菜单"
        />
        <el-empty v-else description="暂无菜单数据" :image-size="72" />
      </div>
      <template #footer>
        <el-button type="primary" @click="menuViewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
