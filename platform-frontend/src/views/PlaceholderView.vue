<script setup lang="ts">
import { useRoute } from 'vue-router'
import EmptyState from '@/components/common/EmptyState.vue'

const route = useRoute()
</script>

<template>
  <EmptyState
    title="功能建设中"
    :description="`路径 ${route.path} 已挂载菜单，业务实现将在后续里程碑交付。`"
    tag="MS2+ 规划中"
  />
</template>
