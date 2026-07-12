<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Template {
  id: number
  templateCode: string
  templateName: string
  demandType: string
}

interface Demand {
  id: number
  demandTitle: string
  requesterOrg: string
  targetCatalogId?: number
  status: string
  stage: string
  demandType: string
  templateCode?: string
  matchedCatalogId?: number
  matchScore?: number
  analysisNote?: string
  assigneeOrg?: string
  supplyMode?: string
  confirmNote?: string
}

interface Catalog {
  id: number
  catalogCode: string
  title: string
  description: string
  publishStatus: string
}

interface SupplyTask {
  id: number
  demandId: number
  taskType: string
  taskName: string
  status: string
  refFlowCode?: string
}

interface Objection {
  id: number
  catalogId: number
  objectionType: string
  content: string
  status: string
  handlerNote?: string
}

interface Manifest {
  id: number
  manifestType: string
  refId: number
  title: string
  status: string
  authLevel?: string
  cascadeFlag?: number
}

const route = useRoute()
const router = useRouter()

const tabMap: Record<string, string> = {
  demand: 'm020', m020: 'm020', '020': 'm020',
  analysis: 'm021', m021: 'm021', '021': 'm021',
  confirm: 'm022', m022: 'm022', '022': 'm022',
  supply: 'm023', m023: 'm023', '023': 'm023',
  catalog: 'm024', m024: 'm024', '024': 'm024',
  objection: 'm025', m025: 'm025', '025': 'm025',
  manifest: 'm026', m026: 'm026', '026': 'm026',
}

const tabToQuery: Record<string, string> = {
  m020: 'demand', m021: 'analysis', m022: 'confirm', m023: 'supply',
  m024: 'catalog', m025: 'objection', m026: 'manifest',
}

const tab = ref('m020')
const templates = ref<Template[]>([])
const demands = ref<Demand[]>([])
const catalogs = ref<Catalog[]>([])
const supplyTasks = ref<SupplyTask[]>([])
const objections = ref<Objection[]>([])
const manifests = ref<Manifest[]>([])
const analysisResult = ref<Record<string, unknown> | null>(null)
const supplyView = ref<Record<string, unknown> | null>(null)
const selectedDemandId = ref<number>()

const demandForm = reactive({
  demandTitle: '',
  requesterOrg: '数据管理局',
  demandType: 'STRUCTURED',
  templateCode: '',
  targetCatalogId: undefined as number | undefined,
})

const catalogForm = reactive({ title: '', description: '' })
const dispatchForm = reactive({ assigneeOrg: '资源管理处', analysisNote: '' })
const confirmNote = ref('供需对接确认，生成归集与共享任务')
const objectionForm = reactive({ catalogId: undefined as number | undefined, objectionType: 'QUALITY', content: '' })

function resolveTab() {
  const q = String(route.query.tab || 'demand').toLowerCase()
  tab.value = tabMap[q] || 'm020'
}

const statusTag = (s: string) => {
  if (['CONFIRMED', 'APPROVED', 'CLOSED'].includes(s)) return 'success'
  if (['REJECTED', 'RETURNED', 'WITHDRAWN'].includes(s)) return 'danger'
  if (['DISPATCHED', 'ANALYZING'].includes(s)) return 'warning'
  return 'info'
}

async function loadDemands() {
  const res = await api.get('/exchange/supply/demands')
  demands.value = res.data
}

async function loadTemplates() {
  const res = await api.get('/exchange/supply/templates')
  templates.value = res.data
}

async function loadCatalogs() {
  const res = await api.get('/exchange/supply/catalog-manifest')
  catalogs.value = res.data
}

async function loadObjections() {
  const res = await api.get('/exchange/supply/objections')
  objections.value = res.data
}

async function loadManifests() {
  const res = await api.get('/exchange/supply/manifests')
  manifests.value = res.data
}

async function loadTabData() {
  try {
    if (tab.value === 'm020') {
      await Promise.all([loadTemplates(), loadDemands()])
    }
    if (tab.value === 'm021') {
      await loadDemands()
    }
    if (tab.value === 'm022') {
      await loadDemands()
    }
    if (tab.value === 'm023') {
      await loadDemands()
      if (selectedDemandId.value) await loadSupplyView(selectedDemandId.value)
    }
    if (tab.value === 'm024') {
      await loadCatalogs()
    }
    if (tab.value === 'm025') {
      await Promise.all([loadCatalogs(), loadObjections()])
    }
    if (tab.value === 'm026') {
      await loadManifests()
    }
  } catch {
    ElMessage.error('加载失败')
  }
}

async function submitDemand() {
  if (!demandForm.demandTitle) {
    ElMessage.warning('请填写需求标题')
    return
  }
  await api.post('/exchange/supply/demands', demandForm)
  ElMessage.success('需求已提交')
  demandForm.demandTitle = ''
  await loadDemands()
}

