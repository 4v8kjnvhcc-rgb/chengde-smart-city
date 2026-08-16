<script setup lang="ts">
/**
 * 质量方案管理：选用质量模型规则组成方案，定时经 DolphinScheduler。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import ExecCycleSelect, { type ExecCycleOption } from '@/views/system/ExecCycleSelect.vue'
import { useExecCycleLabel } from '@/utils/exec-cycle-label'

interface SchemeRow {
  id: number
  schemeName: string
  modelId: number
  modelName?: string
  executorAddress?: string
  cronExpr?: string
  cycleName?: string
  description?: string
  scheduleStatus: string
  generateStatus: string
  execStatus: string
  lastExecAt?: string
  lastMessage?: string
  updatedAt?: string
  rules?: SchemeRule[]
}

interface SchemeRule {
  id?: number
  modelRuleId: number
  ruleTypeName?: string
  ruleName?: string
  tableName?: string
  fieldNames?: string
  checkType?: string
}

interface ModelOpt {
  id: number
  modelName: string
}

interface ModelRuleOpt {
  id: number
  ruleTypeName?: string
  ruleName: string
  tableName?: string
  fieldNames?: string
  checkType?: string
}

interface LogRow {
  id: number
  status?: string
  score?: number
  issueCount?: number
  totalChecks?: number
  startedAt?: string
  endedAt?: string
  message?: string
}

const loading = ref(false)
const rows = ref<SchemeRow[]>([])
const selectedIds = ref<number[]>([])
const { label: cycleLabel } = useExecCycleLabel()
const filter = reactive({
  keyword: '',
  scheduleStatus: '' as string,
  cycleName: '',
})

const filtered = computed(() => {
  let list = rows.value
  const kw = filter.keyword.trim().toLowerCase()
  if (kw) {
    list = list.filter(
      (r) =>
        (r.schemeName || '').toLowerCase().includes(kw)
        || (r.modelName || '').toLowerCase().includes(kw),
    )
  }
  if (filter.scheduleStatus) {
    list = list.filter((r) => r.scheduleStatus === filter.scheduleStatus)
  }
  if (filter.cycleName) {
    list = list.filter((r) => r.cycleName === filter.cycleName)
  }
  return list
})

const { page, pageSize, paged, total, resetPage } = useClientPager(filtered)

const cycleOptions = computed(() => {
  const set = new Set<string>()
  for (const r of rows.value) {
    if (r.cycleName) set.add(r.cycleName)
  }
  return [...set]
})

const editorVisible = ref(false)
const editorSaving = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  schemeName: '',
  modelId: undefined as number | undefined,
  modelName: '',
  executorAddress: 'default',
  cronExpr: '',
  cycleName: '',
  description: '',
})
const selectedRules = ref<SchemeRule[]>([])

const modelPickerVisible = ref(false)
const models = ref<ModelOpt[]>([])
const modelKw = ref('')
const filteredModels = computed(() => {
  const kw = modelKw.value.trim().toLowerCase()
  if (!kw) return models.value
  return models.value.filter((m) => m.modelName.toLowerCase().includes(kw))
})
const {
  page: modelPage,
  pageSize: modelPageSize,
  paged: pagedModels,
  total: modelTotal,
  resetPage: resetModelPage,
} = useClientPager(filteredModels)
const pickedModelId = ref<number | null>(null)

const ruleTransferVisible = ref(false)
const allModelRules = ref<ModelRuleOpt[]>([])
const leftTableFilter = ref('')
const rightTableFilter = ref('')
const leftChecked = ref<number[]>([])
const rightChecked = ref<number[]>([])

const leftRules = computed(() => {
  const selected = new Set(selectedRules.value.map((r) => r.modelRuleId))
  let list = allModelRules.value.filter((r) => !selected.has(r.id))
  const t = leftTableFilter.value.trim()
  if (t) list = list.filter((r) => (r.tableName || '') === t)
  return list
})
const rightRules = computed(() => {
  let list = selectedRules.value
  const t = rightTableFilter.value.trim()
  if (t) list = list.filter((r) => (r.tableName || '') === t)
  return list
})
const tableNameOptions = computed(() => {
  const set = new Set<string>()
  for (const r of allModelRules.value) {
    if (r.tableName) set.add(r.tableName)
  }
  return [...set]
})

const logVisible = ref(false)
const logLoading = ref(false)
const logRows = ref<LogRow[]>([])
const logTitle = ref('日志详情')
const logSchemeId = ref<number | null>(null)
const logFilter = reactive({
  execResult: '' as string,
  timeRange: null as [Date, Date] | null,
})

const filteredLogs = computed(() => {
  let list = logRows.value
  if (logFilter.execResult) {
    list = list.filter((r) => String(r.status || '') === logFilter.execResult)
  }
  if (logFilter.timeRange?.length === 2) {
    const [from, to] = logFilter.timeRange
    const t0 = from.getTime()
    const t1 = to.getTime()
    list = list.filter((r) => {
      const t = r.startedAt ? new Date(r.startedAt).getTime() : NaN
      if (Number.isNaN(t)) return false
      return t >= t0 && t <= t1
    })
  }
  return list
})

async function loadList() {
  loading.value = true
  try {
    rows.value = (await api.get('/governance/quality/schemes')).data || []
    resetPage()
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function onQuery() {
  resetPage()
}

function onReset() {
  filter.keyword = ''
  filter.scheduleStatus = ''
  filter.cycleName = ''
  resetPage()
}

function openCreate() {
  editingId.value = null
  form.schemeName = ''
  form.modelId = undefined
  form.modelName = ''
  form.executorAddress = 'default'
  form.cronExpr = ''
  form.cycleName = ''
  form.description = ''
  selectedRules.value = []
  editorVisible.value = true
}

async function openEdit(row: SchemeRow) {
  const detail = (await api.get(`/governance/quality/schemes/${row.id}`)).data as SchemeRow
  editingId.value = row.id
  form.schemeName = detail.schemeName
  form.modelId = detail.modelId
  form.modelName = detail.modelName || ''
  form.executorAddress = detail.executorAddress || 'default'
  form.cronExpr = detail.cronExpr || ''
  form.cycleName = detail.cycleName || ''
  form.description = detail.description || ''
  selectedRules.value = (detail.rules || []).map((r) => ({ ...r }))
  editorVisible.value = true
}

function onCycleChange(opt: ExecCycleOption | null) {
  form.cycleName = opt?.cycleName || (form.cronExpr ? '自定义' : '')
}

async function openModelPicker() {
  models.value = ((await api.get('/governance/quality/models')).data || []).map(
    (m: { id: number; modelName: string }) => ({ id: m.id, modelName: m.modelName }),
  )
  pickedModelId.value = form.modelId ?? null
  modelKw.value = ''
  resetModelPage()
  modelPickerVisible.value = true
}

function confirmModel() {
  if (pickedModelId.value == null) {
    ElMessage.warning('请选择质量模型')
    return
  }
  const m = models.value.find((x) => x.id === pickedModelId.value)
  if (!m) return
  if (form.modelId !== m.id) {
    selectedRules.value = []
  }
  form.modelId = m.id
  form.modelName = m.modelName
  modelPickerVisible.value = false
}

async function openRuleTransfer() {
  if (form.modelId == null) {
    ElMessage.warning('请先选择质量模型')
    return
  }
  allModelRules.value = (await api.get(`/governance/quality/models/${form.modelId}/rules`)).data || []
  leftTableFilter.value = ''
  rightTableFilter.value = ''
  leftChecked.value = []
  rightChecked.value = []
  ruleTransferVisible.value = true
}

function moveRight() {
  const set = new Set(leftChecked.value)
  for (const r of allModelRules.value) {
    if (!set.has(r.id)) continue
    if (selectedRules.value.some((x) => x.modelRuleId === r.id)) continue
    selectedRules.value.push({
      modelRuleId: r.id,
      ruleTypeName: r.ruleTypeName,
      ruleName: r.ruleName,
      tableName: r.tableName,
      fieldNames: r.fieldNames,
      checkType: r.checkType,
    })
  }
  leftChecked.value = []
}

function moveLeft() {
  const set = new Set(rightChecked.value)
  selectedRules.value = selectedRules.value.filter((r) => !set.has(r.modelRuleId))
  rightChecked.value = []
}

async function saveEditor() {
  if (!form.schemeName.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  if (form.modelId == null) {
    ElMessage.warning('请选择质量模型')
    return
  }
  if (!form.cronExpr.trim()) {
    ElMessage.warning('请选择执行周期')
    return
  }
  if (!selectedRules.value.length) {
    ElMessage.warning('请添加质量规则')
    return
  }
  editorSaving.value = true
  try {
    const body = {
      schemeName: form.schemeName.trim(),
      modelId: form.modelId,
      modelName: form.modelName,
      executorAddress: form.executorAddress || 'default',
      cronExpr: form.cronExpr,
      cycleName: form.cycleName || undefined,
      description: form.description || undefined,
      ruleIds: selectedRules.value.map((r) => r.modelRuleId),
    }
    if (editingId.value) {
      await api.put(`/governance/quality/schemes/${editingId.value}`, body)
      ElMessage.success('已更新')
    } else {
      await api.post('/governance/quality/schemes', body)
      ElMessage.success('已创建')
    }
    editorVisible.value = false
    await loadList()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '保存失败')
  } finally {
    editorSaving.value = false
  }
}

function selectedRows(): SchemeRow[] {
  const set = new Set(selectedIds.value)
  return rows.value.filter((r) => set.has(r.id))
}

async function batchPublish() {
  const list = selectedRows()
  if (!list.length) return ElMessage.warning('请先勾选记录')
  for (const row of list) {
    try {
      await api.post(`/governance/quality/schemes/${row.id}/generate`)
      ElMessage.success(`「${row.schemeName}」已发布，可在「数据质量监控」全程追溯`)
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(`「${row.schemeName}」发布失败：${msg || '请检查 DolphinScheduler'}`)
    }
  }
  await loadList()
}

async function batchExecute() {
  const list = selectedRows()
  if (!list.length) return ElMessage.warning('请先勾选记录')
  const ok = await openVarDialog()
  if (!ok) return
  for (const row of list) {
    try {
      const res = await api.post(`/governance/quality/schemes/${row.id}/execute`, {
        variables: execVariables.value.filter((v) => v.name.trim()),
      })
      ElMessage.success(`「${row.schemeName}」执行完成 · 评分 ${res.data?.score ?? '—'}`)
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(`「${row.schemeName}」执行失败：${msg || '未知错误'}`)
    }
  }
  await loadList()
}

async function batchStart() {
  const list = selectedRows()
  if (!list.length) return ElMessage.warning('请先勾选记录')
  const ok = await openVarDialog()
  if (!ok) return
  for (const row of list) {
    try {
      await api.post(`/governance/quality/schemes/${row.id}/start`, {
        variables: execVariables.value.filter((v) => v.name.trim()),
      })
      ElMessage.success(`「${row.schemeName}」已启动`)
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(`「${row.schemeName}」启动失败：${msg || '请先发布'}`)
    }
  }
  await loadList()
}

const varDialogVisible = ref(false)
const execVariables = ref<Array<{ name: string; value: string }>>([{ name: '', value: '' }])
let varDialogResolve: ((ok: boolean) => void) | null = null

function openVarDialog(): Promise<boolean> {
  execVariables.value = [{ name: '', value: '' }]
  varDialogVisible.value = true
  return new Promise((resolve) => {
    varDialogResolve = resolve
  })
}

function confirmVarDialog() {
  varDialogVisible.value = false
  varDialogResolve?.(true)
  varDialogResolve = null
}

function cancelVarDialog() {
  varDialogVisible.value = false
  varDialogResolve?.(false)
  varDialogResolve = null
}

function addVarRow() {
  execVariables.value.push({ name: '', value: '' })
}

function removeVarRow(i: number) {
  if (execVariables.value.length <= 1) {
    execVariables.value[0] = { name: '', value: '' }
    return
  }
  execVariables.value.splice(i, 1)
}

async function batchStop() {
  const list = selectedRows()
  if (!list.length) return ElMessage.warning('请先勾选记录')
  for (const row of list) {
    try {
      await api.post(`/governance/quality/schemes/${row.id}/stop`)
      ElMessage.success(`「${row.schemeName}」已停止`)
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(msg || '停止失败')
    }
  }
  await loadList()
}

async function batchDelete() {
  const list = selectedRows()
  if (!list.length) return ElMessage.warning('请先勾选记录')
  await ElMessageBox.confirm(`确认删除选中的 ${list.length} 条记录？`, '批量删除确认', { type: 'warning' })
  for (const row of list) {
    try {
      await api.delete(`/governance/quality/schemes/${row.id}`)
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
      ElMessage.error(`「${row.schemeName}」删除失败：${msg || ''}`)
    }
  }
  selectedIds.value = []
  ElMessage.success('批量删除已处理')
  await loadList()
}

async function openLogs(row: SchemeRow) {
  logTitle.value = '日志详情'
  logSchemeId.value = row.id
  logFilter.execResult = ''
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  const start = new Date()
  start.setDate(start.getDate() - 1)
  start.setHours(0, 0, 0, 0)
  logFilter.timeRange = [start, end]
  logVisible.value = true
  await loadLogs()
}

async function loadLogs() {
  if (logSchemeId.value == null) return
  logLoading.value = true
  try {
    logRows.value = (await api.get(`/governance/quality/schemes/${logSchemeId.value}/logs`)).data || []
  } catch {
    logRows.value = []
    ElMessage.error('加载日志失败')
  } finally {
    logLoading.value = false
  }
}

function resetLogFilter() {
  logFilter.execResult = ''
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  const start = new Date()
  start.setDate(start.getDate() - 1)
  start.setHours(0, 0, 0, 0)
  logFilter.timeRange = [start, end]
}

function publishZh(s: string) {
  if (s === 'NONE' || !s) return '未发布'
  if (s === 'SUCCESS') return '成功'
  if (s === 'FAILED') return '失败'
  return statusLabel(s)
}

function scheduleZh(s: string) {
  if (s === 'RUNNING') return '正在运行'
  if (s === 'STOPPED') return '已停止'
  return statusLabel(s)
}

function formatDt(v?: string) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

onMounted(loadList)
</script>

<template>
  <div class="scheme-panel">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="任务名称" class="portal-field-lg">
        <el-input v-model="filter.keyword" clearable placeholder="名称 / 模型" @keyup.enter="onQuery" />
      </el-form-item>
      <el-form-item label="状态" class="portal-field-md">
        <el-select v-model="filter.scheduleStatus" clearable placeholder="全部">
          <el-option label="正在运行" value="RUNNING" />
          <el-option label="已停止" value="STOPPED" />
        </el-select>
      </el-form-item>
      <el-form-item label="执行周期" class="portal-field-md">
        <el-select v-model="filter.cycleName" clearable placeholder="全部">
          <el-option v-for="c in cycleOptions" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="onQuery">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <div class="toolbar__group">
        <el-button type="primary" @click="openCreate">+ 新增</el-button>
        <el-button type="danger" plain @click="batchDelete">批量删除</el-button>
        <el-button @click="loadList" :loading="loading">刷新</el-button>
      </div>
      <div class="toolbar__group">
        <el-tooltip placement="top" content="发布到 DolphinScheduler（创建流程与周期）；发布后默认为已停止，需再点启动才会按周期跑">
          <el-button type="primary" plain @click="batchPublish">发布</el-button>
        </el-tooltip>
        <el-tooltip placement="top" content="按执行周期自动触发稽核（须先发布成功）">
          <el-button type="success" plain @click="batchStart">启动</el-button>
        </el-tooltip>
        <el-tooltip placement="top" content="停止按周期自动执行，配置仍保留">
          <el-button type="warning" plain @click="batchStop">停止</el-button>
        </el-tooltip>
        <el-tooltip placement="top" content="立即跑一轮质量稽核；结果写入执行状态与日志">
          <el-button @click="batchExecute">执行</el-button>
        </el-tooltip>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="paged"
      stripe
      size="small"
      class="scheme-table"
      @selection-change="(sel: SchemeRow[]) => (selectedIds = sel.map((r) => r.id))"
    >
      <el-table-column type="selection" width="44" />
      <el-table-column type="index" label="序号" width="56" />
      <el-table-column prop="schemeName" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="执行周期" width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.cycleName || cycleLabel(row.cronExpr) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.scheduleStatus === 'RUNNING' ? 'success' : 'info'">
            {{ scheduleZh(row.scheduleStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.generateStatus)">{{ publishZh(row.generateStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="执行状态" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.execStatus)">{{ statusLabel(row.execStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastExecAt" label="最近执行" width="170" />
      <el-table-column prop="updatedAt" label="更新时间" width="170" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button link @click="openLogs(row)">日志</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    <el-empty v-if="!loading && !filtered.length" description="暂无数据，请点击新增" />

    <!-- 新增/修改 -->
    <el-drawer
      v-model="editorVisible"
      :title="editingId ? '修改' : '新增'"
      size="720px"
      destroy-on-close
    >
      <div class="editor">
        <div class="sec-title">基础信息</div>
        <el-form label-width="96px">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="名称" required>
                <el-input v-model="form.schemeName" placeholder="请输入名称" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="模型名称" required>
                <el-input
                  :model-value="form.modelName"
                  readonly
                  placeholder="请选择质量模型"
                  @click="openModelPicker"
                >
                  <template #append>
                    <el-button @click="openModelPicker">选择</el-button>
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="执行器地址">
                <el-select v-model="form.executorAddress" style="width: 100%">
                  <el-option label="默认" value="default" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="执行周期" required>
                <ExecCycleSelect v-model="form.cronExpr" :allow-custom="false" @change="onCycleChange" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="描述">
                <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入描述" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <div class="sec-title row-between">
          <span>规则明细</span>
          <el-button type="primary" circle size="small" @click="openRuleTransfer">+</el-button>
        </div>
        <el-table :data="selectedRules" stripe size="small" max-height="280">
          <el-table-column prop="ruleTypeName" label="规则类型" width="120" />
          <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="tableName" label="表名" width="140" show-overflow-tooltip />
          <el-table-column prop="fieldNames" label="字段名" width="120" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!selectedRules.length" description="暂无数据" :image-size="64" />

        <div class="editor-actions">
          <el-button @click="editorVisible = false">取消</el-button>
          <el-button type="primary" :loading="editorSaving" @click="saveEditor">确定</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 选择模型 -->
    <el-dialog v-model="modelPickerVisible" title="选择质量模型" width="480px" destroy-on-close>
      <el-input v-model="modelKw" clearable placeholder="搜索模型" style="margin-bottom: 10px" @input="resetModelPage" />
      <el-table
        :data="pagedModels"
        size="small"
        highlight-current-row
        max-height="320"
        @current-change="(row: ModelOpt | null) => (pickedModelId = row?.id ?? null)"
        @row-click="(row: ModelOpt) => (pickedModelId = row.id)"
      >
        <el-table-column prop="modelName" label="模型名称" min-width="200">
          <template #default="{ row }">
            <span :class="{ 'is-picked': pickedModelId === row.id }">{{ row.modelName }}</span>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination v-model:page="modelPage" v-model:page-size="modelPageSize" :total="modelTotal" />
      <template #footer>
        <el-button @click="modelPickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmModel">确定</el-button>
      </template>
    </el-dialog>

    <!-- 穿梭选规则 -->
    <el-dialog v-model="ruleTransferVisible" title="选择质量规则" width="920px" destroy-on-close>
      <div class="transfer">
        <div class="transfer__pane">
          <div class="transfer__hd">所有规则</div>
          <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
            <el-form-item label="表名" class="portal-field-md">
              <el-select v-model="leftTableFilter" clearable filterable placeholder="输入表名筛选">
                <el-option v-for="t in tableNameOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-form>
          <el-table
            :data="leftRules"
            size="small"
            max-height="300"
            @selection-change="(sel: ModelRuleOpt[]) => (leftChecked = sel.map((r) => r.id))"
          >
            <el-table-column type="selection" width="40" />
            <el-table-column prop="ruleTypeName" label="规则类型" width="100" />
            <el-table-column prop="ruleName" label="名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="tableName" label="表名" width="110" show-overflow-tooltip />
            <el-table-column prop="fieldNames" label="字段名" width="90" show-overflow-tooltip />
          </el-table>
        </div>
        <div class="transfer__ops">
          <el-button @click="moveRight">&gt;</el-button>
          <el-button @click="moveLeft">&lt;</el-button>
        </div>
        <div class="transfer__pane">
          <div class="transfer__hd">已选规则</div>
          <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
            <el-form-item label="表名" class="portal-field-md">
              <el-select v-model="rightTableFilter" clearable filterable placeholder="输入表名筛选">
                <el-option v-for="t in tableNameOptions" :key="`r-${t}`" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-form>
          <el-table
            :data="rightRules"
            size="small"
            max-height="300"
            @selection-change="(sel: SchemeRule[]) => (rightChecked = sel.map((r) => r.modelRuleId))"
          >
            <el-table-column type="selection" width="40" />
            <el-table-column prop="ruleTypeName" label="规则类型" width="100" />
            <el-table-column prop="ruleName" label="名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="tableName" label="表名" width="110" show-overflow-tooltip />
            <el-table-column prop="fieldNames" label="字段名" width="90" show-overflow-tooltip />
          </el-table>
        </div>
      </div>
      <template #footer>
        <el-button @click="ruleTransferVisible = false">取消</el-button>
        <el-button type="primary" @click="ruleTransferVisible = false">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="logVisible"
      :title="logTitle"
      width="960px"
      destroy-on-close
      class="log-dialog"
    >
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="执行结果" class="portal-field-md">
          <el-select v-model="logFilter.execResult" clearable placeholder="全部">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="运行中" value="RUNNING" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行时间" class="portal-field-xl">
          <el-date-picker
            v-model="logFilter.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format=""
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" :loading="logLoading" @click="loadLogs">查询</el-button>
          <el-button @click="resetLogFilter">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="logLoading" :data="filteredLogs" stripe border size="small" max-height="420">
        <el-table-column prop="id" label="任务ID" width="90" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDt(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行时间" width="170">
          <template #default="{ row }">{{ formatDt(row.endedAt || row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="执行结果" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.message || statusLabel(row.status) }}
            <span v-if="row.score != null"> · 评分 {{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default>
            <span class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!logLoading && !filteredLogs.length" description="暂无数据" />

      <template #footer>
        <el-button @click="logVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="varDialogVisible"
      title="启动变量（可选）"
      width="520px"
      destroy-on-close
      @close="cancelVarDialog"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="自定义/波动等规则若配置了变量，可在此填写变量名与常量值；无变量可直接确定。"
        style="margin-bottom: 12px"
      />
      <div v-for="(row, idx) in execVariables" :key="idx" class="var-row">
        <el-input v-model="row.name" placeholder="变量名" style="width: 40%" />
        <el-input v-model="row.value" placeholder="变量值" style="width: 40%" />
        <el-button link type="danger" @click="removeVarRow(idx)">删</el-button>
      </div>
      <el-button link type="primary" @click="addVarRow">+ 添加变量</el-button>
      <template #footer>
        <el-button @click="cancelVarDialog">取消</el-button>
        <el-button type="primary" @click="confirmVarDialog">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.scheme-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-blank);
}
.toolbar__group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.toolbar__group + .toolbar__group {
  padding-left: 14px;
  border-left: 1px solid var(--el-border-color-lighter);
}
.toolbar__group--danger {
  margin-left: auto;
  border-left: none !important;
  padding-left: 0 !important;
}
.scheme-table {
  width: 100%;
}
.sec-title {
  font-size: 14px;
  font-weight: 650;
  margin: 4px 0 12px;
}
.row-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.editor-actions {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.is-picked {
  color: var(--el-color-primary);
  font-weight: 600;
}
.muted {
  color: var(--el-text-color-placeholder);
}
.transfer {
  display: grid;
  grid-template-columns: 1fr 56px 1fr;
  gap: 8px;
  align-items: start;
}
.transfer__pane {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px;
  min-height: 360px;
  background: var(--el-bg-color);
}
.transfer__hd {
  font-weight: 600;
  margin-bottom: 8px;
}
.transfer__ops {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: center;
  padding-top: 120px;
}
@media (max-width: 900px) {
  .toolbar__group + .toolbar__group {
    border-left: none;
    padding-left: 0;
  }
  .toolbar__group--danger {
    margin-left: 0;
  }
  .transfer {
    grid-template-columns: 1fr;
  }
  .transfer__ops {
    flex-direction: row;
    padding-top: 0;
  }
}
</style>
