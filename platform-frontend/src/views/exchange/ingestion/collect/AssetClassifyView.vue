<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import api from '@/api/http'
import { statusLabel } from '@/utils/status-label'

interface LevelRow {
  id: number
  levelCode: string
  levelName: string
  sortNo: number
  sensitivityScore: number
  controlStrength: string
  description?: string
  shareAllowed: number
  openAllowed: number
  maskRequired: number
  approvalLevel: string
  status: string
}
interface CategoryRow {
  id: number
  categoryCode: string
  categoryName: string
  parentId?: number
  dimType: string
  path?: string
  sortNo: number
  shareScopeHint?: string
  description?: string
  status: string
}
interface MarkRow {
  id: number
  assetType: string
  assetId: number
  assetCode?: string
  assetName?: string
  categoryId?: number
  levelCode: string
  gradeBasis: string
  gradeReason?: string
  versionNo: number
  gradedBy?: string
  gradedAt?: string
}
interface ScopeRow {
  id: number
  ruleCode: string
  ruleName: string
  levelCode?: string
  categoryId?: number
  actionType: string
  allowFlag: number
  maskRequired: number
  approvalRequired: number
  subjectScope: string
  severityWeight: number
  remark?: string
  status: string
}

const tab = ref('marks')
const loading = ref(false)
const levels = ref<LevelRow[]>([])
const categories = ref<CategoryRow[]>([])
const marks = ref<MarkRow[]>([])
const scopes = ref<ScopeRow[]>([])
const audits = ref<Record<string, unknown>[]>([])
const hits = ref<Record<string, unknown>[]>([])
const candidates = ref<Record<string, unknown>[]>([])

const markKw = ref('')
const markLevel = ref('')
const markDialog = ref(false)
const markForm = reactive({
  assetType: 'TABLE',
  assetId: undefined as number | undefined,
  assetName: '',
  categoryId: undefined as number | undefined,
  levelCode: 'GENERAL',
  gradeBasis: 'MANUAL',
  gradeReason: '',
  personalInfoScore: 1,
  businessCriticalScore: 1,
  leakImpactScore: 1,
})

const levelDialog = ref(false)
const levelForm = reactive({
  id: undefined as number | undefined,
  levelCode: '',
  levelName: '',
  sortNo: 0,
  sensitivityScore: 1,
  controlStrength: 'LOW',
  description: '',
  shareAllowed: 1,
  openAllowed: 0,
  maskRequired: 0,
  approvalLevel: 'NONE',
  status: 'ACTIVE',
})

const catDialog = ref(false)
const catForm = reactive({
  id: undefined as number | undefined,
  categoryCode: '',
  categoryName: '',
  dimType: 'BUSINESS',
  sortNo: 0,
  shareScopeHint: '',
  description: '',
  status: 'ACTIVE',
})

const scopeDialog = ref(false)
const scopeForm = reactive({
  id: undefined as number | undefined,
  ruleCode: '',
  ruleName: '',
  levelCode: '',
  categoryId: undefined as number | undefined,
  actionType: 'SHARE',
  allowFlag: 1,
  maskRequired: 0,
  approvalRequired: 0,
  subjectScope: 'ALL',
  severityWeight: 1,
  remark: '',
  status: 'ACTIVE',
})

const evalForm = reactive({
  assetType: 'TABLE',
  assetId: undefined as number | undefined,
  actionType: 'SHARE',
  masked: false,
  approved: false,
})
const evalResult = ref<Record<string, unknown> | null>(null)

const DIM_LABEL: Record<string, string> = {
  BUSINESS: '业务主题',
  OBJECT: '数据对象',
  SOURCE: '来源',
  CONTENT: '内容属性',
}

function catName(id?: number) {
  if (!id) return '-'
  return categories.value.find((c) => c.id === id)?.categoryName || String(id)
}
function levelName(code?: string) {
  if (!code) return '-'
  return levels.value.find((l) => l.levelCode === code)?.levelName || code
}

async function loadLevels() {
  levels.value = (await api.get('/exchange/ingestion/classify-grade/levels')).data || []
}
async function loadCategories() {
  categories.value = (await api.get('/exchange/ingestion/classify-grade/categories')).data || []
}
async function loadMarks() {
  marks.value =
    (
      await api.get('/exchange/ingestion/classify-grade/marks', {
        params: { keyword: markKw.value || undefined, levelCode: markLevel.value || undefined },
      })
    ).data || []
}
async function loadScopes() {
  scopes.value = (await api.get('/exchange/ingestion/classify-grade/scope-rules')).data || []
}
async function loadAudit() {
  audits.value = (await api.get('/exchange/ingestion/classify-grade/audit-logs', { params: { limit: 50 } })).data || []
  hits.value = (await api.get('/exchange/ingestion/classify-grade/hit-logs', { params: { limit: 50 } })).data || []
}

