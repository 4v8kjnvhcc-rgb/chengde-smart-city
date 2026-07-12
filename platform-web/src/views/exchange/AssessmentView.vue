<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout from '@/components/common/HubSideLayout.vue'

const navItems = [
  { key: 'm027', label: 'M027 评价数据来源' },
  { key: 'm028', label: 'M028 评价周期' },
  { key: 'm029', label: 'M029 评价指标' },
  { key: 'm030', label: 'M030 评价执行' },
]

const route = useRoute()
const router = useRouter()
const tabMap: Record<string, string> = {
  m027: 'm027', m028: 'm028', m029: 'm029', m030: 'm030',
}

interface DataSource {
  id: number
  sourceCode: string
  sourceName: string
  sourceType: string
  recordCount: number
  lastSyncAt: string
}

interface Period {
  id: number
  periodCode: string
  periodName: string
  cycleType: string
  startDate: string
  endDate: string
  status: string
}

interface Indicator {
  id: number
  indicatorCode: string
  indicatorName: string
  indicatorType: string
  weight: number
  formulaDesc: string
}

interface Execution {
  id: number
  periodId: number
  targetType: string
  targetName: string
  status: string
  totalScore: number
  published: number
  executedAt: string
}

const tab = ref('m027')

function resolveTab() {
  const q = String(route.query.tab || 'm027').toLowerCase()
  tab.value = tabMap[q] || 'm027'
}

watch(tab, () => {
  router.replace({ query: { ...route.query, tab: tab.value } })
})
watch(() => route.query.tab, resolveTab)
const sources = ref<DataSource[]>([])
const periods = ref<Period[]>([])
const indicators = ref<Indicator[]>([])
const executions = ref<Execution[]>([])
const resultRows = ref<Record<string, unknown>[]>([])
const selectedExecId = ref<number>()

const periodForm = reactive({
  periodName: '',
  cycleType: 'QUARTER',
  startDate: '2026-07-01',
  endDate: '2026-09-30',
})

const indicatorForm = reactive({
  indicatorName: '',
  indicatorType: 'B',
  weight: 10,
  formulaDesc: '',
})

const runForm = reactive({
  periodId: undefined as number | undefined,
  targetType: 'DEPT',
  targetName: '数据管理局',
})

async function loadAll() {
  const [ds, ps, ind, ex] = await Promise.all([
    api.get('/exchange/assessment/data-sources'),
    api.get('/exchange/assessment/periods'),
    api.get('/exchange/assessment/indicators'),
    api.get('/exchange/assessment/executions'),
  ])
  sources.value = ds.data
  periods.value = ps.data
  indicators.value = ind.data
  executions.value = ex.data
  if (!runForm.periodId && periods.value.length) {
    runForm.periodId = periods.value.find((p) => p.status === 'ACTIVE')?.id ?? periods.value[0].id
  }
}

async function syncSources() {
  const res = await api.post('/exchange/assessment/data-sources/sync')
  sources.value = res.data
  ElMessage.success('评价数据来源已同步')
}

async function createPeriod() {
  if (!periodForm.periodName) {
    ElMessage.warning('请填写周期名称')
    return
  }
  await api.post('/exchange/assessment/periods', periodForm)
  ElMessage.success('评价周期已创建')
  periodForm.periodName = ''
  loadAll()
}

async function activatePeriod(id: number) {
  await api.post(`/exchange/assessment/periods/${id}/activate`)
  ElMessage.success('周期已启用')
  loadAll()
}

async function createIndicator() {
  if (!indicatorForm.indicatorName) {
    ElMessage.warning('请填写指标名称')
    return
  }
  await api.post('/exchange/assessment/indicators', indicatorForm)
  ElMessage.success('指标已创建')
  indicatorForm.indicatorName = ''
  loadAll()
}

async function runEval() {
  if (!runForm.periodId || !runForm.targetName) {
    ElMessage.warning('请选择周期并填写考核对象')
    return
  }
  const res = await api.post('/exchange/assessment/executions/run', runForm)
  ElMessage.success(`评价已执行 #${res.data}`)
  await loadAll()
  await showResults(res.data)
}

async function showResults(id: number) {
  selectedExecId.value = id
  const res = await api.get(`/exchange/assessment/executions/${id}/results`)
  resultRows.value = res.data
  tab.value = 'm030'
}

async function publishExec(id: number) {
  await api.post(`/exchange/assessment/executions/${id}/publish`)
  ElMessage.success('评价结果已发布')
  loadAll()
}

onMounted(() => { resolveTab(); loadAll() })
</script>

