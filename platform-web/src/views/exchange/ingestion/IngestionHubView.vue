<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import HubSideLayout from '@/components/common/HubSideLayout.vue'
import {
  collectNavItems,
  moduleTitle,
  registerNavItems,
  resolveIngestionNav,
  systemTitle,
  type IngestionSystem,
} from './ingestion-nav'

const route = useRoute()
const router = useRouter()

const system = ref<IngestionSystem>('register')
const module = ref('m039')

const moduleComponents: Record<string, ReturnType<typeof defineAsyncComponent>> = {
  m039: defineAsyncComponent(() => import('./register/GuideView.vue')),
  m040: defineAsyncComponent(() => import('./register/ProjectSystemView.vue')),
  m041: defineAsyncComponent(() => import('./register/DataSourceModelView.vue')),
  m042: defineAsyncComponent(() => import('./register/DictRegisterView.vue')),
  m043: defineAsyncComponent(() => import('./register/TagRegisterView.vue')),
  m044: defineAsyncComponent(() => import('./register/DataItemView.vue')),
  m045: defineAsyncComponent(() => import('./register/TagManageView.vue')),
  m046: defineAsyncComponent(() => import('./register/AssetReportView.vue')),
  m047: defineAsyncComponent(() => import('./register/LineageView.vue')),
  m048: defineAsyncComponent(() => import('./register/SystemLinkView.vue')),
  m049: defineAsyncComponent(() => import('./register/SystemLinkView.vue')),
  m050: defineAsyncComponent(() => import('./register/DictRegisterView.vue')),
  upload: defineAsyncComponent(() => import('./collect/CollectIngestView.vue')),
  ingest: defineAsyncComponent(() => import('./collect/CollectIngestView.vue')),
  pipeline: defineAsyncComponent(() => import('./collect/CollectPipelineView.vue')),
  catalog: defineAsyncComponent(() => import('./collect/CollectCatalogView.vue')),
}

const navItems = computed(() => {
  if (system.value === 'register') return registerNavItems()
  return collectNavItems()
})
const activeComponent = computed(() => moduleComponents[module.value])
const pageTitle = computed(() => `大数据归集平台 · ${systemTitle(system.value)} · ${moduleTitle(module.value)}`)

function syncFromRoute() {
  const legacyTab = String(route.query.tab || '').toLowerCase()
  if (legacyTab === 'stats' || legacyTab === 'm037' || legacyTab === 'm038' || legacyTab === 'stats-base' || legacyTab === 'stats-domain') {
    const sys = legacyTab === 'stats-domain' || legacyTab === 'm038' ? 'domain-stats' : 'base-stats'
    router.replace({ path: '/exchange/application', query: { system: sys } })
    return
  }
  const resolved = resolveIngestionNav(route.query as Record<string, unknown>)
  system.value = resolved.system
  module.value = resolved.module
}

function pushQuery() {
  const q: Record<string, string> = { system: system.value, module: module.value }
  router.replace({ query: { ...route.query, ...q, tab: undefined } })
}

function onSystemChange(next: IngestionSystem) {
  system.value = next
  module.value = next === 'register' ? 'm039' : 'ingest'
  pushQuery()
}

function onModuleChange(key: string) {
  module.value = key
  pushQuery()
}

watch(() => [route.query.system, route.query.module, route.query.tab], syncFromRoute)
onMounted(syncFromRoute)
</script>

<template>
  <div class="ingestion-hub">
    <PageHeader :title="pageTitle" description="数据资产登记管理与数据资源采集汇聚">
      <button type="button" class="hub-back" @click="router.push('/dashboard')">
        返回总览
      </button>
    </PageHeader>
    <div class="ingestion-system-bar">
      <el-radio-group :model-value="system" @change="onSystemChange">
        <el-radio-button value="register">数据资产登记管理</el-radio-button>
        <el-radio-button value="collect">数据资源采集汇聚</el-radio-button>
      </el-radio-group>
      <el-button link type="primary" @click="router.push('/governance?tab=quality')">数据质量中心</el-button>
    </div>
    <HubSideLayout :model-value="module" :items="navItems" @update:model-value="onModuleChange">
      <keep-alive :max="12">
        <component :is="activeComponent" :key="module" :module="module" />
      </keep-alive>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.ingestion-hub :deep(.page-header__title) {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  letter-spacing: 0.02em;
}
.ingestion-hub :deep(.page-header__desc) {
  font-size: 13px;
  color: #6b7280;
}
.hub-back {
  appearance: none;
  border: 1px solid #c5d4eb;
  background: #f5f8fd;
  color: #1d4f91;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.02em;
  padding: 0 14px;
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
  transition: color 150ms ease, background 150ms ease, border-color 150ms ease;
}
.hub-back:hover {
  color: #0d47a1;
  background: #e8f0fb;
  border-color: #9bb8e0;
}
.ingestion-system-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
