<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

const loading = ref(false)
const tab = ref('data-source')
const sources = ref<Record<string, unknown>[]>([])
const periods = ref<Record<string, unknown>[]>([])
const indicators = ref<Record<string, unknown>[]>([])
const executions = ref<Record<string, unknown>[]>([])
const resultRows = ref<Record<string, unknown>[]>([])
const selectedExecId = ref<number>()

const periodForm = reactive({
  periodName: '',
  cycleType: 'QUARTER',
  startDate: '2026-07-01',
  endDate: '2026-09-30',
})
const indicatorForm = reactive({ indicatorName: '', indicatorType: 'B', weight: 10, formulaDesc: '' })
const runForm = reactive({ periodId: undefined as number | undefined, targetType: 'DEPT', targetName: '数据管理局' })

async function load() {
  loading.value = true
  try {
    if (tab.value === 'data-source') {
      sources.value = (await api.get('/exchange/assessment/data-sources')).data
    } else if (tab.value === 'period') {
      periods.value = (await api.get('/exchange/assessment/periods')).data
    } else if (tab.value === 'indicator') {
      indicators.value = (await api.get('/exchange/assessment/indicators')).data
    } else if (tab.value === 'execution') {
      const [ex, ps] = await Promise.all([
        api.get('/exchange/assessment/executions'),
        periods.value.length ? Promise.resolve({ data: periods.value }) : api.get('/exchange/assessment/periods'),
      ])
      executions.value = ex.data
      if (!periods.value.length) periods.value = ps.data
      if (!runForm.periodId && periods.value.length) {
        const active = periods.value.find((p) => p.status === 'ACTIVE')
        runForm.periodId = Number((active || periods.value[0]).id)
      }
    }
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function syncSources() {
  sources.value = (await api.post('/exchange/assessment/data-sources/sync')).data
  ElMessage.success('评价数据来源已同步')
}

async function createPeriod() {
  if (!periodForm.periodName) return ElMessage.warning('请填写周期名称')
  await api.post('/exchange/assessment/periods', periodForm)
  periodForm.periodName = ''
  await load()
}

async function activatePeriod(id: number) {
  await api.post(`/exchange/assessment/periods/${id}/activate`)
  await load()
}

async function createIndicator() {
  if (!indicatorForm.indicatorName) return ElMessage.warning('请填写指标名称')
  await api.post('/exchange/assessment/indicators', indicatorForm)
  indicatorForm.indicatorName = ''
  await load()
}

async function runEval() {
  if (!runForm.periodId || !runForm.targetName) return ElMessage.warning('请选择周期并填写考核对象')
  const res = await api.post('/exchange/assessment/executions/run', runForm)
  await load()
  await showResults(Number(res.data))
}

async function showResults(id: number) {
  selectedExecId.value = id
  resultRows.value = (await api.get(`/exchange/assessment/executions/${id}/results`)).data
}

async function publishExec(id: number) {
  await api.post(`/exchange/assessment/executions/${id}/publish`)
  await load()
}

watch(tab, load)
onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <PageHeader
      title="考核评估配置"
      description="系统管理 · 数据共享交换平台 · 应用平台 — 数据来源、周期、指标与评价执行发布"
    />
    <el-radio-group v-model="tab" class="mb">
      <el-radio-button value="data-source">评价数据来源</el-radio-button>
      <el-radio-button value="period">评价周期</el-radio-button>
      <el-radio-button value="indicator">评价指标</el-radio-button>
      <el-radio-button value="execution">执行与发布</el-radio-button>
    </el-radio-group>

    <PageCard v-if="tab === 'data-source'" title="评价数据来源">
      <el-button type="primary" @click="syncSources">同步数据来源</el-button>
      <el-table class="portal-table" :data="sources" stripe size="small" style="margin-top:12px">
        <el-table-column prop="sourceCode" label="编码" width="120" />
        <el-table-column prop="sourceName" label="来源" min-width="160" />
        <el-table-column prop="sourceType" label="类型" width="100" />
        <el-table-column prop="recordCount" label="记录数" width="100" />
        <el-table-column prop="lastSyncAt" label="最近同步" min-width="160" />
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'period'" title="评价周期管理">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="名称" class="portal-field-md"><el-input v-model="periodForm.periodName" /></el-form-item>
        <el-form-item label="周期" class="portal-field-sm">
          <el-select v-model="periodForm.cycleType">
            <el-option label="周" value="WEEK" />
            <el-option label="月" value="MONTH" />
            <el-option label="季" value="QUARTER" />
            <el-option label="半年" value="HALF_YEAR" />
            <el-option label="年" value="YEAR" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions"><el-button type="primary" @click="createPeriod">新建周期</el-button></el-form-item>
      </el-form>
      <el-table class="portal-table" :data="periods" stripe size="small">
        <el-table-column prop="periodCode" label="编码" width="120" />
        <el-table-column prop="periodName" label="名称" min-width="160" />
        <el-table-column prop="cycleType" label="类型" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'ACTIVE'" link type="primary" @click="activatePeriod(Number(row.id))">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'indicator'" title="评价指标管理">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="指标名" class="portal-field-md"><el-input v-model="indicatorForm.indicatorName" /></el-form-item>
        <el-form-item label="类型" class="portal-field-sm">
          <el-select v-model="indicatorForm.indicatorType">
            <el-option label="A自动" value="A" />
            <el-option label="B人工" value="B" />
          </el-select>
        </el-form-item>
        <el-form-item label="权重%" class="portal-field-sm">
          <el-input-number v-model="indicatorForm.weight" :min="1" :max="100" />
        </el-form-item>
        <el-form-item class="portal-form-actions"><el-button type="primary" @click="createIndicator">新增指标</el-button></el-form-item>
      </el-form>
      <el-table class="portal-table" :data="indicators" stripe size="small">
        <el-table-column prop="indicatorCode" label="编码" width="140" />
        <el-table-column prop="indicatorName" label="名称" min-width="140" />
        <el-table-column prop="indicatorType" label="类型" width="72" />
        <el-table-column prop="weight" label="权重%" width="80" />
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'execution'" title="评价执行与发布">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="周期" class="portal-field-default">
          <el-select v-model="runForm.periodId">
            <el-option v-for="p in periods" :key="String(p.id)" :label="String(p.periodName)" :value="Number(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象类型" class="portal-field-sm">
          <el-select v-model="runForm.targetType">
            <el-option label="区域" value="REGION" />
            <el-option label="部门" value="DEPT" />
            <el-option label="岗位" value="POSITION" />
            <el-option label="业务系统" value="SYSTEM" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象名称" class="portal-field-md"><el-input v-model="runForm.targetName" /></el-form-item>
        <el-form-item class="portal-form-actions"><el-button type="primary" @click="runEval">执行评价</el-button></el-form-item>
      </el-form>
      <el-table class="portal-table" :data="executions" stripe size="small">
        <el-table-column prop="targetName" label="考核对象" min-width="140" />
        <el-table-column prop="targetType" label="类型" width="90" />
        <el-table-column prop="totalScore" label="总分" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="showResults(Number(row.id))">明细</el-button>
            <el-button v-if="!row.published" link type="success" @click="publishExec(Number(row.id))">发布</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="resultRows.length" style="margin-top:16px">
        <h4>评分明细（执行 #{{ selectedExecId }}）</h4>
        <el-table class="portal-table" :data="resultRows" stripe size="small">
          <el-table-column prop="indicatorName" label="指标" min-width="140" />
          <el-table-column prop="score" label="得分" width="80" />
          <el-table-column prop="remark" label="备注" min-width="120" />
        </el-table>
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.mb { margin-bottom: 12px; }
</style>
