<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import {
  FULFILL_PATH_OPTIONS,
  MANIFEST_CENTER_SECTIONS,
  SUPPLY_MAIN_SECTIONS,
  resolveApplicationNav,
  type FulfillPath,
} from './application-nav'

const props = defineProps<{ mode?: 'front' | 'config'; embedded?: boolean }>()

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const moduleKey = ref('supply-flow')
const section = ref('demand')

const templates = ref<{ id: number; templateCode: string; templateName: string; demandType: string; fieldSchema?: string }[]>([])
const demands = ref<Record<string, unknown>[]>([])
const catalogs = ref<Record<string, unknown>[]>([])
const supplyTasks = ref<Record<string, unknown>[]>([])
const duties = ref<Record<string, unknown>[]>([])
const objections = ref<Record<string, unknown>[]>([])
const manifests = ref<Record<string, unknown>[]>([])
const listCenterItems = ref<Record<string, unknown>[]>([])
const listCenterSub = ref('dept-catalog')
const exchangeJobs = ref<Record<string, unknown>[]>([])
const apiEndpoints = ref<Record<string, unknown>[]>([])
const sharePages = ref<Record<string, unknown>[]>([])
const modelFieldValues = reactive<Record<string, string>>({})
const analysisResult = ref<Record<string, unknown> | null>(null)
const analysisCandidates = ref<Record<string, unknown>[]>([])
const relationGraph = ref<{ nodes?: Record<string, unknown>[]; edges?: Record<string, unknown>[] } | null>(null)
const resourceHits = ref<Record<string, unknown>[]>([])
const supplyView = ref<Record<string, unknown> | null>(null)
const selectedDemandId = ref<number>()
const analyzingId = ref<number>()

const demandForm = reactive({
  demandTitle: '',
  requesterOrg: '数据管理局',
  demandType: 'STRUCTURED',
  templateCode: '',
  demandContent: '',
  targetCatalogId: undefined as number | undefined,
})
const dispatchForm = reactive({
  assigneeOrg: '资源管理处',
  analysisNote: '',
  fulfillPath: 'NEED_COLLECT' as FulfillPath,
})
const resourceSearch = reactive({
  keyword: '',
  resourceType: 'ALL',
})
const quickSet = reactive({
  evalStatus: 'MATCHED',
  shareAttr: 'CONDITIONAL',
})
const superviseNote = ref('请尽快完成需求分析与供数确认')
const confirmNote = ref('供需对接确认，可满足需求，转换为数据责任并生成归集/共享任务')
const confirmFeedback = ref('')
const confirmResult = ref<Record<string, unknown> | null>(null)
const editDialog = reactive({
  visible: false,
  id: 0,
  demandTitle: '',
  requesterOrg: '',
  assigneeOrg: '',
  fulfillPath: 'NEED_COLLECT' as FulfillPath,
})
const objectionForm = reactive({ catalogId: undefined as number | undefined, objectionType: 'QUALITY', content: '' })

const EVAL_STATUS_OPTIONS = [
  { value: 'PENDING', label: '待评估' },
  { value: 'MATCHED', label: '已匹配' },
  { value: 'PARTIAL', label: '部分匹配' },
  { value: 'UNMATCHED', label: '未匹配' },
]
const SHARE_ATTR_OPTIONS = [
  { value: 'OPEN', label: '无条件共享' },
  { value: 'CONDITIONAL', label: '有条件共享' },
  { value: 'RESTRICTED', label: '受限共享' },
  { value: 'INTERNAL', label: '内部使用' },
]
const RESOURCE_TYPE_LABEL: Record<string, string> = {
  CATALOG: '目录',
  TABLE: '库表',
  API: '接口',
  DEMAND: '需求',
}

const mainSections = SUPPLY_MAIN_SECTIONS
const manifestCenterSections = MANIFEST_CENTER_SECTIONS

const selectedTemplate = computed(() =>
  templates.value.find((t) => t.templateCode === demandForm.templateCode),
)
const templateFields = computed(() => {
  const raw = selectedTemplate.value?.fieldSchema
  if (!raw) return [] as { key: string; label: string }[]
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (Array.isArray(parsed)) {
      return parsed.map((f: Record<string, string>) => ({
        key: f.key || f.name || f.code || 'field',
        label: f.label || f.name || f.key || '字段',
      }))
    }
    if (parsed && typeof parsed === 'object' && Array.isArray((parsed as { fields?: unknown }).fields)) {
      return ((parsed as { fields: Record<string, string>[] }).fields).map((f) => ({
        key: f.key || f.name || 'field',
        label: f.label || f.name || '字段',
      }))
    }
    return Object.keys(parsed as object).map((k) => ({ key: k, label: k }))
  } catch {
    return [] as { key: string; label: string }[]
  }
})

