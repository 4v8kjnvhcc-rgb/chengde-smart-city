<script setup lang="ts">
import PageCard from '@/components/common/PageCard.vue'
import RcStorageLifecyclePanel from '@/views/resource/RcStorageLifecyclePanel.vue'
</script>

<template>
  <PageCard title="数据备份">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom:12px"
      title="与资源中心「数据库存储管理 · 数据备份」共用策略与产物；可在此或资源中心入口操作。"
    />
    <RcStorageLifecyclePanel mode="backup" />
  </PageCard>
</template>
