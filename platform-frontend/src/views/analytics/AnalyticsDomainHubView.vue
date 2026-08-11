<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavGroup } from '@/components/common/HubSideLayout.vue'
import DomainIndicatorSqlLibrary from '@/views/analytics/DomainIndicatorSqlLibrary.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface ZoneDef {
  key: string
  label: string
  zoneCode: string
  mCodes: string[]
  deepLink: string
  deepLabel: string
}

interface Binding {
  id: number
  assetType: string
  assetRef: string
  assetName: string
  physicalTable?: string
  metaEntryCode?: string
  dataLayer?: string
  status: string
  createdBy?: string
  createdAt?: string
}

interface Candidate {
  assetType: string
  assetRef: string
  assetName: string
  physicalTable?: string
  metaEntryCode?: string
  dataLayer?: string
}

interface Indicator {
  id: number
  indicatorCode: string
  indicatorName: string
  queryNo?: string
  resultField?: string
  fieldType?: string
  fieldName?: string
  sourceTable?: string
  sourceColumn?: string
  aggFunc?: string
  exprText?: string
  unitLabel?: string
  description?: string
  status: string
}

interface AnalysisModel {
  id: number
  modelCode: string
  modelName: string
  mCode?: string
  deDashboardId?: string
  dimensionJson?: string
  description?: string
  status: string
  indicators?: Indicator[]
}

interface ModelSample {
  id?: number
  modelId?: number
  rowNo: number
  dim1?: string
  dim2?: string
  metric1?: number | string
  metric2?: number | string
}

const ASSET_TYPE_ZH: Record<string, string> = {
  METADATA: '元数据表', MANAGED: '纳管表', CATALOG: '目录资源', OTHER: '其他',
}

function assetTypeLabel(v?: string) {
  if (!v) return '—'
  return ASSET_TYPE_ZH[v] || statusLabel(v)
}

const SEVEN_DIMS = [
  { key: '定位', tip: '本区在人口/法人域架构中的位置与职责边界' },
  { key: '数据模型', tip: '通过下方「资产挂载」选定实体与关系，不平行建库' },
  { key: '加工处理', tip: '数据进入本区前的治理/融合流程在主数据侧完成' },
  { key: '存储周期', tip: '保留策略沿用源系统/纳管表策略，此处只做区映射' },
  { key: '数据来源', tip: '从候选资产选型挂载（元数据/纳管表/目录资源）' },
  { key: '使用者', tip: '内部服务区强调授权边界；共享区面向目录与分析消费' },
  { key: '更新频度', tip: '跟随源任务调度；本页不伪造外部调度成功' },
]

function modelRowClassName({ row }: { row: AnalysisModel }) {
  return highlightModelCode.value && row.mCode === highlightModelCode.value ? 'row-hl' : ''
}

const ZONE_DEFS: Record<string, ZoneDef[]> = {
  population: [
    { key: 'zone.collect', zoneCode: 'collect', label: '人口数据采集区设计', mCodes: ['M152'], deepLink: '/exchange/ingestion', deepLabel: '打开数据归集' },
    { key: 'zone.govern', zoneCode: 'govern', label: '人口数据治理及反馈区设计', mCodes: ['M153', 'M155', 'M156'], deepLink: '/governance', deepLabel: '打开数据治理' },
    { key: 'zone.core', zoneCode: 'core', label: '人口核心数据区设计', mCodes: ['M157'], deepLink: '/resource-center', deepLabel: '打开资源中心' },
    { key: 'zone.internal', zoneCode: 'internal', label: '人口数据内部服务区设计', mCodes: ['M158'], deepLink: '/resource-center', deepLabel: '打开资源中心' },
    { key: 'zone.share', zoneCode: 'share', label: '人口数据共享服务区设计', mCodes: ['M154', 'M159', 'M160', 'M161', 'M162', 'M163', 'M164', 'M165', 'M166', 'M167', 'M168', 'M169', 'M170', 'M171', 'M172', 'M173', 'M174'], deepLink: '/catalog', deepLabel: '打开资源目录' },
  ],
  legal: [
    { key: 'zone.collect', zoneCode: 'collect', label: '法人数据采集区设计', mCodes: ['M175'], deepLink: '/exchange/ingestion', deepLabel: '打开数据归集' },
    { key: 'zone.govern', zoneCode: 'govern', label: '法人数据治理及反馈区设计', mCodes: ['M176', 'M178', 'M179'], deepLink: '/governance', deepLabel: '打开数据治理' },
    { key: 'zone.core', zoneCode: 'core', label: '法人核心数据区设计', mCodes: ['M180'], deepLink: '/resource-center', deepLabel: '打开资源中心' },
    { key: 'zone.internal', zoneCode: 'internal', label: '法人数据内部服务区设计', mCodes: ['M181'], deepLink: '/resource-center', deepLabel: '打开资源中心' },
    { key: 'zone.share', zoneCode: 'share', label: '法人数据共享服务区设计', mCodes: ['M177', 'M182', 'M183', 'M184', 'M185', 'M186', 'M187', 'M188', 'M189', 'M190', 'M191', 'M192', 'M193', 'M194', 'M195', 'M196', 'M197'], deepLink: '/catalog', deepLabel: '打开资源目录' },
  ],
  macro: [],
  key: [],
}

