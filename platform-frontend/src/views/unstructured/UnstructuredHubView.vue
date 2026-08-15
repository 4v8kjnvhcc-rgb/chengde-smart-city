<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import PortalPagination from '@/components/common/PortalPagination.vue'
import { useClientPager } from '@/composables/useClientPager'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'
import { useAuthStore } from '@/stores/auth'
import { filterHubNavByPermissions, filterHubNavByMenuVisible, UNSTRUCT_NAV_PERMISSIONS } from '@/utils/hub-nav-permission'

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
  indexStatus: string
  publishStatus: string
  processStatus?: string
  tagJson?: string
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
const { page: docPage, pageSize: docPageSize, paged: pagedDocs, total: docTotal, resetPage: resetDocPage } = useClientPager(docs)
const {
  page: metaPage,
  pageSize: metaPageSize,
  paged: pagedMetaDocs,
  total: metaTotal,
  resetPage: resetMetaPage,
} = useClientPager(docs)
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
const filesSourceTab = ref<'upload' | 'external'>('upload')
const docQuery = reactive({ keyword: '', categoryCode: '', publishStatus: '' })
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
  sourceSystem: '',
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
  tagJson: '',
  mediaFormat: '',
  mediaWidth: undefined as number | undefined,
  mediaHeight: undefined as number | undefined,
  mediaDurationSec: undefined as number | undefined,
})
const metaOverview = ref<Record<string, unknown> | null>(null)
const metaBusyId = ref<number | null>(null)
const similarVisible = ref(false)
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
  applyingRoute = true
  const q = String(route.query.tab || DEFAULT_NAV).toLowerCase()
  activeNav.value = tabMap[q] || DEFAULT_NAV
  if (activeNav.value === 'process') activeNav.value = 'process.clean'
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
  docs.value = (await api.get('/unstructured/platform/documents', {
    params: {
      keyword: docQuery.keyword || undefined,
      publishStatus: docQuery.publishStatus || undefined,
      categoryCode: docQuery.categoryCode || undefined,
    },
  })).data || []
  resetDocPage()
  resetMetaPage()
  resetProcessDocPage()
}

