<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import { statusLabel, statusTagType } from '@/utils/status-label'
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
  ruleId?: number
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
  status: string
  config?: RuleConfig | null
}

const rules = ref<RuleRow[]>([])
const {
  page: rulePage,
  pageSize: rulePageSize,
  paged: pagedRules,
  total: ruleTotal,
  resetPage: resetRulePage,
} = useClientPager(rules)
const loading = ref(false)
const selectedId = ref<number | null>(null)
const sources = ref<QualitySourceOption[]>([])
const tables = ref<QualityTableMeta[]>([])
const tablesLoading = ref(false)
const createVisible = ref(false)

const form = reactive({
  datasourceId: undefined as number | undefined,
  checkType: 'NULL_CHECK',
  targetTable: '',
  targetColumn: '',
  metadataEntryCode: '',
  threshold: undefined as number | undefined,
  configJson: '',
  status: 'ENABLED',
})

const createForm = reactive({
  ruleName: '',
  ruleType: 'COMPLETENESS',
})

const selected = computed(() => rules.value.find((r) => r.id === selectedId.value) || null)
const sourceGroups = computed(() => groupSourcesByRole(sources.value))
const layerHint = computed(() => catalogHintOfSourceId(form.datasourceId))
const columnOptions = computed(() => {
  const t = tables.value.find((x) => x.sourceTable === form.targetTable)
  return t?.columns || []
})

const checkHints: Record<string, string> = {
  NULL_CHECK: '空值稽核：指定表/字段，空值率超过阈值则告警',
  UNIQUENESS: '唯一性稽核：指定表/字段，重复率超过阈值则告警',
  ACCURACY: '准确性稽核：按参数 JSON 校验格式/正则/值域',
  RECORD_COUNT: '记录数稽核：表行数相对阈值（可作上下限）',
}

async function load() {
  loading.value = true
  try {
    rules.value = (await api.get('/governance/quality/rule-mgmt')).data || []
    resetRulePage()
    if (selectedId.value && !rules.value.some((r) => r.id === selectedId.value)) {
      selectedId.value = null
    }
    if (!selectedId.value && rules.value.length) {
      selectRule(rules.value[0])
    } else if (selectedId.value) {
      const row = rules.value.find((r) => r.id === selectedId.value)
      if (row) fillForm(row)
    }
  } catch {
    ElMessage.error('加载规则失败')
  } finally {
    loading.value = false
  }
}

async function ensureSources() {
  if (sources.value.length) return
  sources.value = await loadQualitySourceOptions()
}

function fillForm(row: RuleRow) {
  const c = row.config
  form.checkType = c?.checkType || mapRuleType(row.ruleType)
  form.targetTable = c?.targetTable || ''
  form.targetColumn = c?.targetColumn || ''
  form.metadataEntryCode = c?.metadataEntryCode || ''
  form.threshold = c?.threshold != null ? Number(c.threshold) : undefined
  form.configJson = c?.configJson || ''
  form.status = c?.status || 'ENABLED'
  if (form.datasourceId == null && sources.value.length) {
    form.datasourceId = sources.value[0]?.id
  }
  if (form.datasourceId != null) {
    void reloadTables(false)
  }
}

function mapRuleType(ruleType: string): string {
  const t = (ruleType || '').toUpperCase()
  if (t === 'UNIQUENESS') return 'UNIQUENESS'
  if (t === 'ACCURACY') return 'ACCURACY'
  if (t === 'COMPLETENESS' || t === 'INTEGRITY') return 'NULL_CHECK'
  return 'NULL_CHECK'
}

async function selectRule(row: RuleRow) {
  selectedId.value = row.id
  await ensureSources()
  fillForm(row)
}

async function reloadTables(clearSelection: boolean) {
  if (form.datasourceId == null) {
    tables.value = []
    return
  }
  tablesLoading.value = true
  try {
    tables.value = await loadQualityTables(form.datasourceId)
    if (clearSelection) {
      form.targetTable = ''
      form.targetColumn = ''
      form.metadataEntryCode = ''
    } else if (form.targetTable && !tables.value.some((t) => t.sourceTable === form.targetTable)) {
      // 保留手填/历史表名，追加到选项外展示
    }
  } catch {
    tables.value = []
    ElMessage.warning('加载表清单失败，请检查数据源')
  } finally {
    tablesLoading.value = false
  }
}