const domainMeta: Record<string, { domain: string; title: string }> = {
  '/analytics/population': { domain: 'population', title: '人口大数据支撑' },
  '/analytics/legal-entity': { domain: 'legal', title: '法人大数据支撑' },
  '/analytics/macro': { domain: 'macro', title: '宏观经济大数据支撑' },
  '/analytics/key-domains': { domain: 'key', title: '重点领域大数据支撑' },
}

const route = useRoute()
const router = useRouter()

const meta = computed(() => domainMeta[route.path] || domainMeta['/analytics/population'])
const zones = computed(() => ZONE_DEFS[meta.value.domain] || [])
const hasZones = computed(() => zones.value.length > 0)
/** 人口域：自研样例表展示，不用 DataEase/BI */
const isPopulation = computed(() => meta.value.domain === 'population')

const dataEaseHealthy = ref(false)
const activeNav = ref('')
const shareTab = ref<'mount' | 'catalog' | 'api' | 'indicators' | 'models'>('mount')
const highlightModelCode = ref('')

const bindings = ref<Binding[]>([])
const candidates = ref<Candidate[]>([])
const indicators = ref<Indicator[]>([])
const models = ref<AnalysisModel[]>([])
const modelSamples = ref<ModelSample[]>([])
const samplesLoading = ref(false)

const bindDialog = ref(false)
const selectedCandidate = ref<Candidate | null>(null)
const modelDrawer = ref(false)
const editingModel = ref<AnalysisModel | null>(null)
const modelForm = ref({
  modelName: '',
  deDashboardId: '',
  dimensionJson: '',
  description: '',
  indicatorIds: [] as number[],
})
const iframeSrc = ref('')
const embedMode = ref<'LIVE' | 'LEDGER' | ''>('')
const embedMessage = ref('')
const embedUrl = ref('')

let applyingRoute = false
const zoneLoaded = ref('')
const designerLoaded = ref(false)

const navGroups = computed<HubNavGroup[]>(() => {
  if (!hasZones.value) {
    return [{ title: '域设计', items: [{ key: 'designer', label: '指标与分析模型' }] }]
  }
  return [{
    title: '数据区设计',
    items: zones.value.map((z) => ({ key: z.key, label: z.label })),
  }]
})

const activeZone = computed(() => zones.value.find((z) => z.key === activeNav.value) || null)
const isShare = computed(() => activeZone.value?.zoneCode === 'share')
const isDesignerOnly = computed(() => !hasZones.value && activeNav.value === 'designer')
const pageTitle = computed(() => {
  if (activeZone.value) return activeZone.value.label
  if (isDesignerOnly.value) return `${meta.value.title} · 指标与分析模型`
  return meta.value.title
})

function zoneApiPath(zoneKey: string) {
  return zoneKey.startsWith('zone.') ? zoneKey.slice(5) : zoneKey
}

function pickDefaultNav() {
  if (hasZones.value) {
    activeNav.value = zones.value[0].key
  } else {
    activeNav.value = 'designer'
    if (shareTab.value === 'mount' || shareTab.value === 'catalog' || shareTab.value === 'api') {
      shareTab.value = 'indicators'
    }
  }
}

