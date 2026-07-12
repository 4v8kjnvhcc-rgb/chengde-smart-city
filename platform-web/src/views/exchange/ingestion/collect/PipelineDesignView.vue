<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type DataDefinition, type PipelineJob, type ProbeReport, type ReconcileLog } from '../useIngestionHub'

const props = defineProps<{ module: string }>()
const { loading, loadError, withLoad } = useIngestionLoading()
const jobs = ref<PipelineJob[]>([])
const probes = ref<ProbeReport[]>([])
const definitions = ref<DataDefinition[]>([])
const reconcileLogs = ref<ReconcileLog[]>([])
const reconcileResult = ref<Record<string, unknown> | null>(null)
const defForm = reactive({ defName: '', businessDesc: '', techDesc: '' })

const title = computed(() => ({
  m061: '数据探查', m062: '数据定义', m063: '数据读取', m064: '数据对账',
}[props.module] || '规范设计工作台'))

async function reload() {
  await withLoad(async () => {
    jobs.value = (await ingestionApi.pipelineJobs()).data
    probes.value = (await ingestionApi.probeReports()).data
    definitions.value = (await ingestionApi.definitions()).data
    reconcileLogs.value = (await ingestionApi.reconcileLogs()).data
  })
}

async function runPipeline(jobType: string) {
  await ingestionApi.runPipeline({ jobType, jobName: `${jobType} demo` })
  await reload()
}

async function saveDef() {
  if (!defForm.defName) return
  await ingestionApi.saveDefinition({ ...defForm })
  defForm.defName = ''
  await reload()
}

async function callReconcile(action: string) {
  reconcileResult.value = (await ingestionApi.reconcile(action)).data
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard :title="title">
      <el-space v-if="module === 'm061'" wrap style="margin-bottom:12px">
        <el-button type="primary" @click="runPipeline('PROBE')">执行探查</el-button>
      </el-space>
      <el-table v-if="module === 'm061'" :data="probes" stripe>
        <el-table-column prop="sourceName" label="数据源" />
        <el-table-column prop="nullRate" label="空值率" width="90" />
        <el-table-column prop="domainCheck" label="值域" width="100" />
        <el-table-column prop="entityType" label="实体" width="100" />
        <el-table-column prop="status" label="状态" width="90" />
      </el-table>

      <template v-if="module === 'm062'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="定义名称" class="portal-field-md"><el-input v-model="defForm.defName" /></el-form-item>
          <el-form-item label="业务描述" class="portal-field-lg"><el-input v-model="defForm.businessDesc" /></el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="saveDef">保存 8 项定义</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="definitions" stripe>
          <el-table-column prop="defCode" label="编码" width="140" />
          <el-table-column prop="defName" label="名称" width="140" />
          <el-table-column prop="businessDesc" label="业务描述" min-width="160" />
          <el-table-column prop="techDesc" label="技术描述" min-width="160" />
          <el-table-column prop="status" label="状态" width="90" />
        </el-table>
      </template>

      <template v-if="module === 'm063'">
        <el-button @click="runPipeline('READ')">生成账单</el-button>
        <el-table :data="jobs.filter(j => j.jobType === 'READ')" stripe style="margin-top:12px">
          <el-table-column prop="jobName" label="读取任务" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="billAmount" label="账单(元)" width="100" />
        </el-table>
      </template>

      <template v-if="module === 'm064'">
        <el-space wrap style="margin-bottom:12px">
          <el-button @click="runPipeline('RECONCILE')">执行对账</el-button>
          <el-button @click="callReconcile('analysis')">对账分析</el-button>
          <el-button @click="callReconcile('alert')">告警</el-button>
          <el-button @click="callReconcile('logs')">日志</el-button>
          <el-button @click="callReconcile('anomaly')">异常</el-button>
        </el-space>
        <el-table :data="reconcileLogs" stripe>
          <el-table-column prop="batchNo" label="批次" width="140" />
          <el-table-column prop="matchedPct" label="匹配%" width="90" />
          <el-table-column prop="diffRows" label="差异行" width="90" />
          <el-table-column prop="alertLevel" label="告警" width="90" />
          <el-table-column prop="status" label="状态" width="90" />
        </el-table>
        <el-alert v-if="reconcileResult" :title="JSON.stringify(reconcileResult)" type="info" style="margin-top:12px" />
      </template>
    </PageCard>
  </div>
</template>
