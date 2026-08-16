<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { CircleClose, Grid, Plus, RefreshRight, Search, SwitchButton, VideoPlay } from '@element-plus/icons-vue'
import api from '@/api/http'
import { statusLabel } from '@/utils/status-label'
import ExecCycleSelect, { type ExecCycleOption } from '@/views/system/ExecCycleSelect.vue'
import MetaDataSourcePickerDialog from '@/components/common/MetaDataSourcePickerDialog.vue'
import { connectionKeyOf, type MetaBindSource } from '@/utils/meta-datasource-conn'
import { fetchDataSourceTableNames } from '@/utils/layer-tables'

const props = defineProps<{
  domain: string
  /** 父级 Tab 是否当前展示；切回时重新拉取指标域，与「指标域管理」保持一致 */
  active?: boolean
  /** 是否在指标组页内嵌执行/启动/停止（仅人口大数据指标库；治理平台仍用独立「指标任务」页） */
  embedTaskActions?: boolean
  /**
   * 仅展示指定名称的指标域（人口大数据 Hub 传「人口大数据支撑系统」；
   * 治理平台不传，仍看全部）。
   */
  scopeDomainName?: string
}>()

interface DomainRow {
  id: number
  domainName: string
  domainDbName: string
  remark?: string
}

interface GroupRow {
  id: number
  indicatorDomainId: number
  groupName: string
  targetTable: string
  groupCategory: string
  modelMethod: string
  description?: string
  status: string
}

/** 发布后生成的指标任务（按 groupId 映射） */
interface TaskRow {
  id: number
  groupId: number
  taskName: string
  scheduleStatus: string
  execStatus: string
  calcResult: string
  publishStatus: string
  lastLog?: string
}

interface IndicatorRow {
  id: number
  queryNo?: string
  resultField?: string
  fieldType?: string
  indicatorName: string
  fieldName?: string
  indicatorFlag?: string
}

const loading = ref(false)
const domains = ref<DomainRow[]>([])
const domainKeyword = ref('')
const selectedDomainId = ref<number | null>(null)

const groups = ref<GroupRow[]>([])
/** groupId → 指标任务 */
const taskByGroupId = ref<Map<number, TaskRow>>(new Map())
const query = reactive({
  groupName: '',
  targetTable: '',
  groupCategory: '',
})

const logDialog = ref(false)
const logTitle = ref('')
const logText = ref('')
const logRuns = ref<Array<{ id: number; triggerType: string; execStatus: string; calcResult: string; message?: string; startedAt?: string }>>([])

const detailSaving = ref(false)
const detailFormRef = ref<FormInstance>()
const detail = reactive({
  id: null as number | null,
  indicatorDomainId: null as number | null,
  groupName: '',
  targetTable: '',
  groupCategory: 'UNIT',
  description: '',
})
const detailIndicators = ref<IndicatorRow[]>([])
const detailLoading = ref(false)

/** 右侧单条指标组「发布」：任务详情 */
const publishVisible = ref(false)
const publishSaving = ref(false)
const publishFormRef = ref<FormInstance>()
const publishTarget = ref<GroupRow | null>(null)
const publishForm = reactive({
  taskName: '',
  cronExpr: '',
  cycleCode: '',
  cycleName: '',
  remark: '',
})
const publishRules: FormRules = {
  taskName: [{ required: true, message: '请填写任务名称', trigger: 'blur' }],
  cronExpr: [{ required: true, message: '请选择执行周期', trigger: 'change' }],
}

const tableNamePattern = /^ind_[a-z0-9]+(_[a-z0-9]+)*$/

/** 指标组信息弹窗（新增/编辑共用，对齐原型：表单 + 新增指标 + 指标表） */
const groupDialogVisible = ref(false)
const indTableOptions = ref<string[]>([])
const indTablesLoading = ref(false)

