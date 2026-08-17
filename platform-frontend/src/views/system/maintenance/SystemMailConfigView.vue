<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { withPasswordTransport } from '@/utils/transport-crypto'
import { isEmail } from '@/utils/validators'

defineProps<{ embed?: boolean }>()

const loading = ref(false)
const saving = ref(false)
const form = reactive({
  enabled: false,
  smtpHost: '',
  smtpPort: 465,
  smtpSsl: true,
  username: '',
  password: '',
  passwordSet: false,
  fromName: '',
  fromAddress: '',
})

async function load() {
  loading.value = true
  try {
    const data = (await api.get('/system/mail-config')).data || {}
    form.enabled = !!data.enabled
    form.smtpHost = data.smtpHost || ''
    form.smtpPort = data.smtpPort ?? 465
    form.smtpSsl = data.smtpSsl !== false
    form.username = data.username || ''
    form.password = ''
    form.passwordSet = !!data.passwordSet
    form.fromName = data.fromName || ''
    form.fromAddress = data.fromAddress || ''
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function save() {
  saving.value = true
  try {
    const body: Record<string, unknown> = {
      enabled: form.enabled,
      smtpHost: form.smtpHost,
      smtpPort: form.smtpPort,
      smtpSsl: form.smtpSsl,
      username: form.username,
      fromName: form.fromName,
      fromAddress: form.fromAddress,
    }
    await api.put('/system/mail-config', await withPasswordTransport(body, form.password))
    ElMessage.success('邮箱配置已保存')
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function sendTest() {
  try {
    const { value } = await ElMessageBox.prompt('请输入测试收件人邮箱', '发送测试邮件', {
      confirmButtonText: '发送',
      cancelButtonText: '取消',
      inputValidator: (val) => {
        if (!String(val || '').trim()) return '请输入有效邮箱'
        if (!isEmail(String(val))) return '邮箱格式不对'
        return true
      },
    })
    await api.post('/system/mail-config/test', { to: value })
    ElMessage.success('测试邮件已发送，请查收')
  } catch (e: unknown) {
    if (e === 'cancel' || (e as { action?: string })?.action === 'cancel') return
    ElMessage.error(e instanceof Error ? e.message : '发送失败')
  }
}
</script>

<template>
  <div v-loading="loading">
    <PageCard v-if="!$props.embed" title="系统维护管理 · 系统邮箱配置">
      <p class="hint">配置 SMTP 后可发测试邮件；未配置或发送失败时诚实报错，不假成功。</p>
    </PageCard>
    <el-form label-width="140px" style="max-width:560px">
      <el-form-item label="启用邮箱">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item label="SMTP 主机">
        <el-input v-model="form.smtpHost" placeholder="smtp.example.com" />
      </el-form-item>
      <el-form-item label="端口">
        <el-input-number v-model="form.smtpPort" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="SSL">
        <el-switch v-model="form.smtpSsl" />
      </el-form-item>
      <el-form-item label="账号">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" show-password :placeholder="form.passwordSet ? '已配置，留空则不修改' : '请输入密码'" />
      </el-form-item>
      <el-form-item label="发件人显示名">
        <el-input v-model="form.fromName" />
      </el-form-item>
      <el-form-item label="发件地址">
        <el-input v-model="form.fromAddress" placeholder="可与账号相同" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button @click="sendTest">测试发送</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; }
</style>
