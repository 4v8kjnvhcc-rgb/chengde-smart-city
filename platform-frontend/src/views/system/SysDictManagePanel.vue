<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'

interface SysDict {
  id: number
  dictCode: string
  dictName: string
  remark?: string
  sortOrder: number
  status: number
}

interface SysDictItem {
  id: number
  dictId: number
  itemKey: string
  itemValue: string
  itemLabel?: string
  sortOrder: number
  status: number
  remark?: string
}

const router = useRouter()
const loading = ref(false)
const dicts = ref<SysDict[]>([])
const items = ref<SysDictItem[]>([])
const selectedDictId = ref<number | null>(null)
const dictDialog = ref(false)
const itemDialog = ref(false)
const dictEditingId = ref<number | null>(null)
const itemEditingId = ref<number | null>(null)
const submitting = ref(false)

const dictForm = reactive({
  dictCode: '',
  dictName: '',
  remark: '',
  sortOrder: 0,
  status: 1,
})
const itemForm = reactive({
  itemKey: '',
  itemValue: '',
  itemLabel: '',
  sortOrder: 0,
  status: 1,
  remark: '',
})

/** 有字典管理菜单即可增删改 */
const canAdd = computed(() => true)
const canEdit = computed(() => true)
const canDelete = computed(() => true)

const selectedDict = computed(() => dicts.value.find((d) => d.id === selectedDictId.value) || null)

async function loadDicts() {
  loading.value = true
  try {
    dicts.value = (await api.get('/system/dicts')).data || []
    if (!selectedDictId.value && dicts.value.length) {
      selectedDictId.value = dicts.value[0].id
    } else if (selectedDictId.value && !dicts.value.some((d) => d.id === selectedDictId.value)) {
      selectedDictId.value = dicts.value[0]?.id ?? null
    }
  } finally {
    loading.value = false
  }
}

async function loadItems() {
  if (!selectedDictId.value) {
    items.value = []
    return
  }
  items.value = (await api.get(`/system/dicts/${selectedDictId.value}/items`)).data || []
}

function openCreateDict() {
  dictEditingId.value = null
  Object.assign(dictForm, { dictCode: '', dictName: '', remark: '', sortOrder: 0, status: 1 })
  dictDialog.value = true
}

function openEditDict(row: SysDict) {
  dictEditingId.value = row.id
  Object.assign(dictForm, {
    dictCode: row.dictCode,
    dictName: row.dictName,
    remark: row.remark || '',
    sortOrder: row.sortOrder ?? 0,
    status: row.status ?? 1,
  })
  dictDialog.value = true
}

