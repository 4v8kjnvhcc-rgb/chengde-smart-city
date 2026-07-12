<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface StatMetric { id: number; metricName: string; metricValue: string; trendPct?: number; drillRoute?: string }
interface GuideStep { stepNo: number; stepName: string; stepDesc: string; requiredFlag: number }
interface Project { id: number; projectCode: string; projectName: string; boundOrgId: number; systemName: string }
interface DataSource { id: number; projectId: number; sourceName: string; sourceType: string; connStatus: string; tableCount: number }
interface Dict { id: number; dictCode: string; dictName: string; itemCount: number }
interface Upload { id: number; templateCode: string; fileName: string; rowCount: number; status: string }
interface Channel { id: number; channelCode: string; channelName: string; channelType: string; status: string; lastMessage?: string }
interface PipelineJob { id: number; jobCode: string; jobName: string; jobType: string; status: string; billAmount?: number }
interface Registry { id: number; registryCode: string; title: string; categoryPath: string; secretLevel: string; publishStatus: string; approvalStatus: string }
interface Policy { id: number; policyCode: string; policyName: string; policyType: string; lifecycleStage?: string }
interface HealthMetric { metricLabel: string; metricValue: string; alertLevel: string }

const route = useRoute()
const router = useRouter()

const tabMap: Record<string, string> = {
  stats: 'stats', 'stats-base': 'stats', 'stats-domain': 'stats', m037: 'stats', m038: 'stats',
  register: 'register', m039: 'register', m040: 'register', m050: 'register',
  upload: 'upload', m051: 'upload', m053: 'upload',
  channel: 'channel', m054: 'channel', m060: 'channel',
  pipeline: 'pipeline', m061: 'pipeline', m064: 'pipeline',
  resource: 'resource', m065: 'resource', m068: 'resource',
  govern: 'govern', m069: 'govern', m077: 'govern',
}

const tab = ref('stats')
const baseStats = ref<StatMetric[]>([])
const domainStats = ref<StatMetric[]>([])
const guides = ref<GuideStep[]>([])
const overview = ref<Record<string, unknown> | null>(null)
const projects = ref<Project[]>([])
const dataSources = ref<DataSource[]>([])
const dicts = ref<Dict[]>([])
const uploads = ref<Upload[]>([])
const channels = ref<Channel[]>([])
const pipelineJobs = ref<PipelineJob[]>([])
const registries = ref<Registry[]>([])
const policies = ref<Policy[]>([])
const health = ref<HealthMetric[]>([])
const globalView = ref<Record<string, unknown> | null>(null)
const reconcileResult = ref<Record<string, unknown> | null>(null)
const searchResult = ref<Record<string, unknown> | null>(null)

const projectForm = reactive({ projectName: '', systemName: '业务系统' })
const uploadForm = reactive({ templateCode: 'TPL_STRUCT_01', fileName: 'demo_upload.xlsx' })
const registryForm = reactive({ title: '', categoryPath: '政务数据/主题库', secretLevel: 'INTERNAL' })
const searchQ = ref('')

function resolveTab() {
  const q = String(route.query.tab || 'stats').toLowerCase()
  tab.value = tabMap[q] || 'stats'
}

async function loadStats() {
  const [b, d] = await Promise.all([
    api.get('/exchange/ingestion/stats/base'),
    api.get('/exchange/ingestion/stats/domain'),
  ])
  baseStats.value = b.data
  domainStats.value = d.data
}

async function loadRegister() {
  const [g, o, p, ds, di] = await Promise.all([
    api.get('/exchange/ingestion/guides'),
    api.get('/exchange/ingestion/register/overview'),
    api.get('/exchange/ingestion/projects'),
    api.get('/exchange/ingestion/data-sources'),
    api.get('/exchange/ingestion/dicts'),
  ])
  guides.value = g.data
  overview.value = o.data
  projects.value = p.data
  dataSources.value = ds.data
  dicts.value = di.data
}

async function loadUpload() {
  uploads.value = (await api.get('/exchange/ingestion/uploads')).data
}

async function loadChannel() {
  channels.value = (await api.get('/exchange/ingestion/channels')).data
}

async function loadPipeline() {
  pipelineJobs.value = (await api.get('/exchange/ingestion/pipeline-jobs')).data
}

async function loadResource() {
  registries.value = (await api.get('/exchange/ingestion/registries')).data
}

async function loadGovern() {
  const [p, h, g] = await Promise.all([
    api.get('/exchange/ingestion/policies'),
    api.get('/exchange/ingestion/health'),
    api.get('/exchange/ingestion/global-view'),
  ])
  policies.value = p.data
  health.value = h.data
  globalView.value = g.data
}

async function loadTabData() {
  try {
    if (tab.value === 'stats') await loadStats()
    if (tab.value === 'register') await loadRegister()
    if (tab.value === 'upload') await loadUpload()
    if (tab.value === 'channel') await loadChannel()
    if (tab.value === 'pipeline') await loadPipeline()
    if (tab.value === 'resource') await loadResource()
    if (tab.value === 'govern') await loadGovern()
  } catch { ElMessage.error('加载失败') }
}

