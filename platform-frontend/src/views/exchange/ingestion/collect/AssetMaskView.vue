<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import api from '@/api/http'
import { statusLabel } from '@/utils/status-label'

const tab = ref('rules')
const loading = ref(false)
const overview = ref<Record<string, unknown> | null>(null)
const rules = ref<Record<string, unknown>[]>([])
const policies = ref<Record<string, unknown>[]>([])
const bindings = ref<Record<string, unknown>[]>([])
const cryptos = ref<Record<string, unknown>[]>([])
const audits = ref<Record<string, unknown>[]>([])
const levels = ref<Record<string, unknown>[]>([])

const ALGO_OPTIONS = [
  { value: 'MASK', label: '掩码' },
  { value: 'REPLACE', label: '替换' },
  { value: 'CONSTANT', label: '常数填充' },
  { value: 'TRUNCATE', label: '截断' },
  { value: 'GENERALIZE', label: '泛化' },
  { value: 'HASH', label: '哈希(加盐)' },
  { value: 'CLEAR', label: '置空' },
  { value: 'ENCRYPT', label: '可逆加密' },
  { value: 'FPE', label: '格式保留加密' },
]
const SCENE_OPTIONS = [
  { value: 'QUERY', label: '在线查询' },
  { value: 'LIST', label: '列表展示' },
  { value: 'DETAIL', label: '详情' },
  { value: 'EXPORT', label: '导出' },
  { value: 'API_SHARE', label: 'API 共享' },
  { value: 'OPEN_DOWNLOAD', label: '开放下载' },
  { value: 'TEST_SYNC', label: '测试库同步' },
  { value: 'LOG', label: '日志打印' },
]

const ruleDialog = ref(false)
const ruleForm = reactive({
  id: undefined as number | undefined,
  ruleCode: '',
  ruleName: '',
  algoType: 'MASK',
  paramJson: '{"keepPrefix":3,"keepSuffix":4,"maskChar":"*"}',
  reversible: 0,
  matchFieldPattern: '',
  matchSensitiveTag: '',
  failPolicy: 'CLEAR',
  status: 'ACTIVE',
  description: '',
})
const ruleSample = ref('13812345678')
const rulePreview = ref<Record<string, unknown> | null>(null)

const policyDialog = ref(false)
const policyForm = reactive({
  id: undefined as number | undefined,
  policyCode: '',
  policyName: '',
  sceneCode: 'QUERY',
  matchLevelCode: '',
  matchEnv: 'PROD',
  priority: 50,
  strictMode: 1,
  ruleIdList: [] as number[],
  description: '',
  newVersion: false,
})

const bindDialog = ref(false)
const bindForm = reactive({
  bindingCode: '',
  policyId: undefined as number | undefined,
  targetType: 'TABLE',
  targetId: undefined as number | undefined,
  targetName: '',
})

const cryptoDialog = ref(false)
const cryptoForm = reactive({
  id: undefined as number | undefined,
  refCode: '',
  refName: '',
  algo: 'AES_GCM',
  keyAlias: 'kms://chengde/mask/',
  rotateDays: 90,
  ownerOrg: '',
  status: 'ACTIVE',
  remark: '',
})

const applyForm = reactive({
  sceneCode: 'QUERY',
  levelCode: 'GENERAL',
  env: 'PROD',
  phone: '13812345678',
  id_card: '130803199001011234',
  name: '张三',
  address: '河北省承德市双桥区某某路1号',
})
const applyResult = ref<Record<string, unknown> | null>(null)

const activePolicies = computed(() => policies.value.filter((p) => p.status === 'ACTIVE'))

