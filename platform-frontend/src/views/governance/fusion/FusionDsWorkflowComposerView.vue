<script setup lang="ts">
/**
 * 工作流调度1：下拉选择归集/治理/融合已有任务，预览 DS 脚本并发布；不改 DS 原生 UI。
 */
import { computed, onMounted, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

type Domain = 'INGEST' | 'GOVERNANCE' | 'FUSION'

interface TaskOption {
  id: number
  name: string
  code?: string
  domain: Domain
  scheduleCron?: string
  scheduleEnabled?: boolean
  dsProjectCode?: number
  dsDefinitionCode?: number
  label: string
}

interface Preview {
  domain: string
  taskId: number
  taskName?: string
  taskCode?: string
  definitionName?: string
  script?: string
  scheduleCron?: string
  scheduleEnabled?: boolean
  dsProjectCode?: number
  dsDefinitionCode?: number
  dsScheduleId?: number
  dsOpenUrl?: string | null
}

const domain = ref<Domain>('FUSION')
const taskId = ref<number | null>(null)
const cron = ref('')
const options = ref<TaskOption[]>([])
const optionsLoading = ref(false)
const preview = ref<Preview | null>(null)
const previewLoading = ref(false)
const acting = ref(false)
const dsHealthy = ref(false)
const uiBase = ref('')

const selected = computed(() => options.value.find((o) => o.id === taskId.value) || null)

async function loadMeta() {
  try {
    const res = await api.get('/governance/fusion/ds-composer/meta')
    dsHealthy.value = !!res.data?.healthy
    uiBase.value = (res.data?.uiBase as string) || ''
  } catch {
    dsHealthy.value = false
  }
}

async function loadOptions(keepSelection = false) {
  const prevId = keepSelection ? taskId.value : null
  optionsLoading.value = true
  if (!keepSelection) {
    taskId.value = null
    preview.value = null
    cron.value = ''
  }
  try {
    const res = await api.get('/governance/fusion/ds-composer/task-options', {
      params: { domain: domain.value },
    })
    options.value = (res.data as TaskOption[]) || []
    if (keepSelection && prevId != null && options.value.some((o) => o.id === prevId)) {
      taskId.value = prevId
    } else if (!keepSelection) {
      taskId.value = null
    }
  } catch (e: unknown) {
    options.value = []
    ElMessage.error(e instanceof Error ? e.message : '加载任务列表失败')
  } finally {
    optionsLoading.value = false
  }
}

async function loadPreview() {
  if (taskId.value == null) {
    preview.value = null
    return
  }
  previewLoading.value = true
  try {
    const res = await api.get('/governance/fusion/ds-composer/preview', {
      params: { domain: domain.value, taskId: taskId.value },
    })
    preview.value = res.data as Preview
    cron.value = (preview.value.scheduleCron as string) || cron.value || ''
  } catch (e: unknown) {
    preview.value = null
    ElMessage.error(e instanceof Error ? e.message : '加载预览失败')
  } finally {
    previewLoading.value = false
  }
}

async function publish() {
  if (taskId.value == null) {
    ElMessage.warning('请先选择任务')
    return
  }
  acting.value = true
  try {
    const res = await api.post('/governance/fusion/ds-composer/publish', {
      domain: domain.value,
      taskId: taskId.value,
      cron: cron.value || undefined,
    })
    preview.value = res.data as Preview
    cron.value = (preview.value.scheduleCron as string) || cron.value
    ElMessage.success('已发布到 DolphinScheduler')
    await loadOptions(true)
    await loadPreview()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '发布失败')
  } finally {
    acting.value = false
  }
}

async function stop() {
  if (taskId.value == null) {
    ElMessage.warning('请先选择任务')
    return
  }
  acting.value = true
  try {
    const res = await api.post('/governance/fusion/ds-composer/stop', {
      domain: domain.value,
      taskId: taskId.value,
    })
    preview.value = res.data as Preview
    ElMessage.success('已停止 DolphinScheduler 调度')
    await loadOptions(true)
    await loadPreview()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '停止失败')
  } finally {
    acting.value = false
  }
}

