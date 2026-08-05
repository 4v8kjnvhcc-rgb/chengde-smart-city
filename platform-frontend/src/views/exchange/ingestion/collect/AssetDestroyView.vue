<script setup lang="ts">
import PageCard from '@/components/common/PageCard.vue'
import RcStorageLifecyclePanel from '@/views/resource/RcStorageLifecyclePanel.vue'
</script>

<template>
  <PageCard title="数据销毁">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom:12px"
      title="与资源中心「数据库存储管理 · 数据销毁」共用策略；禁止自动物理删除。"
    />
    <RcStorageLifecyclePanel mode="destroy" />
  </PageCard>
</template>
