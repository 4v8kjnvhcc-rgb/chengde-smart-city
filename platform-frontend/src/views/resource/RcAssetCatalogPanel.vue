<script setup lang="ts">
/**
 * 资源中心 · 资产目录（公开/未公开、审批、驱动交换）
 * mode=mount 子系统未公开目录挂载；approve 公开审批；exchange 按公开目录驱动交换
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/http'
import { formatDateTime } from '@/utils/datetime'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { useAuthStore } from '@/stores/auth'

const props = withDefaults(defineProps<{ mode: 'mount' | 'approve' | 'exchange' }>(), {
  mode: 'mount',
})

const auth = useAuthStore()
const isAdmin = computed(() => auth.isSystemAdmin)

type CatalogEntry = {
  id: number
  entryCode: string
  entryName: string
  managedTableId?: number
  physicalTable?: string
  subsystemCode?: string
  subsystemName?: string
  visibility?: string
  encryptEnabled?: boolean
  encryptAlgo?: string
  publishStatus?: string
  rejectReason?: string
  description?: string
  driveTask?: string
  exchangeTaskRef?: string
  lastExchangeAt?: string
  lastExchangeMessage?: string
  status?: string
}
type CatalogSubsystem = { code: string; name: string }
type CatalogExchangeJob = {
  id: number
  catalogEntryId: number
  jobCode: string
  jobName: string
  physicalTable?: string
  rowCount?: number
  runStatus: string
  message?: string
  createdBy?: string
  createdAt?: string
}
type ManagedTable = { id: number; physicalTable: string; metaEntryCode?: string; status?: string }

const loading = ref(false)
const entries = ref<CatalogEntry[]>([])
const subsystems = ref<CatalogSubsystem[]>([])
const exchangeJobs = ref<CatalogExchangeJob[]>([])
const managedTables = ref<ManagedTable[]>([])

const filter = reactive({
  q: '',
  visibility: '',
  subsystem: '',
  publishStatus: '',
})

const form = reactive({
  managedTableId: undefined as number | undefined,
  entryName: '',
  subsystemCode: 'RESOURCE',
  encryptEnabled: false,
  encryptAlgo: 'AES256',
  description: '',
})

const visibilityLabel = (v?: string) => {
  if (v === 'PUBLIC') return '公开（多子系统共享）'
  if (v === 'PRIVATE') return '未公开（子系统私有）'
  return statusLabel(v) || '—'
}

async function loadSubsystems() {
  if (subsystems.value.length) return
  subsystems.value = (await api.get('/resource-center/platform/catalog/subsystems')).data || []
}

async function loadManagedTables() {
  if (props.mode !== 'mount') return
  managedTables.value = (await api.get('/resource-center/platform/managed-tables')).data || []
}

async function loadEntries() {
  loading.value = true
  try {
    const params: Record<string, string> = {}
    if (props.mode === 'approve') {
      // 待审 + 已公开（便于管理员下线）
      if (filter.publishStatus) params.publishStatus = filter.publishStatus
      else params.publishStatus = 'PENDING_REVIEW'
    } else if (props.mode === 'exchange') {
      params.visibility = 'PUBLIC'
      params.publishStatus = 'PUBLISHED'
    } else {
      if (filter.q.trim()) params.q = filter.q.trim()
      if (filter.visibility) params.visibility = filter.visibility
      if (filter.subsystem) params.subsystem = filter.subsystem
      if (filter.publishStatus) params.publishStatus = filter.publishStatus
    }
    entries.value = (await api.get('/resource-center/platform/catalog/entries', { params })).data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载目录失败')
  } finally {
    loading.value = false
  }
}

async function loadJobs() {
  if (props.mode !== 'exchange') return
  exchangeJobs.value = (await api.get('/resource-center/platform/catalog/exchange-jobs')).data || []
}

function resetFilter() {
  filter.q = ''
  filter.visibility = ''
  filter.subsystem = ''
  filter.publishStatus = props.mode === 'approve' ? 'PENDING_REVIEW' : ''
  void loadEntries()
}

async function createEntry() {
  if (!form.managedTableId) {
    ElMessage.warning('请选择已纳管表创建子系统目录，禁止仅填名称')
    return
  }
  const mt = managedTables.value.find((t) => t.id === form.managedTableId)
  try {
    await api.post('/resource-center/platform/catalog/entries', {
      managedTableId: form.managedTableId,
      entryName: form.entryName.trim() || mt?.physicalTable,
      subsystemCode: form.subsystemCode,
      encryptEnabled: form.encryptEnabled,
      encryptAlgo: form.encryptEnabled ? form.encryptAlgo : 'NONE',
      description: form.description.trim() || undefined,
    })
    ElMessage.success('已登记为未公开目录（归属所选子系统）')
    form.entryName = ''
    form.managedTableId = undefined
    form.description = ''
    form.encryptEnabled = false
    await loadEntries()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '登记失败')
  }
}

async function saveEncrypt(row: CatalogEntry) {
  try {
    await api.put(`/resource-center/platform/catalog/entries/${row.id}/encrypt`, {
      encryptEnabled: !!row.encryptEnabled,
      encryptAlgo: row.encryptEnabled ? (row.encryptAlgo || 'AES256') : 'NONE',
    })
    ElMessage.success('加密控制已更新')
    await loadEntries()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  }
}

async function submitPublish(id: number) {
  try {
    await api.post(`/resource-center/platform/catalog/entries/${id}/submit-publish`)
    ElMessage.success('已提交公开审批，等待系统管理员审核')
    await loadEntries()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  }
}

async function approvePublish(id: number) {
  try {
    await api.post(`/resource-center/platform/catalog/entries/${id}/approve-publish`)
    ElMessage.success('已公开：各子系统可共享该目录，并可驱动数据交换')
    await loadEntries()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '审批失败')
  }
}

async function rejectPublish(id: number) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回公开申请', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '驳回原因不能为空',
    })
    await api.post(`/resource-center/platform/catalog/entries/${id}/reject-publish`, { reason: value })
    ElMessage.success('已驳回，目录保持未公开')
    await loadEntries()
  } catch { /* cancel */ }
}