async function loadOverview() {
  overview.value = (await api.get('/exchange/ingestion/mask-policy/overview')).data
}
async function loadRules() {
  rules.value = (await api.get('/exchange/ingestion/mask-policy/rules')).data || []
}
async function loadPolicies() {
  policies.value = (await api.get('/exchange/ingestion/mask-policy/policies')).data || []
}
async function loadBindings() {
  bindings.value = (await api.get('/exchange/ingestion/mask-policy/bindings')).data || []
}
async function loadCryptos() {
  cryptos.value = (await api.get('/exchange/ingestion/mask-policy/crypto-refs')).data || []
}
async function loadAudits() {
  audits.value = (await api.get('/exchange/ingestion/mask-policy/audit-logs', { params: { limit: 50 } })).data || []
}
async function loadLevels() {
  try {
    levels.value = (await api.get('/exchange/ingestion/classify-grade/levels')).data || []
  } catch {
    levels.value = []
  }
}

async function reload() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadLevels()])
    if (tab.value === 'rules') await loadRules()
    else if (tab.value === 'policies') await Promise.all([loadPolicies(), loadRules()])
    else if (tab.value === 'bindings') await Promise.all([loadBindings(), loadPolicies()])
    else if (tab.value === 'crypto') await loadCryptos()
    else if (tab.value === 'preview') await loadPolicies()
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

function openRule(row?: Record<string, unknown>) {
  ruleForm.id = row?.id as number | undefined
  ruleForm.ruleCode = String(row?.ruleCode || '')
  ruleForm.ruleName = String(row?.ruleName || '')
  ruleForm.algoType = String(row?.algoType || 'MASK')
  ruleForm.paramJson = String(row?.paramJson || '{"keepPrefix":3,"keepSuffix":4,"maskChar":"*"}')
  ruleForm.reversible = Number(row?.reversible ?? 0)
  ruleForm.matchFieldPattern = String(row?.matchFieldPattern || '')
  ruleForm.matchSensitiveTag = String(row?.matchSensitiveTag || '')
  ruleForm.failPolicy = String(row?.failPolicy || 'CLEAR')
  ruleForm.status = String(row?.status || 'ACTIVE')
  ruleForm.description = String(row?.description || '')
  rulePreview.value = null
  ruleDialog.value = true
}

async function saveRule() {
  if (!ruleForm.ruleCode || !ruleForm.ruleName) {
    ElMessage.warning('请填写编码与名称')
    return
  }
  try {
    JSON.parse(ruleForm.paramJson || '{}')
  } catch {
    ElMessage.warning('参数 JSON 格式不正确')
    return
  }
  await api.post('/exchange/ingestion/mask-policy/rules', { ...ruleForm })
  ElMessage.success('规则已保存')
  ruleDialog.value = false
  await loadRules()
  await loadOverview()
}

async function cloneRule(row: Record<string, unknown>) {
  await api.post(`/exchange/ingestion/mask-policy/rules/${row.id}/clone`)
  ElMessage.success('已克隆')
  await loadRules()
}

async function removeRule(row: Record<string, unknown>) {
  await ElMessageBox.confirm(`删除规则「${row.ruleName}」？`, '确认')
  await api.delete(`/exchange/ingestion/mask-policy/rules/${row.id}`)
  ElMessage.success('已删除')
  await loadRules()
}

async function doRulePreview() {
  if (!ruleForm.id && !ruleDialog.value) return
  // 未保存时先用临时：需已有 id；对话框内若新建则提示先保存
  if (!ruleForm.id) {
    ElMessage.warning('请先保存规则后再预览，或对已有规则点「试跑」')
    return
  }
  rulePreview.value = (
    await api.post(`/exchange/ingestion/mask-policy/rules/${ruleForm.id}/preview`, { sample: ruleSample.value })
  ).data
}

async function quickPreview(row: Record<string, unknown>) {
  ruleForm.id = Number(row.id)
  ruleSample.value = '13812345678'
  rulePreview.value = (
    await api.post(`/exchange/ingestion/mask-policy/rules/${row.id}/preview`, { sample: ruleSample.value })
  ).data
  ElMessage.success(`效果：${rulePreview.value?.before} → ${rulePreview.value?.after}`)
}

