<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import GovernanceTaskListView from './GovernanceTaskListView.vue'
import GovernanceComponentsView from './GovernanceComponentsView.vue'

/** 画布依赖 @vue-flow，必须按需异步加载，避免 Hub/列表被 Outdated Optimize Dep 拖垮 */
const GovernanceTaskDesignView = defineAsyncComponent(() => import('./GovernanceTaskDesignView.vue'))
const GovernanceTaskMonitorView = defineAsyncComponent(() => import('./GovernanceTaskMonitorView.vue'))

const props = defineProps<{
  sub: string
  view: string
  taskId: number | null
}>()

const emit = defineEmits<{
  design: [id: number]
  monitor: [id: number]
}>()

const showDesign = computed(() => props.view === 'design' && props.taskId != null)
const showTaskMonitor = computed(() => props.view === 'monitor')
const showMonitorOnly = computed(() => !showDesign.value && !showTaskMonitor.value && props.sub === 'etl-monitor')
const showComponents = computed(() => !showDesign.value && !showTaskMonitor.value && props.sub === 'components')
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
    <GovernanceTaskDesignView v-if="showDesign" :task-id="taskId!" />
    <GovernanceTaskMonitorView v-else-if="showTaskMonitor" :task-id="taskId || undefined" />
    <GovernanceTaskMonitorView v-else-if="showMonitorOnly" />
    <GovernanceComponentsView v-else-if="showComponents" />
    <GovernanceTaskListView
      v-else-if="showList && listMode"
      :mode="listMode"
      @design="emit('design', $event)"
      @monitor="emit('monitor', $event)"
    />
    <el-empty v-else description="请选择左侧数据治理功能" />
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
