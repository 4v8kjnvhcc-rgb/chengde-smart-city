<script setup lang="ts">
/**
 * V3.0「数据质量评估」：按完整性/一致性/准确性/及时性等维度汇总近期稽核结果。
 * 数据来自本地 gov_quality 运行与问题，非演示分。
 */
import { computed, onMounted, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface RunRow {
  id: number
  taskName?: string
  status: string
  score?: number
  issueCount?: number
  startedAt?: string
}

interface IssueRow {
  checkType?: string
  issueCount?: number
}

interface DimRow {
  key: string
  label: string
  issueTotal: number
  checkHits: number
  scoreHint: number | null
}

const CHECK_TO_DIM: Record<string, string> = {
  RECORD_COUNT: 'completeness',
  NULL_CHECK: 'completeness',
  UNIQUENESS: 'uniqueness',
  ACCURACY: 'accuracy',
  RANGE: 'accuracy',
  CONSISTENCY: 'consistency',
  LOGIC: 'consistency',
  VOLATILITY: 'timeliness',
  TIMELINESS: 'timeliness',
  CUSTOM: 'accuracy',
}

const DIM_META: { key: string; label: string }[] = [
  { key: 'completeness', label: '完整性' },
  { key: 'consistency', label: '一致性' },
  { key: 'accuracy', label: '准确性' },
  { key: 'timeliness', label: '及时性' },
  { key: 'uniqueness', label: '唯一性' },
]

const loading = ref(false)
const runs = ref<RunRow[]>([])
const {
  page: runPage,
  pageSize: runPageSize,
  paged: pagedRuns,
  total: runTotal,
  resetPage: resetRunPage,
} = useClientPager(runs)
const issueByDim = ref<Record<string, { issueTotal: number; checkHits: number }>>({})

const avgScore = computed(() => {
  const scored = runs.value.filter((r) => r.score != null)
  if (!scored.length) return null
  return Math.round(scored.reduce((s, r) => s + Number(r.score), 0) / scored.length)
})

const dimensions = computed<DimRow[]>(() =>
  DIM_META.map((m) => {
    const bag = issueByDim.value[m.key] || { issueTotal: 0, checkHits: 0 }
    let scoreHint: number | null = null
    if (bag.checkHits > 0) {
      // 有检查命中时：问题越多分越低（粗评估，与任务均分并用）
      scoreHint = Math.max(0, Math.min(100, 100 - Math.min(80, bag.issueTotal * 2)))
    }
    return {
      key: m.key,
      label: m.label,
      issueTotal: bag.issueTotal,
      checkHits: bag.checkHits,
      scoreHint,
    }
  }),
)

async function load() {
  loading.value = true
  try {
    const r = await api.get('/governance/quality/task-mgmt/runs')
    runs.value = (r.data || []).slice(0, 20)
    resetRunPage()
    const bag: Record<string, { issueTotal: number; checkHits: number }> = {}
    for (const m of DIM_META) bag[m.key] = { issueTotal: 0, checkHits: 0 }
    // 近 3 次运行并行拉取问题做维度汇总（≤3）
    const sample = runs.value.slice(0, 3)
    const issueLists = await Promise.all(
      sample.map((run) =>
        api.get(`/governance/quality/task-mgmt/runs/${run.id}/issues`).then((res) => (res.data || []) as IssueRow[]),
      ),
    )
    for (const issues of issueLists) {
      for (const iss of issues) {
        const dim = CHECK_TO_DIM[String(iss.checkType || '').toUpperCase()] || 'accuracy'
        if (!bag[dim]) bag[dim] = { issueTotal: 0, checkHits: 0 }
        bag[dim].checkHits += 1
        bag[dim].issueTotal += Number(iss.issueCount || 1)
      }
    }
    issueByDim.value = bag
  } catch {
    ElMessage.error('加载质量评估失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <PageCard title="数据质量评估">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="综合评分（近运行）" class="portal-field-md">
        <el-tag size="large" :type="avgScore == null ? 'info' : avgScore >= 80 ? 'success' : avgScore >= 60 ? 'warning' : 'danger'">
          {{ avgScore == null ? '暂无评分' : avgScore }}
        </el-tag>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button @click="load" :loading="loading">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="dimensions" stripe size="small" style="margin-bottom: 16px">
      <el-table-column prop="label" label="评估维度" width="120" />
      <el-table-column prop="checkHits" label="问题条目数" width="120" />
      <el-table-column prop="issueTotal" label="问题行合计" width="120" />
      <el-table-column label="维度参考分" width="120">
        <template #default="{ row }">{{ row.scoreHint == null ? '—' : row.scoreHint }}</template>
      </el-table-column>
      <el-table-column label="说明" min-width="200">
        <template #default="{ row }">
          {{ row.checkHits ? '由近次运行问题按检查类型归维' : '近期运行未命中该维检查' }}
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">近期运行（评估样本）</el-divider>
    <el-table :data="pagedRuns" stripe size="small">
      <el-table-column prop="id" label="运行ID" width="80" />
      <el-table-column prop="taskName" label="任务" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="score" label="评分" width="80" />
      <el-table-column prop="issueCount" label="问题数" width="80" />
      <el-table-column prop="startedAt" label="开始时间" width="170" />
    </el-table>
    <PortalPagination
      v-model:page="runPage"
      v-model:page-size="runPageSize"
      :total="runTotal"
    />
    <el-empty v-if="!loading && !runs.length" description="暂无运行；请先在「质量规则配置」下配置并执行任务" />
  </PageCard>
</template>