function resolveFromRoute() {
  applyingRoute = true
  const q = String(route.query.tab || '').toLowerCase()
  highlightModelCode.value = ''
  if (!q) {
    pickDefaultNav()
  } else if (q === 'designer' || q === 'models' || q === 'indicators') {
    if (hasZones.value) {
      const share = zones.value.find((z) => z.zoneCode === 'share')
      activeNav.value = share?.key || zones.value[0]?.key || ''
      shareTab.value = q === 'indicators' ? 'indicators' : q === 'models' ? 'models' : 'mount'
    } else {
      activeNav.value = 'designer'
      shareTab.value = q === 'indicators' ? 'indicators' : 'models'
    }
  } else if (q.startsWith('zone.')) {
    const hit = zones.value.find((z) => z.key === q)
    activeNav.value = hit?.key || zones.value[0]?.key || 'designer'
  } else {
    const code = q.startsWith('m') ? q.toUpperCase() : `M${q}`
    const parentZone = zones.value.find((z) => z.mCodes.includes(code))
    if (parentZone) {
      activeNav.value = parentZone.key
      if (parentZone.zoneCode === 'share') {
        shareTab.value = 'models'
        highlightModelCode.value = code
      }
    } else if (hasZones.value) {
      pickDefaultNav()
    } else {
      activeNav.value = 'designer'
      shareTab.value = 'models'
      highlightModelCode.value = code
    }
  }
  if (!activeNav.value) pickDefaultNav()
  nextTick(() => { applyingRoute = false })
}

function syncQuery() {
  const q: Record<string, string> = {}
  for (const [k, v] of Object.entries(route.query)) {
    if (v == null || k === 'tab') continue
    q[k] = Array.isArray(v) ? String(v[0]) : String(v)
  }
  q.tab = activeNav.value
  router.replace({ query: q })
}

async function loadOverviewLite() {
  if (isPopulation.value) {
    dataEaseHealthy.value = false
    return
  }
  const res = await api.get(`/analytics/domain/${meta.value.domain}/overview`)
  dataEaseHealthy.value = !!res.data.dataEaseHealthy
}

async function loadBindings(force = false) {
  if (!activeZone.value) return
  const z = activeZone.value.zoneCode
  const cacheKey = `${meta.value.domain}:${z}`
  if (!force && zoneLoaded.value === cacheKey && bindings.value.length >= 0) {
    // still allow empty cache hit
  }
  const [bRes, cRes] = await Promise.all([
    api.get(`/analytics/domain/${meta.value.domain}/zones/${z}/bindings`),
    api.get(`/analytics/domain/${meta.value.domain}/zones/${z}/candidates`),
  ])
  bindings.value = bRes.data || []
  candidates.value = cRes.data || []
  zoneLoaded.value = cacheKey
}

async function loadDesigner(force = false) {
  if (designerLoaded.value && !force) return
  const [iRes, mRes] = await Promise.all([
    api.get(`/analytics/domain/${meta.value.domain}/indicators`),
    api.get(`/analytics/domain/${meta.value.domain}/models`),
  ])
  indicators.value = iRes.data || []
  models.value = mRes.data || []
  designerLoaded.value = true
}

async function loadCurrentView() {
  iframeSrc.value = ''
  embedMode.value = ''
  embedMessage.value = ''
  embedUrl.value = ''
  if (activeZone.value) {
    await loadBindings()
    if (isShare.value || shareTab.value === 'indicators' || shareTab.value === 'models') {
      await loadDesigner()
    }
  } else if (isDesignerOnly.value) {
    await loadDesigner()
  }
}

async function openBindDialog() {
  if (!activeZone.value) return
  await loadBindings(true)
  selectedCandidate.value = null
  bindDialog.value = true
}

async function confirmBind() {
  if (!activeZone.value || !selectedCandidate.value) {
    ElMessage.warning('请选择候选资产')
    return
  }
  const c = selectedCandidate.value
  await api.post(`/analytics/domain/${meta.value.domain}/zones/${activeZone.value.zoneCode}/bindings`, {
    assetType: c.assetType,
    assetRef: c.assetRef,
    assetName: c.assetName,
    physicalTable: c.physicalTable,
    metaEntryCode: c.metaEntryCode,
    dataLayer: c.dataLayer,
  })
  ElMessage.success('已挂载')
  bindDialog.value = false
  await loadBindings(true)
}

async function unbind(row: Binding) {
  await ElMessageBox.confirm(`确认解除挂载「${row.assetName}」？`, '解除挂载', { type: 'warning' })
  await api.delete(`/analytics/domain/bindings/${row.id}`)
  ElMessage.success('已解除')
  await loadBindings(true)
}

function onIndicatorsRefreshed() {
  void loadDesigner(true)
}

function openPortalPreview() {
  if (embedUrl.value) window.open(embedUrl.value, '_blank')
}

