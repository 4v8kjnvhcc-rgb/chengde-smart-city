<script setup lang="ts">
/**
 * 数据质量监控 — 对照建设说明：
 * （1）流程调度控制
 * （2）数据质量监控（含问题跟踪 / 血缘追溯展示）
 * （3）数据标准监控（命名标准 + 数据标准）
 */
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import QualityMonitorSchedulePanel from './QualityMonitorSchedulePanel.vue'
import QualityMonitorStandardPanel from './QualityMonitorStandardPanel.vue'

interface TrendPoint {
  date: string
  runCount: number
  issueCount: number
  avgScore: number | null
}

interface RunRow {
  id: number
  taskId: number
  taskName?: string
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
  databaseName?: string
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

interface PublishedScheme {
  id: number
  schemeName: string
  modelName?: string
  scheduleStatus?: string
  execStatus?: string
  lastExecAt?: string
  qualityTaskId?: number
  cycleName?: string
}

interface TicketRow {
  id: number
  runId: number
  taskId?: number
  title: string
  databaseName?: string
  targetTable?: string
  targetColumn?: string
  issueValue?: string
  owner?: string
  channel?: string
  status: string
  createdAt?: string
}

interface AlertLogRow {
  id: number
  channel?: string
  receivers?: string
  subject?: string
  status?: string
  message?: string
  createdAt?: string
  runId?: number
}

const activeTab = ref('quality')
const qualityLoaded = ref(false)

const stats = ref<Stats | null>(null)
const runs = ref<RunRow[]>([])
const published = ref<PublishedScheme[]>([])
const {
  page: runPage,
  pageSize: runPageSize,
  paged: pagedRuns,
  total: runTotal,
  resetPage: resetRunPage,
} = useClientPager(runs)
const issues = ref<IssueRow[]>([])
const selectedRunId = ref<number | null>(null)
const selectedRun = ref<RunRow | null>(null)
const tickets = ref<TicketRow[]>([])
const alertLogs = ref<AlertLogRow[]>([])
const loading = ref(false)
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const lineageNodes = [
  { name: '规则配置', tip: '模型规则' },
  { name: '发布', tip: 'DS流程' },
  { name: '执行/定时', tip: '稽核' },
  { name: '问题定位', tip: '库表字段值' },
  { name: '告警通知', tip: '邮件/短信' },
]

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

async function loadQuality() {
  loading.value = true
  try {
    const [s, r, p] = await Promise.all([
      api.get('/governance/quality/task-mgmt/stats'),
      api.get('/governance/quality/task-mgmt/runs'),
      api.get('/governance/quality/schemes/published'),
    ])
    stats.value = s.data
    runs.value = r.data || stats.value?.recentRuns || []
    published.value = p.data || []
    tickets.value = (await api.get('/governance/quality/alerts/tickets', { params: { limit: 50 } })).data || []
    resetRunPage()
    qualityLoaded.value = true
    await nextTick()
    renderChart()
  } catch {
    ElMessage.error('加载监控数据失败')
  } finally {
    loading.value = false
  }
}

async function openIssues(row: RunRow) {
  selectedRunId.value = row.id
  selectedRun.value = row
  issues.value = (await api.get(`/governance/quality/task-mgmt/runs/${row.id}/issues`)).data || []
  try {
    alertLogs.value = (await api.get('/governance/quality/alerts/logs', { params: { runId: row.id } })).data || []
  } catch {
    alertLogs.value = []
  }
}

async function rerun(runId: number) {
  const res = await api.post(`/governance/quality/task-mgmt/runs/${runId}/rerun`)
  ElMessage.success(`重跑完成 · 评分 ${res.data.score}`)
  selectedRunId.value = null
  selectedRun.value = null
  issues.value = []
  alertLogs.value = []
  await loadQuality()
}

function issueStatusLabel(status?: string) {
  if (status === 'OPEN') return '待处理'
  if (status === 'CLOSED') return '已关闭'
  return statusLabel(status)
}

function ticketStatusLabel(status?: string) {
  if (status === 'OPEN') return '待处理'
  if (status === 'PROCESSING') return '处理中'
  if (status === 'CLOSED') return '已关闭'
  return statusLabel(status)
}

async function pushNotify() {
  const run = selectedRun.value || runs.value[0]
  if (!run) {
    ElMessage.warning('暂无运行记录可推送')
    return
  }
  try {
    const res = await api.post('/governance/quality/alerts/notify-run', {
      taskId: run.taskId,
      runId: run.id,
      taskName: run.taskName,
    })
    if (res.data?.skipped) {
      ElMessage.info(res.data.message || '无异常，未推送')
    } else {
      ElMessage.success('已触发告警推送（邮件/短信台账）')
    }
    await openIssues(run)
    tickets.value = (await api.get('/governance/quality/alerts/tickets', { params: { limit: 50 } })).data || []
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '推送失败')
  }
}

