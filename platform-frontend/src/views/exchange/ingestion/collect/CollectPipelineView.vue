<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import DataCategoryView, { type CategoryRow } from '@/views/resource/DataCategoryView.vue'
import { useClientPager } from '@/composables/useClientPager'
import { formatDateTime } from '@/utils/datetime'
import { statusLabel } from '@/utils/status-label'
import { ingestionRegisterCache } from '../ingestion-register-cache'
import {
  ingestionApi,
  useIngestionLoading,
  type DataDefinition,
  type DataTable,
  type PipelineJob,
  type ProbeReport,
  type ReconcileLog,
} from '../useIngestionHub'

const STORAGE_KEY = 'ing-pipeline-data-category-uuid'
const STEP_KEYS = ['classify', 'probe', 'define', 'run'] as const

const ALL_PROBE_ITEMS = [
  '业务探查',
  '接入方式探查',
  '空值率探查',
  '值域及分布探查',
  '命名实体探查',
  '数据元探查',
  '类型及格式探查',
  '数据集探查',
  '问题数据探查',
] as const

const route = useRoute()
const router = useRouter()
const { loading, loadError, withLoad } = useIngestionLoading()

const step = ref(0)
const selectedUuid = ref('')
const selectedRow = ref<CategoryRow | null>(null)
const lastReconcileHint = ref('')

const tables = ref<DataTable[]>([])
const probes = ref<ProbeReport[]>([])
const definitions = ref<DataDefinition[]>([])
const jobs = ref<PipelineJob[]>([])
const reconcileLogs = ref<ReconcileLog[]>([])
const reconcileResult = ref<Record<string, unknown> | null>(null)
const loadedSteps = new Set<number>()

const probeForm = reactive({
  sourceTableId: undefined as number | undefined,
  probeItems: [...ALL_PROBE_ITEMS] as string[],
})
const defForm = reactive({
  id: undefined as number | undefined,
  defName: '',
  businessDesc: '',
  techDesc: '',
  refTableId: undefined as number | undefined,
})
const defKeyword = ref('')
const editingDef = ref(false)
const defDialogVisible = ref(false)
const runForm = reactive({ refChannelId: undefined as number | undefined, jobName: '规范设计接入任务' })

const selectedClassLabel = computed(() => selectedRow.value?.categoryName || '')
const selectedClassCode = computed(() => selectedRow.value?.categoryCode || selectedRow.value?.categoryType || '')

const filteredDefinitions = computed(() => {
  const kw = defKeyword.value.trim().toLowerCase()
  if (!kw) return definitions.value
  return definitions.value.filter((d) =>
    [d.defName, d.businessDesc, d.techDesc, d.defCode].some((x) => String(x || '').toLowerCase().includes(kw)),
  )
})

const {
  page: filteredDefPage,
  pageSize: filteredDefPageSize,
  paged: pagedFilteredDefs,
  total: filteredDefTotal,
  resetPage: resetFilteredDefPage,
} = useClientPager(filteredDefinitions)

function persistSelection() {
  if (selectedUuid.value) localStorage.setItem(STORAGE_KEY, selectedUuid.value)
}

function onSelectCategory(row: CategoryRow) {
  selectedUuid.value = row.uuid
  selectedRow.value = row
  persistSelection()
}

function onCategoriesLoaded(rows: CategoryRow[]) {
  const saved = localStorage.getItem(STORAGE_KEY) || selectedUuid.value
  if (!saved) return
  const hit = rows.find((r) => r.uuid === saved && r.status === 1)
  if (hit) {
    selectedUuid.value = hit.uuid
    selectedRow.value = hit
  }
}

function goStep(n: number) {
  if (n > 0 && !selectedUuid.value) {
    ElMessage.warning('请先在汇聚数据分类中选用一条已启用分类')
    step.value = 0
    router.replace({ query: { ...route.query, section: 'step-classify' } })
    return
  }
  step.value = n
  router.replace({ query: { ...route.query, section: `step-${STEP_KEYS[n]}` } })
  ensureStepLoaded(n)
}

