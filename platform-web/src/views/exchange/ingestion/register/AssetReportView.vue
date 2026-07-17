<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading } from '../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const report = ref<Record<string, unknown> | null>(null)

const tableTrend = computed(() => (report.value?.tableTrend as { month: string; count: number }[]) || [])
const storageTrend = computed(() => (report.value?.storageTrend as { month: string; gb: number }[]) || [])
const maxTable = computed(() => Math.max(...tableTrend.value.map(t => t.count), 1))
const maxStorage = computed(() => Math.max(...storageTrend.value.map(t => t.gb), 1))

onMounted(() => withLoad(async () => {
  report.value = (await ingestionApi.assetReport()).data
}))
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据资产报告">
      <p class="hint">全可视化展示平台数据资产：项目、表、脚本、工作流及存储与增长趋势。</p>
      <el-row v-if="report" :gutter="12">
        <el-col :span="4"><el-statistic title="登记项目" :value="Number(report.projectCount || 0)" /></el-col>
        <el-col :span="4"><el-statistic title="物理表" :value="Number(report.tableCount || 0)" /></el-col>
        <el-col :span="4"><el-statistic title="接入任务" :value="Number(report.taskCount || 0)" /></el-col>
        <el-col :span="4"><el-statistic title="活跃脚本" :value="Number(report.scriptCount || 0)" /></el-col>
        <el-col :span="4"><el-statistic title="工作流" :value="Number(report.workflowCount || 0)" /></el-col>
        <el-col :span="4"><el-statistic title="存储(GB)" :value="Number(report.storageGb || 0)" :precision="1" /></el-col>
      </el-row>
      <el-row v-if="report" :gutter="16" style="margin-top:20px">
        <el-col :span="12">
          <h4 class="chart-title">表新增趋势</h4>
          <div class="bar-chart">
            <div v-for="t in tableTrend" :key="t.month" class="bar-item">
              <div class="bar" :style="{ height: `${(t.count / maxTable) * 100}%` }" />
              <span class="bar-label">{{ t.month }}</span>
            </div>
          </div>
        </el-col>
        <el-col :span="12">
          <h4 class="chart-title">数据存储量趋势 (GB)</h4>
          <div class="bar-chart">
            <div v-for="t in storageTrend" :key="t.month" class="bar-item">
              <div class="bar bar-storage" :style="{ height: `${(t.gb / maxStorage) * 100}%` }" />
              <span class="bar-label">{{ t.month }}</span>
            </div>
          </div>
        </el-col>
      </el-row>
      <el-row v-if="report" :gutter="12" style="margin-top:16px">
        <el-col :span="8">
          <h4 class="chart-title">TOP 项目</h4>
          <el-table :data="report.topProjects as Record<string,unknown>[]" stripe size="small">
            <el-table-column prop="projectName" label="项目" />
            <el-table-column prop="tableCount" label="表数" width="70" />
          </el-table>
        </el-col>
        <el-col :span="8">
          <h4 class="chart-title">热门表</h4>
          <el-table :data="report.topTables as Record<string,unknown>[]" stripe size="small">
            <el-table-column prop="tableName" label="表" />
            <el-table-column prop="columnCount" label="字段" width="70" />
          </el-table>
        </el-col>
        <el-col :span="8">
          <h4 class="chart-title">TOP 任务</h4>
          <el-table :data="report.topTasks as Record<string,unknown>[]" stripe size="small">
            <el-table-column prop="taskName" label="任务" />
            <el-table-column label="状态" width="80">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </PageCard>
  </div>
</template>

<style scoped>
.hint { font-size: 13px; color: #606266; margin: 0 0 16px; }
.chart-title { margin: 0 0 8px; font-size: 14px; color: #303133; }
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 140px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  justify-content: flex-end;
}
.bar {
  width: 100%;
  max-width: 48px;
  background: #409eff;
  border-radius: 4px 4px 0 0;
  min-height: 4px;
  transition: height 0.3s;
}
.bar-storage { background: #67c23a; }
.bar-label { font-size: 11px; color: #909399; margin-top: 4px; }
</style>
