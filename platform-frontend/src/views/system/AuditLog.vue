<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel } from '@/utils/status-label'

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
const filters = reactive({ username: '', action: '' })

async function load() {
  loading.value = true
  try {
    const res = await api.get('/system/audit-logs', {
      params: {
        page: page.value,
        size: 20,
        username: filters.username || undefined,
        action: filters.action || undefined,
      },
    })
    rows.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void load()
}

function onReset() {
  filters.username = ''
  filters.action = ''
  page.value = 1
  void load()
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="审计日志" description="安全审计记录（INSERT-ONLY），支持按用户与动作筛选" />
    <PageCard>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="用户" class="portal-field-md">
          <el-input v-model="filters.username" clearable placeholder="用户名" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="动作" class="portal-field-md">
          <el-input v-model="filters.action" clearable placeholder="如 LOGIN" @keyup.enter="search" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table class="portal-table" :data="rows" v-loading="loading" stripe>
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column label="动作" width="160">
          <template #default="{ row }">{{ statusLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="资源类型" width="140">
          <template #default="{ row }">{{ $statusLabel(row.resourceType) }}</template>
        </el-table-column>
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
