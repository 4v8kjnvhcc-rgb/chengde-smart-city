<script setup lang="ts">
/**
 * 归集 · 数据资源目录审批
 * 与数据目录「资源目录审批」同组件；catalogOrigin=INGEST 隔离数据域。
 */
import CatalogApprovalView from '@/views/governance/catalog/CatalogApprovalView.vue'
</script>

<template>
  <CatalogApprovalView catalog-origin="INGEST" />
</template>