function onTemplateChange() {
  Object.keys(modelFieldValues).forEach((k) => delete modelFieldValues[k])
  const tpl = selectedTemplate.value
  if (tpl?.demandType) demandForm.demandType = tpl.demandType
  for (const f of templateFields.value) {
    modelFieldValues[f.key] = ''
  }
}

function fulfillLabel(path: unknown) {
  const hit = FULFILL_PATH_OPTIONS.find((o) => o.value === path)
  return hit?.label || String(path || '-')
}

function evalLabel(v: unknown) {
  return EVAL_STATUS_OPTIONS.find((o) => o.value === v)?.label || String(v || '-')
}

function shareLabel(v: unknown) {
  return SHARE_ATTR_OPTIONS.find((o) => o.value === v)?.label || String(v || '-')
}

function resourceTypeLabel(v: unknown) {
  return RESOURCE_TYPE_LABEL[String(v)] || String(v || '-')
}

function syncRoute() {
  // 嵌入门户时不跟门户 section 抢 query，只用本地 Tab
  if (props.embedded) return
  const r = resolveApplicationNav(route.query as Record<string, unknown>)
  moduleKey.value = 'supply-flow'
  let sec = String(route.query.sdSection || r.section || 'demand')
  if (sec === 'catalog') {
    sec = 'manifest-center'
    listCenterSub.value = 'dept-catalog'
  } else if (sec === 'objection') {
    sec = 'manifest-center'
    listCenterSub.value = 'objection'
  } else if (sec === 'manifest') {
    sec = 'manifest-center'
    listCenterSub.value = 'open-list'
  }
  if (!SUPPLY_MAIN_SECTIONS.some((s) => s.key === sec) && sec !== 'manifest-center') {
    sec = 'demand'
  }
  section.value = sec
}

function setSection(key: string) {
  section.value = key
  if (props.embedded) {
    // 保持门户 tab=subscribe，供需子 Tab 用 sdSection，避免弹回 catalog
    router.replace({
      query: {
        ...route.query,
        system: 'portal',
        module: 'portal-home',
        section: 'subscribe',
        sdSection: key,
      },
    })
    loadSection()
    return
  }
  router.replace({
    query: {
      ...route.query,
      section: key,
      sdSection: key,
    },
    path: '/exchange/application/supply',
  })
  loadSection()
}

function setListCenterSub(key: string) {
  listCenterSub.value = key
  loadListCenter()
}

const statusTag = (s: string) => {
  if (['CONFIRMED', 'APPROVED', 'CLOSED', 'COMPLETED'].includes(s)) return 'success'
  if (['REJECTED', 'RETURNED', 'WITHDRAWN', 'CANCELLED'].includes(s)) return 'danger'
  if (['DISPATCHED', 'ANALYZING', 'SUPERVISING'].includes(s)) return 'warning'
  return 'info'
}
void statusTag

async function searchResourceCatalog() {
  const res = await api.get('/exchange/supply/resource-search', {
    params: {
      keyword: resourceSearch.keyword || undefined,
      resourceType: resourceSearch.resourceType === 'ALL' ? undefined : resourceSearch.resourceType,
    },
  })
  resourceHits.value = res.data.items || []
}

async function loadSection() {
  loading.value = true
  try {
    if (section.value === 'demand') {
      const [tp, dm] = await Promise.all([
        api.get('/exchange/supply/templates'),
        api.get('/exchange/supply/demands'),
      ])
      templates.value = tp.data
      demands.value = dm.data
    } else if (section.value === 'analysis' || section.value === 'confirm') {
      demands.value = (await api.get('/exchange/supply/demands')).data
      if (section.value === 'analysis' && !resourceHits.value.length) {
        await searchResourceCatalog()
      }
    } else if (section.value === 'supply') {
      demands.value = (await api.get('/exchange/supply/demands')).data
      if (selectedDemandId.value) await loadSupplyView(selectedDemandId.value)
    } else if (section.value === 'manifest-center') {
      await loadListCenter()
    } else if (section.value === 'catalog') {
      catalogs.value = (await api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } })).data
    } else if (section.value === 'objection') {
      const [cat, obj] = await Promise.all([
        api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } }),
        api.get('/exchange/supply/objections'),
      ])
      catalogs.value = cat.data
      objections.value = obj.data
    } else if (section.value === 'manifest') {
      manifests.value = (await api.get('/exchange/supply/manifests')).data
    }
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function loadListCenter() {
  const res = await api.get('/exchange/supply/list-center', { params: { listType: listCenterSub.value } })
  listCenterItems.value = res.data.items || []
  if (listCenterSub.value === 'objection') {
    objections.value = listCenterItems.value
    catalogs.value = (await api.get('/exchange/supply/catalog-manifest', { params: { scope: 'published' } })).data
  }
}

