<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps<{ domain: 'base' | 'domain' }>()
const router = useRouter()
const StatsAnalysisView = defineAsyncComponent(() => import('./StatsAnalysisView.vue'))

const title = props.domain === 'base' ? '基础库统计分析应用' : '重点领域统计分析应用'
</script>

<template>
  <div class="app-shell">
    <header class="app-shell__top">
      <div class="app-shell__brand">{{ title }}</div>
      <div class="app-shell__actions">
        <button type="button" class="app-shell__btn app-shell__btn--ghost" @click="router.push('/dashboard')">返回总览</button>
      </div>
    </header>
    <main class="app-shell__main">
      <StatsAnalysisView :locked-domain="domain" />
    </main>
  </div>
</template>

<style scoped>
.app-shell { min-height: 100vh; background: #f3f6fb; display: flex; flex-direction: column; }
.app-shell__top {
  background: linear-gradient(90deg, #0b3a7a 0%, #1251a8 45%, #0d47a1 100%);
  color: #fff;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  gap: 12px;
}
.app-shell__brand { font-size: 16px; font-weight: 700; white-space: nowrap; }
.app-shell__actions { display: flex; gap: 8px; }
.app-shell__btn {
  appearance: none;
  border: 1px solid rgba(255,255,255,.35);
  background: rgba(255,255,255,.1);
  color: #e8f4ff;
  height: 32px;
  padding: 0 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.app-shell__btn--ghost { background: transparent; }
.app-shell__main { flex: 1; max-width: 1280px; width: 100%; margin: 0 auto; padding: 16px 20px 32px; box-sizing: border-box; }
</style>
