<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, type NodeProps } from '@vue-flow/core'
import { findComponent } from '../governance-components'
import { inputHandlesFor, outputHandlesFor, type SwitchCaseRow } from './gov-prop-utils'

const props = defineProps<NodeProps>()

const nodeType = computed(() => String(props.data?.nodeType || 'FILTER'))
const label = computed(() => String(props.data?.label || findComponent(nodeType.value)?.name || nodeType.value))
const color = computed(() => findComponent(nodeType.value)?.color || '#909399')

const cases = computed<SwitchCaseRow[]>(() => {
  const cfg = (props.data?.config || {}) as Record<string, unknown>
  if (!Array.isArray(cfg.cases)) return []
  return (cfg.cases as Record<string, unknown>[]).map((c) => ({
    value: String(c.value ?? ''),
    label: String(c.label || c.value || ''),
  }))
})

const inHandles = computed(() => inputHandlesFor(nodeType.value))
const outHandles = computed(() => outputHandlesFor(nodeType.value, cases.value))

function handleTop(index: number, total: number): string {
  if (total <= 1) return '50%'
  return `${((index + 1) / (total + 1)) * 100}%`
}
</script>

<template>
  <div
    class="gov-flow-node"
    :class="{ 'is-selected': selected }"
    :style="{ '--node-accent': color }"
  >
    <Handle
      v-for="(h, i) in inHandles"
      :id="h.id"
      :key="`in-${h.id}`"
      type="target"
      :position="Position.Left"
      :style="{ top: handleTop(i, inHandles.length) }"
    >
      <span v-if="h.label" class="handle-label handle-label--in">{{ h.label }}</span>
    </Handle>

    <div class="gov-flow-node__body">
      <span class="gov-flow-node__dot" />
      <span class="gov-flow-node__label">{{ label }}</span>
    </div>

    <Handle
      v-for="(h, i) in outHandles"
      :id="h.id"
      :key="`out-${h.id}`"
      type="source"
      :position="Position.Right"
      :style="{ top: handleTop(i, outHandles.length) }"
    >
      <span v-if="h.label" class="handle-label handle-label--out">{{ h.label }}</span>
    </Handle>
  </div>
</template>

<style scoped>
.gov-flow-node {
  --node-accent: #909399;
  min-width: 104px;
  padding: 8px 14px;
  background: #fff;
  border: 1px solid color-mix(in srgb, var(--node-accent) 55%, #dcdfe6);
  border-radius: 10px;
  font-size: 12px;
  position: relative;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
  transition: box-shadow 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}
.gov-flow-node.is-selected {
  border-color: var(--node-accent);
  background: color-mix(in srgb, var(--node-accent) 8%, #fff);
  box-shadow:
    0 0 0 3px color-mix(in srgb, var(--node-accent) 22%, transparent),
    0 2px 8px rgba(15, 23, 42, 0.08);
}
.gov-flow-node__body {
  display: flex;
  align-items: center;
  gap: 6px;
}
.gov-flow-node__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--node-accent);
}
.gov-flow-node__label {
  white-space: nowrap;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #303133;
  font-weight: 500;
}
.handle-label {
  position: absolute;
  font-size: 10px;
  color: #606266;
  white-space: nowrap;
  pointer-events: none;
}
.handle-label--in {
  right: 10px;
  top: -2px;
}
.handle-label--out {
  left: 10px;
  top: -2px;
}
</style>
