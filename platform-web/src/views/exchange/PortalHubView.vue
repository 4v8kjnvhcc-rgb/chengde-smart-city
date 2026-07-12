<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout from '@/components/common/HubSideLayout.vue'

const navItems = [
  { key: 'm032', label: 'M032 门户首页' },
  { key: 'm034', label: 'M034 资源目录' },
  { key: 'm033', label: 'M033 全文检索' },
  { key: 'm035', label: 'M035 订阅申请' },
  { key: 'm036', label: 'M036 八态势' },
]

interface CatalogRow {
  id: number | string
  catalogCode: string
  title: string
  description: string
  publishStatus?: string
  source?: string
  score?: number
}

interface Subscription {
  id: number
  catalogId: number
  applicantOrg: string
  resourceType: string
  purpose: string
  status: string
  approverNote?: string
  createdBy?: string
}

interface Situation {
  id: number
  situationCode: string
  situationName: string
  domainRoute: string
  modelMCode: string
  summaryMetric: string
}

interface HomeData {
  publishedCount: number
  subscriptionTotal: number
  subscriptionPending: number
  situationCount: number
  searchKeyword: string
  recommendations: CatalogRow[]
  themes: { code: string; name: string; route: string }[]
}

const route = useRoute()
const router = useRouter()

const tabMap: Record<string, string> = {
  home: 'm032',
  m032: 'm032',
  '032': 'm032',
  search: 'm033',
  m033: 'm033',
  '033': 'm033',
  catalog: 'm034',
  m034: 'm034',
  '034': 'm034',
  subscribe: 'm035',
  m035: 'm035',
  '035': 'm035',
  situation: 'm036',
  m036: 'm036',
  '036': 'm036',
}

const tabToQuery: Record<string, string> = {
  m032: 'home',
  m033: 'search',
  m034: 'catalog',
  m035: 'subscribe',
  m036: 'situation',
}

const tab = ref('m032')
const home = ref<HomeData | null>(null)
const searchQ = ref('')
const searchRows = ref<CatalogRow[]>([])
const catalogRows = ref<CatalogRow[]>([])
const catalogView = ref<'table' | 'card'>('table')
const subscriptions = ref<Subscription[]>([])
const situations = ref<Situation[]>([])
const catalogOptions = ref<CatalogRow[]>([])

const subForm = reactive({
  catalogId: undefined as number | undefined,
  applicantOrg: '数据管理局',
  resourceType: 'TABLE',
  purpose: '',
})

const reviewNote = ref('')

const statusTag = (s: string) => {
  if (s === 'APPROVED') return 'success'
  if (s === 'REJECTED') return 'danger'
  return 'warning'
}

function resolveTab() {
  const q = String(route.query.tab || 'home').toLowerCase()
  tab.value = tabMap[q] || 'm032'
}

async function loadHome() {
  const res = await api.get('/exchange/portal/home', { params: { keyword: searchQ.value || undefined } })
  home.value = res.data
  catalogOptions.value = res.data.recommendations || []
}

async function loadSearch() {
  const res = await api.get('/exchange/portal/search', { params: { q: searchQ.value || undefined } })
  searchRows.value = res.data
}

async function loadCatalog() {
  const res = await api.get('/exchange/portal/catalog', { params: { keyword: searchQ.value || undefined } })
  catalogRows.value = res.data
}

async function loadSubscriptions() {
  const res = await api.get('/exchange/portal/subscriptions')
  subscriptions.value = res.data
}

async function loadSituations() {
  const res = await api.get('/exchange/portal/situations')
  situations.value = res.data
}

async function loadTabData() {
  try {
    if (tab.value === 'm032') await loadHome()
    if (tab.value === 'm033') await loadSearch()
    if (tab.value === 'm034') await loadCatalog()
    if (tab.value === 'm035') {
      await loadSubscriptions()
      if (!catalogOptions.value.length) {
        const c = await api.get('/exchange/portal/catalog')
        catalogOptions.value = c.data
      }
    }
    if (tab.value === 'm036') await loadSituations()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载失败')
  }
}

async function doSearch() {
  if (tab.value === 'm032') await loadHome()
  else if (tab.value === 'm033') await loadSearch()
  else if (tab.value === 'm034') await loadCatalog()
  else {
    tab.value = 'm033'
    await loadSearch()
  }
}