async function withdrawDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/withdraw`)
  ElMessage.success('需求已撤销')
  await loadDemands()
}

async function analyzeDemand(id: number) {
  const res = await api.post(`/exchange/supply/demands/${id}/analyze`)
  analysisResult.value = res.data
  ElMessage.success('智能匹配完成')
  await loadDemands()
}

async function dispatchDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/dispatch`, dispatchForm)
  ElMessage.success('已分发')
  await loadDemands()
}

async function returnDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/return`, { analysisNote: '材料不全，请补充' })
  ElMessage.success('已退回')
  await loadDemands()
}

async function confirmDemand(id: number) {
  const res = await api.post(`/exchange/supply/demands/${id}/confirm`, {
    confirmNote: confirmNote.value,
    supplyMode: 'EXCHANGE',
    authLevel: 'DEPT',
    cascadeFlag: 0,
  })
  ElMessage.success(`已确认，生成 ${(res.data.tasks as unknown[]).length} 项供给任务`)
  selectedDemandId.value = id
  await loadDemands()
}

async function rejectDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/reject`, { confirmNote: '不符合共享范围' })
  ElMessage.success('已驳回')
  await loadDemands()
}

async function loadSupplyView(id: number) {
  selectedDemandId.value = id
  const res = await api.get(`/exchange/supply/supply-view/${id}`)
  supplyView.value = res.data
  const tasks = await api.get('/exchange/supply/supply-tasks', { params: { demandId: id } })
  supplyTasks.value = tasks.data
}

async function createCatalog() {
  if (!catalogForm.title) return
  await api.post('/exchange/supply/catalog', catalogForm)
  ElMessage.success('目录已创建')
  catalogForm.title = ''
  catalogForm.description = ''
  await loadCatalogs()
}

async function publishCatalog(id: number) {
  await api.post(`/exchange/supply/catalog/${id}/publish`)
  ElMessage.success('目录已发布')
  await loadCatalogs()
}

async function offlineCatalog(id: number) {
  await api.post(`/exchange/supply/catalog/${id}/offline`, { reason: '目录维护' })
  ElMessage.success('目录已下线')
  await loadCatalogs()
}

async function exportCatalog() {
  const res = await api.get('/exchange/supply/catalog-manifest/export')
  ElMessage.success(`已导出 ${res.data.rowCount} 条目录清单`)
}

async function submitObjection() {
  if (!objectionForm.catalogId || !objectionForm.content) {
    ElMessage.warning('请填写异议')
    return
  }
  await api.post('/exchange/supply/objections', objectionForm)
  ElMessage.success('异议已登记')
  objectionForm.content = ''
  await loadObjections()
}

async function closeObjection(id: number) {
  await api.post(`/exchange/supply/objections/${id}/process`, { action: 'CLOSE', handlerNote: '已核实处理' })
  ElMessage.success('异议已关闭')
  await loadObjections()
}

async function exportManifest(id: number) {
  await api.post(`/exchange/supply/manifests/${id}/export`)
  ElMessage.success('清单已导出')
}

const tabTitle = computed(() => ({
  m020: 'M020 数据需求管理', m021: 'M021 数据需求分析', m022: 'M022 数据需求确认',
  m023: 'M023 数据供给查看', m024: 'M024 目录清单', m025: 'M025 异议清单', m026: 'M026 供需清单',
}[tab.value] || '供需对接'))

watch(tab, () => {
  router.replace({ query: { ...route.query, tab: tabToQuery[tab.value] || 'demand' } })
  loadTabData()
})
watch(() => route.query.tab, resolveTab)
onMounted(() => { resolveTab(); loadTabData() })
</script>