watch(() => form.datasourceId, () => {
  void reloadTables(true)
})

function onTablePick(name: string) {
  form.targetColumn = ''
  const meta = tables.value.find((t) => t.sourceTable === name)
  form.metadataEntryCode = meta?.entryCode || ''
}

async function saveConfig() {
  if (!selectedId.value) return
  if (!form.checkType) {
    ElMessage.warning('请选择检查类型')
    return
  }
  if (!form.targetTable.trim()) {
    ElMessage.warning('请选择目标表')
    return
  }
  if (form.checkType !== 'RECORD_COUNT' && !form.targetColumn.trim()) {
    ElMessage.warning('请选择目标字段')
    return
  }
  await api.post(`/governance/quality/rule-mgmt/${selectedId.value}/config`, {
    checkType: form.checkType,
    targetTable: form.targetTable,
    targetColumn: form.targetColumn || null,
    metadataEntryCode: form.metadataEntryCode || null,
    threshold: form.threshold,
    configJson: form.configJson,
    status: form.status,
  })
  ElMessage.success('配置已保存')
  await load()
}

async function createRule() {
  if (!createForm.ruleName.trim()) {
    ElMessage.warning('请填写规则名称')
    return
  }
  const id = (await api.post('/governance/quality/rule-mgmt', {
    ruleName: createForm.ruleName,
    ruleType: createForm.ruleType,
  })).data
  ElMessage.success('规则已创建')
  createVisible.value = false
  createForm.ruleName = ''
  createForm.ruleType = 'COMPLETENESS'
  await load()
  const row = rules.value.find((r) => r.id === id)
  if (row) await selectRule(row)
}

async function removeRule(id: number) {
  await ElMessageBox.confirm('确认删除该质量规则及其配置？', '删除确认', { type: 'warning' })
  await api.delete(`/governance/quality/rule-mgmt/${id}`)
  ElMessage.success('已删除')
  if (selectedId.value === id) selectedId.value = null
  await load()
}

function checkTypeLabel(v: string) {
  return ({
    NULL_CHECK: '空值检查',
    UNIQUENESS: '唯一性',
    ACCURACY: '准确性',
    RECORD_COUNT: '记录数',
  } as Record<string, string>)[v] || v
}

onMounted(async () => {
  await ensureSources()
  await load()
})
</script>

