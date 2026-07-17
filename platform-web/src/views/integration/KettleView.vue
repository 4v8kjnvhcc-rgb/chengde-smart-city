<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

const jobs = ref<Job[]>([])
const lastResult = ref('')

async function load() {
  const res = await api.get('/exchange/kettle/jobs')
  jobs.value = res.data
}

async function run(id: number) {
  const res = await api.post(`/exchange/kettle/jobs/${id}/run`)
  lastResult.value = JSON.stringify(res.data, null, 2)
  ElMessage.success('Kettle 作业已执行')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="ETL 治理（Kettle）" description="Kettle Carte 真实联调：M215 作业台账与执行" />
    <PageCard>
      <el-table class="portal-table" :data="jobs" stripe>
        <el-table-column prop="jobCode" label="作业编码" min-width="140" />
        <el-table-column prop="jobName" label="名称" min-width="180" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="lastMessage" label="最近结果" min-width="220" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="run(row.id)">执行</el-button>
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
