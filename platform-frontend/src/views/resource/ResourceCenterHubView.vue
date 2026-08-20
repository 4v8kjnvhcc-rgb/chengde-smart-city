<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import RcStorageLifecyclePanel from '@/views/resource/RcStorageLifecyclePanel.vue'
import RcStorageSchedulePanel from '@/views/resource/RcStorageSchedulePanel.vue'
import RcStorageMonitorPanel from '@/views/resource/RcStorageMonitorPanel.vue'
import RcResourceMonitorPanel from '@/views/resource/RcResourceMonitorPanel.vue'
import RcStatsAnalysisPanel from '@/views/resource/RcStatsAnalysisPanel.vue'
import CatalogPortalView from '@/views/governance/catalog/CatalogPortalView.vue'
import CatalogResourceView from '@/views/governance/catalog/CatalogResourceView.vue'
import CatalogApprovalView from '@/views/governance/catalog/CatalogApprovalView.vue'
import RcAssetCatalogPanel from '@/views/resource/RcAssetCatalogPanel.vue'
import RcDbSearchPanel from '@/views/resource/RcDbSearchPanel.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'
import ExecCycleSelect from '@/views/system/ExecCycleSelect.vue'
import { useExecCycleLabel } from '@/utils/exec-cycle-label'
import { useAuthStore } from '@/stores/auth'
import { filterHubNavByPermissions, filterHubNavByMenuVisible, RESOURCE_NAV_PERMISSIONS } from '@/utils/hub-nav-permission'

/** 侧栏对齐 V3：数据资产区单入口；资源中心管理为可展开父级，下挂六模块 */
const NAV_BASE: HubNavItem[] = [
  { key: 'asset', label: '数据资产区' },
  {
    key: 'mgmt',
    label: '资源中心管理',
    children: [
      { key: 'partition', label: '分区设计管理' },
      { key: 'storage', label: '数据库存储管理' },
      { key: 'catalog', label: '资产目录管理' },
      { key: 'search', label: '数据库检索查询' },
      { key: 'stats', label: '数据库统计分析' },
      { key: 'monitor', label: '资源监控管理' },
    ],
  },
]

const auth = useAuthStore()
const { label: cycleLabel } = useExecCycleLabel()
const navItems = computed(() => {
  const byPerm = filterHubNavByPermissions(NAV_BASE, auth.permissions, RESOURCE_NAV_PERMISSIONS, {
    isSystemAdmin: auth.isSystemAdmin,
  })
  return filterHubNavByMenuVisible(byPerm, auth.menus, RESOURCE_NAV_PERMISSIONS)
})

interface Library {
  id: number
  libCode: string
  libName: string
  libType: string
  recordCount: number
  managedCount?: number
  description?: string
  ownerOrg?: string
  status?: string
}
interface Theme {
  id: number
  themeCode: string
  themeName: string
  libraryKind: string
  zoneCode?: string
  managedCount?: number
  ownerOrg?: string
  status: string
  description?: string
}
interface ManagedTable {
  id: number
  themeId: number
  libId?: number
  assetType?: string
  physicalTable: string
  metaEntryCode?: string
  themeName?: string
  libraryKind?: string
  zoneCode?: string
  libName?: string
  libType?: string
  recordCount?: number
  status: string
}
interface Candidate {
  entryCode: string
  entryName: string
  physicalTable: string
  dataLayer?: string
  managed: boolean
}
interface AssetModule {
  moduleCode: string
  moduleName: string
  zoneCode: string
  themeId?: number
  themeCode?: string
  themeName?: string
  ownerOrg?: string
  description?: string
  status?: string
  managedCount?: number
  tables?: ManagedTable[]
}
interface FileLibOverview {
  catalogCount?: number
  indexCount?: number
  documentCount?: number
  hint?: string
  catalogDocs?: { id: number; title: string; storageKey?: string; publishStatus?: string; indexStatus?: string; categoryCode?: string }[]
  indexDocs?: { id: number; title: string; storageKey?: string; publishStatus?: string; indexStatus?: string; categoryCode?: string }[]
  relatedStructuredTables?: ManagedTable[]
  catalogLib?: Library
  indexLib?: Library
}
interface Partition {
  id: number
  partitionName: string
  partitionType: string
  tableName?: string
  partitionColumn?: string
  expressionText?: string
  pretestStatus?: string
  pretestMessage?: string
  previewDdl?: string
  remark?: string
  status?: string
}
interface PartitionOp {
  id: number
  partitionDefId?: number
  managedTableId?: number
  physicalTable: string
  opType: string
  opStatus: string
  previewSql?: string
  message?: string
  createdBy?: string
  createdAt?: string
}
interface PartitionColumn {
  columnName: string
  dataType?: string
  columnSize?: number
  nullable?: boolean
}
interface LivePartitionRow {
  partitionName: string
  partitionMethod?: string
  partitionExpression?: string
  partitionDescription?: string
  tableRows?: number
  dataBytes?: number
  indexBytes?: number
  rowShare?: number
  balanceStatus?: string
  partitioned?: boolean
}
interface PartitionMonitorSummary {
  partitionDefId?: number
  partitionName?: string
  tableName?: string
  partitionType?: string
  pretestStatus?: string
  managedTableId?: number
  partitioned?: boolean
  partitionCount?: number
  alertLevel?: string
  alertMessage?: string
}
interface Policy {
  id: number
  policyName: string
  policyCode?: string
  actionType: string
  retentionDays: number
  managedTableId?: number
  storageStrategy?: string
  backupLibraryId?: number
  tableRule?: string
  compressEnabled?: number
  compressType?: string
  destroyRule?: string
  scheduleEnabled?: number
  scheduleCron?: string
  nextRunAt?: string
  lastRunAt?: string
  lastRunStatus?: string
  lastRunMessage?: string
  status?: string
}
interface Artifact {
  id: number
  artifactType?: string
  physicalTable: string
  fileName: string
  filePath?: string
  storageLocation?: string
  rowCount?: number
  byteSize?: number
  sha256?: string
  status: string
  message?: string
  createdAt?: string
}
interface PolicyRun {
  id: number
  policyId: number
  actionType: string
  runStatus: string
  rowCount?: number
  artifactId?: number
  storageLocation?: string
  message?: string
  createdBy?: string
  createdAt?: string
}
interface CatalogEntry {
  id: number
  entryCode: string
  entryName: string
  managedTableId?: number
  physicalTable?: string
  subsystemCode?: string
  subsystemName?: string
  visibility?: string
  encryptEnabled?: boolean
  encryptAlgo?: string
  publishStatus?: string
  rejectReason?: string
  description?: string
  driveTask?: string
  exchangeTaskRef?: string
  lastExchangeAt?: string
  lastExchangeMessage?: string
  status?: string
}
interface CatalogSubsystem {
  code: string
  name: string
}
interface CatalogExchangeJob {
  id: number
  catalogEntryId: number
  jobCode: string
  jobName: string
  physicalTable?: string
  rowCount?: number
  runStatus: string
  message?: string
  createdBy?: string
  createdAt?: string
}
interface SearchHit {
  libCode: string
  libName: string
  libType: string
  recordCount: number
  managedTableId?: number
  metaEntryCode?: string
  physicalTable?: string
}
interface FulltextHit {
  hitType: string
  managedTableId: number
  physicalTable: string
  metaEntryCode?: string
  matchedColumns?: string[]
  summary?: string
  row?: Record<string, string>
}
interface MetadataHit {
  hitType: string
  entryCode: string
  entryName: string
  entryType?: string
  physicalTable?: string
  dataLayer?: string
  businessDomain?: string
  tags?: string
  keywords?: string
  description?: string
  matchedDataItems?: string[]
  managed?: boolean
  managedTableId?: number | null
  recordCount?: number
}

const route = useRoute()
const router = useRouter()

/** 旧 query.tab（含 asset.* / mgmt.* / m130～m138）→ 侧栏叶子 */
const tabMap: Record<string, string> = {
  asset: 'asset',
  library: 'asset', m130: 'asset', m131: 'asset', m132: 'asset',
  'asset.libraries': 'asset', 'asset.classify': 'asset', 'asset.modules': 'asset', 'asset.files': 'asset',
  classify: 'asset', modules: 'asset', files: 'asset',
  partition: 'partition', m133: 'partition', 'mgmt.partition': 'partition',
  storage: 'storage', m134: 'storage', 'mgmt.storage': 'storage',
  catalog: 'catalog', m135: 'catalog', 'mgmt.catalog': 'catalog',
  analytics: 'search',
  search: 'search', m136: 'search', 'mgmt.search': 'search',
  stats: 'stats', m137: 'stats', 'mgmt.stats': 'stats',
  monitor: 'monitor', m138: 'monitor', 'mgmt.monitor': 'monitor',
}

const assetTabMap: Record<string, string> = {
  libraries: 'libraries', 'asset.libraries': 'libraries', library: 'libraries',
  classify: 'classify', 'asset.classify': 'classify',
  modules: 'modules', 'asset.modules': 'modules',
  files: 'files', 'asset.files': 'files',
}

const storageTabMap: Record<string, string> = {
  backup: 'backup',
  archive: 'archive',
  destroy: 'destroy',
  policy: 'policy',
  monitor: 'monitor',
  // 兼容曾用的六 Tab query
  'backup-policies': 'backup',
  'backup-artifacts': 'backup',
  'archive-policies': 'archive',
  'archive-artifacts': 'archive',
  'destroy-policies': 'destroy',
  'destroy-artifacts': 'destroy',
}

const partitionTabMap: Record<string, string> = {
  design: 'design', monitor: 'monitor', maintain: 'maintain', backup: 'backup',
}

const searchTabMap: Record<string, string> = {
  fulltext: 'fulltext', data: 'fulltext', search: 'fulltext',
  meta: 'meta', metadata: 'meta',
  query: 'query', download: 'query',
}

const catalogTabMap: Record<string, string> = {
  query: 'query', register: 'register', approve: 'approve', exchange: 'exchange',
}

const DEFAULT_NAV = 'asset'
const activeNav = ref(DEFAULT_NAV)
const assetTab = ref('libraries')
const moduleTab = ref('')
const storageTab = ref('backup')
const partitionTab = ref('design')
const searchTab = ref('fulltext')
const catalogTab = ref('query')
let applyingRoute = false

const isSysAdmin = computed(() => auth.isSystemAdmin)

