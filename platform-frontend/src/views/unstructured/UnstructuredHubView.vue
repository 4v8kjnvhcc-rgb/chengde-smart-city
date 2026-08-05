<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/http'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import PageCard from '@/components/common/PageCard.vue'
import HubSideLayout, { type HubNavItem } from '@/components/common/HubSideLayout.vue'
import { statusLabel, statusTagType } from '@/utils/status-label'

const navItems: HubNavItem[] = [
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
const docs = ref<Doc[]>([])
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
const docQuery = reactive({ keyword: '', categoryCode: '', publishStatus: '' })
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
const cleanIssues = ref<CleanIssue[]>([])
const cleanTab = ref('execute')
const cleanIssueStatus = ref('OPEN')
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
    const child = navItems.find((n) => n.key === 'process')?.children?.find((c) => c.key === leaf)
    return child?.label || '非结构化数据处理'
  }
  return navItems.find((n) => n.key === leaf)?.label || '非结构数据融合治理平台'
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
}

async function loadDocuments() {
  docs.value = (await api.get('/unstructured/platform/documents', {
    params: {
      keyword: docQuery.keyword || undefined,
      publishStatus: docQuery.publishStatus || undefined,
      categoryCode: docQuery.categoryCode || undefined,
    },
  })).data || []
}

async function loadPipelines(type: ProcessType) {
  pipelines.value = (await api.get('/unstructured/platform/pipelines', {
    params: { pipelineType: type },
  })).data || []
}

async function loadCleanOverview() {
  cleanOverview.value = (await api.get('/unstructured/platform/clean/overview')).data
}

async function loadCleanRules() {
  cleanRules.value = (await api.get('/unstructured/platform/clean/rules')).data || []
}

async function loadCleanIssues() {
  cleanIssues.value = (await api.get('/unstructured/platform/clean/issues', {
    params: { issueStatus: cleanIssueStatus.value || undefined },
  })).data || []
}

