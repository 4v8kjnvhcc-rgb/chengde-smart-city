<script setup lang="ts">
/**
 * 资源中心存储生命周期：备份 / 归档 / 销毁（同一套 rc policies API）。
 * 归集「数据资产管理」与资源中心存储管理可复用；销毁禁止自动物理删除。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import { statusLabel, statusTagType } from '@/utils/status-label'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'

const props = defineProps<{
  mode: 'backup' | 'archive' | 'destroy'
}>()

interface ManagedTable {
  id: number
  physicalTable: string
}
interface Library {
  id: number
  libName: string
  libType: string
}
interface Policy {
  id: number
  policyName: string
  actionType: string
  retentionDays?: number
  managedTableId?: number
  storageStrategy?: string
  backupLibraryId?: number
  tableRule?: string
  compressEnabled?: number
  compressType?: string
  destroyRule?: string
  scheduleEnabled?: number
  scheduleCron?: string
  status?: string
  lastRunStatus?: string
  lastRunAt?: string
}
interface Artifact {
  id: number
  artifactType?: string
  physicalTable?: string
  fileName?: string
  filePath?: string
  storageLocation?: string
  rowCount?: number
  status?: string
  createdAt?: string
}

const loading = ref(false)
const managedTables = ref<ManagedTable[]>([])
const libraries = ref<Library[]>([])
const policies = ref<Policy[]>([])
const artifacts = ref<Artifact[]>([])
const lastRun = ref<Record<string, unknown> | null>(null)

const form = reactive({
  policyName: '',
  retentionDays: 30,
  managedTableId: undefined as number | undefined,
  storageStrategy: 'LOCAL',
  backupLibraryId: undefined as number | undefined,
  tableRule: '',
  compressEnabled: true,
  compressType: 'GZIP',
  destroyRule: '',
  scheduleEnabled: false,
  scheduleCron: '0 0 2 * * ?',
})

const actionType = computed(() =>
  props.mode === 'backup' ? 'BACKUP' : props.mode === 'archive' ? 'ARCHIVE' : 'DESTROY',
)

const filtered = computed(() => policies.value.filter((p) => p.actionType === actionType.value))

const filteredArtifacts = computed(() => {
  const type = props.mode === 'archive' ? 'ARCHIVE' : 'BACKUP'
  return artifacts.value.filter((a) => (a.artifactType || 'BACKUP') === type)
})

const tableName = (id?: number) => {
  if (!id) return '-'
  return managedTables.value.find((t) => t.id === id)?.physicalTable || String(id)
}

const libName = (id?: number) => {
  if (!id) return '-'
  return libraries.value.find((l) => l.id === id)?.libName || String(id)
}

async function reload() {
  loading.value = true
  try {
    const [tables, pols, arts, libs] = await Promise.all([
      api.get('/resource-center/platform/managed-tables'),
      api.get('/resource-center/platform/policies', { params: { actionType: actionType.value } }),
      api.get('/resource-center/platform/backups/artifacts'),
      api.get('/resource-center/platform/libraries'),
    ])
    managedTables.value = tables.data || []
    policies.value = pols.data || []
    artifacts.value = arts.data || []
    libraries.value = libs.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function createPolicy() {
  if (!form.policyName.trim()) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if (!form.managedTableId) {
    ElMessage.warning(props.mode === 'backup' ? '请选择备份目标表' : '请选择关联纳管表')
    return
  }
  if (props.mode === 'backup' && !form.storageStrategy) {
    ElMessage.warning('请选择存储策略')
    return
  }
  if (form.scheduleEnabled && !form.scheduleCron.trim()) {
    ElMessage.warning('启用周期调度时请填写 Cron')
    return
  }
  await api.post('/resource-center/platform/policies', {
    policyName: form.policyName,
    actionType: actionType.value,
    retentionDays: form.retentionDays,
    managedTableId: form.managedTableId,
    storageStrategy: form.storageStrategy,
    backupLibraryId: form.backupLibraryId,
    tableRule: form.tableRule || undefined,
    compressEnabled: props.mode === 'archive' ? form.compressEnabled : false,
    compressType: props.mode === 'archive' ? form.compressType : 'NONE',
    destroyRule: props.mode === 'destroy' ? form.destroyRule : undefined,
    scheduleEnabled: form.scheduleEnabled,
    scheduleCron: form.scheduleEnabled ? form.scheduleCron : undefined,
  })
  ElMessage.success('策略已创建')
  form.policyName = ''
  form.tableRule = ''
  form.destroyRule = ''
  await reload()
}

async function runPolicy(id: number) {
  try {
    const res = await api.post(`/resource-center/platform/policies/${id}/execute`)
    lastRun.value = res.data
    const st = String(res.data?.status || '')
    if (st === 'LEDGER') {
      ElMessage.warning(String(res.data?.message || '已记台账，未改物理数据'))
    } else {
      ElMessage.success(String(res.data?.message || '策略已执行'))
    }
    await reload()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '策略执行失败（销毁场景可能被策略拒绝）')
    await reload()
  }
}

async function verifyArtifact(id: number) {
  const res = await api.get(`/resource-center/platform/backups/artifacts/${id}/verify`)
  ElMessage.success(res.data?.match ? '校验通过' : '校验失败')
}

async function restoreArtifact(id: number) {
  try {
    const res = await api.post(`/resource-center/platform/backups/artifacts/${id}/restore`)
    ElMessage.success(String(res.data?.message || `已恢复 ${res.data?.rowCount ?? 0} 行`))
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '恢复失败')
  }
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert
      v-if="mode === 'backup'"
      type="info"
      :closable="false"
      show-icon
      title="数据备份：配置保存时间、存储策略，选择备份库与纳管表，按备份表规则执行逻辑备份。"
      style="margin-bottom:12px"
    />
    <el-alert
      v-if="mode === 'archive'"
      type="info"
      :closable="false"
      show-icon
      title="数据归档：配置保存时间与压缩方式；执行仅记台账并生成归档产物位置，不移动物理业务数据。"
      style="margin-bottom:12px"
    />
    <el-alert
      v-if="mode === 'destroy'"
      type="warning"
      :closable="false"
      show-icon
      title="数据销毁：可配置销毁规则以释放存储规划；禁止自动物理删除，执行将被拒绝并记运行日志。"
      style="margin-bottom:12px"
    />
    <el-alert
      v-if="lastRun"
      :type="lastRun.status === 'LEDGER' ? 'warning' : 'success'"
      :closable="true"
      style="margin-bottom:12px"
      :title="`最近执行：${statusLabel(String(lastRun.actionType || ''))} · ${statusLabel(String(lastRun.status || ''))}`"
      @close="lastRun = null"
    >
      {{ lastRun.message || `产物行数 ${lastRun.rowCount ?? '-'}` }}
      <template v-if="lastRun.storageLocation">；位置 {{ lastRun.storageLocation }}</template>
    </el-alert>

    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="策略名" class="portal-field-md">
        <el-input
          v-model="form.policyName"
          :placeholder="mode === 'backup' ? '备份策略名称' : mode === 'archive' ? '归档策略名称' : '销毁策略名称'"
        />
      </el-form-item>
      <el-form-item :label="mode === 'backup' ? '备份表' : '纳管表'" class="portal-field-lg">
        <el-select v-model="form.managedTableId" filterable clearable :placeholder="mode === 'backup' ? '选择备份目标表' : '选择纳管表'">
          <el-option v-for="t in managedTables" :key="t.id" :label="t.physicalTable" :value="t.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="mode !== 'destroy'" label="保存天数" class="portal-field-xs">
        <el-input-number v-model="form.retentionDays" :min="1" :max="3650" controls-position="right" />
      </el-form-item>
      <el-form-item v-if="mode === 'backup'" label="存储策略" class="portal-field-sm">
        <el-select v-model="form.storageStrategy">
          <el-option :label="statusLabel('LOCAL')" value="LOCAL" />
          <el-option :label="statusLabel('NAS')" value="NAS" />
          <el-option :label="statusLabel('OBJECT')" value="OBJECT" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="mode === 'backup'" label="备份库" class="portal-field-lg">
        <el-select v-model="form.backupLibraryId" filterable clearable placeholder="选择备份库">
          <el-option
            v-for="l in libraries"
            :key="l.id"
            :label="`${l.libName}（${statusLabel(l.libType)}）`"
            :value="l.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="mode === 'backup'" label="表规则" class="portal-field-xl">
        <el-input v-model="form.tableRule" placeholder="如 dws_* 或业务筛选说明" />
      </el-form-item>
      <el-form-item v-if="mode === 'archive'" label="压缩" class="portal-field-sm">
        <el-switch v-model="form.compressEnabled" />
      </el-form-item>
      <el-form-item v-if="mode === 'archive' && form.compressEnabled" label="压缩方式" class="portal-field-sm">
        <el-select v-model="form.compressType">
          <el-option :label="statusLabel('GZIP')" value="GZIP" />
          <el-option :label="statusLabel('NONE')" value="NONE" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="mode === 'destroy'" label="销毁规则" class="portal-field-xl">
        <el-input v-model="form.destroyRule" placeholder="如：超期且无在用订阅方可申请销毁" />
      </el-form-item>
      <el-form-item label="周期调度" class="portal-field-xs">
        <el-switch v-model="form.scheduleEnabled" />
      </el-form-item>
      <el-form-item v-if="form.scheduleEnabled" label="执行周期" class="portal-field-cron">
        <ExecCycleSelect v-model="form.scheduleCron" />
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button
          :type="mode === 'destroy' ? 'danger' : 'primary'"
          @click="createPolicy"
        >
          {{ mode === 'backup' ? '创建备份策略' : mode === 'archive' ? '创建归档策略' : '创建销毁策略' }}
        </el-button>
      </el-form-item>
    </el-form>

    <el-divider content-position="left">
      {{ mode === 'backup' ? '备份策略' : mode === 'archive' ? '归档策略' : '销毁策略' }}
    </el-divider>
    <el-table :data="filtered" stripe size="small">
      <el-table-column prop="policyName" label="策略" min-width="140" />
      <el-table-column label="纳管表" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ tableName(row.managedTableId) }}</template>
      </el-table-column>
      <el-table-column v-if="mode !== 'destroy'" prop="retentionDays" label="保存天数" width="90" />
      <el-table-column v-if="mode === 'backup'" label="存储策略" width="100">
        <template #default="{ row }">{{ statusLabel(row.storageStrategy || 'LOCAL') }}</template>
      </el-table-column>
      <el-table-column v-if="mode === 'backup'" label="备份库" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ libName(row.backupLibraryId) }}</template>
      </el-table-column>
      <el-table-column v-if="mode === 'backup'" prop="tableRule" label="表规则" min-width="120" show-overflow-tooltip />
      <el-table-column v-if="mode === 'archive'" label="压缩" width="90">
        <template #default="{ row }">{{ statusLabel(row.compressType || (row.compressEnabled ? 'GZIP' : 'NONE')) }}</template>
      </el-table-column>
      <el-table-column v-if="mode === 'destroy'" prop="destroyRule" label="销毁规则" min-width="160" show-overflow-tooltip />
      <el-table-column label="调度" width="140">
        <template #default="{ row }">
          <span v-if="row.scheduleEnabled">{{ row.scheduleCron || '-' }}</span>
          <span v-else>手动</span>
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
      <el-table-column label="操作" :width="mode === 'backup' ? 120 : 100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="mode === 'backup'"
            link
            type="primary"
            @click="runPolicy(row.id)"
          >执行备份</el-button>
          <el-button
            v-if="mode === 'archive'"
            link
            type="primary"
            @click="runPolicy(row.id)"
          >执行归档</el-button>
          <el-button
            v-if="mode === 'destroy'"
            link
            type="danger"
            @click="runPolicy(row.id)"
          >尝试执行</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template v-if="mode === 'backup' || mode === 'archive'">
      <el-divider content-position="left">
        {{ mode === 'backup' ? '备份产物' : '归档产物' }}
      </el-divider>
      <el-table :data="filteredArtifacts" stripe size="small">
        <el-table-column prop="physicalTable" label="表" width="160" />
        <el-table-column prop="fileName" label="文件" min-width="160" show-overflow-tooltip />
        <el-table-column label="存储位置" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.storageLocation || row.filePath || '-' }}</template>
        </el-table-column>
        <el-table-column prop="rowCount" label="行数" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="mode === 'backup' ? 140 : 80">
          <template #default="{ row }">
            <el-button link type="primary" @click="verifyArtifact(row.id)">校验</el-button>
            <el-button
              v-if="mode === 'backup'"
              link
              type="success"
              @click="restoreArtifact(row.id)"
            >恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>
