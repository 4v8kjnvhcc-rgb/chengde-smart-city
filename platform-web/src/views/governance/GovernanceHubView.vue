<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Rule { id: number; ruleCode: string; ruleName: string; ruleType: string; status: string }
interface QTask { id: number; taskName: string; status: string; lastScore?: number; lastMessage?: string }
interface Standard { id: number; itemCode: string; itemName: string; itemType: string; standardRef?: string }
interface Connector { id: number; connectorCode: string; connectorName: string; sourceType: string; lastMessage?: string }
interface MetaEntry { id: number; entryCode: string; entryName: string; entryType: string; omRef?: string }
interface FusionAsset { id: number; assetCode: string; assetName: string; assetType: string; status: string; lastMessage?: string }
interface CatalogRes { id: number; resourceCode: string; resourceName: string; resourceType: string; publishStatus: string; approvalStatus: string; subscriptionStatus?: string }

const route = useRoute()
const router = useRouter()

const tabMap: Record<string, string> = {
  quality: 'quality', m078: 'quality', m085: 'quality', m102: 'quality', m105: 'quality',
  metadata: 'metadata', m086: 'metadata', m097: 'metadata',
  etl: 'etl', m098: 'etl', m099: 'etl', m101: 'etl',
  model: 'model', m106: 'model', m111: 'model',
  catalog: 'catalog', m112: 'catalog', m122: 'catalog',
}

const tab = ref('quality')
const integration = ref<Record<string, unknown>>({})
const rules = ref<Rule[]>([])
const qTasks = ref<QTask[]>([])
const standards = ref<Standard[]>([])
const connectors = ref<Connector[]>([])
const metaEntries = ref<MetaEntry[]>([])
const lineage = ref<Record<string, unknown> | null>(null)
const omServices = ref<unknown[]>([])
const fusionAssets = ref<FusionAsset[]>([])
const catalogRes = ref<CatalogRes[]>([])

const ruleForm = reactive({ ruleName: '', ruleType: 'COMPLETENESS' })
const taskForm = reactive({ taskName: '', ruleId: undefined as number | undefined })
const connectorForm = reactive({ connectorName: '', sourceType: 'MySQL' })
const catalogForm = reactive({ resourceName: '', resourceType: 'DATA', categoryPath: '政务/目录' })

function resolveTab() {
  const q = String(route.query.tab || 'quality').toLowerCase()
  tab.value = tabMap[q] || 'quality'
}

async function loadIntegration() {
  try {
    integration.value = (await api.get('/integration/health')).data || {}
  } catch { integration.value = {} }
}

async function loadQuality() {
  const res = await api.get('/governance/platform/quality/overview')
  rules.value = res.data.rules
  qTasks.value = res.data.tasks
  standards.value = res.data.standards
}

async function loadMetadata() {
  const res = await api.get('/governance/platform/metadata/overview')
  connectors.value = res.data.connectors
  metaEntries.value = res.data.registry
  lineage.value = res.data.lineageGraph
  omServices.value = res.data.omServices || []
}

async function loadEtl() {
  fusionAssets.value = (await api.get('/governance/platform/fusion/assets')).data
}

async function loadModel() {
  fusionAssets.value = (await api.get('/governance/platform/fusion/assets', { params: { assetType: 'MODEL' } })).data
  const all = await api.get('/governance/platform/fusion/assets')
  fusionAssets.value = all.data.filter((a: FusionAsset) => ['MODEL', 'SCRIPT', 'WORKFLOW', 'COMPONENT'].includes(a.assetType))
}

async function loadCatalog() {
  catalogRes.value = (await api.get('/governance/platform/catalog/resources')).data
}

async function loadTabData() {
  await loadIntegration()
  try {
    if (tab.value === 'quality') await loadQuality()
    if (tab.value === 'metadata') await loadMetadata()
    if (tab.value === 'etl') await loadEtl()
    if (tab.value === 'model') await loadModel()
    if (tab.value === 'catalog') await loadCatalog()
  } catch { ElMessage.error('加载失败') }
}

