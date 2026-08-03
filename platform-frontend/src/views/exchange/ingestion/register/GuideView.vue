<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type GuideStep } from '../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const guides = ref<GuideStep[]>([])

const row1 = computed(() => guides.value.slice(0, 6))
const row2 = computed(() => guides.value.slice(6))

onMounted(() => withLoad(async () => {
  guides.value = (await ingestionApi.guides()).data
}))
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="填报指引（11 步）">
      <div class="guide-rows">
        <div class="guide-row">
          <div v-for="g in row1" :key="g.stepNo" class="guide-block">
            <span class="guide-no">{{ g.stepNo }}</span>
            <span class="guide-name">{{ g.stepName }}</span>
          </div>
        </div>
        <div class="guide-row guide-row--second">
          <div v-for="g in row2" :key="g.stepNo" class="guide-block">
            <span class="guide-no">{{ g.stepNo }}</span>
            <span class="guide-name">{{ g.stepName }}</span>
          </div>
        </div>
      </div>
    </PageCard>
  </div>
</template>

<style scoped>
.guide-rows { display: flex; flex-direction: column; gap: 12px; padding: 8px 0; }
.guide-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
}
.guide-row--second {
  grid-template-columns: repeat(5, 1fr);
}
.guide-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 52px;
  padding: 8px 4px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  text-align: center;
}
.guide-no {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.guide-name {
  font-size: 13px;
  color: #303133;
  line-height: 1.3;
  word-break: keep-all;
}
</style>
