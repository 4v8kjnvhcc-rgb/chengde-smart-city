<script setup lang="ts">
/**
 * 执行策略管理：发布 / 执行 / 暂停
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'
import { useExecCycleLabel } from '@/utils/exec-cycle-label'

interface Policy {
  id: number
  policyName: string
  policyCode?: string
  actionType: string
  retentionDays?: number
  storageStrategy?: string
  scheduleEnabled?: number
  scheduleCron?: string
  nextRunAt?: string
  lastRunAt?: string
  lastRunStatus?: string
  lastRunMessage?: string
  status?: string
}

const { label: cycleLabel } = useExecCycleLabel()

const STRATEGY_ZH: Record<string, string> = {
  DB: '备份库快照',
  LOCAL: '本地目录',
  NAS: 'NAS存储',
  OBJECT: '对象存储',
}

const loading = ref(false)
const policies = ref<Policy[]>([])
const query = reactive({
  keyword: '',
  actionType: '',
})
const applied = reactive({
  keyword: '',
  actionType: '',
})
const actingId = ref<number | null>(null)

const filtered = computed(() => {
  const kw = applied.keyword.trim().toLowerCase()
  const act = applied.actionType
  return policies.value.filter((p) => {
    if (act && p.actionType !== act) return false
    if (!kw) return true
    return (
      (p.policyName || '').toLowerCase().includes(kw)
      || (p.policyCode || '').toLowerCase().includes(kw)
    )
  })
})

const {
  page,
  pageSize,
  paged,
  total,
  resetPage,
} = useClientPager(filtered)

watch(applied, () => resetPage(), { deep: true })

const stats = computed(() => {
  const all = policies.value
  return {
    total: all.length,
    backup: all.filter((p) => p.actionType === 'BACKUP').length,
    archive: all.filter((p) => p.actionType === 'ARCHIVE').length,
    destroy: all.filter((p) => p.actionType === 'DESTROY').length,
    published: all.filter((p) => !!p.scheduleEnabled).length,
  }
})

function strategyLabel(code?: string) {
  if (!code) return '-'
  return STRATEGY_ZH[code] || statusLabel(code)
}

function isPublished(row: Policy) {
  return !!row.scheduleEnabled
}

async function reload() {
  loading.value = true
  try {
    policies.value = (await api.get('/resource-center/platform/policies')).data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载策略失败')
  } finally {
    loading.value = false
  }
}

function doQuery() {
  applied.keyword = query.keyword
  applied.actionType = query.actionType
}

function doReset() {
  query.keyword = ''
  query.actionType = ''
  applied.keyword = ''
  applied.actionType = ''
}

/** 发布：按策略已配置的执行周期启动定时执行 */
async function publishPolicy(row: Policy) {
  if (!row.scheduleCron || !String(row.scheduleCron).trim()) {
    ElMessage.warning('该策略未配置执行周期，请先编辑策略')
    return
  }
  actingId.value = row.id
  try {
    await api.put(`/resource-center/platform/policies/${row.id}/schedule`, {
      scheduleEnabled: true,
      scheduleCron: row.scheduleCron,
    })
    const res = await api.post(`/resource-center/platform/policies/${row.id}/schedule/start`)
    ElMessage.success(String(res.data?.message || `已发布，将按「${cycleLabel(row.scheduleCron)}」定时执行`))
    await reload()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '发布失败')
  } finally {
    actingId.value = null
  }
}

/** 暂停：停止周期调度 */
async function pausePolicy(row: Policy) {
  try {
    await ElMessageBox.confirm(`确认暂停策略「${row.policyName}」的周期执行？`, '暂停确认', { type: 'warning' })
  } catch {
    return
  }
  actingId.value = row.id
  try {
    const res = await api.post(`/resource-center/platform/policies/${row.id}/schedule/stop`)
    ElMessage.success(String(res.data?.message || '已暂停'))
    await reload()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '暂停失败')
  } finally {
    actingId.value = null
  }
}