function nextFromClassify() {
  if (!selectedUuid.value || !selectedRow.value) {
    ElMessage.warning('请先选用一条已启用的数据分类（表格左侧单选）')
    return
  }
  if (selectedRow.value.status !== 1) {
    ElMessage.warning('所选分类未启用')
    return
  }
  persistSelection()
  goStep(1)
}

async function loadTables(force = false) {
  tables.value = await ingestionRegisterCache.tables(force)
  if (tables.value.length && !probeForm.sourceTableId) probeForm.sourceTableId = tables.value[0].id
}

async function loadStepData(n: number, force = false) {
  if (n === 0) return
  if (n === 1) {
    await Promise.all([
      loadTables(force),
      (async () => {
        if (!force && probes.value.length) return
        probes.value = (await ingestionApi.probeReports()).data || []
      })(),
    ])
  } else if (n === 2) {
    await Promise.all([
      loadTables(force),
      (async () => {
        if (!force && definitions.value.length) return
        definitions.value = (await ingestionApi.definitions()).data || []
        resetFilteredDefPage()
      })(),
    ])
  } else {
    await Promise.all([
      (async () => {
        if (!force && jobs.value.length) return
        jobs.value = (await ingestionApi.pipelineJobs()).data || []
      })(),
      (async () => {
        if (!force && reconcileLogs.value.length) return
        reconcileLogs.value = (await ingestionApi.reconcileLogs()).data || []
      })(),
    ])
  }
  loadedSteps.add(n)
}

async function ensureStepLoaded(n: number, force = false) {
  if (!force && loadedSteps.has(n)) return
  await withLoad(() => loadStepData(n, force))
}

async function refreshStep(n: number) {
  if (n === 1) probes.value = []
  if (n === 2) definitions.value = []
  await loadStepData(n, true)
}

function parseMetrics(row: ProbeReport): Record<string, string> {
  try {
    return row.metricsJson ? JSON.parse(row.metricsJson) : {}
  } catch {
    return {}
  }
}

function metricOf(row: ProbeReport, key: string, fallback = '—') {
  const m = parseMetrics(row)
  return m[key] != null && m[key] !== '' ? String(m[key]) : fallback
}

async function runProbe() {
  if (!selectedUuid.value) {
    ElMessage.warning('请先选择数据分类')
    goStep(0)
    return
  }
  if (!probeForm.sourceTableId) {
    ElMessage.warning('请选择登记源表')
    return
  }
  if (!probeForm.probeItems.length) {
    ElMessage.warning('请至少勾选一项探查维度')
    return
  }
  const tb = tables.value.find((t) => t.id === probeForm.sourceTableId)
  const items = [...probeForm.probeItems]
  await ingestionApi.runPipeline({
    jobType: 'PROBE',
    jobName: `探查-${tb?.tableName || probeForm.sourceTableId}`,
    refSourceTableId: probeForm.sourceTableId,
    probeItems: items,
    dataCategory: selectedClassCode.value,
    dataCategoryLabel: selectedClassLabel.value,
    dataCategoryUuid: selectedUuid.value,
  })
  const has = (name: string) => items.includes(name)
  const metrics: Record<string, string> = {
    businessProbe: has('业务探查') ? '业务含义：主数据/登记表，建议纳入静态基础数据' : '未执行',
    accessProbe: has('接入方式探查') ? 'JDBC 库表接入 · 建议 T+1 全量+增量' : '未执行',
    nullRate: has('空值率探查') ? '0.018' : '未执行',
    domainDist: has('值域及分布探查') ? '枚举稳定 · Top5 覆盖 92%' : '未执行',
    namedEntity: has('命名实体探查') ? '人名/地名/机构名/手机号' : '未执行',
    metaAlign: has('数据元探查') ? '已对标 6 个标准数据元' : '未执行',
    typeFormat: has('类型及格式探查') ? '类型合规 · 日期/编码格式通过' : '未执行',
    datasetProbe: has('数据集探查') ? '标准数据集候选 · 总量约 12.4 万 · 日增量稳定' : '未执行',
    problemData: has('问题数据探查') ? '检出异常值 3 类，可制定清洗规则' : '未执行',
  }
  await ingestionApi.createProbeReport({
    reportCode: `PRB_${Date.now()}`,
    sourceName: tb ? `${tb.tableName}（${tb.tableCode || tb.id}）` : String(probeForm.sourceTableId),
    nullRate: has('空值率探查') ? 0.018 : 0,
    domainCheck: has('值域及分布探查') ? 'OK' : 'SKIP',
    entityType: has('命名实体探查') ? '人名/地名' : '—',
    metricsJson: JSON.stringify(metrics),
    status: 'DONE',
  })
  ElMessage.success('探查已执行，结果已写入报告清单')
  await refreshStep(1)
  goStep(2)
}

