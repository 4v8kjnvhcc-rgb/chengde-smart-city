<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'

interface MetaModel {
  id: number
  modelCode: string
  modelNameZh: string
  modelNameEn?: string
  modelType: string
  dataLength?: number
  requiredFlag?: number
  componentType?: string
  status: string
  contentJson?: string
}

const models = ref<MetaModel[]>([])
const compare = ref<Record<string, unknown> | null>(null)
const editMode = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  modelNameZh: '',
  modelNameEn: '',
  modelType: 'TABLE',
  requiredFlag: 0,
  componentType: 'FORM',
  contentJson: '[{"code":"col1","name":"字段1","type":"VARCHAR","length":64,"required":true}]',
})
const compareForm = reactive({ leftId: undefined as number | undefined, rightId: undefined as number | undefined })

async function load() {
  models.value = (await api.get('/governance/platform/metadata/models')).data || []
}

function resetForm() {
  editMode.value = false
  editingId.value = null
  form.modelNameZh = ''
  form.modelNameEn = ''
  form.modelType = 'TABLE'
  form.requiredFlag = 0
  form.componentType = 'FORM'
  form.contentJson = '[{"code":"col1","name":"字段1","type":"VARCHAR","length":64,"required":true}]'
}

function startEdit(row: MetaModel) {
  editMode.value = true
  editingId.value = row.id
  form.modelNameZh = row.modelNameZh
  form.modelNameEn = row.modelNameEn || ''
  form.modelType = row.modelType
  form.requiredFlag = row.requiredFlag ?? 0
  form.componentType = row.componentType || 'FORM'
  form.contentJson = row.contentJson || '[]'
}

async function create() {
  if (!form.modelNameZh) return
  await api.post('/governance/platform/metadata/models', { ...form })
  ElMessage.success('元模型已创建（草稿）')
  resetForm()
  await load()
}

async function saveEdit() {
  if (!editingId.value || !form.modelNameZh) return
  const { modelNameEn: _en, ...rest } = form
  await api.put(`/governance/platform/metadata/models/${editingId.value}`, { ...rest })
  ElMessage.success('已保存，需重新发布生效')
  resetForm()
  await load()
}

async function publish(id: number) {
  await api.post(`/governance/platform/metadata/models/${id}/publish`)
  ElMessage.success('已发布')
  await load()
}

async function offlineModel(id: number) {
  await api.post(`/governance/platform/metadata/models/${id}/offline`)
  ElMessage.success('已下线')
  await load()
}

async function doCompare() {
  if (!compareForm.leftId || !compareForm.rightId) return
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
  ElMessage.success(`已导出 ${res.data.count} 个模型`)
}

async function doImport() {
  const exported = (await api.get('/governance/platform/metadata/models/export')).data
  const payload = {
    models: (exported.models || []).slice(0, 1).map((m: MetaModel) => ({
      modelNameZh: `${m.modelNameZh}-导入`,
      modelNameEn: `${m.modelNameEn || m.modelCode}_imp_${Date.now()}`,
      modelType: m.modelType,
      requiredFlag: m.requiredFlag,
      componentType: m.componentType,
      contentJson: m.contentJson,
    })),
  }
  const n = (await api.post('/governance/platform/metadata/models/import', payload)).data
  ElMessage.success(`已导入 ${n} 个`)
  await load()
}

onMounted(load)
</script>

<template>
  <PageCard title="M089 元模型管理">
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item label="中文名" class="portal-field-lg"><el-input v-model="form.modelNameZh" /></el-form-item>
      <el-form-item label="英文名" class="portal-field-md">
        <el-input v-model="form.modelNameEn" :disabled="editMode" :placeholder="editMode ? '英文名不可编辑' : ''" />
      </el-form-item>
      <el-form-item label="类型" class="portal-field-sm">
        <el-select v-model="form.modelType"><el-option label="表" value="TABLE" /><el-option label="字段" value="COLUMN" /></el-select>
      </el-form-item>
      <el-form-item label="必填" class="portal-field-xs">
        <el-select v-model="form.requiredFlag"><el-option label="否" :value="0" /><el-option label="是" :value="1" /></el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button v-if="!editMode" type="primary" @click="create">新建</el-button>
        <el-button v-else type="primary" @click="saveEdit">保存编辑</el-button>
        <el-button v-if="editMode" @click="resetForm">取消</el-button>
        <el-button @click="doExport">导出</el-button>
        <el-button @click="doImport">导入样例</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="models" stripe size="small">
      <el-table-column prop="modelCode" label="编码" width="140" />
      <el-table-column prop="modelNameZh" label="中文名" />
      <el-table-column prop="modelNameEn" label="英文名" width="120" />
      <el-table-column prop="modelType" label="类型" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="startEdit(row)">编辑</el-button>
          <el-button v-if="row.status !== 'PUBLISHED'" link type="primary" @click="publish(row.id)">发布</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link @click="offlineModel(row.id)">下线</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-divider />
    <el-form inline class="portal-inline-form">
      <el-form-item label="比对左" class="portal-field-lg">
        <el-select v-model="compareForm.leftId" clearable>
          <el-option v-for="m in models" :key="m.id" :label="m.modelNameZh" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="比对右" class="portal-field-lg">
        <el-select v-model="compareForm.rightId" clearable>
          <el-option v-for="m in models" :key="'r'+m.id" :label="m.modelNameZh" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item class="portal-form-actions"><el-button @click="doCompare">模型比对</el-button></el-form-item>
    </el-form>
    <el-alert v-if="compare" type="info" :closable="false" :title="`内容相同=${compare.sameContent} · 类型相同=${compare.sameType}`" />
  </PageCard>
</template>
