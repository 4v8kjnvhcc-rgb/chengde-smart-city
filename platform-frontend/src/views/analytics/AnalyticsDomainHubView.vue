<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavGroup } from '@/components/common/HubSideLayout.vue'
import DomainIndicatorSqlLibrary from '@/views/analytics/DomainIndicatorSqlLibrary.vue'
import DomainIndicatorDomainManage from '@/views/analytics/DomainIndicatorDomainManage.vue'
import DomainIndicatorGroupManage from '@/views/analytics/DomainIndicatorGroupManage.vue'
import DomainIndicatorTaskPanel from '@/views/analytics/DomainIndicatorTaskPanel.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { ingestionApi } from '@/views/exchange/ingestion/useIngestionHub'

interface ZoneDef {
  key: string
  label: string
  zoneCode: string
  mCodes: string[]
  deepLink: string
  deepLabel: string
}

interface Binding {
  id: number
  assetType: string
  assetRef: string
  assetName: string
  physicalTable?: string
  metaEntryCode?: string
  dataLayer?: string
  dimGroup?: string
  accessMode?: string
  status: string
  createdBy?: string
  createdAt?: string
}

interface Candidate {
  assetType: string
  assetRef: string
  assetName: string
  physicalTable?: string
  metaEntryCode?: string
  dataLayer?: string
  dimGroup?: string
  accessMode?: string
}

interface MetaSourceOption {
  id: number
  sourceName: string
  categoryId?: number
  categoryName?: string
}

interface CategoryNode {
  id: number
  label: string
  categoryCode?: string
  layerCode?: string
  children?: CategoryNode[]
}

interface OdsTableRow {
  tableName: string
  sourceName: string
  dataLayer: string
}

interface DocRow {
  id: number
  title: string
  categoryCode?: string
  categoryName?: string
  sourceType?: string
  publishStatus?: string
  updatedAt?: string
}

interface ChannelRow {
  id: number
  channelCode: string
  channelName: string
  channelType: string
  status: string
  lastRunAt?: string
  lastMessage?: string
}

interface Indicator {
  id: number
  indicatorCode: string
  indicatorName: string
  queryNo?: string
  resultField?: string
  fieldType?: string
  fieldName?: string
  sourceTable?: string
  sourceColumn?: string
  aggFunc?: string
  exprText?: string
  unitLabel?: string
  description?: string
  status: string
}

interface AnalysisModel {
  id: number
  modelCode: string
  modelName: string
  mCode?: string
  deDashboardId?: string
  dimensionJson?: string
  description?: string
  status: string
  indicators?: Indicator[]
}

interface ModelSample {
  id?: number
  modelId?: number
  rowNo: number
  dim1?: string
  dim2?: string
  metric1?: number | string
  metric2?: number | string
}

interface VerifyLedgerRow {
  id: number
  mCode: string
  sceneCode: string
  sceneName: string
  checkType: string
  sourceDept?: string
  issueSummary?: string
  feedbackStatus: string
  relatedPersonId?: string
  status: string
  createdAt?: string
}

interface ServiceContract {
  id: number
  serviceCode: string
  serviceName: string
  mCode: string
  mode: string
  pathOrChannel?: string
  requestSample?: string
  responseSample?: string
  description?: string
  status: string
}

interface BatchLedgerRow {
  id: number
  batchCode: string
  serviceCode?: string
  channel?: string
  tableName?: string
  rowLimit?: number
  batchStatus: string
  message?: string
  status: string
  createdAt?: string
}

const ASSET_TYPE_ZH: Record<string, string> = {
  METADATA: '元数据表',
  MANAGED: '纳管表',
  CATALOG: '目录资源',
  DOCUMENT: '文件资源',
  CHANNEL_API: 'API通道',
  CHANNEL_CDC: 'CDC通道',
  OTHER: '其他',
}

const DIM_GROUP_ZH: Record<string, string> = {
  DATATYPE: '数据类型',
  LATENCY: '数据时效性',
}

const ACCESS_MODE_ZH: Record<string, string> = {
  STRUCT: '结构化数据接入',
  UNSTRUCT: '非结构化数据接入',
  API: 'API接口数据接入',
  CDC: 'CDC实时数据接入',
}

function assetTypeLabel(v?: string) {
  if (!v) return '—'
  return ASSET_TYPE_ZH[v] || statusLabel(v)
}

function dimGroupLabel(v?: string) {
  if (!v) return '—'
  return DIM_GROUP_ZH[v] || statusLabel(v)
}

function accessModeLabel(v?: string) {
  if (!v) return '—'
  return ACCESS_MODE_ZH[v] || statusLabel(v)
}

type DimItem = { key: string; tip: string }

/** 法人等域通用七维（挂载设计器默认文案） */
const SEVEN_DIMS_GENERIC: DimItem[] = [
  { key: '定位', tip: '本区在人口/法人域架构中的位置与职责边界' },
  { key: '数据模型', tip: '通过下方「资产挂载」选定实体与关系，不平行建库' },
  { key: '加工处理', tip: '数据进入本区前的治理/融合流程在主数据侧完成' },
  { key: '存储周期', tip: '保留策略沿用源系统/纳管表策略，此处只做区映射' },
  { key: '数据来源', tip: '从候选资产选型挂载（元数据/纳管表/目录资源）' },
  { key: '使用者', tip: '内部服务区强调授权边界；共享区面向目录与分析消费' },
  { key: '更新频度', tip: '跟随源任务调度；本页不伪造外部调度成功' },
]

/** 人口域五区七维度 — 对齐规格 docs/superpowers/specs/2026-08-11-population-bigdata-support-design.md */
const POPULATION_ZONE_DIMS: Record<string, DimItem[]> = {
  collect: [
    { key: '定位', tip: '多源异构人口数据统一汇入平台；含结构化与非结构化；按时效区分行为类/档案类通道' },
    { key: '数据模型', tip: '贴源结构为主；通道类型覆盖库表、文件、API' },
    { key: '加工处理', tip: '本区不做主题整合；仅接入、落 ODS、登记元数据' },
    { key: '存储周期', tip: '长期可存；容量策略随存储扩展' },
    { key: '数据来源', tip: '公安、民政、法院及教育/人社/卫健等业务系统' },
    { key: '使用者', tip: '治理反馈区（下游清洗校核）' },
    { key: '更新频度', tip: '默认月更；可按日/周/季/半年/年配置（ExecCycle + DS）' },
  ],
  govern: [
    { key: '定位', tip: '存放问题数据、半结构/非结构转结构结果；供质量分析与问题反馈' },
    { key: '数据模型', tip: '贴源 + 规范化；流水类周期增量切片' },
    { key: '加工处理', tip: '技术性/合法性检核清洗；时点记录变更；半结构预处理' },
    { key: '存储周期', tip: '长期可存；预处理/反馈库中等体量、高吞吐' },
    { key: '数据来源', tip: '采集区；外部数据区' },
    { key: '使用者', tip: '核心区；服务区（须经核心分流，禁止旁路权威）' },
    { key: '更新频度', tip: '默认月更；可按日/周/季/半年/年' },
  ],
  core: [
    { key: '定位', tip: '统一标准的人口基础/主题权威区；「一数一源」；按业务分类存放' },
    { key: '数据模型', tip: '宽表、多维；允许冗余；可在基础/主题上扩展专业库（逻辑分层）' },
    { key: '加工处理', tip: '多源合并到同一人员实体；跨业务计算；提取服务区特征' },
    { key: '存储周期', tip: '长期可存；垂直分片/水平分区；历史与审计近线' },
    { key: '数据来源', tip: '治理及反馈区（经融合落入 DWS）' },
    { key: '使用者', tip: '内部服务区；共享服务区' },
    { key: '更新频度', tip: '默认月更；可按业务配置' },
  ],
  internal: [
    { key: '定位', tip: '高权限、高敏感人口基础数据应用场景的独立服务边界' },
    { key: '数据模型', tip: '消费核心区权威结构化数据；不另建第二权威源' },
    { key: '加工处理', tip: '分级分类 + 双重授权；系统管理员不可直接授跨部门数据访问权' },
    { key: '存储周期', tip: '跟随核心；访问审计日志可近线' },
    { key: '数据来源', tip: '核心区' },
    { key: '使用者', tip: '部门内高敏业务应用与管理员' },
    { key: '更新频度', tip: '跟随核心供数节奏' },
  ],
  share: [
    { key: '定位', tip: '人口资源目录、接口/批量共享、指标与十四分析模型消费' },
    { key: '数据模型', tip: '可发布目录项、接口契约、批量交换库、ADS 指标/专题结果' },
    { key: '加工处理', tip: '目录发布；小流量接口（校核/比对）；大批量前置；自研模型结果展示' },
    { key: '存储周期', tip: '目录与接口元数据长期；批量结果库按交换周期清理或归档' },
    { key: '数据来源', tip: '核心区（及 ADS）' },
    { key: '使用者', tip: '共建单位应用、目录用户、人口 Hub 分析用户' },
    { key: '更新频度', tip: '跟随核心/专题；模型结果随指标任务' },
  ],
}

const POPULATION_ZONE_ALERT: Record<string, string> = {
  collect: '采集区 = 多源汇入设计入口。挂载 ODS/SOURCE 候选；真正抽数在数据归集（Kettle + DS）。本区不做主题整合。',
  govern: '治理反馈区 = 清洗校核与问题回流。挂载 DWD；深链数据治理。更新维护(M155)/信息校核(M156) 为设计口径，问题反馈源部门后再入链。',
  core: '核心区 = 「一数一源」权威库。挂载资源中心纳管表（逻辑 DWS）；深链资源中心。融合落库后供内部/共享分流。',
  internal: '内部服务区 = 高敏受控消费边界。对齐双重授权（M158/M048）：系统管理员可授角色，不可直接授跨部门数据权。',
  share: '共享服务区 = 目录/接口/批量 + 指标 + 十四模型。人口域模型以自研样例/结果表展示，不使用 DataEase/BI。',
}

