<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout from '@/components/common/HubSideLayout.vue'
import SystemMaintenanceView from '@/views/system/maintenance/SystemMaintenanceView.vue'
import SystemMailConfigView from '@/views/system/maintenance/SystemMailConfigView.vue'
import SecurityConfig from '@/views/system/SecurityConfig.vue'

const navItems = [
  { key: 'appearance', label: '外观' },
  { key: 'mail', label: '系统邮箱' },
  { key: 'security', label: '等保安全配置' },
]

const route = useRoute()
const router = useRouter()
const pane = ref('appearance')

function resolvePane() {
  const p = String(route.query.pane || 'appearance').toLowerCase()
  pane.value = navItems.some((n) => n.key === p) ? p : 'appearance'
}

watch(pane, async (p) => {
  await router.replace({ path: '/system/maintenance', query: { pane: p } })
})
watch(() => route.query.pane, () => resolvePane())

onMounted(() => resolvePane())
</script>

<template>
  <div class="maint-hub-root">
    <HubSideLayout v-model="pane" :items="navItems">
      <PageCard v-if="pane === 'appearance'" title="外观">
        <SystemMaintenanceView embed />
      </PageCard>
      <PageCard v-else-if="pane === 'mail'" title="系统邮箱">
        <SystemMailConfigView embed />
      </PageCard>
      <PageCard v-else-if="pane === 'security'" title="等保安全配置">
        <SecurityConfig embed />
      </PageCard>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.maint-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
</style>
