<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, type ElTree } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { leafKeysForTreeCheck } from '@/utils/menu-tree-check'
import OrgManage from '@/views/system/OrgManage.vue'
import UserManage from '@/views/system/UserManage.vue'
import RoleManage from '@/views/system/RoleManage.vue'
import MenuManage from '@/views/system/MenuManage.vue'
import PortalLinkManage from '@/views/system/PortalLinkManage.vue'
import TagLibraryManage from '@/views/system/TagLibraryManage.vue'
import AuditLog from '@/views/system/AuditLog.vue'
import AccessControlView from '@/views/exchange/ingestion/register/AccessControlView.vue'
import SecurityConfig from '@/views/system/SecurityConfig.vue'
import SystemMaintenanceView from '@/views/system/maintenance/SystemMaintenanceView.vue'
import SystemMailConfigView from '@/views/system/maintenance/SystemMailConfigView.vue'
import PortalNavConfigPanel from '@/views/system/PortalNavConfigPanel.vue'
import KettleView from '@/views/integration/KettleView.vue'
import SchedulerView from '@/views/integration/SchedulerView.vue'

/** 整合后的通用支撑平台 IA */
const navItems: HubNavItem[] = [
  {
    key: 'users',
    label: '用户中心',
    children: [
      { key: 'users.org', label: '组织管理' },
      { key: 'users.user', label: '用户管理' },
      { key: 'users.role', label: '角色管理' },
    ],
  },
  {
    key: 'apps',
    label: '应用中心',
    children: [
      { key: 'apps.manage', label: '应用管理' },
      { key: 'apps.integration', label: '系统对接' },
      { key: 'apps.portal', label: '门户配置' },
      { key: 'apps.links', label: '门户外链管理' },
    ],
  },
  { key: 'auth', label: '认证中心' },
  { key: 'services', label: '服务中心' },
  { key: 'tasks', label: '任务管理' },
  { key: 'ops.kettle', label: '集成运维' },
  {
    key: 'sys',
    label: '系统管理',
    children: [
      { key: 'sys.menus', label: '菜单管理' },
      { key: 'sys.dict', label: '字典管理' },
      {
        key: 'sys.cfg',
        label: '系统配置',
        children: [
          { key: 'sys.cfg.appearance', label: '基础信息' },
          { key: 'sys.cfg.mail', label: '系统邮箱' },
        ],
      },
      { key: 'sys.tags', label: '标签库' },
    ],
  },
  {
    key: 'audit',
    label: '日志审计',
    children: [
      { key: 'audit.log', label: '日志审计' },
      { key: 'audit.access', label: '访问控制' },
      { key: 'audit.security', label: '等保安全' },
    ],
  },
  {
    key: 'other',
    label: '其他',
    children: [
      { key: 'other.roleMenus', label: '角色菜单权限' },
      { key: 'other.probe', label: '对接探测' },
    ],
  },
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
  users: 'users.org',
  m139: 'users.org',
  apps: 'apps.manage',
  m140: 'apps.manage',
  auth: 'auth',
  m141: 'auth',
  services: 'services',
  m142: 'services',
  config: 'sys.cfg.appearance',
  m143: 'sys.cfg.appearance',
  audit: 'audit.log',
  m144: 'audit.log',
  integration: 'apps.integration',
  m145: 'other.probe',
  portal: 'apps.portal',
  tasks: 'tasks',
  ops: 'ops.kettle',
  kettle: 'ops.kettle',
  ds: 'tasks',
  scheduler: 'tasks',
}

const DEFAULT_NAV = 'users.org'
const route = useRoute()
const router = useRouter()
const tab = ref(DEFAULT_NAV)
let applyingRoute = false

interface Config {
  id: number
  configKey: string
  configValue: string
  configGroup: string
  description?: string
}
interface Integration {
  id: number
  integrationCode: string
  integrationName: string
  targetSystem: string
  endpoint: string
  status: string
  lastMessage?: string
}
interface MenuRow {
  id: number
  parentId: number
  menuName: string
  path?: string
  menuType: number
  permission?: string
  sortOrder?: number
  children?: MenuRow[]
}
interface CheckNode {
  id: number
  label: string
  children?: CheckNode[]
}

