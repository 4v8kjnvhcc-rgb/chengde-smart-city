<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import UserAccountMenu from '@/components/layout/UserAccountMenu.vue'

defineProps<{
  title: string
  description?: string
}>()

const route = useRoute()
/** 独立系统页隐藏了全局 AppHeader 时，在页头右侧补用户菜单，避免重复 */
const showUserMenu = computed(() => Boolean((route.meta as { hideAppHeader?: boolean }).hideAppHeader))
</script>

<template>
  <div class="page-header">
    <div class="page-header__main">
      <h2 class="page-header__title">{{ title }}</h2>
      <p v-if="description" class="page-header__desc">{{ description }}</p>
    </div>
    <div v-if="$slots.default || showUserMenu" class="page-header__actions">
      <slot />
      <UserAccountMenu v-if="showUserMenu" />
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
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
