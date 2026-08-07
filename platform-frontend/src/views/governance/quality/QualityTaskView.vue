<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'
import {
  catalogHintOfSourceId,
  groupSourcesByRole,
  loadQualitySourceOptions,
  loadQualityTables,
  type QualitySourceOption,
  type QualityTableMeta,
} from './useQualityTargetPicker'

interface TaskRow {
  id: number
  taskName: string
  description?: string
  status: string
  lastScore?: number
  lastMessage?: string
  lastRunAt?: string
  scheduleType?: string
  cronExpr?: string
  datasourceId?: number
  metadataEntryCode?: string
  detailCount?: number
}

interface DetailRow {
  id: number
  taskId: number
  ruleId: number
  targetTable?: string
  targetColumn?: string
  checkType?: string
  status?: string
}

interface RuleOpt {
  id: number
  ruleName: string
  ruleCode: string
  config?: {
    checkType?: string
    targetTable?: string
    targetColumn?: string
    metadataEntryCode?: string
  } | null
}

const tasks = ref<TaskRow[]>([])
const {
  page: taskPage,
  pageSize: taskPageSize,
  paged: pagedTasks,
  total: taskTotal,
  resetPage: resetTaskPage,
} = useClientPager(tasks)
const loading = ref(false)
const drawer = ref(false)
const sources = ref<QualitySourceOption[]>([])
const tables = ref<QualityTableMeta[]>([])
const sourceGroups = computed(() => groupSourcesByRole(sources.value))
const layerHint = computed(() => catalogHintOfSourceId(form.datasourceId))
const tablesLoading = ref(false)
const rules = ref<RuleOpt[]>([])
const details = ref<DetailRow[]>([])

const form = reactive({
  id: null as number | null,
  taskName: '',
  description: '',
  scheduleType: 'MANUAL',
  cronExpr: '',
  datasourceId: undefined as number | undefined,
  metadataEntryCode: '',
})

const detailForm = reactive({
  ruleId: undefined as number | undefined,
  targetTable: '',
  targetColumn: '',
  checkType: '',
})

const columnOptions = computed(() => {
  const t = tables.value.find((x) => x.sourceTable === detailForm.targetTable)
  return t?.columns || []
})

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = (await api.get('/governance/quality/task-mgmt')).data || []
    resetTaskPage()
  } catch {
    ElMessage.error('加载任务失败')
  } finally {
    loading.value = false
  }
}

async function loadRules() {
  rules.value = (await api.get('/governance/quality/rule-mgmt')).data || []
}

async function loadSources() {
  sources.value = await loadQualitySourceOptions()
}

async function reloadTables(clearSelection: boolean) {
  if (form.datasourceId == null) {
    tables.value = []
    return
  }
  tablesLoading.value = true
  try {
    tables.value = await loadQualityTables(form.datasourceId)
    if (clearSelection) {
      detailForm.targetTable = ''
      detailForm.targetColumn = ''
    }
  } catch {
    tables.value = []
  } finally {
    tablesLoading.value = false
  }
}

watch(() => form.datasourceId, () => {
  if (drawer.value) void reloadTables(true)
})

function openCreate() {
  form.id = null
  form.taskName = ''
  form.description = ''
  form.scheduleType = 'MANUAL'
  form.cronExpr = ''
  form.datasourceId = -2
  form.metadataEntryCode = ''
  details.value = []
  drawer.value = true
  void loadRules()
  void loadSources().then(() => reloadTables(true))
}

async function openEdit(row: TaskRow) {
  form.id = row.id
  form.taskName = row.taskName
  form.description = row.description || ''
  form.scheduleType = row.scheduleType || 'MANUAL'
  form.cronExpr = row.cronExpr || ''
  form.datasourceId = row.datasourceId ?? -2
  form.metadataEntryCode = row.metadataEntryCode || ''
  drawer.value = true
  await Promise.all([loadRules(), loadSources(), loadDetails(row.id)])
  await reloadTables(false)
}

async function loadDetails(taskId: number) {
  details.value = (await api.get(`/governance/quality/task-mgmt/${taskId}/details`)).data || []
}

