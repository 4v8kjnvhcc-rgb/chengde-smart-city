<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import {
  FIELD_TYPE_OPTIONS,
  parseFieldDefs,
  stringifyFieldDefs,
  type MetaFieldDef,
} from './meta-labels'

interface MetaModel {
  id: number
  modelCode: string
  modelNameZh: string
  modelNameEn?: string
  modelType: string
  componentType?: string
  status: string
  contentJson?: string
}

interface CatalogEntry {
  id: number
  entryCode: string
  entryName: string
  entryType: string
}

interface FieldDiff {
  added?: string[]
  removed?: string[]
  changed?: string[]
}

interface BindingTable {
  id: number
  entryCode: string
  entryName: string
  conformStatus?: string
  dataLayer?: string
}

interface BindingTask {
  id: number
  taskName: string
  status: string
}

const MODEL_TYPE_OPTIONS = [
  { label: '表模型', value: 'TABLE' },
  { label: '字段模型', value: 'COLUMN' },
]

const COMPONENT_TYPE_OPTIONS = [
  { label: '表单', value: 'FORM' },
  { label: '表格', value: 'TABLE' },
  { label: '列表', value: 'LIST' },
]

const models = ref<MetaModel[]>([])
const {
  page: modelPage,
  pageSize: modelPageSize,
  paged: pagedModels,
  total: modelTotal,
  resetPage: resetModelPage,
} = useClientPager(models)
const compare = ref<Record<string, unknown> | null>(null)
const compareForm = reactive({ leftId: undefined as number | undefined, rightId: undefined as number | undefined })

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({
  modelNameZh: '',
  modelNameEn: '',
  modelType: 'TABLE',
  componentType: 'FORM',
})
const fieldRows = ref<MetaFieldDef[]>([])

const importInputRef = ref<HTMLInputElement | null>(null)

const fromEntryVisible = ref(false)
const fromEntryLoading = ref(false)
const catalogEntries = ref<CatalogEntry[]>([])
const fromEntryId = ref<number | undefined>()

const detailVisible = ref(false)
const detailModel = ref<MetaModel | null>(null)
const bindingsLoading = ref(false)
const bindingTables = ref<BindingTable[]>([])
const bindingTasks = ref<BindingTask[]>([])
const recheckResult = ref<Record<string, unknown> | null>(null)
const rechecking = ref(false)

const fieldDiff = computed(() => (compare.value?.fieldDiff as FieldDiff | undefined) ?? null)

function emptyField(): MetaFieldDef {
  return { code: '', name: '', type: 'VARCHAR', length: 64, required: false, primaryKey: false }
}

function resetDialogForm() {
  form.modelNameZh = ''
  form.modelNameEn = ''
  form.modelType = 'TABLE'
  form.componentType = 'FORM'
  fieldRows.value = [emptyField()]
  editingId.value = null
}

function openCreateDialog() {
  dialogMode.value = 'create'
  resetDialogForm()
  dialogVisible.value = true
}

function openEditDialog(row: MetaModel) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.modelNameZh = row.modelNameZh
  form.modelNameEn = row.modelNameEn || ''
  form.modelType = row.modelType
  form.componentType = row.componentType || 'FORM'
  const parsed = parseFieldDefs(row.contentJson)
  fieldRows.value = parsed.length ? parsed : [emptyField()]
  dialogVisible.value = true
}

function addFieldRow() {
  fieldRows.value.push(emptyField())
}

function removeFieldRow(index: number) {
  if (fieldRows.value.length <= 1) {
    fieldRows.value[0] = emptyField()
    return
  }
  fieldRows.value.splice(index, 1)
}

function buildPayload() {
  const validFields = fieldRows.value.filter(f => f.code.trim())
  return {
    modelNameZh: form.modelNameZh.trim(),
    modelNameEn: form.modelNameEn.trim() || undefined,
    modelType: form.modelType,
    componentType: form.componentType,
    contentJson: stringifyFieldDefs(validFields),
  }
}

async function loadModels() {
  models.value = (await api.get('/governance/platform/metadata/models')).data || []
  resetModelPage()
}

async function saveDialog() {
  if (!form.modelNameZh.trim()) {
    ElMessage.warning('请填写中文名称')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (dialogMode.value === 'create') {
      await api.post('/governance/platform/metadata/models', payload)
      ElMessage.success('元模型已创建（草稿）')
    } else if (editingId.value) {
      await api.put(`/governance/platform/metadata/models/${editingId.value}`, payload)
      ElMessage.success('已保存，需重新发布生效')
    }
    dialogVisible.value = false
    await loadModels()
  } finally {
    saving.value = false
  }
}

async function publish(id: number) {
  await api.post(`/governance/platform/metadata/models/${id}/publish`)
  ElMessage.success('已发布')
  await loadModels()
}

async function offlineModel(id: number) {
  await api.post(`/governance/platform/metadata/models/${id}/offline`)
  ElMessage.success('已下线')
  await loadModels()
}

async function doCompare() {
  if (!compareForm.leftId || !compareForm.rightId) {
    ElMessage.warning('请选择两个模型')
    return
  }
  compare.value = (await api.get('/governance/platform/metadata/models/compare', {
    params: { leftId: compareForm.leftId, rightId: compareForm.rightId },
  })).data
}