async function loadIndTableOptions() {
  indTablesLoading.value = true
  try {
    const [dws, ads] = await Promise.all([
      fetchDataSourceTableNames(-3),
      fetchDataSourceTableNames(-4),
    ])
    indTableOptions.value = Array.from(new Set([...dws, ...ads])).sort((a, b) => a.localeCompare(b))
  } catch {
    indTableOptions.value = []
  } finally {
    indTablesLoading.value = false
  }
}

const detailRules: FormRules = {
  groupName: [{ required: true, message: '请输入指标组名称', trigger: 'blur' }],
  targetTable: [
    { required: true, message: '请输入指标组结果表名', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        const s = String(v || '').trim().toLowerCase()
        if (!tableNamePattern.test(s)) {
          cb(new Error('以 ind_ 开头，支持小写字母、数字、下划线，不能以下划线结尾'))
        } else if (/\d$/.test(s)) {
          cb(new Error('不能以数字结尾'))
        } else cb()
      },
      trigger: 'blur',
    },
  ],
  groupCategory: [{ required: true, message: '请选择指标组分类', trigger: 'change' }],
}

const dsDialog = ref(false)

const sqlDialog = ref(false)
const previewDialog = ref(false)
const sqlSaving = ref(false)
const previewLoading = ref(false)
const sqlForm = reactive({
  datasourceKey: '',
  datasourceName: '',
  timeoutSec: 60,
  sqlText: '',
})
const previewColumns = ref<string[]>([])
const previewRows = ref<Record<string, unknown>[]>([])
const previewMessage = ref('')
const previewTruncated = ref(false)

const filteredDomains = computed(() => {
  const kw = domainKeyword.value.trim()
  if (!kw) return domains.value
  return domains.value.filter((d) => d.domainName.includes(kw) || d.domainDbName.includes(kw))
})

function categoryLabel(code: string) {
  if (code === 'UNIT') return '单元指标组'
  if (code === 'COMPOSITE') return '复合指标组'
  return code || '—'
}

function modelLabel(code: string) {
  if (code === 'SQL') return 'SQL建模'
  return code || '—'
}

async function loadDomains() {
  const res = await api.get(`/analytics/domain/${props.domain}/indicator-domains`)
  let list: DomainRow[] = res.data || []
  const scope = props.scopeDomainName?.trim()
  if (scope) {
    list = list.filter((d) => String(d.domainName || '').includes(scope))
  }
  domains.value = list
  const stillSelected = domains.value.some((d) => d.id === selectedDomainId.value)
  if (!stillSelected) {
    selectedDomainId.value = domains.value.length ? domains.value[0].id : null
  }
}

async function loadGroups() {
  if (selectedDomainId.value == null) {
    groups.value = []
    taskByGroupId.value = new Map()
    return
  }
  loading.value = true
  try {
    const groupReq = api.get(`/analytics/domain/${props.domain}/indicator-groups`, {
      params: {
        indicatorDomainId: selectedDomainId.value,
        groupName: query.groupName || undefined,
        targetTable: query.targetTable || undefined,
        groupCategory: query.groupCategory || undefined,
      },
    })
    if (props.embedTaskActions) {
      const [gRes, tRes] = await Promise.all([
        groupReq,
        api.get(`/analytics/domain/${props.domain}/indicator-tasks`),
      ])
      groups.value = gRes.data || []
      const map = new Map<number, TaskRow>()
      for (const t of (tRes.data || []) as TaskRow[]) {
        if (t.groupId != null) map.set(t.groupId, t)
      }
      taskByGroupId.value = map
    } else {
      const gRes = await groupReq
      groups.value = gRes.data || []
      taskByGroupId.value = new Map()
    }
  } finally {
    loading.value = false
  }
}

function taskOf(row: GroupRow): TaskRow | undefined {
  return taskByGroupId.value.get(row.id)
}

