<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { CircleClose, Grid, Plus, RefreshRight, Search, SwitchButton, VideoPlay } from '@element-plus/icons-vue'
import api from '@/api/http'
import { statusLabel } from '@/utils/status-label'
import ExecCycleSelect, { type ExecCycleOption } from '@/views/system/ExecCycleSelect.vue'
import MetaDataSourcePickerDialog from '@/components/common/MetaDataSourcePickerDialog.vue'
import { connectionKeyOf, type MetaBindSource } from '@/utils/meta-datasource-conn'

const props = defineProps<{
  domain: string
  /** 父级 Tab 是否当前展示；切回时重新拉取指标域，与「指标域管理」保持一致 */
  active?: boolean
  /** 是否在指标页内嵌执行/启动/停止（历史能力；业务支撑四域已拆到「指标任务」Tab，治理平台仍用独立页） */
  embedTaskActions?: boolean
  /**
   * 仅展示指定名称的指标域（业务支撑四域 Hub 传对应系统名；
   * 治理平台不传，仍看全部）。
   */
  scopeDomainName?: string
}>()

interface DomainRow {
  id: string
  domainName: string
  domainDbName: string
  remark?: string
}

interface GroupRow {
  id: string
  indicatorDomainId: string
  groupName: string
  targetTable: string
  groupCategory: string
  modelMethod: string
  description?: string
  status: string
}

/** 发布后生成的指标任务（按 groupId 映射） */
interface TaskRow {
  id: string
  groupId: string
  taskName: string
  scheduleStatus: string
  execStatus: string
  calcResult: string
  publishStatus: string
  lastLog?: string
}

interface IndicatorRow {
  id?: string
  queryNo?: string
  resultField?: string
  fieldType?: string
  fieldLength?: number
  indicatorName: string
  fieldName?: string
  indicatorFlag?: string
}

/** MySQL 8 常用数据类型（字段映射下拉） */
const MYSQL8_FIELD_TYPES = [
  'VARCHAR', 'CHAR', 'TEXT', 'TINYTEXT', 'MEDIUMTEXT', 'LONGTEXT',
  'INT', 'BIGINT', 'SMALLINT', 'TINYINT', 'MEDIUMINT',
  'DECIMAL', 'FLOAT', 'DOUBLE',
  'DATE', 'DATETIME', 'TIMESTAMP', 'TIME', 'YEAR',
  'BOOLEAN', 'BIT', 'JSON', 'BLOB', 'MEDIUMBLOB', 'LONGBLOB',
] as const

type DialogMode = 'create' | 'edit' | 'view'

const loading = ref(false)
const domains = ref<DomainRow[]>([])
const domainKeyword = ref('')
const selectedDomainId = ref<string | null>(null)

const groups = ref<GroupRow[]>([])
const selectedGroups = ref<GroupRow[]>([])
/** groupId → 指标任务 */
const taskByGroupId = ref<Map<string, TaskRow>>(new Map())
const query = reactive({
  groupName: '',
  targetTable: '',
})

const logDialog = ref(false)
const logTitle = ref('')
const logText = ref('')
const logRuns = ref<Array<{ id: number; triggerType: string; execStatus: string; calcResult: string; message?: string; startedAt?: string }>>([])

const detailSaving = ref(false)
const detailFormRef = ref<FormInstance>()
const detail = reactive({
  id: null as string | null,
  indicatorDomainId: null as string | null,
  groupName: '',
  targetTable: '',
  groupCategory: 'UNIT',
  description: '',
})
const detailIndicators = ref<IndicatorRow[]>([])
const detailLoading = ref(false)
/** 弹窗模式：新增 / 编辑 / 查看（已发布只读） */
const dialogMode = ref<DialogMode>('create')
const dialogReadonly = computed(() => dialogMode.value === 'view')

/** 右侧单条/批量指标「发布」：任务详情 */
const publishVisible = ref(false)
const publishSaving = ref(false)
const publishFormRef = ref<FormInstance>()
const publishTarget = ref<GroupRow | null>(null)
const publishBatchIds = ref<string[]>([])
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
const isBatchPublish = computed(() => publishBatchIds.value.length > 0)

const tableNamePattern = /^ind_[a-z0-9]+(_[a-z0-9]+)*$/

/** 指标信息弹窗（新增/编辑/查看：基础信息 + SQL + 字段映射） */
const groupDialogVisible = ref(false)
const groupDialogTitle = computed(() => {
  if (dialogMode.value === 'view') return '查看指标'
  if (dialogMode.value === 'edit') return '编辑指标'
  return '新增指标'
})

