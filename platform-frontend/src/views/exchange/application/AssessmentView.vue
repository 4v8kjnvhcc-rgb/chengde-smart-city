<script setup lang="ts">
import { onMounted, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

defineProps<{ mode?: 'front' | 'config' }>()

const loading = ref(false)
const executions = ref<Record<string, unknown>[]>([])
const resultRows = ref<Record<string, unknown>[]>([])
const selectedExecId = ref<number>()

async function load() {
  loading.value = true
  try {
    const all = (await api.get('/exchange/assessment/executions')).data as Record<string, unknown>[]
    // 前台优先展示已发布；若暂无发布则展示全部便于演示
    const published = all.filter((e) => e.published === true || e.published === 1 || e.status === 'PUBLISHED')
    executions.value = published.length ? published : all
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function showResults(id: number) {
  selectedExecId.value = id
  resultRows.value = (await api.get(`/exchange/assessment/executions/${id}/results`)).data
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <PageCard title="考核结果与应报数据">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="各单位查看考核分数与明细。周期、指标与执行发布请在「系统管理 → 数据共享交换平台 → 应用平台 → 考核评估配置」维护。"
      />
      <el-table class="portal-table" :data="executions" stripe size="small">
        <el-table-column prop="targetName" label="考核对象" min-width="140" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ $statusLabel(row.targetType) }}</template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="showResults(Number(row.id))">明细</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="resultRows.length" style="margin-top:16px">
        <h4>评分明细（执行 #{{ selectedExecId }}）</h4>
        <el-table class="portal-table" :data="resultRows" stripe size="small">
          <el-table-column prop="indicatorName" label="指标" min-width="140" />
          <el-table-column label="类型" width="72">
            <template #default="{ row }">{{ $statusLabel(row.indicatorType) }}</template>
          </el-table-column>
          <el-table-column prop="score" label="得分" width="80" />
          <el-table-column prop="rawValue" label="原始值" width="80" />
          <el-table-column prop="remark" label="备注 / 应报说明" min-width="160" />
        </el-table>
      </div>
      <el-empty v-if="!executions.length" description="暂无考核结果，请管理员在考核评估配置中执行并发布" />
    </PageCard>
  </div>
</template>