async function batchTaskAction(action: 'EXECUTE' | 'START' | 'STOP') {
  // 针对当前指标域下全部已发布组对应的任务，不按行勾选
  const ids = groups.value
    .map((g) => taskOf(g)?.id)
    .filter((id): id is number => id != null)
  if (!ids.length) {
    ElMessage.warning('当前指标域下暂无已发布的指标任务，请先发布指标组')
    return
  }
  const label = action === 'EXECUTE' ? '执行' : action === 'START' ? '启动' : '停止'
  await ElMessageBox.confirm(
    `确认对当前指标域下全部 ${ids.length} 个指标任务执行「${label}」？`,
    '确认',
    { type: 'warning' },
  )
  try {
    const res = await api.post('/analytics/domain/indicator-tasks/batch', { action, ids })
    const ok = res.data?.ok ?? 0
    const fail = res.data?.fail ?? 0
    if (fail > 0) {
      ElMessage.warning(`${label}完成：成功 ${ok}，失败 ${fail}`)
    } else {
      ElMessage.success(`${label}成功（${ok}）`)
    }
    await loadGroups()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || `${label}失败`)
  }
}

async function openTaskLog(row: GroupRow) {
  const task = taskOf(row)
  if (!task) {
    ElMessage.warning('该指标组尚未发布，无任务日志')
    return
  }
  logTitle.value = `日志 — ${task.taskName || row.groupName}`
  try {
    const res = await api.get(`/analytics/domain/indicator-tasks/${task.id}/log`)
    logText.value = res.data?.lastLog || task.lastLog || '暂无日志'
    logRuns.value = res.data?.runs || []
    logDialog.value = true
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载日志失败')
  }
}

function resetQuery() {
  query.groupName = ''
  query.targetTable = ''
  query.groupCategory = ''
  loadGroups()
}

function selectDomain(id: number) {
  selectedDomainId.value = id
  loadGroups()
}

/** 选中指标域并展示右侧指标组 */
function selectDomainAndShow(d: DomainRow) {
  selectDomain(d.id)
}

