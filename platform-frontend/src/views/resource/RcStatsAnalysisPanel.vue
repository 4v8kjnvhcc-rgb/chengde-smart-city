<script setup lang="ts">
/**
 * 大数据平台资源中心 · 数据库统计分析。
 * 三段能力：汇总聚合与描述统计 → 深入分析（趋势回归 / 集中度 / 离群） → 决策支持建议。
 * 数据全部来自资源中心纳管台账与策略执行日志，未刷新过容量的表会如实显示为 0。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface GroupRow {
  code: string
  tableCount: number
  recordCount: number
  bytes: number
  share: number
}
interface DescriptiveRow {
  metricKey: string
  metricName: string
  unit: string
  count: number
  sum: number
  avg: number
  median: number
  p90: number
  max: number
  min: number
  stdDev: number
  cv: number
  maxObject?: string
  minObject?: string
}
interface TableRow {
  physicalTable: string
  themeName?: string
  libName?: string
  libType?: string
  assetType?: string
  ownerOrg?: string
  recordCount: number
  bytes: number
  indexRatio: number
  share: number
  updatedAt?: string
}
interface CodeCount {
  code: string
  count: number
}
interface Summary {
  generatedAt: string
  scopeHint: string
  overview: Record<string, number>
  descriptive: DescriptiveRow[]
  byLibType: GroupRow[]
  byAssetType: GroupRow[]
  byTheme: GroupRow[]
  byOwnerOrg: GroupRow[]
  topTables: TableRow[]
  bottomTables: TableRow[]
  catalogPublish: CodeCount[]
  policyAction: CodeCount[]
}
interface TrendPoint {
  period: string
  newTables: number
  newRecords: number
  newBytes: number
  cumulativeTables: number
  cumulativeRecords: number
  cumulativeBytes: number
}
interface Forecast {
  metricKey: string
  metricName: string
  available: boolean
  slopePerMonth?: number
  r2?: number
  reliability?: string
  monthlyGrowthRate?: number
  forecast?: { period: string; value: number }[]
  hint?: string
}
interface Concentration {
  available: boolean
  tableCount: number
  totalRecords: number
  top1Share: number
  top3Share: number
  top5Share: number
  topTable?: string
  hhi: number
  gini: number
  level: string
  hint: string
}
interface OutlierRow {
  physicalTable: string
  themeName?: string
  recordCount: number
  bytes: number
  indexRatio: number
  zScore: number
  level: string
  reason: string
}
interface JobPoint {
  period: string
  runCount: number
  successCount: number
  ledgerCount: number
  rowCount: number
}
interface Analysis {
  generatedAt: string
  months: number
  trend: TrendPoint[]
  recordForecast: Forecast
  tableForecast: Forecast
  concentration: Concentration
  outliers: OutlierRow[]
  jobTrend: JobPoint[]
  methodNotes: string[]
}
interface Recommendation {
  code: string
  title: string
  level: string
  metric: string
  basis: string
  action: string
  link?: string
  objects: string[]
}
interface Decisions {
  generatedAt: string
  healthScore: number
  healthLevel: string
  summaryText: string
  coverage: Record<string, number>
  recommendations: Recommendation[]
}

const router = useRouter()
const activeTab = ref<'summary' | 'analysis' | 'decisions'>('summary')
const loading = ref(false)

const summary = ref<Summary | null>(null)
const analysis = ref<Analysis | null>(null)
const decisions = ref<Decisions | null>(null)

const filterLibType = ref('')
const filterAssetType = ref('')
const trendMonths = ref(12)
const topView = ref<'top' | 'bottom'>('top')

const distChartRef = ref<HTMLDivElement | null>(null)
const assetChartRef = ref<HTMLDivElement | null>(null)
const trendChartRef = ref<HTMLDivElement | null>(null)
const jobChartRef = ref<HTMLDivElement | null>(null)
let distChart: echarts.ECharts | null = null
let assetChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null
let jobChart: echarts.ECharts | null = null

const overview = computed(() => summary.value?.overview || {})
const currentTables = computed(() =>
  topView.value === 'top' ? summary.value?.topTables || [] : summary.value?.bottomTables || [],
)

function formatNumber(value: unknown): string {
  const n = Number(value ?? 0)
  if (!Number.isFinite(n)) return '—'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function formatBytes(value: unknown): string {
  const n = Number(value ?? 0)
  if (!Number.isFinite(n) || n <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let idx = 0
  let v = n
  while (v >= 1024 && idx < units.length - 1) {
    v /= 1024
    idx += 1
  }
  return `${v.toFixed(idx === 0 ? 0 : 2)} ${units[idx]}`
}

function metricValue(row: DescriptiveRow, key: keyof DescriptiveRow): string {
  const raw = row[key]
  if (row.unit === '字节') return formatBytes(raw)
  return formatNumber(raw)
}

async function loadSummary() {
  loading.value = true
  try {
    summary.value = (
      await api.get('/resource-center/platform/statistics/summary', {
        params: {
          libType: filterLibType.value || undefined,
          assetType: filterAssetType.value || undefined,
        },
      })
    ).data
    await nextTick()
    renderSummaryCharts()
  } catch {
    ElMessage.error('加载统计汇总失败')
  } finally {
    loading.value = false
  }
}

async function loadAnalysis() {
  loading.value = true
  try {
    analysis.value = (
      await api.get('/resource-center/platform/statistics/analysis', {
        params: { months: trendMonths.value },
      })
    ).data
    await nextTick()
    renderAnalysisCharts()
  } catch {
    ElMessage.error('加载深入分析失败')
  } finally {
    loading.value = false
  }
}

async function loadDecisions() {
  loading.value = true
  try {
    decisions.value = (await api.get('/resource-center/platform/statistics/decisions')).data
  } catch {
    ElMessage.error('加载决策建议失败')
  } finally {
    loading.value = false
  }
}

function renderSummaryCharts() {
  const libRows = summary.value?.byLibType || []
  if (distChartRef.value) {
    if (!distChart) distChart = echarts.init(distChartRef.value)
    distChart.setOption({
      tooltip: {
        trigger: 'item',
        formatter: (p: { name: string; value: number; percent: number }) =>
          `${p.name}<br/>记录数 ${formatNumber(p.value)}（${p.percent}%）`,
      },
      legend: { bottom: 0 },
      series: [
        {
          name: '记录量分布',
          type: 'pie',
          radius: ['40%', '68%'],
          center: ['50%', '45%'],
          label: { formatter: '{b} {d}%' },
          data: libRows.map((r) => ({ name: statusLabel(r.code), value: r.recordCount })),
        },
      ],
    })
  }
  const assetRows = summary.value?.byAssetType || []
  if (assetChartRef.value) {
    if (!assetChart) assetChart = echarts.init(assetChartRef.value)
    assetChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: ['纳管表数', '占用容量(MB)'] },
      grid: { left: 50, right: 50, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: assetRows.map((r) => statusLabel(r.code)) },
      yAxis: [
        { type: 'value', name: '表数' },
        { type: 'value', name: 'MB' },
      ],
      series: [
        { name: '纳管表数', type: 'bar', barMaxWidth: 48, data: assetRows.map((r) => r.tableCount) },
        {
          name: '占用容量(MB)',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          data: assetRows.map((r) => Number((r.bytes / 1024 / 1024).toFixed(2))),
        },
      ],
    })
  }
}

function renderAnalysisCharts() {
  const trend = analysis.value?.trend || []
  const forecast = analysis.value?.recordForecast
  if (trendChartRef.value) {
    if (!trendChart) trendChart = echarts.init(trendChartRef.value)
    const periods = trend.map((t) => t.period)
    const forecastPoints = forecast?.available ? forecast.forecast || [] : []
    const axis = periods.concat(forecastPoints.map((f) => f.period))
    const actual = trend.map((t) => t.cumulativeRecords)
    const predicted: (number | null)[] = periods.map((_, i) =>
      i === periods.length - 1 ? actual[i] : null,
    )
    forecastPoints.forEach((f) => predicted.push(f.value))
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['累计记录数', '预测累计记录数', '新增纳管表'] },
      grid: { left: 60, right: 50, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: axis },
      yAxis: [
        { type: 'value', name: '记录数' },
        { type: 'value', name: '新增表' },
      ],
      series: [
        { name: '累计记录数', type: 'line', smooth: true, data: actual },
        {
          name: '预测累计记录数',
          type: 'line',
          smooth: true,
          lineStyle: { type: 'dashed' },
          data: predicted,
        },
        {
          name: '新增纳管表',
          type: 'bar',
          yAxisIndex: 1,
          barMaxWidth: 28,
          data: trend.map((t) => t.newTables),
        },
      ],
    })
  }
  const jobs = analysis.value?.jobTrend || []
  if (jobChartRef.value) {
    if (!jobChart) jobChart = echarts.init(jobChartRef.value)
    jobChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: ['执行次数', '成功', '台账处理'] },
      grid: { left: 50, right: 30, top: 40, bottom: 30 },
      xAxis: { type: 'category', data: jobs.map((j) => j.period) },
      yAxis: { type: 'value', name: '次数' },
      series: [
        { name: '执行次数', type: 'bar', barMaxWidth: 28, data: jobs.map((j) => j.runCount) },
        { name: '成功', type: 'bar', barMaxWidth: 28, data: jobs.map((j) => j.successCount) },
        { name: '台账处理', type: 'bar', barMaxWidth: 28, data: jobs.map((j) => j.ledgerCount) },
      ],
    })
  }
}

async function onTabChange() {
  if (activeTab.value === 'analysis') {
    if (!analysis.value) await loadAnalysis()
    else {
      await nextTick()
      renderAnalysisCharts()
    }
  } else if (activeTab.value === 'decisions') {
    if (!decisions.value) await loadDecisions()
  } else {
    await nextTick()
    renderSummaryCharts()
  }
}

function goto(link?: string) {
  if (!link) return
  const [path, query] = link.split('?')
  const params: Record<string, string> = {}
  for (const kv of (query || '').split('&')) {
    if (!kv) continue
    const [k, v] = kv.split('=')
    params[k] = v
  }
  router.push({ path, query: params })
}

function exportReport() {
  if (!summary.value) {
    ElMessage.warning('请先加载汇总统计')
    return
  }
  const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`
  const lines: string[] = []
  lines.push(esc('承德大数据平台资源中心 · 数据库统计分析报表'))
  lines.push(esc(`生成时间：${summary.value.generatedAt}`))
  lines.push(esc(summary.value.scopeHint))
  lines.push('')
  lines.push(esc('一、总体指标'))
  lines.push(['指标', '值'].map(esc).join(','))
  const overviewLabels: Record<string, string> = {
    libraryCount: '库数量',
    themeCount: '主题/专题库数',
    managedTableCount: '纳管表数',
    totalRecords: '纳管表总记录数',
    totalBytes: '纳管表占用容量(字节)',
    indexBytes: '索引占用容量(字节)',
    libraryRegisteredRecords: '库登记记录数',
    catalogEntryCount: '资产目录条目',
    publishedCatalogCount: '已发布目录',
    policyCount: '存储策略数',
    partitionDefCount: '分区定义数',
    emptyTableCount: '零记录表数',
  }
  for (const [k, label] of Object.entries(overviewLabels)) {
    lines.push([label, summary.value.overview[k] ?? 0].map(esc).join(','))
  }
  lines.push('')
  lines.push(esc('二、描述性统计'))
  lines.push(['指标', '单位', '样本数', '合计', '平均', '中位数', 'P90', '最大', '最大对象', '最小', '标准差', '变异系数%'].map(esc).join(','))
  for (const d of summary.value.descriptive) {
    lines.push([d.metricName, d.unit, d.count, d.sum, d.avg, d.median, d.p90, d.max, d.maxObject, d.min, d.stdDev, d.cv].map(esc).join(','))
  }
  lines.push('')
  lines.push(esc('三、库类型分布'))
  lines.push(['库类型', '纳管表数', '记录数', '容量(字节)', '记录占比%'].map(esc).join(','))
  for (const g of summary.value.byLibType) {
    lines.push([statusLabel(g.code), g.tableCount, g.recordCount, g.bytes, g.share].map(esc).join(','))
  }
  lines.push('')
  lines.push(esc('四、记录量 Top 表'))
  lines.push(['物理表', '所属主题', '责任单位', '记录数', '容量(字节)', '索引占比%', '记录占比%', '统计时间'].map(esc).join(','))
  for (const t of summary.value.topTables) {
    lines.push([t.physicalTable, t.themeName, t.ownerOrg, t.recordCount, t.bytes, t.indexRatio, t.share, t.updatedAt].map(esc).join(','))
  }
  if (decisions.value) {
    lines.push('')
    lines.push(esc(`五、决策支持建议（健康分 ${decisions.value.healthScore}）`))
    lines.push(['优先级', '事项', '规模', '判定依据', '建议动作', '示例对象'].map(esc).join(','))
    for (const r of decisions.value.recommendations) {
      lines.push([statusLabel(r.level), r.title, r.metric, r.basis, r.action, r.objects.join(' / ')].map(esc).join(','))
    }
  }
  const blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `资源中心统计分析_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('统计分析报表已导出')
}

function onResize() {
  distChart?.resize()
  assetChart?.resize()
  trendChart?.resize()
  jobChart?.resize()
}

onMounted(async () => {
  await loadSummary()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  distChart?.dispose()
  assetChart?.dispose()
  trendChart?.dispose()
  jobChart?.dispose()
})
</script>

<template>
  <div v-loading="loading">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 一、汇总聚合与描述性统计 -->
      <el-tab-pane label="汇总统计" name="summary">
        <el-alert
          v-if="summary"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
          :title="summary.scopeHint"
        >
          统计时间：{{ summary.generatedAt }}
        </el-alert>
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="库类型" class="portal-field-md">
            <el-select v-model="filterLibType" clearable placeholder="全部">
              <el-option :label="statusLabel('BASE')" value="BASE" />
              <el-option :label="statusLabel('SEMI')" value="SEMI" />
              <el-option :label="statusLabel('UNSTRUCT')" value="UNSTRUCT" />
              <el-option :label="statusLabel('TOPIC')" value="TOPIC" />
            </el-select>
          </el-form-item>
          <el-form-item label="资产类型" class="portal-field-md">
            <el-select v-model="filterAssetType" clearable placeholder="全部">
              <el-option :label="statusLabel('BASE')" value="BASE" />
              <el-option :label="statusLabel('SEMI')" value="SEMI" />
              <el-option :label="statusLabel('UNSTRUCT')" value="UNSTRUCT" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadSummary">统计</el-button>
            <el-button @click="exportReport">导出报表</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="12" style="margin-bottom: 12px">
          <el-col :xs="12" :sm="8" :md="4">
            <PageCard title="纳管表数">
              <div class="stat-num">{{ formatNumber(overview.managedTableCount) }}</div>
              <div class="stat-sub">零记录 {{ formatNumber(overview.emptyTableCount) }} 张</div>
            </PageCard>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <PageCard title="总记录数">
              <div class="stat-num">{{ formatNumber(overview.totalRecords) }}</div>
              <div class="stat-sub">库登记 {{ formatNumber(overview.libraryRegisteredRecords) }}</div>
            </PageCard>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <PageCard title="占用容量">
              <div class="stat-num">{{ formatBytes(overview.totalBytes) }}</div>
              <div class="stat-sub">索引 {{ formatBytes(overview.indexBytes) }}</div>
            </PageCard>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <PageCard title="库 / 主题">
              <div class="stat-num">{{ formatNumber(overview.libraryCount) }} / {{ formatNumber(overview.themeCount) }}</div>
              <div class="stat-sub">基础·半结构·非结构 / 主题专题</div>
            </PageCard>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <PageCard title="资产目录">
              <div class="stat-num">{{ formatNumber(overview.publishedCatalogCount) }}</div>
              <div class="stat-sub">已发布 / 共 {{ formatNumber(overview.catalogEntryCount) }} 条</div>
            </PageCard>
          </el-col>
          <el-col :xs="12" :sm="8" :md="4">
            <PageCard title="策略 / 分区">
              <div class="stat-num">{{ formatNumber(overview.policyCount) }} / {{ formatNumber(overview.partitionDefCount) }}</div>
              <div class="stat-sub">存储策略 / 分区定义</div>
            </PageCard>
          </el-col>
        </el-row>

        <el-row :gutter="12" style="margin-bottom: 12px">
          <el-col :xs="24" :md="10">
            <PageCard title="库类型记录量分布">
              <div ref="distChartRef" class="chart" />
            </PageCard>
          </el-col>
          <el-col :xs="24" :md="14">
            <PageCard title="资产类型表数与容量">
              <div ref="assetChartRef" class="chart" />
            </PageCard>
          </el-col>
        </el-row>

        <PageCard title="描述性统计（总数 / 平均 / 中位 / 极值 / 离散度）" style="margin-bottom: 12px">
          <el-table :data="summary?.descriptive || []" stripe size="small">
            <el-table-column prop="metricName" label="统计指标" min-width="130" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column prop="count" label="样本数" width="80" />
            <el-table-column label="合计" min-width="110">
              <template #default="{ row }">{{ metricValue(row, 'sum') }}</template>
            </el-table-column>
            <el-table-column label="平均值" min-width="110">
              <template #default="{ row }">{{ metricValue(row, 'avg') }}</template>
            </el-table-column>
            <el-table-column label="中位数" min-width="110">
              <template #default="{ row }">{{ metricValue(row, 'median') }}</template>
            </el-table-column>
            <el-table-column label="P90" min-width="110">
              <template #default="{ row }">{{ metricValue(row, 'p90') }}</template>
            </el-table-column>
            <el-table-column label="最大值" min-width="150">
              <template #default="{ row }">
                {{ metricValue(row, 'max') }}
                <div class="stat-sub">{{ row.maxObject || '—' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="最小值" min-width="150">
              <template #default="{ row }">
                {{ metricValue(row, 'min') }}
                <div class="stat-sub">{{ row.minObject || '—' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="标准差" min-width="110">
              <template #default="{ row }">{{ metricValue(row, 'stdDev') }}</template>
            </el-table-column>
            <el-table-column label="变异系数" width="100">
              <template #default="{ row }">{{ formatNumber(row.cv) }}%</template>
            </el-table-column>
          </el-table>
        </PageCard>

        <PageCard style="margin-bottom: 12px">
          <template #header>
            <div class="card-title-row">
              <span>{{ topView === 'top' ? '记录量 Top 10 纳管表' : '记录量最少的 10 张纳管表' }}</span>
              <el-radio-group v-model="topView" size="small">
                <el-radio-button value="top">头部</el-radio-button>
                <el-radio-button value="bottom">尾部</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <el-table :data="currentTables" stripe size="small">
            <el-table-column prop="physicalTable" label="物理表" min-width="170" show-overflow-tooltip />
            <el-table-column prop="themeName" label="所属主题" min-width="130" show-overflow-tooltip />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ statusLabel(row.libType) }}</template>
            </el-table-column>
            <el-table-column prop="ownerOrg" label="责任单位" min-width="130" show-overflow-tooltip />
            <el-table-column label="记录数" width="120">
              <template #default="{ row }">{{ formatNumber(row.recordCount) }}</template>
            </el-table-column>
            <el-table-column label="占用容量" width="110">
              <template #default="{ row }">{{ formatBytes(row.bytes) }}</template>
            </el-table-column>
            <el-table-column label="索引占比" width="100">
              <template #default="{ row }">{{ formatNumber(row.indexRatio) }}%</template>
            </el-table-column>
            <el-table-column label="记录占比" min-width="150">
              <template #default="{ row }">
                <el-progress :percentage="Math.min(100, Number(row.share || 0))" :stroke-width="10" />
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="统计时间" width="170" />
          </el-table>
          <el-empty v-if="!currentTables.length" description="暂无纳管表，请先在「数据资产区」纳管已登记元数据" />
        </PageCard>

        <el-row :gutter="12">
          <el-col :xs="24" :md="12">
            <PageCard title="主题库记录量排行">
              <el-table :data="summary?.byTheme || []" stripe size="small">
                <el-table-column prop="code" label="主题库" min-width="150" show-overflow-tooltip />
                <el-table-column prop="tableCount" label="表数" width="70" />
                <el-table-column label="记录数" min-width="110">
                  <template #default="{ row }">{{ formatNumber(row.recordCount) }}</template>
                </el-table-column>
                <el-table-column label="容量" width="100">
                  <template #default="{ row }">{{ formatBytes(row.bytes) }}</template>
                </el-table-column>
                <el-table-column label="占比" width="90">
                  <template #default="{ row }">{{ formatNumber(row.share) }}%</template>
                </el-table-column>
              </el-table>
            </PageCard>
          </el-col>
          <el-col :xs="24" :md="12">
            <PageCard title="责任单位资产量排行">
              <el-table :data="summary?.byOwnerOrg || []" stripe size="small">
                <el-table-column prop="code" label="责任单位" min-width="150" show-overflow-tooltip />
                <el-table-column prop="tableCount" label="表数" width="70" />
                <el-table-column label="记录数" min-width="110">
                  <template #default="{ row }">{{ formatNumber(row.recordCount) }}</template>
                </el-table-column>
                <el-table-column label="容量" width="100">
                  <template #default="{ row }">{{ formatBytes(row.bytes) }}</template>
                </el-table-column>
                <el-table-column label="占比" width="90">
                  <template #default="{ row }">{{ formatNumber(row.share) }}%</template>
                </el-table-column>
              </el-table>
            </PageCard>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 二、深入分析 -->
      <el-tab-pane label="趋势与规律分析" name="analysis">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="分析窗口" class="portal-field-md">
            <el-select v-model="trendMonths">
              <el-option label="近 6 个月" :value="6" />
              <el-option label="近 12 个月" :value="12" />
              <el-option label="近 24 个月" :value="24" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadAnalysis">重新分析</el-button>
          </el-form-item>
        </el-form>

        <PageCard title="资产入池趋势与线性回归预测" style="margin-bottom: 12px">
          <div ref="trendChartRef" class="chart chart-lg" />
        </PageCard>

        <el-row :gutter="12" style="margin-bottom: 12px">
          <el-col :xs="24" :md="8">
            <PageCard title="记录量增长模型">
              <template v-if="analysis?.recordForecast?.available">
                <div class="stat-num">{{ formatNumber(analysis.recordForecast.slopePerMonth) }}</div>
                <div class="stat-sub">月均增量（回归斜率）</div>
                <el-descriptions :column="1" border size="small" style="margin-top: 10px">
                  <el-descriptions-item label="拟合优度 R²">
                    {{ formatNumber(analysis.recordForecast.r2) }}
                    <el-tag size="small" :type="statusTagType(analysis.recordForecast.reliability)">
                      {{ statusLabel(analysis.recordForecast.reliability) }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="月均增长率">
                    {{ formatNumber(analysis.recordForecast.monthlyGrowthRate) }}%
                  </el-descriptions-item>
                  <el-descriptions-item
                    v-for="f in analysis.recordForecast.forecast || []"
                    :key="f.period"
                    :label="`${f.period} 预测`"
                  >
                    {{ formatNumber(f.value) }}
                  </el-descriptions-item>
                </el-descriptions>
                <div class="stat-sub" style="margin-top: 8px">{{ analysis.recordForecast.hint }}</div>
              </template>
              <el-empty v-else :description="analysis?.recordForecast?.hint || '样本不足，暂不预测'" />
            </PageCard>
          </el-col>
          <el-col :xs="24" :md="8">
            <PageCard title="纳管表数增长模型">
              <template v-if="analysis?.tableForecast?.available">
                <div class="stat-num">{{ formatNumber(analysis.tableForecast.slopePerMonth) }}</div>
                <div class="stat-sub">月均新增表（回归斜率）</div>
                <el-descriptions :column="1" border size="small" style="margin-top: 10px">
                  <el-descriptions-item label="拟合优度 R²">
                    {{ formatNumber(analysis.tableForecast.r2) }}
                    <el-tag size="small" :type="statusTagType(analysis.tableForecast.reliability)">
                      {{ statusLabel(analysis.tableForecast.reliability) }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item
                    v-for="f in analysis.tableForecast.forecast || []"
                    :key="f.period"
                    :label="`${f.period} 预测`"
                  >
                    {{ formatNumber(f.value) }}
                  </el-descriptions-item>
                </el-descriptions>
              </template>
              <el-empty v-else :description="analysis?.tableForecast?.hint || '样本不足，暂不预测'" />
            </PageCard>
          </el-col>
          <el-col :xs="24" :md="8">
            <PageCard title="数据分布集中度">
              <template v-if="analysis?.concentration?.available">
                <div class="stat-num">{{ formatNumber(analysis.concentration.hhi) }}</div>
                <div class="stat-sub">赫芬达尔指数 HHI（&gt;2500 高度集中）</div>
                <el-descriptions :column="1" border size="small" style="margin-top: 10px">
                  <el-descriptions-item label="集中度判定">
                    <el-tag size="small" :type="statusTagType(analysis.concentration.level)">
                      {{ statusLabel(analysis.concentration.level) }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="基尼系数">{{ formatNumber(analysis.concentration.gini) }}</el-descriptions-item>
                  <el-descriptions-item label="首位表占比">
                    {{ formatNumber(analysis.concentration.top1Share) }}%
                    <span class="stat-sub">{{ analysis.concentration.topTable }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="前三 / 前五占比">
                    {{ formatNumber(analysis.concentration.top3Share) }}% / {{ formatNumber(analysis.concentration.top5Share) }}%
                  </el-descriptions-item>
                </el-descriptions>
                <div class="stat-sub" style="margin-top: 8px">{{ analysis.concentration.hint }}</div>
              </template>
              <el-empty v-else :description="analysis?.concentration?.hint || '暂无记录量数据'" />
            </PageCard>
          </el-col>
        </el-row>

        <PageCard title="离群与异常对象（均值+3σ / Q3+1.5×IQR 双判据）" style="margin-bottom: 12px">
          <el-table :data="analysis?.outliers || []" stripe size="small">
            <el-table-column prop="physicalTable" label="物理表" min-width="170" show-overflow-tooltip />
            <el-table-column prop="themeName" label="所属主题" min-width="130" show-overflow-tooltip />
            <el-table-column label="记录数" width="120">
              <template #default="{ row }">{{ formatNumber(row.recordCount) }}</template>
            </el-table-column>
            <el-table-column label="占用容量" width="110">
              <template #default="{ row }">{{ formatBytes(row.bytes) }}</template>
            </el-table-column>
            <el-table-column label="索引占比" width="100">
              <template #default="{ row }">{{ formatNumber(row.indexRatio) }}%</template>
            </el-table-column>
            <el-table-column label="Z 分数" width="100">
              <template #default="{ row }">{{ formatNumber(row.zScore) }}</template>
            </el-table-column>
            <el-table-column label="级别" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.level)">{{ statusLabel(row.level) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="判定依据" min-width="200" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!(analysis?.outliers || []).length" description="未发现离群对象，记录量分布正常" />
        </PageCard>

        <PageCard title="存储策略执行趋势" style="margin-bottom: 12px">
          <div ref="jobChartRef" class="chart" />
        </PageCard>

        <PageCard title="分析方法说明">
          <ul class="method-list">
            <li v-for="(note, i) in analysis?.methodNotes || []" :key="i">{{ note }}</li>
          </ul>
        </PageCard>
      </el-tab-pane>

      <!-- 三、决策支持 -->
      <el-tab-pane label="决策支持" name="decisions">
        <template v-if="decisions">
          <el-row :gutter="12" style="margin-bottom: 12px">
            <el-col :xs="24" :md="6">
              <PageCard title="资产健康分">
                <div class="stat-num">
                  {{ decisions.healthScore }}
                  <el-tag size="small" :type="statusTagType(decisions.healthLevel)">
                    {{ statusLabel(decisions.healthLevel) }}
                  </el-tag>
                </div>
                <div class="stat-sub">{{ decisions.summaryText }}</div>
                <div class="stat-sub">生成时间：{{ decisions.generatedAt }}</div>
              </PageCard>
            </el-col>
            <el-col :xs="12" :md="4">
              <PageCard title="编目覆盖率">
                <div class="stat-num">{{ formatNumber(decisions.coverage.catalogCoverage) }}%</div>
                <div class="stat-sub">已编目纳管表占比</div>
              </PageCard>
            </el-col>
            <el-col :xs="12" :md="4">
              <PageCard title="策略覆盖率">
                <div class="stat-num">{{ formatNumber(decisions.coverage.policyCoverage) }}%</div>
                <div class="stat-sub">大表已配备份/归档</div>
              </PageCard>
            </el-col>
            <el-col :xs="12" :md="4">
              <PageCard title="分区覆盖率">
                <div class="stat-num">{{ formatNumber(decisions.coverage.partitionCoverage) }}%</div>
                <div class="stat-sub">大表已建分区</div>
              </PageCard>
            </el-col>
            <el-col :xs="12" :md="6">
              <PageCard title="统计新鲜度">
                <div class="stat-num">{{ formatNumber(decisions.coverage.statFreshness) }}%</div>
                <div class="stat-sub">30 天内刷新过容量的表占比</div>
              </PageCard>
            </el-col>
          </el-row>

          <PageCard title="待办建议与处置入口">
            <el-table :data="decisions.recommendations" stripe size="small">
              <el-table-column label="优先级" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="statusTagType(row.level)">{{ statusLabel(row.level) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="事项" min-width="170" />
              <el-table-column prop="metric" label="规模" min-width="150" />
              <el-table-column prop="basis" label="判定依据" min-width="260" show-overflow-tooltip />
              <el-table-column label="示例对象" min-width="180">
                <template #default="{ row }">
                  <span v-if="!row.objects?.length">—</span>
                  <el-tag v-for="o in row.objects" :key="o" size="small" style="margin-right: 4px">{{ o }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="建议动作" min-width="230">
                <template #default="{ row }">
                  <div>{{ row.action }}</div>
                  <el-button v-if="row.link" link type="primary" @click="goto(row.link)">前往处理</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="!decisions.recommendations.length"
              description="未发现待办事项：分区、备份、编目与统计新鲜度四项检查均通过"
            />
          </PageCard>
        </template>
        <el-empty v-else description="加载中" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.stat-num {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.3;
}
.stat-sub {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.chart {
  height: 280px;
  width: 100%;
}
.chart-lg {
  height: 340px;
}
.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.method-list {
  margin: 0;
  padding-left: 18px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.9;
}
</style>
