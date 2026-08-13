<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

interface ErrorLogRow {
  id: number
  source: string
  moduleCode?: string
  moduleName?: string
  level: string
  errorCode?: string
  errorType?: string
  message: string
  stackTrace?: string
  requestUri?: string
  httpMethod?: string
  httpStatus?: number
  pageUrl?: string
  username?: string
  clientIp?: string
  occurredAt: string
  createdAt?: string
}

const rows = ref<ErrorLogRow[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref<ErrorLogRow | null>(null)

const filters = reactive({
  source: '',
  level: '',
  moduleCode: '',
  keyword: '',
})

async function load() {
  loading.value = true
  try {
    const res = await api.get('/system/error-logs', {
      params: {
        page: page.value,
        size: 20,
        source: filters.source || undefined,
        level: filters.level || undefined,
        moduleCode: filters.moduleCode || undefined,
        keyword: filters.keyword || undefined,
      },
    })
    rows.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void load()
}

function onReset() {
  filters.source = ''
  filters.level = ''
  filters.moduleCode = ''
  filters.keyword = ''
  page.value = 1
  void load()
}

async function openDetail(row: ErrorLogRow) {
  detailVisible.value = true
  detail.value = row
  try {
    const res = await api.get(`/system/error-logs/${row.id}`)
    if (res.data) detail.value = res.data
  } catch {
    // 列表行已足够展示
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="系统运行日志"
      description="记录前端、后端等运行报错：来源、功能模块、级别与发生时间"
    />
    <PageCard>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="来源" class="portal-field-sm">
          <el-select v-model="filters.source" clearable placeholder="全部">
            <el-option label="前端" value="FRONTEND" />
            <el-option label="后端" value="BACKEND" />
            <el-option label="任务" value="JOB" />
            <el-option label="网关" value="GATEWAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别" class="portal-field-sm">
          <el-select v-model="filters.level" clearable placeholder="全部">
            <el-option label="警告" value="WARN" />
            <el-option label="错误" value="ERROR" />
            <el-option label="致命" value="FATAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="模块" class="portal-field-md">
          <el-input v-model="filters.moduleCode" clearable placeholder="模块码" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="关键词" class="portal-field-lg">
          <el-input v-model="filters.keyword" clearable placeholder="消息/类型/路径" @keyup.enter="search" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table class="portal-table" :data="rows" v-loading="loading" stripe>
        <el-table-column prop="occurredAt" label="报错时间" width="170" />
        <el-table-column label="来源" width="90">
          <template #default="{ row }">{{ statusLabel(row.source) }}</template>
        </el-table-column>
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.level)" size="small">{{ statusLabel(row.level) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="功能模块" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.moduleName || row.moduleCode || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="message" label="错误信息" min-width="220" show-overflow-tooltip />
        <el-table-column prop="username" label="用户" width="110" />
        <el-table-column label="路径" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.requestUri || row.pageUrl || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="page"
        @current-change="(p: number) => { page = p; load() }"
      />
    </PageCard>

    <el-drawer v-model="detailVisible" title="运行日志详情" size="560px" destroy-on-close>
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="报错时间">{{ detail.occurredAt }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ statusLabel(detail.source) }}</el-descriptions-item>
          <el-descriptions-item label="级别">{{ statusLabel(detail.level) }}</el-descriptions-item>
          <el-descriptions-item label="模块">
            {{ detail.moduleName || '—' }}
            <span v-if="detail.moduleCode" class="muted">（{{ detail.moduleCode }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="错误码">{{ detail.errorCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="错误类型">{{ detail.errorType || '—' }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.username || '—' }}</el-descriptions-item>
          <el-descriptions-item label="客户端IP">{{ detail.clientIp || '—' }}</el-descriptions-item>
          <el-descriptions-item label="HTTP">
            {{ detail.httpMethod || '—' }} {{ detail.httpStatus || '' }}
          </el-descriptions-item>
          <el-descriptions-item label="请求/路由">{{ detail.requestUri || '—' }}</el-descriptions-item>
          <el-descriptions-item label="页面URL">{{ detail.pageUrl || '—' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息">{{ detail.message }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="stack-title">堆栈</h4>
        <pre class="stack">{{ detail.stackTrace || '无' }}</pre>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.stack-title {
  margin: 16px 0 8px;
  font-size: 14px;
}
.stack {
  margin: 0;
  padding: 12px;
  max-height: 360px;
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
.muted {
  color: var(--el-text-color-secondary);
  margin-left: 4px;
}
</style>
