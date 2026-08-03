<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'
import UserAccountMenu from '@/components/layout/UserAccountMenu.vue'
import { LEADER_PORTAL_BRAND } from './application-nav'

const router = useRouter()
const CockpitView = defineAsyncComponent(() => import('./CockpitView.vue'))
</script>

<template>
  <div class="share-shell">
    <header class="share-topnav">
      <div class="share-topnav__inner">
        <div class="share-brand">
          <span class="share-brand__mark" />
          <span class="share-brand__text">{{ LEADER_PORTAL_BRAND }}</span>
        </div>
        <div class="share-topnav__spacer" />
        <div class="share-topnav__right">
          <button type="button" class="share-back" @click="router.push('/dashboard')">
            返回总览
          </button>
          <UserAccountMenu tone="onDark" />
        </div>
      </div>
    </header>
    <main class="share-main">
      <keep-alive :max="2">
        <CockpitView />
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
.share-topnav__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.share-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
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
.share-topnav__spacer {
  flex: 1;
}
.share-back {
  appearance: none;
  border: 1px solid rgba(255, 255, 255, 0.35);
  background: rgba(255, 255, 255, 0.08);
  color: #e8f4ff;
  font-size: 13px;
  font-weight: 500;
  padding: 0 14px;
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
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
</style>