function scheduleZh(s?: string) {
  if (s === 'RUNNING') return '正在运行'
  if (s === 'STOPPED') return '已停止'
  return statusLabel(s)
}

function onResize() {
  chart?.resize()
}

watch(
  activeTab,
  async (t) => {
    if (t === 'quality') {
      if (!qualityLoaded.value) await loadQuality()
      else {
        await nextTick()
        renderChart()
        chart?.resize()
      }
      window.addEventListener('resize', onResize)
    } else {
      window.removeEventListener('resize', onResize)
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
})
</script>

<template>
  <PageCard title="数据质量监控">
    <el-tabs v-model="activeTab" class="qm-tabs">
      <el-tab-pane label="流程调度控制" name="schedule" lazy>
        <QualityMonitorSchedulePanel />
      </el-tab-pane>

      <el-tab-pane label="数据质量监控" name="quality" lazy>
        <div v-loading="loading" class="qm-quality">
          <div class="lineage">
            <div v-for="(n, i) in lineageNodes" :key="n.name" class="lineage__item">
              <div class="lineage__node">
                <b>{{ n.name }}</b>
                <span>{{ n.tip }}</span>
              </div>
              <div v-if="i < lineageNodes.length - 1" class="lineage__arrow">→</div>
            </div>
          </div>

          <el-row :gutter="12" style="margin-bottom: 12px">
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-lab">已发布任务</div>
                <div class="stat-num">{{ published.length }}</div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-lab">今日运行</div>
                <div class="stat-num">{{ stats?.runToday ?? '—' }}</div>
                <div class="stat-sub">成功 {{ stats?.successToday ?? 0 }} · 失败 {{ stats?.failToday ?? 0 }}</div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-lab">今日问题</div>
                <div class="stat-num">{{ stats?.issueToday ?? '—' }}</div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-lab">近均评分</div>
                <div class="stat-num">{{ stats?.avgScore ?? '—' }}</div>
              </div>
            </el-col>
          </el-row>

          <PageCard title="已发布任务（质量方案）" style="margin-bottom: 12px">
            <el-table :data="published" stripe size="small">
              <el-table-column prop="schemeName" label="名称" min-width="140" show-overflow-tooltip />
              <el-table-column prop="modelName" label="模型" width="120" show-overflow-tooltip />
              <el-table-column label="执行周期" width="120">
                <template #default="{ row }">{{ row.cycleName || '—' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">{{ scheduleZh(row.scheduleStatus) }}</template>
              </el-table-column>
              <el-table-column label="执行状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="statusTagType(row.execStatus)">{{ statusLabel(row.execStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="lastExecAt" label="最近执行" width="170" />
            </el-table>
            <el-empty v-if="!published.length" description="暂无已发布任务；请在「质量规则配置 · 质量方案管理」中发布" />
          </PageCard>

          <PageCard title="近 7 日质量趋势" style="margin-bottom: 12px">
            <div ref="chartRef" class="chart" />
          </PageCard>

          <PageCard title="问题工单 · 责任到人" style="margin-bottom: 12px">
            <div class="ticket-bar">
              <span class="muted">待处理异常：可定位到库/表/字段/值，并推送告警</span>
              <el-button size="small" type="primary" @click="pushNotify">推送告警</el-button>
            </div>
            <el-table :data="tickets" stripe size="small" @row-click="(row: TicketRow) => row.runId && openIssues({ id: row.runId, taskId: row.taskId || 0, status: 'SUCCESS' })">
              <el-table-column prop="title" label="问题" min-width="160" show-overflow-tooltip />
              <el-table-column prop="databaseName" label="数据库" width="110" show-overflow-tooltip />
              <el-table-column prop="targetTable" label="表" width="120" show-overflow-tooltip />
              <el-table-column prop="targetColumn" label="字段" width="100" />
              <el-table-column prop="issueValue" label="数据值" min-width="140" show-overflow-tooltip />
              <el-table-column prop="owner" label="责任人" width="100" />
              <el-table-column prop="channel" label="推送" width="90" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" type="danger">{{ ticketStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!tickets.length" description="暂无待处理问题" />
          </PageCard>

          <PageCard title="运行记录 · 校验日志">
            <el-table
              :data="pagedRuns"
              stripe
              size="small"
              highlight-current-row
              @current-change="(row: RunRow | null) => row && openIssues(row)"
            >
              <el-table-column prop="id" label="运行ID" width="80" />
              <el-table-column label="任务" min-width="140">
                <template #default="{ row }">{{ row.taskName || `任务#${row.taskId}` }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="score" label="评分" width="80" />
              <el-table-column prop="issueCount" label="问题数" width="80" />
              <el-table-column prop="startedAt" label="开始时间" width="170" />
              <el-table-column prop="message" label="校验摘要" min-width="180" show-overflow-tooltip />
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button link @click.stop="openIssues(row)">日志</el-button>
                  <el-button link type="primary" @click.stop="rerun(row.id)">重跑</el-button>
                </template>
              </el-table-column>
            </el-table>
            <PortalPagination
              v-model:page="runPage"
              v-model:page-size="runPageSize"
              :total="runTotal"
            />
            <el-empty v-if="!loading && !runs.length" description="暂无运行记录；请发布后执行或启动定时" />
          </PageCard>

          <PageCard v-if="selectedRunId" :title="`问题下钻 · 运行 #${selectedRunId}（库 / 表 / 字段 / 值）`" style="margin-top: 12px">
            <el-table :data="issues" stripe size="small">
              <el-table-column label="检查" width="110">
                <template #default="{ row }">{{ statusLabel(row.checkType) }}</template>
              </el-table-column>
              <el-table-column prop="databaseName" label="数据库" width="120" show-overflow-tooltip />
              <el-table-column prop="targetTable" label="数据表" width="140" show-overflow-tooltip />
              <el-table-column prop="targetColumn" label="字段" width="100" />
              <el-table-column prop="issueValue" label="数据值" min-width="160" show-overflow-tooltip />
              <el-table-column prop="sampleData" label="样本" min-width="140" show-overflow-tooltip />
              <el-table-column prop="issueCount" label="数量" width="70" />
              <el-table-column label="级别" width="80">
                <template #default="{ row }">{{ statusLabel(row.severity) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">{{ issueStatusLabel(row.status) }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!issues.length" description="本次运行无问题记录" />

            <div class="sec-sub">告警推送日志</div>
            <el-table :data="alertLogs" stripe size="small">
              <el-table-column prop="createdAt" label="时间" width="170" />
              <el-table-column label="通道" width="90">
                <template #default="{ row }">{{ statusLabel(row.channel) }}</template>
              </el-table-column>
              <el-table-column prop="receivers" label="接收人" min-width="140" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
            </el-table>
            <el-empty v-if="!alertLogs.length" description="本运行暂无告警推送记录" />
          </PageCard>
        </div>
      </el-tab-pane>

      <el-tab-pane label="数据标准监控" name="standard" lazy>
        <QualityMonitorStandardPanel />
      </el-tab-pane>
    </el-tabs>
  </PageCard>
</template>

<style scoped>
.qm-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
.qm-quality {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.lineage {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  background: linear-gradient(90deg, rgba(15, 23, 42, 0.04), rgba(37, 99, 235, 0.06));
  border: 1px solid var(--el-border-color-lighter);
}
.lineage__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.lineage__node {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  min-width: 88px;
}
.lineage__node b {
  font-size: 13px;
}
.lineage__node span {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.lineage__arrow {
  color: var(--el-color-primary);
  font-weight: 600;
  padding: 0 2px;
}
.stat-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--el-bg-color);
  margin-bottom: 8px;
}
.stat-lab {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.stat-num {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
  margin-top: 4px;
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
.ticket-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.sec-sub {
  margin: 14px 0 8px;
  font-size: 13px;
  font-weight: 650;
}
</style>
