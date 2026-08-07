<script setup lang="ts">
import PageCard from '@/components/common/PageCard.vue'
import RcStorageLifecyclePanel from '@/views/resource/RcStorageLifecyclePanel.vue'
</script>

<template>
  <PageCard title="数据备份">
    <RcStorageLifecyclePanel mode="backup" />
  </PageCard>
</template>
