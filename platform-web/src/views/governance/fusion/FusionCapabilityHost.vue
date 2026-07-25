<script setup lang="ts">
/**
 * V3.0「数据融合处理」子能力宿主。
 * 数据清洗 / 调度 / 执行：仅融合任务（DWD→DWS/ADS），与数据治理任务隔离；不含组件勾选页。
 */
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GovernanceEtlPanel from '../etl/GovernanceEtlPanel.vue'

const FusionScriptView = defineAsyncComponent(() => import('./FusionScriptView.vue'))
const DirectShareGoldenPathView = defineAsyncComponent(() => import('../DirectShareGoldenPathView.vue'))
const ProcessedShareGoldenPathView = defineAsyncComponent(() => import('../ProcessedShareGoldenPathView.vue'))

const props = defineProps<{
  capability: string
  etlView?: string
  etlTaskId?: number | null
}>()

const emit = defineEmits<{
  design: [id: number]
  monitor: [id: number]
}>()

const route = useRoute()
const router = useRouter()

const META: Record<string, { title: string; impl: string }> = {
  script: {
    title: '脚本开发',
    impl: '分层库脚本校验；多表成主题库请用「数据清洗」融合任务。',
  },
  clean: {
    title: '数据清洗（融合加工）',
    impl: '仅融合任务：源=DWD 过程层，目标=DWS/ADS 主题或专题。与数据治理任务（ODS→DWD）完全隔离，本页不出现组件库。',
  },
  schedule: {
    title: '工作流调度',
    impl: '仅调度融合任务（DWD→DWS/ADS）。',
  },
  execute: {
    title: '任务执行',
    impl: '加工共享落主题/专题；或运行融合任务。治理任务请到「数据治理」。',
  },
  version: {
    title: '版本管理',
    impl: '融合脚本发布/回滚。',
  },
}

const meta = computed(() => META[props.capability] || META.script)

const executeTab = ref('processed-share')
watch(
  () => route.query.execTab,
  (v) => {
    const s = String(v || 'processed-share')
    executeTab.value = ['run', 'direct-share', 'processed-share'].includes(s) ? s : 'processed-share'
  },
  { immediate: true },
)

watch(
  () => props.capability,
  () => {
    if (props.capability !== 'execute') executeTab.value = 'processed-share'
  },
)

function onExecTab(name: string | number) {
  const tab = String(name)
  executeTab.value = tab
  router.replace({
    query: { ...route.query, tab: 'model', mSub: 'execute', execTab: tab },
  })
}

const etlSub = computed(() => {
  if (props.capability === 'schedule') return 'task-schedule'
  if (props.capability === 'execute') return 'task-run'
  return 'task-mgmt'
})

const view = computed(() => props.etlView || 'list')
const taskId = computed(() => props.etlTaskId ?? null)
</script>

<template>
  <div class="fusion-cap-host">
    <el-alert
      type="success"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      :title="`V3.0「数据融合处理」→ ${meta.title}`"
      :description="meta.impl"
    />

    <FusionScriptView v-if="capability === 'script' || capability === 'version'" embedded />

    <GovernanceEtlPanel
      v-else-if="capability === 'clean' || capability === 'schedule'"
      :sub="etlSub"
      :view="view"
      :task-id="taskId"
      task-domain="FUSION"
      @design="emit('design', $event)"
      @monitor="emit('monitor', $event)"
    />

    <div v-else-if="capability === 'execute'">
      <el-tabs :model-value="executeTab" @tab-change="onExecTab" style="margin-bottom: 8px">
        <el-tab-pane label="加工共享（主题/专题落库）" name="processed-share" />
        <el-tab-pane label="直通共享（源资源）" name="direct-share" />
        <el-tab-pane label="融合任务运行" name="run" />
      </el-tabs>
      <GovernanceEtlPanel
        v-if="executeTab === 'run'"
        :sub="etlSub"
        :view="view"
        :task-id="taskId"
        task-domain="FUSION"
        @design="emit('design', $event)"
        @monitor="emit('monitor', $event)"
      />
      <DirectShareGoldenPathView v-else-if="executeTab === 'direct-share'" />
      <ProcessedShareGoldenPathView v-else-if="executeTab === 'processed-share'" />
    </div>
  </div>
</template>
