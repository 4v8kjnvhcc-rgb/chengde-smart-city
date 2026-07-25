<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

/** V3.0 统一用户管理系统七模块 */
const navItems: HubNavItem[] = [
  { key: 'users', label: '用户中心' },
  { key: 'apps', label: '应用中心' },
  { key: 'auth', label: '认证中心' },
  { key: 'services', label: '服务中心' },
  { key: 'config', label: '系统管理' },
  { key: 'audit', label: '日志审计' },
  { key: 'integration', label: '系统对接' },
]

interface Config { id: number; configKey: string; configValue: string; configGroup: string; description?: string }
interface Integration {
  id: number
  integrationCode: string
  integrationName: string
  targetSystem: string
  endpoint: string
  status: string
  lastMessage?: string
}

const route = useRoute()
const router = useRouter()

const tabMap: Record<string, string> = {
  users: 'users', m139: 'users',
  apps: 'apps', m140: 'apps',
  auth: 'auth', m141: 'auth',
  services: 'services', m142: 'services',
  config: 'config', m143: 'config',
  audit: 'audit', m144: 'audit',
  integration: 'integration', m145: 'integration',
}

const DEFAULT_NAV = 'users'
const tab = ref(DEFAULT_NAV)
let applyingRoute = false

const configs = ref<Config[]>([])
const integrations = ref<Integration[]>([])
let configsLoaded = false
let integrationsLoaded = false

function resolveFromRoute() {
  applyingRoute = true
  tab.value = tabMap[String(route.query.tab || DEFAULT_NAV).toLowerCase()] || DEFAULT_NAV
  nextTick(() => { applyingRoute = false })
}

function syncQuery() {
  const q: Record<string, string> = {}
  for (const [k, v] of Object.entries(route.query)) {
    if (v == null || k === 'tab') continue
    q[k] = Array.isArray(v) ? String(v[0]) : String(v)
  }
  q.tab = tab.value
  router.replace({ query: q })
}

async function loadConfigs(force = false) {
  if (configsLoaded && !force) return
  const ov = (await api.get('/analytics/platform/support/overview')).data
  configs.value = ((ov?.configs as Config[]) || []).filter((c) => c.configGroup === 'SYSTEM')
  configsLoaded = true
}

async function loadIntegrations(force = false) {
  if (integrationsLoaded && !force) return
  const ov = (await api.get('/analytics/platform/support/overview')).data
  integrations.value = (ov?.integrations as Integration[]) || []
  integrationsLoaded = true
}

async function loadTabData() {
  try {
    if (tab.value === 'config') await loadConfigs()
    else if (tab.value === 'integration') await loadIntegrations()
  } catch {
    ElMessage.error('加载失败')
  }
}

async function saveConfig(row: Config) {
  await api.put(`/analytics/platform/configs/${row.id}`, { configValue: row.configValue })
  ElMessage.success('配置已保存')
}

async function testIntegration(id: number) {
  const res = await api.post(`/analytics/platform/integrations/${id}/test`, {})
  if (res.data?.reachable) ElMessage.success(res.data.message || '可达')
  else ElMessage.warning(res.data?.message || '未连通')
  await loadIntegrations(true)
}

function goSystem(path: string) {
  router.push(path)
}

watch(tab, () => {
  if (!applyingRoute) syncQuery()
  loadTabData()
})
watch(() => route.query.tab, () => { resolveFromRoute() })
onMounted(() => {
  resolveFromRoute()
  loadTabData()
})
</script>

<template>
  <div class="ana-hub-root">
    <HubSideLayout v-model="tab" :items="navItems">
      <PageCard v-if="tab === 'users'" title="用户中心">
        <p class="ana-hint">账号、角色、组织与访问控制已在全局「统一用户管理」实现（框架复用，不平行造账号体系）。</p>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="goSystem('/system/uum?tab=users')">打开统一用户管理</el-button>
            <el-button @click="goSystem('/system/users')">用户管理</el-button>
            <el-button @click="goSystem('/system/roles')">角色管理</el-button>
            <el-button @click="goSystem('/system/orgs')">组织管理</el-button>
            <el-button @click="goSystem('/system/access')">访问控制</el-button>
          </el-form-item>
        </el-form>
      </PageCard>

      <PageCard v-else-if="tab === 'apps'" title="应用中心">
        <p class="ana-hint">应用注册与授权已并入全局统一用户管理 · 应用中心。</p>
        <el-button type="primary" @click="goSystem('/system/uum?tab=apps')">打开应用中心</el-button>
      </PageCard>

      <PageCard v-else-if="tab === 'auth'" title="认证中心">
        <p class="ana-hint">统一身份认证 / SSO 扩展已并入全局系统管理。</p>
        <el-button type="primary" @click="goSystem('/system/uum?tab=auth')">打开认证中心</el-button>
        <el-button @click="goSystem('/system/security')">等保开关</el-button>
      </PageCard>

      <PageCard v-else-if="tab === 'services'" title="服务中心">
        <p class="ana-hint">服务注册、调用统计与审批已并入全局统一用户管理 · 服务中心。</p>
        <el-button type="primary" @click="goSystem('/system/uum?tab=services')">打开服务中心</el-button>
      </PageCard>

      <PageCard v-else-if="tab === 'config'" title="系统管理">
        <el-table :data="configs" stripe size="small">
          <el-table-column prop="configKey" label="配置项" width="200" />
          <el-table-column prop="description" label="说明" />
          <el-table-column label="值" min-width="120">
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
        <el-button style="margin-top:12px" @click="goSystem('/system/security')">等保安全配置</el-button>
      </PageCard>

      <PageCard v-else-if="tab === 'audit'" title="日志审计">
        <p class="ana-hint">审计日志查询与导出已在系统管理中实现。</p>
        <el-button type="primary" @click="goSystem('/system/audit')">打开审计日志</el-button>
      </PageCard>

      <PageCard v-else-if="tab === 'integration'" title="系统对接">
        <el-alert
          type="info"
          :closable="false"
          title="探测结果如实展示；未启动目标服务时为「错误/未连通」，不会伪造成功。"
          style="margin-bottom:12px"
        />
        <el-table :data="integrations" stripe size="small">
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
              <el-button link type="primary" @click="testIntegration(row.id)">检测</el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.ana-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
.ana-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}
</style>
