<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { formatDateTime } from '@/utils/datetime'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface ReportRow {
  id: number
  reportCode: string
  reportName: string
  dimension: string
  score: number
  exportPayload?: string
  createdAt?: string
}

interface TrendPoint {
  date: string
  reportCount: number
  reportAvgScore: number | null
  runCount: number
  runAvgScore: number | null
}

interface IssueRow {
  id: number
  checkType?: string
  targetTable?: string
  targetColumn?: string
  issueType?: string
  issueValue?: string
  issueCount?: number
  severity?: string
  status?: string
}

interface AnalysisCase {
  id: number
  caseCode: string
  caseName: string
  reportId?: number
  targetTable?: string
  targetColumn?: string
  issueType?: string
  severity?: string
  locateSummary?: string
  rootCause?: string
  impactScope?: string
  suggestedAction?: string
  status?: string
  createdAt?: string
}

interface HotspotRow {
  targetTable?: string
  targetColumn?: string
  issueType?: string
  severity?: string
  issueCount?: number
  sampleValue?: string
  suggestedRootCause?: string
  suggestedSolution?: string
}

interface CodeImpact {
  id: number
  impactCode: string
  standardCode?: string
  standardName?: string
  sourceSystem?: string
  sourceTable?: string
  sourceColumn?: string
  mappingStatus?: string
  impactLevel?: string
  impactDesc?: string
  downstreamRefs?: string
  issueCount?: number
  status?: string
  createdAt?: string
}

interface KnowledgeRow {
  id: number
  knowledgeCode: string
  title: string
  issueType?: string
  category?: string
  symptom?: string
  rootCause?: string
  solution?: string
  relatedStandard?: string
  hitCount?: number
  status?: string
  createdAt?: string
}

const activeTab = ref('overview')
const reports = ref<ReportRow[]>([])
const {
  page: reportPage,
  pageSize: reportPageSize,
  paged: pagedReports,
  total: reportTotal,
  resetPage: resetReportPage,
} = useClientPager(reports)
const trend = ref<TrendPoint[]>([])
const loading = ref(false)
const selectedId = ref<number | null>(null)
const issues = ref<IssueRow[]>([])
const drillVisible = ref(false)
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const analysisOverview = ref({
  caseCount: 0,
  openCaseCount: 0,
  impactCount: 0,
  highImpactCount: 0,
  knowledgeCount: 0,
})

const locateQuery = reactive({ keyword: '', targetTable: '', issueType: '', severity: '' })
const locateLoading = ref(false)
const locateCases = ref<AnalysisCase[]>([])
const hotspots = ref<HotspotRow[]>([])
const selectedCase = ref<AnalysisCase | null>(null)

const impactQuery = reactive({ keyword: '', impactLevel: '', mappingStatus: '' })
const impactLoading = ref(false)
const impacts = ref<CodeImpact[]>([])
const impactDetailVisible = ref(false)
const impactDetail = ref<{
  impact?: CodeImpact
  analysisCase?: AnalysisCase
  downstreamList?: string[]
  mappings?: Array<{ mappingStatus?: string; matchScore?: number; remark?: string }>
} | null>(null)

const knowledgeQuery = reactive({ keyword: '', issueType: '', category: '' })
const knowledgeLoading = ref(false)
const knowledgeList = ref<KnowledgeRow[]>([])
const knowledgeDialog = ref(false)
const knowledgeSaving = ref(false)
const knowledgeForm = reactive({
  id: null as number | null,
  title: '',
  issueType: '',
  category: 'LOCATE',
  symptom: '',
  rootCause: '',
  solution: '',
  relatedStandard: '',
  status: 'ACTIVE',
})

const {
  page: casePage,
  pageSize: casePageSize,
  paged: pagedCases,
  total: caseTotal,
  resetPage: resetCasePage,
} = useClientPager(locateCases)

const {
  page: impactPage,
  pageSize: impactPageSize,
  paged: pagedImpacts,
  total: impactTotal,
  resetPage: resetImpactPage,
} = useClientPager(impacts)

const {
  page: knowledgePage,
  pageSize: knowledgePageSize,
  paged: pagedKnowledge,
  total: knowledgeTotal,
  resetPage: resetKnowledgePage,
} = useClientPager(knowledgeList)

