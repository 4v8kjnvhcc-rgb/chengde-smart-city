<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface AuditRow {
  id: number
  username: string
  action: string
  resourceType: string
  detail: string
  createdAt: string
}

const rows = ref<AuditRow[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.get('/system/audit-logs', { params: { page: page.value, size: 20 } })
    rows.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="审计日志" description="安全审计记录（INSERT-ONLY）" />
    <PageCard>
      <el-table class="portal-table" :data="rows" v-loading="loading" stripe>
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column prop="action" label="动作" width="120" />
        <el-table-column prop="resourceType" label="资源类型" width="120" />
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="page"
        @current-change="(p: number) => { page = p; load() }"
      />
    </PageCard>
  </div>
</template>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
