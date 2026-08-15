<script setup lang="ts">
/**
 * M047 数据资产图谱分析：表血缘（主） + 组织鱼骨图谱（辅）
 */
import { ref } from 'vue'
import LineageView from './LineageView.vue'
import AssetFishboneView from './AssetFishboneView.vue'

const tab = ref<'lineage' | 'fishbone'>('lineage')
</script>

<template>
  <div class="lineage-hub">
    <el-tabs v-model="tab" class="hub-tabs">
      <el-tab-pane label="表级血缘" name="lineage" />
      <el-tab-pane label="组织鱼骨图谱" name="fishbone" />
    </el-tabs>
    <LineageView v-if="tab === 'lineage'" />
    <AssetFishboneView v-else />
  </div>
</template>

<style scoped>
.hub-tabs {
  margin-bottom: 8px;
}
</style>
