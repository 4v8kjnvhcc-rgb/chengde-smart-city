<script setup lang="ts">

import { onMounted, reactive, ref } from 'vue'

import { useRoute, useRouter } from 'vue-router'

import PageCard from '@/components/common/PageCard.vue'

import { ingestionRegisterCache } from '../ingestion-register-cache'

import { ingestionApi, useIngestionLoading, type DataDefinition, type DataTable, type PipelineJob, type ProbeReport, type ReconcileLog } from '../useIngestionHub'



const route = useRoute()

const router = useRouter()

const { loading, loadError, withLoad } = useIngestionLoading()



const step = ref(0)

const tables = ref<DataTable[]>([])

const probes = ref<ProbeReport[]>([])

const definitions = ref<DataDefinition[]>([])

const jobs = ref<PipelineJob[]>([])

const reconcileLogs = ref<ReconcileLog[]>([])

const reconcileResult = ref<Record<string, unknown> | null>(null)



const loadedSteps = new Set<number>()



const probeForm = reactive({ sourceTableId: undefined as number | undefined, probeItems: ['空值率', '值域分布', '命名实体'] })

const defForm = reactive({ defName: '', businessDesc: '', techDesc: '', refTableId: undefined as number | undefined })

const runForm = reactive({ refChannelId: undefined as number | undefined, jobName: '规范设计接入任务' })



function goStep(n: number) {

  step.value = n

  router.replace({ query: { ...route.query, section: `step-${['probe', 'define', 'run'][n]}` } })

  ensureStepLoaded(n)

}



async function loadTables(force = false) {

  tables.value = await ingestionRegisterCache.tables(force)

  if (tables.value.length && !probeForm.sourceTableId) probeForm.sourceTableId = tables.value[0].id

}



async function loadStepData(n: number, force = false) {

  if (n === 0) {

    await Promise.all([

      loadTables(force),

      (async () => {

        if (!force && probes.value.length) return

        probes.value = (await ingestionApi.probeReports()).data

      })(),

    ])

  } else if (n === 1) {

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

  if (!probeForm.sourceTableId) return

  const tb = tables.value.find((t) => t.id === probeForm.sourceTableId)

  await ingestionApi.runPipeline({

    jobType: 'PROBE',

    jobName: `探查-${tb?.tableName || probeForm.sourceTableId}`,

    refSourceTableId: probeForm.sourceTableId,

    probeItems: probeForm.probeItems,

  })

  await refreshStep(0)

  goStep(1)

}



async function saveDef() {

  if (!defForm.defName) return

  await ingestionApi.saveDefinition({ ...defForm })

  defForm.defName = ''

  await refreshStep(1)

}



async function runIngestJob() {

  await ingestionApi.runPipeline({ jobType: 'READ', jobName: runForm.jobName, refChannelId: runForm.refChannelId })

  await ingestionApi.runPipeline({ jobType: 'RECONCILE', jobName: '对账-' + runForm.jobName })

  await refreshStep(2)

  reconcileResult.value = (await ingestionApi.reconcile('analysis')).data

  goStep(2)

}



onMounted(() => {

  const sec = String(route.query.section || '')

  if (sec.includes('define')) step.value = 1

  else if (sec.includes('run')) step.value = 2

  ensureStepLoaded(step.value)

})

</script>



<template>

  <div v-loading="loading">

    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />

    <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px"

      title="规范设计复用「数据资产登记」中的数据源、表模型与字段定义；探查针对已登记资产执行，无需重复录入基础元数据。" />



    <el-steps :active="step" finish-status="success" align-center style="margin-bottom:24px">

      <el-step title="探查配置" description="选择登记资产并配置探查项" />

      <el-step title="定义规则" description="基于探查结果定义数据规则" />

      <el-step title="运行与对账" description="执行接入任务并生成对账报告" />

    </el-steps>



    <PageCard v-show="step === 0" title="数据探查">

      <el-form label-width="120px">

        <el-form-item label="登记源表">

          <el-select v-model="probeForm.sourceTableId" filterable style="min-width:320px">

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



    <PageCard v-show="step === 1" title="数据定义">

      <el-form inline class="portal-inline-form portal-inline-form--block">

        <el-form-item label="定义名称" class="portal-field-md"><el-input v-model="defForm.defName" /></el-form-item>

        <el-form-item label="关联登记表" class="portal-field-default">

          <el-select v-model="defForm.refTableId" clearable>

            <el-option v-for="t in tables" :key="t.id" :label="t.tableName" :value="t.id" />

          </el-select>

        </el-form-item>

        <el-form-item label="业务描述" class="portal-field-lg"><el-input v-model="defForm.businessDesc" /></el-form-item>

        <el-form-item class="portal-form-actions">

          <el-button type="primary" @click="saveDef">保存定义</el-button>

          <el-button @click="goStep(2)">下一步：运行任务</el-button>

        </el-form-item>

      </el-form>

      <el-table :data="definitions" stripe size="small">

        <el-table-column prop="defName" label="名称" />

        <el-table-column prop="businessDesc" label="业务描述" />

        <el-table-column prop="techDesc" label="技术描述" />

      </el-table>

    </PageCard>



    <PageCard v-show="step === 2" title="数据读取与对账">

      <el-form inline class="portal-inline-form portal-inline-form--block">

        <el-form-item label="任务名称" class="portal-field-md"><el-input v-model="runForm.jobName" /></el-form-item>

        <el-form-item class="portal-form-actions">

          <el-button type="primary" @click="runIngestJob">运行接入并生成对账</el-button>

        </el-form-item>

      </el-form>

      <el-table :data="jobs" stripe size="small" style="margin-top:12px">

        <el-table-column prop="jobName" label="任务" />

        <el-table-column prop="jobType" label="类型" width="100" />

        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>

        <el-table-column prop="billAmount" label="账单(元)" width="100" />

      </el-table>

      <PageCard title="数据对账" style="margin-top:16px">

        <el-table :data="reconcileLogs" stripe size="small">

          <el-table-column prop="batchNo" label="批次" width="140" />

          <el-table-column prop="matchedPct" label="匹配%" width="90" />

          <el-table-column prop="diffRows" label="差异行" width="90" />

          <el-table-column prop="alertLevel" label="告警" width="90" />

        </el-table>

        <el-alert v-if="reconcileResult" :title="`对账分析：匹配率 ${reconcileResult.matched}%`" type="success" style="margin-top:12px" />

      </PageCard>

    </PageCard>

  </div>

</template>