function openPolicy(row?: Record<string, unknown>) {
  policyForm.id = row?.id as number | undefined
  policyForm.policyCode = String(row?.policyCode || '')
  policyForm.policyName = String(row?.policyName || '')
  policyForm.sceneCode = String(row?.sceneCode || 'QUERY')
  policyForm.matchLevelCode = String(row?.matchLevelCode || '')
  policyForm.matchEnv = String(row?.matchEnv || 'PROD')
  policyForm.priority = Number(row?.priority ?? 50)
  policyForm.strictMode = Number(row?.strictMode ?? 1)
  policyForm.description = String(row?.description || '')
  policyForm.newVersion = false
  try {
    policyForm.ruleIdList = row?.ruleIdsJson ? (JSON.parse(String(row.ruleIdsJson)) as number[]) : []
  } catch {
    policyForm.ruleIdList = []
  }
  policyDialog.value = true
}

async function savePolicy() {
  if (!policyForm.policyCode || !policyForm.policyName) {
    ElMessage.warning('请填写策略编码与名称')
    return
  }
  await api.post('/exchange/ingestion/mask-policy/policies', {
    ...policyForm,
    ruleIdsJson: JSON.stringify(policyForm.ruleIdList || []),
  })
  ElMessage.success('策略已保存')
  policyDialog.value = false
  await loadPolicies()
  await loadOverview()
}

async function publishPolicy(row: Record<string, unknown>) {
  await api.post(`/exchange/ingestion/mask-policy/policies/${row.id}/publish`)
  ElMessage.success('已发布生效')
  await loadPolicies()
}

async function rollbackPolicy(row: Record<string, unknown>) {
  const id = (await api.post(`/exchange/ingestion/mask-policy/policies/${row.id}/rollback`)).data
  ElMessage.success(`已生成回滚草稿版本，ID=${id}`)
  await loadPolicies()
}

function openBind() {
  bindForm.bindingCode = `BIND_${Date.now() % 100000}`
  bindForm.policyId = activePolicies.value[0]?.id as number | undefined
  bindForm.targetType = 'TABLE'
  bindForm.targetId = undefined
  bindForm.targetName = ''
  bindDialog.value = true
}

async function saveBind() {
  if (!bindForm.bindingCode || !bindForm.policyId) {
    ElMessage.warning('请填写绑定编码并选择策略')
    return
  }
  await api.post('/exchange/ingestion/mask-policy/bindings', { ...bindForm })
  ElMessage.success('绑定已保存')
  bindDialog.value = false
  await loadBindings()
}

async function removeBind(row: Record<string, unknown>) {
  await ElMessageBox.confirm('删除该绑定？', '确认')
  await api.delete(`/exchange/ingestion/mask-policy/bindings/${row.id}`)
  await loadBindings()
}

function openCrypto(row?: Record<string, unknown>) {
  cryptoForm.id = row?.id as number | undefined
  cryptoForm.refCode = String(row?.refCode || '')
  cryptoForm.refName = String(row?.refName || '')
  cryptoForm.algo = String(row?.algo || 'AES_GCM')
  cryptoForm.keyAlias = String(row?.keyAlias || 'kms://chengde/mask/')
  cryptoForm.rotateDays = Number(row?.rotateDays ?? 90)
  cryptoForm.ownerOrg = String(row?.ownerOrg || '')
  cryptoForm.status = String(row?.status || 'ACTIVE')
  cryptoForm.remark = String(row?.remark || '')
  cryptoDialog.value = true
}

async function saveCrypto() {
  if (!cryptoForm.refCode || !cryptoForm.keyAlias) {
    ElMessage.warning('请填写引用编码与密钥别名')
    return
  }
  await api.post('/exchange/ingestion/mask-policy/crypto-refs', { ...cryptoForm })
  ElMessage.success('密钥引用已保存')
  cryptoDialog.value = false
  await loadCryptos()
}

async function runApply(preview: boolean) {
  applyResult.value = (
    await api.post(`/exchange/ingestion/mask-policy/${preview ? 'preview' : 'apply'}`, {
      sceneCode: applyForm.sceneCode,
      levelCode: applyForm.levelCode,
      env: applyForm.env,
      fields: {
        phone: applyForm.phone,
        id_card: applyForm.id_card,
        name: applyForm.name,
        address: applyForm.address,
      },
    })
  ).data
}

