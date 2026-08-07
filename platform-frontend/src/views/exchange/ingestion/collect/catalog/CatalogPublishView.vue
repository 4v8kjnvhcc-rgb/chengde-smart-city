<script setup lang="ts">
/**
 * 归集 · 资源目录注册发布
 * 与数据目录「目录注册发布」同组件；catalogOrigin=INGEST 隔离数据域。
 */
import CatalogRegisterPublishView from '@/views/governance/catalog/CatalogRegisterPublishView.vue'
</script>

<template>
  <CatalogRegisterPublishView catalog-origin="INGEST" />
</template>
