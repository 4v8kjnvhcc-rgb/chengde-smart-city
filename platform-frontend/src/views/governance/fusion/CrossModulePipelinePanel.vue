<script setup lang="ts">
/**
 * 跨模块流水线：步骤类型/数量/顺序可调，发布到 DS 串行执行。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/http'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'

type StepType = 'INGEST' | 'GOVERNANCE' | 'QUALITY' | 'FUSION'

interface StepRow {
  stepType: StepType
  refId?: number
  refName?: string
  sortNo?: number
}

interface PipelineRow {
  id: number
  pipelineName: string
  description?: string
  scheduleCron?: string
  publishStatus?: string
  scheduleStatus?: string
  dsScheduleId?: number | null
  lastRunAt?: string
  lastMessage?: string
  stepCount?: number
  steps?: StepRow[]
}

interface Opt {
  id: number
  label: string
}

const STEP_TYPES: { value: StepType; label: string; tone: string }[] = [
  { value: 'INGEST', label: '归集', tone: 'ingest' },
  { value: 'GOVERNANCE', label: '治理', tone: 'gov' },
  { value: 'QUALITY', label: '质量', tone: 'quality' },
  { value: 'FUSION', label: '融合', tone: 'fusion' },
]

/** 列表链路最多展示步数，超出折叠为 +N */
const FLOW_VISIBLE = 3

const loading = ref(false)
const rows = ref<PipelineRow[]>([])
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
/** cronExpr -> 周期名称（来自执行周期管理） */
const cronNameMap = ref<Record<string, string>>({})

const query = reactive({
  keyword: '',
  publishStatus: '' as string,
  scheduleStatus: '' as string,
})

const form = reactive({
  pipelineName: '',
  description: '',
  scheduleCron: '',
  steps: [] as StepRow[],
})

const ingestOpts = ref<Opt[]>([])
const govOpts = ref<Opt[]>([])
const qualityOpts = ref<Opt[]>([])
const fusionOpts = ref<Opt[]>([])
const optsLoaded = ref(false)

/** 前端条件过滤（与质量方案等列表一致，查询立即可见效果） */
const filteredRows = computed(() => {
  let list = rows.value
  const kw = query.keyword.trim().toLowerCase()
  if (kw) {
    list = list.filter((r) => {
      const name = (r.pipelineName || '').toLowerCase()
      const desc = (r.description || '').toLowerCase()
      return name.includes(kw) || desc.includes(kw)
    })
  }
  if (query.publishStatus) {
    list = list.filter((r) => String(r.publishStatus || '') === query.publishStatus)
  }
  if (query.scheduleStatus) {
    list = list.filter((r) => String(r.scheduleStatus || '') === query.scheduleStatus)
  }
  return list
})

const {
  page,
  pageSize,
  paged,
  total,
  resetPage,
} = useClientPager(filteredRows)

const stepPreview = computed(() => form.steps)

function typeLabel(t?: string) {
  return STEP_TYPES.find((x) => x.value === t)?.label || t || '—'
}

function typeTone(t?: string) {
  return STEP_TYPES.find((x) => x.value === t)?.tone || 'ingest'
}

function publishStatusLabel(v?: string) {
  const s = String(v || '').toUpperCase()
  if (s === 'NONE') return '未发布'
  if (s === 'SUCCESS') return '已发布'
  if (s === 'FAILED') return '发布失败'
  return statusLabel(v)
}

function optionsFor(type: StepType): Opt[] {
  if (type === 'INGEST') return ingestOpts.value
  if (type === 'GOVERNANCE') return govOpts.value
  if (type === 'QUALITY') return qualityOpts.value
  return fusionOpts.value
}

async function loadList() {
  loading.value = true
  try {
    rows.value = (await api.get('/governance/cross-pipelines')).data || []
  } catch {
    rows.value = []
    ElMessage.error('加载流水线失败')
  } finally {
    loading.value = false
  }
}

/** 查询：刷新数据并按当前条件过滤，回到第 1 页 */
async function onQuery() {
  await loadList()
  resetPage()
}

function resetQuery() {
  query.keyword = ''
  query.publishStatus = ''
  query.scheduleStatus = ''
  resetPage()
}

