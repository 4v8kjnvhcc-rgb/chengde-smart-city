<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import { downloadText, ingestionApi, useIngestionLoading, type DataColumn, type DataSource, type DataTable } from '../useIngestionHub'

const { loading, loadError, withLoad } = useIngestionLoading()
const dataSources = ref<DataSource[]>([])
const tables = ref<DataTable[]>([])
const columns = ref<DataColumn[]>([])
const selectedSourceId = ref<number>()
const selectedTableId = ref<number>()
const tableForm = reactive({ sourceId: undefined as number | undefined, tableName: '', modelingMode: 'FORWARD' })
const columnForm = reactive({ columnCode: '', columnName: '', dataType: 'VARCHAR(64)', nullableFlag: 1 })
const importText = ref('')
const workflowStep = ref(0)
const scanResult = ref('')

const isForward = computed(() => tableForm.modelingMode === 'FORWARD')
const workflowSteps = computed(() => (isForward.value
  ? ['选择数据源', '定义物理表', '登记数据项', '完成登记']
  : ['选择数据源', '扫描数据库', '导入元数据', '核对确认']))

watch(() => tableForm.modelingMode, () => {
  workflowStep.value = 0
  scanResult.value = ''
})

async function reloadSources() {
  await withLoad(async () => {
    dataSources.value = (await ingestionApi.dataSources()).data
    if (dataSources.value.length && !selectedSourceId.value) {
      selectedSourceId.value = dataSources.value[0].id
      await loadTables()
    }
  })
}

async function loadTables() {
  if (!selectedSourceId.value) return
  tables.value = (await ingestionApi.tables(selectedSourceId.value)).data
  selectedTableId.value = tables.value[0]?.id
  if (selectedTableId.value) await loadColumns()
  else columns.value = []
}

async function loadColumns() {
  if (!selectedTableId.value) return
  columns.value = (await ingestionApi.columns(selectedTableId.value)).data
}

function onTableSelect(row: DataTable | undefined) {
  if (!row) return
  selectedTableId.value = row.id
  loadColumns()
}

async function createTable() {
  if (!tableForm.tableName || !selectedSourceId.value) return
  await ingestionApi.createTable({ sourceId: selectedSourceId.value, tableName: tableForm.tableName, modelingMode: tableForm.modelingMode })
  tableForm.tableName = ''
  await loadTables()
  if (isForward.value) workflowStep.value = 2
  else workflowStep.value = 3
  ElMessage.success('表登记成功')
}

async function createColumn() {
  if (!selectedTableId.value || !columnForm.columnCode || !columnForm.columnName) return
  await ingestionApi.createColumn(selectedTableId.value, { ...columnForm })
  columnForm.columnCode = ''
  columnForm.columnName = ''
  await loadColumns()
  await loadTables()
}

async function downloadTemplate() {
  const res = await ingestionApi.metadataTemplate()
  downloadText('metadata_import_template.csv', res.data)
}

async function doImport() {
  if (!selectedSourceId.value || !importText.value.trim()) return
  const res = await ingestionApi.importMetadata({ sourceId: selectedSourceId.value, csvText: importText.value })
  ElMessage.success(`已导入 ${res.data.importedRows} 条元数据`)
  importText.value = ''
  await loadTables()
  workflowStep.value = 3
}

function simulateScan() {
  const src = dataSources.value.find(s => s.id === selectedSourceId.value)
  scanResult.value = src
    ? `已从「${src.sourceName}」扫描到 ${tables.value.length || 2} 张表，可下载模板或粘贴 CSV 导入字段结构。`
    : '请先选择数据源'
  workflowStep.value = 2
}

function nextStep() {
  if (workflowStep.value < workflowSteps.value.length - 1) workflowStep.value++
}

function prevStep() {
  if (workflowStep.value > 0) workflowStep.value--
}

onMounted(reloadSources)
</script>