async function runPolicy(row: Policy) {
  actingId.value = row.id
  try {
    const res = await api.post(`/resource-center/platform/policies/${row.id}/execute`)
    const st = String(res.data?.status || '')
    if (st === 'LEDGER') {
      ElMessage.warning(String(res.data?.message || '已记台账，未改物理数据'))
    } else {
      ElMessage.success(String(res.data?.message || '执行完成'))
    }
    await reload()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '执行失败')
  } finally {
    actingId.value = null
  }
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading" class="sched">
    <div class="sched-stats">
      <div class="sched-stat">
        <div class="sched-stat__n">{{ stats.total }}</div>
        <div class="sched-stat__l">策略总数</div>
      </div>
      <div class="sched-stat">
        <div class="sched-stat__n">{{ stats.backup }}</div>
        <div class="sched-stat__l">备份</div>
      </div>
      <div class="sched-stat">
        <div class="sched-stat__n">{{ stats.archive }}</div>
        <div class="sched-stat__l">归档</div>
      </div>
      <div class="sched-stat">
        <div class="sched-stat__n">{{ stats.destroy }}</div>
        <div class="sched-stat__l">销毁</div>
      </div>
      <div class="sched-stat sched-stat--accent">
        <div class="sched-stat__n">{{ stats.published }}</div>
        <div class="sched-stat__l">已发布</div>
      </div>
    </div>

    <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="doQuery">
      <el-form-item label="关键字" class="portal-field-lg">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="策略名 / 编码"
          @keyup.enter="doQuery"
        />
      </el-form-item>
      <el-form-item label="动作" class="portal-field-sm">
        <el-select v-model="query.actionType" clearable placeholder="全部">
          <el-option label="备份" value="BACKUP" />
          <el-option label="归档" value="ARCHIVE" />
          <el-option label="销毁" value="DESTROY" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="doQuery">查询</el-button>
        <el-button @click="doReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="paged" stripe border class="portal-table" size="small">
      <el-table-column prop="policyCode" label="编码" width="150" show-overflow-tooltip />
      <el-table-column prop="policyName" label="策略" min-width="160" show-overflow-tooltip />
      <el-table-column label="动作" width="90">
        <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
      </el-table-column>
      <el-table-column prop="retentionDays" label="保存天数" width="90" />
      <el-table-column label="存储策略" width="110">
        <template #default="{ row }">{{ strategyLabel(row.storageStrategy) }}</template>
      </el-table-column>
      <el-table-column label="执行周期" width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ cycleLabel(row.scheduleCron) || '-' }}</template>
      </el-table-column>
      <el-table-column label="发布状态" width="90">
        <template #default="{ row }">
          <el-tag :type="isPublished(row) ? 'success' : 'info'" size="small">
            {{ isPublished(row) ? '已发布' : '未发布' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下次执行" width="170">
        <template #default="{ row }">
          {{ isPublished(row) ? (formatDateTime(row.nextRunAt) || '-') : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="最近状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.lastRunStatus" :type="statusTagType(row.lastRunStatus)" size="small">
            {{ statusLabel(row.lastRunStatus) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="isPublished(row)"
            :loading="actingId === row.id && !isPublished(row)"
            @click="publishPolicy(row)"
          >发布</el-button>
          <el-button
            link
            type="success"
            :loading="actingId === row.id"
            @click="runPolicy(row)"
          >执行</el-button>
          <el-button
            link
            type="warning"
            :disabled="!isPublished(row)"
            :loading="actingId === row.id && isPublished(row)"
            @click="pausePolicy(row)"
          >暂停</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    <el-empty v-if="!loading && !filtered.length" description="暂无策略，请先在数据备份/归档/销毁中新增" />
  </div>
</template>

<style scoped>
.sched-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.sched-stat {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  padding: 12px 14px;
  text-align: center;
}
.sched-stat--accent {
  border-color: color-mix(in srgb, var(--el-color-primary) 35%, var(--el-border-color-lighter));
  background: color-mix(in srgb, var(--el-color-primary) 6%, #fff);
}
.sched-stat__n {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--el-text-color-primary);
}
.sched-stat__l {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
@media (max-width: 1100px) {
  .sched-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