async function ensureOptions() {
  if (optsLoaded.value) return
  try {
    const [jobs, gov, quality, fusion] = await Promise.all([
      api.get('/exchange/ingestion/collect/jobs'),
      api.get('/governance/gov-tasks', { params: { taskDomain: 'GOVERNANCE' } }),
      api.get('/governance/quality/schemes'),
      api.get('/governance/gov-tasks', { params: { taskDomain: 'FUSION' } }),
    ])
    ingestOpts.value = ((jobs.data || []) as Array<{ id: number; taskName?: string }>).map((j) => ({
      id: j.id,
      label: j.taskName || `作业#${j.id}`,
    }))
    govOpts.value = ((gov.data || []) as Array<{ id: number; taskName?: string }>).map((j) => ({
      id: j.id,
      label: j.taskName || `治理#${j.id}`,
    }))
    qualityOpts.value = ((quality.data || []) as Array<{ id: number; schemeName?: string }>).map((j) => ({
      id: j.id,
      label: j.schemeName || `方案#${j.id}`,
    }))
    fusionOpts.value = ((fusion.data || []) as Array<{ id: number; taskName?: string }>).map((j) => ({
      id: j.id,
      label: j.taskName || `融合#${j.id}`,
    }))
    optsLoaded.value = true
  } catch {
    ElMessage.error('加载步骤可选对象失败')
  }
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  form.pipelineName = ''
  form.description = ''
  form.scheduleCron = ''
  form.steps = [{ stepType: 'INGEST' }]
  dialogVisible.value = true
  void ensureOptions()
}

async function openEdit(row: PipelineRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  dialogVisible.value = true
  await ensureOptions()
  try {
    const detail = (await api.get(`/governance/cross-pipelines/${row.id}`)).data as PipelineRow
    form.pipelineName = detail.pipelineName || ''
    form.description = detail.description || ''
    form.scheduleCron = detail.scheduleCron || ''
    form.steps = (detail.steps || []).map((s) => ({
      stepType: (s.stepType || 'INGEST') as StepType,
      refId: s.refId,
      refName: s.refName,
    }))
    if (!form.steps.length) form.steps = [{ stepType: 'INGEST' }]
  } catch {
    ElMessage.error('加载流水线详情失败')
    dialogVisible.value = false
  }
}

function addStep(type: StepType) {
  form.steps.push({ stepType: type })
}

function removeStep(idx: number) {
  if (form.steps.length <= 1) {
    ElMessage.warning('至少保留一个步骤')
    return
  }
  form.steps.splice(idx, 1)
}

function moveStep(idx: number, dir: -1 | 1) {
  const j = idx + dir
  if (j < 0 || j >= form.steps.length) return
  const tmp = form.steps[idx]
  form.steps[idx] = form.steps[j]
  form.steps[j] = tmp
}

function onTypeChange(row: StepRow) {
  row.refId = undefined
  row.refName = undefined
}

function onRefChange(row: StepRow) {
  const opt = optionsFor(row.stepType).find((o) => o.id === row.refId)
  row.refName = opt?.label
}

async function save() {
  if (!form.pipelineName.trim()) {
    ElMessage.warning('请填写流水线名称')
    return
  }
  for (let i = 0; i < form.steps.length; i++) {
    const s = form.steps[i]
    if (s.refId == null) {
      ElMessage.warning(`请为第 ${i + 1} 步选择对象`)
      return
    }
  }
  saving.value = true
  try {
    const body = {
      pipelineName: form.pipelineName.trim(),
      description: form.description || undefined,
      scheduleCron: form.scheduleCron.trim() || undefined,
      steps: form.steps.map((s) => ({
        stepType: s.stepType,
        refId: s.refId,
      })),
    }
    if (dialogMode.value === 'edit' && editingId.value != null) {
      await api.put(`/governance/cross-pipelines/${editingId.value}`, body)
      ElMessage.success('已保存')
    } else {
      await api.post('/governance/cross-pipelines', body)
      ElMessage.success('已新建')
    }
    dialogVisible.value = false
    await loadList()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function publish(row: PipelineRow) {
  try {
    await api.post(`/governance/cross-pipelines/${row.id}/publish`)
    ElMessage.success('已发布到 DolphinScheduler；实时任务监控将显示待执行。如需立刻跑，请点「执行」')
    await loadList()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '发布失败')
  }
}

async function startSched(row: PipelineRow) {
  if (row.publishStatus !== 'SUCCESS') {
    ElMessage.warning('请先发布流水线')
    return
  }
  if (!row.scheduleCron && row.dsScheduleId == null) {
    ElMessage.warning('未配置执行周期，无法启动定时；请编辑填写周期后重新发布，或使用「执行」')
    return
  }
  try {
    await api.post(`/governance/cross-pipelines/${row.id}/start`)
    ElMessage.success('定时已启动')
    await loadList()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '启动失败')
  }
}

