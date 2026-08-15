<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { useAuthStore } from '@/stores/auth'
import { filterHubNavByPermissions, filterHubNavByMenuVisible, BI_NAV_PERMISSIONS } from '@/utils/hub-nav-permission'

const auth = useAuthStore()

/** V3.0 智能 BI 描述中的六子模块（侧栏全名，无 M 码） */
const NAV_BASE: HubNavItem[] = [
  { key: 'display', label: '显示引擎' },
  { key: 'component', label: '组件引擎' },
  { key: 'map', label: '地图管理' },
  { key: 'datasource', label: '数据源管理' },
  { key: 'design', label: '可视化设计' },
  { key: 'self', label: '自助分析' },
]

const navItems = computed(() => {
  const byPerm = filterHubNavByPermissions(NAV_BASE, auth.permissions, BI_NAV_PERMISSIONS, {
    isSystemAdmin: auth.isSystemAdmin,
  })
  return filterHubNavByMenuVisible(byPerm, auth.menus, BI_NAV_PERMISSIONS)
})

interface Widget {
  id: number
  widgetCode: string
  widgetName: string
  widgetType: string
  mCode: string
  deDashboardId: string
  description?: string
}

interface Dashboard {
  id: number
  dashCode: string
  dashName: string
  deDashboardId: string
  status: string
}

const route = useRoute()
const router = useRouter()

const tabMap: Record<string, string> = {
  display: 'display', m146: 'display',
  component: 'component', m147: 'component',
  map: 'map', m148: 'map',
  datasource: 'datasource', m149: 'datasource',
  design: 'design', m150: 'design',
  self: 'self', m151: 'self',
}

const mCodeMap: Record<string, string> = {
  display: 'M146', component: 'M147', map: 'M148',
  datasource: 'M149', design: 'M150', self: 'M151',
}

const DEFAULT_NAV = 'display'
const tab = ref(DEFAULT_NAV)
let applyingRoute = false

const widgets = ref<Widget[]>([])
const dashboards = ref<Dashboard[]>([])
const dataEaseHealthy = ref(false)
const dataEaseUrl = ref('')
const embedUrl = ref('')
const iframeSrc = ref('')
const embedMode = ref<'LIVE' | 'LEDGER' | ''>('')
const embedMessage = ref('')
let overviewLoaded = false

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

const activeWidget = computed(() => widgets.value.find((w) => w.mCode === mCodeMap[tab.value]))

async function loadOverview(force = false) {
  if (overviewLoaded && !force) return
  const res = await api.get('/analytics/platform/bi/overview')
  widgets.value = res.data.widgets || []
  dashboards.value = res.data.dashboards || []
  dataEaseHealthy.value = !!res.data.dataEaseHealthy
  dataEaseUrl.value = res.data.dataEaseUrl || ''
  overviewLoaded = true
}

async function issueEmbed() {
  const mCode = mCodeMap[tab.value]
  if (!mCode) return
  try {
    const res = await api.post(`/analytics/platform/bi/widgets/${mCode}/embed-token`, {})
    embedUrl.value = (res.data.embedUrl as string) || ''
    embedMode.value = (res.data.mode as 'LIVE' | 'LEDGER') || 'LEDGER'
    embedMessage.value = String(res.data.message || '')
    const deUrl = res.data.dataeaseUrl as string | null
    if (embedMode.value === 'LIVE' && deUrl) {
      iframeSrc.value = deUrl
      ElMessage.success(embedMessage.value || 'DataEase 嵌入已加载')
    } else {
      iframeSrc.value = ''
      ElMessage.warning(embedMessage.value || 'DataEase 未就绪，仅门户预览令牌')
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '签发失败')
  }
}

function openPreview() {
  if (embedUrl.value) window.open(embedUrl.value, '_blank')
}

watch(tab, () => {
  if (!applyingRoute) syncQuery()
  embedUrl.value = ''
  iframeSrc.value = ''
  embedMode.value = ''
  embedMessage.value = ''
})
watch(() => route.query.tab, () => { resolveFromRoute() })

onMounted(async () => {
  resolveFromRoute()
  try {
    await loadOverview()
  } catch {
    ElMessage.error('加载失败')
  }
})
</script>

<template>
  <div class="ana-hub-root">
    <HubSideLayout v-model="tab" :items="navItems">
      <PageCard v-if="activeWidget" :title="activeWidget.widgetName">
        <el-alert
          :type="dataEaseHealthy ? 'success' : 'warning'"
          :closable="false"
          style="margin-bottom:12px"
          :title="dataEaseHealthy
            ? `DataEase 在线 · ${dataEaseUrl || '-'}`
            : 'DataEase 离线：签发仅为门户预览台账，不会伪造成功嵌入'"
        />
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="组件编码">{{ activeWidget.widgetCode }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ statusLabel(activeWidget.widgetType) }}</el-descriptions-item>
          <el-descriptions-item label="看板标识">{{ activeWidget.deDashboardId }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ activeWidget.description || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-form inline class="portal-inline-form portal-inline-form--block" style="margin-top:12px">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="issueEmbed">签发嵌入令牌</el-button>
            <el-button v-if="embedUrl" @click="openPreview">门户预览</el-button>
          </el-form-item>
        </el-form>
        <el-alert
          v-if="embedMode"
          :type="embedMode === 'LIVE' ? 'success' : 'warning'"
          :closable="false"
          style="margin-top:8px"
          :title="embedMode === 'LIVE' ? '实时嵌入' : '台账预览'"
          :description="embedMessage"
        />
        <div class="iframe-shell">
          <iframe v-if="iframeSrc" class="de-iframe" :src="iframeSrc" title="DataEase" />
          <div v-else class="iframe-placeholder">
            <div class="title">{{ activeWidget.widgetName }}</div>
            <div class="sub">{{ activeWidget.deDashboardId }}</div>
            <div class="hint">
              {{ dataEaseHealthy ? '点击「签发嵌入令牌」加载 iframe' : '请启动 DataEase 后再签发实时嵌入；当前可走门户预览' }}
            </div>
          </div>
        </div>
      </PageCard>

      <PageCard title="BI 大屏清单" style="margin-top:12px">
        <el-table :data="dashboards" stripe size="small">
          <el-table-column prop="dashCode" label="编码" width="140" />
          <el-table-column prop="dashName" label="名称" />
          <el-table-column prop="deDashboardId" label="看板标识" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
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
.iframe-shell {
  margin-top: 16px;
  min-height: 420px;
  border: 1px solid var(--portal-border, #dcdfe6);
  border-radius: 8px;
  overflow: hidden;
  background: #0b1f33;
}
.de-iframe {
  width: 100%;
  height: 420px;
  border: 0;
}
.iframe-placeholder {
  height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #e8f4ff;
  gap: 8px;
  padding: 16px;
  text-align: center;
}
.title { font-size: 18px; font-weight: 600; }
.sub { font-family: ui-monospace, Consolas, monospace; opacity: 0.9; font-size: 13px; }
.hint { margin-top: 8px; font-size: 13px; opacity: 0.75; line-height: 1.5; }
</style>
