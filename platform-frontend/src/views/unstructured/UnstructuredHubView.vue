<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox, type UploadFile, type UploadUserFile } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { formatDateTime } from '@/utils/datetime'
import { useAuthStore } from '@/stores/auth'
import { filterHubNavByPermissions, filterHubNavByMenuVisible, UNSTRUCT_NAV_PERMISSIONS } from '@/utils/hub-nav-permission'

const props = withDefaults(
  defineProps<{
    /** 嵌入归集「非结构化数据接入」：只展示与文件资源管理·本地上传相同的界面与数据 */
    embedLocalOnly?: boolean
    /** 嵌入时 PageCard 标题，默认与文件资源管理一致 */
    embedTitle?: string
  }>(),
  { embedLocalOnly: false, embedTitle: '文件资源管理' },
)

const auth = useAuthStore()

const NAV_BASE: HubNavItem[] = [
  { key: 'files', label: '文件资源管理' },
  { key: 'classify', label: '数据分类管理' },
  { key: 'search', label: '文件资源检索' },
  { key: 'metadata', label: '非结构化元数据管理' },
  {
    key: 'process',
    label: '非结构化数据处理',
    children: [
      { key: 'process.clean', label: '非结构化数据清洗转换处理' },
      { key: 'process.tag', label: '非结构化数据标识处理' },
      { key: 'process.link', label: '非结构化数据关联处理' },
    ],
  },
]

const navItems = computed(() => {
  const byPerm = filterHubNavByPermissions(NAV_BASE, auth.permissions, UNSTRUCT_NAV_PERMISSIONS, {
    isSystemAdmin: auth.isSystemAdmin,
  })
  return filterHubNavByMenuVisible(byPerm, auth.menus, UNSTRUCT_NAV_PERMISSIONS)
})

interface Category {
  id: number
  categoryCode: string
  categoryName: string
  parentId?: number
  mediaType: string
  description?: string
  sortOrder?: number
  status?: string
}
interface Doc {
  id: number
  docCode: string
  title: string
  originalFileName?: string
  storageKey: string
  categoryCode?: string
  categoryName?: string
  contentType?: string
  fileSize?: number
  description?: string
  author?: string
  mediaFormat?: string
  mediaWidth?: number
  mediaHeight?: number
  mediaDurationSec?: number
  featureJson?: string
  contentJson?: string
  fingerprint?: string
  metaStatus?: string
  sourceType?: string
  sourceSystem?: string
  sourceUrl?: string
  landed?: boolean
  indexStatus: string
  publishStatus: string
  processStatus?: string
  tagJson?: string
  linkedDocId?: number | null
  linkedDocTitle?: string
  summary?: string
  createdAt?: string
  updatedAt?: string
  keywords?: string[]
  topics?: string[]
  sentiment?: string
  similarity?: number
}
interface Pipeline {
  id: number
  docId: number
  pipelineType: string
  status: string
  resultMessage?: string
  detailJson?: string
  createdAt?: string
}
interface CleanRule {
  id: number
  ruleCode: string
  ruleName: string
  ruleType: string
  targetField: string
  errorLevel: string
  enabled?: number
  autoApply?: number
  sortOrder?: number
  configJson?: string
  description?: string
  status?: string
}
interface CleanIssue {
  id: number
  docId: number
  docTitle?: string
  ruleCode?: string
  targetField?: string
  errorLevel?: string
  issueStatus?: string
  beforeValue?: string
  afterValue?: string
  message?: string
  createdAt?: string
}
interface ExternalPlatform {
  id: number
  platformName: string
  connectType: string
  apiConfig: string
  syncFrequency: string
  status?: string
  lastSyncAt?: string
  lastSyncCount?: number
  lastSyncMessage?: string
  createdAt?: string
  updatedAt?: string
}
interface SearchHit {
  id: number
  title: string
  categoryCode?: string
  indexStatus?: string
  publishStatus?: string
  tagJson?: string
  storageKey?: string
  originalFileName?: string
  categoryName?: string
  contentType?: string
  fileSize?: number
  description?: string
  sourceType?: string
  sourceSystem?: string
  sourceUrl?: string
  landed?: boolean
  createdAt?: string
  updatedAt?: string
  source?: string
}

const MEDIA_OPTIONS = [
  { label: '文档', value: 'DOCUMENT', contentType: 'application/pdf' },
  { label: '图片', value: 'IMAGE', contentType: 'image/jpeg' },
  { label: '视频', value: 'VIDEO', contentType: 'video/mp4' },
  { label: '音频', value: 'AUDIO', contentType: 'audio/mpeg' },
] as const

const CONNECT_TYPE_OPTIONS = [
  { label: '接口(API)', value: 'API' },
  { label: 'FTP', value: 'FTP' },
  { label: '对象存储(S3)', value: 'S3' },
  { label: 'HTTP接口', value: 'HTTP' },
  { label: '库表同步', value: 'DB_SYNC' },
] as const

const SYNC_FREQUENCY_OPTIONS = [
  { label: '每小时', value: 'HOURLY' },
  { label: '每日', value: 'DAILY' },
  { label: '每周', value: 'WEEKLY' },
  { label: '每月', value: 'MONTHLY' },
  { label: '手工', value: 'MANUAL' },
] as const

const PROCESS_TYPES = ['CLEAN', 'TAG', 'LINK'] as const
type ProcessType = (typeof PROCESS_TYPES)[number]

const route = useRoute()
const router = useRouter()

/** 兼容旧 query.tab（含 m123～m129）→ 侧栏叶子 key */
const tabMap: Record<string, string> = {
  classify: 'classify', m123: 'classify',
  files: 'files', m124: 'files',
  search: 'search', m125: 'search',
  metadata: 'metadata', m126: 'metadata',
  process: 'process.clean',
  m127: 'process.clean',
  m128: 'process.tag',
  m129: 'process.link',
  'process.clean': 'process.clean',
  'process.tag': 'process.tag',
  'process.link': 'process.link',
}

const DEFAULT_NAV = 'files'
const activeNav = ref(DEFAULT_NAV)
let applyingRoute = false
let overviewLoaded = false

const overview = ref<Record<string, unknown> | null>(null)
const categories = ref<Category[]>([])
const {
  page: catPage,
  pageSize: catPageSize,
  paged: pagedCategories,
  total: catTotal,
  resetPage: resetCatPage,
} = useClientPager(categories)
const docs = ref<Doc[]>([])
const uploadDocs = computed(() => docs.value.filter((d) => d.sourceType !== 'EXTERNAL'))
const externalDocs = computed(() => docs.value.filter((d) => d.sourceType === 'EXTERNAL'))
const { page: docPage, pageSize: docPageSize, paged: pagedDocs, total: docTotal, resetPage: resetDocPage } = useClientPager(uploadDocs)
const {
  page: extDocPage,
  pageSize: extDocPageSize,
  paged: pagedExternalDocs,
  total: extDocTotal,
  resetPage: resetExtDocPage,
} = useClientPager(externalDocs)
const metaQuery = reactive({ keyword: '', mediaHint: '', metaStatus: '' })
const metaFilteredDocs = computed(() => {
  let list = docs.value
  const kw = metaQuery.keyword.trim().toLowerCase()
  if (kw) {
    list = list.filter((d) =>
      [d.title, d.originalFileName, d.docCode, d.author, d.description, tagsLabel(d.tagJson)]
        .filter(Boolean)
        .some((x) => String(x).toLowerCase().includes(kw)),
    )
  }
  if (metaQuery.mediaHint) {
    list = list.filter((d) => mediaKindOf(d.contentType) === metaQuery.mediaHint)
  }
  if (metaQuery.metaStatus) {
    list = list.filter((d) => (d.metaStatus || 'RAW') === metaQuery.metaStatus)
  }
  return list
})
const {
  page: metaPage,
  pageSize: metaPageSize,
  paged: pagedMetaDocs,
  total: metaTotal,
  resetPage: resetMetaPage,
} = useClientPager(metaFilteredDocs)
const pipelines = ref<Pipeline[]>([])
const searchQ = ref('')
const searchCategory = ref<string>('')
const searchMedia = ref('')
const searchTag = ref('')
const searchCreatedRange = ref<[string, string] | null>(null)
const searchUpdatedRange = ref<[string, string] | null>(null)
const searchMinSizeMb = ref<number>()
const searchMaxSizeMb = ref<number>()
const searchSortBy = ref('updatedAt')
const searchSortDir = ref('desc')
const searchHits = ref<SearchHit[]>([])
const {
  page: searchPage,
  pageSize: searchPageSize,
  paged: pagedSearchHits,
  total: searchTotal,
  resetPage: resetSearchPage,
} = useClientPager(searchHits)
const searchEsHealthy = ref<boolean | null>(null)
const searchDone = ref(false)

const catForm = reactive({
  categoryCode: '',
  categoryName: '',
  parentId: undefined as number | undefined,
  mediaType: 'DOCUMENT',
  description: '',
  sortOrder: 0,
})
const catDialogVisible = ref(false)
const catEditingId = ref<number>()
const docForm = reactive({
  title: '',
  categoryCode: '' as string,
  mediaHint: 'DOCUMENT',
  contentType: 'application/pdf',
  description: '',
  tagText: '',
  sourceType: 'UPLOAD',
  sourceSystem: '',
  sourceUrl: '',
})
const selectedFile = ref<File | null>(null)
const uploadFileList = ref<UploadUserFile[]>([])
const filesSourceTab = ref<'upload' | 'external'>('upload')
/** 外部平台 Tab 内：外部文件 / 平台连接 */
const extInnerTab = ref<'files' | 'platforms'>('files')
const docQuery = reactive({ keyword: '', categoryCode: '', publishStatus: '' })
const extDocQuery = reactive({ keyword: '', categoryCode: '', publishStatus: '' })
const selectedUploadDocs = ref<Doc[]>([])
const selectedExternalDocs = ref<Doc[]>([])
const docMaintainMode = ref(false)
const publishing = ref(false)
const syncingPlatformId = ref<number | null>(null)
const docCreateVisible = ref(false)
const docCreating = ref(false)
const externalPlatforms = ref<ExternalPlatform[]>([])
const {
  page: extPlatPage,
  pageSize: extPlatPageSize,
  paged: pagedExtPlatforms,
  total: extPlatTotal,
  resetPage: resetExtPlatPage,
} = useClientPager(externalPlatforms)
const extPlatQuery = reactive({ platformName: '' })
const extPlatDialogVisible = ref(false)
const extPlatEditingId = ref<number>()
const emptyExtPlatConfig = () => ({
  // API
  baseUrl: '',
  apiKey: '',
  // FTP / DB
  host: '',
  port: undefined as number | undefined,
  username: '',
  password: '',
  remotePath: '',
  // S3
  endpoint: '',
  accessKey: '',
  secretKey: '',
  bucket: '',
  region: '',
  // HTTP
  url: '',
  method: 'GET',
  headers: '',
  // DB_SYNC
  database: '',
  jdbcUrl: '',
})
const extPlatForm = reactive({
  platformName: '',
  connectType: '' as string,
  syncFrequency: '' as string,
  config: emptyExtPlatConfig(),
})
const docDialogVisible = ref(false)
const docEditing = reactive({
  id: 0,
  title: '',
  categoryCode: '',
  description: '',
  tagText: '',
  sourceType: 'UPLOAD',
  sourceSystem: '',
  sourceUrl: '',
})
const detailVisible = ref(false)
const detailLoading = ref(false)
const docDetail = ref<Doc | null>(null)
const metaDialogVisible = ref(false)
const metaEdit = reactive({
  id: 0,
  title: '',
  author: '',
  description: '',
  tagText: '',
  mediaFormat: '',
  mediaWidth: undefined as number | undefined,
  mediaHeight: undefined as number | undefined,
  mediaDurationSec: undefined as number | undefined,
  keywordsText: '',
  topicsText: '',
  sentiment: '' as string,
  summary: '',
})
const metaOverview = ref<Record<string, unknown> | null>(null)
const metaBusyId = ref<number | null>(null)
const metaBatchBusy = ref(false)
const selectedMetaDocs = ref<Doc[]>([])
const metaDetailVisible = ref(false)
const metaDetailDoc = ref<Doc | null>(null)
const similarVisible = ref(false)
const similarSeedId = ref<number | null>(null)
const similarSeedTitle = ref('')
const similarHits = ref<Doc[]>([])
const similarLoading = ref(false)

const cleanOverview = ref<Record<string, unknown> | null>(null)
const cleanRules = ref<CleanRule[]>([])
const {
  page: cleanRulePage,
  pageSize: cleanRulePageSize,
  paged: pagedCleanRules,
  total: cleanRuleTotal,
  resetPage: resetCleanRulePage,
} = useClientPager(cleanRules)
const cleanIssues = ref<CleanIssue[]>([])
const cleanTab = ref('execute')
const cleanIssueStatus = ref('')
const {
  page: cleanIssuePage,
  pageSize: cleanIssuePageSize,
  paged: pagedCleanIssues,
  total: cleanIssueTotal,
  resetPage: resetCleanIssuePage,
} = useClientPager(cleanIssues)
const {
  page: processDocPage,
  pageSize: processDocPageSize,
  paged: pagedProcessDocs,
  total: processDocTotal,
  resetPage: resetProcessDocPage,
} = useClientPager(docs)
const {
  page: pipePage,
  pageSize: pipePageSize,
  paged: pagedPipelines,
  total: pipeTotal,
  resetPage: resetPipePage,
} = useClientPager(pipelines)
const cleanBusyId = ref<number | null>(null)
const ruleDialogVisible = ref(false)
const ruleEditingId = ref<number>()
const ruleViewMode = ref(false)
const ruleRunVisible = ref(false)
const ruleRunDocId = ref<number | undefined>()
const ruleRunTarget = ref<CleanRule | null>(null)
const ruleForm = reactive({
  ruleCode: '',
  ruleName: '',
  ruleType: 'VALIDATE',
  targetField: 'title',
  errorLevel: 'WARN',
  enabled: 1,
  autoApply: 0,
  sortOrder: 0,
  configJson: '{}',
  description: '',
})
const cleanResultVisible = ref(false)
const cleanResult = ref<Record<string, unknown> | null>(null)

interface TagDef {
  id: number
  tagCode: string
  tagName: string
  tagKind: string
  matchKeywords?: string
  description?: string
  enabled?: number
  sortOrder?: number
  status?: string
}
interface LinkRule {
  id: number
  ruleCode: string
  ruleName: string
  linkStage: string
  algorithm: string
  configJson?: string
  description?: string
  enabled?: number
  sortOrder?: number
  status?: string
}

const TAG_KIND_ZH: Record<string, string> = { GENERAL: '通用标签', BUSINESS: '业务标签' }
const LINK_STAGE_ZH: Record<string, string> = {
  EXTRACT: '关联提取',
  ANALYZE: '关联分析',
  BACKFILL: '关联回填',
  ALL: '全流程',
}
const LINK_ALGO_ZH: Record<string, string> = {
  SIMILARITY: '相似度',
  CATEGORY: '同分类',
  KEYWORD: '关键词',
}

const tagTab = ref('execute')
const linkTab = ref('execute')
const tagDefs = ref<TagDef[]>([])
const {
  page: tagDefPage,
  pageSize: tagDefPageSize,
  paged: pagedTagDefs,
  total: tagDefTotal,
  resetPage: resetTagDefPage,
} = useClientPager(tagDefs)
const linkRules = ref<LinkRule[]>([])
const {
  page: linkRulePage,
  pageSize: linkRulePageSize,
  paged: pagedLinkRules,
  total: linkRuleTotal,
  resetPage: resetLinkRulePage,
} = useClientPager(linkRules)

