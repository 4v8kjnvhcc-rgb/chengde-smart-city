<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox, type ElTree } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { statusLabel } from '@/utils/status-label'
import { leafKeysForTreeCheck } from '@/utils/menu-tree-check'
import PortalNavConfigPanel from '@/views/system/PortalNavConfigPanel.vue'
import AuthConfigManagePanel from '@/views/system/AuthConfigManagePanel.vue'
import AuditLog from '@/views/system/AuditLog.vue'
import RuntimeErrorLog from '@/views/system/RuntimeErrorLog.vue'

const navItems: HubNavItem[] = [
  { key: 'users', label: '用户中心' },
  { key: 'apps', label: '应用中心' },
  { key: 'auth', label: '认证中心' },
  { key: 'services', label: '服务中心' },
  {
    key: 'audit',
    label: '日志审计',
    children: [
      { key: 'audit.log', label: '操作审计' },
      { key: 'audit.runtime', label: '系统运行日志' },
    ],
  },
  { key: 'integration', label: '系统对接' },
  { key: 'portal', label: '门户配置' },
]

const LEAF_KEYS = new Set<string>()
function collectLeaves(items: HubNavItem[]) {
  for (const it of items) {
    if (it.children?.length) collectLeaves(it.children)
    else LEAF_KEYS.add(it.key)
  }
}
collectLeaves(navItems)

const OLD_TAB_MAP: Record<string, string> = {
  audit: 'audit.log',
}

const route = useRoute()
const router = useRouter()
const tab = ref('users')
const loading = ref(false)

const overview = ref<Record<string, unknown> | null>(null)
const apps = ref<Record<string, unknown>[]>([])
const appGrants = ref<Record<string, unknown>[]>([])
const services = ref<Record<string, unknown>[]>([])
const serviceStats = ref<Record<string, unknown>[]>([])
const approvals = ref<Record<string, unknown>[]>([])
const integrations = ref<Record<string, unknown>[]>([])
const users = ref<Array<{ id: number; displayName: string; username: string }>>([])
const roles = ref<Array<{ id: number; roleName: string; roleCode?: string }>>([])

interface MenuRow {
  id: number
  parentId: number
  menuName: string
  path?: string
  menuType: number
  mCode?: string
  permission?: string
  sortOrder?: number
  status?: number
  children?: MenuRow[]
}
interface CheckNode {
  id: number
  label: string
  children?: CheckNode[]
}

const allMenus = ref<MenuRow[]>([])
const menuCatalogTree = ref<MenuRow[]>([])
const menuCheckTree = ref<CheckNode[]>([])
const selectedRoleId = ref<number | undefined>(undefined)
const menuTreeRef = ref<InstanceType<typeof ElTree>>()
const savingMenus = ref(false)

function buildCatalogTree(rows: MenuRow[]): MenuRow[] {
  const map = new Map<number, MenuRow>()
  const roots: MenuRow[] = []
  for (const r of rows) {
    map.set(r.id, { ...r, children: [] })
  }
  for (const r of map.values()) {
    const p = r.parentId
    if (p && map.has(p)) map.get(p)!.children!.push(r)
    else roots.push(r)
  }
  const sortRec = (list: MenuRow[]) => {
    list.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.id - b.id)
    list.forEach((n) => n.children && sortRec(n.children))
  }
  sortRec(roots)
  return roots
}

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

function menuTypeLabel(t: number) {
  if (t === 1) return '目录'
  if (t === 2) return '菜单项'
  return '—'
}

const appForm = reactive({ appName: '', appType: 'WEB', endpointUrl: '/' })
const grantForm = reactive({
  appId: undefined as number | undefined,
  granteeType: 'USER',
  granteeId: undefined as number | undefined,
  perm: 'ACCESS',
})
const svcForm = reactive({ serviceName: '', servicePath: '/api/v1/', protocol: 'REST' })
const applyForm = reactive({ serviceId: undefined as number | undefined, reason: '' })

function resolveTab() {
  let t = String(route.query.tab || 'users').toLowerCase()
  if (t === 'config') {
    router.replace('/system/maintenance')
    return
  }
  if (OLD_TAB_MAP[t]) t = OLD_TAB_MAP[t]
  tab.value = LEAF_KEYS.has(t) ? t : 'users'
}

async function loadOverview() {
  overview.value = (await api.get('/system/uum/overview')).data
}

