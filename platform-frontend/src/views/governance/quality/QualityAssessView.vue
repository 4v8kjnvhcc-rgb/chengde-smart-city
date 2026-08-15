<script setup lang="ts">
/**
 * V3.0「数据质量评估」
 * 展示质量规则/任务配置中的质量任务列表（含定时），关键字查询 + 分页；
 * 「生成报告」按完整性/规范性/准确性/唯一性/一致性/及时性汇总该任务稽核结果，并下钻问题数据。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface TaskRow {
  id: number
  taskName: string
  description?: string
  status: string
  lastScore?: number
  lastMessage?: string
  lastRunAt?: string
  scheduleType?: string
  cronExpr?: string
  detailCount?: number
  metadataEntryCode?: string
}

interface RunRow {
  id: number
  taskId?: number
  taskName?: string
  status: string
  score?: number
  issueCount?: number
  totalChecks?: number
  startedAt?: string
  endedAt?: string
  message?: string
}

interface IssueRow {
  id?: number
  checkType?: string
  targetTable?: string
  targetColumn?: string
  issueType?: string
  issueValue?: string
  sampleData?: string
  issueCount?: number
  severity?: string
  status?: string
}

interface DimRow {
  key: string
  label: string
  desc: string
  issueTotal: number
  checkHits: number
  scoreHint: number | null
}

const CHECK_TO_DIM: Record<string, string> = {
  RECORD_COUNT: 'completeness',
  NULL_CHECK: 'completeness',
  STANDARD: 'conformity',
  STANDARD_INSPECTION: 'conformity',
  REGEX: 'conformity',
  ACCURACY: 'accuracy',
  RANGE: 'accuracy',
  CUSTOM: 'accuracy',
  UNIQUENESS: 'uniqueness',
  CONSISTENCY: 'consistency',
  LOGIC: 'consistency',
  VOLATILITY: 'timeliness',
  TIMELINESS: 'timeliness',
  FLUCTUATION: 'timeliness',
}

const DIM_META: { key: string; label: string; desc: string }[] = [
  { key: 'completeness', label: '完整性', desc: '记录或字段是否缺失（空值、记录数等）' },
  { key: 'conformity', label: '规范性', desc: '编码、格式、命名是否符合数据标准' },
  { key: 'accuracy', label: '准确性', desc: '是否存在异常值、乱码、量级错误' },
  { key: 'uniqueness', label: '唯一性', desc: '主键/业务键是否重复录入' },
  { key: 'consistency', label: '一致性', desc: '跨表/跨源逻辑与口径是否一致' },
  { key: 'timeliness', label: '及时性', desc: '数据产生到可查看的延时与波动是否达标' },
]

const loading = ref(false)
const keyword = ref('')
const tasks = ref<TaskRow[]>([])

const filteredTasks = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return tasks.value
  return tasks.value.filter((t) => {
    const blob = [
      t.taskName,
      t.description,
      t.cronExpr,
      t.metadataEntryCode,
      t.lastMessage,
      t.scheduleType === 'CRON' ? '定时' : '手动',
      statusLabel(t.status),
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return blob.includes(kw)
  })
})

const {
  page: taskPage,
  pageSize: taskPageSize,
  paged: pagedTasks,
  total: taskTotal,
  resetPage: resetTaskPage,
} = useClientPager(filteredTasks)

watch(keyword, () => resetTaskPage())

const reportVisible = ref(false)
const reportLoading = ref(false)
const reportTask = ref<TaskRow | null>(null)
const reportRuns = ref<RunRow[]>([])
const reportIssues = ref<IssueRow[]>([])
const persistedReportCode = ref<string | null>(null)
const detailTab = ref('runs')
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const {
  page: issuePage,
  pageSize: issuePageSize,
  paged: pagedIssues,
  total: issueTotal,
  resetPage: resetIssuePage,
} = useClientPager(reportIssues)

const avgScore = computed(() => {
  const scored = reportRuns.value.filter((r) => r.score != null)
  if (!scored.length) return null
  return Math.round(scored.reduce((s, r) => s + Number(r.score), 0) / scored.length)
})

const dimensions = computed<DimRow[]>(() => {
  const bag: Record<string, { issueTotal: number; checkHits: number }> = {}
  for (const m of DIM_META) bag[m.key] = { issueTotal: 0, checkHits: 0 }
  for (const iss of reportIssues.value) {
    const dim = CHECK_TO_DIM[String(iss.checkType || '').toUpperCase()] || 'accuracy'
    if (!bag[dim]) bag[dim] = { issueTotal: 0, checkHits: 0 }
    bag[dim].checkHits += 1
    bag[dim].issueTotal += Number(iss.issueCount || 1)
  }
  return DIM_META.map((m) => {
    const b = bag[m.key]
    let scoreHint: number | null = null
    if (b.checkHits > 0) {
      scoreHint = Math.max(0, Math.min(100, 100 - Math.min(80, b.issueTotal * 2)))
    } else if (avgScore.value != null) {
      scoreHint = avgScore.value
    }
    return {
      key: m.key,
      label: m.label,
      desc: m.desc,
      issueTotal: b.issueTotal,
      checkHits: b.checkHits,
      scoreHint,
    }
  })
})

const ruleTypeStats = computed(() => {
  const map = new Map<string, number>()
  for (const iss of reportIssues.value) {
    const key = String(iss.checkType || iss.issueType || 'UNKNOWN').toUpperCase()
    map.set(key, (map.get(key) || 0) + Number(iss.issueCount || 1))
  }
  return [...map.entries()]
    .map(([type, count]) => ({ type, count, label: statusLabel(type) }))
    .sort((a, b) => b.count - a.count)
})

const hotTables = computed(() => {
  const map = new Map<string, number>()
  for (const iss of reportIssues.value) {
    const key = iss.targetTable || '（未指定表）'
    map.set(key, (map.get(key) || 0) + Number(iss.issueCount || 1))
  }
  return [...map.entries()]
    .map(([table, count]) => ({ table, count }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 8)
})

const hotMax = computed(() => Math.max(1, ...hotTables.value.map((x) => x.count), 1))
const ruleMax = computed(() => Math.max(1, ...ruleTypeStats.value.map((x) => x.count), 1))

const issueRowTotal = computed(() =>
  reportIssues.value.reduce((s, i) => s + Number(i.issueCount || 1), 0),
)

const scoreTone = computed(() => {
  const s = avgScore.value
  if (s == null) return 'muted'
  if (s >= 80) return 'good'
  if (s >= 60) return 'warn'
  return 'bad'
})

const scoreVerdict = computed(() => {
  const s = avgScore.value
  if (s == null) return '暂无评分'
  if (s >= 80) return '质量良好'
  if (s >= 60) return '需要关注'
  return '质量较差'
})

function scheduleLabel(row: TaskRow) {
  if (row.scheduleType === 'CRON') {
    return row.cronExpr ? `定时 · ${row.cronExpr}` : '定时'
  }
  return '手动'
}

function dimTone(score: number | null) {
  if (score == null) return 'muted'
  if (score >= 80) return 'good'
  if (score >= 60) return 'warn'
  return 'bad'
}

function dimProgressColor(score: number | null) {
  if (score == null) return '#909399'
  if (score >= 80) return 'var(--el-color-success)'
  if (score >= 60) return 'var(--el-color-warning)'
  return 'var(--el-color-danger)'
}

function renderTrendChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const byDay = new Map<string, { scoreSum: number; scoreN: number; issues: number; runs: number }>()
  for (const r of [...reportRuns.value].reverse()) {
    const day = (r.startedAt || '').slice(0, 10) || '未知'
    const bag = byDay.get(day) || { scoreSum: 0, scoreN: 0, issues: 0, runs: 0 }
    bag.runs += 1
    bag.issues += Number(r.issueCount || 0)
    if (r.score != null) {
      bag.scoreSum += Number(r.score)
      bag.scoreN += 1
    }
    byDay.set(day, bag)
  }
  const dates = [...byDay.keys()]
  const scores = dates.map((d) => {
    const b = byDay.get(d)!
    return b.scoreN ? Math.round(b.scoreSum / b.scoreN) : null
  })
  const issues = dates.map((d) => byDay.get(d)!.issues)
  const runs = dates.map((d) => byDay.get(d)!.runs)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['评分', '问题数', '运行次数'] },
    grid: { left: 44, right: 44, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: dates.map((d) => d.slice(5)) },
    yAxis: [
      { type: 'value', name: '评分', min: 0, max: 100 },
      { type: 'value', name: '次数' },
    ],
    series: [
      { name: '评分', type: 'line', smooth: true, data: scores },
      { name: '问题数', type: 'bar', yAxisIndex: 1, data: issues },
      { name: '运行次数', type: 'line', yAxisIndex: 1, data: runs },
    ],
  })
}

function onResize() {
  chart?.resize()
}

async function loadTasks() {
  loading.value = true
  try {
    const list = ((await api.get('/governance/quality/task-mgmt')).data || []) as TaskRow[]
    // 优先展示定时任务；若无定时则展示全部已配置任务，避免空列表死胡同
    const cron = list.filter((t) => String(t.scheduleType || '').toUpperCase() === 'CRON')
    tasks.value = cron.length ? cron : list
    resetTaskPage()
  } catch {
    ElMessage.error('加载质量任务失败')
  } finally {
    loading.value = false
  }
}

async function generateReport(row: TaskRow) {
  reportTask.value = row
  reportVisible.value = true
  reportLoading.value = true
  reportRuns.value = []
  reportIssues.value = []
  persistedReportCode.value = null
  detailTab.value = 'runs'
  resetIssuePage()
  try {
    const runsRes = await api.get('/governance/quality/task-mgmt/runs', { params: { taskId: row.id } })
    reportRuns.value = (runsRes.data || []) as RunRow[]

    const sample = reportRuns.value.slice(0, 3)
    if (sample.length) {
      const lists = await Promise.all(
        sample.map((run) =>
          api
            .get(`/governance/quality/task-mgmt/runs/${run.id}/issues`)
            .then((res) => (res.data || []) as IssueRow[])
            .catch(() => [] as IssueRow[]),
        ),
      )
      const merged: IssueRow[] = []
      const seen = new Set<string>()
      for (const list of lists) {
        for (const iss of list) {
          const key = `${iss.id ?? ''}|${iss.targetTable}|${iss.targetColumn}|${iss.issueValue}|${iss.checkType}`
          if (seen.has(key)) continue
          seen.add(key)
          merged.push(iss)
        }
      }
      reportIssues.value = merged
      resetIssuePage()
    }

    // 有评分运行时落库一份报告，便于「数据质量分析报告」页回看
    const scored = reportRuns.value.filter((r) => r.score != null)
    if (scored.length) {
      try {
        const saved = await api.post('/governance/platform/quality/reports', {
          reportName: `${row.taskName}·质量评估报告`,
          dimension: '完整性+规范性+准确性+唯一性+一致性+及时性',
        })
        persistedReportCode.value = saved.data?.reportCode || null
      } catch {
        /* 列表展示仍以本页聚合为准，落库失败不阻断查看 */
      }
    } else {
      ElMessage.warning('该任务尚无带评分的运行记录，报告仅展示已有运行与问题（如有）')
    }

    await nextTick()
    renderTrendChart()
  } catch {
    ElMessage.error('生成评估报告失败')
  } finally {
    reportLoading.value = false
  }
}