const detailRules: FormRules = {
  groupName: [{ required: true, message: '请输入指标名称', trigger: 'blur' }],
  targetTable: [
    { required: true, message: '请输入指标表名', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        const s = String(v || '').trim().toLowerCase()
        if (!tableNamePattern.test(s)) {
          cb(new Error('以 ind_ 开头，支持小写字母、数字、下划线，不能以下划线结尾'))
        } else cb()
      },
      trigger: 'blur',
    },
  ],
}

const dsDialog = ref(false)

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

function isPublishedGroup(row: GroupRow) {
  return String(row.status || '').toUpperCase() === 'PUBLISHED'
}

function normalizeFieldRow(f: Partial<IndicatorRow>, resultField: string): IndicatorRow {
  const rf = String(resultField || f.resultField || '').trim() || 'col'
  const safe = rf.replace(/[^a-zA-Z0-9_]/g, '_').toLowerCase()
  return {
    id: f.id,
    queryNo: f.queryNo,
    resultField: rf,
    fieldType: String(f.fieldType || 'VARCHAR').toUpperCase(),
    fieldLength: f.fieldLength != null && Number(f.fieldLength) > 0 ? Number(f.fieldLength) : 64,
    indicatorName: String(f.indicatorName || rf),
    fieldName: String(f.fieldName || `ind_${safe}`),
    indicatorFlag: f.indicatorFlag,
  }
}

function buildFieldsFromColumns(columns: string[], prev: IndicatorRow[] = []): IndicatorRow[] {
  const byRf = new Map(prev.map((p) => [String(p.resultField || '').toLowerCase(), p]))
  return columns
    .map((c) => String(c || '').trim())
    .filter(Boolean)
    .map((col) => {
      const old = byRf.get(col.toLowerCase())
      return normalizeFieldRow(old || { resultField: col, indicatorName: col }, col)
    })
}

