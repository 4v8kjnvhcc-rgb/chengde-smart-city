<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  APPLICATION_SYSTEMS,
  PORTAL_TABS,
  resolveApplicationNav,
  type ApplicationSystem,
} from './application-nav'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const system = ref<ApplicationSystem>('portal')
const module = ref('portal-home')
const portalSection = ref('home')

const moduleComponents: Record<string, ReturnType<typeof defineAsyncComponent>> = {
  'portal-home': defineAsyncComponent(() => import('./PortalHomeView.vue')),
  stats: defineAsyncComponent(() => import('./StatsAnalysisView.vue')),
  cockpit: defineAsyncComponent(() => import('./CockpitView.vue')),
}

const visibleSystems = computed(() =>
  APPLICATION_SYSTEMS.filter((s) => !s.permission || auth.hasPermission(s.permission) || auth.permissions.length === 0),
)

const visiblePortalTabs = computed(() =>
  PORTAL_TABS.filter((t) => {
    const perm = 'permission' in t ? (t as { permission?: string }).permission : undefined
    if (!perm) return true
    if (auth.permissions.length === 0) return true
    return auth.hasPermission(perm)
  }),
)

const activeComponent = computed(() => moduleComponents[module.value] || moduleComponents['portal-home'])

function syncFromRoute() {
  const resolved = resolveApplicationNav(route.query as Record<string, unknown>)
  if (resolved.system === 'stats' && !auth.hasPermission('analytics:stats:view') && auth.permissions.length > 0) {
    system.value = 'portal'
    module.value = 'portal-home'
    portalSection.value = 'home'
    return
  }
  if (resolved.system === 'cockpit' && !auth.hasPermission('analytics:cockpit:view') && auth.permissions.length > 0) {
    system.value = 'portal'
    module.value = 'portal-home'
    portalSection.value = 'home'
    return
  }
  system.value = resolved.system
  module.value = resolved.module
  if (resolved.system === 'portal') {
    portalSection.value = resolved.section || 'home'
  }
}

function goPortalSection(section: string) {
  system.value = 'portal'
  module.value = 'portal-home'
  portalSection.value = section
  router.replace({
    query: {
      system: 'portal',
      module: 'portal-home',
      section,
    },
  })
}

function onSystemChange(next: ApplicationSystem) {
  system.value = next
  const q: Record<string, string> = { system: next }
  if (next === 'portal') {
    module.value = 'portal-home'
    portalSection.value = 'home'
    q.module = 'portal-home'
    q.section = 'home'
  } else if (next === 'stats') {
    module.value = 'stats'
    q.module = 'stats'
    q.section = 'base'
  } else {
    module.value = 'cockpit'
    q.module = 'cockpit'
  }
  router.replace({ query: q })
}

watch(() => [route.query.system, route.query.module, route.query.tab, route.query.section], syncFromRoute)
onMounted(syncFromRoute)
</script>

<template>
  <div class="share-shell">
    <header class="share-topnav">
      <div class="share-topnav__inner">
        <button type="button" class="share-brand" @click="goPortalSection('home')">
          <span class="share-brand__mark" />
          <span class="share-brand__text">承德市数据共享门户</span>
        </button>

        <nav class="share-topnav__links" aria-label="门户导航">
          <button
            v-for="t in visiblePortalTabs"
            :key="t.key"
            type="button"
            class="share-link"
            :class="{ 'is-active': system === 'portal' && portalSection === t.key }"
            @click="goPortalSection(t.key)"
          >
            {{ t.label }}
          </button>
        </nav>

        <nav class="share-topnav__apps" aria-label="应用切换">
          <button
            v-for="s in visibleSystems.filter((x) => x.key !== 'portal')"
            :key="s.key"
            type="button"
            class="share-link share-link--app"
            :class="{ 'is-active': system === s.key }"
            @click="onSystemChange(s.key)"
          >
            {{ s.label }}
          </button>
        </nav>

        <button type="button" class="share-back" @click="router.push('/dashboard')">
          返回总览
        </button>
      </div>
    </header>

    <main class="share-main" :class="{ 'share-main--flush': system === 'portal' }">
      <keep-alive :max="6">
        <component :is="activeComponent" :key="system" mode="front" />
      </keep-alive>
    </main>
  </div>
</template>

<style scoped>
.share-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  margin: 0;
  background: #f3f6fb;
}

.share-topnav {
  background: linear-gradient(90deg, #0b3a7a 0%, #1251a8 45%, #0d47a1 100%);
  color: #fff;
  box-shadow: 0 2px 12px rgba(11, 58, 122, 0.28);
  position: sticky;
  top: 0;
  z-index: 20;
}

.share-topnav__inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 24px;
  height: 56px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.share-brand {
  appearance: none;
  border: 0;
  background: transparent;
  color: #fff;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 0;
  flex-shrink: 0;
}

.share-brand__mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background:
    radial-gradient(circle at 30% 30%, #7ec8ff, transparent 50%),
    linear-gradient(135deg, #4fc3f7, #1565c0);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.35);
}

.share-brand__text {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #fff;
}

.share-topnav__links {
  display: flex;
  align-items: stretch;
  gap: 2px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
}

.share-topnav__apps {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.share-link {
  appearance: none;
  border: 0;
  background: transparent;
  color: rgba(232, 244, 255, 0.88);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.02em;
  padding: 0 14px;
  height: 56px;
  cursor: pointer;
  white-space: nowrap;
  position: relative;
  transition: color 150ms ease, background 150ms ease;
}

.share-link:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
}

.share-link.is-active {
  color: #fff;
  font-weight: 600;
}

.share-link.is-active::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: #ffc107;
}

.share-link--app {
  color: rgba(200, 225, 255, 0.78);
  font-size: 13px;
}

.share-back {
  appearance: none;
  border: 1px solid rgba(255, 255, 255, 0.35);
  background: rgba(255, 255, 255, 0.08);
  color: #e8f4ff;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.02em;
  padding: 0 14px;
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
  flex-shrink: 0;
  margin-left: 8px;
  transition: color 150ms ease, background 150ms ease, border-color 150ms ease;
}

.share-back:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.55);
}

.share-main {
  flex: 1;
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  padding: 16px 20px 32px;
  box-sizing: border-box;
}

.share-main--flush {
  padding-top: 0;
  max-width: none;
  padding-left: 0;
  padding-right: 0;
}

@media (max-width: 900px) {
  .share-topnav__inner {
    flex-wrap: wrap;
    height: auto;
    padding: 8px 12px;
    row-gap: 4px;
  }
  .share-link {
    height: 40px;
  }
  .share-topnav__apps {
    width: auto;
  }
  .share-back {
    margin-left: auto;
  }
}
</style>