async function loadTabData() {
  try {
    if (!overviewLoaded) await loadOverview()
    const nav = activeNav.value
    if (nav === 'classify') {
      await loadCategories()
    } else if (nav === 'files') {
      await Promise.all([loadDocuments(), loadCategories()])
    } else if (nav === 'search') {
      await loadCategories()
      // 不预加载文档列表；检索由用户触发
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
  if (docForm.sourceType === 'UPLOAD') {
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
  } else {
    if (!docForm.sourceUrl.trim()) {
      ElMessage.warning('请填写外部文件资源地址')
      return
    }
    await api.post('/unstructured/platform/documents', {
      title: docForm.title.trim(),
      categoryCode: docForm.categoryCode,
      contentType: docForm.contentType,
      description: docForm.description.trim(),
      tagJson: tagsToJson(docForm.tagText),
      sourceSystem: docForm.sourceSystem.trim(),
      sourceUrl: docForm.sourceUrl.trim(),
    })
  }
  ElMessage.success('文件资源已登记')
  docForm.title = ''
  docForm.description = ''
  docForm.tagText = ''
  docForm.sourceUrl = ''
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

async function doSearch() {
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
    ElMessage.success(`检索完成，命中 ${searchHits.value.length} 条`)
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
        <div class="uns-section-bar">
          <div>
            <strong>文件分类体系</strong>
            <span>统一维护文档、图片、视频、音频分类及编码</span>
          </div>
          <el-button type="primary" @click="addCategory">新增分类</el-button>
        </div>
        <el-table :data="categories" stripe size="small">
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
              <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="editCategory(row)">编辑</el-button>
              <el-button link type="danger" @click="deleteCategory(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

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
        <el-alert
          title="统一登记本地文件或其他文件业务平台资源；文件必须绑定分类后才能发布，发布后可建立检索索引。"
          type="info"
          :closable="false"
          show-icon
          class="uns-alert"
        />
        <el-form inline class="portal-inline-form portal-inline-form--block">
          <el-form-item label="来源" class="portal-field-md">
            <el-radio-group v-model="docForm.sourceType">
              <el-radio-button value="UPLOAD">本地上传</el-radio-button>
              <el-radio-button value="EXTERNAL">外部平台</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="标题" class="portal-field-lg">
            <el-input v-model="docForm.title" placeholder="文档标题" />
          </el-form-item>
          <el-form-item label="分类" class="portal-field-lg">
            <el-select v-model="docForm.categoryCode" placeholder="选择已登记分类" filterable>
              <el-option
                v-for="c in categories"
                :key="c.id"
                :label="c.categoryName"
                :value="c.categoryCode"
              />
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
          <el-form-item v-if="docForm.sourceType === 'EXTERNAL'" label="资源地址" class="portal-field-xl">
            <el-input v-model="docForm.sourceUrl" placeholder="https://..." />
          </el-form-item>
          <el-form-item v-else label="选择文件" class="portal-field-xl">
            <el-upload
              action="#"
              :auto-upload="false"
              :limit="1"
              :show-file-list="true"
              @change="onFileChange"
            >
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
        <el-divider content-position="left">已登记文件资源</el-divider>
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
          </el-form-item>
        </el-form>
        <el-table :data="docs" stripe size="small">
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
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ row.contentType?.split('/')[0] || '—' }}</template>
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
          <el-table-column label="操作" width="320" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
              <el-button link @click="accessFile(row, false)">预览</el-button>
              <el-button link @click="accessFile(row, true)">下载</el-button>
              <el-button link type="primary" @click="openDocEdit(row)">编辑</el-button>
              <el-button
                v-if="row.publishStatus !== 'PUBLISHED'"
                link
                type="primary"
                @click="publishDoc(row.id)"
              >发布</el-button>
              <el-button
                v-else
                link
                @click="offlineDoc(row.id)"
              >下线</el-button>
              <el-button
                v-if="row.indexStatus !== 'INDEXED'"
                link
                type="primary"
                @click="indexDoc(row.id)"
              >建索</el-button>
              <el-button
                v-if="row.publishStatus !== 'PUBLISHED'"
                link
                type="danger"
                @click="deleteDoc(row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

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
        <el-alert
          v-if="searchEsHealthy !== null || overview"
          :type="(searchEsHealthy ?? overview?.esHealthy) ? 'success' : 'warning'"
          :closable="false"
          show-icon
          style="margin-bottom:12px"
          :title="(searchEsHealthy ?? overview?.esHealthy)
            ? 'Elasticsearch 可用，优先走检索引擎'
            : 'Elasticsearch 不可用，将降级为数据库检索'"
        />
        <el-form inline class="portal-inline-form portal-inline-form--block">
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
        <el-table v-if="searchDone" :data="searchHits" stripe size="small">
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
            <template #default="{ row }">{{ statusLabel(row.publishStatus) }}</template>
          </el-table-column>
          <el-table-column label="索引" width="100">
            <template #default="{ row }">{{ statusLabel(row.indexStatus) }}</template>
          </el-table-column>
          <el-table-column prop="source" label="来源" width="120">
            <template #default="{ row }">
              {{ row.source === 'elasticsearch' ? '检索引擎' : '数据库' }}
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
        <el-empty v-else description="输入条件后点击检索" />
      </PageCard>

      <PageCard v-else-if="activeNav === 'metadata'" title="非结构化元数据管理">
        <div class="uns-section-bar">
          <div>
            <strong>基本特征 · 内容理解 · 标签与相似检索 · 元数据落地</strong>
            <span>对文档/图片/音视频提取特征与内容理解结果，落地到元数据库并支持相似连接（引擎模式：台账 LEDGER，未接外部 NLP/CV 时诚实降级）</span>
          </div>
        </div>
        <el-row :gutter="12" class="uns-meta-kpi">
          <el-col :span="6">
            <div class="uns-kpi">
              <div class="uns-kpi-label">文档总数</div>
              <div class="uns-kpi-value">{{ metaOverview?.documents ?? docs.length }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="uns-kpi">
              <div class="uns-kpi-label">待提取</div>
              <div class="uns-kpi-value">{{ metaOverview?.metaRaw ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="uns-kpi">
              <div class="uns-kpi-label">已提取特征</div>
              <div class="uns-kpi-value">{{ metaOverview?.metaExtracted ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="uns-kpi">
              <div class="uns-kpi-label">已内容理解 / 已打标</div>
              <div class="uns-kpi-value">{{ metaOverview?.metaUnderstood ?? '—' }} / {{ metaOverview?.tagged ?? '—' }}</div>
            </div>
          </el-col>
        </el-row>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="uns-meta-flow"
          title="元数据管理规范：采集（登记/上传）→ 基本特征提取 → 内容理解与标签化 → 相似检索消费 → 人工维护落地"
        />
        <el-table :data="docs" stripe size="small">
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
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :loading="metaBusyId === row.id"
                @click="extractFeatures(row)"
              >特征提取</el-button>
              <el-button
                link
                type="primary"
                :loading="metaBusyId === row.id"
                @click="understandContent(row)"
              >内容理解</el-button>
              <el-button link @click="openSimilar(row)">相似检索</el-button>
              <el-button link type="primary" @click="openMetaEdit(row)">编辑落地</el-button>
            </template>
          </el-table-column>
        </el-table>

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
          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="基于标签、关键词、主题与分类指纹的 Jaccard 相似连接（LEDGER），非外部深度学习向量检索"
            class="uns-meta-flow"
          />
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
        <div class="uns-section-bar">
          <div>
            <strong>过滤 · 去重 · 校验 · 转换</strong>
            <span>先定义清洗规则与错误级别，执行后命中问题写入问题库，经确认后入库/放弃；清洗前后做一致性检查（引擎：LEDGER）</span>
          </div>
        </div>
        <el-row :gutter="12" class="uns-meta-kpi">
          <el-col :span="4">
            <div class="uns-kpi">
              <div class="uns-kpi-label">启用规则</div>
              <div class="uns-kpi-value">{{ cleanOverview?.rules ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="uns-kpi">
              <div class="uns-kpi-label">已清洗</div>
              <div class="uns-kpi-value">{{ cleanOverview?.cleaned ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="uns-kpi">
              <div class="uns-kpi-label">问题文档</div>
              <div class="uns-kpi-value">{{ cleanOverview?.problem ?? '—' }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="uns-kpi">
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
            <div class="uns-kpi">
              <div class="uns-kpi-label">清洗流水</div>
              <div class="uns-kpi-value">{{ cleanOverview?.pipelines ?? '—' }}</div>
            </div>
          </el-col>
        </el-row>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="uns-meta-flow"
          title="流程：定义规则（错误级别）→ 执行清洗（过滤/去重/校验/转换）→ 问题数据确认（入库/放弃/其他）→ 清洗前后一致性检查"
        />

        <el-tabs v-model="cleanTab">
          <el-tab-pane label="执行清洗" name="execute">
            <el-table :data="docs" stripe size="small">
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
          </el-tab-pane>

          <el-tab-pane label="清洗规则" name="rules">
            <div class="uns-section-bar">
              <div>
                <strong>规则定义</strong>
                <span>按字段配置过滤、去重、校验、转换，并设置错误级别</span>
              </div>
              <el-button type="primary" @click="openRuleCreate">新增规则</el-button>
            </div>
            <el-table :data="cleanRules" stripe size="small">
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
          </el-tab-pane>

          <el-tab-pane label="问题数据" name="issues">
            <el-form inline class="portal-inline-form portal-inline-form--block" @submit.prevent>
              <el-form-item label="状态" class="portal-field-md">
                <el-select v-model="cleanIssueStatus" clearable placeholder="全部" @change="loadCleanIssues">
                  <el-option label="待确认" value="OPEN" />
                  <el-option label="清洗后入库" value="CLEANED_IN" />
                  <el-option label="已放弃" value="ABANDONED" />
                  <el-option label="其他处理" value="OTHER" />
                </el-select>
              </el-form-item>
              <el-form-item class="portal-form-actions">
                <el-button type="primary" @click="loadCleanIssues">刷新</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="cleanIssues" stripe size="small">
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
          </el-tab-pane>

          <el-tab-pane label="清洗流水线" name="pipelines">
            <el-table :data="pipelines" stripe size="small">
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
              <el-descriptions-item label="引擎">{{ cleanResult.engineMode || 'LEDGER' }}</el-descriptions-item>
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
        <p class="uns-process-hint">
          当前仅执行「{{ processActionLabel }}」处理；流水线列表已按类型过滤。
        </p>
        <el-table :data="docs" stripe size="small">
          <el-table-column prop="title" label="文档" min-width="160" />
          <el-table-column label="处理状态" width="110">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.processStatus)" size="small">
                {{ statusLabel(row.processStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click="runPipe(row.id)">
                执行{{ processActionLabel }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-divider content-position="left">处理流水线</el-divider>
        <el-table :data="pipelines" stripe size="small">
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
.uns-process-hint {
  margin: 0 0 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
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
  margin-bottom: 12px;
}
.uns-kpi {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 12px 14px;
}
.uns-kpi-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.uns-kpi-value {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
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
</style>
