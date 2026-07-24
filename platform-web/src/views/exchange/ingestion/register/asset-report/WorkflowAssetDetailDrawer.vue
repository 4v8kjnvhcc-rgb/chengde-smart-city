<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { ingestionApi } from '../../useIngestionHub'

const props = defineProps<{ modelValue: boolean; workflowId: number | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const loading = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
const activeTab = ref('overview')
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const monitorVisible = ref(false)
const monitorLoading = ref(false)
const monitor = ref<Record<string, unknown> | null>(null)

const runtimeTrend = computed(() => (detail.value?.runtimeTrend as { startedAt?: string; durationSec?: number }[]) || [])
const instances = computed(() => (detail.value?.instances as Record<string, unknown>[]) || [])
const changes = computed(() => (detail.value?.changes as Record<string, unknown>[]) || [])
const monitorNodes = computed(() => (monitor.value?.nodes as Record<string, unknown>[]) || [])

async function load() {
  if (!props.workflowId) return
  loading.value = true
  try {
    detail.value = (await ingestionApi.assetReportWorkflowDetail(props.workflowId)).data
    activeTab.value = 'overview'
    await nextTick()
    renderChart()
  } catch {
    ElMessage.error('加载工作流详情失败')
    detail.value = null
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 16, top: 24, bottom: 40 },
    xAxis: {
      type: 'category',
      data: runtimeTrend.value.map((d) => String(d.startedAt || '').slice(0, 16)),
      axisLabel: { rotate: 30, fontSize: 10 },
    },
    yAxis: { type: 'value', name: '秒' },
    series: [{
      type: 'line',
      smooth: true,
      data: runtimeTrend.value.map((d) => Number(d.durationSec || 0)),
      itemStyle: { color: '#409eff' },
    }],
  })
}

async function openMonitor(row: Record<string, unknown>) {
  monitorVisible.value = true
  monitorLoading.value = true
  try {
    monitor.value = (await ingestionApi.assetReportWorkflowRunMonitor(Number(row.id))).data
  } catch {
    ElMessage.error('加载运行监控失败')
    monitor.value = null
  } finally {
    monitorLoading.value = false
  }
}

watch(() => [props.modelValue, props.workflowId], ([open]) => {
  if (open) void load()
  else {
    chart?.dispose()
    chart = null
  }
})

watch(activeTab, async (tab) => {
  if (tab === 'runtime') {
    await nextTick()
    renderChart()
  }
})

onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="detail ? `工作流详情 · ${detail.taskName || detail.taskCode}` : '工作流详情'"
    size="720px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <el-tabs v-if="detail" v-model="activeTab">
        <el-tab-pane label="概要" name="overview">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="名称">{{ detail.taskName }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ $statusLabel(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ detail.createdBy || '—' }}</el-descriptions-item>
            <el-descriptions-item label="所属项目">{{ detail.projectName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="调度">{{ detail.scheduleEnabled ? '已调度' : '未调度' }}</el-descriptions-item>
            <el-descriptions-item label="Cron">{{ detail.scheduleCron || '—' }}</el-descriptions-item>
            <el-descriptions-item label="说明" :span="2">{{ detail.description || '—' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="运行情况" name="runtime">
          <p class="tip">近 7 天工作流运行时长趋势与实例记录</p>
          <div ref="chartRef" class="chart" />
          <el-table :data="instances" stripe size="small" style="margin-top:12px">
            <el-table-column label="开始时间" min-width="150">
              <template #default="{ row }">{{ row.startedAt || '—' }}</template>
            </el-table-column>
            <el-table-column label="结束时间" min-width="150">
              <template #default="{ row }">{{ row.endedAt || '—' }}</template>
            </el-table-column>
            <el-table-column label="时长(秒)" width="90">
              <template #default="{ row }">{{ (Number(row.durationMs || 0) / 1000).toFixed(1) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openMonitor(row)">监控</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!instances.length" description="近7天无运行实例" />
        </el-tab-pane>
        <el-tab-pane label="变更信息" name="changes">
          <el-timeline>
            <el-timeline-item v-for="(c, i) in changes" :key="i" :timestamp="String(c.changeAt || '')">
              {{ c.summary }}（{{ $statusLabel(c.changeType) }}）{{ c.versionLabel ? ` · ${c.versionLabel}` : '' }}
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!changes.length" description="近7天无变更记录" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-drawer v-model="monitorVisible" title="运行实例监控" size="480px" append-to-body>
      <div v-loading="monitorLoading">
        <el-table :data="monitorNodes" stripe size="small">
          <el-table-column prop="nodeName" label="节点" min-width="100" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ $statusLabel(row.nodeType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column prop="outputRows" label="输出行" width="70" />
          <el-table-column prop="message" label="信息" min-width="120" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!monitorLoading && !monitorNodes.length" description="暂无节点监控数据" />
      </div>
    </el-drawer>
  </el-drawer>
</template>

<style scoped>
.tip { font-size: 12px; color: #909399; margin: 0 0 8px; }
.chart { height: 200px; background: #f5f7fa; border-radius: 6px; }
</style>