const tagDefDialogVisible = ref(false)
const tagDefEditingId = ref<number>()
const tagDefViewMode = ref(false)
const tagDefForm = reactive({
  tagCode: '',
  tagName: '',
  tagKind: 'GENERAL',
  matchKeywords: '',
  description: '',
  enabled: 1,
  sortOrder: 0,
})

const linkRuleDialogVisible = ref(false)
const linkRuleEditingId = ref<number>()
const linkRuleViewMode = ref(false)
const linkRuleForm = reactive({
  ruleCode: '',
  ruleName: '',
  linkStage: 'ALL',
  algorithm: 'SIMILARITY',
  configJson: '{}',
  description: '',
  enabled: 1,
  sortOrder: 0,
})

const processBusyId = ref<number | null>(null)

const processType = computed<ProcessType>(() => {
  if (activeNav.value === 'process.tag') return 'TAG'
  if (activeNav.value === 'process.link') return 'LINK'
  return 'CLEAN'
})

const pageTitle = computed(() => {
  const leaf = activeNav.value
  if (leaf.startsWith('process.')) {
    const child = NAV_BASE.find((n) => n.key === 'process')?.children?.find((c) => c.key === leaf)
    return child?.label || '非结构化数据处理'
  }
  return NAV_BASE.find((n) => n.key === leaf)?.label || '非结构数据融合治理平台'
})
const categoryNameById = computed(() => new Map(categories.value.map((c) => [c.id, c.categoryName])))

function resolveFromRoute() {
  if (props.embedLocalOnly) {
    activeNav.value = 'files'
    filesSourceTab.value = 'upload'
    return
  }
  applyingRoute = true
  const q = String(route.query.tab || DEFAULT_NAV).toLowerCase()
  activeNav.value = tabMap[q] || DEFAULT_NAV
  if (activeNav.value === 'process') activeNav.value = 'process.clean'
  nextTick(() => { applyingRoute = false })
}

function syncQuery() {
  if (props.embedLocalOnly) return
  const q: Record<string, string> = {}
  for (const [k, v] of Object.entries(route.query)) {
    if (v == null || k === 'tab') continue
    q[k] = Array.isArray(v) ? String(v[0]) : String(v)
  }
  q.tab = activeNav.value
  router.replace({ query: q })
}

async function loadOverview() {
  overview.value = (await api.get('/unstructured/platform/overview')).data
  overviewLoaded = true
}

async function loadMetadataOverview() {
  metaOverview.value = (await api.get('/unstructured/platform/metadata/overview')).data
}

async function loadCategories() {
  categories.value = (await api.get('/unstructured/platform/categories')).data || []
  resetCatPage()
}

async function loadDocuments() {
  const forFiles = activeNav.value === 'files'
  const useExtQuery = forFiles && filesSourceTab.value === 'external'
  docs.value = (await api.get('/unstructured/platform/documents', {
    params: {
      keyword: forFiles
        ? ((useExtQuery ? extDocQuery.keyword : docQuery.keyword) || undefined)
        : undefined,
      categoryCode: forFiles
        ? ((useExtQuery ? extDocQuery.categoryCode : docQuery.categoryCode) || undefined)
        : undefined,
      publishStatus: forFiles
        ? ((useExtQuery ? extDocQuery.publishStatus : docQuery.publishStatus) || undefined)
        : undefined,
      sourceType: forFiles ? (useExtQuery ? 'EXTERNAL' : 'UPLOAD') : undefined,
    },
  })).data || []
  resetDocPage()
  resetExtDocPage()
  resetMetaPage()
  resetProcessDocPage()
  selectedUploadDocs.value = []
  selectedExternalDocs.value = []
  selectedMetaDocs.value = []
}

function onResetDocuments() {
  docQuery.keyword = ''
  docQuery.categoryCode = ''
  docQuery.publishStatus = ''
  resetDocPage()
  void loadDocuments()
}

function onResetExternalDocuments() {
  extDocQuery.keyword = ''
  extDocQuery.categoryCode = ''
  extDocQuery.publishStatus = ''
  resetExtDocPage()
  void loadDocuments()
}

async function loadExternalPlatforms() {
  externalPlatforms.value = (await api.get('/unstructured/platform/external-platforms', {
    params: { platformName: extPlatQuery.platformName || undefined },
  })).data || []
  resetExtPlatPage()
}

function onResetExternalPlatforms() {
  extPlatQuery.platformName = ''
  void loadExternalPlatforms()
}

function extPlatIndex(i: number) {
  return (extPlatPage.value - 1) * extPlatPageSize.value + i + 1
}

function resetExtPlatConfig() {
  Object.assign(extPlatForm.config, emptyExtPlatConfig())
}

function onExtPlatConnectTypeChange() {
  resetExtPlatConfig()
  if (extPlatForm.connectType === 'FTP') extPlatForm.config.port = 21
  if (extPlatForm.connectType === 'DB_SYNC') extPlatForm.config.port = 3306
  if (extPlatForm.connectType === 'HTTP') extPlatForm.config.method = 'GET'
}

function applyExtPlatConfig(raw?: string) {
  resetExtPlatConfig()
  if (!raw?.trim()) return
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>
    const c = extPlatForm.config
    if (obj.baseUrl != null) c.baseUrl = String(obj.baseUrl)
    if (obj.apiKey != null) c.apiKey = String(obj.apiKey)
    if (obj.host != null) c.host = String(obj.host)
    if (obj.port != null && String(obj.port).trim() !== '') c.port = Number(obj.port)
    if (obj.username != null) c.username = String(obj.username)
    if (obj.password != null) c.password = String(obj.password)
    if (obj.remotePath != null) c.remotePath = String(obj.remotePath)
    if (obj.endpoint != null) c.endpoint = String(obj.endpoint)
    if (obj.accessKey != null) c.accessKey = String(obj.accessKey)
    if (obj.secretKey != null) c.secretKey = String(obj.secretKey)
    if (obj.bucket != null) c.bucket = String(obj.bucket)
    if (obj.region != null) c.region = String(obj.region)
    if (obj.url != null) c.url = String(obj.url)
    if (obj.method != null) c.method = String(obj.method)
    if (obj.headers != null) c.headers = typeof obj.headers === 'string' ? obj.headers : JSON.stringify(obj.headers)
    if (obj.database != null) c.database = String(obj.database)
    if (obj.jdbcUrl != null) c.jdbcUrl = String(obj.jdbcUrl)
  } catch {
    /* 旧数据非 JSON 时留空，由用户重新填写 */
  }
}

function buildExtPlatApiConfig(): string | null {
  const type = extPlatForm.connectType
  const c = extPlatForm.config
  const put = (key: string, value: unknown, required = false) => {
    const text = value == null ? '' : String(value).trim()
    if (!text) {
      if (required) throw new Error(key)
      return undefined
    }
    return text
  }
  try {
    if (type === 'API') {
      return JSON.stringify({
        baseUrl: put('接口地址', c.baseUrl, true),
        apiKey: put('访问密钥', c.apiKey),
      })
    }
    if (type === 'FTP') {
      const port = c.port ?? 21
      return JSON.stringify({
        host: put('主机地址', c.host, true),
        port,
        username: put('用户名', c.username, true),
        password: put('密码', c.password, true),
        remotePath: put('远程目录', c.remotePath) || '/',
      })
    }
    if (type === 'S3') {
      return JSON.stringify({
        endpoint: put('Endpoint', c.endpoint, true),
        accessKey: put('AccessKey', c.accessKey, true),
        secretKey: put('SecretKey', c.secretKey, true),
        bucket: put('Bucket', c.bucket, true),
        region: put('Region', c.region),
      })
    }
    if (type === 'HTTP') {
      return JSON.stringify({
        url: put('请求地址', c.url, true),
        method: put('请求方法', c.method || 'GET', true),
        headers: put('请求头', c.headers),
        apiKey: put('鉴权令牌', c.apiKey),
      })
    }
    if (type === 'DB_SYNC') {
      const port = c.port ?? 3306
      return JSON.stringify({
        host: put('主机地址', c.host, true),
        port,
        database: put('数据库名', c.database, true),
        username: put('用户名', c.username, true),
        password: put('密码', c.password, true),
        jdbcUrl: put('JDBC URL', c.jdbcUrl),
      })
    }
    ElMessage.warning('请选择对接方式')
    return null
  } catch (e) {
    ElMessage.warning(`请填写${e instanceof Error ? e.message : '接口配置'}`)
    return null
  }
}

function openExtPlatCreate() {
  extPlatEditingId.value = undefined
  Object.assign(extPlatForm, {
    platformName: '',
    connectType: '',
    syncFrequency: '',
  })
  resetExtPlatConfig()
  extPlatDialogVisible.value = true
}

function openExtPlatEdit(row: ExternalPlatform) {
  extPlatEditingId.value = row.id
  Object.assign(extPlatForm, {
    platformName: row.platformName,
    connectType: row.connectType,
    syncFrequency: row.syncFrequency,
  })
  applyExtPlatConfig(row.apiConfig)
  extPlatDialogVisible.value = true
}

async function saveExternalPlatform() {
  if (!extPlatForm.platformName.trim()) {
    ElMessage.warning('请填写平台名称')
    return
  }
  if (!extPlatForm.connectType) {
    ElMessage.warning('请选择对接方式')
    return
  }
  if (!extPlatForm.syncFrequency) {
    ElMessage.warning('请选择同步频率')
    return
  }
  const apiConfig = buildExtPlatApiConfig()
  if (!apiConfig) return
  const body = {
    platformName: extPlatForm.platformName.trim(),
    connectType: extPlatForm.connectType,
    apiConfig,
    syncFrequency: extPlatForm.syncFrequency,
  }
  if (extPlatEditingId.value) {
    await api.put(`/unstructured/platform/external-platforms/${extPlatEditingId.value}`, body)
    ElMessage.success('外部平台已更新')
  } else {
    await api.post('/unstructured/platform/external-platforms', body)
    ElMessage.success('外部平台已新增')
  }
  extPlatDialogVisible.value = false
  await loadExternalPlatforms()
}

async function deleteExternalPlatform(row: ExternalPlatform) {
  try {
    await ElMessageBox.confirm(`确认删除外部平台「${row.platformName}」？`, '删除确认', { type: 'warning' })
    await api.delete(`/unstructured/platform/external-platforms/${row.id}`)
    ElMessage.success('已删除')
    await loadExternalPlatforms()
  } catch { /* cancel */ }
}

async function onFilesSourceTabChange(tab: string | number) {
  if (tab === 'external') {
    if (extInnerTab.value === 'platforms') {
      await loadExternalPlatforms()
    } else {
      await Promise.all([loadDocuments(), loadCategories(), loadExternalPlatforms()])
    }
  } else {
    await Promise.all([loadDocuments(), loadCategories()])
  }
}

async function onExtInnerTabChange(tab: string | number) {
  if (tab === 'platforms') {
    await loadExternalPlatforms()
  } else {
    await Promise.all([loadDocuments(), loadCategories(), loadExternalPlatforms()])
  }
}

async function loadPipelines(type: ProcessType) {
  pipelines.value = (await api.get('/unstructured/platform/pipelines', {
    params: { pipelineType: type },
  })).data || []
  resetPipePage()
}

async function loadCleanOverview() {
  cleanOverview.value = (await api.get('/unstructured/platform/clean/overview')).data
}

async function loadCleanRules() {
  cleanRules.value = (await api.get('/unstructured/platform/clean/rules')).data || []
  resetCleanRulePage()
}

async function loadCleanIssues() {
  cleanIssues.value = (await api.get('/unstructured/platform/clean/issues', {
    params: { issueStatus: cleanIssueStatus.value || undefined },
  })).data || []
  resetCleanIssuePage()
}

function onResetCleanIssues() {
  cleanIssueStatus.value = ''
  void loadCleanIssues()
}

async function loadTabData() {
  try {
    if (!overviewLoaded) await loadOverview()
    const nav = activeNav.value
    if (nav === 'classify') {
      await loadCategories()
    } else if (nav === 'files') {
      if (filesSourceTab.value === 'external') {
        if (extInnerTab.value === 'platforms') {
          await loadExternalPlatforms()
        } else {
          await Promise.all([loadDocuments(), loadCategories(), loadExternalPlatforms()])
        }
      } else {
        await Promise.all([loadDocuments(), loadCategories()])
      }
    } else if (nav === 'search') {
      await loadCategories()
      await doSearch(true)
    } else if (nav === 'metadata') {
      await Promise.all([loadMetadataOverview(), loadDocuments()])
    } else if (nav.startsWith('process.')) {
      if (nav === 'process.clean') {
        cleanTab.value = 'execute'
        await Promise.all([loadCleanOverview(), loadDocuments(), loadPipelines('CLEAN')])
      } else if (nav === 'process.tag') {
        tagTab.value = 'execute'
        await Promise.all([loadDocuments(), loadTagDefs(), loadPipelines('TAG')])
      } else if (nav === 'process.link') {
        linkTab.value = 'execute'
        await Promise.all([loadDocuments(), loadLinkRules(), loadPipelines('LINK')])
      } else {
        await Promise.all([loadPipelines(processType.value), loadDocuments()])
      }
    }
  } catch {
    ElMessage.error('加载失败')
  }
}

async function addCategory() {
  catEditingId.value = undefined
  Object.assign(catForm, {
    categoryCode: '',
    categoryName: '',
    parentId: undefined,
    mediaType: 'DOCUMENT',
    description: '',
    sortOrder: 0,
  })
  catDialogVisible.value = true
}

async function saveCategory() {
  if (!catForm.categoryName.trim()) {
    ElMessage.warning('请填写分类名称')
    return
  }
  const body = {
    categoryCode: catForm.categoryCode.trim() || undefined,
    categoryName: catForm.categoryName.trim(),
    parentId: catForm.parentId,
    mediaType: catForm.mediaType,
    description: catForm.description.trim() || undefined,
    sortOrder: catForm.sortOrder,
  }
  if (catEditingId.value) {
    await api.put(`/unstructured/platform/categories/${catEditingId.value}`, body)
    ElMessage.success('分类已更新')
  } else {
    await api.post('/unstructured/platform/categories', body)
    ElMessage.success('分类已创建')
  }
  catDialogVisible.value = false
  await loadCategories()
  overviewLoaded = false
  await loadOverview()
}

function editCategory(row: Category) {
  catEditingId.value = row.id
  Object.assign(catForm, {
    categoryCode: row.categoryCode,
    categoryName: row.categoryName,
    parentId: row.parentId,
    mediaType: row.mediaType,
    description: row.description || '',
    sortOrder: row.sortOrder || 0,
  })
  catDialogVisible.value = true
}

async function deleteCategory(row: Category) {
  try {
    await ElMessageBox.confirm(`确认删除分类「${row.categoryName}」？`, '删除分类', { type: 'warning' })
    await api.delete(`/unstructured/platform/categories/${row.id}`)
    ElMessage.success('已删除')
    await loadCategories()
    overviewLoaded = false
    await loadOverview()
  } catch { /* cancel */ }
}

function onMediaHintChange(hint: string) {
  const opt = MEDIA_OPTIONS.find((m) => m.value === hint)
  if (opt) docForm.contentType = opt.contentType
}

function categoryMediaLabel(mediaType?: string) {
  if (!mediaType) return '—'
  return MEDIA_OPTIONS.find((m) => m.value === mediaType)?.label || statusLabel(mediaType) || mediaType
}

function categoryParentLabel(row: Category) {
  if (row.parentId == null || row.parentId <= 0) return '顶级分类'
  return categories.value.find((c) => c.id === row.parentId)?.categoryName || '—'
}