const latestScore = computed(() => reports.value[0]?.score ?? null)
const avgScore = computed(() => {
  if (!reports.value.length) return null
  const sum = reports.value.reduce((a, r) => a + Number(r.score || 0), 0)
  return Math.round((sum / reports.value.length) * 10) / 10
})
const trendRunCount = computed(() => trend.value.reduce((a, t) => a + Number(t.runCount || 0), 0))

function scoreTone(score: number | null) {
  if (score == null) return 'neutral'
  if (score >= 90) return 'good'
  if (score >= 75) return 'warn'
  return 'bad'
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption({
    color: ['#0e7490', '#2563eb', '#94a3b8'],
    tooltip: { trigger: 'axis' },
    legend: { data: ['报告均分', '运行均分', '运行数'], top: 4 },
    grid: { left: 44, right: 44, top: 48, bottom: 28 },
    xAxis: {
      type: 'category',
      data: trend.value.map((t) => t.date.slice(5)),
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b' },
    },
    yAxis: [
      {
        type: 'value',
        name: '评分',
        min: 0,
        max: 100,
        nameTextStyle: { color: '#64748b' },
        splitLine: { lineStyle: { color: '#e2e8f0', type: 'dashed' } },
        axisLabel: { color: '#64748b' },
      },
      {
        type: 'value',
        name: '次数',
        nameTextStyle: { color: '#64748b' },
        splitLine: { show: false },
        axisLabel: { color: '#64748b' },
      },
    ],
    series: [
      {
        name: '报告均分',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        areaStyle: { color: 'rgba(14,116,144,0.12)' },
        data: trend.value.map((t) => t.reportAvgScore),
      },
      {
        name: '运行均分',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        data: trend.value.map((t) => t.runAvgScore),
      },
      {
        name: '运行数',
        type: 'bar',
        barMaxWidth: 18,
        yAxisIndex: 1,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
        data: trend.value.map((t) => t.runCount),
      },
    ],
  })
}

async function loadList() {
  loading.value = true
  try {
    reports.value = (await api.get('/governance/quality/reports-mgmt')).data || []
    resetReportPage()
  } catch {
    ElMessage.error('加载报告失败')
  } finally {
    loading.value = false
  }
}

async function loadTrend() {
  try {
    trend.value = (await api.get('/governance/quality/reports-mgmt/trend', { params: { days: 14 } })).data || []
    await nextTick()
    renderChart()
  } catch {
    trend.value = []
  }
}

interface ReportDetail {
  report?: ReportRow
  recentRuns?: Array<{ id: number; taskId?: number; score?: number; status?: string; startedAt?: string }>
  avgRunScore?: number | null
  runCount?: number
}

const detail = ref<ReportDetail | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailPanelRef = ref<HTMLElement | null>(null)
const reportTableRef = ref<{ setCurrentRow?: (row?: ReportRow) => void } | null>(null)

async function showDetail(row: ReportRow) {
  selectedId.value = row.id
  detailVisible.value = true
  detailLoading.value = true
  reportTableRef.value?.setCurrentRow?.(row)
  try {
    const res = await api.get(`/governance/quality/reports-mgmt/${row.id}`)
    detail.value = { ...(res.data || {}), report: res.data?.report || row }
    await nextTick()
    detailPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '加载报告详情失败')
    detail.value = { report: row }
  } finally {
    detailLoading.value = false
  }
}

async function openDrill(row: ReportRow) {
  selectedId.value = row.id
  reportTableRef.value?.setCurrentRow?.(row)
  try {
    const res = await api.get(`/governance/quality/reports-mgmt/${row.id}/drill`)
    issues.value = res.data?.issues || []
    detail.value = {
      report: row,
      avgRunScore: res.data?.score,
      runCount: (res.data?.runs || []).length,
    }
    drillVisible.value = true
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '下钻失败')
  }
}

function openAnalyze(row: ReportRow) {
  activeTab.value = 'locate'
  locateQuery.keyword = row.reportName || row.reportCode || ''
  void loadLocate()
}

async function doExport(row: ReportRow) {
  const res = await api.get(`/governance/quality/reports-mgmt/${row.id}/export`)
  const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${row.reportCode || 'report'}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出 JSON')
}

