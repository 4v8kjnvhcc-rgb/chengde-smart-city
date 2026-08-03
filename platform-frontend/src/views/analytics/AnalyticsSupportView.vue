<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

interface Model {
  id: number
  modelCode: string
  modelName: string
  mCode: string
  deDashboardId: string
  sampleRowCount: number
}

interface Sample {
  rowNo: number
  dim1: string
  dim2: string
  metric1: number
  metric2: number
}

const models = ref<Model[]>([])
const samples = ref<Sample[]>([])
const summary = ref<{ totalModels: number; domains: { domainCode: string; modelCount: number }[] }>()

async function load() {
  const [s, m] = await Promise.all([
    api.get('/analytics/summary'),
    api.get('/analytics/models', { params: { domain: 'support' } }),
  ])
  summary.value = s.data
  models.value = m.data
}

async function openSamples(id: number) {
  const res = await api.get(`/analytics/models/${id}/samples`)
  samples.value = res.data
  ElMessage.success(`已加载 ${res.data.length} 行样例`)
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="通用支撑"
      description="MS6：指标体系 / 标签 / 任务编排 / 结果发布（能力等价 POC）"
    />
    <PageCard title="模型域汇总" style="margin-bottom: 16px">
      <el-descriptions v-if="summary" :column="2" border>
        <el-descriptions-item label="已发布模型总数">{{ summary.totalModels }}</el-descriptions-item>
        <el-descriptions-item label="支撑域模型">{{ models.length }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-if="summary" class="portal-table" :data="summary.domains" stripe style="margin-top: 12px">
        <el-table-column prop="domainCode" label="域" />
        <el-table-column prop="modelCount" label="模型数" />
      </el-table>
    </PageCard>
    <PageCard title="支撑类分析模型">
      <el-table class="portal-table" :data="models" stripe>
        <el-table-column prop="modelCode" label="编码" width="120" />
        <el-table-column prop="modelName" label="名称" min-width="180" />
        <el-table-column prop="mCode" label="模块" width="90" />
        <el-table-column prop="sampleRowCount" label="样例行" width="90" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSamples(row.id)">样例</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-table v-if="samples.length" class="portal-table" :data="samples.slice(0, 20)" stripe size="small" style="margin-top: 16px">
        <el-table-column prop="rowNo" label="#" width="60" />
        <el-table-column prop="dim1" label="维度1" />
        <el-table-column prop="dim2" label="维度2" />
        <el-table-column prop="metric1" label="指标1" />
        <el-table-column prop="metric2" label="指标2" />
      </el-table>
    </PageCard>
  </div>
</template>