const GOVERN_MECHANISM_CARDS = [
  {
    code: 'M155',
    title: '更新维护',
    points: [
      '户籍基准由公安定时维护；各部门负责本域基础数据增改',
      '平台主动采集至 ODS，再经治理/融合进入 DWS',
      '技术落点复用归集定时（DS + Kettle），人口域不自建调度',
    ],
  },
  {
    code: 'M156',
    title: '信息校核',
    points: [
      '入原始库后做检查与基准校核，使业务信息与人员基础信息对应',
      '覆盖注销人员、出生未申报户口、婚姻状况等多源场景',
      '质量问题反馈提供单位更正后再入链；不在核心区静默篡改权威值',
    ],
  },
]

const SHARE_SERVICE_CARDS = [
  {
    code: 'M159',
    title: '服务接口',
    points: [
      '适用小数据量：应用校核、核查比对、基准叠加',
      '政务外网向共建单位应用提供',
      '现网：本页说明 + 接口交换深链；生产 API 属后续工程',
    ],
  },
  {
    code: 'M160',
    title: '大批量应用',
    points: [
      '适用大数据量：共建单位前置批量库 ↔ 交换系统 ↔ 共享平台结果库',
      '批量结果库按交换周期清理或归档',
      '现网：批量台账 LEDGER + 应用/交换深链；全链路属后续工程',
    ],
  },
]

const COLLECT_SOURCE_CHANNELS = [
  { dept: '公安局', channel: '库表/API', latency: '高时效+档案', remark: '户籍基准与人员主数据' },
  { dept: '民政局', channel: '库表/文件', latency: '月更为主', remark: '婚姻、救助等' },
  { dept: '法院', channel: '文件/API', latency: '按案件周期', remark: '裁判文书等结构化抽取' },
  { dept: '教育局', channel: '库表', latency: '学期/年', remark: '学籍与学历' },
  { dept: '人社局', channel: '库表/API', latency: '月更', remark: '社保就业' },
  { dept: '卫健委', channel: '库表/文件', latency: '日/月', remark: '出生死亡等' },
]

const DUAL_AUTH_CARDS = [
  {
    code: 'M158',
    title: '双重授权边界',
    points: [
      '高敏人口基础数据独立服务边界；消费核心区权威数据',
      '系统管理员可授部门管理员角色，不可直接授跨部门数据访问权',
      '跨部门数据权走申请审批；对齐全局 M048/M211',
    ],
  },
  {
    code: 'M048',
    title: '平台授权能力（复用）',
    points: [
      '项目授权 / 数据授权 / 跨部门审批在归集登记「访问控制」',
      '人口内部区不另造授权引擎，深链复用现网 RBAC',
      '主题表数据权扩展到 DWS 查询路径属生产强化项',
    ],
  },
]

const STORAGE_DESIGN_CARDS = [
  {
    code: 'M157',
    title: '存储管理设计',
    points: [
      '垂直分片：相片等与基础信息分库（逻辑）',
      '水平分区：按区县/时间等切分；历史与审计近线',
      '现网：资源中心分区策略 LEDGER；真正 ALTER 需运维窗口',
    ],
  },
]

function modelRowClassName({ row }: { row: AnalysisModel }) {
  return highlightModelCode.value && row.mCode === highlightModelCode.value ? 'row-hl' : ''
}

const ZONE_DEFS: Record<string, ZoneDef[]> = {
  population: [
    { key: 'zone.collect', zoneCode: 'collect', label: '人口数据采集区设计', mCodes: ['M152'], deepLink: '/exchange/ingestion', deepLabel: '打开数据归集' },
    { key: 'zone.govern', zoneCode: 'govern', label: '人口数据治理及反馈区设计', mCodes: ['M153', 'M155', 'M156'], deepLink: '/governance', deepLabel: '打开数据治理' },
    { key: 'zone.core', zoneCode: 'core', label: '人口核心数据区设计', mCodes: ['M157'], deepLink: '/resource-center', deepLabel: '打开资源中心' },
    { key: 'zone.internal', zoneCode: 'internal', label: '人口数据内部服务区设计', mCodes: ['M158'], deepLink: '/exchange/ingestion?system=register&module=m048', deepLabel: '打开双重授权(M048)' },
    { key: 'zone.share', zoneCode: 'share', label: '人口数据共享服务区设计', mCodes: ['M154', 'M159', 'M160', 'M161', 'M162', 'M163', 'M164', 'M165', 'M166', 'M167', 'M168', 'M169', 'M170', 'M171', 'M172', 'M173', 'M174'], deepLink: '/catalog', deepLabel: '打开资源目录' },
  ],
  legal: [
    { key: 'zone.collect', zoneCode: 'collect', label: '法人数据采集区设计', mCodes: ['M175'], deepLink: '/exchange/ingestion', deepLabel: '打开数据归集' },
    { key: 'zone.govern', zoneCode: 'govern', label: '法人数据治理及反馈区设计', mCodes: ['M176', 'M178', 'M179'], deepLink: '/governance', deepLabel: '打开数据治理' },
    { key: 'zone.core', zoneCode: 'core', label: '法人核心数据区设计', mCodes: ['M180'], deepLink: '/resource-center', deepLabel: '打开资源中心' },
    { key: 'zone.internal', zoneCode: 'internal', label: '法人数据内部服务区设计', mCodes: ['M181'], deepLink: '/resource-center', deepLabel: '打开资源中心' },
    { key: 'zone.share', zoneCode: 'share', label: '法人数据共享服务区设计', mCodes: ['M177', 'M182', 'M183', 'M184', 'M185', 'M186', 'M187', 'M188', 'M189', 'M190', 'M191', 'M192', 'M193', 'M194', 'M195', 'M196', 'M197'], deepLink: '/catalog', deepLabel: '打开资源目录' },
  ],
  macro: [],
  key: [],
}

const domainMeta: Record<string, { domain: string; title: string }> = {
  '/analytics/population': { domain: 'population', title: '人口大数据支撑' },
  '/analytics/legal-entity': { domain: 'legal', title: '法人大数据支撑' },
  '/analytics/macro': { domain: 'macro', title: '宏观经济大数据支撑' },
  '/analytics/key-domains': { domain: 'key', title: '重点领域大数据支撑' },
}

const route = useRoute()
const router = useRouter()

const meta = computed(() => domainMeta[route.path] || domainMeta['/analytics/population'])
const zones = computed(() => ZONE_DEFS[meta.value.domain] || [])
const hasZones = computed(() => zones.value.length > 0)
/** 人口域：自研样例表展示，不用 DataEase/BI */
const isPopulation = computed(() => meta.value.domain === 'population')

const dataEaseHealthy = ref(false)
const activeNav = ref('')
const shareTab = ref<'mount' | 'catalog' | 'api' | 'indicators' | 'models'>('mount')
const indicatorLibTab = ref<'domains' | 'groups' | 'tasks'>('domains')
const indicatorDomainTick = ref(0)
const governTab = ref<'mount' | 'mechanism' | 'verify'>('mount')
const collectTab = ref<'mount' | 'sources'>('mount')
const coreTab = ref<'mount' | 'storage'>('mount')
const internalTab = ref<'mount' | 'auth'>('mount')
const highlightModelCode = ref('')

const bindings = ref<Binding[]>([])
const candidates = ref<Candidate[]>([])
const indicators = ref<Indicator[]>([])
const models = ref<AnalysisModel[]>([])
const modelSamples = ref<ModelSample[]>([])
const samplesLoading = ref(false)
const verifyRows = ref<VerifyLedgerRow[]>([])
const verifyLoading = ref(false)
const serviceContracts = ref<ServiceContract[]>([])
const serviceLoading = ref(false)
const invokeResult = ref('')
const batchRows = ref<BatchLedgerRow[]>([])
const batchLoading = ref(false)
const accessOverview = ref<Record<string, unknown> | null>(null)
const storageSummary = ref<Record<string, unknown> | null>(null)
const storageLoading = ref(false)
const verifyForm = ref({
  mCode: 'M156',
  sceneCode: '',
  sceneName: '',
  checkType: 'MULTI_SOURCE',
  sourceDept: '',
  issueSummary: '',
})
const verifyDialog = ref(false)
const batchDialog = ref(false)
const batchForm = ref({
  batchCode: '',
  tableName: 'dws_population_base',
  rowLimit: 1000,
  message: '',
})

const bindDialog = ref(false)
const selectedCandidate = ref<Candidate | null>(null)
/** 采集区三级选型：1=维度分组 2=接入方式 3=选择对象；其他区仍用单步候选 */
const collectWizard = ref(false)
const bindStep = ref<1 | 2 | 3>(1)
const bindDimGroup = ref<'DATATYPE' | 'LATENCY' | ''>('')
const bindAccessMode = ref<'STRUCT' | 'UNSTRUCT' | 'API' | 'CDC' | ''>('')
const bindObjectLoading = ref(false)
const odsSources = ref<MetaSourceOption[]>([])
const odsSourceId = ref<number | null>(null)
const odsTableKeyword = ref('')
const odsTablesAll = ref<OdsTableRow[]>([])
const odsTablePage = ref(1)
const odsTablePageSize = ref(10)
const selectedOdsTable = ref<OdsTableRow | null>(null)
const docKeyword = ref('')
const docCategoryCode = ref('')
const docPublishStatus = ref('')
const docCategories = ref<Array<{ categoryCode: string; categoryName: string }>>([])
const docsAll = ref<DocRow[]>([])
const docPage = ref(1)
const docPageSize = ref(10)
const selectedDoc = ref<DocRow | null>(null)
const channelKeyword = ref('')
const channelsAll = ref<ChannelRow[]>([])
const channelPage = ref(1)
const channelPageSize = ref(10)
const selectedChannel = ref<ChannelRow | null>(null)

