<script setup lang="ts">
/**
 * V3.0「数据融合处理」子能力入口：
 * 脚本开发 / 数据清洗 / 工作流调度 / 任务执行 / 版本管理
 * （框架能力复用数据治理 ETL + 融合脚本）
 */
import { defineAsyncComponent, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'

const FusionScriptView = defineAsyncComponent(() => import('./FusionScriptView.vue'))
const FusionVersionView = defineAsyncComponent(() => import('./FusionVersionView.vue'))

const route = useRoute()
const router = useRouter()

/** 与 V3.0 正文子弹一致 */
const VALID = [
  'script',
  'clean',
  'schedule',
  'execute',
  'version',
] as const
type ProcTab = (typeof VALID)[number]

const active = ref<ProcTab>('script')

function resolveTab(raw: unknown): ProcTab {
  const s = String(raw || 'script')
  // 旧黄金路径 Tab 已下线，落到任务执行
  if (s === 'direct-share' || s === 'processed-share' || s === 'direct' || s === 'processed') return 'execute'
  if ((VALID as readonly string[]).includes(s)) return s as ProcTab
  if (s === 'runs') return 'execute'
  return 'script'
}

watch(
  () => route.query.procTab,
  (v) => {
    active.value = resolveTab(v)
  },
  { immediate: true },
)

function onTab(name: string | number) {
  const tab = resolveTab(name)
  active.value = tab
  router.replace({
    query: {
      ...route.query,
      tab: 'model',
      mSub: 'processing',
      procTab: tab,
    },
  })
}

function goEtl(etlSub: string) {
  router.push({
    path: '/governance',
    query: { tab: 'etl', etlSub },
  })
}

function goFusionComponents() {
  router.push({
    path: '/governance',
    query: { tab: 'model', mSub: 'components' },
  })
}
</script>

<template>
  <PageCard title="数据融合处理">
    <el-tabs :model-value="active" @tab-change="onTab">
      <el-tab-pane label="脚本开发" name="script" />
      <el-tab-pane label="数据清洗" name="clean" />
      <el-tab-pane label="工作流调度" name="schedule" />
      <el-tab-pane label="任务执行" name="execute" />
      <el-tab-pane label="版本管理" name="version" />
    </el-tabs>

    <!-- 脚本开发：本模块已实现 -->
    <FusionScriptView v-if="active === 'script'" embedded />

    <!-- 版本管理：脚本 + 工作流 -->
    <div v-else-if="active === 'version'">
      <FusionVersionView />
    </div>

    <!-- 数据清洗：复用治理组件 MASK/FILTER 等 + ETL 画布 -->
    <div v-else-if="active === 'clean'" class="map-panel">
      <el-descriptions title="与已实现能力的对应" :column="1" border size="small">
        <el-descriptions-item label="V3.0 要求">预置清洗函数：脱敏、去非法字符、格式校验、标准化等</el-descriptions-item>
        <el-descriptions-item label="当前落地">数据治理 / 数据融合组件中的过滤、字段处理、脱敏（MASK）等；在 ETL 画布编排后由 Kettle 执行</el-descriptions-item>
        <el-descriptions-item label="框架">Kettle Carte（与治理共用，不另起内存清洗引擎）</el-descriptions-item>
      </el-descriptions>
      <el-space style="margin-top: 16px" wrap>
        <el-button type="primary" @click="goFusionComponents">打开数据融合组件</el-button>
        <el-button @click="goEtl('task-mgmt')">前往任务管理（画布清洗）</el-button>
        <el-button @click="goEtl('components')">数据治理组件（同源）</el-button>
      </el-space>
    </div>

    <!-- 工作流调度：复用治理任务定时 + DS（M110） -->
    <div v-else-if="active === 'schedule'" class="map-panel">
      <el-descriptions title="与已实现能力的对应" :column="1" border size="small">
        <el-descriptions-item label="V3.0 要求">可视化拖拽工作流、周期调度、依赖/优先级、监控告警</el-descriptions-item>
        <el-descriptions-item label="当前落地">数据治理「任务定时」；调度引擎按工程约束对接 DolphinScheduler（M110）</el-descriptions-item>
        <el-descriptions-item label="框架">DolphinScheduler（开源集成）+ 平台任务台账</el-descriptions-item>
      </el-descriptions>
      <el-space style="margin-top: 16px" wrap>
        <el-button type="primary" @click="goEtl('task-schedule')">打开任务定时</el-button>
        <el-button @click="goEtl('etl-monitor')">ETL 监控</el-button>
      </el-space>
    </div>

    <!-- 任务执行：复用任务运行 -->
    <div v-else-if="active === 'execute'" class="map-panel">
      <el-descriptions title="与已实现能力的对应" :column="1" border size="small">
        <el-descriptions-item label="V3.0 要求">任务提交、重跑/暂停、优先级、执行监控与日志</el-descriptions-item>
        <el-descriptions-item label="当前落地">数据治理「任务运行 / ETL监控」；融合加工入库走 Kettle/SQL 真实落库</el-descriptions-item>
      </el-descriptions>
      <el-space style="margin-top: 16px" wrap>
        <el-button type="primary" @click="goEtl('task-run')">打开任务运行</el-button>
      </el-space>
    </div>
  </PageCard>
</template>

<style scoped>
.map-panel {
  padding: 8px 0 16px;
}
</style>
