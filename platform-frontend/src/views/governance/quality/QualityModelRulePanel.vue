<script setup lang="ts">
/**
 * Tab：质量规则配置 — 左树选表 + 新增向导：
 * 1) 选择规则类型 → 2) 分类型配置页 → （值域/规范）选择正则表达式
 */
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTree } from 'element-plus'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'

type FormKind =
  | 'null'
  | 'range'
  | 'regex'
  | 'script'
  | 'count'
  | 'unique'
  | 'accuracy'
  | 'fluctuation'
  | 'generic'

interface RuleKind {
  code: string
  name: string
  desc: string
  form: FormKind
  checkType: string
}

/** 对照旧页固定规则类型（共 11 条，含自定义）；展示优先以目录 API 为准 */
const RULE_KIND_META: RuleKind[] = [
  { code: 'NullValueCheck', name: '空值检查', desc: '用于检查字段是否为空', form: 'null', checkType: 'NULL_CHECK' },
  { code: 'RangeCheck', name: '值域检查', desc: '用于检查关键指标取值范围', form: 'range', checkType: 'ACCURACY' },
  { code: 'StandardInspection', name: '规范检查', desc: '用于检查字符型字段的格式是否规范', form: 'regex', checkType: 'ACCURACY' },
  { code: 'JavaScript', name: 'Java脚本', desc: '用于执行Java脚本检查数据', form: 'script', checkType: 'ACCURACY' },
  { code: 'RecordCount', name: '记录数', desc: '核查数据总量，校验条数完整性与缺失、冗余情况', form: 'count', checkType: 'RECORD_COUNT' },
  { code: 'Uniqueness', name: '唯一性', desc: '校验关键字段，排查重复数据与重复录入问题', form: 'unique', checkType: 'UNIQUENESS' },
  { code: 'Accuracy', name: '准确性', desc: '核对数据内容，确保数值、文本符合真实业务', form: 'accuracy', checkType: 'ACCURACY' },
  { code: 'DataFluctuation', name: '波动', desc: '监控数据变化，识别异常增减、突发等不合理情况', form: 'fluctuation', checkType: 'RECORD_COUNT' },
  { code: 'Consistency', name: '一致性', desc: '比对关联数据，保障多表多源口径、格式统一', form: 'generic', checkType: 'ACCURACY' },
  { code: 'LogicCheck', name: '逻辑性', desc: '校验业务规则，判断数据间关联关系是否合理', form: 'generic', checkType: 'ACCURACY' },
  { code: 'CustomRule', name: '自定义', desc: '适配业务场景，按需配置专项精度校验规则', form: 'generic', checkType: 'ACCURACY' },
]

const catalogKinds = ref<RuleKind[]>([...RULE_KIND_META])

async function loadCatalogKinds() {
  try {
    const rows = ((await api.get('/governance/quality/rule-mgmt')).data || []) as Array<{
      ruleCode?: string
      ruleName?: string
      description?: string | null
      config?: { checkType?: string } | null
    }>
    if (!rows.length) {
      catalogKinds.value = [...RULE_KIND_META]
      return
    }
    catalogKinds.value = rows.map((r) => {
      const code = String(r.ruleCode || '')
      const base = RULE_KIND_META.find((k) => k.code === code || k.name === r.ruleName)
      return {
        code: code || base?.code || 'CustomRule',
        name: r.ruleName || base?.name || code,
        desc: (r.description || '').trim() || base?.desc || '',
        form: base?.form || 'generic',
        checkType: r.config?.checkType || base?.checkType || 'ACCURACY',
      }
    })
  } catch {
    catalogKinds.value = [...RULE_KIND_META]
  }
}

