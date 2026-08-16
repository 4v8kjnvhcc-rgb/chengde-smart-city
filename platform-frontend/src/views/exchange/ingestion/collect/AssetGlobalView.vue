<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import PageCard from '@/components/common/PageCard.vue'
import api from '@/api/http'
import { statusLabel } from '@/utils/status-label'

const BASE = '/exchange/ingestion/global-asset-view'
const router = useRouter()
const loading = ref(false)
const tab = ref('overview')
const panorama = ref<Record<string, unknown> | null>(null)
const topAssets = ref<Record<string, unknown>[]>([])
const trends = ref<Record<string, unknown> | null>(null)
const lineage = ref<Record<string, unknown> | null>(null)
const facets = ref<Record<string, unknown> | null>(null)
const detail = ref<Record<string, unknown> | null>(null)
const projectId = ref<number | undefined>()

const trendChartRef = ref<HTMLDivElement | null>(null)
let trendChart: echarts.ECharts | null = null

const kpis = computed(() => (panorama.value?.kpis as Record<string, unknown>) || {})
const drillLinks = computed(() => (panorama.value?.drillLinks as Record<string, unknown>[]) || [])
const trendSeries = computed(() => (trends.value?.series as Record<string, unknown>[]) || [])
const bySource = computed(() => Object.entries((facets.value?.bySource as Record<string, number>) || {}).map(([name, count]) => ({ name, count })))
const byLevel = computed(() => Object.entries((facets.value?.byLevel as Record<string, number>) || {}).map(([code, count]) => ({ code, count })))
const byDim = computed(() => Object.entries((facets.value?.byTagDim as Record<string, number>) || {}).map(([code, count]) => ({ code, count })))
const projects = computed(() => (facets.value?.projects as Record<string, unknown>[]) || [])
const lineageNodes = computed(() => (lineage.value?.nodes as Record<string, unknown>[]) || [])
const lineageEdges = computed(() => (lineage.value?.edges as Record<string, unknown>[]) || [])

function disposeTrendChart() {
  trendChart?.dispose()
  trendChart = null
}

function renderTrendChart() {
  if (!trendChartRef.value || tab.value !== 'trends') return
  const series = trendSeries.value
  const days = series.map((r) => String(r.day || ''))
  const newTables = series.map((r) => Number(r.newTables || 0))
  const searches = series.map((r) => Number(r.searches || 0))
  const newMarks = series.map((r) => Number(r.newMarks || 0))
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['新登记表', '检索次数', '新分级标注'] },
    grid: { left: 48, right: 24, top: 48, bottom: 48 },
    xAxis: {
      type: 'category',
      data: days,
      axisLabel: { rotate: days.length > 10 ? 30 : 0 },
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '新登记表', type: 'bar', data: newTables, barMaxWidth: 28 },
      { name: '检索次数', type: 'bar', data: searches, barMaxWidth: 28 },
      { name: '新分级标注', type: 'bar', data: newMarks, barMaxWidth: 28 },
    ],
  })
  trendChart.resize()
}

async function loadOverview() {
  panorama.value = (await api.get(`${BASE}/panorama`)).data
}
async function loadTop() {
  topAssets.value = (await api.get(`${BASE}/top`, { params: { limit: 15 } })).data || []
}
async function loadTrends() {
  trends.value = (await api.get(`${BASE}/trends`, { params: { days: 14 } })).data
}
async function loadFacets() {
  facets.value = (await api.get(`${BASE}/facets`)).data
}
async function loadLineage() {
  lineage.value = (await api.get(`${BASE}/lineage`, { params: { projectId: projectId.value } })).data
}

async function reload() {
  loading.value = true
  try {
    if (tab.value === 'overview') await Promise.all([loadOverview(), loadFacets()])
    else if (tab.value === 'top') await loadTop()
    else if (tab.value === 'trends') await loadTrends()
    else if (tab.value === 'lineage') await Promise.all([loadFacets(), loadLineage()])
  } catch (e: unknown) {
    console.error(e)
  } finally {
    loading.value = false
    if (tab.value === 'trends') {
      await nextTick()
      renderTrendChart()
    } else {
      disposeTrendChart()
    }
  }
}

async function onTab(name: string | number) {
  tab.value = String(name)
  await reload()
}

function goModule(mod: string) {
  router.push({ path: '/exchange/ingestion', query: { system: 'collect', module: mod } })
}

async function openDetail(row: Record<string, unknown>) {
  if (row.assetType !== 'TABLE') return
  detail.value = (await api.get(`${BASE}/assets/TABLE/${row.assetId}`)).data
  tab.value = 'detail'
}

function onResize() {
  trendChart?.resize()
}