function openInDs() {
  const url = preview.value?.dsOpenUrl
  if (url) {
    window.open(url, '_blank')
    return
  }
  if (uiBase.value) {
    window.open(`${uiBase.value}/ui/`, '_blank')
    return
  }
  ElMessage.warning('未配置 DS_UI_BASE，无法打开 DolphinScheduler')
}

watch(domain, () => { void loadOptions() })
watch(taskId, () => { void loadPreview() })

onMounted(async () => {
  await loadMeta()
  await loadOptions()
})
</script>

<template>
  <PageCard title="数据融合处理 · 工作流调度1">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="选择系统已有任务并发布到 DolphinScheduler；工作流名称与 SHELL 回调脚本由平台自动生成（对应 DS 原生页中的标题与脚本框）。"
      description="原「工作流调度」台账入口保持不变。高级画布可在发布后「在 DS 中打开」。"
    />

    <el-alert
      v-if="!dsHealthy"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="DolphinScheduler 当前不可用"
      description="可先选择任务预览脚本；启动定时需 DS 就绪。"
    />

    <el-form label-width="110px" style="max-width: 920px">
      <el-form-item label="任务域">
        <el-select v-model="domain" style="width: 280px">
          <el-option label="归集" value="INGEST" />
          <el-option label="治理" value="GOVERNANCE" />
          <el-option label="融合" value="FUSION" />
        </el-select>
      </el-form-item>

      <el-form-item label="选择任务">
        <el-select
          v-model="taskId"
          filterable
          clearable
          :loading="optionsLoading"
          placeholder="请选择已有任务"
          style="width: 100%; max-width: 640px"
        >
          <el-option
            v-for="o in options"
            :key="o.id"
            :label="o.label"
            :value="o.id"
          >
            <span>{{ o.label }}</span>
            <el-tag
              v-if="o.scheduleEnabled"
              size="small"
              type="success"
              style="margin-left: 8px"
            >已调度</el-tag>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="执行周期">
        <el-input
          v-model="cron"
          placeholder="Cron，如 0 0 2 * * ? （发布时可写入）"
          style="max-width: 640px"
        />
      </el-form-item>

      <el-form-item label="将生成名称">
        <el-input
          :model-value="preview?.definitionName || (selected ? '选择后自动生成' : '')"
          readonly
          style="max-width: 640px"
        />
      </el-form-item>

      <el-form-item label="回调脚本">
        <el-input
          v-loading="previewLoading"
          type="textarea"
          :rows="5"
          readonly
          :model-value="preview?.script || ''"
          placeholder="选择任务后显示将写入 DS 的 SHELL 脚本（只读）"
          style="max-width: 640px; font-family: ui-monospace, monospace"
        />
      </el-form-item>

      <el-form-item label="DS 状态">
        <template v-if="preview">
          <el-tag :type="preview.scheduleEnabled ? 'success' : 'info'" style="margin-right: 8px">
            {{ preview.scheduleEnabled ? '调度已启动' : '未调度 / 已停止' }}
          </el-tag>
          <span v-if="preview.dsDefinitionCode" class="muted">
            project={{ preview.dsProjectCode }} · definition={{ preview.dsDefinitionCode }}
          </span>
          <span v-else class="muted">尚未绑定 DS 流程定义</span>
        </template>
        <span v-else class="muted">—</span>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="acting" :disabled="!taskId" @click="publish">
          发布到 DS（启动定时）
        </el-button>
        <el-button :loading="acting" :disabled="!taskId" @click="stop">停止调度</el-button>
        <el-button :disabled="!preview?.dsOpenUrl && !uiBase" @click="openInDs">在 DS 中打开</el-button>
      </el-form-item>
    </el-form>
  </PageCard>
</template>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