async function syncIndex() {
  const res = await api.post('/exchange/portal/search/sync')
  ElMessage.success(`已索引 ${res.data.indexed} 条目录（ES: ${res.data.esHealthy ? '在线' : '离线'}）`)
  await loadSearch()
}

async function submitSubscription() {
  if (!subForm.catalogId) {
    ElMessage.warning('请选择目录')
    return
  }
  await api.post('/exchange/portal/subscriptions', subForm)
  ElMessage.success('订阅申请已提交')
  subForm.purpose = ''
  await loadSubscriptions()
  await loadHome()
}

async function reviewSub(id: number, action: 'APPROVE' | 'REJECT') {
  await api.post(`/exchange/portal/subscriptions/${id}/review`, {
    action,
    approverNote: reviewNote.value,
  })
  ElMessage.success(action === 'APPROVE' ? '已通过' : '已驳回')
  reviewNote.value = ''
  await loadSubscriptions()
}

function goSituation(s: Situation) {
  router.push(s.domainRoute)
}

function goTheme(routePath: string) {
  router.push(routePath)
}

const tabTitle = computed(() => {
  const titles: Record<string, string> = {
    m032: 'M032 门户首页',
    m033: 'M033 全文检索',
    m034: 'M034 资源目录检索',
    m035: 'M035 资源订阅申请',
    m036: 'M036 领导决策八态势',
  }
  return titles[tab.value] || '应用分析门户'
})

watch(tab, () => {
  router.replace({ query: { ...route.query, tab: tabToQuery[tab.value] || 'home' } })
  loadTabData()
})

watch(() => route.query.tab, resolveTab)

onMounted(() => {
  resolveTab()
  loadTabData()
})
</script>