async function deleteGroup(row: GroupRow) {
  await ElMessageBox.confirm(`确认删除指标组「${row.groupName}」？`, '删除确认', { type: 'warning' })
  try {
    await api.delete(`/analytics/domain/indicator-groups/${row.id}`)
    ElMessage.success('已删除')
    await loadGroups()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

function openPublishDialog(row: GroupRow) {
  publishTarget.value = row
  publishForm.taskName = row.groupName
  publishForm.cronExpr = ''
  publishForm.cycleCode = ''
  publishForm.cycleName = ''
  publishForm.remark = ''
  publishVisible.value = true
}

function onPublishCycleChange(opt: ExecCycleOption | null) {
  publishForm.cycleCode = opt?.cycleCode || ''
  publishForm.cycleName = opt?.cycleName || ''
}

async function confirmPublish() {
  if (!publishFormRef.value || !publishTarget.value) return
  await publishFormRef.value.validate()
  if (!publishForm.cronExpr.trim()) {
    ElMessage.warning('请选择执行周期')
    return
  }
  const g = publishTarget.value
  publishSaving.value = true
  try {
    await api.post(`/analytics/domain/indicator-groups/${g.id}/publish`, {
      taskName: publishForm.taskName.trim(),
      execCycle: publishForm.cycleCode || publishForm.cycleName || 'CUSTOM',
      cronExpr: publishForm.cronExpr.trim(),
      cycleName: publishForm.cycleName || null,
      remark: publishForm.remark?.trim() || null,
    })
    ElMessage.success(`已发布指标组「${g.groupName}」`)
    publishVisible.value = false
    selectDomain(g.indicatorDomainId)
    await loadGroups()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '发布失败')
  } finally {
    publishSaving.value = false
  }
}

async function openCreate() {
  if (selectedDomainId.value == null) {
    ElMessage.warning('请先选择左侧指标域')
    return
  }
  detail.id = null
  detail.indicatorDomainId = selectedDomainId.value
  detail.groupName = ''
  detail.targetTable = ''
  detail.groupCategory = 'UNIT'
  detail.description = ''
  detailIndicators.value = []
  groupDialogVisible.value = true
  void loadIndTableOptions()
}

async function openDetail(row: GroupRow) {
  detailLoading.value = true
  groupDialogVisible.value = true
  void loadIndTableOptions()
  try {
    const [gRes, iRes] = await Promise.all([
      api.get(`/analytics/domain/indicator-groups/${row.id}`),
      api.get(`/analytics/domain/indicator-groups/${row.id}/indicators`),
    ])
    const g = gRes.data as GroupRow
    detail.id = g.id
    detail.indicatorDomainId = g.indicatorDomainId
    detail.groupName = g.groupName
    detail.targetTable = g.targetTable
    detail.groupCategory = g.groupCategory || 'UNIT'
    detail.description = g.description || ''
    detailIndicators.value = iRes.data || []
  } finally {
    detailLoading.value = false
  }
}

async function closeGroupDialog() {
  groupDialogVisible.value = false
  await loadGroups()
}

async function ensureGroupSaved(opts?: { silent?: boolean }): Promise<number | null> {
  if (!detailFormRef.value) return detail.id
  await detailFormRef.value.validate()
  detailSaving.value = true
  try {
    const body = {
      indicatorDomainId: detail.indicatorDomainId,
      groupName: detail.groupName.trim(),
      targetTable: detail.targetTable.trim().toLowerCase(),
      groupCategory: detail.groupCategory,
      description: detail.description?.trim() || null,
      modelMethod: 'SQL',
    }
    if (detail.id == null) {
      const res = await api.post(`/analytics/domain/${props.domain}/indicator-groups`, body)
      detail.id = res.data as number
      if (!opts?.silent) ElMessage.success('指标组已保存')
    } else {
      await api.put(`/analytics/domain/indicator-groups/${detail.id}`, body)
      if (!opts?.silent) ElMessage.success('指标组已更新')
    }
    return detail.id
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
    return null
  } finally {
    detailSaving.value = false
  }
}

/** 下一步：仅校验指标组信息，不落库；进入指标语句页后再保存 */
async function goNextToSql() {
  if (!detailFormRef.value) return
  await detailFormRef.value.validate()
  sqlForm.datasourceKey = ''
  sqlForm.datasourceName = ''
  sqlForm.timeoutSec = 60
  sqlForm.sqlText = ''
  previewMessage.value = ''
  previewTruncated.value = false
  if (detail.id != null) {
    try {
      const res = await api.get(`/analytics/domain/indicator-groups/${detail.id}/sql`)
      sqlForm.datasourceKey = res.data?.datasourceKey || ''
      sqlForm.datasourceName = res.data?.datasourceName || ''
      sqlForm.timeoutSec = res.data?.timeoutSec || 60
      sqlForm.sqlText = res.data?.sqlText || ''
    } catch {
      /* 无历史语句时保持空白 */
    }
  }
  sqlDialog.value = true
}

/** 执行 / 预览：弹出结果页，不入库 */
async function runExecute() {
  await openPreview()
}

function openDsPicker() {
  dsDialog.value = true
}

function onMetaDsPicked(row: MetaBindSource) {
  sqlForm.datasourceKey = connectionKeyOf(row)
  sqlForm.datasourceName = row.sourceName
}

async function openPreview() {
  if (!sqlForm.datasourceKey) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!sqlForm.sqlText.trim()) {
    ElMessage.warning('请填写查询语句')
    return
  }
  previewLoading.value = true
  try {
    const res = await api.post(`/analytics/domain/${props.domain}/indicators/sql/preview`, {
      sqlText: sqlForm.sqlText,
      timeoutSec: sqlForm.timeoutSec,
      datasourceKey: sqlForm.datasourceKey,
    })
    previewColumns.value = res.data?.columns || []
    previewRows.value = res.data?.rows || []
    previewMessage.value = res.data?.message || ''
    previewTruncated.value = !!res.data?.truncated
    previewDialog.value = true
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '预览失败')
  } finally {
    previewLoading.value = false
  }
}

