<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { ingestionApi, type Channel } from '../useIngestionHub'

export interface ConfigField {
  key: string
  label: string
  defaultValue?: string
  hint?: string
}

const props = defineProps<{
  title: string
  channelType: string
  configFields: ConfigField[]
  subtitle?: string
}>()

const channels = ref<Channel[]>([])
const loading = ref(false)
const runBusy = ref(false)
const saveBusy = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | undefined>()
const channelName = ref('')
const channelForm = reactive<Record<string, string>>({})

const dialogTitle = computed(() => (editingId.value ? '编辑上传任务' : '新建上传任务'))

function parseConfig(json?: string): Record<string, string> {
  if (!json) return {}
  try {
    const o = JSON.parse(json) as Record<string, unknown>
    const out: Record<string, string> = {}
    for (const [k, v] of Object.entries(o)) out[k] = String(v ?? '')
    return out
  } catch {
    return {}
  }
}

function resetForm(ch?: Channel) {
  const cfg = parseConfig(ch?.configJson)
  Object.keys(channelForm).forEach((k) => delete channelForm[k])
  for (const f of props.configFields) {
    channelForm[f.key] = cfg[f.key] ?? f.defaultValue ?? ''
  }
  channelName.value = ch?.channelName || ''
  editingId.value = ch?.id
}

async function reload() {
  loading.value = true
  try {
    channels.value = (await ingestionApi.channels(props.channelType)).data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  channelName.value = `${props.title}-${new Date().toISOString().slice(0, 10)}`
  dialogVisible.value = true
}

function openEdit(row: Channel) {
  resetForm(row)
  dialogVisible.value = true
}

async function saveTask(andRun = false) {
  if (!channelName.value.trim()) {
    ElMessage.warning('请填写任务名称')
    return
  }
  saveBusy.value = true
  try {
    const body = {
      channelName: channelName.value.trim(),
      channelType: props.channelType,
      config: { ...channelForm },
    }
    let id = editingId.value
    if (id) {
      await ingestionApi.updateChannel(id, body)
      ElMessage.success('任务已保存')
    } else {
      id = Number((await ingestionApi.createChannel(body)).data)
      editingId.value = id
      ElMessage.success('上传任务已创建')
    }
    await reload()
    if (andRun && id) {
      await doRun(id)
    } else {
      dialogVisible.value = false
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saveBusy.value = false
  }
}

async function doRun(id: number) {
  runBusy.value = true
  try {
    const res = await ingestionApi.runChannel(id)
    ElMessage.success(String(res.data?.message || '执行完成'))
    dialogVisible.value = false
    await reload()
  } catch {
    // request 拦截器已提示
  } finally {
    runBusy.value = false
  }
}

async function runRow(row: Channel) {
  await doRun(row.id)
}

watch(
  () => props.channelType,
  () => {
    void reload()
  },
)

onMounted(reload)
</script>

<template>
  <div v-loading="loading" class="channel-task-panel">
    <PageCard>
      <template #header>
        <div class="wiz-head">
          <div>
            <div class="wiz-title">{{ title }}</div>
            <div class="wiz-sub">{{ subtitle || '主界面展示已创建的上传/接入任务；新建请点右上角' }}</div>
          </div>
          <el-button type="primary" @click="openCreate">新建上传任务</el-button>
        </div>
      </template>

      <el-table :data="channels" stripe size="small" empty-text="暂无上传任务，请点击右上角新建">
        <el-table-column prop="channelName" label="任务名称" min-width="160" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column prop="channelCode" label="编码" width="160" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center" header-align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastRunAt" label="最近执行" width="170" align="center" header-align="center">
          <template #default="{ row }">{{ row.lastRunAt || '—' }}</template>
        </el-table-column>
        <el-table-column prop="lastMessage" label="执行信息" min-width="200" align="center" header-align="center" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right" align="center" header-align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" :loading="runBusy" @click="runRow(row)">执行</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="120px">
        <el-form-item label="任务名称" required>
          <el-input v-model="channelName" placeholder="如：本地目录日增量上传" maxlength="80" />
        </el-form-item>
        <el-form-item v-for="f in configFields" :key="f.key" :label="f.label">
          <el-input v-model="channelForm[f.key]" :placeholder="f.hint || ''" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :loading="saveBusy" @click="saveTask(false)">保存</el-button>
        <el-button type="primary" :loading="saveBusy || runBusy" @click="saveTask(true)">保存并执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wiz-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.wiz-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  line-height: 1.4;
}
.wiz-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>