<template>
  <div>
    <PageHeader :title="`应用平台 · ${tabTitle}`" description="M020～M026：需求管理、分析分发、确认供给、目录与异议清单" />
    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="M020 需求管理" name="m020">
        <PageCard>
          <el-form inline>
            <el-form-item label="模板">
              <el-select v-model="demandForm.templateCode" clearable style="width: 180px">
                <el-option v-for="t in templates" :key="t.templateCode" :label="t.templateName" :value="t.templateCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="demandForm.demandType" style="width: 130px">
                <el-option label="结构化" value="STRUCTURED" />
                <el-option label="非结构化" value="UNSTRUCTURED" />
              </el-select>
            </el-form-item>
            <el-form-item label="标题"><el-input v-model="demandForm.demandTitle" /></el-form-item>
            <el-form-item label="申请方"><el-input v-model="demandForm.requesterOrg" /></el-form-item>
            <el-button type="primary" @click="submitDemand">提交需求</el-button>
          </el-form>
          <el-table :data="demands" stripe>
            <el-table-column prop="demandTitle" label="需求" min-width="160" />
            <el-table-column prop="demandType" label="类型" width="100" />
            <el-table-column prop="stage" label="阶段" width="90" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status === 'SUBMITTED'" link type="danger" @click="withdrawDemand(row.id)">撤销</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="M021 需求分析" name="m021">
        <PageCard>
          <el-table :data="demands.filter(d => d.status === 'SUBMITTED' || d.status === 'ANALYZING' || d.status === 'DISPATCHED')" stripe>
            <el-table-column prop="demandTitle" label="需求" min-width="160" />
            <el-table-column prop="matchScore" label="匹配度" width="80" />
            <el-table-column prop="analysisNote" label="分析说明" min-width="200" />
            <el-table-column prop="assigneeOrg" label="分发单位" width="120" />
            <el-table-column label="操作" width="260">
              <template #default="{ row }">
                <el-button link type="primary" @click="analyzeDemand(row.id)">智能匹配</el-button>
                <el-button link @click="dispatchDemand(row.id)">分发</el-button>
                <el-button link type="warning" @click="returnDemand(row.id)">退回</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-alert v-if="analysisResult" type="info" :closable="false" style="margin-top:12px"
            :title="`匹配目录 ID=${analysisResult.matchedCatalogId} 得分=${analysisResult.matchScore}`" />
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="M022 需求确认" name="m022">
        <PageCard>
          <el-input v-model="confirmNote" style="max-width:480px;margin-bottom:12px" />
          <el-table :data="demands.filter(d => ['ANALYZING','DISPATCHED','SUBMITTED'].includes(d.status))" stripe>
            <el-table-column prop="demandTitle" label="需求" min-width="160" />
            <el-table-column prop="matchedCatalogId" label="匹配目录" width="100" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="success" @click="confirmDemand(row.id)">确认</el-button>
                <el-button link type="danger" @click="rejectDemand(row.id)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="M023 供给查看" name="m023">
        <PageCard>
          <el-select v-model="selectedDemandId" placeholder="选择已确认需求" style="width:280px;margin-bottom:12px" @change="loadSupplyView">
            <el-option v-for="d in demands.filter(x => x.status === 'CONFIRMED')" :key="d.id" :label="d.demandTitle" :value="d.id" />
          </el-select>
          <el-table v-if="supplyTasks.length" :data="supplyTasks" stripe>
            <el-table-column prop="taskType" label="类型" width="100" />
            <el-table-column prop="taskName" label="任务" min-width="200" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="refFlowCode" label="交换流" width="140" />
          </el-table>
          <el-empty v-else description="请选择已确认需求查看供给" />
          <el-descriptions v-if="supplyView" :column="2" border style="margin-top:16px">
            <el-descriptions-item label="共享页">{{ supplyView.sharePageUrl }}</el-descriptions-item>
            <el-descriptions-item label="API">{{ supplyView.apiEndpoint }}</el-descriptions-item>
          </el-descriptions>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="M024 目录清单" name="m024">
        <PageCard>
          <el-form inline>
            <el-form-item label="标题"><el-input v-model="catalogForm.title" /></el-form-item>
            <el-form-item label="说明"><el-input v-model="catalogForm.description" /></el-form-item>
            <el-button type="primary" @click="createCatalog">新建</el-button>
            <el-button @click="exportCatalog">导出清单</el-button>
          </el-form>
          <el-table :data="catalogs" stripe>
            <el-table-column prop="catalogCode" label="编码" width="140" />
            <el-table-column prop="title" label="标题" min-width="160" />
            <el-table-column prop="publishStatus" label="状态" width="100" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button v-if="row.publishStatus === 'DRAFT'" link type="primary" @click="publishCatalog(row.id)">发布</el-button>
                <el-button v-if="row.publishStatus === 'PUBLISHED'" link type="warning" @click="offlineCatalog(row.id)">下线</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="M025 异议清单" name="m025">
        <PageCard>
          <el-form inline>
            <el-form-item label="目录">
              <el-select v-model="objectionForm.catalogId" style="width:200px">
                <el-option v-for="c in catalogs" :key="c.id" :label="c.title" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="objectionForm.objectionType" style="width:120px">
                <el-option label="质量" value="QUALITY" />
                <el-option label="完整性" value="COMPLETENESS" />
                <el-option label="授权" value="AUTH" />
              </el-select>
            </el-form-item>
            <el-form-item label="内容"><el-input v-model="objectionForm.content" style="width:240px" /></el-form-item>
            <el-button type="primary" @click="submitObjection">登记异议</el-button>
          </el-form>
          <el-table :data="objections" stripe>
            <el-table-column prop="catalogId" label="目录ID" width="90" />
            <el-table-column prop="objectionType" label="类型" width="100" />
            <el-table-column prop="content" label="内容" min-width="200" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'CLOSED'" link @click="closeObjection(row.id)">关闭</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="M026 供需清单" name="m026">
        <PageCard>
          <el-table :data="manifests" stripe>
            <el-table-column prop="manifestType" label="类型" width="120" />
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column prop="authLevel" label="授权级别" width="100" />
            <el-table-column prop="cascadeFlag" label="级联" width="70">
              <template #default="{ row }">{{ row.cascadeFlag ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button link type="primary" @click="exportManifest(row.id)">导出</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
