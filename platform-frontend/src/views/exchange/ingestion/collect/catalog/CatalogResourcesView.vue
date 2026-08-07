<script setup lang="ts">
/**
 * 归集 · 数据资源编目管理
 * 与数据目录「资源目录编制」同组件；catalogOrigin=INGEST 隔离数据域。
 */
import CatalogResourceView from '@/views/governance/catalog/CatalogResourceView.vue'
</script>

<template>
  <CatalogResourceView catalog-origin="INGEST" />
</template>
