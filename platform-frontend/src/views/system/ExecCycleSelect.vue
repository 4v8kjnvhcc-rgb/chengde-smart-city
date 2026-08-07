<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import api from '@/api/http'

export interface ExecCycleOption {
  id: number
  cycleCode: string
  cycleName: string
  cronExpr: string
}

const props = defineProps<{
  modelValue?: string
  cycleId?: number | null
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [string]
  'update:cycleId': [number | null]
  change: [ExecCycleOption | null]
}>()

const options = ref<ExecCycleOption[]>([])
const selectedId = ref<number | null>(props.cycleId ?? null)

async function load() {
  options.value = (await api.get('/system/exec-cycles', { params: { status: 'ACTIVE' } })).data || []
  if (selectedId.value == null && props.modelValue) {
    const hit = options.value.find((o) => o.cronExpr === props.modelValue)
    if (hit) selectedId.value = hit.id
  }
}

function onPick(id: number | null) {
  selectedId.value = id
  const hit = options.value.find((o) => o.id === id) || null
  emit('update:cycleId', id)
  emit('update:modelValue', hit?.cronExpr || '')
  emit('change', hit)
}

watch(
  () => props.cycleId,
  (v) => {
    selectedId.value = v ?? null
  },
)

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    const hit = options.value.find((o) => o.cronExpr === v)
    if (hit) selectedId.value = hit.id
  },
)

onMounted(load)
</script>

<template>
  <div class="exec-cycle-select">
    <el-select
      :model-value="selectedId"
      clearable
      filterable
      style="width: 100%"
      :placeholder="placeholder || '请选择执行周期'"
      @update:model-value="onPick"
    >
      <el-option
        v-for="o in options"
        :key="o.id"
        :label="`${o.cycleName}（${o.cronExpr}）`"
        :value="o.id"
      />
    </el-select>
    <div v-if="modelValue" class="cron-hint">Cron：{{ modelValue }}</div>
  </div>
</template>

<style scoped>
.exec-cycle-select { width: 100%; }
.cron-hint { margin-top: 6px; font-size: 12px; color: #909399; }
</style>