function onFileChange(uploadFile: UploadFile) {
  selectedFile.value = uploadFile.raw || null
  if (selectedFile.value && !docForm.title.trim()) {
    docForm.title = selectedFile.value.name.replace(/\.[^.]+$/, '')
  }
  if (selectedFile.value?.type) {
    docForm.contentType = selectedFile.value.type
  }
}

function onUploadChange(file: UploadFile, files: UploadUserFile[]) {
  uploadFileList.value = files.slice(-1)
  onFileChange(file)
}

function onUploadRemove() {
  selectedFile.value = null
  uploadFileList.value = []
}

function resetDocForm() {
  Object.assign(docForm, {
    title: '',
    categoryCode: '',
    mediaHint: 'DOCUMENT',
    contentType: 'application/pdf',
    description: '',
    tagText: '',
    sourceType: filesSourceTab.value === 'external' ? 'EXTERNAL' : 'UPLOAD',
    sourceSystem: '',
    sourceUrl: '',
  })
  selectedFile.value = null
  uploadFileList.value = []
}

async function openDocCreate() {
  resetDocForm()
  docMaintainMode.value = false
  if (filesSourceTab.value === 'external' && !externalPlatforms.value.length) {
    await loadExternalPlatforms()
  }
  if (!categories.value.length) {
    await loadCategories()
  }
  docCreateVisible.value = true
}

async function publishDoc(row: Doc) {
  try {
    await api.post(`/unstructured/platform/documents/${row.id}/publish`)
    ElMessage.success('已发布')
    await loadDocuments()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '发布失败')
  }
}

async function offlineDoc(row: Doc) {
  try {
    await ElMessageBox.confirm(`确认下线「${row.title}」？下线后不可被检索门户直接使用。`, '下线确认', { type: 'warning' })
    await api.post(`/unstructured/platform/documents/${row.id}/offline`)
    ElMessage.success('已下线')
    await loadDocuments()
  } catch (e: unknown) {
    if ((e as string) === 'cancel') return
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '下线失败')
  }
}

async function indexDoc(row: Doc) {
  try {
    await api.post(`/unstructured/platform/documents/${row.id}/index`)
    ElMessage.success('已建立检索索引')
    await loadDocuments()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '建索失败')
  }
}

function currentSelectedDocs() {
  return filesSourceTab.value === 'external' ? selectedExternalDocs.value : selectedUploadDocs.value
}

async function toolbarPublish() {
  const rows = currentSelectedDocs()
  if (!rows.length) {
    ElMessage.warning('请先勾选待发布的文件资源')
    return
  }
  publishing.value = true
  try {
    const res = await api.post('/unstructured/platform/documents/batch-publish', {
      ids: rows.map((r) => r.id),
    })
    const data = res.data || {}
    const failed = Number(data.failed || 0)
    if (failed > 0) {
      ElMessage.warning(`发布完成：成功 ${data.success || 0}，跳过 ${data.skipped || 0}，失败 ${failed}`)
    } else {
      ElMessage.success(`发布完成：成功 ${data.success || 0}，跳过已发布 ${data.skipped || 0}`)
    }
    await loadDocuments()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '批量发布失败')
  } finally {
    publishing.value = false
  }
}

function toolbarMaintain() {
  const rows = currentSelectedDocs()
  if (rows.length !== 1) {
    ElMessage.warning('请勾选一条文件资源进行维护')
    return
  }
  void openDocMaintain(rows[0])
}

async function openDocMaintain(row: Doc) {
  docMaintainMode.value = true
  await openDocEdit(row)
}

async function syncExternalPlatform(row: ExternalPlatform) {
  syncingPlatformId.value = row.id
  try {
    const res = await api.post(`/unstructured/platform/external-platforms/${row.id}/sync`)
    ElMessage.success(res.data?.message || '同步完成')
    await loadExternalPlatforms()
    if (filesSourceTab.value === 'external' && extInnerTab.value === 'files') {
      await loadDocuments()
    }
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '同步失败')
  } finally {
    syncingPlatformId.value = null
  }
}

function onUploadSelectionChange(rows: Doc[]) {
  selectedUploadDocs.value = rows
}

function onExternalSelectionChange(rows: Doc[]) {
  selectedExternalDocs.value = rows
}

async function maintainBuildIndex() {
  const row = docs.value.find((d) => d.id === docEditing.id)
  if (!row) {
    ElMessage.warning('请先刷新列表后再建索')
    return
  }
  await indexDoc(row)
}

async function maintainOffline() {
  const row = docs.value.find((d) => d.id === docEditing.id)
  if (!row) {
    ElMessage.warning('请先刷新列表后再下线')
    return
  }
  await offlineDoc(row)
  docDialogVisible.value = false
}

function tagsToJson(value: string) {
  const tags = value.split(/[,，]/).map((x) => x.trim()).filter(Boolean)
  return JSON.stringify([...new Set(tags)])
}

function tagsLabel(value?: string) {
  if (!value) return '—'
  try {
    const tags = JSON.parse(value)
    return Array.isArray(tags) ? tags.join('、') || '—' : value
  } catch {
    return value
  }
}

function parseContentField(doc: Doc, key: 'keywords' | 'topics' | 'sentiment' | 'summary'): unknown {
  if (key === 'keywords' && Array.isArray(doc.keywords)) return doc.keywords
  if (key === 'topics' && Array.isArray(doc.topics)) return doc.topics
  if (key === 'sentiment' && doc.sentiment) return doc.sentiment
  if (key === 'summary' && doc.summary) return doc.summary
  if (!doc.contentJson) return key === 'keywords' || key === 'topics' ? [] : ''
  try {
    const obj = JSON.parse(doc.contentJson) as Record<string, unknown>
    return obj[key]
  } catch {
    return key === 'keywords' || key === 'topics' ? [] : ''
  }
}

function contentListLabel(doc: Doc, key: 'keywords' | 'topics') {
  const value = parseContentField(doc, key)
  return Array.isArray(value) && value.length ? value.join('、') : '—'
}

function featureBrief(doc: Doc) {
  const parts: string[] = []
  if (doc.mediaFormat) parts.push(doc.mediaFormat)
  if (doc.mediaWidth && doc.mediaHeight) parts.push(`${doc.mediaWidth}×${doc.mediaHeight}`)
  if (doc.mediaDurationSec != null) parts.push(`${doc.mediaDurationSec}s`)
  if (!parts.length && doc.featureJson) {
    try {
      const f = JSON.parse(doc.featureJson) as Record<string, unknown>
      if (f.format) parts.push(String(f.format))
      if (f.width && f.height) parts.push(`${f.width}×${f.height}`)
      if (f.durationSec != null) parts.push(`${f.durationSec}s`)
    } catch { /* ignore */ }
  }
  return parts.length ? parts.join(' · ') : '—'
}

async function registerDoc() {
  if (!docForm.title.trim()) {
    ElMessage.warning('请填写文档标题')
    return
  }
  if (!docForm.categoryCode) {
    ElMessage.warning('请选择文件分类')
    return
  }
  const isExternal = docForm.sourceType === 'EXTERNAL' || filesSourceTab.value === 'external'
  if (isExternal) {
    if (!docForm.sourceSystem.trim()) {
      ElMessage.warning('请选择来源平台')
      return
    }
    if (!docForm.sourceUrl.trim()) {
      ElMessage.warning('请填写资源地址')
      return
    }
  } else if (!selectedFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  docCreating.value = true
  try {
    if (isExternal) {
      const platform = externalPlatforms.value.find((p) => p.platformName === docForm.sourceSystem.trim())
      await api.post('/unstructured/platform/documents', {
        title: docForm.title.trim(),
        categoryCode: docForm.categoryCode,
        contentType: docForm.contentType,
        description: docForm.description.trim() || undefined,
        tagJson: tagsToJson(docForm.tagText),
        platformId: platform?.id,
        sourceSystem: docForm.sourceSystem.trim(),
        sourceUrl: docForm.sourceUrl.trim(),
        sourceType: 'EXTERNAL',
      })
    } else {
      const form = new FormData()
      form.append('file', selectedFile.value!)
      form.append('title', docForm.title.trim())
      form.append('categoryCode', docForm.categoryCode)
      form.append('description', docForm.description.trim())
      form.append('tagJson', tagsToJson(docForm.tagText))
      await api.post('/unstructured/platform/documents/upload', form)
    }
    ElMessage.success('文件资源已注册')
    docCreateVisible.value = false
    resetDocForm()
    if (isExternal) {
      filesSourceTab.value = 'external'
      extInnerTab.value = 'files'
    }
    await loadDocuments()
    overviewLoaded = false
    await loadOverview()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '登记失败')
  } finally {
    docCreating.value = false
  }
}

async function openDocEdit(row: Doc, maintain = false) {
  docMaintainMode.value = maintain
  Object.assign(docEditing, {
    id: row.id,
    title: row.title,
    categoryCode: row.categoryCode || '',
    description: row.description || '',
    tagText: tagsLabel(row.tagJson) === '—' ? '' : tagsLabel(row.tagJson),
    sourceType: row.sourceType || 'UPLOAD',
    sourceSystem: row.sourceSystem || '',
    sourceUrl: row.sourceUrl || '',
  })
  if (row.sourceType === 'EXTERNAL' && !externalPlatforms.value.length) {
    await loadExternalPlatforms()
  }
  docDialogVisible.value = true
}

async function saveDocEdit() {
  if (!docEditing.title.trim()) {
    ElMessage.warning('请填写文档标题')
    return
  }
  if (!docEditing.categoryCode) {
    ElMessage.warning('请选择文件分类')
    return
  }
  const body: Record<string, string> = {
    title: docEditing.title.trim(),
    categoryCode: docEditing.categoryCode,
    description: docEditing.description.trim(),
    tagJson: tagsToJson(docEditing.tagText),
  }
  if (docEditing.sourceType === 'EXTERNAL') {
    if (!docEditing.sourceSystem.trim()) {
      ElMessage.warning('请选择来源平台')
      return
    }
    if (!docEditing.sourceUrl.trim()) {
      ElMessage.warning('请填写资源地址')
      return
    }
    body.sourceSystem = docEditing.sourceSystem.trim()
    body.sourceUrl = docEditing.sourceUrl.trim()
  }
  await api.put(`/unstructured/platform/documents/${docEditing.id}`, body)
  ElMessage.success('文件资源已更新')
  docDialogVisible.value = false
  await loadDocuments()
}

async function deleteDoc(row: Doc) {
  try {
    await ElMessageBox.confirm(`确认删除文件资源「${row.title}」？`, '删除文件资源', { type: 'warning' })
    await api.delete(`/unstructured/platform/documents/${row.id}`)
    ElMessage.success('文件资源已删除')
    await Promise.all([loadDocuments(), loadCleanOverview().catch(() => undefined)])
  } catch { /* cancel */ }
}

async function openDetail(id: number) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    docDetail.value = (await api.get(`/unstructured/platform/documents/${id}`)).data
  } finally {
    detailLoading.value = false
  }
}

function isDocLanded(row: Doc | SearchHit) {
  if (typeof row.landed === 'boolean') return row.landed
  const key = row.storageKey || ''
  return !!key && !key.startsWith('external://')
}

function sourceLabel(row: Doc | SearchHit) {
  if (row.sourceType === 'EXTERNAL') return row.sourceSystem || '外部平台'
  return '本地上传'
}

async function accessFile(row: Doc | SearchHit, download = false) {
  if (!isDocLanded(row)) {
    ElMessage.warning('还未落盘')
    return
  }
  try {
    const res = await api.get(`/unstructured/platform/documents/${row.id}/content`, {
      params: { download },
      responseType: 'blob',
    })
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data], { type: row.contentType })
    // 后端业务错误可能以 JSON blob 返回
    if (blob.type && blob.type.includes('application/json')) {
      const text = await blob.text()
      try {
        const parsed = JSON.parse(text) as { message?: string; msg?: string }
        const msg = parsed.message || parsed.msg || ''
        if (msg.includes('还未落盘')) {
          ElMessage.warning('还未落盘')
          return
        }
      } catch { /* ignore */ }
      ElMessage.error(download ? '下载失败' : '当前文件格式无法预览或内容不可用')
      return
    }
    const url = URL.createObjectURL(blob)
    if (download) {
      const a = document.createElement('a')
      a.href = url
      a.download = row.originalFileName || row.title || '文件'
      a.click()
    } else {
      window.open(url, '_blank', 'noopener,noreferrer')
    }
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch {
    ElMessage.error(download ? '下载失败' : '当前文件格式无法预览或内容不可用')
  }
}