const libOverview = ref<Record<string, unknown> | null>(null)
const themes = ref<Theme[]>([])
const managedTables = ref<ManagedTable[]>([])
const candidates = ref<Candidate[]>([])
const partOverview = ref<Record<string, unknown> | null>(null)
const policies = ref<Policy[]>([])
const policyRuns = ref<PolicyRun[]>([])
const catalogEntries = ref<CatalogEntry[]>([])
const catalogSubsystems = ref<CatalogSubsystem[]>([])
const catalogExchangeJobs = ref<CatalogExchangeJob[]>([])
const catalogFilter = reactive({
  q: '',
  visibility: '',
  subsystem: '',
  publishStatus: '',
})
const searchQ = ref('')
const searchHits = ref<SearchHit[]>([])
const searchDone = ref(false)
const fulltextQ = ref('')
const fulltextHits = ref<FulltextHit[]>([])
const fulltextDone = ref(false)
const fulltextHint = ref('')
const fulltextDetail = ref<FulltextHit | null>(null)
const fulltextDetailVisible = ref(false)
const metaQ = ref('')
const metaTag = ref('')
const metaDomain = ref('')
const metaDataItem = ref('')
const metaHits = ref<MetadataHit[]>([])
const metaDone = ref(false)
const metaHint = ref('')
const pretestResult = ref<Record<string, unknown> | null>(null)
const verifyResult = ref<Record<string, unknown> | null>(null)
const partitionColumns = ref<PartitionColumn[]>([])
const partitionEditId = ref<number | undefined>()
const partitionDialogVisible = ref(false)
const livePartitionResult = ref<Record<string, unknown> | null>(null)
const monitorTableId = ref<number | undefined>()
const partitionOps = ref<PartitionOp[]>([])
const migrateResult = ref<Record<string, unknown> | null>(null)
const maintainResult = ref<Record<string, unknown> | null>(null)
const unsOverview = ref<Record<string, unknown> | null>(null)
const unsDocs = ref<{ id: number; title: string; storageKey?: string; indexStatus?: string; publishStatus?: string }[]>([])
const assetModules = ref<AssetModule[]>([])
const fileLibOverview = ref<FileLibOverview | null>(null)
const fileSubTab = ref('catalog')
const inventory = ref<Record<string, unknown> | null>(null)
const classifyAssetType = ref('')
const candidateKeyword = ref('')
const candidateKeywordApplied = ref('')
const managedKeyword = ref('')
const managedKeywordApplied = ref('')
const relatedKeyword = ref('')
const relatedKeywordApplied = ref('')
const queryTableId = ref<number | undefined>()
const queryKeyword = ref('')
const queryColumn = ref('')
const queryResult = ref<Record<string, unknown> | null>(null)
const lastPolicyRun = ref<Record<string, unknown> | null>(null)

const libForm = reactive({ libName: '', libType: 'BASE', description: '', ownerOrg: '' })
const libEdit = reactive({
  visible: false,
  id: null as number | null,
  libName: '',
  libType: 'BASE',
  description: '',
  ownerOrg: '',
})
const themeForm = reactive({
  themeName: '',
  libraryKind: 'THEME',
  zoneCode: 'MODULE_POPULATION',
  ownerOrg: '示范单位',
})
const themeEdit = reactive({
  visible: false,
  id: null as number | null,
  themeName: '',
  libraryKind: 'THEME',
  zoneCode: 'MODULE_POPULATION',
  ownerOrg: '',
  description: '',
})
const managedEdit = reactive({
  visible: false,
  id: null as number | null,
  physicalTable: '',
  themeId: undefined as number | undefined,
  libId: undefined as number | undefined,
  assetType: 'BASE',
})
const manageForm = reactive({
  themeId: undefined as number | undefined,
  libId: undefined as number | undefined,
  assetType: 'BASE',
  physicalTable: '',
  metaEntryCode: '',
  entryName: '',
})
const partitionForm = reactive({
  partitionName: '',
  partitionType: 'RANGE',
  themeId: undefined as number | undefined,
  tableName: '',
  partitionColumn: '',
  expressionText: '',
  remark: '',
})
const migrateForm = reactive({
  partitionDefId: undefined as number | undefined,
  migrateAction: 'ADD',
  partitionName: 'p_new',
  detail: '',
  targetPartition: 'p_reorg',
})
const maintainForm = reactive({
  partitionDefId: undefined as number | undefined,
  managedTableId: undefined as number | undefined,
  opType: 'ANALYZE',
  artifactId: undefined as number | undefined,
  remark: '',
})
const backupPartitionForm = reactive({
  managedTableId: undefined as number | undefined,
  artifactId: undefined as number | undefined,
  remark: '',
})
const catalogForm = reactive({
  managedTableId: undefined as number | undefined,
  entryName: '',
  subsystemCode: 'RESOURCE',
  encryptEnabled: false,
  encryptAlgo: 'NONE',
  description: '',
})
const policyForm = reactive({
  policyName: '',
  actionType: 'BACKUP',
  retentionDays: 30,
  managedTableId: undefined as number | undefined,
  storageStrategy: 'LOCAL',
  backupLibraryId: undefined as number | undefined,
  tableRule: '',
  compressEnabled: true,
  compressType: 'GZIP',
  destroyRule: '',
  scheduleEnabled: false,
  scheduleCron: '0 0 2 * * ?',
})
const scheduleEdit = reactive({
  policyId: undefined as number | undefined,
  scheduleEnabled: true,
  scheduleCron: '0 0 2 * * ?',
})
const restoreResult = ref<Record<string, unknown> | null>(null)

const themesByZone = computed(() => {
  const map = new Map<string, Theme[]>()
  for (const t of themes.value) {
    const zone = t.zoneCode || '未分区'
    if (!map.has(zone)) map.set(zone, [])
    map.get(zone)!.push(t)
  }
  return [...map.entries()].map(([zone, items]) => ({ zone, items }))
})

/** 纳管下拉：数据中心模块主题优先，避免默认挂到企业主题库 */
const themesForManage = computed(() => {
  const center = themes.value.filter((t) => String(t.zoneCode || '').startsWith('MODULE_'))
  const other = themes.value.filter((t) => !String(t.zoneCode || '').startsWith('MODULE_'))
  return [...center, ...other]
})

const allLibraries = computed(() => {
  if (!libOverview.value) return [] as Library[]
  return [
    ...((libOverview.value.baseLibraries as Library[]) || []),
    ...((libOverview.value.semiLibraries as Library[]) || []),
    ...((libOverview.value.unstructLibraries as Library[]) || []),
  ]
})

const filteredManagedTables = computed(() => {
  const kw = managedKeywordApplied.value.trim().toLowerCase()
  return managedTables.value.filter((t) => {
    if (classifyAssetType.value && (t.assetType || 'BASE') !== classifyAssetType.value) return false
    if (!kw) return true
    const blob = `${t.physicalTable || ''} ${t.metaEntryCode || ''} ${t.libName || ''} ${t.themeName || ''}`.toLowerCase()
    return blob.includes(kw)
  })
})

const filteredCandidates = computed(() => {
  const kw = candidateKeywordApplied.value.trim().toLowerCase()
  if (!kw) return candidates.value
  return candidates.value.filter((row) => {
    const blob = `${row.entryName || ''} ${row.physicalTable || ''} ${row.entryCode || ''} ${row.dataLayer || ''}`.toLowerCase()
    return blob.includes(kw)
  })
})

const filteredRelatedStructuredTables = computed(() => {
  const rows = (fileLibOverview.value?.relatedStructuredTables || []) as ManagedTable[]
  const kw = relatedKeywordApplied.value.trim().toLowerCase()
  if (!kw) return rows
  return rows.filter((row) => {
    const blob = `${row.physicalTable || ''} ${row.assetType || ''} ${row.themeName || ''} ${row.metaEntryCode || ''}`.toLowerCase()
    return blob.includes(kw)
  })
})

function queryCandidates() {
  candidateKeywordApplied.value = candidateKeyword.value.trim()
}

function queryManagedTables() {
  managedKeywordApplied.value = managedKeyword.value.trim()
}

function queryRelatedStructuredTables() {
  relatedKeywordApplied.value = relatedKeyword.value.trim()
}

const artifacts = computed(() => ((partOverview.value?.artifacts as Artifact[]) || []))
const partitionList = computed(() => ((partOverview.value?.partitions as Partition[]) || []))
const partitionMonitorSummary = computed(
  () => ((partOverview.value?.monitorSummary as PartitionMonitorSummary[]) || []),
)
const expressionPlaceholder = computed(() => {
  if (partitionForm.partitionType === 'HASH') return '如 PARTITIONS 4 或 4'
  if (partitionForm.partitionType === 'LIST') return '如 IN (130800,130801)'
  return '如 VALUES LESS THAN (2025-01-01)'
})

const kindLabel = (kind?: string) => {
  if (kind === 'TOPIC') return '专题库'
  if (kind === 'THEME') return '主题库'
  return kind ? statusLabel(kind) : '-'
}

const libTypeLabel = (t?: string) => {
  if (t === 'BASE') return '基础库'
  if (t === 'SEMI') return '半结构化库'
  if (t === 'UNSTRUCT') return '非结构化库'
  return t ? statusLabel(t) : '-'
}

const zoneLabel = (zone?: string) => {
  const map: Record<string, string> = {
    MODULE_POPULATION: '人口库数据中心',
    MODULE_LEGAL: '法人库数据中心',
    MODULE_LICENSE: '电子证照库数据中心',
    MODULE_MACRO: '宏观经济库数据中心',
    MODULE_ENTERPRISE: '企业经济库数据中心',
    MODULE_GEO: '地理信息库数据中心',
    MODULE_CITYPART: '城市部件库数据中心',
    MODULE_TECH: '科技资源库数据中心',
    MODULE_OTHER: '其他业务基础库数据中心',
    MODULE_APPROVAL: '行政审批库数据中心',
    ZONE_THEME: '主题库区',
    ZONE_TOPIC: '专题库区',
  }
  return (zone && map[zone]) || zone || '-'
}

const hitTypeLabel = (t: string) => {
  if (t === 'MANAGED') return '纳管表'
  if (t === 'METADATA') return '元数据'
  return statusLabel(t)
}

function libsOfType(type: string): Library[] {
  if (!libOverview.value) return []
  if (type === 'BASE') return (libOverview.value.baseLibraries as Library[]) || []
  if (type === 'SEMI') return (libOverview.value.semiLibraries as Library[]) || []
  return (libOverview.value.unstructLibraries as Library[]) || []
}

function invTypeCount(type: string): number {
  const by = inventory.value?.byLibraryType as Record<string, { libraryCount?: number }> | undefined
  return by?.[type]?.libraryCount ?? 0
}

function invAssetTables(type: string): number {
  const by = inventory.value?.byAssetTypeTables as Record<string, number> | undefined
  return by?.[type] ?? 0
}

function resolveFromRoute() {
  applyingRoute = true
  const q = String(route.query.tab || 'asset').toLowerCase()
  activeNav.value = tabMap[q] || DEFAULT_NAV
  const at = String(route.query.assetTab || route.query.sub || '').toLowerCase()
  if (at && assetTabMap[at]) assetTab.value = assetTabMap[at]
  else if (q.startsWith('asset.') && assetTabMap[q]) assetTab.value = assetTabMap[q]
  const st = String(route.query.storageTab || '').toLowerCase()
  if (st && storageTabMap[st]) storageTab.value = storageTabMap[st]
  const pt = String(route.query.partitionTab || '').toLowerCase()
  if (pt && partitionTabMap[pt]) partitionTab.value = partitionTabMap[pt]
  const qt = String(route.query.searchTab || '').toLowerCase()
  if (qt && searchTabMap[qt]) searchTab.value = searchTabMap[qt]
  const ct = String(route.query.catalogTab || '').toLowerCase()
  if (ct && catalogTabMap[ct]) catalogTab.value = catalogTabMap[ct]
  nextTick(() => { applyingRoute = false })
}