const bindStepActive = computed(() => bindStep.value - 1)
const bindBreadcrumb = computed(() => {
  const parts: string[] = []
  if (bindDimGroup.value) parts.push(dimGroupLabel(bindDimGroup.value))
  if (bindAccessMode.value) parts.push(accessModeLabel(bindAccessMode.value))
  return parts.join(' / ')
})
const odsTablesFiltered = computed(() => {
  const kw = odsTableKeyword.value.trim().toLowerCase()
  if (!kw) return odsTablesAll.value
  return odsTablesAll.value.filter((t) =>
    t.tableName.toLowerCase().includes(kw) || t.sourceName.toLowerCase().includes(kw),
  )
})
const odsTablesPaged = computed(() => {
  const start = (odsTablePage.value - 1) * odsTablePageSize.value
  return odsTablesFiltered.value.slice(start, start + odsTablePageSize.value)
})
const docsPaged = computed(() => {
  const start = (docPage.value - 1) * docPageSize.value
  return docsAll.value.slice(start, start + docPageSize.value)
})
const channelsFiltered = computed(() => {
  const kw = channelKeyword.value.trim().toLowerCase()
  if (!kw) return channelsAll.value
  return channelsAll.value.filter((c) =>
    c.channelCode.toLowerCase().includes(kw) || c.channelName.toLowerCase().includes(kw),
  )
})
const channelsPaged = computed(() => {
  const start = (channelPage.value - 1) * channelPageSize.value
  return channelsFiltered.value.slice(start, start + channelPageSize.value)
})
const canConfirmCollectBind = computed(() => {
  if (bindAccessMode.value === 'STRUCT') return !!selectedOdsTable.value
  if (bindAccessMode.value === 'UNSTRUCT') return !!selectedDoc.value
  if (bindAccessMode.value === 'API' || bindAccessMode.value === 'CDC') return !!selectedChannel.value
  return false
})

const modelDrawer = ref(false)
const editingModel = ref<AnalysisModel | null>(null)
const modelForm = ref({
  modelName: '',
  deDashboardId: '',
  dimensionJson: '',
  description: '',
  indicatorIds: [] as number[],
})
const iframeSrc = ref('')
const embedMode = ref<'LIVE' | 'LEDGER' | ''>('')
const embedMessage = ref('')
const embedUrl = ref('')

let applyingRoute = false
const zoneLoaded = ref('')
const designerLoaded = ref(false)

const navGroups = computed<HubNavGroup[]>(() => {
  if (!hasZones.value) {
    return [{ title: '域设计', items: [{ key: 'designer', label: '指标与分析模型' }] }]
  }
  return [{
    title: '数据区设计',
    items: zones.value.map((z) => ({ key: z.key, label: z.label })),
  }]
})

const activeZone = computed(() => zones.value.find((z) => z.key === activeNav.value) || null)
const isShare = computed(() => activeZone.value?.zoneCode === 'share')
const isGovern = computed(() => activeZone.value?.zoneCode === 'govern')
const isCollect = computed(() => activeZone.value?.zoneCode === 'collect')
const isCore = computed(() => activeZone.value?.zoneCode === 'core')
const isInternal = computed(() => activeZone.value?.zoneCode === 'internal')
const isDesignerOnly = computed(() => !hasZones.value && activeNav.value === 'designer')
const apiContracts = computed(() => serviceContracts.value.filter((c) => c.mode === 'API'))
const batchContracts = computed(() => serviceContracts.value.filter((c) => c.mode === 'BATCH'))
const storagePartitions = computed(() => (storageSummary.value?.partitions as Record<string, unknown>[]) || [])
const storageOps = computed(() => (storageSummary.value?.ops as Record<string, unknown>[]) || [])
const storageManaged = computed(() => (storageSummary.value?.managedTables as Record<string, unknown>[]) || [])
const pageTitle = computed(() => {
  if (activeZone.value) return activeZone.value.label
  if (isDesignerOnly.value) return `${meta.value.title} · 指标与分析模型`
  return meta.value.title
})

const zoneDims = computed<DimItem[]>(() => {
  const z = activeZone.value?.zoneCode
  if (isPopulation.value && z && POPULATION_ZONE_DIMS[z]) {
    return POPULATION_ZONE_DIMS[z]
  }
  return SEVEN_DIMS_GENERIC
})

const zoneAlertTitle = computed(() => {
  const z = activeZone.value?.zoneCode
  if (isPopulation.value && z && POPULATION_ZONE_ALERT[z]) {
    return POPULATION_ZONE_ALERT[z]
  }
  return '区设计 = 从现有资产选型挂载（挂载≠复制）。过程层 DWD 适合治理反馈区，默认可共享资源挂核心区/共享区。'
})

function zoneApiPath(zoneKey: string) {
  return zoneKey.startsWith('zone.') ? zoneKey.slice(5) : zoneKey
}

function pickDefaultNav() {
  if (hasZones.value) {
    activeNav.value = zones.value[0].key
  } else {
    activeNav.value = 'designer'
    if (shareTab.value === 'mount' || shareTab.value === 'catalog' || shareTab.value === 'api') {
      shareTab.value = 'indicators'
    }
  }
}

function resolveFromRoute() {
  applyingRoute = true
  const q = String(route.query.tab || '').toLowerCase()
  highlightModelCode.value = ''
  if (!q) {
    pickDefaultNav()
  } else if (q === 'designer' || q === 'models' || q === 'indicators') {
    if (hasZones.value) {
      const share = zones.value.find((z) => z.zoneCode === 'share')
      activeNav.value = share?.key || zones.value[0]?.key || ''
      shareTab.value = q === 'indicators' ? 'indicators' : q === 'models' ? 'models' : 'mount'
    } else {
      activeNav.value = 'designer'
      shareTab.value = q === 'indicators' ? 'indicators' : 'models'
    }
  } else if (q.startsWith('zone.')) {
    const hit = zones.value.find((z) => z.key === q)
    activeNav.value = hit?.key || zones.value[0]?.key || 'designer'
  } else {
    const code = q.startsWith('m') ? q.toUpperCase() : `M${q}`
    const parentZone = zones.value.find((z) => z.mCodes.includes(code))
    if (parentZone) {
      activeNav.value = parentZone.key
      if (parentZone.zoneCode === 'share') {
        if (code === 'M159' || code === 'M160') {
          shareTab.value = 'api'
        } else if (code === 'M154') {
          shareTab.value = 'catalog'
        } else {
          shareTab.value = 'models'
          highlightModelCode.value = code
        }
      } else if (parentZone.zoneCode === 'govern' && (code === 'M155' || code === 'M156')) {
        governTab.value = 'verify'
      } else if (parentZone.zoneCode === 'core' && code === 'M157') {
        coreTab.value = 'storage'
      } else if (parentZone.zoneCode === 'internal' && code === 'M158') {
        internalTab.value = 'auth'
      } else if (parentZone.zoneCode === 'collect' && code === 'M152') {
        collectTab.value = 'sources'
      }
    } else if (hasZones.value) {
      pickDefaultNav()
    } else {
      activeNav.value = 'designer'
      shareTab.value = 'models'
      highlightModelCode.value = code
    }
  }
  if (!activeNav.value) pickDefaultNav()
  nextTick(() => { applyingRoute = false })
}

function syncQuery() {
  const q: Record<string, string> = {}
  for (const [k, v] of Object.entries(route.query)) {
    if (v == null || k === 'tab') continue
    q[k] = Array.isArray(v) ? String(v[0]) : String(v)
  }
  q.tab = activeNav.value
  router.replace({ query: q })
}

async function loadOverviewLite() {
  if (isPopulation.value) {
    dataEaseHealthy.value = false
    return
  }
  const res = await api.get(`/analytics/domain/${meta.value.domain}/overview`)
  dataEaseHealthy.value = !!res.data.dataEaseHealthy
}

async function loadBindings(force = false) {
  if (!activeZone.value) return
  const z = activeZone.value.zoneCode
  const cacheKey = `${meta.value.domain}:${z}`
  if (!force && zoneLoaded.value === cacheKey && bindings.value.length >= 0) {
    // still allow empty cache hit
  }
  const [bRes, cRes] = await Promise.all([
    api.get(`/analytics/domain/${meta.value.domain}/zones/${z}/bindings`),
    api.get(`/analytics/domain/${meta.value.domain}/zones/${z}/candidates`),
  ])
  bindings.value = bRes.data || []
  candidates.value = cRes.data || []
  zoneLoaded.value = cacheKey
}

async function loadDesigner(force = false) {
  if (designerLoaded.value && !force) return
  const [iRes, mRes] = await Promise.all([
    api.get(`/analytics/domain/${meta.value.domain}/indicators`),
    api.get(`/analytics/domain/${meta.value.domain}/models`),
  ])
  indicators.value = iRes.data || []
  models.value = mRes.data || []
  designerLoaded.value = true
}

async function loadModelSamples(modelId: number) {
  samplesLoading.value = true
  try {
    const res = await api.get(`/analytics/domain/models/${modelId}/samples`)
    modelSamples.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载样例失败')
  } finally {
    samplesLoading.value = false
  }
}

async function loadVerifyLedger() {
  if (!isPopulation.value) return
  verifyLoading.value = true
  try {
    const res = await api.get(`/analytics/domain/${meta.value.domain}/verify-ledger`)
    verifyRows.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载校核台账失败')
  } finally {
    verifyLoading.value = false
  }
}

async function loadServiceContracts() {
  if (!isPopulation.value) return
  serviceLoading.value = true
  try {
    const res = await api.get(`/analytics/domain/${meta.value.domain}/services`)
    serviceContracts.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载服务契约失败')
  } finally {
    serviceLoading.value = false
  }
}

async function loadBatchLedger() {
  if (!isPopulation.value) return
  batchLoading.value = true
  try {
    const res = await api.get(`/analytics/domain/${meta.value.domain}/batch-ledger`)
    batchRows.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载批量台账失败')
  } finally {
    batchLoading.value = false
  }
}

async function loadStorageSummary() {
  if (!isPopulation.value) return
  storageLoading.value = true
  try {
    const res = await api.get(`/analytics/domain/${meta.value.domain}/storage-summary`)
    storageSummary.value = res.data || null
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载存储摘要失败')
  } finally {
    storageLoading.value = false
  }
}

async function loadAccessOverview() {
  if (!isPopulation.value) return
  try {
    const res = await api.get('/system/access/overview')
    accessOverview.value = res.data || null
  } catch {
    accessOverview.value = null
  }
}

async function createVerifyRow() {
  if (!verifyForm.value.sceneCode || !verifyForm.value.sceneName) {
    ElMessage.warning('请填写场景编码与名称')
    return
  }
  await api.post(`/analytics/domain/${meta.value.domain}/verify-ledger`, { ...verifyForm.value })
  ElMessage.success('已登记校核台账')
  verifyDialog.value = false
  await loadVerifyLedger()
}

