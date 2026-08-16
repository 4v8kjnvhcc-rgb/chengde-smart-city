<script setup lang="ts">
/**
 * Tab：校验规则类型（对照旧页基础规则目录）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import {
  catalogHintOfSourceId,
  groupSourcesByRole,
  loadQualitySourceOptions,
  loadQualityTables,
  type QualitySourceOption,
  type QualityTableMeta,
} from './useQualityTargetPicker'

interface RuleConfig {
  id?: number
  checkType?: string
  targetTable?: string
  targetColumn?: string
  metadataEntryCode?: string
  configJson?: string
  threshold?: number
  status?: string
}

interface RuleRow {
  id: number
  ruleCode: string
  ruleName: string
  ruleType: string
  sortNo?: number
  description?: string | null
  status: string
  config?: RuleConfig | null
}

const CHECK_HINTS: Record<string, string> = {
  NULL_CHECK: '空值检查：指定表/字段，空值率超过阈值则告警',
  UNIQUENESS: '唯一性：指定表/字段，重复率超过阈值则告警',
  ACCURACY: '准确性：按参数 JSON 校验格式/正则/值域',
  RECORD_COUNT: '记录数：表行数相对阈值（可作上下限）',
}

const allRules = ref<RuleRow[]>([])
const keyword = ref('')
const queryName = ref('')
const loading = ref(false)

const filteredRules = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return allRules.value
  return allRules.value.filter(
    (r) =>
      r.ruleName?.toLowerCase().includes(kw)
      || r.ruleCode?.toLowerCase().includes(kw)
      || (r.description || '').toLowerCase().includes(kw),
  )
})

const {
  page: rulePage,
  pageSize: rulePageSize,
  paged: pagedRules,
  total: ruleTotal,
  resetPage: resetRulePage,
} = useClientPager(filteredRules)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const metaForm = reactive({
  sortNo: 1,
  ruleCode: '',
  ruleName: '',
  description: '',
})

const sources = ref<QualitySourceOption[]>([])
const tables = ref<QualityTableMeta[]>([])
const tablesLoading = ref(false)
const configVisible = ref(false)
const configRule = ref<RuleRow | null>(null)
const configSaving = ref(false)
const configForm = reactive({
  datasourceId: undefined as number | undefined,
  checkType: 'NULL_CHECK',
  targetTable: '',
  targetColumn: '',
  metadataEntryCode: '',
  threshold: undefined as number | undefined,
  configJson: '',
  status: 'ENABLED',
})

const sourceGroups = computed(() => groupSourcesByRole(sources.value))
const layerHint = computed(() => catalogHintOfSourceId(configForm.datasourceId))
const columnOptions = computed(() => {
  const t = tables.value.find((x) => x.sourceTable === configForm.targetTable)
  return t?.columns || []
})

function ruleDesc(row: RuleRow): string {
  return row.description?.trim() || '—'
}

async function load() {
  loading.value = true
  try {
    allRules.value = (await api.get('/governance/quality/rule-mgmt')).data || []
    resetRulePage()
  } catch {
    ElMessage.error('加载规则类型失败')
  } finally {
    loading.value = false
  }
}

function doQuery() {
  keyword.value = queryName.value
  resetRulePage()
}

function doReset() {
  queryName.value = ''
  keyword.value = ''
  resetRulePage()
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  const maxSort = allRules.value.reduce((m, r) => Math.max(m, r.sortNo || 0), 0)
  metaForm.sortNo = maxSort + 1
  metaForm.ruleCode = ''
  metaForm.ruleName = ''
  metaForm.description = ''
  dialogVisible.value = true
}

function openEdit(row: RuleRow) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  metaForm.sortNo = row.sortNo != null ? row.sortNo : 1
  metaForm.ruleCode = row.ruleCode
  metaForm.ruleName = row.ruleName
  metaForm.description = row.description || ''
  dialogVisible.value = true
}

async function submitDialog() {
  if (!metaForm.ruleCode.trim()) {
    ElMessage.warning('请填写编码')
    return
  }
  if (!metaForm.ruleName.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  saving.value = true
  try {
    const body = {
      sortNo: metaForm.sortNo,
      ruleCode: metaForm.ruleCode.trim(),
      ruleName: metaForm.ruleName.trim(),
      description: metaForm.description.trim() || null,
    }
    if (dialogMode.value === 'create') {
      await api.post('/governance/quality/rule-mgmt', body)
      ElMessage.success('已新增')
    } else if (editingId.value != null) {
      await api.put(`/governance/quality/rule-mgmt/${editingId.value}`, body)
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeRule(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该校验规则类型？已关联的任务明细将一并移除。', '删除确认', { type: 'warning' })
    await api.delete(`/governance/quality/rule-mgmt/${id}`)
    ElMessage.success('已删除')
    await load()
  } catch (e: unknown) {
    if (e === 'cancel' || String(e) === 'cancel') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function ensureSources() {
  if (sources.value.length) return
  sources.value = await loadQualitySourceOptions()
}

async function reloadTables(clearSelection: boolean) {
  if (configForm.datasourceId == null) {
    tables.value = []
    return
  }
  tablesLoading.value = true
  try {
    tables.value = await loadQualityTables(configForm.datasourceId)
    if (clearSelection) {
      configForm.targetTable = ''
      configForm.targetColumn = ''
      configForm.metadataEntryCode = ''
    }
  } catch {
    tables.value = []
    ElMessage.warning('加载表清单失败')
  } finally {
    tablesLoading.value = false
  }
}

function onTablePick(name: string) {
  configForm.targetColumn = ''
  const meta = tables.value.find((t) => t.sourceTable === name)
  configForm.metadataEntryCode = meta?.entryCode || ''
}

async function openConfig(row: RuleRow) {
  configRule.value = row
  const c = row.config
  configForm.checkType = c?.checkType || 'NULL_CHECK'
  configForm.targetTable = c?.targetTable || ''
  configForm.targetColumn = c?.targetColumn || ''
  configForm.metadataEntryCode = c?.metadataEntryCode || ''
  configForm.threshold = c?.threshold != null ? Number(c.threshold) : undefined
  configForm.configJson = c?.configJson || ''
  configForm.status = c?.status || 'ENABLED'
  configVisible.value = true
  await ensureSources()
  if (configForm.datasourceId == null && sources.value.length) {
    configForm.datasourceId = sources.value[0]?.id
  }
  await reloadTables(false)
}

async function submitConfig() {
  if (!configRule.value) return
  if (!configForm.targetTable.trim()) {
    ElMessage.warning('请选择目标表')
    return
  }
  if (configForm.checkType !== 'RECORD_COUNT' && !configForm.targetColumn.trim()) {
    ElMessage.warning('请选择目标字段')
    return
  }
  configSaving.value = true
  try {
    await api.post(`/governance/quality/rule-mgmt/${configRule.value.id}/config`, {
      checkType: configForm.checkType,
      targetTable: configForm.targetTable,
      targetColumn: configForm.targetColumn || null,
      metadataEntryCode: configForm.metadataEntryCode || null,
      threshold: configForm.threshold,
      configJson: configForm.configJson,
      status: configForm.status,
    })
    ElMessage.success('配置已保存')
    configVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存配置失败')
  } finally {
    configSaving.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="doQuery">
      <el-form-item label="规则名称" class="portal-field-lg">
        <el-input v-model="queryName" clearable placeholder="请输入规则名称" @keyup.enter="doQuery" />
      </el-form-item>
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="doQuery">查询</el-button>
        <el-button @click="doReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="toolbar">
      <el-button type="primary" @click="openCreate">+ 新增</el-button>
    </div>

    <el-table v-loading="loading" :data="pagedRules" stripe border>
      <el-table-column label="排序" width="72">
        <template #default="{ row }">{{ row.sortNo ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="ruleName" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="描述" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">{{ ruleDesc(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">修改</el-button>
          <el-button link type="primary" @click="openConfig(row)">配置</el-button>
          <el-button link type="danger" @click="removeRule(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <PortalPagination v-model:page="rulePage" v-model:page-size="rulePageSize" :total="ruleTotal" />
    <el-empty v-if="!loading && !filteredRules.length" description="暂无规则类型，请点击新增" />

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增' : '编辑'"
      width="480px"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="排序">
          <el-input v-model.number="metaForm.sortNo" placeholder="请输入排序" />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input
            v-model="metaForm.ruleCode"
            :disabled="dialogMode === 'edit'"
            placeholder="如 NullValueCheck"
            maxlength="64"
          />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="metaForm.ruleName" placeholder="请输入名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="metaForm.description" placeholder="请输入描述" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDialog">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="configVisible"
      :title="configRule ? `执行配置 · ${configRule.ruleName}` : '执行配置'"
      width="560px"
      destroy-on-close
      @open="void ensureSources()"
    >
      <el-form label-width="100px">
        <el-form-item label="检查类型" required>
          <el-select v-model="configForm.checkType" style="width: 100%" @change="() => {}">
            <el-option label="空值检查" value="NULL_CHECK" />
            <el-option label="唯一性" value="UNIQUENESS" />
            <el-option label="准确性" value="ACCURACY" />
            <el-option label="记录数" value="RECORD_COUNT" />
          </el-select>
          <div class="hint">{{ CHECK_HINTS[configForm.checkType] }}</div>
        </el-form-item>
        <el-form-item label="来源库" required>
          <el-select
            v-model="configForm.datasourceId"
            filterable
            style="width: 100%"
            @change="() => reloadTables(true)"
          >
            <el-option-group v-for="g in sourceGroups" :key="g.role" :label="g.label">
              <el-option v-for="s in g.options" :key="s.id" :label="s.label" :value="s.id" />
            </el-option-group>
          </el-select>
          <div v-if="layerHint" class="hint">{{ layerHint }}</div>
        </el-form-item>
        <el-form-item label="目标表" required>
          <el-select
            v-model="configForm.targetTable"
            filterable
            allow-create
            default-first-option
            :loading="tablesLoading"
            placeholder="输入表名筛选，或选择/新建"
            style="width: 100%"
            @change="onTablePick"
          >
            <el-option v-for="t in tables" :key="t.sourceTable" :label="t.sourceTable" :value="t.sourceTable" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="['NULL_CHECK', 'UNIQUENESS', 'ACCURACY'].includes(configForm.checkType)"
          label="目标字段"
          required
        >
          <el-select v-model="configForm.targetColumn" filterable allow-create style="width: 100%">
            <el-option v-for="c in columnOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值">
          <el-input-number v-model="configForm.threshold" :min="0" :max="100" :step="0.1" :precision="2" />
        </el-form-item>
        <el-form-item v-if="configForm.checkType === 'ACCURACY'" label="参数 JSON">
          <el-input v-model="configForm.configJson" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSaving" @click="submitConfig">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}
.hint {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
</style>
