<script setup lang="ts">
import type { MenuNode } from '@/stores/auth'
import SidebarMenuItem from './SidebarMenuItem.vue'

defineProps<{
  menus: MenuNode[]
}>()
</script>

<template>
  <el-menu
    class="portal-sidebar-menu"
    :default-active="$route.path"
    router
    background-color="transparent"
    text-color="rgba(255,255,255,0.75)"
    active-text-color="#ffffff"
  >
    <SidebarMenuItem v-for="node in menus" :key="node.id" :node="node" />
  </el-menu>
</template>
