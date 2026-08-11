<script setup lang="ts">
/**
 * 应用分析门户中间选卡页已取消：进入本路由时直达「部门数据共享门户」。
 * 总览侧通过飞出直达各部门/领导门户；此组件仅作旧链接兼容。
 */
import { onBeforeMount } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

onBeforeMount(() => {
  router.replace('/exchange/analysis-portal/dept')
})
</script>

<template>
  <div />
</template>