async function saveTask() {
  if (!form.taskName.trim()) {
    ElMessage.warning('请填写任务名称')
    return
  }
  if (form.datasourceId == null) {
    ElMessage.warning('请选择稽核来源库')
    return
  }
  const body = {
    taskName: form.taskName,
    description: form.description,
    scheduleType: form.scheduleType,
    cronExpr: form.cronExpr || null,
    datasourceId: form.datasourceId,
    metadataEntryCode: form.metadataEntryCode || null,
  }
  if (form.id) {
    await api.put(`/governance/quality/task-mgmt/${form.id}`, body)
    ElMessage.success('任务已更新')
  } else {
    const id = (await api.post('/governance/quality/task-mgmt', body)).data
    form.id = id
    ElMessage.success('任务已创建')
  }
  await loadTasks()
}

async function addDetail() {
  if (!form.id) {
    ElMessage.warning('请先保存任务')
    return
  }
  if (!detailForm.ruleId) {
    ElMessage.warning('请选择规则')
    return
  }
  if (!detailForm.targetTable.trim()) {
    ElMessage.warning('请选择目标表')
    return
  }
  if (detailForm.checkType !== 'RECORD_COUNT' && !detailForm.targetColumn.trim()) {
    ElMessage.warning('请选择目标字段')
    return
  }
  await api.post(`/governance/quality/task-mgmt/${form.id}/details`, {
    ruleId: detailForm.ruleId,
    targetTable: detailForm.targetTable,
    targetColumn: detailForm.targetColumn || null,
    checkType: detailForm.checkType || null,
  })
  ElMessage.success('明细已添加')
  detailForm.ruleId = undefined
  detailForm.targetTable = ''
  detailForm.targetColumn = ''
  detailForm.checkType = ''
  await loadDetails(form.id)
  await loadTasks()
}

function onRulePick(ruleId: number) {
  const r = rules.value.find((x) => x.id === ruleId)
  if (r?.config) {
    detailForm.checkType = r.config.checkType || ''
    detailForm.targetTable = r.config.targetTable || ''
    detailForm.targetColumn = r.config.targetColumn || ''
    if (r.config.metadataEntryCode) {
      form.metadataEntryCode = r.config.metadataEntryCode
    }
  }
}

function onTablePick(name: string) {
  detailForm.targetColumn = ''
  const meta = tables.value.find((t) => t.sourceTable === name)
  if (meta?.entryCode) form.metadataEntryCode = meta.entryCode
}

async function removeDetail(detailId: number) {
  if (!form.id) return
  await api.delete(`/governance/quality/task-mgmt/${form.id}/details/${detailId}`)
  ElMessage.success('已删除明细')
  await loadDetails(form.id)
  await loadTasks()
}

async function removeTask(id: number) {
  await ElMessageBox.confirm('确认删除该质量任务及明细？', '删除确认', { type: 'warning' })
  await api.delete(`/governance/quality/task-mgmt/${id}`)
  ElMessage.success('已删除')
  if (form.id === id) drawer.value = false
  await loadTasks()
}

async function runTask(id: number) {
  try {
    const res = await api.post(`/governance/quality/task-mgmt/${id}/run`)
    ElMessage.success(`执行完成 · 评分 ${res.data.score}`)
    await loadTasks()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '执行失败，请检查任务明细与目标表配置')
  }
}

async function stopTask(id: number) {
  await api.post(`/governance/quality/task-mgmt/${id}/stop`)
  ElMessage.success('已停止')
  await loadTasks()
}

function checkTypeLabel(v?: string) {
  return ({
    NULL_CHECK: '空值检查',
    UNIQUENESS: '唯一性',
    ACCURACY: '准确性',
    RECORD_COUNT: '记录数',
  } as Record<string, string>)[v || ''] || v || '—'
}

function sourceLabel(id?: number) {
  if (id == null) return '—'
  return sources.value.find((s) => s.id === id)?.label || `源#${id}`
}

onMounted(loadTasks)
</script>