const filteredDomains = computed(() => {
  const kw = domainKeyword.value.trim()
  if (!kw) return domains.value
  return domains.value.filter((d) => d.domainName.includes(kw) || d.domainDbName.includes(kw))
})

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
      },
    })
    if (props.embedTaskActions) {
      const [gRes, tRes] = await Promise.all([
        groupReq,
        api.get(`/analytics/domain/${props.domain}/indicator-tasks`),
      ])
      groups.value = gRes.data || []
      const map = new Map<string, TaskRow>()
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

function canPublishGroup(row: GroupRow) {
  const t = taskOf(row)
  if (!t) return true
  const st = String(t.publishStatus || '').toUpperCase()
  return st !== 'PUBLISHED'
}

function onGroupSelectionChange(val: GroupRow[]) {
  selectedGroups.value = val
}

async function batchTaskAction(action: 'EXECUTE' | 'START' | 'STOP') {
  // 仅已发布任务可执行/启动
  const ids = groups.value
    .map((g) => taskOf(g))
    .filter((t): t is TaskRow => !!t && String(t.publishStatus || '').toUpperCase() === 'PUBLISHED')
    .map((t) => t.id)
  if (!ids.length) {
    ElMessage.warning('当前指标域下暂无已发布的指标任务，请先发布指标')
    return
  }
  const label = action === 'EXECUTE' ? '执行' : action === 'START' ? '启动' : '停止'
  await ElMessageBox.confirm(
    `确认对当前指标域下 ${ids.length} 个已发布指标任务执行「${label}」？`,
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
    ElMessage.warning('该指标尚未发布，无任务日志')
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
  loadGroups()
}

function selectDomain(id: string) {
  selectedDomainId.value = id
  loadGroups()
}

/** 选中指标域并展示右侧指标 */
function selectDomainAndShow(d: DomainRow) {
  selectDomain(d.id)
}

async function deleteGroup(row: GroupRow) {
  await ElMessageBox.confirm(`确认删除指标「${row.groupName}」？`, '删除确认', { type: 'warning' })
  try {
    await api.delete(`/analytics/domain/indicator-groups/${row.id}`)
    ElMessage.success('已删除')
    await loadGroups()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

function openPublishDialog(row: GroupRow) {
  if (!canPublishGroup(row)) {
    ElMessage.warning('该指标对应任务已发布，请先在「指标任务」中下线后再发布')
    return
  }
  void ensureFieldsBeforePublish(row.id).then((ok) => {
    if (!ok) return
    publishBatchIds.value = []
    publishTarget.value = row
    publishForm.taskName = row.groupName
    publishForm.cronExpr = ''
    publishForm.cycleCode = ''
    publishForm.cycleName = ''
    publishForm.remark = ''
    publishVisible.value = true
  })
}

async function ensureFieldsBeforePublish(groupId: string): Promise<boolean> {
  try {
    const res = await api.get(`/analytics/domain/indicator-groups/${groupId}/indicators`)
    const list = (res.data || []) as IndicatorRow[]
    if (!list.length) {
      ElMessage.warning('请先编辑指标并执行 SQL 生成字段映射后再发布')
      return false
    }
    const bad = list.find((f) => !String(f.resultField || '').trim())
    if (bad) {
      ElMessage.warning('字段映射缺少查询结果字段，请重新执行 SQL')
      return false
    }
    return true
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '校验字段映射失败')
    return false
  }
}

function openBatchPublishDialog() {
  if (!selectedGroups.value.length) {
    ElMessage.warning('请先勾选要发布的指标')
    return
  }
  const blocked = selectedGroups.value.filter((g) => !canPublishGroup(g))
  if (blocked.length) {
    ElMessage.warning(`有 ${blocked.length} 条已发布任务，请先下线后再批量发布（或取消勾选）`)
    return
  }
  publishTarget.value = null
  publishBatchIds.value = selectedGroups.value.map((g) => g.id)
  publishForm.taskName = selectedGroups.value.length === 1
    ? selectedGroups.value[0].groupName
    : `批量发布（${selectedGroups.value.length}）`
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
  if (!publishFormRef.value) return
  if (isBatchPublish.value) {
    if (!publishForm.cronExpr.trim()) {
      ElMessage.warning('请选择执行周期')
      return
    }
  } else {
    await publishFormRef.value.validate()
    if (!publishForm.cronExpr.trim()) {
      ElMessage.warning('请选择执行周期')
      return
    }
  }
  publishSaving.value = true
  try {
    const bodyBase = {
      execCycle: publishForm.cycleCode || publishForm.cycleName || 'CUSTOM',
      cronExpr: publishForm.cronExpr.trim(),
      cycleName: publishForm.cycleName || null,
      remark: publishForm.remark?.trim() || null,
    }
    if (publishBatchIds.value.length) {
      const res = await api.post('/analytics/domain/indicator-groups/batch-publish', {
        ids: publishBatchIds.value,
        ...bodyBase,
      })
      const ok = res.data?.ok ?? 0
      const fail = res.data?.fail ?? 0
      if (fail > 0) {
        ElMessage.warning(`批量发布完成：成功 ${ok}，失败 ${fail}`)
      } else {
        ElMessage.success(`已批量发布 ${ok} 条指标`)
      }
      publishVisible.value = false
      selectedGroups.value = []
      if (selectedDomainId.value) selectDomain(selectedDomainId.value)
      await loadGroups()
      return
    }
    const g = publishTarget.value
    if (!g) return
    await api.post(`/analytics/domain/indicator-groups/${g.id}/publish`, {
      taskName: publishForm.taskName.trim(),
      ...bodyBase,
    })
    ElMessage.success(`已发布指标「${g.groupName}」，已创建/校验结果表`)
    publishVisible.value = false
    selectDomain(g.indicatorDomainId)
    await loadGroups()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '发布失败')
  } finally {
    publishSaving.value = false
  }
}

function resetSqlForm() {
  sqlForm.datasourceKey = ''
  sqlForm.datasourceName = ''
  sqlForm.timeoutSec = 60
  sqlForm.sqlText = ''
  previewMessage.value = ''
  previewTruncated.value = false
  previewColumns.value = []
  previewRows.value = []
}

async function loadGroupSqlAndFields(groupId: string) {
  const [sqlRes, iRes] = await Promise.all([
    api.get(`/analytics/domain/indicator-groups/${groupId}/sql`).catch(() => ({ data: null })),
    api.get(`/analytics/domain/indicator-groups/${groupId}/indicators`),
  ])
  sqlForm.datasourceKey = sqlRes.data?.datasourceKey || ''
  sqlForm.datasourceName = sqlRes.data?.datasourceName || ''
  sqlForm.timeoutSec = sqlRes.data?.timeoutSec || 60
  sqlForm.sqlText = sqlRes.data?.sqlText || ''
  detailIndicators.value = ((iRes.data || []) as IndicatorRow[]).map((f) =>
    normalizeFieldRow(f, String(f.resultField || f.fieldName || 'col')),
  )
}

async function openCreate() {
  if (selectedDomainId.value == null) {
    ElMessage.warning('请先选择左侧指标域')
    return
  }
  dialogMode.value = 'create'
  detail.id = null
  detail.indicatorDomainId = selectedDomainId.value
  detail.groupName = ''
  detail.targetTable = ''
  detail.groupCategory = 'UNIT'
  detail.description = ''
  detailIndicators.value = []
  resetSqlForm()
  groupDialogVisible.value = true
}

async function openView(row: GroupRow) {
  dialogMode.value = 'view'
  detailLoading.value = true
  groupDialogVisible.value = true
  try {
    const gRes = await api.get(`/analytics/domain/indicator-groups/${row.id}`)
    const g = gRes.data as GroupRow
    detail.id = g.id
    detail.indicatorDomainId = g.indicatorDomainId
    detail.groupName = g.groupName
    detail.targetTable = g.targetTable
    detail.groupCategory = g.groupCategory || 'UNIT'
    detail.description = g.description || ''
    await loadGroupSqlAndFields(row.id)
  } finally {
    detailLoading.value = false
  }
}

async function openDetail(row: GroupRow) {
  if (isPublishedGroup(row)) {
    await openView(row)
    return
  }
  dialogMode.value = 'edit'
  detailLoading.value = true
  groupDialogVisible.value = true
  try {
    const gRes = await api.get(`/analytics/domain/indicator-groups/${row.id}`)
    const g = gRes.data as GroupRow
    detail.id = g.id
    detail.indicatorDomainId = g.indicatorDomainId
    detail.groupName = g.groupName
    detail.targetTable = g.targetTable
    detail.groupCategory = g.groupCategory || 'UNIT'
    detail.description = g.description || ''
    await loadGroupSqlAndFields(row.id)
  } finally {
    detailLoading.value = false
  }
}

async function closeGroupDialog() {
  groupDialogVisible.value = false
  await loadGroups()
}

async function ensureGroupSaved(opts?: { silent?: boolean }): Promise<string | null> {
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
      detail.id = String(res.data)
      if (!opts?.silent) ElMessage.success('指标已保存')
    } else {
      await api.put(`/analytics/domain/indicator-groups/${detail.id}`, body)
      if (!opts?.silent) ElMessage.success('指标已更新')
    }
    return detail.id
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
    return null
  } finally {
    detailSaving.value = false
  }
}

