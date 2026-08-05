<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
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

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['报告均分', '运行均分', '运行数'] },
    grid: { left: 40, right: 40, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: trend.value.map((t) => t.date.slice(5)) },
    yAxis: [
      { type: 'value', name: '评分', min: 0, max: 100 },
      { type: 'value', name: '次数' },
    ],
    series: [
      { name: '报告均分', type: 'line', smooth: true, data: trend.value.map((t) => t.reportAvgScore) },
      { name: '运行均分', type: 'line', smooth: true, data: trend.value.map((t) => t.runAvgScore) },
      { name: '运行数', type: 'bar', yAxisIndex: 1, data: trend.value.map((t) => t.runCount) },
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
    await api.post('/governance/platform/quality/reports', { reportName: '六性质量报告', dimension: '完整性+准确性' })
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
  <div>
    <PageCard title="数据质量分析报告">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        title="分析报告服务问题定位与知识沉淀。面向目录发布请选用源层或资源层任务运行结果；过程层报告仅用于治理整改闭环。"
      />
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="generate">生成报告</el-button>
          <el-button @click="loadList()">刷新列表</el-button>
        </el-form-item>
      </el-form>

      <div ref="chartRef" style="height:260px;margin-bottom:16px" />

      <el-table v-loading="loading" :data="pagedReports" stripe size="small">
        <el-table-column prop="reportCode" label="编码" width="160" />
        <el-table-column prop="reportName" label="名称" min-width="140" />
        <el-table-column prop="dimension" label="维度" width="140" />
        <el-table-column prop="score" label="评分" width="80" />
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

      <el-divider>详情</el-divider>
      <template v-if="detail?.report || selectedId">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="报告评分">{{ detail?.report?.score ?? reports.find(r => r.id === selectedId)?.score ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="关联运行数">{{ detail?.runCount ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="运行均分">{{ detail?.avgRunScore ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="维度">{{ detail?.report?.dimension || reports.find(r => r.id === selectedId)?.dimension || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-table v-if="detail?.recentRuns?.length" :data="detail.recentRuns" stripe size="small" style="margin-top: 12px">
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
        <el-table-column prop="issueValue" label="样例" min-width="120" show-overflow-tooltip />
        <el-table-column prop="issueCount" label="数量" width="70" />
        <el-table-column label="级别" width="80">
          <template #default="{ row }">{{ $statusLabel(row.severity) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!issues.length" description="无关联问题" />
    </el-drawer>
  </div>
</template>
