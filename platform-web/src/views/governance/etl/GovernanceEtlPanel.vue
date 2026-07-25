<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import GovernanceTaskListView from './GovernanceTaskListView.vue'
import GovernanceComponentsView from './GovernanceComponentsView.vue'

const GovernanceTaskDesignView = defineAsyncComponent(() => import('./GovernanceTaskDesignView.vue'))
const GovernanceTaskMonitorView = defineAsyncComponent(() => import('./GovernanceTaskMonitorView.vue'))

const props = withDefaults(defineProps<{
  sub: string
  view: string
  taskId: number | null
  /** GOVERNANCE=ODS→DWD；FUSION=DWD→DWS/ADS；默认治理 */
  taskDomain?: 'GOVERNANCE' | 'FUSION'
}>(), {
  taskDomain: 'GOVERNANCE',
})

const emit = defineEmits<{
  design: [id: number]
  monitor: [id: number]
}>()

const showDesign = computed(() => props.view === 'design' && props.taskId != null)
const showTaskMonitor = computed(() => props.view === 'monitor')
const showMonitorOnly = computed(() => !showDesign.value && !showTaskMonitor.value && props.sub === 'etl-monitor')
const showComponents = computed(() =>
  !showDesign.value && !showTaskMonitor.value && props.sub === 'components' && props.taskDomain === 'GOVERNANCE')
const listMode = computed<'mgmt' | 'run' | 'schedule' | null>(() => {
  if (showDesign.value || showTaskMonitor.value || showMonitorOnly.value || showComponents.value) return null
  if (props.sub === 'task-run') return 'run'
  if (props.sub === 'task-schedule') return 'schedule'
  if (props.sub === 'task-mgmt') return 'mgmt'
  return 'mgmt'
})
const showList = computed(() => listMode.value != null)
</script>

<template>
  <div class="gov-etl-panel" :class="{ 'gov-etl-panel--fill': showComponents }">
    <GovernanceTaskDesignView v-if="showDesign" :task-id="taskId!" :task-domain="taskDomain" />
    <GovernanceTaskMonitorView
      v-else-if="showTaskMonitor"
      :task-id="taskId || undefined"
      :task-domain="taskDomain"
    />
    <GovernanceTaskMonitorView v-else-if="showMonitorOnly" :task-domain="taskDomain" />
    <GovernanceComponentsView v-else-if="showComponents" />
    <GovernanceTaskListView
      v-else-if="showList && listMode"
      :mode="listMode"
      :task-domain="taskDomain"
      @design="emit('design', $event)"
      @monitor="emit('monitor', $event)"
    />
    <el-empty v-else :description="taskDomain === 'FUSION' ? '请选择融合加工功能' : '请选择左侧数据治理功能'" />
  </div>
</template>

<style scoped>
.gov-etl-panel--fill {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.gov-etl-panel--fill > :deep(*) {
  flex: 1;
  min-height: 0;
}
</style>
