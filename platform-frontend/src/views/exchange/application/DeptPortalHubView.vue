<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { DEPT_PORTAL_BRAND, DEPT_PORTAL_TABS } from './application-nav'

const route = useRoute()
const router = useRouter()

const portalSection = ref('home')
const PortalHomeView = defineAsyncComponent(() => import('./PortalHomeView.vue'))

const validTabs = DEPT_PORTAL_TABS.map((t) => t.key)

function syncFromRoute() {
  const s = String(route.query.section || 'home')
  portalSection.value = (validTabs as readonly string[]).includes(s) ? s : 'home'
}

function goSection(section: string) {
  portalSection.value = section
  router.replace({
    path: '/exchange/analysis-portal/dept',
    query: { section },
  })
}

watch(() => route.query.section, syncFromRoute)
onMounted(syncFromRoute)

const flushMain = computed(() => portalSection.value === 'home')
</script>

<template>
  <div class="share-shell">
    <header class="share-topnav">
      <div class="share-topnav__inner">
        <button type="button" class="share-brand" @click="goSection('home')">
          <span class="share-brand__mark" />
          <span class="share-brand__text">{{ DEPT_PORTAL_BRAND }}</span>
        </button>

        <nav class="share-topnav__links" aria-label="部门门户导航">
          <button
            v-for="t in DEPT_PORTAL_TABS"
            :key="t.key"
            type="button"
            class="share-link"
            :class="{ 'is-active': portalSection === t.key }"
            @click="goSection(t.key)"
          >
            {{ t.label }}
          </button>
        </nav>

        <button type="button" class="share-back" @click="router.push('/dashboard')">
          返回总览
        </button>
      </div>
    </header>

    <main class="share-main" :class="{ 'share-main--flush': flushMain }">
      <keep-alive :max="4">
        <PortalHomeView :key="'dept-portal'" />
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
  .share-back {
    margin-left: auto;
  }
}
</style>