<template>
  <div v-loading="loading">
    <el-alert v-if="loadError" type="error" :title="loadError" show-icon :closable="false" style="margin-bottom:12px" />
    <PageCard title="数据库/表/项登记">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="建模方式">
          <el-radio-group v-model="tableForm.modelingMode">
            <el-radio-button value="FORWARD">正向（业务需求先建模）</el-radio-button>
            <el-radio-button value="REVERSE">逆向（从已有库导入）</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-steps :active="workflowStep" finish-status="success" simple style="margin-bottom:16px">
        <el-step v-for="s in workflowSteps" :key="s" :title="s" />
      </el-steps>

      <div v-show="workflowStep === 0">
        <el-form inline class="portal-inline-form">
          <el-form-item label="数据源" class="portal-field-xl">
            <el-select v-model="selectedSourceId" @change="loadTables">
              <el-option v-for="s in dataSources" :key="s.id" :label="s.sourceName" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" :disabled="!selectedSourceId" @click="nextStep">下一步</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-show="workflowStep === 1 && isForward">
        <el-form inline class="portal-inline-form">
          <el-form-item label="表名" class="portal-field-lg">
            <el-input v-model="tableForm.tableName" placeholder="按业务需求定义物理表" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button @click="prevStep">上一步</el-button>
            <el-button type="primary" :disabled="!tableForm.tableName" @click="createTable">登记表并继续</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-show="workflowStep === 1 && !isForward">
        <p class="step-hint">连接已登记数据源，扫描库表清单，准备导入元数据。</p>
        <el-form inline class="portal-inline-form">
          <el-form-item class="portal-form-actions">
            <el-button @click="prevStep">上一步</el-button>
            <el-button type="primary" @click="simulateScan">扫描数据库</el-button>
          </el-form-item>
        </el-form>
        <p v-if="scanResult" class="scan-result">{{ scanResult }}</p>
        <el-form v-if="scanResult" inline class="portal-inline-form" style="margin-top:8px">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="nextStep">下一步</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-show="workflowStep === 2 && isForward">
        <el-form inline size="small" class="portal-inline-form portal-inline-form--sm portal-inline-form--block">
          <el-form-item label="项编码" class="portal-field-xs"><el-input v-model="columnForm.columnCode" /></el-form-item>
          <el-form-item label="项名称" class="portal-field-xs"><el-input v-model="columnForm.columnName" /></el-form-item>
          <el-form-item label="类型" class="portal-field-xs"><el-input v-model="columnForm.dataType" /></el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" size="small" @click="createColumn">添加数据项</el-button>
          </el-form-item>
        </el-form>
        <el-form inline class="portal-inline-form">
          <el-form-item class="portal-form-actions">
            <el-button @click="prevStep">上一步</el-button>
            <el-button type="success" @click="workflowStep = 3">完成登记</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-show="workflowStep === 2 && !isForward">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button @click="downloadTemplate">下载导入模板</el-button>
          </el-form-item>
        </el-form>
        <el-input v-model="importText" type="textarea" :rows="3" placeholder="粘贴 CSV（table_name,column_code,column_name,...）" style="margin-bottom:12px" />
        <el-form inline class="portal-inline-form">
          <el-form-item class="portal-form-actions">
            <el-button @click="prevStep">上一步</el-button>
            <el-button type="primary" :disabled="!importText.trim()" @click="doImport">导入元数据</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 步骤 4 / 完成 -->
      <div v-show="workflowStep === 3">
        <el-alert :title="isForward ? '正向建模登记完成，可在下方查看表与数据项。' : '逆向导入完成，请核对字段结构。'" type="success" :closable="false" />
        <el-button style="margin-top:8px" @click="workflowStep = 0">重新开始</el-button>
      </div>

      <el-divider />
      <el-row :gutter="12">
        <el-col :span="9">
          <el-table :data="tables" stripe size="small" highlight-current-row @current-change="onTableSelect">
            <el-table-column prop="tableCode" label="编码" width="110" />
            <el-table-column prop="tableName" label="表名" />
            <el-table-column prop="modelingMode" label="建模" width="70">
              <template #default="{ row }">{{ row.modelingMode === 'REVERSE' ? '逆向' : '正向' }}</template>
            </el-table-column>
            <el-table-column prop="columnCount" label="项数" width="60" />
          </el-table>
        </el-col>
        <el-col :span="15">
          <el-table :data="columns" stripe size="small">
            <el-table-column prop="columnCode" label="项编码" width="110" />
            <el-table-column prop="columnName" label="项名称" />
            <el-table-column prop="dataType" label="类型" width="100" />
            <el-table-column label="可空" width="55">
              <template #default="{ row }">{{ row.nullableFlag ? '是' : '否' }}</template>
            </el-table-column>
          </el-table>
        </el-col>
      </el-row>
    </PageCard>
  </div>
</template>

<style scoped>
.step-hint { font-size: 13px; color: #606266; margin: 0 0 8px; }
.scan-result { margin-top: 8px; font-size: 13px; color: #409eff; }
</style>
