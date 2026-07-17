<script setup lang="ts">
import { computed, defineAsyncComponent } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import GovernanceTaskListView from './GovernanceTaskListView.vue'

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
const showMonitor = computed(() => props.view === 'monitor')
const showList = computed(() => {
  if (showDesign.value || showMonitor.value) return false
  return ['task-mgmt', 'task-run', 'task-schedule', 'components'].includes(props.sub)
})
const showMonitorOnly = computed(() => !showDesign.value && !showMonitor.value && props.sub === 'etl-monitor')
</script>

<template>
  <div class="gov-etl-panel">
    <GovernanceTaskDesignView v-if="showDesign" :task-id="taskId!" />
    <GovernanceTaskMonitorView v-else-if="showMonitor" :task-id="taskId || undefined" />
    <GovernanceTaskMonitorView v-else-if="showMonitorOnly" />
    <GovernanceTaskListView
      v-else-if="showList"
      @design="emit('design', $event)"
      @monitor="emit('monitor', $event)"
    />
    <PageCard v-if="showList && sub === 'components'" title="数据治理组件" style="margin-top:12px">
      <el-alert type="info" title="提示" :closable="false" show-icon>
        点击任务列表的「开发」按钮进入画布设计，可配置过滤、字段处理、去重、脱敏等治理组件。
      </el-alert>
    </PageCard>
    <el-empty v-else-if="!showList && !showDesign && !showMonitor && !showMonitorOnly" description="请选择左侧数据治理功能" />
  </div>
</template>
