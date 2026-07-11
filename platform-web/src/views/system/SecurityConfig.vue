<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

const form = reactive<Record<string, string>>({})

onMounted(async () => {
  const res = await api.get('/system/security-config')
  Object.assign(form, res.data)
})

async function save() {
  await api.put('/system/security-config', form)
  ElMessage.success('配置已保存')
}
</script>

<template>
  <div>
    <PageHeader title="等保开关" description="安全策略与等保相关配置（M049）" />
    <PageCard>
      <el-form label-width="180px" class="security-form">
        <el-divider content-position="left">认证与会话</el-divider>
        <el-form-item label="双因素登录">
          <el-switch v-model="form.two_factor_enabled" active-value="true" inactive-value="false" />
          <span class="form-hint">开启后登录需填写验证码</span>
        </el-form-item>
        <el-form-item label="会话空闲超时(分钟)">
          <el-input v-model="form.session_idle_minutes" />
        </el-form-item>
        <el-divider content-position="left">密码与锁定</el-divider>
        <el-form-item label="密码最小长度">
          <el-input v-model="form.password_min_length" />
        </el-form-item>
        <el-form-item label="密码复杂度">
          <el-switch v-model="form.password_require_complex" active-value="true" inactive-value="false" />
        </el-form-item>
        <el-form-item label="登录失败锁定次数">
          <el-input v-model="form.login_max_failures" />
        </el-form-item>
        <el-form-item label="锁定时长(分钟)">
          <el-input v-model="form.login_lock_minutes" />
        </el-form-item>
        <el-divider content-position="left">审计</el-divider>
        <el-form-item label="审计开关">
          <el-switch v-model="form.audit_enabled" active-value="true" inactive-value="false" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="save">保存配置</el-button>
        </el-form-item>
      </el-form>
    </PageCard>
  </div>
</template>

<style scoped>
.security-form {
  max-width: 560px;
}
.form-hint {
  margin-left: 12px;
  font-size: 13px;
  color: var(--portal-text-secondary);
}
</style>
