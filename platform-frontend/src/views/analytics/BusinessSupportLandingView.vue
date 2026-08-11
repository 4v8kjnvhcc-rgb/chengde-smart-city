<script setup lang="ts">
/**
 * 业务支撑平台中间选卡页已取消：旧链接直达「人口大数据支撑系统」。
 * 总览侧通过飞出直达人口 / 法人 / 宏观 / 重点领域。
 */
import { onBeforeMount } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onBeforeMount(() => {
  router.replace('/analytics/population')
})
</script>

<template>
  <div />
</template>
