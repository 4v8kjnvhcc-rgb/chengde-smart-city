<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { useAuthStore } from '@/stores/auth'
import AssessmentView from './AssessmentView.vue'
import SupplyDemandView from './SupplyDemandView.vue'

interface CatalogRow {
  id: number | string
  catalogCode: string
  title: string
  description: string
  catalogKind?: string
  themeCode?: string
  themeName?: string
  providerOrg?: string
  shareModes?: string
  resourceCount?: number
  hotScore?: number
  publishedAt?: string
  previewItems?: { label: string; value: string }[]
}

interface HomeData {
  openResourceTotal: number
  apiServiceTotal: number
  shareOrgTotal: number
  exchangeVolumeTotal: number
  publishedCount?: number
  hotKeywords: string[]
  themes: { code: string; name: string; count?: number; apiCount?: number; dataCount?: number }[]
  providers: { name: string; count: number; apiCount?: number; dataCount?: number }[]
  latestResources: CatalogRow[]
  latestApplications?: { id: number; catalogTitle?: string; applicantOrg?: string; status?: string; createdAt?: string }[]
  hotResources: CatalogRow[]
}

interface Subscription {
  id: number
  catalogId: number
  catalogTitle?: string
  catalogCode?: string
  applicantOrg: string
  resourceType: string
  purpose: string
  status: string
  approverNote?: string
  taskId?: number
  taskType?: string
  taskStatus?: string
}

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const portalTab = ref('home')
const home = ref<HomeData | null>(null)
const searchQ = ref('')
const filterTheme = ref('')
const filterProvider = ref('')
const filterShareMode = ref('')
const catalogKind = ref<'DATA' | 'SERVICE' | ''>('')
const catalogView = ref<'table' | 'card'>('table')
const catalogRows = ref<CatalogRow[]>([])
const subscriptions = ref<Subscription[]>([])
const objections = ref<Record<string, unknown>[]>([])
const catalogOptions = ref<CatalogRow[]>([])
const highlightSupply = ref(false)
const supplyDialogVisible = ref(false)
const preview = reactive<{ visible: boolean; row: CatalogRow | null }>({ visible: false, row: null })

const subForm = reactive({
  catalogId: undefined as number | undefined,
  applicantOrg: '数据管理局',
  resourceType: 'TABLE',
  purpose: '',
})
const reviewNote = ref('')

const shareLabel = (m?: string) => {
  if (!m) return '-'
  return m
    .split(',')
    .map((x) => ({ TABLE: '库表同步', FILE: '文件同步', API: 'API服务' }[x.trim()] || x.trim()))
    .join(' / ')
}
const kindLabel = (k?: string) => (k === 'SERVICE' ? '服务目录' : '数据目录')
const statusTag = (s: string) => {
  if (s === 'APPROVED' || s === 'READY') return 'success'
  if (s === 'REJECTED') return 'danger'
  return 'warning'
}
void statusTag

function syncTab() {
  const s = String(route.query.section || 'home')
  portalTab.value = ['home', 'catalog', 'subscribe', 'assessment', 'myspace'].includes(s) ? s : 'home'
  highlightSupply.value = String(route.query.supplyHint || '') === '1'
  if (portalTab.value === 'subscribe' && highlightSupply.value) {
    supplyDialogVisible.value = true
  }
}

function setTab(key: string, opts?: { openSupply?: boolean }) {
  portalTab.value = key
  const q: Record<string, string> = { system: 'portal', module: 'portal-home', section: key }
  if (searchQ.value) q.q = searchQ.value
  if (filterTheme.value) q.themeCode = filterTheme.value
  if (opts?.openSupply) q.supplyHint = '1'
  router.replace({ query: q })
  loadTab()
}

async function loadHome() {
  const res = await api.get('/exchange/portal/home', { params: { keyword: searchQ.value || undefined } })
  home.value = res.data
  catalogOptions.value = [...(res.data.hotResources || []), ...(res.data.latestResources || [])]
}

async function loadCatalog() {
  const res = await api.get('/exchange/portal/catalog', {
    params: {
      keyword: searchQ.value || undefined,
      themeCode: filterTheme.value || undefined,
      providerOrg: filterProvider.value || undefined,
      catalogKind: catalogKind.value || undefined,
      shareMode: filterShareMode.value || undefined,
    },
  })
  catalogRows.value = res.data
  if (!res.data?.length && searchQ.value) {
    highlightSupply.value = true
  }
}

