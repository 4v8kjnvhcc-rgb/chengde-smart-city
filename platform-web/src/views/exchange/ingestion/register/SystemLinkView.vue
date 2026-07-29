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
      sub-title="等保开关、安全策略与系统维护由系统管理模块实装。"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/system/security')">前往系统管理</el-button>
      </template>
    </el-result>
  </PageCard>
</template>
