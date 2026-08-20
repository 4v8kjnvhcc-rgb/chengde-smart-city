<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavGroup } from '@/components/common/HubSideLayout.vue'
import DomainIndicatorSqlLibrary from '@/views/analytics/DomainIndicatorSqlLibrary.vue'
import DomainIndicatorGroupManage from '@/views/analytics/DomainIndicatorGroupManage.vue'
import DomainIndicatorTaskPanel from '@/views/analytics/DomainIndicatorTaskPanel.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'
import { ingestionApi } from '@/views/exchange/ingestion/useIngestionHub'
import { fetchDataSourceTableNames } from '@/utils/layer-tables'

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

interface IndicatorGroupOption {
  id: string
  groupName: string
  targetTable: string
  indicatorDomainId?: string
  indicatorDomainName?: string
  status?: string
}

interface IndicatorTablePreview {
  groupId: string
  groupName: string
  targetTable: string
  columns: string[]
  rows: Record<string, unknown>[]
  message?: string
  loading?: boolean
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
    { key: '定位', tip: '存放治理过程发现的问题数据；存放半结构化数据；存放非结构化数据转结构化数据；提供周期全量脏数据，供数据质量分析以及数据问题反馈使用' },
    { key: '数据模型', tip: '数据模型采取贴源结构；采用模型规范化设计；流水类表采用周期增量切片表存储' },
    { key: '加工处理', tip: '完成技术性及合法性检核清洗；以数据时点记录历史变更事实；提供半结构/非结构转结构化与预处理' },
    { key: '存储周期', tip: '支持长期存储；具体周期随存储空间容量扩大而延长' },
    { key: '数据来源', tip: '采集区数据；外部数据区数据' },
    { key: '使用者', tip: '数据核心区使用者；服务区使用者' },
    { key: '更新频度', tip: '一般每月更新一次；可按一天、一周、一季度、半年或一年更新' },
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

/** 法人域五区七维度 — 同构人口，文案全部为法人（禁止残留「人口」） */
const LEGAL_ZONE_DIMS: Record<string, DimItem[]> = {
  collect: [
    { key: '定位', tip: '多源异构法人数据统一汇入平台；含结构化与非结构化；按时效区分行为类/档案类通道' },
    { key: '数据模型', tip: '贴源结构为主；通道类型覆盖库表、文件、API' },
    { key: '加工处理', tip: '本区不做主题整合；仅接入、落 ODS、登记元数据' },
    { key: '存储周期', tip: '长期可存；容量策略随存储扩展' },
    { key: '数据来源', tip: '市场监管、税务、社保及行业主管部门等业务系统' },
    { key: '使用者', tip: '治理反馈区（下游清洗校核）' },
    { key: '更新频度', tip: '默认月更；可按日/周/季/半年/年配置（ExecCycle + DS）' },
  ],
  govern: [
    { key: '定位', tip: '存放治理过程发现的问题数据；存放半结构化数据；存放非结构化数据转结构化数据；提供周期全量脏数据，供数据质量分析以及数据问题反馈使用' },
    { key: '数据模型', tip: '数据模型采取贴源结构；采用模型规范化设计；流水类表采用周期增量切片表存储' },
    { key: '加工处理', tip: '完成技术性及合法性检核清洗；以数据时点记录历史变更事实；提供半结构/非结构转结构化与预处理' },
    { key: '存储周期', tip: '支持长期存储；具体周期随存储空间容量扩大而延长' },
    { key: '数据来源', tip: '采集区数据；外部数据区数据' },
    { key: '使用者', tip: '数据核心区使用者；服务区使用者' },
    { key: '更新频度', tip: '一般每月更新一次；可按一天、一周、一季度、半年或一年更新' },
  ],
  core: [
    { key: '定位', tip: '统一标准的法人基础/主题权威区；「一数一源」；按业务分类存放' },
    { key: '数据模型', tip: '宽表、多维；允许冗余；可在基础/主题上扩展专业库（逻辑分层）' },
    { key: '加工处理', tip: '多源合并到同一法人实体；跨业务计算；提取服务区特征' },
    { key: '存储周期', tip: '长期可存；垂直分片/水平分区；历史与审计近线' },
    { key: '数据来源', tip: '治理及反馈区（经融合落入 DWS）' },
    { key: '使用者', tip: '内部服务区；共享服务区' },
    { key: '更新频度', tip: '默认月更；可按日/周/季/半年/年按业务配置' },
  ],
  internal: POPULATION_ZONE_DIMS.internal.map((d) => ({ ...d, tip: d.tip.replace(/人口/g, '法人') })),
  share: POPULATION_ZONE_DIMS.share.map((d) => ({ ...d, tip: d.tip.replace(/人口/g, '法人') })),
}

function modelRowClassName({ row }: { row: AnalysisModel }) {
  return highlightModelCode.value && row.mCode === highlightModelCode.value ? 'row-hl' : ''
}

const ZONE_DEFS: Record<string, ZoneDef[]> = {
  population: [
    { key: 'zone.collect', zoneCode: 'collect', label: '人口数据采集区设计', mCodes: ['M152'], deepLink: '/exchange/ingestion?system=collect&module=ingest.structured&section=structured-table', deepLabel: '打开数据归集' },
    { key: 'zone.govern', zoneCode: 'govern', label: '人口数据治理及反馈区设计', mCodes: ['M153', 'M155', 'M156'], deepLink: '/governance?tab=etl&etlSub=task-mgmt', deepLabel: '打开数据治理' },
    { key: 'zone.core', zoneCode: 'core', label: '人口核心数据区设计', mCodes: ['M157'], deepLink: '/governance?tab=model&mSub=clean', deepLabel: '打开数据融合' },
    { key: 'zone.internal', zoneCode: 'internal', label: '人口数据内部服务区设计', mCodes: ['M158'], deepLink: '/exchange/ingestion?system=register&module=m048', deepLabel: '打开访问控制' },
    { key: 'zone.share', zoneCode: 'share', label: '人口数据共享服务区设计', mCodes: ['M154', 'M159', 'M160', 'M161', 'M162', 'M163', 'M164', 'M165', 'M166', 'M167', 'M168', 'M169', 'M170', 'M171', 'M172', 'M173', 'M174'], deepLink: '/catalog', deepLabel: '打开资源目录' },
  ],
  legal: [
    { key: 'zone.collect', zoneCode: 'collect', label: '法人数据采集区设计', mCodes: ['M175'], deepLink: '/exchange/ingestion?system=collect&module=ingest.structured&section=structured-table', deepLabel: '打开数据归集' },
    { key: 'zone.govern', zoneCode: 'govern', label: '法人数据治理及反馈区设计', mCodes: ['M176', 'M178', 'M179'], deepLink: '/governance?tab=etl&etlSub=task-mgmt', deepLabel: '打开数据治理' },
    { key: 'zone.core', zoneCode: 'core', label: '法人核心数据区设计', mCodes: ['M180'], deepLink: '/governance?tab=model&mSub=clean', deepLabel: '打开数据融合' },
    { key: 'zone.internal', zoneCode: 'internal', label: '法人数据内部服务区设计', mCodes: ['M181'], deepLink: '/exchange/ingestion?system=register&module=m048', deepLabel: '打开访问控制' },
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

/** 指标库左侧仅展示本业务支撑系统对应指标域 */
const INDICATOR_SCOPE_NAME: Record<string, string> = {
  population: '人口大数据支撑系统',
  legal: '法人大数据支撑系统',
  macro: '宏观经济及工业运行大数据支撑系统',
  key: '重点领域示范应用支撑系统',
}

const route = useRoute()
const router = useRouter()

const meta = computed(() => domainMeta[route.path] || domainMeta['/analytics/population'])
const zones = computed(() => ZONE_DEFS[meta.value.domain] || [])
const hasZones = computed(() => zones.value.length > 0)
const isPopulation = computed(() => meta.value.domain === 'population')
const isLegal = computed(() => meta.value.domain === 'legal')
/**
 * 本 Hub 仅服务业务支撑四域（人口/法人/宏观/重点）：
 * 分析模型 = 自研元数据 + 样例结果表，一律不接 DataEase。
 * 智能 BI 在 AnalyticsBiHubView，与此无关。
 */
const useSelfBuiltAnalysisModel = computed(() => true)
/** 人口/法人：五区设计 + 分层选型挂载（采集 ODS → 治理 DWD → 核心 DWS → 内部 DWS/ADS） */
const useFiveZoneMount = computed(() => isPopulation.value || isLegal.value)
/** 是否使用与人口相同的指标组/任务指标库（按域过滤） */
const useGroupIndicatorLib = computed(() => !!INDICATOR_SCOPE_NAME[meta.value.domain])
const indicatorScopeName = computed(() => INDICATOR_SCOPE_NAME[meta.value.domain] || '')

const dataEaseHealthy = ref(false)
const activeNav = ref('')
const shareTab = ref<'mount' | 'api' | 'indicators' | 'tasks' | 'models'>('mount')
const governTab = ref<'design' | 'mount' | 'verify'>('design')
const collectTab = ref<'mount'>('mount')
const coreTab = ref<'design' | 'mount' | 'storage'>('design')
const internalTab = ref<'design' | 'classify' | 'grant' | 'mount'>('design')
const highlightModelCode = ref('')

interface ZoneDimDesignRow {
  id: number
  domainCode?: string
  zoneCode?: string
  dimCode: string
  itemCode: string
  itemName: string
  itemType?: string
  content?: string
  configJson?: string
  deepLink?: string
  sortNo?: number
  status: string
  createdAt?: string
  updatedAt?: string
}

const DIM_CODE_OPTIONS = [
  { value: 'POSITION', label: '定位' },
  { value: 'MODEL', label: '数据模型' },
  { value: 'PROCESS', label: '加工处理' },
  { value: 'RETENTION', label: '存储周期' },
  { value: 'SOURCE', label: '数据来源' },
  { value: 'CONSUMER', label: '使用者' },
  { value: 'FREQUENCY', label: '更新频度' },
] as const

const DIM_KEY_TO_CODE: Record<string, string> = {
  定位: 'POSITION',
  数据模型: 'MODEL',
  加工处理: 'PROCESS',
  存储周期: 'RETENTION',
  数据来源: 'SOURCE',
  使用者: 'CONSUMER',
  更新频度: 'FREQUENCY',
}

const ITEM_TYPE_OPTIONS: Record<string, { value: string; label: string }[]> = {
  POSITION: [
    { value: 'PROBLEM_DATA', label: '问题数据' },
    { value: 'SEMI', label: '半结构化数据' },
    { value: 'UNSTRUCT_TO_STRUCT', label: '非结构转结构化' },
    { value: 'DIRTY_FULL', label: '周期全量脏数据' },
    { value: 'AUTHORITY_BASE', label: '基础/主题权威区' },
    { value: 'ONE_SOURCE', label: '一数一源' },
    { value: 'BIZ_CLASSIFY', label: '按业务分类存放' },
    { value: 'HIGH_SENSITIVE_BOUNDARY', label: '高敏独立边界' },
  ],
  MODEL: [
    { value: 'SOURCE_ALIGN', label: '贴源结构' },
    { value: 'NORMALIZED', label: '规范化设计' },
    { value: 'INCREMENTAL_SLICE', label: '周期增量切片' },
    { value: 'WIDE_TABLE', label: '宽表模型' },
    { value: 'MULTIDIM', label: '多维模型' },
    { value: 'SPECIALTY_EXT', label: '专业库扩展' },
    { value: 'CONSUME_CORE', label: '消费核心权威' },
  ],
  PROCESS: [
    { value: 'TECH_LEGAL_CLEAN', label: '技术/合法性检核清洗' },
    { value: 'POINT_IN_TIME', label: '历史时点变更' },
    { value: 'SEMI_UNSTRUCT_PREP', label: '半/非结构预处理' },
    { value: 'ENTITY_MERGE', label: '多源实体合并' },
    { value: 'CROSS_CALC', label: '跨业务计算' },
    { value: 'SERVICE_FEATURE', label: '提取服务区特征' },
    { value: 'CLASSIFY_DUAL_AUTH', label: '分级分类与双重授权' },
  ],
  RETENTION: [
    { value: 'LONG_TERM', label: '长期存储' },
    { value: 'CAPACITY_BASED', label: '随容量延长' },
    { value: 'LONG_TERM_PARTITION', label: '长期存储与分区' },
  ],
  SOURCE: [
    { value: 'COLLECT_ZONE', label: '采集区' },
    { value: 'EXTERNAL_ZONE', label: '外部数据区' },
    { value: 'FROM_GOVERN', label: '治理及反馈区' },
    { value: 'FROM_CORE', label: '来源核心区' },
  ],
  CONSUMER: [
    { value: 'CORE_ZONE', label: '核心区使用者' },
    { value: 'SERVICE_ZONE', label: '服务区使用者' },
    { value: 'INTERNAL_ZONE', label: '内部服务区使用者' },
    { value: 'SHARE_ZONE', label: '共享服务区使用者' },
    { value: 'DEPT_HIGH_SENS', label: '部门高敏应用' },
  ],
  FREQUENCY: [
    { value: 'DAILY', label: '一天' },
    { value: 'WEEKLY', label: '一周' },
    { value: 'MONTHLY', label: '每月' },
    { value: 'QUARTERLY', label: '一季度' },
    { value: 'HALF_YEAR', label: '半年' },
    { value: 'YEARLY', label: '一年' },
  ],
}

function dimCodeLabel(code?: string) {
  return DIM_CODE_OPTIONS.find((d) => d.value === code)?.label || statusLabel(code || '')
}

function itemTypeLabel(dimCode: string, itemType?: string) {
  if (!itemType) return '—'
  return ITEM_TYPE_OPTIONS[dimCode]?.find((x) => x.value === itemType)?.label || statusLabel(itemType)
}

const designRows = ref<ZoneDimDesignRow[]>([])
const designLoading = ref(false)
const designDimFilter = ref('')
const designDialog = ref(false)
const designEditingId = ref<number | null>(null)
const designForm = ref({
  dimCode: 'POSITION',
  itemCode: '',
  itemName: '',
  itemType: '',
  content: '',
  deepLink: '',
  sortNo: 0,
  status: 'ACTIVE',
})

const filteredDesignRows = computed(() => {
  if (!designDimFilter.value) return designRows.value
  return designRows.value.filter((r) => r.dimCode === designDimFilter.value)
})

const designItemTypeOptions = computed(() => ITEM_TYPE_OPTIONS[designForm.value.dimCode] || [])

interface InternalClassifyRow {
  id: number
  assetCode: string
  assetName: string
  categoryCode: string
  categoryName: string
  levelCode: string
  levelName?: string
  classifyBasis?: string
  controlHint?: string
  bindingId?: number
  sortNo?: number
  status: string
  createdAt?: string
  updatedAt?: string
}

interface InternalGrantRow {
  id: number
  grantType: string
  granteeType: string
  granteeCode: string
  granteeName?: string
  assetCode?: string
  assetName?: string
  levelCode?: string
  authMode?: string
  permissionScope?: string
  reason?: string
  status: string
  applicant?: string
  approvedBy?: string
  approvedAt?: string
  createdAt?: string
  updatedAt?: string
}

const LEVEL_OPTIONS = [
  { value: 'GENERAL', label: '一般数据' },
  { value: 'IMPORTANT', label: '重要数据' },
  { value: 'SENSITIVE', label: '敏感数据' },
  { value: 'CORE', label: '核心数据' },
] as const

const LEGAL_CATEGORY_OPTIONS = [
  { value: 'LEGAL_BASE', label: '法人基础数据' },
  { value: 'LEGAL_BIZ', label: '法人业务主题' },
  { value: 'CONTACT', label: '联系信息' },
  { value: 'FINANCE', label: '财务相关' },
  { value: 'OTHER', label: '其他' },
] as const

const POPULATION_CATEGORY_OPTIONS = [
  { value: 'POP_BASE', label: '人口基础数据' },
  { value: 'POP_BIZ', label: '人口业务主题' },
  { value: 'IDENTITY', label: '身份证件信息' },
  { value: 'CONTACT', label: '联系信息' },
  { value: 'OTHER', label: '其他' },
] as const

const CATEGORY_OPTIONS = computed(() =>
  isPopulation.value ? POPULATION_CATEGORY_OPTIONS : LEGAL_CATEGORY_OPTIONS,
)

const defaultClassifyCategory = computed(() =>
  isPopulation.value
    ? { code: 'POP_BASE', name: '人口基础数据' }
    : { code: 'LEGAL_BASE', name: '法人基础数据' },
)

const internalClassifyAlert = computed(() =>
  isPopulation.value
    ? '内部服务区对高敏人口基础数据做分级分类登记；访问须配合「数据权限」双重授权。'
    : '内部服务区对高敏法人基础数据做分级分类登记；访问须配合「数据权限」双重授权。',
)

const classifyAssetPlaceholder = computed(() =>
  isPopulation.value ? '如 dws_population_base' : '如 dws_legal_entity_base',
)

const classifyRows = ref<InternalClassifyRow[]>([])
const classifyLoading = ref(false)
const classifyLevelFilter = ref('')
const classifyDialog = ref(false)
const classifyEditingId = ref<number | null>(null)
const classifyForm = ref({
  assetCode: '',
  assetName: '',
  categoryCode: 'POP_BASE',
  categoryName: '人口基础数据',
  levelCode: 'SENSITIVE',
  levelName: '敏感数据',
  classifyBasis: '',
  controlHint: '',
  sortNo: 0,
  status: 'ACTIVE',
})

const grantRows = ref<InternalGrantRow[]>([])
const grantLoading = ref(false)
const grantTypeFilter = ref('')
const grantStatusFilter = ref('')
const grantDialog = ref(false)
const grantEditingId = ref<number | null>(null)
const grantForm = ref({
  grantType: 'DATA_ACCESS',
  granteeType: 'ORG',
  granteeCode: '',
  granteeName: '',
  assetCode: '',
  assetName: '',
  levelCode: 'SENSITIVE',
  authMode: 'DUAL',
  permissionScope: 'MASKED_READ',
  reason: '',
})

const bindings = ref<Binding[]>([])
const candidates = ref<Candidate[]>([])
const indicators = ref<Indicator[]>([])
const indicatorGroups = ref<IndicatorGroupOption[]>([])
const models = ref<AnalysisModel[]>([])
const modelSamples = ref<ModelSample[]>([])
const indicatorTablePreviews = ref<IndicatorTablePreview[]>([])
const samplesLoading = ref(false)
const verifyRows = ref<VerifyLedgerRow[]>([])
const verifyLoading = ref(false)
const serviceContracts = ref<ServiceContract[]>([])
const serviceLoading = ref(false)
const invokeResult = ref('')
const batchRows = ref<BatchLedgerRow[]>([])
const batchLoading = ref(false)
const storageSummary = ref<Record<string, unknown> | null>(null)
const storageLoading = ref(false)
const verifyForm = ref({
  mCode: 'M179',
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
const batchTableOptions = ref<string[]>([])
const batchTablesLoading = ref(false)

watch(batchDialog, (v) => {
  if (v) void loadBatchTables()
})

async function loadBatchTables() {
  batchTablesLoading.value = true
  try {
    const [dws, ads] = await Promise.all([
      fetchDataSourceTableNames(-3),
      fetchDataSourceTableNames(-4),
    ])
    batchTableOptions.value = Array.from(new Set([...dws, ...ads])).sort((a, b) => a.localeCompare(b))
  } catch {
    batchTableOptions.value = []
  } finally {
    batchTablesLoading.value = false
  }
}

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
const selectedOdsTables = ref<OdsTableRow[]>([])
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
  if (bindAccessMode.value === 'STRUCT') return selectedOdsTables.value.length > 0
  if (bindAccessMode.value === 'UNSTRUCT') return !!selectedDoc.value
  if (bindAccessMode.value === 'API' || bindAccessMode.value === 'CDC') return !!selectedChannel.value
  return false
})
const collectBindConfirmLabel = computed(() => {
  if (bindAccessMode.value === 'STRUCT' && selectedOdsTables.value.length > 1) {
    return `确认挂载（${selectedOdsTables.value.length}）`
  }
  return '确认挂载'
})

const modelDrawer = ref(false)
const editingModel = ref<AnalysisModel | null>(null)
const modelForm = ref({
  modelName: '',
  deDashboardId: '',
  dimensionJson: '',
  description: '',
  indicatorIds: [] as number[],
  indicatorGroupIds: [] as string[],
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

/** 人口五区选型挂载目标分层（采集 ODS → 治理 DWD → 核心 DWS → 内部 DWS/ADS） */
const ZONE_BIND_LAYERS: Record<string, string[]> = {
  collect: ['ODS'],
  govern: ['DWD'],
  core: ['DWS'],
  internal: ['DWS', 'ADS'],
}
const useLayerBindWizard = computed(() =>
  useFiveZoneMount.value
  && !!activeZone.value
  && ['collect', 'govern', 'core', 'internal'].includes(activeZone.value.zoneCode),
)
const zoneBindLayers = computed(() => {
  const z = activeZone.value?.zoneCode || 'collect'
  return ZONE_BIND_LAYERS[z] || ['ODS']
})
const zoneBindLayerLabel = computed(() => zoneBindLayers.value.join('/'))
const structAccessDesc = computed(() => {
  const layers = zoneBindLayers.value
  if (layers.length === 1 && layers[0] === 'ODS') return 'ODS 贴源表'
  if (layers.length === 1 && layers[0] === 'DWD') return 'DWD 过程表'
  if (layers.length === 1 && layers[0] === 'DWS') return 'DWS 主题表'
  if (layers.includes('DWS') && layers.includes('ADS')) return 'DWS/ADS 资源表'
  return `${zoneBindLayerLabel.value} 分层表`
})

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
  if (!z) return SEVEN_DIMS_GENERIC
  if (isLegal.value && LEGAL_ZONE_DIMS[z]) return LEGAL_ZONE_DIMS[z]
  if (useFiveZoneMount.value && POPULATION_ZONE_DIMS[z]) return POPULATION_ZONE_DIMS[z]
  return SEVEN_DIMS_GENERIC
})

/** 治理/核心/内部：顶部七维卡片可点筛选设计列表 */
const dimCardFilterEnabled = computed(() =>
  useFiveZoneMount.value && (isGovern.value || isCore.value || isInternal.value),
)

function dimKeyToCode(key: string) {
  return DIM_KEY_TO_CODE[key] || ''
}

function isDimCardActive(key: string) {
  const code = dimKeyToCode(key)
  return !!code && designDimFilter.value === code
}

function onDimCardClick(d: DimItem) {
  if (!dimCardFilterEnabled.value) return
  const code = dimKeyToCode(d.key)
  if (!code) return
  if (isGovern.value) governTab.value = 'design'
  else if (isCore.value) coreTab.value = 'design'
  else if (isInternal.value) internalTab.value = 'design'
  designDimFilter.value = designDimFilter.value === code ? '' : code
}

function zoneApiPath(zoneKey: string) {
  return zoneKey.startsWith('zone.') ? zoneKey.slice(5) : zoneKey
}

function pickDefaultNav() {
  if (hasZones.value) {
    activeNav.value = zones.value[0].key
  } else {
    activeNav.value = 'designer'
    if (shareTab.value === 'mount' || shareTab.value === 'api') {
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
  } else if (q === 'designer' || q === 'models' || q === 'indicators' || q === 'tasks') {
    if (hasZones.value) {
      const share = zones.value.find((z) => z.zoneCode === 'share')
      activeNav.value = share?.key || zones.value[0]?.key || ''
      shareTab.value = q === 'indicators' ? 'indicators' : q === 'tasks' ? 'tasks' : q === 'models' ? 'models' : 'mount'
    } else {
      activeNav.value = 'designer'
      shareTab.value = q === 'indicators' ? 'indicators' : q === 'tasks' ? 'tasks' : 'models'
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
        } else if (code === 'M154' || code === 'M177') {
          shareTab.value = 'mount'
        } else {
          shareTab.value = 'models'
          highlightModelCode.value = code
        }
      } else if (parentZone.zoneCode === 'govern' && (code === 'M155' || code === 'M156' || code === 'M178' || code === 'M179')) {
        governTab.value = 'verify'
      } else if (parentZone.zoneCode === 'core' && (code === 'M157' || code === 'M180')) {
        coreTab.value = 'storage'
      } else if (parentZone.zoneCode === 'internal' && (code === 'M158' || code === 'M181')) {
        internalTab.value = 'classify'
      } else if (parentZone.zoneCode === 'collect' && (code === 'M152' || code === 'M175')) {
        collectTab.value = 'mount'
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
  // 业务支撑四域不探 DataEase 健康度
  dataEaseHealthy.value = false
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
  const reqs: Promise<unknown>[] = [
    api.get(`/analytics/domain/${meta.value.domain}/indicators`),
    api.get(`/analytics/domain/${meta.value.domain}/models`),
  ]
  if (useGroupIndicatorLib.value) {
    reqs.push(api.get(`/analytics/domain/${meta.value.domain}/indicator-groups`))
  }
  const results = await Promise.all(reqs)
  const iRes = results[0] as { data?: Indicator[] }
  const mRes = results[1] as { data?: AnalysisModel[] }
  indicators.value = iRes.data || []
  models.value = mRes.data || []
  if (useGroupIndicatorLib.value) {
    const gRes = results[2] as { data?: IndicatorGroupOption[] }
    let groups = gRes.data || []
    const scope = indicatorScopeName.value.trim()
    if (scope) {
      groups = groups.filter((g) => String(g.indicatorDomainName || '').includes(scope))
    }
    indicatorGroups.value = groups
  } else {
    indicatorGroups.value = []
  }
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

function parseIndicatorGroupIds(dimensionJson?: string): string[] {
  if (!dimensionJson?.trim()) return []
  try {
    const parsed = JSON.parse(dimensionJson)
    if (Array.isArray(parsed?.indicatorGroupIds)) {
      return parsed.indicatorGroupIds.map((x: unknown) => String(x || '').trim()).filter(Boolean)
    }
  } catch {
    /* ignore */
  }
  return []
}

function buildDimensionJsonWithGroups(groupIds: string[]): string {
  return JSON.stringify({ indicatorGroupIds: groupIds })
}

function modelLinkedLabel(row: AnalysisModel): string {
  const ids = parseIndicatorGroupIds(row.dimensionJson)
  if (ids.length) {
    const map = new Map(indicatorGroups.value.map((g) => [g.id, g.groupName]))
    return ids.map((id) => map.get(id) || id).join('、') || '—'
  }
  return (row.indicators || []).map((i) => i.indicatorName).join('、') || '—'
}

async function loadIndicatorTablePreviews() {
  const ids = modelForm.value.indicatorGroupIds || []
  if (!ids.length) {
    indicatorTablePreviews.value = []
    return
  }
  samplesLoading.value = true
  indicatorTablePreviews.value = ids.map((id) => {
    const g = indicatorGroups.value.find((x) => x.id === id)
    return {
      groupId: id,
      groupName: g?.groupName || id,
      targetTable: g?.targetTable || '',
      columns: [],
      rows: [],
      loading: true,
    }
  })
  try {
    const results = await Promise.all(
      ids.map(async (id) => {
        try {
          const res = await api.get(`/analytics/domain/indicator-groups/${id}/result-data`, {
            params: { limit: 200 },
          })
          const data = res.data || {}
          return {
            groupId: id,
            groupName: String(data.groupName || indicatorGroups.value.find((x) => x.id === id)?.groupName || id),
            targetTable: String(data.targetTable || ''),
            columns: (data.columns || []) as string[],
            rows: (data.rows || []) as Record<string, unknown>[],
            message: String(data.message || ''),
            loading: false,
          } satisfies IndicatorTablePreview
        } catch (e: unknown) {
          return {
            groupId: id,
            groupName: indicatorGroups.value.find((x) => x.id === id)?.groupName || id,
            targetTable: indicatorGroups.value.find((x) => x.id === id)?.targetTable || '',
            columns: [],
            rows: [],
            message: e instanceof Error ? e.message : '加载失败',
            loading: false,
          } satisfies IndicatorTablePreview
        }
      }),
    )
    indicatorTablePreviews.value = results
  } finally {
    samplesLoading.value = false
  }
}

async function loadZoneDimDesigns() {
  if (!activeZone.value || !useFiveZoneMount.value) return
  designLoading.value = true
  try {
    const zone = activeZone.value.zoneCode
    const res = await api.get(`/analytics/domain/${meta.value.domain}/zones/${zone}/dim-designs`)
    designRows.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载七维设计失败')
  } finally {
    designLoading.value = false
  }
}

function openDesignCreate() {
  designEditingId.value = null
  designForm.value = {
    dimCode: designDimFilter.value || 'POSITION',
    itemCode: '',
    itemName: '',
    itemType: '',
    content: '',
    deepLink: '',
    sortNo: 0,
    status: 'ACTIVE',
  }
  designDialog.value = true
}

function openDesignEdit(row: ZoneDimDesignRow) {
  designEditingId.value = row.id
  designForm.value = {
    dimCode: row.dimCode,
    itemCode: row.itemCode,
    itemName: row.itemName,
    itemType: row.itemType || '',
    content: row.content || '',
    deepLink: row.deepLink || '',
    sortNo: row.sortNo || 0,
    status: row.status || 'ACTIVE',
  }
  designDialog.value = true
}

async function saveDesignRow() {
  if (!activeZone.value) return
  if (!designForm.value.itemCode.trim() || !designForm.value.itemName.trim()) {
    ElMessage.warning('请填写编码与名称')
    return
  }
  const zone = activeZone.value.zoneCode
  const body = { ...designForm.value }
  if (designEditingId.value == null) {
    await api.post(`/analytics/domain/${meta.value.domain}/zones/${zone}/dim-designs`, body)
    ElMessage.success('已新增七维设计项')
  } else {
    await api.put(`/analytics/domain/dim-designs/${designEditingId.value}`, body)
    ElMessage.success('已更新七维设计项')
  }
  designDialog.value = false
  await loadZoneDimDesigns()
}

async function removeDesignRow(row: ZoneDimDesignRow) {
  await ElMessageBox.confirm(`确认删除「${row.itemName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/analytics/domain/dim-designs/${row.id}`)
  ElMessage.success('已删除')
  await loadZoneDimDesigns()
}

async function loadInternalClassifies() {
  if (!activeZone.value || !useFiveZoneMount.value) return
  classifyLoading.value = true
  try {
    const zone = activeZone.value.zoneCode
    const res = await api.get(`/analytics/domain/${meta.value.domain}/zones/${zone}/internal-classifies`, {
      params: {
        levelCode: classifyLevelFilter.value || undefined,
      },
    })
    classifyRows.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载分级分类失败')
  } finally {
    classifyLoading.value = false
  }
}

function onClassifyCategoryChange(code: string) {
  classifyForm.value.categoryName = CATEGORY_OPTIONS.value.find((c) => c.value === code)?.label || code
}

function onClassifyLevelChange(code: string) {
  classifyForm.value.levelName = LEVEL_OPTIONS.find((c) => c.value === code)?.label || code
}

function openClassifyCreate() {
  classifyEditingId.value = null
  const cat = defaultClassifyCategory.value
  classifyForm.value = {
    assetCode: '',
    assetName: '',
    categoryCode: cat.code,
    categoryName: cat.name,
    levelCode: 'SENSITIVE',
    levelName: '敏感数据',
    classifyBasis: '',
    controlHint: '访问须双重授权；系统管理员不可直接授跨部门数据访问权',
    sortNo: 0,
    status: 'ACTIVE',
  }
  classifyDialog.value = true
}

function openClassifyEdit(row: InternalClassifyRow) {
  classifyEditingId.value = row.id
  classifyForm.value = {
    assetCode: row.assetCode,
    assetName: row.assetName,
    categoryCode: row.categoryCode,
    categoryName: row.categoryName,
    levelCode: row.levelCode,
    levelName: row.levelName || '',
    classifyBasis: row.classifyBasis || '',
    controlHint: row.controlHint || '',
    sortNo: row.sortNo || 0,
    status: row.status || 'ACTIVE',
  }
  classifyDialog.value = true
}

function fillClassifyFromBinding(b: Binding) {
  classifyForm.value.assetCode = b.physicalTable || b.assetRef || ''
  classifyForm.value.assetName = b.assetName || ''
}

function onClassifyPickBinding(ref: string) {
  const b = bindings.value.find((x) => (x.physicalTable || x.assetRef) === ref)
  if (b) fillClassifyFromBinding(b)
}

function onGrantPickClassify(code: string) {
  const r = classifyRows.value.find((x) => x.assetCode === code)
  if (r) fillGrantFromClassify(r)
}

async function saveClassifyRow() {
  if (!activeZone.value) return
  if (!classifyForm.value.assetCode.trim() || !classifyForm.value.assetName.trim()) {
    ElMessage.warning('请填写资产编码与名称')
    return
  }
  const zone = activeZone.value.zoneCode
  const body = { ...classifyForm.value }
  if (classifyEditingId.value == null) {
    await api.post(`/analytics/domain/${meta.value.domain}/zones/${zone}/internal-classifies`, body)
    ElMessage.success('已新增分级分类')
  } else {
    const { assetCode: _a, ...patch } = body
    await api.put(`/analytics/domain/internal-classifies/${classifyEditingId.value}`, patch)
    ElMessage.success('已更新分级分类')
  }
  classifyDialog.value = false
  await loadInternalClassifies()
}

async function removeClassifyRow(row: InternalClassifyRow) {
  await ElMessageBox.confirm(`确认删除「${row.assetName}」的分级分类？`, '删除确认', { type: 'warning' })
  await api.delete(`/analytics/domain/internal-classifies/${row.id}`)
  ElMessage.success('已删除')
  await loadInternalClassifies()
}

async function loadInternalGrants() {
  if (!activeZone.value || !useFiveZoneMount.value) return
  grantLoading.value = true
  try {
    const zone = activeZone.value.zoneCode
    const res = await api.get(`/analytics/domain/${meta.value.domain}/zones/${zone}/internal-grants`, {
      params: {
        grantType: grantTypeFilter.value || undefined,
        status: grantStatusFilter.value || undefined,
      },
    })
    grantRows.value = res.data || []
  } catch (e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '加载数据权限失败')
  } finally {
    grantLoading.value = false
  }
}

function openGrantCreate() {
  grantEditingId.value = null
  grantForm.value = {
    grantType: 'DATA_ACCESS',
    granteeType: 'ORG',
    granteeCode: '',
    granteeName: '',
    assetCode: '',
    assetName: '',
    levelCode: 'SENSITIVE',
    authMode: 'DUAL',
    permissionScope: 'MASKED_READ',
    reason: '',
  }
  grantDialog.value = true
}

function openGrantEdit(row: InternalGrantRow) {
  grantEditingId.value = row.id
  grantForm.value = {
    grantType: row.grantType,
    granteeType: row.granteeType,
    granteeCode: row.granteeCode,
    granteeName: row.granteeName || '',
    assetCode: row.assetCode || '',
    assetName: row.assetName || '',
    levelCode: row.levelCode || 'SENSITIVE',
    authMode: row.authMode || 'DUAL',
    permissionScope: row.permissionScope || 'MASKED_READ',
    reason: row.reason || '',
  }
  grantDialog.value = true
}

function fillGrantFromClassify(row: InternalClassifyRow) {
  grantForm.value.assetCode = row.assetCode
  grantForm.value.assetName = row.assetName
  grantForm.value.levelCode = row.levelCode
}

function startGrantFromClassify(row: InternalClassifyRow) {
  internalTab.value = 'grant'
  openGrantCreate()
  fillGrantFromClassify(row)
}

async function saveGrantRow() {
  if (!activeZone.value) return
  if (!grantForm.value.granteeCode.trim()) {
    ElMessage.warning('请填写授权对象编码')
    return
  }
  if (grantForm.value.grantType === 'DATA_ACCESS' && !grantForm.value.assetCode.trim()) {
    ElMessage.warning('数据访问授权须指定资产编码')
    return
  }
  const zone = activeZone.value.zoneCode
  if (grantEditingId.value == null) {
    await api.post(`/analytics/domain/${meta.value.domain}/zones/${zone}/internal-grants`, { ...grantForm.value })
    ElMessage.success(grantForm.value.grantType === 'DATA_ACCESS'
      ? '已提交授权（系统管理员发起的数据访问默认待部门管理员审批）'
      : '已新增授权')
  } else {
    await api.put(`/analytics/domain/internal-grants/${grantEditingId.value}`, {
      granteeName: grantForm.value.granteeName,
      assetName: grantForm.value.assetName,
      levelCode: grantForm.value.levelCode,
      permissionScope: grantForm.value.permissionScope,
      reason: grantForm.value.reason,
      authMode: grantForm.value.authMode,
    })
    ElMessage.success('已更新授权申请')
  }
  grantDialog.value = false
  await loadInternalGrants()
}

async function decideGrant(row: InternalGrantRow, action: 'APPROVE' | 'REJECT' | 'REVOKE') {
  const tip = action === 'APPROVE' ? '通过' : action === 'REJECT' ? '驳回' : '撤销'
  await ElMessageBox.confirm(`确认${tip}「${row.granteeName || row.granteeCode}」的授权？`, '操作确认', { type: 'warning' })
  await api.post(`/analytics/domain/internal-grants/${row.id}/decide`, { action })
  ElMessage.success(`已${tip}`)
  await loadInternalGrants()
}

async function removeGrantRow(row: InternalGrantRow) {
  await ElMessageBox.confirm(`确认删除授权记录「${row.granteeName || row.granteeCode}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/analytics/domain/internal-grants/${row.id}`)
  ElMessage.success('已删除')
  await loadInternalGrants()
}

async function loadVerifyLedger() {
  if (!isPopulation.value && !isLegal.value) return
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
  if (!isPopulation.value && !isLegal.value) return
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

function openVerifyDialog() {
  verifyForm.value.mCode = isLegal.value ? 'M179' : 'M156'
  verifyDialog.value = true
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
    if (isShare.value || shareTab.value === 'indicators' || shareTab.value === 'tasks' || shareTab.value === 'models') {
      await loadDesigner()
    }
    if (isPopulation.value && isShare.value && shareTab.value === 'api') {
      await Promise.all([loadServiceContracts(), loadBatchLedger()])
    }
    if ((isPopulation.value || isLegal.value) && isGovern.value) {
      if (governTab.value === 'design') await loadZoneDimDesigns()
      if (governTab.value === 'verify') await loadVerifyLedger()
    }
    if ((isPopulation.value || isLegal.value) && isCore.value) {
      if (coreTab.value === 'design') await loadZoneDimDesigns()
      if (coreTab.value === 'storage') await loadStorageSummary()
    }
    if ((isPopulation.value || isLegal.value) && isInternal.value) {
      if (internalTab.value === 'design') await loadZoneDimDesigns()
      if (internalTab.value === 'classify') await loadInternalClassifies()
      if (internalTab.value === 'grant') await loadInternalGrants()
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
  selectedOdsTables.value = []
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

function collectLayerCategoryIds(nodes: CategoryNode[], layers: string[], out: Set<number>) {
  const layerSet = new Set(layers.map((l) => l.toUpperCase()))
  for (const n of nodes) {
    const code = String(n.categoryCode || '')
    const layer = String(n.layerCode || '').toUpperCase()
    const label = String(n.label || '')
    const hitLayer = layerSet.has(layer)
      || [...layerSet].some((L) => new RegExp(L, 'i').test(code) || new RegExp(L, 'i').test(label))
    if (hitLayer) out.add(n.id)
    if (n.children?.length) collectLayerCategoryIds(n.children, layers, out)
  }
}

function inferBindDataLayer(tableName: string): string {
  const n = String(tableName || '').toLowerCase()
  const allowed = zoneBindLayers.value
  if (n.startsWith('ads_') && allowed.includes('ADS')) return 'ADS'
  if (n.startsWith('dws_') && allowed.includes('DWS')) return 'DWS'
  if (n.startsWith('dwd_') && allowed.includes('DWD')) return 'DWD'
  if (n.startsWith('ods_') && allowed.includes('ODS')) return 'ODS'
  return allowed[0] || 'ODS'
}

async function loadOdsSources() {
  const layers = zoneBindLayers.value
  const tree = ((await api.get('/governance/platform/metadata/source-categories/tree')).data || []) as CategoryNode[]
  const layerIds = new Set<number>()
  collectLayerCategoryIds(tree, layers, layerIds)
  const all = ((await api.get('/governance/platform/metadata/data-sources')).data || []) as MetaSourceOption[]
  const layerRe = new RegExp(layers.join('|'), 'i')
  let filtered = layerIds.size
    ? all.filter((s) => s.categoryId != null && layerIds.has(s.categoryId))
    : all.filter((s) => layerRe.test(String(s.categoryName || '')))
  if (!filtered.length) filtered = all
  odsSources.value = filtered
  if (!odsSourceId.value && filtered.length) {
    odsSourceId.value = filtered[0].id
  }
}

function onOdsTableSelectionChange(rows: OdsTableRow[]) {
  selectedOdsTables.value = rows || []
}

async function loadOdsTables() {
  selectedOdsTables.value = []
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
    const layers = zoneBindLayers.value
    const layerRe = new RegExp(`^(${layers.map((l) => l.toLowerCase()).join('|')})_`, 'i')
    let names = rows
      .map((r) => String(r.sourceTable || r.tableName || '').trim())
      .filter(Boolean)
    // 有分层前缀时优先过滤；无匹配则保留全量（兼容未按前缀命名的库表）
    const prefixed = names.filter((t) => layerRe.test(t))
    if (prefixed.length) names = prefixed
    odsTablesAll.value = names.map((tableName) => ({
      tableName,
      sourceName: src?.sourceName || '—',
      dataLayer: inferBindDataLayer(tableName),
    }))
  } catch {
    odsTablesAll.value = []
    ElMessage.error(`加载 ${zoneBindLayerLabel.value} 表失败`)
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
  if (useLayerBindWizard.value) {
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
    selectedOdsTables.value = []
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
  const zoneCode = activeZone.value.zoneCode
  const bindUrl = `/analytics/domain/${meta.value.domain}/zones/${zoneCode}/bindings`

  if (bindAccessMode.value === 'STRUCT') {
    const tables = selectedOdsTables.value
    if (!tables.length) {
      ElMessage.warning('请勾选要挂载的表')
      return
    }
    const sid = odsSourceId.value
    let ok = 0
    let fail = 0
    for (const t of tables) {
      try {
        await api.post(bindUrl, {
          assetType: 'METADATA',
          assetRef: `MDS_${sid}_${t.tableName}`,
          assetName: t.tableName,
          physicalTable: t.tableName,
          metaEntryCode: sid != null ? `MDS_${sid}` : undefined,
          dataLayer: inferBindDataLayer(t.tableName),
          dimGroup: 'DATATYPE',
          accessMode: 'STRUCT',
        })
        ok += 1
      } catch {
        fail += 1
      }
    }
    if (ok > 0 && fail === 0) {
      ElMessage.success(ok === 1 ? '已挂载' : `已批量挂载 ${ok} 张表`)
    } else if (ok > 0) {
      ElMessage.warning(`成功挂载 ${ok} 张，失败 ${fail} 张`)
    } else {
      ElMessage.error('批量挂载失败')
      return
    }
    bindDialog.value = false
    await loadBindings(true)
    return
  }

  let body: Record<string, unknown> | null = null
  if (bindAccessMode.value === 'UNSTRUCT' && selectedDoc.value) {
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
  await api.post(bindUrl, body)
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
  const dims = defaultDimensionJson(row.mCode || row.modelCode || '')
  const groupIds = parseIndicatorGroupIds(row.dimensionJson)
  modelForm.value = {
    modelName: row.modelName || '',
    deDashboardId: '',
    dimensionJson: row.dimensionJson?.trim() ? row.dimensionJson : dims,
    description: row.description || '',
    indicatorIds: (row.indicators || []).map((i) => i.id),
    indicatorGroupIds: groupIds,
  }
  iframeSrc.value = ''
  embedMode.value = ''
  embedMessage.value = ''
  embedUrl.value = ''
  modelSamples.value = []
  indicatorTablePreviews.value = []
  modelDrawer.value = true
  if (useGroupIndicatorLib.value) {
    void loadIndicatorTablePreviews()
  } else {
    void loadModelSamples(row.id)
  }
}

/** 按模块码给出默认可编辑维度（无库内 JSON 时展示有效信息） */
function defaultDimensionJson(mCodeOrModel: string): string {
  const code = (mCodeOrModel || '').toUpperCase().replace(/^DM_/, '')
  const map: Record<string, string[]> = {
    M161: ['区县', '年龄段'], M162: ['区县', '年份'], M163: ['年龄段', '年份'], M164: ['学历', '区县'],
    M165: ['年份', '性别'], M166: ['年份', '原因'], M167: ['区县', '致贫因'], M168: ['类别', '区县'],
    M169: ['残疾类型', '区县'], M170: ['区县', '年份'], M171: ['区县', '同比期'], M172: ['区县', '同比期'],
    M173: ['行政区', '网格'], M174: ['学区', '行政区'],
    M184: ['区县', '年龄段'], M185: ['区县', '学历'], M186: ['区县', '税种'], M187: ['区县', '年份'],
    M188: ['区县', '险种'], M189: ['规模档', '区县'], M190: ['企业性质', '区县'], M191: ['产业', '区县'],
    M192: ['行业', '区县'], M175: ['区县', '年份'], M176: ['行业', '区县'], M177: ['区县', '月份'], M178: ['区县', '原因'],
    M193: ['区县', '年份'], M194: ['区县', '预算科目'], M195: ['行业', '月份'], M196: ['行业', '区县'],
    M197: ['行业', '税种'], M198: ['贸易方式', '月份'], M199: ['区县', '月份'], M200: ['行业', '规模'],
    M201: ['产业', '年份'], M202: ['区县', '同比期'], M203: ['项目类型', '同比期'],
    M204: ['行政区', '资源类型'], M205: ['事件类型', '月份'], M206: ['行政区', '事件类型'],
    M207: ['事故类型', '月份'], M208: ['行政区', '事故类型'], M209: ['学段', '区县'],
    LEG_001: ['区县', '年份'], LEG_002: ['行业', '区县'], LEG_003: ['区县', '月份'], LEG_004: ['区县', '原因'],
  }
  const dims = map[code] || ['维度1', '维度2']
  return JSON.stringify(dims)
}

async function saveModelDesign() {
  if (!editingModel.value) return
  const body: Record<string, unknown> = {
    modelName: modelForm.value.modelName,
    description: modelForm.value.description,
  }
  if (useGroupIndicatorLib.value) {
    body.dimensionJson = buildDimensionJsonWithGroups(modelForm.value.indicatorGroupIds || [])
  } else {
    body.dimensionJson = modelForm.value.dimensionJson
    body.indicatorIds = modelForm.value.indicatorIds
  }
  await api.put(`/analytics/domain/models/${editingModel.value.id}`, body)
  ElMessage.success('模型设计已保存')
  await loadDesigner(true)
  const fresh = models.value.find((m) => m.id === editingModel.value?.id)
  if (fresh) editingModel.value = fresh
  if (useGroupIndicatorLib.value) {
    modelForm.value.indicatorGroupIds = parseIndicatorGroupIds(fresh?.dimensionJson)
    await loadIndicatorTablePreviews()
  }
}

async function issueModelEmbed() {
  // 业务支撑四域已取消 DataEase 嵌入
}

function onHubSelect(key: string) {
  activeNav.value = key
  designDimFilter.value = ''
  if (key.endsWith('share')) {
    shareTab.value = 'mount'
  }
  if (key.endsWith('govern')) governTab.value = 'design'
  if (key.endsWith('collect')) collectTab.value = 'mount'
  if (key.endsWith('core')) coreTab.value = 'design'
  if (key.endsWith('internal')) internalTab.value = 'design'
  if (!applyingRoute) syncQuery()
  loadCurrentView()
}

watch(() => route.path, async () => {
  activeNav.value = ''
  zoneLoaded.value = ''
  designerLoaded.value = false
  bindings.value = []
  indicators.value = []
  indicatorGroups.value = []
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
  if ((t === 'indicators' || t === 'tasks' || t === 'models' || t === 'mount') && (isShare.value || isDesignerOnly.value)) {
    if (t === 'models' || t === 'indicators' || t === 'tasks') await loadDesigner()
    if (t === 'mount' && activeZone.value) await loadBindings()
  }
  // 进入指标库时默认落在指标组管理（与治理侧最终版能力一致）
  if (t === 'api' && isPopulation.value && isShare.value) {
    await Promise.all([loadServiceContracts(), loadBatchLedger()])
  }
})

watch(governTab, async (t) => {
  if ((!isPopulation.value && !isLegal.value) || !isGovern.value) return
  if (t === 'design') await loadZoneDimDesigns()
  if (t === 'mount') await loadBindings()
  if (t === 'verify') await loadVerifyLedger()
})

watch(collectTab, async (t) => {
  if (!isPopulation.value || !isCollect.value) return
  if (t === 'mount') await loadBindings()
})

watch(coreTab, async (t) => {
  if ((!isPopulation.value && !isLegal.value) || !isCore.value) return
  if (t === 'design') await loadZoneDimDesigns()
  if (t === 'mount') await loadBindings()
  if (t === 'storage') await loadStorageSummary()
})

watch(internalTab, async (t) => {
  if ((!isPopulation.value && !isLegal.value) || !isInternal.value) return
  if (t === 'design') await loadZoneDimDesigns()
  if (t === 'classify') await loadInternalClassifies()
  if (t === 'grant') await loadInternalGrants()
  if (t === 'mount') await loadBindings()
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
        <!-- 采集区不展示顶部七维说明卡片，仅挂载操作区 -->
        <div v-if="!isCollect" class="dim-grid">
          <div
            v-for="d in zoneDims"
            :key="d.key"
            class="dim-item"
            :class="{
              'dim-item--clickable': dimCardFilterEnabled,
              'dim-item--active': dimCardFilterEnabled && isDimCardActive(d.key),
            }"
            @click="onDimCardClick(d)"
          >
            <div class="dim-key">{{ d.key }}</div>
            <div class="dim-tip">{{ d.tip }}</div>
          </div>
        </div>

        <template v-if="useFiveZoneMount && isGovern">
          <el-tabs v-model="governTab">
            <el-tab-pane label="七维设计" name="design">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item label="维度" class="portal-field-md">
                  <el-select v-model="designDimFilter" clearable placeholder="全部">
                    <el-option v-for="d in DIM_CODE_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
                  </el-select>
                </el-form-item>
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openDesignCreate">新增设计项</el-button>
                  <el-button :loading="designLoading" @click="loadZoneDimDesigns">刷新</el-button>
                  <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
                </el-form-item>
              </el-form>
              <el-table v-loading="designLoading" :data="filteredDesignRows" stripe border class="portal-table" empty-text="暂无七维设计项">
                <el-table-column label="维度" width="110">
                  <template #default="{ row }">{{ dimCodeLabel(row.dimCode) }}</template>
                </el-table-column>
                <el-table-column prop="itemCode" label="编码" width="160" show-overflow-tooltip />
                <el-table-column prop="itemName" label="名称" min-width="160" show-overflow-tooltip />
                <el-table-column label="子类型" width="150">
                  <template #default="{ row }">{{ itemTypeLabel(row.dimCode, row.itemType) }}</template>
                </el-table-column>
                <el-table-column prop="content" label="设计说明" min-width="220" show-overflow-tooltip />
                <el-table-column label="深链" width="100">
                  <template #default="{ row }">
                    <el-button v-if="row.deepLink" link type="primary" @click="$router.push(row.deepLink)">打开</el-button>
                    <span v-else>—</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="更新时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openDesignEdit(row)">编辑</el-button>
                    <el-button link type="danger" @click="removeDesignRow(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="资产挂载" name="mount">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openBindDialog">选型挂载</el-button>
                  <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
                  <el-button @click="loadBindings(true)">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table :data="bindings" stripe border class="portal-table" size="small" empty-text="尚未挂载资产，请从候选中选型">
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
                <el-table-column prop="dataLayer" label="分层" width="90" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="挂载时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="90" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="danger" @click="unbind(row)">解除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane v-if="isPopulation || isLegal" label="校核台账" name="verify">
              <el-form inline class="portal-inline-form">
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openVerifyDialog">登记台账</el-button>
                  <el-button :loading="verifyLoading" @click="loadVerifyLedger">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table v-loading="verifyLoading" :data="verifyRows" stripe border class="portal-table" size="small" empty-text="暂无校核台账">
                <el-table-column prop="mCode" label="模块" width="80" />
                <el-table-column prop="sceneName" label="场景" min-width="140" />
                <el-table-column label="类型" width="120">
                  <template #default="{ row }">{{ statusLabel(row.checkType) }}</template>
                </el-table-column>
                <el-table-column prop="sourceDept" label="来源部门" width="120" />
                <el-table-column prop="issueSummary" label="问题摘要" min-width="180" show-overflow-tooltip />
                <el-table-column label="反馈" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="statusTagType(row.feedbackStatus)">
                      {{ statusLabel(row.feedbackStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="setVerifyFeedback(row, 'FEEDBACK')">已反馈</el-button>
                    <el-button link type="success" @click="setVerifyFeedback(row, 'CLOSED')">关闭</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>

        <template v-else-if="useFiveZoneMount && isCollect">
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

        <template v-else-if="useFiveZoneMount && isCore">
          <el-tabs v-model="coreTab">
            <el-tab-pane label="七维设计" name="design">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item label="维度" class="portal-field-md">
                  <el-select v-model="designDimFilter" clearable placeholder="全部">
                    <el-option v-for="d in DIM_CODE_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
                  </el-select>
                </el-form-item>
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openDesignCreate">新增设计项</el-button>
                  <el-button :loading="designLoading" @click="loadZoneDimDesigns">刷新</el-button>
                  <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
                </el-form-item>
              </el-form>
              <el-table v-loading="designLoading" :data="filteredDesignRows" stripe border class="portal-table" empty-text="暂无七维设计项">
                <el-table-column label="维度" width="110">
                  <template #default="{ row }">{{ dimCodeLabel(row.dimCode) }}</template>
                </el-table-column>
                <el-table-column prop="itemCode" label="编码" width="160" show-overflow-tooltip />
                <el-table-column prop="itemName" label="名称" min-width="160" show-overflow-tooltip />
                <el-table-column label="子类型" width="150">
                  <template #default="{ row }">{{ itemTypeLabel(row.dimCode, row.itemType) }}</template>
                </el-table-column>
                <el-table-column prop="content" label="设计说明" min-width="220" show-overflow-tooltip />
                <el-table-column label="深链" width="100">
                  <template #default="{ row }">
                    <el-button v-if="row.deepLink" link type="primary" @click="$router.push(row.deepLink)">打开</el-button>
                    <span v-else>—</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="更新时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openDesignEdit(row)">编辑</el-button>
                    <el-button link type="danger" @click="removeDesignRow(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="资产挂载" name="mount">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openBindDialog">选型挂载</el-button>
                  <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
                  <el-button @click="loadBindings(true)">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table :data="bindings" stripe border class="portal-table" size="small" empty-text="尚未挂载资产，请从候选中选型">
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
                <el-table-column prop="dataLayer" label="分层" width="90" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="挂载时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="90" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="danger" @click="unbind(row)">解除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane v-if="isPopulation || isLegal" label="存储/分区" name="storage">
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

        <template v-else-if="useFiveZoneMount && isInternal">
          <el-tabs v-model="internalTab">
            <el-tab-pane label="规范设计" name="design">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item label="维度" class="portal-field-md">
                  <el-select v-model="designDimFilter" clearable placeholder="全部">
                    <el-option v-for="d in DIM_CODE_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
                  </el-select>
                </el-form-item>
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openDesignCreate">新增规范项</el-button>
                  <el-button :loading="designLoading" @click="loadZoneDimDesigns">刷新</el-button>
                  <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
                </el-form-item>
              </el-form>
              <el-table v-loading="designLoading" :data="filteredDesignRows" stripe border class="portal-table" empty-text="暂无内部服务区规范项">
                <el-table-column label="维度" width="110">
                  <template #default="{ row }">{{ dimCodeLabel(row.dimCode) }}</template>
                </el-table-column>
                <el-table-column prop="itemCode" label="编码" width="160" show-overflow-tooltip />
                <el-table-column prop="itemName" label="名称" min-width="160" show-overflow-tooltip />
                <el-table-column label="子类型" width="150">
                  <template #default="{ row }">{{ itemTypeLabel(row.dimCode, row.itemType) }}</template>
                </el-table-column>
                <el-table-column prop="content" label="管理规范说明" min-width="220" show-overflow-tooltip />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="更新时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="140" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openDesignEdit(row)">编辑</el-button>
                    <el-button link type="danger" @click="removeDesignRow(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="分级分类" name="classify">
              <el-alert
                type="info"
                :closable="false"
                show-icon
                style="margin-bottom:12px"
                :title="internalClassifyAlert"
              />
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item label="敏感等级" class="portal-field-md">
                  <el-select v-model="classifyLevelFilter" clearable placeholder="全部" @change="loadInternalClassifies">
                    <el-option v-for="l in LEVEL_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
                  </el-select>
                </el-form-item>
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openClassifyCreate">新增分级</el-button>
                  <el-button :loading="classifyLoading" @click="loadInternalClassifies">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table v-loading="classifyLoading" :data="classifyRows" stripe border class="portal-table" empty-text="暂无分级分类登记">
                <el-table-column prop="assetCode" label="资产编码" min-width="150" show-overflow-tooltip />
                <el-table-column prop="assetName" label="资产名称" min-width="150" show-overflow-tooltip />
                <el-table-column label="分类" width="130">
                  <template #default="{ row }">{{ statusLabel(row.categoryCode) || row.categoryName }}</template>
                </el-table-column>
                <el-table-column label="敏感等级" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" :type="statusTagType(row.levelCode)">{{ statusLabel(row.levelCode) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="classifyBasis" label="分级依据" min-width="180" show-overflow-tooltip />
                <el-table-column prop="controlHint" label="管控要求" min-width="180" show-overflow-tooltip />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="更新时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="200" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openClassifyEdit(row)">编辑</el-button>
                    <el-button link type="primary" @click="startGrantFromClassify(row)">授权</el-button>
                    <el-button link type="danger" @click="removeClassifyRow(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="数据权限" name="grant">
              <el-alert
                type="warning"
                :closable="false"
                show-icon
                style="margin-bottom:12px"
                title="双重授权：系统管理员可授部门管理员角色，但不可独自终审本人发起的跨部门数据访问；数据访问须待部门管理员审批后生效。"
              />
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item label="授权类型" class="portal-field-md">
                  <el-select v-model="grantTypeFilter" clearable placeholder="全部" @change="loadInternalGrants">
                    <el-option label="角色授权" value="ROLE_GRANT" />
                    <el-option label="数据访问授权" value="DATA_ACCESS" />
                  </el-select>
                </el-form-item>
                <el-form-item label="状态" class="portal-field-md">
                  <el-select v-model="grantStatusFilter" clearable placeholder="全部" @change="loadInternalGrants">
                    <el-option label="待审核" value="PENDING" />
                    <el-option label="已生效" value="ACTIVE" />
                    <el-option label="已驳回" value="REJECTED" />
                    <el-option label="已撤销" value="REVOKED" />
                  </el-select>
                </el-form-item>
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openGrantCreate">新增授权</el-button>
                  <el-button :loading="grantLoading" @click="loadInternalGrants">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table v-loading="grantLoading" :data="grantRows" stripe border class="portal-table" empty-text="暂无数据权限记录">
                <el-table-column label="授权类型" width="120">
                  <template #default="{ row }">{{ statusLabel(row.grantType) }}</template>
                </el-table-column>
                <el-table-column label="对象类型" width="90">
                  <template #default="{ row }">{{ statusLabel(row.granteeType) }}</template>
                </el-table-column>
                <el-table-column prop="granteeName" label="授权对象" min-width="130" show-overflow-tooltip />
                <el-table-column prop="assetName" label="资产" min-width="140" show-overflow-tooltip />
                <el-table-column label="敏感级" width="100">
                  <template #default="{ row }">{{ row.levelCode ? statusLabel(row.levelCode) : '—' }}</template>
                </el-table-column>
                <el-table-column label="授权模式" width="100">
                  <template #default="{ row }">{{ statusLabel(row.authMode) }}</template>
                </el-table-column>
                <el-table-column label="权限范围" width="110">
                  <template #default="{ row }">{{ row.permissionScope ? statusLabel(row.permissionScope) : '—' }}</template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="applicant" label="申请人" width="110" show-overflow-tooltip />
                <el-table-column label="审批时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.approvedAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="240" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="row.status === 'PENDING'" link type="primary" @click="openGrantEdit(row)">编辑</el-button>
                    <el-button v-if="row.status === 'PENDING'" link type="success" @click="decideGrant(row, 'APPROVE')">通过</el-button>
                    <el-button v-if="row.status === 'PENDING'" link type="warning" @click="decideGrant(row, 'REJECT')">驳回</el-button>
                    <el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="decideGrant(row, 'REVOKE')">撤销</el-button>
                    <el-button v-if="row.status !== 'ACTIVE'" link type="danger" @click="removeGrantRow(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="资产挂载" name="mount">
              <el-form inline class="portal-inline-form portal-inline-form--block">
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="openBindDialog">选型挂载</el-button>
                  <el-button @click="$router.push(activeZone.deepLink)">{{ activeZone.deepLabel }}</el-button>
                  <el-button @click="loadBindings(true)">刷新</el-button>
                </el-form-item>
              </el-form>
              <el-table :data="bindings" stripe border class="portal-table" size="small" empty-text="尚未挂载资产，请从候选中选型">
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
                <el-table-column prop="dataLayer" label="分层" width="90" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="挂载时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="90" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="danger" @click="unbind(row)">解除</el-button>
                  </template>
                </el-table-column>
              </el-table>
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
          <el-tab-pane label="接口/批量" name="api">
            <template v-if="isPopulation">
              <h4 class="sub-title">M159 接口契约（LEDGER 试调）</h4>
              <el-form inline class="portal-inline-form">
                <el-form-item class="portal-form-actions">
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
            <DomainIndicatorGroupManage
              v-if="useGroupIndicatorLib"
              :domain="meta.domain"
              :active="shareTab === 'indicators'"
              :scope-domain-name="indicatorScopeName"
            />
            <DomainIndicatorSqlLibrary
              v-else
              :domain="meta.domain"
              @refreshed="onIndicatorsRefreshed"
            />
          </el-tab-pane>
          <el-tab-pane label="指标任务" name="tasks">
            <DomainIndicatorTaskPanel
              v-if="useGroupIndicatorLib"
              :domain="meta.domain"
              :active="shareTab === 'tasks'"
              :scope-domain-name="indicatorScopeName"
            />
            <el-empty v-else description="当前域未配置指标任务" />
          </el-tab-pane>
          <el-tab-pane label="分析模型" name="models">
            <p class="hint">
              {{ useSelfBuiltAnalysisModel
                ? '分析模型 = 场景包（多指标 + 维度 + 自研结果表）。指标 ≠ 模型。不接 DataEase 看板。'
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
                  {{ modelLinkedLabel(row) }}
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
            <DomainIndicatorGroupManage
              v-if="useGroupIndicatorLib"
              :domain="meta.domain"
              :active="shareTab === 'indicators'"
              :scope-domain-name="indicatorScopeName"
            />
            <DomainIndicatorSqlLibrary
              v-else
              :domain="meta.domain"
              @refreshed="onIndicatorsRefreshed"
            />
          </el-tab-pane>
          <el-tab-pane label="指标任务" name="tasks">
            <DomainIndicatorTaskPanel
              v-if="useGroupIndicatorLib"
              :domain="meta.domain"
              :active="shareTab === 'tasks'"
              :scope-domain-name="indicatorScopeName"
            />
            <el-empty v-else description="当前域未配置指标任务" />
          </el-tab-pane>
          <el-tab-pane label="分析模型" name="models">
            <p class="hint">分析模型 = 场景包（多指标 + 维度 + 自研结果表）。指标 ≠ 模型。不接 DataEase 看板。</p>
            <el-table :data="models" stripe size="small">
              <el-table-column prop="modelName" label="模型名称" min-width="180" />
              <el-table-column prop="modelCode" label="编码" width="120" />
              <el-table-column label="关联指标" min-width="160">
                <template #default="{ row }">
                  {{ modelLinkedLabel(row) }}
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

      <PageCard v-else :title="pageTitle">
        <el-empty description="请从左侧选择数据区" />
      </PageCard>
    </HubSideLayout>

    <el-dialog v-model="batchDialog" title="登记批量交换台账" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="批次号"><el-input v-model="batchForm.batchCode" placeholder="可空，自动生成" /></el-form-item>
        <el-form-item label="物理表">
          <el-select
            v-model="batchForm.tableName"
            filterable
            allow-create
            default-first-option
            clearable
            :loading="batchTablesLoading"
            placeholder="输入表名筛选，或选择/新建"
            style="width:100%"
          >
            <el-option v-for="t in batchTableOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
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
            <template v-if="isLegal">
              <el-option label="M178 更新维护" value="M178" />
              <el-option label="M179 信息校核" value="M179" />
            </template>
            <template v-else>
              <el-option label="M155 更新维护" value="M155" />
              <el-option label="M156 信息校核" value="M156" />
            </template>
          </el-select>
        </el-form-item>
        <el-form-item label="场景编码"><el-input v-model="verifyForm.sceneCode" /></el-form-item>
        <el-form-item label="场景名称"><el-input v-model="verifyForm.sceneName" /></el-form-item>
        <el-form-item label="校核类型">
          <el-select v-model="verifyForm.checkType" style="width:100%">
            <el-option label="多源校核" value="MULTI_SOURCE" />
            <el-option label="基线比对" value="BASELINE" />
            <el-option label="更新登记" value="UPDATE" />
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

    <el-dialog v-model="designDialog" :title="designEditingId == null ? '新增七维设计项' : '编辑七维设计项'" width="620px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="维度" required>
          <el-select v-model="designForm.dimCode" style="width:100%" :disabled="designEditingId != null">
            <el-option v-for="d in DIM_CODE_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="designForm.itemCode" :disabled="designEditingId != null" placeholder="如 PROBLEM_DATA" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="designForm.itemName" />
        </el-form-item>
        <el-form-item label="子类型">
          <el-select v-model="designForm.itemType" clearable style="width:100%" placeholder="可选">
            <el-option v-for="t in designItemTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="设计说明">
          <el-input v-model="designForm.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="深链">
          <el-input v-model="designForm.deepLink" placeholder="如 /governance?tab=etl&etlSub=task-mgmt" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="designForm.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="designForm.status" style="width:100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="designDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDesignRow">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="classifyDialog" :title="classifyEditingId == null ? '新增分级分类' : '编辑分级分类'" width="640px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="资产编码" required>
          <el-input v-model="classifyForm.assetCode" :disabled="classifyEditingId != null" :placeholder="classifyAssetPlaceholder" />
        </el-form-item>
        <el-form-item label="资产名称" required>
          <el-input v-model="classifyForm.assetName" />
        </el-form-item>
        <el-form-item v-if="bindings.length" label="从挂载选">
          <el-select
            filterable
            clearable
            style="width:100%"
            placeholder="可选：从本区已挂载资产带入"
            @change="onClassifyPickBinding"
          >
            <el-option
              v-for="b in bindings"
              :key="b.id"
              :label="`${b.assetName}（${b.physicalTable || b.assetRef}）`"
              :value="b.physicalTable || b.assetRef"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="classifyForm.categoryCode" style="width:100%" @change="onClassifyCategoryChange">
            <el-option v-for="c in CATEGORY_OPTIONS" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="敏感等级" required>
          <el-select v-model="classifyForm.levelCode" style="width:100%" @change="onClassifyLevelChange">
            <el-option v-for="l in LEVEL_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="分级依据">
          <el-input v-model="classifyForm.classifyBasis" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="管控要求">
          <el-input v-model="classifyForm.controlHint" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="classifyForm.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="classifyForm.status" style="width:100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="classifyDialog = false">取消</el-button>
        <el-button type="primary" @click="saveClassifyRow">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="grantDialog" :title="grantEditingId == null ? '新增数据权限' : '编辑授权申请'" width="640px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="授权类型" required>
          <el-select v-model="grantForm.grantType" style="width:100%" :disabled="grantEditingId != null">
            <el-option label="角色授权" value="ROLE_GRANT" />
            <el-option label="数据访问授权" value="DATA_ACCESS" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象类型" required>
          <el-select v-model="grantForm.granteeType" style="width:100%" :disabled="grantEditingId != null">
            <el-option label="机构" value="ORG" />
            <el-option label="角色" value="ROLE" />
            <el-option label="用户" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象编码" required>
          <el-input v-model="grantForm.granteeCode" :disabled="grantEditingId != null" placeholder="如 DEPT_ADMIN / 机构编码 / 用户名" />
        </el-form-item>
        <el-form-item label="对象名称">
          <el-input v-model="grantForm.granteeName" />
        </el-form-item>
        <template v-if="grantForm.grantType === 'DATA_ACCESS'">
          <el-form-item label="资产编码" required>
            <el-input v-model="grantForm.assetCode" :disabled="grantEditingId != null" placeholder="须与分级分类资产编码一致" />
          </el-form-item>
          <el-form-item label="资产名称">
            <el-input v-model="grantForm.assetName" />
          </el-form-item>
          <el-form-item v-if="classifyRows.length" label="从分级选">
            <el-select
              filterable
              clearable
              style="width:100%"
              placeholder="从已登记分级分类带入"
              @change="onGrantPickClassify"
            >
              <el-option v-for="r in classifyRows" :key="r.id" :label="`${r.assetName}（${r.assetCode}）`" :value="r.assetCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="敏感等级">
            <el-select v-model="grantForm.levelCode" style="width:100%">
              <el-option v-for="l in LEVEL_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="权限范围">
            <el-select v-model="grantForm.permissionScope" style="width:100%">
              <el-option label="脱敏只读" value="MASKED_READ" />
              <el-option label="只读" value="READ" />
              <el-option label="导出" value="EXPORT" />
            </el-select>
          </el-form-item>
          <el-form-item label="授权模式">
            <el-select v-model="grantForm.authMode" style="width:100%">
              <el-option label="双重授权" value="DUAL" />
              <el-option label="单重授权" value="SINGLE" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="申请理由">
          <el-input v-model="grantForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="grantDialog = false">取消</el-button>
        <el-button type="primary" @click="saveGrantRow">保存</el-button>
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
            <div class="bind-card-desc">{{ structAccessDesc }}</div>
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
              <el-form-item :label="`${zoneBindLayerLabel}数据源`" class="portal-field-xl">
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
              row-key="tableName"
              max-height="320"
              :empty-text="`暂无表，请先选择 ${zoneBindLayerLabel} 数据源`"
              @selection-change="onOdsTableSelectionChange"
            >
              <el-table-column type="selection" width="48" reserve-selection />
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
            {{ collectBindConfirmLabel }}
          </el-button>
        </template>
        <template v-else>
          <el-button @click="bindDialog = false">取消</el-button>
          <el-button type="primary" :disabled="!selectedCandidate" @click="confirmBind">确认挂载</el-button>
        </template>
      </template>
    </el-dialog>

    <el-drawer v-model="modelDrawer" size="720px" :title="editingModel ? `设计：${editingModel.modelName}` : '分析模型'" destroy-on-close>
      <el-form v-if="editingModel" label-width="100px">
        <el-form-item label="模型名称">
          <el-input v-model="modelForm.modelName" />
        </el-form-item>
        <el-form-item label="关联指标">
          <el-select
            v-if="useGroupIndicatorLib"
            v-model="modelForm.indicatorGroupIds"
            multiple
            filterable
            style="width:100%"
            placeholder="选择指标（指标库中的指标组）"
          >
            <el-option
              v-for="g in indicatorGroups"
              :key="g.id"
              :label="`${g.groupName}（${g.targetTable}）`"
              :value="g.id"
            />
          </el-select>
          <el-select v-else v-model="modelForm.indicatorIds" multiple filterable style="width:100%" placeholder="选择指标">
            <el-option v-for="i in indicators" :key="i.id" :label="i.indicatorName" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!useSelfBuiltAnalysisModel" label="看板标识">
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
          <template v-if="!useSelfBuiltAnalysisModel">
            <el-button @click="issueModelEmbed">签发嵌入</el-button>
            <el-button v-if="embedUrl" @click="openPortalPreview">门户预览</el-button>
          </template>
          <el-button
            v-else
            :loading="samplesLoading"
            @click="useGroupIndicatorLib ? loadIndicatorTablePreviews() : (editingModel && loadModelSamples(editingModel.id))"
          >刷新</el-button>
        </el-form-item>
      </el-form>

      <template v-if="useSelfBuiltAnalysisModel && useGroupIndicatorLib">
        <div v-loading="samplesLoading" class="ind-table-stack">
          <el-empty
            v-if="!indicatorTablePreviews.length"
            description="请先选择关联指标，再点「刷新」查看指标表数据"
          />
          <div v-for="t in indicatorTablePreviews" :key="t.groupId" class="ind-table-block">
            <div class="ind-table-title">
              {{ t.groupName }}
              <span v-if="t.targetTable" class="ind-table-sub">（{{ t.targetTable }}）</span>
            </div>
            <el-alert
              v-if="t.message"
              type="warning"
              :closable="false"
              :title="t.message"
              style="margin-bottom: 8px"
            />
            <el-table :data="t.rows" stripe size="small" max-height="280" empty-text="暂无数据">
              <el-table-column
                v-for="col in t.columns"
                :key="col"
                :prop="col"
                :label="col"
                min-width="120"
                show-overflow-tooltip
              />
            </el-table>
          </div>
        </div>
      </template>
      <template v-else-if="useSelfBuiltAnalysisModel">
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
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}
.dim-item--clickable {
  cursor: pointer;
}
.dim-item--clickable:hover {
  border-color: var(--el-color-primary-light-5, #a0cfff);
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.dim-item--active {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
  box-shadow: inset 0 0 0 1px var(--el-color-primary-light-5, #a0cfff);
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
.ind-table-stack {
  margin-top: 8px;
  min-height: 120px;
}
.ind-table-block {
  margin-bottom: 16px;
}
.ind-table-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
}
.ind-table-sub {
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
</style>
