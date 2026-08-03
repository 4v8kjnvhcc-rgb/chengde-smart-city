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
      title="能力来自资源中心存储策略（纳管表备份）；原「数据库存储管理」备份 Tab 已迁入本菜单。"
    />
    <RcStorageLifecyclePanel mode="backup" />
  </PageCard>
</template>
