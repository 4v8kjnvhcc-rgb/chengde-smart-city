<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import api from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { AppearancePublic } from '@/utils/appearance'

const auth = useAuthStore()
const cfg = ref<AppearancePublic | null>(null)

async function load() {
  try {
    const data = (await api.get('/system/appearance/public')).data as AppearancePublic
    cfg.value = data
  } catch {
    cfg.value = null
  }
}

onMounted(load)

watch(
  () => auth.accessToken,
  () => {
    void load()
  },
)

const text = computed(() => {
  if (!cfg.value?.watermarkEnabled) return ''
  const parts: string[] = []
  if (cfg.value.watermarkText) parts.push(cfg.value.watermarkText)
  if (cfg.value.watermarkShowUsername && auth.user?.username) {
    parts.push(auth.user.username)
  }
  return parts.join(' · ')
})

const tiles = computed(() => {
  if (!text.value) return []
  return Array.from({ length: 24 }, (_, i) => i)
})
</script>

<template>
  <div v-if="text" class="sys-watermark" aria-hidden="true">
    <span v-for="i in tiles" :key="i" class="sys-watermark__item">{{ text }}</span>
  </div>
</template>

<style scoped>
.sys-watermark {
  pointer-events: none;
  position: fixed;
  inset: 0;
  z-index: 9999;
  overflow: hidden;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 80px 40px;
  padding: 40px;
  opacity: 0.12;
}
.sys-watermark__item {
  transform: rotate(-24deg);
  font-size: 16px;
  color: #000;
  white-space: nowrap;
  user-select: none;
}
</style>