function syncQuery() {
  const q: Record<string, string> = {}
  for (const [k, v] of Object.entries(route.query)) {
    if (v == null || k === 'tab' || k === 'assetTab' || k === 'storageTab' || k === 'partitionTab' || k === 'searchTab' || k === 'catalogTab' || k === 'sub') continue
    q[k] = Array.isArray(v) ? String(v[0]) : String(v)
  }
  q.tab = activeNav.value
  if (activeNav.value === 'asset') q.assetTab = assetTab.value
  if (activeNav.value === 'storage') q.storageTab = storageTab.value
  if (activeNav.value === 'partition') q.partitionTab = partitionTab.value
  if (activeNav.value === 'search') q.searchTab = searchTab.value
  if (activeNav.value === 'catalog') q.catalogTab = catalogTab.value
  router.replace({ query: q })
}

async function loadLibraryOverview() {
  libOverview.value = (await api.get('/resource-center/platform/libraries/overview')).data
  themes.value = (libOverview.value?.themes as Theme[]) || []
  managedTables.value = (libOverview.value?.managedTables as ManagedTable[]) || []
  assetModules.value = (libOverview.value?.modules as AssetModule[]) || []
  inventory.value = (libOverview.value?.inventory as Record<string, unknown>) || null
}

async function loadCandidates() {
  candidates.value = (await api.get('/resource-center/platform/managed-tables/candidates')).data || []
}

async function loadAssetModules() {
  assetModules.value = (await api.get('/resource-center/platform/asset/modules')).data || []
  if (!moduleTab.value || !assetModules.value.some((m) => m.moduleCode === moduleTab.value)) {
    const withTables = assetModules.value.find((m) => (m.managedCount ?? 0) > 0)
    moduleTab.value = withTables?.moduleCode || assetModules.value[0]?.moduleCode || ''
  }
}

async function loadFileLibraries() {
  fileLibOverview.value = (await api.get('/resource-center/platform/asset/file-libraries')).data
}

async function loadPartitionOverview() {
  partOverview.value = (await api.get('/resource-center/platform/partition/overview')).data
  partitionOps.value = (partOverview.value?.ops as PartitionOp[]) || []
}

async function loadPartitionOps() {
  partitionOps.value = (await api.get('/resource-center/platform/partitions/ops')).data || []
}

async function loadPartitionColumnsByTable(tableName: string) {
  partitionColumns.value = []
  const mt = managedTables.value.find((t) => t.physicalTable === tableName)
  if (!mt) return
  partitionColumns.value = (await api.get(`/resource-center/platform/managed-tables/${mt.id}/columns`)).data || []
}

async function loadPartitionTabData() {
  const tab = partitionTab.value
  if (tab === 'design') {
    await Promise.all([loadPartitionOverview(), loadManagedTablesOnly()])
    if (partitionForm.tableName) await loadPartitionColumnsByTable(partitionForm.tableName)
  } else if (tab === 'monitor') {
    await Promise.all([loadPartitionOverview(), loadManagedTablesOnly()])
  } else if (tab === 'maintain') {
    await Promise.all([loadPartitionOverview(), loadManagedTablesOnly(), loadPartitionOps()])
  } else if (tab === 'backup') {
    await Promise.all([loadPartitionOverview(), loadManagedTablesOnly()])
  }
}

async function loadPolicies(actionType?: string) {
  policies.value = (await api.get('/resource-center/platform/policies', {
    params: actionType ? { actionType } : {},
  })).data || []
}

async function loadPolicyRuns(policyId?: number) {
  policyRuns.value = (await api.get('/resource-center/platform/policies/runs', {
    params: policyId ? { policyId } : {},
  })).data || []
}

async function loadCatalog() {
  const params: Record<string, string> = {}
  if (catalogTab.value === 'approve') {
    params.publishStatus = 'PENDING_REVIEW'
  } else if (catalogTab.value === 'exchange') {
    params.visibility = 'PUBLIC'
    params.publishStatus = 'PUBLISHED'
  } else {
    if (catalogFilter.q.trim()) params.q = catalogFilter.q.trim()
    if (catalogFilter.visibility) params.visibility = catalogFilter.visibility
    if (catalogFilter.subsystem) params.subsystem = catalogFilter.subsystem
    if (catalogFilter.publishStatus) params.publishStatus = catalogFilter.publishStatus
  }
  catalogEntries.value = (await api.get('/resource-center/platform/catalog/entries', { params })).data || []
}

function resetCatalogFilter() {
  catalogFilter.q = ''
  catalogFilter.visibility = ''
  catalogFilter.subsystem = ''
  catalogFilter.publishStatus = ''
  void loadCatalog()
}

async function loadCatalogSubsystems() {
  if (catalogSubsystems.value.length) return
  catalogSubsystems.value = (await api.get('/resource-center/platform/catalog/subsystems')).data || []
}

async function loadCatalogExchangeJobs(entryId?: number) {
  catalogExchangeJobs.value = (await api.get('/resource-center/platform/catalog/exchange-jobs', {
    params: entryId ? { entryId } : {},
  })).data || []
}

async function loadManagedTablesOnly() {
  managedTables.value = (await api.get('/resource-center/platform/managed-tables')).data || []
}

async function loadUnsFiles() {
  const [ov, docs] = await Promise.all([
    api.get('/unstructured/platform/overview'),
    api.get('/unstructured/platform/documents'),
  ])
  unsOverview.value = ov.data
  unsDocs.value = docs.data || []
}

async function loadTabData() {
  try {
    const nav = activeNav.value
    if (nav === 'asset') {
      if (assetTab.value === 'libraries') {
        await loadLibraryOverview()
      } else if (assetTab.value === 'modules') {
        await Promise.all([loadLibraryOverview(), loadAssetModules()])
      } else if (assetTab.value === 'classify') {
        await Promise.all([loadLibraryOverview(), loadCandidates()])
      } else if (assetTab.value === 'files') {
        await Promise.all([loadFileLibraries(), loadUnsFiles()])
      }
    } else if (nav === 'partition') {
      await loadPartitionTabData()
    } else if (nav === 'storage') {
      // backup/archive/destroy/policy/monitor 各子面板自加载
    } else if (nav === 'catalog') {
      // 目录查询/编目/审批/驱动交换由子组件按 Tab 自加载（并行≤3）
    } else if (nav === 'search') {
      if (searchTab.value === 'query') await loadManagedTablesOnly()
    } else if (nav === 'stats') {
      // 由 RcStatsAnalysisPanel 按 Tab 自加载汇总/分析/决策
    } else if (nav === 'monitor') {
      // 由 RcResourceMonitorPanel 按视图自加载 overview
    }
  } catch {
    ElMessage.error('加载失败')
  }
}

async function createLib() {
  if (!libForm.libName.trim()) {
    ElMessage.warning('请填写库名称')
    return
  }
  await api.post('/resource-center/platform/libraries', libForm)
  ElMessage.success('库已创建')
  libForm.libName = ''
  libForm.description = ''
  await loadLibraryOverview()
}

function openEditLib(row: Library) {
  libEdit.visible = true
  libEdit.id = row.id
  libEdit.libName = row.libName
  libEdit.libType = row.libType || 'BASE'
  libEdit.description = row.description || ''
  libEdit.ownerOrg = row.ownerOrg || ''
}

async function saveLibEdit() {
  if (!libEdit.id) return
  if (!libEdit.libName.trim()) {
    ElMessage.warning('请填写库名称')
    return
  }
  await api.put(`/resource-center/platform/libraries/${libEdit.id}`, {
    libName: libEdit.libName.trim(),
    libType: libEdit.libType,
    description: libEdit.description,
    ownerOrg: libEdit.ownerOrg,
  })
  ElMessage.success('库已更新')
  libEdit.visible = false
  await loadLibraryOverview()
}

