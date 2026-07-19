<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { useAuthStore } from '@/stores/auth'
import {
  GOVERNANCE_COMPONENTS,
  GROUP_LABELS,
  type CompGroup,
  defaultEnabledTypes,
  loadEnabledTypes,
  saveEnabledTypes,
} from './governance-components'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
/** 与画布共用同一存储键，避免启用后左侧工具栏读不到 */
const username = computed(() =>
  String(auth.user?.username || localStorage.getItem('username') || 'system').trim() || 'system',
)
const enabled = ref<string[]>([])

const groups = computed(() => {
  const order: CompGroup[] = ['io', 'govern', 'transform', 'extend']
  return order.map(g => ({
    key: g,
    label: GROUP_LABELS[g],
    items: GOVERNANCE_COMPONENTS.filter(c => c.group === g),
  }))
})

function isEnabled(type: string) {
  return enabled.value.includes(type)
}

function toggle(type: string, on: boolean, required?: boolean) {
  if (required) {
    ElMessage.warning('输入/输出为画布必备组件，不可关闭')
    return
  }
  const set = new Set(enabled.value)
  if (on) set.add(type)
  else set.delete(type)
  enabled.value = Array.from(set)
  saveEnabledTypes(enabled.value, username.value)
  ElMessage.success(on ? '已启用到画布' : '已从画布移除')
}

function resetDefault() {
  enabled.value = defaultEnabledTypes()
  saveEnabledTypes(enabled.value, username.value)
  ElMessage.success('已恢复默认启用集')
}

function enableAll() {
  enabled.value = GOVERNANCE_COMPONENTS.map(c => c.type)
  saveEnabledTypes(enabled.value, username.value)
  ElMessage.success('已全部启用')
}

function goDesign() {
  const q: Record<string, unknown> = { ...route.query, tab: 'etl', etlSub: 'task-mgmt' }
  delete q.etlView
  delete q.taskId
  router.replace({ query: q as Record<string, string> })
}

onMounted(() => {
  enabled.value = loadEnabledTypes(username.value)
})
</script>

<template>
  <!-- 外层承接高度：工具栏固定，仅列表滚动（PageCard 根节点不受父级 scoped 样式控制） -->
  <div class="comp-page">
    <PageCard title="数据治理组件">
      <div class="comp-toolbar">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="goDesign">前往任务管理</el-button>
            <el-button @click="resetDefault">恢复默认</el-button>
            <el-button @click="enableAll">全部启用</el-button>
          </el-form-item>
        </el-form>
        <p class="hint">勾选「启用到画布」后，对应组件会出现在可视化开发左侧工具栏。执行统一由 Kettle Carte 完成。</p>
      </div>

      <div class="comp-list">
        <div v-for="g in groups" :key="g.key" class="group-block">
          <h3 class="group-title">{{ g.label }}</h3>
          <el-row :gutter="16">
            <el-col v-for="c in g.items" :key="c.type" :xs="24" :sm="12" :lg="8" style="margin-bottom: 16px">
              <el-card shadow="hover" class="comp-card" :class="{ 'is-on': isEnabled(c.type) }">
                <div class="comp-head">
                  <span class="comp-dot" :style="{ background: c.color }" />
                  <strong>{{ c.name }}</strong>
                  <el-tag size="small" type="success" style="margin-left: 6px">可用</el-tag>
                </div>
                <p class="comp-summary">{{ c.summary }}</p>
                <p class="comp-kettle">Kettle：{{ c.kettleStep }}</p>
                <ul v-if="c.points?.length" class="comp-points">
                  <li v-for="(p, i) in c.points" :key="i">{{ p }}</li>
                </ul>
                <div class="comp-actions">
                  <span>启用到画布</span>
                  <el-switch
                    :model-value="isEnabled(c.type)"
                    :disabled="!!c.required"
                    @change="(v: boolean) => toggle(c.type, v, c.required)"
                  />
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.comp-page {
  height: 100%;
  min-height: 0;
  /* 兜底：主区未传满高时仍限制在视口内，避免整页滚动把工具栏带走 */
  max-height: calc(100vh - var(--portal-header-height) - 136px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.comp-page :deep(.portal-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.comp-page :deep(.el-card__header) {
  flex-shrink: 0;
}
.comp-page :deep(.el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding-top: 12px;
}
.comp-toolbar {
  flex-shrink: 0;
  background: var(--el-bg-color, #fff);
  padding-bottom: 8px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.comp-list {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 4px;
}
.hint {
  color: #909399;
  font-size: 13px;
  margin: 0;
}
.group-title {
  font-size: 15px;
  margin: 8px 0 12px;
}
.comp-card {
  height: 100%;
}
.comp-card.is-on {
  border-color: var(--el-color-primary-light-5);
}
.comp-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.comp-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.comp-summary {
  font-size: 13px;
  color: #606266;
  margin: 0 0 6px;
  min-height: 36px;
}
.comp-kettle {
  font-size: 12px;
  color: #909399;
  margin: 0 0 8px;
}
.comp-points {
  margin: 0 0 10px;
  padding-left: 18px;
  font-size: 12px;
  color: #606266;
}
.comp-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 10px;
}
</style>
