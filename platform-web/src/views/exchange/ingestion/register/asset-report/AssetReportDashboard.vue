<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
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
    report.value = (await ingestionApi.assetReport()).data
  })
  await nextTick()
  renderCharts()
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

function openTable(row: Record<string, unknown>) {
  tableId.value = Number(row.tableId)
  tableDrawer.value = true
}

function openScript(row: Record<string, unknown>) {
  scriptId.value = Number(row.scriptId)
  scriptDrawer.value = true
}

function openWorkflow(row: Record<string, unknown>) {
  workflowId.value = Number(row.id)
  workflowDrawer.value = true
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
      <p class="hint">全可视化展示平台数据资产：项目、表、脚本、工作流及存储与增长趋势；支持 TOP 下钻至详情。</p>

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
          <h4 class="chart-title">热门项目 Top5（按表存储量）</h4>
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