async function createRule() {
  await api.post('/governance/quality/rules', ruleForm)
  ElMessage.success('规则已创建')
  ruleForm.ruleName = ''
  await loadQuality()
}

async function createTask() {
  await api.post('/governance/quality/tasks', taskForm)
  ElMessage.success('任务已创建')
  taskForm.taskName = ''
  await loadQuality()
}

async function runTask(id: number) {
  const res = await api.post(`/governance/quality/tasks/${id}/run`)
  ElMessage.success(`评分 ${res.data.score}`)
  await loadQuality()
}

async function genReport() {
  await api.post('/governance/platform/quality/reports', { reportName: '六性质量报告', dimension: '完整性+准确性' })
  ElMessage.success('报告已生成')
  await loadQuality()
}

async function createConnector() {
  await api.post('/governance/connectors', connectorForm)
  ElMessage.success('适配器已创建')
  connectorForm.connectorName = ''
  await loadMetadata()
}

async function syncConnector(id: number) {
  const res = await api.post(`/governance/connectors/${id}/sync`)
  ElMessage.success(res.data.message)
  await loadMetadata()
}

async function runFusion(id: number) {
  const res = await api.post(`/governance/platform/fusion/assets/${id}/run`)
  ElMessage.success(res.data.message)
  await loadTabData()
}

async function createCatalog() {
  if (!catalogForm.resourceName) return
  await api.post('/governance/platform/catalog/resources', catalogForm)
  ElMessage.success('资源已编目')
  catalogForm.resourceName = ''
  await loadCatalog()
}

async function approveRes(id: number) {
  await api.post(`/governance/platform/catalog/resources/${id}/approve`, { action: 'APPROVE' })
  ElMessage.success('审批通过')
  await loadCatalog()
}

async function subscribeRes(id: number) {
  await api.post(`/governance/platform/catalog/resources/${id}/subscribe`)
  ElMessage.success('订阅申请已提交')
  await loadCatalog()
}

async function distributeRes(id: number) {
  const res = await api.post(`/governance/platform/catalog/resources/${id}/distribute`)
  ElMessage.success(`分发成功 ${res.data.invokeResult}`)
  await loadCatalog()
}

const tabTitle = computed(() => ({
  quality: 'M078-M105 质量与标准', metadata: 'M086-M097 元数据', etl: 'M098-M101 治理ETL',
  model: 'M106-M111 模型与融合', catalog: 'M112-M122 资源目录',
}[tab.value] || '融合治理'))

watch(tab, () => {
  router.replace({ query: { ...route.query, tab: tab.value } })
  loadTabData()
})
watch(() => route.query.tab, resolveTab)
onMounted(() => { resolveTab(); loadTabData() })
</script>