function formatSize(bytes?: number) {
  const size = Number(bytes || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function mediaLabel(contentType?: string) {
  const value = String(contentType || '').toLowerCase()
  if (value.startsWith('image/')) return '图片'
  if (value.startsWith('video/')) return '视频'
  if (value.startsWith('audio/')) return '音频'
  return '文档'
}

function mediaKindOf(contentType?: string) {
  const value = String(contentType || '').toLowerCase()
  if (value.startsWith('image/')) return 'IMAGE'
  if (value.startsWith('video/')) return 'VIDEO'
  if (value.startsWith('audio/')) return 'AUDIO'
  return 'DOCUMENT'
}

function onResetMetaQuery() {
  metaQuery.keyword = ''
  metaQuery.mediaHint = ''
  metaQuery.metaStatus = ''
  resetMetaPage()
}

async function doSearch(silent = false) {
  try {
    const res = await api.get('/unstructured/platform/search', {
      params: {
        q: searchQ.value || undefined,
        categoryCode: searchCategory.value || undefined,
        mediaHint: searchMedia.value || undefined,
        tag: searchTag.value || undefined,
        createdFrom: searchCreatedRange.value?.[0],
        createdTo: searchCreatedRange.value?.[1],
        updatedFrom: searchUpdatedRange.value?.[0],
        updatedTo: searchUpdatedRange.value?.[1],
        minSize: searchMinSizeMb.value == null ? undefined : Math.round(searchMinSizeMb.value * 1024 * 1024),
        maxSize: searchMaxSizeMb.value == null ? undefined : Math.round(searchMaxSizeMb.value * 1024 * 1024),
        sortBy: searchSortBy.value,
        sortDir: searchSortDir.value,
      },
    })
    const data = res.data || {}
    searchHits.value = (data.hits as SearchHit[]) || []
    searchEsHealthy.value = data.esHealthy == null ? null : !!data.esHealthy
    searchDone.value = true
    resetSearchPage()
    if (!silent) {
      ElMessage.success(`检索完成，命中 ${searchHits.value.length} 条`)
    }
  } catch {
    ElMessage.error('检索失败')
  }
}

function openMetaEdit(row: Doc) {
  metaEdit.id = row.id
  metaEdit.title = row.title
  metaEdit.author = row.author || ''
  metaEdit.description = row.description || ''
  metaEdit.tagText = tagsLabel(row.tagJson) === '—' ? '' : tagsLabel(row.tagJson).replace(/、/g, ',')
  metaEdit.mediaFormat = row.mediaFormat || ''
  metaEdit.mediaWidth = row.mediaWidth
  metaEdit.mediaHeight = row.mediaHeight
  metaEdit.mediaDurationSec = row.mediaDurationSec
  const kw = parseContentField(row, 'keywords')
  const topics = parseContentField(row, 'topics')
  metaEdit.keywordsText = Array.isArray(kw) ? kw.join(',') : ''
  metaEdit.topicsText = Array.isArray(topics) ? topics.join(',') : ''
  metaEdit.sentiment = String(parseContentField(row, 'sentiment') || '')
  metaEdit.summary = contentSummary(row) === '—' ? '' : contentSummary(row)
  metaDialogVisible.value = true
}

async function saveMetadata() {
  if (!metaEdit.id) return
  try {
    const body: Record<string, unknown> = {
      title: metaEdit.title,
      author: metaEdit.author || null,
      description: metaEdit.description || null,
      tagJson: tagsToJson(metaEdit.tagText),
      mediaFormat: metaEdit.mediaFormat || null,
      mediaWidth: metaEdit.mediaWidth ?? null,
      mediaHeight: metaEdit.mediaHeight ?? null,
      mediaDurationSec: metaEdit.mediaDurationSec ?? null,
    }
    const hasContentEdit = !!(
      metaEdit.keywordsText.trim()
      || metaEdit.topicsText.trim()
      || metaEdit.sentiment
      || metaEdit.summary.trim()
    )
    if (hasContentEdit) {
      body.keywords = metaEdit.keywordsText
      body.topics = metaEdit.topicsText
      body.sentiment = metaEdit.sentiment || 'NEUTRAL'
      body.summary = metaEdit.summary || null
    }
    await api.put(`/unstructured/platform/documents/${metaEdit.id}/metadata`, body)
    ElMessage.success('元数据已落地保存')
    metaDialogVisible.value = false
    metaEdit.id = 0
    await Promise.all([loadMetadataOverview(), loadDocuments()])
  } catch {
    ElMessage.error('保存元数据失败')
  }
}

function openMetaDetail(row: Doc) {
  metaDetailDoc.value = row
  metaDetailVisible.value = true
}

function contentSummary(row: Doc) {
  const s = row.summary || parseContentField(row, 'summary')
  return s ? String(s) : '—'
}

function mediaInsightsLabel(row: Doc) {
  if (!row.contentJson) return '—'
  try {
    const obj = JSON.parse(row.contentJson) as Record<string, unknown>
    const insights = obj.mediaInsights
    if (!insights || typeof insights !== 'object') return '—'
    const map = insights as Record<string, unknown>
    const parts = Object.entries(map)
      .filter(([, v]) => v != null && String(v).trim() !== '')
      .map(([k, v]) => `${k}=${v}`)
    return parts.length ? parts.join('；') : '—'
  } catch {
    return '—'
  }
}

function onMetaSelectionChange(rows: Doc[]) {
  selectedMetaDocs.value = rows
}

async function batchExtractFeatures() {
  const ids = selectedMetaDocs.value.map((d) => d.id)
  if (!ids.length) {
    ElMessage.warning('请先勾选待提取的文件')
    return
  }
  metaBatchBusy.value = true
  try {
    const res = await api.post('/unstructured/platform/documents/batch-extract-features', { ids })
    ElMessage.success(String(res.data?.message || '批量特征提取完成'))
    await Promise.all([loadMetadataOverview(), loadDocuments()])
  } catch (e: unknown) {
    ElMessage.error((e instanceof Error ? e.message : '') || '批量特征提取失败')
  } finally {
    metaBatchBusy.value = false
  }
}

async function batchUnderstandContent() {
  const ids = selectedMetaDocs.value.map((d) => d.id)
  if (!ids.length) {
    ElMessage.warning('请先勾选待理解的文件')
    return
  }
  metaBatchBusy.value = true
  try {
    const res = await api.post('/unstructured/platform/documents/batch-understand', { ids })
    ElMessage.success(String(res.data?.message || '批量内容理解完成'))
    await Promise.all([loadMetadataOverview(), loadDocuments()])
  } catch (e: unknown) {
    ElMessage.error((e instanceof Error ? e.message : '') || '批量内容理解失败')
  } finally {
    metaBatchBusy.value = false
  }
}

async function extractFeatures(row: Doc) {
  metaBusyId.value = row.id
  try {
    const res = await api.post(`/unstructured/platform/documents/${row.id}/extract-features`)
    ElMessage.success(String(res.data?.message || '基本特征已提取'))
    await Promise.all([loadMetadataOverview(), loadDocuments()])
  } catch (e: unknown) {
    ElMessage.error((e instanceof Error ? e.message : '') || '特征提取失败')
  } finally {
    metaBusyId.value = null
  }
}

async function understandContent(row: Doc) {
  metaBusyId.value = row.id
  try {
    const res = await api.post(`/unstructured/platform/documents/${row.id}/understand`)
    ElMessage.success(String(res.data?.message || '内容理解已落地'))
    await Promise.all([loadMetadataOverview(), loadDocuments()])
  } catch (e: unknown) {
    ElMessage.error((e instanceof Error ? e.message : '') || '内容理解失败')
  } finally {
    metaBusyId.value = null
  }
}

async function openSimilar(row: Doc) {
  similarSeedId.value = row.id
  similarSeedTitle.value = row.title
  similarVisible.value = true
  similarLoading.value = true
  similarHits.value = []
  try {
    similarHits.value = (await api.get(`/unstructured/platform/documents/${row.id}/similar`, {
      params: { limit: 10 },
    })).data || []
  } catch {
    ElMessage.error('相似检索失败')
  } finally {
    similarLoading.value = false
  }
}

async function linkSimilarDoc(target: Doc) {
  if (!similarSeedId.value) return
  try {
    const res = await api.post(`/unstructured/platform/documents/${similarSeedId.value}/similar-link/${target.id}`)
    ElMessage.success(String(res.data?.message || '已建立相似连接'))
    await Promise.all([loadMetadataOverview(), loadDocuments()])
  } catch {
    ElMessage.error('建立相似连接失败')
  }
}

async function unlinkSimilarDoc(row: Doc) {
  try {
    await ElMessageBox.confirm(`确认解除「${row.title}」的相似连接？`, '解除相似连接', { type: 'warning' })
    const res = await api.delete(`/unstructured/platform/documents/${row.id}/similar-link`)
    ElMessage.success(String(res.data?.message || '已解除相似连接'))
    await Promise.all([loadMetadataOverview(), loadDocuments()])
  } catch { /* cancel */ }
}

async function runPipe(docId: number) {
  const type = processType.value
  if (type === 'CLEAN') {
    await runClean(docId)
    return
  }
  processBusyId.value = docId
  try {
    const res = await api.post(`/unstructured/platform/documents/${docId}/pipeline/${type}`)
    ElMessage.success(String(res.data?.message || '处理完成'))
    await Promise.all([loadPipelines(type), loadDocuments()])
  } catch {
    ElMessage.error(type === 'TAG' ? '标识执行失败' : '关联执行失败')
  } finally {
    processBusyId.value = null
  }
}

async function loadTagDefs() {
  tagDefs.value = (await api.get('/unstructured/platform/tag-defs')).data || []
  resetTagDefPage()
}

async function loadLinkRules() {
  linkRules.value = (await api.get('/unstructured/platform/link-rules')).data || []
  resetLinkRulePage()
}

function openTagDefCreate() {
  tagDefViewMode.value = false
  tagDefEditingId.value = undefined
  Object.assign(tagDefForm, {
    tagCode: '',
    tagName: '',
    tagKind: 'GENERAL',
    matchKeywords: '',
    description: '',
    enabled: 1,
    sortOrder: (tagDefs.value.length + 1) * 10,
  })
  tagDefDialogVisible.value = true
}

function openTagDefView(row: TagDef) {
  tagDefViewMode.value = true
  tagDefEditingId.value = row.id
  Object.assign(tagDefForm, {
    tagCode: row.tagCode,
    tagName: row.tagName,
    tagKind: row.tagKind || 'GENERAL',
    matchKeywords: row.matchKeywords || '',
    description: row.description || '',
    enabled: row.enabled ?? 1,
    sortOrder: row.sortOrder ?? 0,
  })
  tagDefDialogVisible.value = true
}

function openTagDefEdit(row: TagDef) {
  tagDefViewMode.value = false
  tagDefEditingId.value = row.id
  Object.assign(tagDefForm, {
    tagCode: row.tagCode,
    tagName: row.tagName,
    tagKind: row.tagKind || 'GENERAL',
    matchKeywords: row.matchKeywords || '',
    description: row.description || '',
    enabled: row.enabled ?? 1,
    sortOrder: row.sortOrder ?? 0,
  })
  tagDefDialogVisible.value = true
}

async function saveTagDef() {
  if (!tagDefForm.tagCode.trim() || !tagDefForm.tagName.trim()) {
    ElMessage.warning('请填写标签编码与名称')
    return
  }
  try {
    const body = { ...tagDefForm }
    if (tagDefEditingId.value) {
      await api.put(`/unstructured/platform/tag-defs/${tagDefEditingId.value}`, body)
    } else {
      await api.post('/unstructured/platform/tag-defs', body)
    }
    ElMessage.success('标签定义已保存')
    tagDefDialogVisible.value = false
    await loadTagDefs()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '保存失败')
  }
}

async function removeTagDef(row: TagDef) {
  await ElMessageBox.confirm(`确认删除标签「${row.tagName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/unstructured/platform/tag-defs/${row.id}`)
  ElMessage.success('已删除')
  await loadTagDefs()
}

function openLinkRuleCreate() {
  linkRuleViewMode.value = false
  linkRuleEditingId.value = undefined
  Object.assign(linkRuleForm, {
    ruleCode: '',
    ruleName: '',
    linkStage: 'ALL',
    algorithm: 'SIMILARITY',
    configJson: '{}',
    description: '',
    enabled: 1,
    sortOrder: (linkRules.value.length + 1) * 10,
  })
  linkRuleDialogVisible.value = true
}

function openLinkRuleView(row: LinkRule) {
  linkRuleViewMode.value = true
  linkRuleEditingId.value = row.id
  Object.assign(linkRuleForm, {
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    linkStage: row.linkStage || 'ALL',
    algorithm: row.algorithm || 'SIMILARITY',
    configJson: row.configJson || '{}',
    description: row.description || '',
    enabled: row.enabled ?? 1,
    sortOrder: row.sortOrder ?? 0,
  })
  linkRuleDialogVisible.value = true
}

function openLinkRuleEdit(row: LinkRule) {
  linkRuleViewMode.value = false
  linkRuleEditingId.value = row.id
  Object.assign(linkRuleForm, {
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    linkStage: row.linkStage || 'ALL',
    algorithm: row.algorithm || 'SIMILARITY',
    configJson: row.configJson || '{}',
    description: row.description || '',
    enabled: row.enabled ?? 1,
    sortOrder: row.sortOrder ?? 0,
  })
  linkRuleDialogVisible.value = true
}

async function saveLinkRule() {
  if (!linkRuleForm.ruleCode.trim() || !linkRuleForm.ruleName.trim()) {
    ElMessage.warning('请填写规则编码与名称')
    return
  }
  try {
    const body = { ...linkRuleForm }
    if (linkRuleEditingId.value) {
      await api.put(`/unstructured/platform/link-rules/${linkRuleEditingId.value}`, body)
    } else {
      await api.post('/unstructured/platform/link-rules', body)
    }
    ElMessage.success('关联规则已保存')
    linkRuleDialogVisible.value = false
    await loadLinkRules()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '保存失败')
  }
}

async function removeLinkRule(row: LinkRule) {
  await ElMessageBox.confirm(`确认删除关联规则「${row.ruleName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/unstructured/platform/link-rules/${row.id}`)
  ElMessage.success('已删除')
  await loadLinkRules()
}

function tagsBrief(row: Doc) {
  try {
    const arr = JSON.parse(row.tagJson || '[]')
    if (Array.isArray(arr) && arr.length) return arr.slice(0, 4).join('、')
  } catch { /* ignore */ }
  return '—'
}

async function runClean(docId: number) {
  cleanBusyId.value = docId
  try {
    const res = await api.post(`/unstructured/platform/documents/${docId}/pipeline/CLEAN`)
    cleanResult.value = res.data || null
    cleanResultVisible.value = true
    ElMessage.success(String(res.data?.message || '清洗完成'))
    await Promise.all([loadCleanOverview(), loadDocuments(), loadPipelines('CLEAN'), loadCleanIssues()])
  } catch {
    ElMessage.error('清洗执行失败')
  } finally {
    cleanBusyId.value = null
  }
}

function openRuleCreate() {
  ruleViewMode.value = false
  ruleEditingId.value = undefined
  Object.assign(ruleForm, {
    ruleCode: '',
    ruleName: '',
    ruleType: 'VALIDATE',
    targetField: 'title',
    errorLevel: 'WARN',
    enabled: 1,
    autoApply: 0,
    sortOrder: (cleanRules.value.length + 1) * 10,
    configJson: '{}',
    description: '',
  })
  ruleDialogVisible.value = true
}

function openRuleView(row: CleanRule) {
  ruleViewMode.value = true
  ruleEditingId.value = row.id
  Object.assign(ruleForm, {
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    ruleType: row.ruleType,
    targetField: row.targetField,
    errorLevel: row.errorLevel,
    enabled: row.enabled ?? 1,
    autoApply: row.autoApply ?? 0,
    sortOrder: row.sortOrder ?? 0,
    configJson: row.configJson || '{}',
    description: row.description || '',
  })
  ruleDialogVisible.value = true
}

function openRuleEdit(row: CleanRule) {
  ruleViewMode.value = false
  ruleEditingId.value = row.id
  Object.assign(ruleForm, {
    ruleCode: row.ruleCode,
    ruleName: row.ruleName,
    ruleType: row.ruleType,
    targetField: row.targetField,
    errorLevel: row.errorLevel,
    enabled: row.enabled ?? 1,
    autoApply: row.autoApply ?? 0,
    sortOrder: row.sortOrder ?? 0,
    configJson: row.configJson || '{}',
    description: row.description || '',
  })
  ruleDialogVisible.value = true
}

function openRuleRun(row: CleanRule) {
  if (row.enabled !== 1) {
    ElMessage.warning('请先启用该规则后再执行清洗')
    return
  }
  ruleRunTarget.value = row
  ruleRunDocId.value = undefined
  ruleRunVisible.value = true
}

async function confirmRuleRun() {
  if (!ruleRunDocId.value) {
    ElMessage.warning('请选择要清洗的非结构化文档')
    return
  }
  ruleRunVisible.value = false
  await runClean(ruleRunDocId.value)
}

async function saveRule() {
  if (!ruleForm.ruleName.trim() || !ruleForm.ruleCode.trim()) {
    ElMessage.warning('请填写规则编码与名称')
    return
  }
  try {
    const body = { ...ruleForm }
    if (ruleEditingId.value) {
      await api.put(`/unstructured/platform/clean/rules/${ruleEditingId.value}`, body)
    } else {
      await api.post('/unstructured/platform/clean/rules', body)
    }
    ElMessage.success('清洗规则已保存')
    ruleDialogVisible.value = false
    await Promise.all([loadCleanRules(), loadCleanOverview()])
  } catch {
    ElMessage.error('保存规则失败')
  }
}

async function removeRule(row: CleanRule) {
  await ElMessageBox.confirm(`确认删除规则「${row.ruleName}」？`, '删除确认', { type: 'warning' })
  await api.delete(`/unstructured/platform/clean/rules/${row.id}`)
  ElMessage.success('已删除')
  await Promise.all([loadCleanRules(), loadCleanOverview()])
}

async function handleIssue(row: CleanIssue, action: 'CLEANED_IN' | 'ABANDONED' | 'OTHER') {
  const labels = { CLEANED_IN: '清洗后入库', ABANDONED: '放弃', OTHER: '其他处理' }
  const { value } = await ElMessageBox.prompt(`对问题数据执行「${labels[action]}」`, '问题数据处置', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputPlaceholder: '处置说明（可选）',
  }).catch(() => ({ value: null as string | null }))
  if (value === null) return
  await api.post(`/unstructured/platform/clean/issues/${row.id}/handle`, {
    action,
    handleNote: value || undefined,
  })
  ElMessage.success('已处置')
  await Promise.all([loadCleanIssues(), loadCleanOverview(), loadDocuments()])
}

