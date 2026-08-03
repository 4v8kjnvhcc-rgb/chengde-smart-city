<script setup lang="ts">
import { onMounted } from 'vue'
import api from '@/api/http'
import SystemWatermark from '@/components/common/SystemWatermark.vue'
import { loadAndApplyPublicAppearance } from '@/utils/appearance'

onMounted(() => {
  void loadAndApplyPublicAppearance(() => api.get('/system/appearance/public'))
})
</script>

<template>
  <router-view />
  <SystemWatermark />
</template>