function openDsPicker() {
  if (dialogReadonly.value) return
  dsDialog.value = true
}

function onMetaDsPicked(row: MetaBindSource) {
  sqlForm.datasourceKey = connectionKeyOf(row)
  sqlForm.datasourceName = row.sourceName
}

/** 执行：跑 SQL 并展示结果；编辑/新增时同步生成第三部分字段映射，查看模式仅预览不改映射 */
async function runExecute() {
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

    // 查看模式：只执行 SQL 并展示结果，不改写字段映射
    if (dialogReadonly.value) {
      ElMessage.success(`执行完成，共 ${previewRows.value.length} 行`)
      previewDialog.value = true
      return
    }

    let columns: string[] = previewColumns.value
    if (!columns.length) {
      const parseRes = await api.post(`/analytics/domain/${props.domain}/indicators/sql/parse`, {
        sqlText: sqlForm.sqlText,
      })
      columns = ((parseRes.data || []) as Array<{ resultField?: string }>)
        .map((f) => String(f.resultField || '').trim())
        .filter(Boolean)
    }
    if (!columns.length) {
      ElMessage.warning('未能从执行结果解析字段，请为 SELECT 列指定 AS 别名')
      return
    }
    detailIndicators.value = buildFieldsFromColumns(columns, detailIndicators.value)
    ElMessage.success(`已根据执行结果生成 ${detailIndicators.value.length} 个字段映射`)
    previewDialog.value = true
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '执行失败')
  } finally {
    previewLoading.value = false
  }
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