async function doExport() {
  const res = await api.get('/governance/platform/metadata/models/export')
  const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = 'meta-models.json'
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success(`已导出 ${res.data.count ?? 0} 个模型`)
}

function triggerImport() {
  importInputRef.value?.click()
}

async function onImportFile(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  try {
    const text = await file.text()
    const parsed = JSON.parse(text) as { models?: unknown[] }
    const modelsPayload = Array.isArray(parsed.models) ? parsed.models : (Array.isArray(parsed) ? parsed : [])
    if (!modelsPayload.length) {
      ElMessage.warning('JSON 中未找到 models 数组')
      return
    }
    const n = (await api.post('/governance/platform/metadata/models/import', { models: modelsPayload })).data
    ElMessage.success(`已导入 ${n} 个模型`)
    await loadModels()
  } catch {
    ElMessage.error('导入失败，请检查 JSON 格式')
  }
}

async function openFromEntryDialog() {
  fromEntryVisible.value = true
  fromEntryId.value = undefined
  fromEntryLoading.value = true
  try {
    const res = await api.get('/governance/platform/metadata/catalog/search', { params: { type: 'asset' } })
    const list: CatalogEntry[] = res.data || []
    catalogEntries.value = list.filter(e => e.entryType === 'TABLE')
  } finally {
    fromEntryLoading.value = false
  }
}

async function submitFromEntry() {
  if (!fromEntryId.value) {
    ElMessage.warning('请选择数据表条目')
    return
  }
  await api.post('/governance/platform/metadata/models/from-entry', { entryId: fromEntryId.value })
  ElMessage.success('已从目录条目生成元模型')
  fromEntryVisible.value = false
  await loadModels()
}

async function openDetail(row: MetaModel) {
  detailModel.value = row
  detailVisible.value = true
  recheckResult.value = null
  await loadBindings(row.id)
}

async function loadBindings(modelId: number) {
  bindingsLoading.value = true
  try {
    const res = await api.get(`/governance/platform/metadata/models/${modelId}/bindings`)
    bindingTables.value = res.data?.tables || []
    bindingTasks.value = res.data?.collectTasks || []
  } finally {
    bindingsLoading.value = false
  }
}

async function doRecheck() {
  if (!detailModel.value) return
  rechecking.value = true
  try {
    recheckResult.value = (await api.post(`/governance/platform/metadata/models/${detailModel.value.id}/recheck`)).data
    ElMessage.success('符合度复检完成')
    await loadBindings(detailModel.value.id)
  } finally {
    rechecking.value = false
  }
}

onMounted(loadModels)
</script>

