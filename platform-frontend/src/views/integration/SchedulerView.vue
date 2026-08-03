<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Workflow {
  id: number
  workflowCode: string
  workflowName: string
  status: string
  lastRunAt: string
  lastMessage: string
}

const workflows = ref<Workflow[]>([])
const lastResult = ref('')

async function load() {
  const res = await api.get('/analytics/workflows')
  workflows.value = res.data
}

async function run(id: number) {
  const res = await api.post(`/analytics/workflows/${id}/run`)
  lastResult.value = JSON.stringify(res.data, null, 2)
  ElMessage.success('DolphinScheduler 工作流已触发')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="调度管理"
      description="DolphinScheduler 真实联调：工作流台账与触发（D10 M098）"
    />
    <PageCard>
      <el-table class="portal-table" :data="workflows" stripe>
        <el-table-column prop="workflowCode" label="工作流编码" min-width="160" />
        <el-table-column prop="workflowName" label="名称" min-width="200" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="lastRunAt" label="最近执行" min-width="170" />
        <el-table-column prop="lastMessage" label="结果" min-width="180" />
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
