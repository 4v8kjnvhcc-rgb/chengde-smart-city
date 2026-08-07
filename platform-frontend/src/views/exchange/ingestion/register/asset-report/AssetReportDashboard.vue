<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading } from '../../useIngestionHub'
import ProjectTablesDrawer from './ProjectTablesDrawer.vue'
import TableAssetDetailDrawer from './TableAssetDetailDrawer.vue'
import ScriptAssetDetailDrawer from './ScriptAssetDetailDrawer.vue'
import WorkflowAssetDetailDrawer from './WorkflowAssetDetailDrawer.vue'

const { loading, loadError, withLoad } = useIngestionLoading()
const report = ref<Record<string, unknown> | null>(null)

const projectDrawer = ref(false)
const projectId = ref<number | null>(null)
const projectName = ref('')

const tableDrawer = ref(false)
const tableId = ref<number | null>(null)

const scriptDrawer = ref(false)
const scriptId = ref<number | null>(null)

const workflowDrawer = ref(false)
const workflowId = ref<number | null>(null)

const tableTrendRef = ref<HTMLDivElement | null>(null)
const storageTrendRef = ref<HTMLDivElement | null>(null)
let tableChart: echarts.ECharts | null = null
let storageChart: echarts.ECharts | null = null

const tableTrend = computed(() => (report.value?.tableTrend as { month?: string; date?: string; value?: number; count?: number }[]) || [])
const storageTrend = computed(() => (report.value?.storageTrend as { month?: string; date?: string; value?: number; gb?: number }[]) || [])
const topProjects = computed(() => (report.value?.topProjects as Record<string, unknown>[]) || [])
const topScripts = computed(() => (report.value?.topScriptsByDuration as Record<string, unknown>[]) || [])
const topTables = computed(() => (report.value?.topTablesByStorage as Record<string, unknown>[]) || [])
const workflows = computed(() => (report.value?.workflows as Record<string, unknown>[]) || [])
const topTasks = computed(() => (report.value?.topTasks as Record<string, unknown>[]) || [])

async function load() {
  await withLoad(async () => {
    const data = (await ingestionApi.assetReport()).data || {}
    // 红框区域使用模拟数据；非红框（登记项目/物理表/数据库/热门表/表新增趋势/热门项目名称与表数）保留真实数据
    report.value = applyMockRedBox(data)
  })
  await nextTick()
  renderCharts()
}

/**
 * 按表数量估算模拟存储(GB)：
 * <10 张 → 0.01–0.2；10–50 张 → 0.2–1；>50 张 → 1–2
 */
function mockStorageByTableCount(tableCount: number, salt = 0): number {
  const n = Math.max(0, Number(tableCount) || 0)
  const t = (Math.abs(Math.sin((n + 1) * 12.9898 + salt * 78.233)) % 1)
  if (n < 10) return Number((0.01 + t * 0.19).toFixed(2))
  if (n <= 50) return Number((0.2 + t * 0.8).toFixed(2))
  return Number((1 + t * 1).toFixed(2))
}