async function generate() {
  try {
    await api.post('/governance/platform/quality/reports', {
      reportName: '六性质量分析报告',
      dimension: '完整性+规范性+准确性+唯一性+一致性+及时性',
    })
    ElMessage.success('报告已生成')
    await loadList()
    await loadTrend()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '生成失败：需先有真实运行评分')
  }
}

async function loadAnalysisOverview() {
  try {
    analysisOverview.value = (await api.get('/governance/quality/reports-mgmt/analysis/overview')).data || analysisOverview.value
  } catch {
    /* 概览失败不阻断 */
  }
}

async function loadLocate() {
  locateLoading.value = true
  try {
    const res = await api.get('/governance/quality/reports-mgmt/analysis/locate', {
      params: {
        keyword: locateQuery.keyword || undefined,
        targetTable: locateQuery.targetTable || undefined,
        issueType: locateQuery.issueType || undefined,
        severity: locateQuery.severity || undefined,
      },
    })
    locateCases.value = res.data?.cases || []
    hotspots.value = res.data?.hotspots || []
    resetCasePage()
    selectedCase.value = locateCases.value[0] || null
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '问题定位加载失败')
  } finally {
    locateLoading.value = false
  }
}

function resetLocateQuery() {
  locateQuery.keyword = ''
  locateQuery.targetTable = ''
  locateQuery.issueType = ''
  locateQuery.severity = ''
  void loadLocate()
}

async function loadImpacts() {
  impactLoading.value = true
  try {
    impacts.value = (await api.get('/governance/quality/reports-mgmt/analysis/code-impacts', {
      params: {
        keyword: impactQuery.keyword || undefined,
        impactLevel: impactQuery.impactLevel || undefined,
        mappingStatus: impactQuery.mappingStatus || undefined,
      },
    })).data || []
    resetImpactPage()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '编码映射影响加载失败')
  } finally {
    impactLoading.value = false
  }
}

function resetImpactQuery() {
  impactQuery.keyword = ''
  impactQuery.impactLevel = ''
  impactQuery.mappingStatus = ''
  void loadImpacts()
}

async function openImpactDetail(row: CodeImpact) {
  if (row.id < 0) {
    impactDetail.value = {
      impact: row,
      downstreamList: (row.downstreamRefs || '').split(/[;,]/).map((s) => s.trim()).filter(Boolean),
      mappings: [],
    }
    impactDetailVisible.value = true
    return
  }
  try {
    impactDetail.value = (await api.get(`/governance/quality/reports-mgmt/analysis/code-impacts/${row.id}`)).data || null
    impactDetailVisible.value = true
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '影响详情加载失败')
  }
}

async function loadKnowledge() {
  knowledgeLoading.value = true
  try {
    knowledgeList.value = (await api.get('/governance/quality/reports-mgmt/analysis/knowledge', {
      params: {
        keyword: knowledgeQuery.keyword || undefined,
        issueType: knowledgeQuery.issueType || undefined,
        category: knowledgeQuery.category || undefined,
      },
    })).data || []
    resetKnowledgePage()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '知识库加载失败')
  } finally {
    knowledgeLoading.value = false
  }
}

function resetKnowledgeQuery() {
  knowledgeQuery.keyword = ''
  knowledgeQuery.issueType = ''
  knowledgeQuery.category = ''
  void loadKnowledge()
}

function openKnowledgeCreate() {
  knowledgeForm.id = null
  knowledgeForm.title = ''
  knowledgeForm.issueType = ''
  knowledgeForm.category = 'LOCATE'
  knowledgeForm.symptom = ''
  knowledgeForm.rootCause = ''
  knowledgeForm.solution = ''
  knowledgeForm.relatedStandard = ''
  knowledgeForm.status = 'ACTIVE'
  knowledgeDialog.value = true
}

function openKnowledgeEdit(row: KnowledgeRow) {
  knowledgeForm.id = row.id
  knowledgeForm.title = row.title
  knowledgeForm.issueType = row.issueType || ''
  knowledgeForm.category = row.category || 'OTHER'
  knowledgeForm.symptom = row.symptom || ''
  knowledgeForm.rootCause = row.rootCause || ''
  knowledgeForm.solution = row.solution || ''
  knowledgeForm.relatedStandard = row.relatedStandard || ''
  knowledgeForm.status = row.status || 'ACTIVE'
  knowledgeDialog.value = true
}