async function loadSubscriptions() {
  subscriptions.value = (await api.get('/exchange/portal/subscriptions')).data
}

async function loadObjections() {
  try {
    objections.value = (await api.get('/exchange/supply/objections')).data
  } catch {
    objections.value = []
  }
}

const shareBrowseMode = ref<'theme' | 'dept'>('theme')

const hotApis = computed(() =>
  (home.value?.hotResources || []).filter((r) => r.catalogKind === 'SERVICE' || String(r.shareModes || '').includes('API')).slice(0, 5),
)
const hotTables = computed(() =>
  (home.value?.hotResources || []).filter((r) => r.catalogKind !== 'SERVICE' && !String(r.shareModes || '').includes('API')).slice(0, 5),
)

function formatDate(v?: string) {
  if (!v) return ''
  return String(v).slice(0, 10)
}

async function loadTab() {
  loading.value = true
  try {
    if (portalTab.value === 'home') {
      await loadHome()
    } else if (portalTab.value === 'catalog') {
      await Promise.all([ensureHomeFacets(), loadCatalog()])
    } else if (portalTab.value === 'subscribe') {
      await Promise.all([
        loadSubscriptions(),
        catalogOptions.value.length ? Promise.resolve() : loadHome().then(() => loadCatalog().then(() => {
          catalogOptions.value = catalogRows.value.length ? catalogRows.value : catalogOptions.value
        })),
      ])
    } else if (portalTab.value === 'myspace') {
      await Promise.all([loadSubscriptions(), loadObjections()])
    } else if (portalTab.value === 'assessment') {
      // AssessmentView self-loads
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function ensureHomeFacets() {
  if (!home.value) await loadHome()
}

function openTheme(code: string) {
  filterTheme.value = code
  filterProvider.value = ''
  setTab('catalog')
}

function openProvider(name: string) {
  filterProvider.value = name
  filterTheme.value = ''
  setTab('catalog')
}

async function doBannerSearch() {
  setTab('catalog')
  await loadCatalog()
}

function clickHotKeyword(kw: string) {
  searchQ.value = kw
  doBannerSearch()
}

function openPreview(row: CatalogRow) {
  preview.row = row
  preview.visible = true
}

function applyFromCatalog(row: CatalogRow) {
  subForm.catalogId = Number(row.id)
  if (row.catalogKind === 'SERVICE') subForm.resourceType = 'API'
  else if (row.shareModes?.includes('TABLE')) subForm.resourceType = 'TABLE'
  else if (row.shareModes?.includes('FILE')) subForm.resourceType = 'FILE'
  else subForm.resourceType = 'API'
  preview.visible = false
  setTab('subscribe')
}

function goSubmitDemand() {
  highlightSupply.value = true
  setTab('subscribe', { openSupply: true })
  supplyDialogVisible.value = true
}

function openSupplyDialog() {
  supplyDialogVisible.value = true
}

async function submitSubscription() {
  if (!auth.hasPermission('portal:subscription:create') && auth.permissions.length) {
    // 无细权限时：已登录仍允许（兼容未跑 V28 的环境）
  }
  if (!subForm.catalogId) return ElMessage.warning('请选择目录')
  await api.post('/exchange/portal/subscriptions', subForm)
  ElMessage.success('订阅申请已提交')
  subForm.purpose = ''
  await loadSubscriptions()
}

async function reviewSub(id: number, action: 'APPROVE' | 'REJECT') {
  const res = await api.post(`/exchange/portal/subscriptions/${id}/review`, {
    action,
    approverNote: reviewNote.value,
  })
  ElMessage.success(action === 'APPROVE' ? `已通过${res.data?.taskId ? `，任务 #${res.data.taskId}` : ''}` : '已驳回')
  reviewNote.value = ''
  await loadSubscriptions()
}

const pendingSubs = computed(() => subscriptions.value.filter((s) => s.status === 'PENDING'))
const mySubs = computed(() => subscriptions.value)

// themeTree reserved for future grouping

watch(() => route.query.section, () => {
  syncTab()
  loadTab()
})

onMounted(() => {
  syncTab()
  loadTab()
})
</script>

<template>
  <div v-loading="loading" class="portal-home">
    <!-- 首页落地（对齐参考图） -->
    <template v-if="portalTab === 'home'">
      <section class="hero">
        <div class="hero__inner">
          <div class="hero__copy">
            <div class="hero__brand">承德市数据共享门户</div>
            <h1 class="hero__title">构建完善的数据资产管理体系，数据汇聚全覆盖</h1>
            <div class="hero__search">
              <input
                v-model="searchQ"
                class="hero__input"
                placeholder="请输入关键词..."
                @keyup.enter="doBannerSearch"
              >
              <button type="button" class="hero__btn" @click="doBannerSearch">搜索</button>
            </div>
            <div v-if="home?.hotKeywords?.length" class="hero__keywords">
              <button
                v-for="kw in home.hotKeywords.slice(0, 8)"
                :key="kw"
                type="button"
                class="hero__kw"
                @click="clickHotKeyword(kw)"
              >{{ kw }}</button>
            </div>
          </div>
          <div class="hero__art" aria-hidden="true">
            <div class="hero__cube" />
            <div class="hero__ring" />
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel__head">
          <h2>共享资源 <span class="panel__en">RESOURCES</span></h2>
          <div class="seg">
            <button type="button" :class="{ 'is-on': shareBrowseMode === 'theme' }" @click="shareBrowseMode = 'theme'">主题</button>
            <button type="button" :class="{ 'is-on': shareBrowseMode === 'dept' }" @click="shareBrowseMode = 'dept'">部门</button>
          </div>
        </div>

        <div v-if="shareBrowseMode === 'theme'" class="theme-grid">
          <button type="button" class="theme-hero" @click="setTab('catalog')">
            <div class="theme-hero__title">主题信息资源</div>
            <div class="theme-hero__metrics">
              <div><span>接口总量</span><b class="c-cyan">{{ home?.apiServiceTotal || 0 }}</b></div>
              <div><span>数据资源</span><b class="c-amber">{{ home?.openResourceTotal || 0 }}</b></div>
            </div>
          </button>
          <button
            v-for="t in home?.themes || []"
            :key="t.code"
            type="button"
            class="theme-card"
            @click="openTheme(t.code)"
          >
            <div class="theme-card__top">
              <span class="theme-card__icon" aria-hidden="true" />
              <span class="theme-card__name">{{ t.name }}</span>
              <span class="theme-card__chev">›</span>
            </div>
            <div class="theme-card__nums">
              <span>接口数 <b class="c-green">{{ t.apiCount || 0 }}</b></span>
              <span>数据资源数 <b class="c-amber">{{ t.dataCount ?? t.count ?? 0 }}</b></span>
            </div>
          </button>
        </div>
        <div v-else class="theme-grid theme-grid--dept">
          <button
            v-for="p in home?.providers || []"
            :key="p.name"
            type="button"
            class="theme-card"
            @click="openProvider(p.name)"
          >
            <div class="theme-card__top">
              <span class="theme-card__icon" aria-hidden="true" />
              <span class="theme-card__name">{{ p.name }}</span>
              <span class="theme-card__chev">›</span>
            </div>
            <div class="theme-card__nums">
              <span>接口数 <b class="c-green">{{ p.apiCount || 0 }}</b></span>
              <span>数据资源数 <b class="c-amber">{{ p.dataCount ?? p.count }}</b></span>
            </div>
          </button>
        </div>
      </section>

      <section class="panel">
        <div class="panel__head">
          <h2>共享动态 <span class="panel__en">DYNAMIC</span></h2>
        </div>
        <div class="dual">
          <div class="dual__col">
            <div class="dual__caption">
              <h3>资源发布动态</h3>
              <button type="button" class="more" @click="setTab('catalog')">更多</button>
            </div>
            <ul class="feed-list">
              <li v-for="r in (home?.latestResources || []).slice(0, 6)" :key="String(r.id)">
                <button type="button" @click="openPreview(r)">
                  <span class="feed-list__dot" />
                  <span class="feed-list__title">{{ r.title }}</span>
                  <span class="feed-list__date">{{ formatDate(r.publishedAt) }}</span>
                </button>
              </li>
              <li v-if="!(home?.latestResources?.length)" class="empty">暂无发布动态</li>
            </ul>
          </div>
          <div class="dual__col">
            <div class="dual__caption">
              <h3>资源申请动态</h3>
              <button type="button" class="more" @click="setTab('subscribe')">更多</button>
            </div>
            <ul class="feed-list">
              <li v-for="a in (home?.latestApplications || []).slice(0, 6)" :key="a.id">
                <button type="button" @click="setTab('subscribe')">
                  <span class="feed-list__dot feed-list__dot--amber" />
                  <span class="feed-list__title">{{ a.applicantOrg || '申请单位' }} · {{ a.catalogTitle }}</span>
                  <span class="feed-list__date">{{ formatDate(a.createdAt) }}</span>
                </button>
              </li>
              <li v-if="!(home?.latestApplications?.length)" class="empty">暂无申请动态</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="panel">
        <div class="panel__head">
          <h2>热门资源 <span class="panel__en">RESOURCES</span></h2>
        </div>
        <div class="dual">
          <div class="dual__col">
            <div class="dual__caption">
              <h3>热门接口 TOP 5</h3>
              <button type="button" class="more" @click="catalogKind='SERVICE'; setTab('catalog')">更多</button>
            </div>
            <ul class="res-list">
              <li v-for="r in (hotApis.length ? hotApis : (home?.hotResources || []).slice(0, 5))" :key="String(r.id)">
                <button type="button" @click="openPreview(r)">
                  <span class="res-list__main">
                    <span class="res-list__title">{{ r.title }}</span>
                    <span class="res-list__meta">{{ r.providerOrg || '—' }}</span>
                  </span>
                  <span class="res-list__views">访问量 {{ r.hotScore || 0 }}</span>
                </button>
              </li>
              <li v-if="!(home?.hotResources?.length)" class="empty">暂无热门接口</li>
            </ul>
          </div>
          <div class="dual__col">
            <div class="dual__caption">
              <h3>热门库表 TOP 5</h3>
              <button type="button" class="more" @click="catalogKind='DATA'; setTab('catalog')">更多</button>
            </div>
            <ul class="res-list">
              <li v-for="r in (hotTables.length ? hotTables : (home?.latestResources || []).slice(0, 5))" :key="String(r.id)">
                <button type="button" @click="openPreview(r)">
                  <span class="res-list__main">
                    <span class="res-list__title">{{ r.title }}</span>
                    <span class="res-list__meta">{{ r.providerOrg || '—' }}</span>
                  </span>
                  <span class="res-list__views">访问量 {{ r.hotScore || 0 }}</span>
                </button>
              </li>
              <li v-if="!(home?.latestResources?.length || hotTables.length)" class="empty">暂无热门库表</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="panel panel--info">
        <div class="panel__head">
          <h2>信息专区 <span class="panel__en">INFORMATION</span></h2>
        </div>
        <div class="info-grid">
          <div class="info-banner" @click="setTab('myspace')">
            <div class="info-banner__title">信息专区</div>
            <div class="info-banner__desc">及时获取最新工作动态、政策动态</div>
          </div>
          <div class="info-side">
            <div class="dual__col info-block">
              <div class="dual__caption"><h3>工作动态</h3></div>
              <ul class="feed-list">
                <li><span class="feed-list__static"><span>数据共享门户上线试运行</span><em>2026-07-01</em></span></li>
                <li><span class="feed-list__static"><span>全市政务数据归集进度通报</span><em>2026-06-18</em></span></li>
              </ul>
            </div>
            <div class="dual__col info-block">
              <div class="dual__caption"><h3>政策法规</h3></div>
              <ul class="feed-list">
                <li><span class="feed-list__static"><span>政务数据共享条例</span><em>2026-03-12</em></span></li>
                <li><span class="feed-list__static"><span>公共数据资源管理办法</span><em>2026-03-12</em></span></li>
                <li><span class="feed-list__static"><span>承德市数据共享交换实施细则</span><em>2026-03-12</em></span></li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      <footer class="portal-foot">
        <div>承德市大数据归集平台 · 数据共享门户</div>
        <div>建议使用 Chrome / Edge 浏览器访问 · V1.0</div>
      </footer>
    </template>

    <!-- 共享资源 / 其他业务区 -->
    <div v-else class="portal-body">
      <PageCard v-if="portalTab === 'catalog'" title="共享资源 · 资源目录">
        <el-row :gutter="16">
          <el-col :span="6">
            <div class="filter-panel">
              <h4>主题领域</h4>
              <el-check-tag
                v-for="t in home?.themes || []"
                :key="t.code"
                :checked="filterTheme === t.code"
                style="margin:0 6px 6px 0"
                @change="filterTheme = filterTheme === t.code ? '' : t.code; loadCatalog()"
              >{{ t.name }}</el-check-tag>
              <h4>数据提供方</h4>
              <el-check-tag
                v-for="p in home?.providers || []"
                :key="p.name"
                :checked="filterProvider === p.name"
                style="margin:0 6px 6px 0"
                @change="filterProvider = filterProvider === p.name ? '' : p.name; loadCatalog()"
              >{{ p.name }}</el-check-tag>
              <h4>共享方式</h4>
              <el-radio-group v-model="filterShareMode" size="small" @change="loadCatalog">
                <el-radio-button value="">全部</el-radio-button>
                <el-radio-button value="TABLE">库表</el-radio-button>
                <el-radio-button value="FILE">文件</el-radio-button>
                <el-radio-button value="API">API</el-radio-button>
              </el-radio-group>
              <h4>目录类型</h4>
              <el-radio-group v-model="catalogKind" size="small" @change="loadCatalog">
                <el-radio-button value="">全部</el-radio-button>
                <el-radio-button value="DATA">数据目录</el-radio-button>
                <el-radio-button value="SERVICE">服务目录</el-radio-button>
              </el-radio-group>
            </div>
          </el-col>
          <el-col :span="18">
            <div class="toolbar">
              <el-input v-model="searchQ" clearable placeholder="关键词" style="max-width:240px" @keyup.enter="loadCatalog" />
              <el-button type="primary" @click="loadCatalog">检索</el-button>
              <el-radio-group v-model="catalogView" size="small">
                <el-radio-button value="table">表格</el-radio-button>
                <el-radio-button value="card">卡片</el-radio-button>
              </el-radio-group>
            </div>
            <el-empty v-if="!catalogRows.length" description="没有找到您需要的资源">
              <el-button type="primary" @click="goSubmitDemand">去数据供需对接提需求</el-button>
            </el-empty>
            <el-table v-else-if="catalogView === 'table'" :data="catalogRows" stripe size="small">
              <el-table-column prop="title" label="资源名称" min-width="140" />
              <el-table-column prop="providerOrg" label="提供方" width="110" />
              <el-table-column label="共享方式" min-width="120">
                <template #default="{ row }">{{ shareLabel(row.shareModes) }}</template>
              </el-table-column>
              <el-table-column prop="resourceCount" label="挂接" width="70" />
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openPreview(row)">预览</el-button>
                  <el-button link type="success" @click="applyFromCatalog(row)">申请</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-row v-else :gutter="12">
              <el-col v-for="c in catalogRows" :key="String(c.id)" :span="8">
                <el-card shadow="hover" class="res-card">
                  <div class="card-code">{{ c.catalogCode }} · {{ kindLabel(c.catalogKind) }}</div>
                  <div class="card-title">{{ c.title }}</div>
                  <div class="card-desc">{{ c.description }}</div>
                  <el-button size="small" type="primary" @click="applyFromCatalog(c)">申请</el-button>
                </el-card>
              </el-col>
            </el-row>
          </el-col>
        </el-row>
      </PageCard>

      <template v-else-if="portalTab === 'subscribe'">
        <PageCard :class="{ 'supply-hint': highlightSupply }">
          <template #header>
            <div class="subscribe-head">
              <span class="subscribe-head__title">资源订阅申请</span>
              <el-button type="primary" @click="openSupplyDialog">数据供需对接</el-button>
            </div>
          </template>
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="资源" class="portal-field-xl">
              <el-select v-model="subForm.catalogId" filterable placeholder="选择资源目录">
                <el-option
                  v-for="c in (catalogOptions.length ? catalogOptions : catalogRows)"
                  :key="String(c.id)"
                  :label="`${c.catalogCode} ${c.title}`"
                  :value="Number(c.id)"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="共享方式" class="portal-field-md">
              <el-select v-model="subForm.resourceType">
                <el-option label="库表同步" value="TABLE" />
                <el-option label="文件同步" value="FILE" />
                <el-option label="API 服务" value="API" />
              </el-select>
            </el-form-item>
            <el-form-item label="申请单位" class="portal-field-md"><el-input v-model="subForm.applicantOrg" /></el-form-item>
            <el-form-item label="用途" class="portal-field-lg"><el-input v-model="subForm.purpose" /></el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="submitSubscription">提交申请</el-button>
            </el-form-item>
          </el-form>
          <el-input v-model="reviewNote" placeholder="审批意见" style="max-width:360px;margin-bottom:12px" />
          <el-table :data="subscriptions" stripe size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="资源" min-width="140">
              <template #default="{ row }">{{ row.catalogTitle || row.catalogId }}</template>
            </el-table-column>
            <el-table-column label="方式" width="100">
              <template #default="{ row }">{{ shareLabel(row.resourceType) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }"><el-tag :type="$statusTagType(row.status)">{{ $statusLabel(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="任务" width="120">
              <template #default="{ row }">
                <span v-if="row.taskId">#{{ row.taskId }} {{ $statusLabel(row.taskStatus) }}</span>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING'">
                  <el-button link type="success" @click="reviewSub(row.id, 'APPROVE')">通过</el-button>
                  <el-button link type="danger" @click="reviewSub(row.id, 'REJECT')">驳回</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </template>

      <PageCard v-else-if="portalTab === 'assessment'" title="考核评估">
        <AssessmentView mode="front" />
      </PageCard>

      <PageCard v-else-if="portalTab === 'myspace'" title="我的空间">
        <el-tabs>
          <el-tab-pane label="我的申请/订阅" name="subs">
            <el-table :data="mySubs" stripe size="small">
              <el-table-column prop="id" label="ID" width="70" />
              <el-table-column label="资源" min-width="140">
                <template #default="{ row }">{{ row.catalogTitle || row.catalogId }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
              <el-table-column label="交换任务" width="140">
                <template #default="{ row }">
                  <span v-if="row.taskId">#{{ row.taskId }} / {{ $statusLabel(row.taskStatus) }}</span>
                  <span v-else>—</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="待我审批" name="pending">
            <el-table :data="pendingSubs" stripe size="small">
              <el-table-column prop="applicantOrg" label="申请单位" width="120" />
              <el-table-column label="资源" min-width="140">
                <template #default="{ row }">{{ row.catalogTitle || row.catalogId }}</template>
              </el-table-column>
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button link type="success" @click="reviewSub(row.id, 'APPROVE')">通过</el-button>
                  <el-button link type="danger" @click="reviewSub(row.id, 'REJECT')">驳回</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="异议" name="obj">
            <el-table :data="objections" stripe size="small">
              <el-table-column prop="catalogId" label="目录ID" width="90" />
              <el-table-column prop="objectionType" label="类型" width="100" />
              <el-table-column prop="content" label="内容" min-width="200" />
              <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </PageCard>
    </div>

    <el-drawer v-model="preview.visible" title="资源预览" size="420px">
      <template v-if="preview.row">
        <h3>{{ preview.row.title }}</h3>
        <p class="muted">{{ preview.row.description }}</p>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item v-for="(it, i) in preview.row.previewItems || []" :key="i" :label="it.label">{{ it.value }}</el-descriptions-item>
        </el-descriptions>
        <el-button type="primary" style="margin-top:12px" @click="applyFromCatalog(preview.row!)">申请该资源</el-button>
      </template>
    </el-drawer>

    <el-dialog
      v-model="supplyDialogVisible"
      title="数据供需对接"
      width="96%"
      top="3vh"
      destroy-on-close
      append-to-body
      class="supply-flow-dialog"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="需求管理 → 分析 → 确认 → 供给 → 清单中心"
      />
      <div class="supply-dialog-body">
        <SupplyDemandView v-if="supplyDialogVisible" mode="front" embedded />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.portal-home { background: #f3f6fb; min-height: 100%; }
.portal-body { max-width: 1280px; margin: 0 auto; padding: 16px 20px 28px; }

.hero {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 78% 45%, rgba(120, 200, 255, 0.28), transparent 42%),
    linear-gradient(115deg, #0a4ea8 0%, #1677ff 42%, #0d47a1 100%);
  color: #fff;
  padding: 56px 20px 48px;
}
.hero__inner {
  max-width: 1280px;
  margin: 0 auto;
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(220px, 0.85fr);
  gap: 24px;
  align-items: center;
}
.hero__brand {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.08em;
  opacity: 0.92;
  margin-bottom: 10px;
}
.hero__title {
  margin: 0 0 22px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0.02em;
  max-width: 640px;
  line-height: 1.4;
}
.hero__search {
  display: flex;
  max-width: 560px;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 40, 100, 0.25);
}
.hero__input {
  flex: 1;
  border: 0;
  outline: none;
  height: 46px;
  padding: 0 16px;
  font-size: 15px;
  color: #1f2937;
}
.hero__btn {
  border: 0;
  background: #0b3d91;
  color: #fff;
  font-weight: 600;
  padding: 0 26px;
  cursor: pointer;
  font-size: 15px;
}
.hero__btn:hover { background: #083070; }
.hero__keywords { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.hero__kw {
  border: 1px solid rgba(255,255,255,0.35);
  background: rgba(255,255,255,0.12);
  color: #fff;
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
}
.hero__art {
  position: relative;
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero__cube {
  width: 120px;
  height: 120px;
  border-radius: 18px;
  background:
    linear-gradient(145deg, rgba(255,255,255,0.55), rgba(255,255,255,0.08)),
    linear-gradient(135deg, #4fc3f7, #1565c0);
  box-shadow: 0 18px 40px rgba(0, 30, 80, 0.35);
  transform: rotate(18deg) skewY(-6deg);
}
.hero__ring {
  position: absolute;
  width: 210px;
  height: 210px;
  border-radius: 50%;
  border: 2px solid rgba(255,255,255,0.28);
  box-shadow: inset 0 0 40px rgba(127, 210, 255, 0.25);
}

.panel {
  max-width: 1280px;
  margin: 0 auto;
  padding: 24px 20px 8px;
}
.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.panel__head h2 {
  margin: 0;
  font-size: 18px;
  color: #1f2937;
  position: relative;
  padding-left: 12px;
}
.panel__head h2::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 4px;
  border-radius: 2px;
  background: #1677ff;
}
.panel__en {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.08em;
  color: #9ca3af;
  vertical-align: middle;
}
.seg {
  display: inline-flex;
  background: #e8eef8;
  border-radius: 6px;
  padding: 3px;
  gap: 2px;
}
.seg button {
  border: 0;
  background: transparent;
  padding: 6px 14px;
  border-radius: 4px;
  cursor: pointer;
  color: #4b5563;
  font-size: 13px;
}
.seg button.is-on {
  background: #fff;
  color: #1677ff;
  font-weight: 600;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}
.more {
  border: 0;
  background: transparent;
  color: #1677ff;
  cursor: pointer;
  font-size: 13px;
}

.theme-grid {
  display: grid;
  grid-template-columns: 1.15fr repeat(3, 1fr);
  gap: 12px;
}
.theme-grid--dept {
  grid-template-columns: repeat(4, 1fr);
}
.theme-hero {
  grid-row: span 2;
  min-height: 220px;
  border: 0;
  border-radius: 10px;
  padding: 24px;
  text-align: left;
  cursor: pointer;
  color: #fff;
  background:
    linear-gradient(160deg, rgba(8, 46, 110, 0.55), rgba(8, 46, 110, 0.9)),
    linear-gradient(135deg, #0d47a1, #42a5f5);
}
.theme-hero__title { font-size: 22px; font-weight: 700; margin-bottom: 28px; }
.theme-hero__metrics {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.theme-hero__metrics span { display: block; font-size: 13px; opacity: 0.9; margin-bottom: 4px; }
.theme-hero__metrics b { font-size: 32px; line-height: 1; font-weight: 700; }
.c-cyan { color: #26e0ff; }
.c-amber { color: #ffb74d; }
.c-green { color: #26a69a; }
.theme-card {
  border: 1px solid #e5eaf2;
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  min-height: 104px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: box-shadow 150ms ease, transform 150ms ease;
}
.theme-card:hover {
  box-shadow: 0 6px 18px rgba(22, 119, 255, 0.12);
  transform: translateY(-1px);
}
.theme-card__top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.theme-card__icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  flex-shrink: 0;
  background:
    radial-gradient(circle at 50% 50%, #fff 0 2px, transparent 3px),
    linear-gradient(135deg, #42a5f5, #1565c0);
  box-shadow: 0 0 0 1px rgba(21, 101, 192, 0.15);
}
.theme-card__name {
  flex: 1;
  font-weight: 600;
  color: #1f2937;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.theme-card__chev { color: #9ca3af; font-size: 18px; line-height: 1; }
.theme-card__nums {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  font-size: 12px;
  color: #6b7280;
  padding-left: 38px;
}
.theme-card__nums .c-amber { color: #fb8c00; }
.theme-card__nums .c-green { color: #26a69a; }

.dual {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  padding-bottom: 12px;
}
.dual__col {
  background: #fff;
  border: 1px solid #e5eaf2;
  border-radius: 10px;
  overflow: hidden;
}
.dual__caption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #eef5ff;
  border-bottom: 1px solid #dde7f5;
}
.dual__caption h3 {
  margin: 0;
  font-size: 15px;
  color: #1f2937;
}
.feed-list { list-style: none; margin: 0; padding: 6px 12px 10px; }
.feed-list li + li { border-top: 1px solid #f0f3f8; }
.feed-list button,
.feed-list__static {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 11px 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
}
.feed-list__static { cursor: default; }
.feed-list__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #1677ff;
  flex-shrink: 0;
}
.feed-list__dot--amber { background: #fb8c00; }
.feed-list__title {
  flex: 1;
  min-width: 0;
  color: #1f2937;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.feed-list__date,
.feed-list__static em {
  flex-shrink: 0;
  color: #9ca3af;
  font-size: 12px;
  font-style: normal;
}
.feed-list__static span {
  flex: 1;
  color: #1f2937;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.res-list { list-style: none; margin: 0; padding: 4px 12px 8px; }
.res-list li + li { border-top: 1px solid #f0f3f8; }
.res-list button {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 12px 4px;
  cursor: pointer;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.res-list__main { min-width: 0; }
.res-list__title { display: block; color: #1f2937; font-size: 14px; font-weight: 500; }
.res-list__meta { display: block; margin-top: 4px; color: #9ca3af; font-size: 12px; }
.res-list__views { flex-shrink: 0; color: #9ca3af; font-size: 12px; padding-top: 2px; }
.empty { padding: 20px; color: #9ca3af; font-size: 13px; }

.info-grid {
  display: grid;
  grid-template-columns: 1.1fr 1.4fr;
  gap: 16px;
  padding-bottom: 8px;
}
.info-banner {
  min-height: 220px;
  border-radius: 10px;
  padding: 28px 24px;
  color: #fff;
  cursor: pointer;
  background:
    linear-gradient(135deg, rgba(8, 46, 110, 0.72), rgba(13, 71, 161, 0.88)),
    linear-gradient(120deg, #0d47a1, #42a5f5);
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}
.info-banner__title { font-size: 26px; font-weight: 700; margin-bottom: 8px; }
.info-banner__desc { font-size: 13px; opacity: 0.9; }
.info-side { display: grid; gap: 12px; }
.info-block { min-height: 0; }

.portal-foot {
  margin: 24px 0 0;
  padding: 22px 20px 28px;
  text-align: center;
  background: #0b3a7a;
  color: rgba(255,255,255,0.82);
  font-size: 12px;
  line-height: 1.8;
}

.filter-panel h4 { margin: 12px 0 8px; font-size: 13px; }
.toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.res-card { margin-bottom: 12px; min-height: 140px; }
.card-code { font-size: 12px; color: #909399; }
.card-title { font-weight: 600; margin: 6px 0; }
.card-desc { font-size: 13px; color: #606266; min-height: 36px; }
.supply-hint { border: 1px solid var(--el-color-warning); }
.supply-embed { margin-top: 8px; }
.muted { color: #909399; font-size: 13px; }
.subscribe-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.subscribe-head__title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.supply-dialog-body {
  max-height: calc(92vh - 140px);
  overflow: auto;
  padding-right: 4px;
}

@media (max-width: 960px) {
  .hero__inner { grid-template-columns: 1fr; }
  .hero__art { display: none; }
  .theme-grid,
  .theme-grid--dept { grid-template-columns: 1fr 1fr; }
  .theme-hero { grid-row: auto; }
  .dual,
  .info-grid { grid-template-columns: 1fr; }
  .hero__title { font-size: 22px; }
}
</style>
