<script setup lang="ts">
import { useRouter } from 'vue-router'
import UserAccountMenu from '@/components/layout/UserAccountMenu.vue'

const router = useRouter()

const systems = [
  {
    key: 'register',
    title: '数据资产登记管理系统',
    desc: '填报指引 · 项目/系统 · 库表项 · 字典标签 · 访问控制',
    query: { system: 'register' },
  },
  {
    key: 'collect',
    title: '数据资源采集汇聚系统',
    desc: '汇聚接入 · 规范设计 · 目录体系 · 质量管控 · 资产管理',
    query: { system: 'collect' },
  },
] as const

function openSystem(q: { system: string }) {
  router.push({ path: '/exchange/ingestion', query: q })
}
</script>

<template>
  <div class="landing">
    <header class="landing__top">
      <div class="landing__brand">大数据归集平台</div>
      <div class="landing__actions">
        <button type="button" class="landing__back" @click="router.push('/dashboard')">返回总览</button>
        <UserAccountMenu tone="onDark" />
      </div>
    </header>
    <main class="landing__main">
      <h1 class="landing__title">大数据归集平台</h1>
      <p class="landing__desc">请选择要进入的独立系统（各自单独打开，互不混页；切换系统请返回总览后重新进入）</p>
      <div class="landing__grid">
        <button
          v-for="s in systems"
          :key="s.key"
          type="button"
          class="landing__card"
          @click="openSystem(s.query)"
        >
          <span class="landing__card-title">{{ s.title }}</span>
          <span class="landing__card-desc">{{ s.desc }}</span>
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
  border: 1px solid rgba(255, 255, 255, 0.35);
  background: rgba(255, 255, 255, 0.08);
  color: #e8f4ff;
  height: 32px;
  padding: 0 14px;
  border-radius: 4px;
  cursor: pointer;
}
.landing__main { max-width: 960px; margin: 0 auto; padding: 40px 20px; }
.landing__title { margin: 0 0 8px; font-size: 28px; color: #0b3a7a; }
.landing__desc { margin: 0 0 28px; color: #606266; }
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
.landing__card:hover {
  border-color: #1251a8;
  box-shadow: 0 6px 18px rgba(11, 58, 122, 0.12);
}
.landing__card-title { display: block; font-size: 18px; font-weight: 650; color: #1f2d3d; }
.landing__card-desc { display: block; margin-top: 8px; font-size: 13px; color: #909399; }
.landing__card-go { display: block; margin-top: 16px; color: #1251a8; font-size: 13px; }
@media (max-width: 720px) {
  .landing__grid { grid-template-columns: 1fr; }
}
</style>
