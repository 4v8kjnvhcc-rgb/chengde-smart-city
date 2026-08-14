<script setup lang="ts">
/**
 * 告警配置：邮件/短信通道落库；邮件走系统 SMTP，短信记台账。
 */
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'

const saving = ref(false)
const loading = ref(false)

const mail = reactive({
  enabled: false,
  defaultReceivers: '',
})

const sms = reactive({
  enabled: false,
  gatewayUrl: '',
  signName: '承德大数据',
  templateCode: 'SMS_QUALITY_ALERT',
  defaultPhones: '',
})

const ownerName = ref('数据治理组')

async function load() {
  loading.value = true
  try {
    const d = (await api.get('/governance/quality/alerts/channel')).data || {}
    mail.enabled = !!d.mailEnabled
    mail.defaultReceivers = d.mailReceivers || ''
    sms.enabled = !!d.smsEnabled
    sms.gatewayUrl = d.smsGatewayUrl || ''
    sms.signName = d.smsSignName || '承德大数据'
    sms.templateCode = d.smsTemplateCode || 'SMS_QUALITY_ALERT'
    sms.defaultPhones = d.smsPhones || ''
    ownerName.value = d.ownerName || '数据治理组'
  } catch {
    ElMessage.error('加载告警通道失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await api.put('/governance/quality/alerts/channel', {
      mailEnabled: mail.enabled,
      mailReceivers: mail.defaultReceivers,
      smsEnabled: sms.enabled,
      smsPhones: sms.defaultPhones,
      smsGatewayUrl: sms.gatewayUrl,
      smsSignName: sms.signName,
      smsTemplateCode: sms.templateCode,
      ownerName: ownerName.value,
    })
    ElMessage.success('告警通道已保存')
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="alert-cfg">
    <el-form label-width="110px" class="owner-form portal-inline-form portal-inline-form--block" inline>
      <el-form-item label="默认责任人" class="portal-field-lg">
        <el-input v-model="ownerName" placeholder="告警责任人" />
      </el-form-item>
    </el-form>

    <div class="alert-cfg__grid">
      <div class="alert-card is-mail">
        <div class="alert-card__head">
          <div class="alert-card__title">
            <span class="alert-card__dot mail" />
            告警邮件
          </div>
          <el-switch v-model="mail.enabled" active-text="启用" inactive-text="关闭" />
        </div>
        <el-form label-width="110px" class="alert-card__form" :disabled="!mail.enabled">
          <el-form-item label="收件人">
            <el-input
              v-model="mail.defaultReceivers"
              type="textarea"
              :rows="3"
              placeholder="多个邮箱用分号分隔；实际发送走「系统管理 · 邮件配置」SMTP"
            />
          </el-form-item>
        </el-form>
      </div>

      <div class="alert-card is-sms">
        <div class="alert-card__head">
          <div class="alert-card__title">
            <span class="alert-card__dot sms" />
            告警短信
          </div>
          <el-switch v-model="sms.enabled" active-text="启用" inactive-text="关闭" />
        </div>
        <el-form label-width="110px" class="alert-card__form" :disabled="!sms.enabled">
          <el-form-item label="网关地址">
            <el-input v-model="sms.gatewayUrl" placeholder="https://sms.example.com/api/send" />
          </el-form-item>
          <el-form-item label="签名">
            <el-input v-model="sms.signName" />
          </el-form-item>
          <el-form-item label="模板编码">
            <el-input v-model="sms.templateCode" placeholder="SMS_QUALITY_ALERT" />
          </el-form-item>
          <el-form-item label="接收手机">
            <el-input
              v-model="sms.defaultPhones"
              type="textarea"
              :rows="2"
              placeholder="多个号码用分号分隔；当前记推送台账，不直连真实短信网关"
            />
          </el-form-item>
        </el-form>
      </div>
    </div>

    <div class="alert-cfg__actions">
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </div>
  </div>
</template>

<style scoped>
.alert-cfg {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.owner-form {
  margin-bottom: 0;
}
.alert-cfg__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.alert-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 16px 18px 8px;
  background: var(--el-bg-color);
}
.alert-card.is-mail {
  background: linear-gradient(180deg, rgba(37, 99, 235, 0.05), transparent 48%);
  border-color: rgba(37, 99, 235, 0.18);
}
.alert-card.is-sms {
  background: linear-gradient(180deg, rgba(16, 185, 129, 0.06), transparent 48%);
  border-color: rgba(16, 185, 129, 0.2);
}
.alert-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.alert-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 650;
}
.alert-card__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.alert-card__dot.mail {
  background: #2563eb;
}
.alert-card__dot.sms {
  background: #10b981;
}
.alert-cfg__actions {
  display: flex;
  justify-content: flex-end;
}
@media (max-width: 900px) {
  .alert-cfg__grid {
    grid-template-columns: 1fr;
  }
}
</style>
