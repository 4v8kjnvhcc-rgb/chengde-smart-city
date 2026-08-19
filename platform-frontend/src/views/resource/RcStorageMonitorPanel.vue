<script setup lang="ts">
/**
 * 执行策略监控：备份/归档/销毁产物与运行结果融合单页，筛选 + 分页 + 日志详情
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'

interface Artifact {
  id: number
  artifactType?: string
  physicalTable?: string
  fileName?: string
  filePath?: string
  storageLocation?: string
  rowCount?: number
  status?: string
  message?: string
  createdAt?: string
}

interface PolicyRun {
  id: number
  policyId?: number
  actionType?: string
  runStatus?: string
  rowCount?: number
  storageLocation?: string
  message?: string
  createdBy?: string
  createdAt?: string
}

interface Policy {
  id: number
  policyName: string
}

type RecordKind = 'BACKUP_ART' | 'ARCHIVE_ART' | 'DESTROY_ART' | 'RUN'

interface MonitorRow {
  key: string
  recordKind: RecordKind
  actionType: string
  title: string
  physicalTable: string
  storageLocation: string
  rowCount?: number
  status: string
  message: string
  createdBy?: string
  createdAt?: string
  artifactId?: number
  runId?: number
  policyId?: number
}

const RECORD_KIND_ZH: Record<RecordKind, string> = {
  BACKUP_ART: '备份产物',
  ARCHIVE_ART: '归档产物',
  DESTROY_ART: '销毁记录',
  RUN: '运行结果',
}

const loading = ref(false)
const artifacts = ref<Artifact[]>([])
const policyRuns = ref<PolicyRun[]>([])
const policies = ref<Policy[]>([])

const detailVisible = ref(false)
const detailRow = ref<MonitorRow | null>(null)

const query = reactive({
  keyword: '',
  recordKind: '' as '' | RecordKind,
  actionType: '',
  status: '',
})
const applied = reactive({
  keyword: '',
  recordKind: '' as '' | RecordKind,
  actionType: '',
  status: '',
})

function policyName(id?: number) {
  if (!id) return '-'
  return policies.value.find((p) => p.id === id)?.policyName || `策略#${id}`
}

const allRows = computed((): MonitorRow[] => {
  const rows: MonitorRow[] = []
  for (const a of artifacts.value) {
    const type = (a.artifactType || 'BACKUP').toUpperCase()
    const st = String(a.status || '').toUpperCase()
    let kind: RecordKind
    let action: string
    if (type === 'DESTROY' || st === 'DESTROYED') {
      kind = 'DESTROY_ART'
      action = 'DESTROY'
    } else if (type === 'ARCHIVE') {
      kind = 'ARCHIVE_ART'
      action = 'ARCHIVE'
    } else {
      kind = 'BACKUP_ART'
      action = 'BACKUP'
    }
    rows.push({
      key: `art-${a.id}`,
      recordKind: kind,
      actionType: action,
      title: a.fileName || a.physicalTable || `产物#${a.id}`,
      physicalTable: a.physicalTable || '-',
      storageLocation: a.storageLocation || a.filePath || '-',
      rowCount: a.rowCount,
      status: a.status || '-',
      message: a.message || '',
      createdAt: a.createdAt,
      artifactId: a.id,
    })
  }
  for (const r of policyRuns.value) {
    rows.push({
      key: `run-${r.id}`,
      recordKind: 'RUN',
      actionType: (r.actionType || '').toUpperCase(),
      title: policyName(r.policyId),
      physicalTable: '-',
      storageLocation: r.storageLocation || '-',
      rowCount: r.rowCount,
      status: r.runStatus || '-',
      message: r.message || '',
      createdBy: r.createdBy,
      createdAt: r.createdAt,
      runId: r.id,
      policyId: r.policyId,
    })
  }
  rows.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
  return rows
})

const filteredRows = computed(() => {
  const kw = applied.keyword.trim().toLowerCase()
  return allRows.value.filter((row) => {
    if (applied.recordKind && row.recordKind !== applied.recordKind) return false
    if (applied.actionType && row.actionType !== applied.actionType) return false
    if (applied.status && String(row.status).toUpperCase() !== applied.status.toUpperCase()) return false
    if (!kw) return true
    return (
      row.title.toLowerCase().includes(kw)
      || row.physicalTable.toLowerCase().includes(kw)
      || row.storageLocation.toLowerCase().includes(kw)
      || row.message.toLowerCase().includes(kw)
      || RECORD_KIND_ZH[row.recordKind].includes(kw)
    )
  })
})

const {
  page,
  pageSize,
  paged,
  total,
  resetPage,
} = useClientPager(filteredRows)

watch(applied, () => resetPage(), { deep: true })

async function reload() {
  loading.value = true
  try {
    try {
      await api.post('/resource-center/platform/lifecycle/sync-artifacts')
    } catch { /* 同步失败仍拉列表 */ }
    const [arts, runs, pols] = await Promise.all([
      api.get('/resource-center/platform/backups/artifacts'),
      api.get('/resource-center/platform/policies/runs'),
      api.get('/resource-center/platform/policies'),
    ])
    artifacts.value = arts.data || []
    policyRuns.value = runs.data || []
    policies.value = pols.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载监控数据失败')
  } finally {
    loading.value = false
  }
}

