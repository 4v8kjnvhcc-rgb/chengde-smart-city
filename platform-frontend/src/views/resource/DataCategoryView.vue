<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/http'
import {
  buildDefaultConfigs,
  categoryTypeLabel,
  CATEGORY_TYPE_OPTIONS,
  configLevelDesc,
  configLevelLabel,
  CONFIG_LEVEL_OPTIONS,
  ensureLevelConfigs,
  FIELD_MATRIX,
  type CategoryType,
  type ConfigLevel,
  type LevelConfigs,
} from './data-category-config'

export interface CategoryRow {
  uuid: string
  categoryCode: string
  categoryName: string
  categoryType: CategoryType
  configLevel: ConfigLevel
  configJson: string
  description?: string
  sortNo: number
  status: number
  createTime?: string
}

const props = withDefaults(
  defineProps<{
    embed?: boolean
    selectable?: boolean
    selectedUuid?: string
  }>(),
  { embed: false, selectable: false, selectedUuid: '' },
)

const emit = defineEmits<{
  'update:selectedUuid': [uuid: string]
  select: [row: CategoryRow]
  loaded: [rows: CategoryRow[]]
}>()

const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const records = ref<CategoryRow[]>([])

const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const hydrating = ref(false)

const form = reactive({
  uuid: '',
  categoryCode: '',
  categoryName: '',
  categoryType: 'STATIC' as CategoryType,
  configLevel: 'BASIC' as ConfigLevel,
  description: '',
  sortNo: 0,
  status: 1,
})

const levelConfigs = ref<LevelConfigs>(buildDefaultConfigs('STATIC'))
const currentFields = computed(() => FIELD_MATRIX[form.categoryType][form.configLevel] || [])