const REGEX_PRESETS = [
  {
    name: '身份证验证',
    expression: '(^[1-9]\\d{5}(18|19|([23]\\d))\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9]|10|20|30|31)\\d{3}[0-9Xx]$)|(^[1-9]\\d{5}\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9]|10|20|30|31)\\d{2}$))',
    remark: '15位或18位',
  },
  {
    name: '手机号验证',
    expression: '^1(3\\d|4[5-9]|5[0-35-9]|6[567]|7[0-8]|8\\d|9[0-35-9])\\d{8}$',
    remark: '手机号的验证',
  },
  {
    name: '邮箱验证',
    expression: '^[a-z]([a-z0-9]*[-_]?[a-z0-9]+)*@([a-z0-9]*[-_]?[a-z0-9]+)+[\\.][a-z]{2,3}([\\.][a-z]{2})?$',
    remark: '邮箱格式验证',
  },
  {
    name: '固定电话验证',
    expression: '^(?:(?:\\d{3}-)?\\d{8}|^(?:\\d{4}-)?\\d{7,8})(?:::-\\d+)?$',
    remark: '固定电话的验证',
  },
]

const ISSUE_LEVELS = [
  { label: '一般', value: '一般' },
  { label: '严重', value: '严重' },
  { label: '致命', value: '致命' },
]

interface TreeNode {
  id: string
  label: string
  type: 'model' | 'table'
  modelId?: number
  modelTableId?: number
  tableName?: string
  children?: TreeNode[]
}

interface ModelRuleRow {
  id: number
  modelId: number
  modelTableId: number
  ruleTypeCode?: string
  ruleTypeName: string
  ruleName: string
  tableName: string
  fieldNames?: string
  remark?: string
  checkType?: string
  configJson?: string
}

interface ColumnOpt {
  name: string
  dataType: string
}

const treeLoading = ref(false)
const treeData = ref<TreeNode[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const selected = ref<{
  modelId: number | null
  modelTableId: number | null
  tableName: string
  modelLabel: string
  datasourceName: string
}>({
  modelId: null,
  modelTableId: null,
  tableName: '',
  modelLabel: '',
  datasourceName: '',
})

const rules = ref<ModelRuleRow[]>([])
const loading = ref(false)
const queryName = ref('')
const queryTable = ref('')
const queryField = ref('')
const keyword = ref('')
const tableKeyword = ref('')
const fieldKeyword = ref('')

const filtered = computed(() => {
  let list = rules.value
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(
      (r) =>
        r.ruleName?.toLowerCase().includes(kw)
        || r.ruleTypeName?.toLowerCase().includes(kw)
        || (r.remark || '').toLowerCase().includes(kw),
    )
  }
  const tk = tableKeyword.value.trim().toLowerCase()
  if (tk) {
    list = list.filter((r) => (r.tableName || '').toLowerCase().includes(tk))
  }
  const fk = fieldKeyword.value.trim().toLowerCase()
  if (fk) {
    list = list.filter((r) => (r.fieldNames || '').toLowerCase().includes(fk))
  }
  return list
})

const { page, pageSize, paged, total, resetPage } = useClientPager(filtered)

const wizardVisible = ref(false)
const wizardStep = ref(1)
const wizardSaving = ref(false)
const editRuleId = ref<number | null>(null)
const selectedKind = ref<RuleKind | null>(null)
const kindPage = ref(1)
const kindPageSize = ref(10)
const pagedKinds = computed(() => {
  const start = (kindPage.value - 1) * kindPageSize.value
  return catalogKinds.value.slice(start, start + kindPageSize.value)
})

const form = reactive({
  ruleName: '',
  tableName: '',
  issueLevel: '一般',
  weight: 1,
  fieldNames: [] as string[],
  fieldType: '',
  filterEmptyString: false,
  expression: '',
  script: '',
  threshold: undefined as number | undefined,
  dateColumn: '',
  bizDate: '',
  comparePeriod: '上一周期',
  calcMethod: '环比',
  errorDesc: '',
})

const fieldError = ref(false)
const columns = ref<ColumnOpt[]>([])
const columnsLoading = ref(false)

