<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import {
  BASE_STAT_TOPICS,
  DOMAIN_STAT_TOPICS,
  resolveApplicationNav,
} from './application-nav'

interface StatMetric {
  id: number
  metricCode: string
  metricName: string
  domainType: string
  metricValue: string
  trendPct?: number
  drillRoute?: string
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
/** base | domain */
const domainTab = ref<'base' | 'domain'>('base')
const topicKey = ref('pop-structure')
const metrics = ref<StatMetric[]>([])
const loadedDomains = new Set<string>()

const topics = computed(() =>
  domainTab.value === 'domain' ? DOMAIN_STAT_TOPICS : BASE_STAT_TOPICS,
)

const activeTopic = computed(() => topics.value.find((t) => t.key === topicKey.value))

const topicMetrics = computed(() => {
  const codes = (activeTopic.value as { metricCodes?: readonly string[] })?.metricCodes
  if (codes?.length) return metrics.value.filter((m) => codes.includes(m.metricCode))
  if (domainTab.value === 'base') {
    return metrics.value.filter((m) => ['POP_BASE', 'HUKOU_BASE', 'GDP_BASE'].includes(m.metricCode))
  }
  return metrics.value
})

const topicDescription = computed(() => {
  const descriptions: Record<number, string> = {
    52: '通过年龄分布、性别比例、职业分布等指标，了解人口结构特点。',
    53: '通过年度人口增长率和人口迁移数据，分析人口增长的趋势。',
    54: '通过人口迁徙数据和人口流动模式，分析人口流动情况。',
    55: '比较户籍人口和常住人口数量及分布的差异。',
    56: '分析不同时间段的GDP增长率，了解经济发展趋势。',
    57: '通过各产业增加值的占比，分析产业结构变化情况。',
    58: '分析就业率和失业率的变化，了解劳动力市场。',
    59: '分析参保人群特征、城乡参保率差异与变化趋势。',
    60: '统计高龄津贴人群分布，分析发放标准与政策效果。',
    61: '统计特困与低保人口分布，比较地区与群体差异。',
    62: '统计行政许可与处罚情况，分析类型、频率与规律。',
  }
  return descriptions[activeTopic.value?.rowIndex || 0] || ''
})

function syncRoute() {
  const r = resolveApplicationNav(route.query as Record<string, unknown>)
  const sec = r.section
  if (sec === 'domain' || DOMAIN_STAT_TOPICS.some((t) => t.key === sec)) {
    domainTab.value = 'domain'
    topicKey.value = DOMAIN_STAT_TOPICS.some((t) => t.key === sec) ? sec : 'insurance'
  } else if (sec === 'base' || BASE_STAT_TOPICS.some((t) => t.key === sec)) {
    domainTab.value = 'base'
    topicKey.value = BASE_STAT_TOPICS.some((t) => t.key === sec) ? sec : 'pop-structure'
  } else {
    domainTab.value = 'base'
    topicKey.value = 'pop-structure'
  }
}

function setDomainTab(tab: 'base' | 'domain') {
  domainTab.value = tab
  topicKey.value = tab === 'domain' ? 'insurance' : 'pop-structure'
  router.replace({ query: { ...route.query, system: 'stats', module: 'stats', section: tab } })
  loadModule(true)
}

function setTopic(key: string) {
  topicKey.value = key
  router.replace({ query: { ...route.query, system: 'stats', module: 'stats', section: key } })
}

async function loadModule(force = false) {
  const cacheKey = domainTab.value
  if (!force && loadedDomains.has(cacheKey)) return
  loading.value = true
  try {
    const path = domainTab.value === 'domain'
      ? '/exchange/application/stats/domain'
      : '/exchange/application/stats/base'
    metrics.value = (await api.get(path)).data
    loadedDomains.add(cacheKey)
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

watch(() => [route.query.system, route.query.section, route.query.tab], () => {
  const prev = domainTab.value
  syncRoute()
  if (domainTab.value !== prev || !loadedDomains.has(domainTab.value)) loadModule()
})
onMounted(() => { syncRoute(); loadModule() })
</script>

<template>
  <div v-loading="loading">
    <el-radio-group :model-value="domainTab" style="margin-bottom:12px" @change="(v: string) => setDomainTab(v as 'base' | 'domain')">
      <el-radio-button value="base">基础库统计分析</el-radio-button>
      <el-radio-button value="domain">重点领域统计分析</el-radio-button>
    </el-radio-group>
    <el-radio-group :model-value="topicKey" style="margin-bottom:12px" @change="setTopic">
      <el-radio-button v-for="t in topics" :key="t.key" :value="t.key">{{ t.label }}</el-radio-button>
    </el-radio-group>

    <PageCard :title="activeTopic?.label || '统计分析'">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" :title="topicDescription" />
      <el-row :gutter="16">
        <el-col v-for="m in topicMetrics" :key="m.id" :span="8">
          <el-card shadow="hover" class="metric-card">
            <div class="metric-name">{{ m.metricName }}</div>
            <div class="metric-value">{{ m.metricValue }}</div>
            <div v-if="m.trendPct != null" class="metric-trend" :class="{ up: Number(m.trendPct) >= 0 }">
              环比 {{ m.trendPct }}%
            </div>
            <el-button v-if="m.drillRoute" link type="primary" @click="$router.push(m.drillRoute!)">下钻分析</el-button>
          </el-card>
        </el-col>
        <el-col v-if="!topicMetrics.length" :span="24">
          <el-empty description="演示环境：该专题指标待接入基础库/重点领域数据源" />
        </el-col>
      </el-row>
      <el-table v-if="topicMetrics.length" :data="topicMetrics" stripe size="small" style="margin-top:16px">
        <el-table-column prop="metricName" label="指标" min-width="140" />
        <el-table-column prop="metricValue" label="当前值" width="120" />
        <el-table-column prop="trendPct" label="环比%" width="90" />
        <el-table-column prop="drillRoute" label="下钻路由" min-width="160" />
      </el-table>
    </PageCard>
  </div>
</template>

<style scoped>
.metric-card { margin-bottom: 12px; min-height: 120px; }
.metric-name { font-size: 14px; color: #606266; }
.metric-value { font-size: 22px; font-weight: 600; margin: 8px 0; color: #409eff; }
.metric-trend { font-size: 12px; color: #f56c6c; }
.metric-trend.up { color: #67c23a; }
</style>