async function createProject() {
  await api.post('/exchange/ingestion/projects', projectForm)
  ElMessage.success('项目已创建')
  projectForm.projectName = ''
  await loadRegister()
}

async function testDs(id: number) {
  await api.post(`/exchange/ingestion/data-sources/${id}/test`)
  ElMessage.success('连接测试通过')
  await loadRegister()
}

async function doUpload() {
  await api.post('/exchange/ingestion/uploads', uploadForm)
  ElMessage.success('上传解析完成')
  await loadUpload()
}

async function runChannel(id: number) {
  const res = await api.post(`/exchange/ingestion/channels/${id}/run`)
  ElMessage.success(res.data.message)
  await loadChannel()
}

async function runPipeline(jobType: string) {
  await api.post('/exchange/ingestion/pipeline-jobs/run', { jobType, jobName: `${jobType} demo` })
  ElMessage.success(`${jobType} 作业完成`)
  await loadPipeline()
}

async function callReconcile(action: string) {
  reconcileResult.value = (await api.get(`/exchange/ingestion/reconcile/${action}`)).data
}

async function createRegistry() {
  if (!registryForm.title) return
  await api.post('/exchange/ingestion/registries', registryForm)
  ElMessage.success('编目已创建')
  registryForm.title = ''
  await loadResource()
}

async function approveRegistry(id: number) {
  await api.post(`/exchange/ingestion/registries/${id}/approve`, { action: 'APPROVE' })
  ElMessage.success('审批通过')
  await loadResource()
}

async function runLifecycle(id: number) {
  const res = await api.post(`/exchange/ingestion/policies/${id}/lifecycle`)
  ElMessage.success(`生命周期: ${res.data.lifecycleStage}`)
}

async function doSearch() {
  searchResult.value = (await api.get('/exchange/ingestion/search', { params: { q: searchQ.value } })).data
}

function goDrill(routePath?: string) {
  if (routePath) router.push(routePath)
}

const tabTitle = computed(() => ({
  stats: 'M037/M038 统计分析', register: 'M039-M050 资产登记', upload: 'M051-M053 数据上传',
  channel: 'M054-M060 多通道接入', pipeline: 'M061-M064 规范设计', resource: 'M065-M068 资源目录',
  govern: 'M069-M077 治理运维',
}[tab.value] || '大数据归集'))

watch(tab, () => {
  router.replace({ query: { ...route.query, tab: tab.value } })
  loadTabData()
})
watch(() => route.query.tab, resolveTab)
onMounted(() => { resolveTab(); loadTabData() })
</script>

