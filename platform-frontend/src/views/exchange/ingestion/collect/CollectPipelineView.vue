<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import DataCategoryView, { type CategoryRow } from '@/views/resource/DataCategoryView.vue'
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
  probeItems: ['空值率', '值域分布', '命名实体'],
})
const defForm = reactive({
  defName: '',
  businessDesc: '',
  techDesc: '',
  refTableId: undefined as number | undefined,
})
const runForm = reactive({ refChannelId: undefined as number | undefined, jobName: '规范设计接入任务' })

const selectedClassLabel = computed(() => selectedRow.value?.categoryName || '')
const selectedClassCode = computed(() => selectedRow.value?.categoryCode || selectedRow.value?.categoryType || '')

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
        probes.value = (await ingestionApi.probeReports()).data
      })(),
    ])
  } else if (n === 2) {
    await Promise.all([
      loadTables(force),
      (async () => {
        if (!force && definitions.value.length) return
        definitions.value = (await ingestionApi.definitions()).data
      })(),
    ])
  } else {
    await Promise.all([
      (async () => {
        if (!force && jobs.value.length) return
        jobs.value = (await ingestionApi.pipelineJobs()).data
      })(),
      (async () => {
        if (!force && reconcileLogs.value.length) return
        reconcileLogs.value = (await ingestionApi.reconcileLogs()).data
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
  await loadStepData(n, true)
}

async function runProbe() {
  if (!selectedUuid.value) {
    ElMessage.warning('请先选择数据分类')
    goStep(0)
    return
  }
  if (!probeForm.sourceTableId) return
  const tb = tables.value.find((t) => t.id === probeForm.sourceTableId)
  await ingestionApi.runPipeline({
    jobType: 'PROBE',
    jobName: `探查-${tb?.tableName || probeForm.sourceTableId}`,
    refSourceTableId: probeForm.sourceTableId,
    probeItems: probeForm.probeItems,
    dataCategory: selectedClassCode.value,
    dataCategoryLabel: selectedClassLabel.value,
    dataCategoryUuid: selectedUuid.value,
  })
  await refreshStep(1)
  goStep(2)
}

async function saveDef() {
  if (!defForm.defName) return
  await ingestionApi.saveDefinition({
    ...defForm,
    dataCategory: selectedClassCode.value,
    dataCategoryLabel: selectedClassLabel.value,
    dataCategoryUuid: selectedUuid.value,
  })
  defForm.defName = ''
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
          v-model:selected-uuid="selectedUuid"
          @select="onSelectCategory"
          @loaded="onCategoriesLoaded"
        />
        <div class="classify-actions">
          <el-tag v-if="selectedClassLabel" type="success" style="margin-right:12px">
            已选用：{{ selectedClassLabel }}
          </el-tag>
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
            <el-checkbox label="空值率" />
            <el-checkbox label="值域分布" />
            <el-checkbox label="命名实体" />
            <el-checkbox label="数据元对标" />
            <el-checkbox label="问题数据" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item>
          <el-button @click="goStep(0)">上一步</el-button>
          <el-button type="primary" @click="runProbe">执行探查</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="probes" stripe size="small" style="margin-top:12px">
        <el-table-column prop="sourceName" label="数据源" />
        <el-table-column prop="nullRate" label="空值率" width="90" />
        <el-table-column prop="domainCheck" label="值域" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-show="step === 2" title="数据定义">
      <el-tag v-if="selectedClassLabel" type="info" style="margin-bottom:12px">当前分类：{{ selectedClassLabel }}</el-tag>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="定义名称" class="portal-field-md"><el-input v-model="defForm.defName" /></el-form-item>
        <el-form-item label="关联登记表" class="portal-field-default">
          <el-select v-model="defForm.refTableId" clearable filterable placeholder="输入表名筛选">
            <el-option v-for="t in tables" :key="t.id" :label="t.tableName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务描述" class="portal-field-lg"><el-input v-model="defForm.businessDesc" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button @click="goStep(1)">上一步</el-button>
          <el-button type="primary" @click="saveDef">保存定义</el-button>
          <el-button @click="goStep(3)">下一步：运行任务</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="definitions" stripe size="small">
        <el-table-column prop="defName" label="名称" />
        <el-table-column prop="businessDesc" label="业务描述" />
        <el-table-column prop="techDesc" label="技术描述" />
      </el-table>
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
          <template #default="{ row }">{{ $statusLabel(row.jobType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>
      <PageCard title="数据对账" style="margin-top:16px">
        <el-table :data="reconcileLogs" stripe size="small">
          <el-table-column prop="batchNo" label="批次" width="140" />
          <el-table-column prop="matchedPct" label="匹配%" width="90" />
          <el-table-column prop="diffRows" label="差异行" width="90" />
          <el-table-column label="告警" width="90">
            <template #default="{ row }">{{ $statusLabel(row.alertLevel) }}</template>
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
