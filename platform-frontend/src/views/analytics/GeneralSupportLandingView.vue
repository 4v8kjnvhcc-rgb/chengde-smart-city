<script setup lang="ts">
/**
 * 通用支撑平台中间选卡页已取消：旧链接直达「统一用户管理系统」。
 * 总览飞出：统一用户 / 智能BI；任务管理、集成运维在统一用户 Hub 侧栏内（菜单挂 id=13）。
 */
import { onBeforeMount } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onBeforeMount(() => {
  router.replace('/analytics/support')
})
</script>

<template>
  <div />
</template>
