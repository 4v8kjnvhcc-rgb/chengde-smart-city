<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api/http'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface StepItem { no: number; name: string; status: string }
interface EligibleTable {
  tableId: number
  tableName: string
  physicalTableName: string
  physicalRows: number
}
interface Overview {
  source: Record<string, unknown>
  fusion: Record<string, unknown>
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
const previewSql = ref('')
const filterColumn = ref('')
const targetTable = ref('')
const targetTableOptions = ref<string[]>([])
const targetTablesLoading = ref(false)

const steps = computed(() => overview.value?.steps || [])
const activeStep = computed(() => {
  const firstPending = steps.value.findIndex((item) => !isDone(item.status))
  return firstPending < 0 ? 6 : firstPending
})
const sourceColumns = computed(() => {
  const cols = overview.value?.source?.columns as Array<{ name: string }> | undefined
  return cols || []
})

function isDone(status: unknown) {
  return ['SUCCESS', 'PUBLISHED', 'ACTIVE', 'APPROVED', 'DISTRIBUTED', 'READY'].includes(
    String(status || '').toUpperCase(),
  )
}

async function loadEligible() {
  eligible.value = (await api.get('/governance/processed-share/eligible-tables')).data || []
  if (!tableId.value && eligible.value.length) {
    const enterprise = eligible.value.find((t) => t.physicalTableName === 'ods_enterprise_base')
    tableId.value = enterprise?.tableId || eligible.value[0].tableId
  }
}

async function loadTargetTableOptions() {
  targetTablesLoading.value = true
  try {
    const rows = (await api.get('/governance/platform/metadata/collect/data-sources/-3/tables')).data || []
    targetTableOptions.value = (rows as Array<{ sourceTable?: string }>)
      .map((r) => String(r.sourceTable || '').trim())
      .filter(Boolean)
      .sort((a, b) => a.localeCompare(b))
  } catch {
    targetTableOptions.value = []
  } finally {
    targetTablesLoading.value = false
  }
}

async function loadOverview() {
  if (!tableId.value) return
  loading.value = true
  try {
    overview.value = (await api.get('/governance/processed-share/overview', {
      params: { tableId: tableId.value },
    })).data
    if (!targetTable.value && overview.value?.source?.producedTable) {
      targetTable.value = String(overview.value.source.producedTable)
    }
    if (!filterColumn.value && sourceColumns.value.length) {
      const nameCol = sourceColumns.value.find((c) => c.name.toLowerCase().includes('name'))
      filterColumn.value = nameCol?.name || sourceColumns.value[0].name
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载黄金路径失败')
  } finally {
    loading.value = false
  }
}

function buildFusionSpec() {
  const physical = String(overview.value?.source?.physicalTableName || '')
  // 让后端用默认模板；仅覆盖目标表与可选过滤
  const spec: Record<string, unknown> = {
    sourceTable: physical,
    targetTable: targetTable.value || undefined,
    writeMode: 'TRUNCATE_INSERT',
  }
  if (filterColumn.value) {
    spec.filterSql = `${filterColumn.value} IS NOT NULL`
  }
  // 非默认表时，若没有后端默认模板，提供通用 select
  if (physical && !['ods_enterprise_base', 'ods_project_base'].includes(physical)) {
    spec.select = sourceColumns.value
      .filter((c) => c.name.toLowerCase() !== 'id')
      .slice(0, 6)
      .map((c) => ({ expr: c.name, as: c.name }))
  }
  return spec
}

async function previewFusion() {
  if (!tableId.value) return
  try {
    const res = await api.post('/governance/processed-share/fusion/preview', {
      tableId: tableId.value,
      fusionSpec: buildFusionSpec(),
    })
    previewSql.value = String(res.data?.insertSql || '')
    ElMessage.success('加工预检通过')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '预检失败')
  }
}

async function runStep(step: number) {
  if (!tableId.value) return
  runningStep.value = step
  try {
    const payload: Record<string, unknown> = {
      tableId: tableId.value,
      producedTable: targetTable.value || undefined,
      fusionSpec: buildFusionSpec(),
    }
    if (step === 2) {
      await api.post('/governance/processed-share/fusion/run', payload)
      ElMessage.success('融合入库完成')
    } else if (step === 3) {
      await api.post('/governance/processed-share/metadata/collect', payload)
      ElMessage.success('产出元数据已入账')
    } else if (step === 4) {
      await api.post('/governance/processed-share/quality/run', payload)
      ElMessage.success('产出质量已执行')
    } else if (step === 5) {
      await api.post('/governance/processed-share/catalog/publish', payload)
      ElMessage.success('融合资源已发布')
    } else if (step === 6) {
      await api.post('/governance/processed-share/subscription/authorize', {
        ...payload,
        shareMode: 'DB_SYNC',
        purpose: '加工共享多表验证',
      })
      ElMessage.success('订阅已审批，本地授权已生成')
    }
    await loadOverview()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '步骤执行失败')
  } finally {
    runningStep.value = 0
  }
}

