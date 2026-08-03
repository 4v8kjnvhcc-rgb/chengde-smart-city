<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import UserAccountMenu from '@/components/layout/UserAccountMenu.vue'
import { assessmentExternalUrl, openAssessmentWithPortalSso } from './application-nav'

const router = useRouter()
const hint = ref('')
const DEFAULT_LANDING = 'http://127.0.0.1:18081/assessment/index#/dashboard'

async function openExt() {
  const landing = assessmentExternalUrl() || DEFAULT_LANDING
  const r = await openAssessmentWithPortalSso(landing)
  if (r.ok) {
    hint.value = '已在新窗口打开考核评估系统（门户票据 SSO）'
    ElMessage.success(hint.value)
  } else {
    hint.value = r.message || '单点登录失败'
    ElMessage.warning(hint.value)
  }
}

onMounted(openExt)
</script>

<template>
  <div class="app-shell">
    <header class="app-shell__top">
      <div class="app-shell__brand">考核评估系统</div>
      <div class="app-shell__actions">
        <button type="button" class="app-shell__btn" @click="router.push('/exchange/application/assessment-config')">考核配置</button>
        <button type="button" class="app-shell__btn app-shell__btn--ghost" @click="router.push('/dashboard')">返回总览</button>
        <UserAccountMenu tone="onDark" />
      </div>
    </header>
    <main class="app-shell__main">
      <el-alert :title="hint || '考核评估为外系统，请通过门户票据 SSO 在新窗口中使用'" type="info" show-icon :closable="false" />
      <div class="actions">
        <el-button type="primary" @click="openExt">打开考核评估系统</el-button>
      </div>
      <p class="muted">落地地址优先门户配置；也可设 <code>VITE_ASSESSMENT_EXTERNAL_URL</code></p>
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
}
.app-shell__brand { font-size: 16px; font-weight: 700; }
.app-shell__actions { display: flex; align-items: center; gap: 8px; }
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
.app-shell__main { flex: 1; max-width: 800px; width: 100%; margin: 0 auto; padding: 32px 20px; box-sizing: border-box; }
.actions { margin-top: 16px; }
.muted { margin-top: 16px; color: #909399; font-size: 13px; }
</style>
