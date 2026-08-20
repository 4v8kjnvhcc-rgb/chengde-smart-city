<script setup lang="ts">
/**
 * 质量规则配置页：Tab 区分
 * 1) 校验规则类型
 * 2) 质量模型管理
 * 3) 质量规则配置
 * 4) 质量方案管理（定时→DolphinScheduler）
 * 5) 整改时间要求
 * 6) 告警配置
 */
import { ref, watch } from 'vue'
import PageCard from '@/components/common/PageCard.vue'
import QualityRuleTypePanel from './QualityRuleTypePanel.vue'
import QualityModelPanel from './QualityModelPanel.vue'
import QualityModelRulePanel from './QualityModelRulePanel.vue'
import QualitySchemePanel from './QualitySchemePanel.vue'
import QualityAlertConfigView from './QualityAlertConfigView.vue'
import QualityFixSlaPanel from './QualityFixSlaPanel.vue'

const activeTab = ref('rule-types')
const modelRuleRef = ref<InstanceType<typeof QualityModelRulePanel> | null>(null)

watch(activeTab, (t) => {
  if (t === 'model-rules') {
    modelRuleRef.value?.reload?.()
  }
})
</script>

<template>
  <PageCard title="质量规则配置">
    <el-tabs v-model="activeTab" class="quality-tabs">
      <el-tab-pane label="校验规则类型" name="rule-types" lazy>
        <QualityRuleTypePanel />
      </el-tab-pane>
      <el-tab-pane label="质量模型管理" name="models" lazy>
        <QualityModelPanel />
      </el-tab-pane>
      <el-tab-pane label="质量规则配置" name="model-rules" lazy>
        <QualityModelRulePanel ref="modelRuleRef" />
      </el-tab-pane>
      <el-tab-pane label="质量方案管理" name="schemes" lazy>
        <QualitySchemePanel />
      </el-tab-pane>
      <el-tab-pane label="整改时间要求" name="fix-sla" lazy>
        <QualityFixSlaPanel />
      </el-tab-pane>
      <el-tab-pane label="告警配置" name="alert-config" lazy>
        <QualityAlertConfigView />
      </el-tab-pane>
    </el-tabs>
  </PageCard>
</template>

<style scoped>
.quality-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
</style>