async function submitDemand() {
  if (!demandForm.demandTitle) return ElMessage.warning('请填写需求标题')
  if (demandForm.demandType === 'UNSTRUCTURED' && !demandForm.demandContent) {
    return ElMessage.warning('请填写非结构化需求内容')
  }
  await api.post('/exchange/supply/demands', {
    ...demandForm,
    modelFields: demandForm.demandType === 'STRUCTURED' ? { ...modelFieldValues } : undefined,
  })
  ElMessage.success('需求已提交')
  demandForm.demandTitle = ''
  demandForm.demandContent = ''
  Object.keys(modelFieldValues).forEach((k) => { modelFieldValues[k] = '' })
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function withdrawDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/withdraw`)
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function analyzeDemand(id: number) {
  analyzingId.value = id
  analysisResult.value = (await api.post(`/exchange/supply/demands/${id}/analyze`)).data
  analysisCandidates.value = (analysisResult.value?.candidates as Record<string, unknown>[]) || []
  relationGraph.value = (analysisResult.value?.relationGraph as typeof relationGraph.value) || null
  if (analysisResult.value?.fulfillPath) {
    dispatchForm.fulfillPath = analysisResult.value.fulfillPath as FulfillPath
  }
  if (analysisResult.value?.evalStatus) {
    quickSet.evalStatus = String(analysisResult.value.evalStatus)
  }
  if (analysisResult.value?.shareAttr) {
    quickSet.shareAttr = String(analysisResult.value.shareAttr)
  }
  demands.value = (await api.get('/exchange/supply/demands')).data
  ElMessage.success('智能辅助分析完成')
}

async function dispatchDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/dispatch`, dispatchForm)
  ElMessage.success('已分发并设定履约路径')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function returnDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/return`, { analysisNote: '材料不全，请补充' })
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function superviseDemand(id: number) {
  if (!superviseNote.value) return ElMessage.warning('请填写督办说明')
  await api.post(`/exchange/supply/demands/${id}/supervise`, { superviseNote: superviseNote.value })
  ElMessage.success('已发起督查督办')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function applyQuickSettings(id?: number) {
  const targetId = id || analyzingId.value
  if (!targetId) return ElMessage.warning('请先选择需求并完成智能匹配')
  const res = await api.post(`/exchange/supply/demands/${targetId}/analysis-settings`, {
    evalStatus: quickSet.evalStatus,
    shareAttr: quickSet.shareAttr,
    fulfillPath: dispatchForm.fulfillPath,
  })
  ElMessage.success(`已设置：评估=${evalLabel(res.data.evalStatus)}，共享=${shareLabel(res.data.shareAttr)}`)
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function applyCandidate(row: Record<string, unknown>) {
  const targetId = analyzingId.value
  if (!targetId) return ElMessage.warning('请先对需求执行智能匹配')
  const body: Record<string, unknown> = {
    evalStatus: row.suggestedEvalStatus || 'MATCHED',
    shareAttr: row.suggestedShareAttr || 'CONDITIONAL',
    resourceType: row.resourceType,
    resourceId: row.resourceId,
    matchScore: row.score,
  }
  if (row.resourceType === 'CATALOG') {
    body.matchedCatalogId = row.resourceId
    body.fulfillPath = Number(row.score) >= 30 ? 'AUTHORIZE_EXISTING' : 'NEED_COLLECT'
  }
  const res = await api.post(`/exchange/supply/demands/${targetId}/analysis-settings`, body)
  quickSet.evalStatus = String(res.data.evalStatus)
  quickSet.shareAttr = String(res.data.shareAttr)
  if (res.data.fulfillPath) dispatchForm.fulfillPath = res.data.fulfillPath as FulfillPath
  ElMessage.success(`已一键绑定「${row.title}」`)
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function bindResourceToDemand(row: Record<string, unknown>) {
  if (!analyzingId.value) {
    ElMessage.info('请先在需求列表点击「智能匹配」，再绑定资源')
    resourceSearch.keyword = String(row.title || '')
    return
  }
  await applyCandidate(row)
}

async function confirmDemand(id: number, row: Record<string, unknown>) {
  const res = await api.post(`/exchange/supply/demands/${id}/confirm`, {
    confirmNote: confirmNote.value,
    confirmFeedback: confirmFeedback.value || undefined,
    fulfillPath: row.fulfillPath || dispatchForm.fulfillPath,
    supplyMode: row.fulfillPath === 'NEED_COLLECT' ? 'COLLECT' : 'EXCHANGE',
    authLevel: 'DEPT',
    cascadeFlag: 0,
  })
  confirmResult.value = res.data
  const taskCount = (res.data.tasks as unknown[])?.length || 0
  ElMessage.success(`已确认：数据责任 #${res.data.dutyId}，生成 ${taskCount} 项归集/共享任务`)
  selectedDemandId.value = id
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function rejectDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/reject`, {
    confirmNote: confirmNote.value || '不符合共享范围',
  })
  ElMessage.success('已驳回')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function confirmReturnDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/confirm-return`, {
    confirmNote: confirmNote.value || '供数部门退回，请补充材料',
    confirmFeedback: confirmFeedback.value || undefined,
  })
  ElMessage.success('已退回需数部门')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function submitConfirmFeedback(id: number) {
  if (!confirmFeedback.value) return ElMessage.warning('请填写督查反馈')
  await api.post(`/exchange/supply/demands/${id}/confirm-feedback`, {
    confirmFeedback: confirmFeedback.value,
  })
  ElMessage.success('督查反馈已提交')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function completeDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/complete`, {
    confirmNote: confirmNote.value,
    confirmFeedback: confirmFeedback.value || undefined,
  })
  ElMessage.success('需求已办结')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

async function cancelDemand(id: number) {
  await api.post(`/exchange/supply/demands/${id}/cancel`, {
    confirmNote: confirmNote.value || '需求已撤销',
  })
  ElMessage.success('需求已撤销')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

function openEdit(row: Record<string, unknown>) {
  editDialog.visible = true
  editDialog.id = Number(row.id)
  editDialog.demandTitle = String(row.demandTitle || '')
  editDialog.requesterOrg = String(row.requesterOrg || '')
  editDialog.assigneeOrg = String(row.assigneeOrg || '')
  editDialog.fulfillPath = (row.fulfillPath as FulfillPath) || 'NEED_COLLECT'
}

async function saveEdit() {
  await api.post(`/exchange/supply/demands/${editDialog.id}/update`, {
    demandTitle: editDialog.demandTitle,
    requesterOrg: editDialog.requesterOrg,
    assigneeOrg: editDialog.assigneeOrg,
    fulfillPath: editDialog.fulfillPath,
    confirmNote: confirmNote.value,
  })
  editDialog.visible = false
  ElMessage.success('需求已修改')
  demands.value = (await api.get('/exchange/supply/demands')).data
}

const confirmPending = computed(() =>
  demands.value.filter((d) => ['ANALYZING', 'DISPATCHED', 'SUPERVISING'].includes(String(d.status))),
)
const confirmManaged = computed(() =>
  demands.value.filter((d) => ['CONFIRMED', 'COMPLETED', 'CANCELLED', 'REJECTED', 'RETURNED'].includes(String(d.status))),
)

async function loadSupplyView(id: number) {
  selectedDemandId.value = id
  supplyView.value = (await api.get(`/exchange/supply/supply-view/${id}`)).data
  supplyTasks.value = (supplyView.value?.tasks as Record<string, unknown>[])
    || (await api.get('/exchange/supply/supply-tasks', { params: { demandId: id } })).data
  duties.value = (supplyView.value?.duties as Record<string, unknown>[])
    || (await api.get('/exchange/supply/duties', { params: { demandId: id } })).data
  exchangeJobs.value = (supplyView.value?.exchangeJobs as Record<string, unknown>[]) || []
  apiEndpoints.value = (supplyView.value?.apiEndpoints as Record<string, unknown>[]) || []
  sharePages.value = (supplyView.value?.sharePages as Record<string, unknown>[]) || []
}

async function submitObjection() {
  if (!objectionForm.catalogId || !objectionForm.content) return ElMessage.warning('请填写异议')
  await api.post('/exchange/supply/objections', objectionForm)
  objectionForm.content = ''
  ElMessage.success('异议已登记')
  await loadListCenter()
}

watch(() => [route.query.module, route.query.section, route.query.tab, route.query.sdSection], () => {
  if (props.embedded) {
    const sd = String(route.query.sdSection || '')
    if (sd && SUPPLY_MAIN_SECTIONS.some((s) => s.key === sd)) {
      section.value = sd
      loadSection()
    }
    return
  }
  syncRoute()
  loadSection()
})
onMounted(() => {
  if (props.embedded) {
    const sd = String(route.query.sdSection || 'demand')
    section.value = SUPPLY_MAIN_SECTIONS.some((s) => s.key === sd) ? sd : 'demand'
    loadSection()
    return
  }
  syncRoute()
  loadSection()
})
</script>

<template>
  <div v-loading="loading">
    <el-alert
      v-if="section === 'manifest-center'"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom:12px"
      title="清单中心：查看部门目录清单、服务清单、开放清单与异议清单，监控数据共享开放全流程。目录发布请在系统管理·供需配置维护。"
    />
    <el-radio-group :model-value="section" style="margin-bottom:12px" @change="setSection">
      <el-radio-button v-for="s in mainSections" :key="s.key" :value="s.key">{{ s.label }}</el-radio-button>
    </el-radio-group>

    <PageCard v-if="section === 'demand'" title="数据需求管理">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="支持模型化管理：选择需求模板后按模板字段填报；可切换结构化 / 非结构化输入。模板维护在「系统管理 → 供需配置」。"
      />
      <el-form label-width="96px" style="max-width:860px">
        <el-form-item label="需求模板">
          <el-select v-model="demandForm.templateCode" clearable placeholder="可选模板" style="width:280px" @change="onTemplateChange">
            <el-option v-for="t in templates" :key="t.templateCode" :label="`${t.templateName}（${t.demandType}）`" :value="t.templateCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求类型">
          <el-radio-group v-model="demandForm.demandType">
            <el-radio-button value="STRUCTURED">结构化</el-radio-button>
            <el-radio-button value="UNSTRUCTURED">非结构化</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="需求标题">
          <el-input v-model="demandForm.demandTitle" placeholder="模型化需求名称" />
        </el-form-item>
        <el-form-item label="申请方">
          <el-input v-model="demandForm.requesterOrg" />
        </el-form-item>
        <template v-if="demandForm.demandType === 'STRUCTURED'">
          <el-form-item v-for="f in templateFields" :key="f.key" :label="f.label">
            <el-input v-model="modelFieldValues[f.key]" :placeholder="`请输入${f.label}`" />
          </el-form-item>
          <el-form-item v-if="!templateFields.length" label="说明">
            <el-text type="info">未选择模板或模板无字段定义时，仅提交标题等基础信息；可在供需配置中维护模板 schema。</el-text>
          </el-form-item>
        </template>
        <el-form-item v-else label="需求正文">
          <el-input v-model="demandForm.demandContent" type="textarea" :rows="5" placeholder="粘贴或录入非结构化需求说明、附件摘要等" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitDemand">提交需求</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="demands" stripe size="small" style="margin-top:12px">
        <el-table-column prop="demandTitle" label="需求" min-width="160" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ $statusLabel(row.demandType) }}</template>
        </el-table-column>
        <el-table-column prop="templateCode" label="模板" width="120" />
        <el-table-column prop="stage" label="阶段" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status === 'SUBMITTED'" link type="danger" @click="withdrawDemand(Number(row.id))">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </PageCard>

    <PageCard v-else-if="section === 'analysis'" title="数据需求分析">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="数据管理员：分析 / 分发 / 退回 / 督查督办；支持资源目录快查与智能辅助匹配（目录·库表·接口），并可一键设置评估状态与共享属性。"
      />

      <PageCard title="资源目录快速查询" style="margin-bottom:12px">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="关键词" class="portal-field-lg">
            <el-input v-model="resourceSearch.keyword" placeholder="目录/库表/接口名称" clearable @keyup.enter="searchResourceCatalog" />
          </el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="resourceSearch.resourceType">
              <el-option label="全部" value="ALL" />
              <el-option label="目录" value="CATALOG" />
              <el-option label="库表" value="TABLE" />
              <el-option label="接口" value="API" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="searchResourceCatalog">查询</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="resourceHits" stripe size="small" max-height="220">
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
          </el-table-column>
          <el-table-column prop="resourceCode" label="编码" width="120" />
          <el-table-column prop="title" label="名称" min-width="140" />
          <el-table-column prop="score" label="匹配度" width="80" />
          <el-table-column prop="subtitle" label="说明" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click="bindResourceToDemand(row)">选用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </PageCard>

      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="履约路径" class="portal-field-xl">
          <el-select v-model="dispatchForm.fulfillPath">
            <el-option v-for="o in FULFILL_PATH_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="分发单位" class="portal-field-md"><el-input v-model="dispatchForm.assigneeOrg" /></el-form-item>
        <el-form-item label="督办说明" class="portal-field-xl"><el-input v-model="superviseNote" /></el-form-item>
      </el-form>
      <el-form inline class="portal-inline-form portal-inline-form--block">
        <el-form-item label="评估状态" class="portal-field-md">
          <el-select v-model="quickSet.evalStatus">
            <el-option v-for="o in EVAL_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="共享属性" class="portal-field-md">
          <el-select v-model="quickSet.shareAttr">
            <el-option v-for="o in SHARE_ATTR_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item class="portal-form-actions">
          <el-button type="success" @click="applyQuickSettings()">一键设置当前需求</el-button>
        </el-form-item>
      </el-form>

      <el-table
        :data="demands.filter(d => ['SUBMITTED','ANALYZING','DISPATCHED','RETURNED','SUPERVISING'].includes(String(d.status)))"
        stripe
        size="small"
      >
        <el-table-column prop="demandTitle" label="需求" min-width="140" />
        <el-table-column prop="matchScore" label="匹配度" width="80" />
        <el-table-column label="评估" width="100">
          <template #default="{ row }">{{ evalLabel(row.evalStatus) }}</template>
        </el-table-column>
        <el-table-column label="共享" width="110">
          <template #default="{ row }">{{ shareLabel(row.shareAttr) }}</template>
        </el-table-column>
        <el-table-column label="履约路径" width="150">
          <template #default="{ row }">{{ fulfillLabel(row.fulfillPath) }}</template>
        </el-table-column>
        <el-table-column prop="assigneeOrg" label="分发单位" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="analyzeDemand(Number(row.id))">智能匹配</el-button>
            <el-button link @click="dispatchDemand(Number(row.id))">分发</el-button>
            <el-button link type="warning" @click="returnDemand(Number(row.id))">退回</el-button>
            <el-button link type="danger" @click="superviseDemand(Number(row.id))">督办</el-button>
            <el-button link type="success" @click="applyQuickSettings(Number(row.id))">一键设置</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="analysisResult" class="analysis-panel">
        <el-alert
          type="success"
          :closable="false"
          style="margin-bottom:12px"
          :title="`需求 #${analysisResult.demandId}：${analysisResult.analysisNote}`"
        />
        <h4>智能匹配候选（目录 / 库表 / 接口）</h4>
        <el-table :data="analysisCandidates" stripe size="small" style="margin-bottom:12px">
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ resourceTypeLabel(row.resourceType) }}</template>
          </el-table-column>
          <el-table-column prop="title" label="资源" min-width="140" />
          <el-table-column prop="score" label="匹配度%" width="90" />
          <el-table-column label="建议评估" width="100">
            <template #default="{ row }">{{ evalLabel(row.suggestedEvalStatus) }}</template>
          </el-table-column>
          <el-table-column label="建议共享" width="110">
            <template #default="{ row }">{{ shareLabel(row.suggestedShareAttr) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click="applyCandidate(row)">一键采用</el-button>
            </template>
          </el-table-column>
        </el-table>

        <h4>关联关系图</h4>
        <div v-if="relationGraph?.nodes?.length" class="relation-graph">
          <div class="relation-nodes">
            <div
              v-for="n in relationGraph.nodes"
              :key="String(n.id)"
              class="relation-node"
              :class="`is-${String(n.type).toLowerCase()}`"
            >
              <span class="relation-node__type">{{ resourceTypeLabel(n.type) }}</span>
              <span class="relation-node__label">{{ n.label }}</span>
            </div>
          </div>
          <ul class="relation-edges">
            <li v-for="(e, idx) in relationGraph.edges || []" :key="idx">
              {{ e.from }} → {{ e.to }}
              <el-tag size="small" type="info">{{ e.label }}</el-tag>
            </li>
          </ul>
        </div>
        <el-empty v-else description="暂无关联关系" :image-size="64" />
      </div>
    </PageCard>

    <PageCard v-else-if="section === 'confirm'" title="数据需求确认">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="供数部门：确认可满足后自动转数据责任，并对接目录 / 共享交换 / 归集生成任务；支持退回、督查反馈，以及对需求整体办结、撤销、修改。"
      />
      <el-form label-width="88px" style="max-width:720px;margin-bottom:12px">
        <el-form-item label="确认说明">
          <el-input v-model="confirmNote" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="督查反馈">
          <el-input v-model="confirmFeedback" type="textarea" :rows="2" placeholder="供数部门对督办的反馈意见" />
        </el-form-item>
      </el-form>

      <h4 class="confirm-h">待确认需求</h4>
      <el-table :data="confirmPending" stripe size="small">
        <el-table-column prop="demandTitle" label="需求" min-width="140" />
        <el-table-column prop="assigneeOrg" label="供数单位" width="110" />
        <el-table-column prop="matchedCatalogId" label="匹配目录" width="90" />
        <el-table-column label="履约路径" width="150">
          <template #default="{ row }">{{ fulfillLabel(row.fulfillPath) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="confirmDemand(Number(row.id), row)">确认并生成任务</el-button>
            <el-button link type="warning" @click="confirmReturnDemand(Number(row.id))">退回</el-button>
            <el-button link @click="submitConfirmFeedback(Number(row.id))">督查反馈</el-button>
            <el-button link type="danger" @click="rejectDemand(Number(row.id))">驳回</el-button>
            <el-button link @click="openEdit(row)">修改</el-button>
            <el-button link type="info" @click="cancelDemand(Number(row.id))">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>

      <h4 class="confirm-h">已确认 / 办结台账</h4>
      <el-table :data="confirmManaged" stripe size="small">
        <el-table-column prop="demandTitle" label="需求" min-width="140" />
        <el-table-column prop="confirmNote" label="确认说明" min-width="160" show-overflow-tooltip />
        <el-table-column prop="confirmFeedback" label="督查反馈" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'CONFIRMED'" link type="success" @click="completeDemand(Number(row.id))">办结</el-button>
            <el-button v-if="!['COMPLETED','CANCELLED','WITHDRAWN'].includes(String(row.status))" link @click="openEdit(row)">修改</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" link type="info" @click="cancelDemand(Number(row.id))">撤销</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" link type="primary" @click="loadSupplyView(Number(row.id)); setSection('supply')">查看任务</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="confirmResult" class="confirm-result">
        <el-alert type="success" :closable="false" show-icon style="margin-bottom:12px"
          :title="`确认成功：数据责任 #${confirmResult.dutyId}；${(confirmResult.integrations as any)?.message || ''}`" />
        <el-descriptions :column="1" border size="small" title="系统对接结果">
          <el-descriptions-item label="目录系统">
            匹配目录 ID={{ (confirmResult.integrations as any)?.catalog?.matchedCatalogId || '-' }}，
            责任 ID={{ (confirmResult.integrations as any)?.catalog?.dutyId }}
          </el-descriptions-item>
          <el-descriptions-item label="数据归集系统">
            {{ ((confirmResult.integrations as any)?.collect?.tasks || []).length }} 项归集任务
          </el-descriptions-item>
          <el-descriptions-item label="共享交换系统">
            {{ ((confirmResult.integrations as any)?.exchange?.tasks || []).length }} 项共享/交换任务
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="(confirmResult.tasks as Record<string, unknown>[]) || []" stripe size="small" style="margin-top:12px">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ $statusLabel(row.taskType) }}</template>
          </el-table-column>
          <el-table-column prop="taskName" label="任务" min-width="200" />
          <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
          <el-table-column prop="refFlowCode" label="关联引用" width="140" />
        </el-table>
      </div>

      <el-dialog v-model="editDialog.visible" title="修改需求" width="520px">
        <el-form label-width="88px">
          <el-form-item label="标题"><el-input v-model="editDialog.demandTitle" /></el-form-item>
          <el-form-item label="申请方"><el-input v-model="editDialog.requesterOrg" /></el-form-item>
          <el-form-item label="供数单位"><el-input v-model="editDialog.assigneeOrg" /></el-form-item>
          <el-form-item label="履约路径">
            <el-select v-model="editDialog.fulfillPath" style="width:100%">
              <el-option v-for="o in FULFILL_PATH_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editDialog.visible = false">取消</el-button>
          <el-button type="primary" @click="saveEdit">保存</el-button>
        </template>
      </el-dialog>
    </PageCard>

    <PageCard v-else-if="section === 'supply'" title="数据供给查看">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px"
        title="查看数据供给共享方式：交换作业、接口、通用共享页面。"
      />
      <el-select v-model="selectedDemandId" placeholder="选择已确认需求" style="width:320px;margin-bottom:12px" @change="loadSupplyView">
        <el-option v-for="d in demands.filter(x => ['CONFIRMED','COMPLETED'].includes(String(x.status)))" :key="String(d.id)" :label="String(d.demandTitle)" :value="Number(d.id)" />
      </el-select>
      <h4 v-if="duties.length" class="confirm-h">数据责任</h4>
      <el-table v-if="duties.length" :data="duties" stripe size="small" style="margin-bottom:12px">
        <el-table-column prop="dutyOrg" label="责任单位" min-width="120" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ $statusLabel(row.dutyType) }}</template>
        </el-table-column>
        <el-table-column label="履约路径" width="160">
          <template #default="{ row }">{{ fulfillLabel(row.fulfillPath) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
      </el-table>

      <el-row :gutter="12">
        <el-col :span="24" :md="8">
          <PageCard title="交换作业">
            <el-table :data="exchangeJobs" stripe size="small" empty-text="暂无交换/归集作业">
              <el-table-column label="类型" width="90">
                <template #default="{ row }">{{ $statusLabel(row.taskType) }}</template>
              </el-table-column>
              <el-table-column prop="taskName" label="作业" min-width="120" />
              <el-table-column prop="flowCode" label="流编码" width="100" />
              <el-table-column label="状态" width="80">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
            </el-table>
          </PageCard>
        </el-col>
        <el-col :span="24" :md="8">
          <PageCard title="接口">
            <el-table :data="apiEndpoints" stripe size="small" empty-text="暂无接口">
              <el-table-column prop="name" label="名称" min-width="100" />
              <el-table-column prop="method" label="方式" width="80" />
              <el-table-column prop="endpoint" label="地址" min-width="140" show-overflow-tooltip />
            </el-table>
          </PageCard>
        </el-col>
        <el-col :span="24" :md="8">
          <PageCard title="通用共享页面">
            <el-table :data="sharePages" stripe size="small" empty-text="暂无共享页">
              <el-table-column prop="title" label="页面" min-width="120" />
              <el-table-column prop="url" label="链接" min-width="140" show-overflow-tooltip />
              <el-table-column label="打开" width="80">
                <template #default="{ row }">
                  <el-button link type="primary" @click="router.push(String(row.url))">进入</el-button>
                </template>
              </el-table-column>
            </el-table>
          </PageCard>
        </el-col>
      </el-row>
      <el-empty v-if="!selectedDemandId" description="请选择已确认需求查看供给共享方式" />
    </PageCard>

    <PageCard v-else-if="section === 'manifest-center'" title="数据清单中心">
      <el-radio-group :model-value="listCenterSub" style="margin-bottom:12px" @change="setListCenterSub">
        <el-radio-button v-for="s in manifestCenterSections" :key="s.key" :value="s.key">{{ s.label }}</el-radio-button>
      </el-radio-group>

      <template v-if="listCenterSub === 'objection'">
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="目录" class="portal-field-default">
            <el-select v-model="objectionForm.catalogId">
              <el-option v-for="c in catalogs" :key="String(c.id)" :label="String(c.title)" :value="Number(c.id)" />
            </el-select>
          </el-form-item>
          <el-form-item label="类型" class="portal-field-sm">
            <el-select v-model="objectionForm.objectionType">
              <el-option label="质量" value="QUALITY" />
              <el-option label="完整性" value="COMPLETENESS" />
              <el-option label="授权" value="AUTH" />
            </el-select>
          </el-form-item>
          <el-form-item label="内容" class="portal-field-lg"><el-input v-model="objectionForm.content" /></el-form-item>
          <el-form-item class="portal-form-actions"><el-button type="primary" @click="submitObjection">登记异议</el-button></el-form-item>
        </el-form>
        <el-table :data="listCenterItems" stripe size="small">
          <el-table-column prop="catalogId" label="目录ID" width="90" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ $statusLabel(row.objectionType) }}</template>
          </el-table-column>
          <el-table-column prop="content" label="内容" min-width="200" />
          <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ $statusLabel(row.status) }}</template>
        </el-table-column>
        </el-table>
      </template>
      <el-table v-else :data="listCenterItems" stripe size="small">
        <el-table-column prop="code" label="编码" width="140">
          <template #default="{ row }">{{ row.code || row.catalogCode || row.id }}</template>
        </el-table-column>
        <el-table-column prop="title" label="名称" min-width="160" />
        <el-table-column v-if="listCenterSub === 'service-list'" label="类型" width="120">
          <template #default="{ row }">{{ $statusLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column v-if="listCenterSub === 'open-list'" label="共享属性" width="110">
          <template #default="{ row }">{{ $statusLabel(row.shareAttr) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }"><el-tag :type="$statusTagType(row.status)" size="small">{{ $statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
      </el-table>
    </PageCard>
  </div>
