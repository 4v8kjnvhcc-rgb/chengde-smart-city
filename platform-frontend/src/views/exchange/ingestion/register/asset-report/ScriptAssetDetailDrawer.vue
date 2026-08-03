<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { ingestionApi } from '../../useIngestionHub'

const props = defineProps<{ modelValue: boolean; scriptId: number | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const loading = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
const activeTab = ref('runtime')
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const runtimeTrend = computed(() => (detail.value?.runtimeTrend as { startedAt?: string; durationSec?: number; status?: string }[]) || [])
const changes = computed(() => (detail.value?.changes as Record<string, unknown>[]) || [])

async function load() {
  if (!props.scriptId) return
  loading.value = true
  try {
    detail.value = (await ingestionApi.assetReportScriptDetail(props.scriptId)).data
    activeTab.value = 'runtime'
    await nextTick()
    renderChart()
  } catch {
    ElMessage.error('加载脚本详情失败')
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
      itemStyle: { color: '#e6a23c' },
    }],
  })
}

watch(() => [props.modelValue, props.scriptId], ([open]) => {
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
    :title="detail ? `脚本详情 · ${detail.scriptName || detail.scriptCode}` : '脚本详情'"
    size="680px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <el-descriptions v-if="detail" :column="2" border size="small" style="margin-bottom:12px">
        <el-descriptions-item label="编码">{{ detail.scriptCode }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ $statusLabel(detail.scriptType) }}</el-descriptions-item>
        <el-descriptions-item label="发布状态">{{ $statusLabel(detail.publishStatus) }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.versionNo }}</el-descriptions-item>
      </el-descriptions>
      <el-tabs v-if="detail" v-model="activeTab">
        <el-tab-pane label="运行情况" name="runtime">
          <p class="tip">默认展示近 7 天脚本运行时长趋势</p>
          <div ref="chartRef" class="chart" />
          <el-empty v-if="!runtimeTrend.length" description="近7天无运行记录" />
        </el-tab-pane>
        <el-tab-pane label="脚本代码" name="code">
          <p class="tip">当前展示已发布（稳定）版本代码</p>
          <pre class="code">{{ detail.publishedCode || '暂无代码' }}</pre>
        </el-tab-pane>
        <el-tab-pane label="变更信息" name="changes">
          <el-timeline>
            <el-timeline-item v-for="(c, i) in changes" :key="i" :timestamp="String(c.changeAt || c.publishedAt || '')">
              {{ c.summary }}（{{ $statusLabel(c.changeType) }}）{{ c.versionLabel ? ` · ${c.versionLabel}` : '' }}
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!changes.length" description="近7天无变更记录" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<style scoped>
.tip { font-size: 12px; color: #909399; margin: 0 0 8px; }
.chart { height: 220px; background: #f5f7fa; border-radius: 6px; }
.code {
  margin: 0;
  padding: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 6px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 480px;
  overflow: auto;
}
</style>
