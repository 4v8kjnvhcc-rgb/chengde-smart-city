<script setup lang="ts">
/**
 * V3.0「数据融合处理」子能力工作台：侧栏点哪一项，这里直接打开对应已实现页面（非空说明页）。
 */
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'
import GovernanceEtlPanel from '../etl/GovernanceEtlPanel.vue'

const FusionScriptView = defineAsyncComponent(() => import('./FusionScriptView.vue'))
const FusionVersionView = defineAsyncComponent(() => import('./FusionVersionView.vue'))
const GovernanceComponentsView = defineAsyncComponent(() => import('../etl/GovernanceComponentsView.vue'))

const props = defineProps<{
  /** script | clean | schedule | execute | version */
  capability: string
}>()

const emit = defineEmits<{
  design: [id: number]
  monitor: [id: number]
}>()

const route = useRoute()

const META: Record<string, { title: string; mapsTo: string }> = {
  script: {
    title: '脚本开发',
    mapsTo: '本模块「融合脚本」在线编辑 / 执行 / 发布（已实现）',
  },
  clean: {
    title: '数据清洗',
    mapsTo: '数据融合组件（过滤/脱敏等）+ 下方任务管理画布编排 → Kettle 执行',
  },
  schedule: {
    title: '工作流定时',
    mapsTo: '融合加工任务定时台账（DolphinScheduler 启停）',
  },
  workflow: {
    title: '工作流调度',
    mapsTo: '跨模块流水线：归集/治理/质量/融合步骤可增删调序，发布 DS 串行执行',
  },
  execute: {
    title: '任务执行',
    mapsTo: '融合「任务运行」与 ETL 监控',
  },
  version: {
    title: '版本管理',
    mapsTo: '脚本与工作流版本发布/回滚、锁定协同、开发/生产隔离、一键发布到生产调度',
  },
}

const meta = computed(() => META[props.capability] || META.script)

const etlSub = computed(() => {
  if (props.capability === 'schedule') return 'task-schedule'
  if (props.capability === 'execute') return 'task-run'
  if (props.capability === 'clean') return 'task-mgmt'
  return 'task-mgmt'
})

const showEtl = computed(() =>
  props.capability === 'clean'
  || props.capability === 'schedule'
  || props.capability === 'execute',
)
</script>

<template>
  <div class="fusion-cap">
    <PageCard :title="`数据融合处理 · ${meta.title}`">
      <!-- 脚本 / 版本 -->
      <FusionScriptView
        v-if="capability === 'script'"
        embedded
      />
      <FusionVersionView v-else-if="capability === 'version'" />

      <!-- 数据清洗：组件库 + 任务画布入口 -->
      <template v-else-if="capability === 'clean'">
        <el-tabs type="border-card">
          <el-tab-pane label="清洗组件（融合组件）" name="comp">
            <GovernanceComponentsView
              page-title="数据清洗 · 可用组件"
              context-hint="勾选过滤、字段处理、脱敏等组件后，在「任务画布」中拖拽编排；与「数据融合组件」同源。"
            />
          </el-tab-pane>
          <el-tab-pane label="任务画布（编排清洗）" name="canvas">
            <GovernanceEtlPanel
              :sub="etlSub"
              view="list"
              :task-id="null"
              @design="emit('design', $event)"
              @monitor="emit('monitor', $event)"
            />
          </el-tab-pane>
        </el-tabs>
      </template>

      <!-- 工作流调度 -->
      <GovernanceEtlPanel
        v-else-if="capability === 'schedule'"
        :sub="etlSub"
        :view="String(route.query.etlView || 'list')"
        :task-id="Number(route.query.taskId || 0) || null"
        @design="emit('design', $event)"
        @monitor="emit('monitor', $event)"
      />

      <!-- 任务执行 -->
      <GovernanceEtlPanel
        v-else-if="capability === 'execute'"
        :sub="etlSub"
        :view="String(route.query.etlView || 'list')"
        :task-id="Number(route.query.taskId || 0) || null"
        @design="emit('design', $event)"
        @monitor="emit('monitor', $event)"
      />

      <el-empty v-else-if="!showEtl" description="未知子能力" />
    </PageCard>
  </div>
</template>