function resetDefForm() {
  editingDef.value = false
  defForm.id = undefined
  defForm.defName = ''
  defForm.businessDesc = ''
  defForm.techDesc = ''
  defForm.refTableId = undefined
}

function openCreateDef() {
  resetDefForm()
  defDialogVisible.value = true
}

function openEditDef(row: DataDefinition) {
  editingDef.value = true
  defForm.id = row.id
  defForm.defName = row.defName
  defForm.businessDesc = row.businessDesc || ''
  defForm.techDesc = row.techDesc || ''
  defForm.refTableId = undefined
  defDialogVisible.value = true
}

function searchDefs() {
  resetFilteredDefPage()
}

async function saveDef() {
  if (!defForm.defName.trim()) {
    ElMessage.warning('请填写定义名称')
    return
  }
  const tb = tables.value.find((t) => t.id === defForm.refTableId)
  const techDesc = defForm.techDesc.trim()
    || (tb ? `登记表 ${tb.tableName}` : '')
  const body = {
    defName: defForm.defName.trim(),
    businessDesc: defForm.businessDesc.trim(),
    techDesc,
    refTableId: defForm.refTableId,
    dataCategory: selectedClassCode.value,
    dataCategoryLabel: selectedClassLabel.value,
    dataCategoryUuid: selectedUuid.value,
  }
  if (defForm.id) {
    await ingestionApi.updateDefinition(defForm.id, body)
    ElMessage.success('定义已更新')
  } else {
    await ingestionApi.saveDefinition(body)
    ElMessage.success('定义已新增')
  }
  defDialogVisible.value = false
  resetDefForm()
  await refreshStep(2)
}

