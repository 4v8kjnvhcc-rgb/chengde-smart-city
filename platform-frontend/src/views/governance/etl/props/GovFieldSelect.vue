<script setup lang="ts">
defineProps<{
  modelValue: string
  fields: string[]
  allowCustom?: boolean
  placeholder?: string
}>()
defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <el-select
    v-if="!allowCustom && fields.length"
    :model-value="modelValue"
    filterable
    clearable
    :placeholder="placeholder || '选择字段'"
    style="width:100%"
    @update:model-value="$emit('update:modelValue', $event || '')"
  >
    <el-option v-for="f in fields" :key="f" :label="f" :value="f" />
  </el-select>
  <el-input
    v-else
    :model-value="modelValue"
    :placeholder="placeholder || '字段名'"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>
