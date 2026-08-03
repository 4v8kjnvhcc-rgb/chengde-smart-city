<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'

const route = useRoute()
const token = computed(() => String(route.query.token || ''))
const targetType = computed(() => String(route.query.targetType || 'model'))
const targetId = computed(() => String(route.query.targetId || ''))
const valid = ref<Record<string, unknown> | null>(null)
const error = ref('')

const iframeUrl = computed(() => {
  if (!valid.value) return ''
  return String(valid.value.dataeaseUrl || '')
})

onMounted(async () => {
  if (!token.value) {
    error.value = '缺少 embed token'
    return
  }
  try {
    const res = await api.get('/analytics/embed-token/validate', { params: { token: token.value } })
    valid.value = res.data
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '令牌校验失败'
  }
})
</script>

<template>
  <div>
    <PageHeader
      title="DataEase 嵌入预览"
      description="真实 DataEase iframe SSO：校验门户令牌后加载 BI 看板"
    />
    <PageCard>
      <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" />
      <template v-else-if="valid">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="目标类型">{{ targetType }}</el-descriptions-item>
          <el-descriptions-item label="目标 ID">{{ targetId }}</el-descriptions-item>
          <el-descriptions-item label="令牌有效">{{ valid.valid }}</el-descriptions-item>
          <el-descriptions-item label="过期时间">{{ valid.expiresAt }}</el-descriptions-item>
        </el-descriptions>
        <div class="iframe-shell">
          <iframe
            v-if="iframeUrl"
            class="de-iframe"
            :src="iframeUrl"
            title="DataEase"
          />
          <div v-else class="iframe-placeholder">
            <div class="title">DataEase Embedded Canvas</div>
            <div class="sub">{{ targetType }} / {{ targetId }}</div>
            <div class="hint">DataEase 未就绪，请先启动 compose profile bi</div>
          </div>
        </div>
      </template>
      <el-skeleton v-else animated :rows="4" />
    </PageCard>
  </div>
</template>

<style scoped>
.iframe-shell {
  margin-top: 16px;
  min-height: 480px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  background: #0b1f33;
}
.de-iframe {
  width: 100%;
  height: 480px;
  border: 0;
}
.iframe-placeholder {
  height: 480px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #e8f4ff;
  gap: 8px;
}
.title {
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.sub {
  font-family: ui-monospace, Consolas, monospace;
  opacity: 0.9;
}
.hint {
  margin-top: 12px;
  font-size: 13px;
  opacity: 0.7;
}
</style>
