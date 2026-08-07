<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'
import { ingestionRegisterCache } from '../ingestion-register-cache'
import {
  ingestionApi,
  type DataSource,
  type DataTable,
  type IngestTask,
} from '../useIngestionHub'

type AccessMode = 'SINGLE' | 'MULTI' | 'SQL'
type MapPair = { source: string; target: string; dataType?: string; length?: number; columnName?: string }

/** 接入任务状态色：成功绿 / 失败红 / 运行中橙 / 部分成功蓝 / 空闲灰 */
function jobStatusTagType(status: unknown): 'success' | 'warning' | 'info' | 'danger' | 'primary' {
  const key = String(status ?? '').trim().toUpperCase()
  if (key === 'SUCCESS') return 'success'
  if (key === 'FAILED' || key === 'ERROR') return 'danger'
  if (key === 'RUNNING') return 'warning'
  if (key === 'PARTIAL') return 'primary'
  return 'info' // IDLE 等
}

function jobSyncMode(row: IngestTask): 'T+1' | 'REALTIME' {
  try {
    const cfg = row.configJson ? JSON.parse(row.configJson) : {}
    return cfg.syncMode === 'REALTIME' ? 'REALTIME' : 'T+1'
  } catch {
    return 'T+1'
  }
}

/** 定时调度是否生效：仅「开」且非纯实时手动 */
function isScheduled(row: IngestTask): boolean {
  return row.enabled === 1 && jobSyncMode(row) !== 'REALTIME'
}

function cronDisplay(row: IngestTask): string {
  if (!isScheduled(row)) return '—'
  return row.scheduleCron?.trim() || '—'
}

const step = ref(0)
const busy = ref(false)
const runBusy = ref(false)
const jobs = ref<IngestTask[]>([])
const sources = ref<DataSource[]>([])
const tables = ref<DataTable[]>([])

const form = reactive({
  taskName: '',
  accessMode: 'SINGLE' as AccessMode,
  sourceId: undefined as number | undefined,
  syncMode: 'T+1',
  writeMode: 'FULL' as 'FULL' | 'INCREMENTAL',
  // single
  tableId: undefined as number | undefined,
  sourceTableMode: 'FIXED' as 'FIXED' | 'PREFIX_DATE',
  tablePrefix: '',
  datePattern: 'yyyyMMdd',
  dateOffsetDays: -1,
  incrementColumn: '',
  targetTable: '',
  // multi
  tableIds: [] as number[],
  excludeTableIds: [] as number[],
  targetTableRule: 'ods_{sourceTable}',
  // sql
  selectSql: '',
  sqlTargetTable: 'ods_sql_result',
  paramBizDate: 'DATE_OFFSET:-1',
  // mapping
  mappingMode: 'NAME' as 'ORDER' | 'NAME' | 'MANUAL',
  pairs: [] as MapPair[],
  // schedule
  scheduleCron: '0 0 2 * * ?',
  enabled: false,
})

const previewText = ref('')
const editingJobId = ref<number | undefined>()
const dialogVisible = ref(false)
const dialogTitle = computed(() => (editingJobId.value ? '编辑接入任务' : '新建接入任务'))

const filteredTables = computed(() => {
  if (!form.sourceId) return tables.value
  return tables.value.filter((t) => t.sourceId === form.sourceId)
})

const steps = ['接入模式', '数据来源', '同步策略', '数据去向', '字段映射', '调度执行']

function suggestOds(tb?: DataTable) {
  const raw = String(tb?.sourceTable || tb?.tableName || tb?.tableCode || '').trim()
  const sanitized = raw.replace(/[^A-Za-z0-9_]/g, '')
  if (!sanitized) return ''
  return sanitized.toLowerCase().startsWith('ods_') ? sanitized : `ods_${sanitized}`
}

