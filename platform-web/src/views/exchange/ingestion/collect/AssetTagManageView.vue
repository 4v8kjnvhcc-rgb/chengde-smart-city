<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import api from '@/api/http'
import { statusLabel } from '@/utils/status-label'

const BASE = '/exchange/ingestion/tag-manage'
const tab = ref('tags')
const loading = ref(false)
const overview = ref<Record<string, unknown> | null>(null)
const dims = ref<Record<string, unknown>[]>([])
const tags = ref<Record<string, unknown>[]>([])
const rules = ref<Record<string, unknown>[]>([])
const pending = ref<Record<string, unknown>[]>([])
const bindings = ref<Record<string, unknown>[]>([])
const navItems = ref<Record<string, unknown>[]>([])
const searchItems = ref<Record<string, unknown>[]>([])
const coverage = ref<Record<string, unknown> | null>(null)
const audits = ref<Record<string, unknown>[]>([])
const drySamples = ref<Record<string, unknown>[]>([])
const related = ref<Record<string, unknown>[]>([])

const tagKw = ref('')
const tagDim = ref('')
const navDim = ref('BUSINESS')
const searchMode = ref('OR')
const searchTagIds = ref<number[]>([])
const excludeTagIds = ref('')

const tables = ref<Record<string, unknown>[]>([])

const DIM_OPTS = [
  { value: 'BUSINESS', label: '业务域' },
  { value: 'THEME', label: '主题' },
  { value: 'OBJECT', label: '数据对象' },
  { value: 'SCENE', label: '应用场景' },
  { value: 'FREQUENCY', label: '更新频率' },
  { value: 'QUALITY', label: '质量等级' },
  { value: 'OTHER', label: '其他' },
]
const VALUE_OPTS = [
  { value: 'ENUM', label: '枚举' },
  { value: 'TREE', label: '层级树' },
  { value: 'TEXT', label: '自由文本' },
  { value: 'BOOL', label: '布尔' },
  { value: 'MULTI', label: '多值' },
]
const ACTION_OPTS = [
  { value: 'SUGGEST', label: '建议打标' },
  { value: 'AUTO', label: '自动打标' },
]
const CONFLICT_OPTS = [
  { value: 'KEEP_MANUAL', label: '不覆盖人工' },
  { value: 'PRIORITY', label: '优先级最高' },
  { value: 'OVERRIDE', label: '覆盖已有' },
]

const tagDialog = ref(false)
const tagForm = reactive({
  id: undefined as number | undefined,
  tagCode: '',
  tagName: '',
  dimType: 'BUSINESS',
  valueType: 'ENUM',
  synonyms: '',
  ruleExpr: '',
  tagDesc: '',
  color: '',
  multiSelect: 1,
  requiredFlag: 0,
  sortNo: 0,
  status: 'ACTIVE',
})

const ruleDialog = ref(false)
const ruleForm = reactive({
  id: undefined as number | undefined,
  ruleCode: '',
  ruleName: '',
  tagId: undefined as number | undefined,
  conditionJson: '{"nameRegex":".*(keyword).*","assetTypes":["TABLE"],"minConfidence":70}',
  actionType: 'SUGGEST',
  priority: 50,
  conflictPolicy: 'KEEP_MANUAL',
  status: 'ACTIVE',
  description: '',
})

const bindDialog = ref(false)
const bindForm = reactive({
  tagId: undefined as number | undefined,
  assetType: 'TABLE',
  assetId: undefined as number | undefined,
  note: '',
})
const batchAssetIds = ref('')

const mergeDialog = ref(false)
const mergeForm = reactive({ keepId: undefined as number | undefined, dropId: undefined as number | undefined })

const pendingSelected = ref<Record<string, unknown>[]>([])