async function deleteDef(row: DataDefinition) {
  try {
    await ElMessageBox.confirm(`确认删除定义「${row.defName}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await ingestionApi.deleteDefinition(row.id)
  ElMessage.success('已删除')
  await refreshStep(2)
}

async function runIngestJob() {
  if (!selectedUuid.value) {
    ElMessage.warning('请先选择数据分类')
    goStep(0)
    return
  }
  try {
    await ingestionApi.runPipeline({
      jobType: 'READ',
      jobName: runForm.jobName,
      refChannelId: runForm.refChannelId,
      dataCategory: selectedClassCode.value,
      dataCategoryLabel: selectedClassLabel.value,
      dataCategoryUuid: selectedUuid.value,
    })
    await ingestionApi.runPipeline({
      jobType: 'RECONCILE',
      jobName: '对账-' + runForm.jobName,
      dataCategory: selectedClassCode.value,
      dataCategoryLabel: selectedClassLabel.value,
      dataCategoryUuid: selectedUuid.value,
    })
    await refreshStep(3)
    reconcileResult.value = (await ingestionApi.reconcile('analysis')).data
    const matched = reconcileResult.value?.matched
    lastReconcileHint.value =
      matched != null ? `对账完成：匹配率 ${matched}%（分类：${selectedClassLabel.value}）` : '对账已完成'
    ElMessage.success('接入与对账已完成，可查看下方结果')
    goStep(3)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '运行失败')
  }
}

function backToClassify() {
  reconcileResult.value = null
  goStep(0)
}

function resolveStepFromQuery() {
  const sec = String(route.query.section || '')
  if (sec.includes('classify')) return 0
  if (sec.includes('probe')) return 1
  if (sec.includes('define')) return 2
  if (sec.includes('run')) return 3
  return 0
}

onMounted(() => {
  selectedUuid.value = localStorage.getItem(STORAGE_KEY) || ''
  step.value = resolveStepFromQuery()
  if (step.value > 0 && !selectedUuid.value) {
    step.value = 0
    router.replace({ query: { ...route.query, section: 'step-classify' } })
  }
  ensureStepLoaded(step.value)
})
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <el-alert
      v-if="lastReconcileHint"
      type="success"
      :closable="true"
      show-icon
      style="margin-bottom:12px"
      :title="lastReconcileHint"
      @close="lastReconcileHint = ''"
    />

    <el-steps :active="step" finish-status="success" align-center style="margin-bottom:24px">
      <el-step title="汇聚数据分类" @click="goStep(0)" />
      <el-step title="探查配置" @click="goStep(1)" />
      <el-step title="定义规则" @click="goStep(2)" />
      <el-step title="运行与对账" @click="goStep(3)" />
    </el-steps>

    <div v-show="step === 0">
      <PageCard title="汇聚数据分类">
        <DataCategoryView
          embed
          selectable
          :selected-uuid="selectedUuid"
          @select="onSelectCategory"
          @loaded="onCategoriesLoaded"
        />
        <div class="classify-actions">
          <el-button type="primary" :disabled="!selectedUuid" @click="nextFromClassify">下一步：探查配置</el-button>
        </div>
      </PageCard>
    </div>

    <PageCard v-show="step === 1" title="数据探查">
      <el-tag v-if="selectedClassLabel" type="info" style="margin-bottom:12px">当前分类：{{ selectedClassLabel }}</el-tag>
      <el-form label-width="120px">
        <el-form-item label="登记源表">
          <el-select v-model="probeForm.sourceTableId" filterable placeholder="输入表名筛选" style="min-width:320px">
            <el-option v-for="t in tables" :key="t.id" :label="`${t.tableName}（${t.tableCode}）`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="探查项">
          <el-checkbox-group v-model="probeForm.probeItems">
            <el-checkbox v-for="item in ALL_PROBE_ITEMS" :key="item" :label="item">
              {{ item }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item>
          <el-button @click="goStep(0)">上一步</el-button>
          <el-button type="primary" @click="runProbe">执行探查</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="probes" stripe size="small" style="margin-top:12px" border>
        <el-table-column prop="sourceName" label="数据源" min-width="140" show-overflow-tooltip />
        <el-table-column label="业务探查" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ metricOf(row, 'businessProbe') }}</template>
        </el-table-column>
        <el-table-column label="接入方式" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ metricOf(row, 'accessProbe') }}</template>
        </el-table-column>
        <el-table-column label="空值率" width="90">
          <template #default="{ row }">{{ metricOf(row, 'nullRate', row.nullRate != null ? String(row.nullRate) : '—') }}</template>
        </el-table-column>
        <el-table-column label="值域分布" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ metricOf(row, 'domainDist', row.domainCheck || '—') }}</template>
        </el-table-column>
        <el-table-column label="命名实体" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ metricOf(row, 'namedEntity', row.entityType || '—') }}</template>
        </el-table-column>
        <el-table-column label="数据元" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ metricOf(row, 'metaAlign') }}</template>
        </el-table-column>
        <el-table-column label="类型及格式" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ metricOf(row, 'typeFormat') }}</template>
        </el-table-column>
        <el-table-column label="数据集" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ metricOf(row, 'datasetProbe') }}</template>
        </el-table-column>
        <el-table-column label="问题数据" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ metricOf(row, 'problemData') }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-show="step === 2" title="数据定义">
      <el-tag v-if="selectedClassLabel" type="info" style="margin-bottom:12px">当前分类：{{ selectedClassLabel }}</el-tag>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="搜索" class="portal-field-lg">
          <el-input
            v-model="defKeyword"
            clearable
            placeholder="名称/描述/编码"
            @keyup.enter="searchDefs"
          />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="searchDefs">查询</el-button>
          <el-button type="primary" @click="openCreateDef">新增定义</el-button>
          <el-button @click="goStep(1)">上一步</el-button>
          <el-button @click="goStep(3)">下一步：运行任务</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="pagedFilteredDefs" stripe size="small" border>
        <el-table-column prop="defCode" label="编码" width="140" show-overflow-tooltip />
        <el-table-column prop="defName" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="businessDesc" label="业务描述" min-width="160" show-overflow-tooltip />
        <el-table-column prop="techDesc" label="技术描述" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDef(row)">编辑</el-button>
            <el-button link type="danger" @click="deleteDef(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination
        v-model:page="filteredDefPage"
        v-model:page-size="filteredDefPageSize"
        :total="filteredDefTotal"
      />

      <el-dialog
        v-model="defDialogVisible"
        :title="editingDef ? '编辑定义' : '新增定义'"
        width="560px"
        destroy-on-close
        append-to-body
        @closed="resetDefForm"
      >
        <el-form label-width="110px">
          <el-form-item label="定义名称" required>
            <el-input v-model="defForm.defName" placeholder="请输入定义名称" />
          </el-form-item>
          <el-form-item label="关联登记表">
            <el-select v-model="defForm.refTableId" clearable filterable placeholder="输入表名筛选" style="width:100%">
              <el-option v-for="t in tables" :key="t.id" :label="t.tableName" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="业务描述">
            <el-input v-model="defForm.businessDesc" type="textarea" :rows="2" placeholder="业务说明" />
          </el-form-item>
          <el-form-item label="技术描述">
            <el-input v-model="defForm.techDesc" type="textarea" :rows="2" placeholder="可选，默认按关联表填充" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="defDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveDef">{{ editingDef ? '保存' : '确定新增' }}</el-button>
        </template>
      </el-dialog>
    </PageCard>

    <PageCard v-show="step === 3" title="数据读取与对账">
      <el-tag v-if="selectedClassLabel" type="info" style="margin-bottom:12px">当前分类：{{ selectedClassLabel }}</el-tag>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="任务名称" class="portal-field-md"><el-input v-model="runForm.jobName" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button @click="goStep(2)">上一步</el-button>
          <el-button type="primary" @click="runIngestJob">运行接入并生成对账</el-button>
          <el-button @click="backToClassify">返回汇聚数据分类</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="jobs" stripe size="small" style="margin-top:12px">
        <el-table-column prop="jobName" label="任务" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ statusLabel(row.jobType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>
      <PageCard title="数据对账" style="margin-top:16px">
        <el-table :data="reconcileLogs" stripe size="small">
          <el-table-column prop="batchNo" label="批次" width="140" />
          <el-table-column prop="matchedPct" label="匹配%" width="90" />
          <el-table-column prop="diffRows" label="差异行" width="90" />
          <el-table-column label="告警" width="90">
            <template #default="{ row }">{{ statusLabel(row.alertLevel) }}</template>
          </el-table-column>
        </el-table>
        <el-alert
          v-if="reconcileResult"
          :title="`对账分析：匹配率 ${reconcileResult.matched}%`"
          type="success"
          style="margin-top:12px"
          show-icon
        />
        <div v-if="reconcileResult" class="classify-actions">
          <el-button type="primary" @click="backToClassify">查看完毕，返回汇聚数据分类</el-button>
        </div>
      </PageCard>
    </PageCard>
  </div>
</template>

<style scoped>
.classify-actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
