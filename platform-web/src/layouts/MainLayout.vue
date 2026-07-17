<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppHeader from '@/components/layout/AppHeader.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import { resolveSidebarContext } from '@/utils/menu'

const auth = useAuthStore()
const route = useRoute()

const isHubPage = computed(() => route.path === '/dashboard' || route.path === '/')
const hideAppHeader = computed(() => Boolean((route.meta as { hideAppHeader?: boolean }).hideAppHeader))
const flushMain = computed(() => Boolean((route.meta as { flushMain?: boolean }).flushMain))
const mainClasses = computed(() => ({
  'portal-main--hub': isHubPage.value,
  'portal-main--flush-header': hideAppHeader.value && flushMain.value,
  'portal-main--no-app-header': hideAppHeader.value && !flushMain.value,
}))

const sidebarContext = computed(() =>
  resolveSidebarContext(auth.menus, route.path, route.meta as { hubLayout?: boolean }),
)

const showSidebar = computed(() => sidebarContext.value !== null)
const sidebarTitle = computed(() => sidebarContext.value?.title ?? '统一门户')
const sidebarMenus = computed(() => sidebarContext.value?.menus ?? [])
</script>

<template>
  <el-container class="portal-layout">
    <el-aside v-if="showSidebar" width="220px" class="portal-aside">
      <div class="portal-aside__brand">{{ sidebarTitle }}</div>
      <AppSidebar :menus="sidebarMenus" />
    </el-aside>
    <el-container class="portal-main-wrap" :class="{ 'portal-main-wrap--hub': isHubPage }">
      <AppHeader v-if="!hideAppHeader" :show-back-hub="!isHubPage" :hub-theme="isHubPage" />
      <el-main class="portal-main" :class="mainClasses">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.portal-layout {
  min-height: 100vh;
  height: 100vh;
}
.portal-aside {
  background: var(--portal-sidebar-bg);
  overflow-x: hidden;
}
.portal-aside__brand {
  height: var(--portal-header-height);
  line-height: var(--portal-header-height);
  padding: 0 20px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.portal-main-wrap {
  flex-direction: column;
  background: var(--portal-bg);
  flex: 1;
  min-height: 0;
}
.portal-main-wrap--hub {
  position: relative;
  overflow: hidden;
}
.portal-main-wrap--hub::before {
  content: '';
  position: absolute;
  inset: -8%;
  background: url('/images/hub-tech-bg.jpg') center / cover no-repeat;
  opacity: 1;
  z-index: 0;
  pointer-events: none;
  animation: hub-bg-drift 28s ease-in-out infinite alternate;
}
.portal-main-wrap--hub::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: linear-gradient(
    120deg,
    rgba(22, 119, 255, 0.06) 0%,
    rgba(255, 255, 255, 0) 40%,
    rgba(127, 119, 221, 0.08) 100%
  );
  background-size: 200% 200%;
  animation: hub-bg-shimmer 16s ease-in-out infinite;
}
@keyframes hub-bg-drift {
  0% {
    transform: scale(1) translate(0, 0);
  }
  100% {
    transform: scale(1.1) translate(-2.5%, -1.5%);
  }
}
@keyframes hub-bg-shimmer {
  0%,
  100% {
    background-position: 0% 50%;
    opacity: 0.5;
  }
  50% {
    background-position: 100% 50%;
    opacity: 0.85;
  }
}
@media (prefers-reduced-motion: reduce) {
  .portal-main-wrap--hub::before,
  .portal-main-wrap--hub::after {
    animation: none !important;
  }
}

.portal-main-wrap--hub :deep(.app-header),
.portal-main-wrap--hub :deep(.portal-main) {
  position: relative;
  z-index: 1;
}
.portal-main {
  padding: 20px;
}
.portal-main--flush-header {
  padding: 0;
  min-height: 100vh;
  box-sizing: border-box;
}
.portal-main--no-app-header {
  min-height: 100vh;
  box-sizing: border-box;
}
.portal-main--hub {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - var(--portal-header-height));
  box-sizing: border-box;
}
.portal-main--hub > :deep(*) {
  flex: 1;
  min-height: 0;
  width: 100%;
}
</style>