/** 需求：红框内写模拟数据，其余接实际登记数据 */
function applyMockRedBox(real: Record<string, unknown>) {
  const out = { ...real }
  const physicalTables = Number(real.physicalTableCount ?? real.tableCount ?? 0)
  const projects = Array.isArray(real.topProjects) ? [...(real.topProjects as Record<string, unknown>[])] : []

  out.topProjects = projects.map((p, idx) => {
    const tc = Number(p.tableCount ?? p.tables ?? 0)
    return {
      ...p,
      storageGb: mockStorageByTableCount(tc, idx + 1),
    }
  })

  // 汇总按整体物理表数量级估算；项目行按各自表数估算
  out.storageGb = mockStorageByTableCount(physicalTables, 0)

  out.auditTableCount = 18
  out.scriptCount = 26
  out.scriptLinkedWorkflowCount = 12
  out.workflowCount = 15
  out.workflowScheduledCount = 9
  out.taskCount = 42

  const totalGb = Number(out.storageGb) || 0.5
  const days = 14
  const today = new Date()
  out.storageTrend = Array.from({ length: days }, (_, i) => {
    const d = new Date(today)
    d.setDate(d.getDate() - (days - 1 - i))
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    // 自约 75% 缓升至当前总量，小幅波动
    const base = totalGb * (0.75 + (0.25 * i) / Math.max(1, days - 1))
    const wobble = totalGb * 0.03 * Math.sin(i * 1.3)
    const v = Number(Math.max(0.01, base + wobble).toFixed(2))
    return { date: `${mm}/${dd}`, month: `${mm}/${dd}`, value: v, gb: v }
  })

  out.topScriptsByDuration = [
    { scriptId: 9001, scriptName: '人口主题日增量清洗', durationSec: 186.4, status: 'SUCCESS' },
    { scriptId: 9002, scriptName: '法人库宽表汇总', durationSec: 142.0, status: 'SUCCESS' },
    { scriptId: 9003, scriptName: '证照影像索引同步', durationSec: 98.5, status: 'SUCCESS' },
    { scriptId: 9004, scriptName: '宏观经济指标校验', durationSec: 67.2, status: 'RUNNING' },
    { scriptId: 9005, scriptName: '地理编码补全任务', durationSec: 41.8, status: 'SUCCESS' },
  ]

  const tables = Array.isArray(real.topTablesByStorage) ? [...(real.topTablesByStorage as Record<string, unknown>[])] : []
  if (tables.length) {
    out.topTablesByStorage = tables.map((t, idx) => ({
      ...t,
      // 单表存储落在较小量级，并随排名递减
      storageGb: Number(Math.max(0.01, mockStorageByTableCount(1, idx + 3) * (1 - idx * 0.12)).toFixed(2)),
    }))
  } else {
    out.topTablesByStorage = [
      { tableId: 0, tableName: 'pers_base_info', storageGb: 0.18, columnCount: 48 },
      { tableId: 0, tableName: 'corp_register', storageGb: 0.15, columnCount: 36 },
      { tableId: 0, tableName: 'license_main', storageGb: 0.12, columnCount: 28 },
      { tableId: 0, tableName: 'macro_indicator', storageGb: 0.09, columnCount: 22 },
      { tableId: 0, tableName: 'geo_poi', storageGb: 0.06, columnCount: 18 },
    ]
  }

  out.workflows = [
    { id: 9101, taskName: '人口主题日调度', status: 'ACTIVE', scheduleEnabled: true },
    { id: 9102, taskName: '法人库周全量', status: 'ACTIVE', scheduleEnabled: true },
    { id: 9103, taskName: '证照影像回流', status: 'PAUSED', scheduleEnabled: false },
    { id: 9104, taskName: '指标质量巡检', status: 'ACTIVE', scheduleEnabled: true },
  ]
  out.topTasks = [
    { taskName: 'MySQL→ODS 人口表抽取', status: 'SUCCESS' },
    { taskName: 'Oracle→ODS 法人同步', status: 'RUNNING' },
    { taskName: '文件接入·证照清单', status: 'SUCCESS' },
    { taskName: 'API 拉取·信用评分', status: 'SUCCESS' },
    { taskName: 'Redis 缓存预热', status: 'FAILED' },
  ]
  return out
}

