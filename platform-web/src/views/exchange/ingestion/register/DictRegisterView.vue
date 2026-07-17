<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { downloadText, ingestionApi, useIngestionLoading, type Dict, type DictItem } from '../useIngestionHub'

const props = defineProps<{ module: string }>()
const { loading, loadError, withLoad } = useIngestionLoading()
const dicts = ref<Dict[]>([])
const selectedIds = ref<number[]>([])
const keyword = ref('')
const dictForm = reactive({ dictCode: '', dictName: '', dictType: 'STANDARD', itemCount: 0 })
const itemDialog = ref(false)
const editDict = ref<Dict | null>(null)
const dictItems = ref<DictItem[]>([])
const itemForm = reactive({ itemKey: '', itemValue: '', sortOrder: 0 })
const editingItemId = ref<number | null>(null)
const importText = ref('')
const exportDialog = ref(false)
const exportIds = ref<number[]>([])

const isManage = computed(() => props.module === 'm050')
const title = computed(() => (isManage.value ? '数据字典管理' : '数据字典登记'))

async function reload() {
  await withLoad(async () => { dicts.value = (await ingestionApi.dicts(keyword.value || undefined)).data })
}

async function createDict() {
  if (!dictForm.dictName) return
  await ingestionApi.createDict({ ...dictForm })
  dictForm.dictName = ''
  dictForm.dictCode = ''
  await reload()
}

async function downloadTemplate() {
  const res = await ingestionApi.dictTemplate()
  downloadText('dict_import_template.csv', res.data)
}

async function doImport() {
  if (!importText.value.trim()) {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.csv,.xlsx,.xls,text/csv'
    input.onchange = async () => {
      const file = input.files?.[0]
      if (!file) return
      const text = await file.text()
      const res = await ingestionApi.importDict(text)
      ElMessage.success(`已导入 ${res.data.importedRows} 行`)
      await reload()
    }
    input.click()
    return
  }
  const res = await ingestionApi.importDict(importText.value)
  ElMessage.success(`已导入 ${res.data.importedRows} 行`)
  importText.value = ''
  await reload()
}

function openExport() {
  exportIds.value = selectedIds.value.length ? [...selectedIds.value] : dicts.value.map(d => d.id)
  exportDialog.value = true
}

async function doExport() {
  const res = await ingestionApi.exportDict(exportIds.value)
  downloadText('dict_export.csv', res.data)
  exportDialog.value = false
  ElMessage.success('导出完成')
}

async function deleteSelected() {
  if (!selectedIds.value.length) return
  await ingestionApi.deleteDicts(selectedIds.value)
  selectedIds.value = []
  await reload()
}

async function openItems(row: Dict) {
  editDict.value = row
  dictItems.value = (await ingestionApi.dictItems(row.id)).data
  itemDialog.value = true
}

async function saveItem() {
  if (!editDict.value || !itemForm.itemKey || !itemForm.itemValue) return
  if (editingItemId.value) {
    await ingestionApi.updateDictItem(editingItemId.value, { ...itemForm })
  } else {
    await ingestionApi.createDictItem(editDict.value.id, { ...itemForm })
  }
  itemForm.itemKey = ''
  itemForm.itemValue = ''
  editingItemId.value = null
  dictItems.value = (await ingestionApi.dictItems(editDict.value.id)).data
  await reload()
}

function editItem(row: DictItem) {
  editingItemId.value = row.id
  itemForm.itemKey = row.itemKey
  itemForm.itemValue = row.itemValue
  itemForm.sortOrder = row.sortOrder
}

async function removeItem(row: DictItem) {
  await ingestionApi.deleteDictItem(row.id)
  if (editDict.value) dictItems.value = (await ingestionApi.dictItems(editDict.value.id)).data
  await reload()
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard :title="title">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item v-if="isManage" label="查询" class="portal-field-md">
          <el-input v-model="keyword" placeholder="编码/名称" clearable @keyup.enter="reload" />
        </el-form-item>
        <el-form-item label="字典编码" class="portal-field-md"><el-input v-model="dictForm.dictCode" placeholder="可选" /></el-form-item>
        <el-form-item label="字典名称" class="portal-field-md"><el-input v-model="dictForm.dictName" /></el-form-item>
        <el-form-item label="类型" class="portal-field-sm">
          <el-select v-model="dictForm.dictType">
            <el-option label="标准" value="STANDARD" />
            <el-option label="业务" value="BUSINESS" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="createDict">新增字典</el-button>
          <el-button v-if="isManage" @click="reload">查询</el-button>
          <el-button @click="downloadTemplate">下载模板</el-button>
          <el-button @click="doImport">导入 Excel/CSV</el-button>
          <el-button @click="openExport">导出</el-button>
          <el-button v-if="isManage" type="danger" :disabled="!selectedIds.length" @click="deleteSelected">删除</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="dicts" stripe @selection-change="(rows: Dict[]) => selectedIds = rows.map(r => r.id)">
        <el-table-column v-if="isManage" type="selection" width="45" />
        <el-table-column prop="dictCode" label="编码" width="160" />
        <el-table-column prop="dictName" label="名称" min-width="160" />
        <el-table-column prop="dictType" label="类型" width="100" />
        <el-table-column prop="itemCount" label="项数" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column v-if="isManage" label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openItems(row)">查看/编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
    <el-dialog v-model="itemDialog" :title="`字典值 - ${editDict?.dictName}`" width="560px">
      <el-form inline size="small" class="portal-inline-form portal-inline-form--sm portal-inline-form--block">
        <el-form-item label="键" class="portal-field-sm"><el-input v-model="itemForm.itemKey" /></el-form-item>
        <el-form-item label="值" class="portal-field-md"><el-input v-model="itemForm.itemValue" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" size="small" @click="saveItem">{{ editingItemId ? '保存' : '新增' }}</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="dictItems" size="small" stripe>
        <el-table-column prop="itemKey" label="键" width="100" />
        <el-table-column prop="itemValue" label="值" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link @click="editItem(row)">编辑</el-button>
            <el-button link type="danger" @click="removeItem(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
    <el-dialog v-model="exportDialog" title="导出数据字典" width="400px">
      <p>已选 {{ exportIds.length }} 个字典，确认导出为 CSV？</p>
      <template #footer>
        <el-button @click="exportDialog = false">取消</el-button>
        <el-button type="primary" @click="doExport">导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>
