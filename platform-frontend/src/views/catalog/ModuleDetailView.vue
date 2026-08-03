<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import PageHeader from '@/components/common/PageHeader.vue'
import PageCard from '@/components/common/PageCard.vue'
import ModuleMetaPanel, { type D05Module } from '@/components/catalog/ModuleMetaPanel.vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const mod = ref<D05Module | null>(null)
const loading = ref(false)

const mCode = computed(() => String(route.params.mCode || '').toUpperCase())

const statusHint = computed(() => {
  if (!mod.value) return ''
  const s = mod.value.implStatus
  if (s === 'implemented') return '该模块已在门户中提供完整操作页面。'
  if (s === 'poc') return '该模块已有演示页，覆盖部分验收流程，尚未达到 D05 L1 完整深度。'
  if (s === 'external') return '该模块由外购/开源组件承载，门户提供代理或深链入口。'
  if (s === 'stub') return '基础设施已集成，业务页面待下一迭代实装。'
  return '该模块已纳入 D05 清单导航，功能开发尚未启动。'
})

async function load() {
  if (!mCode.value) return
  loading.value = true
  try {
    const res = await api.get(`/catalog/modules/${mCode.value}`)
    mod.value = res.data
  } catch {
    mod.value = null
    ElMessage.error(`未找到模块 ${mCode.value}`)
  } finally {
    loading.value = false
  }
}

function goImpl() {
  if (mod.value?.implRoute) router.push(mod.value.implRoute)
}

function goExternal() {
  if (mod.value?.externalUrl) window.open(mod.value.externalUrl, '_blank')
}

function goCatalog() {
  if (!mod.value) return
  router.push(`/catalog/${mod.value.platform}`)
}

watch(() => route.params.mCode, load)
onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <PageHeader
      v-if="mod"
      :title="`${mod.mCode} ${mod.moduleName}`"
      :description="statusHint"
    >
      <el-button @click="goCatalog">返回功能清单</el-button>
      <el-button v-if="mod.implRoute" type="primary" @click="goImpl">进入已实现功能</el-button>
      <el-button v-if="mod.externalUrl" @click="goExternal">打开外部组件</el-button>
    </PageHeader>

    <PageCard v-if="mod" title="D05 验收对照信息">
      <ModuleMetaPanel :module="mod" />
      <el-alert
        style="margin-top: 16px"
        type="info"
        :closable="false"
        show-icon
        title="验收说明"
        description="本页为 D05 功能清单导航入口。L1 模块须具备完整业务流程；当前实现状态见上方标签。后续迭代将按交付级别逐模块加深。"
      />
    </PageCard>
  </div>
</template>
