<script setup lang="ts">
import { Box } from '@element-plus/icons-vue'

defineProps<{
  title?: string
  description?: string
  tag?: string
}>()
</script>

<template>
  <div class="empty-state">
    <el-icon class="empty-state__icon" :size="56"><Box /></el-icon>
    <h3 class="empty-state__title">{{ title || '功能建设中' }}</h3>
    <p v-if="description" class="empty-state__desc">{{ description }}</p>
    <el-tag v-if="tag" type="info" class="empty-state__tag">{{ tag }}</el-tag>
    <slot />
  </div>
</template>

<style scoped>
.empty-state {
  padding: 48px 24px;
  text-align: center;
  background: var(--portal-card-bg);
  border-radius: var(--portal-radius);
  border: 1px dashed var(--portal-border);
}
.empty-state__icon {
  color: #c0c4cc;
  margin-bottom: 16px;
}
.empty-state__title {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--portal-text);
}
.empty-state__desc {
  margin: 0;
  font-size: 14px;
  color: var(--portal-text-secondary);
  max-width: 480px;
  margin-inline: auto;
}
.empty-state__tag {
  margin-top: 16px;
}
</style>