async function createBatchRow() {
  await api.post(`/analytics/domain/${meta.value.domain}/batch-ledger`, {
    batchCode: batchForm.value.batchCode || undefined,
    tableName: batchForm.value.tableName,
    rowLimit: batchForm.value.rowLimit,
    message: batchForm.value.message || 'LEDGER 登记',
    batchStatus: 'OPEN',
  })
  ElMessage.success('已登记批量台账')
  batchDialog.value = false
  await loadBatchLedger()
}

async function setBatchStatus(row: BatchLedgerRow, batchStatus: string) {
  await api.put(`/analytics/domain/batch-ledger/${row.id}/status`, { batchStatus })
  ElMessage.success('批次状态已更新')
  await loadBatchLedger()
}

async function setVerifyFeedback(row: VerifyLedgerRow, feedbackStatus: string) {
  await api.put(`/analytics/domain/verify-ledger/${row.id}/feedback`, { feedbackStatus })
  ElMessage.success('反馈状态已更新')
  await loadVerifyLedger()
}

async function invokeContract(row: ServiceContract) {
  let body: Record<string, unknown> = {}
  try {
    body = row.requestSample ? JSON.parse(row.requestSample) : {}
  } catch {
    body = {}
  }
  const res = await api.post(`/analytics/domain/${meta.value.domain}/services/${row.serviceCode}/invoke`, body)
  invokeResult.value = JSON.stringify(res.data, null, 2)
  ElMessage.success('LEDGER 试调完成')
  if (row.mode === 'BATCH') await loadBatchLedger()
}

async function loadCurrentView() {
  iframeSrc.value = ''
  embedMode.value = ''
  embedMessage.value = ''
  embedUrl.value = ''
  if (activeZone.value) {
    await loadBindings()
    if (isShare.value || shareTab.value === 'indicators' || shareTab.value === 'models') {
      await loadDesigner()
    }
    if (isPopulation.value && isShare.value && shareTab.value === 'api') {
      await Promise.all([loadServiceContracts(), loadBatchLedger()])
    }
    if (isPopulation.value && isGovern.value && governTab.value === 'verify') {
      await loadVerifyLedger()
    }
    if (isPopulation.value && isCore.value && coreTab.value === 'storage') {
      await loadStorageSummary()
    }
    if (isPopulation.value && isInternal.value && internalTab.value === 'auth') {
      await loadAccessOverview()
    }
  } else if (isDesignerOnly.value) {
    await loadDesigner()
  }
}

function resetCollectWizard() {
  bindStep.value = 1
  bindDimGroup.value = ''
  bindAccessMode.value = ''
  selectedCandidate.value = null
  odsSources.value = []
  odsSourceId.value = null
  odsTableKeyword.value = ''
  odsTablesAll.value = []
  odsTablePage.value = 1
  selectedOdsTable.value = null
  docKeyword.value = ''
  docCategoryCode.value = ''
  docPublishStatus.value = ''
  docsAll.value = []
  docPage.value = 1
  selectedDoc.value = null
  channelKeyword.value = ''
  channelsAll.value = []
  channelPage.value = 1
  selectedChannel.value = null
}

function collectOdsCategoryIds(nodes: CategoryNode[], out: Set<number>) {
  for (const n of nodes) {
    const code = String(n.categoryCode || '')
    const layer = String(n.layerCode || '')
    const label = String(n.label || '')
    if (layer === 'ODS' || /ODS|贴源/i.test(code) || /ODS|贴源/i.test(label)) {
      out.add(n.id)
    }
    if (n.children?.length) collectOdsCategoryIds(n.children, out)
  }
}

async function loadOdsSources() {
  const tree = ((await api.get('/governance/platform/metadata/source-categories/tree')).data || []) as CategoryNode[]
  const odsIds = new Set<number>()
  collectOdsCategoryIds(tree, odsIds)
  const all = ((await api.get('/governance/platform/metadata/data-sources')).data || []) as MetaSourceOption[]
  let filtered = odsIds.size
    ? all.filter((s) => s.categoryId != null && odsIds.has(s.categoryId))
    : all.filter((s) => /ODS|贴源/i.test(String(s.categoryName || '')))
  if (!filtered.length) filtered = all
  odsSources.value = filtered
  if (!odsSourceId.value && filtered.length) {
    odsSourceId.value = filtered[0].id
  }
}

async function loadOdsTables() {
  selectedOdsTable.value = null
  odsTablesAll.value = []
  odsTablePage.value = 1
  const id = odsSourceId.value
  if (id == null || id <= 0) return
  bindObjectLoading.value = true
  try {
    const src = odsSources.value.find((s) => s.id === id)
    const rows = ((await api.get(`/governance/platform/metadata/collect/meta-data-sources/${id}/tables`)).data || []) as Array<{
      sourceTable?: string
      tableName?: string
    }>
    odsTablesAll.value = rows
      .map((r) => String(r.sourceTable || r.tableName || '').trim())
      .filter(Boolean)
      .map((tableName) => ({
        tableName,
        sourceName: src?.sourceName || '—',
        dataLayer: 'ODS',
      }))
  } catch {
    odsTablesAll.value = []
    ElMessage.error('加载 ODS 表失败')
  } finally {
    bindObjectLoading.value = false
  }
}

async function loadDocsForBind() {
  selectedDoc.value = null
  docPage.value = 1
  bindObjectLoading.value = true
  try {
    if (!docCategories.value.length) {
      docCategories.value = ((await api.get('/unstructured/platform/categories')).data || []).map((c: Record<string, unknown>) => ({
        categoryCode: String(c.categoryCode || ''),
        categoryName: String(c.categoryName || c.name || c.categoryCode || ''),
      }))
    }
    docsAll.value = ((await api.get('/unstructured/platform/documents', {
      params: {
        keyword: docKeyword.value.trim() || undefined,
        categoryCode: docCategoryCode.value || undefined,
        publishStatus: docPublishStatus.value || undefined,
      },
    })).data || []) as DocRow[]
  } catch {
    docsAll.value = []
    ElMessage.error('加载文件资源失败')
  } finally {
    bindObjectLoading.value = false
  }
}

async function loadChannelsForBind(type: 'API' | 'CDC') {
  selectedChannel.value = null
  channelPage.value = 1
  channelKeyword.value = ''
  bindObjectLoading.value = true
  try {
    channelsAll.value = ((await ingestionApi.channels(type)).data || []) as ChannelRow[]
  } catch {
    channelsAll.value = []
    ElMessage.error(`加载${type}通道失败`)
  } finally {
    bindObjectLoading.value = false
  }
}

async function openBindDialog() {
  if (!activeZone.value) return
  selectedCandidate.value = null
  if (isCollect.value) {
    collectWizard.value = true
    resetCollectWizard()
    bindDialog.value = true
    return
  }
  collectWizard.value = false
  await loadBindings(true)
  bindDialog.value = true
}

function onSelectDimGroup(g: 'DATATYPE' | 'LATENCY') {
  bindDimGroup.value = g
  bindAccessMode.value = ''
}

function onSelectAccessMode(m: 'STRUCT' | 'UNSTRUCT' | 'API' | 'CDC') {
  bindAccessMode.value = m
}

async function bindWizardNext() {
  if (bindStep.value === 1) {
    if (!bindDimGroup.value) {
      ElMessage.warning('请选择数据类型或数据时效性')
      return
    }
    bindStep.value = 2
    return
  }
  if (bindStep.value === 2) {
    if (!bindAccessMode.value) {
      ElMessage.warning('请选择接入方式')
      return
    }
    if (bindDimGroup.value === 'DATATYPE' && bindAccessMode.value !== 'STRUCT' && bindAccessMode.value !== 'UNSTRUCT') {
      ElMessage.warning('数据类型下请选择结构化或非结构化接入')
      return
    }
    if (bindDimGroup.value === 'LATENCY' && bindAccessMode.value !== 'API' && bindAccessMode.value !== 'CDC') {
      ElMessage.warning('数据时效性下请选择 API 或 CDC')
      return
    }
    bindStep.value = 3
    if (bindAccessMode.value === 'STRUCT') {
      await loadOdsSources()
      await loadOdsTables()
    } else if (bindAccessMode.value === 'UNSTRUCT') {
      await loadDocsForBind()
    } else if (bindAccessMode.value === 'API' || bindAccessMode.value === 'CDC') {
      await loadChannelsForBind(bindAccessMode.value)
    }
  }
}

function bindWizardPrev() {
  if (bindStep.value === 3) {
    selectedOdsTable.value = null
    selectedDoc.value = null
    selectedChannel.value = null
    bindStep.value = 2
    return
  }
  if (bindStep.value === 2) {
    bindAccessMode.value = ''
    bindStep.value = 1
  }
}

async function confirmBind() {
  if (!activeZone.value) return
  if (collectWizard.value) {
    await confirmCollectBind()
    return
  }
  if (!selectedCandidate.value) {
    ElMessage.warning('请选择候选资产')
    return
  }
  const c = selectedCandidate.value
  await api.post(`/analytics/domain/${meta.value.domain}/zones/${activeZone.value.zoneCode}/bindings`, {
    assetType: c.assetType,
    assetRef: c.assetRef,
    assetName: c.assetName,
    physicalTable: c.physicalTable,
    metaEntryCode: c.metaEntryCode,
    dataLayer: c.dataLayer,
  })
  ElMessage.success('已挂载')
  bindDialog.value = false
  await loadBindings(true)
}

