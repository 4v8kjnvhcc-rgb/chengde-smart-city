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
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/system/maintenance')">前往系统维护管理</el-button>
      </template>
    </el-result>
  </PageCard>
</template>