async function saveKnowledge() {
  if (!knowledgeForm.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  knowledgeSaving.value = true
  try {
    const body = { ...knowledgeForm }
    if (knowledgeForm.id) {
      await api.put(`/governance/quality/reports-mgmt/analysis/knowledge/${knowledgeForm.id}`, body)
    } else {
      await api.post('/governance/quality/reports-mgmt/analysis/knowledge', body)
    }
    ElMessage.success('知识已保存')
    knowledgeDialog.value = false
    await loadKnowledge()
    await loadAnalysisOverview()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '保存失败')
  } finally {
    knowledgeSaving.value = false
  }
}

async function hitKnowledge(row: KnowledgeRow) {
  try {
    await api.post(`/governance/quality/reports-mgmt/analysis/knowledge/${row.id}/hit`)
    ElMessage.success('已记一次命中，可用于报告引用')
    await loadKnowledge()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '命中记录失败')
  }
}

async function deleteKnowledge(row: KnowledgeRow) {
  try {
    await ElMessageBox.confirm(`确认删除知识「${row.title}」？`, '删除确认', { type: 'warning' })
    await api.delete(`/governance/quality/reports-mgmt/analysis/knowledge/${row.id}`)
    ElMessage.success('已删除')
    await loadKnowledge()
    await loadAnalysisOverview()
  } catch (e: unknown) {
    if ((e as string) === 'cancel') return
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '删除失败')
  }
}

function onResize() {
  chart?.resize()
}

watch(activeTab, async (tab) => {
  if (tab === 'overview') {
    await nextTick()
    renderChart()
    return
  }
  if (tab === 'locate') {
    if (!locateCases.value.length && !hotspots.value.length) await loadLocate()
    return
  }
  if (tab === 'impact') {
    if (!impacts.value.length) await loadImpacts()
    return
  }
  if (tab === 'knowledge') {
    if (!knowledgeList.value.length) await loadKnowledge()
  }
})

