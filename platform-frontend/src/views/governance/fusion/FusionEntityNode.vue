<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'

export interface EntityFieldBrief {
  fieldCode: string
  fieldName: string
  dataType?: string
  pkFlag?: number
}

const props = defineProps<NodeProps>()

const entityName = computed(() => String(props.data?.entityName || '逻辑实体'))
const entityCode = computed(() => String(props.data?.entityCode || ''))
const layer = computed(() => String(props.data?.layer || 'DWS'))
const fields = computed<EntityFieldBrief[]>(() => {
  const raw = props.data?.fields
  return Array.isArray(raw) ? (raw as EntityFieldBrief[]) : []
})
const physicalHint = computed(() => {
  const list = props.data?.physical as Array<{ tableName?: string }> | undefined
  if (!Array.isArray(list) || !list.length) return ''
  return list.map((p) => p.tableName).filter(Boolean).join(', ')
})
const layerLabel = computed(() => {
  const map: Record<string, string> = {
    ODS: '源层 ODS',
    DWD: '过程层 DWD',
    DWS: '主题库 DWS',
    ADS: '专题库 ADS',
  }
  return map[layer.value] || layer.value
})
const accent = computed(() => {
  const map: Record<string, string> = {
    ODS: '#909399',
    DWD: '#e6a23c',
    DWS: '#409eff',
    ADS: '#67c23a',
  }
  return map[layer.value] || '#409eff'
})
const shownFields = computed(() => fields.value.slice(0, 6))
</script>

<template>
  <div
    class="fusion-entity-node"
    :class="{ 'is-selected': selected }"
    :style="{ '--accent': accent }"
  >
    <Handle id="in" type="target" :position="Position.Left" class="fusion-handle" />
    <div class="fusion-entity-node__head">
      <span class="layer-badge">{{ layerLabel }}</span>
      <div class="title" :title="entityName">{{ entityName }}</div>
      <div class="code">{{ entityCode }}</div>
    </div>
    <ul v-if="shownFields.length" class="field-list">
      <li v-for="f in shownFields" :key="f.fieldCode">
        <span class="pk" v-if="f.pkFlag">PK</span>
        <span class="fname">{{ f.fieldName || f.fieldCode }}</span>
        <span class="ftype">{{ f.dataType || '' }}</span>
      </li>
      <li v-if="fields.length > shownFields.length" class="more">
        +{{ fields.length - shownFields.length }} 个属性
      </li>
    </ul>
    <div v-else class="field-empty">暂无业务属性</div>
    <div v-if="physicalHint" class="phys" :title="physicalHint">表：{{ physicalHint }}</div>
    <Handle id="out" type="source" :position="Position.Right" class="fusion-handle" />
  </div>
</template>

<style scoped>
.fusion-entity-node {
  --accent: #409eff;
  width: 220px;
  background: #fff;
  border: 1px solid color-mix(in srgb, var(--accent) 45%, #dcdfe6);
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.08);
  font-size: 12px;
  overflow: hidden;
}
.fusion-entity-node.is-selected {
  border-color: var(--accent);
  box-shadow:
    0 0 0 3px color-mix(in srgb, var(--accent) 20%, transparent),
    0 2px 10px rgba(15, 23, 42, 0.1);
}
.fusion-entity-node__head {
  padding: 8px 10px 6px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: color-mix(in srgb, var(--accent) 8%, #fff);
}
.layer-badge {
  display: inline-block;
  font-size: 10px;
  padding: 0 6px;
  border-radius: 4px;
  color: #fff;
  background: var(--accent);
  margin-bottom: 4px;
  line-height: 16px;
}
.title {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.code {
  color: #909399;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.field-list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
  max-height: 140px;
  overflow: hidden;
}
.field-list li {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  line-height: 18px;
}
.field-list li.more {
  color: #909399;
  font-size: 11px;
}
.pk {
  font-size: 9px;
  font-weight: 700;
  color: #e6a23c;
  border: 1px solid #f5dab1;
  border-radius: 2px;
  padding: 0 3px;
  flex-shrink: 0;
}
.fname {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
}
.ftype {
  color: #c0c4cc;
  font-size: 10px;
  flex-shrink: 0;
}
.field-empty {
  padding: 8px 10px;
  color: #c0c4cc;
}
.phys {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding: 4px 10px 6px;
  color: #67c23a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
}
.fusion-handle {
  width: 10px;
  height: 10px;
  background: var(--accent);
  border: 2px solid #fff;
}
</style>
