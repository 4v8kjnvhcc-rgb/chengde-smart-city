<script setup lang="ts">
import { computed } from 'vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

export interface D05Module {
  mCode: string
  moduleName: string
  sectionKey: string
  sectionName: string
  chapter: string
  platform: string
  logicalDomain: string
  deliveryLevel: string
  implType: string
  description: string
  implStatus: string
  implRoute?: string | null
  externalUrl?: string | null
}

const props = defineProps<{
  module: D05Module
}>()

const statusText = computed(() => statusLabel(props.module.implStatus))
const statusType = computed(() => statusTagType(props.module.implStatus))
</script>

<template>
  <el-descriptions :column="2" border size="small" class="module-desc">
    <el-descriptions-item label="模块编号">{{ module.mCode }}</el-descriptions-item>
    <el-descriptions-item label="交付级别">
      <el-tag size="small">{{ $statusLabel(module.deliveryLevel) }}</el-tag>
    </el-descriptions-item>
    <el-descriptions-item label="逻辑域">{{ module.logicalDomain }}</el-descriptions-item>
    <el-descriptions-item label="实现状态">
      <el-tag size="small" :type="statusType">{{ statusText }}</el-tag>
    </el-descriptions-item>
    <el-descriptions-item label="D05 章节" :span="2">{{ module.chapter }}</el-descriptions-item>
    <el-descriptions-item label="所属板块" :span="2">{{ module.sectionName }}</el-descriptions-item>
    <el-descriptions-item label="实现方式" :span="2">{{ module.implType }}</el-descriptions-item>
    <el-descriptions-item label="功能描述" :span="2">{{ module.description }}</el-descriptions-item>
  </el-descriptions>
</template>

<style scoped>
.module-desc {
  margin-top: 8px;
}
</style>
