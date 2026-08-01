<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoPlay, Document } from '@element-plus/icons-vue'
import api from '@/api/http'

const props = defineProps<{ domain: string }>()
const emit = defineEmits<{ refreshed: [] }>()

export interface IndicatorRow {
  id: number
  queryNo?: string
  resultField?: string
  fieldType?: string
  indicatorName: string
  fieldName?: string
  indicatorCode?: string
}

const loading = ref(false)
const rows = ref<IndicatorRow[]>([])
const datasources = ref<Array<{ key: string; name: string }>>([])

const sqlDialog = ref(false)
const previewDialog = ref(false)
const saving = ref(false)
const parsing = ref(false)

const form = reactive({
  datasourceKey: 'platform',
  timeoutSec: 60,
  sqlText: '',
})

interface ParsedField {
  resultField: string
  fieldType: string
  fieldLength?: number
  fieldPrecision?: number
  indicatorName: string
  fieldName: string
}

const parsedFields = ref<ParsedField[]>([])
const previewColumns = ref<string[]>([])
const previewRows = ref<Record<string, unknown>[]>([])
const previewMessage = ref('')

const defaultSql = `SELECT
  DATE_FORMAT(stat_month, '%Y-%m') AS plan_month,
  COUNT(*) AS total_pop,
  SUM(CASE WHEN age >= 60 THEN 1 ELSE 0 END) AS age60_count,
  SUM(CASE WHEN age >= 60 THEN 1 ELSE 0 END) / COUNT(*) AS age60_rate
FROM demo_population
GROUP BY DATE_FORMAT(stat_month, '%Y-%m')`

async function load() {
  loading.value = true
  try {
    const [iRes, dsRes] = await Promise.all([
      api.get(`/analytics/domain/${props.domain}/indicators`),
      api.get(`/analytics/domain/${props.domain}/indicators/datasources`),
    ])
    rows.value = iRes.data || []
    datasources.value = dsRes.data || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.datasourceKey = datasources.value[0]?.key || 'platform'
  form.timeoutSec = 60
  form.sqlText = props.domain === 'population' ? defaultSql : ''
  parsedFields.value = []
  previewMessage.value = ''
  sqlDialog.value = true
}

function formatSql() {
  const raw = form.sqlText.trim()
  if (!raw) return
  form.sqlText = raw
    .replace(/\s+/g, ' ')
    .replace(/\s*,\s*/g, ',\n  ')
    .replace(/\bSELECT\b/i, 'SELECT\n  ')
    .replace(/\bFROM\b/i, '\nFROM')
    .replace(/\bWHERE\b/i, '\nWHERE')
    .replace(/\bGROUP BY\b/i, '\nGROUP BY')
    .replace(/\bORDER BY\b/i, '\nORDER BY')
  ElMessage.success('已格式化')
}

async function runParse() {
  if (!form.sqlText.trim()) {
    ElMessage.warning('请填写查询语句')
    return
  }
  parsing.value = true
  try {
    const res = await api.post(`/analytics/domain/${props.domain}/indicators/sql/parse`, {
      sqlText: form.sqlText,
    })
    parsedFields.value = (res.data || []).map((f: ParsedField) => ({
      ...f,
      indicatorName: f.indicatorName || f.resultField,
      fieldName: f.fieldName || `ind_${f.resultField}`,
    }))
    ElMessage.success(`已解析 ${parsedFields.value.length} 个结果字段`)
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '解析失败')
  } finally {
    parsing.value = false
  }
}

async function openPreview() {
  if (!form.sqlText.trim()) {
    ElMessage.warning('请填写查询语句')
    return
  }
  try {
    const res = await api.post(`/analytics/domain/${props.domain}/indicators/sql/preview`, {
      sqlText: form.sqlText,
      timeoutSec: form.timeoutSec,
      datasourceKey: form.datasourceKey,
    })
    previewColumns.value = res.data?.columns || []
    previewRows.value = res.data?.rows || []
    previewMessage.value = res.data?.message || ''
    if (res.data?.fields?.length && !parsedFields.value.length) {
      parsedFields.value = res.data.fields
    }
    previewDialog.value = true
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '预览失败')
  }
}