watch(trendSeries, () => {
  if (tab.value === 'trends') void nextTick(() => renderTrendChart())
})

onMounted(() => {
  void reload()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  disposeTrendChart()
})
</script>

<template>
  <div v-loading="loading">
    <PageCard title="全局数据资产视图">
      <el-tabs :model-value="tab" @tab-change="onTab">
        <el-tab-pane label="全景总览" name="overview" />
        <el-tab-pane label="资产 TOP" name="top" />
        <el-tab-pane label="趋势" name="trends" />
        <el-tab-pane label="血缘快照" name="lineage" />
        <el-tab-pane v-if="detail" label="资产详情" name="detail" />
      </el-tabs>

      <div v-if="tab === 'overview'">
        <el-row :gutter="12" class="mb">
          <el-col :span="4"><el-statistic title="项目" :value="Number(kpis.projects || 0)" /></el-col>
          <el-col :span="4"><el-statistic title="数据源" :value="Number(kpis.dataSources || 0)" /></el-col>
          <el-col :span="4"><el-statistic title="登记表" :value="Number(kpis.tables || 0)" /></el-col>
          <el-col :span="4"><el-statistic title="接入通道" :value="Number(kpis.channels || 0)" /></el-col>
          <el-col :span="4"><el-statistic title="已发布编目" :value="Number(kpis.publishedRegistries || 0)" /></el-col>
          <el-col :span="4"><el-statistic title="血缘边" :value="Number(kpis.lineageEdges || 0)" /></el-col>
        </el-row>
        <el-row :gutter="12" class="mb">
          <el-col :span="4"><el-statistic title="分级覆盖率%" :value="Number(kpis.classifyCoveragePct || 0)" /></el-col>
          <el-col :span="4"><el-statistic title="已分级标注" :value="Number(kpis.markedAssets || 0)" /></el-col>
          <el-col :span="4"><el-statistic title="标签覆盖率%" :value="Number(kpis.tagCoveragePct || 0)" /></el-col>
          <el-col :span="4"><el-statistic title="已打标表" :value="Number(kpis.taggedTables || 0)" /></el-col>
          <el-col :span="4"><el-statistic title="搜索文档" :value="Number(kpis.searchDocs || 0)" /></el-col>
          <el-col :span="4"><el-statistic title="脱敏策略" :value="Number(kpis.maskPolicies || 0)" /></el-col>
        </el-row>
        <el-tag class="mb" type="info">搜索引擎：{{ kpis.searchEngine || '—' }}</el-tag>

        <div class="block-title">能力下钻</div>
        <el-row :gutter="12" class="mb">
          <el-col v-for="link in drillLinks" :key="String(link.key)" :span="8" class="mb">
            <el-card shadow="hover" class="drill-card" @click="goModule(String(link.module))">
              <div class="drill-title">{{ link.label }}</div>
              <div class="drill-desc">{{ link.desc }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <div class="block-title">按数据源</div>
            <el-table :data="bySource" size="small" max-height="280" stripe>
              <el-table-column prop="name" label="数据源" />
              <el-table-column prop="count" label="表数" width="80" />
            </el-table>
          </el-col>
          <el-col :span="8">
            <div class="block-title">按敏感级别</div>
            <el-table :data="byLevel" size="small" max-height="280" stripe>
              <el-table-column label="级别" width="120">
                <template #default="{ row }">{{ statusLabel(row.code) }}</template>
              </el-table-column>
              <el-table-column prop="count" label="标注数" width="90" />
            </el-table>
          </el-col>
          <el-col :span="8">
            <div class="block-title">按标签维度</div>
            <el-table :data="byDim" size="small" max-height="280" stripe>
              <el-table-column label="维度" width="120">
                <template #default="{ row }">{{ statusLabel(row.code) }}</template>
              </el-table-column>
              <el-table-column prop="count" label="标签数" width="90" />
            </el-table>
          </el-col>
        </el-row>
      </div>

      <div v-if="tab === 'top'">
        <el-table :data="topAssets" stripe @row-click="openDetail">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ statusLabel(row.assetType) }}</template>
          </el-table-column>
          <el-table-column prop="assetName" label="名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="assetCode" label="编码" width="140" show-overflow-tooltip />
          <el-table-column prop="physicalTable" label="物理表" width="140" show-overflow-tooltip />
          <el-table-column prop="columnCount" label="字段数" width="80" />
          <el-table-column prop="tagBindingCount" label="标签数" width="80" />
          <el-table-column label="级别" width="100">
            <template #default="{ row }">{{ row.levelCode ? statusLabel(row.levelCode) : '—' }}</template>
          </el-table-column>
          <el-table-column prop="hotScore" label="热度" width="70" />
          <el-table-column prop="rankScore" label="综合分" width="90" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.assetType === 'TABLE'" link type="primary" @click.stop="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="tab === 'trends'">
        <div class="block-title">近 {{ trends?.days || 14 }} 日：新登记表 / 检索次数 / 新分级标注</div>
        <div ref="trendChartRef" class="trend-chart" />
        <el-empty v-if="!trendSeries.length" description="暂无趋势数据" />
      </div>

      <div v-if="tab === 'lineage'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="项目" class="portal-field-xl">
            <el-select v-model="projectId" clearable placeholder="默认首个项目" @change="loadLineage">
              <el-option v-for="p in projects" :key="String(p.id)" :label="String(p.name)" :value="p.id as number" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadLineage">刷新血缘</el-button>
            <el-button @click="router.push({ path: '/exchange/ingestion', query: { system: 'register', module: 'm047' } })">打开登记侧血缘页</el-button>
          </el-form-item>
        </el-form>
        <el-descriptions v-if="lineage" :column="3" border size="small" class="mb">
          <el-descriptions-item label="模式">{{ statusLabel(String(lineage.mode)) }}</el-descriptions-item>
          <el-descriptions-item label="节点">{{ lineageNodes.length }}</el-descriptions-item>
          <el-descriptions-item label="边">{{ lineageEdges.length }}</el-descriptions-item>
        </el-descriptions>
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="block-title">节点（最多展示）</div>
            <el-table :data="lineageNodes.slice(0, 40)" size="small" max-height="360" stripe>
              <el-table-column prop="id" label="ID" width="120" show-overflow-tooltip />
              <el-table-column label="类型" width="90">
                <template #default="{ row }">{{ statusLabel(row.type) }}</template>
              </el-table-column>
              <el-table-column prop="label" label="名称" min-width="140" show-overflow-tooltip />
            </el-table>
          </el-col>
          <el-col :span="12">
            <div class="block-title">边</div>
            <el-table :data="lineageEdges.slice(0, 40)" size="small" max-height="360" stripe>
              <el-table-column prop="fromNode" label="从" width="120" show-overflow-tooltip />
              <el-table-column prop="toNode" label="到" width="120" show-overflow-tooltip />
              <el-table-column label="类型" width="100">
                <template #default="{ row }">{{ statusLabel(row.edgeType || row.label) }}</template>
              </el-table-column>
            </el-table>
          </el-col>
        </el-row>
      </div>

      <div v-if="tab === 'detail' && detail">
        <el-empty v-if="detail.found === false" description="资产不存在" />
        <template v-else>
          <el-descriptions :column="2" border size="small" class="mb">
            <el-descriptions-item label="表名">{{ (detail.table as Record<string, unknown>)?.tableName }}</el-descriptions-item>
            <el-descriptions-item label="编码">{{ (detail.table as Record<string, unknown>)?.tableCode }}</el-descriptions-item>
            <el-descriptions-item label="物理表">{{ (detail.table as Record<string, unknown>)?.physicalTableName }}</el-descriptions-item>
            <el-descriptions-item label="Schema">{{ (detail.table as Record<string, unknown>)?.sourceSchema }}</el-descriptions-item>
            <el-descriptions-item label="级别">
              {{ (detail.classify as Record<string, unknown>)?.levelCode ? statusLabel(String((detail.classify as Record<string, unknown>).levelCode)) : '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(String((detail.table as Record<string, unknown>)?.status || '')) }}</el-descriptions-item>
          </el-descriptions>
          <div class="block-title">已挂标签</div>
          <el-tag v-for="t in (detail.tags as Record<string, unknown>[]) || []" :key="String(t.tagId)" class="tag-chip">
            {{ t.tagName }}
          </el-tag>
          <div class="block-title mt">下钻操作</div>
          <el-button v-for="l in (detail.drillLinks as Record<string, unknown>[]) || []" :key="String(l.key)" class="mr" @click="goModule(String(l.module))">
            {{ l.label }}
          </el-button>
          <el-button type="primary" @click="goModule('asset.search')">去数据搜索定位</el-button>
        </template>
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; line-height: 1.5; }
.mb { margin-bottom: 12px; }
.mt { margin-top: 12px; }
.mr { margin-right: 8px; margin-bottom: 8px; }
.block-title { font-weight: 600; margin: 8px 0; }
.drill-card { cursor: pointer; min-height: 72px; }
.drill-title { font-weight: 600; margin-bottom: 4px; }
.drill-desc { color: var(--el-text-color-secondary); font-size: 12px; }
.tag-chip { margin: 0 6px 6px 0; }
.trend-chart { width: 100%; height: 420px; }
</style>