const configs = ref<Config[]>([])
const probeIntegrations = ref<Integration[]>([])
const uumIntegrations = ref<Record<string, unknown>[]>([])
const apps = ref<Record<string, unknown>[]>([])
const appGrants = ref<Record<string, unknown>[]>([])
const authConfigs = ref<Record<string, unknown>[]>([])
const services = ref<Record<string, unknown>[]>([])
const serviceStats = ref<Record<string, unknown>[]>([])
const approvals = ref<Record<string, unknown>[]>([])
const users = ref<Array<{ id: number; displayName: string; username: string }>>([])
const roles = ref<Array<{ id: number; roleName: string; roleCode?: string }>>([])
const menuCheckTree = ref<CheckNode[]>([])
const selectedRoleId = ref<number | undefined>()
const menuTreeRef = ref<InstanceType<typeof ElTree>>()
const savingMenus = ref(false)
const loading = ref(false)

const appForm = reactive({ appName: '', appType: 'WEB', endpointUrl: '/' })
const grantForm = reactive({
  appId: undefined as number | undefined,
  granteeType: 'USER',
  granteeId: undefined as number | undefined,
  perm: 'ACCESS',
})
const svcForm = reactive({ serviceName: '', servicePath: '/api/v1/', protocol: 'REST' })
const applyForm = reactive({ serviceId: undefined as number | undefined, reason: '' })

const paneTitle = computed(() => {
  const find = (items: HubNavItem[]): string | null => {
    for (const it of items) {
      if (it.key === tab.value) return it.label
      if (it.children?.length) {
        const hit = find(it.children)
        if (hit) return hit
      }
    }
    return null
  }
  return find(navItems) || '通用支撑平台'
})

function resolveFromRoute() {
  applyingRoute = true
  const raw = String(route.query.tab || DEFAULT_NAV).toLowerCase()
  const mapped = OLD_TAB_MAP[raw] || raw
  tab.value = LEAF_KEYS.has(mapped) ? mapped : DEFAULT_NAV
  nextTick(() => {
    applyingRoute = false
  })
}

function syncQuery() {
  const q: Record<string, string> = {}
  for (const [k, v] of Object.entries(route.query)) {
    if (v == null || k === 'tab') continue
    q[k] = Array.isArray(v) ? String(v[0]) : String(v)
  }
  q.tab = tab.value
  router.replace({ path: '/analytics/support', query: q })
}