async function confirmCollectBind() {
  if (!activeZone.value || !bindDimGroup.value || !bindAccessMode.value) return
  let body: Record<string, unknown> | null = null
  if (bindAccessMode.value === 'STRUCT' && selectedOdsTable.value) {
    const t = selectedOdsTable.value
    const sid = odsSourceId.value
    body = {
      assetType: 'METADATA',
      assetRef: `MDS_${sid}_${t.tableName}`,
      assetName: t.tableName,
      physicalTable: t.tableName,
      metaEntryCode: sid != null ? `MDS_${sid}` : undefined,
      dataLayer: 'ODS',
      dimGroup: 'DATATYPE',
      accessMode: 'STRUCT',
    }
  } else if (bindAccessMode.value === 'UNSTRUCT' && selectedDoc.value) {
    const d = selectedDoc.value
    body = {
      assetType: 'DOCUMENT',
      assetRef: String(d.id),
      assetName: d.title || String(d.id),
      physicalTable: undefined,
      dataLayer: 'UNSTRUCT',
      dimGroup: 'DATATYPE',
      accessMode: 'UNSTRUCT',
      remark: d.categoryName || d.categoryCode || undefined,
    }
  } else if ((bindAccessMode.value === 'API' || bindAccessMode.value === 'CDC') && selectedChannel.value) {
    const ch = selectedChannel.value
    body = {
      assetType: bindAccessMode.value === 'API' ? 'CHANNEL_API' : 'CHANNEL_CDC',
      assetRef: ch.channelCode,
      assetName: ch.channelName || ch.channelCode,
      physicalTable: undefined,
      dataLayer: bindAccessMode.value,
      dimGroup: 'LATENCY',
      accessMode: bindAccessMode.value,
      remark: ch.status,
    }
  }
  if (!body) {
    ElMessage.warning('请选择要挂载的对象')
    return
  }
  await api.post(`/analytics/domain/${meta.value.domain}/zones/${activeZone.value.zoneCode}/bindings`, body)
  ElMessage.success('已挂载')
  bindDialog.value = false
  await loadBindings(true)
}

async function unbind(row: Binding) {
  await ElMessageBox.confirm(`确认解除挂载「${row.assetName}」？`, '解除挂载', { type: 'warning' })
  await api.delete(`/analytics/domain/bindings/${row.id}`)
  ElMessage.success('已解除')
  await loadBindings(true)
}

function onIndicatorsRefreshed() {
  void loadDesigner(true)
}

function openPortalPreview() {
  if (embedUrl.value) window.open(embedUrl.value, '_blank')
}

function openModelDesign(row: AnalysisModel) {
  editingModel.value = row
  modelForm.value = {
    modelName: row.modelName || '',
    deDashboardId: row.deDashboardId || '',
    dimensionJson: row.dimensionJson || '',
    description: row.description || '',
    indicatorIds: (row.indicators || []).map((i) => i.id),
  }
  iframeSrc.value = ''
  embedMode.value = ''
  embedMessage.value = ''
  embedUrl.value = ''
  modelSamples.value = []
  modelDrawer.value = true
  if (isPopulation.value) {
    void loadModelSamples(row.id)
  }
}

async function saveModelDesign() {
  if (!editingModel.value) return
  const body: Record<string, unknown> = {
    modelName: modelForm.value.modelName,
    dimensionJson: modelForm.value.dimensionJson,
    description: modelForm.value.description,
    indicatorIds: modelForm.value.indicatorIds,
  }
  if (!isPopulation.value) {
    body.deDashboardId = modelForm.value.deDashboardId
  }
  await api.put(`/analytics/domain/models/${editingModel.value.id}`, body)
  ElMessage.success('模型设计已保存')
  await loadDesigner(true)
  const fresh = models.value.find((m) => m.id === editingModel.value?.id)
  if (fresh) editingModel.value = fresh
}

async function issueModelEmbed() {
  if (!editingModel.value || isPopulation.value) return
  try {
    const res = await api.post(`/analytics/domain/models/${editingModel.value.id}/embed-token`, {})
    embedUrl.value = (res.data.embedUrl as string) || ''
    embedMode.value = (res.data.mode as 'LIVE' | 'LEDGER') || 'LEDGER'
    embedMessage.value = String(res.data.message || '')
    const deUrl = res.data.dataeaseUrl as string | null
    if (embedMode.value === 'LIVE' && deUrl) {
      iframeSrc.value = deUrl
      ElMessage.success(embedMessage.value || '嵌入已加载')
    } else {
      iframeSrc.value = ''
      ElMessage.warning(embedMessage.value || 'DataEase 未就绪')
    }
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '签发失败')
  }
}

function onHubSelect(key: string) {
  activeNav.value = key
  if (key.endsWith('share')) {
    shareTab.value = 'mount'
    indicatorLibTab.value = 'domains'
  }
  if (key.endsWith('govern')) governTab.value = 'mount'
  if (key.endsWith('collect')) collectTab.value = 'mount'
  if (key.endsWith('core')) coreTab.value = 'mount'
  if (key.endsWith('internal')) internalTab.value = 'mount'
  if (!applyingRoute) syncQuery()
  loadCurrentView()
}

watch(() => route.path, async () => {
  activeNav.value = ''
  zoneLoaded.value = ''
  designerLoaded.value = false
  bindings.value = []
  indicators.value = []
  models.value = []
  await loadOverviewLite()
  resolveFromRoute()
  await loadCurrentView()
})

watch(() => route.query.tab, async () => {
  if (applyingRoute) return
  resolveFromRoute()
  await loadCurrentView()
})

watch(shareTab, async (t) => {
  if ((t === 'indicators' || t === 'models' || t === 'mount') && (isShare.value || isDesignerOnly.value)) {
    if (t === 'models' || t === 'indicators') await loadDesigner()
    if (t === 'mount' && activeZone.value) await loadBindings()
  }
  if (t === 'api' && isPopulation.value && isShare.value) {
    await Promise.all([loadServiceContracts(), loadBatchLedger()])
  }
})

watch(governTab, async (t) => {
  if (!isPopulation.value || !isGovern.value) return
  if (t === 'mount') await loadBindings()
  if (t === 'verify') await loadVerifyLedger()
})

watch(collectTab, async (t) => {
  if (!isPopulation.value || !isCollect.value) return
  if (t === 'mount') await loadBindings()
})

watch(coreTab, async (t) => {
  if (!isPopulation.value || !isCore.value) return
  if (t === 'mount') await loadBindings()
  if (t === 'storage') await loadStorageSummary()
})

watch(internalTab, async (t) => {
  if (!isPopulation.value || !isInternal.value) return
  if (t === 'mount') await loadBindings()
  if (t === 'auth') await loadAccessOverview()
})

onMounted(async () => {
  try {
    await loadOverviewLite()
    resolveFromRoute()
    if (!applyingRoute && activeNav.value && !route.query.tab) syncQuery()
    await loadCurrentView()
  } catch {
    ElMessage.error('加载失败')
  }
})
</script>