async function saveSql() {
  if (!sqlForm.datasourceKey) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!sqlForm.sqlText.trim()) {
    ElMessage.warning('请填写查询语句')
    return
  }
  const id = await ensureGroupSaved({ silent: true })
  if (id == null) return
  sqlSaving.value = true
  try {
    await api.post(`/analytics/domain/${props.domain}/indicators/sql`, {
      groupId: id,
      datasourceKey: sqlForm.datasourceKey,
      datasourceName: sqlForm.datasourceName,
      timeoutSec: sqlForm.timeoutSec,
      sqlText: sqlForm.sqlText,
      querySlug: detail.targetTable || 'query',
    })
    ElMessage.success('指标组已保存（结果表将在发布后的调度执行或指标任务执行时写入）')
    sqlDialog.value = false
    groupDialogVisible.value = false
    await loadGroups()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    sqlSaving.value = false
  }
}

async function persistIndicator(row: IndicatorRow) {
  try {
    await api.put(`/analytics/domain/indicators/${row.id}`, {
      indicatorName: row.indicatorName,
      fieldName: row.fieldName,
      indicatorFlag: row.indicatorFlag || null,
    })
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
    if (detail.id) {
      const iRes = await api.get(`/analytics/domain/indicator-groups/${detail.id}/indicators`)
      detailIndicators.value = iRes.data || []
    }
  }
}

async function removeIndicator(row: IndicatorRow) {
  await ElMessageBox.confirm(`确认删除指标「${row.indicatorName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/analytics/domain/indicators/${row.id}`)
  ElMessage.success('已删除')
  if (detail.id) {
    const iRes = await api.get(`/analytics/domain/indicator-groups/${detail.id}/indicators`)
    detailIndicators.value = iRes.data || []
  }
}

async function removeCurrentGroup() {
  if (detail.id == null) return
  await ElMessageBox.confirm(`确认删除指标组「${detail.groupName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/analytics/domain/indicator-groups/${detail.id}`)
  ElMessage.success('已删除')
  await closeGroupDialog()
}

watch(() => props.domain, async () => {
  groupDialogVisible.value = false
  selectedDomainId.value = null
  await loadDomains()
  await loadGroups()
})
watch(
  () => props.active,
  async (v, prev) => {
    if (v && prev === false) {
      groupDialogVisible.value = false
      await loadDomains()
      await loadGroups()
    }
  },
)

onMounted(async () => {
  await loadDomains()
  await loadGroups()
})
</script>