async function loadOverview() {
  overview.value = (await api.get(`${BASE}/overview`)).data
}
async function loadDims() {
  dims.value = (await api.get(`${BASE}/dims`)).data || []
}
async function loadTags() {
  tags.value = (await api.get(`${BASE}/tags`, {
    params: { keyword: tagKw.value || undefined, dimType: tagDim.value || undefined },
  })).data || []
}
async function loadRules() {
  rules.value = (await api.get(`${BASE}/rules`)).data || []
}
async function loadPending() {
  pending.value = (await api.get(`${BASE}/pending`, { params: { limit: 100 } })).data || []
}
async function loadBindings() {
  bindings.value = (await api.get(`${BASE}/bindings`, { params: { limit: 100 } })).data || []
}
async function loadNavigate() {
  navItems.value = (await api.get(`${BASE}/navigate`, { params: { dimType: navDim.value } })).data || []
}
async function loadCoverage() {
  coverage.value = (await api.get(`${BASE}/coverage`)).data
}
async function loadAudits() {
  audits.value = (await api.get(`${BASE}/audit-logs`, { params: { limit: 50 } })).data || []
}
async function loadTables() {
  try {
    tables.value = (await api.get('/exchange/ingestion/register/tables')).data || []
  } catch {
    tables.value = []
  }
}

