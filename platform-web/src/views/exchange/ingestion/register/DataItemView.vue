<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { ingestionApi, useIngestionLoading, type DataColumn, type DataTable } from '../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const tables = ref<DataTable[]>([])
const columns = ref<DataColumn[]>([])
const selectedTableId = ref<number>()
const dialogVisible = ref(false)
const editingCol = ref<DataColumn | null>(null)
const colForm = reactive({
  columnCode: '',
  columnName: '',
  dataType: 'VARCHAR(64)',
  lengthVal: 64,
  nullableFlag: 1,
  semanticDesc: '',
  componentType: 'INPUT',
  requiredTip: '',
})

const componentOptions = ['INPUT', 'SELECT', 'DATE', 'NUMBER', 'TEXTAREA']

async function reload() {
  await withLoad(async () => {
    tables.value = (await ingestionApi.tables()).data
    selectedTableId.value = tables.value[0]?.id
    if (selectedTableId.value) columns.value = (await ingestionApi.columns(selectedTableId.value)).data
  })
}

async function onTableChange(id: number) {
  selectedTableId.value = id
  columns.value = (await ingestionApi.columns(id)).data
}

function openCreate() {
  editingCol.value = null
  colForm.columnCode = ''
  colForm.columnName = ''
  colForm.dataType = 'VARCHAR(64)'
  colForm.lengthVal = 64
  colForm.nullableFlag = 1
  colForm.semanticDesc = ''
  colForm.componentType = 'INPUT'
  colForm.requiredTip = ''
  dialogVisible.value = true
}

function openEdit(row: DataColumn) {
  if (row.builtInFlag === 1) {
    ElMessage.warning('系统内置属性不可编辑')
    return
  }
  editingCol.value = row
  colForm.columnName = row.columnName
  colForm.dataType = row.dataType
  colForm.lengthVal = row.lengthVal ?? 64
  colForm.nullableFlag = row.nullableFlag
  colForm.semanticDesc = row.semanticDesc || ''
  colForm.componentType = row.componentType || 'INPUT'
  colForm.requiredTip = row.requiredTip || ''
  dialogVisible.value = true
}

async function saveColumn() {
  if (editingCol.value) {
    await ingestionApi.updateColumn(editingCol.value.id, {
      columnName: colForm.columnName,
      dataType: colForm.dataType,
      lengthVal: colForm.lengthVal,
      nullableFlag: colForm.nullableFlag,
      semanticDesc: colForm.semanticDesc,
      componentType: colForm.componentType,
      requiredTip: colForm.requiredTip,
    })
    ElMessage.success('已保存（元数据维护中对应属性不可恢复）')
  } else if (selectedTableId.value) {
    await ingestionApi.createColumn(selectedTableId.value, { ...colForm })
    ElMessage.success('数据项已新建')
  }
  dialogVisible.value = false
  if (selectedTableId.value) columns.value = (await ingestionApi.columns(selectedTableId.value)).data
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据项管理">
      <p class="hint">支持对数据项语义和结构进行定义，内置属性（如主键编码）不可编辑；自定义属性编辑后元数据维护中不可恢复。</p>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="物理表" class="portal-field-xl">
          <el-select :model-value="selectedTableId" @change="onTableChange">
            <el-option v-for="t in tables" :key="t.id" :label="t.tableName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="openCreate">新建数据项</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="columns" stripe>
        <el-table-column prop="columnCode" label="属性代码" width="120" />
        <el-table-column prop="columnName" label="属性名称" min-width="120" />
        <el-table-column prop="dataType" label="数据类型" width="110" />
        <el-table-column prop="lengthVal" label="长度" width="70" />
        <el-table-column prop="componentType" label="组件类型" width="90" />
        <el-table-column label="必填" width="60">
          <template #default="{ row }">{{ row.nullableFlag ? '否' : '是' }}</template>
        </el-table-column>
        <el-table-column prop="semanticDesc" label="语义说明" min-width="140" />
        <el-table-column label="内置" width="60">
          <template #default="{ row }">{{ row.builtInFlag ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.builtInFlag === 1" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>
    <el-dialog v-model="dialogVisible" :title="editingCol ? '编辑数据项' : '新建数据项'" width="520px">
      <el-form label-width="100px">
        <el-form-item v-if="!editingCol" label="属性代码"><el-input v-model="colForm.columnCode" /></el-form-item>
        <el-form-item label="属性名称"><el-input v-model="colForm.columnName" /></el-form-item>
        <el-form-item label="数据类型"><el-input v-model="colForm.dataType" /></el-form-item>
        <el-form-item label="长度"><el-input-number v-model="colForm.lengthVal" :min="1" /></el-form-item>
        <el-form-item label="组件类型">
          <el-select v-model="colForm.componentType">
            <el-option v-for="c in componentOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="必填">
          <el-switch :model-value="colForm.nullableFlag === 0" @change="(v: boolean) => colForm.nullableFlag = v ? 0 : 1" />
        </el-form-item>
        <el-form-item label="必填提示"><el-input v-model="colForm.requiredTip" /></el-form-item>
        <el-form-item label="语义说明"><el-input v-model="colForm.semanticDesc" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveColumn">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint { font-size: 13px; color: #606266; margin: 0 0 12px; }
</style>