async function deleteLib(row: Library) {
  try {
    await ElMessageBox.confirm(`确认删除库「${row.libName}」？须无关联纳管表。`, '删除库', { type: 'warning' })
    await api.delete(`/resource-center/platform/libraries/${row.id}`)
    ElMessage.success('库已删除')
    await loadLibraryOverview()
  } catch (e: unknown) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function createTheme() {
  if (!themeForm.themeName.trim()) {
    ElMessage.warning('请填写主题/专题库名称')
    return
  }
  await api.post('/resource-center/platform/themes', themeForm)
  ElMessage.success('主题/专题库已创建')
  themeForm.themeName = ''
  await loadLibraryOverview()
}

function openEditTheme(row: Theme) {
  themeEdit.visible = true
  themeEdit.id = row.id
  themeEdit.themeName = row.themeName
  themeEdit.libraryKind = row.libraryKind || 'THEME'
  themeEdit.zoneCode = row.zoneCode || 'MODULE_POPULATION'
  themeEdit.ownerOrg = row.ownerOrg || ''
  themeEdit.description = (row as Theme & { description?: string }).description || ''
}

async function saveThemeEdit() {
  if (!themeEdit.id) return
  if (!themeEdit.themeName.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  await api.put(`/resource-center/platform/themes/${themeEdit.id}`, {
    themeName: themeEdit.themeName.trim(),
    libraryKind: themeEdit.libraryKind,
    zoneCode: themeEdit.zoneCode,
    ownerOrg: themeEdit.ownerOrg,
    description: themeEdit.description,
  })
  ElMessage.success('主题已更新')
  themeEdit.visible = false
  await Promise.all([loadLibraryOverview(), loadAssetModules()])
}

async function deleteTheme(row: Theme) {
  await ElMessageBox.confirm(`确认删除「${row.themeName}」？须无纳管表。`, '删除主题', { type: 'warning' })
  await api.delete(`/resource-center/platform/themes/${row.id}`)
  ElMessage.success('主题已删除')
  await Promise.all([loadLibraryOverview(), loadAssetModules()])
}

function openEditManaged(row: ManagedTable) {
  managedEdit.visible = true
  managedEdit.id = row.id
  managedEdit.physicalTable = row.physicalTable
  managedEdit.themeId = row.themeId
  managedEdit.libId = row.libId
  managedEdit.assetType = row.assetType || 'BASE'
}

async function saveManagedEdit() {
  if (!managedEdit.id) return
  if (!managedEdit.themeId) {
    ElMessage.warning('请选择主题/模块')
    return
  }
  await api.put(`/resource-center/platform/managed-tables/${managedEdit.id}`, {
    themeId: managedEdit.themeId,
    libId: managedEdit.libId,
    assetType: managedEdit.assetType,
  })
  ElMessage.success('资产分类已更新')
  managedEdit.visible = false
  await Promise.all([loadLibraryOverview(), loadAssetModules()])
}

function goStorage(tab: string) {
  activeNav.value = 'storage'
  storageTab.value = tab
  syncQuery()
  loadTabData()
}

function onLibChange(libId: number | undefined) {
  manageForm.libId = libId
  const lib = allLibraries.value.find((l) => l.id === libId)
  if (lib?.libType) manageForm.assetType = lib.libType
}

function pickCandidate(row: Candidate) {
  if (row.managed) {
    ElMessage.warning('该候选表已纳管')
    return
  }
  manageForm.physicalTable = row.physicalTable
  manageForm.metaEntryCode = row.entryCode
  manageForm.entryName = row.entryName
  // 不自动改挂到「企业主题库」等无关主题；须用户显式选择数据中心对应主题/模块
  if (!manageForm.themeId) {
    ElMessage.info('请在上方「主题/模块」中选择对应数据中心（如人口库数据中心），再点「纳管」')
  }
}

async function manageTable() {
  if (!manageForm.themeId) {
    ElMessage.warning('请选择主题/专题库或数据中心模块')
    return
  }
  if (!manageForm.physicalTable || !manageForm.metaEntryCode) {
    ElMessage.warning('请从候选列表选用已登记表（含元数据码），禁止空手填写')
    return
  }
  await api.post('/resource-center/platform/managed-tables', {
    themeId: manageForm.themeId,
    libId: manageForm.libId,
    assetType: manageForm.assetType,
    physicalTable: manageForm.physicalTable,
    metaEntryCode: manageForm.metaEntryCode,
  })
  ElMessage.success('物理表已纳管')
  const theme = themes.value.find((t) => t.id === manageForm.themeId)
  if (theme && !String(theme.zoneCode || '').startsWith('MODULE_')) {
    ElMessage.warning(
      `当前挂在「${theme.themeName}」（非数据中心库区），模块化管理中请查看「未归入数据中心」；若要进人口/法人等中心，请改选对应主题后重新纳管`,
    )
  }
  manageForm.physicalTable = ''
  manageForm.metaEntryCode = ''
  manageForm.entryName = ''
  await Promise.all([loadLibraryOverview(), loadCandidates(), loadAssetModules()])
}

function goStoragePolicy() {
  goStorage('policy')
}

function goUnstructuredHub() {
  router.push({ path: '/unstructured', query: { tab: 'files' } })
}

async function backupManaged(id: number) {
  await runBackup(id)
}

async function unmanageTable(id: number) {
  try {
    await ElMessageBox.confirm('确认解绑该纳管表？', '解绑纳管', { type: 'warning' })
    await api.delete(`/resource-center/platform/managed-tables/${id}`)
    ElMessage.success('已解绑纳管')
    await loadLibraryOverview()
  } catch { /* cancel */ }
}

async function runBackup(id: number) {
  const res = await api.post(`/resource-center/platform/managed-tables/${id}/backup`, { retentionDays: 30 })
  ElMessage.success(`备份完成：${res.data?.rowCount ?? '-'} 行`)
  if (activeNav.value === 'storage' || activeNav.value === 'partition') {
    await loadPartitionOverview()
  }
}

async function createPartition() {
  if (!partitionForm.partitionName.trim()) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if (!partitionForm.tableName) {
    ElMessage.warning('请从已纳管表中选择目标表')
    return
  }
  if (!partitionForm.partitionColumn) {
    ElMessage.warning('请选择分区键列')
    return
  }
  const mt = managedTables.value.find((t) => t.physicalTable === partitionForm.tableName)
  const body = {
    ...partitionForm,
    themeId: mt?.themeId || partitionForm.themeId,
  }
  if (partitionEditId.value) {
    await api.put(`/resource-center/platform/partitions/${partitionEditId.value}`, body)
    ElMessage.success('分区策略已更新，请重新预检')
  } else {
    await api.post('/resource-center/platform/partitions', body)
    ElMessage.success('分区策略已创建')
  }
  partitionDialogVisible.value = false
  resetPartitionForm()
  await loadPartitionOverview()
}

function openPartitionCreate() {
  resetPartitionForm()
  partitionDialogVisible.value = true
}

function resetPartitionForm() {
  partitionEditId.value = undefined
  partitionForm.partitionName = ''
  partitionForm.partitionType = 'RANGE'
  partitionForm.tableName = ''
  partitionForm.partitionColumn = ''
  partitionForm.expressionText = ''
  partitionForm.remark = ''
  partitionColumns.value = []
}

function editPartition(row: Partition) {
  partitionEditId.value = row.id
  partitionForm.partitionName = row.partitionName
  partitionForm.partitionType = row.partitionType || 'RANGE'
  partitionForm.tableName = row.tableName || ''
  partitionForm.partitionColumn = row.partitionColumn || ''
  partitionForm.expressionText = row.expressionText || ''
  partitionForm.remark = row.remark || ''
  if (partitionForm.tableName) loadPartitionColumnsByTable(partitionForm.tableName)
  partitionTab.value = 'design'
  partitionDialogVisible.value = true
}

async function onPartitionTableChange(tableName: string) {
  partitionForm.partitionColumn = ''
  await loadPartitionColumnsByTable(tableName)
}

async function pretestPartition(id: number) {
  const res = await api.post(`/resource-center/platform/partitions/${id}/pretest`)
  pretestResult.value = res.data
  ElMessage.success('分区预检完成（未执行物理 DDL）')
  await loadPartitionOverview()
}

async function deletePartition(id: number) {
  try {
    await ElMessageBox.confirm('确认删除该分区策略？（软删除，不执行物理 DROP PARTITION）', '删除策略', { type: 'warning' })
    await api.delete(`/resource-center/platform/partitions/${id}`)
    ElMessage.success('策略已删除')
    if (partitionEditId.value === id) resetPartitionForm()
    await loadPartitionOverview()
  } catch { /* cancel */ }
}

async function loadLivePartitions() {
  if (!monitorTableId.value) {
    ElMessage.warning('请选择纳管表')
    return
  }
  livePartitionResult.value = (await api.get(`/resource-center/platform/managed-tables/${monitorTableId.value}/partitions`)).data
}

async function submitMigrate() {
  if (!migrateForm.partitionDefId) {
    ElMessage.warning('请选择分区策略')
    return
  }
  const res = await api.post(`/resource-center/platform/partitions/${migrateForm.partitionDefId}/migrate`, {
    migrateAction: migrateForm.migrateAction,
    partitionName: migrateForm.partitionName,
    detail: migrateForm.detail || undefined,
    targetPartition: migrateForm.targetPartition || undefined,
  })
  migrateResult.value = res.data
  ElMessage.success('迁移候选 DDL 已登记（未执行）')
  await loadPartitionOps()
}

async function submitMaintainOp() {
  if (!maintainForm.opType) {
    ElMessage.warning('请选择维护类型')
    return
  }
  if (!maintainForm.partitionDefId && !maintainForm.managedTableId) {
    ElMessage.warning('请选择分区策略或纳管表')
    return
  }
  const res = await api.post('/resource-center/platform/partitions/ops', {
    partitionDefId: maintainForm.partitionDefId,
    managedTableId: maintainForm.managedTableId,
    opType: maintainForm.opType,
    artifactId: maintainForm.artifactId,
    remark: maintainForm.remark || undefined,
  })
  maintainResult.value = res.data
  ElMessage.success(String(res.data?.message || '维护操作已登记'))
  await loadPartitionOps()
}

async function runPartitionBackup() {
  if (!backupPartitionForm.managedTableId) {
    ElMessage.warning('请选择纳管表')
    return
  }
  const res = await api.post('/resource-center/platform/partitions/ops', {
    managedTableId: backupPartitionForm.managedTableId,
    opType: 'BACKUP',
    retentionDays: 30,
  })
  ElMessage.success(String(res.data?.message || '备份完成'))
  await loadPartitionOverview()
}

async function verifyPartitionArtifact(id: number) {
  const res = await api.get(`/resource-center/platform/backups/artifacts/${id}/verify`)
  verifyResult.value = res.data
  ElMessage.success(res.data?.matched ? '校验通过' : '校验未通过')
}

async function registerRestorePlan() {
  if (!backupPartitionForm.managedTableId) {
    ElMessage.warning('请选择纳管表')
    return
  }
  const res = await api.post('/resource-center/platform/partitions/ops', {
    managedTableId: backupPartitionForm.managedTableId,
    opType: 'RESTORE_PLAN',
    artifactId: backupPartitionForm.artifactId,
    remark: backupPartitionForm.remark || undefined,
  })
  ElMessage.success(String(res.data?.message || '恢复计划已登记'))
  await loadPartitionOps()
}

function partitionOpTypeLabel(t: string) {
  const map: Record<string, string> = {
    MIGRATE: '分区迁移',
    COMPRESS: '数据压缩',
    REBUILD_INDEX: '重建索引',
    CLEANUP: '数据清理',
    ANALYZE: '统计信息更新',
    BACKUP: '备份',
    RESTORE_PLAN: '恢复计划',
  }
  return map[t] || statusLabel(t)
}

async function createPolicy() {
  if (!policyForm.policyName.trim()) {
    ElMessage.warning('请填写策略名称')
    return
  }
  if (!policyForm.managedTableId) {
    ElMessage.warning('请选择关联纳管表')
    return
  }
  if (policyForm.scheduleEnabled && !policyForm.scheduleCron.trim()) {
    ElMessage.warning('启用周期调度时请填写 Cron')
    return
  }
  await api.post('/resource-center/platform/policies', {
    policyName: policyForm.policyName,
    actionType: policyForm.actionType,
    retentionDays: policyForm.retentionDays,
    managedTableId: policyForm.managedTableId,
    storageStrategy: policyForm.storageStrategy,
    backupLibraryId: policyForm.backupLibraryId,
    tableRule: policyForm.tableRule || undefined,
    compressEnabled: policyForm.actionType === 'ARCHIVE' ? policyForm.compressEnabled : false,
    compressType: policyForm.actionType === 'ARCHIVE' ? policyForm.compressType : 'NONE',
    destroyRule: policyForm.actionType === 'DESTROY' ? policyForm.destroyRule : undefined,
    scheduleEnabled: policyForm.scheduleEnabled,
    scheduleCron: policyForm.scheduleEnabled ? policyForm.scheduleCron : undefined,
  })
  ElMessage.success('策略已创建')
  policyForm.policyName = ''
  policyForm.tableRule = ''
  policyForm.destroyRule = ''
  await Promise.all([loadPolicies(), loadPartitionOverview()])
}

async function saveSchedule() {
  if (!scheduleEdit.policyId) {
    ElMessage.warning('请选择策略')
    return
  }
  const res = await api.put(`/resource-center/platform/policies/${scheduleEdit.policyId}/schedule`, {
    scheduleEnabled: true,
    scheduleCron: scheduleEdit.scheduleCron,
  })
  ElMessage.success(`调度已启用，下次执行 ${res.data?.nextRunAt || '-'}`)
  await loadPolicies()
}

function pickSchedule(row: Policy) {
  scheduleEdit.policyId = row.id
  scheduleEdit.scheduleEnabled = !!row.scheduleEnabled
  scheduleEdit.scheduleCron = row.scheduleCron || '0 0 2 * * ?'
}

async function runPolicy(id: number) {
  try {
    const res = await api.post(`/resource-center/platform/policies/${id}/execute`)
    lastPolicyRun.value = res.data
    const st = String(res.data?.status || '')
    if (st === 'LEDGER') {
      ElMessage.warning(String(res.data?.message || '已记台账，未改物理数据'))
    } else {
      ElMessage.success(String(res.data?.message || '策略已执行'))
    }
    await Promise.all([loadPolicies(), loadPartitionOverview(), loadPolicyRuns()])
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '策略执行失败')
    await loadPolicyRuns()
  }
}

async function verifyArtifact(id: number) {
  const res = await api.get(`/resource-center/platform/backups/artifacts/${id}/verify`)
  verifyResult.value = res.data
  ElMessage.success(res.data?.match ? '校验通过' : '校验失败')
}

async function restoreArtifact(id: number) {
  try {
    const res = await api.post(`/resource-center/platform/backups/artifacts/${id}/restore`)
    restoreResult.value = res.data
    ElMessage.success(String(res.data?.message || '恢复完成'))
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '恢复失败')
  }
}