<template>
  <div class="ind-group-panel">
    <!-- 列表：左指标域 + 右指标组 -->
    <div class="ind-group-list">
      <aside class="ind-domain-side">
        <div class="side-title">指标域</div>
        <el-input
          v-model="domainKeyword"
          clearable
          size="small"
          placeholder="请输入指标域名称"
          class="side-search"
        />
        <div class="side-list">
          <div
            v-for="d in filteredDomains"
            :key="d.id"
            class="side-item"
            :class="{ active: selectedDomainId === d.id }"
          >
            <button type="button" class="side-item-name" @click="selectDomainAndShow(d)">
              {{ d.domainName }}
            </button>
          </div>
          <el-empty v-if="!filteredDomains.length" description="暂无指标域" :image-size="64" />
        </div>
      </aside>

      <section v-loading="loading" class="ind-group-main">
        <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="loadGroups">
          <el-form-item label="组名称" class="portal-field-md">
            <el-input v-model="query.groupName" clearable placeholder="请输入组名称" />
          </el-form-item>
          <el-form-item label="目标表" class="portal-field-md">
            <el-input v-model="query.targetTable" clearable placeholder="请输入目标表" />
          </el-form-item>
          <el-form-item label="组分类" class="portal-field-md">
            <el-select v-model="query.groupCategory" clearable placeholder="请选择">
              <el-option label="单元指标组" value="UNIT" />
              <el-option label="复合指标组" value="COMPOSITE" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :icon="Search" @click="loadGroups">查询</el-button>
            <el-button :icon="RefreshRight" @click="resetQuery">重置</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
          </el-form-item>
        </el-form>

        <el-form v-if="embedTaskActions" inline class="portal-inline-form">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :icon="VideoPlay" @click="batchTaskAction('EXECUTE')">执行</el-button>
            <el-button type="primary" :icon="SwitchButton" @click="batchTaskAction('START')">启动</el-button>
            <el-button type="primary" :icon="CircleClose" @click="batchTaskAction('STOP')">停止</el-button>
          </el-form-item>
        </el-form>

        <el-table
          class="portal-table"
          :data="groups"
          stripe
          size="small"
          empty-text="暂无数据"
        >
          <el-table-column label="组名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">{{ row.groupName }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="targetTable" label="目标表" min-width="180" show-overflow-tooltip />
          <el-table-column label="组分类" width="120">
            <template #default="{ row }">{{ categoryLabel(row.groupCategory) }}</template>
          </el-table-column>
          <el-table-column label="建模方式" width="110">
            <template #default="{ row }">{{ modelLabel(row.modelMethod) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column v-if="embedTaskActions" label="调度状态" width="100">
            <template #default="{ row }">{{ taskOf(row) ? statusLabel(taskOf(row)!.scheduleStatus) : '—' }}</template>
          </el-table-column>
          <el-table-column v-if="embedTaskActions" label="执行状态" width="100">
            <template #default="{ row }">{{ taskOf(row) ? statusLabel(taskOf(row)!.execStatus) : '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" :width="embedTaskActions ? 220 : 180" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openPublishDialog(row)">发布</el-button>
              <el-button link type="primary" @click="openDetail(row)">修改</el-button>
              <el-button link type="danger" @click="deleteGroup(row)">删除</el-button>
              <el-button
                v-if="embedTaskActions"
                link
                type="primary"
                :disabled="!taskOf(row)"
                @click="openTaskLog(row)"
              >日志</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="list-footer">共 {{ groups.length }} 条</div>
      </section>
    </div>

    <!-- 单条指标组「发布」：任务详情 -->
    <el-dialog v-model="publishVisible" title="任务详情" width="480px" destroy-on-close>
      <el-form
        ref="publishFormRef"
        :model="publishForm"
        :rules="publishRules"
        label-width="110px"
      >
        <el-form-item label="任务名称" prop="taskName" required>
          <el-input v-model="publishForm.taskName" placeholder="请填写任务名称" />
        </el-form-item>
        <el-form-item label="执行周期" prop="cronExpr" required>
          <ExecCycleSelect
            v-model="publishForm.cronExpr"
            :allow-custom="false"
            @change="onPublishCycleChange"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="publishForm.remark" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishSaving" @click="confirmPublish">确定</el-button>
      </template>
    </el-dialog>

    <!-- 指标组信息弹窗（新增/编辑） -->
    <el-dialog
      v-model="groupDialogVisible"
      title="指标组信息"
      width="920px"
      top="6vh"
      destroy-on-close
      class="ind-group-info-dialog"
      @closed="loadGroups"
    >
      <div v-loading="detailLoading" class="ind-group-dialog-body">
        <el-form
          ref="detailFormRef"
          :model="detail"
          :rules="detailRules"
          label-width="140px"
          class="detail-form"
        >
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="指标组名称" prop="groupName" required>
                <el-input v-model="detail.groupName" placeholder="请输入指标组名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="指标组结果表名" prop="targetTable" required>
                <el-select
                  v-model="detail.targetTable"
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  :loading="indTablesLoading"
                  placeholder="输入表名筛选，或以 ind_ 开头新建"
                  style="width: 100%"
                >
                  <el-option v-for="t in indTableOptions" :key="t" :label="t" :value="t" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="指标组分类" prop="groupCategory" required>
                <el-select v-model="detail.groupCategory" placeholder="请选择指标组分类" style="width: 100%">
                  <el-option label="单元指标组" value="UNIT" />
                  <el-option label="复合指标组" value="COMPOSITE" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="指标组描述">
                <el-input v-model="detail.description" placeholder="请输入描述" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <el-table
          class="portal-table detail-ind-table"
          :data="detailIndicators"
          stripe
          size="small"
          empty-text="暂无数据"
          max-height="360"
        >
          <el-table-column prop="queryNo" label="查询编号" min-width="180" show-overflow-tooltip />
          <el-table-column prop="resultField" label="查询结果字段" min-width="120" show-overflow-tooltip />
          <el-table-column prop="fieldType" label="字段类型" width="90" />
          <el-table-column label="指标名称" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.indicatorName" size="small" @change="persistIndicator(row)" />
            </template>
          </el-table-column>
          <el-table-column label="字段名" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.fieldName" size="small" @change="persistIndicator(row)" />
            </template>
          </el-table-column>
          <el-table-column label="指标标识" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.indicatorFlag" size="small" @change="persistIndicator(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="removeIndicator(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="closeGroupDialog">取消</el-button>
        <el-button v-if="detail.id" type="danger" plain @click="removeCurrentGroup">删除</el-button>
        <el-button type="primary" :loading="detailSaving" @click="goNextToSql">下一步</el-button>
      </template>
    </el-dialog>

    <!-- 指标语句（下一步进入；保存时才落库指标组+指标） -->
    <el-dialog
      v-model="sqlDialog"
      title="指标语句"
      width="920px"
      destroy-on-close
      top="5vh"
      append-to-body
      class="ind-sql-dialog"
    >
      <div class="sql-dialog-body">
        <section class="sql-card sql-card--meta">
          <el-form label-width="96px" inline class="portal-inline-form sql-meta-form">
            <el-form-item label="数据源" required class="portal-field-xl">
              <el-input
                :model-value="sqlForm.datasourceName"
                readonly
                placeholder="请选择元数据数据源"
                class="ds-picker-input"
                @click="openDsPicker"
              >
                <template #suffix>
                  <el-icon class="ds-picker-icon" @click.stop="openDsPicker"><Search /></el-icon>
                </template>
              </el-input>
            </el-form-item>
            <el-form-item label="超时(秒)" required>
              <el-input-number
                v-model="sqlForm.timeoutSec"
                :min="5"
                :max="600"
                controls-position="right"
              />
            </el-form-item>
          </el-form>
        </section>

        <section class="sql-card">
          <div class="sql-card-head">
            <div class="sql-card-title">查询语句</div>
            <div class="sql-card-actions">
              <el-button type="primary" size="small" :icon="VideoPlay" :loading="previewLoading" @click="runExecute">
                执行
              </el-button>
              <el-button size="small" :icon="Grid" :loading="previewLoading" @click="openPreview">预览</el-button>
            </div>
          </div>
          <el-input
            v-model="sqlForm.sqlText"
            type="textarea"
            :rows="11"
            class="sql-editor"
            resize="vertical"
            placeholder="请手动填写 SELECT 语句，并为结果列指定 AS 别名，例如：&#10;SELECT YEAR(NOW()) AS year_name FROM (SELECT 1) t"
          />
          <p class="sql-hint">提示：每列需带 AS 别名；点「执行」或「预览」仅查看结果，不入库；点「保存」只保存语句。数据在发布后按所选周期执行，或在指标任务中点「执行」后才写入指标表。</p>
        </section>
      </div>
      <template #footer>
        <el-button @click="sqlDialog = false">取消</el-button>
        <el-button type="primary" :loading="sqlSaving" @click="saveSql">保存</el-button>
      </template>
    </el-dialog>

    <!-- 选择数据源：元数据管理 · 数据源管理 -->
    <MetaDataSourcePickerDialog v-model="dsDialog" title="选择数据源" @confirm="onMetaDsPicked" />

    <el-dialog v-model="previewDialog" title="SQL 执行结果" width="860px" destroy-on-close append-to-body>
      <el-alert v-if="previewMessage" type="warning" :closable="false" :title="previewMessage" style="margin-bottom: 8px" />
      <el-alert
        v-else-if="previewTruncated"
        type="info"
        :closable="false"
        title="预览最多显示 200 行；不会写入指标表"
        style="margin-bottom: 8px"
      />
      <p class="sql-hint" style="margin-top: 0">共 {{ previewRows.length }} 行（预览不入库）</p>
      <el-table :data="previewRows" stripe size="small" max-height="360" empty-text="暂无数据">
        <el-table-column
          v-for="col in previewColumns"
          :key="col"
          :prop="col"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <template #footer>
        <el-button @click="previewDialog = false">关闭</el-button>
        <el-button type="primary" @click="previewDialog = false">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="logDialog" :title="logTitle" width="720px" destroy-on-close>
      <el-input v-model="logText" type="textarea" :rows="14" readonly class="log-box" />
      <el-table v-if="logRuns.length" :data="logRuns" size="small" stripe style="margin-top: 12px" max-height="220">
        <el-table-column prop="startedAt" label="时间" width="170" />
        <el-table-column prop="triggerType" label="触发" width="110" />
        <el-table-column label="执行" width="90">
          <template #default="{ row }">{{ statusLabel(row.execStatus) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="100">
          <template #default="{ row }">{{ statusLabel(row.calcResult) }}</template>
        </el-table-column>
        <el-table-column prop="message" label="摘要" min-width="160" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button type="primary" @click="logDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ind-group-list {
  display: flex;
  gap: 12px;
  min-height: 420px;
}
.ind-domain-side {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: #fff;
  padding: 10px;
}
.side-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.side-search {
  margin-bottom: 8px;
}
.side-list {
  max-height: 520px;
  overflow: auto;
}
.ind-domain-side .side-item {
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 8px 8px 4px;
  margin-bottom: 6px;
  background: var(--el-fill-color-blank, #fff);
}
.ind-domain-side .side-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-5);
}
.side-item-name {
  display: block;
  width: 100%;
  text-align: left;
  border: none;
  background: transparent;
  padding: 2px 4px 6px;
  cursor: pointer;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}