<template>
  <div>
    <PageHeader :title="`大数据归集平台 · ${tabTitle}`" description="M037～M077：统计、登记、上传、接入、处理、目录与治理运维" />
    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="统计分析" name="stats">
        <PageCard title="M037 基础库统计">
          <el-row :gutter="12">
            <el-col v-for="s in baseStats" :key="s.id" :span="6">
              <el-card shadow="hover" class="stat-card" @click="goDrill(s.drillRoute)">
                <div class="stat-name">{{ s.metricName }}</div>
                <div class="stat-val">{{ s.metricValue }}</div>
                <div v-if="s.trendPct" class="stat-trend">{{ s.trendPct > 0 ? '+' : '' }}{{ s.trendPct }}%</div>
              </el-card>
            </el-col>
          </el-row>
        </PageCard>
        <PageCard title="M038 重点领域统计">
          <el-table :data="domainStats" stripe size="small">
            <el-table-column prop="metricName" label="专题" />
            <el-table-column prop="metricValue" label="指标值" />
            <el-table-column prop="trendPct" label="趋势%" width="90" />
            <el-table-column label="下钻" width="80">
              <template #default="{ row }"><el-button link @click="goDrill(row.drillRoute)">查看</el-button></template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="资产登记" name="register">
        <PageCard title="M039 填报指引（11项）">
          <el-steps :active="11" finish-status="success" simple>
            <el-step v-for="g in guides" :key="g.stepNo" :title="g.stepName" />
          </el-steps>
        </PageCard>
        <PageCard title="M040-M050 项目/数据源/字典">
          <el-form inline>
            <el-form-item label="项目名"><el-input v-model="projectForm.projectName" /></el-form-item>
            <el-form-item label="系统"><el-input v-model="projectForm.systemName" /></el-form-item>
            <el-button type="primary" @click="createProject">登记项目</el-button>
          </el-form>
          <el-descriptions v-if="overview" :column="4" border size="small" style="margin-bottom:12px">
            <el-descriptions-item label="项目">{{ overview.projects }}</el-descriptions-item>
            <el-descriptions-item label="数据源">{{ overview.dataSources }}</el-descriptions-item>
            <el-descriptions-item label="字典">{{ overview.dicts }}</el-descriptions-item>
            <el-descriptions-item label="资产">{{ overview.assets }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="dataSources" stripe size="small">
            <el-table-column prop="sourceName" label="数据源" />
            <el-table-column prop="sourceType" label="类型" width="90" />
            <el-table-column prop="connStatus" label="连接" width="90" />
            <el-table-column prop="tableCount" label="表数" width="70" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }"><el-button link @click="testDs(row.id)">测试</el-button></template>
            </el-table-column>
          </el-table>
          <el-space v-if="overview?.systemLinks" style="margin-top:12px">
            <el-button v-for="l in (overview.systemLinks as {route:string;label:string}[])" :key="l.route" @click="router.push(l.route)">{{ l.label }}</el-button>
          </el-space>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="数据上传" name="upload">
        <PageCard>
          <el-form inline>
            <el-form-item label="模板"><el-input v-model="uploadForm.templateCode" /></el-form-item>
            <el-form-item label="文件"><el-input v-model="uploadForm.fileName" /></el-form-item>
            <el-button type="primary" @click="doUpload">上传并解析</el-button>
          </el-form>
          <el-table :data="uploads" stripe>
            <el-table-column prop="fileName" label="文件" />
            <el-table-column prop="templateCode" label="模板" width="140" />
            <el-table-column prop="rowCount" label="行数" width="80" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="多通道接入" name="channel">
        <PageCard>
          <el-table :data="channels" stripe>
            <el-table-column prop="channelName" label="通道" min-width="160" />
            <el-table-column prop="channelType" label="类型" width="100" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column prop="lastMessage" label="最近执行" min-width="220" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }"><el-button link type="primary" @click="runChannel(row.id)">执行</el-button></template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="规范设计" name="pipeline">
        <PageCard>
          <el-space wrap style="margin-bottom:12px">
            <el-button @click="runPipeline('PROBE')">M061 探查</el-button>
            <el-button @click="runPipeline('DEFINE')">M062 定义</el-button>
            <el-button @click="runPipeline('READ')">M063 读取</el-button>
            <el-button @click="runPipeline('RECONCILE')">M064 对账</el-button>
            <el-button @click="callReconcile('analysis')">对账分析API</el-button>
            <el-button @click="callReconcile('alert')">告警API</el-button>
            <el-button @click="callReconcile('logs')">日志API</el-button>
            <el-button @click="callReconcile('anomaly')">异常API</el-button>
          </el-space>
          <el-table :data="pipelineJobs" stripe>
            <el-table-column prop="jobType" label="类型" width="100" />
            <el-table-column prop="jobName" label="作业" min-width="160" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column prop="billAmount" label="账单" width="90" />
          </el-table>
          <el-alert v-if="reconcileResult" :title="JSON.stringify(reconcileResult)" type="info" style="margin-top:12px" />
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="资源目录" name="resource">
        <PageCard>
          <el-form inline>
            <el-form-item label="标题"><el-input v-model="registryForm.title" /></el-form-item>
            <el-form-item label="分类"><el-input v-model="registryForm.categoryPath" /></el-form-item>
            <el-button type="primary" @click="createRegistry">新建编目</el-button>
          </el-form>
          <el-table :data="registries" stripe>
            <el-table-column prop="registryCode" label="编码" width="140" />
            <el-table-column prop="title" label="标题" min-width="160" />
            <el-table-column prop="secretLevel" label="涉密" width="90" />
            <el-table-column prop="approvalStatus" label="审批" width="100" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button v-if="row.approvalStatus === 'PENDING'" link @click="approveRegistry(row.id)">审批</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="治理运维" name="govern">
        <PageCard title="M069-M075 策略与生命周期">
          <el-table :data="policies" stripe size="small">
            <el-table-column prop="policyName" label="策略" />
            <el-table-column prop="policyType" label="类型" width="100" />
            <el-table-column prop="ruleExpr" label="规则" min-width="180" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.lifecycleStage" link @click="runLifecycle(row.id)">演练</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
        <PageCard title="M072 元数据搜索">
          <el-input v-model="searchQ" placeholder="搜索编目/资产" style="max-width:320px;margin-right:8px" @keyup.enter="doSearch" />
          <el-button @click="doSearch">搜索</el-button>
          <el-tag v-if="searchResult" style="margin-left:8px">引擎: {{ searchResult.engine }}</el-tag>
        </PageCard>
        <PageCard title="M076/M077 全局视图与健康">
          <el-descriptions v-if="globalView" :column="3" border size="small">
            <el-descriptions-item label="资产总数">{{ globalView.totalAssets }}</el-descriptions-item>
            <el-descriptions-item label="接入通道">{{ globalView.ingestChannels }}</el-descriptions-item>
            <el-descriptions-item label="已发布编目">{{ globalView.publishedRegistries }}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="health" stripe size="small" style="margin-top:12px">
            <el-table-column prop="metricLabel" label="指标" />
            <el-table-column prop="metricValue" label="值" />
            <el-table-column prop="alertLevel" label="级别" width="90" />
          </el-table>
        </PageCard>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.stat-card { cursor: pointer; margin-bottom: 12px; }
.stat-name { font-size: 13px; color: #909399; }
.stat-val { font-size: 20px; font-weight: 600; margin: 6px 0; }
.stat-trend { font-size: 12px; color: #67c23a; }
</style>