async function loadUsersTab() {
  try {
    const [ov, r, m] = await Promise.all([
      api.get('/system/uum/overview'),
      api.get('/system/roles'),
      api.get('/system/menus'),
    ])
    overview.value = ov.data
    roles.value = r.data || []
    allMenus.value = Array.isArray(m.data) ? m.data : []
    menuCatalogTree.value = buildCatalogTree(allMenus.value)
    menuCheckTree.value = buildCheckTree(allMenus.value)
    if (!selectedRoleId.value && roles.value.length) {
      selectedRoleId.value = roles.value[0].id
    }
    if (selectedRoleId.value) {
      await loadRoleMenus(selectedRoleId.value)
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载用户中心失败')
  }
}

async function loadRoleMenus(roleId: number) {
  const assignedRes = await api.get(`/system/roles/${roleId}/menus`)
  await nextTick()
  const leafKeys = leafKeysForTreeCheck(allMenus.value, assignedRes.data || [])
  menuTreeRef.value?.setCheckedKeys(leafKeys, false)
}

async function onRoleChange(roleId: number | undefined) {
  selectedRoleId.value = roleId
  if (roleId) await loadRoleMenus(roleId)
}

async function saveRoleMenus() {
  if (!selectedRoleId.value || !menuTreeRef.value) {
    ElMessage.warning('请先选择角色')
    return
  }
  const role = roles.value.find((r) => r.id === selectedRoleId.value)
  if (role?.roleCode === 'SYSTEM_ADMIN') {
    ElMessage.warning('系统管理员角色固定拥有全部菜单，无法按勾选削减。请改用业务角色配置菜单。')
    return
  }
  savingMenus.value = true
  try {
    const checked = menuTreeRef.value.getCheckedKeys(false) as number[]
    const menuIds = leafKeysForTreeCheck(allMenus.value, checked)
    await api.put(`/system/roles/${selectedRoleId.value}/menus`, { menuIds })
    ElMessage.success('角色菜单权限已保存')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    savingMenus.value = false
  }
}

async function loadAppsTab() {
  const [a, g, u, r] = await Promise.all([
    api.get('/system/uum/apps'),
    api.get('/system/uum/app-grants'),
    api.get('/system/users', { params: { page: 1, size: 200 } }),
    api.get('/system/roles'),
  ])
  apps.value = a.data || []
  appGrants.value = g.data || []
  users.value = (u.data?.records || []) as typeof users.value
  roles.value = r.data || []
}

async function loadServicesTab() {
  const [s, st, ap] = await Promise.all([
    api.get('/system/uum/services'),
    api.get('/system/uum/service-stats'),
    api.get('/system/uum/service-approvals'),
  ])
  services.value = s.data || []
  serviceStats.value = st.data || []
  approvals.value = ap.data || []
}

async function loadIntegrationTab() {
  integrations.value = (await api.get('/system/uum/integrations')).data || []
}

async function loadTabData() {
  loading.value = true
  try {
    if (tab.value === 'users') await loadUsersTab()
    else if (tab.value === 'apps') await loadAppsTab()
    else if (tab.value === 'services') await loadServicesTab()
    else if (tab.value === 'integration') await loadIntegrationTab()
  } finally {
    loading.value = false
  }
}

watch(tab, async (t) => {
  await router.replace({ path: '/system/uum', query: { tab: t } })
  await loadTabData()
})
watch(() => route.query.tab, () => {
  resolveTab()
})

async function createApp() {
  if (!appForm.appName) return
  await api.post('/system/uum/apps', { ...appForm })
  ElMessage.success('应用已注册')
  appForm.appName = ''
  await loadAppsTab()
}

async function createGrant() {
  if (!grantForm.appId || !grantForm.granteeId) {
    ElMessage.warning('请选择应用与授权对象')
    return
  }
  await api.post('/system/uum/app-grants', { ...grantForm })
  ElMessage.success('应用授权已保存')
  await loadAppsTab()
}

async function removeGrant(id: number) {
  await ElMessageBox.confirm('确认删除该应用授权？', '提示')
  await api.delete(`/system/uum/app-grants/${id}`)
  ElMessage.success('已删除')
  await loadAppsTab()
}

async function createService() {
  if (!svcForm.serviceName) return
  await api.post('/system/uum/services', { ...svcForm })
  ElMessage.success('服务已发布')
  svcForm.serviceName = ''
  await loadServicesTab()
}

async function applyService() {
  if (!applyForm.serviceId) {
    ElMessage.warning('请选择服务')
    return
  }
  await api.post('/system/uum/service-approvals', { ...applyForm })
  ElMessage.success('已提交调用申请')
  applyForm.reason = ''
  await loadServicesTab()
}

async function decide(id: number, pass: boolean) {
  const path = pass ? 'approve' : 'reject'
  await api.post(`/system/uum/service-approvals/${id}/${path}`, { comment: pass ? '同意' : '拒绝' })
  ElMessage.success(pass ? '已通过' : '已拒绝')
  await loadServicesTab()
}

async function testIntegration(id: number) {
  const res = await api.post(`/system/uum/integrations/${id}/test`, {})
  ElMessage.success(String(res.data?.message || '检测完成'))
  await loadIntegrationTab()
}

onMounted(async () => {
  resolveTab()
  await loadTabData()
})
</script>

<template>
  <div v-loading="loading" class="uum-hub-root">
    <HubSideLayout v-model="tab" :items="navItems">
      <PageCard v-if="tab === 'users'" title="用户中心">
        <p class="hint">
          账号、角色、组织与菜单权限；菜单权限按角色配置（用户通过角色获得菜单）。短信/指纹为认证扩展位。
          项目资源授权请到归集平台「访问控制管理」。
        </p>
        <el-space wrap>
          <el-button type="primary" @click="router.push('/system/orgs')">组织与账号</el-button>
          <el-button @click="router.push('/system/roles')">角色管理</el-button>
          <el-button @click="router.push('/system/menus')">菜单管理</el-button>
        </el-space>
        <el-descriptions v-if="overview" :column="3" border style="margin-top:16px">
          <el-descriptions-item label="注册应用数">{{ (overview.apps as unknown[] || []).length }}</el-descriptions-item>
          <el-descriptions-item label="应用授权数">{{ overview.appGrantCount }}</el-descriptions-item>
          <el-descriptions-item label="待审服务调用">{{ overview.pendingApprovals }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">角色菜单权限</h4>
        <p class="hint">选择角色后勾选菜单树，保存即调整该角色（及绑定用户）可见菜单。</p>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="角色" class="portal-field-xl">
            <el-select
              :model-value="selectedRoleId"
              filterable
              placeholder="选择角色"
              @update:model-value="onRoleChange"
            >
              <el-option
                v-for="r in roles"
                :key="r.id"
                :label="r.roleCode ? `${r.roleName}（${r.roleCode}）` : r.roleName"
                :value="r.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :loading="savingMenus" :disabled="!selectedRoleId" @click="saveRoleMenus">
              保存菜单权限
            </el-button>
          </el-form-item>
        </el-form>
        <div class="menu-perm-panel">
          <el-tree
            v-if="menuCheckTree.length"
            ref="menuTreeRef"
            :data="menuCheckTree"
            show-checkbox
            node-key="id"
            default-expand-all
            :props="{ label: 'label', children: 'children' }"
          />
          <el-empty v-else description="暂无菜单数据" />
        </div>

        <h4 class="section-title">菜单目录（树形）</h4>
        <el-table
          class="portal-table"
          :data="menuCatalogTree"
          row-key="id"
          stripe
          size="small"
          default-expand-all
          :tree-props="{ children: 'children' }"
          max-height="360"
        >
          <el-table-column prop="menuName" label="名称" min-width="200" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ menuTypeLabel(row.menuType) }}</template>
          </el-table-column>
          <el-table-column prop="path" label="路径" min-width="160" show-overflow-tooltip />
          <el-table-column prop="permission" label="权限码" min-width="140" show-overflow-tooltip />
          <el-table-column prop="mCode" label="M编号" width="90" />
        </el-table>
      </PageCard>

      <PageCard v-if="tab === 'apps'" title="应用中心">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="应用名" class="portal-field-lg"><el-input v-model="appForm.appName" /></el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="appForm.appType">
              <el-option label="WEB" value="WEB" /><el-option label="API" value="API" />
            </el-select>
          </el-form-item>
          <el-form-item label="入口" class="portal-field-xl"><el-input v-model="appForm.endpointUrl" /></el-form-item>
          <el-form-item class="portal-form-actions"><el-button type="primary" @click="createApp">注册应用</el-button></el-form-item>
        </el-form>
        <el-table :data="apps" stripe size="small" style="margin-bottom:16px">
          <el-table-column prop="appCode" label="编码" width="140" />
          <el-table-column prop="appName" label="名称" />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ $statusLabel(row.appType) }}</template>
          </el-table-column>
          <el-table-column prop="endpointUrl" label="入口" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
        </el-table>

        <h4>应用授权（用户/角色）</h4>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="应用" class="portal-field-lg">
            <el-select v-model="grantForm.appId" filterable>
              <el-option v-for="a in apps" :key="a.id as number" :label="String(a.appName)" :value="a.id as number" />
            </el-select>
          </el-form-item>
          <el-form-item label="对象" class="portal-field-sm">
            <el-select v-model="grantForm.granteeType">
              <el-option label="用户" value="USER" /><el-option label="角色" value="ROLE" />
            </el-select>
          </el-form-item>
          <el-form-item label="选择" class="portal-field-lg">
            <el-select v-if="grantForm.granteeType === 'USER'" v-model="grantForm.granteeId" filterable>
              <el-option v-for="u in users" :key="u.id" :label="u.displayName || u.username" :value="u.id" />
            </el-select>
            <el-select v-else v-model="grantForm.granteeId" filterable>
              <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions"><el-button type="primary" @click="createGrant">授权</el-button></el-form-item>
        </el-form>
        <el-table :data="appGrants" stripe size="small">
          <el-table-column prop="appName" label="应用" />
          <el-table-column prop="granteeType" label="类型" width="80">
            <template #default="{ row }">{{ row.granteeType === 'USER' ? '用户' : '角色' }}</template>
          </el-table-column>
          <el-table-column prop="granteeName" label="对象" width="140" />
          <el-table-column prop="perm" label="权限" width="100">
            <template #default="{ row }">{{ statusLabel(row.perm) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="danger" @click="removeGrant(row.id as number)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>

      <PageCard v-if="tab === 'auth'" title="认证中心">
        <AuthConfigManagePanel />
      </PageCard>

      <PageCard v-if="tab === 'services'" title="服务中心">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="服务名" class="portal-field-lg"><el-input v-model="svcForm.serviceName" /></el-form-item>
          <el-form-item label="路径" class="portal-field-xl"><el-input v-model="svcForm.servicePath" /></el-form-item>
          <el-form-item class="portal-form-actions"><el-button type="primary" @click="createService">发布服务</el-button></el-form-item>
        </el-form>
        <el-table :data="services" stripe size="small" style="margin-bottom:16px">
          <el-table-column prop="serviceCode" label="编码" width="140" />
          <el-table-column prop="serviceName" label="名称" />
          <el-table-column prop="servicePath" label="路径" />
          <el-table-column prop="protocol" label="协议" width="80" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
        </el-table>

        <h4>调用统计</h4>
        <el-table :data="serviceStats" stripe size="small" style="margin-bottom:16px">
          <el-table-column prop="serviceName" label="服务" />
          <el-table-column prop="callDate" label="日期" width="120" />
          <el-table-column prop="callCount" label="调用次数" width="100" />
          <el-table-column prop="successCount" label="成功" width="80" />
          <el-table-column prop="failCount" label="失败" width="80" />
          <el-table-column prop="successRate" label="成功率%" width="100" />
        </el-table>

        <h4>敏感调用审批</h4>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="服务" class="portal-field-lg">
            <el-select v-model="applyForm.serviceId" filterable>
              <el-option v-for="s in services" :key="s.id as number" :label="String(s.serviceName)" :value="s.id as number" />
            </el-select>
          </el-form-item>
          <el-form-item label="原因" class="portal-field-xl"><el-input v-model="applyForm.reason" /></el-form-item>
          <el-form-item class="portal-form-actions"><el-button type="primary" @click="applyService">提交申请</el-button></el-form-item>
        </el-form>
        <el-table :data="approvals" stripe size="small">
          <el-table-column prop="serviceName" label="服务" />
          <el-table-column prop="applicantName" label="申请人" width="120" />
          <el-table-column prop="reason" label="原因" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button link type="primary" @click="decide(row.id as number, true)">通过</el-button>
                <el-button link type="danger" @click="decide(row.id as number, false)">拒绝</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <p class="hint" style="margin-top:8px">API 版本/文档/在线测试：登记服务路径后由开发侧对接 OpenAPI；本页提供注册、监控与审批。</p>
      </PageCard>

      <div v-if="tab === 'audit.log'" class="uum-embed">
        <AuditLog />
      </div>
      <div v-if="tab === 'audit.runtime'" class="uum-embed">
        <RuntimeErrorLog />
      </div>

      <PageCard v-if="tab === 'portal'" title="门户配置">
        <PortalNavConfigPanel />
      </PageCard>

      <PageCard v-if="tab === 'integration'" title="系统对接">
        <el-table :data="integrations" stripe size="small">
          <el-table-column prop="integrationCode" label="编码" width="120" />
          <el-table-column prop="integrationName" label="名称" />
          <el-table-column prop="targetSystem" label="目标系统" width="140" />
          <el-table-column prop="endpoint" label="端点" min-width="200" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="lastMessage" label="最近检测" min-width="140" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click="testIntegration(row.id as number)">检测</el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.uum-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; line-height: 1.6; }
.uum-embed { height: 100%; overflow: auto; padding: 0 4px 12px; }
h4, .section-title { margin: 20px 0 12px; font-size: 14px; font-weight: 600; }
.menu-perm-panel {
  max-height: 360px;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 8px;
  background: var(--el-fill-color-blank);
}
</style>
