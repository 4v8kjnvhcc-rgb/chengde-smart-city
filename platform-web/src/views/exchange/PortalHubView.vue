<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/** 旧 URL → 独立门户页 */
const route = useRoute()
const router = useRouter()

onMounted(() => {
  const tab = String(route.query.tab || '').toLowerCase()
  const board = String(route.query.board || '').toLowerCase()
  const system = String(route.query.system || '').toLowerCase()
  if (board === 'decision' || tab === 'situation' || tab === 'm036' || system === 'cockpit') {
    router.replace({ path: '/exchange/analysis-portal/leader' })
    return
  }
  let section = 'home'
  if (['subscribe', 'm035', '035'].includes(tab)) section = 'subscribe'
  else if (['search', 'catalog', 'm033', 'm034'].includes(tab)) section = 'catalog'
  else if (['myspace', 'objection', 'manifest'].includes(tab)) section = 'myspace'
  else if (['home', 'm032'].includes(tab) || !tab) section = 'home'
  router.replace({
    path: '/exchange/analysis-portal/dept',
    query: { section },
  })
})
</script>

<template>
  <div class="redirect-hint">正在跳转到独立门户…</div>
</template>

<style scoped>
.redirect-hint {
  padding: 48px;
  text-align: center;
  color: #909399;
}
</style>
