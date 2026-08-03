<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Job {
  id: number
  jobCode: string
  jobName: string
  status: string
  lastRunAt: string
  lastMessage: string
}

const router = useRouter()
const jobs = ref<Job[]>([])
const lastResult = ref('')
const carteOk = ref<boolean | null>(null)
const carteMsg = ref('')

async function loadHealth() {
  try {
    const res = await api.get('/governance/kettle/health')
    carteOk.value = String(res.data?.status || '').toUpperCase() === 'ONLINE'
    carteMsg.value = String(res.data?.message || (carteOk.value ? 'Carte 可用' : 'Carte 不可用'))
  } catch {
    try {
      const res = await api.get('/integration/health')
      carteOk.value = !!res.data?.kettle
      carteMsg.value = carteOk.value ? 'Carte 可用' : 'Carte 不可用，请启动 compose profile etl'
    } catch {
      carteOk.value = false
      carteMsg.value = '无法探测 Carte 健康状态'
    }
  }
}

async function load() {
  const res = await api.get('/exchange/kettle/jobs')
  jobs.value = res.data
}

async function run(id: number) {
  const res = await api.post(`/exchange/kettle/jobs/${id}/run`)
  lastResult.value = JSON.stringify(res.data, null, 2)
  ElMessage.warning('下方为演示台账执行；真实治理任务请在「数据融合治理 → 任务运行」中执行')
  load()
}

function goGovernance() {
  router.push({ path: '/governance', query: { tab: 'etl', etlSub: 'task-run' } })
}

onMounted(() => {
  void loadHealth()
  void load()
})
</script>

<template>
  <div>
    <PageHeader
      title="ETL 治理（Kettle）"
      description="正式执行以治理画布 → KTR → Carte 为准；本页作业表为历史演示台账"
    />
    <PageCard>
      <el-alert
        :type="carteOk ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="carteMsg || '探测中…'"
        style="margin-bottom: 12px"
      />
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="goGovernance">前往治理任务运行</el-button>
          <el-button @click="loadHealth">刷新 Carte 状态</el-button>
        </el-form-item>
      </el-form>
      <el-table class="portal-table" :data="jobs" stripe>
        <el-table-column prop="jobCode" label="作业编码" min-width="140" />
        <el-table-column prop="jobName" label="名称" min-width="180" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="lastMessage" label="最近结果" min-width="220" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="run(row.id)">演示执行</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pre v-if="lastResult" class="run-log">{{ lastResult }}</pre>
    </PageCard>
  </div>
</template>

<style scoped>
.run-log {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 12px;
}
</style>
