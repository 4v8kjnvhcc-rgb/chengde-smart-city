<script setup lang="ts">
import PageCard from '@/components/common/PageCard.vue'
import RcStorageLifecyclePanel from '@/views/resource/RcStorageLifecyclePanel.vue'
</script>

<template>
  <PageCard title="数据归档">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom:12px"
      title="与资源中心「数据库存储管理 · 数据归档」共用策略与产物；归档执行记台账。"
    />
    <RcStorageLifecyclePanel mode="archive" />
  </PageCard>
</template>