watch(tableId, () => {
  targetTable.value = ''
  filterColumn.value = ''
  previewSql.value = ''
  loadOverview()
})
onMounted(async () => {
  await Promise.all([loadEligible(), loadTargetTableOptions()])
  await loadOverview()
})
</script>

<template>
  <div v-loading="loading">
    <PageCard title="加工共享黄金路径（多表）">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="源表" class="portal-field-xl">
          <el-select v-model="tableId" filterable placeholder="输入表名筛选">
            <el-option
              v-for="t in eligible"
              :key="t.tableId"
              :label="`${t.tableName}（${t.physicalTableName}）`"
              :value="t.tableId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标表" class="portal-field-lg">
          <el-select
            v-model="targetTable"
            filterable
            allow-create
            default-first-option
            clearable
            :loading="targetTablesLoading"
            placeholder="输入表名筛选，或新建如 dws_xxx"
            style="width: 100%"
          >
            <el-option v-for="t in targetTableOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="过滤非空列" class="portal-field-md">
          <el-select v-model="filterColumn" clearable filterable placeholder="输入字段名筛选">
            <el-option v-for="c in sourceColumns" :key="c.name" :label="c.name" :value="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button @click="previewFusion">预检 SQL</el-button>
        </el-form-item>
      </el-form>
      <el-input
        v-if="previewSql"
        :model-value="previewSql"
        type="textarea"
        :rows="3"
        readonly
        style="margin-bottom: 12px"
      />
      <el-steps :active="activeStep" finish-status="success" align-center>
        <el-step v-for="item in steps" :key="item.no" :title="item.name" :description="statusLabel(item.status)" />
      </el-steps>
    </PageCard>

    <div v-if="overview" class="golden-path-grid">
      <PageCard title="1. 源表">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="物理表">{{ overview.source.physicalTableName }}</el-descriptions-item>
          <el-descriptions-item label="源行数">{{ overview.source.physicalRows }}</el-descriptions-item>
        </el-descriptions>
      </PageCard>
      <PageCard title="2. 融合入库">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(overview.fusion.status)">{{ statusLabel(overview.fusion.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="产出表">{{ overview.fusion.producedTable }}</el-descriptions-item>
          <el-descriptions-item label="产出行数">{{ overview.source.producedRows ?? '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-button type="primary" :loading="runningStep === 2" @click="runStep(2)">
          {{ isDone(overview.fusion.status) ? '重新融合入库' : '执行融合入库' }}
        </el-button>
      </PageCard>
      <PageCard title="3. 产出元数据">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="条目编码">{{ overview.metadata.entryCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(overview.metadata.status)">{{ statusLabel(overview.metadata.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-button type="primary" :loading="runningStep === 3" :disabled="!isDone(overview.fusion.status)" @click="runStep(3)">
          采集并入账
        </el-button>
      </PageCard>
      <PageCard title="4. 产出质量">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="评分">{{ overview.quality.score ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="问题行">{{ overview.quality.issueCount ?? '—' }}</el-descriptions-item>
        </el-descriptions>
        <el-button type="primary" :loading="runningStep === 4" :disabled="!isDone(overview.metadata.status)" @click="runStep(4)">
          执行质量稽核
        </el-button>
      </PageCard>
      <PageCard title="5. 融合编目发布">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="发布状态">
            <el-tag :type="statusTagType(overview.catalog.publishStatus || overview.catalog.status)">
              {{ statusLabel(overview.catalog.publishStatus || overview.catalog.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="来源路径">加工共享</el-descriptions-item>
        </el-descriptions>
        <el-button
          type="primary"
          :loading="runningStep === 5"
          :disabled="overview.quality.score === undefined || overview.quality.score === null"
          @click="runStep(5)"
        >
          审批并发布
        </el-button>
      </PageCard>
      <PageCard title="6. 订阅授权">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="授权编码">{{ overview.subscription.authorizationCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="授权状态">
            {{ statusLabel(overview.subscription.authorizationStatus) }}
          </el-descriptions-item>
        </el-descriptions>
        <el-button
          type="primary"
          :loading="runningStep === 6"
          :disabled="!isDone(overview.catalog.publishStatus)"
          @click="runStep(6)"
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