</template>

<style scoped>
.analysis-panel {
  margin-top: 16px;
  padding-top: 8px;
  border-top: 1px solid var(--portal-border);
}
.confirm-h {
  margin: 16px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--portal-text);
}
.confirm-result {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--portal-border);
}
.analysis-panel h4 {
  margin: 8px 0 10px;
  font-size: 14px;
  color: var(--portal-text);
}
.relation-graph {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.relation-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.relation-node {
  min-width: 140px;
  max-width: 220px;
  padding: 10px 12px;
  border-radius: var(--portal-radius);
  border: 1px solid var(--portal-border);
  background: var(--portal-card-bg);
  box-shadow: var(--portal-shadow);
}
.relation-node__type {
  display: block;
  font-size: 11px;
  color: var(--portal-text-secondary);
  margin-bottom: 4px;
}
.relation-node__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--portal-text);
  word-break: break-all;
}
.relation-node.is-demand {
  border-color: color-mix(in srgb, var(--portal-primary) 50%, var(--portal-border));
  background: color-mix(in srgb, var(--portal-primary) 8%, white);
}
.relation-node.is-catalog {
  border-color: #9ad4c8;
}
.relation-node.is-table {
  border-color: #fac775;
}
.relation-node.is-api {
  border-color: #cecbf6;
}
.relation-edges {
  margin: 0;
  padding-left: 18px;
  color: var(--portal-text-secondary);
  font-size: 12px;
  line-height: 1.8;
}
</style>
