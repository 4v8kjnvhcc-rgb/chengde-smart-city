<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { ingestionApi } from '../../useIngestionHub'

const props = defineProps<{ modelValue: boolean; tableId: number | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const loading = ref(false)
const detail = ref<Record<string, unknown> | null>(null)
const activeTab = ref('columns')
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const columns = computed(() => (detail.value?.columns as Record<string, unknown>[]) || [])
const partitions = computed(() => (detail.value?.partitions as Record<string, unknown>[]) || [])
const storage = computed(() => (detail.value?.storage as Record<string, unknown>) || {})
const lineage = computed(() => (detail.value?.lineage as { upstream?: Record<string, unknown>[]; downstream?: Record<string, unknown>[] }) || {})
const outputTrend = computed(() => (detail.value?.outputTrend as { date?: string; increment?: number; total?: number }[]) || [])
const changes = computed(() => (detail.value?.changes as Record<string, unknown>[]) || [])
const advanced = computed(() => (detail.value?.advancedSettings as Record<string, unknown>) || {})

async function load() {
  if (!props.tableId) return
  loading.value = true
  try {
    detail.value = (await ingestionApi.assetReportTableDetail(props.tableId)).data
    activeTab.value = 'columns'
    await nextTick()
    renderChart()
  } catch {
    ElMessage.error('加载表详情失败')
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
    legend: { data: ['日增量(KB)', '总存储(MB)'] },
    grid: { left: 48, right: 16, top: 36, bottom: 28 },
    xAxis: { type: 'category', data: outputTrend.value.map((d) => d.date || '') },
    yAxis: { type: 'value' },
    series: [
      { name: '日增量(KB)', type: 'line', smooth: true, data: outputTrend.value.map((d) => Number(d.increment || 0)) },
      { name: '总存储(MB)', type: 'line', smooth: true, data: outputTrend.value.map((d) => Number(d.total || 0)) },
    ],
  })
}

watch(() => [props.modelValue, props.tableId], ([open]) => {
  if (open) void load()
  else {
    chart?.dispose()
    chart = null
  }
})

watch(activeTab, async (tab) => {
  if (tab === 'output') {
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
    :title="detail ? `表详情 · ${detail.tableName || detail.tableCode}` : '表详情'"
    size="720px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <el-tabs v-if="detail" v-model="activeTab">
        <el-tab-pane label="字段信息" name="columns">
          <el-table :data="columns" stripe size="small">
            <el-table-column prop="columnCode" label="字段名" width="120" show-overflow-tooltip />
            <el-table-column prop="dataType" label="字段类型" width="110" />
            <el-table-column label="分区字段" width="80">
              <template #default="{ row }">{{ row.partitionCol ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="columnName" label="描述" min-width="140" show-overflow-tooltip />
          </el-table>
          <el-empty v-if="!columns.length" description="暂无字段" />
        </el-tab-pane>
        <el-tab-pane label="分区信息" name="partitions">
          <el-alert v-if="!detail.partitionFlag" type="info" :closable="false" title="当前表非分区表" style="margin-bottom:8px" />
          <el-table :data="partitions" stripe size="small">
            <el-table-column prop="partitionName" label="分区" min-width="120" />
            <el-table-column prop="storageBytes" label="存储量(字节)" width="120" />
            <el-table-column prop="fileCount" label="文件数" width="80" />
          </el-table>
          <el-empty v-if="!partitions.length" description="暂无分区数据" />
        </el-tab-pane>
        <el-tab-pane label="存储信息" name="storage">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="当前存储量">{{ Number(storage.storageGb || 0).toFixed(4) }} GB（{{ storage.storageBytes || 0 }} 字节）</el-descriptions-item>
            <el-descriptions-item label="今日新增">{{ storage.storageBytesToday || 0 }} 字节</el-descriptions-item>
            <el-descriptions-item label="数据变更时间">{{ storage.dataChangedAt || '—' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="血缘关系" name="lineage">
          <h4 class="sub">上游关系</h4>
          <el-table :data="lineage.upstream || []" stripe size="small" style="margin-bottom:12px">
            <el-table-column prop="label" label="来源" min-width="140" />
            <el-table-column prop="node" label="节点" width="120" />
            <el-table-column prop="edgeType" label="类型" width="90" />
          </el-table>
          <el-empty v-if="!(lineage.upstream || []).length" description="暂无上游血缘" :image-size="48" />
          <h4 class="sub">下游关系</h4>
          <el-table :data="lineage.downstream || []" stripe size="small">
            <el-table-column prop="label" label="去向" min-width="140" />
            <el-table-column prop="node" label="节点" width="120" />
            <el-table-column prop="edgeType" label="类型" width="90" />
          </el-table>
          <el-empty v-if="!(lineage.downstream || []).length" description="暂无下游血缘" :image-size="48" />
        </el-tab-pane>
        <el-tab-pane label="产出信息" name="output">
          <div ref="chartRef" class="chart" />
          <el-empty v-if="!outputTrend.length" description="暂无近7天产出数据" />
        </el-tab-pane>
        <el-tab-pane label="变更信息" name="changes">
          <el-timeline>
            <el-timeline-item v-for="(c, i) in changes" :key="i" :timestamp="String(c.changeAt || '')">
              {{ c.summary }}（{{ $statusLabel(c.changeType) }}）{{ c.versionLabel ? ` · ${c.versionLabel}` : '' }}
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!changes.length" description="近7天无变更记录" />
        </el-tab-pane>
        <el-tab-pane label="DDL语句" name="ddl">
          <pre class="code">{{ detail.ddlSql || '暂无 DDL' }}</pre>
        </el-tab-pane>
        <el-tab-pane label="高级设置" name="advanced">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="表所在地址">{{ advanced.location || '—' }}</el-descriptions-item>
            <el-descriptions-item label="数据存储格式">{{ advanced.storageFormat || '—' }}</el-descriptions-item>
            <el-descriptions-item label="存储类型">{{ advanced.storageType || '—' }}</el-descriptions-item>
            <el-descriptions-item label="列分隔符">{{ advanced.fieldDelimiter || '—' }}</el-descriptions-item>
            <el-descriptions-item label="列转义符">{{ advanced.escapeChar || '—' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<style scoped>
.sub { margin: 0 0 8px; font-size: 13px; font-weight: 600; }
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
}
</style>
