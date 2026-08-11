<script setup lang="ts">
/**
 * 应用平台中间选卡页已取消：按 query 兼容旧链接，否则直达「数据供需对接系统」。
 * 总览侧通过飞出直达各子系统。
 */
import { onBeforeMount } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onBeforeMount(() => {
  const system = String(route.query.system || '').toLowerCase()
  if (system === 'base-stats' || system === 'stats-base' || system === 'm037' || system === 'stats') {
    router.replace('/exchange/application/stats-base')
    return
  }
  if (system === 'domain-stats' || system === 'stats-domain' || system === 'm038') {
    router.replace('/exchange/application/stats-domain')
    return
  }
  if (system === 'assessment' || system === 'm036') {
    router.replace('/exchange/application/assessment')
    return
  }
  if (system === 'supply' || system === 'm035') {
    router.replace('/exchange/application/supply')
    return
  }
  router.replace('/exchange/application/supply')
})
</script>

<template>
  <div />
</template>