<template>
  <div>
    <PageHeader
      :title="`应用分析门户 · ${tabTitle}`"
      description="M031～M036：部门共享门户、首页检索、目录浏览、订阅审批与领导八态势"
    />

    <HubSideLayout v-model="tab" :items="navItems">
      <template v-if="tab === 'm032'">
        <PageCard v-if="home">
          <div class="portal-search-bar">
            <el-input v-model="searchQ" placeholder="搜索目录、主题、单位…" clearable @keyup.enter="doSearch" />
            <el-button type="primary" @click="doSearch">搜索</el-button>
          </div>
          <el-row :gutter="16" class="stat-row">
            <el-col :span="6">
              <el-statistic title="已发布目录" :value="home.publishedCount" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="订阅申请" :value="home.subscriptionTotal" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="待审批" :value="home.subscriptionPending" />
            </el-col>
            <el-col :span="6">
              <el-statistic title="八态势" :value="home.situationCount" />
            </el-col>
          </el-row>
          <h4>推荐目录</h4>
          <el-table :data="home.recommendations" stripe size="small">
            <el-table-column prop="catalogCode" label="编码" width="140" />
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column prop="description" label="说明" min-width="220" />
          </el-table>
          <h4>主题导航</h4>
          <el-space wrap>
            <el-button v-for="t in home.themes" :key="t.code" @click="goTheme(t.route)">{{ t.name }}</el-button>
          </el-space>
        </PageCard>
      </template>

      <template v-if="tab === 'm034'">
        <PageCard>
          <div class="portal-toolbar">
            <el-input v-model="searchQ" placeholder="目录关键词" clearable style="max-width: 320px" @keyup.enter="loadCatalog" />
            <el-button type="primary" @click="loadCatalog">检索</el-button>
            <el-radio-group v-model="catalogView" size="small">
              <el-radio-button value="table">表格</el-radio-button>
              <el-radio-button value="card">卡片</el-radio-button>
            </el-radio-group>
          </div>
          <el-table v-if="catalogView === 'table'" :data="catalogRows" stripe>
            <el-table-column prop="catalogCode" label="编码" width="140" />
            <el-table-column prop="title" label="标题" min-width="160" />
            <el-table-column prop="description" label="说明" min-width="220" />
            <el-table-column prop="source" label="来源" width="110" />
          </el-table>
          <el-row v-else :gutter="12">
            <el-col v-for="c in catalogRows" :key="String(c.id)" :span="8">
              <el-card shadow="hover" class="catalog-card">
                <div class="card-code">{{ c.catalogCode }}</div>
                <div class="card-title">{{ c.title }}</div>
                <div class="card-desc">{{ c.description }}</div>
              </el-card>
            </el-col>
          </el-row>
        </PageCard>
      </template>

      <template v-if="tab === 'm033'">
        <PageCard>
          <div class="portal-toolbar">
            <el-input v-model="searchQ" placeholder="Elasticsearch 全文检索（离线时回退数据库）" clearable style="max-width: 420px" @keyup.enter="loadSearch" />
            <el-button type="primary" @click="loadSearch">检索</el-button>
            <el-button @click="syncIndex">同步索引</el-button>
          </div>
          <el-table :data="searchRows" stripe>
            <el-table-column prop="catalogCode" label="编码" width="140" />
            <el-table-column prop="title" label="标题" min-width="160" />
            <el-table-column prop="description" label="说明" min-width="200" />
            <el-table-column prop="source" label="引擎" width="120" />
            <el-table-column prop="score" label="相关度" width="90" />
          </el-table>
        </PageCard>
      </template>

      <template v-if="tab === 'm035'">
        <PageCard>
          <el-form :inline="true" class="sub-form">
            <el-form-item label="目录">
              <el-select v-model="subForm.catalogId" placeholder="选择目录" style="width: 240px">
                <el-option
                  v-for="c in catalogOptions"
                  :key="String(c.id)"
                  :label="`${c.catalogCode} ${c.title}`"
                  :value="Number(c.id)"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="申请单位">
              <el-input v-model="subForm.applicantOrg" style="width: 160px" />
            </el-form-item>
            <el-form-item label="资源类型">
              <el-select v-model="subForm.resourceType" style="width: 120px">
                <el-option label="表" value="TABLE" />
                <el-option label="文件" value="FILE" />
                <el-option label="接口" value="API" />
              </el-select>
            </el-form-item>
            <el-form-item label="用途">
              <el-input v-model="subForm.purpose" style="width: 200px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitSubscription">提交申请</el-button>
            </el-form-item>
          </el-form>
          <el-input v-model="reviewNote" placeholder="审批意见（可选）" style="max-width: 360px; margin-bottom: 12px" />
          <el-table :data="subscriptions" stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="catalogId" label="目录ID" width="90" />
            <el-table-column prop="applicantOrg" label="申请单位" min-width="120" />
            <el-table-column prop="resourceType" label="类型" width="80" />
            <el-table-column prop="purpose" label="用途" min-width="140" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <el-button link type="success" @click="reviewSub(row.id, 'APPROVE')">通过</el-button>
                  <el-button link type="danger" @click="reviewSub(row.id, 'REJECT')">驳回</el-button>
                </template>
                <span v-else class="muted">{{ row.approverNote || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </template>

      <template v-if="tab === 'm036'">
        <PageCard>
          <el-row :gutter="16">
            <el-col v-for="s in situations" :key="s.situationCode" :span="6">
              <el-card shadow="hover" class="situation-card" @click="goSituation(s)">
                <div class="situation-name">{{ s.situationName }}</div>
                <div class="situation-metric">{{ s.summaryMetric }}</div>
                <div class="situation-model">{{ s.modelMCode }} · 点击进入分析</div>
              </el-card>
            </el-col>
          </el-row>
        </PageCard>
      </template>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.portal-search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  max-width: 520px;
}
.stat-row {
  margin: 16px 0;
}
.portal-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.catalog-card {
  margin-bottom: 12px;
  min-height: 120px;
}
.card-code {
  font-size: 12px;
  color: #909399;
}
.card-title {
  font-weight: 600;
  margin: 6px 0;
}
.card-desc {
  font-size: 13px;
  color: #606266;
}
.situation-card {
  margin-bottom: 16px;
  cursor: pointer;
}
.situation-name {
  font-size: 16px;
  font-weight: 600;
}
.situation-metric {
  margin: 8px 0;
  color: #409eff;
}
.situation-model {
  font-size: 12px;
  color: #909399;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.portal-tabs {
  margin-top: 8px;
}
</style>