<template>
  <PageCard title="元模型管理">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="openCreateDialog">新建</el-button>
        <el-button @click="doExport">导出 JSON</el-button>
        <el-button @click="triggerImport">导入 JSON</el-button>
        <el-button @click="openFromEntryDialog">从目录条目生成</el-button>
      </el-form-item>
    </el-form>
    <input ref="importInputRef" type="file" accept=".json,application/json" style="display:none" @change="onImportFile">

    <el-table :data="pagedModels" stripe size="small">
      <el-table-column label="名称" min-width="180">
        <template #default="{ row }">
          <div>{{ row.modelNameZh }}</div>
          <div class="meta-secondary">{{ row.modelCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ $statusLabel(row.modelType) }}</template>
      </el-table-column>
      <el-table-column label="组件" width="90">
        <template #default="{ row }">
          {{ COMPONENT_TYPE_OPTIONS.find(o => o.value === row.componentType)?.label || $statusLabel(row.componentType) || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button link type="primary" @click="openDetail(row)">绑定</el-button>
          <el-button v-if="row.status !== 'PUBLISHED'" link type="primary" @click="publish(row.id)">发布</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link @click="offlineModel(row.id)">下线</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination
      v-model:page="modelPage"
      v-model:page-size="modelPageSize"
      :total="modelTotal"
    />

    <el-divider content-position="left">模型比对</el-divider>
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="左模型" class="portal-field-lg">
        <el-select v-model="compareForm.leftId" clearable filterable placeholder="选择模型">
          <el-option v-for="m in models" :key="m.id" :label="m.modelNameZh" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="右模型" class="portal-field-lg">
        <el-select v-model="compareForm.rightId" clearable filterable placeholder="选择模型">
          <el-option v-for="m in models" :key="'r' + m.id" :label="m.modelNameZh" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="doCompare">比对</el-button>
      </el-form-item>
    </el-form>
    <template v-if="compare">
      <el-alert
        type="info"
        :closable="false"
        :title="`类型相同=${compare.sameType} · 内容相同=${compare.sameContent}`"
        style="margin-bottom:12px"
      />
      <div v-if="fieldDiff" class="meta-diff">
        <div v-if="fieldDiff.added?.length" class="meta-diff__block">
          <span class="meta-diff__label">新增字段：</span>
          <el-tag v-for="f in fieldDiff.added" :key="'a' + f" size="small" type="success" style="margin:2px">{{ f }}</el-tag>
        </div>
        <div v-if="fieldDiff.removed?.length" class="meta-diff__block">
          <span class="meta-diff__label">移除字段：</span>
          <el-tag v-for="f in fieldDiff.removed" :key="'r' + f" size="small" type="danger" style="margin:2px">{{ f }}</el-tag>
        </div>
        <div v-if="fieldDiff.changed?.length" class="meta-diff__block">
          <span class="meta-diff__label">变更字段：</span>
          <el-tag v-for="f in fieldDiff.changed" :key="'c' + f" size="small" type="warning" style="margin:2px">{{ f }}</el-tag>
        </div>
        <div v-if="!fieldDiff.added?.length && !fieldDiff.removed?.length && !fieldDiff.changed?.length" class="meta-secondary">
          字段结构无差异
        </div>
      </div>
    </template>

    <!-- 新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建元模型' : '编辑元模型'"
      width="860px"
      destroy-on-close
    >
      <el-form label-width="96px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="中文名称" required>
              <el-input v-model="form.modelNameZh" placeholder="业务可读名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="英文名称">
              <el-input
                v-model="form.modelNameEn"
                :disabled="dialogMode === 'edit'"
                :placeholder="dialogMode === 'edit' ? '编辑时不可修改' : '可选，留空自动生成'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型类型">
              <el-select v-model="form.modelType" style="width:100%">
                <el-option v-for="o in MODEL_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="组件类型">
              <el-select v-model="form.componentType" style="width:100%">
                <el-option v-for="o in COMPONENT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">字段设计</el-divider>
        <div style="margin-bottom:8px">
          <el-button size="small" @click="addFieldRow">添加字段</el-button>
        </div>
        <el-table :data="fieldRows" size="small" border max-height="320">
          <el-table-column label="编码" width="130">
            <template #default="{ row }">
              <el-input v-model="row.code" size="small" placeholder="code" />
            </template>
          </el-table-column>
          <el-table-column label="名称" width="130">
            <template #default="{ row }">
              <el-input v-model="row.name" size="small" placeholder="名称" />
            </template>
          </el-table-column>
          <el-table-column label="类型" width="130">
            <template #default="{ row }">
              <el-select v-model="row.type" size="small" filterable allow-create style="width:100%">
                <el-option v-for="t in FIELD_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="长度" width="90">
            <template #default="{ row }">
              <el-input-number v-model="row.length" size="small" :min="0" :controls="false" style="width:100%" />
            </template>
          </el-table-column>
          <el-table-column label="必填" width="70" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.required" />
            </template>
          </el-table-column>
          <el-table-column label="主键" width="70" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.primaryKey" />
            </template>
          </el-table-column>
          <el-table-column label="" width="60" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeFieldRow($index)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDialog">保存</el-button>
      </template>
    </el-dialog>

    <!-- 从目录条目生成 -->
    <el-dialog v-model="fromEntryVisible" title="从目录条目生成元模型" width="520px">
      <el-form label-width="88px">
        <el-form-item label="数据表">
          <el-select
            v-model="fromEntryId"
            filterable
            clearable
            placeholder="选择 TABLE 类型条目"
            style="width:100%"
            :loading="fromEntryLoading"
          >
            <el-option
              v-for="e in catalogEntries"
              :key="e.id"
              :label="`${e.entryName} (${e.entryCode})`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fromEntryVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFromEntry">生成</el-button>
      </template>
    </el-dialog>

    <!-- 绑定详情 -->
    <el-drawer v-model="detailVisible" :title="detailModel ? `绑定 · ${detailModel.modelNameZh}` : '绑定详情'" size="520px">
      <div v-loading="bindingsLoading">
        <div style="margin-bottom:12px">
          <el-button type="primary" size="small" :loading="rechecking" @click="doRecheck">符合度复检</el-button>
        </div>
        <el-alert
          v-if="recheckResult"
          type="success"
          :closable="false"
          :title="`复检：共 ${recheckResult.tableCount} 表 · 通过 ${recheckResult.pass} · 部分 ${recheckResult.partial} · 不通过 ${recheckResult.fail}`"
          style="margin-bottom:12px"
        />
        <el-divider content-position="left">绑定数据表</el-divider>
        <el-table :data="bindingTables" size="small" empty-text="暂无绑定表">
          <el-table-column prop="entryName" label="表名" />
          <el-table-column label="分层" width="80">
            <template #default="{ row }">{{ $statusLabel(row.dataLayer) }}</template>
          </el-table-column>
          <el-table-column label="符合度" width="90">
            <template #default="{ row }">
              <el-tag v-if="row.conformStatus" :type="$statusTagType(row.conformStatus)" size="small">
                {{ $statusLabel(row.conformStatus) }}
              </el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-divider content-position="left">关联采集任务</el-divider>
        <el-table :data="bindingTasks" size="small" empty-text="暂无关联任务">
          <el-table-column prop="taskName" label="任务" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </PageCard>
</template>

<style scoped>
.meta-secondary {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.meta-diff__block {
  margin-bottom: 8px;
}
.meta-diff__label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-right: 6px;
}
</style>