<template>
  <PageCard title="数据质量任务">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="openCreate">新建任务</el-button>
        <el-button @click="loadTasks">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="pagedTasks" stripe size="small">
      <el-table-column prop="taskName" label="任务" min-width="140" />
      <el-table-column label="调度" width="100">
        <template #default="{ row }">{{ row.scheduleType === 'CRON' ? '定时' : '手动' }}</template>
      </el-table-column>
      <el-table-column prop="detailCount" label="规则数" width="70" />
      <el-table-column prop="lastScore" label="最近评分" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastMessage" label="最近消息" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link @click="openEdit(row)">配置</el-button>
          <el-button link type="primary" @click="runTask(row.id)">执行</el-button>
          <el-button link @click="stopTask(row.id)">停止</el-button>
          <el-button link type="danger" @click="removeTask(row.id)">删</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination
      v-model:page="taskPage"
      v-model:page-size="taskPageSize"
      :total="taskTotal"
    />
    <el-empty v-if="!loading && !tasks.length" description="暂无质量任务，请新建并添加稽核明细" />

    <el-drawer v-model="drawer" :title="form.id ? '配置质量任务' : '新建质量任务'" size="560px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.taskName" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="来源库" required>
          <el-select v-model="form.datasourceId" filterable placeholder="按源层/过程层/资源层选择" style="width: 100%">
            <el-option-group v-for="g in sourceGroups" :key="g.role" :label="g.label">
              <el-option v-for="s in g.options" :key="s.id" :label="s.label" :value="s.id" />
            </el-option-group>
          </el-select>
          <div v-if="layerHint" class="hint" style="margin-top: 6px; color: var(--el-text-color-secondary); font-size: 12px">
            {{ layerHint }}
          </div>
        </el-form-item>
        <el-form-item label="调度方式">
          <el-select v-model="form.scheduleType" style="width: 160px">
            <el-option label="手动" value="MANUAL" />
            <el-option label="定时" value="CRON" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scheduleType === 'CRON'" label="执行周期">
          <ExecCycleSelect v-model="form.cronExpr" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveTask">保存任务</el-button>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">稽核明细（来源库 / 表 / 字段 / 规则）</el-divider>
      <el-form v-if="form.id" label-width="96px">
        <el-form-item label="规则">
          <el-select v-model="detailForm.ruleId" filterable style="width: 100%" @change="onRulePick">
            <el-option v-for="r in rules" :key="r.id" :label="`${r.ruleName}（${r.ruleCode}）`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查类型">
          <el-select v-model="detailForm.checkType" clearable style="width: 100%">
            <el-option label="空值检查" value="NULL_CHECK" />
            <el-option label="唯一性" value="UNIQUENESS" />
            <el-option label="准确性" value="ACCURACY" />
            <el-option label="记录数" value="RECORD_COUNT" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标表" required>
          <el-select
            v-model="detailForm.targetTable"
            filterable
            allow-create
            default-first-option
            :loading="tablesLoading"
            placeholder="从当前来源库选择"
            style="width: 100%"
            @change="onTablePick"
          >
            <el-option v-for="t in tables" :key="t.sourceTable" :label="t.sourceTable" :value="t.sourceTable" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="detailForm.checkType !== 'RECORD_COUNT'" label="目标字段" required>
          <el-select
            v-model="detailForm.targetColumn"
            filterable
            allow-create
            default-first-option
            placeholder="选择字段"
            style="width: 100%"
          >
            <el-option v-for="c in columnOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="addDetail">添加明细</el-button>
        </el-form-item>
      </el-form>
      <el-alert v-else type="info" :closable="false" title="请先保存任务后再添加明细" style="margin-bottom: 12px" />

      <el-table :data="details" stripe size="small">
        <el-table-column prop="ruleId" label="规则ID" width="80" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ checkTypeLabel(row.checkType) }}</template>
        </el-table-column>
        <el-table-column prop="targetTable" label="表" min-width="120" />
        <el-table-column prop="targetColumn" label="字段" width="100" />
        <el-table-column label="操作" width="70">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeDetail(row.id)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="form.id && !details.length" description="尚未添加明细，执行前须至少一条" />
      <div v-if="form.datasourceId != null" class="drawer-hint">当前来源：{{ sourceLabel(form.datasourceId) }}</div>
    </el-drawer>
  </PageCard>
</template>

<style scoped>
.drawer-hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