async function saveAll() {
  if (dialogReadonly.value) return
  if (!sqlForm.datasourceKey) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!sqlForm.sqlText.trim()) {
    ElMessage.warning('请填写查询语句')
    return
  }
  if (!detailIndicators.value.length) {
    ElMessage.warning('请先点击「执行」生成字段映射')
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
      fields: detailIndicators.value.map((f) => {
        const rf = String(f.resultField || '').trim()
        const safe = rf.replace(/[^a-zA-Z0-9_]/g, '_').toLowerCase() || 'col'
        return {
          resultField: rf,
          fieldType: (f.fieldType || 'VARCHAR').toUpperCase(),
          fieldLength: f.fieldLength != null && f.fieldLength > 0 ? f.fieldLength : 64,
          indicatorName: f.indicatorName || rf,
          fieldName: `ind_${safe}`,
          indicatorFlag: f.indicatorFlag || null,
        }
      }),
    })
    ElMessage.success('指标已保存')
    groupDialogVisible.value = false
    await loadGroups()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    sqlSaving.value = false
  }
}

async function removeIndicator(row: IndicatorRow) {
  if (dialogReadonly.value) return
  await ElMessageBox.confirm(`确认删除字段「${row.indicatorName || row.resultField}」？`, '删除确认', { type: 'warning' })
  if (row.id) {
    await api.delete(`/analytics/domain/indicators/${row.id}`)
  }
  detailIndicators.value = detailIndicators.value.filter((f) => f !== row)
  ElMessage.success('已删除')
}

