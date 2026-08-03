<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { downloadText, ingestionApi, useIngestionLoading, type Dict, type DictItem } from '../useIngestionHub'

const props = defineProps<{ module: string }>()
const { loading, loadError, withLoad } = useIngestionLoading()
const dicts = ref<Dict[]>([])
const selectedIds = ref<number[]>([])
const keyword = ref('')
const dictDialog = ref(false)
const dictSaving = ref(false)
const editingDictId = ref<number | null>(null)
const dictForm = reactive({
  dictName: '',
  standardNo: '',
  remark: '',
})
const itemDialog = ref(false)
const editDict = ref<Dict | null>(null)
const dictItems = ref<DictItem[]>([])
const itemForm = reactive({ itemKey: '', itemValue: '', bizUsage: '', sortOrder: 0 })
const editingItemId = ref<number | null>(null)
const importText = ref('')
const exportDialog = ref(false)
const exportIds = ref<number[]>([])

const isManage = computed(() => props.module === 'm050')
const title = computed(() => (isManage.value ? '数据字典管理' : '数据字典登记'))

async function reload() {
  await withLoad(async () => {
    dicts.value = (await ingestionApi.dicts(keyword.value || undefined)).data
  })
}

function openCreateDict() {
  editingDictId.value = null
  dictForm.dictName = ''
  dictForm.standardNo = ''
  dictForm.remark = ''
  dictDialog.value = true
}

function openEditDict(row: Dict) {
  editingDictId.value = row.id
  dictForm.dictName = row.dictName || ''
  dictForm.standardNo = row.standardNo || ''
  dictForm.remark = row.remark || ''
  dictDialog.value = true
}

async function saveDict() {
  if (!dictForm.dictName.trim()) {
    ElMessage.warning('请填写字典名称')
    return
  }
  dictSaving.value = true
  const body = {
    dictName: dictForm.dictName.trim(),
    standardNo: dictForm.standardNo.trim(),
    remark: dictForm.remark.trim(),
  }
  try {
    if (editingDictId.value) {
      await ingestionApi.updateDict(editingDictId.value, body)
      ElMessage.success('字典已更新')
    } else {
      await ingestionApi.createDict(body)
      ElMessage.success('字典已登记')
    }
    dictDialog.value = false
    editingDictId.value = null
    await reload()
  } catch {
    ElMessage.error(editingDictId.value ? '更新失败' : '登记失败')
  } finally {
    dictSaving.value = false
  }
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
  exportIds.value = selectedIds.value.length ? [...selectedIds.value] : dicts.value.map((d) => d.id)
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
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个字典？`, '删除确认', { type: 'warning' })
  await ingestionApi.deleteDicts(selectedIds.value)
  selectedIds.value = []
  ElMessage.success('已删除')
  await reload()
}

async function openItems(row: Dict) {
  editDict.value = row
  dictItems.value = (await ingestionApi.dictItems(row.id)).data
  itemForm.itemKey = ''
  itemForm.itemValue = ''
  itemForm.bizUsage = ''
  editingItemId.value = null
  itemDialog.value = true
}

async function saveItem() {
  if (!editDict.value || !itemForm.itemKey.trim() || !itemForm.itemValue.trim()) {
    ElMessage.warning('请填写代码值与中文名称')
    return
  }
  const body = {
    itemKey: itemForm.itemKey.trim(),
    itemValue: itemForm.itemValue.trim(),
    bizUsage: itemForm.bizUsage.trim(),
    sortOrder: itemForm.sortOrder,
  }
  if (editingItemId.value) {
    await ingestionApi.updateDictItem(editingItemId.value, body)
  } else {
    await ingestionApi.createDictItem(editDict.value.id, body)
  }
  itemForm.itemKey = ''
  itemForm.itemValue = ''
  itemForm.bizUsage = ''
  editingItemId.value = null
  dictItems.value = (await ingestionApi.dictItems(editDict.value.id)).data
  await reload()
}

function editItem(row: DictItem) {
  editingItemId.value = row.id
  itemForm.itemKey = row.itemKey
  itemForm.itemValue = row.itemValue
  itemForm.bizUsage = row.bizUsage || ''
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
          <el-input v-model="keyword" placeholder="名称/标准依据" clearable @keyup.enter="reload" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="openCreateDict">新增字典</el-button>
          <el-button v-if="isManage" @click="reload">查询</el-button>
          <el-button @click="downloadTemplate">下载模板</el-button>
          <el-button @click="doImport">导入 Excel/CSV</el-button>
          <el-button @click="openExport">导出</el-button>
          <el-button v-if="isManage" type="danger" :disabled="!selectedIds.length" @click="deleteSelected">删除</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="dicts" stripe @selection-change="(rows: Dict[]) => selectedIds = rows.map((r) => r.id)">
        <el-table-column v-if="isManage" type="selection" width="45" />
        <el-table-column prop="dictName" label="字典名称" min-width="140" />
        <el-table-column prop="standardNo" label="标准依据" min-width="160" show-overflow-tooltip />
        <el-table-column prop="itemCount" label="字典项数" width="90" />
        <el-table-column prop="remark" label="说明" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDict(row)">编辑</el-button>
            <el-button link type="primary" @click="openItems(row)">字典项</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <el-dialog v-model="dictDialog" :title="editingDictId ? '编辑字典' : '新增字典'" width="520px" destroy-on-close>
      <el-form label-width="96px">
        <el-form-item label="字典名称" required>
          <el-input v-model="dictForm.dictName" placeholder="如：性别代码" />
        </el-form-item>
        <el-form-item label="标准依据">
          <el-input v-model="dictForm.standardNo" placeholder="如：GB/T 2261.1-2003" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="dictForm.remark" type="textarea" :rows="2" placeholder="用途说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialog = false">取消</el-button>
        <el-button type="primary" :loading="dictSaving" @click="saveDict">{{ editingDictId ? '保存' : '确定登记' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialog" :title="`字典项 · ${editDict?.dictName || ''}`" width="720px">
      <el-form label-width="110px" style="margin-bottom:12px">
        <el-form-item label="代码值" required>
          <el-input v-model="itemForm.itemKey" placeholder="如 M" />
        </el-form-item>
        <el-form-item label="中文名称" required>
          <el-input v-model="itemForm.itemValue" placeholder="如 男" />
        </el-form-item>
        <el-form-item label="业务使用说明">
          <el-input v-model="itemForm.bizUsage" type="textarea" :rows="2" placeholder="业务侧如何使用该代码值（可选）" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveItem">{{ editingItemId ? '保存' : '新增' }}</el-button>
          <el-button v-if="editingItemId" @click="editingItemId = null; itemForm.itemKey = ''; itemForm.itemValue = ''; itemForm.bizUsage = ''">取消编辑</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="dictItems" size="small" stripe>
        <el-table-column prop="itemKey" label="代码值" width="100" />
        <el-table-column prop="itemValue" label="中文名称" width="120" />
        <el-table-column prop="bizUsage" label="业务使用说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="120" fixed="right">
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