function buildCheckTree(rows: MenuRow[]): CheckNode[] {
  const map = new Map<number, CheckNode>()
  const roots: CheckNode[] = []
  for (const r of rows) {
    const suffix = r.menuType === 3 ? ' [按钮]' : r.menuType === 1 ? ' [目录]' : ''
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

async function loadAppsTab() {
  const [a, g, u, r] = await Promise.all([
    api.get('/system/uum/apps'),
    api.get('/system/uum/app-grants'),
    api.get('/system/users'),
    api.get('/system/roles'),
  ])
  apps.value = a.data || []
  appGrants.value = g.data || []
  users.value = u.data || []
  roles.value = r.data || []
}

async function loadAuthTab() {
  authConfigs.value = (await api.get('/system/uum/auth-configs')).data || []
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

async function loadUumIntegrations() {
  uumIntegrations.value = (await api.get('/system/uum/integrations')).data || []
}

async function loadDictConfigs() {
  const ov = (await api.get('/analytics/platform/support/overview')).data
  configs.value = (ov?.configs as Config[]) || []
}

async function loadProbe() {
  const ov = (await api.get('/analytics/platform/support/overview')).data
  probeIntegrations.value = (ov?.integrations as Integration[]) || []
}

async function loadRoleMenusTab() {
  const [r, m] = await Promise.all([api.get('/system/roles'), api.get('/system/menus')])
  roles.value = r.data || []
  const rows = (m.data || []) as MenuRow[]
  menuCheckTree.value = buildCheckTree(rows)
  if (!selectedRoleId.value && roles.value.length) {
    selectedRoleId.value = roles.value[0].id
  }
  if (selectedRoleId.value) await loadRoleMenus(selectedRoleId.value)
}

async function loadRoleMenus(roleId: number) {
  const assignedRes = await api.get(`/system/roles/${roleId}/menus`)
  const ids = (assignedRes.data || []) as number[]
  await nextTick()
  menuTreeRef.value?.setCheckedKeys(leafKeysForTreeCheck(menuCheckTree.value, ids))
}

async function onRoleChange(roleId: number | undefined) {
  selectedRoleId.value = roleId
  if (roleId) await loadRoleMenus(roleId)
}

async function saveRoleMenus() {
  if (!selectedRoleId.value || !menuTreeRef.value) return
  savingMenus.value = true
  try {
    const checked = menuTreeRef.value.getCheckedKeys(false) as number[]
    const half = menuTreeRef.value.getHalfCheckedKeys() as number[]
    await api.put(`/system/roles/${selectedRoleId.value}/menus`, { menuIds: [...checked, ...half] })
    ElMessage.success('角色菜单权限已保存')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    savingMenus.value = false
  }
}

async function loadTabData() {
  loading.value = true
  try {
    if (tab.value === 'apps.manage') await loadAppsTab()
    else if (tab.value === 'apps.integration') await loadUumIntegrations()
    else if (tab.value === 'auth') await loadAuthTab()
    else if (tab.value === 'services') await loadServicesTab()
    else if (tab.value === 'sys.dict') await loadDictConfigs()
    else if (tab.value === 'other.probe') await loadProbe()
    else if (tab.value === 'other.roleMenus') await loadRoleMenusTab()
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function createApp() {
  await api.post('/system/uum/apps', { ...appForm })
  ElMessage.success('应用已注册')
  await loadAppsTab()
}
async function createGrant() {
  await api.post('/system/uum/app-grants', { ...grantForm })
  ElMessage.success('已授权')
  await loadAppsTab()
}
async function removeGrant(id: number) {
  await api.delete(`/system/uum/app-grants/${id}`)
  ElMessage.success('已删除')
  await loadAppsTab()
}
async function saveAuthConfig(row: Record<string, unknown>) {
  await api.put(`/system/uum/auth-configs/${row.id}`, { configValue: row.configValue })
  ElMessage.success('已保存')
}
async function createService() {
  await api.post('/system/uum/services', { ...svcForm })
  ElMessage.success('服务已发布')
  await loadServicesTab()
}
async function applyService() {
  await api.post('/system/uum/service-approvals', { ...applyForm })
  ElMessage.success('已提交')
  await loadServicesTab()
}
async function decide(id: number, pass: boolean) {
  await api.post(`/system/uum/service-approvals/${id}/${pass ? 'approve' : 'reject'}`, {
    comment: pass ? '同意' : '拒绝',
  })
  ElMessage.success(pass ? '已通过' : '已拒绝')
  await loadServicesTab()
}
async function testUumIntegration(id: number) {
  const res = await api.post(`/system/uum/integrations/${id}/test`, {})
  if (res.data?.reachable) ElMessage.success(res.data.message || '可达')
  else ElMessage.warning(res.data?.message || '未连通')
  await loadUumIntegrations()
}
async function saveConfig(row: Config) {
  await api.put(`/analytics/platform/configs/${row.id}`, { configValue: row.configValue })
  ElMessage.success('配置已保存')
}
async function testProbe(id: number) {
  const res = await api.post(`/analytics/platform/integrations/${id}/test`, {})
  if (res.data?.reachable) ElMessage.success(res.data.message || '可达')
  else ElMessage.warning(res.data?.message || '未连通')
  await loadProbe()
}

watch(tab, () => {
  if (!applyingRoute) syncQuery()
  void loadTabData()
})
watch(() => route.query.tab, () => {
  resolveFromRoute()
  void loadTabData()
})
onMounted(() => {
  resolveFromRoute()
  void loadTabData()
})
</script>

<template>
  <div v-loading="loading" class="ana-hub-root">
    <HubSideLayout v-model="tab" :items="navItems">
      <div class="support-pane">
        <!-- 用户中心 -->
        <OrgManage v-if="tab === 'users.org'" />
        <UserManage v-else-if="tab === 'users.user'" />
        <RoleManage v-else-if="tab === 'users.role'" />

        <!-- 应用中心 · 应用管理 -->
        <PageCard v-else-if="tab === 'apps.manage'" title="应用管理">
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
            <el-table-column prop="appType" label="类型" width="80" />
            <el-table-column prop="endpointUrl" label="入口" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ statusLabel(row.status) }}</template>
            </el-table-column>
          </el-table>
          <h4>应用授权</h4>
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
            <el-table-column prop="granteeType" label="类型" width="80" />
            <el-table-column prop="granteeName" label="对象" width="140" />
            <el-table-column prop="perm" label="权限" width="100" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeGrant(row.id as number)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>

        <PageCard v-else-if="tab === 'apps.integration'" title="系统对接">
          <el-table :data="uumIntegrations" stripe size="small">
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
                <el-button link type="primary" @click="testUumIntegration(row.id as number)">检测</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>

        <PageCard v-else-if="tab === 'apps.portal'" title="门户配置">
          <PortalNavConfigPanel />
        </PageCard>

        <div v-else-if="tab === 'apps.links'" class="support-embed">
          <PortalLinkManage />
        </div>

        <!-- 认证 / 服务 -->
        <PageCard v-else-if="tab === 'auth'" title="认证中心">
          <el-alert
            type="info"
            :closable="false"
            title="一期：JWT 用户名密码 + 可选 TOTP。短信验证码 / 指纹为扩展配置位。"
            style="margin-bottom:12px"
          />
          <el-table :data="authConfigs" stripe size="small">
            <el-table-column prop="configKey" label="配置项" width="200" />
            <el-table-column prop="description" label="说明" min-width="220" />
            <el-table-column label="值" min-width="160">
              <template #default="{ row }">
                <el-input v-model="row.configValue" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" @click="saveAuthConfig(row)">保存</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>

        <!-- 任务管理 / 集成运维（原一级「集成运维」迁入） -->
        <div v-else-if="tab === 'tasks'" class="support-embed">
          <SchedulerView />
        </div>
        <div v-else-if="tab === 'ops.kettle'" class="support-embed">
          <KettleView />
        </div>

        <PageCard v-else-if="tab === 'services'" title="服务中心">
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
        </PageCard>

        <!-- 系统管理 -->
        <div v-else-if="tab === 'sys.menus'" class="support-embed"><MenuManage /></div>
        <PageCard v-else-if="tab === 'sys.dict'" title="字典管理">
          <el-alert
            type="info"
            :closable="false"
            style="margin-bottom:12px"
            title="本页维护通用支撑平台参数表；业务「数据字典」仍在归集平台，可从下方入口打开（双能力并存）。"
          />
          <el-button
            type="primary"
            style="margin-bottom:12px"
            @click="router.push('/exchange/ingestion?system=register&module=m050')"
          >
            打开数据字典管理（归集）
          </el-button>
          <el-table :data="configs" stripe size="small">
            <el-table-column prop="configKey" label="配置项" width="200" />
            <el-table-column prop="configGroup" label="分组" width="100" />
            <el-table-column prop="description" label="说明" />
            <el-table-column label="值" min-width="140">
              <template #default="{ row }">
                <el-input v-model="row.configValue" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" @click="saveConfig(row)">保存</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
        <PageCard v-else-if="tab === 'sys.cfg.appearance'" title="基础信息">
          <SystemMaintenanceView embed />
        </PageCard>
        <PageCard v-else-if="tab === 'sys.cfg.mail'" title="系统邮箱">
          <SystemMailConfigView embed />
        </PageCard>
        <div v-else-if="tab === 'sys.tags'" class="support-embed"><TagLibraryManage /></div>

        <!-- 日志审计 -->
        <div v-else-if="tab === 'audit.log'" class="support-embed"><AuditLog /></div>
        <PageCard v-else-if="tab === 'audit.access'" title="访问控制">
          <el-alert
            type="warning"
            :closable="false"
            style="margin-bottom:12px"
            title="双入口：本页为系统侧访问控制；归集平台「访问控制管理」入口同时保留。"
          />
          <el-button
            style="margin-bottom:12px"
            @click="router.push('/exchange/ingestion?system=register&module=m048')"
          >
            打开归集 · 访问控制管理
          </el-button>
          <AccessControlView />
        </PageCard>
        <PageCard v-else-if="tab === 'audit.security'" title="等保安全">
          <SecurityConfig embed />
        </PageCard>

        <!-- 其他 -->
        <PageCard v-else-if="tab === 'other.roleMenus'" title="角色菜单权限">
          <p class="ana-hint">选择角色后勾选菜单树，保存即调整该角色可见菜单与按钮权限。</p>
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="角色" class="portal-field-xl">
              <el-select :model-value="selectedRoleId" filterable placeholder="选择角色" @update:model-value="onRoleChange">
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
        </PageCard>

        <PageCard v-else-if="tab === 'other.probe'" title="对接探测">
          <el-alert
            type="info"
            :closable="false"
            title="探测 DataEase / DolphinScheduler / OpenMetadata 等；未启动目标服务时如实显示未连通。"
            style="margin-bottom:12px"
          />
          <el-table :data="probeIntegrations" stripe size="small">
            <el-table-column prop="integrationCode" label="编码" width="100" />
            <el-table-column prop="integrationName" label="名称" />
            <el-table-column prop="targetSystem" label="目标系统" width="140" />
            <el-table-column prop="endpoint" label="端点" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastMessage" label="最近检测" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" @click="testProbe(row.id)">检测</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>

        <PageCard v-else :title="paneTitle">
          <el-empty description="请从左侧选择功能" />
        </PageCard>
      </div>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.ana-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
.support-pane {
  min-height: 100%;
}
.support-embed :deep(.page-header) {
  display: none;
}
.ana-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}
.menu-perm-panel {
  margin-top: 8px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  max-height: 480px;
  overflow: auto;
  background: #fff;
}
</style>