const regexVisible = ref(false)
const regexKeyword = ref('')
const regexQuery = ref('')
const regexSelected = ref<(typeof REGEX_PRESETS)[0] | null>(null)
const filteredRegex = computed(() => {
  const kw = regexQuery.value.trim().toLowerCase()
  if (!kw) return REGEX_PRESETS
  return REGEX_PRESETS.filter(
    (r) => r.name.toLowerCase().includes(kw) || r.expression.toLowerCase().includes(kw) || r.remark.toLowerCase().includes(kw),
  )
})

const wizardTitle = computed(() => {
  if (wizardStep.value === 1 && !editRuleId.value) return '质量规则'
  const name = selectedKind.value?.name || '规则'
  return `质量规则 - ${name}`
})

const currentForm = computed(() => selectedKind.value?.form || 'generic')
const needsField = computed(() => {
  const f = currentForm.value
  return f !== 'count' && f !== 'script' && f !== 'fluctuation'
})
const needsExpression = computed(() => currentForm.value === 'range' || currentForm.value === 'regex')

async function loadTree(preferTableId?: number | null) {
  treeLoading.value = true
  try {
    treeData.value = (await api.get('/governance/quality/models/tree')).data || []
    await nextTick()
    if (preferTableId) {
      const key = `t-${preferTableId}`
      treeRef.value?.setCurrentKey(key)
      selectByKey(key)
      return
    }
    const firstModel = treeData.value[0]
    const firstTable = firstModel?.children?.[0]
    if (firstTable) {
      treeRef.value?.setCurrentKey(firstTable.id)
      selectByKey(firstTable.id)
    } else {
      selected.value = { modelId: null, modelTableId: null, tableName: '', modelLabel: '', datasourceName: '' }
      rules.value = []
    }
  } catch {
    ElMessage.error('加载质量模型树失败')
  } finally {
    treeLoading.value = false
  }
}

function selectByKey(key: string) {
  const walk = (nodes: TreeNode[]): TreeNode | null => {
    for (const n of nodes) {
      if (n.id === key) return n
      if (n.children?.length) {
        const hit = walk(n.children)
        if (hit) return hit
      }
    }
    return null
  }
  const node = walk(treeData.value)
  if (!node) return
  if (node.type === 'table' && node.modelId != null && node.modelTableId != null) {
    const parent = treeData.value.find((m) => m.modelId === node.modelId)
    selected.value = {
      modelId: node.modelId,
      modelTableId: node.modelTableId,
      tableName: node.tableName || '',
      modelLabel: parent?.label || '',
      datasourceName: String((parent as TreeNode & { datasourceName?: string })?.datasourceName || parent?.label || ''),
    }
    void loadRules()
  } else if (node.type === 'model' && node.modelId != null) {
    selected.value = {
      modelId: node.modelId,
      modelTableId: null,
      tableName: '',
      modelLabel: node.label,
      datasourceName: String((node as TreeNode & { datasourceName?: string }).datasourceName || node.label),
    }
    void loadRules()
  }
}

function onNodeClick(data: TreeNode) {
  selectByKey(data.id)
}

async function loadRules() {
  if (selected.value.modelId == null) {
    rules.value = []
    return
  }
  loading.value = true
  try {
    const params: Record<string, unknown> = {}
    if (selected.value.modelTableId != null) params.modelTableId = selected.value.modelTableId
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    rules.value = (await api.get(`/governance/quality/models/${selected.value.modelId}/rules`, { params })).data || []
    resetPage()
  } catch {
    ElMessage.error('加载模型规则失败')
  } finally {
    loading.value = false
  }
}

function doQuery() {
  keyword.value = queryName.value
  tableKeyword.value = queryTable.value
  fieldKeyword.value = queryField.value
  resetPage()
  void loadRules()
}

function doReset() {
  queryName.value = ''
  queryTable.value = ''
  queryField.value = ''
  keyword.value = ''
  tableKeyword.value = ''
  fieldKeyword.value = ''
  resetPage()
  void loadRules()
}

