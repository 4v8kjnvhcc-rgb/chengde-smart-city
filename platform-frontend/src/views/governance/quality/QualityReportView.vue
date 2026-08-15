<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'

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

async function showDetail(row: ReportRow) {
  selectedId.value = row.id
  const res = await api.get(`/governance/quality/reports-mgmt/${row.id}`)
  detail.value = { ...(res.data || {}), report: res.data?.report || row }
}

async function openDrill(row: ReportRow) {
  selectedId.value = row.id
  const res = await api.get(`/governance/quality/reports-mgmt/${row.id}/drill`)
  issues.value = res.data?.issues || []
  detail.value = {
    report: row,
    avgRunScore: res.data?.score,
    runCount: (res.data?.runs || []).length,
  }
  drillVisible.value = true
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

function onResize() {
  chart?.resize()
}

onMounted(async () => {
  await loadList()
  await loadTrend()
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

      <div class="qr-section-title">质量趋势（近 14 日）</div>
      <div ref="chartRef" class="qr-chart" />

      <div class="qr-section-title">报告清单</div>
      <el-table v-loading="loading" :data="pagedReports" stripe size="small" highlight-current-row>
        <el-table-column prop="reportCode" label="编码" width="160" />
        <el-table-column prop="reportName" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="dimension" label="维度" min-width="180" show-overflow-tooltip />
        <el-table-column label="评分" width="100">
          <template #default="{ row }">
            <span class="qr-score" :class="`qr-score--${scoreTone(row.score)}`">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
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

      <div class="qr-section-title">报告详情</div>
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
          size="small"
          style="margin-top: 12px"
        >
          <el-table-column prop="id" label="运行ID" width="80" />
          <el-table-column prop="score" label="评分" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="startedAt" label="开始时间" min-width="160" />
        </el-table>
      </template>
      <el-empty v-else description="点击「详情」查看结构化摘要" />
    </PageCard>

    <el-drawer v-model="drillVisible" title="问题下钻" size="560px">
      <el-table :data="issues" stripe size="small">
        <el-table-column prop="targetTable" label="表" width="120" />
        <el-table-column prop="targetColumn" label="字段" width="100" />
        <el-table-column label="问题类型" width="100">
          <template #default="{ row }">{{ $statusLabel(row.issueType) }}</template>
        </el-table-column>
        <el-table-column prop="issueValue" label="问题值" min-width="120" show-overflow-tooltip />
        <el-table-column prop="issueCount" label="数量" width="70" />
        <el-table-column label="级别" width="80">
          <template #default="{ row }">{{ $statusLabel(row.severity) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!issues.length" description="无关联问题" />
    </el-drawer>
  </div>
</template>

<style scoped>
.qr-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.qr-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
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
@media (max-width: 960px) {
  .qr-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
