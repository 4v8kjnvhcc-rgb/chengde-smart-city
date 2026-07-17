<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/** 规格书：应用分析门户并入应用平台；旧 URL 友好跳转 */
const route = useRoute()
const router = useRouter()

onMounted(() => {
  const tab = String(route.query.tab || '').toLowerCase()
  const board = String(route.query.board || '').toLowerCase()
  if (board === 'decision' || tab === 'situation' || tab === 'm036') {
    router.replace({ path: '/exchange/application', query: { system: 'cockpit', module: 'cockpit' } })
    return
  }
  let section = 'catalog'
  if (['subscribe', 'm035', '035'].includes(tab)) section = 'subscribe'
  else if (['search', 'home', 'catalog', 'm032', 'm033', 'm034'].includes(tab) || !tab) section = 'catalog'
  router.replace({
    path: '/exchange/application',
    query: { system: 'portal', module: 'portal-home', section },
  })
})
</script>

<template>
  <div class="redirect-hint">正在跳转到应用平台 · 数据共享门户…</div>
</template>

<style scoped>
.redirect-hint {
  padding: 48px;
  text-align: center;
  color: #909399;
}
</style>
