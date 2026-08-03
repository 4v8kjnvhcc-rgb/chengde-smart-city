<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { ingestionApi, type DataSource } from '@/views/exchange/ingestion/useIngestionHub'

interface StandardItem {
  id: number
  itemCode: string
  itemName: string
  itemType: string
  dataType?: string
  dataFormat?: string
  valueDomain?: string
  businessDefinition?: string
  businessRule?: string
  referenceStandard?: string
  category?: string
  sensitivity?: string
  publishStatus: string
  versionNo?: number
  publishedBy?: string
  publishedAt?: string
}

interface VersionRow {
  id: number
  versionNo: number
  changeSummary?: string
  publishedBy?: string
  publishedAt?: string
  snapshotJson?: string
}

interface CodebookRow {
  id: number
  standardItemId: number
  codeValue: string
  codeName: string
  codeDesc?: string
  sortOrder?: number
}

interface NamingRow {
  id: number
  namingType: string
  namingName: string
  standardContent: string
  description?: string
  status: string
  createdBy?: string
  createdAt?: string
}

interface MappingRow {
  id: number
  standardItemId: number
  sourceSystem?: string
  sourceTable?: string
  sourceColumn?: string
  mappingStatus: string
  matchScore?: number
  remark?: string
}

const activeTab = ref('element')
const items = ref<StandardItem[]>([])
const loading = ref(false)
const editMode = ref(false)
const editingId = ref<number | null>(null)
const query = reactive({ keyword: '', publishStatus: '' })

const form = reactive({
  itemCode: '',
  itemName: '',
  itemType: 'ELEMENT',
  dataType: 'VARCHAR',
  dataFormat: '',
  valueDomain: '',
  businessDefinition: '',
  businessRule: '',
  referenceStandard: '',
  category: '',
  sensitivity: 'L2',
})

const versionDrawer = ref(false)
const versionRows = ref<VersionRow[]>([])
const versionDetail = ref('')
const currentItemId = ref<number | null>(null)
const currentItemName = ref('')

// ---- codebook ----
const codeItems = ref<StandardItem[]>([])
const selectedCodeItemId = ref<number | undefined>()
const codebookRows = ref<CodebookRow[]>([])
const codebookLoading = ref(false)
const codeForm = reactive({ itemCode: '', itemName: '', referenceStandard: '' })
const codebookForm = reactive({ codeValue: '', codeName: '', codeDesc: '', sortOrder: 0 })
const importJson = ref('[{"codeValue":"01","codeName":"示例"}]')
const dictForm = reactive({ datasourceId: undefined as number | undefined, table: '', codeColumn: 'code', nameColumn: 'name', descColumn: '' })
const dataSources = ref<DataSource[]>([])
const fromDictVisible = ref(false)

// ---- naming ----
const namingRows = ref<NamingRow[]>([])
const namingLoading = ref(false)
const namingForm = reactive({
  namingType: 'TABLE',
  namingName: '',
  standardContent: '^[a-z][a-z0-9_]*$',
  description: '',
})
const validateForm = reactive({ namingId: undefined as number | undefined, namingType: 'TABLE', name: '' })
const validateResult = ref('')

// ---- mapping ----
const mappingDialog = ref(false)
const mappingItem = ref<StandardItem | null>(null)
const mappingRows = ref<MappingRow[]>([])
const mappingForm = reactive({
  sourceSystem: '',
  sourceTable: '',
  sourceColumn: '',
  mappingStatus: 'MAPPED',
  matchScore: 100 as number | undefined,
  remark: '',
})