async function unpublish(id: number) {
  try {
    await ElMessageBox.confirm('确认将该目录从公开资源目录下线？下线后各子系统不再共享。', '下线公开', {
      type: 'warning',
    })
    await api.post(`/resource-center/platform/catalog/entries/${id}/unpublish`)
    ElMessage.success('已下线为未公开目录')
    await loadEntries()
  } catch { /* cancel */ }
}

async function driveOne(id: number) {
  try {
    const res = await api.post(`/resource-center/platform/catalog/entries/${id}/drive-exchange`)
    ElMessage.success(String(res.data?.message || '交换任务已生成并完成'))
    await Promise.all([loadEntries(), loadJobs()])
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '驱动失败')
  }
}

async function syncFromPublished() {
  try {
    const res = await api.post('/resource-center/platform/catalog/entries/sync-from-published')
    const d = res.data || {}
    ElMessage.success(
      `已同步公开资源目录：新建 ${d.created ?? 0}，更新 ${d.updated ?? 0}，跳过 ${d.skipped ?? 0}`,
    )
    await loadEntries()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '同步失败')
  }
}

async function driveAll() {
  try {
    const res = await api.post('/resource-center/platform/catalog/entries/drive-exchange-batch')
    const d = res.data || {}
    const sync = d.syncedFromGov as Record<string, number> | undefined
    ElMessage.success(
      `批量驱动完成：成功 ${d.success ?? 0} / 共 ${d.total ?? 0}`
        + (sync ? `（先同步公开目录 +${sync.created ?? 0}/~${sync.updated ?? 0}）` : ''),
    )
    await Promise.all([loadEntries(), loadJobs()])
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '批量驱动失败')
  }
}

async function reload() {
  await loadSubsystems()
  await loadManagedTables()
  await loadEntries()
  await loadJobs()
}

watch(() => props.mode, () => {
  void reload()
})

onMounted(() => {
  void reload()
})
</script>

