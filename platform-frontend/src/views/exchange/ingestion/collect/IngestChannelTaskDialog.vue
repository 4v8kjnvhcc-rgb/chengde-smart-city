<script setup lang="ts">
/**
 * 半结构 / API / CDC 同页「任务管理」弹窗：仅展示当前通道类型的接入任务。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'
import { ingestionApi, type Channel } from '../useIngestionHub'

const props = defineProps<{
  modelValue: boolean
  channelType: 'SEMI' | 'API' | 'CDC'
  title?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  edit: [Channel]
}>()

const loading = ref(false)
const runBusy = ref(false)
const channels = ref<Channel[]>([])
const keyword = ref('')
const statusFilter = ref('')

const typeLabel = computed(() => {
  if (props.channelType === 'SEMI') return '半结构化'
  if (props.channelType === 'API') return 'API'
  return 'CDC'
})

const dialogTitle = computed(() => props.title || `${typeLabel.value} · 任务管理`)

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const st = statusFilter.value
  return channels.value.filter((c) => {
    if (st && String(c.status || '').toUpperCase() !== st) return false
    if (!kw) return true
    const cfg = c.configJson || ''
    const blob = [c.channelName, c.channelCode, cfg, c.lastMessage].filter(Boolean).join(' ').toLowerCase()
    return blob.includes(kw)
  })
})

function parseConfig(json?: string): Record<string, string> {
  if (!json) return {}
  try {
    const o = JSON.parse(json) as Record<string, unknown>
    const out: Record<string, string> = {}
    for (const [k, v] of Object.entries(o)) {
      if (v == null || typeof v === 'object') continue
      out[k] = String(v)
    }
    return out
  } catch {
    return {}
  }
}

function linkSummary(row: Channel): string {
  const cfg = parseConfig(row.configJson)
  if (props.channelType === 'API') {
    return `${(cfg.method || 'GET').toUpperCase()} ${cfg.url || '—'} → ${cfg.targetTable || 'ODS'}`
  }
  if (props.channelType === 'CDC') {
    if (cfg.cdcMode === 'write') {
      return `${cfg.changeSource || 'Kafka'} → ${cfg.targetTable || '—'}`
    }
    return `${cfg.sourceDb || cfg.canalHost || '—'} → ${cfg.targetMq || 'Kafka'}`
  }
  const kind = (cfg.sourceKind || 'kafka').toLowerCase()
  if (kind === 'mongo') return `Mongo:${cfg.collection || cfg.database || '—'} → ${cfg.targetTable || 'ODS'}`
  if (kind === 'es') return `ES:${cfg.index || '—'} → ${cfg.targetTable || 'ODS'}`
  return `Kafka:${cfg.topic || '—'} → ${cfg.targetTable || 'ODS'}`
}

function subtypeLabel(row: Channel): string {
  const cfg = parseConfig(row.configJson)
  if (props.channelType === 'SEMI') {
    const k = (cfg.sourceKind || 'kafka').toLowerCase()
    if (k === 'mongo') return 'MongoDB'
    if (k === 'es') return 'ElasticSearch'
    return 'Kafka'
  }
  if (props.channelType === 'CDC') {
    return cfg.cdcMode === 'write' ? 'CDC 写入' : 'CDC 读取'
  }
  return 'API'
}

async function reload() {
  loading.value = true
  try {
    channels.value = (await ingestionApi.channels(props.channelType)).data || []
  } finally {
    loading.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

function onEdit(row: Channel) {
  emit('edit', row)
  close()
}

async function onRun(row: Channel) {
  runBusy.value = true
  try {
    const res = await ingestionApi.runChannel(row.id)
    ElMessage.success(String(res.data?.message || '执行完成'))
    await reload()
  } catch {
    await reload()
  } finally {
    runBusy.value = false
  }
}

async function onRemove(row: Channel) {
  try {
    await ElMessageBox.confirm(
      `确定删除任务「${row.channelName}」？仅删除接入配置，不会删除已落库数据。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await ingestionApi.deleteChannel(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

watch(
  () => [props.modelValue, props.channelType] as const,
  ([open]) => {
    if (open) {
      keyword.value = ''
      statusFilter.value = ''
      void reload()
    }
  },
)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="960px"
    top="6vh"
    destroy-on-close
    append-to-body
    class="ingest-task-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading">
      <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent>
        <el-form-item label="状态" class="portal-field-sm">
          <el-select v-model="statusFilter" clearable placeholder="全部">
            <el-option label="空闲" value="IDLE" />
            <el-option label="运行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" class="portal-field-lg">
          <el-input v-model="keyword" clearable placeholder="任务名称 / 链路" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button @click="reload">刷新</el-button>
        </el-form-item>
      </el-form>
      <p class="task-count">共 {{ filtered.length }} 个任务（{{ typeLabel }}）</p>
      <el-table :data="filtered" stripe border class="portal-table" empty-text="暂无本模块任务，请在配置页保存后查看">
        <el-table-column prop="channelName" label="任务名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ subtypeLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="数据链路" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="link-code">{{ linkSummary(row) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近运行" width="170">
          <template #default="{ row }">{{ formatDateTime(row.lastRunAt) }}</template>
        </el-table-column>
        <el-table-column prop="lastMessage" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="onEdit(row)">编辑</el-button>
            <el-button link type="success" :loading="runBusy" @click="onRun(row)">执行</el-button>
            <el-button link type="danger" @click="onRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.task-count {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.link-code {
  font-size: 12px;
  background: transparent;
  color: var(--el-text-color-regular);
}
</style>
