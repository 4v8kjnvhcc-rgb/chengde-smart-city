<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type AssetTag, type HealthMetric, type Policy } from '../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const classifyPolicies = ref<Policy[]>([])
const maskPolicies = ref<Policy[]>([])
const lifecyclePolicies = ref<Policy[]>([])
const tags = ref<AssetTag[]>([])
const health = ref<HealthMetric[]>([])
const globalView = ref<Record<string, unknown> | null>(null)
const backupJobs = ref<Record<string, unknown>[]>([])
const archiveJobs = ref<Record<string, unknown>[]>([])
const searchQ = ref('')
const searchResult = ref<Record<string, unknown> | null>(null)

async function reload() {
  await withLoad(async () => {
    const [c, m, b, a, d] = await Promise.all([
      ingestionApi.policies('CLASSIFY'),
      ingestionApi.policies('MASK'),
      ingestionApi.policies('BACKUP'),
      ingestionApi.policies('ARCHIVE'),
      ingestionApi.policies('DESTROY'),
    ])
    classifyPolicies.value = c.data
    maskPolicies.value = m.data
    lifecyclePolicies.value = [...b.data, ...a.data, ...d.data]
    tags.value = (await ingestionApi.tags()).data
    globalView.value = (await ingestionApi.globalView()).data
    health.value = (await ingestionApi.health()).data
    backupJobs.value = (await ingestionApi.backupJobs()).data
    archiveJobs.value = (await ingestionApi.archiveJobs()).data
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
    <PageCard title="数据分级分类">
      <el-table :data="classifyPolicies" stripe size="small">
        <el-table-column prop="policyName" label="策略" />
        <el-table-column prop="ruleExpr" label="规则" />
      </el-table>
    </PageCard>
    <PageCard title="数据脱敏策略">
      <el-table :data="maskPolicies" stripe size="small">
        <el-table-column prop="policyName" label="策略" />
        <el-table-column prop="ruleExpr" label="规则" />
      </el-table>
    </PageCard>
    <PageCard title="数据标签管理">
      <el-table :data="tags" stripe size="small">
        <el-table-column prop="tagName" label="标签" />
        <el-table-column prop="ruleExpr" label="规则" />
        <el-table-column prop="hitCount" label="命中" width="80" />
      </el-table>
    </PageCard>
    <PageCard title="数据搜索引擎">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="关键词" class="portal-field-xl">
          <el-input v-model="searchQ" placeholder="搜索编目/资产" @keyup.enter="doSearch" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="doSearch">搜索</el-button>
        </el-form-item>
        <el-tag v-if="searchResult">引擎: {{ searchResult.engine }}</el-tag>
      </el-form>
      <el-table v-if="searchResult?.hits" :data="searchResult.hits as Record<string,unknown>[]" stripe size="small" style="margin-top:12px">
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ $statusLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" />
      </el-table>
    </PageCard>
    <PageCard title="数据备份">
      <el-table :data="backupJobs" stripe size="small">
        <el-table-column prop="jobCode" label="作业" />
        <el-table-column prop="scheduleCron" label="周期" width="120" />
        <el-table-column prop="backupPath" label="路径" min-width="180" />
      </el-table>
    </PageCard>
    <PageCard title="数据归档">
      <el-table :data="archiveJobs" stripe size="small">
        <el-table-column prop="jobCode" label="作业" />
        <el-table-column prop="archivePath" label="归档路径" min-width="180" />
        <el-table-column prop="retentionDays" label="保留天" width="90" />
      </el-table>
    </PageCard>
    <PageCard title="数据销毁">
      <el-table :data="lifecyclePolicies.filter(p => p.policyType === 'DESTROY')" stripe size="small">
        <el-table-column prop="policyName" label="策略" />
        <el-table-column label="演练" width="80">
          <template #default="{ row }"><el-button link @click="runLifecycle(row.id)">执行</el-button></template>
        </el-table-column>
      </el-table>
    </PageCard>
    <PageCard title="全局数据资产视图">
      <el-descriptions v-if="globalView" :column="3" border size="small">
        <el-descriptions-item label="资产总数">{{ globalView.totalAssets }}</el-descriptions-item>
        <el-descriptions-item label="接入通道">{{ globalView.ingestChannels }}</el-descriptions-item>
        <el-descriptions-item label="已发布编目">{{ globalView.publishedRegistries }}</el-descriptions-item>
      </el-descriptions>
    </PageCard>
    <PageCard title="健康监控">
      <el-table :data="health" stripe size="small">
        <el-table-column prop="metricLabel" label="指标" />
        <el-table-column prop="metricValue" label="值" />
        <el-table-column label="级别" width="90">
          <template #default="{ row }">{{ $statusLabel(row.alertLevel) }}</template>
        </el-table-column>
      </el-table>
    </PageCard>
  </div>
</template>