<template>
  <PageCard title="质量规则配置">
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="质量门禁分层：源层→直通编目；过程层(DWD)默认不进门户；资源层(DWS/ADS)→加工编目。请按稽核对象选对来源库。"
    />
    <el-form inline class="portal-inline-form portal-inline-form--block">
      <el-form-item class="portal-form-actions">
        <el-button type="primary" @click="createVisible = true">新建规则</el-button>
        <el-button @click="load">刷新</el-button>
      </el-form-item>
    </el-form>

    <div class="rule-layout">
      <div class="rule-list" v-loading="loading">
        <el-table
          :data="pagedRules"
          stripe
          size="small"
          highlight-current-row
          :current-row-key="selectedId ?? undefined"
          row-key="id"
          @current-change="(row: RuleRow | null) => row && selectRule(row)"
        >
          <el-table-column prop="ruleName" label="规则" min-width="120" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ $statusLabel(row.ruleType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ row }">
              <el-button link type="danger" @click.stop="removeRule(row.id)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-model:page="rulePage"
          v-model:page-size="rulePageSize"
          :total="ruleTotal"
        />
        <el-empty v-if="!loading && !rules.length" description="暂无规则，请先新建" />
      </div>

      <div class="rule-form">
        <template v-if="selected">
          <el-alert
            :title="`${selected.ruleCode} · ${selected.ruleName}`"
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          />
          <el-form label-width="100px">
            <el-form-item label="检查类型">
              <el-select v-model="form.checkType" style="width: 220px">
                <el-option label="空值检查" value="NULL_CHECK" />
                <el-option label="唯一性" value="UNIQUENESS" />
                <el-option label="准确性" value="ACCURACY" />
                <el-option label="记录数" value="RECORD_COUNT" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <span class="hint">{{ checkHints[form.checkType] }}</span>
            </el-form-item>
            <el-form-item label="来源库" required>
              <el-select
                v-model="form.datasourceId"
                filterable
                placeholder="按源层/过程层/资源层选择"
                style="width: 100%"
              >
                <el-option-group v-for="g in sourceGroups" :key="g.role" :label="g.label">
                  <el-option v-for="s in g.options" :key="s.id" :label="s.label" :value="s.id" />
                </el-option-group>
              </el-select>
              <div v-if="layerHint" class="hint" style="margin-top: 6px">{{ layerHint }}</div>
            </el-form-item>
            <el-form-item label="目标表" required>
              <el-select
                v-model="form.targetTable"
                filterable
                allow-create
                default-first-option
                :loading="tablesLoading"
                placeholder="从登记表选择"
                style="width: 100%"
                @change="onTablePick"
              >
                <el-option v-for="t in tables" :key="t.sourceTable" :label="t.sourceTable" :value="t.sourceTable" />
              </el-select>
            </el-form-item>
            <el-form-item
              v-if="['NULL_CHECK', 'UNIQUENESS', 'ACCURACY'].includes(form.checkType)"
              label="目标字段"
              required
            >
              <el-select
                v-model="form.targetColumn"
                filterable
                allow-create
                default-first-option
                placeholder="选择字段"
                style="width: 100%"
              >
                <el-option v-for="c in columnOptions" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="form.metadataEntryCode" label="元数据码">
              <el-tag size="small" type="info">{{ form.metadataEntryCode }}</el-tag>
            </el-form-item>
            <el-form-item label="阈值">
              <el-input-number v-model="form.threshold" :min="0" :max="100" :step="0.1" :precision="2" />
            </el-form-item>
            <el-form-item v-if="form.checkType === 'ACCURACY'" label="参数 JSON">
              <el-input v-model="form.configJson" type="textarea" :rows="4" placeholder='{"pattern":"^\\\\d{17}[\\\\dXx]$"}' />
            </el-form-item>
            <el-form-item label="配置状态">
              <el-select v-model="form.status" style="width: 160px">
                <el-option label="启用" value="ENABLED" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveConfig">保存配置</el-button>
            </el-form-item>
          </el-form>
          <el-descriptions v-if="selected.config" :column="1" size="small" border title="当前配置">
            <el-descriptions-item label="检查类型">{{ checkTypeLabel(selected.config.checkType || '') }}</el-descriptions-item>
            <el-descriptions-item label="目标表">{{ selected.config.targetTable || '—' }}</el-descriptions-item>
            <el-descriptions-item label="目标字段">{{ selected.config.targetColumn || '—' }}</el-descriptions-item>
            <el-descriptions-item label="阈值">{{ selected.config.threshold ?? '—' }}</el-descriptions-item>
          </el-descriptions>
        </template>
        <el-empty v-else description="请选择左侧规则进行配置" />
      </div>
    </div>

    <el-dialog v-model="createVisible" title="新建质量规则" width="420px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="规则名称" required>
          <el-input v-model="createForm.ruleName" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="createForm.ruleType" style="width: 100%">
            <el-option label="完整性" value="COMPLETENESS" />
            <el-option label="唯一性" value="UNIQUENESS" />
            <el-option label="准确性" value="ACCURACY" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="createRule">创建</el-button>
      </template>
    </el-dialog>
  </PageCard>
</template>

<style scoped>
.rule-layout {
  display: grid;
  grid-template-columns: minmax(280px, 42%) 1fr;
  gap: 16px;
  align-items: start;
}
.rule-list {
  min-height: 320px;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
@media (max-width: 900px) {
  .rule-layout {
    grid-template-columns: 1fr;
  }
}
</style>
