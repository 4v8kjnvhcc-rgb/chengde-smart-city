<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api/http'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface StepItem { no: number; name: string; status: string }
interface EligibleTable {
  tableId: number
  sourceName: string
  tableName: string
  physicalTableName: string
  physicalRows: number
  entryCode?: string
  columns?: Array<{ name: string; typeName: string }>
}
interface Overview {
  sample: Record<string, unknown>
  metadata: Record<string, unknown>
  quality: Record<string, unknown>
  catalog: Record<string, unknown>
  subscription: Record<string, unknown>
  steps: StepItem[]
}

const eligible = ref<EligibleTable[]>([])
const tableId = ref<number | undefined>()
const overview = ref<Overview | null>(null)
const loading = ref(false)
const runningStep = ref(0)
const qualityColumn = ref('')
const qualityCheckType = ref('NULL_CHECK')

const steps = computed(() => overview.value?.steps || [])
const activeStep = computed(() => {
  const firstPending = steps.value.findIndex((item) => !isDone(item.status))
  return firstPending < 0 ? 5 : firstPending
})
const sampleColumns = computed(() => {
  const cols = overview.value?.sample?.columns as Array<{ name: string }> | undefined
  return cols || []
})

function isDone(status: unknown) {
  return ['SUCCESS', 'PUBLISHED', 'ACTIVE', 'APPROVED', 'DISTRIBUTED'].includes(
    String(status || '').toUpperCase(),
  )
}

async function loadEligible() {
  eligible.value = (await api.get('/governance/direct-share/eligible-tables')).data || []
  if (!tableId.value && eligible.value.length) {
    const enterprise = eligible.value.find((t) => t.physicalTableName === 'ods_enterprise_base')
    tableId.value = enterprise?.tableId || eligible.value[0].tableId
  }
}

async function loadOverview() {
  if (!tableId.value) return
  loading.value = true
  try {
    overview.value = (await api.get('/governance/direct-share/overview', {
      params: { tableId: tableId.value },
    })).data
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载黄金路径失败')
  } finally {
    loading.value = false
  }
}

async function runStep(step: number) {
  if (!tableId.value) return
  runningStep.value = step
  try {
    const payload: Record<string, unknown> = { tableId: tableId.value }
    if (step === 2) {
      await api.post('/governance/direct-share/metadata/collect', payload)
      ElMessage.success('元数据已入账')
    } else if (step === 3) {
      const rules = qualityColumn.value
        ? [{ column: qualityColumn.value, checkType: qualityCheckType.value }]
        : []
      await api.post('/governance/direct-share/quality/run', { ...payload, rules })
      ElMessage.success('质量规则已执行')
    } else if (step === 4) {
      await api.post('/governance/direct-share/catalog/publish', payload)
      ElMessage.success('目录审批完成并发布')
    } else if (step === 5) {
      await api.post('/governance/direct-share/subscription/authorize', {
        ...payload,
        shareMode: 'DB_SYNC',
        purpose: '直通共享多表验证',
      })
      ElMessage.success('订阅已审批，本地授权记录已生成')
    }
    await loadOverview()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '步骤执行失败')
  } finally {
    runningStep.value = 0
  }
}

watch(tableId, () => {
  qualityColumn.value = ''
  loadOverview()
})
onMounted(async () => {
  await loadEligible()
  await loadOverview()
})
</script>

<template>
  <div v-loading="loading">
    <PageCard title="直通共享黄金路径（多表）">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="汇聚表" class="portal-field-xl">
          <el-select v-model="tableId" filterable placeholder="输入表名筛选">
            <el-option
              v-for="t in eligible"
              :key="t.tableId"
              :label="`${t.tableName}（${t.physicalTableName}）`"
              :value="t.tableId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-steps :active="activeStep" finish-status="success" align-center>
        <el-step v-for="item in steps" :key="item.no" :title="item.name" :description="statusLabel(item.status)" />
      </el-steps>
    </PageCard>

    <div v-if="overview" class="golden-path-grid">
      <PageCard title="1. 已登记、已汇聚表">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="数据源">{{ overview.sample.sourceName }}</el-descriptions-item>
          <el-descriptions-item label="物理表">{{ overview.sample.physicalTableName }}</el-descriptions-item>
          <el-descriptions-item label="汇聚状态">
            <el-tag :type="statusTagType(overview.sample.ingestTaskStatus)">
              {{ statusLabel(overview.sample.ingestTaskStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="落地行数">{{ overview.sample.physicalRows }}</el-descriptions-item>
          <el-descriptions-item label="条目编码">{{ overview.sample.entryCode }}</el-descriptions-item>
        </el-descriptions>
      </PageCard>

      <PageCard title="2. 元数据入账">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(overview.metadata.status)">{{ statusLabel(overview.metadata.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="条目编码">{{ overview.metadata.entryCode || '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-button type="primary" :loading="runningStep === 2" @click="runStep(2)">
          {{ isDone(overview.metadata.status) ? '重新采集元数据' : '采集并入账' }}
        </el-button>
      </PageCard>

      <PageCard title="3. 质量挂表">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="字段" class="portal-field-md">
            <el-select v-model="qualityColumn" clearable placeholder="可选">
              <el-option v-for="c in sampleColumns" :key="c.name" :label="c.name" :value="c.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="检查" class="portal-field-sm">
            <el-select v-model="qualityCheckType">
              <el-option label="非空" value="NULL_CHECK" />
              <el-option label="唯一" value="UNIQUENESS" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(overview.quality.status)">{{ statusLabel(overview.quality.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="评分">{{ overview.quality.score ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="问题行">{{ overview.quality.issueCount ?? '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-button
          type="primary"
          :loading="runningStep === 3"
          :disabled="!isDone(overview.metadata.status)"
          @click="runStep(3)"
        >
          执行质量稽核
        </el-button>
      </PageCard>

      <PageCard title="4. 编目审批并发布">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="发布状态">
            <el-tag :type="statusTagType(overview.catalog.publishStatus || overview.catalog.status)">
              {{ statusLabel(overview.catalog.publishStatus || overview.catalog.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="来源路径">直通共享</el-descriptions-item>
          <el-descriptions-item label="质量评分">{{ overview.catalog.qualityScore ?? '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-button
          type="primary"
          :loading="runningStep === 4"
          :disabled="overview.quality.score === undefined || overview.quality.score === null"
          @click="runStep(4)"
        >
          审批并发布
        </el-button>
      </PageCard>

      <PageCard title="5. 订阅分发 / 授权">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="授权编码">{{ overview.subscription.authorizationCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="授权状态">
            {{ statusLabel(overview.subscription.authorizationStatus) }}
          </el-descriptions-item>
        </el-descriptions>
        <el-button
          type="primary"
          :loading="runningStep === 5"
          :disabled="!isDone(overview.catalog.publishStatus)"
          @click="runStep(5)"
        >
          申请、审批并生成授权
        </el-button>
      </PageCard>
    </div>
  </div>
</template>

<style scoped>
.golden-path-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}
.golden-path-grid :deep(.el-descriptions) { margin-bottom: 12px; }
@media (max-width: 1100px) {
  .golden-path-grid { grid-template-columns: 1fr; }
}
</style>