<template>
  <div v-loading="loading" class="rc-catalog-panel">
    <!-- 挂载：子系统未公开目录 -->
    <template v-if="mode === 'mount'">
      <p class="hint">
        各子系统维护各自<strong>未公开目录</strong>（默认私有）；需跨系统共享时提交公开审批。
        须选择已纳管表挂载，禁止仅填名称。
      </p>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="纳管表" class="portal-field-xl">
          <el-select v-model="form.managedTableId" filterable clearable placeholder="请选择">
            <el-option
              v-for="t in managedTables"
              :key="t.id"
              :label="`${t.physicalTable}${t.metaEntryCode ? '（' + t.metaEntryCode + '）' : ''}`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目录名" class="portal-field-lg">
          <el-input v-model="form.entryName" placeholder="可空，默认表名" />
        </el-form-item>
        <el-form-item label="所属子系统" class="portal-field-lg">
          <el-select v-model="form.subsystemCode">
            <el-option v-for="s in subsystems" :key="s.code" :label="s.name" :value="s.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="加密">
          <el-switch v-model="form.encryptEnabled" />
        </el-form-item>
        <el-form-item v-if="form.encryptEnabled" label="算法" class="portal-field-sm">
          <el-select v-model="form.encryptAlgo">
            <el-option label="AES256" value="AES256" />
            <el-option label="SM4" value="SM4" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="createEntry">挂载未公开目录</el-button>
        </el-form-item>
      </el-form>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="关键词" class="portal-field-md">
          <el-input v-model="filter.q" clearable placeholder="编码/名称" />
        </el-form-item>
        <el-form-item label="可见性" class="portal-field-md">
          <el-select v-model="filter.visibility" clearable placeholder="全部">
            <el-option label="未公开" value="PRIVATE" />
            <el-option label="公开" value="PUBLIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="子系统" class="portal-field-lg">
          <el-select v-model="filter.subsystem" clearable placeholder="全部">
            <el-option v-for="s in subsystems" :key="s.code" :label="s.name" :value="s.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" class="portal-field-md">
          <el-select v-model="filter.publishStatus" clearable placeholder="全部">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="待审核" value="PENDING_REVIEW" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="loadEntries">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="entries" stripe border class="portal-table">
        <el-table-column prop="entryCode" label="编码" width="140" show-overflow-tooltip />
        <el-table-column prop="entryName" label="目录名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="physicalTable" label="纳管表" width="140" show-overflow-tooltip />
        <el-table-column prop="subsystemName" label="所属子系统" width="160" show-overflow-tooltip />
        <el-table-column label="可见性" width="150">
          <template #default="{ row }">{{ visibilityLabel(row.visibility) }}</template>
        </el-table-column>
        <el-table-column label="加密" width="120">
          <template #default="{ row }">
            <el-switch
              v-model="row.encryptEnabled"
              @change="saveEncrypt(row)"
            />
            <span v-if="row.encryptEnabled" class="muted">{{ row.encryptAlgo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="公开状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.publishStatus)" size="small">{{ statusLabel(row.publishStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.publishStatus === 'DRAFT' || row.publishStatus === 'REJECTED'"
              link
              type="primary"
              @click="submitPublish(row.id)"
            >提交公开</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
    </template>

    <!-- 公开审批 -->
    <template v-else-if="mode === 'approve'">
      <p class="hint">
        目录公开及审批由<strong>系统管理员</strong>控制。通过后进入「共享公开目录」，各门户子系统共用同一套公开资源目录。
      </p>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="状态" class="portal-field-md">
          <el-select v-model="filter.publishStatus" clearable placeholder="待审核" @change="loadEntries">
            <el-option label="待审核" value="PENDING_REVIEW" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="loadEntries">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="!isAdmin"
        type="info"
        :closable="false"
        show-icon
        title="当前账号非系统管理员：可查看待审列表，批准/驳回/下线需系统管理员操作。"
        style="margin-bottom:12px"
      />
      <el-table :data="entries" stripe border class="portal-table">
        <el-table-column prop="entryCode" label="编码" width="140" show-overflow-tooltip />
        <el-table-column prop="entryName" label="目录名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="physicalTable" label="纳管表" width="140" show-overflow-tooltip />
        <el-table-column prop="subsystemName" label="申请子系统" width="160" show-overflow-tooltip />
        <el-table-column label="加密" width="100">
          <template #default="{ row }">{{ row.encryptEnabled ? (row.encryptAlgo || '已启用') : '否' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.publishStatus)" size="small">{{ statusLabel(row.publishStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rejectReason" label="驳回原因" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.publishStatus === 'PENDING_REVIEW'">
              <el-button link type="primary" :disabled="!isAdmin" @click="approvePublish(row.id)">批准公开</el-button>
              <el-button link type="danger" :disabled="!isAdmin" @click="rejectPublish(row.id)">驳回</el-button>
            </template>
            <el-button
              v-else-if="row.publishStatus === 'PUBLISHED'"
              link
              type="warning"
              :disabled="!isAdmin"
              @click="unpublish(row.id)"
            >下线公开</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!entries.length" description="暂无待审或已选状态的目录" />
    </template>

    <!-- 驱动交换 -->
    <template v-else>
      <p class="hint">
        读取<strong>已公开目录</strong>（含资源编目审批通过后同步的公开资源）生成交换任务并自动完成台账交换。
      </p>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="driveAll">按公开目录批量驱动交换</el-button>
          <el-button @click="syncFromPublished">同步公开资源目录</el-button>
          <el-button @click="reload">刷新</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="entries" stripe border class="portal-table" style="margin-bottom:16px">
        <el-table-column prop="entryCode" label="编码" width="140" show-overflow-tooltip />
        <el-table-column prop="entryName" label="公开目录" min-width="140" show-overflow-tooltip />
        <el-table-column prop="physicalTable" label="纳管表" width="140" show-overflow-tooltip />
        <el-table-column prop="subsystemName" label="子系统" width="140" show-overflow-tooltip />
        <el-table-column prop="driveTask" label="驱动任务" min-width="140" show-overflow-tooltip />
        <el-table-column label="最近交换" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastExchangeAt) }}</template>
        </el-table-column>
        <el-table-column prop="lastExchangeMessage" label="交换结果" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="driveOne(row.id)">生成并交换</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!entries.length" description="暂无公开目录。请先在资源编目审批通过或挂载后批准公开，再点「同步公开资源目录」。" />

      <el-divider content-position="left">交换任务台账</el-divider>
      <el-table :data="exchangeJobs" stripe border class="portal-table">
        <el-table-column prop="jobCode" label="任务编码" width="180" show-overflow-tooltip />
        <el-table-column prop="jobName" label="任务名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="physicalTable" label="源表" width="140" show-overflow-tooltip />
        <el-table-column prop="rowCount" label="行数" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.runStatus)" size="small">{{ statusLabel(row.runStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="createdBy" label="操作人" width="100" />
      </el-table>
    </template>
  </div>
</template>

<style scoped>
.hint {
  color: var(--el-text-color-secondary);
  margin: 0 0 12px;
  line-height: 1.55;
}
.muted {
  margin-left: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