function algoLabel(v: unknown) {
  return ALGO_OPTIONS.find((a) => a.value === v)?.label || statusLabel(v)
}
function sceneLabel(v: unknown) {
  return SCENE_OPTIONS.find((a) => a.value === v)?.label || statusLabel(v)
}
function levelName(code: unknown) {
  if (!code) return '不限'
  const hit = levels.value.find((l) => l.levelCode === code)
  return hit ? String(hit.levelName) : String(code)
}
function policyName(id: unknown) {
  const hit = policies.value.find((p) => p.id === id)
  return hit ? String(hit.policyName) : String(id)
}

onMounted(reload)
</script>

<template>
  <div v-loading="loading">
    <el-row v-if="overview" :gutter="12" style="margin-bottom:12px">
      <el-col :span="6"><el-statistic title="启用规则" :value="Number(overview.ruleCount || 0)" /></el-col>
      <el-col :span="6"><el-statistic title="已发布策略" :value="Number(overview.policyActive || 0)" /></el-col>
      <el-col :span="6"><el-statistic title="场景绑定" :value="Number(overview.bindingCount || 0)" /></el-col>
      <el-col :span="6"><el-statistic title="密钥引用" :value="Number(overview.cryptoRefCount || 0)" /></el-col>
    </el-row>

    <el-tabs :model-value="tab" @tab-change="onTab">
      <el-tab-pane label="规则库" name="rules" />
      <el-tab-pane label="策略编排" name="policies" />
      <el-tab-pane label="场景绑定" name="bindings" />
      <el-tab-pane label="预览与执行" name="preview" />
      <el-tab-pane label="密钥引用" name="crypto" />
      <el-tab-pane label="审计" name="audit" />
    </el-tabs>

    <PageCard v-if="tab === 'rules'" title="脱敏规则库">
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item class="portal-form-actions">
          <el-button type="primary" @click="openRule()">新建规则</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="rules" stripe border>
        <el-table-column prop="ruleCode" label="编码" width="150" />
        <el-table-column prop="ruleName" label="名称" min-width="140" />
        <el-table-column label="算法" width="110">
          <template #default="{ row }">{{ algoLabel(row.algoType) }}</template>
        </el-table-column>
        <el-table-column prop="matchFieldPattern" label="字段匹配" min-width="140" show-overflow-tooltip />
        <el-table-column label="可逆" width="70">
          <template #default="{ row }">{{ row.reversible === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="quickPreview(row)">试跑</el-button>
            <el-button link type="primary" @click="openRule(row)">编辑</el-button>
            <el-button link @click="cloneRule(row)">克隆</el-button>
            <el-button link type="danger" @click="removeRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'policies'" title="脱敏策略编排">
      <el-button type="primary" style="margin-bottom:12px" @click="openPolicy()">新建策略</el-button>
      <el-table :data="policies" stripe border>
        <el-table-column prop="policyCode" label="编码" width="160" />
        <el-table-column prop="policyName" label="名称" min-width="160" />
        <el-table-column label="场景" width="110">
          <template #default="{ row }">{{ sceneLabel(row.sceneCode) }}</template>
        </el-table-column>
        <el-table-column label="级别" width="100">
          <template #default="{ row }">{{ levelName(row.matchLevelCode) }}</template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column prop="versionNo" label="版本" width="70" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ statusLabel(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPolicy(row)">编辑</el-button>
            <el-button v-if="row.status !== 'ACTIVE'" link type="success" @click="publishPolicy(row)">发布</el-button>
            <el-button link @click="rollbackPolicy(row)">回滚草稿</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'bindings'" title="场景 / 资产绑定">
      <el-button type="primary" style="margin-bottom:12px" @click="openBind">新增绑定</el-button>
      <el-table :data="bindings" stripe border>
        <el-table-column prop="bindingCode" label="绑定编码" width="140" />
        <el-table-column label="策略" min-width="160">
          <template #default="{ row }">{{ policyName(row.policyId) }}</template>
        </el-table-column>
        <el-table-column label="目标类型" width="100">
          <template #default="{ row }">{{ statusLabel(row.targetType) }}</template>
        </el-table-column>
        <el-table-column prop="targetName" label="目标名称" min-width="140" />
        <el-table-column prop="targetId" label="目标ID" width="90" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="removeBind(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'preview'" title="效果预览与执行">
      <el-form label-width="100px" style="max-width:720px">
        <el-form-item label="场景">
          <el-select v-model="applyForm.sceneCode" style="width:100%">
            <el-option v-for="s in SCENE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据级别">
          <el-select v-model="applyForm.levelCode" style="width:100%">
            <el-option v-for="l in levels" :key="String(l.levelCode)" :label="String(l.levelName)" :value="String(l.levelCode)" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号"><el-input v-model="applyForm.phone" /></el-form-item>
        <el-form-item label="身份证"><el-input v-model="applyForm.id_card" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="applyForm.name" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="applyForm.address" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="runApply(true)">预览</el-button>
          <el-button @click="runApply(false)">执行并记审计</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="applyResult"
        :type="applyResult.matched ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="applyResult.matched
          ? `命中策略 ${applyResult.policyName}（v${applyResult.policyVersion}）`
          : String(applyResult.reason || '未命中策略')"
      />
      <el-table v-if="applyResult?.details" :data="applyResult.details as Record<string, unknown>[]" stripe border style="margin-top:12px">
        <el-table-column prop="field" label="字段" width="120" />
        <el-table-column prop="ruleCode" label="规则" width="140" />
        <el-table-column label="算法" width="100">
          <template #default="{ row }">{{ algoLabel(row.algoType) }}</template>
        </el-table-column>
        <el-table-column prop="before" label="脱敏前" min-width="140" />
        <el-table-column prop="after" label="脱敏后" min-width="140" />
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'crypto'" title="密钥引用（KMS 对接）">
      <el-button type="primary" style="margin-bottom:12px" @click="openCrypto()">新增引用</el-button>
      <el-table :data="cryptos" stripe border>
        <el-table-column prop="refCode" label="编码" width="140" />
        <el-table-column prop="refName" label="名称" min-width="140" />
        <el-table-column prop="algo" label="算法" width="100" />
        <el-table-column prop="keyAlias" label="密钥别名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="rotateDays" label="轮换天" width="90" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openCrypto(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="tab === 'audit'" title="脱敏审计">
      <el-table :data="audits" stripe border>
        <el-table-column label="动作" width="100">
          <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
        </el-table-column>
        <el-table-column prop="policyId" label="策略ID" width="90" />
        <el-table-column prop="policyVersion" label="版本" width="70" />
        <el-table-column label="场景" width="110">
          <template #default="{ row }">{{ sceneLabel(row.sceneCode) }}</template>
        </el-table-column>
        <el-table-column label="还原" width="70">
          <template #default="{ row }">{{ row.restored === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="sampleBefore" label="样例前" min-width="120" show-overflow-tooltip />
        <el-table-column prop="sampleAfter" label="样例后" min-width="120" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" width="100" />
        <el-table-column prop="createdAt" label="时间" width="170" />
      </el-table>
    </PageCard>

    <el-dialog v-model="ruleDialog" :title="ruleForm.id ? '编辑规则' : '新建规则'" width="640px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="编码" required><el-input v-model="ruleForm.ruleCode" :disabled="!!ruleForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="ruleForm.ruleName" /></el-form-item>
        <el-form-item label="算法">
          <el-select v-model="ruleForm.algoType" style="width:100%">
            <el-option v-for="a in ALGO_OPTIONS" :key="a.value" :label="a.label" :value="a.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数 JSON"><el-input v-model="ruleForm.paramJson" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="字段匹配"><el-input v-model="ruleForm.matchFieldPattern" placeholder="如 *phone*|*mobile*" /></el-form-item>
        <el-form-item label="敏感标签"><el-input v-model="ruleForm.matchSensitiveTag" placeholder="如 PII_PHONE" /></el-form-item>
        <el-form-item label="可逆"><el-switch v-model="ruleForm.reversible" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="失败策略">
          <el-select v-model="ruleForm.failPolicy" style="width:100%">
            <el-option label="拒绝" value="DENY" /><el-option label="透传" value="PASSTHROUGH" /><el-option label="置空" value="CLEAR" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="ruleForm.description" type="textarea" :rows="2" /></el-form-item>
        <template v-if="ruleForm.id">
          <el-divider>试跑预览</el-divider>
          <el-form-item label="样例输入"><el-input v-model="ruleSample" /></el-form-item>
          <el-form-item>
            <el-button @click="doRulePreview">预览效果</el-button>
            <el-tag v-if="rulePreview" style="margin-left:8px">{{ rulePreview.before }} → {{ rulePreview.after }}</el-tag>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="policyDialog" :title="policyForm.id ? '编辑策略' : '新建策略'" width="640px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="编码" required><el-input v-model="policyForm.policyCode" :disabled="!!policyForm.id && !policyForm.newVersion" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="policyForm.policyName" /></el-form-item>
        <el-form-item label="场景">
          <el-select v-model="policyForm.sceneCode" style="width:100%">
            <el-option v-for="s in SCENE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配级别">
          <el-select v-model="policyForm.matchLevelCode" clearable style="width:100%" placeholder="不限">
            <el-option v-for="l in levels" :key="String(l.levelCode)" :label="String(l.levelName)" :value="String(l.levelCode)" />
          </el-select>
        </el-form-item>
        <el-form-item label="环境">
          <el-select v-model="policyForm.matchEnv" style="width:100%">
            <el-option label="生产" value="PROD" /><el-option label="测试" value="TEST" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="policyForm.priority" :min="1" :max="999" /></el-form-item>
        <el-form-item label="冲突从严"><el-switch v-model="policyForm.strictMode" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="组成规则">
          <el-select v-model="policyForm.ruleIdList" multiple filterable style="width:100%">
            <el-option v-for="r in rules" :key="Number(r.id)" :label="`${r.ruleName}（${r.ruleCode}）`" :value="Number(r.id)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="policyForm.id" label="另存新版本"><el-switch v-model="policyForm.newVersion" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="policyForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="policyDialog = false">取消</el-button>
        <el-button type="primary" @click="savePolicy">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bindDialog" title="新增绑定" width="520px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="绑定编码"><el-input v-model="bindForm.bindingCode" /></el-form-item>
        <el-form-item label="策略">
          <el-select v-model="bindForm.policyId" style="width:100%">
            <el-option v-for="p in activePolicies" :key="Number(p.id)" :label="String(p.policyName)" :value="Number(p.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型">
          <el-select v-model="bindForm.targetType" style="width:100%">
            <el-option label="登记表" value="TABLE" /><el-option label="资产" value="ASSET" />
            <el-option label="编目" value="REGISTRY" /><el-option label="接口" value="API" /><el-option label="任务" value="TASK" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标ID"><el-input-number v-model="bindForm.targetId" :min="1" /></el-form-item>
        <el-form-item label="目标名称"><el-input v-model="bindForm.targetName" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBind">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cryptoDialog" :title="cryptoForm.id ? '编辑密钥引用' : '新增密钥引用'" width="560px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="编码" required><el-input v-model="cryptoForm.refCode" :disabled="!!cryptoForm.id" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="cryptoForm.refName" /></el-form-item>
        <el-form-item label="算法"><el-input v-model="cryptoForm.algo" /></el-form-item>
        <el-form-item label="密钥别名" required><el-input v-model="cryptoForm.keyAlias" placeholder="kms://..." /></el-form-item>
        <el-form-item label="轮换天数"><el-input-number v-model="cryptoForm.rotateDays" :min="1" /></el-form-item>
        <el-form-item label="归属"><el-input v-model="cryptoForm.ownerOrg" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="cryptoForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cryptoDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCrypto">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; line-height: 1.5; }
</style>
