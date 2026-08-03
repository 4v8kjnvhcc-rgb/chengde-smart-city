<script setup lang="ts">
import type { HubNavItem } from './hub-nav'

defineOptions({ name: 'HubNavNodes' })

defineProps<{
  items: HubNavItem[]
}>()
</script>

<template>
  <template v-for="item in items" :key="item.key">
    <el-sub-menu v-if="item.children?.length" :index="item.key">
      <template #title>
        <span class="hub-side-label hub-side-label--single">{{ item.label }}</span>
      </template>
      <!-- 递归：支持 V3.0 三级目录（如 数据融合处理 → 脚本开发） -->
      <HubNavNodes :items="item.children" />
    </el-sub-menu>
    <el-menu-item v-else :index="item.key">
      <el-tooltip v-if="item.subLabel" :content="item.subLabel" placement="right" :show-after="300">
        <span class="hub-side-label hub-side-label--single">{{ item.label }}</span>
      </el-tooltip>
      <span v-else class="hub-side-label hub-side-label--single">{{ item.label }}</span>
    </el-menu-item>
  </template>
</template>