function parsePipelineDetail(row: Pipeline) {
  if (!row.detailJson) return null
  try {
    return JSON.parse(row.detailJson) as Record<string, unknown>
  } catch {
    return null
  }
}

function consistencyLabel(row: Pipeline) {
  const detail = parsePipelineDetail(row)
  const c = detail?.consistency as { passed?: boolean } | undefined
  if (!c) return '—'
  return c.passed ? '通过' : '未通过'
}

const processActionLabel = computed(() => statusLabel(processType.value))

watch(activeNav, () => {
  if (!applyingRoute) syncQuery()
  loadTabData()
})
watch(cleanTab, async (tab) => {
  if (activeNav.value !== 'process.clean') return
  try {
    if (tab === 'rules' && !cleanRules.value.length) await loadCleanRules()
    else if (tab === 'issues') await loadCleanIssues()
    else if (tab === 'pipelines') await loadPipelines('CLEAN')
  } catch {
    ElMessage.error('加载失败')
  }
})
watch(tagTab, async (tab) => {
  if (activeNav.value !== 'process.tag') return
  try {
    if (tab === 'defs' && !tagDefs.value.length) await loadTagDefs()
    else if (tab === 'tasks') await loadPipelines('TAG')
  } catch {
    ElMessage.error('加载失败')
  }
})
watch(linkTab, async (tab) => {
  if (activeNav.value !== 'process.link') return
  try {
    if (tab === 'rules' && !linkRules.value.length) await loadLinkRules()
    else if (tab === 'tasks') await loadPipelines('LINK')
  } catch {
    ElMessage.error('加载失败')
  }
})
watch(() => route.query.tab, () => { resolveFromRoute() })
onMounted(() => {
  resolveFromRoute()
  // 若 query 与默认侧栏相同，activeNav 不变不会触发 watch，需主动加载
  loadTabData()
})
</script>