<template>
  <div>
    <PageHeader
      title="考核评估系统"
      description="M027 数据来源 · M028 评价周期 · M029 指标体系 · M030 评价执行与发布"
    />

    <HubSideLayout v-model="tab" :items="navItems">
      <template v-if="tab === 'm027'">
        <PageCard title="平台业务数据自动采集">
          <el-button type="primary" @click="syncSources">同步数据来源</el-button>
          <el-table class="portal-table" :data="sources" stripe style="margin-top: 12px">
            <el-table-column prop="sourceCode" label="编码" width="120" />
            <el-table-column prop="sourceName" label="来源" min-width="160" />
            <el-table-column prop="sourceType" label="类型" width="100" />
            <el-table-column prop="recordCount" label="记录数" width="100" />
            <el-table-column prop="lastSyncAt" label="最近同步" min-width="160" />
          </el-table>
        </PageCard>
      </template>

      <template v-if="tab === 'm028'">
        <PageCard title="评价周期管理">
          <el-form inline>
            <el-form-item label="名称">
              <el-input v-model="periodForm.periodName" placeholder="如 2026年第三季度" />
            </el-form-item>
            <el-form-item label="周期">
              <el-select v-model="periodForm.cycleType" style="width: 120px">
                <el-option label="周" value="WEEK" />
                <el-option label="月" value="MONTH" />
                <el-option label="季" value="QUARTER" />
                <el-option label="半年" value="HALF_YEAR" />
                <el-option label="年" value="YEAR" />
              </el-select>
            </el-form-item>
            <el-form-item label="起止">
              <el-date-picker v-model="periodForm.startDate" type="date" value-format="YYYY-MM-DD" />
              <span style="margin: 0 6px">—</span>
              <el-date-picker v-model="periodForm.endDate" type="date" value-format="YYYY-MM-DD" />
            </el-form-item>
            <el-button type="primary" @click="createPeriod">新建周期</el-button>
          </el-form>
          <el-table class="portal-table" :data="periods" stripe>
            <el-table-column prop="periodCode" label="编码" width="120" />
            <el-table-column prop="periodName" label="名称" min-width="160" />
            <el-table-column prop="cycleType" label="类型" width="90" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'ACTIVE'" link type="primary" @click="activatePeriod(row.id)">
                  启用
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </template>

      <template v-if="tab === 'm029'">
        <PageCard title="指标体系（A 类自动 / B 类人工）">
          <el-form inline>
            <el-form-item label="指标名">
              <el-input v-model="indicatorForm.indicatorName" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="indicatorForm.indicatorType" style="width: 90px">
                <el-option label="A自动" value="A" />
                <el-option label="B人工" value="B" />
              </el-select>
            </el-form-item>
            <el-form-item label="权重%">
              <el-input-number v-model="indicatorForm.weight" :min="1" :max="100" />
            </el-form-item>
            <el-button type="primary" @click="createIndicator">新增指标</el-button>
          </el-form>
          <el-table class="portal-table" :data="indicators" stripe>
            <el-table-column prop="indicatorCode" label="编码" width="140" />
            <el-table-column prop="indicatorName" label="名称" min-width="140" />
            <el-table-column prop="indicatorType" label="类型" width="72" />
            <el-table-column prop="weight" label="权重%" width="80" />
            <el-table-column prop="formulaDesc" label="说明" min-width="200" />
          </el-table>
        </PageCard>
      </template>

      <template v-if="tab === 'm030'">
        <PageCard title="评价执行与结果发布">
          <el-form inline>
            <el-form-item label="周期">
              <el-select v-model="runForm.periodId" style="width: 200px">
                <el-option v-for="p in periods" :key="p.id" :label="p.periodName" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="对象类型">
              <el-select v-model="runForm.targetType" style="width: 120px">
                <el-option label="区域" value="REGION" />
                <el-option label="部门" value="DEPT" />
                <el-option label="岗位" value="POSITION" />
                <el-option label="业务系统" value="SYSTEM" />
              </el-select>
            </el-form-item>
            <el-form-item label="对象名称">
              <el-input v-model="runForm.targetName" />
            </el-form-item>
            <el-button type="primary" @click="runEval">执行评价</el-button>
          </el-form>
          <el-table class="portal-table" :data="executions" stripe>
            <el-table-column prop="targetName" label="考核对象" min-width="140" />
            <el-table-column prop="targetType" label="类型" width="90" />
            <el-table-column prop="totalScore" label="总分" width="80" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button link type="primary" @click="showResults(row.id)">明细</el-button>
                <el-button v-if="!row.published" link type="success" @click="publishExec(row.id)">发布</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="resultRows.length" style="margin-top: 16px">
            <h4>评分明细（执行 #{{ selectedExecId }}）</h4>
            <el-table class="portal-table" :data="resultRows" stripe size="small">
              <el-table-column prop="indicatorName" label="指标" min-width="140" />
              <el-table-column prop="indicatorType" label="类型" width="72" />
              <el-table-column prop="score" label="得分" width="80" />
              <el-table-column prop="rawValue" label="原始值" width="80" />
              <el-table-column prop="remark" label="备注" min-width="120" />
            </el-table>
          </div>
        </PageCard>
      </template>
    </HubSideLayout>
  </div>
</template>