function resetForm() {
  form.ruleName = ''
  form.tableName = selected.value.tableName
  form.issueLevel = '一般'
  form.weight = 1
  form.fieldNames = []
  form.fieldType = ''
  form.filterEmptyString = false
  form.expression = ''
  form.script = ''
  form.threshold = undefined
  form.dateColumn = ''
  form.bizDate = ''
  form.comparePeriod = '上一周期'
  form.calcMethod = '环比'
  form.errorDesc = ''
  fieldError.value = false
}

function openAdd() {
  if (selected.value.modelId == null || selected.value.modelTableId == null) {
    ElMessage.warning('请先在左侧选择一张表')
    return
  }
  editRuleId.value = null
  wizardStep.value = 1
  selectedKind.value = null
  kindPage.value = 1
  resetForm()
  void loadCatalogKinds()
  wizardVisible.value = true
}

function parseConfig(raw?: string | null): Record<string, unknown> {
  if (!raw) return {}
  try {
    return JSON.parse(raw) as Record<string, unknown>
  } catch {
    return {}
  }
}

async function openEdit(row: ModelRuleRow) {
  if (selected.value.modelId == null) return
  editRuleId.value = row.id
  const kind = catalogKinds.value.find((k) => k.code === row.ruleTypeCode || k.name === row.ruleTypeName)
    || catalogKinds.value.find((k) => k.checkType === row.checkType)
    || catalogKinds.value[0]
    || RULE_KIND_META[0]
  selectedKind.value = kind
  wizardStep.value = 2
  resetForm()
  form.ruleName = row.ruleName
  form.tableName = row.tableName
  form.fieldNames = (row.fieldNames || '').split(',').map((s) => s.trim()).filter(Boolean)
  form.errorDesc = row.remark || ''
  const cfg = parseConfig(row.configJson)
  form.issueLevel = String(cfg.issueLevel || '一般')
  form.weight = Number(cfg.weight ?? 1)
  form.fieldType = String(cfg.fieldType || '')
  form.filterEmptyString = !!cfg.filterEmptyString
  form.expression = String(cfg.expression || cfg.regex || '')
  form.script = String(cfg.script || '')
  form.threshold = cfg.threshold != null ? Number(cfg.threshold) : undefined
  wizardVisible.value = true
  await loadColumns()
  if (form.fieldNames.length && !form.fieldType) {
    syncFieldTypes(form.fieldNames)
  }
}

async function loadColumns() {
  if (selected.value.modelId == null || !selected.value.tableName) {
    columns.value = []
    return
  }
  columnsLoading.value = true
  try {
    const model = (await api.get(`/governance/quality/models/${selected.value.modelId}`)).data as {
      datasourceId: number
    }
    const res = await api.get(`/governance/catalog/resources-mgmt/bind-sources/${model.datasourceId}/table-columns`, {
      params: { tableName: selected.value.tableName, sourceKind: 'META' },
    })
    const d = res.data || {}
    const cols = (d.columns || []) as Array<{ columnName?: string; dataType?: string }>
    columns.value = cols
      .map((c) => ({ name: String(c.columnName || '').trim(), dataType: String(c.dataType || 'VARCHAR') }))
      .filter((c) => !!c.name)
  } catch {
    columns.value = []
  } finally {
    columnsLoading.value = false
  }
}

function onKindRow(row: RuleKind) {
  selectedKind.value = row
}

async function goNext() {
  if (!selectedKind.value) {
    ElMessage.warning('请选择一条规则类型')
    return
  }
  const kind = selectedKind.value
  const ds = selected.value.datasourceName || selected.value.modelLabel || '模型'
  const seq = (rules.value.filter((r) => r.ruleTypeName === kind.name).length || 0) + 1
  form.ruleName = `${ds}_${selected.value.tableName}_${kind.name}_${seq}`
  form.tableName = selected.value.tableName
  form.errorDesc = ''
  form.fieldNames = []
  form.fieldType = ''
  form.expression = ''
  form.script = ''
  form.filterEmptyString = false
  form.issueLevel = '一般'
  form.weight = 1
  fieldError.value = false
  wizardStep.value = 2
  await loadColumns()
}

