<script setup lang="ts">
defineProps<{
  title?: string
}>()
</script>

<template>
  <el-card class="portal-card" shadow="never">
    <template v-if="title || $slots.header" #header>
      <slot name="header">{{ title }}</slot>
    </template>
    <slot />
  </el-card>
</template>
