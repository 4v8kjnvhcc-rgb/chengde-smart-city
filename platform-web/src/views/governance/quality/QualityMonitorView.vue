<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface TrendPoint {
  date: string
  runCount: number
  issueCount: number
  avgScore: number | null
}

interface RunRow {
  id: number
  taskId: number
  status: string
  startedAt?: string
  endedAt?: string
  score?: number
  totalChecks?: number
  issueCount?: number
  message?: string
  triggeredBy?: string
}

interface IssueRow {
  id: number
  checkType?: string
  targetTable?: string
  targetColumn?: string
  issueType?: string
  issueValue?: string
  issueCount?: number
  sampleData?: string
  severity?: string
  status?: string
}

interface Stats {
  taskTotal: number
  taskReady: number
  taskRunning: number
  runToday: number
  successToday: number
  failToday: number
  issueToday: number
  avgScore: number
  trend: TrendPoint[]
  recentRuns: RunRow[]
}

const stats = ref<Stats | null>(null)
const runs = ref<RunRow[]>([])
const issues = ref<IssueRow[]>([])
const selectedRunId = ref<number | null>(null)
const loading = ref(false)
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const trend = stats.value?.trend || []
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['平均分', '运行数', '问题数'] },
    grid: { left: 40, right: 40, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: trend.map((t) => t.date.slice(5)) },
    yAxis: [
      { type: 'value', name: '评分', min: 0, max: 100 },
      { type: 'value', name: '次数' },
    ],
    series: [
      {
        name: '平均分',
        type: 'line',
        smooth: true,
        data: trend.map((t) => t.avgScore),
      },
      {
        name: '运行数',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: trend.map((t) => t.runCount),
      },
      {
        name: '问题数',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: trend.map((t) => t.issueCount),
      },
    ],
  })
}

async function load() {
  loading.value = true
  try {
    const [s, r] = await Promise.all([
      api.get('/governance/quality/task-mgmt/stats'),
      api.get('/governance/quality/task-mgmt/runs'),
    ])
    stats.value = s.data
    runs.value = r.data || stats.value?.recentRuns || []
    await nextTick()
    renderChart()
  } catch {
    ElMessage.error('加载监控数据失败')
  } finally {
    loading.value = false
  }
}

async function openIssues(runId: number) {
  selectedRunId.value = runId
  issues.value = (await api.get(`/governance/quality/task-mgmt/runs/${runId}/issues`)).data || []
}

async function rerun(runId: number) {
  const res = await api.post(`/governance/quality/task-mgmt/runs/${runId}/rerun`)
  ElMessage.success(`重跑完成 · 评分 ${res.data.score}`)
  selectedRunId.value = null
  issues.value = []
  await load()
}

function onResize() {
  chart?.resize()
}

onMounted(async () => {
  await load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<template>
  <div v-loading="loading">
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :xs="12" :sm="6">
        <PageCard title="任务总数">
          <div class="stat-num">{{ stats?.taskTotal ?? '—' }}</div>
        </PageCard>
      </el-col>
      <el-col :xs="12" :sm="6">
        <PageCard title="今日运行">
          <div class="stat-num">{{ stats?.runToday ?? '—' }}</div>
          <div class="stat-sub">成功 {{ stats?.successToday ?? 0 }} · 失败 {{ stats?.failToday ?? 0 }}</div>
        </PageCard>
      </el-col>
      <el-col :xs="12" :sm="6">
        <PageCard title="今日问题">
          <div class="stat-num">{{ stats?.issueToday ?? '—' }}</div>
        </PageCard>
      </el-col>
      <el-col :xs="12" :sm="6">
        <PageCard title="近均评分">
          <div class="stat-num">{{ stats?.avgScore ?? '—' }}</div>
        </PageCard>
      </el-col>
    </el-row>

    <PageCard title="近 7 日质量趋势" style="margin-bottom: 12px">
      <div ref="chartRef" class="chart" />
    </PageCard>

    <PageCard title="运行记录">
      <el-table :data="runs" stripe size="small" highlight-current-row @current-change="(row: RunRow | null) => row && openIssues(row.id)">
        <el-table-column prop="id" label="运行ID" width="80" />
        <el-table-column prop="taskId" label="任务ID" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="评分" width="80" />
        <el-table-column prop="issueCount" label="问题数" width="80" />
        <el-table-column prop="startedAt" label="开始时间" width="170" />
        <el-table-column prop="message" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link @click.stop="openIssues(row.id)">问题</el-button>
            <el-button link type="primary" @click.stop="rerun(row.id)">重跑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-if="selectedRunId" :title="`问题下钻 · 运行 #${selectedRunId}`" style="margin-top: 12px">
      <el-table :data="issues" stripe size="small">
        <el-table-column label="检查" width="110">
          <template #default="{ row }">{{ $statusLabel(row.checkType) }}</template>
        </el-table-column>
        <el-table-column prop="targetTable" label="表" width="140" />
        <el-table-column prop="targetColumn" label="字段" width="100" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ $statusLabel(row.issueType) }}</template>
        </el-table-column>
        <el-table-column prop="issueValue" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column prop="issueCount" label="数量" width="70" />
        <el-table-column label="级别" width="80">
          <template #default="{ row }">{{ $statusLabel(row.severity) }}</template>
        </el-table-column>
        <el-table-column prop="sampleData" label="样本" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!issues.length" description="本次运行无问题记录" />
    </PageCard>
  </div>
</template>

<style scoped>
.stat-num {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
}
.stat-sub {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.chart {
  height: 280px;
  width: 100%;
}
</style>