function onResetDocuments() {
  docQuery.keyword = ''
  docQuery.categoryCode = ''
  docQuery.publishStatus = ''
  resetDocPage()
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
    await loadExternalPlatforms()
  } else if (!docs.value.length || !categories.value.length) {
    await Promise.all([loadDocuments(), loadCategories()])
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
        await loadExternalPlatforms()
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

function onFileChange(uploadFile: UploadFile) {
  selectedFile.value = uploadFile.raw || null
  if (selectedFile.value && !docForm.title.trim()) {
    docForm.title = selectedFile.value.name.replace(/\.[^.]+$/, '')
  }
  if (selectedFile.value?.type) {
    docForm.contentType = selectedFile.value.type
  }
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
  if (!selectedFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  const form = new FormData()
  form.append('file', selectedFile.value)
  form.append('title', docForm.title.trim())
  form.append('categoryCode', docForm.categoryCode)
  form.append('description', docForm.description.trim())
  form.append('tagJson', tagsToJson(docForm.tagText))
  form.append('sourceSystem', docForm.sourceSystem.trim())
  await api.post('/unstructured/platform/documents/upload', form)
  ElMessage.success('文件资源已登记')
  docForm.title = ''
  docForm.description = ''
  docForm.tagText = ''
  docForm.sourceSystem = ''
  selectedFile.value = null
  await loadDocuments()
  overviewLoaded = false
  await loadOverview()
}

function openDocEdit(row: Doc) {
  Object.assign(docEditing, {
    id: row.id,
    title: row.title,
    categoryCode: row.categoryCode || '',
    description: row.description || '',
    tagText: tagsLabel(row.tagJson) === '—' ? '' : tagsLabel(row.tagJson),
    sourceSystem: row.sourceSystem || '',
  })
  docDialogVisible.value = true
}

async function saveDocEdit() {
  await api.put(`/unstructured/platform/documents/${docEditing.id}`, {
    title: docEditing.title.trim(),
    categoryCode: docEditing.categoryCode,
    description: docEditing.description.trim(),
    tagJson: tagsToJson(docEditing.tagText),
    sourceSystem: docEditing.sourceSystem.trim(),
  })
  ElMessage.success('文件资源已更新')
  docDialogVisible.value = false
  await loadDocuments()
}

async function deleteDoc(row: Doc) {
  try {
    await ElMessageBox.confirm(`确认删除文件资源「${row.title}」？`, '删除文件资源', { type: 'warning' })
    await api.delete(`/unstructured/platform/documents/${row.id}`)
    ElMessage.success('文件资源已删除')
    await loadDocuments()
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

async function accessFile(row: Doc | SearchHit, download = false) {
  if (row.sourceType === 'EXTERNAL' && row.sourceUrl) {
    window.open(row.sourceUrl, '_blank', 'noopener,noreferrer')
    return
  }
  try {
    const res = await api.get(`/unstructured/platform/documents/${row.id}/content`, {
      params: { download },
      responseType: 'blob',
    })
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data], { type: row.contentType })
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

async function publishDoc(id: number) {
  await api.post(`/unstructured/platform/documents/${id}/publish`)
  ElMessage.success('已发布')
  await loadDocuments()
}

async function offlineDoc(id: number) {
  await api.post(`/unstructured/platform/documents/${id}/offline`)
  ElMessage.success('已下线')
  await loadDocuments()
}

async function indexDoc(id: number) {
  await api.post(`/unstructured/platform/documents/${id}/index`)
  ElMessage.success('已建索引')
  await loadDocuments()
  overviewLoaded = false
  await loadOverview()
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
  metaEdit.tagJson = row.tagJson || '[]'
  metaEdit.mediaFormat = row.mediaFormat || ''
  metaEdit.mediaWidth = row.mediaWidth
  metaEdit.mediaHeight = row.mediaHeight
  metaEdit.mediaDurationSec = row.mediaDurationSec
  metaDialogVisible.value = true
}

async function saveMetadata() {
  if (!metaEdit.id) return
  try {
    await api.put(`/unstructured/platform/documents/${metaEdit.id}/metadata`, {
      title: metaEdit.title,
      author: metaEdit.author || null,
      description: metaEdit.description || null,
      tagJson: metaEdit.tagJson,
      mediaFormat: metaEdit.mediaFormat || null,
      mediaWidth: metaEdit.mediaWidth ?? null,
      mediaHeight: metaEdit.mediaHeight ?? null,
      mediaDurationSec: metaEdit.mediaDurationSec ?? null,
    })
    ElMessage.success('元数据已落地保存')
    metaDialogVisible.value = false
    metaEdit.id = 0
    await Promise.all([loadMetadataOverview(), loadDocuments()])
  } catch {
    ElMessage.error('保存元数据失败')
  }
}

async function extractFeatures(row: Doc) {
  metaBusyId.value = row.id
  try {
    const res = await api.post(`/unstructured/platform/documents/${row.id}/extract-features`)
    ElMessage.success(String(res.data?.message || '基本特征已提取'))
    await Promise.all([loadMetadataOverview(), loadDocuments()])
  } catch {
    ElMessage.error('特征提取失败')
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
  } catch {
    ElMessage.error('内容理解失败')
  } finally {
    metaBusyId.value = null
  }
}

async function openSimilar(row: Doc) {
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

async function runPipe(docId: number) {
  const type = processType.value
  if (type === 'CLEAN') {
    await runClean(docId)
    return
  }
  const res = await api.post(`/unstructured/platform/documents/${docId}/pipeline/${type}`)
  ElMessage.success(String(res.data?.message || '处理完成'))
  await Promise.all([loadPipelines(type), loadDocuments()])
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

function openRuleEdit(row: CleanRule) {
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
watch(() => route.query.tab, () => { resolveFromRoute() })
onMounted(() => {
  resolveFromRoute()
  // 若 query 与默认侧栏相同，activeNav 不变不会触发 watch，需主动加载
  loadTabData()
})
</script>

<template>
  <div class="uns-hub-root">
    <HubSideLayout v-model="activeNav" :items="navItems">
      <PageCard v-if="activeNav === 'classify'" title="数据分类管理">
        <div class="uns-toolbar">
          <div class="uns-list-header uns-list-header--inline">
            <span>文件分类体系</span>
          </div>
          <el-button type="primary" @click="addCategory">新增分类</el-button>
        </div>
        <el-table :data="pagedCategories" stripe size="small">
          <el-table-column prop="categoryCode" label="编码" width="160" />
          <el-table-column prop="categoryName" label="名称" min-width="140" />
          <el-table-column label="上级分类" min-width="120">
            <template #default="{ row }">{{ row.parentId ? categoryNameById.get(row.parentId) || '—' : '顶级分类' }}</template>
          </el-table-column>
          <el-table-column label="媒介" width="100">
            <template #default="{ row }">{{ statusLabel(row.mediaType) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
          <el-table-column prop="sortOrder" label="排序" width="70" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
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

      <PageCard v-else-if="activeNav === 'files'" title="文件资源管理">
        <el-tabs v-model="filesSourceTab" class="uns-files-tabs" @tab-change="onFilesSourceTabChange">
          <el-tab-pane label="本地上传" name="upload">
            <div class="uns-register-card">
              <div class="uns-register-card__header">
                <span class="uns-register-card__title">登记本地文件</span>
              </div>
              <el-form inline class="portal-inline-form">
                <el-form-item label="标题" class="portal-field-lg">
                  <el-input v-model="docForm.title" placeholder="文档标题" />
                </el-form-item>
                <el-form-item label="分类" class="portal-field-lg">
                  <el-select v-model="docForm.categoryCode" placeholder="选择已登记分类" filterable>
                    <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.categoryCode" />
                  </el-select>
                </el-form-item>
                <el-form-item label="媒介类型" class="portal-field-sm">
                  <el-select v-model="docForm.mediaHint" @change="onMediaHintChange">
                    <el-option v-for="m in MEDIA_OPTIONS" :key="m.value" :label="m.label" :value="m.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="来源平台" class="portal-field-lg">
                  <el-input v-model="docForm.sourceSystem" placeholder="可选，如 OA 系统" />
                </el-form-item>
                <el-form-item label="选择文件" class="portal-field-xl">
                  <el-upload action="#" :auto-upload="false" :limit="1" :show-file-list="true" @change="onFileChange">
                    <el-button>选择文件（最大 200 MB）</el-button>
                  </el-upload>
                </el-form-item>
                <el-form-item label="标签" class="portal-field-lg">
                  <el-input v-model="docForm.tagText" placeholder="多个标签用逗号分隔" />
                </el-form-item>
                <el-form-item label="描述" class="portal-field-xl">
                  <el-input v-model="docForm.description" placeholder="文件内容与用途说明" />
                </el-form-item>
                <el-form-item class="portal-form-actions">
                  <el-button type="primary" @click="registerDoc">登记</el-button>
                </el-form-item>
              </el-form>
            </div>
            <div class="uns-list-header">
              <span>已登记文件资源</span>
            </div>
            <el-form inline class="portal-inline-form portal-inline-form--block portal-inline-form--sm" size="small">
              <el-form-item label="关键词" class="portal-field-lg">
                <el-input v-model="docQuery.keyword" clearable placeholder="标题或文件名" @keyup.enter="loadDocuments" />
              </el-form-item>
              <el-form-item label="分类" class="portal-field-md">
                <el-select v-model="docQuery.categoryCode" clearable placeholder="全部分类">
                  <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.categoryCode" />
                </el-select>
              </el-form-item>
              <el-form-item label="发布状态" class="portal-field-sm">
                <el-select v-model="docQuery.publishStatus" clearable placeholder="全部">
                  <el-option label="草稿" value="DRAFT" />
                  <el-option label="已发布" value="PUBLISHED" />
                  <el-option label="已下线" value="OFFLINE" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="loadDocuments">查询</el-button>
                <el-button @click="onResetDocuments">重置</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="pagedDocs" stripe size="small">
              <el-table-column label="文件资源" min-width="190">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openDetail(row.id)">{{ row.title }}</el-button>
                  <div class="uns-file-sub">{{ row.originalFileName || row.docCode }} · {{ formatSize(row.fileSize) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="分类" width="130">
                <template #default="{ row }">
                  {{ categories.find((c) => c.categoryCode === row.categoryCode)?.categoryName || row.categoryCode || '—' }}
                </template>
              </el-table-column>
              <el-table-column label="媒介" width="80">
                <template #default="{ row }">{{ mediaLabel(row.contentType) }}</template>
              </el-table-column>
              <el-table-column label="来源" min-width="120">
                <template #default="{ row }">{{ row.sourceSystem || (row.sourceType === 'EXTERNAL' ? '外部平台' : '平台上传') }}</template>
              </el-table-column>
              <el-table-column label="发布" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.publishStatus)" size="small">
                    {{ statusLabel(row.publishStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="索引" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.indexStatus)" size="small">
                    {{ statusLabel(row.indexStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openDetail(row.id)">详情</el-button>
                  <el-button link size="small" @click="accessFile(row, false)">预览</el-button>
                  <el-button link size="small" @click="accessFile(row, true)">下载</el-button>
                  <el-divider direction="vertical" />
                  <el-button link type="primary" size="small" @click="openDocEdit(row)">编辑</el-button>
                  <el-button
                    v-if="row.publishStatus !== 'PUBLISHED'"
                    link
                    type="success"
                    size="small"
                    @click="publishDoc(row.id)"
                  >发布</el-button>
                  <el-button v-else link size="small" @click="offlineDoc(row.id)">下线</el-button>
                  <el-dropdown v-if="row.indexStatus !== 'INDEXED' || row.publishStatus !== 'PUBLISHED'" trigger="click" size="small">
                    <el-button link size="small">更多</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-if="row.indexStatus !== 'INDEXED'" @click="indexDoc(row.id)">建立索引</el-dropdown-item>
                        <el-dropdown-item v-if="row.publishStatus !== 'PUBLISHED'" divided style="color:var(--el-color-danger)" @click="deleteDoc(row)">删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </template>
              </el-table-column>
            </el-table>
            <PortalPagination
              v-if="docs.length"
              v-model:page="docPage"
              v-model:page-size="docPageSize"
              :total="docTotal"
            />
          </el-tab-pane>

          <el-tab-pane label="外部平台" name="external">
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
                <el-button type="primary" @click="openExtPlatCreate">新增</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="pagedExtPlatforms" stripe size="small">
              <el-table-column type="index" label="序号" width="70" :index="extPlatIndex" />
              <el-table-column prop="platformName" label="平台名称" min-width="180" show-overflow-tooltip />
              <el-table-column label="对接方式" width="140">
                <template #default="{ row }">{{ statusLabel(row.connectType) }}</template>
              </el-table-column>
              <el-table-column label="同步频率" width="110">
                <template #default="{ row }">{{ statusLabel(row.syncFrequency) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140" fixed="right">
                <template #default="{ row }">
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

        <el-dialog v-model="docDialogVisible" title="编辑文件资源" width="560px">
          <el-form label-width="90px">
            <el-form-item label="标题" required><el-input v-model="docEditing.title" /></el-form-item>
            <el-form-item label="分类" required>
              <el-select v-model="docEditing.categoryCode" filterable>
                <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.categoryCode" />
              </el-select>
            </el-form-item>
            <el-form-item label="来源平台"><el-input v-model="docEditing.sourceSystem" /></el-form-item>
            <el-form-item label="标签"><el-input v-model="docEditing.tagText" placeholder="多个标签用逗号分隔" /></el-form-item>
            <el-form-item label="描述"><el-input v-model="docEditing.description" type="textarea" :rows="3" /></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="docDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveDocEdit">保存</el-button>
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
            <el-table-column prop="createdAt" label="创建时间" width="160" />
            <el-table-column prop="updatedAt" label="更新时间" width="160" />
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
        <div class="uns-list-header">
          <span>元数据列表</span>
        </div>
        <el-table :data="pagedMetaDocs" stripe size="small">
          <el-table-column prop="title" label="文档" min-width="150" show-overflow-tooltip />
          <el-table-column label="类型" width="80">
            <template #default="{ row }">{{ mediaLabel(row.contentType) }}</template>
          </el-table-column>
          <el-table-column label="作者" width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ row.author || '—' }}</template>
          </el-table-column>
          <el-table-column label="基本特征" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ featureBrief(row) }}</template>
          </el-table-column>
          <el-table-column label="关键词" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ contentListLabel(row, 'keywords') }}</template>
          </el-table-column>
          <el-table-column label="主题" min-width="120" show-overflow-tooltip>
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
          <el-table-column label="元数据状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.metaStatus || 'RAW')" size="small">
                {{ statusLabel(row.metaStatus || 'RAW') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
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
              <el-dropdown trigger="click" size="small">
                <el-button link size="small">更多</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="openSimilar(row)">相似检索</el-dropdown-item>
                    <el-dropdown-item @click="openMetaEdit(row)">编辑落地</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-if="docs.length"
          v-model:page="metaPage"
          v-model:page-size="metaPageSize"
          :total="metaTotal"
        />

        <el-dialog v-model="metaDialogVisible" title="编辑并落地元数据" width="620px" @closed="metaEdit.id = 0">
          <el-form label-width="100px">
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
            <el-form-item label="标签 JSON">
              <el-input v-model="metaEdit.tagJson" type="textarea" :rows="3" placeholder='["政务","公开"]' />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="metaDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveMetadata">保存落地</el-button>
          </template>
        </el-dialog>

        <el-drawer v-model="similarVisible" :title="`相似检索：${similarSeedTitle}`" size="520px">
<el-table v-loading="similarLoading" :data="similarHits" stripe size="small">
            <el-table-column prop="title" label="相似文档" min-width="160" show-overflow-tooltip />
            <el-table-column label="相似度" width="90">
              <template #default="{ row }">
                {{ row.similarity == null ? '—' : `${Math.round(Number(row.similarity) * 100)}%` }}
              </template>
            </el-table-column>
            <el-table-column label="标签" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ tagsLabel(row.tagJson) }}</template>
            </el-table-column>
            <el-table-column label="元数据" width="110">
              <template #default="{ row }">{{ statusLabel(row.metaStatus || 'RAW') }}</template>
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
              <div class="uns-kpi-label">清洗流水</div>
              <div class="uns-kpi-value">{{ cleanOverview?.pipelines ?? '—' }}</div>
            </div>
          </el-col>
        </el-row>

        <el-tabs v-model="cleanTab">
          <el-tab-pane label="执行清洗" name="execute">
            <el-table :data="pagedProcessDocs" stripe size="small">
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
              <el-table-column label="操作" width="140" fixed="right">
                <template #default="{ row }">
                  <el-button
                    link
                    type="primary"
                    :loading="cleanBusyId === row.id"
                    @click="runClean(row.id)"
                  >执行清洗</el-button>
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
                <span>规则定义</span>
              </div>
              <el-button type="primary" @click="openRuleCreate">新增规则</el-button>
            </div>
            <el-table :data="pagedCleanRules" stripe size="small">
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
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openRuleEdit(row)">编辑</el-button>
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

          <el-tab-pane label="清洗流水线" name="pipelines">
            <el-table :data="pagedPipelines" stripe size="small">
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
              <el-table-column prop="createdAt" label="时间" width="160" />
            </el-table>
            <PortalPagination
              v-if="pipelines.length"
              v-model:page="pipePage"
              v-model:page-size="pipePageSize"
              :total="pipeTotal"
            />
          </el-tab-pane>
        </el-tabs>

        <el-dialog v-model="ruleDialogVisible" :title="ruleEditingId ? '编辑清洗规则' : '新增清洗规则'" width="640px">
          <el-form label-width="100px">
            <el-form-item label="规则编码">
              <el-input v-model="ruleForm.ruleCode" :disabled="!!ruleEditingId" placeholder="如 VALIDATE_TITLE_LENGTH" />
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
            <el-button @click="ruleDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveRule">保存</el-button>
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

      <PageCard
        v-else-if="activeNav.startsWith('process.')"
        :title="pageTitle"
      >
        <div class="uns-list-header">
          <span>待处理文档</span>
        </div>
        <el-table :data="pagedProcessDocs" stripe size="small">
          <el-table-column prop="title" label="文档" min-width="160" />
          <el-table-column label="处理状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.processStatus)" size="small">
                {{ statusLabel(row.processStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" @click="runPipe(row.id)">
                执行{{ processActionLabel }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <PortalPagination
          v-if="docs.length"
          v-model:page="processDocPage"
          v-model:page-size="processDocPageSize"
          :total="processDocTotal"
        />
        <div class="uns-list-header" style="margin-top:16px">
          <span>处理流水线</span>
        </div>
        <el-table :data="pagedPipelines" stripe size="small">
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ statusLabel(row.pipelineType) }}</template>
          </el-table-column>
          <el-table-column prop="docId" label="文档 ID" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="resultMessage" label="结果" min-width="200" show-overflow-tooltip />
        </el-table>
        <PortalPagination
          v-if="pipelines.length"
          v-model:page="pipePage"
          v-model:page-size="pipePageSize"
          :total="pipeTotal"
        />
      </PageCard>

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
              <el-descriptions-item label="资源来源">{{ docDetail.sourceSystem || (docDetail.sourceType === 'EXTERNAL' ? '外部平台' : '平台上传') }}</el-descriptions-item>
              <el-descriptions-item label="发布状态">{{ statusLabel(docDetail.publishStatus) }}</el-descriptions-item>
              <el-descriptions-item label="索引状态">{{ statusLabel(docDetail.indexStatus) }}</el-descriptions-item>
              <el-descriptions-item label="关联标签">{{ tagsLabel(docDetail.tagJson) }}</el-descriptions-item>
              <el-descriptions-item label="文件描述">{{ docDetail.description || '—' }}</el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ docDetail.createdAt || '—' }}</el-descriptions-item>
              <el-descriptions-item label="更新时间">{{ docDetail.updatedAt || '—' }}</el-descriptions-item>
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
.uns-register-card,
.uns-search-card {
  margin-bottom: 16px;
  padding: 16px 18px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: linear-gradient(135deg, #f5f9ff 0%, #fafcff 100%);
}
.uns-register-card__header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 12px;
}
.uns-register-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
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
</style>