function managedTableLabel(id?: number) {
  if (!id) return '-'
  return managedTables.value.find((t) => t.id === id)?.physicalTable || String(id)
}

function libraryLabel(id?: number) {
  if (!id) return '-'
  return allLibraries.value.find((l) => l.id === id)?.libName || String(id)
}

function policyLabel(id?: number) {
  if (!id) return '-'
  return policies.value.find((p) => p.id === id)?.policyName || String(id)
}

async function createCatalog() {
  if (!catalogForm.managedTableId) {
    ElMessage.warning('请选择已纳管表创建资产目录，禁止仅填名称')
    return
  }
  const mt = managedTables.value.find((t) => t.id === catalogForm.managedTableId)
  await api.post('/resource-center/platform/catalog/entries', {
    managedTableId: catalogForm.managedTableId,
    entryName: catalogForm.entryName.trim() || mt?.physicalTable,
    subsystemCode: catalogForm.subsystemCode,
    encryptEnabled: catalogForm.encryptEnabled,
    encryptAlgo: catalogForm.encryptEnabled ? catalogForm.encryptAlgo : 'NONE',
    description: catalogForm.description.trim() || undefined,
  })
  ElMessage.success('资产目录已登记（未公开）')
  catalogForm.entryName = ''
  catalogForm.managedTableId = undefined
  catalogForm.description = ''
  catalogForm.encryptEnabled = false
  catalogForm.encryptAlgo = 'NONE'
  catalogTab.value = 'query'
  await loadCatalog()
}

async function saveCatalogEncrypt(row: CatalogEntry) {
  await api.put(`/resource-center/platform/catalog/entries/${row.id}/encrypt`, {
    encryptEnabled: !!row.encryptEnabled,
    encryptAlgo: row.encryptEnabled ? (row.encryptAlgo || 'AES256') : 'NONE',
  })
  ElMessage.success('加密控制已更新')
  await loadCatalog()
}

async function submitCatalogPublish(id: number) {
  await api.post(`/resource-center/platform/catalog/entries/${id}/submit-publish`)
  ElMessage.success('已提交公开审批')
  await loadCatalog()
}

async function approveCatalogPublish(id: number) {
  await api.post(`/resource-center/platform/catalog/entries/${id}/approve-publish`)
  ElMessage.success('已公开，可被各子系统共享并驱动交换')
  await loadCatalog()
}

async function rejectCatalogPublish(id: number) {
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回公开申请', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '驳回原因不能为空',
    })
    await api.post(`/resource-center/platform/catalog/entries/${id}/reject-publish`, { reason: value })
    ElMessage.success('已驳回')
    await loadCatalog()
  } catch { /* cancel */ }
}

async function unpublishCatalog(id: number) {
  try {
    await ElMessageBox.confirm('确认将该目录从公开资源目录下线？', '下线公开', { type: 'warning' })
    await api.post(`/resource-center/platform/catalog/entries/${id}/unpublish`)
    ElMessage.success('已下线为未公开目录')
    await loadCatalog()
  } catch { /* cancel */ }
}

async function driveCatalogExchange(id: number) {
  const res = await api.post(`/resource-center/platform/catalog/entries/${id}/drive-exchange`)
  ElMessage.success(String(res.data?.message || '交换任务已生成'))
  await Promise.all([loadCatalog(), loadCatalogExchangeJobs()])
}

async function driveAllPublicCatalogExchange() {
  const res = await api.post('/resource-center/platform/catalog/entries/drive-exchange-batch')
  ElMessage.success(`批量驱动完成：成功 ${res.data?.success ?? 0} / 共 ${res.data?.total ?? 0}`)
  await Promise.all([loadCatalog(), loadCatalogExchangeJobs()])
}

async function doSearch() {
  const res = await api.get('/resource-center/platform/search', { params: { q: searchQ.value } })
  searchHits.value = (res.data?.hits as SearchHit[]) || []
  searchDone.value = true
  ElMessage.success(`检索完成，命中 ${searchHits.value.length} 条`)
}

async function doFulltextSearch() {
  if (!fulltextQ.value.trim()) {
    ElMessage.warning('请输入关键词（如姓名、身份证号码、手机号）')
    return
  }
  const res = await api.get('/resource-center/platform/search/fulltext', {
    params: { q: fulltextQ.value.trim() },
  })
  fulltextHits.value = (res.data?.hits as FulltextHit[]) || []
  fulltextDone.value = true
  fulltextHint.value = String(res.data?.hint || '')
  ElMessage.success(`全文检索完成，命中 ${fulltextHits.value.length} 条业务数据`)
}

async function doMetadataSearch() {
  if (!metaQ.value.trim() && !metaTag.value.trim() && !metaDomain.value.trim() && !metaDataItem.value.trim()) {
    ElMessage.warning('请至少填写关键词、标签、业务分类或数据项之一')
    return
  }
  const res = await api.get('/resource-center/platform/search/metadata', {
    params: {
      q: metaQ.value.trim() || undefined,
      tag: metaTag.value.trim() || undefined,
      domain: metaDomain.value.trim() || undefined,
      dataItem: metaDataItem.value.trim() || undefined,
    },
  })
  metaHits.value = (res.data?.hits as MetadataHit[]) || []
  metaDone.value = true
  metaHint.value = String(res.data?.hint || '')
  ElMessage.success(`元数据检索完成，命中 ${metaHits.value.length} 条`)
}

function openFulltextDetail(row: FulltextHit) {
  fulltextDetail.value = row
  fulltextDetailVisible.value = true
}

async function lockManagedTableAndQuery(managedTableId?: number | null, presetKeyword?: string) {
  if (!managedTableId) {
    ElMessage.warning('该元数据尚未纳管，请先在「数据资产区」纳管后再查询')
    return
  }
  searchTab.value = 'query'
  await loadManagedTablesOnly()
  queryTableId.value = managedTableId
  queryKeyword.value = presetKeyword?.trim() || ''
  queryColumn.value = ''
  queryResult.value = null
  if (!applyingRoute) syncQuery()
  ElMessage.success('已锁定物理表，可设置查询条件后浏览或下载')
}

async function doQueryTable() {
  if (!queryTableId.value) {
    ElMessage.warning('请选择纳管表')
    return
  }
  const res = await api.get(`/resource-center/platform/managed-tables/${queryTableId.value}/query`, {
    params: {
      limit: 100,
      keyword: queryKeyword.value.trim() || undefined,
      column: queryColumn.value.trim() || undefined,
    },
  })
  queryResult.value = res.data
  ElMessage.success(`已查询 ${res.data?.rowCount ?? 0} 行（最多 100）`)
}

