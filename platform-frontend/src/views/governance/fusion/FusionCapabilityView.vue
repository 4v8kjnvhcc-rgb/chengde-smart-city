<script setup lang="ts">
/**
 * V3.0「数据融合处理」子能力工作台：侧栏点哪一项，这里直接打开对应已实现页面（非空说明页）。
 */
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'
import GovernanceEtlPanel from '../etl/GovernanceEtlPanel.vue'

const FusionScriptView = defineAsyncComponent(() => import('./FusionScriptView.vue'))
const FusionVersionView = defineAsyncComponent(() => import('./FusionVersionView.vue'))
const GovernanceComponentsView = defineAsyncComponent(() => import('../etl/GovernanceComponentsView.vue'))
const DirectShareGoldenPathView = defineAsyncComponent(() => import('../DirectShareGoldenPathView.vue'))
const ProcessedShareGoldenPathView = defineAsyncComponent(() => import('../ProcessedShareGoldenPathView.vue'))

const props = defineProps<{
  /** script | clean | schedule | execute | version | direct-share | processed-share */
  capability: string
}>()

const emit = defineEmits<{
  design: [id: number]
  monitor: [id: number]
}>()

const route = useRoute()
const router = useRouter()

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
    mapsTo: '数据治理「任务运行」；工程验收另含直通共享 / 加工共享',
  },
  version: {
    title: '版本管理',
    mapsTo: '脚本与工作流版本发布/回滚、锁定协同、开发/生产隔离、一键发布到生产调度',
  },
  'direct-share': {
    title: '直通共享（场景）',
    mapsTo: '黄金路径：源表质量 → 编目发布（挂靠任务执行验收）',
  },
  'processed-share': {
    title: '加工共享（场景）',
    mapsTo: '黄金路径：加工落库 → 产出质量 → 编目（挂靠任务执行验收）',
  },
}

const meta = computed(() => META[props.capability] || META.script)

const execScene = ref<'run' | 'direct-share' | 'processed-share'>('run')

watch(
  () => props.capability,
  (c) => {
    if (c === 'direct-share' || c === 'processed-share') execScene.value = c
    else if (c === 'execute') execScene.value = 'run'
  },
  { immediate: true },
)

const etlSub = computed(() => {
  if (props.capability === 'schedule') return 'task-schedule'
  if (props.capability === 'execute' && execScene.value === 'run') return 'task-run'
  if (props.capability === 'clean') return 'task-mgmt'
  return 'task-mgmt'
})

const showEtl = computed(() =>
  props.capability === 'clean'
  || props.capability === 'schedule'
  || (props.capability === 'execute' && execScene.value === 'run'),
)

function goSibling(mSub: string) {
  router.replace({
    query: { ...route.query, tab: 'model', mSub },
  })
}
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

      <!-- 任务执行 + 场景 -->
      <template v-else-if="capability === 'execute' || capability === 'direct-share' || capability === 'processed-share'">
        <el-radio-group v-model="execScene" size="small" style="margin-bottom: 12px" @change="(v: string | number | boolean) => goSibling(String(v) === 'run' ? 'execute' : String(v))">
          <el-radio-button value="run">任务运行</el-radio-button>
          <el-radio-button value="direct-share">直通共享</el-radio-button>
          <el-radio-button value="processed-share">加工共享</el-radio-button>
        </el-radio-group>
        <GovernanceEtlPanel
          v-if="execScene === 'run'"
          :sub="etlSub"
          :view="String(route.query.etlView || 'list')"
          :task-id="Number(route.query.taskId || 0) || null"
          @design="emit('design', $event)"
          @monitor="emit('monitor', $event)"
        />
        <DirectShareGoldenPathView v-else-if="execScene === 'direct-share'" />
        <ProcessedShareGoldenPathView v-else-if="execScene === 'processed-share'" />
      </template>

      <el-empty v-else-if="!showEtl" description="未知子能力" />
    </PageCard>
  </div>
</template>