async function stopSched(row: PipelineRow) {
  try {
    await api.post(`/governance/cross-pipelines/${row.id}/stop`)
    ElMessage.success('定时已停止')
    await loadList()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '停止失败')
  }
}

async function runOnce(row: PipelineRow) {
  if (row.publishStatus !== 'SUCCESS') {
    ElMessage.warning('请先发布流水线')
    return
  }
  try {
    await api.post(`/governance/cross-pipelines/${row.id}/run-once`)
    ElMessage.success('已触发立即执行')
    await loadList()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '执行失败')
  }
}

async function remove(row: PipelineRow) {
  await ElMessageBox.confirm(
    `确认删除流水线「${row.pipelineName}」？将同步下线并删除 DolphinScheduler 中的对应流程定义。`,
    '删除确认',
    { type: 'warning' },
  )
  try {
    await api.delete(`/governance/cross-pipelines/${row.id}`)
    ElMessage.success('已删除（含 DS 流程）')
    await loadList()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '删除失败')
  }
}

async function purgeDsOrphans() {
  await ElMessageBox.confirm(
    '将清理 DolphinScheduler 项目 chengde_cross_pipeline 中，门户已无记录的「跨模块流水线_*」流程定义。继续？',
    '清理 DS 残留',
    { type: 'warning' },
  )
  try {
    const res = await api.post('/governance/cross-pipelines/purge-ds-orphans')
    const data = (res as { data?: { removed?: number; failed?: number } }).data || {}
    const removed = data.removed ?? 0
    const failed = data.failed ?? 0
    if (failed > 0) {
      ElMessage.warning(`已清理 ${removed} 条，失败 ${failed} 条（上线状态需先下线；可刷新 DS 后重试）`)
    } else {
      ElMessage.success(removed > 0 ? `已清理 ${removed} 条 DS 残留流程` : '没有需要清理的残留流程')
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '清理失败')
  }
}

function canRun(row: PipelineRow): boolean {
  return row.publishStatus === 'SUCCESS'
}

function canStart(row: PipelineRow): boolean {
  return row.publishStatus === 'SUCCESS' && !!(row.scheduleCron || row.dsScheduleId != null)
}

function rowSteps(row: PipelineRow): StepRow[] {
  return row.steps || []
}

function visibleFlowSteps(row: PipelineRow): StepRow[] {
  return rowSteps(row).slice(0, FLOW_VISIBLE)
}

function hiddenFlowCount(row: PipelineRow): number {
  return Math.max(0, rowSteps(row).length - FLOW_VISIBLE)
}

function flowFullText(row: PipelineRow): string {
  const steps = rowSteps(row)
  if (!steps.length) return ''
  return steps.map((s, i) => `${i + 1}.${typeLabel(s.stepType)}${s.refName ? `(${s.refName})` : ''}`).join(' → ')
}

function cycleDisplay(cron?: string): string {
  if (!cron) return '—'
  return cronNameMap.value[cron] || cron
}

async function loadCycleNames() {
  try {
    const list = (await api.get('/system/exec-cycles', { params: { status: 'ACTIVE' } })).data || []
    const map: Record<string, string> = {}
    for (const o of list as Array<{ cycleName?: string; cronExpr?: string }>) {
      if (o.cronExpr) map[o.cronExpr] = o.cycleName || o.cronExpr
    }
    cronNameMap.value = map
  } catch {
    cronNameMap.value = {}
  }
}

onMounted(async () => {
  await loadCycleNames()
  await loadList()
})
</script>

