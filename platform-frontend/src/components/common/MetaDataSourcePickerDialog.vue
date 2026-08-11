<script setup lang="ts">
/**
 * 统一「选择数据源」弹窗：左分类 + 右表（名称/版本/所属分类/提供部门）。
 * 数据来自元数据数据源管理（bind-sources sourceKind=META）。
 */
import { computed, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import type { MetaBindSource } from '@/utils/meta-datasource-conn'

interface MetaCategory {
  id: number
  categoryName?: string
  label?: string
  name?: string
  layerCode?: string
  children?: MetaCategory[]
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 初始选中分类 id */
    initialCategoryId?: number | null
    title?: string
  }>(),
  {
    initialCategoryId: null,
    title: '选择数据源',
  },
)

const emit = defineEmits<{
  'update:modelValue': [boolean]
  confirm: [MetaBindSource]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const categories = ref<MetaCategory[]>([])
const categoryId = ref<number | null>(null)
const sideKeyword = ref('')
const nameKeyword = ref('')
const loading = ref(false)
const rows = ref<MetaBindSource[]>([])
const selected = ref<MetaBindSource | null>(null)

function flattenTree(nodes: MetaCategory[], out: MetaCategory[] = []): MetaCategory[] {
  for (const n of nodes || []) {
    out.push(n)
    if (n.children?.length) flattenTree(n.children, out)
  }
  return out
}

function categoryLabel(c: MetaCategory) {
  return c.categoryName || c.label || c.name || `分类#${c.id}`
}

const filteredCategories = computed(() => {
  const kw = sideKeyword.value.trim()
  if (!kw) return categories.value
  return categories.value.filter((c) => categoryLabel(c).includes(kw))
})

async function loadCategories() {
  try {
    const tree = (await api.get('/governance/platform/metadata/source-categories/tree')).data || []
    categories.value = flattenTree(tree)
    // 默认「全部」：不按分类过滤，展示数据源管理中全部 ACTIVE 源
    if (props.initialCategoryId && categories.value.some((c) => c.id === props.initialCategoryId)) {
      categoryId.value = props.initialCategoryId
    } else {
      categoryId.value = null
    }
  } catch {
    categories.value = []
    ElMessage.error('加载数据源分类失败')
  }
}

async function loadSources() {
  loading.value = true
  try {
    const res = await api.get('/governance/catalog/resources-mgmt/bind-sources', {
      params: {
        sourceKind: 'META',
        categoryId: categoryId.value || undefined,
        keyword: nameKeyword.value || sideKeyword.value || undefined,
      },
    })
    rows.value = res.data || []
  } catch {
    rows.value = []
    ElMessage.error('加载数据源失败')
  } finally {
    loading.value = false
  }
}

function selectCategory(id: number | null) {
  categoryId.value = id
  selected.value = null
  loadSources()
}

function onRowClick(row: MetaBindSource) {
  selected.value = row
}

function confirm() {
  if (!selected.value) {
    ElMessage.warning('请先选择一条数据源')
    return
  }
  emit('confirm', selected.value)
  visible.value = false
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    selected.value = null
    sideKeyword.value = ''
    nameKeyword.value = ''
    await loadCategories()
    await loadSources()
  },
)
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="900px"
    destroy-on-close
    append-to-body
    :close-on-click-modal="false"
  >
    <div class="ds-picker">
      <aside class="ds-picker-side">
        <div class="ds-picker-side-label">数据源分类</div>
        <el-input v-model="sideKeyword" clearable placeholder="请输入名称" size="small" style="margin-bottom: 8px" />
        <div
          class="ds-cat"
          :class="{ active: categoryId == null }"
          @click="selectCategory(null)"
        >
          全部
        </div>
        <div
          v-for="c in filteredCategories"
          :key="c.id"
          class="ds-cat"
          :class="{ active: categoryId === c.id }"
          @click="selectCategory(c.id)"
        >
          {{ categoryLabel(c) }}
        </div>
        <el-empty v-if="!filteredCategories.length" description="暂无分类" :image-size="48" />
      </aside>
      <div class="ds-picker-main">
        <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
          <el-form-item label="数据源名称" class="portal-field-lg">
            <el-input
              v-model="nameKeyword"
              clearable
              placeholder="请输入数据源名称"
              @keyup.enter="loadSources"
            />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadSources">查询</el-button>
            <el-button
              @click="
                nameKeyword = '';
                loadSources();
              "
            >
              重置
            </el-button>
          </el-form-item>
        </el-form>
        <el-table
          v-loading="loading"
          :data="rows"
          size="small"
          stripe
          highlight-current-row
          max-height="380"
          empty-text="暂无数据源"
          @row-click="onRowClick"
        >
          <el-table-column prop="sourceName" label="名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="版本" width="90">
            <template #default="{ row }">{{ row.versionLabel || '—' }}</template>
          </el-table-column>
          <el-table-column label="所属分类" width="100">
            <template #default="{ row }">{{ row.categoryName || '—' }}</template>
          </el-table-column>
          <el-table-column label="提供部门" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.providerOrg || '—' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.ds-picker {
  display: flex;
  gap: 12px;
  min-height: 420px;
}
.ds-picker-side {
  width: 160px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  padding-right: 10px;
}
.ds-picker-side-label {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.ds-cat {
  padding: 8px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.ds-cat:hover {
  background: var(--el-fill-color-light);
}
.ds-cat.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}
.ds-picker-main {
  flex: 1;
  min-width: 0;
}
</style>
