<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import UserAccountMenu from '@/components/layout/UserAccountMenu.vue'
import { assessmentExternalUrl, openAssessmentWithPortalSso } from './application-nav'

const router = useRouter()

const apps = [
  {
    key: 'supply',
    title: '数据供需对接系统',
    path: '/exchange/application/supply',
    external: false,
  },
  {
    key: 'assessment',
    title: '考核评估系统',
    path: '',
    external: true,
  },
  {
    key: 'stats-base',
    title: '基础库统计分析应用',
    path: '/exchange/application/stats-base',
    external: false,
  },
  {
    key: 'stats-domain',
    title: '重点领域统计分析应用',
    path: '/exchange/application/stats-domain',
    external: false,
  },
] as const

async function openApp(app: (typeof apps)[number]) {
  if (app.external) {
    const landing = assessmentExternalUrl() || 'http://127.0.0.1:18081/assessment/index#/dashboard'
    const r = await openAssessmentWithPortalSso(landing)
    if (r.ok) ElMessage.success('已进入考核评估系统')
    else ElMessage.warning(r.message || '单点登录失败')
    return
  }
  router.push(app.path)
}
</script>

<template>
  <div class="landing">
    <header class="landing__top">
      <div class="landing__brand">应用平台</div>
      <div class="landing__actions">
        <button type="button" class="landing__back" @click="router.push('/dashboard')">返回总览</button>
        <UserAccountMenu tone="onDark" />
      </div>
    </header>
    <main class="landing__main">
      <h1 class="landing__title">应用平台</h1>
      <div class="landing__grid">
        <button
          v-for="a in apps"
          :key="a.key"
          type="button"
          class="landing__card"
          @click="openApp(a)"
        >
          <span class="landing__card-title">{{ a.title }}</span>
          <span class="landing__card-go">进入 →</span>
        </button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.landing { min-height: 100vh; background: #f3f6fb; }
.landing__top {
  background: linear-gradient(90deg, #0b3a7a 0%, #1251a8 45%, #0d47a1 100%);
  color: #fff;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}
.landing__brand { font-size: 16px; font-weight: 700; }
.landing__actions { display: flex; align-items: center; gap: 12px; }
.landing__back {
  appearance: none;
  border: 1px solid rgba(255,255,255,.35);
  background: rgba(255,255,255,.08);
  color: #e8f4ff;
  height: 32px;
  padding: 0 14px;
  border-radius: 4px;
  cursor: pointer;
}
.landing__main { max-width: 960px; margin: 0 auto; padding: 40px 20px; }
.landing__title { margin: 0 0 28px; font-size: 28px; color: #0b3a7a; }
.landing__grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.landing__card {
  appearance: none;
  border: 1px solid #d9e4f5;
  background: #fff;
  border-radius: 10px;
  padding: 24px;
  text-align: left;
  cursor: pointer;
}
.landing__card:hover { border-color: #1251a8; box-shadow: 0 6px 18px rgba(11,58,122,.12); }
.landing__card-title { display: block; font-size: 18px; font-weight: 650; color: #1f2d3d; }
.landing__card-go { display: block; margin-top: 16px; color: #1251a8; font-size: 13px; }
@media (max-width: 720px) { .landing__grid { grid-template-columns: 1fr; } }
</style>