<template>
  <PageCard>
    <template #header>
      <div class="card-head">
        <div>
          <div class="card-head__title">跨模块流水线</div>
          <div class="card-head__sub">按顺序串联归集 / 治理 / 质量 / 融合，步骤可增删调序</div>
        </div>
        <div class="card-head__actions">
          <el-button @click="purgeDsOrphans">清理 DS 残留</el-button>
          <el-button type="primary" @click="openCreate">新建流水线</el-button>
        </div>
      </div>
    </template>

    <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="onQuery">
      <el-form-item label="名称" class="portal-field-lg">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="流水线名称"
          @keyup.enter="onQuery"
        />
      </el-form-item>
      <el-form-item label="发布状态" class="portal-field-md">
        <el-select v-model="query.publishStatus" clearable placeholder="全部" @change="resetPage">
          <el-option label="未发布" value="NONE" />
          <el-option label="已发布" value="SUCCESS" />
          <el-option label="发布失败" value="FAILED" />
        </el-select>
      </el-form-item>
      <el-form-item label="定时状态" class="portal-field-md">
        <el-select v-model="query.scheduleStatus" clearable placeholder="全部" @change="resetPage">
          <el-option label="已停止" value="STOPPED" />
          <el-option label="运行中" value="RUNNING" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" native-type="button" :loading="loading" @click="onQuery">查询</el-button>
        <el-button native-type="button" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" class="portal-table pipeline-table" :data="paged" stripe>
      <el-table-column prop="pipelineName" label="名称" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="name-cell">
            <span class="name-cell__title">{{ row.pipelineName }}</span>
            <span v-if="row.description" class="name-cell__desc">{{ row.description }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="执行链路" min-width="220">
        <template #default="{ row }">
          <el-tooltip
            v-if="rowSteps(row).length"
            :content="flowFullText(row)"
            placement="top"
            :disabled="rowSteps(row).length <= 1 && !hiddenFlowCount(row)"
          >
            <div class="flow-chips">
              <template v-for="(s, i) in visibleFlowSteps(row)" :key="`${row.id}-${i}`">
                <span v-if="i > 0" class="flow-arrow" aria-hidden="true">→</span>
                <span class="flow-chip" :class="`flow-chip--${typeTone(s.stepType)}`">
                  <i class="flow-chip__n">{{ i + 1 }}</i>
                  {{ typeLabel(s.stepType) }}
                </span>
              </template>
              <template v-if="hiddenFlowCount(row) > 0">
                <span class="flow-arrow" aria-hidden="true">→</span>
                <span class="flow-chip flow-chip--more">+{{ hiddenFlowCount(row) }}</span>
              </template>
            </div>
          </el-tooltip>
          <span v-else class="muted">{{ row.stepCount ? `${row.stepCount} 步` : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="发布" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.publishStatus)" size="small" effect="light">
            {{ publishStatusLabel(row.publishStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="定时" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.scheduleStatus)" size="small" effect="light">
            {{ statusLabel(row.scheduleStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="执行周期" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
            <span v-if="row.scheduleCron" class="cycle-cell">
            <span class="cycle-cell__name">{{ cycleDisplay(row.scheduleCron) }}</span>
          </span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="最近说明" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.lastMessage || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <div class="op-row">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="publish(row)">发布</el-button>
            <el-button link :disabled="!canRun(row)" @click="runOnce(row)">执行</el-button>
            <el-button
              v-if="row.scheduleStatus !== 'RUNNING'"
              link
              :disabled="!canStart(row)"
              @click="startSched(row)"
            >
              启动
            </el-button>
            <el-button v-else link type="warning" @click="stopSched(row)">停止</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建流水线' : '编辑流水线'"
      width="820px"
      destroy-on-close
      class="pipeline-dialog"
    >
      <el-form label-width="88px" class="pipeline-form">
        <el-form-item label="名称" required>
          <el-input v-model="form.pipelineName" maxlength="128" placeholder="请输入流水线名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选说明" />
        </el-form-item>
        <el-form-item label="执行周期">
          <ExecCycleSelect
            v-model="form.scheduleCron"
            :allow-custom="false"
            placeholder="请选择执行周期（来自系统配置）"
            style="max-width: 420px"
          />
        </el-form-item>

        <div class="flow-preview-panel">
          <div class="flow-preview-panel__label">执行预览</div>
          <div class="flow-preview-panel__track">
            <template v-for="(s, i) in stepPreview" :key="`pv-${i}`">
              <span v-if="i > 0" class="flow-arrow flow-arrow--lg">→</span>
              <span class="flow-chip flow-chip--lg" :class="`flow-chip--${typeTone(s.stepType)}`">
                <i class="flow-chip__n">{{ i + 1 }}</i>
                {{ typeLabel(s.stepType) }}
                <em v-if="s.refName" class="flow-chip__ref">{{ s.refName }}</em>
              </span>
            </template>
            <span v-if="!stepPreview.length" class="muted">暂无步骤</span>
          </div>
        </div>

        <div class="steps-block">
          <div class="steps-block__head">
            <span class="steps-block__title">步骤编排</span>
            <el-dropdown trigger="click" @command="(c: StepType) => addStep(c)">
              <el-button type="primary" plain size="small">+ 添加步骤</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="t in STEP_TYPES" :key="t.value" :command="t.value">
                    {{ t.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <div class="steps-editor">
            <div
              v-for="(s, idx) in form.steps"
              :key="idx"
              class="step-card"
              :class="`step-card--${typeTone(s.stepType)}`"
            >
              <div class="step-card__badge">{{ idx + 1 }}</div>
              <div class="step-card__fields">
                <el-select
                  v-model="s.stepType"
                  style="width: 112px"
                  @change="onTypeChange(s)"
                >
                  <el-option
                    v-for="t in STEP_TYPES"
                    :key="t.value"
                    :label="t.label"
                    :value="t.value"
                  />
                </el-select>
                <el-select
                  v-model="s.refId"
                  filterable
                  placeholder="选择业务对象"
                  style="flex: 1; min-width: 180px"
                  @change="onRefChange(s)"
                >
                  <el-option
                    v-for="o in optionsFor(s.stepType)"
                    :key="o.id"
                    :label="o.label"
                    :value="o.id"
                  />
                </el-select>
              </div>
              <div class="step-card__ops">
                <el-button
                  text
                  :disabled="idx === 0"
                  title="上移"
                  @click="moveStep(idx, -1)"
                >
                  ↑
                </el-button>
                <el-button
                  text
                  :disabled="idx === form.steps.length - 1"
                  title="下移"
                  @click="moveStep(idx, 1)"
                >
                  ↓
                </el-button>
                <el-button text type="danger" title="删除" @click="removeStep(idx)">×</el-button>
              </div>
            </div>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确定</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
}
.card-head__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--portal-text, #1f2937);
}
.card-head__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--portal-text-secondary, #6b7280);
}
.card-head__actions {
  display: flex;
  gap: 8px;
}

.name-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.35;
}
.name-cell__title {
  font-weight: 600;
  color: var(--portal-text, #1f2937);
}
.name-cell__desc {
  font-size: 12px;
  color: var(--portal-text-secondary, #6b7280);
}

.muted {
  color: var(--portal-text-secondary, #6b7280);
}
.flow-chip--more {
  color: #64748b;
  background: #f1f5f9;
  border-color: #e2e8f0;
  font-weight: 600;
  cursor: default;
}
.cycle-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.35;
}
.cycle-cell__name {
  font-size: 13px;
  color: var(--portal-text, #1f2937);
}
.cron-code {
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #f3f4f6;
  color: #6b7280;
  width: fit-content;
}

.flow-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  row-gap: 6px;
}
.flow-arrow {
  color: #9ca3af;
  font-size: 12px;
  user-select: none;
}
.flow-arrow--lg {
  font-size: 14px;
  margin: 0 2px;
}
.flow-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px 2px 4px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  border: 1px solid transparent;
  white-space: nowrap;
}
.flow-chip--lg {
  padding: 4px 10px 4px 6px;
  font-size: 13px;
}
.flow-chip__n {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  font-style: normal;
  font-size: 10px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.75);
}
.flow-chip__ref {
  font-style: normal;
  font-weight: 400;
  opacity: 0.75;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.flow-chip--ingest {
  color: #0f766e;
  background: #ecfdf5;
  border-color: #a7f3d0;
}
.flow-chip--gov {
  color: #1d4ed8;
  background: #eff6ff;
  border-color: #bfdbfe;
}
.flow-chip--quality {
  color: #b45309;
  background: #fffbeb;
  border-color: #fde68a;
}
.flow-chip--fusion {
  color: #0369a1;
  background: #f0f9ff;
  border-color: #bae6fd;
}

.op-row {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}

.flow-preview-panel {
  margin: 4px 0 16px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}
.flow-preview-panel__label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 10px;
  letter-spacing: 0.04em;
}
.flow-preview-panel__track {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-height: 32px;
}

.steps-block {
  margin-top: 4px;
}
.steps-block__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.steps-block__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--portal-text, #1f2937);
}

.steps-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.step-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 12px 12px 10px;
  border-radius: 10px;
  border: 1px solid var(--portal-border, #e5e7eb);
  background: #fff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.step-card:hover {
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
}
.step-card--ingest {
  border-left: 3px solid #14b8a6;
}
.step-card--gov {
  border-left: 3px solid #3b82f6;
}
.step-card--quality {
  border-left: 3px solid #f59e0b;
}
.step-card--fusion {
  border-left: 3px solid #0284c7;
}
.step-card__badge {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
  background: #64748b;
}
.step-card--ingest .step-card__badge { background: #0d9488; }
.step-card--gov .step-card__badge { background: #2563eb; }
.step-card--quality .step-card__badge { background: #d97706; }
.step-card--fusion .step-card__badge { background: #0284c7; }

.step-card__fields {
  flex: 1;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}
.step-card__ops {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.step-card__ops :deep(.el-button) {
  padding: 4px 6px;
  font-size: 16px;
  font-weight: 600;
}

.pipeline-form :deep(.el-form-item:last-of-type) {
  margin-bottom: 8px;
}
</style>
