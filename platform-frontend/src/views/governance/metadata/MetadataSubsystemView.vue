<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { resolveMetaSection, type MetaSection } from './meta-nav'

const props = defineProps<{ section?: string }>()

const route = useRoute()
const router = useRouter()
const section = ref<MetaSection>('model')

const components: Record<MetaSection, ReturnType<typeof defineAsyncComponent>> = {
  model: defineAsyncComponent(() => import('./MetaModelView.vue')),
  collect: defineAsyncComponent(() => import('./MetaCollectView.vue')),
  monitor: defineAsyncComponent(() => import('./MetaMonitorView.vue')),
  maintain: defineAsyncComponent(() => import('./MetaMaintainView.vue')),
  version: defineAsyncComponent(() => import('./MetaVersionView.vue')),
  catalog: defineAsyncComponent(() => import('./MetaCatalogView.vue')),
  analyze: defineAsyncComponent(() => import('./MetaAnalyzeView.vue')),
}

const active = computed(() => components[section.value])

function syncSection() {
  section.value = resolveMetaSection(props.section ?? route.query.section)
}

watch(() => props.section, syncSection)
watch(() => route.query.section, syncSection)
onMounted(syncSection)

// 保留路由深链：外部改 section 时写回 query
watch(section, (v) => {
  if (String(route.query.section || '') === v) return
  router.replace({ query: { ...route.query, tab: 'metadata', section: v } })
})
</script>

<template>
  <keep-alive :max="7">
    <component :is="active" />
  </keep-alive>
</template>