function downloadQueryCsv() {
  if (!queryResult.value) {
    ElMessage.warning('请先查询')
    return
  }
  const columns = (queryResult.value.columns as string[]) || []
  const rows = (queryResult.value.rows as Record<string, string>[]) || []
  const esc = (v: unknown) => `"${String(v ?? '').replace(/"/g, '""')}"`
  const lines = [columns.map(esc).join(',')]
  for (const row of rows) {
    lines.push(columns.map((c) => esc(row[c])).join(','))
  }
  const blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${queryResult.value.physicalTable || 'query'}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

function onStorageTabChange() {
  if (!applyingRoute) syncQuery()
}

watch(activeNav, () => {
  if (!applyingRoute) syncQuery()
  loadTabData()
})
watch(assetTab, () => {
  if (!applyingRoute && activeNav.value === 'asset') {
    syncQuery()
    loadTabData()
  }
})
watch(storageTab, () => {
  if (!applyingRoute && activeNav.value === 'storage') {
    onStorageTabChange()
    loadTabData()
  }
})
watch(partitionTab, () => {
  if (!applyingRoute && activeNav.value === 'partition') {
    syncQuery()
    loadTabData()
  }
})
watch(searchTab, () => {
  if (!applyingRoute && activeNav.value === 'search') {
    syncQuery()
    loadTabData()
  }
})
watch(catalogTab, () => {
  if (!applyingRoute && activeNav.value === 'catalog') {
    syncQuery()
    loadTabData()
  }
})
watch(() => route.query.tab, () => { resolveFromRoute() })
onMounted(() => {
  resolveFromRoute()
  loadTabData()
})
</script>

<template>
  <div class="rc-hub-root">
    <HubSideLayout v-model="activeNav" :items="navItems">
      <!-- 数据资产区：四模块页内 Tab -->
      <PageCard v-if="activeNav === 'asset'" title="数据资产区">
        <el-tabs v-model="assetTab">
          <el-tab-pane label="基础库、半结构化和非结构化库管理" name="libraries">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom:12px"
              title="实现基础库、半结构化库和非结构化库的管理功能，包括数据归档、备份、恢复和迁移等。提供数据资产梳理和盘点功能，支持多角度查看和管理城市数据资源中心的资产。"
            />
            <el-descriptions v-if="inventory" :column="4" border size="small" style="margin-bottom:12px">
              <el-descriptions-item label="基础库">{{ invTypeCount('BASE') }} 个</el-descriptions-item>
              <el-descriptions-item label="半结构化库">{{ invTypeCount('SEMI') }} 个</el-descriptions-item>
              <el-descriptions-item label="非结构化库">{{ invTypeCount('UNSTRUCT') }} 个</el-descriptions-item>
              <el-descriptions-item label="纳管表">
                {{ inventory.managedTableCount ?? 0 }} 张 / {{ inventory.managedRecordCount ?? 0 }} 行
              </el-descriptions-item>
            </el-descriptions>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="库名" class="portal-field-md">
                <el-input v-model="libForm.libName" placeholder="库名称" />
              </el-form-item>
              <el-form-item label="类型" class="portal-field-sm">
                <el-select v-model="libForm.libType">
                  <el-option label="基础库" value="BASE" />
                  <el-option label="半结构化" value="SEMI" />
                  <el-option label="非结构化" value="UNSTRUCT" />
                </el-select>
              </el-form-item>
              <el-form-item label="责任单位" class="portal-field-md">
                <el-input v-model="libForm.ownerOrg" placeholder="可选" />
              </el-form-item>
              <el-form-item label="说明" class="portal-field-lg">
                <el-input v-model="libForm.description" placeholder="盘点说明" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="createLib">新增库</el-button>
                <el-button @click="goStorage('backup')">备份</el-button>
                <el-button @click="goStorage('archive')">归档</el-button>
                <el-button @click="goStorage('monitor')">恢复/监控</el-button>
                <el-button @click="goStorage('policy')">迁移/策略</el-button>
              </el-form-item>
            </el-form>
            <p class="hint" style="margin:0 0 12px;color:#606266;font-size:13px">
              盘点：上方按基础/半结构/非结构统计；下方纳管表可即时备份，归档/恢复/迁移策略在「数据库存储管理」。
            </p>
            <template v-if="libOverview">
              <template v-for="grp in [
                { key: 'BASE', title: '基础库（人口/法人/证照/宏观/企业/地理/部件/科技/其他/行政审批）' },
                { key: 'SEMI', title: '半结构化库' },
                { key: 'UNSTRUCT', title: '非结构化库（含文件目录/索引/分布式存储）' },
              ]" :key="grp.key">
                <el-divider content-position="left">{{ grp.title }}</el-divider>
                <el-table :data="libsOfType(grp.key)" stripe border size="small" style="margin-bottom:8px">
                  <el-table-column prop="libCode" label="编码" width="150" />
                  <el-table-column prop="libName" label="名称" min-width="140" />
                  <el-table-column prop="ownerOrg" label="责任单位" width="140" show-overflow-tooltip />
                  <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
                  <el-table-column prop="managedCount" label="关联表" width="80" />
                  <el-table-column prop="recordCount" label="记录数" width="90" />
                  <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                      <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="140" fixed="right">
                    <template #default="{ row }">
                      <el-button link type="primary" @click="openEditLib(row)">编辑</el-button>
                      <el-button link type="danger" @click="deleteLib(row)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </template>
              <el-divider content-position="left">纳管表（表=资产单元；可备份/解绑/调整分类）</el-divider>
              <el-table :data="managedTables" stripe border size="small">
                <el-table-column prop="physicalTable" label="物理表" min-width="140" show-overflow-tooltip />
                <el-table-column label="资产类型" width="110">
                  <template #default="{ row }">{{ libTypeLabel(row.assetType || row.libType) }}</template>
                </el-table-column>
                <el-table-column prop="libName" label="所属库" width="140" show-overflow-tooltip />
                <el-table-column prop="themeName" label="模块/主题" width="140" show-overflow-tooltip />
                <el-table-column prop="recordCount" label="行数" width="90" />
                <el-table-column label="操作" width="280" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openEditManaged(row)">调整分类</el-button>
                    <el-button link type="success" @click="backupManaged(row.id)">备份</el-button>
                    <el-button link @click="goStorage('archive')">归档</el-button>
                    <el-button link @click="goStorage('monitor')">恢复</el-button>
                    <el-button link type="danger" @click="unmanageTable(row.id)">解绑</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </el-tab-pane>

          <el-tab-pane label="数据资产管理与分类" name="classify">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="名称" class="portal-field-md">
                <el-input v-model="themeForm.themeName" placeholder="主题/专题库名称" />
              </el-form-item>
              <el-form-item label="类型" class="portal-field-sm">
                <el-select v-model="themeForm.libraryKind">
                  <el-option label="主题库" value="THEME" />
                  <el-option label="专题库" value="TOPIC" />
                </el-select>
              </el-form-item>
              <el-form-item label="库区/模块" class="portal-field-lg">
                <el-select v-model="themeForm.zoneCode" filterable>
                  <el-option
                    v-for="m in assetModules"
                    :key="m.zoneCode"
                    :label="m.moduleName"
                    :value="m.zoneCode"
                  />
                  <el-option label="主题库区" value="ZONE_THEME" />
                  <el-option label="专题库区" value="ZONE_TOPIC" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="createTheme">新增主题库</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="themes" stripe border size="small" style="margin-bottom:16px">
              <el-table-column prop="themeCode" label="编码" width="160" />
              <el-table-column prop="themeName" label="名称" min-width="140" />
              <el-table-column label="类型" width="90">
                <template #default="{ row }">{{ kindLabel(row.libraryKind) }}</template>
              </el-table-column>
              <el-table-column label="库区/模块" width="160">
                <template #default="{ row }">{{ zoneLabel(row.zoneCode) }}</template>
              </el-table-column>
              <el-table-column prop="managedCount" label="纳管表" width="80" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openEditTheme(row)">编辑</el-button>
                  <el-button link type="danger" @click="deleteTheme(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-divider content-position="left">物理表纳管（须选用候选 + 关联资源类型）</el-divider>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="主题/模块" class="portal-field-lg">
                <el-select v-model="manageForm.themeId" placeholder="必选：请选数据中心对应主题" filterable>
                  <el-option
                    v-for="t in themesForManage"
                    :key="t.id"
                    :label="`${t.themeName}（${kindLabel(t.libraryKind)} · ${zoneLabel(t.zoneCode)}）`"
                    :value="t.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="所属库" class="portal-field-lg">
                <el-select
                  :model-value="manageForm.libId"
                  clearable
                  filterable
                  placeholder="关联基础/半结构/非结构库"
                  @update:model-value="onLibChange"
                >
                  <el-option
                    v-for="l in allLibraries"
                    :key="l.id"
                    :label="`${l.libName}（${libTypeLabel(l.libType)}）`"
                    :value="l.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="资产类型" class="portal-field-sm">
                <el-select v-model="manageForm.assetType">
                  <el-option label="基础库" value="BASE" />
                  <el-option label="半结构化" value="SEMI" />
                  <el-option label="非结构化" value="UNSTRUCT" />
                </el-select>
              </el-form-item>
              <el-form-item label="已选表" class="portal-field-lg">
                <el-input
                  :model-value="manageForm.physicalTable
                    ? `${manageForm.physicalTable} / ${manageForm.metaEntryCode}`
                    : ''"
                  readonly
                  placeholder="请从下方候选选用"
                />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="manageTable">纳管</el-button>
                <el-button @click="loadCandidates">刷新候选</el-button>
              </el-form-item>
            </el-form>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="关键字" class="portal-field-lg">
                <el-input v-model="candidateKeyword" clearable placeholder="条目名称/产出表/元数据码" @keyup.enter="queryCandidates" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="queryCandidates">查询</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="filteredCandidates" stripe size="small" style="margin-bottom:12px">
              <el-table-column prop="entryName" label="条目名称" min-width="140" />
              <el-table-column prop="physicalTable" label="产出表" min-width="140" />
              <el-table-column prop="entryCode" label="元数据码" width="200" show-overflow-tooltip />
              <el-table-column label="分层" width="80">
                <template #default="{ row }">{{ row.dataLayer || '-' }}</template>
              </el-table-column>
              <el-table-column label="已纳管" width="80">
                <template #default="{ row }">{{ row.managed ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button link type="primary" :disabled="row.managed" @click="pickCandidate(row)">选用</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="关键字" class="portal-field-lg">
                <el-input v-model="managedKeyword" clearable placeholder="物理表/元数据码/所属库/模块" @keyup.enter="queryManagedTables" />
              </el-form-item>
              <el-form-item label="按资产类型筛选" class="portal-field-sm">
                <el-select v-model="classifyAssetType" clearable placeholder="全部">
                  <el-option label="基础库" value="BASE" />
                  <el-option label="半结构化" value="SEMI" />
                  <el-option label="非结构化" value="UNSTRUCT" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="queryManagedTables">查询</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="filteredManagedTables" stripe border size="small">
              <el-table-column prop="physicalTable" label="物理表（资产单元）" min-width="150" show-overflow-tooltip />
              <el-table-column prop="metaEntryCode" label="元数据码" width="200" show-overflow-tooltip />
              <el-table-column label="资产类型" width="110">
                <template #default="{ row }">{{ libTypeLabel(row.assetType) }}</template>
              </el-table-column>
              <el-table-column prop="libName" label="所属库" width="140" show-overflow-tooltip />
              <el-table-column prop="themeName" label="所属模块/主题" min-width="140" show-overflow-tooltip />
              <el-table-column prop="recordCount" label="行数" width="90" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openEditManaged(row)">调整分类</el-button>
                  <el-button link type="danger" @click="unmanageTable(row.id)">解绑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="数据资产中心模块化管理" name="modules">
            <p class="hint" style="margin:0 0 12px;color:#606266;font-size:13px">
              按数据中心 Tab 查看已纳管表。纳管时请选择名称/库区为对应中心的主题（如「人口库数据中心」）；若挂到企业主题库等，会出现在「未归入数据中心」。
            </p>
            <el-empty v-if="!assetModules.length" description="暂无数据中心模块" />
            <el-tabs v-else v-model="moduleTab" type="card" class="module-center-tabs">
              <el-tab-pane
                v-for="m in assetModules"
                :key="m.moduleCode"
                :name="m.moduleCode"
                :label="`${m.moduleName}（${m.managedCount ?? 0}）`"
              >
                <div class="module-pane-meta">
                  <el-tag :type="statusTagType(m.status)" size="small">{{ statusLabel(m.status) }}</el-tag>
                  <span>{{ m.ownerOrg || '责任单位待定' }}</span>
                  <span>纳管 {{ m.managedCount ?? 0 }} 表</span>
                </div>
                <p v-if="m.description" class="module-pane-desc">{{ m.description }}</p>
                <el-table
                  v-if="m.tables && m.tables.length"
                  :data="m.tables"
                  stripe
                  border
                  size="small"
                  class="portal-table"
                >
                  <el-table-column prop="physicalTable" label="物理表" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="metaEntryCode" label="元数据码" min-width="180" show-overflow-tooltip />
                  <el-table-column prop="themeName" label="主题/模块" min-width="140" show-overflow-tooltip />
                  <el-table-column label="资产类型" width="100">
                    <template #default="{ row }">{{ libTypeLabel(row.assetType) || '—' }}</template>
                  </el-table-column>
                  <el-table-column prop="recordCount" label="行数" width="90" />
                  <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                      <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="100" fixed="right">
                    <template #default="{ row }">
                      <el-button link type="danger" @click="unmanageTable(row.id)">解绑</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-else description="暂无纳管表，请到「数据资产管理与分类」选用候选后纳管到本中心主题" :image-size="64" />
              </el-tab-pane>
            </el-tabs>
            <el-divider content-position="left">全部主题/专题（按库区）</el-divider>
            <el-empty v-if="!themesByZone.length" description="暂无主题/专题库，请先在「数据资产管理与分类」创建" />
            <div v-for="group in themesByZone" :key="group.zone" style="margin-bottom:16px">
              <el-divider content-position="left">{{ zoneLabel(group.zone) }}</el-divider>
              <el-table :data="group.items" stripe border size="small">
                <el-table-column prop="themeCode" label="编码" width="160" />
                <el-table-column prop="themeName" label="名称" min-width="140" />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">{{ kindLabel(row.libraryKind) }}</template>
                </el-table-column>
                <el-table-column prop="ownerOrg" label="责任单位" width="140" show-overflow-tooltip />
                <el-table-column prop="managedCount" label="纳管表" width="80" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="文件目录库、文件索引库管理" name="files">