async function removeCurrentGroup() {
  if (detail.id == null || dialogReadonly.value) return
  await ElMessageBox.confirm(`确认删除指标「${detail.groupName}」？`, '删除确认', { type: 'warning' })
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
    <!-- 列表：左指标域 + 右指标 -->
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
          <el-form-item label="指标名称" class="portal-field-md">
            <el-input v-model="query.groupName" clearable placeholder="请输入指标名称" />
          </el-form-item>
          <el-form-item label="指标表名" class="portal-field-md">
            <el-input v-model="query.targetTable" clearable placeholder="请输入指标表名" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :icon="Search" @click="loadGroups">查询</el-button>
            <el-button :icon="RefreshRight" @click="resetQuery">重置</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增</el-button>
            <el-button type="primary" @click="openBatchPublishDialog">批量发布</el-button>
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
          border
          size="small"
          empty-text="暂无数据"
          @selection-change="onGroupSelectionChange"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column label="指标名称" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">{{ row.groupName }}</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="targetTable" label="指标表名" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column v-if="embedTaskActions" label="调度状态" width="100">
            <template #default="{ row }">{{ taskOf(row) ? statusLabel(taskOf(row)!.scheduleStatus) : '—' }}</template>
          </el-table-column>
          <el-table-column v-if="embedTaskActions" label="执行状态" width="100">
            <template #default="{ row }">{{ taskOf(row) ? statusLabel(taskOf(row)!.execStatus, 'exec') : '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" :width="embedTaskActions ? 220 : 200" fixed="right">
            <template #default="{ row }">
              <template v-if="isPublishedGroup(row)">
                <el-button link type="primary" @click="openView(row)">查看</el-button>
              </template>
              <template v-else>
                <el-button link type="primary" :disabled="!canPublishGroup(row)" @click="openPublishDialog(row)">发布</el-button>
                <el-button link type="primary" @click="openDetail(row)">编辑</el-button>
                <el-button link type="danger" @click="deleteGroup(row)">删除</el-button>
              </template>
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

    <!-- 单条/批量指标「发布」：任务详情 -->
    <el-dialog
      v-model="publishVisible"
      :title="isBatchPublish ? `批量发布（${publishBatchIds.length}）` : '任务详情'"
      width="480px"
      destroy-on-close
    >
      <el-form
        ref="publishFormRef"
        :model="publishForm"
        :rules="publishRules"
        label-width="110px"
      >
        <el-form-item v-if="!isBatchPublish" label="任务名称" prop="taskName" required>
          <el-input v-model="publishForm.taskName" placeholder="请填写任务名称" />
        </el-form-item>
        <el-form-item v-else label="说明">
          <span class="hint-inline">将为勾选的 {{ publishBatchIds.length }} 条指标按同一执行周期发布</span>
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

    <!-- 指标信息：基础信息 + SQL + 字段映射（三合一） -->
    <el-dialog
      v-model="groupDialogVisible"
      :title="groupDialogTitle"
      width="960px"
      top="4vh"
      destroy-on-close
      class="ind-group-info-dialog"
      @closed="loadGroups"
    >
      <div v-loading="detailLoading" class="ind-group-dialog-body">
        <section class="ind-section">
          <div class="ind-section__title">一、基础信息</div>
          <el-form
            ref="detailFormRef"
            :model="detail"
            :rules="detailRules"
            label-width="120px"
            class="detail-form"
            :disabled="dialogReadonly"
          >
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="指标名称" prop="groupName" required>
                  <el-input v-model="detail.groupName" placeholder="请输入指标名称" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="指标表名" prop="targetTable" required>
                  <el-input
                    v-model="detail.targetTable"
                    clearable
                    placeholder="以 ind_ 开头，支持小写字母、数字、下划线"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="指标描述">
                  <el-input v-model="detail.description" placeholder="请输入描述" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </section>

        <section class="ind-section">
          <div class="ind-section__title">二、指标语句</div>
          <el-form label-width="96px" inline class="portal-inline-form sql-meta-form" :disabled="dialogReadonly">
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
                :disabled="dialogReadonly"
              />
            </el-form-item>
          </el-form>
          <div class="sql-card-head">
            <div class="sql-card-title">查询语句</div>
            <div class="sql-card-actions">
              <el-button type="primary" size="small" :icon="VideoPlay" :loading="previewLoading" @click="runExecute">
                执行
              </el-button>
              <el-button
                v-if="!dialogReadonly"
                size="small"
                :icon="Grid"
                :loading="previewLoading"
                @click="openPreview"
              >预览</el-button>
            </div>
          </div>
          <el-input
            v-model="sqlForm.sqlText"
            type="textarea"
            :rows="8"
            class="sql-editor"
            resize="vertical"
            :readonly="dialogReadonly"
            placeholder="请手动填写 SELECT 语句，并为结果列指定 AS 别名，例如：&#10;SELECT YEAR(NOW()) AS year_name FROM (SELECT 1) t"
          />
        </section>

        <section class="ind-section">
          <div class="ind-section__title">三、字段映射</div>
          <el-table
            class="portal-table detail-ind-table"
            :data="detailIndicators"
            stripe
            size="small"
            empty-text="暂无字段，请在上方编写 SQL 后点击「执行」生成"
            max-height="320"
          >
            <el-table-column type="index" label="序号" width="60" />
            <el-table-column label="字段名" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.fieldName || `ind_${row.resultField}` }}</template>
            </el-table-column>
            <el-table-column prop="resultField" label="查询结果字段" min-width="120" show-overflow-tooltip />
            <el-table-column label="指标名称" min-width="140">
              <template #default="{ row }">
                <el-input
                  v-if="!dialogReadonly"
                  v-model="row.indicatorName"
                  size="small"
                />
                <span v-else>{{ row.indicatorName }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数据类型" width="140">
              <template #default="{ row }">
                <el-select
                  v-if="!dialogReadonly"
                  v-model="row.fieldType"
                  size="small"
                  filterable
                >
                  <el-option v-for="t in MYSQL8_FIELD_TYPES" :key="t" :label="t" :value="t" />
                </el-select>
                <span v-else>{{ row.fieldType || 'VARCHAR' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="长度" width="110">
              <template #default="{ row }">
                <el-input-number
                  v-if="!dialogReadonly"
                  v-model="row.fieldLength"
                  size="small"
                  :min="1"
                  :max="65535"
                  controls-position="right"
                />
                <span v-else>{{ row.fieldLength ?? 64 }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!dialogReadonly" label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeIndicator(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>
      <template #footer>
        <el-button @click="closeGroupDialog">{{ dialogReadonly ? '关闭' : '取消' }}</el-button>
        <el-button
          v-if="!dialogReadonly && detail.id"
          type="danger"
          plain
          @click="removeCurrentGroup"
        >删除</el-button>
        <el-button
          v-if="!dialogReadonly"
          type="primary"
          :loading="detailSaving || sqlSaving"
          @click="saveAll"
        >保存</el-button>
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
          <template #default="{ row }">{{ statusLabel(row.execStatus, 'exec') }}</template>
        </el-table-column>
        <el-table-column label="结果" width="100">
          <template #default="{ row }">{{ statusLabel(row.calcResult, 'calc') }}</template>
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
.hint-inline {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
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
  margin-bottom: 0;
}
.detail-ind-table {
  margin-top: 8px;
}
.ind-section {
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.ind-section:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}
.ind-section__title {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.sql-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 8px 0 10px;
}
.sql-card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
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
.ind-group-info-dialog .el-dialog__body {
  max-height: calc(100vh - 180px);
  overflow: auto;
}
</style>
