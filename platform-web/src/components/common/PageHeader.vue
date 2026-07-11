<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <div class="page-header">
    <div class="page-header__main">
      <h2 class="page-header__title">{{ title }}</h2>
      <p v-if="description" class="page-header__desc">{{ description }}</p>
    </div>
    <div v-if="$slots.default" class="page-header__actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.page-header__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--portal-text);
}
.page-header__desc {
  margin: 6px 0 0;
  font-size: 14px;
  color: var(--portal-text-secondary);
}
.page-header__actions {
  flex-shrink: 0;
}
</style>
