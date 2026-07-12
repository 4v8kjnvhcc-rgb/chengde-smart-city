<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavGroup } from '@/components/common/HubSideLayout.vue'

interface DomainModule {
  id: number
  domainCode: string
  mCode: string
  moduleName: string
  moduleType: string
  capGroup?: string
  deDashboardId?: string
  status: string
  lastMessage?: string
  lastRunAt?: string
}

interface Sample {
  rowNo: number
  dim1: string
  dim2: string
  metric1: number
  metric2: number
}

const route = useRoute()
const router = useRouter()

const domainMeta: Record<string, { domain: string; title: string; desc: string }> = {
  '/analytics/population': { domain: 'population', title: '人口大数据', desc: 'M152～M174：数据治理 + 分析模型 + DataEase 嵌入' },
  '/analytics/legal-entity': { domain: 'legal', title: '法人大数据', desc: 'M175～M192：数据治理 + 分析模型 + DataEase 嵌入' },
  '/analytics/macro': { domain: 'macro', title: '宏观经济', desc: 'M193～M203：宏观分析模型 + DataEase 嵌入' },
  '/analytics/key-domains': { domain: 'key', title: '重点领域', desc: 'M204～M209：应急/安全/民生分析模型' },
}

const meta = computed(() => domainMeta[route.path] || domainMeta['/analytics/population'])
const modules = ref<DomainModule[]>([])
const overview = ref<Record<string, unknown> | null>(null)
const activeMCode = ref('M152')
const detail = ref<Record<string, unknown> | null>(null)
const iframeSrc = ref('')
const embedMeta = ref('')

const dataOpsModules = computed(() => modules.value.filter(m => m.moduleType === 'DATA_OPS'))
const analysisModules = computed(() => modules.value.filter(m => m.moduleType === 'ANALYSIS'))

const navGroups = computed<HubNavGroup[]>(() => [
  {
    title: '数据治理（L1）',
    items: dataOpsModules.value.map(m => ({
      key: m.mCode,
      label: m.mCode,
      subLabel: m.moduleName,
    })),
  },
  {
    title: '分析模型',
    items: analysisModules.value.map(m => ({
      key: m.mCode,
      label: m.mCode,
      subLabel: m.moduleName,
    })),
  },
])

const activeModule = computed(() => modules.value.find(m => m.mCode === activeMCode.value))

function resolveTab() {
  const q = String(route.query.tab || '').toLowerCase()
  if (!q) {
    const first = modules.value[0]
    if (first) activeMCode.value = first.mCode
    return
  }
  const code = q.startsWith('m') ? q.toUpperCase() : `M${q}`
  if (modules.value.some(m => m.mCode === code)) {
    activeMCode.value = code
  }
}

async function loadOverview() {
  const res = await api.get(`/analytics/domain/${meta.value.domain}/overview`)
  overview.value = res.data
  modules.value = res.data.modules
  resolveTab()
  await loadDetail()
}

async function loadDetail() {
  if (!activeMCode.value) return
  detail.value = (await api.get(`/analytics/domain/modules/${activeMCode.value}`)).data
  iframeSrc.value = ''
  embedMeta.value = ''
}

async function runOps() {
  const res = await api.post(`/analytics/domain/modules/${activeMCode.value}/run`, {})
  ElMessage.success(res.data.message)
  await loadOverview()
  await loadDetail()
}

async function issueEmbed() {
  const res = await api.post(`/analytics/domain/modules/${activeMCode.value}/embed-token`, {})
  embedMeta.value = JSON.stringify(res.data, null, 2)
  const val = await api.get('/analytics/embed-token/validate', { params: { token: res.data.token } })
  iframeSrc.value = String(val.data.dataeaseUrl || '')
  ElMessage.success('DataEase 嵌入已加载')
  if (res.data.embedUrl) window.open(res.data.embedUrl as string, '_blank')
}

watch(activeMCode, (code) => {
  router.replace({ query: { ...route.query, tab: code.toLowerCase() } })
  loadDetail()
})
watch(() => route.path, () => { loadOverview() })
watch(() => route.query.tab, resolveTab)

onMounted(loadOverview)
</script>

<template>
  <div>
    <PageHeader :title="meta.title" :description="meta.desc" />

    <PageCard v-if="overview" title="域概览" style="margin-bottom:16px">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="模块总数">{{ overview.totalModules }}</el-descriptions-item>
        <el-descriptions-item label="数据治理">{{ overview.dataOpsCount }}</el-descriptions-item>
        <el-descriptions-item label="分析模型">{{ overview.analysisCount }}</el-descriptions-item>
        <el-descriptions-item label="DataEase">{{ overview.dataEaseHealthy ? '在线' : '离线' }}</el-descriptions-item>
      </el-descriptions>
    </PageCard>

    <HubSideLayout v-model="activeMCode" :groups="navGroups">
      <PageCard v-if="activeModule" :title="`${activeModule.mCode} ${activeModule.moduleName}`">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="类型">{{ activeModule.moduleType }}</el-descriptions-item>
          <el-descriptions-item label="分组">{{ activeModule.capGroup }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ activeModule.status }}</el-descriptions-item>
          <el-descriptions-item label="最近运行">{{ activeModule.lastRunAt || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="activeModule.deDashboardId" label="DE 看板" :span="2">
            {{ activeModule.deDashboardId }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="activeModule.moduleType === 'DATA_OPS'" class="action-row">
          <el-button type="primary" @click="runOps">执行治理任务</el-button>
          <span v-if="activeModule.lastMessage" class="hint">{{ activeModule.lastMessage }}</span>
        </div>

        <template v-if="activeModule.moduleType === 'ANALYSIS' && detail?.model">
          <div class="action-row">
            <el-button type="primary" @click="issueEmbed">签发 DataEase 嵌入</el-button>
            <el-tag>样例 {{ detail.sampleCount }} 行</el-tag>
          </div>
          <el-table :data="(detail.samplesPreview as Sample[])" stripe size="small" style="margin-top:12px">
            <el-table-column prop="rowNo" label="#" width="60" />
            <el-table-column prop="dim1" label="维度1" />
            <el-table-column prop="dim2" label="维度2" />
            <el-table-column prop="metric1" label="指标1" />
            <el-table-column prop="metric2" label="指标2" />
          </el-table>
          <div class="iframe-shell">
            <iframe v-if="iframeSrc" class="de-iframe" :src="iframeSrc" title="DataEase" />
            <div v-else class="iframe-placeholder">签发令牌后在此加载 GPL iframe</div>
          </div>
          <pre v-if="embedMeta" class="embed-log">{{ embedMeta }}</pre>
        </template>
      </PageCard>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.action-row {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.hint { font-size: 13px; color: #67c23a; }
.iframe-shell {
  margin-top: 12px;
  min-height: 360px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  background: #0b1f33;
}
.de-iframe { width: 100%; height: 360px; border: 0; }
.iframe-placeholder {
  height: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #e8f4ff;
  opacity: 0.8;
}
.embed-log {
  margin-top: 12px;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 12px;
}
</style>
