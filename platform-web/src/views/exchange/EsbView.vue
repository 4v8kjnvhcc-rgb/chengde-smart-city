<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Flow {
  id: number
  flowCode: string
  flowName: string
  status: string
  lastInvokeAt: string
  lastResult: string
}

const flows = ref<Flow[]>([])
const lastInvoke = ref('')

async function load() {
  const res = await api.get('/exchange/esb/flows')
  flows.value = res.data
}

async function invoke(id: number) {
  const res = await api.post(`/exchange/esb/flows/${id}/invoke`)
  lastInvoke.value = JSON.stringify(res.data, null, 2)
  ElMessage.success('MessageFlow 调用成功')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="服务总线 ESB" description="演示场景：MessageFlow 调用与 SMC 日志（能力等价 POC）" />
    <PageCard>
      <el-table class="portal-table" :data="flows" stripe>
        <el-table-column prop="flowCode" label="Flow 编码" min-width="140" />
        <el-table-column prop="flowName" label="名称" min-width="180" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="lastResult" label="最近结果" min-width="240" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link @click="invoke(row.id)">调用</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pre v-if="lastInvoke" class="invoke-log">{{ lastInvoke }}</pre>
    </PageCard>
  </div>
</template>

<style scoped>
.invoke-log {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 12px;
}
</style>