<template>
  <div class="uns-hub-root" :class="{ 'uns-hub-root--embed': embedLocalOnly }">
    <HubSideLayout v-model="activeNav" :items="embedLocalOnly ? [] : navItems">
      <PageCard v-if="activeNav === 'classify'" title="数据分类管理">
        <div class="uns-toolbar">
          <div class="uns-list-header uns-list-header--inline">
            <span>文件分类体系</span>
          </div>
          <el-button type="primary" @click="addCategory">新增分类</el-button>
        </div>
        <el-table :data="pagedCategories" stripe border class="portal-table" size="small">
          <el-table-column prop="categoryCode" label="分类编码" width="140" show-overflow-tooltip />
          <el-table-column prop="categoryName" label="分类名称" min-width="140" show-overflow-tooltip />
          <el-table-column label="上级分类" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ categoryParentLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="媒介类型" width="100">
            <template #default="{ row }">{{ categoryMediaLabel(row.mediaType) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="分类说明" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="editCategory(row)">编辑</el-button>
              <el-button link type="danger" @click="deleteCategory(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-if="categories.length"
          v-model:page="catPage"
          v-model:page-size="catPageSize"
          :total="catTotal"
        />

        <el-dialog
          v-model="catDialogVisible"
          :title="catEditingId ? '编辑文件分类' : '新增文件分类'"
          width="560px"
        >
          <el-form label-width="90px">
            <el-form-item label="分类编码" required>
              <el-input v-model="catForm.categoryCode" placeholder="如 CAT_GOV_DOC；不填则自动生成" />
            </el-form-item>
            <el-form-item label="分类名称" required>
              <el-input v-model="catForm.categoryName" placeholder="如 政务公文" />
            </el-form-item>
            <el-form-item label="上级分类">
              <el-select v-model="catForm.parentId" clearable placeholder="顶级分类">
                <el-option
                  v-for="c in categories.filter((x) => x.id !== catEditingId)"
                  :key="c.id"
                  :label="c.categoryName"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="媒介类型" required>
              <el-select v-model="catForm.mediaType">
                <el-option v-for="m in MEDIA_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="排序号">
              <el-input-number v-model="catForm.sortOrder" :min="0" :max="9999" />
            </el-form-item>
            <el-form-item label="分类说明">
              <el-input v-model="catForm.description" type="textarea" :rows="3" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="catDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveCategory">保存</el-button>
          </template>
        </el-dialog>
      </PageCard>

      <PageCard v-else-if="activeNav === 'files'" :title="embedLocalOnly ? embedTitle : '文件资源管理'">
        <el-alert
          v-if="!embedLocalOnly"
          type="info"
          :closable="false"
          show-icon
          class="uns-files-hint"
          title="统一非结构化注册、发布、维护管理；对接外部文件业务平台并可动态同步，形成可持续更新的文件资源管理平台。"
        />
        <el-tabs
          v-model="filesSourceTab"
          class="uns-files-tabs"
          :class="{ 'uns-files-tabs--no-header': embedLocalOnly }"
          @tab-change="onFilesSourceTabChange"
        >
          <el-tab-pane label="本地上传" name="upload">
            <el-form inline class="portal-inline-form portal-inline-form--block">
              <el-form-item label="关键词" class="portal-field-lg">
                <el-input v-model="docQuery.keyword" clearable placeholder="标题或文件名" @keyup.enter="loadDocuments" />
              </el-form-item>
              <el-form-item label="分类" class="portal-field-md">
                <el-select v-model="docQuery.categoryCode" clearable placeholder="全部">
                  <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.categoryCode" />
                </el-select>
              </el-form-item>
              <el-form-item label="发布状态" class="portal-field-md">
                <el-select v-model="docQuery.publishStatus" clearable placeholder="全部">
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="已发布" value="PUBLISHED" />
                  <el-option label="已下线" value="OFFLINE" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="loadDocuments">查询</el-button>
                <el-button @click="onResetDocuments">重置</el-button>
                <el-button type="primary" @click="openDocCreate">注册</el-button>
                <el-button :loading="publishing" @click="toolbarPublish">发布</el-button>
                <el-button @click="toolbarMaintain">维护</el-button>
              </el-form-item>
            </el-form>
            <el-table
              :data="pagedDocs"
              stripe
              border
              class="portal-table"
              size="small"
              row-key="id"
              @selection-change="onUploadSelectionChange"
            >
              <el-table-column type="selection" width="48" />
              <el-table-column label="文件资源" min-width="190" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-button link type="primary" @click="openDetail(row.id)">{{ row.title }}</el-button>
                  <div class="uns-file-sub">{{ row.originalFileName || row.docCode }} · {{ formatSize(row.fileSize) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="分类" width="130" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ categories.find((c) => c.categoryCode === row.categoryCode)?.categoryName || row.categoryCode || '—' }}
                </template>
              </el-table-column>
              <el-table-column label="媒介" width="80">
                <template #default="{ row }">{{ mediaLabel(row.contentType) }}</template>
              </el-table-column>
              <el-table-column label="来源" min-width="100" show-overflow-tooltip>
                <template #default>本地上传</template>
              </el-table-column>
              <el-table-column label="发布状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.publishStatus)" size="small">{{ statusLabel(row.publishStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="360" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openDetail(row.id)">详情</el-button>
                  <el-button link type="primary" size="small" @click="openDocMaintain(row)">维护</el-button>
                  <el-button
                    v-if="row.publishStatus !== 'PUBLISHED'"
                    link
                    type="primary"
                    size="small"
                    @click="publishDoc(row)"
                  >发布</el-button>
                  <el-button
                    v-else
                    link
                    type="warning"
                    size="small"
                    @click="offlineDoc(row)"
                  >下线</el-button>
                  <el-button link type="primary" size="small" @click="accessFile(row, false)">预览</el-button>
                  <el-button link type="primary" size="small" @click="accessFile(row, true)">下载</el-button>
                  <el-button link type="danger" size="small" @click="deleteDoc(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <PortalPagination
              v-if="uploadDocs.length"
              v-model:page="docPage"
              v-model:page-size="docPageSize"
              :total="docTotal"
            />
          </el-tab-pane>

          <el-tab-pane v-if="!embedLocalOnly" label="外部平台" name="external">
            <el-tabs v-model="extInnerTab" type="card" class="uns-ext-inner-tabs" @tab-change="onExtInnerTabChange">
              <el-tab-pane label="外部文件" name="files">
                <el-form inline class="portal-inline-form portal-inline-form--block">
                  <el-form-item label="关键词" class="portal-field-lg">
                    <el-input v-model="extDocQuery.keyword" clearable placeholder="标题或文件名" @keyup.enter="loadDocuments" />
                  </el-form-item>
                  <el-form-item label="分类" class="portal-field-md">
                    <el-select v-model="extDocQuery.categoryCode" clearable placeholder="全部">
                      <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.categoryCode" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="发布状态" class="portal-field-md">
                    <el-select v-model="extDocQuery.publishStatus" clearable placeholder="全部">
                      <el-option label="草稿" value="DRAFT" />
                      <el-option label="已发布" value="PUBLISHED" />
                      <el-option label="已下线" value="OFFLINE" />
                    </el-select>
                  </el-form-item>
                  <el-form-item class="portal-form-actions">
                    <el-button type="primary" @click="loadDocuments">查询</el-button>
                    <el-button @click="onResetExternalDocuments">重置</el-button>
                    <el-button type="primary" @click="openDocCreate">注册</el-button>
                    <el-button :loading="publishing" @click="toolbarPublish">发布</el-button>
                    <el-button @click="toolbarMaintain">维护</el-button>
                  </el-form-item>
                </el-form>
                <el-table
                  :data="pagedExternalDocs"
                  stripe
                  border
                  class="portal-table"
                  size="small"
                  row-key="id"
                  @selection-change="onExternalSelectionChange"
                >
                  <el-table-column type="selection" width="48" />
                  <el-table-column label="文件资源" min-width="190" show-overflow-tooltip>
                    <template #default="{ row }">
                      <el-button link type="primary" @click="openDetail(row.id)">{{ row.title }}</el-button>
                      <div class="uns-file-sub">{{ row.originalFileName || row.docCode }} · {{ formatSize(row.fileSize) }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column label="分类" width="130" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ categories.find((c) => c.categoryCode === row.categoryCode)?.categoryName || row.categoryCode || '—' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="媒介" width="80">
                    <template #default="{ row }">{{ mediaLabel(row.contentType) }}</template>
                  </el-table-column>
                  <el-table-column label="来源平台" min-width="140" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.sourceSystem || '—' }}</template>
                  </el-table-column>
                  <el-table-column label="发布状态" width="100">
                    <template #default="{ row }">
                      <el-tag :type="statusTagType(row.publishStatus)" size="small">{{ statusLabel(row.publishStatus) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="更新时间" width="170">
                    <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="360" fixed="right">
                    <template #default="{ row }">
                      <el-button link type="primary" size="small" @click="openDetail(row.id)">详情</el-button>
                      <el-button link type="primary" size="small" @click="openDocMaintain(row)">维护</el-button>
                      <el-button
                        v-if="row.publishStatus !== 'PUBLISHED'"
                        link
                        type="primary"
                        size="small"
                        @click="publishDoc(row)"
                      >发布</el-button>
                      <el-button
                        v-else
                        link
                        type="warning"
                        size="small"
                        @click="offlineDoc(row)"
                      >下线</el-button>
                      <el-button link type="primary" size="small" @click="accessFile(row, false)">预览</el-button>
                      <el-button link type="primary" size="small" @click="accessFile(row, true)">下载</el-button>
                      <el-button link type="danger" size="small" @click="deleteDoc(row)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <el-empty v-if="!externalDocs.length" description="暂无外部文件，请点击「注册」登记" :image-size="72" />
                <PortalPagination
                  v-if="externalDocs.length"
                  v-model:page="extDocPage"
                  v-model:page-size="extDocPageSize"
                  :total="extDocTotal"
                />
              </el-tab-pane>

              <el-tab-pane label="平台连接" name="platforms">
                <el-form inline class="portal-inline-form portal-inline-form--block">
                  <el-form-item label="平台名称" class="portal-field-xl">
                    <el-input
                      v-model="extPlatQuery.platformName"
                      clearable
                      placeholder="请输入平台名称"
                      @keyup.enter="loadExternalPlatforms"
                    />
                  </el-form-item>
                  <el-form-item class="portal-form-actions">
                    <el-button type="primary" @click="loadExternalPlatforms">查询</el-button>
                    <el-button @click="onResetExternalPlatforms">重置</el-button>
                    <el-button type="primary" @click="openExtPlatCreate">新增对接</el-button>
                  </el-form-item>
                </el-form>
                <el-table :data="pagedExtPlatforms" stripe border class="portal-table" size="small">
                  <el-table-column type="index" label="序号" width="70" :index="extPlatIndex" />
                  <el-table-column prop="platformName" label="平台名称" min-width="160" show-overflow-tooltip />
                  <el-table-column label="对接方式" width="120">
                    <template #default="{ row }">{{ statusLabel(row.connectType) }}</template>
                  </el-table-column>
                  <el-table-column label="同步频率" width="100">
                    <template #default="{ row }">{{ statusLabel(row.syncFrequency) }}</template>
                  </el-table-column>
                  <el-table-column label="最近同步" width="170">
                    <template #default="{ row }">{{ formatDateTime(row.lastSyncAt) || '—' }}</template>
                  </el-table-column>
                  <el-table-column label="状态" width="90">
                    <template #default="{ row }">
                      <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="200" fixed="right">
                    <template #default="{ row }">
                      <el-button
                        link
                        type="primary"
                        :loading="syncingPlatformId === row.id"
                        @click="syncExternalPlatform(row)"
                      >动态同步</el-button>
                      <el-button link type="primary" @click="openExtPlatEdit(row)">编辑</el-button>
                      <el-button link type="danger" @click="deleteExternalPlatform(row)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <PortalPagination
                  v-if="externalPlatforms.length"
                  v-model:page="extPlatPage"
                  v-model:page-size="extPlatPageSize"
                  :total="extPlatTotal"
                />
              </el-tab-pane>
            </el-tabs>
          </el-tab-pane>
        </el-tabs>

        <el-dialog
          v-model="extPlatDialogVisible"
          :title="extPlatEditingId ? '编辑外部平台' : '新增'"
          width="620px"
        >
          <el-form label-width="110px">
            <el-form-item label="平台名称" required>
              <el-input v-model="extPlatForm.platformName" placeholder="请输入" />
            </el-form-item>
            <el-form-item label="对接方式" required>
              <el-select
                v-model="extPlatForm.connectType"
                placeholder="请选择"
                style="width:100%"
                @change="onExtPlatConnectTypeChange"
              >
                <el-option v-for="o in CONNECT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>

            <template v-if="extPlatForm.connectType === 'API'">
              <el-form-item label="接口地址" required>
                <el-input v-model="extPlatForm.config.baseUrl" placeholder="https://api.example.com/files" />
              </el-form-item>
              <el-form-item label="访问密钥">
                <el-input v-model="extPlatForm.config.apiKey" placeholder="可选，API Key / Token" show-password />
              </el-form-item>
            </template>

            <template v-else-if="extPlatForm.connectType === 'FTP'">
              <el-form-item label="主机地址" required>
                <el-input v-model="extPlatForm.config.host" placeholder="ftp.example.com" />
              </el-form-item>
              <el-form-item label="端口" required>
                <el-input-number v-model="extPlatForm.config.port" :min="1" :max="65535" :controls="false" placeholder="21" />
              </el-form-item>
              <el-form-item label="用户名" required>
                <el-input v-model="extPlatForm.config.username" placeholder="请输入" />
              </el-form-item>
              <el-form-item label="密码" required>
                <el-input v-model="extPlatForm.config.password" type="password" show-password placeholder="请输入" />
              </el-form-item>
              <el-form-item label="远程目录">
                <el-input v-model="extPlatForm.config.remotePath" placeholder="/" />
              </el-form-item>
            </template>

            <template v-else-if="extPlatForm.connectType === 'S3'">
              <el-form-item label="Endpoint" required>
                <el-input v-model="extPlatForm.config.endpoint" placeholder="http://s3.example.com:8333" />
              </el-form-item>
              <el-form-item label="AccessKey" required>
                <el-input v-model="extPlatForm.config.accessKey" placeholder="请输入" />
              </el-form-item>
              <el-form-item label="SecretKey" required>
                <el-input v-model="extPlatForm.config.secretKey" type="password" show-password placeholder="请输入" />
              </el-form-item>
              <el-form-item label="Bucket" required>
                <el-input v-model="extPlatForm.config.bucket" placeholder="bucket-name" />
              </el-form-item>
              <el-form-item label="Region">
                <el-input v-model="extPlatForm.config.region" placeholder="可选，如 us-east-1" />
              </el-form-item>
            </template>

            <template v-else-if="extPlatForm.connectType === 'HTTP'">
              <el-form-item label="请求地址" required>
                <el-input v-model="extPlatForm.config.url" placeholder="https://example.com/sync" />
              </el-form-item>
              <el-form-item label="请求方法" required>
                <el-select v-model="extPlatForm.config.method" style="width:100%">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                </el-select>
              </el-form-item>
              <el-form-item label="鉴权令牌">
                <el-input v-model="extPlatForm.config.apiKey" placeholder="可选 Bearer Token" show-password />
              </el-form-item>
              <el-form-item label="请求头">
                <el-input
                  v-model="extPlatForm.config.headers"
                  type="textarea"
                  :rows="2"
                  placeholder='可选，如 {"Content-Type":"application/json"}'
                />
              </el-form-item>
            </template>

            <template v-else-if="extPlatForm.connectType === 'DB_SYNC'">
              <el-form-item label="主机地址" required>
                <el-input v-model="extPlatForm.config.host" placeholder="127.0.0.1" />
              </el-form-item>
              <el-form-item label="端口" required>
                <el-input-number v-model="extPlatForm.config.port" :min="1" :max="65535" :controls="false" placeholder="3306" />
              </el-form-item>
              <el-form-item label="数据库名" required>
                <el-input v-model="extPlatForm.config.database" placeholder="请输入" />
              </el-form-item>
              <el-form-item label="用户名" required>
                <el-input v-model="extPlatForm.config.username" placeholder="请输入" />
              </el-form-item>
              <el-form-item label="密码" required>
                <el-input v-model="extPlatForm.config.password" type="password" show-password placeholder="请输入" />
              </el-form-item>
              <el-form-item label="JDBC URL">
                <el-input v-model="extPlatForm.config.jdbcUrl" placeholder="可选，不填则按主机/端口/库名拼接" />
              </el-form-item>
            </template>

            <el-form-item v-else label="接口配置" required>
              <el-alert type="info" :closable="false" title="请先选择对接方式，再填写对应配置项" />
            </el-form-item>

            <el-form-item label="同步频率" required>
              <el-select v-model="extPlatForm.syncFrequency" placeholder="请选择" style="width:100%">
                <el-option v-for="o in SYNC_FREQUENCY_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button type="primary" @click="saveExternalPlatform">保存</el-button>
            <el-button @click="extPlatDialogVisible = false">取消</el-button>
          </template>
        </el-dialog>

        <el-dialog
          v-model="docCreateVisible"
          :title="docForm.sourceType === 'EXTERNAL' ? '新增外部文件' : '新增本地文件'"
          width="620px"
          destroy-on-close
          @closed="resetDocForm"
        >
          <el-form label-width="90px">
            <el-form-item label="标题" required>
              <el-input v-model="docForm.title" placeholder="文档标题" />
            </el-form-item>
            <el-form-item label="分类" required>
              <el-select v-model="docForm.categoryCode" placeholder="选择已登记分类" filterable style="width:100%">
                <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.categoryCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="媒介类型">
              <el-select v-model="docForm.mediaHint" style="width:100%" @change="onMediaHintChange">
                <el-option v-for="m in MEDIA_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
              </el-select>
            </el-form-item>
            <template v-if="docForm.sourceType === 'EXTERNAL'">
              <el-form-item label="来源平台" required>
                <el-select
                  v-model="docForm.sourceSystem"
                  placeholder="选择已登记外部平台"
                  filterable
                  style="width:100%"
                >
                  <el-option
                    v-for="p in externalPlatforms.filter((x) => !x.status || x.status === 'ACTIVE')"
                    :key="p.id"
                    :label="p.platformName"
                    :value="p.platformName"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="资源地址" required>
                <el-input
                  v-model="docForm.sourceUrl"
                  placeholder="外部资源地址或业务键（未落盘亦可登记）"
                />
              </el-form-item>
              <el-alert
                v-if="!externalPlatforms.length"
                type="warning"
                :closable="false"
                show-icon
                title="暂无可用平台，请先在「平台连接」中登记外部平台"
                style="margin-bottom:12px"
              />
            </template>
            <el-form-item v-else label="选择文件" required>
              <el-upload
                action="#"
                :auto-upload="false"
                :limit="1"
                v-model:file-list="uploadFileList"
                :on-change="onUploadChange"
                :on-remove="onUploadRemove"
              >
                <el-button>选择文件（最大 200 MB）</el-button>
              </el-upload>
            </el-form-item>
            <el-form-item label="标签">
              <el-input v-model="docForm.tagText" placeholder="多个标签用逗号分隔" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="docForm.description" type="textarea" :rows="3" placeholder="文件内容与用途说明" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="docCreateVisible = false">取消</el-button>
            <el-button type="primary" :loading="docCreating" @click="registerDoc">注册</el-button>
          </template>
        </el-dialog>
      </PageCard>

      <PageCard v-else-if="activeNav === 'search'" title="文件资源检索">
        <div class="uns-search-card">
          <el-form inline class="portal-inline-form">
            <el-form-item label="关键词" class="portal-field-xl">
              <el-input v-model="searchQ" clearable placeholder="文件名、标题、描述或标签" @keyup.enter="doSearch" />
            </el-form-item>
            <el-form-item label="分类" class="portal-field-lg">
              <el-select v-model="searchCategory" clearable placeholder="全部分类">
                <el-option
                  v-for="c in categories"
                  :key="c.id"
                  :label="c.categoryName"
                  :value="c.categoryCode"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="文件类型" class="portal-field-md">
              <el-select v-model="searchMedia" clearable placeholder="全部类型">
                <el-option v-for="m in MEDIA_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="标签" class="portal-field-md">
              <el-input v-model="searchTag" clearable placeholder="标签关键词" />
            </el-form-item>
            <el-form-item label="创建时间" class="portal-field-xl">
              <el-date-picker
                v-model="searchCreatedRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
              />
            </el-form-item>
            <el-form-item label="更新时间" class="portal-field-xl">
              <el-date-picker
                v-model="searchUpdatedRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
              />
            </el-form-item>
            <el-form-item label="大小范围" class="portal-field-xl">
              <div class="uns-size-range">
                <el-input-number v-model="searchMinSizeMb" :min="0" :precision="1" placeholder="最小" />
                <span>至</span>
                <el-input-number v-model="searchMaxSizeMb" :min="0" :precision="1" placeholder="最大" />
                <span>MB</span>
              </div>
            </el-form-item>
            <el-form-item label="排序" class="portal-field-xl">
              <div class="uns-sort-group">
                <el-select v-model="searchSortBy">
                  <el-option label="更新时间" value="updatedAt" />
                  <el-option label="创建时间" value="createdAt" />
                  <el-option label="文件大小" value="fileSize" />
                  <el-option label="文件名称" value="title" />
                </el-select>
                <el-select v-model="searchSortDir">
                  <el-option label="降序" value="desc" />
                  <el-option label="升序" value="asc" />
                </el-select>
              </div>
            </el-form-item>
            <el-form-item class="portal-form-actions">
              <el-button type="primary" @click="doSearch">检索</el-button>
            </el-form-item>
          </el-form>
        </div>
        <template v-if="searchDone">
          <div class="uns-list-header">
            <span>检索结果</span>
            <em class="uns-list-header__count">共 {{ searchTotal }} 条</em>
          </div>
          <el-table :data="pagedSearchHits" stripe size="small">
            <el-table-column label="文件名称" min-width="180">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row.id)">{{ row.title }}</el-button>
                <div class="uns-file-sub">{{ row.originalFileName || row.title }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="categoryName" label="分类" width="130" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ mediaLabel(row.contentType) }}</template>
            </el-table-column>
            <el-table-column label="大小" width="100">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="更新时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="发布" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.publishStatus)" size="small">{{ statusLabel(row.publishStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="索引" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.indexStatus)" size="small">{{ statusLabel(row.indexStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="标签" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">{{ tagsLabel(row.tagJson) }}</template>
            </el-table-column>
            <el-table-column label="访问" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
                <el-button link @click="accessFile(row, false)">预览</el-button>
                <el-button link @click="accessFile(row, true)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
          <PortalPagination
            v-if="searchHits.length"
            v-model:page="searchPage"
            v-model:page-size="searchPageSize"
            :total="searchTotal"
          />
        </template>
        <el-empty v-else description="输入条件后点击检索" />
      </PageCard>

      <PageCard v-else-if="activeNav === 'metadata'" title="非结构化元数据管理">
        <el-alert
          class="uns-meta-flow"
          type="info"
          :closable="false"
          show-icon
          title="治理核心：基本特征提取 → 内容客观理解（关键词/主题/情感）→ 标签化与相似性检索/连接 → 元数据落地"
          description="除标题、格式等基本特征外，还可管理标签、相似性检索与相似连接，便于检索与消费。多媒体另支持分辨率/时长等特征及内容理解台账。"
        />

        <el-row :gutter="12" class="uns-meta-kpi">
          <el-col :span="6">
            <div class="uns-kpi uns-kpi--blue">
              <div class="uns-kpi-label">文档总数</div>
              <div class="uns-kpi-value">{{ metaOverview?.documents ?? docs.length }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="uns-kpi uns-kpi--orange">
              <div class="uns-kpi-label">待提取</div>
              <div class="uns-kpi-value">{{ metaOverview?.metaRaw ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="uns-kpi uns-kpi--green">
              <div class="uns-kpi-label">已提取特征</div>
              <div class="uns-kpi-value">{{ metaOverview?.metaExtracted ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="uns-kpi uns-kpi--purple">
              <div class="uns-kpi-label">已内容理解 / 已打标</div>
              <div class="uns-kpi-value">{{ metaOverview?.metaUnderstood ?? '—' }} / {{ metaOverview?.tagged ?? '—' }}</div>
            </div>
          </el-col>
        </el-row>

        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="关键词" class="portal-field-lg">
            <el-input
              v-model="metaQuery.keyword"
              clearable
              placeholder="标题 / 作者 / 标签"
              @keyup.enter="resetMetaPage"
            />
          </el-form-item>
          <el-form-item label="媒介类型" class="portal-field-md">
            <el-select v-model="metaQuery.mediaHint" clearable placeholder="全部">
              <el-option v-for="m in MEDIA_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="元数据状态" class="portal-field-md">
            <el-select v-model="metaQuery.metaStatus" clearable placeholder="全部">
              <el-option label="原始" value="RAW" />
              <el-option label="已提取特征" value="EXTRACTED" />
              <el-option label="已内容理解" value="UNDERSTOOD" />
            </el-select>
          </el-form-item>
          <el-form-item class="portal-form-actions">
            <el-button type="primary" @click="resetMetaPage">查询</el-button>
            <el-button @click="onResetMetaQuery">重置</el-button>
            <el-button :loading="metaBatchBusy" :disabled="!selectedMetaDocs.length" @click="batchExtractFeatures">
              批量特征提取
            </el-button>
            <el-button :loading="metaBatchBusy" :disabled="!selectedMetaDocs.length" @click="batchUnderstandContent">
              批量内容理解
            </el-button>
          </el-form-item>
        </el-form>

        <el-table
          :data="pagedMetaDocs"
          stripe
          border
          class="portal-table"
          size="small"
          @selection-change="onMetaSelectionChange"
        >
          <el-table-column type="selection" width="48" />
          <el-table-column prop="title" label="文件对象" min-width="150" show-overflow-tooltip />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ mediaLabel(row.contentType) }}</template>
          </el-table-column>
          <el-table-column label="作者" width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.author || '—' }}</template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="基本特征" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ featureBrief(row) }}</template>
          </el-table-column>
          <el-table-column label="关键词" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ contentListLabel(row, 'keywords') }}</template>
          </el-table-column>
          <el-table-column label="主题" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ contentListLabel(row, 'topics') }}</template>
          </el-table-column>
          <el-table-column label="情感" width="80">
            <template #default="{ row }">
              {{ statusLabel(parseContentField(row, 'sentiment') || '') }}
            </template>
          </el-table-column>
          <el-table-column label="标签" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ tagsLabel(row.tagJson) }}</template>
          </el-table-column>
          <el-table-column label="相似连接" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button
                v-if="row.linkedDocId"
                link
                type="primary"
                @click="openDetail(row.linkedDocId)"
              >{{ row.linkedDocTitle || `#${row.linkedDocId}` }}</el-button>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="元数据状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.metaStatus || 'RAW')" size="small">
                {{ statusLabel(row.metaStatus || 'RAW') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="360" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                size="small"
                :loading="metaBusyId === row.id"
                @click="extractFeatures(row)"
              >特征提取</el-button>
              <el-button
                link
                type="primary"
                size="small"
                :loading="metaBusyId === row.id"
                @click="understandContent(row)"
              >内容理解</el-button>
              <el-button link type="primary" size="small" @click="openSimilar(row)">相似检索</el-button>
              <el-button link type="primary" size="small" @click="openMetaEdit(row)">编辑落地</el-button>
              <el-button link type="primary" size="small" @click="openMetaDetail(row)">查看</el-button>
              <el-button
                v-if="row.linkedDocId"
                link
                type="danger"
                size="small"
                @click="unlinkSimilarDoc(row)"
              >解除连接</el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-if="metaFilteredDocs.length"
          v-model:page="metaPage"
          v-model:page-size="metaPageSize"
          :total="metaTotal"
        />

        <el-dialog v-model="metaDialogVisible" title="编辑并落地元数据" width="680px" @closed="metaEdit.id = 0">
          <el-form label-width="110px">
            <el-divider content-position="left">基本特征</el-divider>
            <el-form-item label="标题">
              <el-input v-model="metaEdit.title" />
            </el-form-item>
            <el-form-item label="作者">
              <el-input v-model="metaEdit.author" placeholder="作者/责任人" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="metaEdit.description" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="格式">
              <el-input v-model="metaEdit.mediaFormat" placeholder="如 PDF / PNG / MP4" />
            </el-form-item>
            <el-form-item label="分辨率">
              <div class="uns-meta-size-row">
                <el-input-number v-model="metaEdit.mediaWidth" :min="1" :controls="false" placeholder="宽" />
                <span>×</span>
                <el-input-number v-model="metaEdit.mediaHeight" :min="1" :controls="false" placeholder="高" />
              </div>
            </el-form-item>
            <el-form-item label="时长(秒)">
              <el-input-number v-model="metaEdit.mediaDurationSec" :min="0" :controls="false" />
            </el-form-item>
            <el-divider content-position="left">内容客观理解</el-divider>
            <el-form-item label="关键词">
              <el-input v-model="metaEdit.keywordsText" type="textarea" :rows="2" placeholder="逗号分隔，如：政务共享,目录" />
            </el-form-item>
            <el-form-item label="主题">
              <el-input v-model="metaEdit.topicsText" placeholder="逗号分隔，如：数据共享,规划公示" />
            </el-form-item>
            <el-form-item label="情感倾向">
              <el-select v-model="metaEdit.sentiment" clearable placeholder="请选择">
                <el-option label="正向" value="POSITIVE" />
                <el-option label="中性" value="NEUTRAL" />
                <el-option label="负向" value="NEGATIVE" />
              </el-select>
            </el-form-item>
            <el-form-item label="摘要">
              <el-input v-model="metaEdit.summary" type="textarea" :rows="2" placeholder="内容理解摘要" />
            </el-form-item>
            <el-form-item label="标签">
              <el-input v-model="metaEdit.tagText" type="textarea" :rows="2" placeholder="多个标签用逗号分隔，如：政务,公开" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="metaDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveMetadata">保存落地</el-button>
          </template>
        </el-dialog>

        <el-drawer v-model="metaDetailVisible" title="元数据详情" size="560px">
          <template v-if="metaDetailDoc">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="标题">{{ metaDetailDoc.title }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ mediaLabel(metaDetailDoc.contentType) }}</el-descriptions-item>
              <el-descriptions-item label="作者">{{ metaDetailDoc.author || '—' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatDateTime(metaDetailDoc.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="基本特征">{{ featureBrief(metaDetailDoc) }}</el-descriptions-item>
              <el-descriptions-item label="关键词">{{ contentListLabel(metaDetailDoc, 'keywords') }}</el-descriptions-item>
              <el-descriptions-item label="主题">{{ contentListLabel(metaDetailDoc, 'topics') }}</el-descriptions-item>
              <el-descriptions-item label="情感">{{ statusLabel(parseContentField(metaDetailDoc, 'sentiment') || '') }}</el-descriptions-item>
              <el-descriptions-item label="摘要">{{ contentSummary(metaDetailDoc) }}</el-descriptions-item>
              <el-descriptions-item label="多媒体理解">{{ mediaInsightsLabel(metaDetailDoc) }}</el-descriptions-item>
              <el-descriptions-item label="标签">{{ tagsLabel(metaDetailDoc.tagJson) }}</el-descriptions-item>
              <el-descriptions-item label="相似连接">
                {{ metaDetailDoc.linkedDocTitle || (metaDetailDoc.linkedDocId ? `#${metaDetailDoc.linkedDocId}` : '—') }}
              </el-descriptions-item>
              <el-descriptions-item label="元数据状态">{{ statusLabel(metaDetailDoc.metaStatus || 'RAW') }}</el-descriptions-item>
            </el-descriptions>
          </template>
        </el-drawer>

        <el-drawer v-model="similarVisible" :title="`相似检索：${similarSeedTitle}`" size="560px">
          <el-table v-loading="similarLoading" :data="similarHits" stripe border class="portal-table" size="small">
            <el-table-column prop="title" label="相似文档" min-width="160" show-overflow-tooltip />
            <el-table-column label="相似度" width="90">
              <template #default="{ row }">
                {{ row.similarity == null ? '—' : `${Math.round(Number(row.similarity) * 100)}%` }}
              </template>
            </el-table-column>
            <el-table-column label="标签" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ tagsLabel(row.tagJson) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="linkSimilarDoc(row)">建立连接</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!similarLoading && !similarHits.length" description="暂无相似结果，请先完成特征提取或内容理解" />
        </el-drawer>
      </PageCard>

      <PageCard
        v-else-if="activeNav === 'process.clean'"
        title="非结构化数据清洗转换处理"
      >
        <el-row :gutter="12" class="uns-meta-kpi">
          <el-col :span="4">
            <div class="uns-kpi uns-kpi--blue">
              <div class="uns-kpi-label">启用规则</div>
              <div class="uns-kpi-value">{{ cleanOverview?.rules ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="uns-kpi uns-kpi--green">
              <div class="uns-kpi-label">已清洗</div>
              <div class="uns-kpi-value">{{ cleanOverview?.cleaned ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="uns-kpi uns-kpi--orange">
              <div class="uns-kpi-label">问题文档</div>
              <div class="uns-kpi-value">{{ cleanOverview?.problem ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="uns-kpi uns-kpi--orange">
              <div class="uns-kpi-label">待确认问题</div>
              <div class="uns-kpi-value">{{ cleanOverview?.openIssues ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="uns-kpi">
              <div class="uns-kpi-label">已放弃</div>
              <div class="uns-kpi-value">{{ cleanOverview?.abandoned ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="uns-kpi uns-kpi--purple">
              <div class="uns-kpi-label">清洗任务</div>
              <div class="uns-kpi-value">{{ cleanOverview?.tasks ?? cleanOverview?.pipelines ?? '—' }}</div>
            </div>
          </el-col>
        </el-row>

        <el-tabs v-model="cleanTab">
          <el-tab-pane label="执行清洗" name="execute">
            <el-table :data="pagedProcessDocs" stripe border class="portal-table" size="small">
              <el-table-column prop="title" label="文档" min-width="160" show-overflow-tooltip />
              <el-table-column label="类型" width="80">
                <template #default="{ row }">{{ mediaLabel(row.contentType) }}</template>
              </el-table-column>
              <el-table-column label="处理状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.processStatus)" size="small">
                    {{ statusLabel(row.processStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="描述" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.description || '—' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="260" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openDetail(row.id)">查看</el-button>
                  <el-button link type="primary" @click="openDocEdit(row)">编辑</el-button>
                  <el-button
                    link
                    type="primary"
                    :loading="cleanBusyId === row.id"
                    @click="runClean(row.id)"
                  >执行清洗</el-button>
                  <el-button link type="danger" @click="deleteDoc(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <PortalPagination
              v-if="docs.length"
              v-model:page="processDocPage"
              v-model:page-size="processDocPageSize"
              :total="processDocTotal"
            />
          </el-tab-pane>

          <el-tab-pane label="清洗规则" name="rules">
            <div class="uns-toolbar">
              <div class="uns-list-header uns-list-header--inline">
                <span>规则定义（对非结构化文档元数据执行过滤/去重/校验/转换）</span>
              </div>
              <el-button type="primary" @click="openRuleCreate">新增规则</el-button>
            </div>
            <el-table :data="pagedCleanRules" stripe border class="portal-table" size="small">
              <el-table-column prop="ruleCode" label="编码" width="180" show-overflow-tooltip />
              <el-table-column prop="ruleName" label="名称" min-width="140" show-overflow-tooltip />
              <el-table-column label="类型" width="90">
                <template #default="{ row }">{{ statusLabel(row.ruleType) }}</template>
              </el-table-column>
              <el-table-column prop="targetField" label="目标字段" width="110" />
              <el-table-column label="错误级别" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.errorLevel)" size="small">{{ statusLabel(row.errorLevel) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="启用" width="70">
                <template #default="{ row }">{{ row.enabled === 1 ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column label="自动改写" width="90">
                <template #default="{ row }">{{ row.autoApply === 1 ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
              <el-table-column label="操作" width="260" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openRuleView(row)">查看</el-button>
                  <el-button link type="primary" @click="openRuleEdit(row)">编辑</el-button>
                  <el-button link type="primary" @click="openRuleRun(row)">执行清洗</el-button>
                  <el-button link type="danger" @click="removeRule(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <PortalPagination
              v-if="cleanRules.length"
              v-model:page="cleanRulePage"
              v-model:page-size="cleanRulePageSize"
              :total="cleanRuleTotal"
            />
          </el-tab-pane>

          <el-tab-pane label="问题数据" name="issues">
            <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent>
              <el-form-item label="状态" class="portal-field-md">
                <el-select v-model="cleanIssueStatus" clearable placeholder="全部">
                  <el-option label="待确认" value="OPEN" />
                  <el-option label="清洗后入库" value="CLEANED_IN" />
                  <el-option label="已放弃" value="ABANDONED" />
                  <el-option label="其他处理" value="OTHER" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="loadCleanIssues">查询</el-button>
                <el-button @click="onResetCleanIssues">重置</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="pagedCleanIssues" stripe size="small">
              <el-table-column prop="docTitle" label="文档" min-width="140" show-overflow-tooltip />
              <el-table-column prop="ruleCode" label="规则" width="160" show-overflow-tooltip />
              <el-table-column prop="targetField" label="字段" width="100" />
              <el-table-column label="错误级别" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.errorLevel)" size="small">{{ statusLabel(row.errorLevel) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">{{ statusLabel(row.issueStatus) }}</template>
              </el-table-column>
              <el-table-column prop="beforeValue" label="清洗前" min-width="120" show-overflow-tooltip />
              <el-table-column prop="afterValue" label="清洗后" min-width="120" show-overflow-tooltip />
              <el-table-column prop="message" label="说明" min-width="180" show-overflow-tooltip />
              <el-table-column label="处置" width="220" fixed="right">
                <template #default="{ row }">
                  <template v-if="row.issueStatus === 'OPEN'">
                    <el-button link type="primary" @click="handleIssue(row, 'CLEANED_IN')">入库</el-button>
                    <el-button link type="danger" @click="handleIssue(row, 'ABANDONED')">放弃</el-button>
                    <el-button link @click="handleIssue(row, 'OTHER')">其他</el-button>
                  </template>
                  <span v-else>—</span>
                </template>
              </el-table-column>
            </el-table>
            <PortalPagination
              v-if="cleanIssueTotal"
              v-model:page="cleanIssuePage"
              v-model:page-size="cleanIssuePageSize"
              :total="cleanIssueTotal"
            />
          </el-tab-pane>

          <el-tab-pane label="任务管理" name="pipelines">
            <el-table :data="pagedPipelines" stripe border class="portal-table" size="small">
              <el-table-column prop="docId" label="文档 ID" width="90" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="一致性" width="90">
                <template #default="{ row }">{{ consistencyLabel(row) }}</template>
              </el-table-column>
              <el-table-column prop="resultMessage" label="结果" min-width="260" show-overflow-tooltip />
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
            <PortalPagination
              v-if="pipelines.length"
              v-model:page="pipePage"
              v-model:page-size="pipePageSize"
              :total="pipeTotal"
            />
          </el-tab-pane>
        </el-tabs>

        <el-dialog
          v-model="ruleDialogVisible"
          :title="ruleViewMode ? '查看清洗规则' : ruleEditingId ? '编辑清洗规则' : '新增清洗规则'"
          width="640px"
          destroy-on-close
          append-to-body
        >
          <el-form label-width="100px" :disabled="ruleViewMode">
            <el-form-item label="规则编码">
              <el-input v-model="ruleForm.ruleCode" :disabled="ruleViewMode || !!ruleEditingId" placeholder="如 VALIDATE_TITLE_LENGTH" />
            </el-form-item>
            <el-form-item label="规则名称">
              <el-input v-model="ruleForm.ruleName" />
            </el-form-item>
            <el-form-item label="规则类型">
              <el-select v-model="ruleForm.ruleType">
                <el-option label="过滤" value="FILTER" />
                <el-option label="去重" value="DEDUP" />
                <el-option label="校验" value="VALIDATE" />
                <el-option label="转换" value="TRANSFORM" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标字段">
              <el-select v-model="ruleForm.targetField">
                <el-option label="title" value="title" />
                <el-option label="description" value="description" />
                <el-option label="author" value="author" />
                <el-option label="tagJson" value="tagJson" />
                <el-option label="mediaFormat" value="mediaFormat" />
              </el-select>
            </el-form-item>
            <el-form-item label="错误级别">
              <el-select v-model="ruleForm.errorLevel">
                <el-option label="信息" value="INFO" />
                <el-option label="警告" value="WARN" />
                <el-option label="错误" value="ERROR" />
                <el-option label="严重" value="CRITICAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <el-form-item label="自动改写">
              <el-switch v-model="ruleForm.autoApply" :active-value="1" :inactive-value="0" />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="ruleForm.sortOrder" :min="0" />
            </el-form-item>
            <el-form-item label="参数 JSON">
              <el-input v-model="ruleForm.configJson" type="textarea" :rows="4" placeholder='{"min":2,"max":200}' />
            </el-form-item>
            <el-form-item label="说明">
              <el-input v-model="ruleForm.description" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="ruleDialogVisible = false">{{ ruleViewMode ? '关闭' : '取消' }}</el-button>
            <el-button v-if="!ruleViewMode" type="primary" @click="saveRule">保存</el-button>
          </template>
        </el-dialog>

        <el-dialog
          v-model="ruleRunVisible"
          title="选择文档执行清洗"
          width="480px"
          destroy-on-close
          append-to-body
        >
          <el-alert
            type="info"
            :closable="false"
            show-icon
            :title="`规则「${ruleRunTarget?.ruleName || ''}」已启用，将与其它启用规则一并作用于所选文档。`"
            style="margin-bottom: 12px"
          />
          <el-form label-width="90px">
            <el-form-item label="目标文档" required>
              <el-select v-model="ruleRunDocId" filterable placeholder="请选择非结构化文档" style="width: 100%">
                <el-option v-for="d in docs" :key="d.id" :label="d.title" :value="d.id" />
              </el-select>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="ruleRunVisible = false">取消</el-button>
            <el-button type="primary" :loading="!!cleanBusyId" @click="confirmRuleRun">执行清洗</el-button>
          </template>
        </el-dialog>

        <el-drawer v-model="cleanResultVisible" title="本次清洗结果" size="520px">
          <template v-if="cleanResult">
            <el-alert :closable="false" show-icon :title="String(cleanResult.message || '')" type="success" class="uns-meta-flow" />
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="处理状态">{{ statusLabel(cleanResult.processStatus) }}</el-descriptions-item>
              <el-descriptions-item label="问题条数">{{ cleanResult.issueCount ?? 0 }}</el-descriptions-item>
              <el-descriptions-item label="一致性">
                {{ (cleanResult.consistency as { passed?: boolean } | undefined)?.passed ? '通过' : '未通过' }}
              </el-descriptions-item>
            </el-descriptions>
            <el-divider content-position="left">命中规则</el-divider>
            <el-table :data="(cleanResult.appliedRules as Record<string, unknown>[]) || []" size="small" stripe>
              <el-table-column prop="ruleName" label="规则" min-width="120" />
              <el-table-column label="类型" width="80">
                <template #default="{ row }">{{ statusLabel(row.ruleType) }}</template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="160" show-overflow-tooltip />
            </el-table>
          </template>
        </el-drawer>
      </PageCard>

      <PageCard v-else-if="activeNav === 'process.tag'" title="非结构化数据标识处理">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="uns-meta-flow"
          title="基于标签定义（通用标签 / 业务标签）与业务知识关键词比对，对文档打标分类；执行结果记入任务管理。"
        />
        <el-tabs v-model="tagTab" style="margin-top: 12px">
          <el-tab-pane label="执行标识" name="execute">
            <el-table :data="pagedProcessDocs" stripe border class="portal-table" size="small">
              <el-table-column prop="title" label="文档" min-width="160" show-overflow-tooltip />
              <el-table-column label="类型" width="80">
                <template #default="{ row }">{{ mediaLabel(row.contentType) }}</template>
              </el-table-column>
              <el-table-column label="标签" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ tagsBrief(row) }}</template>
              </el-table-column>
              <el-table-column label="处理状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.processStatus)" size="small">{{ statusLabel(row.processStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="260" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openDetail(row.id)">查看</el-button>
                  <el-button link type="primary" @click="openDocEdit(row)">编辑</el-button>
                  <el-button link type="primary" :loading="processBusyId === row.id" @click="runPipe(row.id)">执行标识</el-button>
                  <el-button link type="danger" @click="deleteDoc(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <PortalPagination v-if="docs.length" v-model:page="processDocPage" v-model:page-size="processDocPageSize" :total="processDocTotal" />
          </el-tab-pane>
          <el-tab-pane label="标签定义" name="defs">
            <div class="uns-toolbar">
              <div class="uns-list-header uns-list-header--inline"><span>通用标签 / 业务标签知识库</span></div>
              <el-button type="primary" @click="openTagDefCreate">新增标签</el-button>
            </div>
            <el-table :data="pagedTagDefs" stripe border class="portal-table" size="small">
              <el-table-column prop="tagCode" label="编码" width="160" show-overflow-tooltip />
              <el-table-column prop="tagName" label="名称" min-width="140" show-overflow-tooltip />
              <el-table-column label="类型" width="100">
                <template #default="{ row }">{{ TAG_KIND_ZH[row.tagKind] || row.tagKind }}</template>
              </el-table-column>
              <el-table-column prop="matchKeywords" label="匹配词" min-width="140" show-overflow-tooltip />
              <el-table-column label="启用" width="70">
                <template #default="{ row }">{{ row.enabled === 1 ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="description" label="说明" min-width="140" show-overflow-tooltip />
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openTagDefView(row)">查看</el-button>
                  <el-button link type="primary" @click="openTagDefEdit(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeTagDef(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <PortalPagination v-if="tagDefs.length" v-model:page="tagDefPage" v-model:page-size="tagDefPageSize" :total="tagDefTotal" />
          </el-tab-pane>
          <el-tab-pane label="任务管理" name="tasks">
            <el-table :data="pagedPipelines" stripe border class="portal-table" size="small">
              <el-table-column prop="docId" label="文档 ID" width="90" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="resultMessage" label="结果" min-width="240" show-overflow-tooltip />
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
            <PortalPagination v-if="pipelines.length" v-model:page="pipePage" v-model:page-size="pipePageSize" :total="pipeTotal" />
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <PageCard v-else-if="activeNav === 'process.link'" title="非结构化数据关联处理">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="uns-meta-flow"
          title="按关联规则执行：关联提取 → 关联分析 → 关联回填；结果写入文档关联字段并记入任务管理。"
        />
        <el-tabs v-model="linkTab" style="margin-top: 12px">
          <el-tab-pane label="执行关联" name="execute">
            <el-table :data="pagedProcessDocs" stripe border class="portal-table" size="small">
              <el-table-column prop="title" label="文档" min-width="160" show-overflow-tooltip />
              <el-table-column label="已关联" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">{{ row.linkedDocTitle || (row.linkedDocId ? `#${row.linkedDocId}` : '—') }}</template>
              </el-table-column>
              <el-table-column label="处理状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.processStatus)" size="small">{{ statusLabel(row.processStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="260" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openDetail(row.id)">查看</el-button>
                  <el-button link type="primary" @click="openDocEdit(row)">编辑</el-button>
                  <el-button link type="primary" :loading="processBusyId === row.id" @click="runPipe(row.id)">执行关联</el-button>
                  <el-button link type="danger" @click="deleteDoc(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <PortalPagination v-if="docs.length" v-model:page="processDocPage" v-model:page-size="processDocPageSize" :total="processDocTotal" />
          </el-tab-pane>
          <el-tab-pane label="关联规则" name="rules">
            <div class="uns-toolbar">
              <div class="uns-list-header uns-list-header--inline"><span>关联提取 / 分析 / 回填规则</span></div>
              <el-button type="primary" @click="openLinkRuleCreate">新增规则</el-button>
            </div>
            <el-table :data="pagedLinkRules" stripe border class="portal-table" size="small">
              <el-table-column prop="ruleCode" label="编码" width="160" show-overflow-tooltip />
              <el-table-column prop="ruleName" label="名称" min-width="140" show-overflow-tooltip />
              <el-table-column label="阶段" width="100">
                <template #default="{ row }">{{ LINK_STAGE_ZH[row.linkStage] || row.linkStage }}</template>
              </el-table-column>
              <el-table-column label="算法" width="100">
                <template #default="{ row }">{{ LINK_ALGO_ZH[row.algorithm] || row.algorithm }}</template>
              </el-table-column>
              <el-table-column label="启用" width="70">
                <template #default="{ row }">{{ row.enabled === 1 ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openLinkRuleView(row)">查看</el-button>
                  <el-button link type="primary" @click="openLinkRuleEdit(row)">编辑</el-button>
                  <el-button link type="danger" @click="removeLinkRule(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <PortalPagination v-if="linkRules.length" v-model:page="linkRulePage" v-model:page-size="linkRulePageSize" :total="linkRuleTotal" />
          </el-tab-pane>
          <el-tab-pane label="任务管理" name="tasks">
            <el-table :data="pagedPipelines" stripe border class="portal-table" size="small">
              <el-table-column prop="docId" label="文档 ID" width="90" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="resultMessage" label="结果" min-width="240" show-overflow-tooltip />
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
            </el-table>
            <PortalPagination v-if="pipelines.length" v-model:page="pipePage" v-model:page-size="pipePageSize" :total="pipeTotal" />
          </el-tab-pane>
        </el-tabs>
      </PageCard>

      <el-dialog
        v-model="tagDefDialogVisible"
        :title="tagDefViewMode ? '查看标签' : tagDefEditingId ? '编辑标签' : '新增标签'"
        width="560px"
        destroy-on-close
        append-to-body
      >
        <el-form label-width="100px" :disabled="tagDefViewMode">
          <el-form-item label="标签编码" required>
            <el-input v-model="tagDefForm.tagCode" :disabled="tagDefViewMode || !!tagDefEditingId" />
          </el-form-item>
          <el-form-item label="标签名称" required>
            <el-input v-model="tagDefForm.tagName" />
          </el-form-item>
          <el-form-item label="标签类型" required>
            <el-select v-model="tagDefForm.tagKind" style="width: 100%">
              <el-option label="通用标签" value="GENERAL" />
              <el-option label="业务标签" value="BUSINESS" />
            </el-select>
          </el-form-item>
          <el-form-item label="匹配关键词">
            <el-input v-model="tagDefForm.matchKeywords" placeholder="逗号分隔，如：控规,公示" />
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="tagDefForm.enabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="tagDefForm.sortOrder" :min="0" />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="tagDefForm.description" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="tagDefDialogVisible = false">{{ tagDefViewMode ? '关闭' : '取消' }}</el-button>
          <el-button v-if="!tagDefViewMode" type="primary" @click="saveTagDef">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="linkRuleDialogVisible"
        :title="linkRuleViewMode ? '查看关联规则' : linkRuleEditingId ? '编辑关联规则' : '新增关联规则'"
        width="600px"
        destroy-on-close
        append-to-body
      >
        <el-form label-width="100px" :disabled="linkRuleViewMode">
          <el-form-item label="规则编码" required>
            <el-input v-model="linkRuleForm.ruleCode" :disabled="linkRuleViewMode || !!linkRuleEditingId" />
          </el-form-item>
          <el-form-item label="规则名称" required>
            <el-input v-model="linkRuleForm.ruleName" />
          </el-form-item>
          <el-form-item label="关联阶段" required>
            <el-select v-model="linkRuleForm.linkStage" style="width: 100%">
              <el-option label="关联提取" value="EXTRACT" />
              <el-option label="关联分析" value="ANALYZE" />
              <el-option label="关联回填" value="BACKFILL" />
              <el-option label="全流程" value="ALL" />
            </el-select>
          </el-form-item>
          <el-form-item label="算法" required>
            <el-select v-model="linkRuleForm.algorithm" style="width: 100%">
              <el-option label="相似度" value="SIMILARITY" />
              <el-option label="同分类" value="CATEGORY" />
              <el-option label="关键词" value="KEYWORD" />
            </el-select>
          </el-form-item>
          <el-form-item label="参数 JSON">
            <el-input v-model="linkRuleForm.configJson" type="textarea" :rows="3" placeholder='{"keywords":["数据共享"]}' />
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="linkRuleForm.enabled" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="linkRuleForm.sortOrder" :min="0" />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="linkRuleForm.description" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="linkRuleDialogVisible = false">{{ linkRuleViewMode ? '关闭' : '取消' }}</el-button>
          <el-button v-if="!linkRuleViewMode" type="primary" @click="saveLinkRule">保存</el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="docDialogVisible"
        :title="docMaintainMode ? '维护文件资源' : '编辑文件资源'"
        width="560px"
        destroy-on-close
        append-to-body
      >
        <el-form label-width="90px">
          <el-form-item label="标题" required><el-input v-model="docEditing.title" /></el-form-item>
          <el-form-item label="分类" required>
            <el-select v-model="docEditing.categoryCode" filterable style="width:100%">
              <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.categoryCode" />
            </el-select>
          </el-form-item>
          <template v-if="docEditing.sourceType === 'EXTERNAL'">
            <el-form-item label="来源平台" required>
              <el-select v-model="docEditing.sourceSystem" filterable style="width:100%">
                <el-option
                  v-for="p in externalPlatforms.filter((x) => !x.status || x.status === 'ACTIVE')"
                  :key="p.id"
                  :label="p.platformName"
                  :value="p.platformName"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="资源地址" required>
              <el-input v-model="docEditing.sourceUrl" placeholder="外部资源地址或业务键" />
            </el-form-item>
          </template>
          <el-form-item label="标签"><el-input v-model="docEditing.tagText" placeholder="多个标签用逗号分隔" /></el-form-item>
          <el-form-item label="描述"><el-input v-model="docEditing.description" type="textarea" :rows="3" /></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="docDialogVisible = false">取消</el-button>
          <template v-if="docMaintainMode">
            <el-button @click="maintainBuildIndex">建索</el-button>
            <el-button type="warning" @click="maintainOffline">下线</el-button>
          </template>
          <el-button type="primary" @click="saveDocEdit">保存</el-button>
        </template>
      </el-dialog>

      <el-drawer v-model="detailVisible" title="文件资源详情" size="560px">
        <div v-loading="detailLoading">
          <template v-if="docDetail">
            <div class="uns-detail-head">
              <div class="uns-detail-icon">{{ mediaLabel(docDetail.contentType).slice(0, 1) }}</div>
              <div>
                <h3>{{ docDetail.title }}</h3>
                <p>{{ docDetail.originalFileName || docDetail.docCode }}</p>
              </div>
            </div>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="资源编码">{{ docDetail.docCode }}</el-descriptions-item>
              <el-descriptions-item label="文件分类">{{ docDetail.categoryName || docDetail.categoryCode || '—' }}</el-descriptions-item>
              <el-descriptions-item label="文件类型">{{ mediaLabel(docDetail.contentType) }}（{{ docDetail.contentType }}）</el-descriptions-item>
              <el-descriptions-item label="文件大小">{{ formatSize(docDetail.fileSize) }}</el-descriptions-item>
              <el-descriptions-item label="资源来源">{{ sourceLabel(docDetail) }}</el-descriptions-item>
              <el-descriptions-item label="发布状态">{{ statusLabel(docDetail.publishStatus) }}</el-descriptions-item>
              <el-descriptions-item label="索引状态">{{ statusLabel(docDetail.indexStatus) }}</el-descriptions-item>
              <el-descriptions-item label="关联标签">{{ tagsLabel(docDetail.tagJson) }}</el-descriptions-item>
              <el-descriptions-item label="文件描述">{{ docDetail.description || '—' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ formatDateTime(docDetail.createdAt) }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ formatDateTime(docDetail.updatedAt) }}</el-descriptions-item>
            </el-descriptions>
            <div class="uns-detail-actions">
              <el-button type="primary" @click="accessFile(docDetail, false)">预览文件</el-button>
              <el-button @click="accessFile(docDetail, true)">下载文件</el-button>
            </div>
          </template>
        </div>
      </el-drawer>
    </HubSideLayout>
  </div>
</template>

<style scoped>
.uns-hub-root {
  height: calc(100vh - var(--portal-header-height) - 40px);
  min-height: 0;
}
.uns-alert {
  margin-bottom: 12px;
}
.uns-ext-inner-tabs {
  margin-top: 4px;
}
.uns-ext-inner-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.uns-section-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
}
.uns-section-bar strong {
  display: block;
  margin-bottom: 3px;
  color: var(--el-text-color-primary);
}
.uns-section-bar span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.uns-file-sub {
  margin-top: 2px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.uns-files-hint {
  margin-bottom: 12px;
}
.uns-size-range,
.uns-sort-group {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.uns-size-range :deep(.el-input-number) {
  width: 78px;
}
.uns-sort-group :deep(.el-select:first-child) {
  flex: 1;
}
.uns-sort-group :deep(.el-select:last-child) {
  width: 76px;
}
.uns-detail-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px;
  border-radius: 8px;
  background: linear-gradient(135deg, #eef6ff, #f8fbff);
}
.uns-detail-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  border-radius: 10px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #409eff, #1769d2);
}
.uns-detail-head h3 {
  margin: 0 0 4px;
  font-size: 16px;
}
.uns-detail-head p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.uns-detail-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
.uns-meta-kpi {
  margin-bottom: 14px;
}
.uns-kpi {
  border-radius: 10px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  background: #fff;
}
.uns-kpi--blue {
  background: linear-gradient(135deg, #f0f7ff, #fafcff);
  border-color: #d6e8ff;
}
.uns-kpi--orange {
  background: linear-gradient(135deg, #fff8f0, #fffdfb);
  border-color: #ffe0c2;
}
.uns-kpi--green {
  background: linear-gradient(135deg, #f2fbf5, #fbfffc);
  border-color: #cfeedd;
}
.uns-kpi--purple {
  background: linear-gradient(135deg, #f7f3ff, #fcfbff);
  border-color: #e2d6ff;
}
.uns-kpi-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.uns-kpi-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 650;
  color: var(--el-text-color-primary);
  letter-spacing: 0.2px;
}
.uns-meta-flow {
  margin-bottom: 12px;
}
.uns-meta-size-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.uns-meta-size-row .el-input-number {
  width: 120px;
}
.uns-search-card {
  margin-bottom: 16px;
  padding: 16px 18px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: linear-gradient(135deg, #f5f9ff 0%, #fafcff 100%);
}
.uns-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.uns-list-header {
  display: flex;
  align-items: center;
  margin: 4px 0 10px;
  padding: 0 0 8px;
  border-bottom: 2px solid var(--el-color-primary-light-7);
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.uns-list-header--inline {
  margin: 0;
  padding: 0;
  border-bottom: none;
}
.uns-list-header__count {
  margin-left: 10px;
  font-style: normal;
  font-weight: 400;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.uns-list-header span::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 14px;
  background: var(--el-color-primary);
  border-radius: 2px;
  margin-right: 8px;
  vertical-align: text-bottom;
}
.uns-files-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}
.uns-files-tabs--no-header :deep(.el-tabs__header) {
  display: none;
}
.uns-files-tabs--no-header :deep(.el-tabs__content) {
  padding-top: 0;
}
.uns-hub-root--embed :deep(.hub-side-layout__aside) {
  display: none;
}
.uns-hub-root--embed :deep(.hub-side-layout) {
  min-height: 0;
}
.uns-hub-root--embed :deep(.hub-side-layout__main) {
  margin-left: 0;
  width: 100%;
  max-width: 100%;
}
</style>