function doQuery() {
  applied.keyword = query.keyword
  applied.recordKind = query.recordKind
  applied.actionType = query.actionType
  applied.status = query.status
}

function doReset() {
  query.keyword = ''
  query.recordKind = ''
  query.actionType = ''
  query.status = ''
  applied.keyword = ''
  applied.recordKind = ''
  applied.actionType = ''
  applied.status = ''
}

function openLogDetail(row: MonitorRow) {
  detailRow.value = row
  detailVisible.value = true
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading" class="mon">
    <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="doQuery">
      <el-form-item label="关键字" class="portal-field-lg">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="名称 / 表 / 位置 / 说明"
          @keyup.enter="doQuery"
        />
      </el-form-item>
      <el-form-item label="记录类型" class="portal-field-md">
        <el-select v-model="query.recordKind" clearable placeholder="全部">
          <el-option label="备份产物" value="BACKUP_ART" />
          <el-option label="归档产物" value="ARCHIVE_ART" />
          <el-option label="销毁记录" value="DESTROY_ART" />
          <el-option label="运行结果" value="RUN" />
        </el-select>
      </el-form-item>
      <el-form-item label="动作" class="portal-field-sm">
        <el-select v-model="query.actionType" clearable placeholder="全部">
          <el-option label="备份" value="BACKUP" />
          <el-option label="归档" value="ARCHIVE" />
          <el-option label="销毁" value="DESTROY" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" class="portal-field-sm">
        <el-select v-model="query.status" clearable placeholder="全部">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="已销毁" value="DESTROYED" />
          <el-option label="台账" value="LEDGER" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="doQuery">查询</el-button>
        <el-button @click="doReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="paged" stripe border class="portal-table" size="small">
      <el-table-column label="记录类型" width="110">
        <template #default="{ row }">{{ RECORD_KIND_ZH[row.recordKind as RecordKind] }}</template>
      </el-table-column>
      <el-table-column label="动作" width="90">
        <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
      </el-table-column>
      <el-table-column prop="title" label="名称/策略" min-width="160" show-overflow-tooltip />
      <el-table-column prop="physicalTable" label="表" width="140" show-overflow-tooltip />
      <el-table-column prop="storageLocation" label="存储位置" min-width="200" show-overflow-tooltip />
      <el-table-column prop="rowCount" label="行数" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) || '-' }}</template>
      </el-table-column>
      <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openLogDetail(row)">日志详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    <el-empty v-if="!loading && !filteredRows.length" description="暂无监控记录" />

    <el-dialog
      v-model="detailVisible"
      title="日志详情"
      width="640px"
      destroy-on-close
    >
      <el-descriptions v-if="detailRow" :column="1" border size="small">
        <el-descriptions-item label="记录类型">
          {{ RECORD_KIND_ZH[detailRow.recordKind] }}
        </el-descriptions-item>
        <el-descriptions-item label="动作">
          {{ statusLabel(detailRow.actionType) }}
        </el-descriptions-item>
        <el-descriptions-item label="名称/策略">{{ detailRow.title }}</el-descriptions-item>
        <el-descriptions-item label="表">{{ detailRow.physicalTable }}</el-descriptions-item>
        <el-descriptions-item label="存储位置">{{ detailRow.storageLocation }}</el-descriptions-item>
        <el-descriptions-item label="行数">{{ detailRow.rowCount ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(detailRow.status)" size="small">
            {{ statusLabel(detailRow.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="时间">
          {{ formatDateTime(detailRow.createdAt) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item v-if="detailRow.createdBy" label="执行人">
          {{ detailRow.createdBy }}
        </el-descriptions-item>
        <el-descriptions-item label="说明">
          <div class="log-detail-msg">{{ detailRow.message || '无详细说明' }}</div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.log-detail-msg {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
}
</style>