.ind-domain-side .side-item.active .side-item-name {
  color: var(--el-color-primary);
}
.ind-group-main {
  flex: 1;
  min-width: 0;
}
.list-footer {
  margin-top: 10px;
  text-align: right;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.log-box :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
}
.detail-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.detail-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}
.detail-form {
  margin-bottom: 8px;
}
.detail-ind-table {
  margin-top: 12px;
}
.sql-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sql-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fff;
  padding: 12px 14px;
}
.sql-card--meta {
  background: var(--el-fill-color-blank, #fafbfc);
}
.sql-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.sql-card-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.sql-card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.sql-meta-form {
  margin-bottom: 0;
}
.sql-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
.ds-picker-input {
  width: 280px;
  cursor: pointer;
}
.ds-picker-icon {
  cursor: pointer;
  color: var(--el-color-primary);
}
.sql-editor :deep(.el-textarea__inner) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.55;
  letter-spacing: 0.01em;
  color: #1f2a37;
  background: #f7f8fa;
  border-color: var(--el-border-color);
  border-radius: 6px;
  padding: 12px 14px;
  box-shadow: none;
}
.sql-editor :deep(.el-textarea__inner:focus) {
  background: #fff;
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
}
</style>

<style>
/* append-to-body 弹窗需非 scoped 才能作用到 dialog 外壳 */
.ind-sql-dialog .el-dialog__header {
  margin-right: 0;
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.ind-sql-dialog .el-dialog__body {
  padding: 16px 20px;
}
.ind-sql-dialog .el-dialog__footer {
  padding: 12px 20px 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