onMounted(async () => {
  await Promise.all([loadList(), loadTrend(), loadAnalysisOverview()])
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="qr-page">
    <PageCard title="数据质量分析报告">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="qr-hint"
        title="系统辅助定位质量问题根因：快速定位表字段热点、分析编码映射影响，并将处置经验沉淀为知识，支撑业务质量分析报告。"
      />

      <el-tabs v-model="activeTab" class="qr-tabs">
        <el-tab-pane label="报告总览" name="overview" />
        <el-tab-pane label="问题快速定位" name="locate" />
        <el-tab-pane label="编码映射影响" name="impact" />
        <el-tab-pane label="知识沉淀" name="knowledge" />
      </el-tabs>

      <div v-show="activeTab === 'overview'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="generate">生成报告</el-button>
            <el-button @click="loadList()">刷新列表</el-button>
          </el-form-item>
        </el-form>

        <div class="qr-kpis">
          <div class="qr-kpi" :class="`qr-kpi--${scoreTone(latestScore)}`">
            <div class="qr-kpi__label">最新报告评分</div>
            <div class="qr-kpi__value">{{ latestScore ?? '—' }}</div>
          </div>
          <div class="qr-kpi" :class="`qr-kpi--${scoreTone(avgScore)}`">
            <div class="qr-kpi__label">历史均分</div>
            <div class="qr-kpi__value">{{ avgScore ?? '—' }}</div>
          </div>
          <div class="qr-kpi">
            <div class="qr-kpi__label">报告份数</div>
            <div class="qr-kpi__value">{{ reports.length }}</div>
          </div>
          <div class="qr-kpi">
            <div class="qr-kpi__label">近 14 日运行次数</div>
            <div class="qr-kpi__value">{{ trendRunCount }}</div>
          </div>
        </div>

        <div class="qr-kpis qr-kpis--analysis">
          <div class="qr-kpi">
            <div class="qr-kpi__label">定位案例</div>
            <div class="qr-kpi__value qr-kpi__value--sm">{{ analysisOverview.caseCount }}</div>
          </div>
          <div class="qr-kpi">
            <div class="qr-kpi__label">待处理/分析中</div>
            <div class="qr-kpi__value qr-kpi__value--sm">{{ analysisOverview.openCaseCount }}</div>
          </div>
          <div class="qr-kpi">
            <div class="qr-kpi__label">映射影响项</div>
            <div class="qr-kpi__value qr-kpi__value--sm">{{ analysisOverview.impactCount }}</div>
          </div>
          <div class="qr-kpi">
            <div class="qr-kpi__label">知识条目</div>
            <div class="qr-kpi__value qr-kpi__value--sm">{{ analysisOverview.knowledgeCount }}</div>
          </div>
        </div>

        <div class="qr-section-title">质量趋势（近 14 日）</div>
        <div ref="chartRef" class="qr-chart" />

        <div class="qr-section-title">报告清单</div>
        <el-table
          ref="reportTableRef"
          v-loading="loading"
          :data="pagedReports"
          stripe
          border
          size="small"
          highlight-current-row
          row-key="id"
          class="portal-table"
        >
          <el-table-column prop="reportCode" label="编码" width="160" />
          <el-table-column prop="reportName" label="名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="dimension" label="维度" min-width="180" show-overflow-tooltip />
          <el-table-column label="评分" width="100">
            <template #default="{ row }">
              <span class="qr-score" :class="`qr-score--${scoreTone(row.score)}`">{{ row.score }}</span>
            </template>
          </el-table-column>
          <el-table-column label="生成时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="showDetail(row)">详情</el-button>
              <el-button link type="primary" @click="openAnalyze(row)">分析</el-button>
              <el-button link @click="openDrill(row)">下钻</el-button>
              <el-button link @click="doExport(row)">导出</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-model:page="reportPage"
          v-model:page-size="reportPageSize"
          :total="reportTotal"
        />
        <el-empty v-if="!loading && !reports.length" description="暂无报告；需先有任务运行评分后再生成" />

        <div ref="detailPanelRef" class="qr-section-title">报告详情</div>
        <template v-if="detail?.report || selectedId">
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="报告评分">
              {{ detail?.report?.score ?? reports.find(r => r.id === selectedId)?.score ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="关联运行数">{{ detail?.runCount ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="运行均分">{{ detail?.avgRunScore ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="维度">
              {{ detail?.report?.dimension || reports.find(r => r.id === selectedId)?.dimension || '—' }}
            </el-descriptions-item>
          </el-descriptions>
          <el-table
            v-if="detail?.recentRuns?.length"
            :data="detail.recentRuns"
            stripe
            border
            size="small"
            style="margin-top: 12px"
            class="portal-table"
          >
            <el-table-column prop="id" label="运行ID" width="80" />
            <el-table-column prop="score" label="评分" width="80" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ statusLabel(row.status) }}</template>
            </el-table-column>
            <el-table-column label="开始时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
            </el-table-column>
          </el-table>
        </template>
        <el-empty v-else description="点击「详情」查看结构化摘要" />
      </div>

      <div v-show="activeTab === 'locate'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="关键字" class="portal-field-lg">
            <el-input v-model="locateQuery.keyword" clearable placeholder="案例/表字段/根因" />
          </el-form-item>
          <el-form-item label="表名" class="portal-field-md">
            <el-input v-model="locateQuery.targetTable" clearable placeholder="全部" />
          </el-form-item>
          <el-form-item label="问题类型" class="portal-field-md">
            <el-select v-model="locateQuery.issueType" clearable placeholder="全部">
              <el-option label="空值" value="NULL_VALUE" />
              <el-option label="重复值" value="DUPLICATE" />
              <el-option label="无效值" value="INVALID" />
              <el-option label="超范围" value="OUT_OF_RANGE" />
              <el-option label="格式错误" value="FORMAT_ERROR" />
            </el-select>
          </el-form-item>
          <el-form-item label="级别" class="portal-field-sm">
            <el-select v-model="locateQuery.severity" clearable placeholder="全部">
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadLocate">查询</el-button>
            <el-button @click="resetLocateQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <div class="qr-section-title">定位案例</div>
        <el-table
          v-loading="locateLoading"
          :data="pagedCases"
          stripe
          border
          size="small"
          highlight-current-row
          class="portal-table"
          @current-change="(row: AnalysisCase | null) => { selectedCase = row }"
        >
          <el-table-column prop="caseCode" label="编码" width="150" />
          <el-table-column prop="caseName" label="案例" min-width="160" show-overflow-tooltip />
          <el-table-column prop="targetTable" label="表" min-width="140" show-overflow-tooltip />
          <el-table-column prop="targetColumn" label="字段" width="120" show-overflow-tooltip />
          <el-table-column label="问题类型" width="100">
            <template #default="{ row }">{{ statusLabel(row.issueType) }}</template>
          </el-table-column>
          <el-table-column label="级别" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.severity)">{{ statusLabel(row.severity) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="selectedCase = row">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination v-model:page="casePage" v-model:page-size="casePageSize" :total="caseTotal" />

        <el-descriptions v-if="selectedCase" :column="1" size="small" border class="qr-case-detail">
          <el-descriptions-item label="快速定位">{{ selectedCase.locateSummary || '—' }}</el-descriptions-item>
          <el-descriptions-item label="疑似根因">{{ selectedCase.rootCause || '—' }}</el-descriptions-item>
          <el-descriptions-item label="影响范围">{{ selectedCase.impactScope || '—' }}</el-descriptions-item>
          <el-descriptions-item label="建议整改">{{ selectedCase.suggestedAction || '—' }}</el-descriptions-item>
        </el-descriptions>

        <div class="qr-section-title">问题热点（运行问题聚合 + 知识建议）</div>
        <el-table :data="hotspots" stripe border size="small" class="portal-table">
          <el-table-column prop="targetTable" label="表" min-width="140" show-overflow-tooltip />
          <el-table-column prop="targetColumn" label="字段" width="120" />
          <el-table-column label="问题类型" width="100">
            <template #default="{ row }">{{ statusLabel(row.issueType) }}</template>
          </el-table-column>
          <el-table-column prop="issueCount" label="数量" width="80" />
          <el-table-column prop="sampleValue" label="样例值" min-width="120" show-overflow-tooltip />
          <el-table-column prop="suggestedRootCause" label="知识建议根因" min-width="180" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!locateLoading && !locateCases.length && !hotspots.length" description="暂无定位结果" />
      </div>

      <div v-show="activeTab === 'impact'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="关键字" class="portal-field-lg">
            <el-input v-model="impactQuery.keyword" clearable placeholder="标准/表字段/描述" />
          </el-form-item>
          <el-form-item label="影响级别" class="portal-field-sm">
            <el-select v-model="impactQuery.impactLevel" clearable placeholder="全部">
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
          </el-form-item>
          <el-form-item label="映射状态" class="portal-field-md">
            <el-select v-model="impactQuery.mappingStatus" clearable placeholder="全部">
              <el-option label="已对标" value="MAPPED" />
              <el-option label="部分对标" value="PARTIAL" />
              <el-option label="未对标" value="UNMAPPED" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadImpacts">查询</el-button>
            <el-button @click="resetImpactQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table
          v-loading="impactLoading"
          :data="pagedImpacts"
          stripe
          border
          size="small"
          class="portal-table"
        >
          <el-table-column prop="impactCode" label="编码" width="140" />
          <el-table-column prop="standardName" label="标准项" min-width="140" show-overflow-tooltip />
          <el-table-column prop="sourceTable" label="源表" min-width="140" show-overflow-tooltip />
          <el-table-column prop="sourceColumn" label="源字段" width="120" />
          <el-table-column label="映射状态" width="100">
            <template #default="{ row }">{{ statusLabel(row.mappingStatus) }}</template>
          </el-table-column>
          <el-table-column label="影响级别" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.impactLevel)">{{ statusLabel(row.impactLevel) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="issueCount" label="问题数" width="80" />
          <el-table-column prop="impactDesc" label="影响说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openImpactDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination v-model:page="impactPage" v-model:page-size="impactPageSize" :total="impactTotal" />
        <el-empty v-if="!impactLoading && !impacts.length" description="暂无编码映射影响记录" />
      </div>

      <div v-show="activeTab === 'knowledge'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="关键字" class="portal-field-lg">
            <el-input v-model="knowledgeQuery.keyword" clearable placeholder="标题/症状/方案" />
          </el-form-item>
          <el-form-item label="问题类型" class="portal-field-md">
            <el-select v-model="knowledgeQuery.issueType" clearable placeholder="全部">
              <el-option label="空值" value="NULL_VALUE" />
              <el-option label="重复值" value="DUPLICATE" />
              <el-option label="无效值" value="INVALID" />
              <el-option label="超范围" value="OUT_OF_RANGE" />
            </el-select>
          </el-form-item>
          <el-form-item label="类别" class="portal-field-md">
            <el-select v-model="knowledgeQuery.category" clearable placeholder="全部">
              <el-option label="快速定位" value="LOCATE" />
              <el-option label="编码映射" value="CODE_MAP" />
              <el-option label="流程整改" value="PROCESS" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadKnowledge">查询</el-button>
            <el-button @click="resetKnowledgeQuery">重置</el-button>
            <el-button type="success" @click="openKnowledgeCreate">新增知识</el-button>
          </el-form-item>
        </el-form>

        <el-table
          v-loading="knowledgeLoading"
          :data="pagedKnowledge"
          stripe
          border
          size="small"
          class="portal-table"
        >
          <el-table-column prop="knowledgeCode" label="编码" width="140" />
          <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
          <el-table-column label="问题类型" width="100">
            <template #default="{ row }">{{ statusLabel(row.issueType) }}</template>
          </el-table-column>
          <el-table-column label="类别" width="100">
            <template #default="{ row }">{{ statusLabel(row.category) }}</template>
          </el-table-column>
          <el-table-column prop="symptom" label="症状" min-width="160" show-overflow-tooltip />
          <el-table-column prop="hitCount" label="命中" width="70" />
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openKnowledgeEdit(row)">编辑</el-button>
              <el-button link @click="hitKnowledge(row)">引用命中</el-button>
              <el-button link type="danger" @click="deleteKnowledge(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-model:page="knowledgePage"
          v-model:page-size="knowledgePageSize"
          :total="knowledgeTotal"
        />
        <el-empty v-if="!knowledgeLoading && !knowledgeList.length" description="暂无知识条目" />
      </div>
    </PageCard>

    <el-drawer
      v-model="detailVisible"
      :title="detail?.report?.reportName || '报告详情'"
      size="640px"
    >
      <div v-loading="detailLoading">
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="编码">{{ detail?.report?.reportCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail?.report?.reportName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="报告评分">{{ detail?.report?.score ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="运行均分">{{ detail?.avgRunScore ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="关联运行数">{{ detail?.runCount ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="维度">{{ detail?.report?.dimension || '—' }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">
            {{ formatDateTime(detail?.report?.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>
        <div class="qr-section-title" style="margin-top: 16px">关联运行</div>
        <el-table
          v-if="detail?.recentRuns?.length"
          :data="detail.recentRuns"
          stripe
          border
          size="small"
          class="portal-table"
        >
          <el-table-column prop="id" label="运行ID" width="80" />
          <el-table-column prop="score" label="评分" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="开始时间" min-width="160">
            <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无关联运行" />
      </div>
    </el-drawer>

    <el-drawer v-model="drillVisible" title="问题下钻" size="560px">
      <el-table :data="issues" stripe border size="small" class="portal-table">
        <el-table-column prop="targetTable" label="表" width="120" />
        <el-table-column prop="targetColumn" label="字段" width="100" />
        <el-table-column label="问题类型" width="100">
          <template #default="{ row }">{{ statusLabel(row.issueType) }}</template>
        </el-table-column>
        <el-table-column prop="issueValue" label="问题值" min-width="120" show-overflow-tooltip />
        <el-table-column prop="issueCount" label="数量" width="70" />
        <el-table-column label="级别" width="80">
          <template #default="{ row }">{{ statusLabel(row.severity) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!issues.length" description="无关联问题" />
    </el-drawer>

    <el-drawer v-model="impactDetailVisible" title="编码映射影响详情" size="560px">
      <template v-if="impactDetail?.impact">
        <el-descriptions :column="1" size="small" border>
          <el-descriptions-item label="编码">{{ impactDetail.impact.impactCode }}</el-descriptions-item>
          <el-descriptions-item label="标准项">
            {{ impactDetail.impact.standardName || '—' }}（{{ impactDetail.impact.standardCode || '—' }}）
          </el-descriptions-item>
          <el-descriptions-item label="源表字段">
            {{ impactDetail.impact.sourceTable }}.{{ impactDetail.impact.sourceColumn }}
          </el-descriptions-item>
          <el-descriptions-item label="映射状态">
            {{ statusLabel(impactDetail.impact.mappingStatus) }}
          </el-descriptions-item>
          <el-descriptions-item label="影响级别">
            {{ statusLabel(impactDetail.impact.impactLevel) }}
          </el-descriptions-item>
          <el-descriptions-item label="影响说明">{{ impactDetail.impact.impactDesc || '—' }}</el-descriptions-item>
        </el-descriptions>
        <div class="qr-section-title" style="margin-top: 16px">下游影响</div>
        <el-tag
          v-for="d in impactDetail.downstreamList || []"
          :key="d"
          class="qr-down-tag"
          size="small"
        >{{ d }}</el-tag>
        <el-empty v-if="!(impactDetail.downstreamList || []).length" description="暂无下游引用" />
        <div v-if="impactDetail.analysisCase" class="qr-section-title" style="margin-top: 16px">关联定位案例</div>
        <el-alert
          v-if="impactDetail.analysisCase"
          :closable="false"
          type="warning"
          :title="impactDetail.analysisCase.caseName"
          :description="impactDetail.analysisCase.rootCause || impactDetail.analysisCase.locateSummary"
        />
      </template>
    </el-drawer>

    <el-dialog
      v-model="knowledgeDialog"
      :title="knowledgeForm.id ? '编辑知识' : '新增知识'"
      width="640px"
      destroy-on-close
    >
      <el-form label-width="96px">
        <el-form-item label="标题" required>
          <el-input v-model="knowledgeForm.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="问题类型">
          <el-select v-model="knowledgeForm.issueType" clearable placeholder="请选择" style="width: 100%">
            <el-option label="空值" value="NULL_VALUE" />
            <el-option label="重复值" value="DUPLICATE" />
            <el-option label="无效值" value="INVALID" />
            <el-option label="超范围" value="OUT_OF_RANGE" />
            <el-option label="格式错误" value="FORMAT_ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="knowledgeForm.category" style="width: 100%">
            <el-option label="快速定位" value="LOCATE" />
            <el-option label="编码映射" value="CODE_MAP" />
            <el-option label="流程整改" value="PROCESS" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="症状">
          <el-input v-model="knowledgeForm.symptom" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="根因">
          <el-input v-model="knowledgeForm.rootCause" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="解决方案">
          <el-input v-model="knowledgeForm.solution" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="关联标准">
          <el-input v-model="knowledgeForm.relatedStandard" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="knowledgeDialog = false">取消</el-button>
        <el-button type="primary" :loading="knowledgeSaving" @click="saveKnowledge">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.qr-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.qr-hint {
  margin-bottom: 12px;
}
.qr-tabs {
  margin-bottom: 8px;
}
.qr-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}
.qr-kpis--analysis {
  margin-top: -6px;
}
.qr-kpi {
  padding: 14px 16px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}
.qr-kpi__label {
  font-size: 12px;
  color: #64748b;
}
.qr-kpi__value {
  margin-top: 6px;
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.1;
}
.qr-kpi__value--sm {
  font-size: 22px;
}
.qr-kpi--good .qr-kpi__value { color: #047857; }
.qr-kpi--warn .qr-kpi__value { color: #b45309; }
.qr-kpi--bad .qr-kpi__value { color: #b91c1c; }
.qr-section-title {
  margin: 8px 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.qr-chart {
  height: 280px;
  margin-bottom: 8px;
}
.qr-score {
  font-weight: 650;
}
.qr-score--good { color: #047857; }
.qr-score--warn { color: #b45309; }
.qr-score--bad { color: #b91c1c; }
.qr-case-detail {
  margin-top: 12px;
  margin-bottom: 8px;
}
.qr-down-tag {
  margin: 0 8px 8px 0;
}
@media (max-width: 960px) {
  .qr-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
