<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type AssetTag, type HealthMetric, type Policy } from '../useIngestionHub'

const props = defineProps<{ module: string }>()
const { loading, loadError, withLoad } = useIngestionLoading()
const policies = ref<Policy[]>([])
const tags = ref<AssetTag[]>([])
const health = ref<HealthMetric[]>([])
const globalView = ref<Record<string, unknown> | null>(null)
const backupJobs = ref<Record<string, unknown>[]>([])
const archiveJobs = ref<Record<string, unknown>[]>([])
const searchQ = ref('')
const searchResult = ref<Record<string, unknown> | null>(null)

const POLICY_FILTER: Record<string, string | undefined> = {
  m069: 'CLASSIFY', m070: 'MASK', m073: 'BACKUP', m074: 'ARCHIVE', m075: 'DESTROY',
}

const title = computed(() => ({
  m069: '数据分级分类', m070: '数据脱敏策略', m071: '数据标签管理',
  m072: '数据搜索引擎', m073: '数据备份', m074: '数据归档',
  m075: '数据销毁', m076: '全局数据资产视图', m077: '健康监控',
}[props.module] || '数据资产管理'))

async function reload() {
  await withLoad(async () => {
    const pf = POLICY_FILTER[props.module]
    if (pf || ['m069', 'm070', 'm073', 'm074', 'm075'].includes(props.module)) {
      policies.value = (await ingestionApi.policies(pf)).data
    }
    if (props.module === 'm071') tags.value = (await ingestionApi.tags()).data
    if (props.module === 'm076') globalView.value = (await ingestionApi.globalView()).data
    if (props.module === 'm077') health.value = (await ingestionApi.health()).data
    if (props.module === 'm073') backupJobs.value = (await ingestionApi.backupJobs()).data
    if (props.module === 'm074') archiveJobs.value = (await ingestionApi.archiveJobs()).data
  })
}

async function runLifecycle(id: number) {
  await ingestionApi.lifecycle(id)
  await reload()
}

async function doSearch() {
  searchResult.value = (await ingestionApi.search(searchQ.value)).data
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard :title="title">
      <el-table v-if="['m069','m070','m073','m074','m075'].includes(module)" :data="policies" stripe>
        <el-table-column prop="policyName" label="策略" />
        <el-table-column prop="policyType" label="类型" width="100" />
        <el-table-column prop="ruleExpr" label="规则" min-width="180" />
        <el-table-column v-if="['m073','m074','m075'].includes(module)" label="演练" width="100">
          <template #default="{ row }"><el-button link @click="runLifecycle(row.id)">执行</el-button></template>
        </el-table-column>
      </el-table>

      <el-table v-if="module === 'm071'" :data="tags" stripe>
        <el-table-column prop="tagName" label="标签" />
        <el-table-column prop="ruleExpr" label="规则" />
        <el-table-column prop="hitCount" label="命中" width="80" />
      </el-table>

      <template v-if="module === 'm072'">
        <el-input v-model="searchQ" placeholder="搜索编目/资产" style="max-width:320px;margin-right:8px" @keyup.enter="doSearch" />
        <el-button @click="doSearch">搜索</el-button>
        <el-tag v-if="searchResult" style="margin-left:8px">引擎: {{ searchResult.engine }}</el-tag>
        <el-table v-if="searchResult?.hits" :data="searchResult.hits as Record<string,unknown>[]" stripe style="margin-top:12px">
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column prop="title" label="标题" />
        </el-table>
      </template>

      <el-table v-if="module === 'm073'" :data="backupJobs" stripe>
        <el-table-column prop="jobCode" label="作业" />
        <el-table-column prop="scheduleCron" label="周期" width="120" />
        <el-table-column prop="backupPath" label="路径" min-width="180" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>

      <el-table v-if="module === 'm074'" :data="archiveJobs" stripe>
        <el-table-column prop="jobCode" label="作业" />
        <el-table-column prop="archivePath" label="归档路径" min-width="180" />
        <el-table-column prop="retentionDays" label="保留天" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>

      <template v-if="module === 'm076'">
        <el-descriptions v-if="globalView" :column="3" border size="small">
          <el-descriptions-item label="资产总数">{{ globalView.totalAssets }}</el-descriptions-item>
          <el-descriptions-item label="接入通道">{{ globalView.ingestChannels }}</el-descriptions-item>
          <el-descriptions-item label="已发布编目">{{ globalView.publishedRegistries }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <el-table v-if="module === 'm077'" :data="health" stripe>
        <el-table-column prop="metricLabel" label="指标" />
        <el-table-column prop="metricValue" label="值" />
        <el-table-column prop="alertLevel" label="级别" width="90" />
      </el-table>
    </PageCard>
  </div>
</template>
