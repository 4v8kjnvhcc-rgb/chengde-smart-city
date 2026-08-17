<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import RcStorageLifecyclePanel from '@/views/resource/RcStorageLifecyclePanel.vue'
import RcResourceMonitorPanel from '@/views/resource/RcResourceMonitorPanel.vue'
import RcStatsAnalysisPanel from '@/views/resource/RcStatsAnalysisPanel.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
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
  backup: 'backup', archive: 'archive', destroy: 'destroy', policy: 'policy', monitor: 'monitor',
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
const lifecycleHints = ref<Record<string, string>>({})
const classifyAssetType = ref('')
const queryTableId = ref<number | undefined>()
const queryKeyword = ref('')
const queryColumn = ref('')
const queryResult = ref<Record<string, unknown> | null>(null)
const lastPolicyRun = ref<Record<string, unknown> | null>(null)

const libForm = reactive({ libName: '', libType: 'BASE', description: '', ownerOrg: '' })
const themeForm = reactive({
  themeName: '',
  libraryKind: 'THEME',
  zoneCode: 'MODULE_POPULATION',
  ownerOrg: '示范单位',
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

const allLibraries = computed(() => {
  if (!libOverview.value) return [] as Library[]
  return [
    ...((libOverview.value.baseLibraries as Library[]) || []),
    ...((libOverview.value.semiLibraries as Library[]) || []),
    ...((libOverview.value.unstructLibraries as Library[]) || []),
  ]
})

const filteredManagedTables = computed(() => {
  if (!classifyAssetType.value) return managedTables.value
  return managedTables.value.filter((t) => (t.assetType || 'BASE') === classifyAssetType.value)
})

const filteredPolicies = computed(() => {
  const all = policies.value.length
    ? policies.value
    : ((partOverview.value?.policies as Policy[]) || [])
  if (storageTab.value === 'backup') return all.filter((p) => p.actionType === 'BACKUP')
  if (storageTab.value === 'archive') return all.filter((p) => p.actionType === 'ARCHIVE')
  if (storageTab.value === 'destroy') return all.filter((p) => p.actionType === 'DESTROY')
  return all
})

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
  if (st === 'backup' || st === 'archive' || st === 'destroy') {
    const mod = st === 'backup' ? 'asset.backup' : st === 'archive' ? 'asset.archive' : 'asset.destroy'
    router.replace({ path: '/exchange/ingestion', query: { system: 'collect', module: mod } })
    applyingRoute = false
    return
  }
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
  lifecycleHints.value = (libOverview.value?.lifecycleHints as Record<string, string>) || {}
}

async function loadCandidates() {
  candidates.value = (await api.get('/resource-center/platform/managed-tables/candidates')).data || []
}

async function loadAssetModules() {
  assetModules.value = (await api.get('/resource-center/platform/asset/modules')).data || []
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
      if (storageTab.value === 'policy') {
        await Promise.all([loadManagedTablesOnly(), loadPolicies(), loadLibraryOverview()])
      } else if (storageTab.value === 'monitor') {
        await Promise.all([loadPartitionOverview(), loadPolicies(), loadPolicyRuns()])
        await loadManagedTablesOnly()
      }
    } else if (nav === 'catalog') {
      await loadCatalogSubsystems()
      if (catalogTab.value === 'register') {
        await loadManagedTablesOnly()
      } else if (catalogTab.value === 'exchange') {
        await Promise.all([loadCatalog(), loadCatalogExchangeJobs()])
      } else {
        await loadCatalog()
      }
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
  if (!manageForm.themeId) {
    const topic = themes.value.find((t) => t.libraryKind === 'TOPIC')
    const theme = themes.value.find((t) => t.libraryKind === 'THEME')
    manageForm.themeId = topic?.id || theme?.id
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
  manageForm.physicalTable = ''
  manageForm.metaEntryCode = ''
  manageForm.entryName = ''
  await Promise.all([loadLibraryOverview(), loadCandidates()])
}

function goStoragePolicy() {
  activeNav.value = 'storage'
  storageTab.value = 'policy'
  syncQuery()
  loadTabData()
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
            <el-descriptions v-if="inventory" :column="4" border size="small" style="margin-bottom:12px">
              <el-descriptions-item label="基础库">{{ invTypeCount('BASE') }} 个</el-descriptions-item>
              <el-descriptions-item label="半结构化库">{{ invTypeCount('SEMI') }} 个</el-descriptions-item>
              <el-descriptions-item label="非结构化库">{{ invTypeCount('UNSTRUCT') }} 个</el-descriptions-item>
              <el-descriptions-item label="纳管表">
                {{ inventory.managedTableCount ?? 0 }} 张 / {{ inventory.managedRecordCount ?? 0 }} 行
              </el-descriptions-item>
            </el-descriptions>
            <el-alert
              v-if="lifecycleHints.backup"
              type="warning"
              :closable="false"
              show-icon
              style="margin-bottom:12px"
              :title="`生命周期：备份 — ${lifecycleHints.backup}；归档 — ${lifecycleHints.archive || '-'}；恢复 — ${lifecycleHints.restore || '-'}；迁移 — ${lifecycleHints.migrate || '-'}`"
            />
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
                <el-button @click="goStoragePolicy">存储策略</el-button>
              </el-form-item>
            </el-form>
            <template v-if="libOverview">
              <template v-for="grp in [
                { key: 'BASE', title: '基础库（人口/法人/证照/宏观/企业/地理/部件/科技/其他/行政审批）' },
                { key: 'SEMI', title: '半结构化库' },
                { key: 'UNSTRUCT', title: '非结构化库（含文件目录/索引/分布式存储）' },
              ]" :key="grp.key">
                <el-divider content-position="left">{{ grp.title }}</el-divider>
                <el-table :data="libsOfType(grp.key)" stripe size="small" style="margin-bottom:8px">
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
                </el-table>
              </template>
              <el-divider content-position="left">纳管表（备份请到「数据备份」配置定时策略）</el-divider>
              <el-table :data="managedTables" stripe size="small">
                <el-table-column prop="physicalTable" label="物理表" min-width="140" />
                <el-table-column label="资产类型" width="110">
                  <template #default="{ row }">{{ libTypeLabel(row.assetType || row.libType) }}</template>
                </el-table-column>
                <el-table-column prop="libName" label="所属库" width="140" show-overflow-tooltip />
                <el-table-column prop="themeName" label="模块/主题" width="140" show-overflow-tooltip />
                <el-table-column prop="recordCount" label="行数" width="90" />
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
            <el-table :data="themes" stripe size="small" style="margin-bottom:16px">
              <el-table-column prop="themeCode" label="编码" width="160" />
              <el-table-column prop="themeName" label="名称" />
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
            </el-table>
            <el-divider content-position="left">物理表纳管（须选用候选 + 关联资源类型）</el-divider>
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="主题/模块" class="portal-field-lg">
                <el-select v-model="manageForm.themeId" placeholder="必选主题/专题库" filterable>
                  <el-option
                    v-for="t in themes"
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
            <el-table :data="candidates" stripe size="small" style="margin-bottom:12px">
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
              <el-form-item label="按资产类型筛选" class="portal-field-sm">
                <el-select v-model="classifyAssetType" clearable placeholder="全部">
                  <el-option label="基础库" value="BASE" />
                  <el-option label="半结构化" value="SEMI" />
                  <el-option label="非结构化" value="UNSTRUCT" />
                </el-select>
              </el-form-item>
            </el-form>
            <el-table :data="filteredManagedTables" stripe size="small">
              <el-table-column prop="physicalTable" label="物理表（资产单元）" min-width="150" />
              <el-table-column prop="metaEntryCode" label="元数据码" width="200" show-overflow-tooltip />
              <el-table-column label="资产类型" width="110">
                <template #default="{ row }">{{ libTypeLabel(row.assetType) }}</template>
              </el-table-column>
              <el-table-column prop="libName" label="所属库" width="140" show-overflow-tooltip />
              <el-table-column prop="themeName" label="所属模块/主题" min-width="140" />
              <el-table-column prop="recordCount" label="行数" width="90" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link @click="unmanageTable(row.id)">解绑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="数据资产中心模块化管理" name="modules">
<el-row :gutter="12" style="margin-bottom:16px">
              <el-col v-for="m in assetModules" :key="m.moduleCode" :xs="24" :sm="12" :md="8" style="margin-bottom:12px">
                <el-card shadow="hover" body-style="padding:12px">
                  <div style="font-weight:600;margin-bottom:6px">{{ m.moduleName }}</div>
                  <div style="color:#606266;font-size:13px;margin-bottom:8px">
                    {{ m.ownerOrg || '责任单位待定' }} · 纳管 {{ m.managedCount ?? 0 }} 表
                  </div>
                  <el-tag :type="statusTagType(m.status)" size="small">{{ statusLabel(m.status) }}</el-tag>
                  <el-table
                    v-if="m.tables && m.tables.length"
                    :data="m.tables"
                    stripe
                    size="small"
                    style="margin-top:8px"
                    max-height="160"
                  >
                    <el-table-column prop="physicalTable" label="表" show-overflow-tooltip />
                    <el-table-column prop="recordCount" label="行" width="70" />
                  </el-table>
                  <el-empty v-else description="暂无纳管表" :image-size="48" />
                </el-card>
              </el-col>
            </el-row>
            <el-divider content-position="left">全部主题/专题（按库区）</el-divider>
            <el-empty v-if="!themesByZone.length" description="暂无主题/专题库，请先在「数据资产管理与分类」创建" />
            <div v-for="group in themesByZone" :key="group.zone" style="margin-bottom:16px">
              <el-divider content-position="left">{{ zoneLabel(group.zone) }}</el-divider>
              <el-table :data="group.items" stripe size="small">
                <el-table-column prop="themeCode" label="编码" width="160" />
                <el-table-column prop="themeName" label="名称" />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">{{ kindLabel(row.libraryKind) }}</template>
                </el-table-column>
                <el-table-column prop="ownerOrg" label="责任单位" width="140" />
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
                <el-table :data="fileLibOverview?.relatedStructuredTables || []" stripe size="small">
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

      <!-- 数据库存储管理：五 Tab 对齐 V3.0 -->
      <PageCard v-else-if="activeNav === 'storage'" title="数据库存储管理">
        <el-tabs v-model="storageTab" @tab-change="onStorageTabChange">
          <el-tab-pane label="数据备份" name="backup">
            <RcStorageLifecyclePanel mode="backup" />
          </el-tab-pane>
          <el-tab-pane label="数据归档" name="archive">
            <RcStorageLifecyclePanel mode="archive" />
          </el-tab-pane>
          <el-tab-pane label="数据销毁" name="destroy">
            <RcStorageLifecyclePanel mode="destroy" />
          </el-tab-pane>
          <el-tab-pane label="执行策略管理" name="policy">
            <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px" title="备份 / 归档 / 销毁请在对应 Tab 新增策略并启动周期调度，此处仅查看与调整启停。" />
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="策略" class="portal-field-lg">
                <el-select v-model="scheduleEdit.policyId" filterable clearable placeholder="选择已有策略" @change="() => {
                  const row = policies.find((p) => p.id === scheduleEdit.policyId)
                  if (row) pickSchedule(row)
                }">
                  <el-option v-for="p in policies" :key="p.id" :label="`${p.policyName}（${statusLabel(p.actionType)}）`" :value="p.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="执行周期" class="portal-field-cron">
                <ExecCycleSelect v-model="scheduleEdit.scheduleCron" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="saveSchedule">保存并启用调度</el-button>
              </el-form-item>
            </el-form>

            <el-table :data="policies.length ? policies : ((partOverview?.policies as Policy[]) || [])" stripe size="small">
              <el-table-column prop="policyCode" label="编码" width="140" />
              <el-table-column prop="policyName" label="策略" min-width="140" />
              <el-table-column label="动作" width="90">
                <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
              </el-table-column>
              <el-table-column prop="retentionDays" label="保存天数" width="90" />
              <el-table-column label="执行周期" width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <span v-if="row.scheduleEnabled">{{ cycleLabel(row.scheduleCron) }}</span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="下次执行" width="170">
                <template #default="{ row }">{{ row.nextRunAt || '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="pickSchedule(row)">调调度</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="执行策略监控" name="monitor">
            <el-alert
              v-if="lastPolicyRun"
              :type="lastPolicyRun.status === 'LEDGER' ? 'warning' : 'success'"
              :closable="true"
              style="margin-bottom:12px"
              :title="`最近执行：${statusLabel(String(lastPolicyRun.actionType || ''))} · ${statusLabel(String(lastPolicyRun.status || ''))}`"
              @close="lastPolicyRun = null"
            >
              {{ lastPolicyRun.message || `产物行数 ${lastPolicyRun.rowCount ?? '-'}` }}
              <template v-if="lastPolicyRun.storageLocation">；位置 {{ lastPolicyRun.storageLocation }}</template>
            </el-alert>
            <el-alert
              v-if="restoreResult"
              type="success"
              :closable="true"
              style="margin-bottom:12px"
              :title="`恢复完成：${restoreResult.restoreTable || ''}`"
              @close="restoreResult = null"
            >
              {{ restoreResult.message }}（行数 {{ restoreResult.rowCount ?? '-' }}）
            </el-alert>

            <el-divider content-position="left">策略运行结果</el-divider>
            <el-table :data="policyRuns" stripe size="small">
              <el-table-column label="策略" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ policyLabel(row.policyId) }}</template>
              </el-table-column>
              <el-table-column label="动作" width="90">
                <template #default="{ row }">{{ statusLabel(row.actionType) }}</template>
              </el-table-column>
              <el-table-column label="结果" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.runStatus)" size="small">{{ statusLabel(row.runStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="rowCount" label="行数" width="80" />
              <el-table-column prop="storageLocation" label="存储位置" min-width="200" show-overflow-tooltip />
              <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
              <el-table-column prop="createdAt" label="时间" width="170" />
            </el-table>

            <el-divider content-position="left">备份/归档产物与恢复</el-divider>
            <el-table :data="artifacts" stripe size="small">
              <el-table-column label="类型" width="90">
                <template #default="{ row }">{{ statusLabel(row.artifactType || 'BACKUP') }}</template>
              </el-table-column>
              <el-table-column prop="physicalTable" label="表" width="160" />
              <el-table-column prop="fileName" label="文件" min-width="160" show-overflow-tooltip />
              <el-table-column label="存储位置" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ row.storageLocation || row.filePath || '-' }}</template>
              </el-table-column>
              <el-table-column prop="rowCount" label="行数" width="80" />
              <el-table-column prop="createdAt" label="时间" width="170" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="verifyArtifact(row.id)">校验</el-button>
                  <el-button
                    v-if="(row.artifactType || 'BACKUP') === 'BACKUP'"
                    link
                    type="success"
                    @click="restoreArtifact(row.id)"
                  >恢复</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-alert
              v-if="verifyResult"
              :type="verifyResult.match ? 'success' : 'error'"
              :closable="false"
              style="margin-top:12px"
              :title="verifyResult.match ? 'SHA-256 校验通过' : 'SHA-256 校验失败'"
            />
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <!-- 资产目录 -->
      <PageCard v-else-if="activeNav === 'catalog'" title="资产目录管理">
