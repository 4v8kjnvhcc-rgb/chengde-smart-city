<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ingestionApi } from '../../useIngestionHub'

const props = defineProps<{
  modelValue: boolean
  projectId: number | null
  projectName?: string
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'open-table', id: number): void
}>()

const loading = ref(false)
const rows = ref<Record<string, unknown>[]>([])

async function load() {
  if (!props.projectId) return
  loading.value = true
  try {
    rows.value = (await ingestionApi.assetReportProjectTables(props.projectId)).data
  } catch {
    ElMessage.error('加载项目表清单失败')
    rows.value = []
  } finally {
    loading.value = false
  }
}

watch(() => [props.modelValue, props.projectId], ([open]) => {
  if (open) void load()
})
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="`项目表清单 · ${projectName || ''}`"
    size="560px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <el-table :data="rows" stripe size="small" @row-click="(row: Record<string, unknown>) => emit('open-table', Number(row.id))">
        <el-table-column prop="tableCode" label="编码" width="120" show-overflow-tooltip />
        <el-table-column prop="tableName" label="表名" min-width="120" show-overflow-tooltip />
        <el-table-column prop="columnCount" label="字段" width="60" />
        <el-table-column label="存储(GB)" width="90">
          <template #default="{ row }">{{ Number(row.storageGb || 0).toFixed(3) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="该项目下暂无登记表" />
    </div>
  </el-drawer>
</template>
