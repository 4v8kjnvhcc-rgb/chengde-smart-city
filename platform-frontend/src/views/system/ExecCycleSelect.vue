<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import api from '@/api/http'

const CUSTOM_ID = -1

export interface ExecCycleOption {
  id: number
  cycleCode: string
  cycleName: string
  cronExpr: string
}

const props = withDefaults(defineProps<{
  modelValue?: string
  cycleId?: number | null
  placeholder?: string
  allowCustom?: boolean
}>(), {
  allowCustom: true,
})

const emit = defineEmits<{
  'update:modelValue': [string]
  'update:cycleId': [number | null]
  change: [ExecCycleOption | null]
}>()

const options = ref<ExecCycleOption[]>([])
const selectedId = ref<number | null>(props.cycleId ?? null)
const customCron = ref('')

const isCustom = computed(() => selectedId.value === CUSTOM_ID)

function syncFromModelValue(v?: string) {
  if (!v) {
    if (props.cycleId == null) {
      selectedId.value = null
      customCron.value = ''
    }
    return
  }
  const hit = options.value.find((o) => o.cronExpr === v)
  if (hit) {
    selectedId.value = hit.id
    customCron.value = ''
  } else if (props.allowCustom) {
    selectedId.value = CUSTOM_ID
    customCron.value = v
  } else {
    selectedId.value = null
    customCron.value = ''
  }
}

async function load() {
  options.value = (await api.get('/system/exec-cycles', { params: { status: 'ACTIVE' } })).data || []
  syncFromModelValue(props.modelValue)
}

function onPick(id: number | null) {
  selectedId.value = id
  if (id === CUSTOM_ID) {
    emit('update:cycleId', null)
    emit('update:modelValue', customCron.value.trim())
    emit('change', null)
    return
  }
  const hit = options.value.find((o) => o.id === id) || null
  customCron.value = ''
  emit('update:cycleId', id)
  emit('update:modelValue', hit?.cronExpr || '')
  emit('change', hit)
}

watch(
  () => props.cycleId,
  (v) => {
    if (v != null) selectedId.value = v
  },
)

watch(
  () => props.modelValue,
  (v) => syncFromModelValue(v),
)

watch(customCron, (v) => {
  if (selectedId.value === CUSTOM_ID) {
    emit('update:modelValue', v.trim())
    emit('update:cycleId', null)
    emit('change', null)
  }
})

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
        :label="o.cycleName"
        :value="o.id"
      />
      <el-option v-if="allowCustom" label="自定义周期" :value="CUSTOM_ID" />
    </el-select>
    <el-input
      v-if="isCustom"
      v-model="customCron"
      class="custom-cron-input"
      placeholder="请输入自定义周期表达式"
      clearable
    />
  </div>
</template>

<style scoped>
.exec-cycle-select {
  width: 100%;
}
.custom-cron-input {
  margin-top: 8px;
}
</style>
