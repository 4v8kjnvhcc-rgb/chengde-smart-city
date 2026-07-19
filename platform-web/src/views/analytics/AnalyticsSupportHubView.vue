<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout from '@/components/common/HubSideLayout.vue'

const navItems = [
  { key: 'users', label: '用户中心' },
  { key: 'apps', label: '应用中心' },
  { key: 'auth', label: '认证中心' },
  { key: 'services', label: '服务中心' },
  { key: 'config', label: '系统管理' },
  { key: 'audit', label: '日志审计' },
  { key: 'integration', label: '系统对接' },
]

interface App { id: number; appCode: string; appName: string; appType: string; endpointUrl?: string; status: string }
interface Service { id: number; serviceCode: string; serviceName: string; servicePath: string; protocol: string; status: string }
interface Config { id: number; configKey: string; configValue: string; configGroup: string; description?: string }
interface Integration { id: number; integrationCode: string; integrationName: string; targetSystem: string; endpoint: string; status: string; lastMessage?: string }

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

const tab = ref('users')
const overview = ref<{
  apps: App[]
  services: Service[]
  configs: Config[]
  integrations: Integration[]
} | null>(null)

const appForm = reactive({ appName: '', appType: 'WEB', endpointUrl: '/' })
const svcForm = reactive({ serviceName: '', servicePath: '/api/v1/', protocol: 'REST' })

function resolveTab() {
  tab.value = tabMap[String(route.query.tab || 'users').toLowerCase()] || 'users'
}

async function load() {
  try {
    overview.value = (await api.get('/analytics/platform/support/overview')).data
  } catch {
    ElMessage.error('加载失败')
  }
}

async function createApp() {
  if (!appForm.appName) return
  await api.post('/analytics/platform/apps', appForm)
  ElMessage.success('应用已注册')
  appForm.appName = ''
  await load()
}

async function createService() {
  if (!svcForm.serviceName) return
  await api.post('/analytics/platform/services', svcForm)
  ElMessage.success('服务已发布')
  svcForm.serviceName = ''
  await load()
}

async function saveConfig(row: Config) {
  await api.put(`/analytics/platform/configs/${row.id}`, { configValue: row.configValue })
  ElMessage.success('配置已保存')
}

async function testIntegration(id: number) {
  const res = await api.post(`/analytics/platform/integrations/${id}/test`, {})
  ElMessage.success(res.data.message)
  await load()
}

function goSystem(path: string) {
  router.push(path)
}

const tabTitle = computed(() => ({
  users: 'M139 用户中心',
  apps: 'M140 应用中心',
  auth: 'M141 认证中心',
  services: 'M142 服务中心',
  config: 'M143 系统管理',
  audit: 'M144 日志审计',
  integration: 'M145 系统对接',
}[tab.value] || '通用支撑'))

watch(tab, () => {
  router.replace({ query: { ...route.query, tab: tab.value } })
})
watch(() => route.query.tab, resolveTab)
onMounted(() => { resolveTab(); load() })
</script>

<template>
  <div>
    <PageHeader
      :title="`通用支撑平台 · ${tabTitle}`"
      description="入口在系统管理：统一用户管理（3.1.1）及审计/等保；本页为分析侧聚合导航"
    />
    <HubSideLayout v-model="tab" :items="navItems">
      <PageCard v-if="tab === 'users'" title="M139 用户中心">
        <p>账号、角色、组织与访问控制已在全局系统管理「身份与权限」实现。</p>
        <el-button type="primary" @click="goSystem('/system/uum?tab=users')">打开统一用户管理</el-button>
        <el-button @click="goSystem('/system/users')">用户管理</el-button>
        <el-button @click="goSystem('/system/roles')">角色管理</el-button>
        <el-button @click="goSystem('/system/orgs')">组织管理</el-button>
        <el-button @click="goSystem('/system/access')">访问控制</el-button>
      </PageCard>

      <PageCard v-if="tab === 'apps'" title="M140 应用中心">
        <p>应用注册与用户/角色授权已并入全局系统管理「统一用户管理」。</p>
        <el-button type="primary" @click="goSystem('/system/uum?tab=apps')">打开应用中心</el-button>
      </PageCard>

      <PageCard v-if="tab === 'auth'" title="M141 认证中心（SSO 扩展）">
        <p>认证方式与 SSO 配置已并入全局系统管理。</p>
        <el-button type="primary" @click="goSystem('/system/uum?tab=auth')">打开认证中心</el-button>
        <el-button @click="goSystem('/system/security')">等保开关</el-button>
      </PageCard>

      <PageCard v-if="tab === 'services'" title="M142 服务中心">
        <p>服务注册、调用统计与审批已并入全局系统管理。</p>
        <el-button type="primary" @click="goSystem('/system/uum?tab=services')">打开服务中心</el-button>
      </PageCard>

      <PageCard v-if="tab === 'config'" title="M143 系统管理">
        <el-table v-if="overview" :data="overview.configs.filter(c => c.configGroup === 'SYSTEM')" stripe size="small">
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

      <PageCard v-if="tab === 'audit'" title="M144 日志审计">
        <p>审计日志查询与导出已在系统管理中实现。</p>
        <el-button type="primary" @click="goSystem('/system/audit')">打开审计日志</el-button>
      </PageCard>

      <PageCard v-if="tab === 'integration'" title="M145 系统对接">
        <el-table v-if="overview" :data="overview.integrations" stripe size="small">
          <el-table-column prop="integrationCode" label="编码" width="100" />
          <el-table-column prop="integrationName" label="名称" />
          <el-table-column prop="targetSystem" label="目标系统" width="140" />
          <el-table-column prop="endpoint" label="端点" min-width="200" />
          <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
          <el-table-column prop="lastMessage" label="最近检测" min-width="140" />
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
