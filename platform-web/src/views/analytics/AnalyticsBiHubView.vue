<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

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

const tab = ref('display')
const widgets = ref<Widget[]>([])
const dashboards = ref<Dashboard[]>([])
const dataEaseHealthy = ref(false)
const dataEaseUrl = ref('')
const embedUrl = ref('')
const iframeSrc = ref('')
const embedMeta = ref('')

function resolveTab() {
  tab.value = tabMap[String(route.query.tab || 'display').toLowerCase()] || 'display'
}

const activeWidget = computed(() => widgets.value.find(w => w.mCode === mCodeMap[tab.value]))

const tabTitle = computed(() => activeWidget.value?.widgetName || '智能 BI')

async function loadOverview() {
  const res = await api.get('/analytics/platform/bi/overview')
  widgets.value = res.data.widgets
  dashboards.value = res.data.dashboards
  dataEaseHealthy.value = !!res.data.dataEaseHealthy
  dataEaseUrl.value = res.data.dataEaseUrl || ''
}

async function issueEmbed() {
  const mCode = mCodeMap[tab.value]
  if (!mCode) return
  try {
    const res = await api.post(`/analytics/platform/bi/widgets/${mCode}/embed-token`, {})
    embedUrl.value = res.data.embedUrl as string
    embedMeta.value = JSON.stringify(res.data, null, 2)
    const val = await api.get('/analytics/embed-token/validate', { params: { token: res.data.token } })
    iframeSrc.value = String(val.data.dataeaseUrl || '')
    ElMessage.success('DataEase 嵌入令牌已签发')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '签发失败')
  }
}

function openPreview() {
  if (embedUrl.value) window.open(embedUrl.value, '_blank')
}

watch(tab, () => {
  router.replace({ query: { ...route.query, tab: tab.value } })
  embedUrl.value = ''
  iframeSrc.value = ''
  embedMeta.value = ''
})
watch(() => route.query.tab, resolveTab)

onMounted(async () => {
  resolveTab()
  try {
    await loadOverview()
  } catch {
    ElMessage.error('加载失败')
  }
})
</script>

<template>
  <div>
    <PageHeader
      :title="`智能 BI 平台 · ${tabTitle}`"
      :description="`M146～M151：DataEase GPL iframe 嵌入 · 健康=${dataEaseHealthy ? '在线' : '离线'} · ${dataEaseUrl}`"
    />
    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="显示引擎 M146" name="display" />
      <el-tab-pane label="组件引擎 M147" name="component" />
      <el-tab-pane label="地图管理 M148" name="map" />
      <el-tab-pane label="数据源 M149" name="datasource" />
      <el-tab-pane label="可视化设计 M150" name="design" />
      <el-tab-pane label="自助分析 M151" name="self" />
    </el-tabs>

    <PageCard v-if="activeWidget" :title="`${activeWidget.mCode} ${activeWidget.widgetName}`" style="margin-top:16px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="Widget 编码">{{ activeWidget.widgetCode }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ activeWidget.widgetType }}</el-descriptions-item>
        <el-descriptions-item label="DE Dashboard">{{ activeWidget.deDashboardId }}</el-descriptions-item>
        <el-descriptions-item label="说明">{{ activeWidget.description }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:12px">
        <el-button type="primary" @click="issueEmbed">签发嵌入令牌并加载 iframe</el-button>
        <el-button v-if="embedUrl" @click="openPreview">新窗口预览</el-button>
      </div>
      <div class="iframe-shell">
        <iframe v-if="iframeSrc" class="de-iframe" :src="iframeSrc" title="DataEase" />
        <div v-else class="iframe-placeholder">
          <div class="title">DataEase Embedded Canvas</div>
          <div class="sub">{{ activeWidget.deDashboardId }}</div>
          <div class="hint">点击「签发嵌入令牌」加载 GPL iframe；未启动 DataEase 时显示占位</div>
        </div>
      </div>
      <pre v-if="embedMeta" class="embed-log">{{ embedMeta }}</pre>
    </PageCard>

    <PageCard title="BI 大屏清单" style="margin-top:16px">
      <el-table :data="dashboards" stripe size="small">
        <el-table-column prop="dashCode" label="编码" width="140" />
        <el-table-column prop="dashName" label="名称" />
        <el-table-column prop="deDashboardId" label="DE Dashboard" />
        <el-table-column prop="status" label="状态" width="90" />
      </el-table>
    </PageCard>
  </div>
</template>

<style scoped>
.iframe-shell {
  margin-top: 16px;
  min-height: 480px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  background: #0b1f33;
}
.de-iframe {
  width: 100%;
  height: 480px;
  border: 0;
}
.iframe-placeholder {
  height: 480px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #e8f4ff;
  gap: 8px;
}
.title { font-size: 22px; font-weight: 600; }
.sub { font-family: ui-monospace, Consolas, monospace; opacity: 0.9; }
.hint { margin-top: 12px; font-size: 13px; opacity: 0.7; }
.embed-log {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 12px;
}
</style>