<el-descriptions :column="4" border size="small" style="margin-bottom:12px">
              <el-descriptions-item label="文档总数">{{ fileLibOverview?.documentCount ?? unsOverview?.documents ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="目录库条目">{{ fileLibOverview?.catalogCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="索引库条目">{{ fileLibOverview?.indexCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="存储/检索">
                {{ unsOverview?.seaweedHealthy ? '存储正常' : '存储异常' }} /
                {{ unsOverview?.esHealthy ? '检索正常' : '检索降级' }}
              </el-descriptions-item>
            </el-descriptions>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="goUnstructuredHub">打开非结构化文件资源</el-button>
                <el-button @click="loadFileLibraries">刷新</el-button>
              </el-form-item>
            </el-form>
            <p class="hint" style="margin:0 0 12px;color:#606266;font-size:13px">
              文件目录库维护存储键与发布态；文件索引库维护建索态；「关联结构化表」展示半结构/非结构资产类型纳管表（如证照扫描件元数据抽取表），实现非结构化与结构化互查。
            </p>
            <el-tabs v-model="fileSubTab">
              <el-tab-pane label="文件目录库" name="catalog">
                <el-table :data="fileLibOverview?.catalogDocs || unsDocs" stripe size="small">
                  <el-table-column prop="title" label="文件标题" min-width="160" />
                  <el-table-column prop="storageKey" label="目录存储键" min-width="200" show-overflow-tooltip />
                  <el-table-column prop="categoryCode" label="分类" width="120" show-overflow-tooltip />
                  <el-table-column label="发布" width="100">
                    <template #default="{ row }">
                      <el-tag :type="statusTagType(row.publishStatus)" size="small">
                        {{ statusLabel(row.publishStatus) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
              <el-tab-pane label="文件索引库" name="index">
                <el-table :data="fileLibOverview?.indexDocs || []" stripe size="small">
                  <el-table-column prop="title" label="文件标题" min-width="160" />
                  <el-table-column prop="storageKey" label="存储键" min-width="180" show-overflow-tooltip />
                  <el-table-column label="索引" width="100">
                    <template #default="{ row }">
                      <el-tag :type="statusTagType(row.indexStatus)" size="small">
                        {{ statusLabel(row.indexStatus) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="发布" width="100">
                    <template #default="{ row }">
                      <el-tag :type="statusTagType(row.publishStatus)" size="small">
                        {{ statusLabel(row.publishStatus) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty
                  v-if="!(fileLibOverview?.indexDocs || []).length"
                  description="暂无已建索文件，请在非结构化平台对文档执行建索"
                />
              </el-tab-pane>
              <el-tab-pane label="关联结构化表" name="related">
                <el-form inline class="portal-inline-form portal-inline-form--block">
                  <el-form-item label="关键字" class="portal-field-lg">
                    <el-input
                      v-model="relatedKeyword"
                      clearable
                      placeholder="物理表/模块/元数据码"
                      @keyup.enter="queryRelatedStructuredTables"
                    />
                  </el-form-item>
                  <el-form-item class="portal-form-actions">
                    <el-button type="primary" @click="queryRelatedStructuredTables">查询</el-button>
                  </el-form-item>
                </el-form>
                <el-table :data="filteredRelatedStructuredTables" stripe size="small">
                  <el-table-column prop="physicalTable" label="物理表" min-width="160" />
                  <el-table-column label="资产类型" width="110">
                    <template #default="{ row }">{{ libTypeLabel(row.assetType) }}</template>
                  </el-table-column>
                  <el-table-column prop="themeName" label="所属模块" width="160" />
                  <el-table-column prop="metaEntryCode" label="元数据码" min-width="180" show-overflow-tooltip />
                  <el-table-column prop="recordCount" label="行数" width="90" />
                </el-table>
                <el-empty
                  v-if="!(fileLibOverview?.relatedStructuredTables || []).length"
                  description="可将非结构化相关结构化表纳管为半结构化/非结构化资产类型后在此互查"
                />
              </el-tab-pane>
            </el-tabs>
          </el-tab-pane>
        </el-tabs>

        <el-dialog v-model="libEdit.visible" title="编辑库" width="480px" destroy-on-close>
          <el-form label-width="96px">
            <el-form-item label="名称" required>
              <el-input v-model="libEdit.libName" maxlength="128" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="libEdit.libType" style="width:100%">
                <el-option label="基础库" value="BASE" />
                <el-option label="半结构化" value="SEMI" />
                <el-option label="非结构化" value="UNSTRUCT" />
              </el-select>
            </el-form-item>
            <el-form-item label="责任单位">
              <el-input v-model="libEdit.ownerOrg" maxlength="128" />
            </el-form-item>
            <el-form-item label="说明">
              <el-input v-model="libEdit.description" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="libEdit.visible = false">取消</el-button>
            <el-button type="primary" @click="saveLibEdit">保存</el-button>
          </template>
        </el-dialog>

        <el-dialog v-model="themeEdit.visible" title="编辑主题/模块" width="520px" destroy-on-close>
          <el-form label-width="96px">
            <el-form-item label="名称" required>
              <el-input v-model="themeEdit.themeName" maxlength="128" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="themeEdit.libraryKind" style="width:100%">
                <el-option label="主题库" value="THEME" />
                <el-option label="专题库" value="TOPIC" />
              </el-select>
            </el-form-item>
            <el-form-item label="库区/模块">
              <el-select v-model="themeEdit.zoneCode" filterable style="width:100%">
                <el-option
                  v-for="m in assetModules.filter((x) => x.moduleCode !== 'MOD_UNASSIGNED')"
                  :key="m.zoneCode"
                  :label="m.moduleName"
                  :value="m.zoneCode"
                />
                <el-option label="主题库区" value="ZONE_THEME" />
                <el-option label="专题库区" value="ZONE_TOPIC" />
              </el-select>
            </el-form-item>
            <el-form-item label="责任单位">
              <el-input v-model="themeEdit.ownerOrg" maxlength="128" />
            </el-form-item>
            <el-form-item label="说明">
              <el-input v-model="themeEdit.description" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="themeEdit.visible = false">取消</el-button>
            <el-button type="primary" @click="saveThemeEdit">保存</el-button>
          </template>
        </el-dialog>

        <el-dialog v-model="managedEdit.visible" title="调整资产分类" width="520px" destroy-on-close>
          <p class="hint" style="margin:0 0 12px;color:#606266;font-size:13px">
            表：{{ managedEdit.physicalTable }} — 调整所属数据中心主题与资源类型库，确保分类准确。
          </p>
          <el-form label-width="110px">
            <el-form-item label="主题/模块" required>
              <el-select v-model="managedEdit.themeId" filterable style="width:100%">
                <el-option
                  v-for="t in themesForManage"
                  :key="t.id"
                  :label="`${t.themeName}（${zoneLabel(t.zoneCode)}）`"
                  :value="t.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="所属库">
              <el-select v-model="managedEdit.libId" clearable filterable style="width:100%">
                <el-option
                  v-for="l in allLibraries"
                  :key="l.id"
                  :label="`${l.libName}（${libTypeLabel(l.libType)}）`"
                  :value="l.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="资产类型">
              <el-select v-model="managedEdit.assetType" style="width:100%">
                <el-option label="基础库" value="BASE" />
                <el-option label="半结构化" value="SEMI" />
                <el-option label="非结构化" value="UNSTRUCT" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="managedEdit.visible = false">取消</el-button>
            <el-button type="primary" @click="saveManagedEdit">保存</el-button>
          </template>
        </el-dialog>
      </PageCard>

      <!-- 分区设计管理：策略 / 监控 / 维护 / 备份恢复 -->
      <PageCard v-else-if="activeNav === 'partition'" title="分区设计管理">
<el-tabs v-model="partitionTab">
          <el-tab-pane label="策略设计" name="design">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="openPartitionCreate">新增</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="partitionList" stripe size="small">
              <el-table-column prop="partitionName" label="分区策略" min-width="120" />
              <el-table-column label="类型" width="90">
                <template #default="{ row }">{{ statusLabel(row.partitionType) }}</template>
              </el-table-column>
              <el-table-column prop="tableName" label="目标表" min-width="140" show-overflow-tooltip />
              <el-table-column prop="partitionColumn" label="分区键" width="120" />
              <el-table-column prop="expressionText" label="表达式" min-width="160" show-overflow-tooltip />
              <el-table-column label="预检" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.pretestStatus)" size="small">
                    {{ statusLabel(row.pretestStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="editPartition(row)">编辑</el-button>
                  <el-button link type="primary" @click="pretestPartition(row.id)">预检</el-button>
                  <el-button link type="danger" @click="deletePartition(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-alert
              v-if="pretestResult"
              type="info"
              :closable="true"
              style="margin-top:12px"
              :title="String(pretestResult.pretestMessage || '预检完成')"
              @close="pretestResult = null"
            >
              <pre style="white-space:pre-wrap;margin:8px 0 0">{{ pretestResult.previewDdl }}</pre>
            </el-alert>
            <el-dialog
              v-model="partitionDialogVisible"
              :title="partitionEditId ? '编辑分区策略' : '新增分区策略'"
              width="560px"
              destroy-on-close
              @closed="resetPartitionForm"
            >
              <el-form label-width="88px">
                <el-form-item label="策略名" required>
                  <el-input v-model="partitionForm.partitionName" placeholder="分区策略名称" />
                </el-form-item>
                <el-form-item label="类型" required>
                  <el-select v-model="partitionForm.partitionType" style="width:100%">
                    <el-option :label="statusLabel('RANGE')" value="RANGE" />
                    <el-option :label="statusLabel('HASH')" value="HASH" />
                    <el-option :label="statusLabel('LIST')" value="LIST" />
                  </el-select>
                </el-form-item>
                <el-form-item label="目标表" required>
                  <el-select
                    v-model="partitionForm.tableName"
                    placeholder="输入表名筛选"
                    filterable
                    style="width:100%"
                    @change="onPartitionTableChange"
                  >
                    <el-option
                      v-for="t in managedTables"
                      :key="t.id"
                      :label="t.physicalTable"
                      :value="t.physicalTable"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="分区键" required>
                  <el-select
                    v-model="partitionForm.partitionColumn"
                    placeholder="选择分区列"
                    filterable
                    style="width:100%"
                    :disabled="!partitionForm.tableName"
                  >
                    <el-option
                      v-for="c in partitionColumns"
                      :key="c.columnName"
                      :label="`${c.columnName}${c.dataType ? ' (' + c.dataType + ')' : ''}`"
                      :value="c.columnName"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="表达式">
                  <el-input v-model="partitionForm.expressionText" :placeholder="expressionPlaceholder" />
                </el-form-item>
              </el-form>
              <template #footer>
                <el-button @click="partitionDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="createPartition">
                  {{ partitionEditId ? '保存' : '确定' }}
                </el-button>
              </template>
            </el-dialog>
          </el-tab-pane>

          <el-tab-pane label="分区监控" name="monitor">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="纳管表" class="portal-field-xl">
                <el-select v-model="monitorTableId" placeholder="输入表名筛选" filterable>
                  <el-option v-for="t in managedTables" :key="t.id" :label="t.physicalTable" :value="t.id" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="loadLivePartitions">刷新分区状态</el-button>
              </el-form-item>
            </el-form>
            <el-table
              v-if="partitionMonitorSummary.length"
              :data="partitionMonitorSummary"
              stripe
              size="small"
              style="margin-bottom:12px"
            >
              <el-table-column prop="partitionName" label="策略" min-width="120" />
              <el-table-column prop="tableName" label="目标表" min-width="140" />
              <el-table-column label="类型" width="90">
                <template #default="{ row }">{{ statusLabel(row.partitionType) }}</template>
              </el-table-column>
              <el-table-column label="物理分区数" width="100">
                <template #default="{ row }">{{ row.partitionCount ?? 0 }}</template>
              </el-table-column>
              <el-table-column label="告警" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.alertLevel)" size="small">
                    {{ statusLabel(row.alertLevel) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="alertMessage" label="说明" min-width="220" show-overflow-tooltip />
            </el-table>
            <el-alert
              v-if="livePartitionResult"
              :type="livePartitionResult.alertLevel === 'UNEVEN' || livePartitionResult.alertLevel === 'BLOCKED' ? 'warning' : 'info'"
              :closable="false"
              show-icon
              style="margin-bottom:12px"
              :title="String(livePartitionResult.alertMessage || '分区状态')"
            />
            <el-table
              v-if="livePartitionResult"
              :data="(livePartitionResult.partitions as LivePartitionRow[]) || []"
              stripe
              size="small"
            >
              <el-table-column prop="partitionName" label="分区名" min-width="120" />
              <el-table-column prop="partitionMethod" label="方法" width="100" />
              <el-table-column prop="partitionDescription" label="边界/描述" min-width="140" show-overflow-tooltip />
              <el-table-column prop="tableRows" label="行数" width="100" />
              <el-table-column label="占比%" width="90">
                <template #default="{ row }">{{ row.rowShare ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="均衡" width="100">
                <template #default="{ row }">
                  <el-tag v-if="row.balanceStatus" :type="statusTagType(row.balanceStatus)" size="small">
                    {{ statusLabel(row.balanceStatus) }}
                  </el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="分区维护" name="maintain">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="策略" class="portal-field-lg">
                <el-select v-model="maintainForm.partitionDefId" clearable filterable placeholder="可选">
                  <el-option
                    v-for="p in partitionList"
                    :key="p.id"
                    :label="`${p.partitionName} / ${p.tableName || '-'}`"
                    :value="p.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="纳管表" class="portal-field-lg">
                <el-select v-model="maintainForm.managedTableId" clearable filterable placeholder="输入表名筛选">
                  <el-option v-for="t in managedTables" :key="t.id" :label="t.physicalTable" :value="t.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="维护类型" class="portal-field-md">
                <el-select v-model="maintainForm.opType">
                  <el-option label="统计信息更新" value="ANALYZE" />
                  <el-option label="数据压缩" value="COMPRESS" />
                  <el-option label="重建索引" value="REBUILD_INDEX" />
                  <el-option label="数据清理" value="CLEANUP" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="submitMaintainOp">提交维护</el-button>
              </el-form-item>
            </el-form>
            <el-divider content-position="left">分区迁移（候选 DDL，不执行）</el-divider>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="策略" class="portal-field-lg">
                <el-select v-model="migrateForm.partitionDefId" filterable placeholder="选择策略">
                  <el-option
                    v-for="p in partitionList"
                    :key="p.id"
                    :label="`${p.partitionName} / ${p.tableName || '-'}`"
                    :value="p.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="动作" class="portal-field-sm">
                <el-select v-model="migrateForm.migrateAction">
                  <el-option label="新增分区" value="ADD" />
                  <el-option label="删除分区" value="DROP" />
                  <el-option label="重组分区" value="REORGANIZE" />
                </el-select>
              </el-form-item>
              <el-form-item label="分区名" class="portal-field-sm">
                <el-input v-model="migrateForm.partitionName" />
              </el-form-item>
              <el-form-item label="明细" class="portal-field-xl">
                <el-input v-model="migrateForm.detail" placeholder="如 VALUES LESS THAN (...)" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="submitMigrate">登记迁移预检</el-button>
              </el-form-item>
            </el-form>
            <el-alert
              v-if="migrateResult || maintainResult"
              type="info"
              :closable="true"
              style="margin-bottom:12px"
              :title="String((maintainResult || migrateResult)?.message || '操作完成')"
              @close="migrateResult = null; maintainResult = null"
            >
              <pre style="white-space:pre-wrap;margin:8px 0 0">{{ (maintainResult || migrateResult)?.previewSql }}</pre>
            </el-alert>
            <el-table :data="partitionOps" stripe size="small">
              <el-table-column label="类型" width="120">
                <template #default="{ row }">{{ partitionOpTypeLabel(row.opType) }}</template>
              </el-table-column>
              <el-table-column prop="physicalTable" label="目标表" min-width="140" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.opStatus)" size="small">
                    {{ statusLabel(row.opStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
              <el-table-column prop="createdBy" label="操作人" width="100" />
              <el-table-column prop="createdAt" label="时间" width="170" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="备份恢复" name="backup">
<el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="纳管表" class="portal-field-xl">
                <el-select v-model="backupPartitionForm.managedTableId" filterable placeholder="输入表名筛选">
                  <el-option v-for="t in managedTables" :key="t.id" :label="t.physicalTable" :value="t.id" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="runPartitionBackup">执行逻辑备份</el-button>
              </el-form-item>
            </el-form>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="备份产物" class="portal-field-xl">
                <el-select v-model="backupPartitionForm.artifactId" clearable filterable placeholder="选择产物登记恢复计划">
                  <el-option
                    v-for="a in artifacts"
                    :key="a.id"
                    :label="`${a.fileName} / ${a.physicalTable}`"
                    :value="a.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="恢复说明" class="portal-field-xl">
                <el-input v-model="backupPartitionForm.remark" placeholder="恢复步骤与责任人" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button @click="registerRestorePlan">登记恢复计划</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="artifacts" stripe size="small">
              <el-table-column prop="physicalTable" label="表" min-width="140" />
              <el-table-column prop="fileName" label="文件" min-width="180" show-overflow-tooltip />
              <el-table-column prop="rowCount" label="行数" width="90" />
              <el-table-column prop="sha256" label="SHA-256" min-width="160" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" @click="verifyPartitionArtifact(row.id)">校验</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-alert
              v-if="verifyResult"
              type="info"
              :closable="true"
              style="margin-top:12px"
              :title="String(verifyResult.message || (verifyResult.matched ? '校验通过' : '校验结果'))"
              @close="verifyResult = null"
            />
            <el-divider content-position="left">恢复计划台账</el-divider>
            <el-table
              :data="partitionOps.filter((o) => o.opType === 'RESTORE_PLAN' || o.opType === 'BACKUP')"
              stripe
              size="small"
            >
              <el-table-column label="类型" width="120">
                <template #default="{ row }">{{ partitionOpTypeLabel(row.opType) }}</template>
              </el-table-column>
              <el-table-column prop="physicalTable" label="目标表" min-width="140" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.opStatus)" size="small">{{ statusLabel(row.opStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
              <el-table-column prop="createdAt" label="时间" width="170" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <!-- 数据库存储管理：五 Tab（备份/归档/销毁策略 + 执行策略管理/监控） -->
      <PageCard v-else-if="activeNav === 'storage'" title="数据库存储管理">
        <el-tabs v-model="storageTab" @tab-change="onStorageTabChange">
          <el-tab-pane label="数据备份" name="backup" lazy>
            <RcStorageLifecyclePanel v-if="storageTab === 'backup'" mode="backup" section="policies" />
          </el-tab-pane>
          <el-tab-pane label="数据归档" name="archive" lazy>
            <RcStorageLifecyclePanel v-if="storageTab === 'archive'" mode="archive" section="policies" />
          </el-tab-pane>
          <el-tab-pane label="数据销毁" name="destroy" lazy>
            <RcStorageLifecyclePanel v-if="storageTab === 'destroy'" mode="destroy" section="policies" />
          </el-tab-pane>
          <el-tab-pane label="执行策略管理" name="policy" lazy>
            <RcStorageSchedulePanel v-if="storageTab === 'policy'" />
          </el-tab-pane>
          <el-tab-pane label="执行策略监控" name="monitor" lazy>
            <RcStorageMonitorPanel v-if="storageTab === 'monitor'" />
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <!-- 资产目录 -->
      <PageCard v-else-if="activeNav === 'catalog'" title="资产目录管理">
        <el-tabs v-model="catalogTab">
          <el-tab-pane label="目录查询" name="query">
            <p class="catalog-hint">门户多子系统共享同一套<strong>公开资源目录</strong>；此处查询已发布可共享资源。</p>
            <CatalogPortalView v-if="catalogTab === 'query'" embedded />
          </el-tab-pane>

          <el-tab-pane label="资源编目" name="register">
            <el-tabs type="card" class="catalog-inner">
              <el-tab-pane label="治理资源编目" name="gov">
                <CatalogResourceView v-if="catalogTab === 'register'" embedded />
              </el-tab-pane>
              <el-tab-pane label="子系统目录挂载" name="rc-mount" lazy>
                <RcAssetCatalogPanel v-if="catalogTab === 'register'" mode="mount" />
              </el-tab-pane>
            </el-tabs>
          </el-tab-pane>

          <el-tab-pane label="公开审批" name="approve">
            <el-tabs type="card" class="catalog-inner">
              <el-tab-pane label="公开目录审批" name="rc-approve">
                <p class="catalog-hint">系统管理员控制目录公开；批准后进入共享公开目录，供各子系统共用并驱动交换。</p>
                <RcAssetCatalogPanel v-if="catalogTab === 'approve'" mode="approve" />
              </el-tab-pane>
              <el-tab-pane label="资源目录审批" name="gov-approve" lazy>
                <CatalogApprovalView v-if="catalogTab === 'approve'" embedded />
              </el-tab-pane>
            </el-tabs>
          </el-tab-pane>

          <el-tab-pane label="驱动交换" name="exchange">
            <RcAssetCatalogPanel v-if="catalogTab === 'exchange'" mode="exchange" />
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <!-- 数据库检索查询：数据全文检索 / 元数据检索 / 数据查询与下载 -->
      <PageCard v-else-if="activeNav === 'search'" title="数据库检索查询">
        <RcDbSearchPanel v-model:search-tab="searchTab" />
      </PageCard>

      <!-- 统计：汇总聚合 / 趋势规律 / 决策支持 -->
      <PageCard v-else-if="activeNav === 'stats'" title="数据库统计分析">
        <RcStatsAnalysisPanel />
      </PageCard>

      <!-- 监控：可用性 / 完整性 / 安全性 / 性能 -->
      <PageCard v-else-if="activeNav === 'monitor'" title="资源监控管理">
        <RcResourceMonitorPanel />
      </PageCard>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.rc-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
.module-pane-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  color: #606266;
  font-size: 13px;
}
.module-pane-desc {
  margin: 0 0 12px;
  color: #909399;
  font-size: 12px;
}
.module-center-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.catalog-hint {
  color: var(--el-text-color-secondary);
  margin: 0 0 12px;
  line-height: 1.5;
  font-size: 13px;
}
.catalog-inner {
  margin-top: 4px;
}
}
</style>
