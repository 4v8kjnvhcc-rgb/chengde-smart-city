<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

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
const trend = ref<TrendPoint[]>([])
const loading = ref(false)
const selectedId = ref<number | null>(null)
const detailText = ref('')
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

async function showDetail(row: ReportRow) {
  selectedId.value = row.id
  const res = await api.get(`/governance/quality/reports-mgmt/${row.id}`)
  detailText.value = JSON.stringify(res.data, null, 2)
}

async function openDrill(row: ReportRow) {
  selectedId.value = row.id
  const res = await api.get(`/governance/quality/reports-mgmt/${row.id}/drill`)
  issues.value = res.data?.issues || []
  detailText.value = JSON.stringify({ score: res.data?.score, runCount: (res.data?.runs || []).length, issueCount: res.data?.issueCount }, null, 2)
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
  await api.post('/governance/platform/quality/reports', { reportName: '六性质量报告', dimension: '完整性+准确性' })
  ElMessage.success('报告已生成')
  await loadList()
  await loadTrend()
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
    <PageCard title="质量报告">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="generate">生成报告</el-button>
          <el-button @click="loadList()">刷新列表</el-button>
        </el-form-item>
      </el-form>

      <div ref="chartRef" style="height:260px;margin-bottom:16px" />

      <el-table v-loading="loading" :data="reports" stripe size="small">
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

      <el-divider>详情</el-divider>
      <pre style="white-space:pre-wrap;font-size:12px;max-height:220px;overflow:auto;background:#f8f8f8;padding:8px;border-radius:4px">{{ detailText || '点击「详情」查看' }}</pre>
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
    </el-drawer>
  </div>
</template>