function renderCharts() {
  if (tableTrendRef.value) {
    if (!tableChart) tableChart = echarts.init(tableTrendRef.value)
    tableChart.setOption({
      grid: { left: 40, right: 16, top: 24, bottom: 28 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: tableTrend.value.map((t) => t.month || t.date || ''),
        axisLabel: { fontSize: 10 },
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{
        type: 'line',
        smooth: true,
        data: tableTrend.value.map((t) => Number(t.value ?? t.count ?? 0)),
        areaStyle: { opacity: 0.12 },
        itemStyle: { color: '#409eff' },
      }],
    })
  }
  if (storageTrendRef.value) {
    if (!storageChart) storageChart = echarts.init(storageTrendRef.value)
    storageChart.setOption({
      grid: { left: 48, right: 16, top: 24, bottom: 28 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: storageTrend.value.map((t) => t.month || t.date || ''),
        axisLabel: { fontSize: 10 },
      },
      yAxis: { type: 'value' },
      series: [{
        type: 'line',
        smooth: true,
        data: storageTrend.value.map((t) => Number(t.value ?? t.gb ?? 0)),
        areaStyle: { opacity: 0.12 },
        itemStyle: { color: '#67c23a' },
      }],
    })
  }
}

function openProject(row: Record<string, unknown>) {
  projectId.value = Number(row.projectId)
  projectName.value = String(row.projectName || '')
  projectDrawer.value = true
}

function openScript(row: Record<string, unknown>) {
  const id = Number(row.scriptId)
  if (!id || id >= 9000) {
    ElMessage.info('当前为模拟脚本数据，暂无详情')
    return
  }
  scriptId.value = id
  scriptDrawer.value = true
}

function openWorkflow(row: Record<string, unknown>) {
  const id = Number(row.id)
  if (!id || id >= 9000) {
    ElMessage.info('当前为模拟工作流数据，暂无详情')
    return
  }
  workflowId.value = id
  workflowDrawer.value = true
}

function openTable(row: Record<string, unknown>) {
  const id = Number(row.tableId)
  if (!id) {
    ElMessage.info('当前为模拟表存储数据，暂无详情')
    return
  }
  tableId.value = id
  tableDrawer.value = true
}

function onResize() {
  tableChart?.resize()
  storageChart?.resize()
}

watch(() => report.value, async () => {
  await nextTick()
  renderCharts()
})