async function reloadAll() {
  loading.value = true
  try {
    await Promise.all([loadLevels(), loadCategories()])
    if (tab.value === 'marks') await loadMarks()
    else if (tab.value === 'scopes') await loadScopes()
    else if (tab.value === 'audit') await loadAudit()
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function onTabChange(name: string | number) {
  tab.value = String(name)
  await reloadAll()
}

async function loadCandidates() {
  candidates.value =
    (
      await api.get('/exchange/ingestion/classify-grade/candidates', {
        params: { assetType: markForm.assetType, keyword: markForm.assetName || undefined },
      })
    ).data || []
}

function openMark() {
  markForm.assetType = 'TABLE'
  markForm.assetId = undefined
  markForm.assetName = ''
  markForm.categoryId = undefined
  markForm.levelCode = 'GENERAL'
  markForm.gradeBasis = 'MANUAL'
  markForm.gradeReason = ''
  markForm.personalInfoScore = 1
  markForm.businessCriticalScore = 1
  markForm.leakImpactScore = 1
  markDialog.value = true
  void loadCandidates()
}

async function suggestLevel() {
  const res = await api.post('/exchange/ingestion/classify-grade/suggest-level', {
    personalInfoScore: markForm.personalInfoScore,
    businessCriticalScore: markForm.businessCriticalScore,
    leakImpactScore: markForm.leakImpactScore,
  })
  markForm.levelCode = String(res.data?.suggestedLevelCode || 'GENERAL')
  markForm.gradeBasis = 'RULE_SUGGEST'
  markForm.gradeReason = String(res.data?.reason || '')
  ElMessage.success(`建议级别：${res.data?.suggestedLevelName}`)
}

async function saveMark() {
  if (!markForm.assetId) {
    ElMessage.warning('请选择资产')
    return
  }
  await api.post('/exchange/ingestion/classify-grade/marks', { ...markForm })
  ElMessage.success('标注已保存')
  markDialog.value = false
  await loadMarks()
}

function openLevel(row?: LevelRow) {
  levelForm.id = row?.id
  levelForm.levelCode = row?.levelCode || ''
  levelForm.levelName = row?.levelName || ''
  levelForm.sortNo = row?.sortNo ?? 0
  levelForm.sensitivityScore = row?.sensitivityScore ?? 1
  levelForm.controlStrength = row?.controlStrength || 'LOW'
  levelForm.description = row?.description || ''
  levelForm.shareAllowed = row?.shareAllowed ?? 1
  levelForm.openAllowed = row?.openAllowed ?? 0
  levelForm.maskRequired = row?.maskRequired ?? 0
  levelForm.approvalLevel = row?.approvalLevel || 'NONE'
  levelForm.status = row?.status || 'ACTIVE'
  levelDialog.value = true
}

async function saveLevel() {
  if (!levelForm.levelCode || !levelForm.levelName) {
    ElMessage.warning('请填写级别编码与名称')
    return
  }
  await api.post('/exchange/ingestion/classify-grade/levels', { ...levelForm })
  ElMessage.success('级别已保存')
  levelDialog.value = false
  await loadLevels()
}

function openCat(row?: CategoryRow) {
  catForm.id = row?.id
  catForm.categoryCode = row?.categoryCode || ''
  catForm.categoryName = row?.categoryName || ''
  catForm.dimType = row?.dimType || 'BUSINESS'
  catForm.sortNo = row?.sortNo ?? 0
  catForm.shareScopeHint = row?.shareScopeHint || ''
  catForm.description = row?.description || ''
  catForm.status = row?.status || 'ACTIVE'
  catDialog.value = true
}

async function saveCat() {
  if (!catForm.categoryCode || !catForm.categoryName) {
    ElMessage.warning('请填写分类编码与名称')
    return
  }
  await api.post('/exchange/ingestion/classify-grade/categories', { ...catForm })
  ElMessage.success('分类已保存')
  catDialog.value = false
  await loadCategories()
}

async function removeCat(row: CategoryRow) {
  await ElMessageBox.confirm(`删除分类「${row.categoryName}」？`, '确认')
  await api.delete(`/exchange/ingestion/classify-grade/categories/${row.id}`)
  ElMessage.success('已删除')
  await loadCategories()
}

function openScope(row?: ScopeRow) {
  scopeForm.id = row?.id
  scopeForm.ruleCode = row?.ruleCode || ''
  scopeForm.ruleName = row?.ruleName || ''
  scopeForm.levelCode = row?.levelCode || ''
  scopeForm.categoryId = row?.categoryId
  scopeForm.actionType = row?.actionType || 'SHARE'
  scopeForm.allowFlag = row?.allowFlag ?? 1
  scopeForm.maskRequired = row?.maskRequired ?? 0
  scopeForm.approvalRequired = row?.approvalRequired ?? 0
  scopeForm.subjectScope = row?.subjectScope || 'ALL'
  scopeForm.severityWeight = row?.severityWeight ?? 1
  scopeForm.remark = row?.remark || ''
  scopeForm.status = row?.status || 'ACTIVE'
  scopeDialog.value = true
}

async function saveScope() {
  if (!scopeForm.ruleCode || !scopeForm.ruleName) {
    ElMessage.warning('请填写规则编码与名称')
    return
  }
  await api.post('/exchange/ingestion/classify-grade/scope-rules', {
    ...scopeForm,
    levelCode: scopeForm.levelCode || null,
    categoryId: scopeForm.categoryId || null,
  })
  ElMessage.success('策略已保存')
  scopeDialog.value = false
  await loadScopes()
}

async function removeScope(row: ScopeRow) {
  await ElMessageBox.confirm(`删除策略「${row.ruleName}」？`, '确认')
  await api.delete(`/exchange/ingestion/classify-grade/scope-rules/${row.id}`)
  ElMessage.success('已删除')
  await loadScopes()
}

async function runEvaluate() {
  if (!evalForm.assetId) {
    ElMessage.warning('请填写资产 ID')
    return
  }
  evalResult.value = (await api.post('/exchange/ingestion/classify-grade/evaluate', { ...evalForm })).data
}

onMounted(reloadAll)
</script>

<template>
  <div v-loading="loading">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom:12px"
      title="对齐 GB/T 43697-2024：识别→分类→分级→管控→复核。级别与分类可配置；类别+级别冲突时取更严策略。不含涉密/军事数据。"
    />
    <el-tabs :model-value="tab" @tab-change="onTabChange">
      <el-tab-pane label="资产标注与定级" name="marks" />
      <el-tab-pane label="级别字典" name="levels" />
      <el-tab-pane label="分类体系" name="categories" />
      <el-tab-pane label="使用范围策略" name="scopes" />
      <el-tab-pane label="审计与命中" name="audit" />
    </el-tabs>

    <PageCard v-if="tab === 'marks'" title="资产分类分级标注">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="关键词" class="portal-field-md">
          <el-input v-model="markKw" clearable placeholder="资产名称/编码" @keyup.enter="loadMarks" />
        </el-form-item>
        <el-form-item label="级别" class="portal-field-sm">
          <el-select v-model="markLevel" clearable placeholder="全部">
            <el-option v-for="l in levels" :key="l.levelCode" :label="l.levelName" :value="l.levelCode" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="loadMarks">查询</el-button>
          <el-button @click="openMark">新增标注</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="marks" stripe border>
        <el-table-column label="资产类型" width="100">
          <template #default="{ row }">{{ statusLabel(row.assetType) }}</template>
        </el-table-column>
        <el-table-column prop="assetName" label="资产名称" min-width="160" />
        <el-table-column prop="assetCode" label="编码" width="140" />
        <el-table-column label="分类" width="120">
          <template #default="{ row }">{{ catName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="级别" width="110">
          <template #default="{ row }">{{ levelName(row.levelCode) }}</template>
        </el-table-column>
        <el-table-column label="定级依据" width="110">
          <template #default="{ row }">{{ statusLabel(row.gradeBasis) }}</template>
        </el-table-column>
        <el-table-column prop="gradedBy" label="定级人" width="100" />
        <el-table-column prop="versionNo" label="版本" width="70" />
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'levels'" title="敏感级别字典">
      <el-button type="primary" style="margin-bottom:12px" @click="openLevel()">新增级别</el-button>
      <el-table :data="levels" stripe border>
        <el-table-column prop="levelCode" label="编码" width="120" />
        <el-table-column prop="levelName" label="名称" width="120" />
        <el-table-column prop="sensitivityScore" label="敏感分" width="90" />
        <el-table-column label="管控强度" width="100">
          <template #default="{ row }">{{ statusLabel(row.controlStrength) }}</template>
        </el-table-column>
        <el-table-column label="可共享" width="80">
          <template #default="{ row }">{{ row.shareAllowed === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="可开放" width="80">
          <template #default="{ row }">{{ row.openAllowed === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="须脱敏" width="80">
          <template #default="{ row }">{{ row.maskRequired === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="approvalLevel" label="审批等级" width="100" />
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openLevel(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'categories'" title="多维分类体系">
      <el-button type="primary" style="margin-bottom:12px" @click="openCat()">新增分类</el-button>
      <el-table :data="categories" stripe border>
        <el-table-column prop="categoryCode" label="编码" width="140" />
        <el-table-column prop="categoryName" label="名称" width="140" />
        <el-table-column label="维度" width="110">
          <template #default="{ row }">{{ DIM_LABEL[row.dimType] || row.dimType }}</template>
        </el-table-column>
        <el-table-column prop="path" label="路径" min-width="140" />
        <el-table-column prop="shareScopeHint" label="共享域提示" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCat(row)">编辑</el-button>
            <el-button link type="danger" @click="removeCat(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'scopes'" title="使用范围策略">
      <p class="hint">同一资产同时命中多条类别/级别策略时，按严重权重取最严；同权时「禁止」优先于「允许」。</p>
      <el-button type="primary" style="margin-bottom:12px" @click="openScope()">新增策略</el-button>
      <el-table :data="scopes" stripe border>
        <el-table-column prop="ruleName" label="策略" min-width="160" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
        </el-table-column>
        <el-table-column label="级别" width="100">
          <template #default="{ row }">{{ levelName(row.levelCode) }}</template>
        </el-table-column>
        <el-table-column label="分类" width="120">
          <template #default="{ row }">{{ catName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="允许" width="70">
          <template #default="{ row }">{{ row.allowFlag === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="脱敏" width="70">
          <template #default="{ row }">{{ row.maskRequired === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="审批" width="70">
          <template #default="{ row }">{{ row.approvalRequired === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="severityWeight" label="从严权重" width="100" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openScope(row)">编辑</el-button>
            <el-button link type="danger" @click="removeScope(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">策略试算</el-divider>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="资产类型" class="portal-field-sm">
          <el-select v-model="evalForm.assetType">
            <el-option label="登记表" value="TABLE" />
            <el-option label="数据资产" value="ASSET" />
            <el-option label="编目" value="REGISTRY" />
          </el-select>
        </el-form-item>
        <el-form-item label="资产ID" class="portal-field-sm">
          <el-input-number v-model="evalForm.assetId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="操作" class="portal-field-sm">
          <el-select v-model="evalForm.actionType">
            <el-option label="访问" value="VIEW" />
            <el-option label="共享" value="SHARE" />
            <el-option label="开放" value="OPEN" />
            <el-option label="导出" value="EXPORT" />
            <el-option label="流转" value="TRANSFER" />
          </el-select>
        </el-form-item>
        <el-form-item label="已脱敏"><el-switch v-model="evalForm.masked" /></el-form-item>
        <el-form-item label="已审批"><el-switch v-model="evalForm.approved" /></el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="runEvaluate">校验</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="evalResult"
        :type="evalResult.result === 'ALLOW' ? 'success' : 'error'"
        :title="`${statusLabel(String(evalResult.result))}：${evalResult.reason}`"
        show-icon
        :closable="false"
      />
    </PageCard>

    <PageCard v-else-if="tab === 'audit'" title="变更审计与策略命中">
      <h4>定级变更</h4>
      <el-table :data="audits" stripe size="small" style="margin-bottom:16px">
        <el-table-column label="变更" width="90">
          <template #default="{ row }">{{ statusLabel(String(row.changeType)) }}</template>
        </el-table-column>
        <el-table-column prop="assetType" label="类型" width="90" />
        <el-table-column prop="assetId" label="资产ID" width="90" />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="beforeJson" label="变更前" min-width="160" show-overflow-tooltip />
        <el-table-column prop="afterJson" label="变更后" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
      <h4>策略命中</h4>
      <el-table :data="hits" stripe size="small">
        <el-table-column label="结果" width="90">
          <template #default="{ row }">{{ statusLabel(String(row.result)) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">{{ statusLabel(String(row.actionType)) }}</template>
        </el-table-column>
        <el-table-column prop="assetId" label="资产ID" width="90" />
        <el-table-column prop="levelCode" label="级别" width="100" />
        <el-table-column prop="reason" label="原因" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
    </PageCard>

    <el-dialog v-model="markDialog" title="资产分类分级标注" width="640px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="资产类型">
          <el-select v-model="markForm.assetType" style="width:100%" @change="loadCandidates">
            <el-option label="登记表" value="TABLE" />
            <el-option label="数据资产" value="ASSET" />
            <el-option label="编目" value="REGISTRY" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择资产">
          <el-select
            v-model="markForm.assetId"
            filterable
            remote
            :remote-method="(q: string) => { markForm.assetName = q; loadCandidates() }"
            style="width:100%"
            placeholder="检索登记对象"
          >
            <el-option
              v-for="c in candidates"
              :key="String(c.assetId)"
              :label="`${c.assetName}（${c.assetCode}）`"
              :value="Number(c.assetId)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="markForm.categoryId" clearable style="width:100%">
            <el-option
              v-for="c in categories"
              :key="c.id"
              :label="`${DIM_LABEL[c.dimType] || c.dimType} · ${c.categoryName}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="markForm.levelCode" style="width:100%">
            <el-option v-for="l in levels" :key="l.levelCode" :label="l.levelName" :value="l.levelCode" />
          </el-select>
        </el-form-item>
        <el-divider>规则建议定级（可选）</el-divider>
        <el-form-item label="个人信息程度"><el-input-number v-model="markForm.personalInfoScore" :min="0" :max="5" /></el-form-item>
        <el-form-item label="业务关键性"><el-input-number v-model="markForm.businessCriticalScore" :min="0" :max="5" /></el-form-item>
        <el-form-item label="泄露影响"><el-input-number v-model="markForm.leakImpactScore" :min="0" :max="5" /></el-form-item>
        <el-form-item>
          <el-button @click="suggestLevel">生成建议级别</el-button>
        </el-form-item>
        <el-form-item label="定级说明"><el-input v-model="markForm.gradeReason" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="markDialog = false">取消</el-button>
        <el-button type="primary" @click="saveMark">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="levelDialog" :title="levelForm.id ? '编辑级别' : '新增级别'" width="560px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="编码" required><el-input v-model="levelForm.levelCode" :disabled="!!levelForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="levelForm.levelName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="levelForm.sortNo" :min="0" /></el-form-item>
        <el-form-item label="敏感分"><el-input-number v-model="levelForm.sensitivityScore" :min="1" :max="10" /></el-form-item>
        <el-form-item label="管控强度">
          <el-select v-model="levelForm.controlStrength" style="width:100%">
            <el-option label="低" value="LOW" /><el-option label="中" value="MEDIUM" /><el-option label="高" value="HIGH" />
          </el-select>
        </el-form-item>
        <el-form-item label="可共享"><el-switch v-model="levelForm.shareAllowed" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="可开放"><el-switch v-model="levelForm.openAllowed" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="须脱敏"><el-switch v-model="levelForm.maskRequired" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="审批等级">
          <el-select v-model="levelForm.approvalLevel" style="width:100%">
            <el-option label="无需" value="NONE" /><el-option label="L1" value="L1" /><el-option label="L2" value="L2" /><el-option label="L3" value="L3" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="levelForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="levelDialog = false">取消</el-button>
        <el-button type="primary" @click="saveLevel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="catDialog" :title="catForm.id ? '编辑分类' : '新增分类'" width="560px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="编码" required><el-input v-model="catForm.categoryCode" :disabled="!!catForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="catForm.categoryName" /></el-form-item>
        <el-form-item label="维度">
          <el-select v-model="catForm.dimType" style="width:100%">
            <el-option v-for="(lab, k) in DIM_LABEL" :key="k" :label="lab" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="共享域提示"><el-input v-model="catForm.shareScopeHint" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="catForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCat">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="scopeDialog" :title="scopeForm.id ? '编辑策略' : '新增策略'" width="600px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="编码" required><el-input v-model="scopeForm.ruleCode" :disabled="!!scopeForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="scopeForm.ruleName" /></el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="scopeForm.actionType" style="width:100%">
            <el-option label="访问" value="VIEW" /><el-option label="共享" value="SHARE" />
            <el-option label="开放" value="OPEN" /><el-option label="导出" value="EXPORT" /><el-option label="流转" value="TRANSFER" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定级别">
          <el-select v-model="scopeForm.levelCode" clearable style="width:100%">
            <el-option v-for="l in levels" :key="l.levelCode" :label="l.levelName" :value="l.levelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定分类">
          <el-select v-model="scopeForm.categoryId" clearable style="width:100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许"><el-switch v-model="scopeForm.allowFlag" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="须脱敏"><el-switch v-model="scopeForm.maskRequired" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="须审批"><el-switch v-model="scopeForm.approvalRequired" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="从严权重"><el-input-number v-model="scopeForm.severityWeight" :min="1" :max="99" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="scopeForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scopeDialog = false">取消</el-button>
        <el-button type="primary" @click="saveScope">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; line-height: 1.5; }
h4 { margin: 0 0 8px; font-size: 14px; }
</style>