async function reload() {
  loading.value = true
  try {
    await loadOverview()
    if (tab.value === 'tags') await Promise.all([loadTags(), loadDims()])
    else if (tab.value === 'rules') await Promise.all([loadRules(), loadTags()])
    else if (tab.value === 'bind') await Promise.all([loadTags(), loadTables(), loadBindings()])
    else if (tab.value === 'pending') await loadPending()
    else if (tab.value === 'search') await Promise.all([loadTags(), loadNavigate()])
    else if (tab.value === 'govern') await loadCoverage()
    else if (tab.value === 'audit') await loadAudits()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function onTab(name: string | number) {
  tab.value = String(name)
  await reload()
}

function openTag(row?: Record<string, unknown>) {
  tagForm.id = row?.id as number | undefined
  tagForm.tagCode = String(row?.tagCode || '')
  tagForm.tagName = String(row?.tagName || '')
  tagForm.dimType = String(row?.dimType || 'BUSINESS')
  tagForm.valueType = String(row?.valueType || 'ENUM')
  tagForm.synonyms = String(row?.synonyms || '')
  tagForm.ruleExpr = String(row?.ruleExpr || '')
  tagForm.tagDesc = String(row?.tagDesc || '')
  tagForm.color = String(row?.color || '')
  tagForm.multiSelect = Number(row?.multiSelect ?? 1)
  tagForm.requiredFlag = Number(row?.requiredFlag ?? 0)
  tagForm.sortNo = Number(row?.sortNo ?? 0)
  tagForm.status = String(row?.status || 'ACTIVE')
  tagDialog.value = true
}

async function saveTag() {
  if (!tagForm.tagName.trim()) {
    ElMessage.warning('请填写标签名称')
    return
  }
  await api.post(`${BASE}/tags`, { ...tagForm })
  tagDialog.value = false
  ElMessage.success('标签已保存')
  await loadTags()
  await loadOverview()
}

async function disableTag(row: Record<string, unknown>) {
  await ElMessageBox.confirm(`停用标签「${row.tagName}」？`, '确认')
  await api.post(`${BASE}/tags/${row.id}/disable`)
  ElMessage.success('已停用')
  await loadTags()
}

async function saveMerge() {
  if (!mergeForm.keepId || !mergeForm.dropId) {
    ElMessage.warning('请选择保留与废弃标签')
    return
  }
  const res = await api.post(`${BASE}/tags/merge`, { ...mergeForm })
  ElMessage.success(`合并完成，迁移 ${res.data?.migrated ?? 0} 条绑定`)
  mergeDialog.value = false
  await loadTags()
}

function openRule(row?: Record<string, unknown>) {
  ruleForm.id = row?.id as number | undefined
  ruleForm.ruleCode = String(row?.ruleCode || '')
  ruleForm.ruleName = String(row?.ruleName || '')
  ruleForm.tagId = row?.tagId as number | undefined
  ruleForm.conditionJson = String(row?.conditionJson || '{"nameRegex":".*(keyword).*","assetTypes":["TABLE"],"minConfidence":70}')
  ruleForm.actionType = String(row?.actionType || 'SUGGEST')
  ruleForm.priority = Number(row?.priority ?? 50)
  ruleForm.conflictPolicy = String(row?.conflictPolicy || 'KEEP_MANUAL')
  ruleForm.status = String(row?.status || 'ACTIVE')
  ruleForm.description = String(row?.description || '')
  drySamples.value = []
  ruleDialog.value = true
}

async function saveRule() {
  if (!ruleForm.ruleCode || !ruleForm.ruleName || !ruleForm.tagId) {
    ElMessage.warning('请填写编码、名称与目标标签')
    return
  }
  try {
    JSON.parse(ruleForm.conditionJson || '{}')
  } catch {
    ElMessage.warning('条件 JSON 格式错误')
    return
  }
  await api.post(`${BASE}/rules`, { ...ruleForm })
  ruleDialog.value = false
  ElMessage.success('规则已保存')
  await loadRules()
}

async function dryRun(row: Record<string, unknown>) {
  const res = await api.post(`${BASE}/rules/${row.id}/dry-run`, null, { params: { limit: 20 } })
  drySamples.value = (res.data?.samples as Record<string, unknown>[]) || []
  ElMessage.success(`试跑命中 ${res.data?.hitCount ?? 0} 条（未写库）`)
}

async function runRule(row: Record<string, unknown>) {
  await ElMessageBox.confirm(`执行规则「${row.ruleName}」并写入打标结果？默认不覆盖人工标签。`, '执行识别')
  const res = await api.post(`${BASE}/rules/${row.id}/run`, null, { params: { limit: 500 } })
  ElMessage.success(`命中 ${res.data?.hitCount}，应用 ${res.data?.applied}，跳过 ${res.data?.skipped}`)
  await reload()
}

async function runAllRules() {
  await ElMessageBox.confirm('按优先级执行全部启用规则？', '全量识别')
  const res = await api.post(`${BASE}/rules/run-all`, null, { params: { limitPerRule: 300 } })
  ElMessage.success(`规则 ${res.data?.rules} 条，命中 ${res.data?.totalHits}，应用 ${res.data?.totalApplied}`)
  await reload()
}

async function publishRule(row: Record<string, unknown>) {
  await api.post(`${BASE}/rules/${row.id}/publish`)
  ElMessage.success('规则已发布启用')
  await loadRules()
}

async function deleteRule(row: Record<string, unknown>) {
  await ElMessageBox.confirm(`删除规则「${row.ruleName}」？`, '确认')
  await api.delete(`${BASE}/rules/${row.id}`)
  ElMessage.success('已删除')
  await loadRules()
}

async function saveBind() {
  if (!bindForm.tagId || !bindForm.assetId) {
    ElMessage.warning('请选择标签与资产')
    return
  }
  await api.post(`${BASE}/bindings`, { ...bindForm, source: 'MANUAL', confirmStatus: 'CONFIRMED' })
  bindDialog.value = false
  ElMessage.success('打标成功')
  await loadBindings()
  await loadOverview()
}

async function batchBind() {
  if (!bindForm.tagId || !batchAssetIds.value.trim()) {
    ElMessage.warning('请选择标签并填写资产 ID（逗号分隔）')
    return
  }
  const assetIds = batchAssetIds.value.split(/[,;\s]+/).filter(Boolean).map(Number)
  const res = await api.post(`${BASE}/bindings/batch`, {
    tagId: bindForm.tagId,
    assetType: bindForm.assetType,
    assetIds,
  })
  ElMessage.success(`批量打标：成功 ${res.data?.ok}，失败 ${res.data?.fail}`)
  await loadBindings()
}

async function unbind(row: Record<string, unknown>) {
  await ElMessageBox.confirm('摘除该标签绑定？', '确认')
  await api.delete(`${BASE}/bindings/${row.id}`)
  ElMessage.success('已摘标')
  await loadBindings()
}

async function confirmOne(row: Record<string, unknown>, accept: boolean) {
  await api.post(`${BASE}/pending/${row.id}/confirm`, null, { params: { accept } })
  ElMessage.success(accept ? '已确认' : '已驳回')
  await loadPending()
  await loadOverview()
}

async function batchConfirm(accept: boolean) {
  const ids = pendingSelected.value.map((r) => Number(r.id)).filter(Boolean)
  if (!ids.length) {
    ElMessage.warning('请先勾选待确认项')
    return
  }
  await api.post(`${BASE}/pending/batch-confirm`, { ids, accept })
  ElMessage.success(accept ? '批量已通过' : '批量已驳回')
  await loadPending()
}

async function doSearch() {
  if (!searchTagIds.value.length) {
    ElMessage.warning('请选择至少一个标签')
    return
  }
  const res = await api.post(`${BASE}/search`, {
    tagIds: searchTagIds.value,
    mode: searchMode.value,
    excludeTagIds: excludeTagIds.value || undefined,
    limit: 100,
  })
  searchItems.value = (res.data?.items as Record<string, unknown>[]) || []
  ElMessage.success(`找到 ${res.data?.total ?? 0} 条资产`)
}

async function openNavTag(row: Record<string, unknown>) {
  searchTagIds.value = [Number(row.tagId)]
  searchMode.value = 'OR'
  tab.value = 'search'
  await doSearch()
  related.value = (await api.get(`${BASE}/related/${row.tagId}`)).data || []
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <PageCard title="数据标签管理">
      <p class="hint">
        按业务含义维护标签体系与智能识别规则；支持快速打标、建议确认、按标签检索导航与覆盖率治理。
        侧重业务语义定位，可与分级分类/脱敏联动，不等同于安全分级。
      </p>
      <el-descriptions v-if="overview" :column="4" border size="small" class="mb">
        <el-descriptions-item label="启用标签">{{ overview.activeTags }}</el-descriptions-item>
        <el-descriptions-item label="业务标签">{{ overview.customTags }}</el-descriptions-item>
        <el-descriptions-item label="启用规则">{{ overview.activeRules }}</el-descriptions-item>
        <el-descriptions-item label="待确认">{{ overview.pendingConfirm }}</el-descriptions-item>
        <el-descriptions-item label="表资产">{{ overview.tableCount }}</el-descriptions-item>
        <el-descriptions-item label="已打标表">{{ overview.taggedTables }}</el-descriptions-item>
        <el-descriptions-item label="覆盖率">{{ overview.coverageRate }}%</el-descriptions-item>
        <el-descriptions-item label="绑定数">{{ overview.bindings }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs :model-value="tab" @tab-change="onTab">
        <el-tab-pane label="标签体系" name="tags" />
        <el-tab-pane label="智能识别规则" name="rules" />
        <el-tab-pane label="快速打标" name="bind" />
        <el-tab-pane label="待确认" name="pending" />
        <el-tab-pane label="检索导航" name="search" />
        <el-tab-pane label="覆盖率治理" name="govern" />
        <el-tab-pane label="变更审计" name="audit" />
      </el-tabs>

      <!-- 标签体系 -->
      <div v-if="tab === 'tags'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="维度" class="portal-field-md">
            <el-select v-model="tagDim" clearable placeholder="全部" @change="loadTags">
              <el-option v-for="d in DIM_OPTS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词" class="portal-field-lg">
            <el-input v-model="tagKw" clearable placeholder="名称/编码/同义词" @keyup.enter="loadTags" />
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="loadTags">查询</el-button>
            <el-button type="primary" @click="openTag()">新建标签</el-button>
            <el-button @click="mergeDialog = true">合并标签</el-button>
          </el-form-item>
        </el-form>
        <el-alert type="info" :closable="false" show-icon class="mb"
          title="维度可配置（业务域/主题/对象等）；国标类目仅可维护规则与同义词。受控词表优先，同义词便于检索命中。" />
        <el-table :data="tags" stripe>
          <el-table-column prop="tagCode" label="编码" width="120" show-overflow-tooltip />
          <el-table-column prop="tagName" label="名称" width="140" show-overflow-tooltip />
          <el-table-column label="维度" width="100">
            <template #default="{ row }">{{ statusLabel(row.dimType) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ statusLabel(row.valueType) }}</template>
          </el-table-column>
          <el-table-column prop="synonyms" label="同义词" min-width="140" show-overflow-tooltip />
          <el-table-column label="来源" width="90">
            <template #default="{ row }">{{ statusLabel(row.tagSource) }}</template>
          </el-table-column>
          <el-table-column prop="bindingCount" label="绑定数" width="80" />
          <el-table-column label="必填" width="70">
            <template #default="{ row }">{{ row.requiredFlag ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTag(row)">编辑</el-button>
              <el-button v-if="row.tagSource !== 'STANDARD' && row.status === 'ACTIVE'" link type="danger" @click="disableTag(row)">停用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 规则 -->
      <div v-if="tab === 'rules'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="openRule()">新建规则</el-button>
            <el-button type="warning" @click="runAllRules">全量识别</el-button>
          </el-form-item>
        </el-form>
        <el-alert type="info" :closable="false" show-icon class="mb"
          title="条件 JSON 支持 nameRegex、commentKeywords、dataTypes、assetTypes、minConfidence。默认冲突策略：不覆盖人工标签。" />
        <el-table :data="rules" stripe>
          <el-table-column prop="ruleCode" label="编码" width="130" show-overflow-tooltip />
          <el-table-column prop="ruleName" label="名称" width="150" show-overflow-tooltip />
          <el-table-column prop="tagName" label="目标标签" width="120" />
          <el-table-column label="动作" width="100">
            <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" width="80" />
          <el-table-column label="冲突" width="110">
            <template #default="{ row }">{{ statusLabel(row.conflictPolicy) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">{{ statusLabel(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRule(row)">编辑</el-button>
              <el-button link @click="dryRun(row)">试跑</el-button>
              <el-button link type="warning" @click="runRule(row)">执行</el-button>
              <el-button link @click="publishRule(row)">发布</el-button>
              <el-button link type="danger" @click="deleteRule(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="drySamples.length" class="mt">
          <div class="block-title">试跑命中样例</div>
          <el-table :data="drySamples" size="small" max-height="240">
            <el-table-column prop="assetType" label="类型" width="90">
              <template #default="{ row }">{{ statusLabel(row.assetType) }}</template>
            </el-table-column>
            <el-table-column prop="assetCode" label="编码" width="140" />
            <el-table-column prop="assetName" label="名称" min-width="160" />
            <el-table-column prop="confidence" label="置信度" width="90" />
          </el-table>
        </div>
      </div>

      <!-- 快速打标 -->
      <div v-if="tab === 'bind'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="bindDialog = true; bindForm.tagId = undefined; bindForm.assetId = undefined">单条打标</el-button>
          </el-form-item>
        </el-form>
        <el-form label-width="100px" class="mb" style="max-width:720px">
          <el-form-item label="批量标签">
            <el-select v-model="bindForm.tagId" filterable placeholder="选择标签" style="width:100%">
              <el-option v-for="t in tags.filter(x => x.level !== 1)" :key="String(t.id)" :label="`${t.tagName} (${t.tagCode})`" :value="t.id as number" />
            </el-select>
          </el-form-item>
          <el-form-item label="资产类型">
            <el-radio-group v-model="bindForm.assetType">
              <el-radio value="TABLE">表</el-radio>
              <el-radio value="COLUMN">字段</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="资产ID列表">
            <el-input v-model="batchAssetIds" type="textarea" :rows="2" placeholder="逗号分隔，如 12,15,20" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="batchBind">批量打标</el-button>
          </el-form-item>
        </el-form>
        <div class="block-title">最近绑定</div>
        <el-table :data="bindings" stripe>
          <el-table-column prop="tagName" label="标签" width="120" />
          <el-table-column label="资产类型" width="90">
            <template #default="{ row }">{{ statusLabel(row.assetType) }}</template>
          </el-table-column>
          <el-table-column prop="assetLabel" label="资产" min-width="160" show-overflow-tooltip />
          <el-table-column label="来源" width="90">
            <template #default="{ row }">{{ statusLabel(row.source) }}</template>
          </el-table-column>
          <el-table-column label="确认" width="90">
            <template #default="{ row }">{{ statusLabel(row.confirmStatus) }}</template>
          </el-table-column>
          <el-table-column prop="confidence" label="置信度" width="90" />
          <el-table-column prop="taggedBy" label="操作人" width="100" />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="danger" @click="unbind(row)">摘标</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 待确认 -->
      <div v-if="tab === 'pending'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item class="portal-form-actions">
            <el-button type="success" @click="batchConfirm(true)">批量通过</el-button>
            <el-button type="danger" @click="batchConfirm(false)">批量驳回</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="pending" stripe @selection-change="(rows: Record<string, unknown>[]) => (pendingSelected = rows)">
          <el-table-column type="selection" width="48" />
          <el-table-column prop="tagName" label="建议标签" width="130" />
          <el-table-column label="资产" min-width="180">
            <template #default="{ row }">{{ statusLabel(row.assetType) }} · {{ row.assetLabel || row.assetId }}</template>
          </el-table-column>
          <el-table-column prop="confidence" label="置信度" width="90" />
          <el-table-column prop="note" label="来源说明" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="success" @click="confirmOne(row, true)">通过</el-button>
              <el-button link type="danger" @click="confirmOne(row, false)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 检索导航 -->
      <div v-if="tab === 'search'">
        <el-row :gutter="16">
          <el-col :span="8">
            <div class="block-title">标签导航</div>
            <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
              <el-form-item label="维度" class="portal-field-md">
                <el-select v-model="navDim" @change="loadNavigate">
                  <el-option v-for="d in DIM_OPTS" :key="d.value" :label="d.label" :value="d.value" />
                </el-select>
              </el-form-item>
            </el-form>
            <el-table :data="navItems" size="small" max-height="420" @row-click="openNavTag">
              <el-table-column prop="tagName" label="标签" />
              <el-table-column prop="assetCount" label="资产数" width="80" />
            </el-table>
            <div v-if="related.length" class="mt">
              <div class="block-title">相关标签</div>
              <el-tag v-for="r in related" :key="String(r.tagId)" class="tag-chip" @click="openNavTag(r)">
                {{ r.tagName }} ({{ r.cooccur }})
              </el-tag>
            </div>
          </el-col>
          <el-col :span="16">
            <div class="block-title">组合筛选</div>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="标签" class="portal-field-xl">
                <el-select v-model="searchTagIds" multiple filterable collapse-tags placeholder="选择标签">
                  <el-option v-for="t in tags.filter(x => x.level !== 1)" :key="String(t.id)" :label="String(t.tagName)" :value="t.id as number" />
                </el-select>
              </el-form-item>
              <el-form-item label="逻辑" class="portal-field-sm">
                <el-select v-model="searchMode">
                  <el-option label="或(OR)" value="OR" />
                  <el-option label="且(AND)" value="AND" />
                </el-select>
              </el-form-item>
              <el-form-item label="排除" class="portal-field-md">
                <el-input v-model="excludeTagIds" placeholder="标签ID逗号分隔" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="doSearch">检索</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="searchItems" stripe>
              <el-table-column prop="assetCode" label="编码" width="140" />
              <el-table-column prop="assetName" label="名称" min-width="160" />
              <el-table-column label="标签" min-width="200">
                <template #default="{ row }">
                  <el-tag v-for="t in (row.tags as Record<string, unknown>[] || [])" :key="String(t.tagId)" size="small" class="tag-chip"
                    :type="searchTagIds.includes(Number(t.tagId)) ? 'success' : 'info'">
                    {{ t.tagName }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-col>
        </el-row>
      </div>

      <!-- 治理 -->
      <div v-if="tab === 'govern' && coverage">
        <el-descriptions :column="3" border size="small" class="mb">
          <el-descriptions-item label="表资产">{{ coverage.tableCount }}</el-descriptions-item>
          <el-descriptions-item label="已打标">{{ coverage.taggedTables }}</el-descriptions-item>
          <el-descriptions-item label="覆盖率">{{ coverage.coverageRate }}%</el-descriptions-item>
        </el-descriptions>
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="block-title">未打标资产（样例）</div>
            <el-table :data="(coverage.untaggedSamples as Record<string, unknown>[]) || []" size="small" max-height="280">
              <el-table-column prop="assetCode" label="编码" width="120" />
              <el-table-column prop="assetName" label="名称" />
            </el-table>
          </el-col>
          <el-col :span="12">
            <div class="block-title">必填标签缺失告警</div>
            <el-table :data="(coverage.missingRequired as Record<string, unknown>[]) || []" size="small" max-height="280">
              <el-table-column prop="assetName" label="资产" />
              <el-table-column prop="tagName" label="缺失标签" width="120" />
            </el-table>
          </el-col>
        </el-row>
        <el-row :gutter="16" class="mt">
          <el-col :span="12">
            <div class="block-title">热门标签</div>
            <el-table :data="(coverage.hotTags as Record<string, unknown>[]) || []" size="small">
              <el-table-column prop="tagName" label="标签" />
              <el-table-column prop="count" label="绑定数" width="90" />
            </el-table>
          </el-col>
          <el-col :span="12">
            <div class="block-title">冷门标签</div>
            <el-table :data="(coverage.coldTags as Record<string, unknown>[]) || []" size="small">
              <el-table-column prop="tagName" label="标签" />
              <el-table-column prop="count" label="绑定数" width="90" />
            </el-table>
          </el-col>
        </el-row>
      </div>

      <!-- 审计 -->
      <div v-if="tab === 'audit'">
        <el-table :data="audits" stripe>
          <el-table-column prop="createdAt" label="时间" width="170" />
          <el-table-column label="动作" width="110">
            <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
          </el-table-column>
          <el-table-column prop="tagId" label="标签ID" width="90" />
          <el-table-column label="资产" width="140">
            <template #default="{ row }">
              <span v-if="row.assetType">{{ statusLabel(row.assetType) }}#{{ row.assetId }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="90">
            <template #default="{ row }">{{ statusLabel(row.source) }}</template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="100" />
          <el-table-column prop="afterJson" label="变更摘要" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
    </PageCard>

    <el-dialog v-model="tagDialog" :title="tagForm.id ? '编辑标签' : '新建标签'" width="560px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="名称" required><el-input v-model="tagForm.tagName" /></el-form-item>
        <el-form-item v-if="!tagForm.id" label="编码"><el-input v-model="tagForm.tagCode" placeholder="可选，自动生成" /></el-form-item>
        <el-form-item label="维度">
          <el-select v-model="tagForm.dimType" style="width:100%">
            <el-option v-for="d in DIM_OPTS" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="取值类型">
          <el-select v-model="tagForm.valueType" style="width:100%">
            <el-option v-for="d in VALUE_OPTS" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="同义词"><el-input v-model="tagForm.synonyms" placeholder="逗号分隔，如 人口,户籍" /></el-form-item>
        <el-form-item label="识别表达式"><el-input v-model="tagForm.ruleExpr" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="tagForm.tagDesc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="颜色"><el-input v-model="tagForm.color" placeholder="#409EFF" /></el-form-item>
        <el-form-item label="多选"><el-switch v-model="tagForm.multiSelect" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="必填"><el-switch v-model="tagForm.requiredFlag" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="tagForm.sortNo" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTag">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ruleDialog" :title="ruleForm.id ? '编辑规则' : '新建规则'" width="640px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="ruleForm.ruleCode" :disabled="!!ruleForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="ruleForm.ruleName" /></el-form-item>
        <el-form-item label="目标标签" required>
          <el-select v-model="ruleForm.tagId" filterable style="width:100%">
            <el-option v-for="t in tags.filter(x => x.level !== 1)" :key="String(t.id)" :label="`${t.tagName} (${t.tagCode})`" :value="t.id as number" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件 JSON" required>
          <el-input v-model="ruleForm.conditionJson" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="ruleForm.actionType" style="width:100%">
            <el-option v-for="d in ACTION_OPTS" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="冲突策略">
          <el-select v-model="ruleForm.conflictPolicy" style="width:100%">
            <el-option v-for="d in CONFLICT_OPTS" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="ruleForm.priority" :min="1" :max="999" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="ruleForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindDialog" title="单条打标" width="480px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="标签" required>
          <el-select v-model="bindForm.tagId" filterable style="width:100%">
            <el-option v-for="t in tags.filter(x => x.level !== 1)" :key="String(t.id)" :label="String(t.tagName)" :value="t.id as number" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="bindForm.assetType">
            <el-radio value="TABLE">表</el-radio>
            <el-radio value="COLUMN">字段</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="bindForm.assetType === 'TABLE'" label="数据表">
          <el-select v-model="bindForm.assetId" filterable style="width:100%">
            <el-option v-for="t in tables" :key="String(t.id)" :label="`${t.tableName || t.tableCode} (#${t.id})`" :value="t.id as number" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="字段ID">
          <el-input-number v-model="bindForm.assetId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="bindForm.note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBind">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="mergeDialog" title="合并标签" width="480px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="保留标签">
          <el-select v-model="mergeForm.keepId" filterable style="width:100%">
            <el-option v-for="t in tags.filter(x => x.tagSource !== 'STANDARD')" :key="'k'+t.id" :label="String(t.tagName)" :value="t.id as number" />
          </el-select>
        </el-form-item>
        <el-form-item label="废弃标签">
          <el-select v-model="mergeForm.dropId" filterable style="width:100%">
            <el-option v-for="t in tags.filter(x => x.tagSource !== 'STANDARD')" :key="'d'+t.id" :label="String(t.tagName)" :value="t.id as number" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mergeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveMerge">合并</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; line-height: 1.5; }
.mb { margin-bottom: 12px; }
.mt { margin-top: 12px; }
.block-title { font-weight: 600; margin: 8px 0; }
.tag-chip { margin: 0 6px 6px 0; cursor: pointer; }
</style>