<el-tabs v-model="catalogTab">
          <el-tab-pane label="目录查询" name="query">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="关键词" class="portal-field-md">
                <el-input v-model="catalogFilter.q" placeholder="编码/名称" clearable @keyup.enter="loadCatalog" />
              </el-form-item>
              <el-form-item label="可见性" class="portal-field-sm">
                <el-select v-model="catalogFilter.visibility" clearable placeholder="全部">
                  <el-option label="公开" value="PUBLIC" />
                  <el-option label="未公开" value="PRIVATE" />
                </el-select>
              </el-form-item>
              <el-form-item label="子系统" class="portal-field-lg">
                <el-select v-model="catalogFilter.subsystem" clearable filterable placeholder="全部">
                  <el-option v-for="s in catalogSubsystems" :key="s.code" :label="s.name" :value="s.code" />
                </el-select>
              </el-form-item>
              <el-form-item label="审批状态" class="portal-field-sm">
                <el-select v-model="catalogFilter.publishStatus" clearable placeholder="全部">
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="待审核" value="PENDING_REVIEW" />
                  <el-option label="已公开" value="PUBLISHED" />
                  <el-option label="已驳回" value="REJECTED" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="loadCatalog">查询</el-button>
                <el-button @click="resetCatalogFilter">重置</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="catalogEntries" stripe size="small">
              <el-table-column prop="entryCode" label="编码" width="140" show-overflow-tooltip />
              <el-table-column prop="entryName" label="名称" min-width="120" show-overflow-tooltip />
              <el-table-column prop="physicalTable" label="纳管表" width="140" show-overflow-tooltip />
              <el-table-column prop="subsystemName" label="所属子系统" min-width="140" show-overflow-tooltip />
              <el-table-column label="可见性" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.visibility)" size="small">{{ statusLabel(row.visibility) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="审批" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.publishStatus)" size="small">{{ statusLabel(row.publishStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="加密" width="150">
                <template #default="{ row }">
                  <el-switch
                    v-model="row.encryptEnabled"
                    size="small"
                    inline-prompt
                    active-text="开"
                    inactive-text="关"
                    @change="saveCatalogEncrypt(row)"
                  />
                  <el-select
                    v-if="row.encryptEnabled"
                    v-model="row.encryptAlgo"
                    size="small"
                    style="width:88px;margin-left:6px"
                    @change="saveCatalogEncrypt(row)"
                  >
                    <el-option label="AES256" value="AES256" />
                    <el-option label="SM4" value="SM4" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column prop="driveTask" label="驱动任务" min-width="120" show-overflow-tooltip />
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="row.publishStatus === 'DRAFT' || row.publishStatus === 'REJECTED'"
                    link
                    type="primary"
                    @click="submitCatalogPublish(row.id)"
                  >提交公开</el-button>
                  <el-button
                    v-if="isSysAdmin && row.visibility === 'PUBLIC'"
                    link
                    type="warning"
                    @click="unpublishCatalog(row.id)"
                  >下线</el-button>
                  <span v-if="row.rejectReason" class="text-muted">{{ row.rejectReason }}</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="资源编目" name="register">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="纳管表" class="portal-field-xl">
                <el-select v-model="catalogForm.managedTableId" placeholder="输入表名筛选" filterable>
                  <el-option
                    v-for="t in managedTables"
                    :key="t.id"
                    :label="`${t.physicalTable}${t.themeName ? ' · ' + t.themeName : ''}`"
                    :value="t.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="目录名" class="portal-field-md">
                <el-input v-model="catalogForm.entryName" placeholder="可选，默认用表名" />
              </el-form-item>
              <el-form-item label="所属子系统" class="portal-field-lg">
                <el-select v-model="catalogForm.subsystemCode" filterable>
                  <el-option v-for="s in catalogSubsystems" :key="s.code" :label="s.name" :value="s.code" />
                </el-select>
              </el-form-item>
              <el-form-item label="加密控制" class="portal-field-sm">
                <el-switch v-model="catalogForm.encryptEnabled" />
              </el-form-item>
              <el-form-item v-if="catalogForm.encryptEnabled" label="算法" class="portal-field-sm">
                <el-select v-model="catalogForm.encryptAlgo">
                  <el-option label="AES256" value="AES256" />
                  <el-option label="SM4" value="SM4" />
                </el-select>
              </el-form-item>
              <el-form-item label="说明" class="portal-field-xl">
                <el-input v-model="catalogForm.description" placeholder="可选" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="createCatalog">登记编目</el-button>
              </el-form-item>
            </el-form>
</el-tab-pane>

          <el-tab-pane label="公开审批" name="approve">
<el-table :data="catalogEntries" stripe size="small">
              <el-table-column prop="entryCode" label="编码" width="140" />
              <el-table-column prop="entryName" label="名称" min-width="120" />
              <el-table-column prop="subsystemName" label="申请子系统" min-width="140" />
              <el-table-column prop="physicalTable" label="纳管表" width="140" show-overflow-tooltip />
              <el-table-column label="加密" width="100">
                <template #default="{ row }">
                  {{ row.encryptEnabled ? statusLabel(row.encryptAlgo || 'ENCRYPT') : '不加密' }}
                </template>
              </el-table-column>
              <el-table-column label="审批状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.publishStatus)" size="small">{{ statusLabel(row.publishStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <template v-if="isSysAdmin">
                    <el-button link type="success" @click="approveCatalogPublish(row.id)">通过公开</el-button>
                    <el-button link type="danger" @click="rejectCatalogPublish(row.id)">驳回</el-button>
                  </template>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="驱动交换" name="exchange">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="driveAllPublicCatalogExchange">按公开目录批量驱动交换</el-button>
                <el-button @click="loadCatalogExchangeJobs()">刷新交换台账</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="catalogEntries" stripe size="small" style="margin-bottom:16px">
              <el-table-column prop="entryCode" label="编码" width="140" />
              <el-table-column prop="entryName" label="公开目录" min-width="120" />
              <el-table-column prop="physicalTable" label="纳管表" width="140" show-overflow-tooltip />
              <el-table-column prop="driveTask" label="驱动任务" min-width="140" show-overflow-tooltip />
              <el-table-column prop="lastExchangeAt" label="最近交换" width="170" />
              <el-table-column prop="lastExchangeMessage" label="交换结果" min-width="180" show-overflow-tooltip />
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="driveCatalogExchange(row.id)">生成并交换</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-divider content-position="left">交换任务台账</el-divider>
            <el-table :data="catalogExchangeJobs" stripe size="small">
              <el-table-column prop="jobCode" label="任务编码" width="180" show-overflow-tooltip />
              <el-table-column prop="jobName" label="任务名称" min-width="140" show-overflow-tooltip />
              <el-table-column prop="physicalTable" label="源表" width="140" show-overflow-tooltip />
              <el-table-column prop="rowCount" label="行数" width="80" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.runStatus)" size="small">{{ statusLabel(row.runStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
              <el-table-column prop="createdAt" label="时间" width="170" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <!-- 数据库检索查询：数据全文检索 / 元数据检索 / 数据查询与下载 -->
      <PageCard v-else-if="activeNav === 'search'" title="数据库检索查询">
<el-tabs v-model="searchTab">
          <el-tab-pane label="数据全文检索" name="fulltext">
<el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="关键词" class="portal-field-xl">
                <el-input
                  v-model="fulltextQ"
                  placeholder="姓名 / 身份证号 / 手机号等"
                  clearable
                  @keyup.enter="doFulltextSearch"
                />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="doFulltextSearch">全文检索</el-button>
              </el-form-item>
            </el-form>
            <el-alert
              v-if="fulltextDone && fulltextHint"
              type="info"
              :closable="false"
              style="margin-bottom:12px"
              :title="fulltextHint"
            />
            <el-table v-if="fulltextDone" :data="fulltextHits" stripe size="small">
              <el-table-column prop="physicalTable" label="物理表" width="160" show-overflow-tooltip />
              <el-table-column prop="metaEntryCode" label="元数据码" width="140" show-overflow-tooltip />
              <el-table-column label="命中字段" width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ (row.matchedColumns || []).join('、') || '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="summary" label="命中摘要" min-width="220" show-overflow-tooltip />
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openFulltextDetail(row)">详情</el-button>
                  <el-button
                    link
                    type="primary"
                    @click="lockManagedTableAndQuery(row.managedTableId, fulltextQ)"
                  >
                    锁定并查询
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="输入姓名、身份证号或手机号等关键信息后检索" />
            <el-drawer v-model="fulltextDetailVisible" title="命中详情" size="480px">
              <template v-if="fulltextDetail">
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="物理表">{{ fulltextDetail.physicalTable }}</el-descriptions-item>
                  <el-descriptions-item label="元数据码">{{ fulltextDetail.metaEntryCode || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="命中字段">
                    {{ (fulltextDetail.matchedColumns || []).join('、') || '-' }}
                  </el-descriptions-item>
                </el-descriptions>
                <el-divider content-position="left">行数据</el-divider>
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item
                    v-for="(val, key) in (fulltextDetail.row || {})"
                    :key="String(key)"
                    :label="String(key)"
                  >
                    {{ val ?? '-' }}
                  </el-descriptions-item>
                </el-descriptions>
              </template>
            </el-drawer>
          </el-tab-pane>

          <el-tab-pane label="元数据检索" name="meta">
<el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="关键词" class="portal-field-md">
                <el-input v-model="metaQ" placeholder="编码/名称/表名" clearable @keyup.enter="doMetadataSearch" />
              </el-form-item>
              <el-form-item label="标签" class="portal-field-sm">
                <el-input v-model="metaTag" placeholder="标签" clearable @keyup.enter="doMetadataSearch" />
              </el-form-item>
              <el-form-item label="业务分类" class="portal-field-md">
                <el-input v-model="metaDomain" placeholder="域/分层/类型" clearable @keyup.enter="doMetadataSearch" />
              </el-form-item>
              <el-form-item label="数据项" class="portal-field-md">
                <el-input v-model="metaDataItem" placeholder="字段名/数据项" clearable @keyup.enter="doMetadataSearch" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="doMetadataSearch">元数据检索</el-button>
              </el-form-item>
            </el-form>
            <el-alert
              v-if="metaDone && metaHint"
              type="info"
              :closable="false"
              style="margin-bottom:12px"
              :title="metaHint"
            />
            <el-table v-if="metaDone" :data="metaHits" stripe size="small">
              <el-table-column prop="entryCode" label="元数据码" width="150" show-overflow-tooltip />
              <el-table-column prop="entryName" label="名称" min-width="140" show-overflow-tooltip />
              <el-table-column prop="physicalTable" label="物理表" width="150" show-overflow-tooltip />
              <el-table-column label="业务分类" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.businessDomain || row.dataLayer || '-' }}</template>
              </el-table-column>
              <el-table-column prop="tags" label="标签" width="120" show-overflow-tooltip />
              <el-table-column label="命中数据项" width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ (row.matchedDataItems || []).join('、') || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="纳管" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.managed ? 'success' : 'info'" size="small">
                    {{ row.managed ? '已纳管' : '未纳管' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140" fixed="right">
                <template #default="{ row }">
                  <el-button
                    link
                    type="primary"
                    :disabled="!row.managed"
                    @click="lockManagedTableAndQuery(row.managedTableId)"
                  >
                    锁定并查询
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="按分类、标签、数据项或关键词检索元数据" />
          </el-tab-pane>

          <el-tab-pane label="数据查询与下载" name="query">
<el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="纳管表" class="portal-field-xl">
                <el-select v-model="queryTableId" placeholder="输入表名筛选" filterable clearable>
                  <el-option
                    v-for="t in managedTables"
                    :key="t.id"
                    :label="`${t.physicalTable}${t.metaEntryCode ? ' · ' + t.metaEntryCode : ''}`"
                    :value="t.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="查询关键词" class="portal-field-md">
                <el-input v-model="queryKeyword" placeholder="可选，过滤行" clearable @keyup.enter="doQueryTable" />
              </el-form-item>
              <el-form-item label="指定列" class="portal-field-sm">
                <el-input v-model="queryColumn" placeholder="可选列名" clearable @keyup.enter="doQueryTable" />
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="doQueryTable">查询</el-button>
                <el-button :disabled="!queryResult" @click="downloadQueryCsv">下载 CSV</el-button>
              </el-form-item>
            </el-form>
            <template v-if="queryResult">
              <el-alert
                type="info"
                :closable="false"
                style="margin-bottom:12px"
                :title="`表 ${queryResult.physicalTable} · 返回 ${queryResult.rowCount} 行（上限 ${queryResult.limit}）${queryResult.filter ? ' · 条件 ' + queryResult.filter : ''}`"
              />
              <el-table :data="(queryResult.rows as Record<string, string>[]) || []" stripe size="small" max-height="420">
                <el-table-column
                  v-for="col in ((queryResult.columns as string[]) || [])"
                  :key="col"
                  :prop="col"
                  :label="col"
                  min-width="120"
                  show-overflow-tooltip
                />
              </el-table>
            </template>
            <el-empty v-else description="选择纳管表后查询预览，并可下载 CSV；也可从全文/元数据检索锁定表跳转至此" />
          </el-tab-pane>
        </el-tabs>
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
</style>
