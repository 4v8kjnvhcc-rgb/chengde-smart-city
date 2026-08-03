<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Model {
  id: number
  modelCode: string
  modelName: string
  domainCode: string
  mCode: string
  deDashboardId: string
  sampleRowCount: number
  status: string
}

interface Sample {
  rowNo: number
  dim1: string
  dim2: string
  metric1: number
  metric2: number
}

interface Dashboard {
  id: number
  dashCode: string
  dashName: string
  deDashboardId: string
  status: string
}

const route = useRoute()
const models = ref<Model[]>([])
const samples = ref<Sample[]>([])
const dashboards = ref<Dashboard[]>([])
const selectedId = ref<number>()
const embedInfo = ref('')

const domainMeta: Record<string, { domain: string; title: string; desc: string }> = {
  '/analytics/bi': { domain: 'bi', title: '智能 BI', desc: 'DataEase 真实部署：大屏清单 + iframe SSO' },
  '/analytics/population': { domain: 'population', title: '人口大数据', desc: '分析模型样例数据（≥100 行/模型）' },
  '/analytics/legal-entity': { domain: 'legal', title: '法人大数据', desc: '分析模型样例数据（≥100 行/模型）' },
  '/analytics/macro': { domain: 'macro', title: '宏观经济', desc: '分析模型样例数据（≥100 行/模型）' },
  '/analytics/key-domains': { domain: 'key', title: '重点领域', desc: '分析模型样例数据（≥100 行/模型）' },
}

const meta = computed(() => domainMeta[route.path] || { domain: 'bi', title: '分析域', desc: '' })
const isBi = computed(() => meta.value.domain === 'bi')

async function loadModels() {
  const res = await api.get('/analytics/models', { params: { domain: meta.value.domain } })
  models.value = res.data
  samples.value = []
  selectedId.value = undefined
  if (models.value.length) {
    await openModel(models.value[0].id)
  }
}

async function loadDashboards() {
  if (!isBi.value) return
  const res = await api.get('/analytics/dashboards')
  dashboards.value = res.data
}

async function openModel(id: number) {
  selectedId.value = id
  const res = await api.get(`/analytics/models/${id}/samples`)
  samples.value = res.data
}

async function issueEmbed(targetType: string, targetId: string) {
  const res = await api.post('/analytics/embed-token', { targetType, targetId })
  embedInfo.value = JSON.stringify(res.data, null, 2)
  ElMessage.success('已签发 DataEase 嵌入令牌')
  const url = res.data.embedUrl as string
  if (url) window.open(url, '_blank')
}

watch(
  () => route.path,
  () => {
    loadModels()
    loadDashboards()
  },
)

onMounted(() => {
  loadModels()
  loadDashboards()
})
</script>

<template>
  <div>
    <PageHeader :title="meta.title" :description="meta.desc" />

    <PageCard v-if="isBi" title="智能 BI 大屏（DataEase）" style="margin-bottom: 16px">
      <el-table class="portal-table" :data="dashboards" stripe>
        <el-table-column prop="dashCode" label="编码" width="140" />
        <el-table-column prop="dashName" label="名称" min-width="180" />
        <el-table-column prop="deDashboardId" label="DE Dashboard" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="issueEmbed('dashboard', row.deDashboardId)">
              iframe SSO
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard :title="`分析模型（${models.length}）`">
      <el-table class="portal-table" :data="models" stripe highlight-current-row @row-click="(row: Model) => openModel(row.id)">
        <el-table-column prop="modelCode" label="编码" width="120" />
        <el-table-column prop="modelName" label="名称" min-width="160" />
        <el-table-column prop="mCode" label="模块" width="90" />
        <el-table-column prop="deDashboardId" label="DE 看板" min-width="140" />
        <el-table-column prop="sampleRowCount" label="样例行" width="90" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openModel(row.id)">样例</el-button>
            <el-button link type="primary" @click.stop="issueEmbed('model', row.deDashboardId)">嵌入</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="samples.length" class="sample-block">
        <div class="sample-title">样例数据（前 {{ Math.min(samples.length, 20) }} / {{ samples.length }} 行）</div>
        <el-table class="portal-table" :data="samples.slice(0, 20)" stripe size="small">
          <el-table-column prop="rowNo" label="#" width="60" />
          <el-table-column prop="dim1" label="维度1" />
          <el-table-column prop="dim2" label="维度2" />
          <el-table-column prop="metric1" label="指标1" />
          <el-table-column prop="metric2" label="指标2" />
        </el-table>
      </div>

      <pre v-if="embedInfo" class="embed-log">{{ embedInfo }}</pre>
    </PageCard>
  </div>
</template>

<style scoped>
.sample-block {
  margin-top: 16px;
}
.sample-title {
  margin-bottom: 8px;
  font-weight: 600;
  color: #303133;
}
.embed-log {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 12px;
}
</style>