<template>
  <div>
    <PageHeader :title="`数据融合治理 · ${tabTitle}`" description="M078～M122：OpenMetadata + 质量中心 + ETL/模型 + 资源目录" />
    <el-alert v-if="integration.openmetadata !== undefined" type="info" :closable="false" show-icon
      :title="`OpenMetadata=${integration.openmetadata ? 'UP' : 'DOWN'} · 集成=${integration.enabled ? 'ON' : 'OFF'}`"
      style="margin-bottom:12px" />
    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="质量与标准" name="quality">
        <PageCard title="M078/M079 质量规则与任务">
          <el-form inline>
            <el-form-item label="规则"><el-input v-model="ruleForm.ruleName" /></el-form-item>
            <el-select v-model="ruleForm.ruleType" style="width:140px">
              <el-option label="完整性" value="COMPLETENESS" /><el-option label="准确性" value="ACCURACY" />
            </el-select>
            <el-button type="primary" @click="createRule">新增规则</el-button>
            <el-button @click="genReport">M083 生成报告</el-button>
          </el-form>
          <el-table :data="rules" stripe size="small">
            <el-table-column prop="ruleCode" label="编码" width="140" />
            <el-table-column prop="ruleName" label="名称" />
            <el-table-column prop="ruleType" label="类型" width="120" />
          </el-table>
          <el-divider />
          <el-form inline>
            <el-form-item label="任务"><el-input v-model="taskForm.taskName" /></el-form-item>
            <el-select v-model="taskForm.ruleId" clearable style="width:180px">
              <el-option v-for="r in rules" :key="r.id" :label="r.ruleName" :value="r.id" />
            </el-select>
            <el-button type="primary" @click="createTask">创建任务</el-button>
          </el-form>
          <el-table :data="qTasks" stripe size="small">
            <el-table-column prop="taskName" label="任务" />
            <el-table-column prop="lastScore" label="评分" width="80" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }"><el-button link @click="runTask(row.id)">执行</el-button></template>
            </el-table-column>
          </el-table>
        </PageCard>
        <PageCard title="M102-M105 标准体系">
          <el-table :data="standards" stripe size="small">
            <el-table-column prop="itemCode" label="编码" width="140" />
            <el-table-column prop="itemName" label="名称" />
            <el-table-column prop="itemType" label="类型" width="100" />
            <el-table-column prop="standardRef" label="引用标准" />
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="元数据" name="metadata">
        <PageCard title="M086-M097 OpenMetadata">
          <el-form inline>
            <el-form-item label="适配器"><el-input v-model="connectorForm.connectorName" /></el-form-item>
            <el-button type="primary" @click="createConnector">新增</el-button>
          </el-form>
          <el-table :data="connectors" stripe size="small">
            <el-table-column prop="connectorName" label="名称" />
            <el-table-column prop="sourceType" label="类型" width="90" />
            <el-table-column prop="lastMessage" label="同步" min-width="200" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }"><el-button link @click="syncConnector(row.id)">采集</el-button></template>
            </el-table-column>
          </el-table>
          <el-table :data="metaEntries" stripe size="small" style="margin-top:12px">
            <el-table-column prop="entryName" label="元数据条目" />
            <el-table-column prop="entryType" label="类型" width="100" />
            <el-table-column prop="omRef" label="OM引用" />
          </el-table>
          <el-tag v-if="omServices.length" style="margin-top:8px">OM服务 {{ omServices.length }} 个</el-tag>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="治理ETL" name="etl">
        <PageCard>
          <el-space wrap style="margin-bottom:12px">
            <el-button @click="router.push('/integration/kettle')">M099 Kettle ETL</el-button>
            <el-button @click="router.push('/integration/ds')">M100 DS监控</el-button>
          </el-space>
          <el-table :data="fusionAssets.filter(a => ['GOVERN_TASK','ETL','KETTLE'].includes(a.assetType))" stripe>
            <el-table-column prop="assetName" label="治理资产" />
            <el-table-column prop="assetType" label="类型" width="110" />
            <el-table-column prop="refIntegration" label="集成" width="120" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }"><el-button link @click="runFusion(row.id)">执行</el-button></template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="模型融合" name="model">
        <PageCard title="M106-M111 逻辑/物理/脚本/工作流/算子">
          <el-table :data="fusionAssets" stripe>
            <el-table-column prop="assetName" label="资产" />
            <el-table-column prop="assetType" label="类型" width="110" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }"><el-button link @click="runFusion(row.id)">运行</el-button></template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="资源目录" name="catalog">
        <PageCard>
          <el-form inline>
            <el-form-item label="资源名"><el-input v-model="catalogForm.resourceName" /></el-form-item>
            <el-button type="primary" @click="createCatalog">编目</el-button>
          </el-form>
          <el-table :data="catalogRes" stripe>
            <el-table-column prop="resourceCode" label="编码" width="130" />
            <el-table-column prop="resourceName" label="名称" />
            <el-table-column prop="resourceType" label="类型" width="80" />
            <el-table-column prop="approvalStatus" label="审批" width="90" />
            <el-table-column prop="subscriptionStatus" label="订阅" width="100" />
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button v-if="row.approvalStatus==='PENDING'" link @click="approveRes(row.id)">审批</el-button>
                <el-button v-if="row.publishStatus==='PUBLISHED'&&!row.subscriptionStatus" link @click="subscribeRes(row.id)">订阅</el-button>
                <el-button v-if="row.subscriptionStatus==='SUBSCRIBED'" link @click="distributeRes(row.id)">分发</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