<template>
  <div class="ana-hub-root">
    <HubSideLayout v-model="activeNav" :groups="navGroups" @select="onHubSelect">
      <!-- 五区：资产挂载设计 -->
      <PageCard v-if="activeZone && !isShare" :title="pageTitle">
        <el-alert
          type="info"
          :closable="false"
          style="margin-bottom:12px"
          :title="zoneAlertTitle"
        />
        <div class="dim-grid">
          <div v-for="d in zoneDims" :key="d.key" class="dim-item">
            <div class="dim-key">{{ d.key }}</div>
            <div class="dim-tip">{{ d.tip }}</div>
          </div>
        </div>

        <template v-if="isPopulation && isGovern">
          <el-tabs v-model="governTab">
            <el-tab-pane label="资产挂载" name="mount">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openBindDialog">选型挂载</el-button>
                  <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
                  <el-button @click="loadBindings(true)">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table :data="bindings" stripe size="small" empty-text="尚未挂载资产，请从候选中选型">
                <el-table-column prop="assetName" label="资产名称" min-width="160" />
                <el-table-column label="类型" width="100">
                  <template #default="{ row }">{{ assetTypeLabel(row.assetType) }}</template>
                </el-table-column>
                <el-table-column prop="physicalTable" label="物理表" min-width="140" show-overflow-tooltip />
                <el-table-column prop="dataLayer" label="分层" width="90" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="挂载时间" width="170" />
                <el-table-column label="操作" width="90">
                  <template #default="{ row }">
                    <el-button link type="danger" @click="unbind(row)">解除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="更新/校核机制" name="mechanism">
              <div class="mech-grid">
                <div v-for="c in GOVERN_MECHANISM_CARDS" :key="c.code" class="mech-card">
                  <div class="mech-title"><el-tag size="small" type="warning">{{ c.code }}</el-tag> {{ c.title }}</div>
                  <ul class="mech-list">
                    <li v-for="(p, i) in c.points" :key="i">{{ p }}</li>
                  </ul>
                </div>
              </div>
              <el-button type="primary" @click="$router.push('/governance')">打开数据治理</el-button>
            </el-tab-pane>
            <el-tab-pane label="校核台账" name="verify">
              <p class="hint">M155/M156 设计台账（LEDGER）：登记问题与反馈状态，不在此运行真实校核引擎。</p>
              <el-form inline class="portal-inline-form">
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="verifyDialog = true">登记台账</el-button>
                  <el-button :loading="verifyLoading" @click="loadVerifyLedger">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table v-loading="verifyLoading" :data="verifyRows" stripe size="small" empty-text="暂无校核台账">
                <el-table-column prop="mCode" label="模块" width="80" />
                <el-table-column prop="sceneName" label="场景" min-width="140" />
                <el-table-column prop="checkType" label="类型" width="120" />
                <el-table-column prop="sourceDept" label="来源部门" width="120" />
                <el-table-column prop="issueSummary" label="问题摘要" min-width="180" show-overflow-tooltip />
                <el-table-column prop="feedbackStatus" label="反馈" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.feedbackStatus === 'CLOSED' ? 'success' : row.feedbackStatus === 'FEEDBACK' ? 'warning' : 'info'">
                      {{ row.feedbackStatus }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="setVerifyFeedback(row, 'FEEDBACK')">已反馈</el-button>
                    <el-button link type="success" @click="setVerifyFeedback(row, 'CLOSED')">关闭</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>

        <template v-else-if="isPopulation && isCollect">
          <el-tabs v-model="collectTab">
            <el-tab-pane label="资产挂载" name="mount">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openBindDialog">选型挂载</el-button>
                  <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
                  <el-button @click="loadBindings(true)">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table :data="bindings" stripe size="small" empty-text="尚未挂载资产，请从候选中选型">
                <el-table-column prop="assetName" label="资产名称" min-width="160" />
                <el-table-column label="维度分组" width="110">
                  <template #default="{ row }">{{ dimGroupLabel(row.dimGroup) }}</template>
                </el-table-column>
                <el-table-column label="接入方式" width="140">
                  <template #default="{ row }">{{ accessModeLabel(row.accessMode) }}</template>
                </el-table-column>
                <el-table-column label="来源类型" width="110">
                  <template #default="{ row }">{{ assetTypeLabel(row.assetType) }}</template>
                </el-table-column>
                <el-table-column prop="physicalTable" label="物理表/引用" min-width="140" show-overflow-tooltip />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="挂载时间" width="170" />
                <el-table-column label="操作" width="90">
                  <template #default="{ row }">
                    <el-button link type="danger" @click="unbind(row)">解除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="多源通道清单" name="sources">
              <p class="hint">采集区通道设计清单（演示）：真正抽数在数据归集（Kettle + DS），本页不伪造调度成功。</p>
              <el-table :data="COLLECT_SOURCE_CHANNELS" stripe size="small">
                <el-table-column prop="dept" label="来源部门" width="120" />
                <el-table-column prop="channel" label="通道类型" width="120" />
                <el-table-column prop="latency" label="时效" width="140" />
                <el-table-column prop="remark" label="说明" min-width="180" />
              </el-table>
              <el-button type="primary" style="margin-top:12px" @click="$router.push('/exchange/ingestion')">打开数据归集</el-button>
            </el-tab-pane>
          </el-tabs>
        </template>

        <template v-else-if="isPopulation && isCore">
          <el-tabs v-model="coreTab">
            <el-tab-pane label="资产挂载" name="mount">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openBindDialog">选型挂载</el-button>
                  <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
                  <el-button @click="loadBindings(true)">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table :data="bindings" stripe size="small" empty-text="尚未挂载资产，请从候选中选型">
                <el-table-column prop="assetName" label="资产名称" min-width="160" />
                <el-table-column label="类型" width="100">
                  <template #default="{ row }">{{ assetTypeLabel(row.assetType) }}</template>
                </el-table-column>
                <el-table-column prop="physicalTable" label="物理表" min-width="140" show-overflow-tooltip />
                <el-table-column prop="dataLayer" label="分层" width="90" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="挂载时间" width="170" />
                <el-table-column label="操作" width="90">
                  <template #default="{ row }">
                    <el-button link type="danger" @click="unbind(row)">解除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="存储/分区" name="storage">
              <div class="mech-grid">
                <div v-for="c in STORAGE_DESIGN_CARDS" :key="c.code" class="mech-card">
                  <div class="mech-title"><el-tag size="small" type="success">{{ c.code }}</el-tag> {{ c.title }}</div>
                  <ul class="mech-list">
                    <li v-for="(p, i) in c.points" :key="i">{{ p }}</li>
                  </ul>
                </div>
              </div>
              <p class="hint">{{ String(storageSummary?.message || '预检通过 ≠ 已物理分区') }}</p>
              <el-button :loading="storageLoading" @click="loadStorageSummary">刷新摘要</el-button>
              <el-button type="primary" @click="$router.push('/resource-center')">打开资源中心</el-button>
              <h4 class="sub-title">纳管表</h4>
              <el-table :data="storageManaged" stripe size="small" empty-text="暂无（需 V190）">
                <el-table-column prop="physicalTable" label="物理表" min-width="160" />
                <el-table-column prop="metaEntryCode" label="元数据编码" min-width="160" />
                <el-table-column prop="recordCount" label="行数" width="90" />
                <el-table-column prop="status" label="状态" width="90" />
              </el-table>
              <h4 class="sub-title">分区策略</h4>
              <el-table :data="storagePartitions" stripe size="small" empty-text="暂无分区策略">
                <el-table-column prop="partitionCode" label="编码" width="140" />
                <el-table-column prop="partitionName" label="名称" min-width="140" />
                <el-table-column prop="tableName" label="表" min-width="140" />
                <el-table-column prop="partitionColumn" label="分区列" width="110" />
                <el-table-column prop="pretestStatus" label="预检" width="90" />
                <el-table-column prop="pretestMessage" label="预检说明" min-width="180" show-overflow-tooltip />
              </el-table>
              <h4 class="sub-title">运维计划（LEDGER）</h4>
              <el-table :data="storageOps" stripe size="small" empty-text="暂无运维计划">
                <el-table-column prop="physicalTable" label="表" min-width="140" />
                <el-table-column prop="opType" label="类型" width="120" />
                <el-table-column prop="opStatus" label="状态" width="100" />
                <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>

        <template v-else-if="isPopulation && isInternal">
          <el-tabs v-model="internalTab">
            <el-tab-pane label="资产挂载" name="mount">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openBindDialog">选型挂载</el-button>
                  <el-button @click="$router.push('/resource-center')">打开资源中心</el-button>
                  <el-button @click="loadBindings(true)">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table :data="bindings" stripe size="small" empty-text="尚未挂载资产，请从候选中选型">
                <el-table-column prop="assetName" label="资产名称" min-width="160" />
                <el-table-column label="类型" width="100">
                  <template #default="{ row }">{{ assetTypeLabel(row.assetType) }}</template>
                </el-table-column>
                <el-table-column prop="physicalTable" label="物理表" min-width="140" show-overflow-tooltip />
                <el-table-column prop="dataLayer" label="分层" width="90" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createdAt" label="挂载时间" width="170" />
                <el-table-column label="操作" width="90">
                  <template #default="{ row }">
                    <el-button link type="danger" @click="unbind(row)">解除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="双重授权" name="auth">
              <div class="mech-grid">
                <div v-for="c in DUAL_AUTH_CARDS" :key="c.code" class="mech-card">
                  <div class="mech-title"><el-tag size="small" type="danger">{{ c.code }}</el-tag> {{ c.title }}</div>
                  <ul class="mech-list">
                    <li v-for="(p, i) in c.points" :key="i">{{ p }}</li>
                  </ul>
                </div>
              </div>
              <p class="hint">复用平台访问控制概览（只读）；授权操作请进入 M048。</p>
              <el-button type="primary" @click="$router.push(activeZone.deepLink)">打开双重授权(M048)</el-button>
              <el-button @click="loadAccessOverview">刷新概览</el-button>
              <el-descriptions v-if="accessOverview" :column="2" border size="small" style="margin-top:12px">
                <el-descriptions-item v-for="(v, k) in accessOverview" :key="String(k)" :label="String(k)">
                  {{ typeof v === 'object' ? JSON.stringify(v) : v }}
                </el-descriptions-item>
              </el-descriptions>
              <el-empty v-else description="暂无概览（需登录且访问控制可用）" />
            </el-tab-pane>
          </el-tabs>
        </template>

        <template v-else>
          <el-form inline class="portal-inline-form portal-inline-form--block">
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="openBindDialog">选型挂载</el-button>
              <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
              <el-button @click="loadBindings(true)">刷新</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="bindings" stripe size="small" empty-text="尚未挂载资产，请从候选中选型">
            <el-table-column prop="assetName" label="资产名称" min-width="160" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ assetTypeLabel(row.assetType) }}</template>
            </el-table-column>
            <el-table-column prop="physicalTable" label="物理表" min-width="140" show-overflow-tooltip />
            <el-table-column prop="dataLayer" label="分层" width="90" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="挂载时间" width="170" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="danger" @click="unbind(row)">解除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </PageCard>

      <!-- 共享服务区：挂载 + 指标 + 模型 -->
      <PageCard v-else-if="activeZone && isShare" :title="pageTitle">
        <el-alert
          v-if="isPopulation"
          type="info"
          :closable="false"
          style="margin-bottom:12px"
          :title="zoneAlertTitle"
        />
        <el-alert
          v-else
          :type="dataEaseHealthy ? 'success' : 'warning'"
          :closable="false"
          style="margin-bottom:12px"
          :title="dataEaseHealthy
            ? '共享服务区：目录/接口深链 + 指标库 + 分析模型列表；DataEase 在线可实时嵌入'
            : '共享服务区设计器可用；DataEase 离线时模型预览仅为台账（LEDGER）'"
        />
        <div v-if="isPopulation" class="dim-grid" style="margin-bottom:12px">
          <div v-for="d in zoneDims" :key="d.key" class="dim-item">
            <div class="dim-key">{{ d.key }}</div>
            <div class="dim-tip">{{ d.tip }}</div>
          </div>
        </div>
        <el-tabs v-model="shareTab">
          <el-tab-pane label="资产挂载" name="mount">
            <el-form inline class="portal-inline-form">
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="openBindDialog">选型挂载目录资源</el-button>
                <el-button @click="loadBindings(true)">刷新</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="bindings" stripe size="small" empty-text="可挂载已编目资源">
              <el-table-column prop="assetName" label="资源名称" min-width="160" />
              <el-table-column prop="assetRef" label="资源编码" min-width="120" />
              <el-table-column prop="physicalTable" label="物理表" min-width="120" show-overflow-tooltip />
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button link type="danger" @click="unbind(row)">解除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="目录深链" name="catalog">
            <p class="hint">共享目录编制、审批与门户在数据目录管理系统完成；此处仅作区设计入口（M154）。</p>
            <el-button type="primary" @click="$router.push('/catalog')">打开资源目录</el-button>
          </el-tab-pane>
          <el-tab-pane label="接口/批量" name="api">
            <template v-if="isPopulation">
              <div class="mech-grid">
                <div v-for="c in SHARE_SERVICE_CARDS" :key="c.code" class="mech-card">
                  <div class="mech-title"><el-tag size="small">{{ c.code }}</el-tag> {{ c.title }}</div>
                  <ul class="mech-list">
                    <li v-for="(p, i) in c.points" :key="i">{{ p }}</li>
                  </ul>
                </div>
              </div>
              <h4 class="sub-title">M159 接口契约（LEDGER 试调）</h4>
              <el-form inline class="portal-inline-form">
                <el-form-item class="portal-form-actions">
                  <el-button :loading="serviceLoading" @click="loadServiceContracts">刷新契约</el-button>
                  <el-button type="primary" @click="$router.push('/exchange/esb')">打开接口交换</el-button>
                </el-form-item>
              </el-form>
              <el-table v-loading="serviceLoading" :data="apiContracts" stripe size="small" empty-text="暂无接口契约（需 V190）">
                <el-table-column prop="serviceName" label="服务名称" min-width="160" />
                <el-table-column prop="serviceCode" label="编码" min-width="140" />
                <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="invokeContract(row)">试调</el-button>
                  </template>
                </el-table-column>
              </el-table>

              <h4 class="sub-title">M160 批量交换台账</h4>
              <el-form inline class="portal-inline-form">
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="batchDialog = true">登记批次</el-button>
                  <el-button :loading="batchLoading" @click="loadBatchLedger">刷新批次</el-button>
                  <el-button @click="$router.push('/exchange/application')">打开应用平台</el-button>
                </el-form-item>
              </el-form>
              <el-table v-loading="serviceLoading" :data="batchContracts" stripe size="small" empty-text="暂无批量契约" style="margin-bottom:8px">
                <el-table-column prop="serviceName" label="批量服务" min-width="160" />
                <el-table-column prop="serviceCode" label="编码" min-width="140" />
                <el-table-column label="操作" width="120">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="invokeContract(row)">试调登记</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-table v-loading="batchLoading" :data="batchRows" stripe size="small" empty-text="暂无批量台账（需 V191）">
                <el-table-column prop="batchCode" label="批次号" min-width="160" />
                <el-table-column prop="tableName" label="表" min-width="140" />
                <el-table-column prop="rowLimit" label="行上限" width="90" />
                <el-table-column prop="batchStatus" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.batchStatus === 'DONE' ? 'success' : row.batchStatus === 'FAILED' ? 'danger' : 'warning'">
                      {{ row.batchStatus }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
                <el-table-column label="操作" width="220">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="setBatchStatus(row, 'ACCEPTED')">受理</el-button>
                    <el-button link type="success" @click="setBatchStatus(row, 'DONE')">完成</el-button>
                    <el-button link type="danger" @click="setBatchStatus(row, 'FAILED')">失败</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-input
                v-if="invokeResult"
                v-model="invokeResult"
                type="textarea"
                :rows="8"
                readonly
                style="margin-top:12px"
              />
            </template>
            <template v-else>
              <p class="hint">接口交换与批量共享走共享交换平台能力，不在分析域平行实现。</p>
              <el-button type="primary" @click="$router.push('/exchange/esb')">打开接口交换</el-button>
              <el-button @click="$router.push('/exchange/application')">打开应用平台</el-button>
            </template>
          </el-tab-pane>
          <el-tab-pane label="指标库" name="indicators">
            <el-tabs v-if="isPopulation" v-model="indicatorLibTab" class="indicator-lib-tabs">
              <el-tab-pane label="指标域管理" name="domains" lazy>
                <DomainIndicatorDomainManage
                  :domain="meta.domain"
                  @changed="indicatorDomainTick++"
                />
              </el-tab-pane>
              <el-tab-pane label="指标组管理" name="groups" lazy>
                <DomainIndicatorGroupManage
                  :key="`groups-${indicatorDomainTick}`"
                  :domain="meta.domain"
                  :active="indicatorLibTab === 'groups'"
                />
              </el-tab-pane>
              <el-tab-pane label="指标任务" name="tasks" lazy>
                <DomainIndicatorTaskPanel :domain="meta.domain" />
              </el-tab-pane>
            </el-tabs>
            <DomainIndicatorSqlLibrary
              v-else
              :domain="meta.domain"
              @refreshed="onIndicatorsRefreshed"
            />
          </el-tab-pane>
          <el-tab-pane label="分析模型" name="models">
            <p class="hint">
              {{ isPopulation
                ? '分析模型 = 场景包（多指标 + 维度 + 自研结果表）。指标 ≠ 模型。'
                : '分析模型 = 场景包（多指标 + 维度 + 看板）。指标 ≠ 模型。' }}
            </p>
            <el-table
              :data="models"
              stripe
              size="small"
              empty-text="暂无模型"
              :row-class-name="modelRowClassName"
            >
              <el-table-column prop="modelName" label="模型名称" min-width="180" />
              <el-table-column prop="modelCode" label="编码" width="120" />
              <el-table-column label="关联指标" min-width="160">
                <template #default="{ row }">
                  {{ (row.indicators || []).map((i: Indicator) => i.indicatorName).join('、') || '—' }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openModelDesign(row)">设计</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <!-- 宏观/重点：无五区，直接设计器 -->
      <PageCard v-else-if="isDesignerOnly" :title="pageTitle">
        <el-tabs v-model="shareTab">
          <el-tab-pane label="指标库" name="indicators">
            <DomainIndicatorSqlLibrary
              :domain="meta.domain"
              @refreshed="onIndicatorsRefreshed"
            />
          </el-tab-pane>
          <el-tab-pane label="分析模型" name="models">
            <el-table :data="models" stripe size="small">
              <el-table-column prop="modelName" label="模型名称" min-width="180" />
              <el-table-column label="关联指标" min-width="160">
                <template #default="{ row }">
                  {{ (row.indicators || []).map((i: Indicator) => i.indicatorName).join('、') || '—' }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openModelDesign(row)">设计</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <PageCard v-else :title="pageTitle">
        <el-empty description="请从左侧选择数据区" />
      </PageCard>
    </HubSideLayout>

    <el-dialog v-model="batchDialog" title="登记批量交换台账" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="批次号"><el-input v-model="batchForm.batchCode" placeholder="可空，自动生成" /></el-form-item>
        <el-form-item label="物理表"><el-input v-model="batchForm.tableName" /></el-form-item>
        <el-form-item label="行上限"><el-input-number v-model="batchForm.rowLimit" :min="1" :max="1000000" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="batchForm.message" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialog = false">取消</el-button>
        <el-button type="primary" @click="createBatchRow">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verifyDialog" title="登记校核/更新台账" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="模块">
          <el-select v-model="verifyForm.mCode" style="width:100%">
            <el-option label="M155 更新维护" value="M155" />
            <el-option label="M156 信息校核" value="M156" />
          </el-select>
        </el-form-item>
        <el-form-item label="场景编码"><el-input v-model="verifyForm.sceneCode" /></el-form-item>
        <el-form-item label="场景名称"><el-input v-model="verifyForm.sceneName" /></el-form-item>
        <el-form-item label="校核类型">
          <el-select v-model="verifyForm.checkType" style="width:100%">
            <el-option label="MULTI_SOURCE" value="MULTI_SOURCE" />
            <el-option label="BASELINE" value="BASELINE" />
            <el-option label="UPDATE" value="UPDATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源部门"><el-input v-model="verifyForm.sourceDept" /></el-form-item>
        <el-form-item label="问题摘要"><el-input v-model="verifyForm.issueSummary" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="verifyDialog = false">取消</el-button>
        <el-button type="primary" @click="createVerifyRow">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="bindDialog"
      :title="collectWizard ? '选型挂载' : '从现有资产选型挂载'"
      :width="collectWizard ? '760px' : '720px'"
      destroy-on-close
    >
      <template v-if="collectWizard">
        <el-steps :active="bindStepActive" finish-status="success" align-center style="margin-bottom:16px">
          <el-step title="选维度分组" />
          <el-step title="选接入方式" />
          <el-step title="选择对象" />
        </el-steps>
        <div v-if="bindBreadcrumb" class="bind-crumb">{{ bindBreadcrumb }}</div>

        <div v-if="bindStep === 1" class="bind-card-grid">
          <div
            class="bind-card"
            :class="{ active: bindDimGroup === 'DATATYPE' }"
            @click="onSelectDimGroup('DATATYPE')"
          >
            <div class="bind-card-title">数据类型</div>
            <div class="bind-card-desc">结构化 / 非结构化接入</div>
          </div>
          <div
            class="bind-card"
            :class="{ active: bindDimGroup === 'LATENCY' }"
            @click="onSelectDimGroup('LATENCY')"
          >
            <div class="bind-card-title">数据时效性</div>
            <div class="bind-card-desc">API / CDC 实时接入</div>
          </div>
        </div>

        <div v-else-if="bindStep === 2 && bindDimGroup === 'DATATYPE'" class="bind-card-grid">
          <div
            class="bind-card"
            :class="{ active: bindAccessMode === 'STRUCT' }"
            @click="onSelectAccessMode('STRUCT')"
          >
            <div class="bind-card-title">结构化数据接入</div>
            <div class="bind-card-desc">ODS 贴源表</div>
          </div>
          <div
            class="bind-card"
            :class="{ active: bindAccessMode === 'UNSTRUCT' }"
            @click="onSelectAccessMode('UNSTRUCT')"
          >
            <div class="bind-card-title">非结构化数据接入</div>
            <div class="bind-card-desc">文件资源管理</div>
          </div>
        </div>

        <div v-else-if="bindStep === 2 && bindDimGroup === 'LATENCY'" class="bind-card-grid">
          <div
            class="bind-card"
            :class="{ active: bindAccessMode === 'API' }"
            @click="onSelectAccessMode('API')"
          >
            <div class="bind-card-title">API 接口数据接入</div>
            <div class="bind-card-desc">归集 API 通道</div>
          </div>
          <div
            class="bind-card"
            :class="{ active: bindAccessMode === 'CDC' }"
            @click="onSelectAccessMode('CDC')"
          >
            <div class="bind-card-title">CDC 实时数据接入</div>
            <div class="bind-card-desc">归集 CDC 通道</div>
          </div>
        </div>

        <div v-else-if="bindStep === 3" v-loading="bindObjectLoading">
          <template v-if="bindAccessMode === 'STRUCT'">
            <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
              <el-form-item label="ODS数据源" class="portal-field-xl">
                <el-select
                  v-model="odsSourceId"
                  filterable
                  placeholder="选择数据源"
                  @change="loadOdsTables"
                >
                  <el-option
                    v-for="s in odsSources"
                    :key="s.id"
                    :label="s.sourceName"
                    :value="s.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="表名" class="portal-field-lg">
                <el-input v-model="odsTableKeyword" clearable placeholder="表名关键字" @keyup.enter="odsTablePage = 1" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="odsTablePage = 1">查询</el-button>
                <el-button @click="odsTableKeyword = ''; odsTablePage = 1">重置</el-button>
              </el-form-item>
            </el-form>
            <el-table
              :data="odsTablesPaged"
              stripe
              size="small"
              highlight-current-row
              max-height="320"
              empty-text="暂无表，请先选择 ODS 数据源"
              @current-change="(row: OdsTableRow | null) => { selectedOdsTable = row }"
            >
              <el-table-column prop="tableName" label="表名" min-width="160" />
              <el-table-column prop="sourceName" label="数据源" min-width="140" show-overflow-tooltip />
              <el-table-column prop="dataLayer" label="分层" width="80" />
            </el-table>
            <div class="bind-pager">
              <el-pagination
                v-model:current-page="odsTablePage"
                v-model:page-size="odsTablePageSize"
                layout="total, prev, pager, next"
                :total="odsTablesFiltered.length"
                small
              />
            </div>
          </template>

          <template v-else-if="bindAccessMode === 'UNSTRUCT'">
            <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
              <el-form-item label="关键词" class="portal-field-md">
                <el-input v-model="docKeyword" clearable />
              </el-form-item>
              <el-form-item label="分类" class="portal-field-md">
                <el-select v-model="docCategoryCode" clearable placeholder="全部">
                  <el-option
                    v-for="c in docCategories"
                    :key="c.categoryCode"
                    :label="c.categoryName"
                    :value="c.categoryCode"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="发布状态" class="portal-field-sm">
                <el-select v-model="docPublishStatus" clearable placeholder="全部">
                  <el-option label="已发布" value="PUBLISHED" />
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="已下线" value="OFFLINE" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="loadDocsForBind">查询</el-button>
                <el-button @click="docKeyword = ''; docCategoryCode = ''; docPublishStatus = ''; loadDocsForBind()">重置</el-button>
              </el-form-item>
            </el-form>
            <el-table
              :data="docsPaged"
              stripe
              size="small"
              highlight-current-row
              max-height="320"
              empty-text="暂无文件资源"
              @current-change="(row: DocRow | null) => { selectedDoc = row }"
            >
              <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
              <el-table-column label="分类" width="120">
                <template #default="{ row }">{{ row.categoryName || row.categoryCode || '—' }}</template>
              </el-table-column>
              <el-table-column label="来源" width="100">
                <template #default="{ row }">{{ row.sourceType === 'EXTERNAL' ? '外部平台' : '本地上传' }}</template>
              </el-table-column>
              <el-table-column label="发布状态" width="100">
                <template #default="{ row }">{{ statusLabel(row.publishStatus) }}</template>
              </el-table-column>
              <el-table-column prop="updatedAt" label="更新时间" width="160" />
            </el-table>
            <div class="bind-pager">
              <el-pagination
                v-model:current-page="docPage"
                v-model:page-size="docPageSize"
                layout="total, prev, pager, next"
                :total="docsAll.length"
                small
              />
            </div>
          </template>

          <template v-else-if="bindAccessMode === 'API' || bindAccessMode === 'CDC'">
            <el-form inline class="portal-inline-form portal-inline-form--sm" size="small">
              <el-form-item label="通道名" class="portal-field-lg">
                <el-input v-model="channelKeyword" clearable placeholder="编码/名称" @keyup.enter="channelPage = 1" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="channelPage = 1">查询</el-button>
                <el-button @click="channelKeyword = ''; channelPage = 1">重置</el-button>
              </el-form-item>
            </el-form>
            <el-table
              :data="channelsPaged"
              stripe
              size="small"
              highlight-current-row
              max-height="320"
              empty-text="暂无通道，请先在数据归集中配置"
              @current-change="(row: ChannelRow | null) => { selectedChannel = row }"
            >
              <el-table-column prop="channelCode" label="通道编码" min-width="140" />
              <el-table-column prop="channelName" label="名称" min-width="160" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">{{ statusLabel(row.status) }}</template>
              </el-table-column>
              <el-table-column prop="lastRunAt" label="最近同步" width="160" />
            </el-table>
            <div class="bind-pager">
              <el-pagination
                v-model:current-page="channelPage"
                v-model:page-size="channelPageSize"
                layout="total, prev, pager, next"
                :total="channelsFiltered.length"
                small
              />
            </div>
          </template>
        </div>
      </template>

      <template v-else>
        <el-alert type="info" :closable="false" style="margin-bottom:8px" title="只登记归属关系，不复制数据。优先选择已登记元数据或已纳管对象。" />
        <el-table
          :data="candidates"
          stripe
          size="small"
          highlight-current-row
          max-height="360"
          @current-change="(row: Candidate | null) => { selectedCandidate = row }"
        >
          <el-table-column prop="assetName" label="名称" min-width="160" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ assetTypeLabel(row.assetType) }}</template>
          </el-table-column>
          <el-table-column prop="physicalTable" label="物理表" min-width="140" show-overflow-tooltip />
          <el-table-column prop="dataLayer" label="分层" width="90" />
        </el-table>
      </template>

      <template #footer>
        <template v-if="collectWizard">
          <el-button @click="bindDialog = false">取消</el-button>
          <el-button v-if="bindStep > 1" @click="bindWizardPrev">上一步</el-button>
          <el-button
            v-if="bindStep < 3"
            type="primary"
            :disabled="bindStep === 1 ? !bindDimGroup : !bindAccessMode"
            @click="bindWizardNext"
          >
            下一步
          </el-button>
          <el-button
            v-else
            type="primary"
            :disabled="!canConfirmCollectBind"
            @click="confirmBind"
          >
            确认挂载
          </el-button>
        </template>
        <template v-else>
          <el-button @click="bindDialog = false">取消</el-button>
          <el-button type="primary" :disabled="!selectedCandidate" @click="confirmBind">确认挂载</el-button>
        </template>
      </template>
    </el-dialog>

    <el-drawer v-model="modelDrawer" size="640px" :title="editingModel ? `设计：${editingModel.modelName}` : '分析模型'" destroy-on-close>
      <el-form v-if="editingModel" label-width="100px">
        <el-form-item label="模型名称">
          <el-input v-model="modelForm.modelName" />
        </el-form-item>
        <el-form-item label="关联指标">
          <el-select v-model="modelForm.indicatorIds" multiple filterable style="width:100%" placeholder="选择指标">
            <el-option v-for="i in indicators" :key="i.id" :label="i.indicatorName" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="维度 JSON">
          <el-input v-model="modelForm.dimensionJson" type="textarea" :rows="3" placeholder='例如 ["区县","年龄段"]' />
        </el-form-item>
        <el-form-item v-if="!isPopulation" label="看板标识">
          <el-input
            v-model="modelForm.deDashboardId"
            placeholder="填预览地址中的 dvId，如 1280620734217064448（勿填公共分享码）"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="modelForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveModelDesign">保存设计</el-button>
          <template v-if="!isPopulation">
            <el-button @click="issueModelEmbed">签发嵌入</el-button>
            <el-button v-if="embedUrl" @click="openPortalPreview">门户预览</el-button>
          </template>
          <el-button v-else :loading="samplesLoading" @click="editingModel && loadModelSamples(editingModel.id)">刷新样例</el-button>
        </el-form-item>
      </el-form>

      <template v-if="isPopulation">
        <el-alert
          type="info"
          :closable="false"
          style="margin-bottom:8px"
          title="自研结果预览"
          description="人口域不嵌入 DataEase；下表为模型样例/结果行（可验收 ≥100 行）。"
        />
        <el-table v-loading="samplesLoading" :data="modelSamples" stripe size="small" max-height="360" empty-text="暂无样例">
          <el-table-column prop="rowNo" label="#" width="60" />
          <el-table-column prop="dim1" label="维度1" min-width="100" />
          <el-table-column prop="dim2" label="维度2" min-width="100" />
          <el-table-column prop="metric1" label="指标1" width="100" />
          <el-table-column prop="metric2" label="指标2" width="100" />
        </el-table>
      </template>
      <template v-else>
        <el-alert
          v-if="embedMode"
          :type="embedMode === 'LIVE' ? 'success' : 'warning'"
          :closable="false"
          style="margin-bottom:8px"
          :title="embedMode === 'LIVE' ? '实时嵌入' : '台账预览'"
          :description="embedMessage"
        />
        <div class="iframe-shell">
          <iframe v-if="iframeSrc" class="de-iframe" :src="iframeSrc" title="DataEase" />
          <div v-else class="iframe-placeholder">
            {{ dataEaseHealthy ? '签发令牌后加载嵌入画布' : '启动 DataEase 后可加载实时嵌入' }}
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.ana-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
.dim-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}
.dim-item {
  border: 1px solid var(--portal-border, #e4e7ed);
  border-radius: 6px;
  padding: 8px 10px;
  background: var(--el-fill-color-blank, #fff);
}
.dim-key { font-weight: 600; font-size: 13px; margin-bottom: 4px; }
.dim-tip { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.4; }
.mech-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}
.mech-card {
  border: 1px solid var(--portal-border, #e4e7ed);
  border-radius: 6px;
  padding: 10px 12px;
  background: var(--el-fill-color-light, #f5f7fa);
}
.mech-title { font-weight: 600; font-size: 13px; margin-bottom: 6px; display: flex; align-items: center; gap: 6px; }
.mech-list { margin: 0; padding-left: 18px; font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.55; }
.sub-title { margin: 16px 0 8px; font-size: 14px; font-weight: 600; }
.hint { color: var(--el-text-color-secondary); margin: 0 0 12px; font-size: 13px; }
.iframe-shell {
  margin-top: 12px;
  min-height: 280px;
  border: 1px solid var(--portal-border, #dcdfe6);
  border-radius: 8px;
  overflow: hidden;
  background: #0b1f33;
}
.de-iframe { width: 100%; height: 280px; border: 0; }
.iframe-placeholder {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #e8f4ff;
  opacity: 0.8;
  padding: 16px;
  text-align: center;
}
:deep(.row-hl) { background: var(--el-color-primary-light-9) !important; }
.bind-crumb {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 12px;
}
.bind-card-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.bind-card {
  border: 1px solid var(--portal-border, #dcdfe6);
  border-radius: 8px;
  padding: 20px 16px;
  cursor: pointer;
  background: var(--el-fill-color-blank, #fff);
  transition: border-color 0.15s, box-shadow 0.15s;
}
.bind-card:hover {
  border-color: var(--el-color-primary-light-3);
}
.bind-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.bind-card-title { font-weight: 600; font-size: 15px; margin-bottom: 6px; }
.bind-card-desc { font-size: 12px; color: var(--el-text-color-secondary); }
.bind-pager { margin-top: 10px; display: flex; justify-content: flex-end; }
.indicator-lib-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
</style>