async function loadModelSamples(modelId: number) {
  samplesLoading.value = true
  modelSamples.value = []
  try {
    const res = await api.get(`/analytics/domain/models/${modelId}/samples`)
    modelSamples.value = (res.data as ModelSample[]) || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载样例失败')
  } finally {
    samplesLoading.value = false
  }
}

function openModelDesign(row: AnalysisModel) {
  editingModel.value = row
  modelForm.value = {
    modelName: row.modelName || '',
    deDashboardId: row.deDashboardId || '',
    dimensionJson: row.dimensionJson || '',
    description: row.description || '',
    indicatorIds: (row.indicators || []).map((i) => i.id),
  }
  iframeSrc.value = ''
  embedMode.value = ''
  embedMessage.value = ''
  embedUrl.value = ''
  modelSamples.value = []
  modelDrawer.value = true
  if (isPopulation.value) {
    void loadModelSamples(row.id)
  }
}

async function saveModelDesign() {
  if (!editingModel.value) return
  const body: Record<string, unknown> = {
    modelName: modelForm.value.modelName,
    dimensionJson: modelForm.value.dimensionJson,
    description: modelForm.value.description,
    indicatorIds: modelForm.value.indicatorIds,
  }
  if (!isPopulation.value) {
    body.deDashboardId = modelForm.value.deDashboardId
  }
  await api.put(`/analytics/domain/models/${editingModel.value.id}`, body)
  ElMessage.success('模型设计已保存')
  await loadDesigner(true)
  const fresh = models.value.find((m) => m.id === editingModel.value?.id)
  if (fresh) editingModel.value = fresh
}

async function issueModelEmbed() {
  if (!editingModel.value || isPopulation.value) return
  try {
    const res = await api.post(`/analytics/domain/models/${editingModel.value.id}/embed-token`, {})
    embedUrl.value = (res.data.embedUrl as string) || ''
    embedMode.value = (res.data.mode as 'LIVE' | 'LEDGER') || 'LEDGER'
    embedMessage.value = String(res.data.message || '')
    const deUrl = res.data.dataeaseUrl as string | null
    if (embedMode.value === 'LIVE' && deUrl) {
      iframeSrc.value = deUrl
      ElMessage.success(embedMessage.value || '嵌入已加载')
    } else {
      iframeSrc.value = ''
      ElMessage.warning(embedMessage.value || 'DataEase 未就绪')
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '签发失败')
  }
}

function onHubSelect(key: string) {
  activeNav.value = key
  if (key.endsWith('share')) shareTab.value = 'mount'
  if (!applyingRoute) syncQuery()
  loadCurrentView()
}

watch(() => route.path, async () => {
  activeNav.value = ''
  zoneLoaded.value = ''
  designerLoaded.value = false
  bindings.value = []
  indicators.value = []
  models.value = []
  await loadOverviewLite()
  resolveFromRoute()
  await loadCurrentView()
})

watch(() => route.query.tab, async () => {
  if (applyingRoute) return
  resolveFromRoute()
  await loadCurrentView()
})

watch(shareTab, async (t) => {
  if ((t === 'indicators' || t === 'models' || t === 'mount') && (isShare.value || isDesignerOnly.value)) {
    if (t === 'indicators' || t === 'models') await loadDesigner()
    if (t === 'mount' && activeZone.value) await loadBindings()
  }
})

onMounted(async () => {
  try {
    await loadOverviewLite()
    resolveFromRoute()
    if (!applyingRoute && activeNav.value && !route.query.tab) syncQuery()
    await loadCurrentView()
  } catch {
    ElMessage.error('加载失败')
  }
})
</script>