async function loadList() {
  loading.value = true
  try {
    const res = await api.get('/resource/data-category', {
      params: { keyword: keyword.value.trim() || undefined, page: page.value, size: size.value },
    })
    records.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
    emit('loaded', records.value)
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void loadList()
}

function pickRow(row: CategoryRow) {
  if (row.status !== 1) {
    ElMessage.warning('请选择已启用的分类')
    return
  }
  emit('update:selectedUuid', row.uuid)
  emit('select', row)
}

function openCreate() {
  isEdit.value = false
  hydrating.value = true
  form.uuid = ''
  form.categoryCode = ''
  form.categoryName = ''
  form.categoryType = 'STATIC'
  form.configLevel = 'BASIC'
  form.description = ''
  form.sortNo = 0
  form.status = 1
  levelConfigs.value = buildDefaultConfigs('STATIC')
  dialogVisible.value = true
  hydrating.value = false
}

function openEdit(row: CategoryRow) {
  isEdit.value = true
  hydrating.value = true
  form.uuid = row.uuid
  form.categoryCode = row.categoryCode
  form.categoryName = row.categoryName
  form.categoryType = row.categoryType
  form.configLevel = row.configLevel || 'BASIC'
  form.description = row.description || ''
  form.sortNo = row.sortNo ?? 0
  form.status = row.status ?? 1
  let parsed: unknown = {}
  try {
    parsed = row.configJson ? JSON.parse(row.configJson) : {}
  } catch {
    parsed = {}
  }
  levelConfigs.value = ensureLevelConfigs(row.categoryType, parsed)
  dialogVisible.value = true
  hydrating.value = false
}

watch(
  () => form.categoryType,
  (type) => {
    if (!dialogVisible.value || hydrating.value) return
    levelConfigs.value = buildDefaultConfigs(type)
  },
)

watch(
  () => form.configLevel,
  (level) => {
    if (!dialogVisible.value || hydrating.value) return
    const cur = levelConfigs.value[level]
    if (!cur || Object.keys(cur).length === 0) {
      levelConfigs.value = {
        ...levelConfigs.value,
        [level]: buildDefaultConfigs(form.categoryType)[level],
      }
    }
  },
)

async function save() {
  if (!form.categoryCode.trim() || !form.categoryName.trim()) {
    ElMessage.warning('请填写分类编码与名称')
    return
  }
  saving.value = true
  try {
    const payload: Record<string, unknown> = {
      categoryCode: form.categoryCode.trim(),
      categoryName: form.categoryName.trim(),
      categoryType: form.categoryType,
      configLevel: form.configLevel,
      description: form.description,
      sortNo: form.sortNo,
      status: form.status,
      configJson: JSON.stringify(levelConfigs.value),
    }
    if (isEdit.value && form.uuid) payload.uuid = form.uuid
    await api.post('/resource/data-category', payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadList()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRow(row: CategoryRow) {
  try {
    await ElMessageBox.confirm(`确认删除分类「${row.categoryName}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await api.delete(`/resource/data-category/${row.uuid}`)
    ElMessage.success('已删除')
    if (props.selectedUuid === row.uuid) emit('update:selectedUuid', '')
    await loadList()
  } catch (e: unknown) {
    if (e === 'cancel' || (e as { action?: string })?.action === 'cancel') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function statusText(s: number) {
  return s === 1 ? '启用' : '停用'
}

defineExpose({ loadList, records })
onMounted(loadList)
</script>

<template>
  <div class="dc-page" :class="{ 'dc-page--embed': props.embed }">
    <el-card shadow="never" class="dc-query">
      <el-form inline class="portal-inline-form">
        <el-form-item label="关键词" class="portal-field-lg">
          <el-input v-model="keyword" clearable placeholder="按分类名称搜索" @keyup.enter="search" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="openCreate">新增</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="dc-table-card" v-loading="loading">
      <el-table :data="records" border stripe @row-click="(row: CategoryRow) => props.selectable && pickRow(row)">
        <el-table-column v-if="props.selectable" label="选用" width="70" align="center">
          <template #default="{ row }">
            <el-radio
              :model-value="props.selectedUuid"
              :value="row.uuid"
              :disabled="row.status !== 1"
              @change="pickRow(row)"
              @click.stop
            >
              &nbsp;
            </el-radio>
          </template>
        </el-table-column>
        <el-table-column prop="categoryCode" label="分类编码" width="120" />
        <el-table-column prop="categoryName" label="分类名称" min-width="140" />
        <el-table-column label="分类类型" width="130">
          <template #default="{ row }">{{ categoryTypeLabel(row.categoryType) }}</template>
        </el-table-column>
        <el-table-column label="配置级别" width="110">
          <template #default="{ row }">{{ configLevelLabel(row.configLevel) }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sortNo" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click.stop="removeRow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="dc-pager">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑分类' : '新增分类'"
      width="680px"
      destroy-on-close
      append-to-body
    >
      <el-form label-width="140px">
        <el-form-item label="分类编码" required>
          <el-input v-model="form.categoryCode" :disabled="isEdit" placeholder="如 STATIC / FILE" />
        </el-form-item>
        <el-form-item label="分类名称" required>
          <el-input v-model="form.categoryName" />
        </el-form-item>
        <el-form-item label="分类类型">
          <el-select v-model="form.categoryType" style="width:100%">
            <el-option v-for="o in CATEGORY_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置级别">
          <el-select v-model="form.configLevel" style="width:100%">
            <el-option v-for="o in CONFIG_LEVEL_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-divider content-position="left">配置明细</el-divider>
        <el-alert type="info" :closable="false" show-icon :title="configLevelDesc(form.configLevel)" style="margin-bottom:16px" />
        <template v-for="f in currentFields" :key="f.key">
          <el-form-item :label="f.label">
            <el-select v-if="f.control === 'select'" v-model="levelConfigs[form.configLevel][f.key]" style="width:100%">
              <el-option v-for="opt in f.options || []" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-checkbox-group
              v-else-if="f.control === 'checkbox'"
              v-model="levelConfigs[form.configLevel][f.key] as string[]"
            >
              <el-checkbox v-for="opt in f.options || []" :key="String(opt.value)" :label="String(opt.value)">
                {{ opt.label }}
              </el-checkbox>
            </el-checkbox-group>
            <el-switch v-else-if="f.control === 'switch'" v-model="levelConfigs[form.configLevel][f.key] as boolean" />
            <el-input-number
              v-else-if="f.control === 'number'"
              v-model="levelConfigs[form.configLevel][f.key] as number"
              :min="f.min"
              :max="f.max"
              :step="f.step ?? 1"
            />
            <el-input
              v-else
              v-model="levelConfigs[form.configLevel][f.key] as string"
              :placeholder="f.placeholder"
            />
          </el-form-item>
        </template>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dc-page {
  min-height: calc(100vh - var(--portal-header-height) - 40px);
  background: #f5f7fa;
  padding: 16px 24px 24px;
}
.dc-page--embed {
  min-height: 0;
  background: transparent;
  padding: 0;
}
.dc-query { margin-bottom: 12px; }
.dc-table-card { background: #fff; }
.dc-pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