async function saveSql() {
  if (!form.datasourceKey) {
    ElMessage.warning('请选择数据源')
    return
  }
  if (!form.sqlText.trim()) {
    ElMessage.warning('请填写查询语句')
    return
  }
  if (!parsedFields.value.length) {
    await runParse()
    if (!parsedFields.value.length) return
  }
  saving.value = true
  try {
    const ds = datasources.value.find((d) => d.key === form.datasourceKey)
    await api.post(`/analytics/domain/${props.domain}/indicators/sql`, {
      datasourceKey: form.datasourceKey,
      datasourceName: ds?.name,
      timeoutSec: form.timeoutSec,
      sqlText: form.sqlText,
      querySlug: parsedFields.value[0]?.fieldName || 'query',
      fields: parsedFields.value,
    })
    ElMessage.success('指标语句已保存')
    sqlDialog.value = false
    await load()
    emit('refreshed')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function persistRow(row: IndicatorRow) {
  try {
    await api.put(`/analytics/domain/indicators/${row.id}`, {
      indicatorName: row.indicatorName,
      fieldName: row.fieldName,
    })
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
    await load()
  }
}

async function removeRow(row: IndicatorRow) {
  await ElMessageBox.confirm(`确认删除指标「${row.indicatorName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/analytics/domain/indicators/${row.id}`)
  ElMessage.success('已删除')
  await load()
  emit('refreshed')
}

watch(() => props.domain, () => load())
onMounted(load)

defineExpose({ load })
</script>

<template>
  <div v-loading="loading" class="ind-sql-lib">
    <el-form inline class="portal-inline-form">
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="openCreate">新增指标</el-button>
        <el-button @click="load">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-table class="portal-table" :data="rows" stripe size="small" empty-text="暂无数据">
      <el-table-column prop="queryNo" label="查询编号" min-width="200" show-overflow-tooltip />
      <el-table-column prop="resultField" label="查询结果字段" min-width="140" show-overflow-tooltip />
      <el-table-column prop="fieldType" label="字段类型" width="100" />
      <el-table-column label="指标名称" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.indicatorName" size="small" @change="persistRow(row)" />
        </template>
      </el-table-column>
      <el-table-column label="字段名" min-width="160">
        <template #default="{ row }">
          <el-input v-model="row.fieldName" size="small" @change="persistRow(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="removeRow(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="sqlDialog" title="指标语句" width="820px" destroy-on-close top="6vh">
      <el-form label-width="110px">
        <el-form-item label="数据源" required>
          <el-select v-model="form.datasourceKey" filterable style="width: 280px">
            <el-option v-for="d in datasources" :key="d.key" :label="d.name" :value="d.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时时间(秒)" required>
          <el-input-number v-model="form.timeoutSec" :min="5" :max="600" controls-position="right" />
        </el-form-item>
        <el-form-item label="查询语句">
          <div class="sql-toolbar">
            <el-tooltip content="预览执行">
              <el-button :icon="VideoPlay" circle type="primary" plain size="small" @click="openPreview" />
            </el-tooltip>
            <el-tooltip content="格式化SQL语句">
              <el-button :icon="Document" circle type="primary" plain size="small" @click="formatSql" />
            </el-tooltip>
            <el-button link type="primary" :loading="parsing" @click="runParse">解析字段</el-button>
          </div>
          <el-input
            v-model="form.sqlText"
            type="textarea"
            :rows="10"
            class="sql-editor"
            placeholder="请输入 SELECT 语句，并为结果列指定 AS 别名"
          />
        </el-form-item>
      </el-form>

      <el-table :data="parsedFields" stripe size="small" max-height="220" empty-text="暂无数据">
        <el-table-column prop="resultField" label="结果字段名" min-width="140" />
        <el-table-column prop="fieldType" label="字段类型" width="100" />
        <el-table-column prop="fieldLength" label="字段长度" width="100" />
        <el-table-column prop="fieldPrecision" label="字段精度" width="100" />
      </el-table>

      <template #footer>
        <el-button @click="sqlDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSql">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialog" title="预览" width="860px" destroy-on-close>
      <el-alert v-if="previewMessage" type="warning" :closable="false" :title="previewMessage" style="margin-bottom: 8px" />
      <el-table :data="previewRows" stripe size="small" max-height="360" empty-text="暂无数据">
        <el-table-column
          v-for="col in previewColumns"
          :key="col"
          :prop="col"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <template #footer>
        <el-button @click="previewDialog = false">关闭</el-button>
        <el-button type="primary" @click="previewDialog = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sql-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.sql-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.45;
}
</style>
