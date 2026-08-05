<script setup lang="ts">
/**
 * 资源监控管理：可用性 / 完整性 / 安全性 / 性能
 */
import { computed, onMounted, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface Metric {
  id?: number
  metricKey: string
  metricCategory?: string
  metricLabel: string
  metricValue: string
  metricUnit?: string
  resourceType?: string
  alertLevel: string
  checkedAt?: string
}

interface Channel {
  channelType?: string
  jobCode?: string
  jobName?: string
  physicalTable?: string
  rowCount?: number
  runStatus?: string
  message?: string
  createdAt?: string
}

interface AuditRow {
  action?: string
  username?: string
  resourceType?: string
  resourceId?: string
  detail?: string
  ipAddress?: string
  createdAt?: string
}

interface IntegritySample {
  id: number
  physicalTable?: string
  fileName?: string
  rowCount?: number
  byteSize?: number
  sha256?: string
  status?: string
  fileExists?: boolean
  createdAt?: string
}

const loading = ref(false)
const refreshing = ref(false)
const monitorTab = ref('AVAILABILITY')
const overview = ref<Record<string, unknown> | null>(null)

const summary = computed(() => (overview.value?.summary as Record<string, unknown>) || {})
const byCategory = computed(() => (overview.value?.byCategory as Record<string, Metric[]>) || {})
const channels = computed(() => (overview.value?.channels as Channel[]) || [])
const audits = computed(() => (overview.value?.audits as AuditRow[]) || [])
const integritySamples = computed(() => (overview.value?.integritySamples as IntegritySample[]) || [])
const currentMetrics = computed(() => byCategory.value[monitorTab.value] || [])
const hint = computed(() => String(overview.value?.hint || ''))
const checkedAt = computed(() => overview.value?.checkedAt ? String(overview.value.checkedAt) : '-')

const CATEGORIES = [
  { key: 'AVAILABILITY', label: '可用性监控', desc: '数据库服务、存储设备、数据传输通道是否可达' },
  { key: 'INTEGRITY', label: '完整性监控', desc: '传输/存储过程校验，防止丢失与损坏' },
  { key: 'SECURITY', label: '安全性监控', desc: '加密、权限可见性、访问审计与异常探测' },
  { key: 'PERFORMANCE', label: '性能监控', desc: '响应时间、传输行量、存储容量与策略执行' },
] as const

function healthOf(cat: string) {
  return String(summary.value[cat] || 'WARN')
}

function formatValue(row: Metric) {
  const unit = row.metricUnit ? ` ${row.metricUnit}` : ''
  const raw = row.metricValue
  if (['UP', 'DOWN', 'IDLE', 'LOCAL_FALLBACK', 'LEDGER', 'PASS', 'FAIL'].includes(String(raw))) {
    return statusLabel(raw) + unit
  }
  return `${raw}${unit}`
}

function resourceTypeLabel(t?: string) {
  if (!t) return '-'
  return statusLabel(t)
}

async function loadOverview() {
  loading.value = true
  try {
    const res = await api.get('/resource-center/platform/monitor/overview')
    overview.value = res.data || null
    const metrics = (overview.value?.metrics as Metric[]) || []
    if (!metrics.length) {
      const refreshed = await api.post('/resource-center/platform/monitor/refresh')
      overview.value = refreshed.data || null
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载监控失败')
  } finally {
    loading.value = false
  }
}

async function refresh() {
  refreshing.value = true
  try {
    const res = await api.post('/resource-center/platform/monitor/refresh')
    overview.value = res.data || null
    ElMessage.success('监控指标已刷新')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '刷新失败')
  } finally {
    refreshing.value = false
  }
}

onMounted(loadOverview)
</script>

<template>
  <div v-loading="loading">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom:12px"
      :title="hint || '对数据资源进行可用性、完整性、安全性与性能的实时监控'"
    />

    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="最近采集">
        <span>{{ checkedAt }}</span>
      </el-form-item>
      <el-form-item label="预警">
        <el-tag type="warning" size="small">{{ summary.warnCount ?? 0 }}</el-tag>
      </el-form-item>
      <el-form-item label="严重">
        <el-tag type="danger" size="small">{{ summary.criticalCount ?? 0 }}</el-tag>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" :loading="refreshing" @click="refresh">刷新监控</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="12" style="margin-bottom:12px">
      <el-col v-for="c in CATEGORIES" :key="c.key" :span="6">
        <div
          class="rc-monitor-card"
          :class="{ active: monitorTab === c.key }"
          @click="monitorTab = c.key"
        >
          <div class="rc-monitor-card__head">
            <span>{{ c.label }}</span>
            <el-tag :type="statusTagType(healthOf(c.key))" size="small">
              {{ statusLabel(healthOf(c.key)) }}
            </el-tag>
          </div>
          <div class="rc-monitor-card__desc">{{ c.desc }}</div>
          <div class="rc-monitor-card__count">
            {{ (byCategory[c.key] || []).length }} 项指标
          </div>
        </div>
      </el-col>
    </el-row>

    <el-tabs v-model="monitorTab">
      <el-tab-pane
        v-for="c in CATEGORIES"
        :key="c.key"
        :label="c.label"
        :name="c.key"
      >
        <el-table :data="currentMetrics" stripe size="small">
          <el-table-column prop="metricLabel" label="监控项" min-width="160" />
          <el-table-column label="资源类型" width="110">
            <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
          </el-table-column>
          <el-table-column label="当前值" min-width="180">
            <template #default="{ row }">{{ formatValue(row) }}</template>
          </el-table-column>
          <el-table-column label="级别" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.alertLevel)" size="small">
                {{ statusLabel(row.alertLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="checkedAt" label="采集时间" width="170" />
        </el-table>
        <el-empty v-if="!currentMetrics.length" description="暂无指标，请点击「刷新监控」采集" />
      </el-tab-pane>
    </el-tabs>

    <template v-if="monitorTab === 'AVAILABILITY'">
      <el-divider content-position="left">数据传输通道（目录交换 / 备份传输）</el-divider>
      <el-table :data="channels" stripe size="small">
        <el-table-column label="通道类型" width="140">
          <template #default="{ row }">{{ statusLabel(row.channelType) }}</template>
        </el-table-column>
        <el-table-column prop="jobCode" label="任务编码" width="140" show-overflow-tooltip />
        <el-table-column prop="jobName" label="任务" min-width="140" show-overflow-tooltip />
        <el-table-column prop="physicalTable" label="表" width="140" show-overflow-tooltip />
        <el-table-column prop="rowCount" label="行数" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.runStatus)" size="small">
              {{ statusLabel(row.runStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
      <el-empty v-if="!channels.length" description="暂无通道运行记录" />
    </template>

    <template v-if="monitorTab === 'INTEGRITY'">
      <el-divider content-position="left">备份产物完整性样本</el-divider>
      <el-table :data="integritySamples" stripe size="small">
        <el-table-column prop="physicalTable" label="表" width="160" />
        <el-table-column prop="fileName" label="文件" min-width="160" show-overflow-tooltip />
        <el-table-column prop="rowCount" label="行数" width="90" />
        <el-table-column label="SHA-256" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sha256 || '未登记' }}</template>
        </el-table-column>
        <el-table-column label="文件存在" width="100">
          <template #default="{ row }">
            <el-tag :type="row.fileExists ? 'success' : 'danger'" size="small">
              {{ row.fileExists ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!integritySamples.length" description="暂无备份产物，可在存储管理执行备份后复核" />
    </template>

    <template v-if="monitorTab === 'SECURITY'">
      <el-divider content-position="left">资源访问审计（最近）</el-divider>
      <el-table :data="audits" stripe size="small">
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="username" label="用户" width="110" />
        <el-table-column prop="action" label="动作" width="160" show-overflow-tooltip />
        <el-table-column prop="resourceType" label="资源类型" width="140" show-overflow-tooltip />
        <el-table-column prop="resourceId" label="资源ID" width="90" />
        <el-table-column prop="ipAddress" label="IP" width="130" />
        <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!audits.length" description="暂无资源中心审计记录" />
    </template>
  </div>
</template>

<style scoped>
.rc-monitor-card {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 12px;
  cursor: pointer;
  background: var(--el-bg-color);
  min-height: 96px;
  transition: border-color 0.15s ease;
}
.rc-monitor-card:hover,
.rc-monitor-card.active {
  border-color: var(--el-color-primary);
}
.rc-monitor-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 6px;
}
.rc-monitor-card__desc {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
  margin-bottom: 8px;
}
.rc-monitor-card__count {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
</style>