async function load() {
  loading.value = true
  try {
    const res = await api.get('/governance/standards', {
      params: {
        itemType: 'ELEMENT',
        keyword: query.keyword || undefined,
        publishStatus: query.publishStatus || undefined,
      },
    })
    items.value = res.data || []
  } catch {
    ElMessage.error('加载数据元失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editMode.value = false
  editingId.value = null
  form.itemCode = ''
  form.itemName = ''
  form.itemType = 'ELEMENT'
  form.dataType = 'VARCHAR'
  form.dataFormat = ''
  form.valueDomain = ''
  form.businessDefinition = ''
  form.businessRule = ''
  form.referenceStandard = ''
  form.category = ''
  form.sensitivity = 'L2'
}

function startEdit(row: StandardItem) {
  if (row.publishStatus === 'PUBLISHED') {
    ElMessage.warning('已发布不可编辑，请先下线')
    return
  }
  editMode.value = true
  editingId.value = row.id
  form.itemCode = row.itemCode
  form.itemName = row.itemName
  form.itemType = row.itemType || 'ELEMENT'
  form.dataType = row.dataType || ''
  form.dataFormat = row.dataFormat || ''
  form.valueDomain = row.valueDomain || ''
  form.businessDefinition = row.businessDefinition || ''
  form.businessRule = row.businessRule || ''
  form.referenceStandard = row.referenceStandard || ''
  form.category = row.category || ''
  form.sensitivity = row.sensitivity || 'L2'
}

async function create() {
  if (!form.itemName) return
  await api.post('/governance/standards', { ...form })
  ElMessage.success('已创建（草稿）')
  resetForm()
  await load()
}

async function saveEdit() {
  if (!editingId.value || !form.itemName) return
  await api.put(`/governance/standards/${editingId.value}`, { ...form })
  ElMessage.success('已保存')
  resetForm()
  await load()
}

async function publish(id: number) {
  await api.post(`/governance/standards/${id}/publish`)
  ElMessage.success('已发布')
  await load()
}

async function offline(id: number) {
  await api.post(`/governance/standards/${id}/offline`)
  ElMessage.success('已下线')
  await load()
}

async function remove(id: number) {
  await ElMessageBox.confirm('确认删除该草稿数据元？', '删除确认', { type: 'warning' })
  await api.delete(`/governance/standards/${id}`)
  ElMessage.success('已删除')
  await load()
}

async function openVersions(row: StandardItem) {
  currentItemId.value = row.id
  currentItemName.value = row.itemName
  versionRows.value = (await api.get(`/governance/standards/${row.id}/versions`)).data || []
  versionDetail.value = ''
  versionDrawer.value = true
}

async function showVersion(row: VersionRow) {
  if (!currentItemId.value) {
    versionDetail.value = row.snapshotJson || ''
    return
  }
  const res = await api.get(`/governance/standards/${currentItemId.value}/versions/${row.versionNo}`)
  versionDetail.value = res.data?.snapshotJson || ''
}

async function loadCodeItems() {
  codeItems.value = (await api.get('/governance/standards', { params: { itemType: 'CODE' } })).data || []
  if (!selectedCodeItemId.value && codeItems.value.length) {
    selectedCodeItemId.value = codeItems.value[0].id
  }
}

async function loadCodebook() {
  if (!selectedCodeItemId.value) {
    codebookRows.value = []
    return
  }
  codebookLoading.value = true
  try {
    codebookRows.value = (await api.get(`/governance/standards/${selectedCodeItemId.value}/codebook`)).data || []
  } catch {
    ElMessage.error('加载码表失败')
  } finally {
    codebookLoading.value = false
  }
}

async function createCodeItem() {
  if (!codeForm.itemName) return
  const id = (await api.post('/governance/standards', {
    itemCode: codeForm.itemCode || undefined,
    itemName: codeForm.itemName,
    itemType: 'CODE',
    referenceStandard: codeForm.referenceStandard || undefined,
  })).data
  ElMessage.success('编码标准已创建')
  codeForm.itemCode = ''
  codeForm.itemName = ''
  codeForm.referenceStandard = ''
  await loadCodeItems()
  selectedCodeItemId.value = id
  await loadCodebook()
}

async function addCodebook() {
  if (!selectedCodeItemId.value || !codebookForm.codeValue || !codebookForm.codeName) return
  await api.post(`/governance/standards/${selectedCodeItemId.value}/codebook`, { ...codebookForm })
  ElMessage.success('码值已添加')
  codebookForm.codeValue = ''
  codebookForm.codeName = ''
  codebookForm.codeDesc = ''
  await loadCodebook()
}

async function removeCodebook(id: number) {
  await api.delete(`/governance/standards/codebook/${id}`)
  ElMessage.success('已删除')
  await loadCodebook()
}

async function doImport() {
  if (!selectedCodeItemId.value) return
  let items: unknown[]
  try {
    items = JSON.parse(importJson.value)
  } catch {
    ElMessage.error('JSON 格式不正确')
    return
  }
  const res = await api.post(`/governance/standards/${selectedCodeItemId.value}/codebook/import`, { items, replace: true })
  ElMessage.success(`导入 ${res.data?.imported || 0} 条`)
  await loadCodebook()
}

async function doExport() {
  if (!selectedCodeItemId.value) return
  const res = await api.get(`/governance/standards/${selectedCodeItemId.value}/codebook/export`)
  importJson.value = JSON.stringify(res.data || [], null, 2)
  ElMessage.success('已导出到下方 JSON')
}

async function openFromDict() {
  if (!dataSources.value.length) {
    try {
      dataSources.value = (await ingestionApi.dataSources()).data || []
    } catch { /* ignore */ }
  }
  fromDictVisible.value = true
}

async function doFromDict() {
  if (!selectedCodeItemId.value || !dictForm.table) return
  const res = await api.post(`/governance/standards/${selectedCodeItemId.value}/codebook/from-dict`, {
    datasourceId: dictForm.datasourceId,
    table: dictForm.table,
    codeColumn: dictForm.codeColumn,
    nameColumn: dictForm.nameColumn,
    descColumn: dictForm.descColumn || undefined,
    replace: true,
  })
  ElMessage.success(`读取 ${res.data?.readCount || 0} 条，导入 ${res.data?.imported || 0} 条`)
  fromDictVisible.value = false
  await loadCodebook()
}

async function loadNaming() {
  namingLoading.value = true
  try {
    namingRows.value = (await api.get('/governance/standards/naming')).data || []
  } catch {
    ElMessage.error('加载命名规范失败')
  } finally {
    namingLoading.value = false
  }
}

async function createNaming() {
  if (!namingForm.namingName || !namingForm.standardContent) return
  await api.post('/governance/standards/naming', { ...namingForm })
  ElMessage.success('已创建')
  namingForm.namingName = ''
  namingForm.description = ''
  await loadNaming()
}

async function removeNaming(id: number) {
  await ElMessageBox.confirm('确认删除该命名规范？', '删除确认', { type: 'warning' })
  await api.delete(`/governance/standards/naming/${id}`)
  ElMessage.success('已删除')
  await loadNaming()
}

async function doValidate() {
  const res = await api.post('/governance/standards/naming/validate', {
    namingId: validateForm.namingId,
    namingType: validateForm.namingType,
    name: validateForm.name,
  })
  validateResult.value = res.data?.valid
    ? `通过：${res.data.message}`
    : `未通过：${res.data?.message || ''}`
}

async function openMapping(row: StandardItem) {
  mappingItem.value = row
  mappingRows.value = (await api.get(`/governance/standards/${row.id}/mappings`)).data || []
  mappingForm.sourceSystem = ''
  mappingForm.sourceTable = ''
  mappingForm.sourceColumn = ''
  mappingForm.mappingStatus = 'MAPPED'
  mappingForm.matchScore = 100
  mappingForm.remark = ''
  mappingDialog.value = true
}

async function createMapping() {
  if (!mappingItem.value) return
  await api.post('/governance/standards/mappings', {
    standardItemId: mappingItem.value.id,
    ...mappingForm,
  })
  ElMessage.success('对标已添加')
  mappingRows.value = (await api.get(`/governance/standards/${mappingItem.value.id}/mappings`)).data || []
}

async function removeMapping(id: number) {
  await api.delete(`/governance/standards/mappings/${id}`)
  if (mappingItem.value) {
    mappingRows.value = (await api.get(`/governance/standards/${mappingItem.value.id}/mappings`)).data || []
  }
}

watch(selectedCodeItemId, () => { loadCodebook() })
watch(activeTab, (t) => {
  if (t === 'code') loadCodeItems().then(loadCodebook)
  if (t === 'naming') loadNaming()
})

onMounted(load)
</script>

<template>
  <div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="V3.0「数据标准体系」：数据元 / 编码 / 命名 / 标准文件；支持发布版本与业务映射。"
    />
    <el-tabs v-model="activeTab">
      <el-tab-pane label="数据元" name="element">
        <PageCard title="数据元标准">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="关键词" class="portal-field-md">
              <el-input v-model="query.keyword" clearable placeholder="编码/名称" @keyup.enter="load" />
            </el-form-item>
            <el-form-item label="发布状态" class="portal-field-sm">
              <el-select v-model="query.publishStatus" clearable placeholder="全部">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="已下线" value="OFFLINE" />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="load">查询</el-button>
            </el-form-item>
          </el-form>

          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="编码" class="portal-field-md"><el-input v-model="form.itemCode" :disabled="editMode" placeholder="可空自动生成" /></el-form-item>
            <el-form-item label="名称" class="portal-field-lg"><el-input v-model="form.itemName" /></el-form-item>
            <el-form-item label="数据类型" class="portal-field-sm">
              <el-select v-model="form.dataType">
                <el-option label="VARCHAR" value="VARCHAR" />
                <el-option label="INT" value="INT" />
                <el-option label="DATE" value="DATE" />
                <el-option label="DECIMAL" value="DECIMAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="格式" class="portal-field-sm"><el-input v-model="form.dataFormat" /></el-form-item>
            <el-form-item label="引用标准" class="portal-field-md"><el-input v-model="form.referenceStandard" /></el-form-item>
            <el-form-item label="分类" class="portal-field-sm"><el-input v-model="form.category" /></el-form-item>
            <el-form-item label="业务定义" class="portal-field-xl"><el-input v-model="form.businessDefinition" /></el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button v-if="!editMode" type="primary" @click="create">新建</el-button>
              <el-button v-else type="primary" @click="saveEdit">保存</el-button>
              <el-button v-if="editMode" @click="resetForm">取消</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="loading" :data="items" stripe size="small">
            <el-table-column prop="itemCode" label="编码" width="140" />
            <el-table-column prop="itemName" label="名称" min-width="120" />
            <el-table-column prop="dataType" label="类型" width="90" />
            <el-table-column prop="referenceStandard" label="引用标准" min-width="120" />
            <el-table-column label="发布状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.publishStatus)" size="small">{{ statusLabel(row.publishStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="versionNo" label="版本" width="70" />
            <el-table-column label="操作" width="320" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="startEdit(row)">编辑</el-button>
                <el-button v-if="row.publishStatus !== 'PUBLISHED'" link type="success" @click="publish(row.id)">发布</el-button>
                <el-button v-if="row.publishStatus === 'PUBLISHED'" link @click="offline(row.id)">下线</el-button>
                <el-button link @click="openVersions(row)">版本</el-button>
                <el-button link type="warning" @click="openMapping(row)">对标</el-button>
                <el-button v-if="row.publishStatus === 'DRAFT'" link type="danger" @click="remove(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="编码标准" name="code" lazy>
        <PageCard title="编码标准（码表）">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="编码" class="portal-field-md"><el-input v-model="codeForm.itemCode" placeholder="可空" /></el-form-item>
            <el-form-item label="名称" class="portal-field-lg"><el-input v-model="codeForm.itemName" /></el-form-item>
            <el-form-item label="引用标准" class="portal-field-md"><el-input v-model="codeForm.referenceStandard" /></el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="createCodeItem">新建编码标准</el-button>
            </el-form-item>
          </el-form>

          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="当前标准" class="portal-field-xl">
              <el-select v-model="selectedCodeItemId" placeholder="选择编码标准" filterable>
                <el-option v-for="c in codeItems" :key="c.id" :label="`${c.itemName} (${c.itemCode})`" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button @click="doExport">导出 JSON</el-button>
              <el-button @click="doImport">导入 JSON</el-button>
              <el-button @click="openFromDict">从字典导入</el-button>
            </el-form-item>
          </el-form>

          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="码值" class="portal-field-sm"><el-input v-model="codebookForm.codeValue" /></el-form-item>
            <el-form-item label="名称" class="portal-field-md"><el-input v-model="codebookForm.codeName" /></el-form-item>
            <el-form-item label="说明" class="portal-field-lg"><el-input v-model="codebookForm.codeDesc" /></el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="addCodebook">添加码值</el-button>
            </el-form-item>
          </el-form>

          <el-input v-model="importJson" type="textarea" :rows="3" placeholder="导入/导出 JSON" style="margin-bottom:12px" />

          <el-table v-loading="codebookLoading" :data="codebookRows" stripe size="small">
            <el-table-column prop="codeValue" label="码值" width="120" />
            <el-table-column prop="codeName" label="名称" min-width="140" />
            <el-table-column prop="codeDesc" label="说明" min-width="160" />
            <el-table-column prop="sortOrder" label="排序" width="70" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeCodebook(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="命名规范" name="naming" lazy>
        <PageCard title="命名规范">
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="类型" class="portal-field-sm">
              <el-select v-model="namingForm.namingType">
                <el-option label="表" value="TABLE" />
                <el-option label="字段" value="COLUMN" />
                <el-option label="API" value="API" />
                <el-option label="其它" value="OTHER" />
              </el-select>
            </el-form-item>
            <el-form-item label="名称" class="portal-field-md"><el-input v-model="namingForm.namingName" /></el-form-item>
            <el-form-item label="正则/规则" class="portal-field-xl"><el-input v-model="namingForm.standardContent" /></el-form-item>
            <el-form-item label="说明" class="portal-field-md"><el-input v-model="namingForm.description" /></el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="createNaming">新建</el-button>
            </el-form-item>
          </el-form>

          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item label="校验规范" class="portal-field-lg">
              <el-select v-model="validateForm.namingId" clearable placeholder="可选已有规范">
                <el-option v-for="n in namingRows" :key="n.id" :label="n.namingName" :value="n.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="待检名称" class="portal-field-md"><el-input v-model="validateForm.name" /></el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="doValidate">校验</el-button>
            </el-form-item>
          </el-form>
          <el-alert v-if="validateResult" :title="validateResult" :type="validateResult.startsWith('通过') ? 'success' : 'warning'" show-icon :closable="false" style="margin-bottom:12px" />

          <el-table v-loading="namingLoading" :data="namingRows" stripe size="small">
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ $statusLabel(row.namingType) }}</template>
            </el-table-column>
            <el-table-column prop="namingName" label="名称" min-width="120" />
            <el-table-column prop="standardContent" label="规则内容" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="danger" @click="removeNaming(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </PageCard>
      </el-tab-pane>

      <el-tab-pane label="标准文件" name="file">
        <PageCard title="标准文件">
          <el-empty description="标准文件管理后续迭代开放" />
        </PageCard>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="versionDrawer" :title="`版本历史 · ${currentItemName}`" size="480px">
      <el-table :data="versionRows" stripe size="small" @row-click="showVersion">
        <el-table-column prop="versionNo" label="版本" width="70" />
        <el-table-column prop="changeSummary" label="说明" />
        <el-table-column prop="publishedBy" label="发布人" width="90" />
        <el-table-column prop="publishedAt" label="时间" width="160" />
      </el-table>
      <el-divider>快照</el-divider>
      <pre style="white-space:pre-wrap;font-size:12px;max-height:280px;overflow:auto">{{ versionDetail || '点击版本行查看快照' }}</pre>
    </el-drawer>

    <el-dialog v-model="fromDictVisible" title="从数据字典导入" width="520px">
      <el-form label-width="100px">
        <el-form-item label="数据源">
          <el-select v-model="dictForm.datasourceId" clearable placeholder="空则用平台库" style="width:100%">
            <el-option v-for="d in dataSources" :key="d.id" :label="d.sourceName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="表名"><el-input v-model="dictForm.table" /></el-form-item>
        <el-form-item label="码值列"><el-input v-model="dictForm.codeColumn" /></el-form-item>
        <el-form-item label="名称列"><el-input v-model="dictForm.nameColumn" /></el-form-item>
        <el-form-item label="说明列"><el-input v-model="dictForm.descColumn" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fromDictVisible = false">取消</el-button>
        <el-button type="primary" @click="doFromDict">导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mappingDialog" :title="`对标 · ${mappingItem?.itemName || ''}`" width="720px">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="来源系统" class="portal-field-md"><el-input v-model="mappingForm.sourceSystem" /></el-form-item>
        <el-form-item label="表" class="portal-field-md"><el-input v-model="mappingForm.sourceTable" /></el-form-item>
        <el-form-item label="字段" class="portal-field-md"><el-input v-model="mappingForm.sourceColumn" /></el-form-item>
        <el-form-item label="状态" class="portal-field-sm">
          <el-select v-model="mappingForm.mappingStatus">
            <el-option label="已对标" value="MAPPED" />
            <el-option label="部分对标" value="PARTIAL" />
            <el-option label="未对标" value="UNMAPPED" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="createMapping">添加</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="mappingRows" stripe size="small">
        <el-table-column prop="sourceSystem" label="系统" width="120" />
        <el-table-column prop="sourceTable" label="表" width="120" />
        <el-table-column prop="sourceColumn" label="字段" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.mappingStatus)" size="small">{{ statusLabel(row.mappingStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="matchScore" label="匹配分" width="80" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeMapping(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>