<template>
  <div class="ana-hub-root">
    <HubSideLayout v-model="activeNav" :groups="navGroups" @select="onHubSelect">
      <!-- 五区：资产挂载设计 -->
      <PageCard v-if="activeZone && !isShare" :title="pageTitle">
        <el-alert
          type="info"
          :closable="false"
          style="margin-bottom:12px"
          title="区设计 = 从现有资产选型挂载（挂载≠复制）。过程层 DWD 适合治理反馈区，默认可共享资源挂核心区/共享区。"
        />
        <div class="dim-grid">
          <div v-for="d in SEVEN_DIMS" :key="d.key" class="dim-item">
            <div class="dim-key">{{ d.key }}</div>
            <div class="dim-tip">{{ d.tip }}</div>
          </div>
        </div>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="openBindDialog">选型挂载</el-button>
            <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
            <el-button @click="loadBindings(true)">刷新</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="bindings" stripe size="small" empty-text="尚未挂载资产，请从候选中选型">
          <el-table-column prop="assetName" label="资产名称" min-width="160" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ assetTypeLabel(row.assetType) }}</template>
          </el-table-column>
          <el-table-column prop="physicalTable" label="物理表" min-width="140" show-overflow-tooltip />
          <el-table-column prop="dataLayer" label="分层" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="挂载时间" width="170" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button link type="danger" @click="unbind(row)">解除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>

      <!-- 共享服务区：挂载 + 指标 + 模型 -->
      <PageCard v-else-if="activeZone && isShare" :title="pageTitle">
        <el-alert
          v-if="isPopulation"
          type="info"
          :closable="false"
          style="margin-bottom:12px"
          title="共享服务区：目录/接口深链 + 指标库 + 分析模型；人口域模型以自研样例/结果表展示，不使用 DataEase/BI。"
        />
        <el-alert
          v-else
          :type="dataEaseHealthy ? 'success' : 'warning'"
          :closable="false"
          style="margin-bottom:12px"
          :title="dataEaseHealthy
            ? '共享服务区：目录/接口深链 + 指标库 + 分析模型列表；DataEase 在线可实时嵌入'
            : '共享服务区设计器可用；DataEase 离线时模型预览仅为台账（LEDGER）'"
        />
        <el-tabs v-model="shareTab">
          <el-tab-pane label="资产挂载" name="mount">
            <el-form inline class="portal-inline-form">
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="openBindDialog">选型挂载目录资源</el-button>
                <el-button @click="loadBindings(true)">刷新</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="bindings" stripe size="small" empty-text="可挂载已编目资源">
              <el-table-column prop="assetName" label="资源名称" min-width="160" />
              <el-table-column prop="assetRef" label="资源编码" min-width="120" />
              <el-table-column prop="physicalTable" label="物理表" min-width="120" show-overflow-tooltip />
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button link type="danger" @click="unbind(row)">解除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="目录深链" name="catalog">
            <p class="hint">共享目录编制、审批与门户在数据目录管理系统完成；此处仅作区设计入口。</p>
            <el-button type="primary" @click="$router.push('/catalog')">打开资源目录</el-button>
          </el-tab-pane>
          <el-tab-pane label="接口/批量" name="api">
            <p class="hint">接口交换与批量共享走共享交换平台能力，不在分析域平行实现。</p>
            <el-button type="primary" @click="$router.push('/exchange/esb')">打开接口交换</el-button>
            <el-button @click="$router.push('/exchange/application')">打开应用平台</el-button>
          </el-tab-pane>
          <el-tab-pane label="指标库" name="indicators">
            <DomainIndicatorSqlLibrary
              :domain="meta.domain"
              @refreshed="onIndicatorsRefreshed"
            />
          </el-tab-pane>
          <el-tab-pane label="分析模型" name="models">
            <p class="hint">
              {{ isPopulation
                ? '分析模型 = 场景包（多指标 + 维度 + 自研结果表）。指标 ≠ 模型。'
                : '分析模型 = 场景包（多指标 + 维度 + 看板）。指标 ≠ 模型。' }}
            </p>
            <el-table
              :data="models"
              stripe
              size="small"
              empty-text="暂无模型"
              :row-class-name="modelRowClassName"
            >
              <el-table-column prop="modelName" label="模型名称" min-width="180" />
              <el-table-column prop="modelCode" label="编码" width="120" />
              <el-table-column label="关联指标" min-width="160">
                <template #default="{ row }">
                  {{ (row.indicators || []).map((i: Indicator) => i.indicatorName).join('、') || '—' }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openModelDesign(row)">设计</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <!-- 宏观/重点：无五区，直接设计器 -->
      <PageCard v-else-if="isDesignerOnly" :title="pageTitle">
        <el-tabs v-model="shareTab">
          <el-tab-pane label="指标库" name="indicators">
            <DomainIndicatorSqlLibrary
              :domain="meta.domain"
              @refreshed="onIndicatorsRefreshed"
            />
          </el-tab-pane>
          <el-tab-pane label="分析模型" name="models">
            <el-table :data="models" stripe size="small">
              <el-table-column prop="modelName" label="模型名称" min-width="180" />
              <el-table-column label="关联指标" min-width="160">
                <template #default="{ row }">
                  {{ (row.indicators || []).map((i: Indicator) => i.indicatorName).join('、') || '—' }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openModelDesign(row)">设计</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <PageCard v-else :title="pageTitle">
        <el-empty description="请从左侧选择数据区" />
      </PageCard>
    </HubSideLayout>

    <el-dialog v-model="bindDialog" title="从现有资产选型挂载" width="720px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom:8px" title="只登记归属关系，不复制数据。优先选择已登记元数据或已纳管对象。" />
      <el-table
        :data="candidates"
        stripe
        size="small"
        highlight-current-row
        max-height="360"
        @current-change="(row: Candidate | null) => { selectedCandidate = row }"
      >
        <el-table-column prop="assetName" label="名称" min-width="160" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ assetTypeLabel(row.assetType) }}</template>
        </el-table-column>
        <el-table-column prop="physicalTable" label="物理表" min-width="140" show-overflow-tooltip />
        <el-table-column prop="dataLayer" label="分层" width="90" />
      </el-table>
      <template #footer>
        <el-button @click="bindDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedCandidate" @click="confirmBind">确认挂载</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="modelDrawer" size="640px" :title="editingModel ? `设计：${editingModel.modelName}` : '分析模型'" destroy-on-close>
      <el-form v-if="editingModel" label-width="100px">
        <el-form-item label="模型名称">
          <el-input v-model="modelForm.modelName" />
        </el-form-item>
        <el-form-item label="关联指标">
          <el-select v-model="modelForm.indicatorIds" multiple filterable style="width:100%" placeholder="选择指标">
            <el-option v-for="i in indicators" :key="i.id" :label="i.indicatorName" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="维度 JSON">
          <el-input v-model="modelForm.dimensionJson" type="textarea" :rows="3" placeholder='例如 ["区县","年龄段"]' />
        </el-form-item>
        <el-form-item v-if="!isPopulation" label="看板标识">
          <el-input
            v-model="modelForm.deDashboardId"
            placeholder="填预览地址中的 dvId，如 1280620734217064448（勿填公共分享码）"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="modelForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveModelDesign">保存设计</el-button>
          <template v-if="!isPopulation">
            <el-button @click="issueModelEmbed">签发嵌入</el-button>
            <el-button v-if="embedUrl" @click="openPortalPreview">门户预览</el-button>
          </template>
          <el-button v-else :loading="samplesLoading" @click="editingModel && loadModelSamples(editingModel.id)">刷新样例</el-button>
        </el-form-item>
      </el-form>

      <template v-if="isPopulation">
        <el-alert
          type="info"
          :closable="false"
          style="margin-bottom:8px"
          title="自研结果预览"
          description="人口域不嵌入 DataEase；下表为模型样例/结果行（可验收 ≥100 行）。"
        />
        <el-table v-loading="samplesLoading" :data="modelSamples" stripe size="small" max-height="360" empty-text="暂无样例">
          <el-table-column prop="rowNo" label="#" width="60" />
          <el-table-column prop="dim1" label="维度1" min-width="100" />
          <el-table-column prop="dim2" label="维度2" min-width="100" />
          <el-table-column prop="metric1" label="指标1" width="100" />
          <el-table-column prop="metric2" label="指标2" width="100" />
        </el-table>
      </template>
      <template v-else>
        <el-alert
          v-if="embedMode"
          :type="embedMode === 'LIVE' ? 'success' : 'warning'"
          :closable="false"
          style="margin-bottom:8px"
          :title="embedMode === 'LIVE' ? '实时嵌入' : '台账预览'"
          :description="embedMessage"
        />
        <div class="iframe-shell">
          <iframe v-if="iframeSrc" class="de-iframe" :src="iframeSrc" title="DataEase" />
          <div v-else class="iframe-placeholder">
            {{ dataEaseHealthy ? '签发令牌后加载嵌入画布' : '启动 DataEase 后可加载实时嵌入' }}
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.ana-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
.dim-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}
.dim-item {
  border: 1px solid var(--portal-border, #e4e7ed);
  border-radius: 6px;
  padding: 8px 10px;
  background: var(--el-fill-color-blank, #fff);
}
.dim-key { font-weight: 600; font-size: 13px; margin-bottom: 4px; }
.dim-tip { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.4; }
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; font-size: 13px; }
.iframe-shell {
  margin-top: 12px;
  min-height: 280px;
  border: 1px solid var(--portal-border, #dcdfe6);
  border-radius: 8px;
  overflow: hidden;
  background: #0b1f33;
}
.de-iframe { width: 100%; height: 280px; border: 0; }
.iframe-placeholder {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #e8f4ff;
  opacity: 0.8;
  padding: 16px;
  text-align: center;
}
:deep(.row-hl) { background: var(--el-color-primary-light-9) !important; }
</style>