async function submitDict() {
  if (!dictForm.dictCode.trim() || !dictForm.dictName.trim()) {
    ElMessage.warning('请填写编码与名称')
    return
  }
  submitting.value = true
  try {
    const body = { ...dictForm }
    if (dictEditingId.value) {
      await api.put(`/system/dicts/${dictEditingId.value}`, body)
      ElMessage.success('字典已更新')
    } else {
      const id = (await api.post('/system/dicts', body)).data
      selectedDictId.value = id
      ElMessage.success('字典已创建')
    }
    dictDialog.value = false
    await loadDicts()
    await loadItems()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function removeDict(row: SysDict) {
  await ElMessageBox.confirm(`确认删除字典「${row.dictName}」？需先清空字典项。`, '删除确认', { type: 'warning' })
  await api.delete(`/system/dicts/${row.id}`)
  ElMessage.success('已删除')
  if (selectedDictId.value === row.id) selectedDictId.value = null
  await loadDicts()
  await loadItems()
}

function openCreateItem() {
  if (!selectedDictId.value) return
  itemEditingId.value = null
  Object.assign(itemForm, { itemKey: '', itemValue: '', itemLabel: '', sortOrder: 0, status: 1, remark: '' })
  itemDialog.value = true
}

function openEditItem(row: SysDictItem) {
  itemEditingId.value = row.id
  Object.assign(itemForm, {
    itemKey: row.itemKey,
    itemValue: row.itemValue,
    itemLabel: row.itemLabel || '',
    sortOrder: row.sortOrder ?? 0,
    status: row.status ?? 1,
    remark: row.remark || '',
  })
  itemDialog.value = true
}

async function submitItem() {
  if (!selectedDictId.value || !itemForm.itemKey.trim()) {
    ElMessage.warning('请填写项编码')
    return
  }
  submitting.value = true
  try {
    const body = { ...itemForm }
    if (itemEditingId.value) {
      await api.put(`/system/dicts/items/${itemEditingId.value}`, body)
      ElMessage.success('字典项已更新')
    } else {
      await api.post(`/system/dicts/${selectedDictId.value}/items`, body)
      ElMessage.success('字典项已创建')
    }
    itemDialog.value = false
    await loadItems()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function removeItem(row: SysDictItem) {
  await ElMessageBox.confirm(`确认删除项「${row.itemKey}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/system/dicts/items/${row.id}`)
  ElMessage.success('已删除')
  await loadItems()
}

watch(selectedDictId, () => {
  void loadItems()
})

onMounted(async () => {
  await loadDicts()
  await loadItems()
})
</script>

<template>
  <div class="sys-dict-panel" v-loading="loading">
    <el-alert
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
      title="本页维护平台统一数据字典（sys_dict）。数据资产登记业务字典仍在归集平台，双能力并存。"
    />
    <el-button
      type="primary"
      plain
      style="margin-bottom: 12px"
      @click="router.push('/exchange/ingestion?system=register&module=m050')"
    >
      打开数据字典管理（归集）
    </el-button>

    <div class="sys-dict-layout">
      <div class="sys-dict-left">
        <div class="sys-dict-toolbar">
          <span class="sys-dict-title">字典类型</span>
          <el-button v-if="canAdd" type="primary" size="small" @click="openCreateDict">新建</el-button>
        </div>
        <el-table
          :data="dicts"
          highlight-current-row
          size="small"
          height="420"
          @current-change="(row: SysDict | null) => { if (row) selectedDictId = row.id }"
        >
          <el-table-column prop="dictCode" label="编码" min-width="90" />
          <el-table-column prop="dictName" label="名称" min-width="100" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button v-if="canEdit" link type="primary" @click.stop="openEditDict(row)">编辑</el-button>
              <el-button v-if="canDelete" link type="danger" @click.stop="removeDict(row)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="sys-dict-right">
        <div class="sys-dict-toolbar">
          <span class="sys-dict-title">
            {{ selectedDict ? `${selectedDict.dictName} · 字典项` : '请选择字典类型' }}
          </span>
          <el-button v-if="canAdd || canEdit" type="primary" size="small" :disabled="!selectedDictId" @click="openCreateItem">
            新建项
          </el-button>
        </div>
        <el-table :data="items" stripe size="small" height="420">
          <el-table-column prop="itemKey" label="项编码" min-width="140" />
          <el-table-column prop="itemValue" label="项值" min-width="120" />
          <el-table-column prop="itemLabel" label="说明" min-width="140" />
          <el-table-column label="状态" width="70">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button v-if="canEdit" link type="primary" @click="openEditItem(row)">编辑</el-button>
              <el-button v-if="canDelete" link type="danger" @click="removeItem(row)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dictDialog" :title="dictEditingId ? '编辑字典' : '新建字典'" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="编码" required><el-input v-model="dictForm.dictCode" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="dictForm.dictName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="dictForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dictForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="dictForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDict">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemDialog" :title="itemEditingId ? '编辑字典项' : '新建字典项'" width="480px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="项编码" required><el-input v-model="itemForm.itemKey" /></el-form-item>
        <el-form-item label="项值"><el-input v-model="itemForm.itemValue" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="itemForm.itemLabel" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="itemForm.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="itemForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="itemForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitItem">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sys-dict-layout {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(360px, 1.4fr);
  gap: 12px;
}
.sys-dict-left,
.sys-dict-right {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 10px;
  background: #fff;
}
.sys-dict-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.sys-dict-title {
  font-weight: 600;
  font-size: 14px;
}
@media (max-width: 960px) {
  .sys-dict-layout {
    grid-template-columns: 1fr;
  }
}
</style>