onMounted(() => {
  void load()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  tableChart?.dispose()
  storageChart?.dispose()
  tableChart = null
  storageChart = null
})
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据资产报告">
      <p class="hint">
        登记项目、物理表、数据库、热门表及表新增趋势、热门项目（名称/表数）来自实际登记数据；
        存储量、脚本/工作流/接入任务及相关图表为演示模拟数据。
      </p>

      <el-row v-if="report" :gutter="12" class="stat-row">
        <el-col :xs="12" :sm="8" :md="4"><el-statistic title="登记项目" :value="Number(report.projectCount || 0)" /></el-col>
        <el-col :xs="12" :sm="8" :md="4"><el-statistic title="物理表" :value="Number(report.tableCount || 0)" /></el-col>
        <el-col :xs="12" :sm="8" :md="4"><el-statistic title="数据库" :value="Number(report.databaseCount || 0)" /></el-col>
        <el-col :xs="12" :sm="8" :md="4"><el-statistic title="存储(GB)" :value="Number(report.storageGb || 0)" :precision="2" /></el-col>
        <el-col :xs="12" :sm="8" :md="4"><el-statistic title="热门表" :value="Number(report.hotTableCount || 0)" /></el-col>
        <el-col :xs="12" :sm="8" :md="4"><el-statistic title="稽核表" :value="Number(report.auditTableCount || 0)" /></el-col>
      </el-row>

      <el-row v-if="report" :gutter="12" class="stat-row" style="margin-top:12px">
        <el-col :xs="12" :sm="8" :md="6">
          <el-statistic title="脚本总数" :value="Number(report.scriptCount || 0)" />
          <div class="sub-stat">已关联工作流 {{ Number(report.scriptLinkedWorkflowCount || 0) }}</div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6">
          <el-statistic title="工作流总数" :value="Number(report.workflowCount || 0)" />
          <div class="sub-stat">已调度 {{ Number(report.workflowScheduledCount || 0) }}</div>
        </el-col>
        <el-col :xs="12" :sm="8" :md="6"><el-statistic title="接入任务" :value="Number(report.taskCount || 0)" /></el-col>
      </el-row>

      <el-row v-if="report" :gutter="16" style="margin-top:20px">
        <el-col :span="12">
          <h4 class="chart-title">表新增趋势</h4>
          <div ref="tableTrendRef" class="chart-box" />
        </el-col>
        <el-col :span="12">
          <h4 class="chart-title">数据存储量趋势 (GB)</h4>
          <div ref="storageTrendRef" class="chart-box" />
        </el-col>
      </el-row>

      <el-row v-if="report" :gutter="12" style="margin-top:16px">
        <el-col :span="8">
          <h4 class="chart-title">热门项目 Top5（按表数）</h4>
          <el-table :data="topProjects" stripe size="small" @row-click="openProject">
            <el-table-column prop="projectName" label="项目" min-width="100" show-overflow-tooltip />
            <el-table-column prop="tableCount" label="表数" width="60" />
            <el-table-column label="存储(GB)" width="90">
              <template #default="{ row }">{{ Number(row.storageGb || 0).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
        </el-col>
        <el-col :span="8">
          <h4 class="chart-title">脚本运行时长 Top5</h4>
          <el-table :data="topScripts" stripe size="small" @row-click="openScript">
            <el-table-column prop="scriptName" label="脚本" min-width="110" show-overflow-tooltip />
            <el-table-column label="时长(秒)" width="80">
              <template #default="{ row }">{{ Number(row.durationSec || 0).toFixed(1) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
            </el-table-column>
          </el-table>
        </el-col>
        <el-col :span="8">
          <h4 class="chart-title">表存储量 Top5</h4>
          <el-table :data="topTables" stripe size="small" @row-click="openTable">
            <el-table-column prop="tableName" label="表名" min-width="110" show-overflow-tooltip />
            <el-table-column label="存储(GB)" width="90">
              <template #default="{ row }">{{ Number(row.storageGb || 0).toFixed(2) }}</template>
            </el-table-column>
            <el-table-column prop="columnCount" label="字段" width="60" />
          </el-table>
        </el-col>
      </el-row>

      <el-row v-if="report" :gutter="12" style="margin-top:16px">
        <el-col :span="12">
          <h4 class="chart-title">工作流</h4>
          <el-table :data="workflows" stripe size="small" @row-click="openWorkflow">
            <el-table-column prop="taskName" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
            </el-table-column>
            <el-table-column label="调度" width="70">
              <template #default="{ row }">{{ row.scheduleEnabled ? '是' : '否' }}</template>
            </el-table-column>
          </el-table>
        </el-col>
        <el-col :span="12">
          <h4 class="chart-title">TOP 接入任务</h4>
          <el-table :data="topTasks" stripe size="small">
            <el-table-column prop="taskName" label="任务" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </PageCard>

    <ProjectTablesDrawer
      v-model="projectDrawer"
      :project-id="projectId"
      :project-name="projectName"
      @open-table="(id: number) => { tableId = id; tableDrawer = true }"
    />
    <TableAssetDetailDrawer v-model="tableDrawer" :table-id="tableId" />
    <ScriptAssetDetailDrawer v-model="scriptDrawer" :script-id="scriptId" />
    <WorkflowAssetDetailDrawer v-model="workflowDrawer" :workflow-id="workflowId" />
  </div>
</template>

<style scoped>
.hint { font-size: 13px; color: #606266; margin: 0 0 16px; line-height: 1.6; }
.chart-title { margin: 0 0 8px; font-size: 14px; color: #303133; }
.chart-box { height: 180px; background: #f5f7fa; border-radius: 6px; }
.stat-row :deep(.el-statistic__head) { font-size: 12px; color: #909399; }
.sub-stat { font-size: 12px; color: #909399; margin-top: 4px; }
:deep(.el-table__row) { cursor: pointer; }
</style>