function syncFieldTypes(names: string[]) {
  const types = names
    .map((n) => columns.value.find((c) => c.name === n)?.dataType)
    .filter((t): t is string => !!t)
  form.fieldType = [...new Set(types)].join(',')
}

function onFieldChange(names: string[]) {
  fieldError.value = !names?.length
  syncFieldTypes(names || [])
}

function openRegexPicker() {
  regexKeyword.value = ''
  regexQuery.value = ''
  regexSelected.value = null
  regexVisible.value = true
}

function confirmRegex() {
  if (!regexSelected.value) {
    ElMessage.warning('请选择一条正则')
    return
  }
  form.expression = regexSelected.value.expression
  regexVisible.value = false
}

function buildConfigJson(): string {
  return JSON.stringify({
    form: currentForm.value,
    issueLevel: form.issueLevel,
    weight: form.weight,
    fieldType: form.fieldType || null,
    filterEmptyString: form.filterEmptyString,
    expression: form.expression || null,
    script: form.script || null,
    threshold: form.threshold ?? null,
    dateColumn: form.dateColumn || null,
    bizDate: form.bizDate || null,
    comparePeriod: form.comparePeriod || null,
    calcMethod: form.calcMethod || null,
  })
}

async function submitWizard() {
  if (selected.value.modelId == null || selected.value.modelTableId == null) return
  if (!selectedKind.value) {
    ElMessage.warning('请选择规则类型')
    return
  }
  if (!form.ruleName.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  if (needsField.value && !form.fieldNames.length) {
    fieldError.value = true
    ElMessage.warning('请选择字段')
    return
  }
  if (currentForm.value === 'range' && !form.expression.trim()) {
    ElMessage.warning('请填写值域范围')
    return
  }
  if (currentForm.value === 'regex' && !form.expression.trim()) {
    ElMessage.warning('请填写正则表达式')
    return
  }
  if (currentForm.value === 'script' && !form.script.trim()) {
    ElMessage.warning('请填写脚本')
    return
  }
  if (!form.errorDesc.trim()) {
    ElMessage.warning('请填写错误描述')
    return
  }

  wizardSaving.value = true
  try {
    const body = {
      modelTableId: selected.value.modelTableId,
      ruleTypeCode: selectedKind.value.code,
      ruleTypeName: selectedKind.value.name,
      checkType: selectedKind.value.checkType,
      ruleName: form.ruleName.trim(),
      fieldNames: form.fieldNames.length ? form.fieldNames : null,
      remark: form.errorDesc.trim(),
      threshold: form.threshold,
      configJson: buildConfigJson(),
    }
    if (editRuleId.value != null) {
      await api.put(`/governance/quality/models/${selected.value.modelId}/rules/${editRuleId.value}`, body)
      ElMessage.success('已保存')
    } else {
      await api.post(`/governance/quality/models/${selected.value.modelId}/rules`, body)
      ElMessage.success('已新增规则')
    }
    wizardVisible.value = false
    await loadRules()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    wizardSaving.value = false
  }
}

async function removeRule(row: ModelRuleRow) {
  await ElMessageBox.confirm('确认删除该质量规则？', '删除确认', { type: 'warning' })
  await api.delete(`/governance/quality/models/${row.modelId}/rules/${row.id}`)
  ElMessage.success('已删除')
  await loadRules()
}

async function clearFieldRules() {
  if (selected.value.modelId == null || selected.value.modelTableId == null) {
    ElMessage.warning('请先选择表')
    return
  }
  const field = queryField.value.trim() || fieldKeyword.value.trim()
  if (!field) {
    ElMessage.warning('请先在「字段」筛选框填写要清除的字段名')
    return
  }
  await ElMessageBox.confirm(`确认清除字段「${field}」下的全部规则？不可恢复。`, '一键清除', { type: 'warning' })
  const res = await api.post(`/governance/quality/models/${selected.value.modelId}/rules/clear-by-field`, {
    modelTableId: selected.value.modelTableId,
    fieldName: field,
  })
  ElMessage.success(String(res.data?.message || '已清除'))
  await loadRules()
}

watch(
  () => wizardVisible.value,
  (v) => {
    if (!v) {
      wizardStep.value = 1
      editRuleId.value = null
      selectedKind.value = null
    }
  },
)

onMounted(() => {
  void loadCatalogKinds()
  void loadTree()
})

defineExpose({ reload: () => loadTree(selected.value.modelTableId) })
</script>

<template>
  <div class="model-rule-layout" v-loading="treeLoading">
    <aside class="tree-pane">
      <div class="tree-title">质量规则</div>
      <el-tree
        v-if="treeData.length"
        ref="treeRef"
        :data="treeData"
        node-key="id"
        default-expand-all
        highlight-current
        :props="{ label: 'label', children: 'children' }"
        @node-click="onNodeClick"
      />
      <el-empty v-else description="请先在「质量模型管理」新增模型" :image-size="64" />
    </aside>

    <section class="rule-pane">
      <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent="doQuery">
        <el-form-item label="名称" class="portal-field-md">
          <el-input v-model="queryName" clearable placeholder="规则名称" @keyup.enter="doQuery" />
        </el-form-item>
        <el-form-item label="表名" class="portal-field-md">
          <el-input v-model="queryTable" clearable placeholder="表名模糊" @keyup.enter="doQuery" />
        </el-form-item>
        <el-form-item label="字段" class="portal-field-md">
          <el-input v-model="queryField" clearable placeholder="字段名（清除用）" @keyup.enter="doQuery" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="doQuery">查询</el-button>
          <el-button @click="doReset">重置</el-button>
          <el-button type="primary" @click="openAdd">+ 新增</el-button>
          <el-button type="danger" plain @click="clearFieldRules">一键清除字段规则</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="selected.modelTableId"
        type="info"
        :closable="false"
        show-icon
        :title="`当前表：${selected.tableName}（模型：${selected.modelLabel}）`"
        style="margin-bottom: 12px"
      />
      <el-alert
        v-else-if="selected.modelId"
        type="warning"
        :closable="false"
        show-icon
        title="已选中模型，请展开并选择具体表后再新增规则"
        style="margin-bottom: 12px"
      />

      <el-table v-loading="loading" :data="paged" stripe border>
        <el-table-column prop="ruleTypeName" label="规则类型" width="120" show-overflow-tooltip />
        <el-table-column prop="ruleName" label="名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="tableName" label="表名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="fieldNames" label="字段名" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">修改</el-button>
            <el-button link type="danger" @click="removeRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PortalPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
      <el-empty v-if="!loading && !filtered.length" description="暂无规则，请点击新增" />
    </section>

    <!-- 向导：步骤1 选类型 / 步骤2 分类型配置 -->
    <el-dialog
      v-model="wizardVisible"
      :title="wizardTitle"
      width="720px"
      destroy-on-close
      append-to-body
      :close-on-click-modal="false"
    >
      <!-- 步骤1 -->
      <template v-if="wizardStep === 1 && !editRuleId">
        <el-table
          :data="pagedKinds"
          highlight-current-row
          border
          max-height="420"
          @current-change="(row: RuleKind | null) => { if (row) onKindRow(row) }"
          @row-click="onKindRow"
        >
          <el-table-column prop="name" label="规则名称" width="140" />
          <el-table-column prop="desc" label="描述" min-width="280" show-overflow-tooltip />
        </el-table>
        <PortalPagination
          v-model:page="kindPage"
          v-model:page-size="kindPageSize"
          :total="catalogKinds.length"
        />
      </template>

      <!-- 步骤2：公共字段 + 分类型扩展 -->
      <el-form v-else label-width="100px" class="rule-form">
        <el-form-item label="名称" required>
          <el-input v-model="form.ruleName" maxlength="256" />
        </el-form-item>
        <el-form-item label="数据表">
          <el-input :model-value="form.tableName" disabled />
        </el-form-item>
        <el-form-item label="问题级别">
          <el-select v-model="form.issueLevel" style="width: 200px">
            <el-option v-for="o in ISSUE_LEVELS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="1" :max="100" />
        </el-form-item>

        <!-- 空值检查 -->
        <template v-if="currentForm === 'null'">
          <el-form-item label="字段名" required :error="fieldError ? '请选择字段' : ''">
            <div class="field-row">
              <el-select
                v-model="form.fieldNames"
                multiple
                filterable
                clearable
                collapse-tags
                collapse-tags-tooltip
                :loading="columnsLoading"
                placeholder="请选择字段（可多选）"
                style="flex: 1"
                @change="onFieldChange"
              >
                <el-option v-for="c in columns" :key="c.name" :label="c.name" :value="c.name" />
              </el-select>
              <el-checkbox v-model="form.filterEmptyString" class="field-extra">是否过滤空字符串</el-checkbox>
            </div>
          </el-form-item>
        </template>

        <!-- 值域检查 -->
        <template v-else-if="currentForm === 'range'">
          <el-form-item label="字段名" required :error="fieldError ? '请选择字段' : ''">
            <div class="field-row">
              <el-select
                v-model="form.fieldNames"
                multiple
                filterable
                clearable
                collapse-tags
                collapse-tags-tooltip
                :loading="columnsLoading"
                placeholder="请选择字段（可多选）"
                style="flex: 1"
                @change="onFieldChange"
              >
                <el-option v-for="c in columns" :key="c.name" :label="c.name" :value="c.name" />
              </el-select>
              <el-input v-model="form.fieldType" disabled placeholder="字段类型" style="width: 140px" />
            </div>
          </el-form-item>
          <el-form-item label="值域范围" required>
            <div class="field-row">
              <el-input v-model="form.expression" placeholder="请输入值域表达式" />
              <el-button type="primary" link @click="openRegexPicker">选择表达式</el-button>
            </div>
          </el-form-item>
        </template>

        <!-- 规范检查 -->
        <template v-else-if="currentForm === 'regex'">
          <el-form-item label="字段名" required :error="fieldError ? '请选择字段' : ''">
            <el-select
              v-model="form.fieldNames"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              :loading="columnsLoading"
              placeholder="请选择字段（可多选）"
              style="width: 100%"
              @change="onFieldChange"
            >
              <el-option v-for="c in columns" :key="c.name" :label="c.name" :value="c.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="正则表达式" required>
            <div class="field-row">
              <el-input v-model="form.expression" placeholder="请输入正则表达式" />
              <el-button type="primary" link @click="openRegexPicker">选择表达式</el-button>
            </div>
          </el-form-item>
        </template>

        <!-- Java脚本 -->
        <template v-else-if="currentForm === 'script'">
          <el-form-item label="脚本" required>
            <el-input v-model="form.script" type="textarea" :rows="6" placeholder="请输入 Java 校验脚本" />
          </el-form-item>
        </template>

        <!-- 记录数 / 波动 -->
        <template v-else-if="currentForm === 'count' || currentForm === 'fluctuation'">
          <el-form-item v-if="currentForm === 'fluctuation'" label="字段名">
            <el-select
              v-model="form.fieldNames"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              :loading="columnsLoading"
              placeholder="可选，可多选"
              style="width: 100%"
              @change="onFieldChange"
            >
              <el-option v-for="c in columns" :key="c.name" :label="c.name" :value="c.name" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="currentForm === 'fluctuation'" label="日期字段">
            <el-select v-model="form.dateColumn" filterable clearable placeholder="业务日期字段" style="width: 100%">
              <el-option v-for="c in columns" :key="c.name" :label="c.name" :value="c.name" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="currentForm === 'fluctuation'" label="业务日期">
            <el-input v-model="form.bizDate" placeholder="如 ${biz_date} 或 2026-01-01" />
          </el-form-item>
          <el-form-item v-if="currentForm === 'fluctuation'" label="对比周期">
            <el-select v-model="form.comparePeriod" style="width: 100%">
              <el-option label="上一周期" value="上一周期" />
              <el-option label="上周" value="上周" />
              <el-option label="上月" value="上月" />
              <el-option label="上年同期" value="上年同期" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="currentForm === 'fluctuation'" label="计算方式">
            <el-select v-model="form.calcMethod" style="width: 100%">
              <el-option label="环比" value="环比" />
              <el-option label="同比" value="同比" />
              <el-option label="差值" value="差值" />
            </el-select>
          </el-form-item>
          <el-form-item :label="currentForm === 'fluctuation' ? '波动范围' : '阈值'">
            <el-input-number v-model="form.threshold" :min="0" :precision="2" />
          </el-form-item>
        </template>

        <!-- 唯一性 / 准确性 / 通用 -->
        <template v-else>
          <el-form-item label="字段名" required :error="fieldError ? '请选择字段' : ''">
            <el-select
              v-model="form.fieldNames"
              multiple
              filterable
              clearable
              collapse-tags
              collapse-tags-tooltip
              :loading="columnsLoading"
              placeholder="请选择字段（可多选）"
              style="width: 100%"
              @change="onFieldChange"
            >
              <el-option v-for="c in columns" :key="c.name" :label="c.name" :value="c.name" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="currentForm === 'accuracy' || currentForm === 'generic'" label="表达式">
            <div class="field-row">
              <el-input v-model="form.expression" placeholder="可选，填写校验表达式" />
              <el-button type="primary" link @click="openRegexPicker">选择表达式</el-button>
            </div>
          </el-form-item>
        </template>

        <el-form-item label="错误描述" required>
          <el-input v-model="form.errorDesc" placeholder="请输入错误描述" maxlength="512" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="wizardVisible = false">取消</el-button>
        <el-button v-if="wizardStep === 1 && !editRuleId" type="primary" @click="goNext">下一步</el-button>
        <template v-else>
          <el-button v-if="!editRuleId" type="primary" @click="wizardStep = 1">上一步</el-button>
          <el-button type="primary" :loading="wizardSaving" @click="submitWizard">确定</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 选择正则 -->
    <el-dialog
      v-model="regexVisible"
      title="选择正则"
      width="760px"
      destroy-on-close
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form inline class="portal-inline-form portal-inline-form--sm" size="small" @submit.prevent="regexQuery = regexKeyword">
        <el-form-item label="名称" class="portal-field-lg">
          <el-input v-model="regexKeyword" clearable placeholder="请输入名称" @keyup.enter="regexQuery = regexKeyword" />
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="regexQuery = regexKeyword">查询</el-button>
          <el-button @click="regexKeyword = ''; regexQuery = ''">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table
        :data="filteredRegex"
        border
        highlight-current-row
        max-height="360"
        @current-change="(row: typeof REGEX_PRESETS[0] | null) => { regexSelected = row }"
        @row-click="(row: typeof REGEX_PRESETS[0]) => { regexSelected = row }"
      >
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="expression" label="表达式" min-width="280" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" width="140" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="regexVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRegex">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.model-rule-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 12px;
  min-height: 480px;
}
.tree-pane {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 10px;
  background: var(--el-fill-color-blank);
  overflow: auto;
}
.tree-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.rule-pane {
  min-width: 0;
}
.field-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.field-extra {
  flex-shrink: 0;
  white-space: nowrap;
}
.rule-form :deep(.el-form-item) {
  margin-bottom: 16px;
}
@media (max-width: 960px) {
  .model-rule-layout {
    grid-template-columns: 1fr;
  }
}
</style>