function buildConfig() {
  const mapping = {
    mode: form.mappingMode,
    pairs: form.pairs.map((p) => ({
      source: p.source,
      target: p.target || p.source,
      dataType: p.dataType,
      length: p.length,
    })),
  }
  const config: Record<string, unknown> = {
    accessMode: form.accessMode,
    sourceId: form.sourceId,
    syncMode: form.syncMode,
    writeMode: form.writeMode,
    mapping,
    scheduleCron: form.scheduleCron,
  }
  if (form.accessMode === 'SINGLE') {
    config.single = {
      tableId: form.tableId,
      sourceTableMode: form.sourceTableMode,
      tablePrefix: form.tablePrefix,
      datePattern: form.datePattern,
      dateOffsetDays: form.dateOffsetDays,
      incrementColumn: form.incrementColumn,
      targetTable: form.targetTable,
    }
  } else if (form.accessMode === 'MULTI') {
    config.multi = {
      tableIds: form.tableIds,
      excludeTableIds: form.excludeTableIds,
      targetTableRule: form.targetTableRule,
    }
  } else {
    config.sql = {
      sourceId: form.sourceId,
      selectSql: form.selectSql,
      targetTable: form.sqlTargetTable,
      paramBindings: { biz_date: form.paramBizDate },
    }
  }
  return config
}

async function loadBase() {
  jobs.value = (await ingestionApi.jobs()).data || []
}

async function ensureDialogData() {
  if (sources.value.length && tables.value.length) return
  const [ds, tbs] = await Promise.all([
    ingestionRegisterCache.dataSources(),
    ingestionRegisterCache.tables(),
  ])
  sources.value = ds || []
  tables.value = tbs || []
}

async function openCreateDialog() {
  resetWizard()
  dialogVisible.value = true
  await ensureDialogData()
}

function onModeChange() {
  form.pairs = []
  previewText.value = ''
}

function onSyncModeChange() {
  if (form.syncMode === 'REALTIME') {
    form.enabled = false
  }
}

function onSingleTableChange() {
  const tb = tables.value.find((t) => t.id === form.tableId)
  form.targetTable = suggestOds(tb)
  form.pairs = []
}

async function loadMapping(mode: 'ORDER' | 'NAME' | 'MANUAL' = form.mappingMode) {
  form.mappingMode = mode
  if (form.accessMode === 'SQL') {
    if (!form.pairs.length) {
      ElMessage.info('条件 SQL 请手工填写源列→目标列；可先点预览确认 SQL')
    }
    return
  }
  const tableId = form.accessMode === 'SINGLE' ? form.tableId : form.tableIds[0]
  if (!tableId) {
    ElMessage.warning('请先选择源表')
    return
  }
  busy.value = true
  try {
    const res = await ingestionApi.mappingSuggest(tableId, mode)
    form.pairs = (res.data || []).map((p) => ({ ...p }))
    if (mode === 'ORDER') {
      // 顺序映射：目标列保持与源同序同名（与 NAME 一致，后续可改目标名）
    }
  } finally {
    busy.value = false
  }
}

async function doPreview() {
  busy.value = true
  try {
    const res = await ingestionApi.previewJob(buildConfig())
    previewText.value = JSON.stringify(res.data || {}, null, 2)
    ElMessage.success('预览已生成')
  } catch {
    /* interceptor */
  } finally {
    busy.value = false
  }
}

async function saveJob(andRun = false) {
  if (!form.taskName.trim()) {
    ElMessage.warning('请填写任务名称')
    return
  }
  if (form.accessMode === 'SINGLE' && !form.tableId) {
    ElMessage.warning('请选择源表')
    return
  }
  if (form.accessMode === 'MULTI' && !form.tableIds.length) {
    ElMessage.warning('请至少选择一张表')
    return
  }
  if (form.accessMode === 'SQL' && !form.selectSql.trim()) {
    ElMessage.warning('请填写 SELECT SQL')
    return
  }
  if (!form.pairs.length && form.accessMode !== 'MULTI') {
    ElMessage.warning('请配置字段映射')
    return
  }
  busy.value = true
  try {
    if (form.syncMode === 'REALTIME') {
      form.enabled = false
    }
    const body: Record<string, unknown> = {
      taskName: form.taskName.trim(),
      accessMode: form.accessMode,
      writeMode: form.writeMode,
      scheduleCron: form.scheduleCron,
      enabled: form.enabled,
      sourceId: form.sourceId,
      tableId: form.accessMode === 'SINGLE' ? form.tableId : undefined,
      targetTable: form.accessMode === 'SQL' ? form.sqlTargetTable : form.targetTable,
      config: buildConfig(),
    }
    let id = editingJobId.value
    if (id) {
      await ingestionApi.updateJob(id, body)
    } else {
      const res = await ingestionApi.createJob(body)
      id = res.data
      editingJobId.value = id
    }
    ElMessage.success(andRun ? '已保存，开始执行…' : '任务已保存')
    dialogVisible.value = false
    await reloadJobs()
    if (andRun && id) {
      await runJob(id)
    }
  } catch {
    /* interceptor */
  } finally {
    busy.value = false
  }
}