function closeReport() {
  reportVisible.value = false
  reportTask.value = null
}

onMounted(() => {
  void loadTasks()
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
    <PageCard title="数据质量评估">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="关键字" class="portal-field-xl">
          <el-input
            v-model="keyword"
            clearable
            placeholder="任务名 / 调度 / 消息"
            @keyup.enter="resetTaskPage"
          />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="resetTaskPage">查询</el-button>
          <el-button @click="loadTasks" :loading="loading">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="pagedTasks" stripe size="small">
        <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="调度" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ scheduleLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="detailCount" label="规则数" width="80" />
        <el-table-column prop="lastScore" label="最近评分" width="90">
          <template #default="{ row }">{{ row.lastScore ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastRunAt" label="最近运行" width="170" />
        <el-table-column prop="lastMessage" label="最近消息" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="generateReport(row)">质量评估</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-model:page="taskPage"
        v-model:page-size="taskPageSize"
        :total="taskTotal"
      />
      <el-empty
        v-if="!loading && !filteredTasks.length"
        description="暂无质量任务；请先在「质量规则配置 / 数据质量任务」中配置并保存任务"
      />
    </PageCard>

    <el-drawer
      v-model="reportVisible"
      :title="reportTask ? `质量评估报告 · ${reportTask.taskName}` : '质量评估报告'"
      size="78%"
      destroy-on-close
      class="assess-drawer"
      @closed="closeReport"
    >
      <div v-loading="reportLoading" class="assess-report">
        <!-- 总览头：一眼看清结论 -->
        <section class="rpt-hero" :class="`tone-${scoreTone}`">
          <div class="rpt-hero__score">
            <div class="rpt-score-ring">
              <span class="rpt-score-num">{{ avgScore == null ? '—' : avgScore }}</span>
              <span class="rpt-score-unit">分</span>
            </div>
            <div class="rpt-score-meta">
              <b>{{ scoreVerdict }}</b>
              <span>综合评分 · 近次稽核运行均值</span>
            </div>
          </div>
          <div class="rpt-hero__kpis">
            <div class="rpt-kpi">
              <span class="rpt-kpi__lab">稽核运行</span>
              <strong>{{ reportRuns.length }}</strong>
            </div>
            <div class="rpt-kpi">
              <span class="rpt-kpi__lab">问题条目</span>
              <strong>{{ reportIssues.length }}</strong>
            </div>
            <div class="rpt-kpi">
              <span class="rpt-kpi__lab">问题行合计</span>
              <strong>{{ issueRowTotal }}</strong>
            </div>
            <div class="rpt-kpi">
              <span class="rpt-kpi__lab">规则数</span>
              <strong>{{ reportTask?.detailCount ?? '—' }}</strong>
            </div>
          </div>
          <div v-if="persistedReportCode" class="rpt-hero__code">
            报告编码 {{ persistedReportCode }}
          </div>
        </section>

        <!-- 六维卡片 -->
        <section class="rpt-block">
          <header class="rpt-block__hd">
            <h3>六维评估</h3>
            <span>完整性 · 规范性 · 准确性 · 唯一性 · 一致性 · 及时性</span>
          </header>
          <div class="dim-grid">
            <article
              v-for="row in dimensions"
              :key="row.key"
              class="dim-card"
              :class="`tone-${dimTone(row.scoreHint)}`"
            >
              <div class="dim-card__top">
                <span class="dim-card__name">{{ row.label }}</span>
                <span class="dim-card__score">{{ row.scoreHint == null ? '—' : row.scoreHint }}</span>
              </div>
              <el-progress
                :percentage="row.scoreHint == null ? 0 : row.scoreHint"
                :stroke-width="8"
                :show-text="false"
                :color="dimProgressColor(row.scoreHint)"
              />
              <p class="dim-card__desc">{{ row.desc }}</p>
              <div class="dim-card__foot">
                <span>命中 {{ row.checkHits }}</span>
                <span>问题行 {{ row.issueTotal }}</span>
              </div>
            </article>
          </div>
        </section>

        <!-- 集中区 + 类型 + 趋势 -->
        <section class="rpt-block">
          <div class="rpt-split">
            <div class="rpt-panel">
              <header class="rpt-block__hd">
                <h3>问题集中区</h3>
                <span>按目标表</span>
              </header>
              <div v-if="hotTables.length" class="bar-list">
                <div v-for="item in hotTables" :key="item.table" class="bar-row">
                  <div class="bar-row__label" :title="item.table">{{ item.table }}</div>
                  <div class="bar-row__track">
                    <div class="bar-row__fill" :style="{ width: `${(item.count / hotMax) * 100}%` }" />
                  </div>
                  <div class="bar-row__val">{{ item.count }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无问题集中区" :image-size="56" />
            </div>
            <div class="rpt-panel">
              <header class="rpt-block__hd">
                <h3>规则类型分布</h3>
                <span>问题规则类型</span>
              </header>
              <div v-if="ruleTypeStats.length" class="bar-list">
                <div v-for="item in ruleTypeStats" :key="item.type" class="bar-row">
                  <div class="bar-row__label" :title="item.label">{{ item.label }}</div>
                  <div class="bar-row__track">
                    <div
                      class="bar-row__fill bar-row__fill--alt"
                      :style="{ width: `${(item.count / ruleMax) * 100}%` }"
                    />
                  </div>
                  <div class="bar-row__val">{{ item.count }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无规则类型统计" :image-size="56" />
            </div>
          </div>
        </section>

        <section class="rpt-block">
          <header class="rpt-block__hd">
            <h3>质量趋势</h3>
            <span>按运行时间 · 评分 / 问题数 / 运行次数</span>
          </header>
          <div class="rpt-panel rpt-panel--chart">
            <div ref="chartRef" class="assess-chart" />
            <el-empty v-if="!reportRuns.length" description="暂无运行，无法绘制趋势" :image-size="56" />
          </div>
        </section>

        <!-- 明细 Tab，减少一屏堆叠 -->
        <section class="rpt-block">
          <el-tabs v-model="detailTab" class="rpt-tabs">
            <el-tab-pane :label="`稽核执行（${reportRuns.length}）`" name="runs">
              <el-table :data="reportRuns" stripe size="small" max-height="320">
                <el-table-column prop="id" label="运行ID" width="80" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="score" label="评分" width="80">
                  <template #default="{ row }">{{ row.score ?? '—' }}</template>
                </el-table-column>
                <el-table-column prop="totalChecks" label="检查项" width="80" />
                <el-table-column prop="issueCount" label="问题数" width="80" />
                <el-table-column prop="startedAt" label="开始时间" width="170" />
                <el-table-column prop="message" label="消息" min-width="140" show-overflow-tooltip />
              </el-table>
              <el-empty v-if="!reportRuns.length" description="暂无稽核运行" :image-size="56" />
            </el-tab-pane>
            <el-tab-pane :label="`问题明细（${reportIssues.length}）`" name="issues">
              <el-table :data="pagedIssues" stripe size="small" max-height="320">
                <el-table-column prop="targetTable" label="表" width="130" show-overflow-tooltip />
                <el-table-column prop="targetColumn" label="字段" width="110" show-overflow-tooltip />
                <el-table-column label="检查类型" width="110">
                  <template #default="{ row }">{{ statusLabel(row.checkType) }}</template>
                </el-table-column>
                <el-table-column label="问题类型" width="100">
                  <template #default="{ row }">{{ statusLabel(row.issueType) }}</template>
                </el-table-column>
                <el-table-column prop="issueValue" label="问题值" min-width="120" show-overflow-tooltip />
                <el-table-column prop="sampleData" label="问题数据" min-width="140" show-overflow-tooltip />
                <el-table-column prop="issueCount" label="数量" width="70" />
                <el-table-column label="级别" width="80">
                  <template #default="{ row }">{{ statusLabel(row.severity) }}</template>
                </el-table-column>
              </el-table>
              <PortalPagination
                v-model:page="issuePage"
                v-model:page-size="issuePageSize"
                :total="issueTotal"
              />
              <el-empty v-if="!reportLoading && !reportIssues.length" description="近期运行未检出问题数据" :image-size="56" />
            </el-tab-pane>
          </el-tabs>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.assess-report {
  min-height: 240px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 8px;
}

.rpt-hero {
  display: grid;
  grid-template-columns: minmax(220px, 280px) 1fr;
  gap: 16px 20px;
  align-items: center;
  padding: 18px 20px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--el-color-primary) 8%, transparent), transparent 55%),
    var(--el-bg-color);
}
.rpt-hero.tone-good {
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--el-color-success) 12%, transparent), transparent 60%),
    var(--el-bg-color);
  border-color: color-mix(in srgb, var(--el-color-success) 28%, var(--el-border-color-lighter));
}
.rpt-hero.tone-warn {
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--el-color-warning) 14%, transparent), transparent 60%),
    var(--el-bg-color);
  border-color: color-mix(in srgb, var(--el-color-warning) 30%, var(--el-border-color-lighter));
}
.rpt-hero.tone-bad {
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--el-color-danger) 12%, transparent), transparent 60%),
    var(--el-bg-color);
  border-color: color-mix(in srgb, var(--el-color-danger) 28%, var(--el-border-color-lighter));
}
.rpt-hero__score {
  display: flex;
  align-items: center;
  gap: 14px;
}
.rpt-score-ring {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--el-bg-color);
  border: 3px solid var(--el-color-primary-light-5);
  box-shadow: 0 6px 18px color-mix(in srgb, var(--el-color-primary) 12%, transparent);
}
.tone-good .rpt-score-ring {
  border-color: var(--el-color-success-light-5);
}
.tone-warn .rpt-score-ring {
  border-color: var(--el-color-warning-light-5);
}
.tone-bad .rpt-score-ring {
  border-color: var(--el-color-danger-light-5);
}
.rpt-score-num {
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
  color: var(--el-text-color-primary);
}
.rpt-score-unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.rpt-score-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rpt-score-meta b {
  font-size: 18px;
  font-weight: 650;
}
.rpt-score-meta span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.rpt-hero__kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.rpt-kpi {
  padding: 10px 12px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--el-bg-color) 82%, #fff);
  border: 1px solid var(--el-border-color-extra-light);
}
.rpt-kpi__lab {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.rpt-kpi strong {
  font-size: 22px;
  font-weight: 650;
  line-height: 1.1;
}
.rpt-hero__code {
  grid-column: 1 / -1;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rpt-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rpt-block__hd {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.rpt-block__hd h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
}
.rpt-block__hd span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.dim-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.dim-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 14px;
  background: var(--el-bg-color);
  min-height: 148px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.dim-card.tone-good {
  border-color: color-mix(in srgb, var(--el-color-success) 35%, var(--el-border-color-lighter));
}
.dim-card.tone-warn {
  border-color: color-mix(in srgb, var(--el-color-warning) 40%, var(--el-border-color-lighter));
}
.dim-card.tone-bad {
  border-color: color-mix(in srgb, var(--el-color-danger) 35%, var(--el-border-color-lighter));
}
.dim-card__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.dim-card__name {
  font-size: 14px;
  font-weight: 600;
}
.dim-card__score {
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
}
.dim-card.tone-good .dim-card__score {
  color: var(--el-color-success);
}
.dim-card.tone-warn .dim-card__score {
  color: var(--el-color-warning);
}
.dim-card.tone-bad .dim-card__score {
  color: var(--el-color-danger);
}
.dim-card__desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
  flex: 1;
}
.dim-card__foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.rpt-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.rpt-panel {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 12px 14px;
  background: var(--el-bg-color);
  min-height: 180px;
}
.rpt-panel--chart {
  min-height: 280px;
}
.assess-chart {
  height: 260px;
  width: 100%;
}

.bar-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 6px;
}
.bar-row {
  display: grid;
  grid-template-columns: minmax(72px, 28%) 1fr 40px;
  gap: 8px;
  align-items: center;
}
.bar-row__label {
  font-size: 12px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bar-row__track {
  height: 8px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
  overflow: hidden;
}
.bar-row__fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--el-color-danger-light-3), var(--el-color-danger));
}
.bar-row__fill--alt {
  background: linear-gradient(90deg, var(--el-color-primary-light-3), var(--el-color-primary));
}
.bar-row__val {
  text-align: right;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.rpt-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

@media (max-width: 1100px) {
  .rpt-hero {
    grid-template-columns: 1fr;
  }
  .rpt-hero__kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .dim-grid,
  .rpt-split {
    grid-template-columns: 1fr 1fr;
  }
}
@media (max-width: 720px) {
  .dim-grid,
  .rpt-split {
    grid-template-columns: 1fr;
  }
}
</style>
