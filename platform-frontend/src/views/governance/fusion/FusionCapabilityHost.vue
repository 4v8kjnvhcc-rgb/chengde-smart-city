<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GovernanceEtlPanel from '../etl/GovernanceEtlPanel.vue'
import CrossModulePipelinePanel from './CrossModulePipelinePanel.vue'

const FusionScriptView = defineAsyncComponent(() => import('./FusionScriptView.vue'))
const FusionVersionView = defineAsyncComponent(() => import('./FusionVersionView.vue'))
const RealtimeTaskMonitorPanel = defineAsyncComponent(() => import('./RealtimeTaskMonitorPanel.vue'))

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

const workflowTab = ref('pipeline')
watch(
  () => route.query.wfTab,
  (v) => {
    const s = String(v || 'pipeline')
    workflowTab.value = ['pipeline', 'monitor'].includes(s) ? s : 'pipeline'
  },
  { immediate: true },
)

watch(
  () => props.capability,
  () => {
    if (props.capability !== 'workflow') workflowTab.value = 'pipeline'
  },
)

function onWorkflowTab(name: string | number) {
  const tab = String(name)
  workflowTab.value = tab
  router.replace({
    query: { ...route.query, tab: 'model', mSub: 'workflow', wfTab: tab },
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
    <FusionScriptView v-if="capability === 'script'" embedded />
    <FusionVersionView v-else-if="capability === 'version'" />

    <div v-else-if="capability === 'schedule'" class="fusion-schedule">
      <GovernanceEtlPanel
        :sub="etlSub"
        :view="view"
        :task-id="taskId"
        task-domain="FUSION"
        @design="emit('design', $event)"
        @monitor="emit('monitor', $event)"
      />
    </div>

    <div v-else-if="capability === 'workflow'" class="fusion-workflow">
      <el-tabs :model-value="workflowTab" @tab-change="onWorkflowTab" style="margin-bottom: 8px">
        <el-tab-pane label="跨模块流水线" name="pipeline" />
        <el-tab-pane label="实时任务监控" name="monitor" />
      </el-tabs>
      <CrossModulePipelinePanel v-if="workflowTab === 'pipeline'" />
      <RealtimeTaskMonitorPanel v-else-if="workflowTab === 'monitor'" />
    </div>

    <GovernanceEtlPanel
      v-else-if="capability === 'clean'"
      :sub="etlSub"
      :view="view"
      :task-id="taskId"
      task-domain="FUSION"
      @design="emit('design', $event)"
      @monitor="emit('monitor', $event)"
    />

    <GovernanceEtlPanel
      v-else-if="capability === 'execute'"
      :sub="etlSub"
      :view="view"
      :task-id="taskId"
      task-domain="FUSION"
      @design="emit('design', $event)"
      @monitor="emit('monitor', $event)"
    />
  </div>
</template>

<style scoped>
.fusion-schedule,
.fusion-workflow {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
</style>