async function runJob(id: number) {
  runBusy.value = true
  try {
    const res = await ingestionApi.runJob(id)
    const d = res.data || {}
    if (d.accessMode === 'MULTI') {
      if (d.status === 'PARTIAL') {
        ElMessage.warning(String(d.message || '多表部分成功，请查看错误明细'))
      } else {
        ElMessage.success(`多表完成：${d.tableCount} 张表，共 ${d.collectedRows ?? 0} 行`)
      }
    } else {
      ElMessage.success(`已落入 smart_city_ods.${d.odsTable || ''}（${d.collectedRows ?? 0} 行）`)
    }
    await reloadJobs()
  } catch {
    await reloadJobs()
  } finally {
    runBusy.value = false
  }
}

async function resetJob(id: number) {
  try {
    await ingestionApi.resetJob(id)
    ElMessage.success('已重置，可重新执行')
    await reloadJobs()
  } catch {
    /* interceptor */
  }
}

async function deleteJob(row: IngestTask) {
  try {
    await ElMessageBox.confirm(
      `确定删除任务「${row.taskName}」？仅删除任务配置，不会删除 ODS 已落库数据。`,
      '删除确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await ingestionApi.deleteJob(row.id)
    ElMessage.success('任务已删除')
    if (editingJobId.value === row.id) {
      dialogVisible.value = false
    }
    await reloadJobs()
  } catch {
    /* interceptor */
  }
}

async function reloadJobs() {
  jobs.value = (await ingestionApi.jobs()).data || []
}

async function editJob(row: IngestTask) {
  editingJobId.value = row.id
  form.taskName = row.taskName
  form.accessMode = (row.accessMode as AccessMode) || 'SINGLE'
  form.writeMode = (row.writeMode as 'FULL' | 'INCREMENTAL') || 'FULL'
  form.scheduleCron = row.scheduleCron || '0 0 2 * * ?'
  form.enabled = row.enabled === 1
  form.sourceId = row.sourceId
  form.tableId = row.tableId
  form.targetTable = row.targetTable || ''
  try {
    const cfg = row.configJson ? JSON.parse(row.configJson) : {}
    form.syncMode = cfg.syncMode || 'T+1'
    if (cfg.single) {
      form.sourceTableMode = cfg.single.sourceTableMode || 'FIXED'
      form.tablePrefix = cfg.single.tablePrefix || ''
      form.datePattern = cfg.single.datePattern || 'yyyyMMdd'
      form.dateOffsetDays = cfg.single.dateOffsetDays ?? -1
      form.incrementColumn = cfg.single.incrementColumn || ''
      form.targetTable = cfg.single.targetTable || form.targetTable
      form.tableId = cfg.single.tableId || form.tableId
    }
    if (cfg.multi) {
      form.tableIds = cfg.multi.tableIds || []
      form.excludeTableIds = cfg.multi.excludeTableIds || []
      form.targetTableRule = cfg.multi.targetTableRule || 'ods_{sourceTable}'
    }
    if (cfg.sql) {
      form.selectSql = cfg.sql.selectSql || ''
      form.sqlTargetTable = cfg.sql.targetTable || 'ods_sql_result'
      form.paramBizDate = cfg.sql.paramBindings?.biz_date || 'DATE_OFFSET:-1'
      form.sourceId = cfg.sql.sourceId || form.sourceId
    }
    if (cfg.mapping?.pairs) {
      form.pairs = cfg.mapping.pairs
      form.mappingMode = cfg.mapping.mode || 'NAME'
    } else {
      form.pairs = []
    }
  } catch {
    /* ignore */
  }
  previewText.value = ''
  step.value = 0
  dialogVisible.value = true
  await ensureDialogData()
}

function resetWizard() {
  editingJobId.value = undefined
  form.taskName = ''
  form.accessMode = 'SINGLE'
  form.sourceId = undefined
  form.syncMode = 'T+1'
  form.writeMode = 'FULL'
  form.tableId = undefined
  form.sourceTableMode = 'FIXED'
  form.tablePrefix = ''
  form.datePattern = 'yyyyMMdd'
  form.dateOffsetDays = -1
  form.incrementColumn = ''
  form.tableIds = []
  form.excludeTableIds = []
  form.targetTableRule = 'ods_{sourceTable}'
  form.pairs = []
  form.selectSql = ''
  form.sqlTargetTable = 'ods_sql_result'
  form.paramBizDate = 'DATE_OFFSET:-1'
  form.targetTable = ''
  form.scheduleCron = '0 0 2 * * ?'
  form.enabled = false
  previewText.value = ''
  step.value = 0
}

function onDialogClosed() {
  resetWizard()
}

function canGoNext(): boolean {
  if (step.value === 0) {
    if (!form.taskName.trim()) {
      ElMessage.warning('请填写任务名称')
      return false
    }
  }
  if (step.value === 1) {
    if (form.accessMode === 'SINGLE' && !form.tableId) {
      ElMessage.warning('请选择源表')
      return false
    }
    if (form.accessMode === 'MULTI' && !form.tableIds.length) {
      ElMessage.warning('请至少选择一张表')
      return false
    }
    if (form.accessMode === 'SQL' && !form.selectSql.trim()) {
      ElMessage.warning('请填写 SELECT SQL')
      return false
    }
  }
  return true
}

function nextStep() {
  if (!canGoNext()) return
  if (step.value < steps.length - 1) step.value += 1
  if (step.value === 4 && form.accessMode !== 'MULTI' && !form.pairs.length) {
    loadMapping('NAME')
  }
}

onMounted(() => {
  loadBase()
})
</script>

<template>
  <div class="table-wizard">
    <PageCard>
      <template #header>
        <div class="wiz-head">
          <div>
            <div class="wiz-title">数据汇聚接入</div>
            <div class="wiz-sub">单表 / 多表 / 条件 SQL 任务列表；新建或编辑在弹窗中完成</div>
          </div>
          <el-button type="primary" @click="openCreateDialog">新建任务</el-button>
        </div>
      </template>

      <el-table :data="jobs" stripe size="small" empty-text="暂无接入任务，请点击右上角新建">
        <el-table-column prop="taskName" label="任务" min-width="140" align="center" header-align="center" />
        <el-table-column label="模式" width="90" align="center" header-align="center">
          <template #default="{ row }">
            {{ row.accessMode === 'MULTI' ? '多表' : row.accessMode === 'SQL' ? '条件' : '单表' }}
          </template>
        </el-table-column>
        <el-table-column prop="targetTable" label="目标表" min-width="120" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column label="同步" width="80" align="center" header-align="center">
          <template #default="{ row }">
            {{ jobSyncMode(row) === 'REALTIME' ? '实时' : 'T+1' }}
          </template>
        </el-table-column>
        <el-table-column label="调度" width="70" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="isScheduled(row) ? 'success' : 'info'" size="small" effect="plain">
              {{ isScheduled(row) ? '定时' : '手动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Cron" width="110" align="center" header-align="center">
          <template #default="{ row }">{{ cronDisplay(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="jobStatusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="collectedRows" label="行数" width="80" align="center" header-align="center" />
        <el-table-column prop="lastRunMessage" label="最近日志" min-width="180" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column label="操作" width="260" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="editJob(row)">编辑</el-button>
            <el-button link type="warning" @click="resetJob(row.id)">重置</el-button>
            <el-button link type="success" :loading="runBusy" :disabled="row.status === 'RUNNING'" @click="runJob(row.id)">执行</el-button>
            <el-button link type="danger" :disabled="row.status === 'RUNNING'" @click="deleteJob(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="860px"
      destroy-on-close
      append-to-body
      class="ingest-job-dialog"
      @closed="onDialogClosed"
    >
      <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 16px">
        <el-step v-for="s in steps" :key="s" :title="s" />
      </el-steps>

      <el-form label-width="120px" class="portal-inline-form portal-inline-form--block">
        <template v-if="step === 0">
          <el-form-item label="任务名称" class="portal-field-xl" required>
            <el-input v-model="form.taskName" placeholder="必填，如：人口库日批接入" maxlength="80" @keyup.enter="nextStep" />
          </el-form-item>
          <el-form-item label="接入模式">
            <el-radio-group v-model="form.accessMode" @change="onModeChange">
              <el-radio-button value="SINGLE">单表接入</el-radio-button>
              <el-radio-button value="MULTI">多表批量</el-radio-button>
              <el-radio-button value="SQL">条件接入</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </template>

        <template v-else-if="step === 1">
          <el-form-item label="数据源" class="portal-field-xl">
            <el-select v-model="form.sourceId" filterable clearable placeholder="可选，用于过滤表">
              <el-option v-for="s in sources" :key="s.id" :label="s.sourceName" :value="s.id" />
            </el-select>
          </el-form-item>
          <template v-if="form.accessMode === 'SINGLE'">
            <el-form-item label="源表" class="portal-field-xl">
              <el-select v-model="form.tableId" filterable placeholder="已登记表" @change="onSingleTableChange">
                <el-option
                  v-for="t in filteredTables"
                  :key="t.id"
                  :label="`${t.tableName}（${t.sourceTable || t.tableCode}）`"
                  :value="t.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="表名模式">
              <el-radio-group v-model="form.sourceTableMode">
                <el-radio value="FIXED">固定表名</el-radio>
                <el-radio value="PREFIX_DATE">分表前缀+日期</el-radio>
              </el-radio-group>
            </el-form-item>
            <template v-if="form.sourceTableMode === 'PREFIX_DATE'">
              <el-form-item label="表前缀" class="portal-field-lg">
                <el-input v-model="form.tablePrefix" placeholder="如 biz_order_" />
              </el-form-item>
              <el-form-item label="日期格式" class="portal-field-md">
                <el-input v-model="form.datePattern" placeholder="yyyyMMdd" />
              </el-form-item>
              <el-form-item label="日期偏移" class="portal-field-sm">
                <el-input-number v-model="form.dateOffsetDays" :min="-30" :max="0" />
              </el-form-item>
            </template>
          </template>
          <template v-else-if="form.accessMode === 'MULTI'">
            <el-form-item label="选择表" class="portal-field-xl">
              <el-select v-model="form.tableIds" multiple filterable collapse-tags placeholder="多选已登记表">
                <el-option
                  v-for="t in filteredTables"
                  :key="t.id"
                  :label="`${t.tableName}（${t.sourceTable || t.tableCode}）`"
                  :value="t.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button @click="form.tableIds = filteredTables.map((t) => t.id)">全选当前数据源</el-button>
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="SELECT SQL">
              <el-input
                v-model="form.selectSql"
                type="textarea"
                :rows="5"
                placeholder="SELECT a.id, b.name FROM t1 a JOIN t2 b ON ... WHERE dt = ${biz_date}"
                style="width: min(720px, 100%)"
              />
            </el-form-item>
            <el-form-item label="biz_date" class="portal-field-lg">
              <el-input v-model="form.paramBizDate" placeholder="DATE_OFFSET:-1" />
            </el-form-item>
          </template>
        </template>

        <template v-else-if="step === 2">
          <el-form-item label="同步约定">
            <el-radio-group v-model="form.syncMode" @change="onSyncModeChange">
              <el-radio value="T+1">T+1 日批</el-radio>
              <el-radio value="REALTIME">实时/立即</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="写入方式">
            <el-radio-group v-model="form.writeMode">
              <el-radio value="FULL">全量（重建目标表）</el-radio>
              <el-radio value="INCREMENTAL">增量（追加）</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.writeMode === 'INCREMENTAL' && form.accessMode === 'SINGLE'" label="增量列" class="portal-field-lg">
            <el-input v-model="form.incrementColumn" placeholder="如 create_time / update_time" />
          </el-form-item>
        </template>

        <template v-else-if="step === 3">
          <template v-if="form.accessMode === 'MULTI'">
            <el-form-item label="目标命名" class="portal-field-xl">
              <el-input v-model="form.targetTableRule" placeholder="ods_{sourceTable}" />
            </el-form-item>
          </template>
          <template v-else-if="form.accessMode === 'SQL'">
            <el-form-item label="目标表" class="portal-field-xl">
              <el-input v-model="form.sqlTargetTable" placeholder="ods_xxx" />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="目标表" class="portal-field-xl">
              <el-input v-model="form.targetTable" placeholder="默认 ods_源表名" />
            </el-form-item>
          </template>
        </template>

        <template v-else-if="step === 4">
          <p v-if="form.accessMode === 'MULTI'" class="hint">多表批量将按各表自己的登记字段自动同名映射，无需在此配置。</p>
          <template v-if="form.accessMode !== 'MULTI'">
            <el-form-item class="portal-form-actions">
              <el-button :loading="busy" @click="loadMapping('ORDER')">顺序映射</el-button>
              <el-button type="primary" :loading="busy" @click="loadMapping('NAME')">同名映射</el-button>
              <el-button @click="form.pairs.push({ source: '', target: '', dataType: 'VARCHAR' })">手工加行</el-button>
            </el-form-item>
            <el-table :data="form.pairs" stripe size="small" style="width: 100%; margin-bottom: 12px">
              <el-table-column label="源字段" min-width="140">
                <template #default="{ row }">
                  <el-input v-model="row.source" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="目标字段" min-width="140">
                <template #default="{ row }">
                  <el-input v-model="row.target" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="类型" width="120">
                <template #default="{ row }">
                  <el-input v-model="row.dataType" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ $index }">
                  <el-button link type="danger" @click="form.pairs.splice($index, 1)">删</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </template>

        <template v-else>
          <template v-if="form.syncMode === 'REALTIME'">
            <p class="hint">当前为「实时/立即」：不启用定时调度，仅可通过列表「执行」或保存后立即执行触发。</p>
          </template>
          <template v-else>
            <el-form-item label="执行周期" class="portal-field-cron">
              <ExecCycleSelect v-model="form.scheduleCron" />
            </el-form-item>
            <el-form-item label="启用调度">
              <el-switch v-model="form.enabled" />
            </el-form-item>
            <p class="hint">调度「开」后，后台每分钟扫描，按 Cron 到期自动执行；关闭则仅手动执行。</p>
          </template>
          <el-form-item class="portal-form-actions">
            <el-button :loading="busy" @click="doPreview">预览 SQL</el-button>
          </el-form-item>
          <pre v-if="previewText" class="preview-box">{{ previewText }}</pre>
        </template>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button :disabled="step === 0" @click="step -= 1">上一步</el-button>
          <el-button v-if="step < steps.length - 1" type="primary" @click="nextStep">下一步</el-button>
          <template v-else>
            <el-button type="primary" :loading="busy" @click="saveJob(false)">保存任务</el-button>
            <el-button type="success" :loading="busy || runBusy" @click="saveJob(true)">保存并立即执行</el-button>
          </template>
          <el-button @click="dialogVisible = false">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wiz-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}
.wiz-title {
  font-size: 15px;
  font-weight: 600;
}
.wiz-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--portal-text-secondary, #909399);
}
.dialog-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
.hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: #909399;
}
.preview-box {
  margin: 8px 0 0;
  padding: 10px 12px;
  max-height: 180px;
  overflow: auto;
  font-size: 12px;
  background: #f7f9fc;
  border: 1px solid var(--portal-border, #ebeef5);
  border-radius: 8px;
}
</style>
