<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import PageCard from '@/components/common/PageCard.vue'

const props = defineProps<{ module: string }>()
const router = useRouter()

const link = computed(() => {
  if (props.module === 'm048') {
    return {
      route: '/system/access',
      title: '访问控制管理',
      desc: '功能/资源/数据权限已迁至系统管理「身份与权限 · 访问控制」。',
    }
  }
  return { route: '/system/security', title: '系统维护管理', desc: '等保开关、安全策略与系统维护由系统管理模块实装。' }
})
</script>

<template>
  <PageCard :title="link.title">
    <el-result icon="info" :title="link.title" :sub-title="link.desc">
      <template #extra>
        <el-button type="primary" @click="router.push(link.route)">前往系统管理</el-button>
      </template>
    </el-result>
  </PageCard>
</template>
