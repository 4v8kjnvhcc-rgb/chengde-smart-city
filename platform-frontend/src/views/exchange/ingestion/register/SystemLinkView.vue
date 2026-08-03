<script setup lang="ts">
import { useRouter } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'

defineProps<{ module?: string }>()
const router = useRouter()
</script>

<template>
  <PageCard title="系统维护管理">
    <el-result
      icon="info"
      title="系统维护管理"
      sub-title="外观、系统邮箱与等保安全配置已挂在系统管理 · 平台运维。"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/system/maintenance')">前往系统维护管理</el-button>
      </template>
    </el-result>
  </PageCard>
</template>
