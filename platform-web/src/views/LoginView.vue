<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  totpCode: '',
})

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password, form.totpCode || undefined)
    router.push('/dashboard')
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-page__brand">
      <div class="login-page__brand-inner">
        <h1>承德高新区智慧城市基础平台</h1>
        <p>统一门户 · 数据共享 · 融合治理 · 挖掘分析</p>
      </div>
    </div>
    <div class="login-page__form-wrap">
      <el-card class="login-card" shadow="never">
        <h2 class="login-card__title">统一门户登录</h2>
        <p class="login-card__subtitle">请使用分配的账号登录系统</p>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名">
            <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" size="large" />
          </el-form-item>
          <el-form-item label="双因素验证码">
            <el-input v-model="form.totpCode" placeholder="未开启可留空" size="large" />
          </el-form-item>
          <el-button type="primary" size="large" class="login-card__btn" :loading="loading" @click="submit">
            登录
          </el-button>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  background: linear-gradient(
    105deg,
    #001529 0%,
    #002a52 15%,
    #0d3d7a 30%,
    #1a5fb4 50%,
    #2d7fd4 68%,
    #5a9fd8 85%,
    #e8f0f8 95%,
    #f5f8fc 100%
  );
}
.login-page__brand {
  flex: 1;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}
.login-page__brand-inner h1 {
  color: #fff;
  font-size: 28px;
  margin: 0 0 16px;
  font-weight: 600;
}
.login-page__brand-inner p {
  color: rgba(255, 255, 255, 0.75);
  font-size: 16px;
  margin: 0;
}
.login-page__form-wrap {
  width: 480px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: transparent;
}
.login-card {
  width: 100%;
  border: none;
  border-radius: var(--portal-radius);
  box-shadow: 0 8px 32px rgba(0, 21, 41, 0.18);
  background: rgba(255, 255, 255, 0.96);
}
.login-card__title {
  margin: 0 0 8px;
  font-size: 22px;
  color: var(--portal-text);
}
.login-card__subtitle {
  margin: 0 0 24px;
  font-size: 14px;
  color: var(--portal-text-secondary);
}
.login-card__btn {
  width: 100%;
  margin-top: 8px;
}
@media (max-width: 900px) {
  .login-page {
    flex-direction: column;
    background: linear-gradient(
      180deg,
      #001529 0%,
      #0d3d7a 30%,
      #1a5fb4 50%,
      #5a9fd8 85%,
      #e8f0f8 95%,
      #f5f8fc 100%
    );
  }
  .login-page__brand {
    padding: 32px 24px;
  }
  .login-page__form-wrap {
    width: 100%;
  }
}
</style>
