<script setup lang="ts">
/**
 * 门户列表分页（中文文案，禁止英文 Total / Go to 等）
 */
withDefaults(
  defineProps<{
    total: number
    page: number
    pageSize: number
    pageSizes?: number[]
  }>(),
  {
    pageSizes: () => [10, 20, 50],
  },
)

const emit = defineEmits<{
  'update:page': [v: number]
  'update:pageSize': [v: number]
}>()
</script>

<template>
  <div v-if="total > 0" class="portal-pager">
    <span class="portal-pager__total">共 {{ total }} 条</span>
    <el-select
      :model-value="pageSize"
      class="portal-pager__sizes"
      size="small"
      @update:model-value="(v: number) => { emit('update:pageSize', v); emit('update:page', 1) }"
    >
      <el-option v-for="s in pageSizes" :key="s" :label="`${s} 条/页`" :value="s" />
    </el-select>
    <el-pagination
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next"
      background
      prev-text="上一页"
      next-text="下一页"
      @current-change="(v: number) => emit('update:page', v)"
    />
    <span class="portal-pager__jumper">
      前往
      <el-input-number
        :model-value="page"
        :min="1"
        :max="Math.max(1, Math.ceil(total / pageSize) || 1)"
        :controls="false"
        size="small"
        class="portal-pager__goto"
        @change="(v: number | undefined) => v != null && emit('update:page', v)"
      />
      页
    </span>
  </div>
</template>

<style scoped>
.portal-pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px 14px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #eef1f6;
  font-size: 13px;
  color: #606266;
}
.portal-pager__total { color: #606266; }
.portal-pager__sizes { width: 110px; }
.portal-pager__jumper {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.portal-pager__goto { width: 56px; }
.portal-pager__goto :deep(.el-input__inner) { text-align: center; }
</style>
